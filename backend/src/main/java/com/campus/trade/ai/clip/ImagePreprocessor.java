package com.campus.trade.ai.clip;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * CLIP 图像预处理（与 Hugging Face CLIPImageProcessor 一致）：
 * 转 RGB（透明背景合成白色）→ 短边缩放到 224（双三次、带抗锯齿，等价于 PIL BICUBIC）→ 中心裁剪 224×224
 * → 像素 /255 → 按 CLIP 均值方差标准化 → NCHW float。
 * <p>纯 Java 实现，线程安全（无共享可变状态）。webp 依赖 TwelveMonkeys imageio-webp 插件（ImageIO SPI 自动注册）。
 */
public final class ImagePreprocessor {

    public static final int SIZE = 224;
    private static final float[] MEAN = {0.48145466f, 0.4578275f, 0.40821073f};
    private static final float[] STD = {0.26862954f, 0.26130258f, 0.27577711f};

    private final long maxPixels;

    public ImagePreprocessor(long maxPixels) {
        this.maxPixels = maxPixels;
    }

    /** 解码 + 预处理，返回长度 3*224*224 的 NCHW 张量数据。 */
    public float[] preprocess(byte[] imageBytes) {
        return toTensor(decode(imageBytes));
    }

    /**
     * 解码图片。大图按整数步长子采样解码（保证短边仍 ≥ 2×224），降低内存与耗时。
     *
     * @throws IllegalArgumentException 无法识别的格式、损坏或像素过多
     */
    public BufferedImage decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("图片为空");
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> it = ImageIO.getImageReaders(in);
            if (!it.hasNext()) {
                throw new IllegalArgumentException("无法识别的图片格式");
            }
            ImageReader reader = it.next();
            try {
                reader.setInput(in, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || (long) w * h > maxPixels) {
                    throw new IllegalArgumentException("图片尺寸不合法: " + w + "x" + h);
                }
                ImageReadParam param = reader.getDefaultReadParam();
                int step = Math.max(1, Math.min(w, h) / (SIZE * 2));
                if (step > 1) {
                    param.setSourceSubsampling(step, step, 0, 0);
                }
                BufferedImage img = reader.read(0, param);
                if (img == null) {
                    throw new IllegalArgumentException("图片解码失败");
                }
                return img;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof IllegalArgumentException iae) {
                throw iae;
            }
            throw new IllegalArgumentException("图片解码失败: " + e.getMessage(), e);
        }
    }

    /** 已解码图片 → CLIP 输入张量。 */
    public static float[] toTensor(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[] argb = img.getRGB(0, 0, w, h, null, 0, w);
        boolean hasAlpha = img.getColorModel().hasAlpha();
        float[][] ch = new float[3][w * h];
        for (int i = 0; i < argb.length; i++) {
            int p = argb[i];
            float a = hasAlpha ? ((p >>> 24) & 0xFF) / 255f : 1f;
            // 透明像素合成到白色背景
            ch[0][i] = Math.round(((p >> 16) & 0xFF) * a + 255f * (1 - a));
            ch[1][i] = Math.round(((p >> 8) & 0xFF) * a + 255f * (1 - a));
            ch[2][i] = Math.round((p & 0xFF) * a + 255f * (1 - a));
        }
        // 短边缩放到 224（与 HF 实现一致：长边按比例取整）
        int nw;
        int nh;
        if (w <= h) {
            nw = SIZE;
            nh = (int) (SIZE * (double) h / w);
        } else {
            nh = SIZE;
            nw = (int) (SIZE * (double) w / h);
        }
        // 中心裁剪起点向下取整（同 HF center_crop）
        int left = (nw - SIZE) / 2;
        int top = (nh - SIZE) / 2;
        float[] out = new float[3 * SIZE * SIZE];
        for (int c = 0; c < 3; c++) {
            float[] r = Resampler.resize(ch[c], w, h, nw, nh);
            int base = c * SIZE * SIZE;
            for (int y = 0; y < SIZE; y++) {
                int sy = Math.min(nh - 1, y + top);
                for (int x = 0; x < SIZE; x++) {
                    int sx = Math.min(nw - 1, x + left);
                    float v = Math.max(0f, Math.min(255f, Math.round(r[sy * nw + sx]))) / 255f;
                    out[base + y * SIZE + x] = (v - MEAN[c]) / STD[c];
                }
            }
        }
        return out;
    }

    /** 可分离卷积重采样，双三次核 a=-0.5，缩小时按比例放宽核宽（抗锯齿），算法同 Pillow。 */
    static final class Resampler {
        private Resampler() {
        }

        static float[] resize(float[] src, int w, int h, int nw, int nh) {
            float[] tmp = w == nw ? src : horizontal(src, w, h, nw);
            return h == nh ? tmp : vertical(tmp, nw, h, nh);
        }

        private static double cubic(double x) {
            final double a = -0.5;
            x = Math.abs(x);
            if (x < 1) {
                return ((a + 2) * x - (a + 3)) * x * x + 1;
            }
            if (x < 2) {
                return (((x - 5) * x + 8) * x - 4) * a;
            }
            return 0;
        }

        /** 预计算每个输出位置的起点和权重。 */
        private static Object[] coeffs(int in, int out) {
            double scale = (double) in / out;
            double filterScale = Math.max(1.0, scale);
            double support = 2.0 * filterScale;
            int[] starts = new int[out];
            double[][] weights = new double[out][];
            for (int i = 0; i < out; i++) {
                double center = (i + 0.5) * scale;
                int xmin = Math.max(0, (int) (center - support + 0.5));
                int xmax = Math.min(in, (int) (center + support + 0.5));
                double[] k = new double[Math.max(0, xmax - xmin)];
                double sum = 0;
                for (int x = 0; x < k.length; x++) {
                    k[x] = cubic((x + xmin - center + 0.5) / filterScale);
                    sum += k[x];
                }
                if (sum != 0) {
                    for (int x = 0; x < k.length; x++) {
                        k[x] /= sum;
                    }
                }
                starts[i] = xmin;
                weights[i] = k;
            }
            return new Object[]{starts, weights};
        }

        private static float[] horizontal(float[] src, int w, int h, int nw) {
            Object[] c = coeffs(w, nw);
            int[] starts = (int[]) c[0];
            double[][] ws = (double[][]) c[1];
            float[] out = new float[nw * h];
            for (int y = 0; y < h; y++) {
                int row = y * w;
                for (int x = 0; x < nw; x++) {
                    double s = 0;
                    double[] k = ws[x];
                    int st = starts[x];
                    for (int i = 0; i < k.length; i++) {
                        s += src[row + st + i] * k[i];
                    }
                    // Pillow 在每一趟后都量化到 8 位，这里保持一致以便与 Python 参考实现逐像素对齐
                    out[y * nw + x] = (float) Math.max(0, Math.min(255, Math.round(s)));
                }
            }
            return out;
        }

        private static float[] vertical(float[] src, int w, int h, int nh) {
            Object[] c = coeffs(h, nh);
            int[] starts = (int[]) c[0];
            double[][] ws = (double[][]) c[1];
            float[] out = new float[w * nh];
            for (int y = 0; y < nh; y++) {
                double[] k = ws[y];
                int st = starts[y];
                for (int x = 0; x < w; x++) {
                    double s = 0;
                    for (int i = 0; i < k.length; i++) {
                        s += src[(st + i) * w + x] * k[i];
                    }
                    out[y * w + x] = (float) Math.max(0, Math.min(255, Math.round(s)));
                }
            }
            return out;
        }
    }
}
