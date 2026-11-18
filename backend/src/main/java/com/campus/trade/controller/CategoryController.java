package com.campus.trade.controller;

import com.campus.trade.common.Result;
import com.campus.trade.service.CategoryService;
import com.campus.trade.vo.CategoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "分类")
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "分类平铺（仅启用）")
    @GetMapping
    public Result<List<CategoryVO>> flat() {
        return Result.ok(categoryService.flat());
    }

    @Operation(summary = "分类树（仅启用）")
    @GetMapping("/tree")
    public Result<List<CategoryVO>> tree() {
        return Result.ok(categoryService.tree());
    }
}
