package com.campus.trade.experiments.recommend;

import com.campus.trade.recommend.core.Behavior;
import com.campus.trade.recommend.core.BehaviorWeightConfig;
import com.campus.trade.recommend.core.ColdStartRecommender;
import com.campus.trade.recommend.core.HotRanker;
import com.campus.trade.recommend.core.HybridRecommender;
import com.campus.trade.recommend.core.InteractionMatrix;
import com.campus.trade.recommend.core.ItemCF;
import com.campus.trade.recommend.core.ItemSimilarityModel;
import com.campus.trade.recommend.core.RecResult;
import com.campus.trade.recommend.core.ScoredItem;
import com.campus.trade.recommend.core.UserCF;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/**
 * 论文推荐实验入口（多划分种子）。
 * 用法：java -cp build com.campus.trade.experiments.recommend.RecommendExperiment dataDir outDir seed1,seed2,...
 * 输出（outDir/raw/）：
 *   run_metrics.csv     每个 数据集×种子×子集×方法×N 的 Precision/Recall/NDCG/覆盖率/新颖度
 *   per_user_n10.csv.gz 每个用户的 hits@10 与 NDCG@10（供 Python 做配对显著性检验）
 *   dataset_stats.csv   每个 数据集×种子 的划分规模与训练耗时
 * 每个 N 单独调用一次推荐（混合策略的冷启动 CF 名额 ⌈ηN⌉ 按当前 N 计算，与线上按 size 计算一致）。
 */
public final class RecommendExperiment {

    static final int[] NS = {5, 10, 20};
    static final int K = 40;
    static final int COLD_THRESHOLD = 5;
    static final double COLD_INTEREST_RATIO = 0.8;
    static final double[] ETAS = {0, 0.05, 0.1, 0.2, 0.3};
    /** MovieLens 冷启动协议：抽取 20% 评估用户，只保留 1~4 个训练物品 */
    static final double ML_COLD_FRACTION = 0.2;
    static final int ML_COLD_MAX_KEEP = 4;

    interface Rec {
        List<Long> recommend(long userId, int n);
    }

    private final PrintWriter metrics;
    private final PrintWriter perUser;
    private final PrintWriter stats;

    RecommendExperiment(Path rawDir) throws IOException {
        Files.createDirectories(rawDir);
        metrics = new PrintWriter(Files.newBufferedWriter(rawDir.resolve("run_metrics.csv"), StandardCharsets.UTF_8));
        metrics.println("dataset,seed,subset,method,N,users,precision,recall,ndcg,coverage,novelty");
        perUser = new PrintWriter(new OutputStreamWriter(new GZIPOutputStream(
                Files.newOutputStream(rawDir.resolve("per_user_n10.csv.gz"))), StandardCharsets.UTF_8));
        perUser.println("dataset,seed,method,user,cold,hits,test_size,ndcg");
        stats = new PrintWriter(Files.newBufferedWriter(rawDir.resolve("dataset_stats.csv"), StandardCharsets.UTF_8));
        stats.println("dataset,seed,users,items,records,train_pairs,test_pairs,eval_users,cold_users,train_items,"
                + "fit_itemcf_pure_ms,fit_cf_weighted_ms,fit_usercf_ms,eval_ms");
    }

    public static void main(String[] args) throws IOException {
        Path dataDir = Path.of(args.length > 0 ? args[0] : "data");
        Path outDir = Path.of(args.length > 1 ? args[1] : "results");
        String seedArg = args.length > 2 ? args[2] : "42,43,44,45,46";
        List<Long> seeds = new ArrayList<>();
        for (String s : seedArg.split(",")) {
            seeds.add(Long.parseLong(s.trim()));
        }
        RecommendExperiment exp = new RecommendExperiment(outDir.resolve("raw"));
        Dataset ml = Dataset.movieLens(dataDir.resolve("ml-100k"));
        Dataset campus = Dataset.campus(dataDir.resolve("campus"));
        for (long seed : seeds) {
            Split mlSplit = Split.of(ml.behaviors, seed);
            exp.run("ml100k", ml, mlSplit, seed);
            exp.run("ml100k_coldsim", ml, mlSplit.truncateUsers(ML_COLD_FRACTION, ML_COLD_MAX_KEEP, seed), seed);
            exp.run("campus", campus, Split.of(campus.behaviors, seed), seed);
        }
        exp.close();
    }

    void close() {
        metrics.close();
        perUser.close();
        stats.close();
    }

    void run(String dsName, Dataset ds, Split split, long seed) {
        long t0 = System.currentTimeMillis();
        long now = ds.behaviors.stream().mapToLong(Behavior::timeMillis).max().orElse(0);
        // 兴趣表示：加权 + 对数压缩（MovieLens 为二值）；纯 CF 用二值；消融用 线性加权
        Map<Long, Map<Long, Double>> wInterest = ds.weightConfig.aggregate(split.train, now);
        Map<Long, Map<Long, Double>> binInterest = BehaviorWeightConfig.binary().aggregate(split.train, now);
        InteractionMatrix wm = InteractionMatrix.of(wInterest);
        InteractionMatrix bm = InteractionMatrix.of(binInterest);

        Map<Long, Integer> pop = new HashMap<>();
        for (int i = 0; i < bm.itemCount(); i++) {
            pop.put(bm.itemId(i), bm.itemUsers(i).length);
        }
        long[] trainItems = new long[bm.itemCount()];
        for (int i = 0; i < trainItems.length; i++) {
            trainItems[i] = bm.itemId(i);
        }

        long ts = System.currentTimeMillis();
        ItemSimilarityModel pure = ItemCF.fit(bm, K, false, false);
        long tPure = System.currentTimeMillis() - ts;
        ts = System.currentTimeMillis();
        ItemSimilarityModel weighted = ItemCF.fit(wm, K, true, true);
        long tWeighted = System.currentTimeMillis() - ts;
        ts = System.currentTimeMillis();
        UserCF userCf = UserCF.fit(wm, K);
        long tUser = System.currentTimeMillis() - ts;

        HotRanker hot = HotRanker.build(split.train, ds.weightConfig, now, ds.itemCategory);
        ColdStartRecommender cold = new ColdStartRecommender(hot, COLD_INTEREST_RATIO);

        Map<String, Rec> methods = new LinkedHashMap<>();
        methods.put("random", (u, n) -> randomRec(u, n, binInterest, trainItems, seed));
        methods.put("popular", (u, n) -> ids(hot.top(n, binInterest.getOrDefault(u, Map.of()).keySet(), null)));
        methods.put("usercf", (u, n) -> ids(userCf.recommend(u, n, null)));
        methods.put("itemcf_pure", itemCf(pure, binInterest));
        methods.put("cf_weighted", itemCf(weighted, wInterest));
        // 消融：纯 CF → +行为加权(对数) → +IUF → +归一化(= cf_weighted)；MovieLens 只有一种行为，跳过加权步骤
        if (ds.multiBehavior) {
            methods.put("abl_w", itemCf(ItemCF.fit(wm, K, false, false), wInterest));
            methods.put("abl_w_iuf", itemCf(ItemCF.fit(wm, K, true, false), wInterest));
            Map<Long, Map<Long, Double>> linInterest = ds.weightConfig.withLogScale(false).aggregate(split.train, now);
            methods.put("abl_linear_iuf_norm", itemCf(ItemCF.fit(InteractionMatrix.of(linInterest), K, true, true), linInterest));
        } else {
            methods.put("abl_iuf", itemCf(ItemCF.fit(bm, K, true, false), binInterest));
        }
        methods.put("abl_norm_only", itemCf(ItemCF.fit(bm, K, false, true), binInterest));
        for (double eta : ETAS) {
            HybridRecommender h = new HybridRecommender(weighted, hot, cold, COLD_THRESHOLD, ds.itemCategory::get, eta);
            methods.put("hybrid_eta" + eta, (u, n) -> recIds(h.recommend(wInterest.getOrDefault(u, Map.of()),
                    ds.userInterests.getOrDefault(u, List.of()), n, null, null, null, null)));
        }
        if (!ds.userInterests.isEmpty()) {
            HybridRecommender h = new HybridRecommender(weighted, hot, cold, COLD_THRESHOLD, ds.itemCategory::get, 0.1);
            methods.put("hybrid_eta0.1_nodeclared", (u, n) -> recIds(h.recommend(wInterest.getOrDefault(u, Map.of()),
                    List.of(), n, null, null, null, null)));
        }

        List<Long> evalUsers = new ArrayList<>(split.test.keySet());
        Set<Long> coldUsers = new HashSet<>();
        for (Long u : evalUsers) {
            if (wInterest.getOrDefault(u, Map.of()).size() < COLD_THRESHOLD) {
                coldUsers.add(u);
            }
        }
        List<Long> coldList = evalUsers.stream().filter(coldUsers::contains).toList();

        for (Map.Entry<String, Rec> e : methods.entrySet()) {
            Map<Integer, Map<Long, List<Long>>> recs = new HashMap<>();
            for (int n : NS) {
                Map<Long, List<Long>> byUser = new HashMap<>();
                for (Long u : evalUsers) {
                    List<Long> r = e.getValue().recommend(u, n);
                    byUser.put(u, r.size() > n ? r.subList(0, n) : r);
                }
                recs.put(n, byUser);
            }
            for (int n : NS) {
                writeMetrics(dsName, seed, "all", e.getKey(), n, evalUsers, recs.get(n), split, pop, trainItems.length);
                if (!coldList.isEmpty()) {
                    writeMetrics(dsName, seed, "cold", e.getKey(), n, coldList, recs.get(n), split, pop, trainItems.length);
                }
            }
            Map<Long, List<Long>> r10 = recs.get(10);
            for (Long u : evalUsers) {
                Set<Long> t = split.test.get(u);
                List<Long> r = r10.get(u);
                perUser.printf(Locale.ROOT, "%s,%d,%s,%d,%d,%d,%d,%.6f%n", dsName, seed, e.getKey(), u,
                        coldUsers.contains(u) ? 1 : 0, hits(r, t), t.size(), ndcg(r, t, 10));
            }
        }
        long allUsers = ds.behaviors.stream().mapToLong(Behavior::userId).distinct().count();
        long allItems = ds.behaviors.stream().mapToLong(Behavior::itemId).distinct().count();
        long evalMs = System.currentTimeMillis() - t0;
        stats.println(String.join(",", dsName, String.valueOf(seed), String.valueOf(allUsers), String.valueOf(allItems),
                String.valueOf(ds.behaviors.size()), String.valueOf(split.trainPairs()), String.valueOf(split.testPairs()),
                String.valueOf(evalUsers.size()), String.valueOf(coldList.size()), String.valueOf(trainItems.length),
                String.valueOf(tPure), String.valueOf(tWeighted), String.valueOf(tUser), String.valueOf(evalMs)));
        System.out.printf(Locale.ROOT, "[%s seed=%d] users=%d cold=%d methods=%d %d ms%n", dsName, seed,
                evalUsers.size(), coldList.size(), methods.size(), evalMs);
    }

    void writeMetrics(String ds, long seed, String subset, String method, int n, List<Long> users,
                      Map<Long, List<Long>> recs, Split split, Map<Long, Integer> pop, int trainItemCount) {
        long hitSum = 0;
        long testSum = 0;
        double ndcgSum = 0;
        double popSum = 0;
        long recCnt = 0;
        Set<Long> covered = new HashSet<>();
        for (Long u : users) {
            Set<Long> t = split.test.get(u);
            List<Long> r = recs.get(u);
            hitSum += hits(r, t);
            testSum += t.size();
            ndcgSum += ndcg(r, t, n);
            for (Long i : r) {
                covered.add(i);
                popSum += Math.log1p(pop.getOrDefault(i, 0));
                recCnt++;
            }
        }
        double p = hitSum / (double) ((long) n * users.size());
        double rc = testSum == 0 ? 0 : hitSum / (double) testSum;
        metrics.printf(Locale.ROOT, "%s,%d,%s,%s,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f%n", ds, seed, subset, method, n,
                users.size(), p, rc, ndcgSum / users.size(), covered.size() / (double) trainItemCount,
                recCnt == 0 ? 0 : popSum / recCnt);
    }

    static int hits(List<Long> r, Set<Long> t) {
        int h = 0;
        for (Long i : r) {
            if (t.contains(i)) {
                h++;
            }
        }
        return h;
    }

    /** NDCG@N：二值相关，DCG = Σ rel_p / log2(p+2)（p 从 0 起），IDCG 按 min(|T|, N) 个相关物品全部排在最前计算。 */
    static double ndcg(List<Long> r, Set<Long> t, int n) {
        double dcg = 0;
        for (int p = 0; p < Math.min(n, r.size()); p++) {
            if (t.contains(r.get(p))) {
                dcg += 1 / log2(p + 2);
            }
        }
        double idcg = 0;
        for (int p = 0; p < Math.min(t.size(), n); p++) {
            idcg += 1 / log2(p + 2);
        }
        return idcg == 0 ? 0 : dcg / idcg;
    }

    static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }

    static Rec itemCf(ItemSimilarityModel model, Map<Long, Map<Long, Double>> interest) {
        return (u, n) -> recIds(ItemCF.recommend(model, interest.getOrDefault(u, Map.of()), n, null, null));
    }

    static List<Long> randomRec(long u, int n, Map<Long, Map<Long, Double>> interest, long[] items, long seed) {
        Random r = new Random(seed * 31 + u);
        Set<Long> seen = interest.getOrDefault(u, Map.of()).keySet();
        Set<Long> picked = new HashSet<>();
        List<Long> out = new ArrayList<>(n);
        while (out.size() < n && picked.size() + seen.size() < items.length) {
            long id = items[r.nextInt(items.length)];
            if (!seen.contains(id) && picked.add(id)) {
                out.add(id);
            }
        }
        return out;
    }

    static List<Long> ids(List<ScoredItem> l) {
        return l.stream().map(ScoredItem::itemId).toList();
    }

    static List<Long> recIds(List<RecResult> l) {
        return l.stream().map(RecResult::itemId).toList();
    }
}
