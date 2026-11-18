#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""价格建议实验作图（模拟数据）：results/price_main.csv + price_sample_size.csv → figures/fig4_price_suggest.png"""
import csv, os, sys
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib import font_manager

for f in font_manager.findSystemFonts():
    if "NotoSansCJK" in f or "NotoSansSC" in f:
        font_manager.fontManager.addfont(f)
plt.rcParams["font.sans-serif"] = ["Noto Sans CJK SC", "Noto Sans SC", "DejaVu Sans"]
plt.rcParams["axes.unicode_minus"] = False

res = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..", "results")
rows = list(csv.DictReader(open(os.path.join(res, "price_main.csv"), encoding="utf-8")))
size = list(csv.DictReader(open(os.path.join(res, "price_sample_size.csv"), encoding="utf-8")))

fig, axes = plt.subplots(1, 2, figsize=(14, 5.2))
# (a) 各方法在全部 / 有原价 / 无原价 测试商品上的 MdAPE
methods = [r["method"] for r in rows if r["group"] == "全部"]
groups = ["全部", "有原价", "无原价"]
w = 0.26
ax = axes[0]
for gi, g in enumerate(groups):
    vals = [float(next(r for r in rows if r["method"] == m and r["group"] == g)["MdAPE"]) for m in methods]
    errs = [float(next(r for r in rows if r["method"] == m and r["group"] == g)["MdAPE_std"]) for m in methods]
    ax.bar([i + (gi - 1) * w for i in range(len(methods))], vals, w, yerr=errs, capsize=2, label=g)
ax.set_xticks(range(len(methods)))
ax.set_xticklabels(methods, rotation=35, ha="right", fontsize=8)
ax.set_ylabel("MdAPE（越低越好）")
ax.set_title("(a) 各方法中位绝对百分比误差（5 次划分均值±标准差）")
ax.legend()
# (b) 样本量敏感性
ax = axes[1]
for m in dict.fromkeys(r["method"] for r in size):
    pts = [(int(r["n_samples"]), float(r["MdAPE"])) for r in size if r["method"] == m]
    ax.plot([str(p[0]) for p in pts], [p[1] for p in pts], marker="o", label=m)
ax.set_xlabel("每个测试商品可见的同类样本数 n")
ax.set_ylabel("MdAPE（越低越好）")
ax.set_title("(b) 样本量敏感性")
ax.legend()
fig.suptitle("价格建议离线评估（模拟数据）")
fig.tight_layout()
os.makedirs(os.path.join(res, "figures"), exist_ok=True)
out = os.path.join(res, "figures", "fig4_price_suggest.png")
fig.savefig(out, dpi=150)
print("saved", out)
