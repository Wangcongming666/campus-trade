package com.campus.trade.controller;

import com.campus.trade.common.PageQuery;
import com.campus.trade.common.PageResult;
import com.campus.trade.common.Result;
import com.campus.trade.dto.ReviewCreateDTO;
import com.campus.trade.service.ReviewService;
import com.campus.trade.vo.ReviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;

@Tag(name = "评价")
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "评价订单（订单完成后双方各一次，重算对方信誉分）")
    @PostMapping
    public Result<ReviewVO> create(@Valid @RequestBody ReviewCreateDTO dto) {
        return Result.ok(reviewService.create(dto));
    }

    @Operation(summary = "某用户收到的评价（游客可看）")
    @GetMapping
    public Result<PageResult<ReviewVO>> list(@RequestParam Long userId, @ParameterObject PageQuery pq) {
        return Result.ok(reviewService.pageForUser(userId, pq));
    }
}
