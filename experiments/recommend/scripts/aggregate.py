#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""汇总多种子结果（均值 ± 标准差）、做按用户的配对显著性检验，输出 CSV 与 Markdown 表。
用法：python scripts/aggregate.py [results]
输入：results/raw/run_metrics.csv、results/raw/per_user_n10.csv.gz、results/raw/dataset_stats.csv
输出：results/summary.csv、results/eta_sensitivity.csv、results/significance.csv、results/tables.md
"""
import csv, gzip, os, sys
from collections import defaultdict
import numpy as np
from scipy import stats

RES = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..", "results")
RAW = os.path.join(RES, "raw")
METRICS = ["precision", "recall", "ndcg", "coverage", "novelty"]

DS = {
    "ml100k": "MovieLens-100K（真实）",
    "ml100k_coldsim": "MovieLens-100K 冷启动协议（真实数据，人为截断 20% 用户的训练行为）",
    "campus": "Campus-Sim（模拟数据）",
}
M = {
    "random": "Random（随机）",
    "popular": "热度 Popular",
    "usercf": "UserCF（参照）",
    "itemcf_pure": "纯 CF（ItemCF，二值、无 IUF、无归一化）",
    "cf_weighted": "CF 加权（行为加权+对数压缩+IUF+归一化）",
    "hybrid_eta0.0": "CF 加权 + 兴趣冷启动（混合，η=0）",
    "hybrid_eta0.1": "CF 加权 + 兴趣冷启动（混合，η=0.1，线上）",
    "hybrid_eta0.05": "混合 η=0.05", "hybrid_eta0.2": "混合 η=0.2", "hybrid_eta0.3": "混合 η=0.3",
    "hybrid_eta0.1_nodeclared": "混合 η=0.1，不用注册兴趣（只用行为推断分类）",
    "abl_w": "纯 CF + 行为加权（对数压缩）",
    "abl_w_iuf": "纯 CF + 行为加权 + IUF",
    "abl_linear_iuf_norm": "行为线性加权（无对数压缩）+ IUF + 归一化",
    "abl_iuf": "纯 CF + IUF",
    "abl_norm_only": "纯 CF + 归一化（无加权、无 IUF）",
}
SHORT = {"random": "随机", "popular": "热度", "usercf": "UserCF", "itemcf_pure": "纯CF", "cf_weighted": "CF加权",
         "hybrid_eta0.0": "混合η=0", "hybrid_eta0.1": "混合η=0.1", "hybrid_eta0.1_nodeclared": "混合η=0.1(无注册兴趣)",
         "abl_w": "纯CF+加权", "abl_w_iuf": "纯CF+加权+IUF", "abl_iuf": "纯CF+IUF",
         "abl_linear_iuf_norm": "线性加权+IUF+归一化", "abl_norm_only": "纯CF+归一化"}
MAIN = ["random", "popular", "usercf", "itemcf_pure", "cf_weighted", "hybrid_eta0.0", "hybrid_eta0.1"]
ETAS = ["hybrid_eta0.0", "hybrid_eta0.05", "hybrid_eta0.1", "hybrid_eta0.2", "hybrid_eta0.3"]


def load_metrics():
    agg = defaultdict(lambda: defaultdict(list))  # (ds, subset, method, N) -> metric -> [per seed]
    users = {}
    with open(os.path.join(RAW, "run_metrics.csv"), encoding="utf-8") as f:
        for r in csv.DictReader(f):
            key = (r["dataset"], r["subset"], r["method"], int(r["N"]))
            for m in METRICS:
                agg[key][m].append(float(r[m]))
            users.setdefault(key, []).append(int(r["users"]))
    return agg, users


def ms(vals, digits=4):
    a = np.array(vals)
    sd = a.std(ddof=1) if len(a) > 1 else 0.0
    return f"{a.mean():.{digits}f} ± {sd:.{digits}f}"


def write_summary(agg, users):
    with open(os.path.join(RES, "summary.csv"), "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["dataset", "subset", "method", "N", "seeds", "users_mean"] +
                   [f"{m}_{s}" for m in METRICS for s in ("mean", "std")])
        for key in sorted(agg):
            row = list(key) + [len(agg[key]["precision"]), f"{np.mean(users[key]):.1f}"]
            for m in METRICS:
                a = np.array(agg[key][m])
                row += [f"{a.mean():.6f}", f"{(a.std(ddof=1) if len(a) > 1 else 0):.6f}"]
            w.writerow(row)


def table(agg, users, ds, subset, methods, n, out):
    if not any((ds, subset, m, n) in agg for m in methods):
        return
    u = [np.mean(users[(ds, subset, m, n)]) for m in methods if (ds, subset, m, n) in agg]
    seeds = len(agg[(ds, subset, methods[0], n)]["precision"]) if (ds, subset, methods[0], n) in agg else 0
    out.append(f"\n**{DS[ds]} · {'全体评估用户' if subset == 'all' else '冷启动子集'} · N={n}**"
               f"（{seeds} 个划分种子，均值 ± 标准差；平均评估用户 {u[0]:.0f}）\n")
    out.append(f"| 方法 | P@{n} | R@{n} | NDCG@{n} | 覆盖率 | 新颖度(平均 ln(1+流行度)，越小越新颖) |")
    out.append("|---|---|---|---|---|---|")
    if ds.startswith("ml100k") and "cf_weighted" in methods:
        out[-3] += "（注：MovieLens 只有\"评过分\"一种行为，行为加权不适用，\"CF 加权\"在该数据集上 = 二值反馈 + IUF + 归一化）"
    for m in methods:
        k = (ds, subset, m, n)
        if k not in agg:
            continue
        a = agg[k]
        out.append(f"| {M.get(m, m)} | {ms(a['precision'])} | {ms(a['recall'])} | {ms(a['ndcg'])} | "
                   f"{ms(a['coverage'])} | {ms(a['novelty'], 3)} |")


def load_per_user():
    # (ds, method) -> {(seed, user): (hits, ndcg, cold)}
    d = defaultdict(dict)
    with gzip.open(os.path.join(RAW, "per_user_n10.csv.gz"), "rt", encoding="utf-8") as f:
        for r in csv.DictReader(f):
            d[(r["dataset"], r["method"])][(int(r["seed"]), int(r["user"]))] = (
                int(r["hits"]) / 10.0, float(r["ndcg"]), r["cold"] == "1")
    return d


COMPARISONS = [
    # (数据集, 子集, A, B, 说明)
    ("ml100k", "all", "cf_weighted", "itemcf_pure", "IUF+归一化 是否优于纯 CF"),
    ("ml100k", "all", "cf_weighted", "popular", "CF 加权 vs 热度"),
    ("ml100k", "all", "usercf", "cf_weighted", "UserCF vs CF 加权（不利结果核验）"),
    ("ml100k", "all", "abl_iuf", "itemcf_pure", "消融：+IUF"),
    ("ml100k", "all", "cf_weighted", "abl_iuf", "消融：+归一化"),
    ("ml100k_coldsim", "cold", "hybrid_eta0.0", "cf_weighted", "兴趣冷启动（η=0）vs CF 加权"),
    ("ml100k_coldsim", "cold", "hybrid_eta0.1", "hybrid_eta0.0", "η=0.1 vs η=0"),
    ("ml100k_coldsim", "cold", "hybrid_eta0.0", "popular", "混合（η=0）vs 热度"),
    ("ml100k_coldsim", "cold", "usercf", "hybrid_eta0.1", "UserCF vs 混合（η=0.1）"),
    ("campus", "all", "cf_weighted", "itemcf_pure", "CF 加权 vs 纯 CF"),
    ("campus", "all", "cf_weighted", "popular", "CF 加权 vs 热度"),
    ("campus", "all", "hybrid_eta0.1", "cf_weighted", "混合（η=0.1）vs CF 加权"),
    ("campus", "all", "hybrid_eta0.1", "hybrid_eta0.0", "η=0.1 vs η=0"),
    ("campus", "all", "usercf", "hybrid_eta0.1", "UserCF vs 混合（η=0.1）"),
    ("campus", "all", "abl_w", "itemcf_pure", "消融：+行为加权（对数）"),
    ("campus", "all", "abl_w_iuf", "abl_w", "消融：+IUF"),
    ("campus", "all", "cf_weighted", "abl_w_iuf", "消融：+归一化"),
    ("campus", "all", "cf_weighted", "abl_linear_iuf_norm", "对数压缩 vs 线性加权"),
    ("campus", "cold", "hybrid_eta0.0", "cf_weighted", "兴趣冷启动（η=0）vs CF 加权"),
    ("campus", "cold", "hybrid_eta0.1", "cf_weighted", "兴趣冷启动（η=0.1）vs CF 加权"),
    ("campus", "cold", "hybrid_eta0.1", "hybrid_eta0.0", "η=0.1 vs η=0"),
    ("campus", "cold", "hybrid_eta0.1", "popular", "混合（η=0.1）vs 热度"),
    ("campus", "cold", "hybrid_eta0.1", "hybrid_eta0.1_nodeclared", "注册兴趣的作用"),
    ("campus", "cold", "usercf", "hybrid_eta0.1", "UserCF vs 混合（η=0.1）"),
]


def holm(ps):
    order = np.argsort(ps)
    adj = np.empty(len(ps))
    running = 0.0
    for rank, i in enumerate(order):
        running = max(running, min(1.0, (len(ps) - rank) * ps[i]))
        adj[i] = running
    return adj


def significance(pu):
    rows = []
    for ds, subset, a, b, note in COMPARISONS:
        if (ds, a) not in pu or (ds, b) not in pu:
            continue
        A, B = pu[(ds, a)], pu[(ds, b)]
        keys = [k for k in A if k in B and (subset == "all" or A[k][2])]
        seeds = sorted({k[0] for k in keys})
        for mi, metric in ((1, "NDCG@10"), (0, "P@10")):
            # 主检验：每个用户一个观测（在其出现于该子集的各种子上取平均），双侧
            per_user = defaultdict(lambda: [[], []])
            for k in keys:
                per_user[k[1]][0].append(A[k][mi])
                per_user[k[1]][1].append(B[k][mi])
            xa = np.array([np.mean(v[0]) for v in per_user.values()])
            xb = np.array([np.mean(v[1]) for v in per_user.values()])
            diff = xa - xb
            if np.allclose(diff, 0):
                pw, pt = 1.0, 1.0
            else:
                pw = stats.wilcoxon(xa, xb).pvalue
                pt = stats.ttest_rel(xa, xb).pvalue
            # 稳健性：逐种子 Wilcoxon，统计 p<0.05 且方向与总体一致的种子数
            sig_seeds = 0
            for s in seeds:
                ka = [k for k in keys if k[0] == s]
                va = np.array([A[k][mi] for k in ka]); vb = np.array([B[k][mi] for k in ka])
                if not np.allclose(va, vb) and stats.wilcoxon(va, vb).pvalue < 0.05 and \
                        np.sign((va - vb).mean()) == np.sign(diff.mean()):
                    sig_seeds += 1
            rows.append(dict(dataset=ds, subset=subset, A=a, B=b, note=note, metric=metric,
                             users=len(xa), mean_A=xa.mean(), mean_B=xb.mean(), diff=diff.mean(),
                             rel=(diff.mean() / xb.mean() if xb.mean() else float("nan")),
                             wilcoxon_p=pw, ttest_p=pt, seeds_sig=f"{sig_seeds}/{len(seeds)}"))
    for metric in ("NDCG@10", "P@10"):
        idx = [i for i, r in enumerate(rows) if r["metric"] == metric]
        adj = holm(np.array([rows[i]["wilcoxon_p"] for i in idx]))
        for i, p in zip(idx, adj):
            rows[i]["wilcoxon_p_holm"] = p
    with open(os.path.join(RES, "significance.csv"), "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader()
        for r in rows:
            w.writerow({k: (f"{v:.6g}" if isinstance(v, float) else v) for k, v in r.items()})
    return rows


def pfmt(p):
    return "<1e-10" if p < 1e-10 else (f"{p:.2e}" if p < 1e-3 else f"{p:.3f}")


def sig_table(rows, out):
    out.append("\n| 数据集 | 子集 | 对比（A vs B） | 指标 | 用户数 | A | B | 差值(相对) | Wilcoxon p | Holm 校正 p | 配对 t p | 单种子显著 |")
    out.append("|---|---|---|---|---|---|---|---|---|---|---|---|")
    for r in rows:
        star = "**" if r["wilcoxon_p_holm"] < 0.05 else ""
        out.append(f"| {DS[r['dataset']].split('（')[0]} | {'全体' if r['subset'] == 'all' else '冷启动'} | "
                   f"{SHORT.get(r['A'], r['A'])} vs {SHORT.get(r['B'], r['B'])} | {r['metric']} | {r['users']} | "
                   f"{r['mean_A']:.4f} | {r['mean_B']:.4f} | {r['diff']:+.4f}（{r['rel']:+.1%}） | {star}{pfmt(r['wilcoxon_p'])}{star} | "
                   f"{pfmt(r['wilcoxon_p_holm'])} | {pfmt(r['ttest_p'])} | {r['seeds_sig']} |")


def eta_csv(agg):
    with open(os.path.join(RES, "eta_sensitivity.csv"), "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["dataset", "subset", "eta", "N", "recall_mean", "recall_std", "ndcg_mean", "ndcg_std",
                    "coverage_mean", "coverage_std", "precision_mean", "precision_std"])
        for ds in ("campus", "ml100k_coldsim"):
            for subset in ("cold", "all"):
                for m in ETAS:
                    for n in (5, 10, 20):
                        a = agg.get((ds, subset, m, n))
                        if not a:
                            continue
                        row = [ds, subset, m.replace("hybrid_eta", ""), n]
                        for k in ("recall", "ndcg", "coverage", "precision"):
                            v = np.array(a[k]); row += [f"{v.mean():.6f}", f"{v.std(ddof=1):.6f}"]
                        w.writerow(row)


def stats_table(out):
    rows = list(csv.DictReader(open(os.path.join(RAW, "dataset_stats.csv"), encoding="utf-8")))
    out.append("\n| 数据集 | 用户 | 物品 | 记录 | 训练对(均值) | 测试对(均值) | 评估用户 | 冷启动用户(均值±标准差) | 每种子耗时(ms，均值) |")
    out.append("|---|---|---|---|---|---|---|---|---|")
    for ds in DS:
        rs = [r for r in rows if r["dataset"] == ds]
        if not rs:
            continue
        g = lambda k: np.array([float(r[k]) for r in rs])
        out.append(f"| {DS[ds]} | {rs[0]['users']} | {rs[0]['items']} | {rs[0]['records']} | {g('train_pairs').mean():.0f} | "
                   f"{g('test_pairs').mean():.0f} | {g('eval_users').mean():.0f} | {ms(g('cold_users'), 1)} | {g('eval_ms').mean():.0f} |")


def main():
    agg, users = load_metrics()
    write_summary(agg, users)
    eta_csv(agg)
    rows = significance(load_per_user())
    out = ["# 推荐实验结果表（由 scripts/aggregate.py 自动生成，数字来自 results/raw/ 的真实运行）", "",
           "> MovieLens-100K 为公开真实数据；**Campus-Sim 为模拟数据**，其数值不代表真实平台效果。", ""]
    out.append("## 0. 数据集与划分"); stats_table(out)
    out.append("\n## 1. 主表（N=10）")
    for ds in ("ml100k", "campus"):
        table(agg, users, ds, "all", MAIN, 10, out)
    out.append("\n## 2. 冷启动子集（训练集交互物品数 < 5，N=10）")
    out.append("\nMovieLens-100K 没有自然冷启动用户（每人 ≥ 20 条评分），因此用\"冷启动协议\"：每个种子随机抽取 20% 评估用户，"
               "只保留其 1~4 个训练物品（测试集不变），模型在截断后的训练集上重训。")
    for ds in ("ml100k_coldsim", "campus"):
        table(agg, users, ds, "cold", MAIN + (["hybrid_eta0.1_nodeclared"] if ds == "campus" else []), 10, out)
    out.append("\n## 3. 消融（N=10）")
    table(agg, users, "ml100k", "all", ["itemcf_pure", "abl_norm_only", "abl_iuf", "cf_weighted"], 10, out)
    for subset in ("all", "cold"):
        table(agg, users, "campus", subset, ["itemcf_pure", "abl_norm_only", "abl_w", "abl_w_iuf", "cf_weighted",
                                             "abl_linear_iuf_norm", "hybrid_eta0.0", "hybrid_eta0.1",
                                             "hybrid_eta0.1_nodeclared"], 10, out)
    out.append("\n## 4. 冷启动 CF 名额 η 敏感性（冷启动子集，N=10）")
    for ds in ("campus", "ml100k_coldsim"):
        table(agg, users, ds, "cold", ETAS, 10, out)
    out.append("\n## 5. 配对显著性检验（按用户，NDCG@10 与 P@10，双侧）")
    out.append("\n每个用户一个观测：在其属于该子集的各划分种子上取平均（5 个种子），A、B 为该指标的用户平均值。"
               "主检验为 Wilcoxon 符号秩检验（scipy.stats.wilcoxon，零差值剔除），同时给出配对 t 检验；"
               "Holm 校正在同一指标的全部对比上进行，校正后 p<0.05 的加粗。\"单种子显著\"= 逐种子单独做 Wilcoxon，p<0.05 且方向一致的种子数。")
    sig_table([r for r in rows if r["metric"] == "NDCG@10"], out)
    sig_table([r for r in rows if r["metric"] == "P@10"], out)
    out.append("\n## 附表 A：N=5 / N=20")
    for n in (5, 20):
        for ds, subset in (("ml100k", "all"), ("campus", "all"), ("ml100k_coldsim", "cold"), ("campus", "cold")):
            table(agg, users, ds, subset, MAIN, n, out)
    out.append("\n## 附表 B：MovieLens 冷启动协议下的全体用户（含被截断用户）")
    table(agg, users, "ml100k_coldsim", "all", MAIN, 10, out)
    with open(os.path.join(RES, "tables.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(out) + "\n")
    print("written:", ", ".join(["summary.csv", "eta_sensitivity.csv", "significance.csv", "tables.md"]))


if __name__ == "__main__":
    main()
