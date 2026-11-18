package com.campus.trade.ai.clip;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZeroShotScorerTest {

    private static CategoryEmbeddings.Entry e(long id, float... v) {
        return new CategoryEmbeddings.Entry(id, "c" + id, null, null, ZeroShotScorer.l2normalize(v));
    }

    @Test
    void topKSortedAndProbabilitiesSumToOne() {
        List<CategoryEmbeddings.Entry> cats = List.of(e(1, 1, 0, 0), e(2, 0, 1, 0), e(3, 0, 0, 1), e(4, 1, 1, 0));
        List<ZeroShotScorer.Scored> all = ZeroShotScorer.score(new float[]{0.9f, 0.3f, 0f}, cats, 100, 10);
        assertEquals(4, all.size());
        assertEquals(1L, all.get(0).category().categoryId());
        double sum = all.stream().mapToDouble(ZeroShotScorer.Scored::probability).sum();
        assertEquals(1.0, sum, 1e-9);
        for (int i = 1; i < all.size(); i++) {
            assertTrue(all.get(i - 1).probability() >= all.get(i).probability());
        }
        assertEquals(3, ZeroShotScorer.score(new float[]{0.9f, 0.3f, 0f}, cats, 100, 3).size());
    }

    @Test
    void temperatureSharpensDistribution() {
        List<CategoryEmbeddings.Entry> cats = List.of(e(1, 1, 0), e(2, 0, 1));
        float[] img = {0.6f, 0.4f};
        double soft = ZeroShotScorer.score(img, cats, 1, 1).get(0).probability();
        double sharp = ZeroShotScorer.score(img, cats, 100, 1).get(0).probability();
        assertTrue(sharp > soft);
        assertTrue(sharp > 0.99);
        // 大温度下不溢出
        assertFalse(Double.isNaN(ZeroShotScorer.score(img, cats, 1e6, 1).get(0).probability()));
    }

    @Test
    void emptyCandidatesAndDimensionMismatch() {
        assertTrue(ZeroShotScorer.score(new float[]{1, 0}, List.of(), 100, 3).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> ZeroShotScorer.score(new float[]{1, 0, 0}, List.of(e(1, 1, 0)), 100, 3));
    }
}
