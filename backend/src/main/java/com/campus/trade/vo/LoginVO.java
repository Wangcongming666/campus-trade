package com.campus.trade.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** 登录返回 {accessToken, refreshToken, expiresIn, userInfo}。 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LoginVO extends TokenVO {
    private UserMeVO userInfo;

    public LoginVO(TokenVO t, UserMeVO userInfo) {
        super(t.getAccessToken(), t.getRefreshToken(), t.getExpiresIn());
        this.userInfo = userInfo;
    }
}
