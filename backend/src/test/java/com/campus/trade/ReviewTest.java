package com.campus.trade;

import com.campus.trade.service.ReviewService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class ReviewTest extends BaseIntegrationTest {

    private String completedOrder(String seller, String buyer, String title) throws Exception {
        String goodsId = onShelfGoods(seller, title, 50);
        String orderId = ok(json(auth(post("/api/v1/orders"), buyer), Map.of("goodsId", goodsId))).get("id").asText();
        ok(json(auth(patch("/api/v1/orders/" + orderId + "/status"), buyer), Map.of("action", "PAY")));
        ok(json(auth(patch("/api/v1/orders/" + orderId + "/status"), buyer), Map.of("action", "CONFIRM")));
        return orderId;
    }

    private JsonNode review(String token, String orderId, int score, String content) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("orderId", orderId, "score", score));
        if (content != null) {
            body.put("content", content);
        }
        return perform(json(auth(post("/api/v1/reviews"), token), body), 200);
    }

    private int credit(long userId) {
        return jdbc.queryForObject("SELECT credit_score FROM sys_user WHERE id=?", Integer.class, userId);
    }

    @Test
    void reviewFlowAndCreditScore() throws Exception {
        String seller = newUserToken();
        String buyer = newUserToken();
        String stranger = newUserToken();
        long sellerId = userIdOf(seller);
        long buyerId = userIdOf(buyer);

        // 未完成订单不可评价
        String g = onShelfGoods(seller, "评价测试-未完成", 10);
        String pending = ok(json(auth(post("/api/v1/orders"), buyer), Map.of("goodsId", g))).get("id").asText();
        assertEquals(50002, review(buyer, pending, 5, null).get("code").asInt());

        String o1 = completedOrder(seller, buyer, "评价测试-1");
        assertEquals(403, perform(json(auth(post("/api/v1/reviews"), stranger), Map.of("orderId", o1, "score", 5)), 403)
                .get("code").asInt());
        assertEquals(400, review(buyer, o1, 6, null).get("code").asInt());
        assertEquals(400, review(buyer, o1, 0, null).get("code").asInt());

        JsonNode r = review(buyer, o1, 4, "  不错  ");
        assertEquals(200, r.get("code").asInt());
        assertEquals("不错", r.get("data").get("content").asText());
        assertEquals(String.valueOf(buyerId), r.get("data").get("fromUser").get("id").asText());
        assertEquals(80, credit(sellerId), "avg 4 × 20");
        assertEquals(50001, review(buyer, o1, 5, null).get("code").asInt());
        assertTrue(ok(auth(get("/api/v1/orders/" + o1), buyer)).get("reviewed").asBoolean());
        assertFalse(ok(auth(get("/api/v1/orders/" + o1), seller)).get("reviewed").asBoolean());

        // 卖家评买家
        assertEquals(200, review(seller, o1, 3, null).get("code").asInt());
        assertEquals(60, credit(buyerId));

        // 第二单 5 分 -> 卖家 avg 4.5 -> 90
        String o2 = completedOrder(seller, buyer, "评价测试-2");
        review(buyer, o2, 5, null);
        assertEquals(90, credit(sellerId));
        assertEquals(90, ok(get("/api/v1/users/" + sellerId)).get("creditScore").asInt());

        // 游客查看收到的评价（倒序）
        JsonNode list = ok(get("/api/v1/reviews").param("userId", String.valueOf(sellerId)));
        assertEquals(2, list.get("total").asLong());
        assertEquals(5, list.get("records").get(0).get("score").asInt());
        assertTrue(list.get("records").get(0).get("orderId").isTextual());
        assertEquals(400, perform(get("/api/v1/reviews"), 200).get("code").asInt());
    }

    @Test
    void creditFormula() {
        assertEquals(100, ReviewService.creditOf(new BigDecimal("5")));
        assertEquals(20, ReviewService.creditOf(new BigDecimal("1")));
        assertEquals(87, ReviewService.creditOf(new BigDecimal("4.3333")));
        assertEquals(90, ReviewService.creditOf(new BigDecimal("4.5")));
        assertEquals(70, ReviewService.creditOf(new BigDecimal("3.5")));
    }
}
