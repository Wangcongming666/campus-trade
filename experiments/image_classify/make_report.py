#!/usr/bin/env python3
"""根据 results/metrics.json 与混淆矩阵生成 results/results.md（论文用表格）。"""
import csv
import json
import os
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
R = os.path.join(HERE, "results")
m = json.load(open(os.path.join(R, "metrics.json"), encoding="utf-8"))
samples = list(csv.DictReader(open(os.path.join(HERE, "samples.csv"), encoding="utf-8")))
pct = lambda x: f"{x * 100:.1f}%"  # noqa
L = []
L.append("# CLIP 零样本商品图片分类 · 小样本评估结果\n")
L.append("> 本文件由 `make_report.py` 自动生成，所有数字来自 `run_all.sh` 的真实运行（" + m["cpu"]
         + f"，ONNX Runtime {m['onnxruntime']}，intra-op 线程 {m['threads']}）。\n")
L.append(f"- 样本：{m['nSamples']} 张，{m['nClasses']} 个叶子分类，来源 Wikimedia Commons（自由许可，逐张来源与许可见 `samples.csv`）")
L.append("- 模型：CLIP ViT-B/32（openai/clip-vit-base-patch32，ONNX 版 Xenova/clip-vit-base-patch32），零样本，未用任何样本训练或调参")
L.append("- 置信度：余弦相似度 × 100 后 softmax；一级分类准确率 = Top1 叶子所属一级分类是否正确（无子分类的一级分类以自身计）\n")
L.append("## 1. 消融：单提示词 vs 提示词集成，fp32 vs int8 量化\n")
L.append("| 图像编码器 | 文本提示 | Top1 | Top1 95% CI (Wilson) | Top3 | 一级分类 Top1 | 推理耗时均值 / P95 (ms) | 模型大小 |")
L.append("|---|---|---|---|---|---|---|---|")
for key, label in [("fp32-single", ("fp32", "单提示词")), ("fp32-ensemble", ("fp32", "提示词集成")),
                   ("int8-single", ("int8", "单提示词")), ("int8-ensemble", ("int8", "提示词集成"))]:
    r = m["runs"].get(key)
    if not r:
        continue
    lo, hi = r["top1CI95"]
    L.append(f"| {label[0]} | {label[1]} | {pct(r['top1'])} | {pct(lo)}–{pct(hi)} | {pct(r['top3'])} | "
             f"{pct(r['parentTop1'])} | {r['inferMsMean']:.1f} / {r['inferMsP95']:.1f} | {r['modelMB']} MB |")
L.append("\n单提示词为每类 1 句（如 \"a photo of a bicycle.\"）；提示词集成为每类 5 个模板 × 5–7 个物品描述（25–35 句）的归一化均值，见 `scripts/ai/category_prompts.json`。\n")
L.append("## 2. 后端 Java 实现复核（同一批样本）\n")
L.append("| 图像编码器 | Top1 | Top3 | 一级 Top1 | 与 Python Top1 一致率 | 图像向量余弦（均值/最小） | 解码+预处理 (ms) | 推理 均值/P95 (ms) |")
L.append("|---|---|---|---|---|---|---|---|")
for tag, j in m.get("java", {}).items():
    c = j["embeddingCosineVsPython"]
    L.append(f"| {tag} | {pct(j['top1'])} | {pct(j['top3'])} | {pct(j['parentTop1'])} | {pct(j['top1AgreementWithPython'])} | "
             f"{c['mean']:.6f} / {c['min']:.6f} | {j['decodePreprocessMsMean']:.1f} | {j['inferMsMean']:.1f} / {j['inferMsP95']:.1f} |")
L.append("\nJava 端使用后端 `com.campus.trade.ai.clip` 的同一套代码（预处理按 Pillow BICUBIC 算法实现），仅提示词集成。"
         "两边计时都包含解码；Java 端解码+预处理较慢（ImageIO 解码与纯 Java 重采样，含 JIT 预热期），但仍明显小于推理耗时。"
         "int8 量化模型在 Java（Maven 版 ONNX Runtime）与 Python（pip 版）上结果不完全一致（量化算子在不同构建/CPU 指令集下实现不同），fp32 完全一致。\n")
from math import comb


def mcnemar(a, b):
    """配对精确 McNemar 检验（双侧）：a、b 为逐张是否正确的列表。"""
    n01 = sum(1 for x, y in zip(a, b) if x and not y)
    n10 = sum(1 for x, y in zip(a, b) if y and not x)
    n = n01 + n10
    if n == 0:
        return n01, n10, 1.0
    k = min(n01, n10)
    p = sum(comb(n, i) for i in range(0, k + 1)) / 2 ** n * 2
    return n01, n10, min(1.0, p)


def correct(key):
    return [int(r["correct"]) for r in csv.DictReader(open(os.path.join(R, f"predictions_{key}.csv"), encoding="utf-8"))]


L.append("## 附：提示词集成的显著性（配对精确 McNemar 检验）\n")
L.append("| 对比 | 仅单提示词对 | 仅集成对 | p 值 |")
L.append("|---|---|---|---|")
for v in ["fp32", "int8"]:
    if f"{v}-single" in m["runs"]:
        a, b, p = mcnemar(correct(f"{v}-single"), correct(f"{v}-ensemble"))
        L.append(f"| {v} 单提示词 vs 集成 | {a} | {b} | {p:.3f} |")
L.append("\n样本量小（108 张），p ≥ 0.05 时只能说集成“有提升趋势”，不能下显著结论。\n")
best = "fp32-ensemble"
L.append(f"## 3. 各分类 Top1 准确率（{best}，后端默认配置）\n")
cnt = Counter(s["categoryName"] for s in samples)
L.append("| 分类 | 样本数 | 单提示词 | 提示词集成 |")
L.append("|---|---|---|---|")
for name, acc in m["runs"][best]["perClassTop1"].items():
    L.append(f"| {name} | {cnt[name]} | {pct(m['runs']['fp32-single']['perClassTop1'][name])} | {pct(acc)} |")
L.append(f"\n## 4. 混淆矩阵（{best}，行=真实，列=预测 Top1）\n")
rows = list(csv.reader(open(os.path.join(R, f"confusion_{best}.csv"), encoding="utf-8")))
L.append("| " + " | ".join(rows[0]) + " |")
L.append("|" + "---|" * len(rows[0]))
for r in rows[1:]:
    L.append("| " + " | ".join(r) + " |")
r = m["runs"][best]
L.append(f"\n## 5. 置信度\n\n- Top1 预测正确时平均置信度 {r['meanTop1ConfCorrect']:.3f}，错误时 {r['meanTop1ConfWrong']:.3f}（{best}）。"
         "前端可据此只在置信度较高时自动选中，较低时仅展示 Top3 供用户点选。\n")
open(os.path.join(R, "results.md"), "w", encoding="utf-8").write("\n".join(L) + "\n")
print("已生成 results/results.md")
