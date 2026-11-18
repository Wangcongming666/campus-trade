package com.campus.trade;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class OrderTest extends BaseIntegrationTest {

    private JsonNode act(String token, String orderId, String action, String reason) throws Exception {
        Map<String, Object> body = reason == null ? Map.of("action", action) : Map.of("action", action, "reason", reason);
        return perform(json(auth(patch("/api/v1/orders/" + orderId + "/status"), token), body), 200);
    }

    @Test
    void createPayConfirmHappyPath() throws Exception {
        String seller = newUserToken();
        String buyer = newUserToken();
        String other = newUserToken();
        long buyerId = userIdOf(buyer);
        String goodsId = onShelfGoods(seller, "订单测试-相机", 666.66);

        // 不能买自己的
        assertEquals(30002, perform(json(auth(post("/api/v1/orders"), seller), Map.of("goodsId", goodsId)), 200)
                .get("code").asInt());

        JsonNode o = ok(json(auth(post("/api/v1/orders"), buyer), Map.of("goodsId", goodsId, "remark", "周五交易")));
        String orderId = o.get("id").asText();
        assertTrue(o.get("id").isTextual());
        assertEquals("PENDING", o.get("status").asText());
        assertEquals(0, new BigDecimal("666.66").compareTo(o.get("price").decimalValue()));
        assertEquals("订单测试-相机", o.get("goodsTitle").asText());
        assertTrue(o.get("goodsCover").asText().startsWith("/files/"));
        assertEquals(String.valueOf(buyerId), o.get("buyer").get("id").asText());
        assertEquals("周五交易", o.get("remark").asText());
        assertFalse(o.get("reviewed").asBoolean());
        assertEquals("RESERVED", goodsStatus(goodsId));
        await(() -> behaviorCount(buyerId, Long.parseLong(goodsId), 4) == 1, "order behavior");

        // 已预订：再下单 30003，详情仍可见，列表不可见
        assertEquals(30003, perform(json(auth(post("/api/v1/orders"), other), Map.of("goodsId", goodsId)), 200)
                .get("code").asInt());
        assertEquals("RESERVED", ok(get("/api/v1/goods/" + goodsId)).get("status").asText());

        // 下单后改价不影响快照
        jdbc.update("UPDATE goods SET price=1 WHERE id=?", Long.parseLong(goodsId));

        // 角色校验
        assertEquals(30005, act(seller, orderId, "PAY", null).get("code").asInt());
        assertEquals(403, perform(json(auth(patch("/api/v1/orders/" + orderId + "/status"), other),
                Map.of("action", "CANCEL")), 403).get("code").asInt());
        assertEquals(400, act(buyer, orderId, "DELIVER", null).get("code").asInt());
        // 状态校验：PENDING 不能 CONFIRM
        assertEquals(30001, act(buyer, orderId, "CONFIRM", null).get("code").asInt());

        JsonNode paid = act(buyer, orderId, "PAY", null);
        assertEquals(200, paid.get("code").asInt());
        assertEquals("PAID", paid.get("data").get("status").asText());
        assertTrue(paid.get("data").get("payTime").isTextual());
        assertEquals(30001, act(buyer, orderId, "PAY", null).get("code").asInt());
        assertEquals(30005, act(seller, orderId, "CONFIRM", null).get("code").asInt());

        JsonNode done = act(buyer, orderId, "CONFIRM", null).get("data");
        assertEquals("COMPLETED", done.get("status").asText());
        assertTrue(done.get("completeTime").isTextual());
        assertEquals(0, new BigDecimal("666.66").compareTo(done.get("price").decimalValue()));
        assertEquals("SOLD", goodsStatus(goodsId));
        assertEquals(30001, act(buyer, orderId, "CANCEL", null).get("code").asInt());
        assertEquals(30001, act(seller, orderId, "REFUND", null).get("code").asInt());

        // 列表与详情权限
        JsonNode mine = ok(auth(get("/api/v1/orders"), buyer));
        assertEquals(1, mine.get("total").asLong());
        assertEquals(orderId, mine.get("records").get(0).get("id").asText());
        assertEquals(1, ok(auth(get("/api/v1/orders").param("role", "seller"), seller)).get("total").asLong());
        assertEquals(0, ok(auth(get("/api/v1/orders").param("role", "seller"), buyer)).get("total").asLong());
        assertEquals(1, ok(auth(get("/api/v1/orders").param("status", "COMPLETED"), buyer)).get("total").asLong());
        assertEquals(0, ok(auth(get("/api/v1/orders").param("status", "PENDING"), buyer)).get("total").asLong());
        assertEquals(400, perform(auth(get("/api/v1/orders").param("role", "boss"), buyer), 200).get("code").asInt());
        ok(auth(get("/api/v1/orders/" + orderId), seller));
        perform(auth(get("/api/v1/orders/" + orderId), other), 403);
        ok(auth(get("/api/v1/orders/" + orderId), token("admin", "admin123")));
        assertEquals(30004, perform(auth(get("/api/v1/orders/1"), buyer), 200).get("code").asInt());
        perform(get("/api/v1/orders"), 401);
    }

    @Test
    void cancelAndRefundRestoreGoods() throws Exception {
        String seller = newUserToken();
        String buyer = newUserToken();
        String goodsId = onShelfGoods(seller, "订单测试-台式机", 1000);

        String o1 = ok(json(auth(post("/api/v1/orders"), buyer), Map.of("goodsId", goodsId))).get("id").asText();
        JsonNode c = act(seller, o1, "CANCEL", "卖家不想卖了").get("data");
        assertEquals("CANCELLED", c.get("status").asText());
        assertEquals("卖家不想卖了", c.get("cancelReason").asText());
        assertEquals("ON_SHELF", goodsStatus(goodsId));
        assertEquals(30001, act(buyer, o1, "PAY", null).get("code").asInt());

        String o2 = ok(json(auth(post("/api/v1/orders"), buyer), Map.of("goodsId", goodsId))).get("id").asText();
        act(buyer, o2, "PAY", null);
        JsonNode r = act(buyer, o2, "REFUND", null).get("data");
        assertEquals("CANCELLED", r.get("status").asText());
        assertEquals("退款", r.get("cancelReason").asText());
        assertEquals("ON_SHELF", goodsStatus(goodsId));
        assertEquals(1, ok(get("/api/v1/goods/search").param("keyword", "订单测试-台式机")).get("total").asLong());

        // 非 ON_SHELF 商品不能下单
        jdbc.update("UPDATE goods SET status='OFF_SHELF' WHERE id=?", Long.parseLong(goodsId));
        assertEquals(20002, perform(json(auth(post("/api/v1/orders"), buyer), Map.of("goodsId", goodsId)), 200)
                .get("code").asInt());
        assertEquals(20001, perform(json(auth(post("/api/v1/orders"), buyer), Map.of("goodsId", "1")), 200)
                .get("code").asInt());
        assertEquals(400, perform(json(auth(post("/api/v1/orders"), buyer), Map.of()), 200).get("code").asInt());
    }

    @Test
    void concurrentOrdersOnlyOneWins() throws Exception {
        String seller = newUserToken();
        String goodsId = onShelfGoods(seller, "订单测试-并发抢购", 9.9);
        List<String> buyers = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            buyers.add(newUserToken());
        }
        ExecutorService pool = Executors.newFixedThreadPool(buyers.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (String b : buyers) {
            results.add(pool.submit(() -> {
                start.await();
                return perform(json(auth(post("/api/v1/orders"), b), Map.of("goodsId", goodsId)), 200).get("code").asInt();
            }));
        }
        start.countDown();
        int success = 0;
        int reserved = 0;
        for (Future<Integer> f : results) {
            int code = f.get(20, TimeUnit.SECONDS);
            if (code == 200) {
                success++;
            } else if (code == 30003) {
                reserved++;
            }
        }
        pool.shutdown();
        assertEquals(1, success);
        assertEquals(buyers.size() - 1, reserved);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM trade_order WHERE goods_id=?", Integer.class,
                Long.parseLong(goodsId)));
    }

    @Test
    void seededOrdersAreConsistent() throws Exception {
        String user1 = token("user1", "123456");
        JsonNode list = ok(auth(get("/api/v1/orders").param("pageSize", "50"), user1));
        assertTrue(list.get("total").asLong() >= 3);
        JsonNode completed = ok(auth(get("/api/v1/orders/7000000000000000003"), user1));
        assertEquals("COMPLETED", completed.get("status").asText());
        assertTrue(completed.get("reviewed").asBoolean(), "seed review by user1");
        assertFalse(ok(auth(get("/api/v1/orders/7000000000000000003"), token("user2", "123456")))
                .get("reviewed").asBoolean());
    }
}
