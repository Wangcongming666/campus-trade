package com.campus.trade.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 在线状态推送 /user/queue/presence：{userId, online}。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PresenceVO {
    private Long userId;
    private boolean online;
}
