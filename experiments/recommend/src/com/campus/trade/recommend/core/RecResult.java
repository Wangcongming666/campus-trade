package com.campus.trade.recommend.core;

/**
 * 推荐结果。
 *
 * @param itemId           被推荐的商品
 * @param score            推荐分（同一来源内可比较）
 * @param source           来源通道（即该候选实际命中的通道，用于推荐理由）
 * @param reasonItemId     推荐理由的种子商品：对该候选得分贡献最大的用户历史商品（ITEM_CF / CONTENT 有效，否则为 null）
 * @param reasonCategoryId 推荐理由的分类：冷启动时该候选来自哪个兴趣分类的热度榜（COLD_START 有效，否则为 null）
 */
public record RecResult(long itemId, double score, RecSource source, Long reasonItemId, Long reasonCategoryId) {

    public RecResult(long itemId, double score, RecSource source, Long reasonItemId) {
        this(itemId, score, source, reasonItemId, null);
    }
}
