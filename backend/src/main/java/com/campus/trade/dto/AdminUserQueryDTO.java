package com.campus.trade.dto;

import com.campus.trade.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminUserQueryDTO extends PageQuery {
    @Schema(description = "用户名/昵称/手机号/学号模糊匹配")
    private String keyword;
    @Pattern(regexp = "NORMAL|BANNED", message = "status 取值为 NORMAL/BANNED")
    private String status;
    @Pattern(regexp = "USER|ADMIN", message = "role 取值为 USER/ADMIN")
    private String role;
}
