package com.campus.trade.security;

import java.security.Principal;

/** STOMP 会话用户：name = userId（用于 /user/{userId}/queue/** 路由）。 */
public record StompPrincipal(LoginUser loginUser) implements Principal {
    @Override
    public String getName() {
        return String.valueOf(loginUser.userId());
    }
}
