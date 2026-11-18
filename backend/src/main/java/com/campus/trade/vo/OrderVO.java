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
}
