package com.campus.trade.dto;

import com.campus.trade.common.Constants;
import com.campus.trade.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 商品列表/搜索共用筛选条件；keyword 由子类定义（列表可选、搜索必填）。 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class GoodsFilterDTO extends PageQuery {
    @Schema(description = "分类 ID（一级分类会包含其子分类）")
    private Long categoryId;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    @Schema(description = "成色 NEW/LIKE_NEW/GOOD/FAIR/POOR")
    @Pattern(regexp = Constants.CONDITION_REGEX, message = "成色取值为 NEW/LIKE_NEW/GOOD/FAIR/POOR")
    private String condition;
    @Schema(description = "latest（默认）| price_asc | price_desc | hot")
    @Pattern(regexp = "latest|price_asc|price_desc|hot", message = "sort 取值为 latest/price_asc/price_desc/hot")
    private String sort;

    /** 关键词（标题/描述模糊匹配） */
    public abstract String getKeyword();
}
