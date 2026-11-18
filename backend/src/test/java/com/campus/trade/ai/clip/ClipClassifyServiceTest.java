package com.campus.trade.ai.clip;

import com.campus.trade.BaseIntegrationTest;
import com.campus.trade.ai.ClassifyResult;
import com.campus.trade.ai.ClassifyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * provider=clip 且模型存在时，经后端 ClassifyService 走完整链路（含分类校验与名称补全）。
 * 模型目录可用 -Dclip.test.modelDir 指定；模型缺失时跳过。
 */
@TestPropertySource(properties = {
        "campus.ai.classify.provider=clip",
        "campus.ai.clip.model-dir=${clip.test.modelDir:../scripts/ai/models/clip-vit-base-patch32}",
        "campus.ai.clip.preload=false"})
class ClipClassifyServiceTest extends BaseIntegrationTest {

    static final long BIKE = 3000000000000000801L;
    static final long TRANSPORT = 3000000000000000008L;

    @Autowired
    ClassifyService classifyService;
    @Autowired
    ClipProperties props;

    @Test
    void classifiesRealPhotosThroughService() throws Exception {
        assumeTrue(Files.isRegularFile(Path.of(props.getModelDir(), props.getVisionModel())), "模型未下载，跳过");
        Path photos = Path.of(System.getProperty("clip.test.images", "../experiments/image_classify/images"));
        assumeTrue(Files.isDirectory(photos), "实验图片未下载，跳过");
        List<Path> bikes;
        try (var s = Files.list(photos)) {
            bikes = s.filter(p -> p.getFileName().toString().startsWith(BIKE + "_")).sorted().limit(3).toList();
        }
        assumeTrue(!bikes.isEmpty(), "无自行车样本，跳过");
        for (Path p : bikes) {
            ClassifyResult r = classifyService.classify(Files.readAllBytes(p), p.getFileName().toString());
            assertEquals("CLIP", r.source(), p.toString());
            assertEquals(3, r.candidates().size());
            assertEquals(BIKE, r.categoryId(), p.toString());
            assertEquals("自行车", r.categoryName());
            assertEquals(TRANSPORT, r.parentCategoryId());
            assertEquals("交通出行", r.parentCategoryName());
            assertTrue(r.confidence() > 0 && r.confidence() <= 1);
        }
    }
}
