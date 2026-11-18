package com.campus.trade.ai.clip;

import com.campus.trade.BaseIntegrationTest;
import com.campus.trade.ai.ClassifyResult;
import com.campus.trade.ai.ClassifyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 模型文件缺失时应用照常启动、其他接口正常，分类器报告不可用（降级）。
 */
@TestPropertySource(properties = {"campus.ai.classify.provider=clip", "campus.ai.clip.model-dir=/nonexistent/clip-model",
        "campus.ai.clip.preload=true"})
class ClipClassifierContextTest extends BaseIntegrationTest {

    @Autowired
    ClipImageClassifier classifier;
    @Autowired
    ClassifyService classifyService;

    @Test
    void appStartsWithoutModel() throws Exception {
        assertEquals("clip", classifier.name());
        assertFalse(classifier.isAvailable());
        assertThrows(IllegalStateException.class, () -> classifier.classify(new byte[10], "a.jpg"));
        assertTrue(ok(get("/api/v1/categories")).isArray());
        // 经后端 ClassifyService 调用：降级为空建议
        ClassifyResult r = classifyService.classify(new byte[10], "a.jpg");
        assertEquals(ClassifyResult.SOURCE_NONE, r.source());
        assertNull(r.categoryId());
        assertTrue(r.candidates().isEmpty());
    }
}
