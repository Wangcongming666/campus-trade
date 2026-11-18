package com.campus.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.trade.common.BizException;
import com.campus.trade.common.Constants;
import com.campus.trade.common.ErrorCode;
import com.campus.trade.dto.PasswordUpdateDTO;
import com.campus.trade.dto.StudentVerifyDTO;
import com.campus.trade.dto.UserUpdateDTO;
import com.campus.trade.entity.StudentAuth;
import com.campus.trade.entity.SysUser;
import com.campus.trade.mapper.StudentAuthMapper;
import com.campus.trade.mapper.SysUserMapper;
import com.campus.trade.security.SecurityUtils;
import com.campus.trade.vo.UserMeVO;
import com.campus.trade.vo.UserPublicVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;
    private final StudentAuthMapper studentAuthMapper;
    private final PasswordEncoder passwordEncoder;

    public UserMeVO me() {
        return UserConverter.toMe(requireCurrent());
    }

    public UserMeVO updateMe(UserUpdateDTO dto) {
        SysUser u = requireCurrent();
        LambdaUpdateWrapper<SysUser> w = new LambdaUpdateWrapper<SysUser>().eq(SysUser::getId, u.getId());
        boolean changed = false;
        if (StringUtils.hasText(dto.getNickname())) {
            w.set(SysUser::getNickname, dto.getNickname().trim());
            changed = true;
        }
        if (dto.getAvatar() != null) {
            w.set(SysUser::getAvatar, StringUtils.hasText(dto.getAvatar()) ? dto.getAvatar() : null);
            changed = true;
        }
        if (StringUtils.hasText(dto.getPhone()) && !dto.getPhone().equals(u.getPhone())) {
            if (userMapper.exists(new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, dto.getPhone())
                    .ne(SysUser::getId, u.getId()))) {
                throw new BizException(ErrorCode.PHONE_EXISTS);
            }
            w.set(SysUser::getPhone, dto.getPhone());
            changed = true;
        }
        if (changed) {
            try {
                userMapper.update(null, w);
            } catch (DuplicateKeyException e) {
                throw new BizException(ErrorCode.PHONE_EXISTS);
            }
        }
        return me();
    }

    public void updatePassword(PasswordUpdateDTO dto) {
        SysUser u = requireCurrent();
        if (!passwordEncoder.matches(dto.getOldPassword(), u.getPasswordHash())) {
            throw new BizException(ErrorCode.OLD_PASSWORD_WRONG);
        }
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>().eq(SysUser::getId, u.getId())
                .set(SysUser::getPasswordHash, passwordEncoder.encode(dto.getNewPassword())));
    }

    /** 提交学生认证：写入/重置 student_auth 为 PENDING，sys_user.student_auth_status 同步为 PENDING。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitStudentVerify(StudentVerifyDTO dto) {
        SysUser u = requireCurrent();
        if (Constants.AuthStatus.APPROVED.equals(u.getStudentAuthStatus())) {
            throw new BizException(ErrorCode.STUDENT_ALREADY_VERIFIED);
        }
        if (Constants.AuthStatus.PENDING.equals(u.getStudentAuthStatus())) {
            throw new BizException(ErrorCode.STUDENT_VERIFY_PENDING);
        }
        String studentNo = dto.getStudentNo().trim();
        boolean usedByUser = userMapper.exists(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStudentNo, studentNo).ne(SysUser::getId, u.getId()));
        boolean usedByPending = studentAuthMapper.exists(new LambdaQueryWrapper<StudentAuth>()
                .eq(StudentAuth::getStudentNo, studentNo).ne(StudentAuth::getUserId, u.getId())
                .in(StudentAuth::getStatus, Constants.AuthStatus.PENDING, Constants.AuthStatus.APPROVED));
        if (usedByUser || usedByPending) {
            throw new BizException(ErrorCode.STUDENT_NO_EXISTS);
        }
        StudentAuth existing = studentAuthMapper.selectOne(new LambdaQueryWrapper<StudentAuth>()
                .eq(StudentAuth::getUserId, u.getId()));
        StudentAuth a = existing != null ? existing : new StudentAuth();
        a.setUserId(u.getId());
        a.setStudentNo(studentNo);
        a.setRealName(dto.getRealName().trim());
        a.setSchool(dto.getSchool().trim());
        a.setCardImageUrl(dto.getCardImageUrl());
        a.setStatus(Constants.AuthStatus.PENDING);
        a.setReason(null);
        a.setAuditorId(null);
        a.setAuditTime(null);
        if (existing == null) {
            studentAuthMapper.insert(a);
        } else {
            studentAuthMapper.update(null, new LambdaUpdateWrapper<StudentAuth>().eq(StudentAuth::getId, a.getId())
                    .set(StudentAuth::getStudentNo, a.getStudentNo()).set(StudentAuth::getRealName, a.getRealName())
                    .set(StudentAuth::getSchool, a.getSchool()).set(StudentAuth::getCardImageUrl, a.getCardImageUrl())
                    .set(StudentAuth::getStatus, a.getStatus()).set(StudentAuth::getReason, null)
                    .set(StudentAuth::getAuditorId, null).set(StudentAuth::getAuditTime, null));
        }
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>().eq(SysUser::getId, u.getId())
                .set(SysUser::getStudentAuthStatus, Constants.AuthStatus.PENDING));
        return Map.of("id", a.getId(), "status", a.getStatus());
    }

    public UserPublicVO publicProfile(Long id) {
        SysUser u = userMapper.selectById(id);
        if (u == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        return UserConverter.toPublic(u);
    }

    private SysUser requireCurrent() {
        SysUser u = userMapper.selectById(SecurityUtils.requireUserId());
        if (u == null) {
            throw new BizException(ErrorCode.TOKEN_EXPIRED);
        }
        return u;
    }
}
