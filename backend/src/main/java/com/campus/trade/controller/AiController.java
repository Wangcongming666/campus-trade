package com.campus.trade.controller;

import com.campus.trade.ai.ClassifyResult;
import com.campus.trade.ai.ClassifyService;
import com.campus.trade.common.BizException;
import com.campus.trade.common.ErrorCode;
import com.campus.trade.common.Result;
import com.campus.trade.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Tag(name = "AI 智能识别")
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final ClassifyService classifyService;
    private final FileService fileService;

    /**
     * 图片自动分类（需登录）。二选一：multipart 字段 file（jpg/png/webp，≤5MB），或 imageUrl（本站已上传图片的相对路径，
     * 如 POST /files/upload 返回的 /files/2026/11/18/xxx.jpg）。同时提供时以 file 为准。
     * imageUrl 只读本地存储，不发起网络请求（防 SSRF），其他 URL 返回 400。
     * 识别不可用/失败时仍返回 code=200，data.source=NONE、分类字段为 null、candidates=[]。
     */
    @Operation(summary = "图片自动分类（file 或 imageUrl 二选一，返回 Top1 + Top3 候选）")
    @PostMapping(value = "/classify", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    public Result<ClassifyResult> classify(@RequestPart(value = "file", required = false) MultipartFile file,
                                           @RequestParam(value = "imageUrl", required = false) String imageUrl) {
        byte[] bytes;
        String filename;
        if (file != null && !file.isEmpty()) {
            fileService.validateImage(file);
            try {
                bytes = file.getBytes();
            } catch (IOException e) {
                throw new BizException(ErrorCode.FILE_UPLOAD_FAILED);
            }
            filename = file.getOriginalFilename();
        } else if (StringUtils.hasText(imageUrl)) {
            bytes = fileService.readLocalImage(imageUrl);
            filename = imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
        } else {
            throw new BizException(ErrorCode.AI_IMAGE_REQUIRED);
        }
        return Result.ok(classifyService.classify(bytes, filename));
    }
}
