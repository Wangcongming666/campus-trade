package com.campus.trade.recommend.controller;

import com.campus.trade.common.Result;
import com.campus.trade.recommend.service.RecommendService;
import com.campus.trade.security.SecurityUtils;
import com.campus.trade.service.RecommendFeedService;
import com.campus.trade.vo.GoodsVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 智能推荐接口。两个 GET 接口均在 SecurityConfig 的游客白名单内（/api/v1/recommend/**）：
 * 携带合法 Token 时个性化，游客走热度/冷启动。size 越界由全局异常处理返回 code=400。
 */
@Tag(name = "智能推荐")
@RestController
@RequestMapping("/api/v1/recommend")
@Validated
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendService recommendService;
    private final RecommendFeedService feedService;

    /**
     * GET /api/v1/recommend/home：当前用户取自 JWT（SecurityUtils），游客为 null。
     * <ul>
     *   <li>兼容模式（不传 pageNum/pageSize）：?size=10，data 为 GoodsVO 数组（与原接口一致）；</li>
     *   <li>分页模式（传了 pageNum 或 pageSize 任一）：data 为 {records,total,pageNum,pageSize,pages,hasMore}，
     *       由后端 RecommendFeedService 在外层缓存 Top100 后切片（第 1 页重算，翻页读缓存，页间不重复）。
     *       pageSize 默认取 size（默认 10），超过 50 按 50 处理。</li>
     * </ul>
     */
    @Operation(summary = "首页推荐（游客热度 / 冷启动 / ItemCF 混合）；传 pageNum/pageSize 时返回分页结构")
    @GetMapping("/home")
    public Result<Object> home(@RequestParam(defaultValue = "10") @Min(1) @Max(50) Integer size,
                               @RequestParam(required = false) Integer pageNum,
                               @RequestParam(required = false) Integer pageSize) {
        Long uid = SecurityUtils.currentUserId();
        if (pageNum == null && pageSize == null) {
            return Result.ok(recommendService.home(uid, size));
        }
        int ps = pageSize == null || pageSize < 1 ? size : Math.min(pageSize, RecommendFeedService.MAX_PAGE_SIZE);
        return Result.ok(feedService.home(uid, pageNum == null ? 1 : pageNum, ps));
    }

    /** GET /api/v1/recommend/similar/{goodsId}?size=10：相似商品（详情页"看了又看"）；商品不存在返回 20001。 */
    @Operation(summary = "相似商品")
    @GetMapping("/similar/{goodsId}")
    public Result<List<GoodsVO>> similar(@PathVariable Long goodsId,
                                         @RequestParam(defaultValue = "10") @Min(1) @Max(50) Integer size) {
        return Result.ok(recommendService.similar(goodsId, size, SecurityUtils.currentUserId()));
    }
}
