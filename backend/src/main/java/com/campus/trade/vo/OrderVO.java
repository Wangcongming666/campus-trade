package com.campus.trade.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderVO {
    private Long id;
    private Long goodsId;
    private String goodsTitle;
    private String goodsCover;
    private UserBriefVO buyer;
    private UserBriefVO seller;
    private BigDecimal price;
    private String remark;
    private String status;
    private String cancelReason;
    private LocalDateTime payTime;
    private LocalDateTime completeTime;
    private LocalDateTime createTime;
    /** 当前用户是否已评价该订单 */
    private boolean reviewed;
    /** 待付款订单的 WAITING 支付单号（POST /pay/{payNo}/confirm 使用），其他状态为 null */
    private String payNo;
    /** 支付截止时间（下单时间 + 15 分钟），超时自动取消；非待付款为 null */
    private LocalDateTime payExpireTime;
}
