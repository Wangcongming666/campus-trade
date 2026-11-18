package com.campus.trade.recommend.core;

import java.util.HashMap;
import java.util.Map;

/**
 * 稀疏用户-物品交互矩阵（CSR + CSC 双向索引），ID 映射为连续下标。
 * 同时保存按用户（行）和按物品（列）的邻接表，供 ItemCF / UserCF 高效计算。
 */
public final class InteractionMatrix {

    private final long[] userIds;
    private final long[] itemIds;
    private final Map<Long, Integer> userIndex;
    private final Map<Long, Integer> itemIndex;
    private final int[][] userItems;
    private final double[][] userValues;
    private final int[][] itemUsers;
    private final double[][] itemValues;

    private InteractionMatrix(long[] userIds, long[] itemIds, Map<Long, Integer> userIndex, Map<Long, Integer> itemIndex,
                              int[][] userItems, double[][] userValues, int[][] itemUsers, double[][] itemValues) {
        this.userIds = userIds;
        this.itemIds = itemIds;
        this.userIndex = userIndex;
        this.itemIndex = itemIndex;
        this.userItems = userItems;
        this.userValues = userValues;
        this.itemUsers = itemUsers;
        this.itemValues = itemValues;
    }

    /** 由 user → (item → 兴趣分) 构建；ID 按升序编号，保证结果确定。 */
    public static InteractionMatrix of(Map<Long, Map<Long, Double>> interest) {
        long[] users = interest.keySet().stream().mapToLong(Long::longValue).sorted().toArray();
        long[] items = interest.values().stream().flatMap(m -> m.keySet().stream())
                .mapToLong(Long::longValue).distinct().sorted().toArray();
        Map<Long, Integer> uIdx = new HashMap<>(users.length * 2);
        Map<Long, Integer> iIdx = new HashMap<>(items.length * 2);
        for (int i = 0; i < users.length; i++) {
            uIdx.put(users[i], i);
        }
        for (int i = 0; i < items.length; i++) {
            iIdx.put(items[i], i);
        }
        int[][] ui = new int[users.length][];
        double[][] uv = new double[users.length][];
        int[] itemDeg = new int[items.length];
        for (int u = 0; u < users.length; u++) {
            Map<Long, Double> row = interest.get(users[u]);
            int[] idx = new int[row.size()];
            double[] val = new double[row.size()];
            int p = 0;
            for (Map.Entry<Long, Double> e : row.entrySet()) {
                idx[p] = iIdx.get(e.getKey());
                val[p] = e.getValue();
                itemDeg[idx[p]]++;
                p++;
            }
            ui[u] = idx;
            uv[u] = val;
        }
        int[][] iu = new int[items.length][];
        double[][] iv = new double[items.length][];
        for (int i = 0; i < items.length; i++) {
            iu[i] = new int[itemDeg[i]];
            iv[i] = new double[itemDeg[i]];
        }
        int[] fill = new int[items.length];
        for (int u = 0; u < users.length; u++) {
            for (int p = 0; p < ui[u].length; p++) {
                int i = ui[u][p];
                iu[i][fill[i]] = u;
                iv[i][fill[i]] = uv[u][p];
                fill[i]++;
            }
        }
        return new InteractionMatrix(users, items, uIdx, iIdx, ui, uv, iu, iv);
    }

    public int userCount() { return userIds.length; }
    public int itemCount() { return itemIds.length; }
    public long userId(int idx) { return userIds[idx]; }
    public long itemId(int idx) { return itemIds[idx]; }
    public Integer userIndex(long userId) { return userIndex.get(userId); }
    public Integer itemIndex(long itemId) { return itemIndex.get(itemId); }
    public int[] userItems(int u) { return userItems[u]; }
    public double[] userValues(int u) { return userValues[u]; }
    public int[] itemUsers(int i) { return itemUsers[i]; }
    public double[] itemValues(int i) { return itemValues[i]; }
    public long nnz() {
        long n = 0;
        for (int[] r : userItems) {
            n += r.length;
        }
        return n;
    }
}
