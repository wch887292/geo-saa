package com.geosaa.modules.auth.service;

import com.geosaa.common.exception.BusinessException;
import com.geosaa.common.exception.UnauthorizedException;
import com.geosaa.modules.auth.dto.LoginRequest;
import com.geosaa.modules.auth.dto.LoginResponse;
import com.geosaa.modules.auth.entity.RolePermission;
import com.geosaa.modules.auth.entity.UserInfo;
import com.geosaa.modules.auth.mapper.RolePermissionMapper;
import com.geosaa.modules.auth.mapper.UserInfoMapper;
import com.geosaa.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String LOGIN_ATTEMPT_KEY = "geo:login:attempt:";
    private static final String TOKEN_BLACKLIST_KEY = "geo:token:blacklist:";
    private static final String REFRESH_TOKEN_KEY = "geo:token:refresh:";
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOCK_DURATION_MINUTES = 30;
    private static final long TOKEN_BLACKLIST_TTL_HOURS = 24;

    private final UserInfoMapper userInfoMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;

    public LoginResponse login(LoginRequest request) {
        // 检查登录失败次数
        String attemptKey = LOGIN_ATTEMPT_KEY + request.getUsername();
        Integer attempts = (Integer) redisTemplate.opsForValue().get(attemptKey);
        if (attempts != null && attempts >= MAX_LOGIN_ATTEMPTS) {
            long ttl = redisTemplate.getExpire(attemptKey, TimeUnit.MINUTES);
            throw new BusinessException("账号已被锁定，请" + ttl + "分钟后重试");
        }

        UserInfo userInfo = userInfoMapper.selectByUsername(request.getUsername());
        if (userInfo == null) {
            recordLoginFailure(attemptKey);
            throw new UnauthorizedException("用户名或密码错误");
        }

        if (!passwordEncoder.matches(request.getPassword(), userInfo.getPassword())) {
            recordLoginFailure(attemptKey);
            throw new UnauthorizedException("用户名或密码错误");
        }

        if (userInfo.getStatus() != null && userInfo.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }

        // 登录成功，清除失败记录
        redisTemplate.delete(attemptKey);

        String token = jwtTokenProvider.generateToken(userInfo.getUsername());
        // 生成刷新令牌
        String refreshToken = jwtTokenProvider.generateRefreshToken(userInfo.getUsername());
        redisTemplate.opsForValue().set(REFRESH_TOKEN_KEY + refreshToken, userInfo.getUsername(), 7, TimeUnit.DAYS);

        // 获取角色权限
        List<String> permissions = getPermissionsByRole(userInfo.getRole());

        log.info("用户登录成功: username={}, role={}", userInfo.getUsername(), userInfo.getRole());
        return new LoginResponse(token, refreshToken, userInfo.getUsername(), userInfo.getNickname(), userInfo.getRole(), permissions);
    }

    public void logout(String username, String token) {
        // 将 token 加入黑名单
        if (token != null) {
            String tokenKey = TOKEN_BLACKLIST_KEY + token;
            redisTemplate.opsForValue().set(tokenKey, "logout", TOKEN_BLACKLIST_TTL_HOURS, TimeUnit.HOURS);
        }
        log.info("用户登出: username={}", username);
    }

    public LoginResponse refreshToken(String refreshToken) {
        String refreshKey = REFRESH_TOKEN_KEY + refreshToken;
        String username = (String) redisTemplate.opsForValue().get(refreshKey);
        if (username == null) {
            throw new UnauthorizedException("刷新令牌无效或已过期");
        }

        UserInfo userInfo = userInfoMapper.selectByUsername(username);
        if (userInfo == null) {
            throw new UnauthorizedException("用户不存在");
        }

        // 删除旧刷新令牌
        redisTemplate.delete(refreshKey);

        // 生成新令牌
        String newToken = jwtTokenProvider.generateToken(username);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(username);
        redisTemplate.opsForValue().set(REFRESH_TOKEN_KEY + newRefreshToken, username, 7, TimeUnit.DAYS);

        List<String> permissions = getPermissionsByRole(userInfo.getRole());
        return new LoginResponse(newToken, newRefreshToken, username, userInfo.getNickname(), userInfo.getRole(), permissions);
    }

    public UserInfo getCurrentUser(String username) {
        return userInfoMapper.selectByUsername(username);
    }

    /**
     * 获取用户信息（含角色权限列表）
     */
    public Map<String, Object> getUserInfoWithPermissions(String username) {
        UserInfo userInfo = userInfoMapper.selectByUsername(username);
        if (userInfo == null) {
            throw new UnauthorizedException("用户不存在");
        }
        List<String> permissions = getPermissionsByRole(userInfo.getRole());
        List<Map<String, Object>> menus = getDynamicMenus(userInfo.getRole());

        Map<String, Object> result = new HashMap<>();
        result.put("user", userInfo);
        result.put("permissions", permissions);
        result.put("menus", menus);
        return result;
    }

    /**
     * 获取动态菜单（从数据库 role_permission 表读取）
     */
    public List<Map<String, Object>> getDynamicMenus(String role) {
        List<RolePermission> rolePermissions = rolePermissionMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRole, role)
                .eq(RolePermission::getStatus, 1)
        );

        // 转换权限为菜单结构
        return rolePermissions.stream()
            .filter(rp -> rp.getPermission() != null && rp.getPermission().startsWith("menu:"))
            .map(rp -> {
                Map<String, Object> menu = new HashMap<>();
                menu.put("name", rp.getDescription());
                menu.put("path", rp.getPermission().replace("menu:", "/"));
                menu.put("permission", rp.getPermission());
                return menu;
            })
            .collect(Collectors.toList());
    }

    /**
     * 获取角色权限列表
     */
    public List<String> getPermissionsByRole(String role) {
        List<RolePermission> rolePermissions = rolePermissionMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRole, role)
                .eq(RolePermission::getStatus, 1)
        );
        return rolePermissions.stream()
            .map(RolePermission::getPermission)
            .collect(Collectors.toList());
    }

    /**
     * 校验 Token 是否在黑名单中
     */
    public boolean isTokenBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(TOKEN_BLACKLIST_KEY + token));
    }

    /**
     * 记录登录失败
     */
    private void recordLoginFailure(String attemptKey) {
        redisTemplate.opsForValue().increment(attemptKey);
        redisTemplate.expire(attemptKey, LOCK_DURATION_MINUTES, TimeUnit.MINUTES);
    }
}