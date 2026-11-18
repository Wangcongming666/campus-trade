package com.campus.trade;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class ReportAnnouncementTest extends BaseIntegrationTest {

    @Test
    void reportFlow() throws Exception {
        String reporter = newUserToken();
        String seller = newUserToken();
        String admin = token("admin", "admin123");
        String goodsId = onShelfGoods(seller, "举报测试商品", 5);

        JsonNode r = ok(json(auth(post("/api/v1/reports"), reporter),
                Map.of("targetType", "GOODS", "targetId", goodsId, "reason", "虚假信息", "detail", "图片盗用")));
        String id = r.get("id").asText();
        assertEquals("PENDING", r.get("status").asText());
        assertEquals("举报测试商品", r.get("targetName").asText());
        assertEquals(60006, perform(json(auth(post("/api/v1/reports"), reporter),
                Map.of("targetType", "GOODS", "targetId", goodsId, "reason", "再次举报")), 200).get("code").asInt());
        assertEquals(60005, perform(json(auth(post("/api/v1/reports"), seller),
                Map.of("targetType", "GOODS", "targetId", goodsId, "reason", "x")), 200).get("code").asInt());
        assertEquals(60005, perform(json(auth(post("/api/v1/reports"), seller),
                Map.of("targetType", "USER", "targetId", String.valueOf(userIdOf(seller)), "reason", "x")), 200)
                .get("code").asInt());
        assertEquals(20001, perform(json(auth(post("/api/v1/reports"), reporter),
                Map.of("targetType", "GOODS", "targetId", "1", "reason", "x")), 200).get("code").asInt());
        assertEquals(10011, perform(json(auth(post("/api/v1/reports"), reporter),
                Map.of("targetType", "USER", "targetId", "1", "reason", "x")), 200).get("code").asInt());
        assertEquals(400, perform(json(auth(post("/api/v1/reports"), reporter),
                Map.of("targetType", "ORDER", "targetId", "1", "reason", "x")), 200).get("code").asInt());
        JsonNode ur = ok(json(auth(post("/api/v1/reports"), reporter),
                Map.of("targetType", "USER", "targetId", String.valueOf(userIdOf(seller)), "reason", "骚扰")));
        assertEquals("USER", ur.get("targetType").asText());
        perform(json(post("/api/v1/reports"), Map.of("targetType", "GOODS", "targetId", goodsId, "reason", "x")), 401);

        JsonNode pending = ok(auth(get("/api/v1/admin/reports").param("status", "PENDING").param("pageSize", "100"), admin));
        assertTrue(pending.get("records").findValuesAsText("id").contains(id));
        assertTrue(pending.get("records").findValuesAsText("id").contains("9000000000000000001"), "seed report");

        JsonNode h = ok(json(auth(patch("/api/v1/admin/reports/" + id), admin), Map.of("result", "APPROVE", "remark", "已下架处理")));
        assertEquals("APPROVED", h.get("status").asText());
        assertEquals("APPROVE", h.get("result").asText());
        assertEquals("已下架处理", h.get("remark").asText());
        assertTrue(h.get("auditTime").isTextual());
        assertEquals(60007, perform(json(auth(patch("/api/v1/admin/reports/" + id), admin),
                Map.of("result", "REJECT")), 200).get("code").asInt());
        assertEquals("REJECTED", ok(json(auth(patch("/api/v1/admin/reports/" + ur.get("id").asText()), admin),
                Map.of("result", "REJECT"))).get("status").asText());
        assertEquals(60010, perform(json(auth(patch("/api/v1/admin/reports/1"), admin),
                Map.of("result", "REJECT")), 200).get("code").asInt());
        // 处理后可再次举报
        ok(json(auth(post("/api/v1/reports"), reporter), Map.of("targetType", "GOODS", "targetId", goodsId, "reason", "又违规")));
    }

    @Test
    void announcementLifecycle() throws Exception {
        String admin = token("admin", "admin123");
        JsonNode pub = ok(get("/api/v1/announcements"));
        assertTrue(pub.get("records").findValuesAsText("id").contains("9100000000000000001"));
        pub.get("records").forEach(a -> assertEquals("PUBLISHED", a.get("status").asText()));
        assertFalse(pub.get("records").findValuesAsText("id").contains("9100000000000000002"), "draft hidden");
        assertEquals(60008, perform(get("/api/v1/announcements/9100000000000000002"), 200).get("code").asInt());

        JsonNode a = ok(json(auth(post("/api/v1/admin/announcements"), admin), Map.of("title", "测试公告", "content", "内容")));
        String id = a.get("id").asText();
        assertEquals("DRAFT", a.get("status").asText());
        assertTrue(a.get("publishTime").isNull());
        assertEquals(60008, perform(get("/api/v1/announcements/" + id), 200).get("code").asInt());

        JsonNode p = ok(json(auth(patch("/api/v1/admin/announcements/" + id + "/status"), admin), Map.of("status", "PUBLISHED")));
        assertEquals("PUBLISHED", p.get("status").asText());
        assertTrue(p.get("publishTime").isTextual());
        assertEquals("测试公告", ok(get("/api/v1/announcements/" + id)).get("title").asText());
        assertEquals(id, ok(get("/api/v1/announcements")).get("records").get(0).get("id").asText(), "newest first");

        JsonNode u = ok(json(auth(put("/api/v1/admin/announcements/" + id), admin), Map.of("title", "测试公告-改", "content", "新内容")));
        assertEquals("测试公告-改", u.get("title").asText());
        assertEquals("PUBLISHED", u.get("status").asText());

        ok(json(auth(patch("/api/v1/admin/announcements/" + id + "/status"), admin), Map.of("status", "OFF_SHELF")));
        assertEquals(60008, perform(get("/api/v1/announcements/" + id), 200).get("code").asInt());
        assertEquals(400, perform(json(auth(patch("/api/v1/admin/announcements/" + id + "/status"), admin),
                Map.of("status", "ARCHIVED")), 200).get("code").asInt());
        assertEquals(1, ok(auth(get("/api/v1/admin/announcements").param("status", "OFF_SHELF"), admin)).get("total").asLong());

        JsonNode direct = ok(json(auth(post("/api/v1/admin/announcements"), admin),
                Map.of("title", "直接发布", "content", "c", "status", "PUBLISHED")));
        assertEquals("PUBLISHED", direct.get("status").asText());
        assertTrue(direct.get("publishTime").isTextual());

        ok(auth(delete("/api/v1/admin/announcements/" + id), admin));
        assertEquals(60008, perform(auth(get("/api/v1/admin/announcements/" + id), admin), 200).get("code").asInt());
        perform(json(post("/api/v1/admin/announcements"), Map.of("title", "x", "content", "y")), 401);
        perform(json(auth(post("/api/v1/admin/announcements"), newUserToken()), Map.of("title", "x", "content", "y")), 403);
    }
}
