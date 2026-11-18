package com.campus.trade.service;

import com.campus.trade.common.Constants;
import com.campus.trade.common.FeedPage;
import com.campus.trade.entity.Goods;
import com.campus.trade.mapper.GoodsMapper;
import com.campus.trade.recommend.service.RecommendService;
import com.campus.trade.vo.GoodsVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 首页个性化推荐分页（包在 RecommendService 外层，不改推荐算法）：
 * <ul>
 *   <li>第 1 页：先取 RecommendService.home(userId, headSize=10)（与不分页时的默认首页完全一致，保证首屏的
 *       CF/兴趣/热门配比不变；推荐算法的冷启动 CF 名额按 size 比例计算，直接取 100 条会让 CF 占比偏高），
 *       再用 home(userId, feedSize=100) 中未出现的商品按原顺序接在后面，得到完整候选序列（CF→兴趣→热门→最新→浏览回填），
 *       只缓存 [id, reason, reasonType] 到 Redis {@code rec:feed:{userId}}（游客共用 {@code rec:feed:guest}），TTL 10 分钟；</li>
 *   <li>第 2 页起：读缓存切片（缓存失效时重新计算），页与页之间不重复、顺序稳定；超出范围返回空列表；</li>
 *   <li>每页按 ID 重新加载商品并组装 VO（收藏状态实时），已下架/售出/删除的商品在该页中跳过，total 不变。</li>
 * </ul>
 */
@Slf4j
@Service
public class RecommendFeedService {

    public static final int MAX_PAGE_SIZE = 50;

    private final RecommendService recommendService;
    private final GoodsService goodsService;
    private final GoodsMapper goodsMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper om;
    private final int feedSize;
    private final int headSize;
    private final Duration ttl;

    public RecommendFeedService(RecommendService recommendService, GoodsService goodsService, GoodsMapper goodsMapper,
                                StringRedisTemplate redis, ObjectMapper om,
                                @Value("${campus.recommend.feed.size:100}") int feedSize,
                                @Value("${campus.recommend.feed.cache-minutes:10}") long cacheMinutes,
                                @Value("${campus.recommend.feed.head-size:10}") int headSize) {
        this.recommendService = recommendService;
        this.goodsService = goodsService;
        this.goodsMapper = goodsMapper;
        this.redis = redis;
        this.om = om;
        this.feedSize = feedSize;
        this.headSize = Math.max(1, Math.min(headSize, feedSize));
        this.ttl = Duration.ofMinutes(cacheMinutes);
    }

    /** 缓存条目：商品 ID + 推荐理由。 */
    public record Entry(Long id, String reason, String reasonType) {
    }

    public static String key(Long userId) {
        return "rec:feed:" + (userId == null ? "guest" : userId);
    }

    public FeedPage<GoodsVO> home(Long userId, int pageNum, int pageSize) {
        int p = Math.max(1, pageNum);
        int s = Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));
        List<Entry> feed = p == 1 ? rebuild(userId) : cached(userId);
        if (feed == null) {
            feed = rebuild(userId);
        }
        long from = (long) (p - 1) * s;
        if (from >= feed.size()) {
            return FeedPage.of(List.of(), feed.size(), p, s);
        }
        List<Entry> slice = feed.subList((int) from, (int) Math.min(feed.size(), from + s));
        return FeedPage.of(hydrate(slice, userId), feed.size(), p, s);
    }

    private List<Entry> rebuild(Long userId) {
        LinkedHashMap<Long, Entry> seq = new LinkedHashMap<>();
        for (GoodsVO v : recommendService.home(userId, headSize)) {
            seq.putIfAbsent(v.getId(), new Entry(v.getId(), v.getReason(), v.getReasonType()));
        }
        for (GoodsVO v : recommendService.home(userId, feedSize)) {
            if (seq.size() >= feedSize) {
                break;
            }
            seq.putIfAbsent(v.getId(), new Entry(v.getId(), v.getReason(), v.getReasonType()));
        }
        List<Entry> feed = new ArrayList<>(seq.values());
        try {
            redis.opsForValue().set(key(userId), om.writeValueAsString(feed), ttl);
        } catch (Exception e) {
            log.warn("recommend feed cache write failed: {}", e.getMessage());
        }
        return feed;
    }

    private List<Entry> cached(Long userId) {
        try {
            String json = redis.opsForValue().get(key(userId));
            return json == null ? null : om.readValue(json, new TypeReference<List<Entry>>() {
            });
        } catch (Exception e) {
            log.warn("recommend feed cache read failed: {}", e.getMessage());
            return null;
        }
    }

    private List<GoodsVO> hydrate(List<Entry> slice, Long userId) {
        Map<Long, Goods> byId = goodsMapper.selectBatchIds(slice.stream().map(Entry::id).toList()).stream()
                .collect(Collectors.toMap(Goods::getId, Function.identity()));
        List<Goods> goods = new ArrayList<>();
        Map<Long, Entry> reasons = new HashMap<>();
        for (Entry e : slice) {
            Goods g = byId.get(e.id());
            if (g == null || !Constants.GoodsStatus.ON_SHELF.equals(g.getStatus())
                    || (userId != null && userId.equals(g.getSellerId()))) {
                continue;
            }
            goods.add(g);
            reasons.put(g.getId(), e);
        }
        List<GoodsVO> vos = goodsService.toListVOs(goods, userId);
        for (GoodsVO vo : vos) {
            Entry e = reasons.get(vo.getId());
            vo.setReason(e.reason());
            vo.setReasonType(e.reasonType());
        }
        return vos;
    }
}
