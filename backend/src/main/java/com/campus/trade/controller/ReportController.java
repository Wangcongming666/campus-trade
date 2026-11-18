package com.campus.trade.controller;

import com.campus.trade.common.Result;
import com.campus.trade.dto.ReportCreateDTO;
import com.campus.trade.service.ReportService;
import com.campus.trade.vo.ReportVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "举报")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "举报商品或用户 {targetType: GOODS|USER, targetId, reason, detail?}")
    @PostMapping
    public Result<ReportVO> create(@Valid @RequestBody ReportCreateDTO dto) {
        return Result.ok(reportService.create(dto));
    }
}
