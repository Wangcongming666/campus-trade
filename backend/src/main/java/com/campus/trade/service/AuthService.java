package com.campus.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.trade.common.BizException;
import com.campus.trade.common.Constants;
import com.campus.trade.common.ErrorCode;
import com.campus.trade.dto.LoginDTO;
import com.campus.trade.dto.RefreshDTO;
import com.campus.trade.dto.RegisterDTO;
import com.campus.trade.entity.SysUser;
import com.campus.trade.mapper.SysUserMapper;
import com.campus.trade.security.JwtUtil;
import com.campus.trade.security.LoginUser;
import com.campus.trade.security.RedisKeys;
import com.campus.trade.vo.LoginVO;
import com.campus.trade.vo.TokenVO;
import com.campus.trade.vo.UserMeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final CaptchaService captchaService;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redis;

    public UserMeVO register(RegisterDTO dto) {
        captchaService.verify(dto.getCaptchaKey(), dto.getCaptchaCode());
        if (userMapper.exists(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, dto.getUsername()))) {
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }
        String phone = StringUtils.hasText(dto.getPhone()) ? dto.getPhone() : null;
        if (phone != null && userMapper.exists(new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, phone))) {
            throw new BizException(ErrorCode.PHONE_EXISTS);
        }
        SysUser u = new SysUser();
        u.setUsername(dto.getUsername());
        u.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        u.setNickname(dto.getUsername());
        u.setPhone(phone);
        u.setRole(Constants.Role.USER);
        u.setStatus(Constants.UserStatus.NORMAL);
        u.setCreditScore(100);
        u.setStudentAuthStatus(Constants.AuthStatus.NONE);
        try {
            userMapper.insert(u);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }
        return UserConverter.toMe(userMapper.selectById(u.getId()));
    }

    public LoginVO login(LoginDTO dto) {
        captchaService.verify(dto.getCaptchaKey(), dto.getCaptchaCode());
        String account = dto.getAccount().trim();
        SysUser u = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, account).or().eq(SysUser::getPhone, account)
                .or().eq(SysUser::getStudentNo, account)
                .last("LIMIT 1"));
        if (u == null || !passwordEncoder.matches(dto.getPassword(), u.getPasswordHash())) {
            throw new BizException(ErrorCode.BAD_CREDENTIALS);
        }
        if (Constants.UserStatus.BANNED.equals(u.getStatus())) {
            throw new BizException(ErrorCode.ACCOUNT_BANNED);
        }
        LocalDateTime now = LocalDateTime.now();
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, u.getId()).set(SysUser::getLastLoginTime, now));
        u.setLastLoginTime(now);
        return new LoginVO(issueTokens(u), UserConverter.toMe(u));
    }

    /** refreshToken 轮换：旧的立即作废，返回新的一对。 */
    public TokenVO refresh(RefreshDTO dto) {
        String oldId = sha256(dto.getRefreshToken());
        String userId = redis.opsForValue().getAndDelete(RedisKeys.refresh(oldId));
        if (userId == null) {
            throw new BizException(ErrorCode.TOKEN_EXPIRED);
        }
        redis.opsForSet().remove(RedisKeys.userRefreshSet(Long.valueOf(userId)), oldId);
        SysUser u = userMapper.selectById(Long.valueOf(userId));
        if (u == null) {
            throw new BizException(ErrorCode.TOKEN_EXPIRED);
        }
        if (Constants.UserStatus.BANNED.equals(u.getStatus())) {
            throw new BizException(ErrorCode.ACCOUNT_BANNED);
        }
        return issueTokens(u);
    }

    /** 退出：accessToken 加入黑名单直至过期，删除配对的 refreshToken（及请求体中显式传入的 refreshToken）。 */
    public void logout(LoginUser user, String refreshToken) {
        long ttlMs = user.expireAt() - System.currentTimeMillis();
        if (ttlMs > 0) {
            redis.opsForValue().set(RedisKeys.blacklist(user.jti()), "1", Duration.ofMillis(ttlMs));
        }
        if (StringUtils.hasText(user.refreshId())) {
            redis.delete(RedisKeys.refresh(user.refreshId()));
            redis.opsForSet().remove(RedisKeys.userRefreshSet(user.userId()), user.refreshId());
        }
        if (StringUtils.hasText(refreshToken)) {
            String key = RedisKeys.refresh(sha256(refreshToken));
            String owner = redis.opsForValue().get(key);
            if (String.valueOf(user.userId()).equals(owner)) {
                redis.delete(key);
                redis.opsForSet().remove(RedisKeys.userRefreshSet(user.userId()), sha256(refreshToken));
            }
        }
    }

    /** 封禁：作废该用户全部 refreshToken，并打上封禁标记使现有 accessToken 立即失效。 */
    public void revokeAllSessions(Long userId) {
        String setKey = RedisKeys.userRefreshSet(userId);
        java.util.Set<String> ids = redis.opsForSet().members(setKey);
        if (ids != null && !ids.isEmpty()) {
            redis.delete(ids.stream().map(RedisKeys::refresh).toList());
        }
        redis.delete(setKey);
        // accessToken 最长 2 小时有效，标记保留同样时长即可；之后登录/刷新由数据库状态拦截
        redis.opsForValue().set(RedisKeys.banned(userId), "1", Duration.ofSeconds(jwtUtil.getAccessExpire()));
        // 记录作废时间点：此前签发的 accessToken 永久失效（解封后同样无效），保留至其最长有效期
        redis.opsForValue().set(RedisKeys.revokedBefore(userId), String.valueOf(System.currentTimeMillis()),
                Duration.ofSeconds(jwtUtil.getAccessExpire()));
    }

    /** 解封：移除封禁标记；封禁前签发的 token 仍由 revokedBefore 拦截，需重新登录。 */
    public void clearBanMark(Long userId) {
        redis.delete(RedisKeys.banned(userId));
    }

    private TokenVO issueTokens(SysUser u) {
        String refreshToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        // Redis 以 refreshToken 的 SHA-256 为 key；accessToken 中只携带该摘要（rid），不泄露 refreshToken 本身
        String refreshId = sha256(refreshToken);
        redis.opsForValue().set(RedisKeys.refresh(refreshId), String.valueOf(u.getId()),
                Duration.ofSeconds(jwtUtil.getRefreshExpire()));
        String setKey = RedisKeys.userRefreshSet(u.getId());
        redis.opsForSet().add(setKey, refreshId);
        redis.expire(setKey, Duration.ofSeconds(jwtUtil.getRefreshExpire()));
        String accessToken = jwtUtil.createAccessToken(u.getId(), u.getRole(), refreshId);
        return new TokenVO(accessToken, refreshToken, jwtUtil.getAccessExpire());
    }

    static String sha256(String raw) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(d);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
