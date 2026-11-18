#!/usr/bin/env python3
"""
为每个分类预先计算 CLIP 文本向量（prompt ensembling），输出 JSON 供 Java 端零样本分类使用。

Java 端只运行图像编码器，避免在 Java 里实现 CLIP BPE 分词。
做法与 CLIP 论文一致：对每个分类的全部提示词分别编码 -> L2 归一化 -> 求均值 -> 再归一化。
同时输出单提示词向量 singleEmbedding，供消融实验对比。

用法：
  python3 build_text_embeddings.py \
      --model-dir models/clip-vit-base-patch32 \
      --prompts category_prompts.json \
      --out models/clip-vit-base-patch32/category_embeddings.json
"""
import argparse
import datetime as dt
import hashlib
import json
import os
import sys

import numpy as np
import onnxruntime as ort
from tokenizers import Tokenizer

HERE = os.path.dirname(os.path.abspath(__file__))
MAX_LEN = 77


def l2n(v):
    v = np.asarray(v, dtype=np.float64)
    return v / (np.linalg.norm(v, axis=-1, keepdims=True) + 1e-12)


class TextEncoder:
    def __init__(self, model_dir):
        self.tok = Tokenizer.from_file(os.path.join(model_dir, "tokenizer.json"))
        self.tok.no_padding()
        self.tok.no_truncation()
        so = ort.SessionOptions()
        so.intra_op_num_threads = max(1, (os.cpu_count() or 2) // 2)
        self.sess = ort.InferenceSession(os.path.join(model_dir, "text_model.onnx"), so,
                                         providers=["CPUExecutionProvider"])

    def encode(self, text):
        # 逐条编码、不做 padding：CLIP 文本塔取 EOS 位置的特征，padding 可能干扰池化位置
        ids = self.tok.encode(text.lower()).ids[:MAX_LEN]
        out = self.sess.run(["text_embeds"], {"input_ids": np.array([ids], dtype=np.int64)})[0][0]
        return l2n(out)


def build(model_dir, prompts_file):
    with open(prompts_file, encoding="utf-8") as f:
        cfg = json.load(f)
    enc = TextEncoder(model_dir)
    templates = cfg["templates"]
    cats = []
    for cid, c in cfg["categories"].items():
        prompts = [t.format(item) for item in c["items"] for t in templates]
        embs = np.stack([enc.encode(p) for p in prompts])
        ens = l2n(embs.mean(axis=0))
        single = enc.encode(c["single"])
        cats.append({
            "categoryId": cid,
            "name": c["name"],
            "parentId": c.get("parentId"),
            "parentName": c.get("parentName"),
            "promptCount": len(prompts),
            "single": c["single"],
            "embedding": [round(float(x), 7) for x in ens],
            "singleEmbedding": [round(float(x), 7) for x in single],
        })
        print(f"  {cid} {c['name']}: {len(prompts)} 条提示词", file=sys.stderr)
    with open(prompts_file, "rb") as f:
        sha = hashlib.sha256(f.read()).hexdigest()
    return {
        "model": "openai/clip-vit-base-patch32 (ONNX: Xenova/clip-vit-base-patch32)",
        "dim": len(cats[0]["embedding"]),
        "logitScale": 100.0,
        "promptsSha256": sha,
        "createdAt": dt.datetime.now().astimezone().isoformat(timespec="seconds"),
        "categories": cats,
    }


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--model-dir", default=os.path.join(HERE, "models", "clip-vit-base-patch32"))
    ap.add_argument("--prompts", default=os.path.join(HERE, "category_prompts.json"))
    ap.add_argument("--out", default=None, help="默认 <model-dir>/category_embeddings.json")
    a = ap.parse_args()
    out = a.out or os.path.join(a.model_dir, "category_embeddings.json")
    data = build(a.model_dir, a.prompts)
    tmp = out + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False)
    os.replace(tmp, out)
    print(f"已写出 {out}（{len(data['categories'])} 个分类，维度 {data['dim']}）")


if __name__ == "__main__":
    main()
