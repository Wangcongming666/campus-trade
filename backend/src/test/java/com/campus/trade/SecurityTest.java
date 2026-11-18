package com.campus.trade;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class SecurityTest extends BaseIntegrationTest {

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        JsonNode n = perform(get("/api/v1/users/me"), 401);
        assertEquals(401, n.get("code").asInt());
        assertTrue(n.get("data").isNull());
        perform(json(post("/api/v1/goods"), Map.of()), 401);
        perform(post("/api/v1/goods/" + GOODS_HEADPHONE + "/favorite"), 401);
        perform(get("/api/v1/users/me/interests"), 401);
        perform(json(post("/api/v1/price/suggest"), Map.of()), 401);
    }

    @Test
    void invalidTokenReturns401Expired() throws Exception {
        JsonNode n = perform(auth(get("/api/v1/users/me"), "not-a-jwt"), 401);
        assertEquals("登录已过期", n.get("msg").asText());
        // 白名单接口带无效 token 仍按游客访问
        ok(auth(get("/api/v1/goods"), "not-a-jwt"));
    }

    @Test
    void guestWhitelist() throws Exception {
        ok(get("/api/v1/goods"));
        ok(get("/api/v1/goods/search").param("keyword", "耳机"));
        ok(get("/api/v1/goods/" + GOODS_HEADPHONE));
        ok(get("/api/v1/categories"));
        ok(get("/api/v1/categories/tree"));
        ok(get("/api/v1/auth/captcha"));
        ok(get("/api/v1/users/" + USER1_ID));
        // /recommend/** 放行（phase 1 尚无实现 -> 不是 401/403）
        int s = mvc.perform(get("/api/v1/recommend/home")).andReturn().getResponse().getStatus();
        assertNotEquals(401, s);
        assertNotEquals(403, s);
    }

    @Test
    void adminEndpointsRequireAdminRole() throws Exception {
        String userToken = token("user1", "123456");
        JsonNode n = perform(auth(get("/api/v1/admin/stats"), userToken), 403);
        assertEquals(403, n.get("code").asInt());
        perform(get("/api/v1/admin/stats"), 401);
        String adminToken = token("admin", "admin123");
        int s = mvc.perform(auth(get("/api/v1/admin/stats"), adminToken)).andReturn().getResponse().getStatus();
        assertNotEquals(401, s);
        assertNotEquals(403, s);
    }

    @Test
    void docsAreAccessible() throws Exception {
        perform(get("/v3/api-docs"), 200);
    }
}
