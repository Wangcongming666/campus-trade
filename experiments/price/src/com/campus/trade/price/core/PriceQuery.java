package com.campus.trade.price.core;

/**
 * 价格建议请求（与分类无关的部分；分类由调用方用来筛选样本）。
 *
 * @param condition     目标成色
 * @param originalPrice 原价，可为 null
 * @param title         标题，可为 null
 */
public record PriceQuery(String condition, Double originalPrice, String title) {
}
