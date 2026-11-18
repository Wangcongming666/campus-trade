package com.campus.trade.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 刷新 Token 返回 {accessToken, refreshToken, expiresIn}（refreshToken 已轮换）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenVO {
    private String accessToken;
    private String refreshToken;
    /** accessToken 有效期（秒） */
    private long expiresIn;
}
