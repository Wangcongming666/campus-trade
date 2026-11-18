package com.campus.trade;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** goods.trade_location：发布/编辑/详情/列表。 */
class GoodsTradeLocationTest extends BaseIntegrationTest {

    private Map<String, Object> body(String title, String location) {
        Map<String, Object> b = new HashMap<>();
        b.put("title", title);
        b.put("description", "交易地点测试");
        b.put("categoryId", String.valueOf(CAT_PHONE));
        b.put("condition", "GOOD");
        b.put("price", 12.5);
        b.put("images", List.of("/files/" + UUID.randomUUID() + ".png"));
        if (location != null) {
            b.put("tradeLocation", location);
        }
        return b;
    }

    @Test
    void createUpdateDetailAndList() throws Exception {
        String t = newUserToken();
        String title = "地点测试" + UUID.randomUUID().toString().substring(0, 6);
        JsonNode created = ok(json(auth(post("/api/v1/goods"), t), body(title, "  东区食堂门口 ")));
        String id = created.get("id").asText();
        assertEquals("东区食堂门口", created.get("tradeLocation").asText());
        assertEquals("东区食堂门口", jdbc.queryForObject("SELECT trade_location FROM goods WHERE id=?", String.class,
                Long.parseLong(id)));

        jdbc.update("UPDATE goods SET status='ON_SHELF' WHERE id=?", Long.parseLong(id));
        assertEquals("东区食堂门口", ok(get("/api/v1/goods/" + id)).get("tradeLocation").asText());
        JsonNode list = ok(get("/api/v1/goods").param("keyword", title));
        assertEquals(1, list.get("total").asInt());
        assertEquals("东区食堂门口", list.get("records").get(0).get("tradeLocation").asText());

        // 编辑：修改地点
        JsonNode updated = ok(json(auth(put("/api/v1/goods/" + id), t), body(title, "图书馆一楼大厅")));
        assertEquals("图书馆一楼大厅", updated.get("tradeLocation").asText());
        // 编辑：不传则清空
        JsonNode cleared = ok(json(auth(put("/api/v1/goods/" + id), t), body(title, null)));
        assertTrue(cleared.has("tradeLocation"));
        assertTrue(cleared.get("tradeLocation").isNull());
        assertNull(jdbc.queryForObject("SELECT trade_location FROM goods WHERE id=?", String.class, Long.parseLong(id)));

        // 选填：不传也能发布；超过 100 字 400
        assertTrue(ok(json(auth(post("/api/v1/goods"), t), body("无地点商品", null))).get("tradeLocation").isNull());
        assertEquals(400, perform(json(auth(post("/api/v1/goods"), t), body("超长地点", "地".repeat(101))), 200)
                .get("code").asInt());
    }

    @Test
    void seedGoodsHaveLocations() throws Exception {
        assertEquals("东区食堂门口", ok(get("/api/v1/goods/4000000000000000001")).get("tradeLocation").asText());
        assertEquals("二食堂门口", ok(get("/api/v1/goods/" + GOODS_HEADPHONE)).get("tradeLocation").asText());
    }
}
