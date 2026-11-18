package com.campus.trade.vo;

import lombok.Data;

@Data
public class SellerVO {
    private Long id;
    private String nickname;
    private String avatar;
    private Integer creditScore;
    private String school;
}
