package com.campus.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginDTO {
    @Schema(description = "用户名 / 手机号 / 学号")
    @NotBlank(message = "账号不能为空")
    private String account;
    @NotBlank(message = "密码不能为空")
    private String password;
    @Schema(description = "验证码 key（GET /auth/captcha 返回）")
    private String captchaKey;
    private String captchaCode;
}
