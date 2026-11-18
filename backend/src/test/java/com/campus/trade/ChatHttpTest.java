package com.campus.trade;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class ChatHttpTest extends BaseIntegrationTest {

    private static final String SEED_SESSION = "8000000000000000001";

    @Test
    void openSessionIsIdempotentAndRecordsBehaviorOnce() throws Exception {
        String seller = newUserToken();
        String buyer = newUserToken();
        String stranger = newUserToken();
        long sellerId = userIdOf(seller);
        long buyerId = userIdOf(buyer);
        long strangerId = userIdOf(stranger);
        String goodsId = onShelfGoods(seller, "聊天测试-平板", 300);

        JsonNode s1 = ok(json(auth(post("/api/v1/chat/sessions"), buyer),
                Map.of("goodsId", goodsId, "toUserId", String.valueOf(sellerId))));
        String sid = s1.get("id").asText();
        assertTrue(s1.get("id").isTextual());
        assertEquals(goodsId, s1.get("goodsId").asText());
        assertEquals("聊天测试-平板", s1.get("goodsTitle").asText());
        assertTrue(s1.get("goodsCover").isTextual());
        assertEquals(String.valueOf(sellerId), s1.get("peer").get("id").asText());
        assertEquals(0, s1.get("unreadCount").asInt());

        // 再次发起、以及卖家反向发起，均返回同一会话
        assertEquals(sid, ok(json(auth(post("/api/v1/chat/sessions"), buyer),
                Map.of("goodsId", goodsId, "toUserId", sellerId))).get("id").asText());
        JsonNode fromSeller = ok(json(auth(post("/api/v1/chat/sessions"), seller),
                Map.of("goodsId", goodsId, "toUserId", buyerId)));
        assertEquals(sid, fromSeller.get("id").asText());
        assertEquals(String.valueOf(buyerId), fromSeller.get("peer").get("id").asText());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM chat_session WHERE goods_id=?", Integer.class,
                Long.parseLong(goodsId)));
        // 生成列由数据库计算
        assertEquals(Math.min(sellerId, buyerId), jdbc.queryForObject(
                "SELECT user_low_id FROM chat_session WHERE id=?", Long.class, Long.parseLong(sid)));
        await(() -> behaviorCount(buyerId, Long.parseLong(goodsId), 3) == 1, "chat behavior");
        Thread.sleep(300);
        assertEquals(1, behaviorCount(buyerId, Long.parseLong(goodsId), 3));
        assertEquals(0, behaviorCount(sellerId, Long.parseLong(goodsId), 3), "卖家主动联系不记录行为");

        // 错误场景
        assertEquals(40001, perform(json(auth(post("/api/v1/chat/sessions"), seller),
                Map.of("goodsId", goodsId, "toUserId", sellerId)), 200).get("code").asInt());
        assertEquals(40002, perform(json(auth(post("/api/v1/chat/sessions"), stranger),
                Map.of("goodsId", goodsId, "toUserId", buyerId)), 200).get("code").asInt());
        assertEquals(20001, perform(json(auth(post("/api/v1/chat/sessions"), buyer),
                Map.of("goodsId", "1", "toUserId", sellerId)), 200).get("code").asInt());
        perform(auth(get("/api/v1/chat/sessions/" + sid + "/messages"), stranger), 403);
        perform(auth(patch("/api/v1/chat/sessions/" + sid + "/read"), stranger), 403);
        assertEquals(40003, perform(auth(get("/api/v1/chat/sessions/1/messages"), buyer), 200).get("code").asInt());
        assertEquals(0, ok(auth(get("/api/v1/chat/sessions"), stranger)).size());
        assertNotEquals(0, strangerId);
        perform(get("/api/v1/chat/sessions"), 401);
    }

    @Test
    void seededSessionMessagesPagingAndRead() throws Exception {
        String user1 = token("user1", "123456");
        JsonNode sessions = ok(auth(get("/api/v1/chat/sessions"), user1));
        JsonNode seed = null;
        for (JsonNode s : sessions) {
            if (SEED_SESSION.equals(s.get("id").asText())) {
                seed = s;
            }
        }
        assertNotNull(seed);
        assertEquals(1, seed.get("unreadCount").asInt());
        assertEquals("可以，明天下午二食堂见", seed.get("lastContent").asText());
        assertEquals("小红学姐", seed.get("peer").get("nickname").asText());

        // pageNum=1 为最新一页，页内按时间升序
        JsonNode p1 = ok(auth(get("/api/v1/chat/sessions/" + SEED_SESSION + "/messages").param("pageSize", "2"), user1));
        assertEquals(4, p1.get("total").asLong());
        assertEquals(2, p1.get("records").size());
        assertEquals("850 可以吗？可以的话当面看看", p1.get("records").get(0).get("content").asText());
        assertEquals("可以，明天下午二食堂见", p1.get("records").get(1).get("content").asText());
        assertTrue(p1.get("records").get(0).get("fromUserId").isTextual());
        JsonNode p2 = ok(auth(get("/api/v1/chat/sessions/" + SEED_SESSION + "/messages")
                .param("pageSize", "2").param("pageNum", "2"), user1));
        assertEquals("学姐你好，耳机还在吗？", p2.get("records").get(0).get("content").asText());

        JsonNode r = ok(auth(patch("/api/v1/chat/sessions/" + SEED_SESSION + "/read"), user1));
        assertEquals(SEED_SESSION, r.get("sessionId").asText());
        assertEquals(String.valueOf(USER1_ID), r.get("readerId").asText());
        assertTrue(r.get("readCount").asInt() <= 1);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM chat_message WHERE session_id=? AND to_user_id=? AND read_flag=0",
                Integer.class, Long.parseLong(SEED_SESSION), USER1_ID));
        for (JsonNode s : ok(auth(get("/api/v1/chat/sessions"), user1))) {
            if (SEED_SESSION.equals(s.get("id").asText())) {
                assertEquals(0, s.get("unreadCount").asInt());
            }
        }
    }
}
