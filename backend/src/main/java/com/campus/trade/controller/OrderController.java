package com.campus.trade.controller;

import com.campus.trade.common.PageResult;
import com.campus.trade.common.Result;
import com.campus.trade.dto.OrderActionDTO;
import com.campus.trade.dto.OrderCreateDTO;
import com.campus.trade.dto.OrderQueryDTO;
import com.campus.trade.service.OrderService;
import com.campus.trade.service.PayService;
import com.campus.trade.vo.PayOrderVO;
import com.campus.trade.vo.OrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;

@Tag(name = "订单")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final PayService payService;

    @Operation(summary = "下单（商品 ON_SHELF→RESERVED）")
    @PostMapping
    public Result<OrderVO> create(@Valid @RequestBody OrderCreateDTO dto) {
        return Result.ok(orderService.create(dto));
    }

    @Operation(summary = "我的订单（role=buyer|seller，可按 status 过滤）")
    @GetMapping
    public Result<PageResult<OrderVO>> list(@Valid @ParameterObject OrderQueryDTO q) {
        return Result.ok(orderService.page(q));
    }

    @Operation(summary = "订单详情（买家/卖家/管理员）")
    @GetMapping("/{id}")
    public Result<OrderVO> detail(@PathVariable Long id) {
        return Result.ok(orderService.detail(id));
    }

    @Operation(summary = "订单状态变更 action=PAY|CONFIRM|CANCEL|REFUND")
    @PatchMapping("/{id}/status")
    public Result<OrderVO> changeStatus(@PathVariable Long id, @Valid @RequestBody OrderActionDTO dto) {
        return Result.ok(orderService.changeStatus(id, dto));
    }

    @Operation(summary = "创建/获取模拟支付单（仅买家；同一订单只有一张 WAITING 支付单，重复请求返回同一张）")
    @PostMapping("/{id}/pay")
    public Result<PayOrderVO> pay(@PathVariable Long id) {
        return Result.ok(payService.createOrGet(id));
    }
}
