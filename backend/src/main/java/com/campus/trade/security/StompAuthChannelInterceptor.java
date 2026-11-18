package com.campus.trade.security;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * STOMP 入站鉴权：CONNECT 帧必须携带 Authorization: Bearer &lt;accessToken&gt;（有效、未退出、未封禁），
 * 否则拒绝连接（客户端收到 ERROR 帧）。SUBSCRIBE 仅允许 /user/queue/**；SEND 需已认证。
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER = "Bearer ";

    private final JwtUtil jwtUtil;
    private final JwtAuthFilter jwtAuthFilter;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (acc == null || acc.getCommand() == null) {
            return message;
        }
        StompCommand cmd = acc.getCommand();
        if (StompCommand.CONNECT.equals(cmd) || StompCommand.STOMP.equals(cmd)) {
            String header = acc.getFirstNativeHeader("Authorization");
            if (header == null || !header.startsWith(BEARER)) {
                throw new MessageDeliveryException("未登录：CONNECT 需携带 Authorization: Bearer <token>");
            }
            LoginUser user = jwtUtil.parse(header.substring(BEARER.length()).trim());
            if (user == null || !jwtAuthFilter.isActive(user)) {
                throw new MessageDeliveryException("登录已过期");
            }
            acc.setUser(new StompPrincipal(user));
        } else if (StompCommand.SUBSCRIBE.equals(cmd)) {
            requireUser(acc);
            String dest = acc.getDestination();
            if (dest == null || !dest.startsWith("/user/queue/")) {
                throw new MessageDeliveryException("只允许订阅 /user/queue/**");
            }
        } else if (StompCommand.SEND.equals(cmd)) {
            requireUser(acc);
        }
        return message;
    }

    private static void requireUser(StompHeaderAccessor acc) {
        if (!(acc.getUser() instanceof StompPrincipal)) {
            throw new MessageDeliveryException("未登录");
        }
    }
}
