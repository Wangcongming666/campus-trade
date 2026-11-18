package com.campus.trade.controller;

import com.campus.trade.common.PageQuery;
import com.campus.trade.common.PageResult;
import com.campus.trade.common.Result;
import com.campus.trade.service.AnnouncementService;
import com.campus.trade.vo.AnnouncementVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;

@Tag(name = "公告")
@RestController
@RequestMapping("/api/v1/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @Operation(summary = "已发布公告（游客可看，按发布时间倒序）")
    @GetMapping
    public Result<PageResult<AnnouncementVO>> list(@ParameterObject PageQuery pq) {
        return Result.ok(announcementService.publicPage(pq));
    }

    @Operation(summary = "公告详情（仅已发布）")
    @GetMapping("/{id}")
    public Result<AnnouncementVO> detail(@PathVariable Long id) {
        return Result.ok(announcementService.publicDetail(id));
    }
}
