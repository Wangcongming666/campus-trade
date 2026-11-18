package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long sessionId;
    private Long fromUserId;
    private Long toUserId;
    private Long goodsId;
    private String content;
    /** 1 文本 2 图片 3 商品卡片 */
    private Integer type;
    /** 0 未读 1 已读 */
    private Integer readFlag;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
