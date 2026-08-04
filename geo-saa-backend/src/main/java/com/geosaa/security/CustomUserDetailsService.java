package com.geosaa.security;

import com.geosaa.modules.auth.entity.UserInfo;
import com.geosaa.modules.auth.mapper.UserInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserInfoMapper userInfoMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserInfo userInfo = userInfoMapper.selectByUsername(username);
        if (userInfo == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        return new User(userInfo.getUsername(), userInfo.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + userInfo.getRole())));
    }
}