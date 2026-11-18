package com.campus.trade.ai.clip;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 零样本打分：图像向量与各分类文本向量的余弦相似度 × logitScale，softmax 得到置信度，取 TopK。
 * 纯计算，无状态。
 */
public final class ZeroShotScorer {

    private ZeroShotScorer() {
    }

    /** 打分结果（置信度为 softmax 概率，所有参与分类之和为 1）。 */
    public record Scored(CategoryEmbeddings.Entry category, double cosine, double probability) {
    }

    public static float[] l2normalize(float[] v) {
        double s = 0;
        for (float x : v) {
            s += (double) x * x;
        }
        double n = Math.sqrt(s);
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            out[i] = (float) (v[i] / (n + 1e-12));
        }
        return out;
    }

    /**
     * @param imageEmbedding 图像编码器输出（不要求已归一化）
     * @param candidates     参与打分的分类（如只取启用分类）
     * @param logitScale     温度，CLIP 默认 100
     * @param topK           返回前 K 个，按概率降序
     */
    public static List<Scored> score(float[] imageEmbedding, List<CategoryEmbeddings.Entry> candidates,
                                     double logitScale, int topK) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        float[] img = l2normalize(imageEmbedding);
        int n = candidates.size();
        double[] cos = new double[n];
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            float[] t = candidates.get(i).vector();
            if (t.length != img.length) {
                throw new IllegalArgumentException("向量维度不一致: " + t.length + " vs " + img.length);
            }
            double d = 0;
            for (int j = 0; j < img.length; j++) {
                d += (double) img[j] * t[j];
            }
            cos[i] = d;
            max = Math.max(max, d * logitScale);
        }
        double sum = 0;
        double[] e = new double[n];
        for (int i = 0; i < n; i++) {
            e[i] = Math.exp(cos[i] * logitScale - max);
            sum += e[i];
        }
        List<Scored> all = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            all.add(new Scored(candidates.get(i), cos[i], e[i] / sum));
        }
        all.sort(Comparator.comparingDouble(Scored::probability).reversed());
        return all.subList(0, Math.min(Math.max(topK, 0), n));
    }
}
