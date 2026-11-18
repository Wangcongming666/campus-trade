package com.campus.trade.recommend.service;

import com.campus.trade.common.Constants;
import com.campus.trade.entity.Goods;
import com.campus.trade.entity.UserBehavior;
import com.campus.trade.recommend.config.RecommendProperties;
import com.campus.trade.recommend.core.Behavior;
import com.campus.trade.recommend.core.BehaviorWeightConfig;
import com.campus.trade.recommend.core.HotRanker;
import com.campus.trade.recommend.core.InteractionMatrix;
import com.campus.trade.recommend.core.ItemCF;
import com.campus.trade.recommend.core.ItemSimilarityModel;
import com.campus.trade.recommend.core.ScoredItem;
import com.campus.trade.recommend.store.RecommendModelStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 离线训练：扫描行为 → 聚合兴趣 → ItemCF-IUF 相似度矩阵(TopK) + 带时间衰减的热度榜 → 写入存储（Redis）。
 * 分类热度榜同时挂到商品分类的所有祖先分类上，因此用户在注册时选一级分类（如"数码电子"）也能命中二级分类商品。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendOfflineService {

    private static final int PAGE = 5000;

    private final RecommendDataService data;
    private final RecommendModelStore store;
    private final RecommendProperties props;
    private final BehaviorWeightConfig interestWeightConfig;

    /** 存储中还没有任何模型版本时才计算（启动时调用）。 */
    public boolean rebuildIfAbsent() {
        return store.currentVersion() == null && rebuild();
    }

    /** @return 是否真正执行（拿不到锁说明其他实例正在跑） */
    public boolean rebuild() {
        if (!store.tryLock(Duration.ofMinutes(30))) {
            log.info("[recommend] 离线任务已在其他实例运行，跳过");
            return false;
        }
        long start = System.currentTimeMillis();
        try {
            long now = System.currentTimeMillis();
            List<Behavior> behaviors = loadBehaviors(BehaviorConverter.daysAgo(props.getTrainWindowDays()));

            // 1. ItemCF-IUF 相似度矩阵
            Map<Long, Map<Long, Double>> interest = interestWeightConfig.aggregate(behaviors, now);
            ItemSimilarityModel model = ItemCF.fit(InteractionMatrix.of(interest), props.getTopK(), props.isIuf(),
                    props.isNormalize());

            // 2. 热度榜：只统计 ON_SHELF 且未删除的商品；热度用独立半衰期，不做对数压缩/封顶
            Set<Long> ids = new HashSet<>();
            behaviors.forEach(b -> ids.add(b.itemId()));
            Map<Long, Long> onSaleCategory = new HashMap<>();
            for (Goods g : data.basicGoods(ids).values()) {
                if (Constants.GoodsStatus.ON_SHELF.equals(g.getStatus()) && g.getCategoryId() != null) {
                    onSaleCategory.put(g.getId(), g.getCategoryId());
                }
            }
            List<Behavior> onSale = behaviors.stream().filter(b -> onSaleCategory.containsKey(b.itemId())).toList();
            BehaviorWeightConfig hotCfg = interestWeightConfig.withLogScale(false)
                    .withMaxPerPair(Double.POSITIVE_INFINITY).withHalfLifeDays(props.getHotHalfLifeDays());
            HotRanker leaf = HotRanker.build(onSale, hotCfg, now, onSaleCategory);
            HotRanker hot = withAncestors(leaf, onSaleCategory, data.enabledCategoryParents());

            String version = LocalDateTime.now(BehaviorConverter.ZONE).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            store.save(model, hot, version);
            log.info("[recommend] 离线模型 {} 完成：行为 {} 条，用户 {}，有邻居商品 {}，热度榜 {}，耗时 {} ms",
                    version, behaviors.size(), interest.size(), model.itemCount(), hot.global().size(),
                    System.currentTimeMillis() - start);
            return true;
        } catch (RuntimeException e) {
            log.error("[recommend] 离线模型计算失败，继续使用旧版本", e);
            return false;
        } finally {
            store.unlock();
        }
    }

    /** 把每个商品的热度同时计入其分类的所有祖先分类榜（保持全局热度顺序）。 */
    static HotRanker withAncestors(HotRanker leaf, Map<Long, Long> itemCategory, Map<Long, Long> parents) {
        Map<Long, List<ScoredItem>> byCat = new LinkedHashMap<>();
        for (ScoredItem s : leaf.global()) {
            Long cat = itemCategory.get(s.itemId());
            for (Long c : RecommendDataService.selfAndAncestors(cat, parents)) {
                byCat.computeIfAbsent(c, k -> new ArrayList<>()).add(s);
            }
        }
        return new HotRanker(leaf.global(), byCat);
    }

    private List<Behavior> loadBehaviors(LocalDateTime since) {
        List<Behavior> list = new ArrayList<>();
        long lastId = 0;
        while (true) {
            List<UserBehavior> page = data.scanBehaviors(lastId, since, PAGE);
            for (UserBehavior e : page) {
                list.add(BehaviorConverter.toBehavior(e));
            }
            if (page.size() < PAGE) {
                break;
            }
            lastId = page.get(page.size() - 1).getId();
        }
        return list;
    }
}
