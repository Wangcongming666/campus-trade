package com.campus.trade.ai;

import com.campus.trade.entity.Category;
import com.campus.trade.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/** 分类名称 / ID → 数据库中启用分类的候选（补全名称与一级分类），供各 ImageClassifier 与 ClassifyService 复用。 */
@Component
@RequiredArgsConstructor
public class CategoryResolver {

    private final CategoryService categoryService;

    /** 按名称找启用的分类（子分类优先）。 */
    public Optional<ClassifyResult.Candidate> byName(String name, double confidence) {
        List<Category> all = categoryService.listEnabled();
        return all.stream().filter(c -> c.getName().equals(name))
                .sorted(Comparator.comparing(c -> c.getParentId() == null ? 1 : 0))
                .findFirst().map(c -> toCandidate(c, all, confidence));
    }

    /** 按 ID 校验并补全；分类不存在或已停用返回 empty。 */
    public Optional<ClassifyResult.Candidate> byId(Long id, double confidence) {
        if (id == null) {
            return Optional.empty();
        }
        List<Category> all = categoryService.listEnabled();
        return all.stream().filter(c -> c.getId().equals(id)).findFirst().map(c -> toCandidate(c, all, confidence));
    }

    private static ClassifyResult.Candidate toCandidate(Category c, List<Category> all, double confidence) {
        Category parent = c.getParentId() == null ? null
                : all.stream().filter(p -> p.getId().equals(c.getParentId())).findFirst().orElse(null);
        double conf = Math.max(0, Math.min(1, Math.round(confidence * 10000) / 10000.0));
        // 一级分类本身作为结果时，parentCategory 填自身，便于前端级联选择器定位
        Long parentId = parent != null ? parent.getId() : c.getId();
        String parentName = parent != null ? parent.getName() : c.getName();
        return new ClassifyResult.Candidate(c.getId(), c.getName(), parentId, parentName, conf);
    }
}
