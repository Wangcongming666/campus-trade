package com.campus.trade.recommend.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 物品相似度模型：每个物品只保存 TopK 个邻居（按相似度降序）。
 * 离线计算后可整体序列化到 Redis（每个物品一个 ZSET），在线只读。
 */
public final class ItemSimilarityModel {

    private final Map<Long, List<ScoredItem>> neighbors;
    private final int topK;

    public ItemSimilarityModel(Map<Long, List<ScoredItem>> neighbors, int topK) {
        this.neighbors = neighbors;
        this.topK = topK;
    }

    /** 物品的 TopK 邻居（降序）；不存在返回空列表。 */
    public List<ScoredItem> neighbors(long itemId) {
        return neighbors.getOrDefault(itemId, Collections.emptyList());
    }

    public Map<Long, List<ScoredItem>> asMap() {
        return Collections.unmodifiableMap(neighbors);
    }

    public int topK() {
        return topK;
    }

    public int itemCount() {
        return neighbors.size();
    }

    /** 截断为更小的 K（相似度本身与 K 无关，故可一次计算最大 K 后截断做参数敏感性实验）。 */
    public ItemSimilarityModel truncate(int k) {
        if (k >= topK) {
            return this;
        }
        Map<Long, List<ScoredItem>> m = new HashMap<>(neighbors.size() * 2);
        neighbors.forEach((id, list) -> m.put(id, List.copyOf(list.subList(0, Math.min(k, list.size())))));
        return new ItemSimilarityModel(m, k);
    }
}
