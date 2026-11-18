package com.campus.trade.recommend.controller;

import com.campus.trade.common.Result;
import com.campus.trade.recommend.service.RecommendService;
import com.campus.trade.security.SecurityUtils;
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

    /** GET /api/v1/recommend/home?size=10：当前用户取自 JWT（SecurityUtils），游客为 null。 */
    @Operation(summary = "首页推荐（游客热度 / 冷启动 / ItemCF 混合）")
    @GetMapping("/home")
    public Result<List<GoodsVO>> home(@RequestParam(defaultValue = "10") @Min(1) @Max(50) Integer size) {
        return Result.ok(recommendService.home(SecurityUtils.currentUserId(), size));
    }

    /** GET /api/v1/recommend/similar/{goodsId}?size=10：相似商品（详情页"看了又看"）；商品不存在返回 20001。 */
    @Operation(summary = "相似商品")
    @GetMapping("/similar/{goodsId}")
    public Result<List<GoodsVO>> similar(@PathVariable Long goodsId,
                                         @RequestParam(defaultValue = "10") @Min(1) @Max(50) Integer size) {
        return Result.ok(recommendService.similar(goodsId, size, SecurityUtils.currentUserId()));
    }
}
