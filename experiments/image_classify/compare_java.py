#!/usr/bin/env python3
"""对比后端 Java 实现与 Python 参考实现：图像向量余弦、Top1 一致率、Java 端准确率与耗时。结果并入 results/metrics.json。"""
import csv
import json
import os
import statistics

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
R = os.path.join(HERE, "results")
metrics = json.load(open(os.path.join(R, "metrics.json"), encoding="utf-8"))
emb = json.load(open(os.path.join(HERE, "..", "..", "scripts", "ai", "models", "clip-vit-base-patch32",
                                  "category_embeddings.json"), encoding="utf-8"))
parent = {c["categoryId"]: (c["parentId"] or c["categoryId"]) for c in emb["categories"]}
samples = [s["file"] for s in csv.DictReader(open(os.path.join(HERE, "samples.csv"), encoding="utf-8"))]
metrics["java"] = {}
for tag in ["fp32", "int8"]:
    pf = os.path.join(R, f"java_predictions_{tag}.csv")
    if not os.path.exists(pf):
        continue
    rows = list(csv.DictReader(open(pf, encoding="utf-8")))
    jemb = {}
    for line in open(os.path.join(R, f"java_embeds_{tag}.csv"), encoding="utf-8"):
        p = line.strip().split(",")
        jemb[p[0]] = np.array([float(x) for x in p[1:]])
    pyemb = np.load(os.path.join(R, f"image_embeds_{tag}.npy"))
    cos = [float(jemb[f] @ pyemb[i]) for i, f in enumerate(samples)]
    py_pred = {r["file"]: r for r in csv.DictReader(open(os.path.join(R, f"predictions_{tag}-ensemble.csv"), encoding="utf-8"))}
    names = {c["categoryId"]: c["name"] for c in emb["categories"]}
    agree = sum(names[r["top1Id"]] == py_pred[r["file"]]["top1"] for r in rows)
    n = len(rows)
    metrics["java"][tag] = {
        "top1": sum(r["top1Id"] == r["truthId"] for r in rows) / n,
        "top3": sum(r["truthId"] in (r["top1Id"], r["top2Id"], r["top3Id"]) for r in rows) / n,
        "parentTop1": sum(parent[r["top1Id"]] == parent[r["truthId"]] for r in rows) / n,
        "top1AgreementWithPython": agree / n,
        "embeddingCosineVsPython": {"mean": statistics.mean(cos), "min": min(cos)},
        "decodePreprocessMsMean": statistics.mean(float(r["decodePreprocessMs"]) for r in rows),
        "inferMsMean": statistics.mean(float(r["inferMs"]) for r in rows),
        "inferMsP95": float(np.percentile([float(r["inferMs"]) for r in rows], 95)),
    }
    print(tag, json.dumps(metrics["java"][tag], ensure_ascii=False))
json.dump(metrics, open(os.path.join(R, "metrics.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
