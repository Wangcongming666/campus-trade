package com.campus.trade.controller;

import com.campus.trade.common.Result;
import com.campus.trade.dto.LoginDTO;
import com.campus.trade.dto.RefreshDTO;
import com.campus.trade.dto.RegisterDTO;
import com.campus.trade.security.SecurityUtils;
import com.campus.trade.service.AuthService;
import com.campus.trade.service.CaptchaService;
import com.campus.trade.vo.CaptchaVO;
import com.campus.trade.vo.LoginVO;
import com.campus.trade.vo.TokenVO;
import com.campus.trade.vo.UserMeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "认证")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;

    @Operation(summary = "获取图形验证码（5 分钟有效，一次性）")
    @GetMapping("/captcha")
    public Result<CaptchaVO> captcha() {
        return Result.ok(captchaService.generate());
    }

    @Operation(summary = "注册")
    @PostMapping("/register")
    public Result<UserMeVO> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.ok(authService.register(dto));
    }

    @Operation(summary = "登录（account 可为用户名/手机号/学号）")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.ok(authService.login(dto));
    }

    @Operation(summary = "刷新 Token（refreshToken 轮换）")
    @PostMapping("/refresh")
    public Result<TokenVO> refresh(@Valid @RequestBody RefreshDTO dto) {
        return Result.ok(authService.refresh(dto));
    }

    @Operation(summary = "退出登录（accessToken 拉黑，删除 refreshToken）")
    @PostMapping("/logout")
    public Result<Void> logout(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false,
                    description = "可选：额外指定要作废的 refreshToken；不传则作废与当前 accessToken 配对的 refreshToken")
            @RequestBody(required = false) RefreshDTO dto) {
        authService.logout(SecurityUtils.requireUser(), dto == null ? null : dto.getRefreshToken());
        return Result.ok();
    }
}
