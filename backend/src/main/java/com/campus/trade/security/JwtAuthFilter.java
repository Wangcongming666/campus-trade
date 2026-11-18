package com.campus.trade.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 解析 Authorization: Bearer &lt;accessToken&gt;。
 * Token 无效/过期/已退出时不直接拒绝：白名单接口按游客处理，受保护接口由 EntryPoint 返回 401「登录已过期」。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String ATTR_TOKEN_INVALID = "campus.tokenInvalid";
    private static final String BEARER = "Bearer ";

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redis;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER)) {
            String token = header.substring(BEARER.length()).trim();
            LoginUser user = jwtUtil.parse(token);
            if (user != null && isActive(user)) {
                var auth = new UsernamePasswordAuthenticationToken(user, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } else {
                request.setAttribute(ATTR_TOKEN_INVALID, Boolean.TRUE);
            }
        }
        chain.doFilter(request, response);
    }

    /** 未退出（不在黑名单）且未被封禁。STOMP CONNECT 校验复用。 */
    public boolean isActive(LoginUser user) {
        Long n = redis.countExistingKeys(List.of(RedisKeys.blacklist(user.jti()), RedisKeys.banned(user.userId())));
        if (n != null && n > 0) {
            return false;
        }
        // 封禁前签发的 token 在解封后也不能复活，必须重新登录
        String before = redis.opsForValue().get(RedisKeys.revokedBefore(user.userId()));
        return before == null || user.issuedAt() >= Long.parseLong(before);
    }
}
