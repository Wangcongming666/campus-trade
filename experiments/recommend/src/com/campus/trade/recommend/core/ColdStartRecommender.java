package com.campus.trade.recommend.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongPredicate;

/**
 * 新用户冷启动：兴趣分类热度 + 全局热度。
 * <p>
 * 兴趣分类 = 注册时选择的分类 ∪ 已有少量行为所在分类（行为分类优先）。
 * 前 ⌈n·interestRatio⌉ 个位置在各兴趣分类热度榜间轮询（round-robin，保证多样性），
 * 剩余位置用全局热度补齐；没有任何兴趣信息时直接返回全局热度。
 */
public final class ColdStartRecommender {

    private final HotRanker hot;
    private final double interestRatio;

    public ColdStartRecommender(HotRanker hot, double interestRatio) {
        this.hot = hot;
        this.interestRatio = interestRatio;
    }

    public List<RecResult> recommend(List<Long> interestCategories, int n, Set<Long> exclude, LongPredicate filter) {
        List<RecResult> out = new ArrayList<>(n);
        Set<Long> used = new HashSet<>();
        if (exclude != null) {
            used.addAll(exclude);
        }
        if (interestCategories != null && !interestCategories.isEmpty()) {
            int quota = (int) Math.ceil(n * interestRatio);
            List<List<ScoredItem>> lists = new ArrayList<>();
            for (Long c : interestCategories) {
                lists.add(hot.topInCategory(c, quota, used, filter));
            }
            int[] pos = new int[lists.size()];
            boolean progress = true;
            while (out.size() < quota && progress) {
                progress = false;
                for (int k = 0; k < lists.size() && out.size() < quota; k++) {
                    List<ScoredItem> l = lists.get(k);
                    while (pos[k] < l.size() && used.contains(l.get(pos[k]).itemId())) {
                        pos[k]++;
                    }
                    if (pos[k] < l.size()) {
                        ScoredItem s = l.get(pos[k]++);
                        used.add(s.itemId());
                        out.add(new RecResult(s.itemId(), s.score(), RecSource.COLD_START, null, interestCategories.get(k)));
                        progress = true;
                    }
                }
            }
        }
        for (ScoredItem s : hot.top(n - out.size(), used, filter)) {
            out.add(new RecResult(s.itemId(), s.score(), RecSource.HOT, null));
        }
        return out;
    }
}
