package com.campus.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** GET /goods/search：keyword 必填。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GoodsSearchDTO extends GoodsFilterDTO {
    @Schema(description = "关键词（必填，标题/描述模糊匹配）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "关键词不能为空")
    private String keyword;
}
