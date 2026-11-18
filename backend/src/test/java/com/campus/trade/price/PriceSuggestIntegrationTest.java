package com.campus.trade.price;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.campus.trade.BaseIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 价格建议集成测试（MySQL campus_trade_test）。
 * 在"收纳日用"分类下造 9 件已售商品：goods.price 故意填 9999，成交价写在 COMPLETED 订单的 trade_order.price 上，
 * 用来验证"优先用成交价快照"；其中 1 件成交价 99999 为异常值。测试结束删除插入的数据。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PriceSuggestIntegrationTest extends BaseIntegrationTest {

    static final long CAT_STORAGE = 3000000000000000302L;   // 收纳日用（父：生活用品）
    static final long CAT_NOVEL = 3000000000000000203L;     // 课外读物：只有待审核商品，无样本
    static final long CAT_AUDIO = 3000000000000000103L;    // 耳机音箱：只有 1 件在售种子商品
    static final long CAT_BEAUTY = 3000000000000000006L;    // 美妆个护：无任何商品、无父分类

    String token;
    final List<Long> goods = new ArrayList<>();
    final List<Long> orders = new ArrayList<>();

    @BeforeAll
    void setUp() throws Exception {
        token = newUserToken();
        // 1000,1100,...,1700 + 异常值 99999
        for (int i = 0; i < 9; i++) {
            double deal = i < 8 ? 1000 + 100 * i : 99999;
            long gid = IdWorker.getId();
            jdbc.update("INSERT INTO goods(id,seller_id,category_id,title,description,price,original_price,`condition`,"
                            + "trade_method,status) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    gid, USER2_ID, CAT_STORAGE, "价格测试 收纳柜 " + i, "价格测试", 9999, null, "GOOD", "FACE", "SOLD");
            goods.add(gid);
            long oid = IdWorker.getId();
            jdbc.update("INSERT INTO trade_order(id,goods_id,buyer_id,seller_id,price,status,complete_time) "
                    + "VALUES(?,?,?,?,?,'COMPLETED',NOW())", oid, gid, USER1_ID, USER2_ID, deal);
            orders.add(oid);
        }
    }

    @AfterAll
    void cleanUp() {
        orders.forEach(id -> jdbc.update("DELETE FROM trade_order WHERE id=?", id));
        goods.forEach(id -> jdbc.update("DELETE FROM goods WHERE id=?", id));
    }

    private Map<String, Object> body(long cat, String cond, Object orig, String title) {
        Map<String, Object> m = new HashMap<>();
        m.put("categoryId", String.valueOf(cat));
        m.put("condition", cond);
        if (orig != null) {
            m.put("originalPrice", orig);
        }
        if (title != null) {
            m.put("title", title);
        }
        return m;
    }

    /** 返回原始 JSON 文本，用于检查两位小数。 */
    private String raw(Map<String, Object> body) throws Exception {
        MvcResult r = mvc.perform(auth(post("/api/v1/price/suggest"), token).contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(body))).andReturn();
        assertEquals(200, r.getResponse().getStatus());
        return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private JsonNode suggest(Map<String, Object> body) throws Exception {
        return ok(json(auth(post("/api/v1/price/suggest"), token), body));
    }

    private static BigDecimal dec(JsonNode d, String f) {
        return new BigDecimal(d.get(f).asText());
    }

    @Test
    void requiresLogin() throws Exception {
        assertEquals(401, perform(json(post("/api/v1/price/suggest"), body(CAT_STORAGE, "GOOD", null, null)), 401)
                .get("code").asInt());
    }

    @Test
    void usesCompletedOrderPriceAndRemovesOutlier() throws Exception {
        String s = raw(body(CAT_STORAGE, "GOOD", null, null));
        // 8 个成交价 1000..1700 等权：加权中位数 1350，P25=1150，P75=1550；99999 被 IQR 剔除；goods.price=9999 不参与
        assertTrue(s.contains("\"suggestedPrice\":1350.00"), s);
        assertTrue(s.contains("\"minPrice\":1150.00"), s);
        assertTrue(s.contains("\"maxPrice\":1550.00"), s);
        assertTrue(s.contains("\"sampleCount\":8"), s);
        JsonNode d = om.readTree(s).get("data");
        assertTrue(d.get("reason").asText().contains("剔除 1 个异常价"), d.toString());
    }

    @Test
    void conditionAlignment() throws Exception {
        // 样本都是 GOOD(0.7)，请求 NEW(1.0)：1350 / 0.7 = 1928.571… → 1928.57
        JsonNode d = suggest(body(CAT_STORAGE, "NEW", null, null));
        assertEquals(0, new BigDecimal("1928.57").compareTo(dec(d, "suggestedPrice")), d.toString());
    }

    @Test
    void neverExceedsOriginalPrice() throws Exception {
        JsonNode d = suggest(body(CAT_STORAGE, "LIKE_NEW", 1200, "收纳柜"));
        BigDecimal p = dec(d, "suggestedPrice");
        assertTrue(p.compareTo(new BigDecimal("1200.00")) <= 0, d.toString());
        assertTrue(dec(d, "maxPrice").compareTo(new BigDecimal("1200.00")) <= 0, d.toString());
        assertTrue(dec(d, "minPrice").compareTo(p) <= 0 && p.compareTo(dec(d, "maxPrice")) <= 0);
        assertTrue(d.get("reason").asText().contains("原价"), d.toString());
    }

    @Test
    void fewSamplesRelyOnOriginalPrice() throws Exception {
        // 耳机音箱分类只有 1 件在售种子商品 → 样本 < 5，主要依据原价×成色系数
        JsonNode d = suggest(body(CAT_AUDIO, "GOOD", 1000, null));
        assertTrue(d.get("reason").asText().contains("主要依据原价折算"), d.toString());
        assertTrue(d.get("sampleCount").asInt() < 5);
        assertTrue(dec(d, "suggestedPrice").compareTo(new BigDecimal("1000.00")) <= 0);
    }

    @Test
    void onlyOriginalPriceWhenNoSamples() throws Exception {
        // 原价 200 × LIKE_NEW 0.85 = 170；区间 ±15%；JSON 中金额保留两位小数
        String s = raw(body(CAT_BEAUTY, "LIKE_NEW", new BigDecimal("200"), null));
        assertTrue(s.contains("\"suggestedPrice\":170.00"), s);
        assertTrue(s.contains("\"minPrice\":144.50"), s);
        assertTrue(s.contains("\"maxPrice\":195.50"), s);
        assertTrue(s.contains("\"sampleCount\":0"), s);
    }

    @Test
    void categoryMedianFallback() throws Exception {
        // 课外读物：无已售/在售样本、未填原价 → 分类（为空则父分类"教材书籍"）整体价格中位数
        JsonNode d = suggest(body(CAT_NOVEL, "GOOD", null, null));
        assertTrue(dec(d, "suggestedPrice").signum() > 0, d.toString());
        assertEquals(0, d.get("sampleCount").asInt());
        assertTrue(d.get("reason").asText().contains("中位数"), d.toString());
    }

    @Test
    void noDataAtAll() throws Exception {
        JsonNode r = perform(json(auth(post("/api/v1/price/suggest"), token), body(CAT_BEAUTY, "GOOD", null, null)), 200);
        assertEquals(80001, r.get("code").asInt(), r.toString());
    }

    @Test
    void validation() throws Exception {
        assertEquals(400, perform(json(auth(post("/api/v1/price/suggest"), token), body(CAT_STORAGE, "BAD", null, null)), 200)
                .get("code").asInt());
        Map<String, Object> noCat = body(CAT_STORAGE, "GOOD", null, null);
        noCat.remove("categoryId");
        assertEquals(400, perform(json(auth(post("/api/v1/price/suggest"), token), noCat), 200).get("code").asInt());
        assertEquals(400, perform(json(auth(post("/api/v1/price/suggest"), token), body(CAT_STORAGE, "GOOD", -1, null)), 200)
                .get("code").asInt());
        assertEquals(20004, perform(json(auth(post("/api/v1/price/suggest"), token), body(CAT_DISABLED, "GOOD", null, null)), 200)
                .get("code").asInt());
    }
}
