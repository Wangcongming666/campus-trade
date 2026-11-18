package com.campus.trade.ai.clip;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 分类文本向量（scripts/ai/build_text_embeddings.py 生成的 category_embeddings.json）。
 * 每个分类一条：categoryId、名称、父分类、L2 归一化后的 512 维向量。不可变，线程安全。
 */
public final class CategoryEmbeddings {

    /** 单个分类的文本向量；parentId/parentName 对无子分类的一级分类为 null。 */
    public record Entry(long categoryId, String name, Long parentId, String parentName, float[] vector) {
    }

    private final List<Entry> entries;
    private final int dim;

    public CategoryEmbeddings(List<Entry> entries) {
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("分类向量为空");
        }
        this.dim = entries.get(0).vector().length;
        for (Entry e : entries) {
            if (e.vector().length != dim) {
                throw new IllegalArgumentException("分类 " + e.categoryId() + " 向量维度不一致");
            }
        }
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public List<Entry> entries() {
        return entries;
    }

    public int dim() {
        return dim;
    }

    /**
     * 读取 JSON。
     *
     * @param ensemble true 读 embedding（提示词集成），false 读 singleEmbedding（单提示词）
     */
    public static CategoryEmbeddings load(InputStream in, boolean ensemble) throws IOException {
        JsonNode root = new ObjectMapper().readTree(in);
        JsonNode cats = root.path("categories");
        if (!cats.isArray()) {
            throw new IOException("缺少 categories 数组");
        }
        String field = ensemble ? "embedding" : "singleEmbedding";
        List<Entry> list = new ArrayList<>();
        for (JsonNode c : cats) {
            JsonNode v = c.path(field);
            if (!v.isArray() || v.isEmpty()) {
                throw new IOException("分类 " + c.path("categoryId").asText() + " 缺少 " + field);
            }
            float[] vec = new float[v.size()];
            for (int i = 0; i < vec.length; i++) {
                vec[i] = (float) v.get(i).asDouble();
            }
            JsonNode p = c.path("parentId");
            list.add(new Entry(Long.parseLong(c.path("categoryId").asText()), c.path("name").asText(),
                    p.isNull() || p.isMissingNode() || p.asText().isEmpty() ? null : Long.parseLong(p.asText()),
                    c.path("parentName").isNull() ? null : c.path("parentName").asText(null),
                    ZeroShotScorer.l2normalize(vec)));
        }
        return new CategoryEmbeddings(list);
    }
}
