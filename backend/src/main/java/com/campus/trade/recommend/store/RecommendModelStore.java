package com.campus.trade.recommend.store;

import com.campus.trade.recommend.core.HotRanker;
import com.campus.trade.recommend.core.ItemSimilarityModel;
import com.campus.trade.recommend.core.ScoredItem;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 离线模型（相似度矩阵 + 热度榜）的存储抽象：Redis（多实例共享）或内存（单机调试）。 */
public interface RecommendModelStore {

    /** 保存一个新版本并切换为当前版本。 */
    void save(ItemSimilarityModel similarity, HotRanker hot, String version);

    /** 当前版本号；尚无模型返回 null。 */
    String currentVersion();

    /** 批量取商品的 TopK 邻居（降序）；没有邻居的商品不出现在结果中。 */
    Map<Long, List<ScoredItem>> neighbors(Collection<Long> goodsIds, int topK);

    /** 当前热度榜（全局 + 分类）；尚无模型返回空榜。 */
    HotRanker hot();

    /** 离线任务分布式锁，避免多实例重复计算。 */
    boolean tryLock(Duration ttl);

    void unlock();
}
