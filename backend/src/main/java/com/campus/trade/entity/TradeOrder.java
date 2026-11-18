package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("trade_order")
public class TradeOrder {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long goodsId;
    private Long buyerId;
    private Long sellerId;
    /** 下单时价格快照 */
    private BigDecimal price;
    private String remark;
    private String status;
    private String cancelReason;
    private LocalDateTime payTime;
    private LocalDateTime completeTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
