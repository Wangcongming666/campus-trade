package com.campus.trade.ai.clip;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class ImagePreprocessorTest {

    /** 与 Python 参考实现（PIL BICUBIC，experiments/image_classify/eval_zero_shot.py 的 preprocess）对齐。 */
    @Test
    void matchesPillowReference() throws Exception {
        int w = 301;
        int h = 187;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = (x * 7 + y * 3) % 256;
                int g = (x * x + y) % 256;
                int b = (x ^ y) % 256;
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        // 走完整的编码→解码路径
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        float[] t = new ImagePreprocessor(80_000_000L).preprocess(bos.toByteArray());
        int s = ImagePreprocessor.SIZE;
        assertEquals(3 * s * s, t.length);
        // 参考值由 PIL 计算（见 scripts/ai/README.md「一致性校验」）
        double[][] ref = {{0, 0, 0, 0.266115665435791}, {1, 100, 50, 1.1594098806381226},
                {2, 223, 223, -0.427934855222702}, {0, 112, 112, -1.0185459852218628},
                {1, 7, 200, -1.662050485610962}};
        for (double[] r : ref) {
            int idx = (int) r[0] * s * s + (int) r[1] * s + (int) r[2];
            assertEquals(r[3], t[idx], 0.02, "c,y,x=" + (int) r[0] + "," + (int) r[1] + "," + (int) r[2]);
        }
        double mean = 0;
        for (float v : t) {
            mean += v;
        }
        assertEquals(0.2208602875471115, mean / t.length, 0.005);
    }

    @Test
    void decodesWebpViaTwelveMonkeys() {
        byte[] webp = Base64.getDecoder().decode("UklGRhwAAABXRUJQVlA4TA8AAAAvB8ABAAcQ/Y/+ByKi/wEA");
        ImagePreprocessor p = new ImagePreprocessor(80_000_000L);
        BufferedImage img = p.decode(webp);
        assertEquals(8, img.getWidth());
        int rgb = img.getRGB(3, 3) & 0xFFFFFF;
        assertEquals(0xFF0000, rgb);
        assertEquals(3 * 224 * 224, p.preprocess(webp).length);
    }

    @Test
    void transparentPixelsBecomeWhite() {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB); // 全透明
        float[] t = ImagePreprocessor.toTensor(img);
        // 白色 255 标准化后：(1-mean)/std
        assertEquals((1 - 0.48145466) / 0.26862954, t[0], 1e-4);
    }

    @Test
    void rejectsGarbageAndOversizedImages() {
        ImagePreprocessor p = new ImagePreprocessor(80_000_000L);
        assertThrows(IllegalArgumentException.class, () -> p.decode(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> p.decode("not an image".getBytes()));
        ImagePreprocessor tiny = new ImagePreprocessor(50);
        assertThrows(IllegalArgumentException.class, () -> {
            BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, "jpg", bos);
            tiny.decode(bos.toByteArray());
        });
    }

    @Test
    void largeImageIsSubsampledOnDecode() throws Exception {
        BufferedImage img = new BufferedImage(2000, 1500, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", bos);
        BufferedImage d = new ImagePreprocessor(80_000_000L).decode(bos.toByteArray());
        assertTrue(Math.min(d.getWidth(), d.getHeight()) >= 448);
        assertTrue(d.getWidth() < 2000);
    }
}
