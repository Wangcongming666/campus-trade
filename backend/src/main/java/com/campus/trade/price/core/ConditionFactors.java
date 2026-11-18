package com.campus.trade.price.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 成色系数 f(c)：同一商品在成色 c 下的价格 ≈ 原价 × f(c)。
 * <p>
 * 默认 NEW 1.0 / LIKE_NEW 0.85 / GOOD 0.7 / FAIR 0.55 / POOR 0.4（可配置）。
 * {@link #estimate} 用带原价的样本估计：f̂(c) = (n_c · median(price/orig) + m · f₀(c)) / (n_c + m)，
 * 即以先验系数 f₀ 做收缩（m 为先验强度），样本少时不会被少数异常值带偏。
 */
public final class ConditionFactors {

    public static final List<String> CONDITIONS = List.of("NEW", "LIKE_NEW", "GOOD", "FAIR", "POOR");

    private final Map<String, Double> factors;

    public ConditionFactors(Map<String, Double> factors) {
        this.factors = Map.copyOf(factors);
    }

    public static ConditionFactors defaults() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("NEW", 1.0);
        m.put("LIKE_NEW", 0.85);
        m.put("GOOD", 0.7);
        m.put("FAIR", 0.55);
        m.put("POOR", 0.4);
        return new ConditionFactors(m);
    }

    /** 未知成色按 GOOD 处理。 */
    public double of(String condition) {
        Double f = condition == null ? null : factors.get(condition);
        return f != null ? f : factors.getOrDefault("GOOD", 0.7);
    }

    public Map<String, Double> asMap() {
        return factors;
    }

    /**
     * 用样本中"价格/原价"估计成色系数，并向先验收缩。
     *
     * @param priorStrength m，先验强度（相当于 m 个虚拟样本），建议 10
     */
    public ConditionFactors estimate(Collection<PriceSample> samples, double priorStrength) {
        Map<String, List<Double>> ratios = new HashMap<>();
        for (PriceSample s : samples) {
            if (s.originalPrice() != null && s.originalPrice() > 0 && s.price() > 0 && s.condition() != null) {
                double r = s.price() / s.originalPrice();
                if (r > 0 && r <= 1.5) { // 明显录入错误的样本不参与估计
                    ratios.computeIfAbsent(s.condition(), k -> new ArrayList<>()).add(r);
                }
            }
        }
        Map<String, Double> out = new LinkedHashMap<>();
        for (String c : CONDITIONS) {
            double prior = of(c);
            List<Double> rs = ratios.getOrDefault(c, List.of());
            if (rs.isEmpty()) {
                out.put(c, prior);
            } else {
                double med = WeightedStats.median(rs);
                out.put(c, (rs.size() * med + priorStrength * prior) / (rs.size() + priorStrength));
            }
        }
        return new ConditionFactors(out);
    }
}
