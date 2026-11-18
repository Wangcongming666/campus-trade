package com.campus.trade;

import com.campus.trade.ai.CategoryResolver;
import com.campus.trade.ai.ClassifyResult;
import com.campus.trade.ai.ClassifyService;
import com.campus.trade.ai.ImageClassifier;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** POST /api/v1/ai/classify（测试配置 provider=mock）与 ClassifyService 选择/兜底逻辑。 */
class AiClassifyTest extends BaseIntegrationTest {

    static final long CAT_HEADPHONE = 3000000000000000103L;
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};

    @Autowired
    CategoryResolver resolver;
    @Autowired
    List<ImageClassifier> classifiers;

    private MockMultipartFile png(String name) {
        return new MockMultipartFile("file", name, "image/png", PNG);
    }

    @Test
    void classifyUploadedFileByMock() throws Exception {
        String t = newUserToken();
        JsonNode d = ok(auth(multipart("/api/v1/ai/classify").file(png("airpods-pro-耳机.png")), t));
        assertEquals("MOCK", d.get("source").asText());
        assertEquals(String.valueOf(CAT_HEADPHONE), d.get("categoryId").asText());
        assertTrue(d.get("categoryId").isTextual(), "Long → string in record");
        assertEquals("耳机音箱", d.get("categoryName").asText());
        assertEquals(String.valueOf(CAT_DIGITAL), d.get("parentCategoryId").asText());
        assertEquals("数码电子", d.get("parentCategoryName").asText());
        assertTrue(d.get("confidence").asDouble() >= 0.6);
        JsonNode cands = d.get("candidates");
        assertTrue(cands.size() >= 1 && cands.size() <= 3);
        assertEquals(String.valueOf(CAT_HEADPHONE), cands.get(0).get("categoryId").asText());
        assertTrue(cands.get(0).get("parentCategoryId").isTextual());
        for (int i = 1; i < cands.size(); i++) {
            assertTrue(cands.get(i - 1).get("confidence").asDouble() >= cands.get(i).get("confidence").asDouble());
        }
        // 识别不出：低置信度 Top3 候选，分类都存在于数据库
        JsonNode unknown = ok(auth(multipart("/api/v1/ai/classify").file(png("IMG_0001.png")), t));
        assertEquals(3, unknown.get("candidates").size());
        assertTrue(unknown.get("confidence").asDouble() <= 0.1);
        for (JsonNode c : unknown.get("candidates")) {
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM category WHERE id=? AND status='ENABLED'",
                    Integer.class, c.get("categoryId").asLong()));
        }
    }

    @Test
    void classifyByLocalImageUrlOnly() throws Exception {
        String t = newUserToken();
        String url = ok(auth(multipart("/api/v1/files/upload").file(png("guitar.png")), t)).get("url").asText();
        assertTrue(url.startsWith("/files/"));
        JsonNode d = ok(auth(multipart("/api/v1/ai/classify").param("imageUrl", url), t));
        assertEquals("MOCK", d.get("source").asText());
        assertEquals(3, d.get("candidates").size());

        // 防 SSRF / 路径穿越：非本站路径一律 400
        for (String bad : List.of("http://169.254.169.254/latest/meta-data", "https://example.com/a.png",
                "//evil.com/a.png", "/files/../../etc/passwd", "/etc/passwd", "file:///etc/passwd",
                "/files/%2e%2e/secret.png", "/api/v1/users/me")) {
            assertEquals(400, perform(auth(multipart("/api/v1/ai/classify").param("imageUrl", bad), t), 200)
                    .get("code").asInt(), bad);
        }
        assertEquals(90002, perform(auth(multipart("/api/v1/ai/classify").param("imageUrl", "/files/2026/01/01/none.png"), t), 200)
                .get("code").asInt());
    }

    @Test
    void validationAndAuth() throws Exception {
        String t = newUserToken();
        assertEquals(401, perform(multipart("/api/v1/ai/classify").file(png("a.png")), 401).get("code").asInt());
        assertEquals(90001, perform(auth(multipart("/api/v1/ai/classify"), t), 200).get("code").asInt());
        assertEquals(70001, perform(auth(multipart("/api/v1/ai/classify")
                .file(new MockMultipartFile("file", "a.gif", "image/gif", "GIF89a".getBytes())), t), 200).get("code").asInt());
        // 扩展名伪装
        assertEquals(70001, perform(auth(multipart("/api/v1/ai/classify")
                .file(new MockMultipartFile("file", "a.png", "image/png", "not an image".getBytes())), t), 200).get("code").asInt());
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        assertEquals(70002, perform(auth(multipart("/api/v1/ai/classify")
                .file(new MockMultipartFile("file", "big.png", "image/png", big)), t), 200).get("code").asInt());
    }

    /** 可控的测试分类器。 */
    record Stub(String name, boolean isAvailable, ClassifyResult result, RuntimeException error) implements ImageClassifier {
        @Override
        public ClassifyResult classify(byte[] imageBytes, String filename) {
            if (error != null) {
                throw error;
            }
            return result;
        }
    }

    @Test
    void serviceSelectsByNameAndReturnsNoneOnFailure() {
        ClassifyResult.Candidate good = new ClassifyResult.Candidate(CAT_HEADPHONE, "x", null, null, 0.4);
        ClassifyResult.Candidate missing = new ClassifyResult.Candidate(123L, "不存在", null, null, 0.9);
        ClassifyResult.Candidate phone = new ClassifyResult.Candidate(CAT_PHONE, "y", null, null, 0.7);
        ClassifyResult.Candidate disabled = new ClassifyResult.Candidate(CAT_DISABLED, "停用", null, null, 0.8);
        ClassifyResult raw = new ClassifyResult(123L, "不存在", null, null, 0.9, List.of(missing, good, disabled, phone), null);
        List<ImageClassifier> list = List.of(new Stub("ok", true, raw, null), new Stub("down", false, raw, null),
                new Stub("boom", true, raw, new IllegalStateException("model crashed")));

        ClassifyResult r = new ClassifyService(list, resolver, "OK").classify(PNG, "a.png");
        assertEquals(CAT_PHONE, r.categoryId(), "invalid/disabled dropped, sorted by confidence");
        assertEquals("手机", r.categoryName());
        assertEquals(CAT_DIGITAL, r.parentCategoryId());
        assertEquals(List.of(CAT_PHONE, CAT_HEADPHONE), r.candidates().stream().map(ClassifyResult.Candidate::categoryId).toList());
        assertEquals("ok", r.source());

        for (String p : List.of("down", "boom", "clip", "absent")) {
            ClassifyResult none = new ClassifyService(list, resolver, p).classify(PNG, "a.png");
            assertEquals("NONE", none.source(), p);
            assertNull(none.categoryId());
            assertNull(none.categoryName());
            assertTrue(none.candidates().isEmpty());
        }
        // baidu 未配置密钥：不可用 → NONE（不降级 mock）
        ClassifyResult baidu = new ClassifyService(classifiers, resolver, "baidu").classify(PNG, "airpods.png");
        assertEquals("NONE", baidu.source());
        // 全部候选都无效 → NONE
        ClassifyResult allBad = new ClassifyService(List.of(new Stub("bad", true,
                new ClassifyResult(123L, null, null, null, 0.9, List.of(missing), "bad"), null)), resolver, "bad")
                .classify(PNG, "a.png");
        assertEquals("NONE", allBad.source());
    }
}
