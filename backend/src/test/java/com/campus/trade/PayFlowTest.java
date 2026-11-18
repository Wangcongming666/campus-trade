package com.campus.trade;

import com.campus.trade.service.PayService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** 模拟支付：支付单生成/复用、确认幂等、权限、超时自动取消及与确认支付的并发安全。 */
class PayFlowTest extends BaseIntegrationTest {

    @Autowired
    PayService payService;

    private record Ctx(String seller, String buyer, String goodsId, String orderId, String payNo) {
    }

    private Ctx newOrder(String title) throws Exception {
        String seller = newUserToken();
        String buyer = newUserToken();
        String goodsId = onShelfGoods(seller, title, 88.5);
        JsonNode o = ok(json(auth(post("/api/v1/orders"), buyer), Map.of("goodsId", goodsId)));
        return new Ctx(seller, buyer, goodsId, o.get("id").asText(), o.get("payNo").asText());
    }

    private String payStatus(String payNo) {
        return jdbc.queryForObject("SELECT status FROM pay_order WHERE pay_no=?", String.class, payNo);
    }

    private String orderStatus(String orderId) {
        return jdbc.queryForObject("SELECT status FROM trade_order WHERE id=?", String.class, Long.parseLong(orderId));
    }

    /** 把订单下单时间与支付单过期时间往前拨，模拟已超时。 */
    private void backdate(Ctx c, int minutes, boolean alsoPayExpire) {
        jdbc.update("UPDATE trade_order SET create_time = DATE_SUB(NOW(), INTERVAL ? MINUTE) WHERE id=?", minutes,
                Long.parseLong(c.orderId()));
        if (alsoPayExpire) {
            jdbc.update("UPDATE pay_order SET expire_time = DATE_SUB(NOW(), INTERVAL 1 MINUTE) WHERE pay_no=?", c.payNo());
        }
    }

    @Test
    void orderCreatesWaitingPayOrderAndPayEndpointReusesIt() throws Exception {
        String seller = newUserToken();
        String buyer = newUserToken();
        String other = newUserToken();
        long buyerId = userIdOf(buyer);
        String goodsId = onShelfGoods(seller, "支付测试-台灯", 66.6);
        JsonNode o = ok(json(auth(post("/api/v1/orders"), buyer), Map.of("goodsId", goodsId)));
        String orderId = o.get("id").asText();
        String payNo = o.get("payNo").asText();
        assertTrue(payNo.startsWith("P"));
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        long minutes = Duration.between(LocalDateTime.parse(o.get("createTime").asText(), f),
                LocalDateTime.parse(o.get("payExpireTime").asText(), f)).toMinutes();
        assertEquals(15, minutes, "pay expires 15 minutes after order creation");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM pay_order WHERE order_id=? AND status='WAITING'",
                Integer.class, Long.parseLong(orderId)));

        JsonNode p1 = ok(auth(post("/api/v1/orders/" + orderId + "/pay"), buyer));
        JsonNode p2 = ok(auth(post("/api/v1/orders/" + orderId + "/pay"), buyer));
        assertEquals(payNo, p1.get("payNo").asText());
        assertEquals(payNo, p2.get("payNo").asText());
        assertEquals("WAITING", p1.get("status").asText());
        assertEquals("PENDING", p1.get("orderStatus").asText());
        assertEquals(orderId, p1.get("orderId").asText());
        assertEquals(String.valueOf(buyerId), p1.get("userId").asText());
        assertEquals(66.6, p1.get("amount").asDouble(), 1e-9);
        assertTrue(p1.get("remainSeconds").asLong() > 800 && p1.get("remainSeconds").asLong() <= 900);
        assertTrue(p1.get("id").isTextual());

        // 只有买家能操作
        assertEquals(30005, perform(auth(post("/api/v1/orders/" + orderId + "/pay"), seller), 200).get("code").asInt());
        assertEquals(403, perform(auth(post("/api/v1/orders/" + orderId + "/pay"), other), 403).get("code").asInt());
        assertEquals(403, perform(auth(post("/api/v1/pay/" + payNo + "/confirm"), seller), 403).get("code").asInt());
        assertEquals(403, perform(auth(get("/api/v1/pay/" + payNo), other), 403).get("code").asInt());
        assertEquals(401, perform(post("/api/v1/pay/" + payNo + "/confirm"), 401).get("code").asInt());
        assertEquals(91001, perform(auth(get("/api/v1/pay/PNOTEXIST"), buyer), 200).get("code").asInt());
        assertEquals(30004, perform(auth(post("/api/v1/orders/1/pay"), buyer), 200).get("code").asInt());
        assertEquals("WAITING", ok(auth(get("/api/v1/pay/" + payNo), buyer)).get("status").asText());

        // 确认支付：订单 PENDING→PAID，重复确认幂等
        JsonNode paid = ok(auth(post("/api/v1/pay/" + payNo + "/confirm"), buyer));
        assertEquals("PAID", paid.get("status").asText());
        assertEquals("PAID", paid.get("orderStatus").asText());
        assertFalse(paid.get("payTime").isNull());
        assertEquals(0, paid.get("remainSeconds").asLong());
        JsonNode again = ok(auth(post("/api/v1/pay/" + payNo + "/confirm"), buyer));
        assertEquals("PAID", again.get("status").asText());
        assertEquals(paid.get("payTime").asText(), again.get("payTime").asText());
        assertEquals("PAID", orderStatus(orderId));
        assertEquals("RESERVED", goodsStatus(goodsId));
        // 已支付后再请求支付单，返回已支付的那张
        assertEquals("PAID", ok(auth(post("/api/v1/orders/" + orderId + "/pay"), buyer)).get("status").asText());
        JsonNode detail = ok(auth(get("/api/v1/orders/" + orderId), buyer));
        assertEquals("PAID", detail.get("status").asText());
        assertTrue(detail.get("payNo").isNull(), "payNo only for PENDING orders");
        // 管理员可查
        assertEquals("PAID", ok(auth(get("/api/v1/pay/" + payNo), token("admin", "admin123"))).get("status").asText());
    }

    @Test
    void concurrentConfirmIsIdempotent() throws Exception {
        Ctx c = newOrder("支付测试-并发确认");
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Future<JsonNode>> fs = new ArrayList<>();
            CountDownLatch start = new CountDownLatch(1);
            for (int i = 0; i < 6; i++) {
                fs.add(pool.submit(() -> {
                    start.await();
                    return perform(auth(post("/api/v1/pay/" + c.payNo() + "/confirm"), c.buyer()), 200);
                }));
            }
            start.countDown();
            for (Future<JsonNode> f : fs) {
                JsonNode r = f.get(20, TimeUnit.SECONDS);
                assertEquals(200, r.get("code").asInt(), r.toString());
                assertEquals("PAID", r.get("data").get("status").asText());
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals("PAID", orderStatus(c.orderId()));
        assertEquals("PAID", payStatus(c.payNo()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM pay_order WHERE order_id=?", Integer.class,
                Long.parseLong(c.orderId())));
    }

    @Test
    void scanCancelsTimeoutOrdersAndRestoresGoods() throws Exception {
        Ctx timeout = newOrder("支付测试-超时");
        Ctx fresh = newOrder("支付测试-未超时");
        backdate(timeout, 16, true);
        assertTrue(payService.scanTimeoutOrders() >= 1);

        assertEquals("CANCELLED", orderStatus(timeout.orderId()));
        assertEquals(PayService.TIMEOUT_REASON, jdbc.queryForObject("SELECT cancel_reason FROM trade_order WHERE id=?",
                String.class, Long.parseLong(timeout.orderId())));
        assertEquals("CLOSED", payStatus(timeout.payNo()));
        assertEquals("ON_SHELF", goodsStatus(timeout.goodsId()));
        // 未超时的不受影响
        assertEquals("PENDING", orderStatus(fresh.orderId()));
        assertEquals("WAITING", payStatus(fresh.payNo()));
        // 已关闭的支付单不能再确认，也不能再生成支付单
        assertEquals(91003, perform(auth(post("/api/v1/pay/" + timeout.payNo() + "/confirm"), timeout.buyer()), 200)
                .get("code").asInt());
        assertEquals(30001, perform(auth(post("/api/v1/orders/" + timeout.orderId() + "/pay"), timeout.buyer()), 200)
                .get("code").asInt());
        // 再扫一次不重复处理
        assertFalse(payService.cancelIfTimeout(Long.parseLong(timeout.orderId())));
        // 商品恢复上架后可被他人下单
        ok(json(auth(post("/api/v1/orders"), newUserToken()), Map.of("goodsId", timeout.goodsId())));
    }

    @Test
    void confirmAfterExpiryCancelsImmediately() throws Exception {
        Ctx c = newOrder("支付测试-过期确认");
        backdate(c, 16, true);
        assertEquals(91002, perform(auth(post("/api/v1/pay/" + c.payNo() + "/confirm"), c.buyer()), 200)
                .get("code").asInt());
        assertEquals("CANCELLED", orderStatus(c.orderId()));
        assertEquals("CLOSED", payStatus(c.payNo()));
        assertEquals("ON_SHELF", goodsStatus(c.goodsId()));

        // 旧接口 PATCH action=PAY 同样受超时约束
        Ctx c2 = newOrder("支付测试-旧接口过期");
        backdate(c2, 16, false);
        assertEquals(91002, perform(json(auth(patch("/api/v1/orders/" + c2.orderId() + "/status"), c2.buyer()),
                Map.of("action", "PAY")), 200).get("code").asInt());
        assertEquals("CANCELLED", orderStatus(c2.orderId()));
        assertEquals("ON_SHELF", goodsStatus(c2.goodsId()));
    }

    @Test
    void confirmAndTimeoutRaceLeavesConsistentState() throws Exception {
        for (int round = 0; round < 4; round++) {
            Ctx c = newOrder("支付测试-竞争" + round);
            // 订单已满 15 分钟，但支付单过期时间未到：确认支付能通过前置检查，与超时关单正面竞争
            backdate(c, 16, false);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<JsonNode> confirm = pool.submit(() -> {
                start.await();
                return perform(auth(post("/api/v1/pay/" + c.payNo() + "/confirm"), c.buyer()), 200);
            });
            Future<Boolean> cancel = pool.submit(() -> {
                start.await();
                return payService.cancelIfTimeout(Long.parseLong(c.orderId()));
            });
            start.countDown();
            JsonNode r = confirm.get(20, TimeUnit.SECONDS);
            boolean cancelled = cancel.get(20, TimeUnit.SECONDS);
            pool.shutdownNow();
            String os = orderStatus(c.orderId());
            String ps = payStatus(c.payNo());
            String gs = goodsStatus(c.goodsId());
            if (cancelled) {
                assertEquals("CANCELLED", os);
                assertEquals("CLOSED", ps);
                assertEquals("ON_SHELF", gs);
                assertNotEquals(200, r.get("code").asInt(), r.toString());
            } else {
                assertEquals(200, r.get("code").asInt(), r.toString());
                assertEquals("PAID", os);
                assertEquals("PAID", ps);
                assertEquals("RESERVED", gs);
            }
        }
    }

    @Test
    void legacyPatchActionsSyncPayOrder() throws Exception {
        Ctx paid = newOrder("支付测试-旧接口支付");
        ok(json(auth(patch("/api/v1/orders/" + paid.orderId() + "/status"), paid.buyer()), Map.of("action", "PAY")));
        assertEquals("PAID", payStatus(paid.payNo()));

        Ctx cancelled = newOrder("支付测试-取消");
        ok(json(auth(patch("/api/v1/orders/" + cancelled.orderId() + "/status"), cancelled.seller()),
                Map.of("action", "CANCEL")));
        assertEquals("CLOSED", payStatus(cancelled.payNo()));
        assertEquals(91003, perform(auth(post("/api/v1/pay/" + cancelled.payNo() + "/confirm"), cancelled.buyer()), 200)
                .get("code").asInt());
    }

    @Test
    void seededPendingOrderHasWaitingPayOrder() throws Exception {
        String user1 = token("user1", "123456");
        JsonNode o = ok(auth(get("/api/v1/orders/7000000000000000001"), user1));
        assertEquals("PENDING", o.get("status").asText());
        assertEquals("P202611180000000001", o.get("payNo").asText());
        assertEquals("PAID", ok(auth(post("/api/v1/orders/7000000000000000002/pay"), token("user2", "123456")))
                .get("status").asText());
    }
}
