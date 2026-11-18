package com.campus.trade.recommend.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongPredicate;

/**
 * 内容相似（新商品冷启动）：分类一致性 + 标题 TF-IDF 余弦。
 * <pre>
 *   tfidf(t,d) = tf(t,d) · (ln((N+1)/(df(t)+1)) + 1)，向量 L2 归一化
 *   sim(a,b)   = α·1[cat(a)=cat(b)] + (1-α)·cos(v_a, v_b)
 * </pre>
 * 新商品没有任何行为，ItemCF 无法计算相似度，此时用本类找"看起来相似"的商品。
 */
public final class ContentSimilarity {

    /** 参与计算的商品文档 */
    public record Doc(long itemId, long categoryId, String title) {
    }

    private final double categoryWeight;
    private final Map<String, Double> idf;
    private final int docCount;
    private final Map<Long, Doc> docs = new LinkedHashMap<>();
    private final Map<Long, Map<String, Double>> vectors = new HashMap<>();

    private ContentSimilarity(double categoryWeight, Map<String, Double> idf, int docCount) {
        this.categoryWeight = categoryWeight;
        this.idf = idf;
        this.docCount = docCount;
    }

    /**
     * @param categoryWeight α，分类一致的加分权重，建议 0.3
     */
    public static ContentSimilarity build(Collection<Doc> corpus, double categoryWeight) {
        Map<String, Integer> df = new HashMap<>();
        for (Doc d : corpus) {
            for (String t : Set.copyOf(TextTokenizer.tokenize(d.title()))) {
                df.merge(t, 1, Integer::sum);
            }
        }
        int n = corpus.size();
        Map<String, Double> idf = new HashMap<>(df.size() * 2);
        df.forEach((t, c) -> idf.put(t, Math.log((n + 1.0) / (c + 1.0)) + 1.0));
        ContentSimilarity cs = new ContentSimilarity(categoryWeight, idf, n);
        for (Doc d : corpus) {
            cs.docs.put(d.itemId(), d);
            cs.vectors.put(d.itemId(), cs.vectorize(d.title()));
        }
        return cs;
    }

    /** 文本 → L2 归一化 TF-IDF 向量（未登录词使用最大 idf）。 */
    public Map<String, Double> vectorize(String title) {
        Map<String, Double> tf = new HashMap<>();
        for (String t : TextTokenizer.tokenize(title)) {
            tf.merge(t, 1.0, Double::sum);
        }
        double unseenIdf = Math.log(docCount + 1.0) + 1.0;
        double norm = 0;
        for (Map.Entry<String, Double> e : tf.entrySet()) {
            double w = e.getValue() * idf.getOrDefault(e.getKey(), unseenIdf);
            e.setValue(w);
            norm += w * w;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (Map.Entry<String, Double> e : tf.entrySet()) {
                e.setValue(e.getValue() / norm);
            }
        }
        return tf;
    }

    static double cosine(Map<String, Double> a, Map<String, Double> b) {
        if (a.size() > b.size()) {
            Map<String, Double> t = a;
            a = b;
            b = t;
        }
        double s = 0;
        for (Map.Entry<String, Double> e : a.entrySet()) {
            Double v = b.get(e.getKey());
            if (v != null) {
                s += e.getValue() * v;
            }
        }
        return s;
    }

    public double similarity(Doc a, Map<String, Double> va, Doc b, Map<String, Double> vb) {
        double cat = a.categoryId() == b.categoryId() ? 1.0 : 0.0;
        return categoryWeight * cat + (1 - categoryWeight) * cosine(va, vb);
    }

    /** 与语料中某个商品最相似的 n 个商品。 */
    public List<ScoredItem> similar(long itemId, int n, LongPredicate filter) {
        Doc d = docs.get(itemId);
        return d == null ? List.of() : similarTo(d, n, Set.of(itemId), filter);
    }

    /** 与任意一个商品（可不在语料中，如刚发布的新商品）最相似的 n 个商品。 */
    public List<ScoredItem> similarTo(Doc probe, int n, Set<Long> exclude, LongPredicate filter) {
        Map<String, Double> vp = vectorize(probe.title());
        TopK top = new TopK(n);
        for (Doc d : docs.values()) {
            if (d.itemId() == probe.itemId() || (exclude != null && exclude.contains(d.itemId()))) {
                continue;
            }
            if (filter != null && !filter.test(d.itemId())) {
                continue;
            }
            double s = similarity(probe, vp, d, vectors.get(d.itemId()));
            if (s > 0) {
                top.offer(d.itemId(), s);
            }
        }
        return top.sorted();
    }

    /**
     * 用户画像（历史商品 → 权重）与语料商品的加权相似度，用于把新商品推给可能感兴趣的用户。
     * 返回结果带推荐理由（贡献最大的历史商品）。
     */
    public List<RecResult> recommendForProfile(Map<Doc, Double> profile, int n, Set<Long> exclude, LongPredicate filter) {
        List<Map.Entry<Doc, Map<String, Double>>> probes = new ArrayList<>();
        Map<Long, Double> weights = new HashMap<>();
        for (Map.Entry<Doc, Double> e : profile.entrySet()) {
            probes.add(Map.entry(e.getKey(), vectorize(e.getKey().title())));
            weights.put(e.getKey().itemId(), e.getValue());
        }
        List<RecResult> scored = new ArrayList<>();
        for (Doc d : docs.values()) {
            if (weights.containsKey(d.itemId()) || (exclude != null && exclude.contains(d.itemId()))) {
                continue;
            }
            if (filter != null && !filter.test(d.itemId())) {
                continue;
            }
            double total = 0, best = -1;
            Long reason = null;
            for (Map.Entry<Doc, Map<String, Double>> p : probes) {
                double s = similarity(p.getKey(), p.getValue(), d, vectors.get(d.itemId())) * weights.get(p.getKey().itemId());
                total += s;
                if (s > best) {
                    best = s;
                    reason = p.getKey().itemId();
                }
            }
            if (total > 0) {
                scored.add(new RecResult(d.itemId(), total, RecSource.CONTENT, reason));
            }
        }
        scored.sort((a, b) -> {
            int c = Double.compare(b.score(), a.score());
            return c != 0 ? c : Long.compare(a.itemId(), b.itemId());
        });
        return scored.size() > n ? new ArrayList<>(scored.subList(0, n)) : scored;
    }

    public int size() {
        return docs.size();
    }
}
