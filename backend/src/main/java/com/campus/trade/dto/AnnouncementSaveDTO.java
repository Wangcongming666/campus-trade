package com.campus.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AnnouncementSaveDTO {
    @NotBlank(message = "标题不能为空")
    @Size(max = 120, message = "标题不超过 120 字")
    private String title;
    @NotBlank(message = "正文不能为空")
    @Size(max = 20000, message = "正文过长")
    private String content;
    @Schema(description = "仅创建时生效：DRAFT（默认）| PUBLISHED")
    @Pattern(regexp = "DRAFT|PUBLISHED", message = "创建时 status 取值为 DRAFT/PUBLISHED")
    private String status;
}
