package com.campus.trade.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StudentAuthVO {
    private Long id;
    private Long userId;
    private String username;
    private String nickname;
    private String studentNo;
    private String realName;
    private String school;
    private String cardImageUrl;
    private String status;
    private String reason;
    private LocalDateTime createTime;
}
