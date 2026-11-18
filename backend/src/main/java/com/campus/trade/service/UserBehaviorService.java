package com.campus.trade.service;

import com.campus.trade.common.Constants;
import com.campus.trade.config.AsyncConfig;
import com.campus.trade.entity.UserBehavior;
import com.campus.trade.mapper.UserBehaviorMapper;
import com.campus.trade.security.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 用户行为日志（user_behavior），供推荐算法使用。全部方法异步执行，失败只记日志不影响主流程；userId 为 null（游客）直接跳过。
 * <ul>
 *   <li>{@link #recordView}：商品详情，type=1，同一用户同一商品 30 分钟去重（Redis behavior:view:{userId}:{goodsId}）</li>
 *   <li>{@link #recordFavorite}：收藏成功，type=2</li>
 *   <li>{@link #recordChat}：发起聊天，type=3（phase 2 在 POST /chat/sessions 中调用）</li>
 *   <li>{@link #recordOrder}：下单成功，type=4（phase 2 在 POST /orders 中调用）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserBehaviorService {

    public static final Duration VIEW_DEDUPE_TTL = Duration.ofMinutes(30);

    private final UserBehaviorMapper behaviorMapper;
    private final StringRedisTemplate redis;

    @Async(AsyncConfig.BEHAVIOR_EXECUTOR)
    public void recordView(Long userId, Long goodsId) {
        if (userId == null || goodsId == null) {
            return;
        }
        try {
            Boolean first = redis.opsForValue().setIfAbsent(RedisKeys.behaviorView(userId, goodsId), "1", VIEW_DEDUPE_TTL);
            if (!Boolean.TRUE.equals(first)) {
                return;
            }
            insert(userId, goodsId, Constants.BehaviorType.VIEW);
        } catch (Exception e) {
            log.warn("record view behavior failed, userId={}, goodsId={}: {}", userId, goodsId, e.getMessage());
        }
    }

    @Async(AsyncConfig.BEHAVIOR_EXECUTOR)
    public void recordFavorite(Long userId, Long goodsId) {
        safeInsert(userId, goodsId, Constants.BehaviorType.FAVORITE);
    }

    @Async(AsyncConfig.BEHAVIOR_EXECUTOR)
    public void recordChat(Long userId, Long goodsId) {
        safeInsert(userId, goodsId, Constants.BehaviorType.CHAT);
    }

    @Async(AsyncConfig.BEHAVIOR_EXECUTOR)
    public void recordOrder(Long userId, Long goodsId) {
        safeInsert(userId, goodsId, Constants.BehaviorType.ORDER);
    }

    private void safeInsert(Long userId, Long goodsId, int type) {
        if (userId == null || goodsId == null) {
            return;
        }
        try {
            insert(userId, goodsId, type);
        } catch (Exception e) {
            log.warn("record behavior failed, type={}, userId={}, goodsId={}: {}", type, userId, goodsId, e.getMessage());
        }
    }

    private void insert(Long userId, Long goodsId, int type) {
        UserBehavior b = new UserBehavior();
        b.setUserId(userId);
        b.setGoodsId(goodsId);
        b.setBehaviorType(type);
        b.setCreateTime(LocalDateTime.now());
        behaviorMapper.insert(b);
    }
}
