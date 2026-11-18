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

/** STOMP 端到端：真实端口 + WebSocketStompClient。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestDatabaseInitializer.class)
class ChatStompTest {

    @LocalServerPort
    int port;
    @Autowired
    ObjectMapper om;
    @Autowired
    JdbcTemplate jdbc;

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

    @Test
    void connectWithoutValidTokenIsRejected() {
        // 服务端 ChannelInterceptor 拒绝 CONNECT -> ERROR 帧并关闭连接，connect future 异常完成
        assertThrows(ExecutionException.class, () -> connect(null));
        assertThrows(ExecutionException.class, () -> connect("bogus.token.value"));
    }

    @Test
    void sendPushesToBothSidesAndReadReceipt() throws Exception {
        String seller = newUser();
        String buyer = newUser();
        long sellerId = call(HttpMethod.GET, "/api/v1/users/me", seller, null).get("id").asLong();
        long buyerId = call(HttpMethod.GET, "/api/v1/users/me", buyer, null).get("id").asLong();
        Map<String, Object> goods = Map.of("title", "STOMP 测试商品", "description", "d", "categoryId", "3000000000000000101",
                "condition", "GOOD", "price", 10, "images", List.of("/files/s.png"));
        String goodsId = call(HttpMethod.POST, "/api/v1/goods", seller, goods).get("id").asText();
        jdbc.update("UPDATE goods SET status='ON_SHELF' WHERE id=?", Long.parseLong(goodsId));

        StompSession sellerWs = connect(seller);
        StompSession buyerWs = connect(buyer);
        BlockingQueue<JsonNode> sellerMsgs = subscribe(sellerWs, "/user/queue/messages");
        BlockingQueue<JsonNode> buyerMsgs = subscribe(buyerWs, "/user/queue/messages");
        BlockingQueue<JsonNode> buyerRead = subscribe(buyerWs, "/user/queue/read");
        BlockingQueue<JsonNode> buyerErrors = subscribe(buyerWs, "/user/queue/errors");

        // 首条消息自动创建会话
        buyerWs.send("/app/chat.send", Map.of("toUserId", String.valueOf(sellerId), "goodsId", goodsId,
                "content", "你好，还在吗？", "type", 1));
        JsonNode got = sellerMsgs.poll(5, TimeUnit.SECONDS);
        assertNotNull(got, "seller receives message");
        JsonNode echo = buyerMsgs.poll(5, TimeUnit.SECONDS);
        assertNotNull(echo, "sender receives echo");
        assertEquals(got.get("id").asText(), echo.get("id").asText());
        assertTrue(got.get("id").isTextual(), "Long id as string over STOMP");
        assertEquals(String.valueOf(buyerId), got.get("fromUserId").asText());
        assertEquals(String.valueOf(sellerId), got.get("toUserId").asText());
        assertEquals("你好，还在吗？", got.get("content").asText());
        assertEquals(1, got.get("type").asInt());
        assertEquals(0, got.get("readFlag").asInt());
        assertTrue(got.get("createTime").asText().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
        String sessionId = got.get("sessionId").asText();

        // 商品卡片（type=3，content 为 JSON 字符串原样存储）
        String card = "{\"goodsId\":\"" + goodsId + "\",\"title\":\"STOMP 测试商品\",\"coverUrl\":\"/files/s.png\",\"price\":10}";
        buyerWs.send("/app/chat.send", Map.of("toUserId", sellerId, "goodsId", goodsId, "content", card, "type", 3));
        JsonNode cardMsg = sellerMsgs.poll(5, TimeUnit.SECONDS);
        assertNotNull(cardMsg);
        assertEquals(card, cardMsg.get("content").asText());
        assertEquals(sessionId, cardMsg.get("sessionId").asText());
        buyerMsgs.poll(5, TimeUnit.SECONDS);

        // 落库与会话摘要/未读
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM chat_message WHERE session_id=?", Integer.class,
                Long.parseLong(sessionId)));
        Map<String, Object> row = jdbc.queryForMap("SELECT last_content, initiator_id, peer_unread, initiator_unread "
                + "FROM chat_session WHERE id=?", Long.parseLong(sessionId));
        assertEquals("[商品]", row.get("last_content"));
        assertEquals(buyerId, ((Number) row.get("initiator_id")).longValue());
        assertEquals(2, ((Number) row.get("peer_unread")).intValue());
        assertEquals(0, ((Number) row.get("initiator_unread")).intValue());
        JsonNode sellerSessions = call(HttpMethod.GET, "/api/v1/chat/sessions", seller, null);
        assertEquals(2, sellerSessions.get(0).get("unreadCount").asInt());

        // 卖家标记已读 -> 买家收到回执 {sessionId, readerId}
        JsonNode r = call(HttpMethod.PATCH, "/api/v1/chat/sessions/" + sessionId + "/read", seller, null);
        assertEquals(2, r.get("readCount").asInt());
        JsonNode receipt = buyerRead.poll(5, TimeUnit.SECONDS);
        assertNotNull(receipt, "read receipt pushed");
        assertEquals(sessionId, receipt.get("sessionId").asText());
        assertEquals(String.valueOf(sellerId), receipt.get("readerId").asText());
        assertFalse(receipt.has("readCount"));
        assertEquals(0, call(HttpMethod.GET, "/api/v1/chat/sessions", seller, null).get(0).get("unreadCount").asInt());

        // 错误回到 /user/queue/errors：给自己发、空内容
        buyerWs.send("/app/chat.send", Map.of("toUserId", buyerId, "goodsId", goodsId, "content", "x", "type", 1));
        JsonNode err = buyerErrors.poll(5, TimeUnit.SECONDS);
        assertNotNull(err);
        assertEquals(40001, err.get("code").asInt());
        buyerWs.send("/app/chat.send", Map.of("toUserId", sellerId, "goodsId", goodsId, "content", "", "type", 1));
        err = buyerErrors.poll(5, TimeUnit.SECONDS);
        assertNotNull(err);
        assertEquals(400, err.get("code").asInt());
        assertNull(sellerMsgs.poll(500, TimeUnit.MILLISECONDS), "invalid messages are not delivered");
    }
}
