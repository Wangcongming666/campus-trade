package com.campus.trade.recommend.core;

/**
 * 一条用户行为（对应 user_behavior 表的一行），纯 Java 值对象，与 Spring/MyBatis 解耦。
 *
 * @param userId     用户 ID
 * @param itemId     商品 ID（表字段 goods_id）
 * @param type       行为类型：1 浏览、2 收藏、3 聊天、4 下单（见 {@link BehaviorWeightConfig}）
 * @param timeMillis 行为发生时间（epoch 毫秒）
 */
public record Behavior(long userId, long itemId, int type, long timeMillis) {
}
