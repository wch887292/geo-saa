package com.geosaa.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 业务指标中心（O3 可观测性）。
 *
 * <p>统一管理 GEO-SaaS 的自定义 Prometheus 指标，避免各模块直接注入 MeterRegistry
 * 导致指标名/标签分散。所有指标以 {@code geo_} 前缀命名，与 Spring Boot 内置指标
 * （{@code http_server_requests_*}、{@code jvm_*}、{@code process_*}）区分。
 *
 * <p>指标清单：
 * <ul>
 *   <li>{@code geo_http_requests_total{path,status,method}} — 接口调用量与错误率</li>
 *   <li>{@code geo_ai_invoke_duration_seconds{adapter}} — AI 调用耗时/P99</li>
 *   <li>{@code geo_collector_success_total{brand,engine}} — 采集器成功率（失败绝不写库的可观测化）</li>
 *   <li>{@code geo_collector_failure_total{brand,engine}} — 采集器失败数</li>
 *   <li>{@code geo_mq_message_total{queue,result}} — MQ 消费成功/失败数</li>
 * </ul>
 */
@Slf4j
@Component
public class BusinessMetrics {

    private final MeterRegistry registry;

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
        // 启动时注册常用指标，避免首次调用时才创建导致 Prometheus 抓取间隔内缺失
        Counter.builder("geo_http_requests_total")
                .description("GEO-SaaS HTTP 请求总数")
                .tag("path", "unknown")
                .tag("status", "unknown")
                .tag("method", "unknown")
                .register(registry);
        Timer.builder("geo_ai_invoke_duration_seconds")
                .description("GEO-SaaS AI 调用耗时")
                .tag("adapter", "openai")
                .register(registry);
        log.info("业务指标已注册: geo_http_requests_total, geo_ai_invoke_duration_seconds, " +
                "geo_collector_success_total, geo_collector_failure_total, geo_mq_message_total");
    }

    // ---------- HTTP 请求 ----------

    public void incrementHttpRequest(String path, String status, String method) {
        Counter.builder("geo_http_requests_total")
                .description("GEO-SaaS HTTP 请求总数")
                .tag("path", sanitize(path))
                .tag("status", status)
                .tag("method", method)
                .register(registry)
                .increment();
    }

    // ---------- AI 调用 ----------

    public Timer.Sample startAiInvoke(String adapter) {
        return Timer.start(registry);
    }

    public void stopAiInvoke(Timer.Sample sample, String adapter) {
        sample.stop(Timer.builder("geo_ai_invoke_duration_seconds")
                .description("GEO-SaaS AI 调用耗时")
                .tag("adapter", adapter)
                .register(registry));
    }

    /** 便捷方法：记录一次 AI 调用耗时（毫秒） */
    public void recordAiInvokeDuration(String adapter, long durationMs) {
        Timer.builder("geo_ai_invoke_duration_seconds")
                .description("GEO-SaaS AI 调用耗时")
                .tag("adapter", adapter)
                .register(registry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    // ---------- 采集器 ----------

    public void incrementCollectorSuccess(String brand, String engine) {
        Counter.builder("geo_collector_success_total")
                .description("GEO 采集器成功数")
                .tag("brand", sanitize(brand))
                .tag("engine", sanitize(engine))
                .register(registry)
                .increment();
    }

    public void incrementCollectorFailure(String brand, String engine) {
        Counter.builder("geo_collector_failure_total")
                .description("GEO 采集器失败数")
                .tag("brand", sanitize(brand))
                .tag("engine", sanitize(engine))
                .register(registry)
                .increment();
    }

    // ---------- MQ 消息 ----------

    public void incrementMqMessage(String queue, String result) {
        Counter.builder("geo_mq_message_total")
                .description("GEO-SaaS MQ 消息处理数")
                .tag("queue", sanitize(queue))
                .tag("result", result)
                .register(registry)
                .increment();
    }

    /** 路径标签降噪：将路径中的数字 ID 替换为 {id}，避免指标基数爆炸 */
    private String sanitize(String value) {
        if (value == null) {
            return "unknown";
        }
        // 将 /api/v1/content/123 中的数字段替换为 {id}
        return value.replaceAll("/\\d+", "/{id}");
    }
}
