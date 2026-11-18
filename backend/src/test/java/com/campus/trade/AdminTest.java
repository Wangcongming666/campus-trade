package com.campus.trade;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class AdminTest extends BaseIntegrationTest {

    private String admin() throws Exception {
        return token("admin", "admin123");
    }

    private String pendingGoods(String token, String title) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("description", "d");
        body.put("categoryId", String.valueOf(CAT_PHONE));
        body.put("condition", "GOOD");
        body.put("price", 10);
        body.put("images", List.of("/files/p.png"));
        return ok(json(auth(post("/api/v1/goods"), token), body)).get("id").asText();
    }

    @Test
    void nonAdminIsForbidden() throws Exception {
        String user = newUserToken();
        for (String path : List.of("/api/v1/admin/goods/audits", "/api/v1/admin/goods", "/api/v1/admin/users",
                "/api/v1/admin/student-verifies", "/api/v1/admin/stats", "/api/v1/admin/reports",
                "/api/v1/admin/announcements")) {
            assertEquals(403, perform(auth(get(path), user), 403).get("code").asInt(), path);
            perform(get(path), 401);
        }
    }

    @Test
    void goodsAuditFlow() throws Exception {
        String admin = admin();
        long adminId = userIdOf(admin);
        String seller = newUserToken();
        String g1 = pendingGoods(seller, "审核测试-通过");
        String g2 = pendingGoods(seller, "审核测试-驳回");

        JsonNode audits = ok(auth(get("/api/v1/admin/goods/audits").param("pageSize", "100"), admin));
        List<String> ids = audits.get("records").findValuesAsText("id");
        assertTrue(ids.contains(g1) && ids.contains(g2));
        audits.get("records").forEach(r -> assertEquals("PENDING_AUDIT", r.get("status").asText()));
        JsonNode a1 = null;
        for (JsonNode r : audits.get("records")) {
            if (r.get("id").asText().equals(g1)) a1 = r;
        }
        assertNotNull(a1);
        assertEquals("d", a1.get("description").asText());
        assertEquals("/files/p.png", a1.get("images").get(0).asText());
        assertTrue(a1.get("seller").has("nickname"));

        JsonNode ap = ok(json(auth(patch("/api/v1/admin/goods/" + g1 + "/audit"), admin), Map.of("result", "APPROVE")));
        assertEquals("ON_SHELF", ap.get("status").asText());
        assertEquals(adminId, jdbc.queryForObject("SELECT auditor_id FROM goods WHERE id=?", Long.class, Long.parseLong(g1)));
        assertNotNull(jdbc.queryForObject("SELECT audit_time FROM goods WHERE id=?", Object.class, Long.parseLong(g1)));
        assertEquals(60001, perform(json(auth(patch("/api/v1/admin/goods/" + g1 + "/audit"), admin),
                Map.of("result", "APPROVE")), 200).get("code").asInt());
        assertEquals(1, ok(get("/api/v1/goods/search").param("keyword", "审核测试-通过")).get("total").asLong());

        assertEquals(400, perform(json(auth(patch("/api/v1/admin/goods/" + g2 + "/audit"), admin),
                Map.of("result", "REJECT")), 200).get("code").asInt());
        assertEquals(400, perform(json(auth(patch("/api/v1/admin/goods/" + g2 + "/audit"), admin),
                Map.of("result", "MAYBE")), 200).get("code").asInt());
        JsonNode rj = ok(json(auth(patch("/api/v1/admin/goods/" + g2 + "/audit"), admin),
                Map.of("result", "REJECT", "reason", "图片不清晰")));
        assertEquals("REJECTED", rj.get("status").asText());
        assertEquals("图片不清晰", rj.get("auditReason").asText());
        assertEquals("图片不清晰", ok(auth(get("/api/v1/goods/" + g2), seller)).get("auditReason").asText());
        assertEquals(20001, perform(get("/api/v1/goods/" + g2), 200).get("code").asInt());

        // 后台商品列表（任意状态 + 过滤）
        assertEquals(1, ok(auth(get("/api/v1/admin/goods").param("status", "REJECTED")
                .param("keyword", "审核测试"), admin)).get("total").asLong());
        assertEquals(2, ok(auth(get("/api/v1/admin/goods").param("sellerId", String.valueOf(userIdOf(seller))), admin))
                .get("total").asLong());
        assertTrue(ok(auth(get("/api/v1/admin/goods").param("status", "SOLD"), admin)).get("total").asLong() >= 1);

        // 管理员上下架
        assertEquals("OFF_SHELF", ok(json(auth(patch("/api/v1/admin/goods/" + g1 + "/status"), admin),
                Map.of("status", "OFF_SHELF"))).get("status").asText());
        assertEquals("ON_SHELF", ok(json(auth(patch("/api/v1/admin/goods/" + g1 + "/status"), admin),
                Map.of("status", "ON_SHELF"))).get("status").asText());
        assertEquals(20005, perform(json(auth(patch("/api/v1/admin/goods/" + g2 + "/status"), admin),
                Map.of("status", "ON_SHELF")), 200).get("code").asInt());
        assertEquals(20005, perform(json(auth(patch("/api/v1/admin/goods/4000000000000000013/status"), admin),
                Map.of("status", "OFF_SHELF")), 200).get("code").asInt(), "RESERVED 不能下架");
        assertEquals(400, perform(json(auth(patch("/api/v1/admin/goods/" + g1 + "/status"), admin),
                Map.of("status", "SOLD")), 200).get("code").asInt());
    }

    @Test
    void banAndUnbanUser() throws Exception {
        String admin = admin();
        long adminId = userIdOf(admin);
        String name = "adm_" + UUID.randomUUID().toString().substring(0, 8);
        ok(json(post("/api/v1/auth/register"), Map.of("username", name, "password", "123456")));
        JsonNode l = login(name, "123456");
        String access = l.get("accessToken").asText();
        String refresh = l.get("refreshToken").asText();
        long uid = userIdOf(access);

        JsonNode list = ok(auth(get("/api/v1/admin/users").param("keyword", name), admin));
        assertEquals(1, list.get("total").asLong());
        assertTrue(list.get("records").get(0).has("lastLoginTime"));
        assertTrue(ok(auth(get("/api/v1/admin/users").param("role", "ADMIN"), admin)).get("total").asLong() >= 1);

        JsonNode banned = ok(json(auth(patch("/api/v1/admin/users/" + uid + "/status"), admin), Map.of("status", "BANNED")));
        assertEquals("BANNED", banned.get("status").asText());
        assertEquals(401, perform(auth(get("/api/v1/users/me"), access), 401).get("code").asInt());
        perform(json(post("/api/v1/auth/refresh"), Map.of("refreshToken", refresh)), 401);
        assertEquals(10004, perform(json(post("/api/v1/auth/login"), Map.of("account", name, "password", "123456")), 200)
                .get("code").asInt());
        assertEquals(1, ok(auth(get("/api/v1/admin/users").param("status", "BANNED").param("keyword", name), admin))
                .get("total").asLong());

        ok(json(auth(patch("/api/v1/admin/users/" + uid + "/status"), admin), Map.of("status", "NORMAL")));
        // 解封后封禁前签发的旧 token 不能复活，必须重新登录
        perform(auth(get("/api/v1/users/me"), access), 401);
        perform(json(post("/api/v1/auth/refresh"), Map.of("refreshToken", refresh)), 401);
        ok(auth(get("/api/v1/users/me"), token(name, "123456")));

        assertEquals(60002, perform(json(auth(patch("/api/v1/admin/users/" + adminId + "/status"), admin),
                Map.of("status", "BANNED")), 200).get("code").asInt());
        jdbc.update("UPDATE sys_user SET role='ADMIN' WHERE id=?", uid);
        assertEquals(60003, perform(json(auth(patch("/api/v1/admin/users/" + uid + "/status"), admin),
                Map.of("status", "BANNED")), 200).get("code").asInt());
        jdbc.update("UPDATE sys_user SET role='USER' WHERE id=?", uid);
        assertEquals(400, perform(json(auth(patch("/api/v1/admin/users/" + uid + "/status"), admin),
                Map.of("status", "DELETED")), 200).get("code").asInt());
        assertEquals(10011, perform(json(auth(patch("/api/v1/admin/users/1/status"), admin),
                Map.of("status", "BANNED")), 200).get("code").asInt());
    }

    @Test
    void studentVerifyReview() throws Exception {
        String admin = admin();
        String u1 = newUserToken();
        String u2 = newUserToken();
        String no1 = "N" + System.nanoTime() % 1_000_000_000L;
        String no2 = "M" + System.nanoTime() % 1_000_000_000L;
        ok(json(auth(post("/api/v1/users/me/student-verify"), u1),
                Map.of("studentNo", no1, "realName", "甲", "school", "测试大学", "cardImageUrl", "/files/c1.png")));
        ok(json(auth(post("/api/v1/users/me/student-verify"), u2),
                Map.of("studentNo", no2, "realName", "乙", "school", "测试大学", "cardImageUrl", "/files/c2.png")));

        JsonNode pending = ok(auth(get("/api/v1/admin/student-verifies").param("status", "PENDING").param("pageSize", "100"), admin));
        String id1 = null;
        String id2 = null;
        for (JsonNode r : pending.get("records")) {
            if (no1.equals(r.get("studentNo").asText())) {
                id1 = r.get("id").asText();
                assertEquals("甲", r.get("realName").asText());
                assertTrue(r.get("username").isTextual());
                java.util.Set<String> fields = new java.util.TreeSet<>();
                r.fieldNames().forEachRemaining(fields::add);
                assertEquals(new java.util.TreeSet<>(List.of("id", "userId", "username", "nickname", "realName",
                        "studentNo", "school", "cardImageUrl", "status", "reason", "createTime")), fields);
                assertEquals("/files/c1.png", r.get("cardImageUrl").asText());
            }
            if (no2.equals(r.get("studentNo").asText())) {
                id2 = r.get("id").asText();
            }
        }
        assertNotNull(id1);
        assertNotNull(id2);
        assertTrue(pending.get("records").findValuesAsText("studentNo").contains("2023002002"), "seed pending verify");

        JsonNode ap = ok(json(auth(patch("/api/v1/admin/student-verifies/" + id1), admin), Map.of("result", "APPROVE")));
        assertEquals("APPROVED", ap.get("status").asText());
        JsonNode me = ok(auth(get("/api/v1/users/me"), u1));
        assertEquals("APPROVED", me.get("studentAuthStatus").asText());
        assertEquals(no1, me.get("studentNo").asText());
        assertEquals("测试大学", me.get("school").asText());
        assertEquals(60004, perform(json(auth(patch("/api/v1/admin/student-verifies/" + id1), admin),
                Map.of("result", "REJECT", "reason", "x")), 200).get("code").asInt());

        assertEquals(400, perform(json(auth(patch("/api/v1/admin/student-verifies/" + id2), admin),
                Map.of("result", "REJECT")), 200).get("code").asInt());
        JsonNode rj = ok(json(auth(patch("/api/v1/admin/student-verifies/" + id2), admin),
                Map.of("result", "REJECT", "reason", "照片模糊")));
        assertEquals("REJECTED", rj.get("status").asText());
        assertEquals("照片模糊", rj.get("reason").asText());
        assertEquals("REJECTED", ok(auth(get("/api/v1/users/me"), u2)).get("studentAuthStatus").asText());
        // 驳回后可重新提交
        ok(json(auth(post("/api/v1/users/me/student-verify"), u2),
                Map.of("studentNo", no2, "realName", "乙", "school", "测试大学", "cardImageUrl", "/files/c3.png")));
        assertEquals(60009, perform(json(auth(patch("/api/v1/admin/student-verifies/1"), admin),
                Map.of("result", "APPROVE")), 200).get("code").asInt());
    }

    @Test
    void stats() throws Exception {
        String admin = admin();
        String seller = newUserToken();
        String buyer = newUserToken();
        String g = onShelfGoods(seller, "统计测试", 1);
        String oid = ok(json(auth(post("/api/v1/orders"), buyer), Map.of("goodsId", g))).get("id").asText();
        ok(json(auth(patch("/api/v1/orders/" + oid + "/status"), buyer), Map.of("action", "PAY")));
        ok(json(auth(patch("/api/v1/orders/" + oid + "/status"), buyer), Map.of("action", "CONFIRM")));

        JsonNode s = ok(auth(get("/api/v1/admin/stats"), admin));
        assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Long.class), s.get("userCount").asLong());
        assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM goods WHERE deleted=0", Long.class), s.get("goodsCount").asLong());
        assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM trade_order", Long.class), s.get("orderCount").asLong());
        assertEquals(0, jdbc.queryForObject("SELECT COALESCE(SUM(price),0) FROM trade_order WHERE status='COMPLETED'",
                java.math.BigDecimal.class).compareTo(s.get("gmv").decimalValue()));
        assertTrue(s.get("gmv").isNumber());
        assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM goods WHERE deleted=0 AND status='PENDING_AUDIT'", Long.class),
                s.get("pendingGoodsAudit").asLong());
        assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM student_auth WHERE status='PENDING'", Long.class),
                s.get("pendingStudentVerify").asLong());

        JsonNode trend = s.get("trend");
        assertEquals(7, trend.size());
        assertEquals(LocalDate.now().minusDays(6).toString(), trend.get(0).get("date").asText());
        JsonNode today = trend.get(6);
        assertEquals(LocalDate.now().toString(), today.get("date").asText());
        assertTrue(today.get("newUsers").asLong() >= 2);
        assertTrue(today.get("newGoods").asLong() >= 1);
        assertTrue(today.get("newOrders").asLong() >= 1);

        long shareSum = 0;
        for (JsonNode c : s.get("categoryShares")) {
            assertTrue(c.get("categoryId").isTextual());
            assertNotNull(c.get("categoryName").textValue());
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM category WHERE id=? AND parent_id IS NOT NULL",
                    Long.class, c.get("categoryId").asLong()));
            shareSum += c.get("count").asLong();
        }
        assertEquals(s.get("goodsCount").asLong(), shareSum);

        JsonNode hot = s.get("hotGoods");
        assertTrue(hot.size() <= 10 && hot.size() > 0);
        for (int i = 1; i < hot.size(); i++) {
            assertTrue(hot.get(i - 1).get("viewCount").asLong() >= hot.get(i).get("viewCount").asLong());
        }
        assertEquals(jdbc.queryForObject("SELECT MAX(view_count) FROM goods WHERE deleted=0", Long.class),
                hot.get(0).get("viewCount").asLong());
        for (String f : List.of("id", "title", "coverUrl", "price", "favoriteCount")) {
            assertTrue(hot.get(0).has(f), f);
        }

        assertEquals(30, ok(auth(get("/api/v1/admin/stats").param("days", "30"), admin)).get("trend").size());
        assertEquals(400, perform(auth(get("/api/v1/admin/stats").param("days", "14"), admin), 200).get("code").asInt());
        assertEquals(400, perform(auth(get("/api/v1/admin/stats").param("days", "abc"), admin), 200).get("code").asInt());
    }
}
