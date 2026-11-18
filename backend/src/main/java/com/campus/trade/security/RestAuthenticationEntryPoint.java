package com.campus.trade.security;

import com.campus.trade.common.ErrorCode;
import com.campus.trade.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 未登录 / Token 失效：HTTP 401 + {code:401}。 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final JsonResponseWriter writer;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        boolean tokenInvalid = Boolean.TRUE.equals(request.getAttribute(JwtAuthFilter.ATTR_TOKEN_INVALID));
        ErrorCode ec = tokenInvalid ? ErrorCode.TOKEN_EXPIRED : ErrorCode.UNAUTHORIZED;
        writer.write(response, HttpServletResponse.SC_UNAUTHORIZED, Result.fail(ec));
    }
}
