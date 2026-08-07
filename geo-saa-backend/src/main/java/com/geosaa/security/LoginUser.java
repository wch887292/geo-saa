package com.geosaa.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/**
 * 登录用户主体。
 *
 * <p>在 Spring Security 标准 {@link User} 的基础上携带业务字段（用户 ID、昵称、角色），
 * 这样 Controller 层可以通过 {@link SecurityUtils#getCurrentUserId()} 拿到真实用户 ID，
 * 不再需要写死 {@code 1L}。
 */
@Getter
public class LoginUser extends User {

    private final Long userId;
    private final String nickname;
    private final String role;

    public LoginUser(Long userId,
                     String username,
                     String password,
                     String nickname,
                     String role,
                     boolean enabled,
                     Collection<? extends GrantedAuthority> authorities) {
        super(username, password, enabled, true, true, true, authorities);
        this.userId = userId;
        this.nickname = nickname;
        this.role = role;
    }
}
