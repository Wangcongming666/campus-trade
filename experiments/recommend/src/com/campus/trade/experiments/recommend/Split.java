package com.campus.trade.experiments.recommend;

import com.campus.trade.recommend.core.Behavior;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 按用户随机 8:2 划分（"用户-物品对"粒度，测试物品在该用户训练集中不出现）。
 * 物品对数 n&gt;=2 时测试数 = max(1, round(0.2n))，n=1 时全部进训练集。
 * 用户按 ID 升序、物品按 ID 升序后用固定种子洗牌，完全可复现（与 smart-module 的划分逐条一致）。
 */
final class Split {

    final List<Behavior> train = new ArrayList<>();
    final Map<Long, Set<Long>> test = new TreeMap<>();
    /** 冷启动协议中被截断训练行为的用户（自然划分时为空） */
    final Set<Long> truncatedUsers = new TreeSet<>();

    static Split of(List<Behavior> all, long seed) {
        Map<Long, Map<Long, List<Behavior>>> byUser = group(all);
        Random rnd = new Random(seed);
        Split s = new Split();
        for (Map.Entry<Long, Map<Long, List<Behavior>>> e : byUser.entrySet()) {
            List<Long> items = new ArrayList<>(e.getValue().keySet());
            Collections.shuffle(items, rnd);
            int n = items.size();
            int nTest = n >= 2 ? Math.max(1, (int) Math.round(0.2 * n)) : 0;
            Set<Long> testItems = new TreeSet<>(items.subList(0, nTest));
            for (Map.Entry<Long, List<Behavior>> ie : e.getValue().entrySet()) {
                if (!testItems.contains(ie.getKey())) {
                    s.train.addAll(ie.getValue());
                }
            }
            if (!testItems.isEmpty()) {
                s.test.put(e.getKey(), testItems);
            }
        }
        return s;
    }

    /**
     * 冷启动协议（用于 MovieLens：该数据集每个用户至少 20 条评分，没有自然冷启动用户）：
     * 在普通划分的基础上，随机抽取 fraction 比例的评估用户，只保留其 1~maxKeep 个（均匀随机）训练物品，
     * 测试集不变；其他用户不受影响。模型在截断后的训练集上重新训练。
     */
    Split truncateUsers(double fraction, int maxKeep, long seed) {
        Random rnd = new Random(seed * 7919L + 17);
        Map<Long, Map<Long, List<Behavior>>> byUser = group(train);
        Split s = new Split();
        s.test.putAll(test);
        for (Map.Entry<Long, Map<Long, List<Behavior>>> e : byUser.entrySet()) {
            boolean pick = test.containsKey(e.getKey()) && rnd.nextDouble() < fraction;
            if (!pick) {
                e.getValue().values().forEach(s.train::addAll);
                continue;
            }
            List<Long> items = new ArrayList<>(e.getValue().keySet());
            Collections.shuffle(items, rnd);
            int keep = Math.min(items.size(), 1 + rnd.nextInt(maxKeep));
            for (Long i : items.subList(0, keep)) {
                s.train.addAll(e.getValue().get(i));
            }
            s.truncatedUsers.add(e.getKey());
        }
        return s;
    }

    long trainPairs() {
        Set<Long> pairs = new HashSet<>();
        for (Behavior b : train) {
            pairs.add(b.userId() * 10_000_000L + b.itemId());
        }
        return pairs.size();
    }

    long testPairs() {
        return test.values().stream().mapToLong(Set::size).sum();
    }

    private static Map<Long, Map<Long, List<Behavior>>> group(List<Behavior> all) {
        Map<Long, Map<Long, List<Behavior>>> byUser = new TreeMap<>();
        for (Behavior b : all) {
            byUser.computeIfAbsent(b.userId(), k -> new TreeMap<>())
                    .computeIfAbsent(b.itemId(), k -> new ArrayList<>()).add(b);
        }
        return byUser;
    }
}
