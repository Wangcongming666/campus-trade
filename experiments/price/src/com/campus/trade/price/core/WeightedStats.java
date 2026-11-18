package com.campus.trade.price.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** 加权分位数与普通分位数工具。 */
public final class WeightedStats {

    private WeightedStats() {
    }

    /** 普通分位数（线性插值，q∈[0,1]）。 */
    public static double quantile(Collection<Double> values, double q) {
        List<Double> v = new ArrayList<>(values);
        if (v.isEmpty()) {
            return Double.NaN;
        }
        v.sort(Double::compare);
        double pos = q * (v.size() - 1);
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);
        return v.get(lo) + (v.get(hi) - v.get(lo)) * (pos - lo);
    }

    public static double median(Collection<Double> values) {
        return quantile(values, 0.5);
    }

    /**
     * 加权分位数：按值排序后，取累计权重首次达到 q·W 的值；恰好落在边界时与下一个值取平均
     * （等权时与普通中位数一致）。
     */
    public static double weightedQuantile(double[] values, double[] weights, double q) {
        int n = values.length;
        if (n == 0) {
            return Double.NaN;
        }
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) {
            idx[i] = i;
        }
        java.util.Arrays.sort(idx, (a, b) -> Double.compare(values[a], values[b]));
        double total = 0;
        for (double w : weights) {
            total += w;
        }
        double target = q * total;
        double cum = 0;
        for (int k = 0; k < n; k++) {
            cum += weights[idx[k]];
            if (Math.abs(cum - target) < 1e-12 * Math.max(1, total) && k + 1 < n) {
                return (values[idx[k]] + values[idx[k + 1]]) / 2;
            }
            if (cum > target) {
                return values[idx[k]];
            }
        }
        return values[idx[n - 1]];
    }
}
