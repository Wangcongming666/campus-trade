package com.campus.trade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.campus.trade.service.ChatPresenceService;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** 在线状态与「正在输入」：真实端口 + WebSocketStompClient。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestDatabaseInitializer.class)
class ChatPresenceStompTest {

    @LocalServerPort
    int port;
    @Autowired
    ObjectMapper om;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    ChatPresenceService presenceService;

    private final RestTemplate rest = new RestTemplate(new org.springframework.http.client.JdkClientHttpRequestFactory());
    private final List<StompSession> sessions = new ArrayList<>();
    private WebSocketStompClient client;

    @AfterEach
    void tearDown() {
        sessions.forEach(s -> {
            if (s.isConnected()) {
                s.disconnect();
            }
        });
        if (client != null) {
            client.stop();
        }
    }

    private String base() {
        return "http://localhost:" + port;
    }

    private JsonNode call(HttpMethod method, String path, String token, Object body) throws Exception {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            h.setBearerAuth(token);
        }
        ResponseEntity<String> r = rest.exchange(base() + path, method,
                new HttpEntity<>(body == null ? null : om.writeValueAsString(body), h), String.class);
        JsonNode n = om.readTree(r.getBody());
        assertEquals(200, n.get("code").asInt(), n.toString());
        return n.get("data");
    }

    private String newUser() throws Exception {
        String name = "ws_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        call(HttpMethod.POST, "/api/v1/auth/register", null, Map.of("username", name, "password", "123456"));
        return call(HttpMethod.POST, "/api/v1/auth/login", null, Map.of("account", name, "password", "123456"))
                .get("accessToken").asText();
    }

    private WebSocketStompClient client() {
        if (client == null) {
            client = new WebSocketStompClient(new StandardWebSocketClient());
            MappingJackson2MessageConverter conv = new MappingJackson2MessageConverter();
            conv.setObjectMapper(om);
            client.setMessageConverter(conv);
        }
        return client;
    }

    private StompSession connect(String token) throws Exception {
        StompHeaders connect = new StompHeaders();
        if (token != null) {
            connect.add("Authorization", "Bearer " + token);
        }
        StompSession s = client().connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connect,
                new StompSessionHandlerAdapter() {
                }).get(5, TimeUnit.SECONDS);
        sessions.add(s);
        return s;
    }

    private BlockingQueue<JsonNode> subscribe(StompSession s, String dest) throws InterruptedException {
        BlockingQueue<JsonNode> q = new LinkedBlockingQueue<>();
        s.subscribe(dest, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return JsonNode.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                q.add((JsonNode) payload);
            }
        });
        Thread.sleep(200); // 等待 SUBSCRIBE 到达服务端
        return q;
    }

    private long uid(String token) throws Exception {
        return call(HttpMethod.GET, "/api/v1/users/me", token, null).get("id").asLong();
    }

    private JsonNode online(String token, long... ids) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (long id : ids) {
            sb.append(sb.length() == 0 ? "" : ",").append(id);
        }
        return call(HttpMethod.GET, "/api/v1/chat/online?userIds=" + sb, token, null);
    }

    /** 卖家发布商品，买家通过 HTTP 发起会话；返回 [goodsId, sessionId]。 */
    private String[] openSession(String seller, String buyer, long sellerId) throws Exception {
        Map<String, Object> goods = Map.of("title", "在线状态测试商品", "description", "d", "categoryId", "3000000000000000101",
                "condition", "GOOD", "price", 10, "images", List.of("/files/p.png"));
        String goodsId = call(HttpMethod.POST, "/api/v1/goods", seller, goods).get("id").asText();
        jdbc.update("UPDATE goods SET status='ON_SHELF' WHERE id=?", Long.parseLong(goodsId));
        String sessionId = call(HttpMethod.POST, "/api/v1/chat/sessions", buyer,
                Map.of("goodsId", goodsId, "toUserId", String.valueOf(sellerId))).get("id").asText();
        return new String[]{goodsId, sessionId};
    }

    @Test
    void presenceSupportsMultipleTabsAndPushesTransitions() throws Exception {
        String seller = newUser();
        String buyer = newUser();
        long sellerId = uid(seller);
        long buyerId = uid(buyer);
        openSession(seller, buyer, sellerId);

        StompSession sellerWs = connect(seller);
        BlockingQueue<JsonNode> presence = subscribe(sellerWs, "/user/queue/presence");
        JsonNode st = online(seller, buyerId, sellerId);
        assertFalse(st.get(String.valueOf(buyerId)).asBoolean());
        assertTrue(st.get(String.valueOf(sellerId)).asBoolean());

        // 第一个标签页上线 → 推送 online=true
        StompSession tab1 = connect(buyer);
        JsonNode up = presence.poll(5, TimeUnit.SECONDS);
        assertNotNull(up, "online push");
        assertEquals(String.valueOf(buyerId), up.get("userId").asText());
        assertTrue(up.get("online").asBoolean());
        assertTrue(online(seller, buyerId).get(String.valueOf(buyerId)).asBoolean());
        assertEquals(1L, redis.opsForZSet().zCard(ChatPresenceService.key(buyerId)));

        // 第二个标签页：不重复推送
        StompSession tab2 = connect(buyer);
        assertNull(presence.poll(700, TimeUnit.MILLISECONDS));
        assertEquals(2L, redis.opsForZSet().zCard(ChatPresenceService.key(buyerId)));

        // 关闭一个标签页：仍在线、不推送
        tab1.disconnect();
        assertNull(presence.poll(700, TimeUnit.MILLISECONDS));
        assertTrue(online(seller, buyerId).get(String.valueOf(buyerId)).asBoolean());

        // 最后一个断开 → 推送 online=false
        tab2.disconnect();
        JsonNode down = presence.poll(5, TimeUnit.SECONDS);
        assertNotNull(down, "offline push");
        assertEquals(String.valueOf(buyerId), down.get("userId").asText());
        assertFalse(down.get("online").asBoolean());
        assertFalse(online(seller, buyerId).get(String.valueOf(buyerId)).asBoolean());
    }

    @Test
    void stalePresenceExpiresAndQueryValidation() throws Exception {
        String t = newUser();
        long ghost = 1234567890123L;
        // 实例宕机残留：心跳时间早于 TTL 的 member 视为离线
        redis.opsForZSet().add(ChatPresenceService.key(ghost), "dead-session", System.currentTimeMillis() - 10 * 60_000);
        assertFalse(online(t, ghost).get(String.valueOf(ghost)).asBoolean());
        redis.opsForZSet().add(ChatPresenceService.key(ghost), "live-session", System.currentTimeMillis());
        assertTrue(online(t, ghost).get(String.valueOf(ghost)).asBoolean());
        redis.delete(ChatPresenceService.key(ghost));

        // 心跳会刷新本实例连接的 score
        StompSession ws = connect(t);
        long me = uid(t);
        Thread.sleep(300);
        String member = redis.opsForZSet().range(ChatPresenceService.key(me), 0, -1).iterator().next();
        redis.opsForZSet().add(ChatPresenceService.key(me), member, System.currentTimeMillis() - 10 * 60_000);
        assertFalse(online(t, me).get(String.valueOf(me)).asBoolean());
        presenceService.heartbeat();
        assertTrue(online(t, me).get(String.valueOf(me)).asBoolean());
        ws.disconnect();

        // 未登录 401；超过 100 个 400
        HttpHeaders h = new HttpHeaders();
        try {
            rest.exchange(base() + "/api/v1/chat/online?userIds=1", HttpMethod.GET, new HttpEntity<>(h), String.class);
            fail("expected 401");
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            assertEquals(401, e.getStatusCode().value());
        }
        StringBuilder many = new StringBuilder("1");
        for (int i = 2; i <= 101; i++) {
            many.append(',').append(i);
        }
        h.setBearerAuth(t);
        String body = rest.exchange(base() + "/api/v1/chat/online?userIds=" + many, HttpMethod.GET, new HttpEntity<>(h),
                String.class).getBody();
        assertEquals(400, om.readTree(body).get("code").asInt());
    }

    @Test
    void typingIsForwardedToPeerOnly() throws Exception {
        String seller = newUser();
        String buyer = newUser();
        String stranger = newUser();
        long sellerId = uid(seller);
        long buyerId = uid(buyer);
        String sessionId = openSession(seller, buyer, sellerId)[1];

        StompSession sellerWs = connect(seller);
        StompSession buyerWs = connect(buyer);
        StompSession strangerWs = connect(stranger);
        BlockingQueue<JsonNode> sellerTyping = subscribe(sellerWs, "/user/queue/typing");
        BlockingQueue<JsonNode> buyerTyping = subscribe(buyerWs, "/user/queue/typing");
        BlockingQueue<JsonNode> strangerErrors = subscribe(strangerWs, "/user/queue/errors");
        BlockingQueue<JsonNode> buyerErrors = subscribe(buyerWs, "/user/queue/errors");

        buyerWs.send("/app/chat.typing", Map.of("sessionId", sessionId));
        JsonNode got = sellerTyping.poll(5, TimeUnit.SECONDS);
        assertNotNull(got, "seller sees typing");
        assertEquals(sessionId, got.get("sessionId").asText());
        assertEquals(String.valueOf(buyerId), got.get("userId").asText());
        assertNull(buyerTyping.poll(500, TimeUnit.MILLISECONDS), "not echoed to sender");

        // 反向
        sellerWs.send("/app/chat.typing", Map.of("sessionId", sessionId));
        JsonNode back = buyerTyping.poll(5, TimeUnit.SECONDS);
        assertNotNull(back);
        assertEquals(String.valueOf(sellerId), back.get("userId").asText());

        // 非会话成员：403 到自己的 errors，不转发
        strangerWs.send("/app/chat.typing", Map.of("sessionId", sessionId));
        JsonNode err = strangerErrors.poll(5, TimeUnit.SECONDS);
        assertNotNull(err);
        assertEquals(403, err.get("code").asInt());
        assertNull(sellerTyping.poll(500, TimeUnit.MILLISECONDS));
        // 会话不存在 / 缺 sessionId
        buyerWs.send("/app/chat.typing", Map.of("sessionId", "1"));
        assertEquals(40003, buyerErrors.poll(5, TimeUnit.SECONDS).get("code").asInt());
        buyerWs.send("/app/chat.typing", Map.of("foo", "bar"));
        assertEquals(400, buyerErrors.poll(5, TimeUnit.SECONDS).get("code").asInt());
    }
}
