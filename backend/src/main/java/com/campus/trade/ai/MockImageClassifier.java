package com.campus.trade.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 测试/演示用分类器（provider=mock）：按文件名关键词映射到现有子分类。
 * 命中时 Top1 置信度 0.6~0.9；识别不出时返回 3 个常见品类作为低置信度（0.1）候选。不看图片内容。
 */
@Component
@RequiredArgsConstructor
public class MockImageClassifier implements ImageClassifier {

    public static final String NAME = "mock";

    private final CategoryResolver resolver;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public ClassifyResult classify(byte[] imageBytes, String filename) {
        LinkedHashMap<String, Integer> hits = CategoryKeywordMatcher.match(filename);
        List<ClassifyResult.Candidate> ranked = new ArrayList<>();
        if (!hits.isEmpty()) {
            int total = hits.values().stream().mapToInt(Integer::intValue).sum();
            List<Map.Entry<String, Integer>> sorted = new ArrayList<>(hits.entrySet());
            sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed()); // 稳定排序：同分保持规则顺序
            for (int i = 0; i < sorted.size(); i++) {
                double conf = i == 0 ? Math.min(0.9, 0.6 + 0.1 * sorted.get(i).getValue())
                        : Math.min(0.5, 0.3 * sorted.get(i).getValue() / total);
                resolver.byName(sorted.get(i).getKey(), conf).ifPresent(ranked::add);
            }
        }
        for (String name : CategoryKeywordMatcher.fallbackNames()) {
            if (ranked.size() >= ClassifyResult.TOP_N) {
                break;
            }
            if (ranked.stream().noneMatch(c -> c.categoryName().equals(name))) {
                resolver.byName(name, ranked.isEmpty() ? 0.1 : 0.05).ifPresent(ranked::add);
            }
        }
        return ClassifyResult.of(ranked, "MOCK");
    }
}
