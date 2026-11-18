package com.campus.trade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrderActionDTO {
    @NotBlank(message = "action 不能为空")
    @Pattern(regexp = "PAY|CONFIRM|CANCEL|REFUND", message = "action 取值为 PAY/CONFIRM/CANCEL/REFUND")
    private String action;
    @Size(max = 255, message = "原因不超过 255 字")
    private String reason;
}
