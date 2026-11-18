package com.campus.trade.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 图片分类调度：按 campus.ai.classify.provider（clip | mock | baidu，默认 clip）从 Spring 容器中所有
 * {@link ImageClassifier} 里按 name() 选择实现。选中的实现不存在、不可用或抛异常时返回 {@link ClassifyResult#none()}
 * （code=200，source=NONE），不做跨实现降级。实现返回的分类会再次校验为数据库中存在且启用的分类，
 * 补全名称/一级分类，按置信度降序，candidates 截取 Top3。
 */
@Slf4j
@Service
public class ClassifyService {

    private final Map<String, ImageClassifier> classifiers = new HashMap<>();
    private final String provider;
    private final CategoryResolver resolver;

    public ClassifyService(List<ImageClassifier> list, CategoryResolver resolver,
                           @Value("${campus.ai.classify.provider:clip}") String provider) {
        list.forEach(c -> classifiers.put(c.name().toLowerCase(Locale.ROOT), c));
        this.resolver = resolver;
        this.provider = provider == null ? "clip" : provider.trim().toLowerCase(Locale.ROOT);
        log.info("[ai] image classifier provider={}, registered={}", this.provider, classifiers.keySet());
    }

    public String provider() {
        return provider;
    }

    /** imageBytes 已由调用方完成类型与大小校验。 */
    public ClassifyResult classify(byte[] imageBytes, String filename) {
        ImageClassifier c = classifiers.get(provider);
        if (c == null) {
            log.warn("[ai] classifier '{}' not found", provider);
            return ClassifyResult.none();
        }
        ClassifyResult raw;
        try {
            if (!c.isAvailable()) {
                return ClassifyResult.none();
            }
            raw = c.classify(imageBytes, filename);
        } catch (Exception e) {
            log.warn("[ai] classifier '{}' failed: {}", provider, e.getMessage());
            return ClassifyResult.none();
        }
        return normalize(raw, c.name());
    }

    /** 校验分类存在且启用、去重、按置信度降序、Top3；全部无效时返回 NONE。 */
    ClassifyResult normalize(ClassifyResult raw, String defaultSource) {
        if (raw == null) {
            return ClassifyResult.none();
        }
        LinkedHashMap<Long, ClassifyResult.Candidate> valid = new LinkedHashMap<>();
        if (raw.categoryId() != null) {
            resolver.byId(raw.categoryId(), raw.confidence()).ifPresent(x -> valid.put(x.categoryId(), x));
        }
        for (ClassifyResult.Candidate x : raw.candidates()) {
            if (x != null && !valid.containsKey(x.categoryId())) {
                resolver.byId(x.categoryId(), x.confidence()).ifPresent(v -> valid.put(v.categoryId(), v));
            }
        }
        if (valid.isEmpty()) {
            return ClassifyResult.none();
        }
        List<ClassifyResult.Candidate> ranked = new ArrayList<>(valid.values());
        ranked.sort(Comparator.comparingDouble(ClassifyResult.Candidate::confidence).reversed());
        String source = raw.source() == null || raw.source().isBlank() ? defaultSource : raw.source();
        return ClassifyResult.of(ranked, source);
    }
}
