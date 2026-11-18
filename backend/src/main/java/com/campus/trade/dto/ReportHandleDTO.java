package com.campus.trade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReportHandleDTO {
    @NotBlank(message = "result 不能为空")
    @Pattern(regexp = "APPROVE|REJECT", message = "result 取值为 APPROVE/REJECT")
    private String result;
    @Size(max = 255, message = "处理说明不超过 255 字")
    private String remark;
}
