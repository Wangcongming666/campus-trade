package com.campus.trade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReportCreateDTO {
    @NotBlank(message = "targetType 不能为空")
    @Pattern(regexp = "GOODS|USER", message = "targetType 取值为 GOODS/USER")
    private String targetType;
    @NotNull(message = "targetId 不能为空")
    private Long targetId;
    @NotBlank(message = "举报原因不能为空")
    @Size(max = 64, message = "举报原因不超过 64 字")
    private String reason;
    @Size(max = 500, message = "补充说明不超过 500 字")
    private String detail;
}
