package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("report")
public class Report {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long reporterId;
    /** GOODS / USER */
    private String targetType;
    private Long targetId;
    private String reason;
    private String detail;
    /** PENDING / APPROVED / REJECTED */
    private String status;
    /** APPROVE / REJECT */
    private String result;
    private String remark;
    private Long auditorId;
    private LocalDateTime auditTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
