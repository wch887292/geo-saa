package com.geosaa.modules.auth.controller;

import com.geosaa.common.Result;
import com.geosaa.modules.auth.dto.LoginRequest;
import com.geosaa.modules.auth.dto.LoginResponse;
import com.geosaa.modules.auth.entity.UserInfo;
import com.geosaa.modules.auth.service.AuthService;
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
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return Result.success(response);
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