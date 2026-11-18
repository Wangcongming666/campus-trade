package com.campus.trade.vo;

import com.campus.trade.entity.SysUser;
import lombok.Data;

@Data
public class UserBriefVO {
    private Long id;
    private String nickname;
    private String avatar;

    public static UserBriefVO of(SysUser u) {
        if (u == null) {
            return null;
        }
        UserBriefVO vo = new UserBriefVO();
        vo.setId(u.getId());
        vo.setNickname(u.getNickname());
        vo.setAvatar(u.getAvatar());
        return vo;
    }
}
