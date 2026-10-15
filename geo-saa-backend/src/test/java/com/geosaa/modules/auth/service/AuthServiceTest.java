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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AuthService 单测（O5 测试补全）。
 *
 * <p>覆盖：登录成功/用户不存在/密码错误/账号锁定/账号禁用、登出黑名单、
 * refreshToken 成功/无效/Redis 不存在、getCurrentUser、getUserInfoWithPermissions、
 * getDynamicMenus 菜单过滤、getPermissionsByRole。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserInfoMapper userInfoMapper;
    @Mock private RolePermissionMapper rolePermissionMapper;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;
    @Mock private TokenBlacklistService tokenBlacklistService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        authService = new AuthService(userInfoMapper, rolePermissionMapper, jwtTokenProvider,
                passwordEncoder, redisTemplate, tokenBlacklistService);
    }

    private UserInfo activeUser(String username, String role) {
        UserInfo u = new UserInfo();
        u.setUsername(username);
        u.setPassword("encoded-pass");
        u.setNickname("测试用户");
        u.setRole(role);
        u.setStatus(1);
        return u;
    }

    private LoginRequest req(String username, String password) {
        LoginRequest r = new LoginRequest();
        r.setUsername(username);
        r.setPassword(password);
        return r;
    }

    private RolePermission perm(String role, String permission, String desc, Integer status) {
        RolePermission rp = new RolePermission();
        rp.setRole(role);
        rp.setPermission(permission);
        rp.setDescription(desc);
        rp.setStatus(status);
        return rp;
    }

    // ---------- 登录成功 ----------

    @Test
    void loginSuccessReturnsTokensAndPermissions() {
        UserInfo user = activeUser("admin", "ADMIN");
        when(userInfoMapper.selectByUsername("admin")).thenReturn(user);
        when(passwordEncoder.matches("pass123", "encoded-pass")).thenReturn(true);
        when(jwtTokenProvider.generateToken(anyString(), anyLong(), anyString())).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(anyString(), anyLong(), anyString())).thenReturn("refresh-token");
        when(rolePermissionMapper.selectList(any())).thenReturn(List.of(
                perm("ADMIN", "content:all", "内容管理", 1),
                perm("ADMIN", "menu:dashboard", "工作台", 1)
        ));

        LoginResponse resp = authService.login(req("admin", "pass123"));

        assertEquals("access-token", resp.getToken());
        assertEquals("refresh-token", resp.getRefreshToken());
        assertEquals("admin", resp.getUsername());
        assertEquals("ADMIN", resp.getRole());
        assertEquals(2, resp.getPermissions().size());
        // 登录成功应清除失败计数
        verify(redisTemplate).delete("geo:login:attempt:admin");
        // refresh token 写入 Redis，7 天
        verify(valueOps).set(eq("geo:token:refresh:refresh-token"), eq("admin"), eq(7L), eq(TimeUnit.DAYS));
    }

    // ---------- 用户不存在 ----------

    @Test
    void loginUserNotFoundThrowsUnauthorizedAndRecordsFailure() {
        when(userInfoMapper.selectByUsername("nobody")).thenReturn(null);

        assertThrows(UnauthorizedException.class, () -> authService.login(req("nobody", "pass")));

        // 记录失败计数 + 过期
        verify(valueOps).increment("geo:login:attempt:nobody");
        verify(redisTemplate).expire(eq("geo:login:attempt:nobody"), eq(30L), eq(TimeUnit.MINUTES));
    }

    // ---------- 密码错误 ----------

    @Test
    void loginWrongPasswordThrowsUnauthorizedAndRecordsFailure() {
        when(userInfoMapper.selectByUsername("admin")).thenReturn(activeUser("admin", "ADMIN"));
        when(passwordEncoder.matches("wrong", "encoded-pass")).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> authService.login(req("admin", "wrong")));

        verify(valueOps).increment("geo:login:attempt:admin");
        verify(redisTemplate, never()).delete(anyString());
    }

    // ---------- 账号锁定 ----------

    @Test
    void loginAccountLockedThrowsBusinessExceptionWithTtl() {
        when(valueOps.get("geo:login:attempt:admin")).thenReturn(5);
        when(redisTemplate.getExpire("geo:login:attempt:admin", TimeUnit.MINUTES)).thenReturn(12L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(req("admin", "pass")));
        assertTrue(ex.getMessage().contains("12"));

        // 锁定时不应查库或比对密码
        verify(userInfoMapper, never()).selectByUsername(any());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    // ---------- 账号禁用 ----------

    @Test
    void loginDisabledAccountThrowsBusinessException() {
        UserInfo disabled = activeUser("admin", "ADMIN");
        disabled.setStatus(0);
        when(userInfoMapper.selectByUsername("admin")).thenReturn(disabled);
        when(passwordEncoder.matches("pass", "encoded-pass")).thenReturn(true);

        assertThrows(BusinessException.class, () -> authService.login(req("admin", "pass")));
    }

    // ---------- 登出 ----------

    @Test
    void logoutAddsTokenToBlacklist() {
        authService.logout("admin", "some-token");
        verify(tokenBlacklistService).blacklist("some-token");
    }

    // ---------- refreshToken 成功 ----------

    @Test
    void refreshTokenSuccessRotatesTokens() {
        when(jwtTokenProvider.validateRefreshToken("old-refresh")).thenReturn(true);
        when(valueOps.get("geo:token:refresh:old-refresh")).thenReturn("admin");
        when(userInfoMapper.selectByUsername("admin")).thenReturn(activeUser("admin", "ADMIN"));
        when(jwtTokenProvider.generateToken(anyString(), anyLong(), anyString())).thenReturn("new-access");
        when(jwtTokenProvider.generateRefreshToken(anyString(), anyLong(), anyString())).thenReturn("new-refresh");
        when(rolePermissionMapper.selectList(any())).thenReturn(List.of());

        LoginResponse resp = authService.refreshToken("old-refresh");

        assertEquals("new-access", resp.getToken());
        assertEquals("new-refresh", resp.getRefreshToken());
        // 旧 refresh token 被删除
        verify(redisTemplate).delete("geo:token:refresh:old-refresh");
        // 新 refresh token 写入
        verify(valueOps).set(eq("geo:token:refresh:new-refresh"), eq("admin"), eq(7L), eq(TimeUnit.DAYS));
    }

    // ---------- refreshToken 验签失败 ----------

    @Test
    void refreshTokenInvalidSignatureThrowsUnauthorized() {
        when(jwtTokenProvider.validateRefreshToken("bad")).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> authService.refreshToken("bad"));
        verify(valueOps, never()).get(anyString());
    }

    // ---------- refreshToken Redis 不存在 ----------

    @Test
    void refreshTokenNotInRedisThrowsUnauthorized() {
        when(jwtTokenProvider.validateRefreshToken("old-refresh")).thenReturn(true);
        when(valueOps.get("geo:token:refresh:old-refresh")).thenReturn(null);

        assertThrows(UnauthorizedException.class, () -> authService.refreshToken("old-refresh"));
    }

    // ---------- refreshToken null ----------

    @Test
    void refreshTokenNullThrowsUnauthorized() {
        assertThrows(UnauthorizedException.class, () -> authService.refreshToken(null));
    }

    // ---------- getCurrentUser ----------

    @Test
    void getCurrentUserReturnsUserInfo() {
        UserInfo user = activeUser("admin", "ADMIN");
        when(userInfoMapper.selectByUsername("admin")).thenReturn(user);

        assertEquals(user, authService.getCurrentUser("admin"));
    }

    // ---------- getUserInfoWithPermissions 成功 ----------

    @Test
    void getUserInfoWithPermissionsReturnsUserMenusAndPermissions() {
        UserInfo user = activeUser("admin", "ADMIN");
        when(userInfoMapper.selectByUsername("admin")).thenReturn(user);
        when(rolePermissionMapper.selectList(any())).thenReturn(List.of(
                perm("ADMIN", "content:all", "内容管理", 1),
                perm("ADMIN", "menu:dashboard", "工作台", 1),
                perm("ADMIN", "menu:content", "创作中心", 1)
                // status=0 的禁用项由查询条件过滤，mock 只返回启用项
        ));

        Map<String, Object> result = authService.getUserInfoWithPermissions("admin");

        assertEquals(user, result.get("user"));
        List<String> perms = (List<String>) result.get("permissions");
        assertEquals(3, perms.size()); // status=0 被过滤
        List<Map<String, Object>> menus = (List<Map<String, Object>>) result.get("menus");
        assertEquals(2, menus.size()); // 仅 menu: 前缀且 status=1
        assertEquals("/dashboard", menus.get(0).get("path"));
    }

    // ---------- getUserInfoWithPermissions 用户不存在 ----------

    @Test
    void getUserInfoWithPermissionsUserNotFoundThrowsUnauthorized() {
        when(userInfoMapper.selectByUsername("nobody")).thenReturn(null);
        assertThrows(UnauthorizedException.class, () -> authService.getUserInfoWithPermissions("nobody"));
    }

    // ---------- getDynamicMenus 过滤非 menu 权限 ----------

    @Test
    void getDynamicMenusFiltersNonMenuPermissions() {
        when(rolePermissionMapper.selectList(any())).thenReturn(List.of(
                perm("USER", "menu:customer", "客户", 1),
                perm("USER", "content:view", "查看内容", 1), // 非 menu 前缀
                perm("USER", "menu:monitor", "监测", 1)
        ));

        List<Map<String, Object>> menus = authService.getDynamicMenus("USER");
        assertEquals(2, menus.size());
        assertEquals("客户", menus.get(0).get("name"));
        assertEquals("/customer", menus.get(0).get("path"));
    }

    // ---------- getPermissionsByRole ----------

    @Test
    void getPermissionsByRoleReturnsAllActivePermissions() {
        when(rolePermissionMapper.selectList(any())).thenReturn(List.of(
                perm("USER", "content:view", "查看", 1),
                perm("USER", "content:edit", "编辑", 1)
                // status=0 的删除权限由查询条件过滤
        ));

        List<String> perms = authService.getPermissionsByRole("USER");
        assertEquals(2, perms.size());
        assertTrue(perms.contains("content:view"));
        assertTrue(perms.contains("content:edit"));
    }

    // ---------- isTokenBlacklisted 透传 ----------

    @Test
    void isTokenBlacklistedDelegatesToService() {
        when(tokenBlacklistService.isBlacklisted("tok")).thenReturn(true);
        assertTrue(authService.isTokenBlacklisted("tok"));
    }
}
