package com.campus.trade.price.core;

import com.campus.trade.recommend.core.ContentSimilarity;
import com.campus.trade.recommend.core.ScoredItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 二手价格建议（纯 Java）。
 * <pre>
 * 1. 成色对齐：  p̃_s = p_s · f(c_q) / f(c_s)
 * 2. 样本权重：  w_s = (sold ? w_sold : w_listed) · (1 + γ · cos(title_q, title_s))
 * 3. IQR 去异常：保留 p̃ ∈ [Q1 − k·IQR, Q3 + k·IQR]
 * 4. 样本估计：  m = wMedian(p̃), [lo, hi] = [wP25, wP75]
 * 5. 原价融合：  a = orig · f(c_q)；
 *               λ = n/(n+κ) · σ_a² / (σ_a² + σ_s²)
 *               σ_s：对齐后样本 ln(价格) 的稳健标准差 IQR/1.349（同类商品之间的价位差异）
 *               σ_a：带原价样本残差 ln(p_s / (orig_s·f(c_s))) 的稳健标准差（"原价×成色系数"的折算误差）
 *               P = λ·m + (1−λ)·a，区间 = λ·[lo,hi] + (1−λ)·a·[e^{Q1(残差)}, e^{Q3(残差)}]
 * 6. 约束：      P ≤ orig，min ≤ P ≤ max ≤ orig
 * </pre>
 * 0. 成色系数校准（可选）：f̂(c) = (n_c·median(p/orig) + m·f₀(c)) / (n_c + m)，见 {@link ConditionFactors#estimate}
 * 样本数 n &lt; minSamples 且有原价时 λ &lt; 0.5，即"主要依赖原价折算"；无原价且无样本时返回分类整体中位数。
 */
public final class PriceSuggester {

    /** 样本不足 4 个时的默认离散度（对数尺度）：同类样本价位差异大，原价折算误差相对小。 */
    static final double DEFAULT_SIGMA_SAMPLES = 0.6;
    static final double DEFAULT_SIGMA_ANCHOR = 0.3;

    private final PriceSuggestConfig cfg;

    public PriceSuggester(PriceSuggestConfig cfg) {
        this.cfg = cfg;
    }

    public PriceSuggestConfig config() {
        return cfg;
    }

    /**
     * @param q              请求
     * @param samples        同分类样本（调用方已筛选：已售/在售、未删除；可包含请求商品之外的任何商品）
     * @param categoryMedian 分类整体价格中位数（无原价且无样本时的兜底，可为 null）
     */
    public PriceSuggestion suggest(PriceQuery q, List<PriceSample> samples, Double categoryMedian) {
        ConditionFactors f = cfg.estimateFactors() ? cfg.factors().estimate(samples, cfg.factorPrior()) : cfg.factors();
        double fq = f.of(q.condition());
        Double orig = q.originalPrice() != null && q.originalPrice() > 0 ? q.originalPrice() : null;
        String condName = conditionName(q.condition());

        // 1-2. 对齐 + 权重
        Map<Long, Double> titleCos = cfg.titleWeighting() ? titleCosine(q.title(), samples) : Map.of();
        List<double[]> pts = new ArrayList<>(); // [alignedPrice, weight]
        for (PriceSample s : samples) {
            if (s.price() <= 0) {
                continue;
            }
            double p = cfg.conditionAlign() ? s.price() * fq / f.of(s.condition()) : s.price();
            double w = s.sold() ? cfg.soldWeight() : cfg.listedWeight();
            w *= 1 + cfg.titleGamma() * titleCos.getOrDefault(s.goodsId(), 0.0);
            if (w > 0) {
                pts.add(new double[]{p, w});
            }
        }
        // 3. IQR 去异常
        int removed = 0;
        if (cfg.iqrFilter() && pts.size() >= 4) {
            List<Double> vals = pts.stream().map(a -> a[0]).toList();
            double q1 = WeightedStats.quantile(vals, 0.25);
            double q3 = WeightedStats.quantile(vals, 0.75);
            double iqr = q3 - q1;
            double lo = q1 - cfg.iqrK() * iqr;
            double hi = q3 + cfg.iqrK() * iqr;
            int before = pts.size();
            pts.removeIf(a -> a[0] < lo || a[0] > hi);
            removed = before - pts.size();
        }
        int n = pts.size();

        if (n == 0 && orig == null) {
            if (categoryMedian != null && categoryMedian > 0) {
                double m = categoryMedian;
                return finish(m, m * (1 - cfg.anchorBand()), m * (1 + cfg.anchorBand()), 0, null,
                        PriceSuggestion.Basis.CATEGORY_MEDIAN,
                        String.format(Locale.ROOT, "同分类暂无可参考的成交/在售样本，且未填写原价，暂按该分类全部商品价格中位数 ¥%.2f 给出参考，建议补充原价以获得更准确的建议。", m));
            }
            return new PriceSuggestion(0, 0, 0, 0, PriceSuggestion.Basis.NONE, "同分类暂无任何价格数据，且未填写原价，无法给出建议。");
        }

        double sm = Double.NaN, slo = Double.NaN, shi = Double.NaN;
        if (n > 0) {
            double[] v = new double[n];
            double[] w = new double[n];
            for (int i = 0; i < n; i++) {
                v[i] = pts.get(i)[0];
                w[i] = pts.get(i)[1];
            }
            sm = WeightedStats.weightedQuantile(v, w, 0.5);
            slo = WeightedStats.weightedQuantile(v, w, 0.25);
            shi = WeightedStats.weightedQuantile(v, w, 0.75);
        }
        StringBuilder why = new StringBuilder();
        if (n > 0) {
            why.append(String.format(Locale.ROOT, "参考同分类 %d 件%s商品", n, "成交/在售"));
            if (cfg.conditionAlign()) {
                why.append("（已按成色折算到「").append(condName).append("」");
            } else {
                why.append("（");
            }
            if (removed > 0) {
                why.append(String.format(Locale.ROOT, "，剔除 %d 个异常价", removed));
            }
            if (!titleCos.isEmpty()) {
                why.append("，标题相似的商品权重更高");
            }
            why.append(String.format(Locale.ROOT, "），加权中位数 ¥%.2f", sm));
        }

        if (orig != null && (cfg.blendOriginal() || n == 0)) {
            double anchor = orig * fq;
            // 原价折算的残差分布（用同类带原价样本估计）
            List<Double> resid = new ArrayList<>();
            for (PriceSample s : samples) {
                if (s.originalPrice() != null && s.originalPrice() > 0 && s.price() > 0) {
                    double r = Math.log(s.price() / (s.originalPrice() * f.of(s.condition())));
                    if (Math.abs(r) < Math.log(5)) { // 与折算价相差 5 倍以上视为录入错误
                        resid.add(r);
                    }
                }
            }
            double alo, ahi, sigmaA;
            if (resid.size() >= 4) {
                double r1 = WeightedStats.quantile(resid, 0.25);
                double r3 = WeightedStats.quantile(resid, 0.75);
                alo = anchor * Math.exp(r1);
                ahi = anchor * Math.exp(r3);
                sigmaA = Math.max(0.05, (r3 - r1) / 1.349);
            } else {
                alo = anchor * (1 - cfg.anchorBand());
                ahi = anchor * (1 + cfg.anchorBand());
                sigmaA = DEFAULT_SIGMA_ANCHOR;
            }
            ahi = Math.min(orig, ahi);
            double lambdaN = n / (n + cfg.blendKappa());
            double lambda = lambdaN;
            if (cfg.dispersionBlend() && n > 0) {
                double sigmaS = DEFAULT_SIGMA_SAMPLES;
                if (n >= 4) {
                    List<Double> logs = new ArrayList<>(n);
                    for (double[] a : pts) {
                        logs.add(Math.log(a[0]));
                    }
                    sigmaS = Math.max(0.05, (WeightedStats.quantile(logs, 0.75) - WeightedStats.quantile(logs, 0.25)) / 1.349);
                }
                lambda = lambdaN * sigmaA * sigmaA / (sigmaA * sigmaA + sigmaS * sigmaS);
            }
            double p = n > 0 ? lambda * sm + (1 - lambda) * anchor : anchor;
            double lo = n > 0 ? lambda * slo + (1 - lambda) * alo : alo;
            double hi = n > 0 ? lambda * shi + (1 - lambda) * ahi : ahi;
            boolean few = n < cfg.minSamples();
            if (why.length() > 0) {
                why.append("；");
            } else {
                why.append("同分类暂无可参考样本；");
            }
            why.append(String.format(Locale.ROOT, "原价 ¥%.2f × 成色系数 %.2f%s = ¥%.2f", orig, fq,
                    cfg.estimateFactors() && !resid.isEmpty() ? "（已按同类样本校准）" : "", anchor));
            if (n > 0) {
                why.append(String.format(Locale.ROOT, "，两者按可信度 %.0f%% : %.0f%% 融合", lambda * 100, (1 - lambda) * 100));
            }
            if (few) {
                why.append(String.format(Locale.ROOT, "（样本少于 %d 件，主要依据原价折算）", cfg.minSamples()));
            }
            why.append("。");
            return finish(p, lo, hi, n, orig, lambda < 0.5 ? PriceSuggestion.Basis.ORIGINAL_PRICE : PriceSuggestion.Basis.SAMPLES,
                    why.toString());
        }
        if (n < cfg.minSamples()) {
            why.append(String.format(Locale.ROOT, "；样本少于 %d 件，结果仅供参考，建议填写原价。", cfg.minSamples()));
        } else {
            why.append("。");
        }
        return finish(sm, slo, shi, n, orig, PriceSuggestion.Basis.SAMPLES, why.toString());
    }

    /** 约束：min ≤ P ≤ max，且都不超过原价、不小于 0。 */
    private static PriceSuggestion finish(double p, double lo, double hi, int n, Double orig,
                                          PriceSuggestion.Basis basis, String reason) {
        if (orig != null) {
            p = Math.min(p, orig);
            hi = Math.min(hi, orig);
        }
        lo = Math.max(0, Math.min(lo, p));
        hi = Math.max(hi, p);
        return new PriceSuggestion(Math.max(0, p), lo, hi, n, basis, reason);
    }

    private static Map<Long, Double> titleCosine(String title, List<PriceSample> samples) {
        if (title == null || title.isBlank() || samples.isEmpty()) {
            return Map.of();
        }
        List<ContentSimilarity.Doc> docs = new ArrayList<>(samples.size());
        for (PriceSample s : samples) {
            docs.add(new ContentSimilarity.Doc(s.goodsId(), 0, s.title() == null ? "" : s.title()));
        }
        ContentSimilarity cs = ContentSimilarity.build(docs, 0.0);
        Map<Long, Double> out = new HashMap<>();
        for (ScoredItem si : cs.similarTo(new ContentSimilarity.Doc(Long.MIN_VALUE, 0, title), docs.size(), null, null)) {
            out.put(si.itemId(), si.score());
        }
        return out;
    }

    public static String conditionName(String c) {
        if (c == null) {
            return "未知成色";
        }
        return switch (c) {
            case "NEW" -> "全新";
            case "LIKE_NEW" -> "几乎全新";
            case "GOOD" -> "轻微使用痕迹";
            case "FAIR" -> "明显使用痕迹";
            case "POOR" -> "较多磨损";
            default -> c;
        };
    }
}
