package com.geosaa.modules.auth.service;

import com.geosaa.modules.auth.dto.LoginRequest;
import com.geosaa.modules.auth.entity.UserInfo;
import com.geosaa.modules.auth.mapper.RolePermissionMapper;
import com.geosaa.modules.auth.mapper.UserInfoMapper;
import com.geosaa.security.JwtTokenProvider;
import com.geosaa.security.TokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录限流回归测试：IP 维度补充。
 *
 * <p>原实现只按用户名计数，存在撞库（遍历大量用户名，每账号不超阈值）与
 * 锁号DoS（5 次错误即可锁死任意已知账号 30 分钟）两个缺口。
 * 修复后叠加 IP 维度滑动窗口（15 分钟 20 次）。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceRateLimitTest {

    @Mock
    private UserInfoMapper userInfoMapper;
    @Mock
    private RolePermissionMapper rolePermissionMapper;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOps;
    @Mock
    private TokenBlacklistService tokenBlacklistService;

    private AuthService service;

    private static final String IP = "203.0.113.9";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new AuthService(userInfoMapper, rolePermissionMapper, jwtTokenProvider,
                passwordEncoder, redisTemplate, tokenBlacklistService);
    }

    private LoginRequest req(String username) {
        LoginRequest r = new LoginRequest();
        r.setUsername(username);
        r.setPassword("whatever");
        return r;
    }

    @Test
    @DisplayName("IP 失败次数超阈值后被拦截，即使账号名各不相同（防撞库）")
    void shouldThrottleWhenIpExceedsThreshold() {
        // 账号阈值(5)未触发，但 IP 阈值(20)已触发
        when(valueOps.get(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            if (key.startsWith("geo:login:ip:")) {
                return 20;              // IP 维度已超阈值
            }
            return 0;                   // 账号维度远未到 5
        });
        when(redisTemplate.getExpire(anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(15L);

        // 关键：用户名每次都不一样 —— 这正是撞库的形态，
        // 旧实现下每个用户名只有 1 次失败，永远不会触发任何限制
        var ex = assertThrows(RuntimeException.class, () -> service.login(req("victim-" + IP), IP));
        assertTrue(ex.getMessage().contains("过于频繁"),
                "应命中 IP 限流提示，实际: " + ex.getMessage());

        // 未触碰数据库 —— 说明在限流层就被拦下
        verify(userInfoMapper, never()).selectByUsername(anyString());
    }

    @Test
    @DisplayName("登录失败时同时累加账号与 IP 两个维度")
    void shouldRecordBothDimensionsOnFailure() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(userInfoMapper.selectByUsername(anyString())).thenReturn(null);

        assertThrows(RuntimeException.class, () -> service.login(req("ghost"), IP));

        verify(valueOps, times(2)).increment(anyString()); // 账号 1 次 + IP 1 次
        verify(redisTemplate, times(2)).expire(anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("账号维度仍独立生效：单个账号连续失败 5 次即锁定")
    void shouldStillLockPerAccount() {
        when(valueOps.get(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            if (key.startsWith("geo:login:attempt:")) {
                return 5;                // 账号维度已锁
            }
            return 0;
        });
        when(redisTemplate.getExpire(anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(30L);

        var ex = assertThrows(RuntimeException.class, () -> service.login(req("admin"), IP));
        assertTrue(ex.getMessage().contains("锁定"),
                "应命中账号锁定提示，实际: " + ex.getMessage());
        verify(userInfoMapper, never()).selectByUsername(anyString());
    }

    @Test
    @DisplayName("TTL 为 -1/-2 等异常值时不把负数展示给用户")
    void shouldNotLeakNegativeTtl() {
        when(valueOps.get(anyString())).thenReturn(5);
        // Redis 返回 -1（无过期）/ -2（key 不存在）
        when(redisTemplate.getExpire(anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(-1L);

        var ex = assertThrows(RuntimeException.class, () -> service.login(req("admin"), IP));
        assertTrue(!ex.getMessage().contains("-1"),
                "不应把负数 TTL 展示给用户，实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("clientIp 为空时退化为仅按账号限流，保持向后兼容")
    void shouldWorkWithoutClientIp() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(userInfoMapper.selectByUsername(anyString())).thenReturn(null);

        assertThrows(RuntimeException.class, () -> service.login(req("ghost"), null));

        // 仅累加账号维度，不写 IP 维度
        verify(valueOps, times(1)).increment(anyString());
    }
}