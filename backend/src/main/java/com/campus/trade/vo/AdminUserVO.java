package com.campus.trade.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminUserVO extends UserMeVO {
    private LocalDateTime lastLoginTime;
}
