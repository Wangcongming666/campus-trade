package com.campus.trade.ai;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 图片分类结果。categoryId 等为 Top1；candidates 固定为 Top3（不足 3 个时有几个给几个），按置信度降序。
 * ID 为 Long，经全局 Jackson 配置序列化为字符串。识别失败/不可用时为 {@link #none()}：分类字段为 null、candidates 为空、source=NONE。
 */
@Schema(description = "图片分类结果")
public record ClassifyResult(
        @Schema(description = "Top1 子分类 ID") Long categoryId,
        @Schema(description = "Top1 子分类名称") String categoryName,
        @Schema(description = "Top1 的一级分类 ID") Long parentCategoryId,
        @Schema(description = "Top1 的一级分类名称") String parentCategoryName,
        @Schema(description = "Top1 置信度 0~1") double confidence,
        @Schema(description = "Top3 候选") List<Candidate> candidates,
        @Schema(description = "结果来源：clip / mock / baidu；失败为 NONE") String source) {

    public static final String SOURCE_NONE = "NONE";
    public static final int TOP_N = 3;

    public ClassifyResult {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    /** 由候选列表（已按置信度降序）构造：Top1 取第一个，candidates 截取前 3 个。 */
    public static ClassifyResult of(List<Candidate> ranked, String source) {
        if (ranked == null || ranked.isEmpty()) {
            return none();
        }
        Candidate top = ranked.get(0);
        return new ClassifyResult(top.categoryId(), top.categoryName(), top.parentCategoryId(),
                top.parentCategoryName(), top.confidence(), ranked.subList(0, Math.min(TOP_N, ranked.size())), source);
    }

    public static ClassifyResult none() {
        return new ClassifyResult(null, null, null, null, 0, List.of(), SOURCE_NONE);
    }

    @Schema(description = "候选分类")
    public record Candidate(Long categoryId, String categoryName, Long parentCategoryId, String parentCategoryName,
                            double confidence) {
    }
}
