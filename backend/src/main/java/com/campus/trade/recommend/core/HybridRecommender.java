package com.campus.trade.recommend.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongPredicate;

/**
 * 混合推荐策略（纯 Java）：
 * <ol>
 *   <li>用户有效交互商品数 &lt; coldStartThreshold：冷启动。已有少量行为（有种子商品）时，先用不超过
 *       ⌈n·coldCfRatio⌉ 个位置放 ItemCF 邻居 / 内容相似结果，其余位置走兴趣分类热度 + 全局热度；</li>
 *   <li>否则 ItemCF-IUF；</li>
 *   <li>不足 N 个时，依次用 内容相似（新商品，可选）→ 全局热度 补齐。</li>
 * </ol>
 */
public final class HybridRecommender {

    private final ItemSimilarityModel model;
    private final HotRanker hot;
    private final ColdStartRecommender cold;
    private final int coldStartThreshold;
    private final Function<Long, Long> itemCategory;
    private final double coldCfRatio;

    /** 等价于 coldCfRatio = 0（冷启动用户不用 CF）。 */
    public HybridRecommender(ItemSimilarityModel model, HotRanker hot, ColdStartRecommender cold,
                             int coldStartThreshold, Function<Long, Long> itemCategory) {
        this(model, hot, cold, coldStartThreshold, itemCategory, 0);
    }

    /**
     * @param itemCategory 商品 → 分类，用于把少量行为所在分类并入冷启动兴趣（可为 null）
     * @param coldCfRatio  冷启动用户（有少量行为时）CF 结果最多占的比例，0 表示冷启动不用 CF
     */
    public HybridRecommender(ItemSimilarityModel model, HotRanker hot, ColdStartRecommender cold,
                             int coldStartThreshold, Function<Long, Long> itemCategory, double coldCfRatio) {
        this.coldCfRatio = coldCfRatio;
        this.model = model;
        this.hot = hot;
        this.cold = cold;
        this.coldStartThreshold = coldStartThreshold;
        this.itemCategory = itemCategory;
    }

    public boolean isColdUser(Map<Long, Double> userInterest) {
        return userInterest == null || userInterest.size() < coldStartThreshold;
    }

    /**
     * @param userInterest       用户历史兴趣 item → r(u,i)（未登录传空 Map）
     * @param declaredCategories 注册时选择的兴趣分类（可为空）
     * @param n                  推荐个数
     * @param exclude            额外排除（已售出、本人发布等）
     * @param filter             候选过滤（如只保留在售）
     * @param content            内容相似索引（一般只含新商品），可为 null
     * @param contentProfile     内容画像：用户最近交互商品文档 → 权重，可为 null
     */
    public List<RecResult> recommend(Map<Long, Double> userInterest, List<Long> declaredCategories, int n,
                                     Set<Long> exclude, LongPredicate filter,
                                     ContentSimilarity content, Map<ContentSimilarity.Doc, Double> contentProfile) {
        Map<Long, Double> interest = userInterest == null ? Map.of() : userInterest;
        Set<Long> used = new HashSet<>(interest.keySet());
        if (exclude != null) {
            used.addAll(exclude);
        }
        if (isColdUser(interest)) {
            List<RecResult> out = new ArrayList<>(n);
            if (!interest.isEmpty() && coldCfRatio > 0) {
                int quota = Math.min(n, (int) Math.ceil(n * coldCfRatio - 1e-9));
                for (RecResult r : ItemCF.recommend(model, interest, quota, used, filter)) {
                    out.add(r);
                    used.add(r.itemId());
                }
                if (out.size() < quota && content != null && contentProfile != null && !contentProfile.isEmpty()) {
                    for (RecResult r : content.recommendForProfile(contentProfile, quota - out.size(), used, filter)) {
                        out.add(r);
                        used.add(r.itemId());
                    }
                }
            }
            out.addAll(cold.recommend(effectiveCategories(interest, declaredCategories), n - out.size(), used, filter));
            return out;
        }
        Map<Long, RecResult> out = new LinkedHashMap<>();
        for (RecResult r : ItemCF.recommend(model, interest, n, used, filter)) {
            out.put(r.itemId(), r);
        }
        if (out.size() < n && content != null && contentProfile != null && !contentProfile.isEmpty()) {
            Set<Long> ex = new HashSet<>(used);
            ex.addAll(out.keySet());
            for (RecResult r : content.recommendForProfile(contentProfile, n - out.size(), ex, filter)) {
                out.putIfAbsent(r.itemId(), r);
            }
        }
        if (out.size() < n) {
            Set<Long> ex = new HashSet<>(used);
            ex.addAll(out.keySet());
            for (ScoredItem s : hot.top(n - out.size(), ex, filter)) {
                out.putIfAbsent(s.itemId(), new RecResult(s.itemId(), s.score(), RecSource.HOT, null));
            }
        }
        return new ArrayList<>(out.values());
    }

    /** 已有行为的分类（按兴趣和降序）优先，其后是注册时声明的分类。 */
    public List<Long> effectiveCategories(Map<Long, Double> interest, List<Long> declared) {
        Set<Long> cats = new LinkedHashSet<>();
        if (itemCategory != null && !interest.isEmpty()) {
            Map<Long, Double> catScore = new LinkedHashMap<>();
            interest.forEach((item, w) -> {
                Long c = itemCategory.apply(item);
                if (c != null) {
                    catScore.merge(c, w, Double::sum);
                }
            });
            catScore.entrySet().stream()
                    .sorted(Map.Entry.<Long, Double>comparingByValue(Comparator.reverseOrder())
                            .thenComparing(Map.Entry.comparingByKey()))
                    .forEach(e -> cats.add(e.getKey()));
        }
        if (declared != null) {
            cats.addAll(declared);
        }
        return new ArrayList<>(cats);
    }
}
