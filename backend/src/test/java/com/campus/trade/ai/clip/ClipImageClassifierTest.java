package com.campus.trade.ai.clip;

import com.campus.trade.ai.ClassifyResult;
import com.campus.trade.entity.Category;
import com.campus.trade.mapper.CategoryMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 不启动 Spring 的分类器测试。需要真实模型的用例在模型缺失时自动跳过
 * （模型目录可用 -Dclip.test.modelDir=... 指定，默认 ../scripts/ai/models/clip-vit-base-patch32）。
 */
class ClipImageClassifierTest {

    static final String MODEL_DIR = System.getProperty("clip.test.modelDir", "../scripts/ai/models/clip-vit-base-patch32");
    static final long PHONE = 3000000000000000101L;
    static final long BIKE = 3000000000000000801L;
    static final long DIGITAL = 3000000000000000001L;

    @SuppressWarnings("unchecked")
    private static ObjectProvider<CategoryMapper> provider(CategoryMapper m) {
        ObjectProvider<CategoryMapper> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(m);
        return p;
    }

    private static Category cat(long id, Long parent, String name, String status) {
        Category c = new Category();
        c.setId(id);
        c.setParentId(parent);
        c.setName(name);
        c.setStatus(status);
        return c;
    }

    private static boolean modelPresent() {
        return Files.isRegularFile(Path.of(MODEL_DIR, "vision_model.onnx"))
                && Files.isRegularFile(Path.of(MODEL_DIR, "category_embeddings.json"));
    }

    private static byte[] jpeg(BufferedImage img) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", bos);
        return bos.toByteArray();
    }

    @Test
    void missingModelDegradesGracefully() {
        ClipProperties props = new ClipProperties();
        props.setModelDir("/nonexistent/clip");
        ClipImageClassifier c = new ClipImageClassifier(props, provider(null), "clip");
        assertEquals("clip", c.name());
        assertFalse(c.isAvailable());
        assertTrue(c.status().startsWith("failed"));
        assertThrows(IllegalStateException.class, () -> c.classify(new byte[]{1, 2, 3}, "a.jpg"));
        // 重试间隔内不重复尝试加载（快速返回）
        long t0 = System.nanoTime();
        assertFalse(c.isAvailable());
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 50);
        c.close();
    }

    @Test
    void disabledSwitch() {
        ClipProperties props = new ClipProperties();
        props.setEnabled(false);
        props.setModelDir(MODEL_DIR);
        ClipImageClassifier c = new ClipImageClassifier(props, provider(null), "clip");
        assertFalse(c.isAvailable());
        assertEquals("disabled", c.status());
    }

    @Test
    void corruptModelFileDegrades() throws Exception {
        Path dir = Files.createTempDirectory("clip-bad");
        Files.writeString(dir.resolve("vision_model.onnx"), "not a model");
        Files.writeString(dir.resolve("category_embeddings.json"),
                "{\"categories\":[{\"categoryId\":\"1\",\"name\":\"a\",\"parentId\":null,\"parentName\":null,"
                        + "\"embedding\":[1,0],\"singleEmbedding\":[1,0]}]}");
        ClipProperties props = new ClipProperties();
        props.setModelDir(dir.toString());
        ClipImageClassifier c = new ClipImageClassifier(props, provider(null), "clip");
        assertFalse(c.isAvailable());
    }

    @Test
    void realModelReturnsTop3WithParents() throws Exception {
        assumeTrue(modelPresent(), "模型未下载，跳过");
        ClipProperties props = new ClipProperties();
        props.setModelDir(MODEL_DIR);
        ClipImageClassifier c = new ClipImageClassifier(props, provider(null), "clip");
        assertTrue(c.isAvailable());

        BufferedImage img = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 640, 480);
        g.setColor(Color.BLACK);
        g.fillRoundRect(250, 60, 140, 360, 30, 30);
        g.dispose();
        ClassifyResult r = c.classify(jpeg(img), "x.jpg");
        assertEquals("CLIP", r.source());
        assertEquals(3, r.candidates().size());
        assertNotNull(r.categoryId());
        assertEquals(r.candidates().get(0).categoryId(), r.categoryId());
        assertEquals(r.candidates().get(0).confidence(), r.confidence());
        double sum = 0;
        for (int i = 0; i < 3; i++) {
            ClassifyResult.Candidate cd = r.candidates().get(i);
            assertNotNull(cd.categoryName());
            assertTrue(cd.confidence() >= 0 && cd.confidence() <= 1);
            if (i > 0) {
                assertTrue(r.candidates().get(i - 1).confidence() >= cd.confidence());
            }
            assertNotNull(cd.parentCategoryId());
            assertNotNull(cd.parentCategoryName());
            sum += cd.confidence();
        }
        assertTrue(sum <= 1.0001);

        // 真实照片（实验样本存在时）：自行车应进入 Top3
        Path photos = Path.of(System.getProperty("clip.test.images", "../experiments/image_classify/images"));
        if (Files.isDirectory(photos)) {
            try (var s = Files.list(photos)) {
                List<Path> bikes = s.filter(p -> p.getFileName().toString().startsWith(BIKE + "_")).limit(3).toList();
                for (Path p : bikes) {
                    ClassifyResult br = c.classify(Files.readAllBytes(p), p.getFileName().toString());
                    assertTrue(br.candidates().stream().anyMatch(x -> x.categoryId() == BIKE), p.toString());
                }
            }
        }
        c.close();
    }

    @Test
    void disabledCategoriesFilteredAndNamesFromDb() throws Exception {
        assumeTrue(modelPresent(), "模型未下载，跳过");
        CategoryMapper mapper = mock(CategoryMapper.class);
        List<Category> cats = new ArrayList<>();
        cats.add(cat(DIGITAL, null, "数码产品", "ENABLED"));               // 改名
        cats.add(cat(PHONE, DIGITAL, "手机", "DISABLED"));               // 停用
        cats.add(cat(3000000000000000102L, DIGITAL, "电脑平板", "ENABLED"));
        cats.add(cat(3000000000000000103L, DIGITAL, "耳机音箱", "ENABLED"));
        cats.add(cat(3000000000000000004L, null, "服饰鞋包", "ENABLED"));
        when(mapper.selectList(any())).thenReturn(cats);
        ClipProperties props = new ClipProperties();
        props.setModelDir(MODEL_DIR);
        ClipImageClassifier c = new ClipImageClassifier(props, provider(mapper), "clip");
        BufferedImage img = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
        ClassifyResult r = c.classify(jpeg(img), "x.jpg");
        Set<Long> allowed = Set.of(3000000000000000102L, 3000000000000000103L, 3000000000000000004L);
        assertEquals(3, r.candidates().size());
        for (ClassifyResult.Candidate cd : r.candidates()) {
            assertTrue(allowed.contains(cd.categoryId()), "只应出现启用的叶子分类: " + cd);
            if (cd.categoryId() == 3000000000000000004L) {
                // 无子分类的一级分类：父分类填自身
                assertEquals(cd.categoryId(), cd.parentCategoryId());
                assertEquals("服饰鞋包", cd.parentCategoryName());
            } else {
                assertEquals(DIGITAL, cd.parentCategoryId());
                assertEquals("数码产品", cd.parentCategoryName());
            }
        }
        double sum = r.candidates().stream().mapToDouble(ClassifyResult.Candidate::confidence).sum();
        assertEquals(1.0, sum, 0.001); // 只剩 3 个可选分类，概率在其中重新归一化
        c.close();
    }
}
