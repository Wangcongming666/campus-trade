package com.campus.trade.recommend.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HybridAndContentTest {

    @Test
    void tokenizerMakesBigrams() {
        assertEquals(List.of("高等", "等数", "数学", "iphone13"), TextTokenizer.tokenize("九成新 高等数学 iPhone13"));
    }

    @Test
    void contentSimilarityPrefersSameCategoryAndKeywords() {
        var docs = List.of(
                new ContentSimilarity.Doc(1, 1, "高等数学 同济第七版"),
                new ContentSimilarity.Doc(2, 1, "高等数学 习题册"),
                new ContentSimilarity.Doc(3, 1, "大学英语 四级"),
                new ContentSimilarity.Doc(4, 2, "小米 手机"));
        var cs = ContentSimilarity.build(docs, 0.3);
        var sim = cs.similar(1, 3, null);
        assertEquals(2, sim.get(0).itemId());
        assertEquals(3, sim.get(1).itemId());
        // 新商品（不在语料中）
        var probe = new ContentSimilarity.Doc(99, 2, "全新 小米手机 充电器");
        assertEquals(4, cs.similarTo(probe, 1, null, null).get(0).itemId());
    }

    @Test
    void coldUserGetsInterestCategoryThenHot() {
        List<Behavior> bs = new ArrayList<>();
        for (int u = 0; u < 10; u++) {
            bs.add(new Behavior(u, 100, 1, 0)); // 分类1 最热
            if (u < 5) bs.add(new Behavior(u, 200, 1, 0)); // 分类2
        }
        Map<Long, Long> cat = Map.of(100L, 1L, 200L, 2L);
        var hot = HotRanker.build(bs, BehaviorWeightConfig.defaults(), 0, cat);
        var cold = new ColdStartRecommender(hot, 0.5);
        var model = ItemCF.fit(InteractionMatrix.of(BehaviorWeightConfig.defaults().aggregate(bs, 0)), 10, true, true);
        var hybrid = new HybridRecommender(model, hot, cold, 3, cat::get);
        var recs = hybrid.recommend(Map.of(), List.of(2L), 2, null, null, null, null);
        assertEquals(200, recs.get(0).itemId());
        assertEquals(RecSource.COLD_START, recs.get(0).source());
        assertEquals(100, recs.get(1).itemId());
        assertEquals(RecSource.HOT, recs.get(1).source());
        // 推荐理由：冷启动结果记录命中的兴趣分类，热度结果没有
        assertEquals(2L, recs.get(0).reasonCategoryId());
        assertNull(recs.get(1).reasonCategoryId());
        assertNull(recs.get(1).reasonItemId());
    }

    @Test
    void userCfResultsKeepRankingAndHaveNoSeed() {
        List<Behavior> bs = new ArrayList<>();
        long[][] data = {{1, 10}, {1, 11}, {2, 10}, {2, 11}, {2, 12}, {3, 10}, {3, 13}};
        for (long[] d : data) {
            bs.add(new Behavior(d[0], d[1], 1, 0));
        }
        var ucf = UserCF.fit(InteractionMatrix.of(BehaviorWeightConfig.defaults().aggregate(bs, 0)), 10);
        var plain = ucf.recommend(1, 5, null);
        var wrapped = ucf.recommendResults(1, 5, null);
        assertEquals(plain.size(), wrapped.size());
        for (int i = 0; i < plain.size(); i++) {
            assertEquals(plain.get(i).itemId(), wrapped.get(i).itemId());
            assertEquals(RecSource.USER_CF, wrapped.get(i).source());
            assertNull(wrapped.get(i).reasonItemId());
        }
    }

    @Test
    void coldUserWithSeedsGetsCfQuotaThenInterest() {
        // 用户 9 只看过商品 100（冷启动）；100 与 300 共现，300 是 ItemCF 邻居
        List<Behavior> bs = new ArrayList<>();
        for (int u = 0; u < 5; u++) {
            bs.add(new Behavior(u, 100, 1, 0));
            bs.add(new Behavior(u, 300, 1, 0));
            bs.add(new Behavior(u, 200, 1, 0));
        }
        bs.add(new Behavior(9, 100, 1, 0));
        Map<Long, Long> cat = Map.of(100L, 1L, 200L, 2L, 300L, 1L, 400L, 3L);
        var hot = HotRanker.build(bs, BehaviorWeightConfig.defaults(), 0, cat);
        var model = ItemCF.fit(InteractionMatrix.of(BehaviorWeightConfig.defaults().aggregate(bs, 0)), 10, true, true);
        Map<Long, Double> interest = Map.of(100L, 1.0);

        // coldCfRatio = 0：与旧行为一致，不出 CF
        var noCf = new HybridRecommender(model, hot, new ColdStartRecommender(hot, 0.8), 3, cat::get);
        assertTrue(noCf.recommend(interest, List.of(2L), 2, null, null, null, null).stream()
                .noneMatch(r -> r.source() == RecSource.ITEM_CF));

        var hybrid = new HybridRecommender(model, hot, new ColdStartRecommender(hot, 0.8), 3, cat::get, 0.1);
        var recs = hybrid.recommend(interest, List.of(2L), 2, null, null, null, null);
        assertEquals(2, recs.size());
        assertEquals(RecSource.ITEM_CF, recs.get(0).source());
        assertEquals(100L, recs.get(0).reasonItemId());
        assertNotEquals(RecSource.ITEM_CF, recs.get(1).source());
        assertNotEquals(recs.get(0).itemId(), recs.get(1).itemId());

        // 没有 ItemCF 邻居时用内容相似补 CF 名额
        var docs = List.of(new ContentSimilarity.Doc(100, 1, "索尼 降噪耳机"),
                new ContentSimilarity.Doc(400, 3, "索尼 蓝牙耳机 九成新"));
        var cs = ContentSimilarity.build(docs, 0.3);
        var emptyModel = ItemCF.fit(InteractionMatrix.of(BehaviorWeightConfig.defaults().aggregate(
                List.of(new Behavior(9, 100, 1, 0)), 0)), 10, true, true);
        var h2 = new HybridRecommender(emptyModel, hot, new ColdStartRecommender(hot, 0.8), 3, cat::get, 0.1);
        var r2 = h2.recommend(interest, List.of(), 3, null, null, cs, Map.of(docs.get(0), 1.0));
        assertEquals(400, r2.get(0).itemId());
        assertEquals(RecSource.CONTENT, r2.get(0).source());
        assertEquals(100L, r2.get(0).reasonItemId());
    }
}
