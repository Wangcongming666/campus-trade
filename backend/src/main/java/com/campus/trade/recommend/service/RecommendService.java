package com.campus.trade.recommend.service;

import com.campus.trade.common.BizException;
import com.campus.trade.common.Constants;
import com.campus.trade.common.ErrorCode;
import com.campus.trade.entity.Goods;
import com.campus.trade.recommend.config.RecommendProperties;
import com.campus.trade.recommend.core.Behavior;
import com.campus.trade.recommend.core.BehaviorWeightConfig;
import com.campus.trade.recommend.core.ColdStartRecommender;
import com.campus.trade.recommend.core.ContentSimilarity;
import com.campus.trade.recommend.core.HotRanker;
import com.campus.trade.recommend.core.HybridRecommender;
import com.campus.trade.recommend.core.ItemSimilarityModel;
import com.campus.trade.recommend.core.RecResult;
import com.campus.trade.recommend.core.ScoredItem;
import com.campus.trade.recommend.store.RecommendModelStore;
import com.campus.trade.service.GoodsService;
import com.campus.trade.service.UserInterestService;
import com.campus.trade.vo.GoodsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 混合推荐在线服务。
 * <ul>
 *   <li>游客：全局热度（带时间衰减）；</li>
 *   <li>登录且有效交互商品数 &lt; coldStartThreshold：冷启动。已有少量行为时先给 ⌈size·coldCfRatio⌉ 个
 *       ItemCF 邻居 / 内容相似结果；其余为兴趣分类（注册兴趣 + 行为所在分类，含子分类）候选 + 全局热度。
 *       分类热度榜只含有过行为的商品，数据量小时可能为空，因此兴趣通道会把该分类（含子分类）下在售的
 *       新商品追加在热度榜之后作为候选；</li>
 *   <li>否则：ItemCF-IUF（邻居从 Redis 读取）→ 新商品内容相似 → 全局热度 补齐；</li>
 *   <li>统一过滤：已交互、本人发布、非 ON_SHELF / 已删除；首页候选全部用完仍不足 size 时，最后才用只浏览过的在售商品补齐。</li>
 * </ul>
 * 每个结果按其实际命中的通道填写推荐理由（GoodsVO.reason / reasonType，文案见 {@link RecommendReason}）：
 * 混合策略中每个候选只由一个通道放入列表（ItemCF → 内容相似 → 热度，冷启动为兴趣分类 → 热度），
 * 该通道即其主要得分来源；CF 理由中的种子商品取对该候选得分贡献最大的那件。
 * 算法在 recommend.core（纯 Java）；VO 组装复用 GoodsService.toListVOs，兴趣分类复用 UserInterestService。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendService {

    /** 召回放大倍数：过滤（下架/本人发布）后仍能凑够 size */
    private static final int OVERFETCH = 3;
    /** 过滤已交互商品时最多读取的历史商品数 */
    private static final int SEEN_LIMIT = 5000;
    /** 列表最终兜底（最新发布）回看的天数 */
    private static final int LATEST_FALLBACK_DAYS = 3650;

    private final RecommendModelStore store;
    private final RecommendDataService data;
    private final GoodsService goodsService;
    private final UserInterestService userInterestService;
    private final RecommendProperties props;
    private final BehaviorWeightConfig interestWeightConfig;

    /** 首页推荐。userId 为 null 表示游客。 */
    public List<GoodsVO> home(Long userId, int size) {
        HotRanker hot = store.hot();
        if (userId == null) {
            List<Long> ids = hot.top(size * OVERFETCH, null, null).stream().map(ScoredItem::itemId).toList();
            return finish(ids, Map.of(), Set.of(), null, size, null);
        }
        long now = System.currentTimeMillis();
        List<Behavior> behaviors = data
                .recentBehaviors(userId, BehaviorConverter.daysAgo(props.getTrainWindowDays()), props.getUserBehaviorLimit())
                .stream().map(BehaviorConverter::toBehavior).toList();
        Map<Long, Double> interest = interestWeightConfig.aggregateUser(behaviors, now);

        // 历史商品基础信息：分类（冷启动兴趣）、标题（内容画像）
        Map<Long, Goods> history = interest.isEmpty() ? Map.of() : data.basicGoods(interest.keySet());

        // 已交互商品（含时间窗口外/超过条数上限的历史）一律过滤
        Set<Long> seen = new HashSet<>(interest.keySet());
        seen.addAll(data.interactedGoodsIds(userId, SEEN_LIMIT));
        List<Long> declared = interestCategories(userId);

        Map<Long, List<ScoredItem>> nb = store.neighbors(interest.keySet(), props.getTopK());
        ItemSimilarityModel partial = new ItemSimilarityModel(nb, props.getTopK());
        java.util.function.Function<Long, Long> historyCategory =
                id -> history.containsKey(id) ? history.get(id).getCategoryId() : null;
        // 冷启动 CF 名额按最终 size 计算：hybrid 召回 size*OVERFETCH 个候选，比例相应缩小
        double coldCfRatio = props.getColdCfRatio() / OVERFETCH;
        HybridRecommender hybrid = new HybridRecommender(partial, hot,
                new ColdStartRecommender(hot, props.getColdInterestRatio()), props.getColdStartThreshold(),
                historyCategory, coldCfRatio);
        boolean cold = hybrid.isColdUser(interest);
        if (cold) {
            HotRanker interestHot = withOnShelfInterestCandidates(hot,
                    hybrid.effectiveCategories(interest, declared), seen, userId, size * OVERFETCH);
            hybrid = new HybridRecommender(partial, hot,
                    new ColdStartRecommender(interestHot, props.getColdInterestRatio()), props.getColdStartThreshold(),
                    historyCategory, coldCfRatio);
        }

        ContentSimilarity content = null;
        Map<ContentSimilarity.Doc, Double> profile = null;
        // 老用户：内容相似只覆盖新商品（ItemCF 管不到的）；冷启动用户有种子时邻居往往为空，候选放宽到窗口内全部在售
        boolean needContent = !cold || (!interest.isEmpty() && props.getColdCfRatio() > 0);
        if (needContent) {
            int days = cold ? props.getTrainWindowDays() : props.getNewGoodsDays();
            List<Goods> fresh = data.recentOnShelf(BehaviorConverter.daysAgo(days), null,
                    props.getContentCandidateLimit()).stream()
                    .filter(g -> !userId.equals(g.getSellerId())).toList();
            if (!fresh.isEmpty()) {
                content = ContentSimilarity.build(fresh.stream().map(RecommendService::doc).toList(),
                        props.getContentCategoryWeight());
                profile = new LinkedHashMap<>();
                for (Map.Entry<Long, Double> e : topByValue(interest, 5)) {
                    Goods g = history.get(e.getKey());
                    if (g != null) {
                        profile.put(doc(g), e.getValue());
                    }
                }
            }
        }
        List<RecResult> recs = hybrid.recommend(interest, declared, size * OVERFETCH, seen, null, content, profile);
        if (log.isDebugEnabled()) {
            log.debug("[recommend] user={} cold={} sources={}", userId, cold,
                    recs.stream().collect(Collectors.groupingBy(RecResult::source, Collectors.counting())));
        }
        return finish(recs.stream().map(RecResult::itemId).toList(), reasons(recs, behaviors, history, declared),
                seen, userId, size, () -> data.viewOnlyGoodsIds(userId, SEEN_LIMIT));
    }

    /**
     * 冷启动兴趣通道的候选池：在离线热度榜的分类榜之后，追加该兴趣分类（含子分类）下最近发布、在售、
     * 非本人发布、未交互过的商品（得分 0，排在有热度的商品之后）。离线分类热度榜只含有过行为的商品，
     * 行为很少时可能为空或全是用户已交互的商品，此时兴趣分类仍能从在售商品中取到候选。
     */
    private HotRanker withOnShelfInterestCandidates(HotRanker hot, List<Long> categories, Set<Long> seen,
                                                    Long userId, int n) {
        if (categories.isEmpty()) {
            return hot;
        }
        Map<Long, List<ScoredItem>> byCat = new HashMap<>(hot.categories());
        for (Long c : categories) {
            List<ScoredItem> list = new ArrayList<>(hot.category(c));
            Set<Long> in = list.stream().map(ScoredItem::itemId).collect(Collectors.toCollection(HashSet::new));
            long fresh = list.stream().filter(x -> !seen.contains(x.itemId())).count();
            if (fresh >= n) {
                continue; // 热度榜已足够
            }
            Set<Long> cats = data.categoryService().selfAndDescendantIds(c);
            if (cats.isEmpty()) {
                continue;
            }
            int limit = (int) Math.min(props.getContentCandidateLimit(), n + seen.size());
            for (Goods g : data.recentOnShelf(BehaviorConverter.daysAgo(props.getTrainWindowDays()), cats, limit)) {
                if (!seen.contains(g.getId()) && !userId.equals(g.getSellerId()) && in.add(g.getId())) {
                    list.add(new ScoredItem(g.getId(), 0));
                }
            }
            byCat.put(c, list);
        }
        return new HotRanker(hot.global(), byCat);
    }

    /** 按每个候选实际命中的通道生成推荐理由。 */
    private Map<Long, RecommendReason> reasons(List<RecResult> recs, List<Behavior> behaviors, Map<Long, Goods> history,
                                               List<Long> declared) {
        Map<Long, Integer> topBehavior = new HashMap<>(); // 商品 → 用户对它权重最高的行为（下单>聊天>收藏>浏览）
        for (Behavior b : behaviors) {
            topBehavior.merge(b.itemId(), b.type(), Math::max);
        }
        Set<Long> cats = recs.stream().map(RecResult::reasonCategoryId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> catNames = cats.isEmpty() ? Map.of() : data.categoryService().namesByIds(cats);
        Map<Long, RecommendReason> out = new HashMap<>();
        for (RecResult r : recs) {
            Goods seed = r.reasonItemId() == null ? null : history.get(r.reasonItemId());
            String seedTitle = seed == null ? null : seed.getTitle();
            Integer type = r.reasonItemId() == null ? null : topBehavior.get(r.reasonItemId());
            RecommendReason reason = switch (r.source()) {
                case ITEM_CF -> RecommendReason.itemCf(type, seedTitle);
                case CONTENT -> RecommendReason.content(type, seedTitle);
                case USER_CF -> RecommendReason.SIMILAR_USERS;
                case COLD_START -> RecommendReason.interest(catNames.get(r.reasonCategoryId()),
                        declared.contains(r.reasonCategoryId()));
                case HOT -> RecommendReason.HOT_WEEK;
            };
            out.put(r.itemId(), reason);
        }
        return out;
    }

    /** 相似商品：ItemCF 邻居优先；新商品/邻居不足时用内容相似（同一级分类 + 标题 TF-IDF）补齐。 */
    public List<GoodsVO> similar(long goodsId, int size, Long userId) {
        Goods g = data.goods(goodsId); // @TableLogic：已删除视为不存在
        if (g == null) {
            throw new BizException(ErrorCode.GOODS_NOT_FOUND);
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        store.neighbors(List.of(goodsId), Math.max(props.getTopK(), size * OVERFETCH))
                .getOrDefault(goodsId, List.of()).forEach(s -> ids.add(s.itemId()));
        if (ids.size() < size * OVERFETCH) {
            Long root = RecommendDataService.rootOf(g.getCategoryId(), data.enabledCategoryParents());
            Set<Long> cats = root == null ? Set.of() : data.categoryService().selfAndDescendantIds(root);
            List<Goods> pool = data.recentOnShelf(BehaviorConverter.daysAgo(props.getTrainWindowDays()), cats,
                    props.getContentCandidateLimit());
            if (!pool.isEmpty()) {
                ContentSimilarity cs = ContentSimilarity.build(pool.stream().map(RecommendService::doc).toList(),
                        props.getContentCategoryWeight());
                cs.similarTo(doc(g), size * OVERFETCH, Set.of(goodsId), null).forEach(s -> ids.add(s.itemId()));
            }
        }
        ids.remove(goodsId);
        Map<Long, RecommendReason> reasons = new HashMap<>();
        ids.forEach(id -> reasons.put(id, RecommendReason.SIMILAR_TO_CURRENT)); // ItemCF 邻居与内容相似都以当前商品为种子
        return finish(new ArrayList<>(ids), reasons, Set.of(goodsId), userId, size, null);
    }

    /** 注册兴趣分类：复用 UserInterestService，只保留仍为 ENABLED 的分类（该方法本身不过滤停用分类）。 */
    List<Long> interestCategories(long userId) {
        if (!props.isUserInterestEnabled()) {
            return List.of();
        }
        List<Long> raw = userInterestService.categoryIdsOf(userId);
        if (raw.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> enabled = data.enabledCategoryParents();
        return raw.stream().filter(enabled::containsKey).distinct().toList();
    }

    /**
     * 过滤 + 组装：去掉已交互、本人发布、非上架商品；不足 size 先用热度补齐（本周热门），仍不足（热度榜为空、
     * 或热度商品都被过滤掉）时用最新上架兜底（最新发布）。reasons 中没有的商品一律按热度理由处理，保证每项理由非空。
     * 以上全部用完仍不足 size 时，若给了 viewedFallback（仅首页登录用户），最后一层用"只浏览过（无收藏/聊天/下单）、
     * 仍在售、非本人发布"的商品按最近浏览倒序补齐，排在所有其他结果之后（你最近浏览过，还在售）；
     * 收藏/聊天/下单过的商品永不重新推荐。
     */
    private List<GoodsVO> finish(List<Long> candidates, Map<Long, RecommendReason> reasons, Set<Long> seen,
                                 Long userId, int size, java.util.function.Supplier<List<Long>> viewedFallback) {
        Map<Long, RecommendReason> why = new HashMap<>(reasons);
        List<Long> ordered = new ArrayList<>(new LinkedHashSet<>(candidates));
        ordered.removeIf(seen::contains);
        List<Goods> picked = filter(ordered, userId, size);
        Set<Long> used = new HashSet<>(seen);
        picked.forEach(x -> used.add(x.getId()));
        if (picked.size() < size) {
            List<Long> more = store.hot().top((size - picked.size()) * OVERFETCH, used, null).stream()
                    .map(ScoredItem::itemId).toList();
            addFiltered(picked, used, more, userId, size);
        }
        if (picked.size() < size) {
            // 本人发布的商品会被 filter 去掉，多取一些，避免还没凑满就用完
            int limit = (size - picked.size()) * OVERFETCH + used.size() + 1;
            List<Long> more = data.recentOnShelf(BehaviorConverter.daysAgo(LATEST_FALLBACK_DAYS), null, limit)
                    .stream().map(Goods::getId).filter(id -> !used.contains(id)).toList();
            more.forEach(id -> why.putIfAbsent(id, RecommendReason.LATEST));
            addFiltered(picked, used, more, userId, size);
        }
        if (picked.size() < size && viewedFallback != null) {
            Set<Long> pickedIds = picked.stream().map(Goods::getId).collect(Collectors.toSet());
            List<Long> viewed = viewedFallback.get().stream().filter(id -> !pickedIds.contains(id)).toList();
            for (Goods x : filter(viewed, userId, size - picked.size())) {
                if (pickedIds.add(x.getId())) {
                    picked.add(x);
                    why.put(x.getId(), RecommendReason.VIEWED_STILL_ON_SHELF);
                }
            }
        }
        List<GoodsVO> vos = goodsService.toListVOs(picked, userId);
        for (GoodsVO vo : vos) {
            RecommendReason r = why.getOrDefault(vo.getId(), RecommendReason.HOT_WEEK);
            vo.setReason(r.text());
            vo.setReasonType(r.type());
        }
        return vos;
    }

    private void addFiltered(List<Goods> picked, Set<Long> used, List<Long> more, Long userId, int size) {
        List<Long> fresh = more.stream().filter(id -> !used.contains(id)).toList();
        for (Goods x : filter(fresh, userId, size - picked.size())) {
            if (used.add(x.getId())) {
                picked.add(x);
            }
        }
    }

    /** 只保留 ON_SHELF、未删除、非本人发布的商品，保持候选顺序，最多 limit 个。 */
    private List<Goods> filter(List<Long> ids, Long userId, int limit) {
        List<Goods> out = new ArrayList<>(Math.max(0, limit));
        if (ids.isEmpty() || limit <= 0) {
            return out;
        }
        for (Goods g : data.fullGoodsInOrder(ids)) {
            if (out.size() >= limit) {
                break;
            }
            if (!Constants.GoodsStatus.ON_SHELF.equals(g.getStatus())) {
                continue;
            }
            if (userId != null && userId.equals(g.getSellerId())) {
                continue; // 不推荐自己发布的商品
            }
            out.add(g);
        }
        return out;
    }

    private static ContentSimilarity.Doc doc(Goods g) {
        return new ContentSimilarity.Doc(g.getId(), g.getCategoryId() == null ? -1 : g.getCategoryId(), g.getTitle());
    }

    private static List<Map.Entry<Long, Double>> topByValue(Map<Long, Double> m, int n) {
        return m.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue(Comparator.reverseOrder()))
                .limit(n).toList();
    }

    /** 调试用：当前模型版本与热度榜规模。 */
    public Map<String, Object> modelInfo() {
        Map<String, Object> m = new HashMap<>();
        m.put("version", store.currentVersion());
        m.put("hotSize", store.hot().global().size());
        return m;
    }
}
