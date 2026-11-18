package com.campus.trade.price.core;

/**
 * 价格样本（同分类的历史商品）。
 *
 * @param goodsId       商品 ID
 * @param price         价格：已售商品用成交价（trade_order.price 快照），在售商品用挂牌价
 * @param condition     成色 NEW / LIKE_NEW / GOOD / FAIR / POOR
 * @param originalPrice 原价，可为 null
 * @param title         标题，可为 null
 * @param sold          true=已成交样本（权重高），false=在售挂牌样本（权重低）
 */
public record PriceSample(long goodsId, double price, String condition, Double originalPrice, String title, boolean sold) {
}
