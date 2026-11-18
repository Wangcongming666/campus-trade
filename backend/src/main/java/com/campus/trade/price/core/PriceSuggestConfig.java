package com.campus.trade.price.core;

/**
 * 价格建议参数（不可变，with 方法返回副本，便于离线消融实验）。
 *
 * @param factors          成色系数（先验/默认值）
 * @param soldWeight       已成交样本权重
 * @param listedWeight     在售挂牌样本权重（挂牌价通常高于成交价，可信度低）
 * @param titleGamma       标题相似加权 γ：w ← w·(1+γ·cos)
 * @param iqrK             IQR 去异常倍数 k：保留 [Q1−k·IQR, Q3+k·IQR]
 * @param minSamples       样本数阈值：少于该值时主要依赖原价折算
 * @param blendKappa       样本数平滑常数 κ：λ_n = n/(n+κ)
 * @param anchorBand       原价折算价的默认区间半宽（±比例，残差样本不足时使用）
 * @param estimateFactors  是否用同类带原价样本校准成色系数（以 factors 为先验收缩）
 * @param factorPrior      成色系数收缩的先验强度 m
 * @param conditionAlign   消融开关：成色对齐
 * @param titleWeighting   消融开关：标题相似加权
 * @param iqrFilter        消融开关：IQR 去异常
 * @param blendOriginal    消融开关：融合原价
 * @param dispersionBlend  融合权重是否按离散度（逆方差）调整；false 时只用 λ_n
 */
public record PriceSuggestConfig(
        ConditionFactors factors, double soldWeight, double listedWeight, double titleGamma, double iqrK,
        int minSamples, double blendKappa, double anchorBand, boolean estimateFactors, double factorPrior,
        boolean conditionAlign, boolean titleWeighting, boolean iqrFilter, boolean blendOriginal, boolean dispersionBlend) {

    public static PriceSuggestConfig defaults() {
        return new PriceSuggestConfig(ConditionFactors.defaults(), 1.0, 0.4, 2.0, 1.5, 5, 5.0, 0.15, true, 10,
                true, true, true, true, true);
    }

    public PriceSuggestConfig withFactors(ConditionFactors f) {
        return new PriceSuggestConfig(f, soldWeight, listedWeight, titleGamma, iqrK, minSamples, blendKappa, anchorBand,
                estimateFactors, factorPrior, conditionAlign, titleWeighting, iqrFilter, blendOriginal, dispersionBlend);
    }

    public PriceSuggestConfig withEstimateFactors(boolean v) {
        return new PriceSuggestConfig(factors, soldWeight, listedWeight, titleGamma, iqrK, minSamples, blendKappa, anchorBand,
                v, factorPrior, conditionAlign, titleWeighting, iqrFilter, blendOriginal, dispersionBlend);
    }

    public PriceSuggestConfig withConditionAlign(boolean v) {
        return new PriceSuggestConfig(factors, soldWeight, listedWeight, titleGamma, iqrK, minSamples, blendKappa, anchorBand,
                estimateFactors, factorPrior, v, titleWeighting, iqrFilter, blendOriginal, dispersionBlend);
    }

    public PriceSuggestConfig withTitleWeighting(boolean v) {
        return new PriceSuggestConfig(factors, soldWeight, listedWeight, titleGamma, iqrK, minSamples, blendKappa, anchorBand,
                estimateFactors, factorPrior, conditionAlign, v, iqrFilter, blendOriginal, dispersionBlend);
    }

    public PriceSuggestConfig withIqrFilter(boolean v) {
        return new PriceSuggestConfig(factors, soldWeight, listedWeight, titleGamma, iqrK, minSamples, blendKappa, anchorBand,
                estimateFactors, factorPrior, conditionAlign, titleWeighting, v, blendOriginal, dispersionBlend);
    }

    public PriceSuggestConfig withBlendOriginal(boolean v) {
        return new PriceSuggestConfig(factors, soldWeight, listedWeight, titleGamma, iqrK, minSamples, blendKappa, anchorBand,
                estimateFactors, factorPrior, conditionAlign, titleWeighting, iqrFilter, v, dispersionBlend);
    }

    public PriceSuggestConfig withDispersionBlend(boolean v) {
        return new PriceSuggestConfig(factors, soldWeight, listedWeight, titleGamma, iqrK, minSamples, blendKappa, anchorBand,
                estimateFactors, factorPrior, conditionAlign, titleWeighting, iqrFilter, blendOriginal, v);
    }

    public PriceSuggestConfig withWeights(double sold, double listed) {
        return new PriceSuggestConfig(factors, sold, listed, titleGamma, iqrK, minSamples, blendKappa, anchorBand,
                estimateFactors, factorPrior, conditionAlign, titleWeighting, iqrFilter, blendOriginal, dispersionBlend);
    }

    public PriceSuggestConfig withParams(double titleGamma, double blendKappa, int minSamples) {
        return new PriceSuggestConfig(factors, soldWeight, listedWeight, titleGamma, iqrK, minSamples, blendKappa, anchorBand,
                estimateFactors, factorPrior, conditionAlign, titleWeighting, iqrFilter, blendOriginal, dispersionBlend);
    }
}
