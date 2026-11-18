package com.campus.trade.recommend.core;

/** 带分数的物品（相似邻居或推荐结果）。 */
public record ScoredItem(long itemId, double score) {
}
