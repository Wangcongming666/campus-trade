package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 模拟支付单（pay_order）。生成列 waiting_flag 仅用于数据库唯一约束，实体不映射。 */
@Data
@TableName("pay_order")
public class PayOrder {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String payNo;
    private Long orderId;
    private Long userId;
    private BigDecimal amount;
    /** WAITING / PAID / CLOSED */
    private String status;
    private LocalDateTime expireTime;
    private LocalDateTime payTime;
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
