package com.campus.trade.recommend.core;

/** 推荐结果来源，便于前端展示推荐理由和线上统计各通道效果。 */
public enum RecSource {
    /** 基于物品的协同过滤（ItemCF-IUF），reasonItemId = 贡献最大的种子商品 */
    ITEM_CF,
    /** 基于用户的协同过滤（目前仅离线实验使用），无种子商品，理由为"相似用户也看过" */
    USER_CF,
    /** 内容相似（分类 + 标题 TF-IDF），主要服务新商品，reasonItemId = 贡献最大的画像商品 */
    CONTENT,
    /** 冷启动：兴趣分类热度，reasonCategoryId = 命中的兴趣分类 */
    COLD_START,
    /** 全局热度补齐 */
    HOT
}
