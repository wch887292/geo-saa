package com.geosaa.common.metrics;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * HTTP 请求指标拦截器（O3 可观测性）。
 *
 * <p>在请求完成后统计 {@code geo_http_requests_total{path,status,method}}，
 * 与 Spring Boot 内置的 {@code http_server_requests_seconds} 互补：
 * 内置指标侧重耗时分布，本指标侧重按业务路径聚合的调用量与错误率，
 * 且路径经过 {@link BusinessMetrics#sanitize(String)} 降噪（数字 ID → {id}），
 * 避免 Prometheus 指标基数爆炸。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsInterceptor implements HandlerInterceptor {

    private final BusinessMetrics businessMetrics;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            String path = request.getRequestURI();
            String status = String.valueOf(response.getStatus());
            String method = request.getMethod();
            businessMetrics.incrementHttpRequest(path, status, method);
        } catch (Exception e) {
            // 指标统计失败绝不影响业务请求
            log.debug("HTTP 指标统计失败: {}", e.getMessage());
        }
    }
}
