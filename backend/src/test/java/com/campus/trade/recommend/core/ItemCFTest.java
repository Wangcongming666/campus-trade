package com.campus.trade.recommend.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ItemCFTest {

    private static List<Behavior> toy() {
        List<Behavior> list = new ArrayList<>();
        // 用户1、2、3 都同时看过 A(10) 与 B(20)；用户3 还看过 C(30)；用户4 只看过 C、D(40)
        long t = 0;
        for (long u : new long[]{1, 2, 3}) {
            list.add(new Behavior(u, 10, 1, t));
            list.add(new Behavior(u, 20, 1, t));
        }
        list.add(new Behavior(3, 30, 1, t));
        list.add(new Behavior(4, 30, 1, t));
        list.add(new Behavior(4, 40, 4, t));
        list.add(new Behavior(5, 10, 2, t));
        return list;
    }

    @Test
    void similarityIsNormalizedAndTopK() {
        var interest = BehaviorWeightConfig.binary().aggregate(toy(), 0);
        var model = ItemCF.fit(InteractionMatrix.of(interest), 1, true, true);
        assertEquals(1, model.neighbors(10).size());
        assertEquals(20, model.neighbors(10).get(0).itemId());
        assertEquals(1.0, model.neighbors(10).get(0).score(), 1e-9, "行最大值归一化后首位为 1");
    }

    @Test
    void recommendExcludesSeenAndFiltered() {
        var interest = BehaviorWeightConfig.binary().aggregate(toy(), 0);
        var model = ItemCF.fit(InteractionMatrix.of(interest), 10, true, true);
        List<RecResult> recs = ItemCF.recommend(model, interest.get(5L), 10, Set.of(), null);
        assertEquals(20, recs.get(0).itemId());
        assertEquals(10L, recs.get(0).reasonItemId());
        assertTrue(recs.stream().noneMatch(r -> r.itemId() == 10));
        // 过滤掉"已售出"的 20
        List<RecResult> filtered = ItemCF.recommend(model, interest.get(5L), 10, Set.of(20L), null);
        assertTrue(filtered.stream().noneMatch(r -> r.itemId() == 20));
    }

    @Test
    void iufPenalizesActiveUsers() {
        // 活跃用户 9 看了所有物品，IUF 下其贡献被削弱
        List<Behavior> list = new ArrayList<>(toy());
        for (long i = 10; i <= 40; i += 10) {
            list.add(new Behavior(9, i, 1, 0));
        }
        var m = InteractionMatrix.of(BehaviorWeightConfig.binary().aggregate(list, 0));
        double plain = ItemCF.fit(m, 10, false, false).neighbors(10).stream()
                .filter(s -> s.itemId() == 40).findFirst().orElseThrow().score();
        double iuf = ItemCF.fit(m, 10, true, false).neighbors(10).stream()
                .filter(s -> s.itemId() == 40).findFirst().orElseThrow().score();
        assertTrue(iuf < plain);
    }

    @Test
    void weightsAndDecay() {
        var cfg = BehaviorWeightConfig.defaults().withLogScale(false).withHalfLifeDays(7);
        long now = 14L * 86_400_000L;
        Map<Long, Double> m = cfg.aggregateUser(List.of(new Behavior(1, 1, 4, 0), new Behavior(1, 2, 1, now)), now);
        assertEquals(5 * 0.25, m.get(1L), 1e-9);
        assertEquals(1.0, m.get(2L), 1e-9);
        Map<Long, Double> logged = BehaviorWeightConfig.defaults().aggregateUser(List.of(new Behavior(1, 1, 4, 0)), 0);
        assertEquals(Math.log(6), logged.get(1L), 1e-9);
    }
}
