package com.geosaa.security;

import com.geosaa.common.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具类。
 *
 * <p>统一收口“当前登录用户是谁”的取值逻辑，避免 Controller/Service 各自写死用户 ID。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前登录主体，未登录返回 null。
     */
    public static LoginUser getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof LoginUser loginUser ? loginUser : null;
    }

    /**
     * 获取当前登录用户 ID，未登录抛出 401。
     */
    public static Long getCurrentUserId() {
        LoginUser loginUser = getLoginUser();
        if (loginUser == null || loginUser.getUserId() == null) {
            throw new UnauthorizedException("未获取到登录用户信息，请重新登录");
        }
        return loginUser.getUserId();
    }

    /**
     * 获取当前登录用户 ID，未登录返回 null（用于可选场景，不抛异常）。
     */
    public static Long getCurrentUserIdOrNull() {
        LoginUser loginUser = getLoginUser();
        return loginUser == null ? null : loginUser.getUserId();
    }

    /**
     * 获取当前登录用户名，未登录返回 null。
     */
    public static String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getName();
    }
}
