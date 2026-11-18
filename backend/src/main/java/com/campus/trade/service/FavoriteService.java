package com.campus.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.trade.common.BizException;
import com.campus.trade.common.Constants.GoodsStatus;
import com.campus.trade.common.ErrorCode;
import com.campus.trade.entity.Favorite;
import com.campus.trade.entity.Goods;
import com.campus.trade.mapper.FavoriteMapper;
import com.campus.trade.mapper.GoodsMapper;
import com.campus.trade.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/** 收藏 / 取消收藏（幂等），同步维护 goods.favorite_count；新增收藏异步写 behavior_type=2。 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final GoodsMapper goodsMapper;
    private final UserBehaviorService behaviorService;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> add(Long goodsId) {
        Long uid = SecurityUtils.requireUserId();
        Goods g = goodsMapper.selectById(goodsId);
        if (g == null) {
            throw new BizException(ErrorCode.GOODS_NOT_FOUND);
        }
        boolean mine = uid.equals(g.getSellerId());
        if (!mine && !GoodsStatus.ON_SHELF.equals(g.getStatus()) && !GoodsStatus.RESERVED.equals(g.getStatus())) {
            throw new BizException(GoodsStatus.OFF_SHELF.equals(g.getStatus()) || GoodsStatus.SOLD.equals(g.getStatus())
                    ? ErrorCode.GOODS_OFF_SHELF : ErrorCode.GOODS_NOT_FOUND);
        }
        boolean created = false;
        if (!favoriteMapper.exists(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, uid).eq(Favorite::getGoodsId, goodsId))) {
            Favorite f = new Favorite();
            f.setUserId(uid);
            f.setGoodsId(goodsId);
            try {
                favoriteMapper.insert(f);
                created = true;
            } catch (DuplicateKeyException ignored) {
                // 并发重复收藏，视为已收藏
            }
        }
        if (created) {
            goodsMapper.incrFavoriteCount(goodsId, 1);
            behaviorService.recordFavorite(uid, goodsId);
        }
        return result(goodsId, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> remove(Long goodsId) {
        Long uid = SecurityUtils.requireUserId();
        int n = favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, uid).eq(Favorite::getGoodsId, goodsId));
        if (n > 0) {
            goodsMapper.incrFavoriteCount(goodsId, -1);
        }
        return result(goodsId, false);
    }

    private Map<String, Object> result(Long goodsId, boolean favorited) {
        Goods g = goodsMapper.selectById(goodsId);
        return Map.of("favorited", favorited, "favoriteCount", g == null ? 0 : g.getFavoriteCount());
    }
}
