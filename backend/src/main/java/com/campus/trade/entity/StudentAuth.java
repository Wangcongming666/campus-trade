package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("student_auth")
public class StudentAuth {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String studentNo;
    private String realName;
    private String school;
    private String cardImageUrl;
    private String status;
    private String reason;
    private Long auditorId;
    private LocalDateTime auditTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
