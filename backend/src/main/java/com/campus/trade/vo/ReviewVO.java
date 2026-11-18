package com.campus.trade.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReviewVO {
    private Long id;
    private Long orderId;
    private UserBriefVO fromUser;
    private Integer score;
    private String content;
    private LocalDateTime createTime;
}
