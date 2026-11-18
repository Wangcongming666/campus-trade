package com.campus.trade.dto;

import com.campus.trade.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrderQueryDTO extends PageQuery {
    @Schema(description = "buyer（默认，我买到的）| seller（我卖出的）")
    @Pattern(regexp = "buyer|seller", message = "role 取值为 buyer/seller")
    private String role;
    @Pattern(regexp = "PENDING|PAID|COMPLETED|CANCELLED", message = "status 取值为 PENDING/PAID/COMPLETED/CANCELLED")
    private String status;
}
