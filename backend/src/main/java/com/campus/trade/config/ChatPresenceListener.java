package com.campus.trade.config;

import com.campus.trade.security.StompPrincipal;
import com.campus.trade.service.ChatPresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * 监听 STOMP 连接事件维护在线状态。使用 SessionConnectedEvent（服务端已回 CONNECTED、鉴权拦截器已设置用户）
 * 而非 SessionConnectEvent：后者在 ChannelInterceptor 鉴权之前发布，此时拿不到用户，且被拒绝的 CONNECT 也会触发。
 */
@Component
@RequiredArgsConstructor
public class ChatPresenceListener {

    private final ChatPresenceService presenceService;

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        Long uid = userId(event.getUser());
        String sid = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
        presenceService.onConnect(uid, sid);
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        presenceService.onDisconnect(userId(event.getUser()), event.getSessionId());
    }

    private static Long userId(Principal p) {
        return p instanceof StompPrincipal sp ? sp.loginUser().userId() : null;
    }
}
