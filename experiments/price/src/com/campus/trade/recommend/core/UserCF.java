package com.campus.trade.recommend.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基于用户的协同过滤（UserCF，加权余弦相似度），作为离线实验对比基线。
 * <pre>
 *   w(u,v) = Σ_i r(u,i)·r(v,i) / (||r(u,·)||·||r(v,·)||)
 *   p(u,i) = Σ_{v∈S(u,K)∩N(i)} w(u,v)·r(v,i)
 * </pre>
 */
public final class UserCF {

    private final InteractionMatrix m;
    /** 每个用户下标的 TopK 邻居：[邻居下标数组, 相似度数组] */
    private final int[][] nbIdx;
    private final double[][] nbSim;

    private UserCF(InteractionMatrix m, int[][] nbIdx, double[][] nbSim) {
        this.m = m;
        this.nbIdx = nbIdx;
        this.nbSim = nbSim;
    }

    public static UserCF fit(InteractionMatrix m, int topK) {
        int nUsers = m.userCount();
        double[] norm = new double[nUsers];
        for (int u = 0; u < nUsers; u++) {
            double s = 0;
            for (double v : m.userValues(u)) {
                s += v * v;
            }
            norm[u] = Math.sqrt(s);
        }
        double[] scratch = new double[nUsers];
        int[] touched = new int[nUsers];
        int[][] nbIdx = new int[nUsers][];
        double[][] nbSim = new double[nUsers][];
        for (int u = 0; u < nUsers; u++) {
            int nt = 0;
            int[] items = m.userItems(u);
            double[] rv = m.userValues(u);
            for (int p = 0; p < items.length; p++) {
                int[] us = m.itemUsers(items[p]);
                double[] vs = m.itemValues(items[p]);
                for (int q = 0; q < us.length; q++) {
                    int v = us[q];
                    if (v == u) {
                        continue;
                    }
                    if (scratch[v] == 0) {
                        touched[nt++] = v;
                    }
                    scratch[v] += rv[p] * vs[q];
                }
            }
            TopK top = new TopK(topK);
            for (int t = 0; t < nt; t++) {
                int v = touched[t];
                top.offer(v, scratch[v] / (norm[u] * norm[v]));
                scratch[v] = 0;
            }
            List<ScoredItem> list = top.sorted();
            nbIdx[u] = list.stream().mapToInt(s -> (int) s.itemId()).toArray();
            nbSim[u] = list.stream().mapToDouble(ScoredItem::score).toArray();
        }
        return new UserCF(m, nbIdx, nbSim);
    }

    /** 截断邻居数（相似度与 K 无关，可一次计算最大 K）。 */
    public UserCF truncate(int k) {
        int[][] ni = new int[nbIdx.length][];
        double[][] ns = new double[nbSim.length][];
        for (int u = 0; u < nbIdx.length; u++) {
            int len = Math.min(k, nbIdx[u].length);
            ni[u] = Arrays.copyOf(nbIdx[u], len);
            ns[u] = Arrays.copyOf(nbSim[u], len);
        }
        return new UserCF(m, ni, ns);
    }

    public List<ScoredItem> recommend(long userId, int n, Set<Long> exclude) {
        Integer u = m.userIndex(userId);
        if (u == null) {
            return List.of();
        }
        Set<Integer> seen = new java.util.HashSet<>();
        for (int i : m.userItems(u)) {
            seen.add(i);
        }
        Map<Integer, Double> acc = new HashMap<>();
        for (int p = 0; p < nbIdx[u].length; p++) {
            int v = nbIdx[u][p];
            double w = nbSim[u][p];
            int[] items = m.userItems(v);
            double[] vals = m.userValues(v);
            for (int q = 0; q < items.length; q++) {
                if (!seen.contains(items[q])) {
                    acc.merge(items[q], w * vals[q], Double::sum);
                }
            }
        }
        TopK top = new TopK(n);
        for (Map.Entry<Integer, Double> e : acc.entrySet()) {
            long id = m.itemId(e.getKey());
            if (exclude == null || !exclude.contains(id)) {
                top.offer(id, e.getValue());
            }
        }
        return new ArrayList<>(top.sorted());
    }

    /**
     * 与 {@link #recommend} 排序完全相同，只是包装成 {@link RecResult}（来源 USER_CF）。
     * UserCF 的得分来自相似用户而非用户自己的某件商品，没有自然的"种子商品"，因此 reasonItemId 为 null，
     * 线上理由文案用"和你兴趣相似的同学也看过"。
     */
    public List<RecResult> recommendResults(long userId, int n, Set<Long> exclude) {
        List<RecResult> out = new ArrayList<>();
        for (ScoredItem s : recommend(userId, n, exclude)) {
            out.add(new RecResult(s.itemId(), s.score(), RecSource.USER_CF, null));
        }
        return out;
    }
}
