package com.campus.trade.price.controller;

import com.campus.trade.common.Result;
import com.campus.trade.price.dto.PriceSuggestDTO;
import com.campus.trade.price.service.PriceSuggestService;
import com.campus.trade.price.vo.PriceSuggestVO;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 价格建议。需要登录：/api/v1/price/** 不在 SecurityConfig 白名单内，落入 anyRequest().authenticated()。
 */
@Tag(name = "价格建议")
@RestController
@RequestMapping("/api/v1/price")
@RequiredArgsConstructor
public class PriceController {

    private final PriceSuggestService priceSuggestService;

    /** POST /api/v1/price/suggest {categoryId, condition, originalPrice?, title?} */
    @Operation(summary = "价格建议（需登录）")
    @PostMapping("/suggest")
    public Result<PriceSuggestVO> suggest(@Valid @RequestBody PriceSuggestDTO dto) {
        return Result.ok(priceSuggestService.suggest(dto));
    }
}
