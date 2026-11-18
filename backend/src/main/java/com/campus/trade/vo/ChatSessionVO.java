package com.campus.trade.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatSessionVO {
    private Long id;
    private Long goodsId;
    private String goodsTitle;
    private String goodsCover;
    private UserBriefVO peer;
    private String lastContent;
    private LocalDateTime lastTime;
    /** 当前用户在该会话的未读数 */
    private int unreadCount;
}
