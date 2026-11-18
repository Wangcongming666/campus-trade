package com.campus.trade.vo;

import com.campus.trade.entity.ChatMessage;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessageVO {
    private Long id;
    private Long sessionId;
    private Long fromUserId;
    private Long toUserId;
    private Long goodsId;
    private String content;
    private Integer type;
    private Integer readFlag;
    private LocalDateTime createTime;

    public static ChatMessageVO of(ChatMessage m) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setId(m.getId());
        vo.setSessionId(m.getSessionId());
        vo.setFromUserId(m.getFromUserId());
        vo.setToUserId(m.getToUserId());
        vo.setGoodsId(m.getGoodsId());
        vo.setContent(m.getContent());
        vo.setType(m.getType());
        vo.setReadFlag(m.getReadFlag());
        vo.setCreateTime(m.getCreateTime());
        return vo;
    }
}
