package com.campus.trade.controller;

import com.campus.trade.common.Result;
import com.campus.trade.service.PayService;
import com.campus.trade.vo.PayOrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "模拟支付", description = "POST /orders/{id}/pay 创建/获取支付单 → POST /pay/{payNo}/confirm 确认支付；15 分钟未支付自动取消")
@RestController
@RequestMapping("/api/v1/pay")
@RequiredArgsConstructor
public class PayController {

    private final PayService payService;

    @Operation(summary = "确认模拟支付（仅买家；订单 PENDING→PAID，幂等）")
    @PostMapping("/{payNo}/confirm")
    public Result<PayOrderVO> confirm(@PathVariable String payNo) {
        return Result.ok(payService.confirm(payNo));
    }

    @Operation(summary = "查询支付单（买家本人或管理员）")
    @GetMapping("/{payNo}")
    public Result<PayOrderVO> get(@PathVariable String payNo) {
        return Result.ok(payService.get(payNo));
    }
}
