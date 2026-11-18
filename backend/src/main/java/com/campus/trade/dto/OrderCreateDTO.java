package com.campus.trade.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrderCreateDTO {
    @NotNull(message = "商品 ID 不能为空")
    private Long goodsId;
    @Size(max = 255, message = "备注不超过 255 字")
    private String remark;
}
