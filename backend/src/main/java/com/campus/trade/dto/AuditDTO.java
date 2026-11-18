package com.campus.trade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 审核：商品审核、学生认证审核共用；REJECT 时 reason 必填（服务层校验）。 */
@Data
public class AuditDTO {
    @NotBlank(message = "result 不能为空")
    @Pattern(regexp = "APPROVE|REJECT", message = "result 取值为 APPROVE/REJECT")
    private String result;
    @Size(max = 255, message = "原因不超过 255 字")
    private String reason;
}
