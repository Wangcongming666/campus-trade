package com.campus.trade.price.experiment;

import com.campus.trade.price.core.ConditionFactors;
import com.campus.trade.price.core.PriceQuery;
import com.campus.trade.price.core.PriceSample;
import com.campus.trade.price.core.PriceSuggestConfig;
import com.campus.trade.price.core.PriceSuggester;
import com.campus.trade.price.core.PriceSuggestion;
import com.campus.trade.price.core.WeightedStats;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * 价格建议离线评估（数据为 scripts/gen_campus_data.py 生成的<b>模拟数据</b>）。
 * <p>
 * 协议：已售商品（deal_price 非空，模拟 trade_order.price）随机留出 20% 作测试集；
 * 其余已售商品（成交价，sold=true）+ 全部在售商品（挂牌价，sold=false）作为可见样本。
 * 对每个测试商品，用其分类、成色、原价（可能为空）、标题请求建议价，与真实成交价比较。
 * 重复 5 次不同随机划分（seed, seed+1, …）取均值与标准差。
 * <p>
 * 用法：java PriceExperimentMain [dataDir=data/campus] [outDir=results] [seed=42] [repeats=5]
 */
public final class PriceExperimentMain {

    static final double TEST_RATIO = 0.2;
    static final double FIXED_DISCOUNT = 0.6;   // 基线2：原价 × 6 折（二手常见经验折扣）
    static final double FIXED_BAND = 0.15;      // 基线2 的区间：±15%（与本方法只有原价时的区间半宽一致）
    static final int[] SAMPLE_SIZES = {0, 1, 2, 3, 5, 10, 20, 50};

    record Goods(long id, long cat, String title, double listPrice, boolean sold, Double orig, String cond, Double deal) {
    }

    record Pred(double p, double lo, double hi) {
    }

    /** 方法：给定测试商品与该分类的可见样本，输出预测。 */
    interface Method {
        Pred predict(Goods g, List<PriceSample> catSamples);
    }

    public static void main(String[] args) throws IOException {
        Path dataDir = Path.of(args.length > 0 ? args[0] : "data/campus");
        Path out = Path.of(args.length > 1 ? args[1] : "results");
        long seed = args.length > 2 ? Long.parseLong(args[2]) : 42L;
        int repeats = args.length > 3 ? Integer.parseInt(args[3]) : 5;
        Files.createDirectories(out);
        List<Goods> all = load(dataDir.resolve("goods.csv"));
        new PriceExperimentMain().run(all, out, seed, repeats);
    }

    static List<Goods> load(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        String[] head = lines.get(0).split(",", -1);
        Map<String, Integer> col = new HashMap<>();
        for (int i = 0; i < head.length; i++) {
            col.put(head[i].trim(), i);
        }
        List<Goods> list = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            String[] f = line.split(",", -1);
            String orig = f[col.get("original_price")];
            String deal = f[col.get("deal_price")];
            list.add(new Goods(Long.parseLong(f[col.get("id")]), Long.parseLong(f[col.get("category_id")]),
                    f[col.get("title")], Double.parseDouble(f[col.get("price")]), "2".equals(f[col.get("status")]),
                    orig.isBlank() ? null : Double.valueOf(orig), f[col.get("condition")],
                    deal.isBlank() ? null : Double.valueOf(deal)));
        }
        return list;
    }

    // ------------------------------------------------------------------ 方法定义

    static Pred categoryMean(Goods g, List<PriceSample> s) {
        List<Double> sold = new ArrayList<>();
        for (PriceSample x : s) {
            if (x.sold()) {
                sold.add(x.price());
            }
        }
        if (sold.isEmpty()) {
            for (PriceSample x : s) {
                sold.add(x.price());
            }
        }
        if (sold.isEmpty()) {
            return new Pred(0, 0, 0);
        }
        double mean = sold.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return new Pred(mean, WeightedStats.quantile(sold, 0.25), WeightedStats.quantile(sold, 0.75));
    }

    static Pred fixedDiscount(Goods g, List<PriceSample> s) {
        if (g.orig() == null) {
            return categoryMean(g, s); // 未填原价时无法折算，退回类目均值
        }
        double p = g.orig() * FIXED_DISCOUNT;
        return new Pred(p, p * (1 - FIXED_BAND), Math.min(g.orig(), p * (1 + FIXED_BAND)));
    }

    static Method ours(PriceSuggestConfig cfg) {
        return (g, s) -> {
            List<Double> all = new ArrayList<>();
            for (PriceSample x : s) {
                all.add(x.price());
            }
            Double catMedian = all.isEmpty() ? null : WeightedStats.median(all);
            PriceSuggestion r = new PriceSuggester(cfg).suggest(new PriceQuery(g.cond(), g.orig(), g.title()), s, catMedian);
            return new Pred(r.suggested(), r.min(), r.max());
        };
    }

    static LinkedHashMap<String, Method> methods() {
        PriceSuggestConfig d = PriceSuggestConfig.defaults();
        LinkedHashMap<String, Method> m = new LinkedHashMap<>();
        m.put("基线1-类目均值", PriceExperimentMain::categoryMean);
        m.put("基线2-原价×0.6", PriceExperimentMain::fixedDiscount);
        m.put("本方法", ours(d));
        m.put("消融-不校准成色系数", ours(d.withEstimateFactors(false)));
        m.put("消融-仅按样本数融合", ours(d.withDispersionBlend(false)));
        m.put("消融-不融合原价", ours(d.withBlendOriginal(false)));
        m.put("消融-无成色对齐", ours(d.withConditionAlign(false)));
        m.put("消融-无标题加权", ours(d.withTitleWeighting(false)));
        m.put("消融-无IQR去异常", ours(d.withIqrFilter(false)));
        m.put("消融-在售与成交等权", ours(d.withWeights(1.0, 1.0)));
        return m;
    }

    // ------------------------------------------------------------------ 评估

    static final class Acc {
        double ae, ape, width;
        int hit, n;
        List<Double> apes = new ArrayList<>();

        void add(Pred p, double y) {
            double e = Math.abs(p.p() - y);
            ae += e;
            ape += e / y;
            apes.add(e / y);
            width += (p.hi() - p.lo()) / y;
            if (y >= p.lo() - 1e-9 && y <= p.hi() + 1e-9) {
                hit++;
            }
            n++;
        }

        double[] metrics() {
            return new double[]{ae / n, ape / n, WeightedStats.median(apes), (double) hit / n, width / n};
        }
    }

    static final String[] METRICS = {"MAE", "MAPE", "MdAPE", "HitRate", "RelWidth"};

    private void run(List<Goods> all, Path out, long seed, int repeats) throws IOException {
        List<Goods> sold = all.stream().filter(g -> g.sold() && g.deal() != null).toList();
        LinkedHashMap<String, Method> methods = methods();
        // method -> group -> list of metric arrays (per split)
        Map<String, Map<String, List<double[]>>> res = new LinkedHashMap<>();
        List<String> splitRows = new ArrayList<>();
        Map<String, Map<Integer, List<Double>>> sizeRes = new LinkedHashMap<>();
        int testSize = 0;
        long withOrigTest = 0;
        long withOrigMin = Long.MAX_VALUE, withOrigMax = 0;
        for (int r = 0; r < repeats; r++) {
            long s = seed + r;
            List<Goods> shuffled = new ArrayList<>(sold);
            Collections.shuffle(shuffled, new Random(s));
            int nTest = (int) Math.round(sold.size() * TEST_RATIO);
            List<Goods> test = shuffled.subList(0, nTest);
            java.util.Set<Long> testIds = new java.util.HashSet<>();
            test.forEach(g -> testIds.add(g.id()));
            Map<Long, List<PriceSample>> byCat = new HashMap<>();
            for (Goods g : all) {
                if (testIds.contains(g.id())) {
                    continue;
                }
                if (g.sold() && g.deal() != null) {
                    byCat.computeIfAbsent(g.cat(), k -> new ArrayList<>())
                            .add(new PriceSample(g.id(), g.deal(), g.cond(), g.orig(), g.title(), true));
                } else if (!g.sold()) {
                    byCat.computeIfAbsent(g.cat(), k -> new ArrayList<>())
                            .add(new PriceSample(g.id(), g.listPrice(), g.cond(), g.orig(), g.title(), false));
                }
            }
            testSize = nTest;
            withOrigTest = test.stream().filter(g -> g.orig() != null).count();
            withOrigMin = Math.min(withOrigMin, withOrigTest);
            withOrigMax = Math.max(withOrigMax, withOrigTest);
            for (Map.Entry<String, Method> me : methods.entrySet()) {
                Map<String, Acc> acc = new LinkedHashMap<>();
                acc.put("全部", new Acc());
                acc.put("有原价", new Acc());
                acc.put("无原价", new Acc());
                for (Goods g : test) {
                    Pred p = me.getValue().predict(g, byCat.getOrDefault(g.cat(), List.of()));
                    acc.get("全部").add(p, g.deal());
                    acc.get(g.orig() != null ? "有原价" : "无原价").add(p, g.deal());
                }
                for (Map.Entry<String, Acc> ae : acc.entrySet()) {
                    double[] m = ae.getValue().metrics();
                    res.computeIfAbsent(me.getKey(), k -> new LinkedHashMap<>())
                            .computeIfAbsent(ae.getKey(), k -> new ArrayList<>()).add(m);
                    splitRows.add(String.format(Locale.ROOT, "%d,%s,%s,%d,%.4f,%.4f,%.4f,%.4f,%.4f", s, me.getKey(),
                            ae.getKey(), ae.getValue().n, m[0], m[1], m[2], m[3], m[4]));
                }
            }
            // 样本量敏感性：每个测试商品只给同分类随机 n 个样本
            Random sr = new Random(s * 31 + 7);
            for (int n : SAMPLE_SIZES) {
                Map<String, Acc> acc = new LinkedHashMap<>();
                for (String name : List.of("基线1-类目均值", "基线2-原价×0.6", "本方法", "消融-不融合原价")) {
                    acc.put(name, new Acc());
                }
                for (Goods g : test) {
                    List<PriceSample> pool = new ArrayList<>(byCat.getOrDefault(g.cat(), List.of()));
                    Collections.shuffle(pool, sr);
                    List<PriceSample> sub = pool.subList(0, Math.min(n, pool.size()));
                    for (String name : acc.keySet()) {
                        acc.get(name).add(methods.get(name).predict(g, sub), g.deal());
                    }
                }
                for (Map.Entry<String, Acc> ae : acc.entrySet()) {
                    sizeRes.computeIfAbsent(ae.getKey(), k -> new LinkedHashMap<>())
                            .computeIfAbsent(n, k -> new ArrayList<>()).add(ae.getValue().metrics()[2]); // MdAPE
                }
            }
            System.out.printf("[split seed=%d] test=%d (有原价 %d)%n", s, nTest, withOrigTest);
        }

        // ---------- 输出 CSV
        try (PrintWriter w = writer(out.resolve("price_splits.csv"))) {
            w.println("split_seed,method,group,n," + String.join(",", METRICS));
            splitRows.forEach(w::println);
        }
        StringBuilder md = new StringBuilder();
        md.append("# 价格建议离线评估（模拟数据，程序自动生成）\n\n")
                .append(String.format(Locale.ROOT, "> 数据：scripts/gen_campus_data.py 生成的**模拟数据**（seed=20261118），已售 %d 件，"
                        + "每次随机留出 20%%（%d 件，其中有原价 %d–%d 件）作测试，重复 %d 次（split seed=%d..%d），表中为均值±标准差。"
                        + "MAPE/MdAPE 为平均/中位绝对百分比误差，区间命中率为真实成交价落在 [minPrice, maxPrice] 的比例。\n\n",
                        sold.size(), testSize, withOrigMin, withOrigMax, repeats, seed, seed + repeats - 1))
                .append("| 方法 | 分组 | MAE(元) | MAPE | MdAPE | 区间命中率 | 平均相对区间宽度 |\n|---|---|---|---|---|---|---|\n");
        try (PrintWriter w = writer(out.resolve("price_main.csv"))) {
            w.println("method,group,MAE,MAE_std,MAPE,MAPE_std,MdAPE,MdAPE_std,HitRate,HitRate_std,RelWidth,RelWidth_std");
            for (Map.Entry<String, Map<String, List<double[]>>> me : res.entrySet()) {
                for (Map.Entry<String, List<double[]>> ge : me.getValue().entrySet()) {
                    double[][] ms = new double[METRICS.length][2];
                    StringBuilder line = new StringBuilder(me.getKey() + "," + ge.getKey());
                    for (int k = 0; k < METRICS.length; k++) {
                        final int kk = k;
                        double[] v = ge.getValue().stream().mapToDouble(a -> a[kk]).toArray();
                        ms[k] = meanStd(v);
                        line.append(String.format(Locale.ROOT, ",%.4f,%.4f", ms[k][0], ms[k][1]));
                    }
                    w.println(line);
                    md.append(String.format(Locale.ROOT, "| %s | %s | %.2f±%.2f | %.4f±%.4f | %.4f±%.4f | %.4f±%.4f | %.4f |\n",
                            me.getKey(), ge.getKey(), ms[0][0], ms[0][1], ms[1][0], ms[1][1], ms[2][0], ms[2][1],
                            ms[3][0], ms[3][1], ms[4][0]));
                }
            }
        }
        md.append("\n## 样本量敏感性（MdAPE，均值，全部测试商品）\n\n| 方法 |");
        for (int n : SAMPLE_SIZES) {
            md.append(" n=").append(n).append(" |");
        }
        md.append("\n|---|").append("---|".repeat(SAMPLE_SIZES.length)).append('\n');
        try (PrintWriter w = writer(out.resolve("price_sample_size.csv"))) {
            w.println("method,n_samples,MdAPE,MdAPE_std");
            for (Map.Entry<String, Map<Integer, List<Double>>> me : sizeRes.entrySet()) {
                md.append("| ").append(me.getKey()).append(" |");
                for (Map.Entry<Integer, List<Double>> ne : me.getValue().entrySet()) {
                    double[] ms = meanStd(ne.getValue().stream().mapToDouble(Double::doubleValue).toArray());
                    w.printf(Locale.ROOT, "%s,%d,%.4f,%.4f%n", me.getKey(), ne.getKey(), ms[0], ms[1]);
                    md.append(String.format(Locale.ROOT, " %.4f |", ms[0]));
                }
                md.append('\n');
            }
        }
        // 估计出的成色系数（全部可见样本，供论文说明）
        List<PriceSample> allSamples = new ArrayList<>();
        for (Goods g : all) {
            if (g.sold() && g.deal() != null) {
                allSamples.add(new PriceSample(g.id(), g.deal(), g.cond(), g.orig(), g.title(), true));
            } else if (!g.sold()) {
                allSamples.add(new PriceSample(g.id(), g.listPrice(), g.cond(), g.orig(), g.title(), false));
            }
        }
        ConditionFactors est = ConditionFactors.defaults().estimate(allSamples, 10);
        md.append("\n## 成色系数：默认值 vs 由全部样本估计（先验强度 m=10）\n\n| 成色 | 默认 | 估计 |\n|---|---|---|\n");
        try (PrintWriter w = writer(out.resolve("price_condition_factors.csv"))) {
            w.println("condition,default,estimated");
            for (String c : ConditionFactors.CONDITIONS) {
                w.printf(Locale.ROOT, "%s,%.4f,%.4f%n", c, ConditionFactors.defaults().of(c), est.of(c));
                md.append(String.format(Locale.ROOT, "| %s | %.2f | %.4f |\n", c, ConditionFactors.defaults().of(c), est.of(c)));
            }
        }
        Files.writeString(out.resolve("price_results.md"), md.toString(), StandardCharsets.UTF_8);
        System.out.print(md);
    }

    static double[] meanStd(double[] v) {
        double m = 0;
        for (double x : v) {
            m += x;
        }
        m /= v.length;
        double s = 0;
        for (double x : v) {
            s += (x - m) * (x - m);
        }
        return new double[]{m, v.length > 1 ? Math.sqrt(s / (v.length - 1)) : 0};
    }

    static PrintWriter writer(Path p) throws IOException {
        return new PrintWriter(Files.newBufferedWriter(p, StandardCharsets.UTF_8));
    }

}
