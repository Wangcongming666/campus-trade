package com.campus.trade.controller;

import com.campus.trade.common.PageQuery;
import com.campus.trade.common.PageResult;
import com.campus.trade.common.Result;
import com.campus.trade.dto.AdminGoodsQueryDTO;
import com.campus.trade.dto.AdminUserQueryDTO;
import com.campus.trade.dto.AuditDTO;
import com.campus.trade.dto.StatusDTO;
import com.campus.trade.service.AdminService;
import com.campus.trade.vo.AdminUserVO;
import com.campus.trade.vo.GoodsVO;
import com.campus.trade.vo.StatsVO;
import com.campus.trade.vo.StudentAuthVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理端")
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @Operation(summary = "待审核商品（完整 GoodsVO，含 description/images）")
    @GetMapping("/goods/audits")
    public Result<PageResult<GoodsVO>> audits(@ParameterObject PageQuery pq) {
        return Result.ok(adminService.auditList(pq));
    }

    @Operation(summary = "审核商品 APPROVE→ON_SHELF / REJECT→REJECTED（reason 必填）")
    @PatchMapping("/goods/{id}/audit")
    public Result<GoodsVO> audit(@PathVariable Long id, @Valid @RequestBody AuditDTO dto) {
        return Result.ok(adminService.audit(id, dto));
    }

    @Operation(summary = "全部商品（status/keyword/categoryId/sellerId 过滤）")
    @GetMapping("/goods")
    public Result<PageResult<GoodsVO>> goods(@Valid @ParameterObject AdminGoodsQueryDTO q) {
        return Result.ok(adminService.goodsList(q));
    }

    @Operation(summary = "管理员上/下架 {status: ON_SHELF|OFF_SHELF}")
    @PatchMapping("/goods/{id}/status")
    public Result<GoodsVO> goodsStatus(@PathVariable Long id, @Valid @RequestBody StatusDTO dto) {
        return Result.ok(adminService.goodsStatus(id, dto.getStatus()));
    }

    @Operation(summary = "用户列表（keyword/status/role 过滤）")
    @GetMapping("/users")
    public Result<PageResult<AdminUserVO>> users(@Valid @ParameterObject AdminUserQueryDTO q) {
        return Result.ok(adminService.userList(q));
    }

    @Operation(summary = "封禁/解封 {status: NORMAL|BANNED}")
    @PatchMapping("/users/{id}/status")
    public Result<AdminUserVO> userStatus(@PathVariable Long id, @Valid @RequestBody StatusDTO dto) {
        return Result.ok(adminService.userStatus(id, dto.getStatus()));
    }

    @Operation(summary = "学生认证申请列表")
    @GetMapping("/student-verifies")
    public Result<PageResult<StudentAuthVO>> verifies(
            @RequestParam(required = false)
            @Pattern(regexp = "PENDING|APPROVED|REJECTED", message = "status 取值为 PENDING/APPROVED/REJECTED") String status,
            @ParameterObject PageQuery pq) {
        return Result.ok(adminService.verifyList(status, pq));
    }

    @Operation(summary = "审核学生认证 {result: APPROVE|REJECT, reason?}")
    @PatchMapping("/student-verifies/{id}")
    public Result<StudentAuthVO> verify(@PathVariable Long id, @Valid @RequestBody AuditDTO dto) {
        return Result.ok(adminService.verify(id, dto));
    }

    @Operation(summary = "统计概览", description = "days 仅支持 7 或 30（默认 7），其他值返回 400")
    @GetMapping("/stats")
    public Result<StatsVO> stats(
            @RequestParam(defaultValue = "7") @Pattern(regexp = "7|30", message = "days 取值为 7 或 30") String days) {
        return Result.ok(adminService.stats(Integer.parseInt(days)));
    }
}
