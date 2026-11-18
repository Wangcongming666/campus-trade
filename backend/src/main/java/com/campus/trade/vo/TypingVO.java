package com.campus.trade.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 「正在输入」推送 /user/queue/typing：{sessionId, userId}，userId 为正在输入的一方。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TypingVO {
    private Long sessionId;
    private Long userId;
}
