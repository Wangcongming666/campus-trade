package com.campus.trade.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReviewCreateDTO {
    @NotNull(message = "订单 ID 不能为空")
    private Long orderId;
    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分为 1-5 分")
    @Max(value = 5, message = "评分为 1-5 分")
    private Integer score;
    @Size(max = 500, message = "评价内容不超过 500 字")
    private String content;
}
