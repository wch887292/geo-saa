package com.geosaa.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * 跨域配置。
 *
 * <p>修复前使用 {@code addAllowedOriginPattern("*")} + {@code setAllowCredentials(true)}，
 * 等于允许任意站点携带 Cookie/凭证访问接口，是典型的 CSRF / 凭证泄露面。
 * 现在改为白名单驱动，域名从配置读取，生产环境必须显式指定。
 *
 * <p>同时暴露 {@link CorsConfigurationSource} Bean：{@code SecurityConfig} 中通过
 * {@code .cors(Customizer.withDefaults())} 将其挂进安全过滤器链，否则预检（OPTIONS）
 * 请求会先被安全链以 401 拒绝，浏览器拿不到 CORS 响应头，跨域调用永远失败。
 */
@Slf4j
@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    @Value("${cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Value("${cors.max-age:3600}")
    private long maxAge;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (origins.isEmpty()) {
            throw new IllegalStateException("cors.allowed-origins 未配置，请通过 CORS_ALLOWED_ORIGINS 指定前端域名");
        }
        if (origins.contains("*") && allowCredentials) {
            throw new IllegalStateException(
                    "cors.allowed-origins 不能在 allow-credentials=true 时使用通配符 *，请显式列出前端域名");
        }

        // 使用 OriginPattern 以便支持 http://*.example.com 这类受控通配
        origins.forEach(config::addAllowedOriginPattern);
        config.setAllowCredentials(allowCredentials);
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setMaxAge(maxAge);

        log.info("CORS 允许来源: {}", origins);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public CorsFilter corsFilter(CorsConfigurationSource corsConfigurationSource) {
        return new CorsFilter(corsConfigurationSource);
    }
}
