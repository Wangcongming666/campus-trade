package com.campus.trade.price.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 价格建议结果：金额均为两位小数 BigDecimal。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriceSuggestVO {
    private BigDecimal suggestedPrice;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    /** 参与计算的同类样本数（去异常值后） */
    private Integer sampleCount;
    /** 中文说明：依据了哪些样本、如何折算 */
    private String reason;
}
