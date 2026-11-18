package com.campus.trade.recommend.service;

import com.campus.trade.entity.UserBehavior;
import com.campus.trade.recommend.core.Behavior;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** 实体 ↔ 纯 Java 算法对象转换（时区按规范 Asia/Shanghai）。 */
final class BehaviorConverter {

    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private BehaviorConverter() {
    }

    static Behavior toBehavior(UserBehavior e) {
        return new Behavior(e.getUserId(), e.getGoodsId(), e.getBehaviorType() == null ? 0 : e.getBehaviorType(),
                toMillis(e.getCreateTime()));
    }

    static long toMillis(LocalDateTime t) {
        return t == null ? 0 : t.atZone(ZONE).toInstant().toEpochMilli();
    }

    static LocalDateTime daysAgo(int days) {
        return LocalDateTime.now(ZONE).minusDays(days);
    }
}
