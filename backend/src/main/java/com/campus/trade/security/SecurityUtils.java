package com.campus.trade.security;

import com.campus.trade.common.BizException;
import com.campus.trade.common.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {
    private SecurityUtils() {
    }

    /** 当前登录用户，游客返回 null。 */
    public static LoginUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser u) {
            return u;
        }
        return null;
    }

    /** 当前用户 ID，游客返回 null。 */
    public static Long currentUserId() {
        LoginUser u = currentUser();
        return u == null ? null : u.userId();
    }

    public static LoginUser requireUser() {
        LoginUser u = currentUser();
        if (u == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return u;
    }

    public static Long requireUserId() {
        return requireUser().userId();
    }
}
