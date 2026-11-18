package com.campus.trade;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class GoodsTest extends BaseIntegrationTest {

    private Map<String, Object> goodsBody(String title, List<String> images) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", title);
        m.put("description", "测试描述 " + title);
        m.put("categoryId", String.valueOf(CAT_PHONE));
        m.put("condition", "GOOD");
        m.put("tradeMethod", "FACE");
        m.put("originalPrice", 999.00);
        m.put("price", 123.45);
        m.put("images", images);
        m.put("coverUrl", "https://ignored.example/cover.png");
        return m;
    }

    private String createGoods(String token, String title) throws Exception {
        JsonNode g = ok(json(auth(post("/api/v1/goods"), token), goodsBody(title,
                List.of("/files/a1.png", "/files/a2.png"))));
        return g.get("id").asText();
    }

    @Test
    void listReturnsOnlyOnShelfWithPagination() throws Exception {
        JsonNode d = ok(get("/api/v1/goods").param("pageSize", "100"));
        assertTrue(d.get("total").isNumber());
        assertEquals(1, d.get("pageNum").asLong());
        assertEquals(100, d.get("pageSize").asLong());
        assertTrue(d.get("pages").isNumber());
        Set<String> ids = new HashSet<>();
        for (JsonNode g : d.get("records")) {
            assertEquals("ON_SHELF", g.get("status").asText());
            assertTrue(g.get("id").isTextual());
            assertTrue(g.get("seller").get("id").isTextual());
            assertNull(g.get("description"), "list must not return description");
            assertNull(g.get("images"), "list must not return images");
            assertNull(g.get("auditReason"));
            assertFalse(g.get("favorited").asBoolean());
            assertTrue(g.get("createTime").asText().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
            ids.add(g.get("id").asText());
        }
        assertTrue(ids.contains(String.valueOf(GOODS_HEADPHONE)));
        assertFalse(ids.contains(String.valueOf(GOODS_PENDING)));
        assertFalse(ids.contains(String.valueOf(GOODS_OFF)));

        JsonNode p = ok(get("/api/v1/goods").param("pageNum", "2").param("pageSize", "3"));
        assertEquals(2, p.get("pageNum").asLong());
        assertTrue(p.get("records").size() <= 3);
        // pageSize 超过 100 截断为 100
        assertEquals(100, ok(get("/api/v1/goods").param("pageSize", "500")).get("pageSize").asLong());
    }

    @Test
    void listFiltersAndSorts() throws Exception {
        // 一级分类包含子分类
        JsonNode d = ok(get("/api/v1/goods").param("categoryId", String.valueOf(CAT_DIGITAL)).param("pageSize", "50"));
        assertTrue(d.get("total").asLong() >= 3);
        Set<String> digitalCats = Set.of("3000000000000000101", "3000000000000000102", "3000000000000000103",
                String.valueOf(CAT_DIGITAL));
        d.get("records").forEach(g -> assertTrue(digitalCats.contains(g.get("categoryId").asText())));

        d = ok(get("/api/v1/goods").param("minPrice", "100").param("maxPrice", "500").param("pageSize", "50"));
        assertTrue(d.get("total").asLong() > 0);
        d.get("records").forEach(g -> {
            BigDecimal price = g.get("price").decimalValue();
            assertTrue(price.compareTo(new BigDecimal("100")) >= 0 && price.compareTo(new BigDecimal("500")) <= 0);
        });

        d = ok(get("/api/v1/goods").param("condition", "NEW").param("pageSize", "50"));
        d.get("records").forEach(g -> assertEquals("NEW", g.get("condition").asText()));

        d = ok(get("/api/v1/goods").param("sort", "price_asc").param("pageSize", "50"));
        BigDecimal prev = BigDecimal.ZERO;
        for (JsonNode g : d.get("records")) {
            assertTrue(g.get("price").decimalValue().compareTo(prev) >= 0);
            prev = g.get("price").decimalValue();
        }
        d = ok(get("/api/v1/goods").param("sort", "hot").param("pageSize", "50"));
        int prevView = Integer.MAX_VALUE;
        for (JsonNode g : d.get("records")) {
            assertTrue(g.get("viewCount").asInt() <= prevView);
            prevView = g.get("viewCount").asInt();
        }

        // 非法 sort / condition -> 400
        assertEquals(400, perform(get("/api/v1/goods").param("sort", "bogus"), 200).get("code").asInt());
        assertEquals(400, perform(get("/api/v1/goods").param("condition", "BROKEN"), 200).get("code").asInt());
    }

    @Test
    void searchRequiresKeywordAndSharesFilters() throws Exception {
        assertEquals(400, perform(get("/api/v1/goods/search"), 200).get("code").asInt());
        JsonNode d = ok(get("/api/v1/goods/search").param("keyword", "耳机"));
        assertEquals(1, d.get("total").asLong());
        assertEquals(String.valueOf(GOODS_HEADPHONE), d.get("records").get(0).get("id").asText());
        // 描述匹配 + 价格过滤
        assertEquals(1, ok(get("/api/v1/goods/search").param("keyword", "电池健康")).get("total").asLong());
        assertEquals(0, ok(get("/api/v1/goods/search").param("keyword", "耳机").param("maxPrice", "100"))
                .get("total").asLong());
        // /goods 也接受可选 keyword
        assertEquals(1, ok(get("/api/v1/goods").param("keyword", "耳机")).get("total").asLong());
        // LIKE 通配符被转义：% 只匹配字面量（种子中仅 iPhone 描述含 "88%"）
        assertEquals(1, ok(get("/api/v1/goods/search").param("keyword", "%")).get("total").asLong());
        assertEquals(0, ok(get("/api/v1/goods/search").param("keyword", "%%")).get("total").asLong());
    }

    @Test
    void detailIncrementsViewCountAndRecordsBehaviorWithDedupe() throws Exception {
        int before = jdbc.queryForObject("SELECT view_count FROM goods WHERE id=?", Integer.class, GOODS_HEADPHONE);
        JsonNode guest = ok(get("/api/v1/goods/" + GOODS_HEADPHONE));
        assertEquals(before + 1, guest.get("viewCount").asInt());
        assertTrue(guest.get("description").isTextual());
        assertEquals(2, guest.get("images").size());
        assertEquals(guest.get("coverUrl").asText(), guest.get("images").get(0).asText());
        assertNull(guest.get("auditReason"));

        String token = newUserToken();
        long uid = userIdOf(token);
        ok(auth(get("/api/v1/goods/" + GOODS_HEADPHONE), token));
        await(() -> behaviorCount(uid, GOODS_HEADPHONE, 1) == 1, "view behavior");
        ok(auth(get("/api/v1/goods/" + GOODS_HEADPHONE), token));
        Thread.sleep(500);
        assertEquals(1, behaviorCount(uid, GOODS_HEADPHONE, 1), "30 分钟内去重");
        assertEquals(before + 3, jdbc.queryForObject("SELECT view_count FROM goods WHERE id=?", Integer.class, GOODS_HEADPHONE));
    }

    @Test
    void detailVisibility() throws Exception {
        assertEquals(20001, perform(get("/api/v1/goods/" + GOODS_PENDING), 200).get("code").asInt());
        assertEquals(20002, perform(get("/api/v1/goods/" + GOODS_OFF), 200).get("code").asInt());
        assertEquals(20001, perform(get("/api/v1/goods/123"), 200).get("code").asInt());
        // 本人可见待审核商品
        String u1 = token("user1", "123456");
        assertEquals("PENDING_AUDIT", ok(auth(get("/api/v1/goods/" + GOODS_PENDING), u1)).get("status").asText());
        // 管理员可见下架商品
        String admin = token("admin", "admin123");
        assertEquals("OFF_SHELF", ok(auth(get("/api/v1/goods/" + GOODS_OFF), admin)).get("status").asText());
    }

    @Test
    void reservedAndSoldShowInDetailButNotInList() throws Exception {
        String token = newUserToken();
        String id = createGoods(token, "预订测试商品");
        jdbc.update("UPDATE goods SET status='RESERVED' WHERE id=?", Long.parseLong(id));
        assertEquals("RESERVED", ok(get("/api/v1/goods/" + id)).get("status").asText());
        assertEquals(0, ok(get("/api/v1/goods/search").param("keyword", "预订测试商品")).get("total").asLong());
        // 已预订：卖家不能改、不能删、不能上下架
        assertEquals(20005, perform(json(auth(put("/api/v1/goods/" + id), token), goodsBody("x", List.of())), 200)
                .get("code").asInt());
        assertEquals(20005, perform(auth(delete("/api/v1/goods/" + id), token), 200).get("code").asInt());
        assertEquals(20005, perform(json(auth(patch("/api/v1/goods/" + id + "/status"), token),
                Map.of("status", "OFF_SHELF")), 200).get("code").asInt());
        jdbc.update("UPDATE goods SET status='SOLD' WHERE id=?", Long.parseLong(id));
        assertEquals("SOLD", ok(get("/api/v1/goods/" + id)).get("status").asText());
    }

    @Test
    void createUpdateStatusAndDelete() throws Exception {
        String token = newUserToken();
        String other = newUserToken();

        Map<String, Object> body = goodsBody("全新测试手机", List.of("/files/c1.png", "/files/c2.png", "/files/c3.png"));
        JsonNode g = ok(json(auth(post("/api/v1/goods"), token), body));
        String id = g.get("id").asText();
        assertEquals("PENDING_AUDIT", g.get("status").asText());
        assertEquals("/files/c1.png", g.get("coverUrl").asText(), "cover = images[0], client coverUrl ignored");
        assertEquals(3, g.get("images").size());
        assertEquals(0, new BigDecimal("123.45").compareTo(g.get("price").decimalValue()));
        assertEquals("手机", g.get("categoryName").asText());
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM goods_image WHERE goods_id=?", Integer.class, Long.parseLong(id)));
        assertEquals(List.of(1, 2, 3), jdbc.queryForList(
                "SELECT sort_no FROM goods_image WHERE goods_id=? ORDER BY sort_no", Integer.class, Long.parseLong(id)));

        // 校验：10 张图 -> 400；停用分类 -> 20004；非法成色 -> 400
        List<String> ten = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ten.add("/files/x" + i + ".png");
        }
        assertEquals(400, perform(json(auth(post("/api/v1/goods"), token), goodsBody("t", ten)), 200).get("code").asInt());
        Map<String, Object> disabled = goodsBody("t", List.of());
        disabled.put("categoryId", CAT_DISABLED);
        assertEquals(20004, perform(json(auth(post("/api/v1/goods"), token), disabled), 200).get("code").asInt());
        Map<String, Object> badCond = goodsBody("t", List.of());
        badCond.put("condition", "BROKEN");
        assertEquals(400, perform(json(auth(post("/api/v1/goods"), token), badCond), 200).get("code").asInt());

        // 待审核不能上架；非本人 20003
        assertEquals(20005, perform(json(auth(patch("/api/v1/goods/" + id + "/status"), token),
                Map.of("status", "ON_SHELF")), 200).get("code").asInt());
        assertEquals(20003, perform(json(auth(patch("/api/v1/goods/" + id + "/status"), other),
                Map.of("status", "OFF_SHELF")), 200).get("code").asInt());
        assertEquals(400, perform(json(auth(patch("/api/v1/goods/" + id + "/status"), token),
                Map.of("status", "SOLD")), 200).get("code").asInt());

        // 模拟审核通过 -> 下架 -> 上架
        jdbc.update("UPDATE goods SET status='ON_SHELF', audit_reason='ok' WHERE id=?", Long.parseLong(id));
        assertEquals("OFF_SHELF", ok(json(auth(patch("/api/v1/goods/" + id + "/status"), token),
                Map.of("status", "OFF_SHELF"))).get("status").asText());
        assertEquals("ON_SHELF", ok(json(auth(patch("/api/v1/goods/" + id + "/status"), token),
                Map.of("status", "ON_SHELF"))).get("status").asText());
        assertEquals(1, ok(get("/api/v1/goods/search").param("keyword", "全新测试手机")).get("total").asLong());

        // 修改：他人 20003；本人 -> PENDING_AUDIT，图片替换，审核意见清空
        assertEquals(20003, perform(json(auth(put("/api/v1/goods/" + id), other), body), 200).get("code").asInt());
        Map<String, Object> upd = goodsBody("全新测试手机-改", List.of("/files/u1.png"));
        JsonNode u = ok(json(auth(put("/api/v1/goods/" + id), token), upd));
        assertEquals("PENDING_AUDIT", u.get("status").asText());
        assertEquals("全新测试手机-改", u.get("title").asText());
        assertEquals("/files/u1.png", u.get("coverUrl").asText());
        assertEquals(1, u.get("images").size());
        assertNull(jdbc.queryForObject("SELECT audit_reason FROM goods WHERE id=?", String.class, Long.parseLong(id)));

        // 我的发布
        JsonNode mine = ok(auth(get("/api/v1/users/me/goods"), token));
        assertEquals(1, mine.get("total").asLong());
        assertEquals(1, ok(auth(get("/api/v1/users/me/goods").param("status", "PENDING_AUDIT"), token)).get("total").asLong());
        assertEquals(0, ok(auth(get("/api/v1/users/me/goods").param("status", "ON_SHELF"), token)).get("total").asLong());
        assertEquals(400, perform(auth(get("/api/v1/users/me/goods").param("status", "WHATEVER"), token), 200)
                .get("code").asInt());

        // 删除：他人 20003；本人逻辑删除
        assertEquals(20003, perform(auth(delete("/api/v1/goods/" + id), other), 200).get("code").asInt());
        ok(auth(delete("/api/v1/goods/" + id), token));
        assertEquals(1, jdbc.queryForObject("SELECT deleted FROM goods WHERE id=?", Integer.class, Long.parseLong(id)));
        assertEquals(20001, perform(auth(get("/api/v1/goods/" + id), token), 200).get("code").asInt());
        assertEquals(0, ok(auth(get("/api/v1/users/me/goods"), token)).get("total").asLong());
    }
}
