package com.campus.trade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class GoodsStatusDTO {
    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "ON_SHELF|OFF_SHELF", message = "卖家只能将商品设为 ON_SHELF 或 OFF_SHELF")
    private String status;
}
