package com.campus.trade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestDatabaseInitializer.class)
public abstract class BaseIntegrationTest {

    public static final long USER1_ID = 2000000000000000002L;
    public static final long USER2_ID = 2000000000000000003L;
    public static final long CAT_DIGITAL = 3000000000000000001L;
    public static final long CAT_PHONE = 3000000000000000101L;
    public static final long CAT_DISABLED = 3000000000000000009L;
    public static final long GOODS_HEADPHONE = 4000000000000000003L; // user2, ON_SHELF
    public static final long GOODS_PENDING = 4000000000000000011L;   // user1, PENDING_AUDIT
    public static final long GOODS_OFF = 4000000000000000012L;       // user2, OFF_SHELF

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper om;
    @Autowired
    protected JdbcTemplate jdbc;

    protected JsonNode perform(MockHttpServletRequestBuilder req, int expectHttp) throws Exception {
        MvcResult r = mvc.perform(req).andReturn();
        String body = r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(expectHttp, r.getResponse().getStatus(), "HTTP status, body=" + body);
        return om.readTree(body);
    }

    protected JsonNode ok(MockHttpServletRequestBuilder req) throws Exception {
        JsonNode n = perform(req, 200);
        assertEquals(200, n.get("code").asInt(), n.toString());
        return n.get("data");
    }

    protected MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, Object body) throws Exception {
        return req.contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body));
    }

    protected MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder req, String token) {
        return req.header("Authorization", "Bearer " + token);
    }

    protected JsonNode login(String account, String password) throws Exception {
        return ok(json(post("/api/v1/auth/login"), Map.of("account", account, "password", password)));
    }

    protected String token(String account, String password) throws Exception {
        return login(account, password).get("accessToken").asText();
    }

    /** 注册一个新用户并返回 accessToken。 */
    protected String newUserToken() throws Exception {
        String name = "t_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        ok(json(post("/api/v1/auth/register"), Map.of("username", name, "password", "123456")));
        return token(name, "123456");
    }

    /** 以 token 对应用户发布一件商品并直接置为 ON_SHELF（跳过审核），返回商品 ID。 */
    protected String onShelfGoods(String token, String title, double price) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("title", title);
        body.put("description", "描述 " + title);
        body.put("categoryId", String.valueOf(CAT_PHONE));
        body.put("condition", "GOOD");
        body.put("tradeMethod", "FACE");
        body.put("price", price);
        body.put("images", java.util.List.of("/files/" + UUID.randomUUID() + ".png"));
        String id = ok(json(auth(post("/api/v1/goods"), token), body)).get("id").asText();
        jdbc.update("UPDATE goods SET status='ON_SHELF' WHERE id=?", Long.parseLong(id));
        return id;
    }

    protected String goodsStatus(String goodsId) {
        return jdbc.queryForObject("SELECT status FROM goods WHERE id=?", String.class, Long.parseLong(goodsId));
    }

    protected long userIdOf(String token) throws Exception {
        return ok(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/users/me"), token))
                .get("id").asLong();
    }

    protected void await(BooleanSupplier cond, String message) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            if (cond.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        fail("timeout waiting: " + message);
    }

    protected int behaviorCount(long userId, long goodsId, int type) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM user_behavior WHERE user_id=? AND goods_id=? AND behavior_type=?",
                Integer.class, userId, goodsId, type);
        return n == null ? 0 : n;
    }
}
