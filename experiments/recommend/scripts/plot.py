#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""根据 results/summary.csv 与 eta_sensitivity.csv 画中文图（results/figures/）。误差线为 5 个划分种子的标准差。"""
import csv, os, sys
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib import font_manager
import numpy as np

RES = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..", "results")
FIG = os.path.join(RES, "figures")
os.makedirs(FIG, exist_ok=True)
for name in ["Noto Sans CJK SC", "Noto Sans CJK JP", "WenQuanYi Zen Hei", "SimHei", "Microsoft YaHei"]:
    if any(f.name == name for f in font_manager.fontManager.ttflist):
        plt.rcParams["font.sans-serif"] = [name]
        break
else:
    print("警告：未找到中文字体，图中中文可能显示为方框", file=sys.stderr)
plt.rcParams["axes.unicode_minus"] = False

S = {}
for r in csv.DictReader(open(os.path.join(RES, "summary.csv"), encoding="utf-8")):
    S[(r["dataset"], r["subset"], r["method"], int(r["N"]))] = r

SHORT = {"random": "随机", "popular": "热度", "usercf": "UserCF", "itemcf_pure": "纯CF", "cf_weighted": "CF加权",
         "hybrid_eta0.0": "混合 η=0", "hybrid_eta0.1": "混合 η=0.1", "abl_norm_only": "纯CF+归一化",
         "abl_w": "+行为加权", "abl_w_iuf": "+加权+IUF", "abl_iuf": "纯CF+IUF", "abl_linear_iuf_norm": "线性加权版",
         "hybrid_eta0.1_nodeclared": "混合η=0.1\n无注册兴趣"}
MAIN = ["random", "popular", "usercf", "itemcf_pure", "cf_weighted", "hybrid_eta0.0", "hybrid_eta0.1"]
MET = [("precision", "P@10"), ("recall", "R@10"), ("ndcg", "NDCG@10")]


def bars(ax, ds, subset, methods, title):
    methods = [m for m in methods if (ds, subset, m, 10) in S]
    x = np.arange(len(methods)); w = 0.26
    for j, (k, lab) in enumerate(MET):
        mean = [float(S[(ds, subset, m, 10)][k + "_mean"]) for m in methods]
        sd = [float(S[(ds, subset, m, 10)][k + "_std"]) for m in methods]
        ax.bar(x + (j - 1) * w, mean, w, yerr=sd, capsize=2, label=lab)
    ax.set_xticks(x); ax.set_xticklabels([SHORT.get(m, m) for m in methods], rotation=25, fontsize=8)
    ax.set_title(title, fontsize=10); ax.legend(fontsize=8); ax.grid(axis="y", alpha=0.3)


def fig(name, panels, suptitle):
    f, axes = plt.subplots(1, len(panels), figsize=(6.2 * len(panels), 4.3))
    axes = np.atleast_1d(axes)
    for ax, p in zip(axes, panels):
        bars(ax, *p)
    f.suptitle(suptitle, fontsize=11)
    f.tight_layout(); f.savefig(os.path.join(FIG, name), dpi=160); plt.close(f)


fig("fig1_main_comparison.png",
    [("ml100k", "all", MAIN, "MovieLens-100K（真实数据）全体用户"),
     ("campus", "all", MAIN, "校园二手 Campus-Sim（模拟数据）全体用户")],
    "主对比（K=40，N=10，5 个划分种子均值 ± 标准差）")
fig("fig2_cold_start.png",
    [("ml100k_coldsim", "cold", MAIN, "MovieLens-100K 冷启动协议（真实数据，截断 20% 用户）"),
     ("campus", "cold", MAIN + ["hybrid_eta0.1_nodeclared"], "校园二手（模拟数据）冷启动子集")],
    "冷启动用户（训练交互物品数 < 5，N=10，均值 ± 标准差）")
fig("fig3_ablation.png",
    [("ml100k", "all", ["itemcf_pure", "abl_norm_only", "abl_iuf", "cf_weighted"], "MovieLens-100K（真实数据）"),
     ("campus", "all", ["itemcf_pure", "abl_norm_only", "abl_w", "abl_w_iuf", "cf_weighted", "abl_linear_iuf_norm"],
      "校园二手（模拟数据）")],
    "CF 消融：纯 CF → 行为加权 → IUF → 归一化（N=10，均值 ± 标准差）")

# η 敏感性：左轴 Recall@10 / NDCG@10，右轴覆盖率
E = list(csv.DictReader(open(os.path.join(RES, "eta_sensitivity.csv"), encoding="utf-8")))
f, axes = plt.subplots(1, 2, figsize=(12, 4.3))
for ax, (ds, title) in zip(axes, [("campus", "校园二手（模拟数据）冷启动子集"),
                                  ("ml100k_coldsim", "MovieLens-100K 冷启动协议（真实数据）")]):
    rs = sorted([r for r in E if r["dataset"] == ds and r["subset"] == "cold" and r["N"] == "10"],
                key=lambda r: float(r["eta"]))
    eta = [float(r["eta"]) for r in rs]
    g = lambda k: (np.array([float(r[k + "_mean"]) for r in rs]), np.array([float(r[k + "_std"]) for r in rs]))
    for k, lab, c in (("recall", "Recall@10", "tab:blue"), ("ndcg", "NDCG@10", "tab:green")):
        m, s = g(k); ax.errorbar(eta, m, yerr=s, marker="o", capsize=3, label=lab, color=c)
    ax.set_xlabel("η（冷启动用户 CF 名额比例，名额 = ceil(η·N)）"); ax.set_ylabel("Recall@10 / NDCG@10")
    ax2 = ax.twinx(); m, s = g("coverage")
    ax2.errorbar(eta, m, yerr=s, marker="s", ls="--", capsize=3, color="tab:red", label="覆盖率@10")
    ax2.set_ylabel("覆盖率@10", color="tab:red")
    h1, l1 = ax.get_legend_handles_labels(); h2, l2 = ax2.get_legend_handles_labels()
    ax.legend(h1 + h2, l1 + l2, fontsize=8, loc="center right"); ax.set_title(title, fontsize=10)
    ax.set_xticks(eta); ax.grid(alpha=0.3)
f.suptitle("η 敏感性：覆盖率与召回率的权衡（N=10，5 个划分种子均值 ± 标准差；η=0.05 与 0.1 在 N=10 时名额同为 1）", fontsize=10)
f.tight_layout(); f.savefig(os.path.join(FIG, "fig4_eta_sensitivity.png"), dpi=160); plt.close(f)
print("figures written to", FIG)
