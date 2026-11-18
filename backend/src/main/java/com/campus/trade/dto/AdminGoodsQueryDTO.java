package com.campus.trade.dto;

import com.campus.trade.common.PageQuery;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminGoodsQueryDTO extends PageQuery {
    @Pattern(regexp = "PENDING_AUDIT|ON_SHELF|RESERVED|OFF_SHELF|SOLD|REJECTED", message = "status 取值不合法")
    private String status;
    private String keyword;
    private Long categoryId;
    private Long sellerId;
}
