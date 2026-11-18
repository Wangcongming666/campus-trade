package com.campus.trade.dto;

import com.campus.trade.common.Constants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** POST /goods、PUT /goods/{id} 请求体。 */
@Data
public class GoodsSaveDTO {
    @NotBlank(message = "标题不能为空")
    @Size(max = 120, message = "标题不超过 120 字")
    private String title;
    @NotBlank(message = "描述不能为空")
    @Size(max = 5000, message = "描述不超过 5000 字")
    private String description;
    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.00", message = "价格不能为负")
    @Digits(integer = 8, fraction = 2, message = "价格最多 8 位整数、2 位小数")
    private BigDecimal price;
    @DecimalMin(value = "0.00", message = "原价不能为负")
    @Digits(integer = 8, fraction = 2, message = "原价最多 8 位整数、2 位小数")
    private BigDecimal originalPrice;
    @NotBlank(message = "成色不能为空")
    @Pattern(regexp = Constants.CONDITION_REGEX, message = "成色取值为 NEW/LIKE_NEW/GOOD/FAIR/POOR")
    private String condition;
    @Schema(description = "FACE（默认）/ EXPRESS / EITHER")
    @Pattern(regexp = Constants.TRADE_METHOD_REGEX, message = "交易方式取值为 FACE/EXPRESS/EITHER")
    private String tradeMethod;
    @NotNull(message = "分类不能为空")
    private Long categoryId;
    @Schema(description = "图片 URL 列表（POST /files/upload 返回的 url），按顺序，第一张为封面，最多 9 张")
    @Size(max = 9, message = "商品图片最多 9 张")
    private List<@NotBlank(message = "图片地址不能为空") @Size(max = 512, message = "图片地址过长") String> images;

    @Schema(description = "客户端可传，但服务端忽略：cover_url 始终取 images[0]", accessMode = Schema.AccessMode.READ_ONLY)
    private String coverUrl;
}
