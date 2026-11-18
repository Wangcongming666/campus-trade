package com.campus.trade;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class UserAndInterestTest extends BaseIntegrationTest {

    @Test
    void interestsReplaceWholeSet() throws Exception {
        String token = newUserToken();
        assertEquals(0, ok(auth(get("/api/v1/users/me/interests"), token)).size());

        JsonNode r = ok(json(auth(put("/api/v1/users/me/interests"), token),
                Map.of("categoryIds", List.of("3000000000000000002", "3000000000000000001", "3000000000000000101"))));
        assertEquals(3, r.size());
        assertEquals("3000000000000000001", r.get(0).get("id").asText(), "按分类 sort 排序");
        assertTrue(r.get(0).has("parentId") && r.get(0).has("sort") && r.get(0).has("name"));
        assertEquals(3, ok(auth(get("/api/v1/users/me/interests"), token)).size());

        // 整体覆盖
        r = ok(json(auth(put("/api/v1/users/me/interests"), token), Map.of("categoryIds", List.of("3000000000000000005"))));
        assertEquals(1, r.size());
        assertEquals("3000000000000000005", r.get(0).get("id").asText());

        // 超过 5 个 -> 400，原数据不变
        List<String> six = List.of("3000000000000000001", "3000000000000000002", "3000000000000000003",
                "3000000000000000004", "3000000000000000005", "3000000000000000006");
        assertEquals(400, perform(json(auth(put("/api/v1/users/me/interests"), token), Map.of("categoryIds", six)), 200)
                .get("code").asInt());
        // 停用 / 不存在分类 -> 400，且事务内不改动
        assertEquals(400, perform(json(auth(put("/api/v1/users/me/interests"), token),
                Map.of("categoryIds", List.of("3000000000000000001", String.valueOf(CAT_DISABLED)))), 200).get("code").asInt());
        assertEquals(400, perform(json(auth(put("/api/v1/users/me/interests"), token),
                Map.of("categoryIds", List.of("999"))), 200).get("code").asInt());
        JsonNode cur = ok(auth(get("/api/v1/users/me/interests"), token));
        assertEquals(1, cur.size());
        assertEquals("3000000000000000005", cur.get(0).get("id").asText());

        // 空数组清空
        assertEquals(0, ok(json(auth(put("/api/v1/users/me/interests"), token), Map.of("categoryIds", List.of()))).size());
        assertEquals(0, ok(auth(get("/api/v1/users/me/interests"), token)).size());

        perform(json(put("/api/v1/users/me/interests"), Map.of("categoryIds", List.of())), 401);
    }

    @Test
    void profilePasswordAndStudentVerify() throws Exception {
        String token = newUserToken();
        long uid = userIdOf(token);
        JsonNode me = ok(json(auth(put("/api/v1/users/me"), token), Map.of("nickname", "新昵称", "avatar", "/files/av.png")));
        assertEquals("新昵称", me.get("nickname").asText());
        assertEquals("/files/av.png", me.get("avatar").asText());
        assertEquals(10005, perform(json(auth(put("/api/v1/users/me"), token), Map.of("phone", "13800000001")), 200)
                .get("code").asInt());

        assertEquals(10007, perform(json(auth(put("/api/v1/users/me/password"), token),
                Map.of("oldPassword", "bad", "newPassword", "654321")), 200).get("code").asInt());
        ok(json(auth(put("/api/v1/users/me/password"), token), Map.of("oldPassword", "123456", "newPassword", "654321")));
        String username = me.get("username").asText();
        assertEquals(10002, perform(json(post("/api/v1/auth/login"),
                Map.of("account", username, "password", "123456")), 200).get("code").asInt());
        login(username, "654321");

        Map<String, Object> verify = Map.of("studentNo", "S" + uid % 100000000, "realName", "张三",
                "school", "示例大学", "cardImageUrl", "/files/card.png");
        assertEquals("PENDING", ok(json(auth(post("/api/v1/users/me/student-verify"), token), verify)).get("status").asText());
        assertEquals("PENDING", ok(auth(get("/api/v1/users/me"), token)).get("studentAuthStatus").asText());
        assertEquals(10009, perform(json(auth(post("/api/v1/users/me/student-verify"), token), verify), 200).get("code").asInt());
        // 学号被已认证用户占用
        String other = newUserToken();
        assertEquals(10010, perform(json(auth(post("/api/v1/users/me/student-verify"), other),
                Map.of("studentNo", "2022001001", "realName", "李四", "school", "示例大学", "cardImageUrl", "/files/c.png")), 200)
                .get("code").asInt());

        JsonNode pub = ok(get("/api/v1/users/" + USER1_ID));
        assertTrue(pub.get("studentVerified").asBoolean());
        assertNull(pub.get("phone"));
        assertNull(pub.get("username"));
        assertEquals(10011, perform(get("/api/v1/users/1"), 200).get("code").asInt());
    }

    @Test
    void fileUploadValidation() throws Exception {
        String token = newUserToken();
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        JsonNode f = ok(auth(multipart("/api/v1/files/upload")
                .file(new MockMultipartFile("file", "pic.png", "image/png", png)), token));
        assertTrue(f.get("url").asText().startsWith("/files/"));
        assertEquals("pic.png", f.get("name").asText());
        assertEquals(png.length, f.get("size").asLong());
        // 上传后可经 /files/** 访问
        var served = mvc.perform(get(f.get("url").asText())).andReturn().getResponse();
        assertEquals(200, served.getStatus());
        assertArrayEquals(png, served.getContentAsByteArray());

        assertEquals(70001, perform(auth(multipart("/api/v1/files/upload")
                .file(new MockMultipartFile("file", "a.gif", "image/gif", new byte[]{'G', 'I', 'F'})), token), 200)
                .get("code").asInt());
        // 伪装扩展名
        assertEquals(70001, perform(auth(multipart("/api/v1/files/upload")
                .file(new MockMultipartFile("file", "fake.png", "image/png", "hello".getBytes())), token), 200)
                .get("code").asInt());
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(png, 0, big, 0, png.length);
        assertEquals(70002, perform(auth(multipart("/api/v1/files/upload")
                .file(new MockMultipartFile("file", "big.png", "image/png", big)), token), 200).get("code").asInt());
        perform(multipart("/api/v1/files/upload").file(new MockMultipartFile("file", "p.png", "image/png", png)), 401);
    }
}
