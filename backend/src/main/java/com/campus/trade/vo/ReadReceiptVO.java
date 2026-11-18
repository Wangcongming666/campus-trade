package com.campus.trade.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 已读回执。STOMP 推送到对方 /user/queue/read 的载荷为 {sessionId, readerId}；
 * HTTP PATCH /chat/sessions/{id}/read 额外返回 readCount（本次标记为已读的条数）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReadReceiptVO {
    private Long sessionId;
    /** 读消息的一方 */
    private Long readerId;
    private Integer readCount;
}
