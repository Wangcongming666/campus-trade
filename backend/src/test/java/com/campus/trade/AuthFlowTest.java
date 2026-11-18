package com.campus.trade;

import com.campus.trade.service.CaptchaService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class AuthFlowTest extends BaseIntegrationTest {

    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private CaptchaService captchaService;

    @Test
    void captchaReturnsBase64ImageStoredInRedis() throws Exception {
        JsonNode d = ok(get("/api/v1/auth/captcha"));
        String key = d.get("captchaKey").asText();
        assertTrue(d.get("imageBase64").asText().startsWith("data:image/png;base64,"));
        assertNotNull(redis.opsForValue().get("captcha:" + key));
        Long ttl = redis.getExpire("captcha:" + key);
        assertTrue(ttl != null && ttl > 0 && ttl <= 300);
    }

    @Test
    void captchaIsVerifiedWhenEnabled() throws Exception {
        ReflectionTestUtils.setField(captchaService, "enabled", true);
        try {
            JsonNode d = ok(get("/api/v1/auth/captcha"));
            String key = d.get("captchaKey").asText();
            Map<String, Object> body = new HashMap<>(Map.of("account", "user1", "password", "123456",
                    "captchaKey", key, "captchaCode", "XXXX0"));
            assertEquals(10006, perform(json(post("/api/v1/auth/login"), body), 200).get("code").asInt());
            // 验证码一次性：错误后已删除，重新获取
            d = ok(get("/api/v1/auth/captcha"));
            key = d.get("captchaKey").asText();
            String code = redis.opsForValue().get("captcha:" + key);
            body.put("captchaKey", key);
            body.put("captchaCode", code.toLowerCase());
            assertNotNull(ok(json(post("/api/v1/auth/login"), body)).get("accessToken"));
            assertNull(redis.opsForValue().get("captcha:" + key));
        } finally {
            ReflectionTestUtils.setField(captchaService, "enabled", false);
        }
    }

    @Test
    void registerLoginByUsernamePhoneAndStudentNo() throws Exception {
        String name = "reg_" + UUID.randomUUID().toString().substring(0, 8);
        String phone = "139" + String.valueOf(System.nanoTime()).substring(0, 8);
        JsonNode u = ok(json(post("/api/v1/auth/register"), Map.of("username", name, "password", "abc12345", "phone", phone)));
        assertTrue(u.get("id").isTextual(), "Long id serialized as string");
        assertEquals("USER", u.get("role").asText());
        assertEquals("NONE", u.get("studentAuthStatus").asText());

        // 重复用户名 10001
        assertEquals(10001, perform(json(post("/api/v1/auth/register"),
                Map.of("username", name, "password", "abc12345")), 200).get("code").asInt());
        // 校验失败 400
        JsonNode bad = perform(json(post("/api/v1/auth/register"), Map.of("username", "a", "password", "1")), 200);
        assertEquals(400, bad.get("code").asInt());

        JsonNode l = login(name, "abc12345");
        assertEquals(7200, l.get("expiresIn").asLong());
        assertEquals(name, l.get("userInfo").get("username").asText());
        assertNotNull(l.get("refreshToken").asText());
        assertNotNull(jdbc.queryForObject("SELECT last_login_time FROM sys_user WHERE username=?", Object.class, name));

        assertEquals(name, login(phone, "abc12345").get("userInfo").get("username").asText());
        assertEquals("user1", login("2022001001", "123456").get("userInfo").get("username").asText());

        assertEquals(10002, perform(json(post("/api/v1/auth/login"),
                Map.of("account", name, "password", "wrong-pwd")), 200).get("code").asInt());
        assertEquals(10002, perform(json(post("/api/v1/auth/login"),
                Map.of("account", "nobody_xyz", "password", "wrong-pwd")), 200).get("code").asInt());
    }

    @Test
    void bannedUserIsRejected() throws Exception {
        String name = "ban_" + UUID.randomUUID().toString().substring(0, 8);
        ok(json(post("/api/v1/auth/register"), Map.of("username", name, "password", "123456")));
        String refresh = login(name, "123456").get("refreshToken").asText();
        jdbc.update("UPDATE sys_user SET status='BANNED' WHERE username=?", name);
        assertEquals(10004, perform(json(post("/api/v1/auth/login"),
                Map.of("account", name, "password", "123456")), 200).get("code").asInt());
        assertEquals(10004, perform(json(post("/api/v1/auth/refresh"),
                Map.of("refreshToken", refresh)), 200).get("code").asInt());
    }

    @Test
    void refreshRotatesAndLogoutBlacklists() throws Exception {
        JsonNode l = login("user2", "123456");
        String access = l.get("accessToken").asText();
        String refresh = l.get("refreshToken").asText();

        JsonNode r = ok(json(post("/api/v1/auth/refresh"), Map.of("refreshToken", refresh)));
        String access2 = r.get("accessToken").asText();
        String refresh2 = r.get("refreshToken").asText();
        assertNotEquals(refresh, refresh2);
        assertNull(r.get("userInfo"));
        // 旧 refreshToken 已作废 -> 401
        JsonNode old = perform(json(post("/api/v1/auth/refresh"), Map.of("refreshToken", refresh)), 401);
        assertEquals(401, old.get("code").asInt());

        // 两个 accessToken 都可用
        ok(auth(get("/api/v1/users/me"), access));
        ok(auth(get("/api/v1/users/me"), access2));

        // 退出：access2 进入黑名单，refresh2 被删除
        ok(auth(post("/api/v1/auth/logout"), access2));
        JsonNode after = perform(auth(get("/api/v1/users/me"), access2), 401);
        assertEquals(401, after.get("code").asInt());
        assertEquals("登录已过期", after.get("msg").asText());
        perform(json(post("/api/v1/auth/refresh"), Map.of("refreshToken", refresh2)), 401);
        // 另一个会话不受影响
        ok(auth(get("/api/v1/users/me"), access));
    }
}
