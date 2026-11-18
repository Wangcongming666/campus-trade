package com.campus.trade.price.dto;

import com.campus.trade.common.Constants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** POST /api/v1/price/suggest 请求体。 */
@Data
public class PriceSuggestDTO {
    @NotNull(message = "分类不能为空")
    private Long categoryId;
    @NotBlank(message = "成色不能为空")
    @Pattern(regexp = Constants.CONDITION_REGEX, message = "成色取值为 NEW/LIKE_NEW/GOOD/FAIR/POOR")
    private String condition;
    @Schema(description = "原价，可选；填写后建议价不会超过原价")
    @DecimalMin(value = "0.01", message = "原价必须大于 0")
    @Digits(integer = 8, fraction = 2, message = "原价最多 8 位整数、2 位小数")
    private BigDecimal originalPrice;
    @Schema(description = "标题，可选；用于给标题相似的同类商品更高权重")
    @Size(max = 120, message = "标题不超过 120 字")
    private String title;
}
