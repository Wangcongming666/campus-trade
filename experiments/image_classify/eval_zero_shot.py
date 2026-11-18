#!/usr/bin/env python3
"""
CLIP 零样本商品图片分类小样本评估（Python 参考实现，与后端 Java 实现同一模型、同一向量、同一预处理）。

指标：二级(叶子)分类 Top1 / Top3 准确率、一级分类 Top1 准确率（取 Top1 叶子所属一级分类）、
Top1 的 Wilson 95% 置信区间、各类准确率、混淆矩阵、单张平均耗时（CPU，预处理+推理）。
消融：单提示词 vs 提示词集成 × fp32 vs int8 量化图像编码器。

用法：python3 eval_zero_shot.py [--threads 2]
输入：samples.csv、images/、../../scripts/ai/models/clip-vit-base-patch32/
输出：results/metrics.json、results/predictions_*.csv、results/confusion_*.csv、results/results.md
"""
import argparse
import csv
import json
import math
import os
import platform
import statistics
import subprocess
import time

import numpy as np
import onnxruntime as ort
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
MODEL_DIR = os.path.join(HERE, "..", "..", "scripts", "ai", "models", "clip-vit-base-patch32")
MEAN = np.array([0.48145466, 0.4578275, 0.40821073], dtype=np.float32)
STD = np.array([0.26862954, 0.26130258, 0.27577711], dtype=np.float32)
LOGIT_SCALE = 100.0


def preprocess(path):
    """与 Java ImagePreprocessor 一致：透明合成白底 → 短边 224 BICUBIC → 中心裁剪 → 标准化。"""
    im = Image.open(path)
    if im.mode in ("RGBA", "LA") or (im.mode == "P" and "transparency" in im.info):
        im = im.convert("RGBA")
        bg = Image.new("RGBA", im.size, (255, 255, 255, 255))
        im = Image.alpha_composite(bg, im)
    im = im.convert("RGB")
    w, h = im.size
    if w <= h:
        nw, nh = 224, int(224 * h / w)
    else:
        nw, nh = int(224 * w / h), 224
    im = im.resize((nw, nh), Image.BICUBIC)
    left, top = (nw - 224) // 2, (nh - 224) // 2
    im = im.crop((left, top, left + 224, top + 224))
    a = (np.asarray(im, dtype=np.float32) / 255.0 - MEAN) / STD
    return a.transpose(2, 0, 1)[None].astype(np.float32)


def wilson(k, n, z=1.96):
    if n == 0:
        return (0.0, 0.0)
    p = k / n
    d = 1 + z * z / n
    c = (p + z * z / (2 * n)) / d
    m = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / d
    return (max(0.0, c - m), min(1.0, c + m))


def softmax(x):
    x = x - x.max(axis=-1, keepdims=True)
    e = np.exp(x)
    return e / e.sum(axis=-1, keepdims=True)


def cpu_name():
    try:
        out = subprocess.check_output(["lscpu"], text=True)
        for line in out.splitlines():
            if line.startswith("Model name"):
                return line.split(":", 1)[1].strip()
    except Exception:  # noqa
        pass
    return platform.processor()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--threads", type=int, default=2, help="ONNX Runtime intra-op 线程数（与后端默认一致）")
    a = ap.parse_args()
    samples = list(csv.DictReader(open(os.path.join(HERE, "samples.csv"), encoding="utf-8")))
    emb = json.load(open(os.path.join(MODEL_DIR, "category_embeddings.json"), encoding="utf-8"))
    cats = emb["categories"]
    ids = [c["categoryId"] for c in cats]
    names = {c["categoryId"]: c["name"] for c in cats}
    parent = {c["categoryId"]: (c["parentId"] or c["categoryId"]) for c in cats}
    text = {
        "ensemble": np.array([c["embedding"] for c in cats], dtype=np.float32),
        "single": np.array([c["singleEmbedding"] for c in cats], dtype=np.float32),
    }
    for k in text:
        text[k] /= np.linalg.norm(text[k], axis=1, keepdims=True)
    os.makedirs(os.path.join(HERE, "results"), exist_ok=True)

    metrics = {"cpu": cpu_name(), "threads": a.threads, "onnxruntime": ort.__version__,
               "nSamples": len(samples), "nClasses": len(ids),
               "samplesPerClass": {names[i]: sum(1 for s in samples if s["categoryId"] == i) for i in ids},
               "runs": {}}
    img_embs = {}
    for variant, fname in [("fp32", "vision_model.onnx"), ("int8", "vision_model_quantized.onnx")]:
        path = os.path.join(MODEL_DIR, fname)
        if not os.path.exists(path):
            print(f"跳过 {variant}：缺少 {path}")
            continue
        so = ort.SessionOptions()
        so.intra_op_num_threads = a.threads
        so.inter_op_num_threads = 1
        so.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
        sess = ort.InferenceSession(path, so, providers=["CPUExecutionProvider"])
        sess.run(None, {"pixel_values": np.zeros((1, 3, 224, 224), np.float32)})  # 预热
        embs, pre_ms, inf_ms = [], [], []
        for s in samples:
            t0 = time.perf_counter()
            x = preprocess(os.path.join(HERE, "images", s["file"]))
            t1 = time.perf_counter()
            e = sess.run(["image_embeds"], {"pixel_values": x})[0][0]
            t2 = time.perf_counter()
            embs.append(e / np.linalg.norm(e))
            pre_ms.append((t1 - t0) * 1000)
            inf_ms.append((t2 - t1) * 1000)
        embs = np.stack(embs)
        img_embs[variant] = embs
        np.save(os.path.join(HERE, "results", f"image_embeds_{variant}.npy"), embs)
        for prompt in ["single", "ensemble"]:
            probs = softmax(LOGIT_SCALE * embs @ text[prompt].T)
            order = np.argsort(-probs, axis=1)
            n = len(samples)
            top1 = top3 = par1 = 0
            conf = {i: {j: 0 for j in ids} for i in ids}
            per = {i: [0, 0] for i in ids}
            rows = []
            for k, s in enumerate(samples):
                truth = s["categoryId"]
                pred = [ids[j] for j in order[k, :3]]
                ok1 = pred[0] == truth
                top1 += ok1
                top3 += truth in pred
                par1 += parent[pred[0]] == parent[truth]
                conf[truth][pred[0]] += 1
                per[truth][0] += ok1
                per[truth][1] += 1
                rows.append({"file": s["file"], "truth": names[truth],
                             "top1": names[pred[0]], "p1": f"{probs[k, order[k, 0]]:.4f}",
                             "top2": names[pred[1]], "p2": f"{probs[k, order[k, 1]]:.4f}",
                             "top3": names[pred[2]], "p3": f"{probs[k, order[k, 2]]:.4f}",
                             "correct": int(ok1)})
            key = f"{variant}-{prompt}"
            lo, hi = wilson(top1, n)
            correct_conf = [float(probs[k, order[k, 0]]) for k in range(n) if rows[k]["correct"]]
            wrong_conf = [float(probs[k, order[k, 0]]) for k in range(n) if not rows[k]["correct"]]
            metrics["runs"][key] = {
                "top1": top1 / n, "top1CI95": [lo, hi], "top3": top3 / n, "parentTop1": par1 / n,
                "perClassTop1": {names[i]: per[i][0] / per[i][1] for i in ids if per[i][1]},
                "meanTop1ConfCorrect": statistics.mean(correct_conf) if correct_conf else None,
                "meanTop1ConfWrong": statistics.mean(wrong_conf) if wrong_conf else None,
                "preprocessMsMean": statistics.mean(pre_ms), "inferMsMean": statistics.mean(inf_ms),
                "inferMsP50": statistics.median(inf_ms),
                "inferMsP95": float(np.percentile(inf_ms, 95)), "modelFile": fname,
                "modelMB": round(os.path.getsize(path) / 1e6, 1),
            }
            with open(os.path.join(HERE, "results", f"predictions_{key}.csv"), "w", newline="", encoding="utf-8") as f:
                w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
                w.writeheader()
                w.writerows(rows)
            with open(os.path.join(HERE, "results", f"confusion_{key}.csv"), "w", newline="", encoding="utf-8") as f:
                w = csv.writer(f)
                w.writerow(["真实\\预测"] + [names[j] for j in ids])
                for i in ids:
                    w.writerow([names[i]] + [conf[i][j] for j in ids])
            print(f"{key}: top1={top1 / n:.4f} top3={top3 / n:.4f} parent={par1 / n:.4f} "
                  f"pre={statistics.mean(pre_ms):.1f}ms infer={statistics.mean(inf_ms):.1f}ms")
    if "fp32" in img_embs and "int8" in img_embs:
        cos = (img_embs["fp32"] * img_embs["int8"]).sum(1)
        metrics["fp32VsInt8EmbeddingCosineMean"] = float(cos.mean())
    json.dump(metrics, open(os.path.join(HERE, "results", "metrics.json"), "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
