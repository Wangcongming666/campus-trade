package com.campus.trade.security;

public final class RedisKeys {
    private RedisKeys() {
    }

    public static String captcha(String key) {
        return "captcha:" + key;
    }

    /** sha256(refreshToken) -> userId，TTL 7 天 */
    public static String refresh(String refreshId) {
        return "auth:refresh:" + refreshId;
    }

    /** 用户当前有效的 refreshId 集合（封禁时批量作废） */
    public static String userRefreshSet(Long userId) {
        return "auth:user-refresh:" + userId;
    }

    /** 被封禁用户标记：存在时其 accessToken 立即失效 */
    public static String banned(Long userId) {
        return "auth:banned:" + userId;
    }

    /** 用户 token 作废时间点（毫秒）：签发早于该时间的 accessToken 一律无效（封禁时写入，解封不删除），TTL = accessToken 有效期 */
    public static String revokedBefore(Long userId) {
        return "auth:revoked-before:" + userId;
    }

    /** accessToken 黑名单（jti），TTL 至 token 过期 */
    public static String blacklist(String jti) {
        return "auth:blacklist:" + jti;
    }

    public static String behaviorView(Long userId, Long goodsId) {
        return "behavior:view:" + userId + ":" + goodsId;
    }
}
