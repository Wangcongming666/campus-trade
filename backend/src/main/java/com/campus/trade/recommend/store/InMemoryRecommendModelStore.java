package com.campus.trade.recommend.store;

import com.campus.trade.recommend.core.HotRanker;
import com.campus.trade.recommend.core.ItemSimilarityModel;
import com.campus.trade.recommend.core.ScoredItem;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** 单机内存存储（campus.recommend.store=memory），本地调试或没有 Redis 时使用。 */
@Component
@ConditionalOnProperty(prefix = "campus.recommend", name = "store", havingValue = "memory")
public class InMemoryRecommendModelStore implements RecommendModelStore {

    private volatile ItemSimilarityModel similarity;
    private volatile HotRanker hot = new HotRanker(List.of(), Map.of());
    private volatile String version;
    private final AtomicBoolean lock = new AtomicBoolean(false);

    @Override
    public void save(ItemSimilarityModel similarity, HotRanker hot, String version) {
        this.similarity = similarity;
        this.hot = hot;
        this.version = version;
    }

    @Override
    public String currentVersion() {
        return version;
    }

    @Override
    public Map<Long, List<ScoredItem>> neighbors(Collection<Long> goodsIds, int topK) {
        Map<Long, List<ScoredItem>> out = new HashMap<>();
        ItemSimilarityModel m = similarity;
        if (m == null) {
            return out;
        }
        for (Long id : goodsIds) {
            List<ScoredItem> l = m.neighbors(id);
            if (!l.isEmpty()) {
                out.put(id, l.subList(0, Math.min(topK, l.size())));
            }
        }
        return out;
    }

    @Override
    public HotRanker hot() {
        return hot;
    }

    @Override
    public boolean tryLock(Duration ttl) {
        return lock.compareAndSet(false, true);
    }

    @Override
    public void unlock() {
        lock.set(false);
    }
}
