package com.geosaa.security;

import com.geosaa.modules.auth.entity.UserInfo;
import com.geosaa.modules.auth.mapper.UserInfoMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DefaultPasswordGuard} 回归测试。
 *
 * <p>覆盖三种情形：仍用默认口令（fail 档必须拒绝启动）、已改密（放行）、
 * 空散列（视为更危险，同样命中）。同时验证 BCrypt 重新加盐后仍能被识别为默认口令，
 * 避免日后 init.sql 重新生成散列导致守卫静默失效。
 */
class DefaultPasswordGuardTest {

    private final UserInfoMapper userInfoMapper = Mockito.mock(UserInfoMapper.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    private DefaultPasswordGuard guard(String action) {
        DefaultPasswordGuard g = new DefaultPasswordGuard(userInfoMapper, encoder);
        // 模拟 @Value 注入
        org.springframework.test.util.ReflectionTestUtils.setField(
                g, "action", action);
        return g;
    }

    private void givenAdminWithHash(String hash) {
        UserInfo admin = new UserInfo();
        admin.setUsername(DefaultPasswordGuard.ADMIN_USERNAME);
        admin.setPassword(hash);
        admin.setRole("ADMIN");
        Mockito.when(userInfoMapper.selectByUsername(DefaultPasswordGuard.ADMIN_USERNAME))
                .thenReturn(admin);
    }

    @Test
    @DisplayName("1. prod 档案(fail) + 仍是默认口令散列 → 拒绝启动")
    void shouldFailFastWhenDefaultPasswordInProd() {
        givenAdminWithHash(DefaultPasswordGuard.KNOWN_DEFAULT_BCRYPT_HASH);

        DefaultPasswordGuard guard = guard("fail");
        assertThrows(IllegalStateException.class, () -> guard.run(null),
                "生产环境仍在使用默认口令时必须拒绝启动");
    }

    @Test
    @DisplayName("2. dev 档案(warn) + 仍是默认口令散列 → 仅告警不阻断")
    void shouldOnlyWarnInDev() {
        givenAdminWithHash(DefaultPasswordGuard.KNOWN_DEFAULT_BCRYPT_HASH);

        DefaultPasswordGuard guard = guard("warn");
        assertDoesNotThrow(() -> guard.run(null),
                "开发档案下不应阻断启动，否则每次拉取代码后本地开发都要先改密");
    }

    @Test
    @DisplayName("3. 管理员已改密 → 正常放行")
    void shouldPassWhenPasswordChanged() {
        String changed = encoder.encode("MyNewStrongPassw0rd!");
        givenAdminWithHash(changed);

        DefaultPasswordGuard guard = guard("fail");
        assertDoesNotThrow(() -> guard.run(null), "已自定义口令的管理员应正常启动");
        assertFalse(guard.isUsingDefaultPassword(changed));
    }

    @Test
    @DisplayName("4. 默认口令被 BCrypt 重新加盐后仍能识别（防止守卫静默失效）")
    void shouldDetectResaltedDefaultPassword() {
        String resalted = encoder.encode(DefaultPasswordGuard.DEFAULT_PASSWORD_PLAINTEXT);
        // 确认这确实是重新加盐后的不同散列
        org.junit.jupiter.api.Assertions.assertNotEquals(
                DefaultPasswordGuard.KNOWN_DEFAULT_BCRYPT_HASH, resalted);
        givenAdminWithHash(resalted);

        DefaultPasswordGuard guard = guard("fail");
        assertTrue(guard.isUsingDefaultPassword(resalted),
                "字面量比对失败后应回退到 matches 兜底，否则换盐即绕过守卫");
        assertThrows(IllegalStateException.class, () -> guard.run(null));
    }

    @Test
    @DisplayName("5. 空口令散列视为命中默认口令（比默认口令更危险）")
    void shouldTreatBlankHashAsDefault() {
        DefaultPasswordGuard guard = guard("fail");
        assertTrue(guard.isUsingDefaultPassword(null));
        assertTrue(guard.isUsingDefaultPassword("   "));
    }

    @Test
    @DisplayName("6. 表中无 admin 记录 → 跳过检查，不误伤空库场景")
    void shouldSkipWhenAdminMissing() {
        Mockito.when(userInfoMapper.selectByUsername(DefaultPasswordGuard.ADMIN_USERNAME))
                .thenReturn(null);

        DefaultPasswordGuard guard = guard("fail");
        assertDoesNotThrow(() -> guard.run(null), "空库/未初始化场景不应被守卫阻断");
    }

    @Test
    @DisplayName("7. 显式 disabled → 放行但保留风险告警")
    void shouldAllowWhenExplicitlyDisabled() {
        givenAdminWithHash(DefaultPasswordGuard.KNOWN_DEFAULT_BCRYPT_HASH);

        DefaultPasswordGuard guard = guard("disabled");
        assertDoesNotThrow(() -> guard.run(null), "用户显式承担风险时应允许启动");
    }

    @Test
    @DisplayName("8. 非法散列格式不抛异常（交由登录时报错）")
    void shouldTolerateMalformedHash() {
        DefaultPasswordGuard guard = guard("fail");
        assertFalse(guard.isUsingDefaultPassword("not-a-bcrypt-hash"),
                "无法解析的散列不应被误判为默认口令");
    }
}