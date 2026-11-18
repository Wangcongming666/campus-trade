package com.campus.trade.controller;

import com.campus.trade.common.PageResult;
import com.campus.trade.common.Result;
import com.campus.trade.dto.GoodsQueryDTO;
import com.campus.trade.dto.GoodsSearchDTO;
import com.campus.trade.dto.GoodsSaveDTO;
import com.campus.trade.dto.GoodsStatusDTO;
import com.campus.trade.service.FavoriteService;
import com.campus.trade.service.GoodsService;
import com.campus.trade.vo.GoodsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "商品")
@RestController
@RequestMapping("/api/v1/goods")
@RequiredArgsConstructor
public class GoodsController {

    private final GoodsService goodsService;
    private final FavoriteService favoriteService;

    @Operation(summary = "商品列表（仅 ON_SHELF）")
    @GetMapping
    public Result<PageResult<GoodsVO>> list(@Valid @ParameterObject GoodsQueryDTO q) {
        return Result.ok(goodsService.page(q));
    }

    @Operation(summary = "商品搜索（keyword 必填，其余筛选同列表）")
    @GetMapping("/search")
    public Result<PageResult<GoodsVO>> search(@Valid @ParameterObject GoodsSearchDTO q) {
        return Result.ok(goodsService.page(q));
    }

    @Operation(summary = "商品详情（浏览数 +1）")
    @GetMapping("/{id}")
    public Result<GoodsVO> detail(@PathVariable Long id) {
        return Result.ok(goodsService.detail(id));
    }

    @Operation(summary = "发布商品（进入待审核）")
    @PostMapping
    public Result<GoodsVO> create(@Valid @RequestBody GoodsSaveDTO dto) {
        return Result.ok(goodsService.create(dto));
    }

    @Operation(summary = "修改本人商品（重新进入待审核）")
    @PutMapping("/{id}")
    public Result<GoodsVO> update(@PathVariable Long id, @Valid @RequestBody GoodsSaveDTO dto) {
        return Result.ok(goodsService.update(id, dto));
    }

    @Operation(summary = "卖家上/下架（ON_SHELF ↔ OFF_SHELF）")
    @PatchMapping("/{id}/status")
    public Result<GoodsVO> changeStatus(@PathVariable Long id, @Valid @RequestBody GoodsStatusDTO dto) {
        return Result.ok(goodsService.changeStatus(id, dto.getStatus()));
    }

    @Operation(summary = "删除本人商品（逻辑删除）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        goodsService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "收藏（幂等）")
    @PostMapping("/{id}/favorite")
    public Result<Map<String, Object>> favorite(@PathVariable Long id) {
        return Result.ok(favoriteService.add(id));
    }

    @Operation(summary = "取消收藏（幂等）")
    @DeleteMapping("/{id}/favorite")
    public Result<Map<String, Object>> unfavorite(@PathVariable Long id) {
        return Result.ok(favoriteService.remove(id));
    }
}
