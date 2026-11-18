package com.campus.trade.price.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PriceSuggesterTest {

    private static final double EPS = 1e-9;

    private static PriceSample sold(long id, double price, String cond) {
        return new PriceSample(id, price, cond, null, "商品" + id, true);
    }

    @Test
    void weightedQuantileMatchesMedianWhenEqualWeights() {
        double[] v = {5, 1, 3, 2, 4};
        double[] w = {1, 1, 1, 1, 1};
        assertEquals(3, WeightedStats.weightedQuantile(v, w, 0.5), EPS);
        double[] v2 = {1, 2, 3, 4};
        double[] w2 = {1, 1, 1, 1};
        assertEquals(2.5, WeightedStats.weightedQuantile(v2, w2, 0.5), EPS);
        // 权重集中在 4 上：中位数被拉到 4
        double[] w3 = {1, 1, 1, 10};
        assertEquals(4, WeightedStats.weightedQuantile(v2, w3, 0.5), EPS);
        assertEquals(2.5, WeightedStats.quantile(List.of(1.0, 2.0, 3.0, 4.0), 0.5), EPS);
    }

    @Test
    void conditionAlignmentAndIqrRemovesOutlier() {
        List<PriceSample> s = new ArrayList<>();
        // 全新 100 元 → 对齐到 GOOD(0.7) = 70；GOOD 70 不变
        for (int i = 0; i < 5; i++) {
            s.add(sold(i, 100, "NEW"));
            s.add(sold(10 + i, 70, "GOOD"));
        }
        s.add(sold(99, 7000, "GOOD")); // 录入错误，应被 IQR 剔除
        PriceSuggester ps = new PriceSuggester(PriceSuggestConfig.defaults());
        PriceSuggestion r = ps.suggest(new PriceQuery("GOOD", null, null), s, null);
        assertEquals(70, r.suggested(), 1e-6);
        assertEquals(10, r.sampleCount());
        assertEquals(70, r.min(), 1e-6);
        assertEquals(70, r.max(), 1e-6);
        assertEquals(PriceSuggestion.Basis.SAMPLES, r.basis());
        assertTrue(r.reason().contains("剔除 1 个异常价"), r.reason());

        // 关闭成色对齐时，NEW 的 100 元直接参与 → 中位数在 70~100 之间
        PriceSuggestion raw = new PriceSuggester(PriceSuggestConfig.defaults().withConditionAlign(false))
                .suggest(new PriceQuery("GOOD", null, null), s, null);
        assertEquals(85, raw.suggested(), 1e-6);
    }

    @Test
    void listedSamplesHaveLowerWeight() {
        List<PriceSample> s = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            s.add(sold(i, 50, "GOOD"));
        }
        for (int i = 0; i < 4; i++) {
            s.add(new PriceSample(100 + i, 80, "GOOD", null, null, false));
        }
        // 已成交权重 3×1.0=3 > 在售 4×0.4=1.6 → 中位数取成交价 50
        PriceSuggestion r = new PriceSuggester(PriceSuggestConfig.defaults()).suggest(new PriceQuery("GOOD", null, null), s, null);
        assertEquals(50, r.suggested(), 1e-6);
        // 等权时在售更多 → 80
        PriceSuggestion eq = new PriceSuggester(PriceSuggestConfig.defaults().withWeights(1, 1))
                .suggest(new PriceQuery("GOOD", null, null), s, null);
        assertEquals(80, eq.suggested(), 1e-6);
    }

    @Test
    void titleSimilarityPullsTowardsSimilarGoods() {
        List<PriceSample> s = new ArrayList<>();
        s.add(new PriceSample(1, 3000, "GOOD", null, "iPhone 13 128G 蓝色", true));
        s.add(new PriceSample(2, 3100, "GOOD", null, "iPhone 13 256G 白色", true));
        s.add(new PriceSample(3, 600, "GOOD", null, "小米 红米 Note 手机", true));
        s.add(new PriceSample(4, 650, "GOOD", null, "荣耀 畅玩 手机", true));
        s.add(new PriceSample(5, 700, "GOOD", null, "OPPO A 系列 手机", true));
        PriceQuery q = new PriceQuery("GOOD", null, "iPhone 13 128G 白色");
        double withTitle = new PriceSuggester(PriceSuggestConfig.defaults().withIqrFilter(false)).suggest(q, s, null).suggested();
        double noTitle = new PriceSuggester(PriceSuggestConfig.defaults().withIqrFilter(false).withTitleWeighting(false))
                .suggest(q, s, null).suggested();
        assertEquals(700, noTitle, 1e-6);
        assertTrue(withTitle >= 3000, "title weighting should favour iPhone samples, got " + withTitle);
    }

    @Test
    void fewSamplesRelyOnOriginalPriceAndNeverExceedIt() {
        List<PriceSample> s = List.of(sold(1, 900, "GOOD"), sold(2, 950, "GOOD"));
        PriceSuggestion r = new PriceSuggester(PriceSuggestConfig.defaults().withDispersionBlend(false))
                .suggest(new PriceQuery("GOOD", 1000.0, null), s, null);
        // n=2, λ=2/7；anchor=700；P = 2/7·925 + 5/7·700
        double expect = 2.0 / 7 * 925 + 5.0 / 7 * 700;
        assertEquals(expect, r.suggested(), 1e-6);
        assertEquals(PriceSuggestion.Basis.ORIGINAL_PRICE, r.basis());
        assertTrue(r.reason().contains("主要依据原价折算"), r.reason());
        assertTrue(r.min() <= r.suggested() && r.suggested() <= r.max() && r.max() <= 1000);

        // 样本价远高于原价：建议价与上限被原价截断
        List<PriceSample> high = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            high.add(sold(i, 5000 + i, "NEW"));
        }
        PriceSuggestion capped = new PriceSuggester(PriceSuggestConfig.defaults())
                .suggest(new PriceQuery("NEW", 1000.0, null), high, null);
        assertEquals(1000, capped.suggested(), 1e-6);
        assertEquals(1000, capped.max(), 1e-6);
        assertTrue(capped.min() <= capped.suggested());
    }

    @Test
    void dispersionBlendTrustsAccurateOriginalPrice() {
        // 同类样本价位差异很大（10~1000），但"原价×成色系数"几乎完全准确 → 应主要相信原价折算
        List<PriceSample> s = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            double orig = 10 * Math.pow(1.13, i);
            s.add(new PriceSample(i, orig * 0.7 * (1 + (i % 3 - 1) * 0.01), "GOOD", orig, "x" + i, true));
        }
        PriceSuggestConfig cfg = PriceSuggestConfig.defaults().withEstimateFactors(false);
        PriceSuggestion disp = new PriceSuggester(cfg).suggest(new PriceQuery("GOOD", 200.0, null), s, null);
        PriceSuggestion byN = new PriceSuggester(cfg.withDispersionBlend(false)).suggest(new PriceQuery("GOOD", 200.0, null), s, null);
        assertEquals(140, disp.suggested(), 3.0);
        assertTrue(Math.abs(byN.suggested() - 140) > 20, "count-only blending ignores the accurate anchor: " + byN.suggested());
        assertEquals(PriceSuggestion.Basis.ORIGINAL_PRICE, disp.basis());
        assertTrue(disp.min() <= disp.suggested() && disp.suggested() <= disp.max());
    }

    @Test
    void factorEstimationCorrectsAnchor() {
        // 该类目实际残值只有原价的一半（GOOD）
        List<PriceSample> s = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            s.add(new PriceSample(i, 50, "GOOD", 100.0, null, true));
        }
        PriceQuery q = new PriceQuery("GOOD", 100.0, null);
        PriceSuggestion est = new PriceSuggester(PriceSuggestConfig.defaults().withBlendOriginal(true))
                .suggest(q, List.of(s.get(0), s.get(1), s.get(2)), null); // 3 个样本 → 估计 (3·0.5+10·0.7)/13
        double f = (3 * 0.5 + 10 * 0.7) / 13;
        assertTrue(est.reason().contains("已按同类样本校准"), est.reason());
        assertTrue(est.suggested() < 70 && est.suggested() >= 50, "got " + est.suggested() + " f=" + f);
        PriceSuggestion raw = new PriceSuggester(PriceSuggestConfig.defaults().withEstimateFactors(false))
                .suggest(q, List.of(s.get(0), s.get(1), s.get(2)), null);
        assertTrue(raw.suggested() > est.suggested());
    }

    @Test
    void onlyOriginalPrice() {
        PriceSuggestion r = new PriceSuggester(PriceSuggestConfig.defaults())
                .suggest(new PriceQuery("LIKE_NEW", 200.0, null), List.of(), 999.0);
        assertEquals(170, r.suggested(), 1e-6);
        assertEquals(170 * 0.85, r.min(), 1e-6);
        assertEquals(195.5, r.max(), 1e-6);
        assertEquals(0, r.sampleCount());
        assertEquals(PriceSuggestion.Basis.ORIGINAL_PRICE, r.basis());
    }

    @Test
    void noSamplesNoOriginalFallsBackToCategoryMedian() {
        PriceSuggester ps = new PriceSuggester(PriceSuggestConfig.defaults());
        PriceSuggestion r = ps.suggest(new PriceQuery("GOOD", null, "随便"), List.of(), 88.0);
        assertEquals(88, r.suggested(), 1e-6);
        assertEquals(PriceSuggestion.Basis.CATEGORY_MEDIAN, r.basis());
        assertTrue(r.reason().contains("中位数"));
        PriceSuggestion none = ps.suggest(new PriceQuery("GOOD", null, null), List.of(), null);
        assertEquals(PriceSuggestion.Basis.NONE, none.basis());
    }

    @Test
    void estimateConditionFactorsShrinksToPrior() {
        List<PriceSample> s = new ArrayList<>();
        for (int i = 0; i < 90; i++) {
            s.add(new PriceSample(i, 50, "GOOD", 100.0, null, true)); // 观测 0.5
        }
        s.add(new PriceSample(1000, 500, "NEW", 100.0, null, true)); // 比值 5 > 1.5，视为录入错误，忽略
        ConditionFactors est = ConditionFactors.defaults().estimate(s, 10);
        assertEquals((90 * 0.5 + 10 * 0.7) / 100, est.of("GOOD"), 1e-9);
        assertEquals(1.0, est.of("NEW"), 1e-9);
        assertEquals(0.7 * 1, ConditionFactors.defaults().of("UNKNOWN"), 1e-9);
        assertEquals(5, est.asMap().size());
        assertEquals(Map.of("NEW", 1.0, "LIKE_NEW", 0.85, "GOOD", 0.7, "FAIR", 0.55, "POOR", 0.4),
                ConditionFactors.defaults().asMap());
    }
}
