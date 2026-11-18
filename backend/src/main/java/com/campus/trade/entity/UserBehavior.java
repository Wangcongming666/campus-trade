package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_behavior")
public class UserBehavior {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long goodsId;
    /** 1 浏览 2 收藏 3 聊天 4 下单 */
    private Integer behaviorType;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
