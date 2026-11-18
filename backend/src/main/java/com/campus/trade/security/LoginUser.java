package com.campus.trade.security;

/**
 * 当前登录用户（SecurityContext principal）。
 *
 * @param userId    用户 ID
 * @param role      USER / ADMIN
 * @param jti       accessToken 唯一 ID（退出时加入黑名单）
 * @param refreshId 与该 accessToken 配对的 refreshToken 的 SHA-256 摘要
 * @param expireAt  accessToken 过期时间戳（毫秒）
 * @param issuedAt  accessToken 签发时间戳（毫秒），用于封禁后作废此前签发的全部 token
 */
public record LoginUser(Long userId, String role, String jti, String refreshId, long expireAt, long issuedAt) {
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
