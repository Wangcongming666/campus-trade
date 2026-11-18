package com.campus.trade.price.core;

/**
 * 价格建议结果（未取整，调用方转 BigDecimal 两位小数）。
 *
 * @param suggested   建议价
 * @param min         区间下限（加权 P25，与原价折算融合）
 * @param max         区间上限（加权 P75，与原价折算融合）
 * @param sampleCount 实际参与计算的样本数（去异常值后）
 * @param basis       主要依据
 * @param reason      中文说明
 */
public record PriceSuggestion(double suggested, double min, double max, int sampleCount, Basis basis, String reason) {

    public enum Basis {
        /** 同类样本为主（可能融合原价） */
        SAMPLES,
        /** 样本不足，主要依赖原价×成色系数 */
        ORIGINAL_PRICE,
        /** 无原价且无同类样本：分类整体中位数 */
        CATEGORY_MEDIAN,
        /** 什么都没有 */
        NONE
    }
}
