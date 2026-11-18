package com.campus.trade.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    /** HS256 密钥，至少 32 字节 */
    private String secret;
    /** accessToken 有效期（秒），默认 2 小时 */
    private long accessExpire = 7200;
    /** refreshToken 有效期（秒），默认 7 天 */
    private long refreshExpire = 604800;
    private String issuer = "campus-trade";
}
