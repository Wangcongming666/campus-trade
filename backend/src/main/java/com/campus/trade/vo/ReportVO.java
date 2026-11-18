package com.campus.trade.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReportVO {
    private Long id;
    private UserBriefVO reporter;
    private String targetType;
    private Long targetId;
    /** 被举报对象名称：商品标题或用户昵称（已删除时为 null） */
    private String targetName;
    private String reason;
    private String detail;
    private String status;
    private String result;
    private String remark;
    private Long auditorId;
    private LocalDateTime auditTime;
    private LocalDateTime createTime;
}
