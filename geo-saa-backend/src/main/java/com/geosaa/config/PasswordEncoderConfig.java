package com.geosaa.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器独立配置。
 *
 * <p>从 SecurityConfig 中拆出，避免 SecurityConfig 与依赖 PasswordEncoder 的
 * 业务 Service 之间形成循环依赖，从而可以关闭 allow-circular-references。
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
