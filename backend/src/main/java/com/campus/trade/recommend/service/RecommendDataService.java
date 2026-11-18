package com.campus.trade.recommend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.trade.common.Constants;
import com.campus.trade.entity.Category;
import com.campus.trade.entity.Goods;
import com.campus.trade.entity.UserBehavior;
import com.campus.trade.mapper.GoodsMapper;
import com.campus.trade.mapper.UserBehaviorMapper;
import com.campus.trade.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 推荐模块的取数封装：直接复用后端的 UserBehaviorMapper / GoodsMapper（BaseMapper + LambdaQueryWrapper），
 * 不新增 Mapper、不写列名字符串，表结构以后端实体为准。
 * 已删除商品由 Goods 上的 @TableLogic 自动过滤。
 */
@Component
@RequiredArgsConstructor
public class RecommendDataService {

    private static final int ID_BATCH = 1000;

    private final UserBehaviorMapper behaviorMapper;
    private final GoodsMapper goodsMapper;
    private final CategoryService categoryService;

    /** 按主键游标分页扫描 since 之后的行为（离线训练）。 */
    public List<UserBehavior> scanBehaviors(long afterId, LocalDateTime since, int limit) {
        return behaviorMapper.selectList(new LambdaQueryWrapper<UserBehavior>()
                .gt(UserBehavior::getId, afterId)
                .ge(UserBehavior::getCreateTime, since)
                .orderByAsc(UserBehavior::getId)
                .last("LIMIT " + limit));
    }

    /** 用户最近行为（在线兴趣）。 */
    public List<UserBehavior> recentBehaviors(long userId, LocalDateTime since, int limit) {
        return behaviorMapper.selectList(new LambdaQueryWrapper<UserBehavior>()
                .eq(UserBehavior::getUserId, userId)
                .ge(UserBehavior::getCreateTime, since)
                .orderByDesc(UserBehavior::getCreateTime)
                .last("LIMIT " + limit));
    }

    /** 用户交互过的全部商品（不限时间窗口），用于过滤。 */
    public List<Long> interactedGoodsIds(long userId, int limit) {
        return behaviorMapper.selectObjs(new LambdaQueryWrapper<UserBehavior>()
                        .select(UserBehavior::getGoodsId)
                        .eq(UserBehavior::getUserId, userId)
                        .groupBy(UserBehavior::getGoodsId)
                        .last("LIMIT " + limit))
                .stream().filter(Objects::nonNull).map(o -> ((Number) o).longValue()).toList();
    }

    /**
     * 用户只浏览过（没有收藏、聊天、下单）的商品，按最近一次浏览时间倒序（首页最后一层兜底用）。
     * 只看最近 limit 条行为；某商品只要出现过非浏览行为就排除。
     */
    public List<Long> viewOnlyGoodsIds(long userId, int limit) {
        List<UserBehavior> rows = behaviorMapper.selectList(new LambdaQueryWrapper<UserBehavior>()
                .select(UserBehavior::getGoodsId, UserBehavior::getBehaviorType, UserBehavior::getCreateTime)
                .eq(UserBehavior::getUserId, userId)
                .orderByDesc(UserBehavior::getCreateTime).orderByDesc(UserBehavior::getId)
                .last("LIMIT " + limit));
        java.util.Set<Long> stronger = new java.util.HashSet<>();
        for (UserBehavior b : rows) {
            if (b.getGoodsId() != null && !Objects.equals(b.getBehaviorType(), Constants.BehaviorType.VIEW)) {
                stronger.add(b.getGoodsId());
            }
        }
        LinkedHashSet<Long> out = new LinkedHashSet<>();
        for (UserBehavior b : rows) {
            if (b.getGoodsId() != null && !stronger.contains(b.getGoodsId())) {
                out.add(b.getGoodsId()); // 行已按时间倒序，首次出现即最近一次浏览
            }
        }
        return new ArrayList<>(out);
    }

    /** 商品基础字段（id、卖家、分类、标题、状态、发布时间），分批 IN 查询。 */
    public Map<Long, Goods> basicGoods(Collection<Long> ids) {
        List<Long> all = new ArrayList<>(new LinkedHashSet<>(ids));
        Map<Long, Goods> out = new HashMap<>(all.size() * 2);
        for (int i = 0; i < all.size(); i += ID_BATCH) {
            goodsMapper.selectList(basicColumns().in(Goods::getId, all.subList(i, Math.min(all.size(), i + ID_BATCH))))
                    .forEach(g -> out.put(g.getId(), g));
        }
        return out;
    }

    /** 最近发布的上架商品（内容相似候选池），categoryIds 为空表示不限分类。 */
    public List<Goods> recentOnShelf(LocalDateTime since, Collection<Long> categoryIds, int limit) {
        LambdaQueryWrapper<Goods> w = basicColumns()
                .eq(Goods::getStatus, Constants.GoodsStatus.ON_SHELF)
                .ge(Goods::getCreateTime, since);
        if (categoryIds != null && !categoryIds.isEmpty()) {
            w.in(Goods::getCategoryId, categoryIds);
        }
        return goodsMapper.selectList(w.orderByDesc(Goods::getCreateTime).last("LIMIT " + limit));
    }

    /** 完整商品行（供 GoodsService.toListVOs 组装），按 ids 顺序返回，不存在/已删除的跳过。 */
    public List<Goods> fullGoodsInOrder(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Goods> m = new HashMap<>();
        goodsMapper.selectBatchIds(ids).forEach(g -> m.put(g.getId(), g));
        List<Goods> out = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Goods g = m.get(id);
            if (g != null) {
                out.add(g);
            }
        }
        return out;
    }

    public Goods goods(long id) {
        return goodsMapper.selectById(id);
    }

    /** 启用分类的 id → parentId（父分类为 null 表示一级分类）。 */
    public Map<Long, Long> enabledCategoryParents() {
        Map<Long, Long> m = new HashMap<>();
        for (Category c : categoryService.listEnabled()) {
            m.put(c.getId(), c.getParentId());
        }
        return m;
    }

    /** 分类自身及其所有祖先（自下而上），未知/停用分类只返回自身。 */
    public static List<Long> selfAndAncestors(Long categoryId, Map<Long, Long> parents) {
        List<Long> chain = new ArrayList<>(3);
        Long cur = categoryId;
        while (cur != null && !chain.contains(cur)) {
            chain.add(cur);
            cur = parents.get(cur);
        }
        return chain;
    }

    /** 一级分类（根）。 */
    public static Long rootOf(Long categoryId, Map<Long, Long> parents) {
        List<Long> chain = selfAndAncestors(categoryId, parents);
        return chain.isEmpty() ? null : chain.get(chain.size() - 1);
    }

    public CategoryService categoryService() {
        return categoryService;
    }

    private static LambdaQueryWrapper<Goods> basicColumns() {
        return new LambdaQueryWrapper<Goods>().select(Goods::getId, Goods::getSellerId, Goods::getCategoryId,
                Goods::getTitle, Goods::getStatus, Goods::getCreateTime);
    }
}
