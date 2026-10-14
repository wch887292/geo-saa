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
import com.geosaa.security.TokenBlacklistService;
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
    private static final String LOGIN_IP_ATTEMPT_KEY = "geo:login:ip:";
    private static final String REFRESH_TOKEN_KEY = "geo:token:refresh:";
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOCK_DURATION_MINUTES = 30;

    /**
     * 单IP 在时间窗内允许的最大失败次数。
     *
     * <p>原实现只按用户名计数，存在两个缺口：
     * <ol>
     *   <li><b>撞库</b>：攻击者对大量不同用户名各试少量密码，永远碰不到单账号 5 次阈值；</li>
     *   <li><b>锁号 DoS</b>：反过来，只要知道某个存在的用户名，连续 5 次错误即可把该账号
     *       锁死 30 分钟，攻击成本极低。</li>
     * </ol>
     * 增加 IP 维度后，单IP 的失败总量被限死，撞库失效；
     * 同时 IP 阈值（20）远高于单账号阈值（5），正常用户输错几次不会被误伤，
     * 但攻击者想批量锁号也会先撞上自己的 IP 配额。
     */
    private static final int MAX_IP_LOGIN_ATTEMPTS = 20;
    private static final long IP_WINDOW_MINUTES = 15;

    private final UserInfoMapper userInfoMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;
    private final TokenBlacklistService tokenBlacklistService;

    public LoginResponse login(LoginRequest request) {
        return login(request, null);
    }

    /**
     * 登录（带 IP 维度限流）。
     *
     * @param clientIp 客户端 IP，可为空；为空时退化为仅按用户名计数（保持旧行为）。
     */
    public LoginResponse login(LoginRequest request, String clientIp) {
        // 检查登录失败次数
        String attemptKey = LOGIN_ATTEMPT_KEY + request.getUsername();
        Integer attempts = (Integer) redisTemplate.opsForValue().get(attemptKey);
        if (attempts != null && attempts >= MAX_LOGIN_ATTEMPTS) {
            long ttl = redisTemplate.getExpire(attemptKey, TimeUnit.MINUTES);
            throw new BusinessException("账号已被锁定，请" + Math.max(ttl, 1) + "分钟后重试");
        }

        // IP 维度限流：拦住"同一批密码遍历大量用户名"的撞库行为
        if (clientIp != null && !clientIp.isBlank()) {
            String ipKey = LOGIN_IP_ATTEMPT_KEY + clientIp;
            Integer ipAttempts = (Integer) redisTemplate.opsForValue().get(ipKey);
            if (ipAttempts != null && ipAttempts >= MAX_IP_LOGIN_ATTEMPTS) {
                long ipTtl = redisTemplate.getExpire(ipKey, TimeUnit.MINUTES);
                throw new BusinessException("登录尝试过于频繁，请" + Math.max(ipTtl, 1) + "分钟后再试");
            }
        }

        UserInfo userInfo = userInfoMapper.selectByUsername(request.getUsername());
        if (userInfo == null) {
            recordLoginFailure(attemptKey, clientIp);
            throw new UnauthorizedException("用户名或密码错误");
        }

        if (!passwordEncoder.matches(request.getPassword(), userInfo.getPassword())) {
            recordLoginFailure(attemptKey, clientIp);
            throw new UnauthorizedException("用户名或密码错误");
        }

        if (userInfo.getStatus() != null && userInfo.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }

        // 登录成功，清除失败记录
        redisTemplate.delete(attemptKey);
        if (clientIp != null && !clientIp.isBlank()) {
            redisTemplate.delete(LOGIN_IP_ATTEMPT_KEY + clientIp);
        }

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
        tokenBlacklistService.blacklist(token);
        log.info("用户登出: username={}", username);
    }

    public LoginResponse refreshToken(String refreshToken) {
        // 先验签：确认它确实是本服务签发的、未过期的 refresh 类型令牌，
        // 避免只靠 Redis key 存在性判断（拿到任意字符串都能试探）
        if (refreshToken == null || !jwtTokenProvider.validateRefreshToken(refreshToken)) {
            throw new UnauthorizedException("刷新令牌无效或已过期");
        }

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
        return tokenBlacklistService.isBlacklisted(token);
    }

    /**
     * 记录登录失败
     */
    private void recordLoginFailure(String attemptKey, String clientIp) {
        redisTemplate.opsForValue().increment(attemptKey);
        redisTemplate.expire(attemptKey, LOCK_DURATION_MINUTES, TimeUnit.MINUTES);

        // IP 维度计数（滑动窗口）：与账号锁定相互独立，
        // 账号锁定清零登录成功时不应把 IP 配额也一并清掉，否则攻击者可用"一次成功登录"
        // 重置自己的撞库进度。IP 配额只在登录成功时才回收。
        if (clientIp != null && !clientIp.isBlank()) {
            String ipKey = LOGIN_IP_ATTEMPT_KEY + clientIp;
            redisTemplate.opsForValue().increment(ipKey);
            redisTemplate.expire(ipKey, IP_WINDOW_MINUTES, TimeUnit.MINUTES);
        }
    }
}