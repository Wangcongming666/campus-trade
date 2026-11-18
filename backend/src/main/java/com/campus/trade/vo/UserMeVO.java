package com.campus.trade.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserMeVO {
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String phone;
    private String studentNo;
    private String school;
    private String role;
    private String status;
    private Integer creditScore;
    private String studentAuthStatus;
    private LocalDateTime createTime;
}
