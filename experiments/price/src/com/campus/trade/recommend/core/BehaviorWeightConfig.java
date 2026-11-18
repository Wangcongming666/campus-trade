package com.campus.trade.recommend.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 行为权重与时间衰减配置（不可变）。
 * <p>
 * 用户 u 对商品 i 的隐式兴趣：r(u,i) = ln(1 + min(cap, Σ w(type) · decay(Δt)))（logScale=false 时不取对数），
 * decay(Δt) = 0.5^(Δt / halfLife)，halfLife &lt;= 0 表示不做时间衰减。
 */
public final class BehaviorWeightConfig {

    public static final int BROWSE = 1;
    public static final int FAVORITE = 2;
    public static final int CHAT = 3;
    public static final int ORDER = 4;

    private static final double DAY_MS = 86_400_000d;

    private final Map<Integer, Double> weights;
    private final double halfLifeDays;
    private final double maxPerPair;
    private final boolean logScale;

    private BehaviorWeightConfig(Map<Integer, Double> weights, double halfLifeDays, double maxPerPair) {
        this(weights, halfLifeDays, maxPerPair, false);
    }

    private BehaviorWeightConfig(Map<Integer, Double> weights, double halfLifeDays, double maxPerPair, boolean logScale) {
        this.weights = Map.copyOf(weights);
        this.halfLifeDays = halfLifeDays;
        this.maxPerPair = maxPerPair;
        this.logScale = logScale;
    }

    /**
     * 默认：浏览1、收藏3、聊天4、下单5；不衰减；单个(用户,商品)兴趣上限 10（防止反复刷浏览）；
     * 聚合后做对数压缩 r=ln(1+r)（离线实验验证：比线性加权、二值反馈都更好，见 README）。
     */
    public static BehaviorWeightConfig defaults() {
        return new BehaviorWeightConfig(Map.of(BROWSE, 1.0, FAVORITE, 3.0, CHAT, 4.0, ORDER, 5.0), 0, 10, true);
    }

    /** 所有行为权重均为 1、无上限：即"二值隐式反馈"，用于 MovieLens 评分转隐式反馈。 */
    public static BehaviorWeightConfig binary() {
        return new BehaviorWeightConfig(Map.of(BROWSE, 1.0, FAVORITE, 1.0, CHAT, 1.0, ORDER, 1.0), 0, 1);
    }

    public BehaviorWeightConfig withWeight(int type, double weight) {
        Map<Integer, Double> m = new HashMap<>(weights);
        m.put(type, weight);
        return new BehaviorWeightConfig(m, halfLifeDays, maxPerPair, logScale);
    }

    public BehaviorWeightConfig withWeights(Map<Integer, Double> newWeights) {
        return new BehaviorWeightConfig(newWeights, halfLifeDays, maxPerPair, logScale);
    }

    public BehaviorWeightConfig withHalfLifeDays(double days) {
        return new BehaviorWeightConfig(weights, days, maxPerPair, logScale);
    }

    public BehaviorWeightConfig withMaxPerPair(double cap) {
        return new BehaviorWeightConfig(weights, halfLifeDays, cap, logScale);
    }

    /** 对聚合后的兴趣做对数压缩 r = ln(1 + r)，削弱单个用户深度行为对相似度的支配。 */
    public BehaviorWeightConfig withLogScale(boolean enable) {
        return new BehaviorWeightConfig(weights, halfLifeDays, maxPerPair, enable);
    }

    public boolean logScale() {
        return logScale;
    }

    public double weight(int type) {
        return weights.getOrDefault(type, 0.0);
    }

    public double halfLifeDays() {
        return halfLifeDays;
    }

    public double maxPerPair() {
        return maxPerPair;
    }

    /** 时间衰减系数，now 之后的行为按 Δt=0 处理。 */
    public double decay(long timeMillis, long nowMillis) {
        if (halfLifeDays <= 0) {
            return 1.0;
        }
        double days = Math.max(0, nowMillis - timeMillis) / DAY_MS;
        return Math.pow(0.5, days / halfLifeDays);
    }

    /** 单条行为的贡献 = 权重 × 衰减。 */
    public double score(Behavior b, long nowMillis) {
        return weight(b.type()) * decay(b.timeMillis(), nowMillis);
    }

    /** 聚合为 user → (item → 兴趣分)。使用 TreeMap 保证遍历顺序确定（实验可复现）。 */
    public Map<Long, Map<Long, Double>> aggregate(Collection<Behavior> behaviors, long nowMillis) {
        Map<Long, Map<Long, Double>> result = new TreeMap<>();
        for (Behavior b : behaviors) {
            double s = score(b, nowMillis);
            if (s <= 0) {
                continue;
            }
            result.computeIfAbsent(b.userId(), k -> new TreeMap<>()).merge(b.itemId(), s, Double::sum);
        }
        if (maxPerPair > 0 && Double.isFinite(maxPerPair)) {
            for (Map<Long, Double> m : result.values()) {
                m.replaceAll((k, v) -> Math.min(v, maxPerPair));
            }
        }
        if (logScale) {
            for (Map<Long, Double> m : result.values()) {
                m.replaceAll((k, v) -> Math.log1p(v));
            }
        }
        return result;
    }

    /** 聚合单个用户的行为为 item → 兴趣分（在线接口用）。 */
    public Map<Long, Double> aggregateUser(Collection<Behavior> behaviors, long nowMillis) {
        Map<Long, Double> m = new TreeMap<>();
        for (Behavior b : behaviors) {
            double s = score(b, nowMillis);
            if (s > 0) {
                m.merge(b.itemId(), s, Double::sum);
            }
        }
        if (maxPerPair > 0 && Double.isFinite(maxPerPair)) {
            m.replaceAll((k, v) -> Math.min(v, maxPerPair));
        }
        if (logScale) {
            m.replaceAll((k, v) -> Math.log1p(v));
        }
        return m;
    }
}
