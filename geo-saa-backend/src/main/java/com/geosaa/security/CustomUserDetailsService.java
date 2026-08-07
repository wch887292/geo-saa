package com.geosaa.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geosaa.modules.auth.entity.RolePermission;
import com.geosaa.modules.auth.entity.UserInfo;
import com.geosaa.modules.auth.mapper.RolePermissionMapper;
import com.geosaa.modules.auth.mapper.UserInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserInfoMapper userInfoMapper;
    private final RolePermissionMapper rolePermissionMapper;

    /**
     * 加载用户及其全部权限。
     *
     * <p>返回的 authorities 包含两类：
     * <ul>
     *   <li>{@code ROLE_XXX} —— 供 {@code hasRole('XXX')} 使用</li>
     *   <li>业务权限串，如 {@code system:all} —— 供 {@code hasAuthority('system:all')} 使用</li>
     * </ul>
     * 修复前过滤器写死了空权限列表，导致所有方法级鉴权形同虚设。
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserInfo userInfo = userInfoMapper.selectByUsername(username);
        if (userInfo == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        boolean enabled = userInfo.getStatus() == null || userInfo.getStatus() != 0;

        return new LoginUser(
                userInfo.getId(),
                userInfo.getUsername(),
                userInfo.getPassword() != null ? userInfo.getPassword() : "",
                userInfo.getNickname(),
                userInfo.getRole(),
                enabled,
                buildAuthorities(userInfo.getRole())
        );
    }

    private List<GrantedAuthority> buildAuthorities(String role) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (role == null || role.isBlank()) {
            return authorities;
        }

        // 角色统一大写，避免数据库中 admin / ADMIN 混写导致 hasRole 判断失败
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT)));

        List<RolePermission> permissions = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>()
                        .eq(RolePermission::getRole, role)
                        .eq(RolePermission::getStatus, 1)
        );
        permissions.stream()
                .map(RolePermission::getPermission)
                .filter(p -> p != null && !p.isBlank())
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);

        return authorities;
    }
}
