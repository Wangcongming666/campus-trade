package com.campus.trade.recommend.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongPredicate;

/**
 * 热度排行（全局 + 分类）。
 * <pre>
 *   hot(i) = Σ_{b∈B(i)} w(type_b) · 0.5^((now - t_b)/halfLife)
 * </pre>
 * halfLife &lt;= 0 时退化为加权行为计数（"热门推荐"基线）。
 */
public final class HotRanker {

    private final List<ScoredItem> global;
    private final Map<Long, List<ScoredItem>> byCategory;

    public HotRanker(List<ScoredItem> global, Map<Long, List<ScoredItem>> byCategory) {
        this.global = global;
        this.byCategory = byCategory;
    }

    /**
     * @param itemCategory 商品 → 分类（可为 null，则不建分类榜）
     */
    public static HotRanker build(Collection<Behavior> behaviors, BehaviorWeightConfig cfg, long nowMillis,
                                  Map<Long, Long> itemCategory) {
        Map<Long, Double> score = new HashMap<>();
        for (Behavior b : behaviors) {
            double s = cfg.score(b, nowMillis);
            if (s > 0) {
                score.merge(b.itemId(), s, Double::sum);
            }
        }
        List<ScoredItem> all = new ArrayList<>(score.size());
        score.forEach((id, s) -> all.add(new ScoredItem(id, s)));
        all.sort(TopK.DESC);
        Map<Long, List<ScoredItem>> byCat = new HashMap<>();
        if (itemCategory != null) {
            for (ScoredItem s : all) {
                Long c = itemCategory.get(s.itemId());
                if (c != null) {
                    byCat.computeIfAbsent(c, k -> new ArrayList<>()).add(s);
                }
            }
        }
        return new HotRanker(List.copyOf(all), byCat);
    }

    public List<ScoredItem> global() {
        return global;
    }

    public List<ScoredItem> category(long categoryId) {
        return byCategory.getOrDefault(categoryId, Collections.emptyList());
    }

    public Map<Long, List<ScoredItem>> categories() {
        return Collections.unmodifiableMap(byCategory);
    }

    public List<ScoredItem> top(int n, Set<Long> exclude, LongPredicate filter) {
        return take(global, n, exclude, filter);
    }

    public List<ScoredItem> topInCategory(long categoryId, int n, Set<Long> exclude, LongPredicate filter) {
        return take(category(categoryId), n, exclude, filter);
    }

    static List<ScoredItem> take(List<ScoredItem> src, int n, Set<Long> exclude, LongPredicate filter) {
        List<ScoredItem> out = new ArrayList<>(n);
        for (ScoredItem s : src) {
            if (out.size() >= n) {
                break;
            }
            if (exclude != null && exclude.contains(s.itemId())) {
                continue;
            }
            if (filter != null && !filter.test(s.itemId())) {
                continue;
            }
            out.add(s);
        }
        return out;
    }
}
