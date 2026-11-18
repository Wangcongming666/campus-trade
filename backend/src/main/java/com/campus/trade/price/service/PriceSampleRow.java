package com.campus.trade.price.service;

import lombok.Data;

import java.math.BigDecimal;

/** 价格样本查询结果行。 */
@Data
public class PriceSampleRow {
    private Long goodsId;
    private String title;
    private String condition;
    private BigDecimal originalPrice;
    private String status;
    private BigDecimal price;
}
