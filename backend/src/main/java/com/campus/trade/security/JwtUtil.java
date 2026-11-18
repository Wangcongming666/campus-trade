package com.campus.trade.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtUtil {

    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_REFRESH_ID = "rid";
    private static final String CLAIM_ISSUED_MS = "iatMs";

    private final JwtProperties props;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** 生成 accessToken，subject = userId。 */
    public String createAccessToken(Long userId, String role, String refreshId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
                .issuer(props.getIssuer())
                .subject(String.valueOf(userId))
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_REFRESH_ID, refreshId)
                .claim(CLAIM_ISSUED_MS, now)
                .issuedAt(new Date(now))
                .expiration(new Date(now + props.getAccessExpire() * 1000))
                .signWith(key())
                .compact();
    }

    /** 解析并校验签名与有效期；无效返回 null。 */
    public LoginUser parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key()).requireIssuer(props.getIssuer()).build()
                    .parseSignedClaims(token).getPayload();
            return new LoginUser(Long.valueOf(c.getSubject()), c.get(CLAIM_ROLE, String.class), c.getId(),
                    c.get(CLAIM_REFRESH_ID, String.class), c.getExpiration().getTime(), issuedMs(c));
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /** 毫秒级签发时间；兼容旧 token 时退回秒级 iat。 */
    private static long issuedMs(Claims c) {
        Long ms = c.get(CLAIM_ISSUED_MS, Long.class);
        if (ms != null) {
            return ms;
        }
        return c.getIssuedAt() == null ? 0L : c.getIssuedAt().getTime();
    }

    public long getAccessExpire() {
        return props.getAccessExpire();
    }

    public long getRefreshExpire() {
        return props.getRefreshExpire();
    }
}
