package com.campus.trade.service;

import com.campus.trade.common.Constants;
import com.campus.trade.entity.SysUser;
import com.campus.trade.vo.SellerVO;
import com.campus.trade.vo.UserMeVO;
import com.campus.trade.vo.UserPublicVO;

public final class UserConverter {
    private UserConverter() {
    }

    public static UserMeVO toMe(SysUser u) {
        UserMeVO vo = new UserMeVO();
        vo.setId(u.getId());
        vo.setUsername(u.getUsername());
        vo.setNickname(u.getNickname());
        vo.setAvatar(u.getAvatar());
        vo.setPhone(u.getPhone());
        vo.setStudentNo(u.getStudentNo());
        vo.setSchool(u.getSchool());
        vo.setRole(u.getRole());
        vo.setStatus(u.getStatus());
        vo.setCreditScore(u.getCreditScore());
        vo.setStudentAuthStatus(u.getStudentAuthStatus());
        vo.setCreateTime(u.getCreateTime());
        return vo;
    }

    public static UserPublicVO toPublic(SysUser u) {
        UserPublicVO vo = new UserPublicVO();
        vo.setId(u.getId());
        vo.setNickname(u.getNickname());
        vo.setAvatar(u.getAvatar());
        vo.setCreditScore(u.getCreditScore());
        vo.setSchool(u.getSchool());
        vo.setStudentVerified(Constants.AuthStatus.APPROVED.equals(u.getStudentAuthStatus()));
        vo.setCreateTime(u.getCreateTime());
        return vo;
    }

    public static SellerVO toSeller(SysUser u) {
        SellerVO vo = new SellerVO();
        vo.setId(u.getId());
        vo.setNickname(u.getNickname());
        vo.setAvatar(u.getAvatar());
        vo.setCreditScore(u.getCreditScore());
        vo.setSchool(u.getSchool());
        return vo;
    }
}
