package com.campus.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** GET /goods：keyword 可选。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GoodsQueryDTO extends GoodsFilterDTO {
    @Schema(description = "关键词（可选，标题/描述模糊匹配）")
    private String keyword;
}
