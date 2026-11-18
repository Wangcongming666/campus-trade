package com.campus.trade.recommend.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongPredicate;

/**
 * 基于物品的协同过滤（ItemCF / ItemCF-IUF），纯 Java 实现。
 *
 * <pre>
 * 相似度（加权余弦 + IUF）：
 *   w(i,j) = Σ_{u∈N(i)∩N(j)} r(u,i)·r(u,j) / log(1+|N(u)|)  /  ( ||r(·,i)|| · ||r(·,j)|| )
 *   iuf=false 时分子不除 log(1+|N(u)|)，即普通加权余弦。
 * 归一化（按行最大值）：w'(i,j) = w(i,j) / max_j w(i,j)
 * 只保留每个物品 TopK 个邻居。
 * 打分：p(u,j) = Σ_{i∈N(u)∩S(j,K)} w'(i,j) · r(u,i)
 * </pre>
 * 计算方式：对每个物品 i，遍历 N(i) 中用户的交互物品累加到稠密 scratch 数组，
 * 复杂度 O(Σ_u |N(u)|²)，内存只需 O(|I|·K)，5000 商品级别秒级完成。
 */
public final class ItemCF {

    private ItemCF() {
    }

    /**
     * 离线训练相似度矩阵。
     *
     * @param m         交互矩阵
     * @param topK      每个物品保留的邻居数 K
     * @param iuf       是否使用 IUF（惩罚活跃用户）
     * @param normalize 是否按行最大值归一化
     */
    public static ItemSimilarityModel fit(InteractionMatrix m, int topK, boolean iuf, boolean normalize) {
        int nItems = m.itemCount();
        int nUsers = m.userCount();
        double[] norm = new double[nItems];
        for (int i = 0; i < nItems; i++) {
            double s = 0;
            for (double v : m.itemValues(i)) {
                s += v * v;
            }
            norm[i] = Math.sqrt(s);
        }
        double[] userPenalty = new double[nUsers];
        for (int u = 0; u < nUsers; u++) {
            userPenalty[u] = iuf ? 1.0 / Math.log(1.0 + m.userItems(u).length) : 1.0;
        }
        double[] scratch = new double[nItems];
        int[] touched = new int[nItems];
        Map<Long, List<ScoredItem>> neighbors = new HashMap<>(nItems * 2);
        for (int i = 0; i < nItems; i++) {
            int nt = 0;
            int[] users = m.itemUsers(i);
            double[] rui = m.itemValues(i);
            for (int p = 0; p < users.length; p++) {
                int u = users[p];
                double base = rui[p] * userPenalty[u];
                int[] items = m.userItems(u);
                double[] vals = m.userValues(u);
                for (int q = 0; q < items.length; q++) {
                    int j = items[q];
                    if (j == i) {
                        continue;
                    }
                    if (scratch[j] == 0) {
                        touched[nt++] = j;
                    }
                    scratch[j] += base * vals[q];
                }
            }
            TopK top = new TopK(topK);
            for (int t = 0; t < nt; t++) {
                int j = touched[t];
                double sim = scratch[j] / (norm[i] * norm[j]);
                top.offer(m.itemId(j), sim);
                scratch[j] = 0;
            }
            List<ScoredItem> list = top.sorted();
            if (normalize && !list.isEmpty()) {
                double max = list.get(0).score();
                if (max > 0) {
                    List<ScoredItem> nl = new ArrayList<>(list.size());
                    for (ScoredItem s : list) {
                        nl.add(new ScoredItem(s.itemId(), s.score() / max));
                    }
                    list = nl;
                }
            }
            if (!list.isEmpty()) {
                neighbors.put(m.itemId(i), List.copyOf(list));
            }
        }
        return new ItemSimilarityModel(neighbors, topK);
    }

    /**
     * 为用户生成 TopN 推荐。
     *
     * @param model           相似度模型
     * @param userInterest    用户历史兴趣 item → r(u,i)
     * @param n               推荐个数
     * @param exclude         需排除的物品（已交互、已售出等），可为 null
     * @param candidateFilter 候选过滤（例如只保留在售商品），可为 null
     */
    public static List<RecResult> recommend(ItemSimilarityModel model, Map<Long, Double> userInterest, int n,
                                            Set<Long> exclude, LongPredicate candidateFilter) {
        Map<Long, Acc> acc = new HashMap<>();
        for (Map.Entry<Long, Double> e : userInterest.entrySet()) {
            long i = e.getKey();
            double r = e.getValue();
            for (ScoredItem nb : model.neighbors(i)) {
                long j = nb.itemId();
                if (userInterest.containsKey(j) || (exclude != null && exclude.contains(j))) {
                    continue;
                }
                double c = nb.score() * r;
                Acc a = acc.computeIfAbsent(j, k -> new Acc());
                a.score += c;
                if (c > a.best) {
                    a.best = c;
                    a.reason = i;
                }
            }
        }
        List<Map.Entry<Long, Acc>> entries = new ArrayList<>(acc.entrySet());
        entries.sort((x, y) -> {
            int c = Double.compare(y.getValue().score, x.getValue().score);
            return c != 0 ? c : Long.compare(x.getKey(), y.getKey());
        });
        List<RecResult> out = new ArrayList<>(n);
        for (Map.Entry<Long, Acc> e : entries) {
            if (out.size() >= n) {
                break;
            }
            if (candidateFilter != null && !candidateFilter.test(e.getKey())) {
                continue;
            }
            out.add(new RecResult(e.getKey(), e.getValue().score, RecSource.ITEM_CF, e.getValue().reason));
        }
        return out;
    }

    /** 打分累加器（reason 用 long 保存，避免雪花 ID 转 double 丢精度）。 */
    private static final class Acc {
        double score;
        double best = -1;
        long reason;
    }
}
