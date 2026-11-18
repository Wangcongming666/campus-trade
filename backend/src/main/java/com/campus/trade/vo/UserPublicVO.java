package com.campus.trade.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserPublicVO {
    private Long id;
    private String nickname;
    private String avatar;
    private Integer creditScore;
    private String school;
    private boolean studentVerified;
    private LocalDateTime createTime;
}
