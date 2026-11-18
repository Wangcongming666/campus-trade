package com.campus.trade.controller;

import com.campus.trade.common.PageQuery;
import com.campus.trade.common.PageResult;
import com.campus.trade.common.Result;
import com.campus.trade.dto.AnnouncementSaveDTO;
import com.campus.trade.dto.ReportHandleDTO;
import com.campus.trade.dto.StatusDTO;
import com.campus.trade.service.AnnouncementService;
import com.campus.trade.service.ReportService;
import com.campus.trade.vo.AnnouncementVO;
import com.campus.trade.vo.ReportVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理端-举报与公告")
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminContentController {

    private final ReportService reportService;
    private final AnnouncementService announcementService;

    @Operation(summary = "举报列表")
    @GetMapping("/reports")
    public Result<PageResult<ReportVO>> reports(
            @RequestParam(required = false)
            @Pattern(regexp = "PENDING|APPROVED|REJECTED", message = "status 取值为 PENDING/APPROVED/REJECTED") String status,
            @ParameterObject PageQuery pq) {
        return Result.ok(reportService.page(status, pq));
    }

    @Operation(summary = "处理举报 {result: APPROVE|REJECT, remark?}")
    @PatchMapping("/reports/{id}")
    public Result<ReportVO> handleReport(@PathVariable Long id, @Valid @RequestBody ReportHandleDTO dto) {
        return Result.ok(reportService.handle(id, dto));
    }

    @Operation(summary = "公告列表（全部状态）")
    @GetMapping("/announcements")
    public Result<PageResult<AnnouncementVO>> announcements(
            @RequestParam(required = false)
            @Pattern(regexp = "DRAFT|PUBLISHED|OFF_SHELF", message = "status 取值为 DRAFT/PUBLISHED/OFF_SHELF") String status,
            @ParameterObject PageQuery pq) {
        return Result.ok(announcementService.adminPage(status, pq));
    }

    @Operation(summary = "公告详情")
    @GetMapping("/announcements/{id}")
    public Result<AnnouncementVO> announcement(@PathVariable Long id) {
        return Result.ok(announcementService.adminDetail(id));
    }

    @Operation(summary = "新建公告（status 默认 DRAFT，可直接 PUBLISHED）")
    @PostMapping("/announcements")
    public Result<AnnouncementVO> create(@Valid @RequestBody AnnouncementSaveDTO dto) {
        return Result.ok(announcementService.create(dto));
    }

    @Operation(summary = "修改公告标题/正文")
    @PutMapping("/announcements/{id}")
    public Result<AnnouncementVO> update(@PathVariable Long id, @Valid @RequestBody AnnouncementSaveDTO dto) {
        return Result.ok(announcementService.update(id, dto));
    }

    @Operation(summary = "公告状态 {status: DRAFT|PUBLISHED|OFF_SHELF}")
    @PatchMapping("/announcements/{id}/status")
    public Result<AnnouncementVO> status(@PathVariable Long id, @Valid @RequestBody StatusDTO dto) {
        return Result.ok(announcementService.changeStatus(id, dto.getStatus()));
    }

    @Operation(summary = "删除公告")
    @DeleteMapping("/announcements/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        announcementService.delete(id);
        return Result.ok();
    }
}
