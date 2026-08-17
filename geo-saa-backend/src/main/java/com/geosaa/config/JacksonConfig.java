package com.geosaa.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4 将 Jackson 升级到 3.x：
 * <ul>
 *   <li>GAV 由 {@code com.fasterxml.jackson} 变为 {@code tools.jackson}；</li>
 *   <li>包名由 {@code com.fasterxml.jackson.databind} 变为 {@code tools.jackson.databind}；</li>
 *   <li>其自动配置仅注册 {@code tools.jackson.databind.ObjectMapper} 类型的 Bean。</li>
 * </ul>
 * 项目既有代码（SecurityConfig、各 Service 等共十余处）仍基于 Jackson 2 API 注入
 * {@code com.fasterxml.jackson.databind.ObjectMapper}，因此此处显式提供一个 Jackson 2 的
 * ObjectMapper Bean，保证所有既有注入点不受影响。REST 响应序列化由 SB4 自带的 Jackson 3 负责，
 * 二者类型不同、互不冲突（暂共存，后续可整体迁移到 Jackson 3）。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
