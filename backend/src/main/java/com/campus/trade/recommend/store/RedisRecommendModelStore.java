package com.campus.trade.recommend.store;

import com.campus.trade.recommend.core.HotRanker;
import com.campus.trade.recommend.core.ItemSimilarityModel;
import com.campus.trade.recommend.core.ScoredItem;
import com.campus.trade.recommend.config.RecommendProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Redis 存储（RedisTemplate 写法）。Key 设计（{v} 为版本号，整体按版本切换，避免读到写了一半的矩阵）：
 * <pre>
 *   rec:ver                     STRING  当前版本号
 *   rec:{v}:sim:{goodsId}       ZSET    member=邻居商品ID, score=归一化相似度（TopK）
 *   rec:{v}:hot                 ZSET    全局热度榜
 *   rec:{v}:hot:cat:{catId}     ZSET    分类热度榜
 *   rec:{v}:hot:cats            SET     有热度榜的分类 ID
 *   rec:lock:offline            STRING  离线任务锁（SET NX EX）
 * </pre>
 * 每个版本的 key 都带 TTL（keyTtlHours），旧版本自然过期，无需手动删除。
 */
@Component
@ConditionalOnProperty(prefix = "campus.recommend", name = "store", havingValue = "redis", matchIfMissing = true)
public class RedisRecommendModelStore implements RecommendModelStore {

    static final String VER_KEY = "rec:ver";
    static final String LOCK_KEY = "rec:lock:offline";
    private static final int BATCH = 500;

    private final StringRedisTemplate redis;
    private final RecommendProperties props;
    private final String lockToken = UUID.randomUUID().toString();

    /** 本地缓存热度榜，版本变化时刷新 */
    private volatile String cachedHotVersion;
    private volatile HotRanker cachedHot = new HotRanker(List.of(), Map.of());

    public RedisRecommendModelStore(StringRedisTemplate redis, RecommendProperties props) {
        this.redis = redis;
        this.props = props;
    }

    static String simKey(String v, long goodsId) { return "rec:" + v + ":sim:" + goodsId; }
    static String hotKey(String v) { return "rec:" + v + ":hot"; }
    static String catHotKey(String v, long cat) { return "rec:" + v + ":hot:cat:" + cat; }
    static String catsKey(String v) { return "rec:" + v + ":hot:cats"; }

    @Override
    public void save(ItemSimilarityModel similarity, HotRanker hot, String version) {
        Duration ttl = Duration.ofHours(props.getKeyTtlHours());
        List<Map.Entry<Long, List<ScoredItem>>> entries = new ArrayList<>(similarity.asMap().entrySet());
        for (int from = 0; from < entries.size(); from += BATCH) {
            List<Map.Entry<Long, List<ScoredItem>>> batch = entries.subList(from, Math.min(entries.size(), from + BATCH));
            redis.executePipelined(new SessionCallback<Object>() {
                @Override
                @SuppressWarnings({"unchecked", "rawtypes"})
                public Object execute(RedisOperations ops) throws DataAccessException {
                    for (Map.Entry<Long, List<ScoredItem>> e : batch) {
                        String key = simKey(version, e.getKey());
                        ops.opsForZSet().add(key, toTuples(e.getValue()));
                        ops.expire(key, ttl);
                    }
                    return null;
                }
            });
        }
        int limit = props.getHotListSize();
        redis.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public Object execute(RedisOperations ops) throws DataAccessException {
                List<ScoredItem> g = hot.global();
                if (!g.isEmpty()) {
                    ops.opsForZSet().add(hotKey(version), toTuples(g.subList(0, Math.min(limit, g.size()))));
                    ops.expire(hotKey(version), ttl);
                }
                for (Map.Entry<Long, List<ScoredItem>> e : hot.categories().entrySet()) {
                    List<ScoredItem> l = e.getValue();
                    ops.opsForZSet().add(catHotKey(version, e.getKey()), toTuples(l.subList(0, Math.min(limit, l.size()))));
                    ops.expire(catHotKey(version, e.getKey()), ttl);
                    ops.opsForSet().add(catsKey(version), String.valueOf(e.getKey()));
                }
                ops.expire(catsKey(version), ttl);
                return null;
            }
        });
        // 全部写完再切换版本指针
        redis.opsForValue().set(VER_KEY, version, ttl);
    }

    private static Set<TypedTuple<String>> toTuples(List<ScoredItem> list) {
        Set<TypedTuple<String>> set = new HashSet<>(list.size() * 2);
        for (ScoredItem s : list) {
            set.add(new DefaultTypedTuple<>(String.valueOf(s.itemId()), s.score()));
        }
        return set;
    }

    @Override
    public String currentVersion() {
        return redis.opsForValue().get(VER_KEY);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<Long, List<ScoredItem>> neighbors(Collection<Long> goodsIds, int topK) {
        String v = currentVersion();
        Map<Long, List<ScoredItem>> out = new HashMap<>();
        if (v == null || goodsIds.isEmpty()) {
            return out;
        }
        List<Long> ids = new ArrayList<>(goodsIds);
        List<Object> res = redis.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings({"rawtypes"})
            public Object execute(RedisOperations ops) throws DataAccessException {
                for (Long id : ids) {
                    ops.opsForZSet().reverseRangeWithScores(simKey(v, id), 0, topK - 1);
                }
                return null;
            }
        });
        for (int i = 0; i < ids.size(); i++) {
            Set<TypedTuple<String>> tuples = (Set<TypedTuple<String>>) res.get(i);
            if (tuples != null && !tuples.isEmpty()) {
                out.put(ids.get(i), toScored(tuples));
            }
        }
        return out;
    }

    private static List<ScoredItem> toScored(Set<TypedTuple<String>> tuples) {
        List<ScoredItem> l = new ArrayList<>(tuples.size());
        for (TypedTuple<String> t : tuples) {
            l.add(new ScoredItem(Long.parseLong(t.getValue()), t.getScore() == null ? 0 : t.getScore()));
        }
        return l; // reverseRangeWithScores 已按分数降序
    }

    @Override
    public HotRanker hot() {
        String v = currentVersion();
        if (v == null) {
            return new HotRanker(List.of(), Map.of());
        }
        if (v.equals(cachedHotVersion)) {
            return cachedHot;
        }
        int limit = props.getHotListSize();
        Set<TypedTuple<String>> g = redis.opsForZSet().reverseRangeWithScores(hotKey(v), 0, limit - 1);
        Map<Long, List<ScoredItem>> byCat = new LinkedHashMap<>();
        Set<String> cats = redis.opsForSet().members(catsKey(v));
        if (cats != null) {
            for (String c : cats) {
                Set<TypedTuple<String>> t = redis.opsForZSet().reverseRangeWithScores(catHotKey(v, Long.parseLong(c)), 0, limit - 1);
                if (t != null) {
                    byCat.put(Long.parseLong(c), toScored(t));
                }
            }
        }
        HotRanker h = new HotRanker(g == null ? List.of() : toScored(g), byCat);
        cachedHot = h;
        cachedHotVersion = v;
        return h;
    }

    @Override
    public boolean tryLock(Duration ttl) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(LOCK_KEY, lockToken, ttl));
    }

    @Override
    public void unlock() {
        // 只删除自己持有的锁
        if (lockToken.equals(redis.opsForValue().get(LOCK_KEY))) {
            redis.delete(LOCK_KEY);
        }
    }
}
