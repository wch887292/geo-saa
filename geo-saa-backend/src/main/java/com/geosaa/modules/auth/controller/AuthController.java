package com.geosaa.modules.auth.controller;

import com.geosaa.common.Result;
import com.geosaa.modules.auth.dto.LoginRequest;
import com.geosaa.modules.auth.dto.LoginResponse;
import com.geosaa.modules.auth.entity.UserInfo;
import com.geosaa.modules.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

/**
 * 认证控制器 - 登录/登出/Token刷新/用户信息
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户登录
     *
     * <p>传入客户端 IP 供 {@link AuthService#login} 做 IP 维度限流：
     * 仅有按用户名的锁定不足以防撞库（攻击者可用同一批密码遍历大量账号），
     * 也会被反向利用来锁死已知账号（5次错误即锁 30 分钟）。
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletRequest httpRequest) {
        LoginResponse response = authService.login(request, resolveClientIp(httpRequest));
        return Result.success(response);
    }

    /**
     * 解析真实客户端 IP。
     *
     * <p>优先取网关透传的真实 IP 头（X-Forwarded-For 最左侧非可信代理项、
     * X-Real-IP），取不到再回退到 {@code request.getRemoteAddr()}。
     * 这些头可被客户端伪造，因此只应用于限流与审计，
     * 生产环境仍应在反向代理层强制覆写这些头。
     */
    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            for (String part : xff.split(",")) {
                String ip = part.trim();
                if (!ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                    return ip;
                }
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String remote = request.getRemoteAddr();
        return (remote == null || remote.isBlank()) ? "unknown" : remote;
    }

    /**
     * 用户登出（将 Token 加入黑名单）
     */
    @PostMapping("/logout")
    public Result<Void> logout(Principal principal,
                               @RequestHeader(value = "Authorization", required = false) String authHeader) {
        // Authorization 头设为非必填：缺失时不应返回 400，登出应始终幂等成功
        if (principal != null) {
            String token = authHeader != null && authHeader.startsWith("Bearer ") ?
                    authHeader.substring(7) : null;
            authService.logout(principal.getName(), token);
        }
        return Result.success(null);
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public Result<UserInfo> me(Principal principal) {
        UserInfo userInfo = authService.getCurrentUser(principal.getName());
        return Result.success(userInfo);
    }

    /**
     * 获取用户信息（含角色权限列表和动态菜单）
     */
    @GetMapping("/user-info")
    public Result<Map<String, Object>> userInfo(Principal principal) {
        Map<String, Object> userInfoWithPermissions = authService.getUserInfoWithPermissions(principal.getName());
        return Result.success(userInfoWithPermissions);
    }

    /**
     * 刷新 Token
     */
    @PostMapping("/refresh")
    public Result<LoginResponse> refreshToken(@RequestParam String refreshToken) {
        LoginResponse response = authService.refreshToken(refreshToken);
        return Result.success(response);
    }

    /**
     * 获取动态菜单
     */
    @GetMapping("/menus")
    public Result<java.util.List<Map<String, Object>>> menus(Principal principal) {
        UserInfo userInfo = authService.getCurrentUser(principal.getName());
        java.util.List<Map<String, Object>> menus = authService.getDynamicMenus(userInfo.getRole());
        return Result.success(menus);
    }
}