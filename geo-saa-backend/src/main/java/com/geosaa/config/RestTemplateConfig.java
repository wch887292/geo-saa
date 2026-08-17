package com.geosaa.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 外部 HTTP 调用客户端配置。
 *
 * <p>修复前 {@code OpenAiAdapter} 里直接 {@code new RestTemplate()}，
 * 默认无连接超时、无读超时。一旦上游 AI 服务卡住，请求线程会被无限期占用，
 * 在诊断/批量生成这类高并发场景下足以拖垮整个 Tomcat 线程池。
 *
 * <p>Spring Boot 4 移除了 {@code RestTemplateBuilder}，此处改为手动构建
 * {@link RestTemplate} 并设置连接/读超时，行为与升级前保持一致。
 */
@Configuration
public class RestTemplateConfig {

    @Value("${ai.request.connect-timeout-ms:5000}")
    private long connectTimeoutMs;

    @Value("${ai.request.read-timeout-ms:60000}")
    private long readTimeoutMs;

    @Bean("aiRestTemplate")
    public RestTemplate aiRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return new RestTemplate(factory);
    }
}
