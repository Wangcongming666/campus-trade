package com.campus.trade.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 通用状态变更 {status}，取值由各接口校验。 */
@Data
public class StatusDTO {
    @NotBlank(message = "status 不能为空")
    private String status;
}
