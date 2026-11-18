package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_session")
public class ChatSession {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long goodsId;
    private Long initiatorId;
    private Long peerId;
    /** 生成列 LEAST(initiator_id, peer_id)：只读，不参与 INSERT/UPDATE */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Long userLowId;
    /** 生成列 GREATEST(initiator_id, peer_id)：只读，不参与 INSERT/UPDATE */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Long userHighId;
    private String lastContent;
    private LocalDateTime lastTime;
    private Integer initiatorUnread;
    private Integer peerUnread;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
