package com.geosaa.modules.monitor.collector;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Perplexity 官方 API 采集适配器。
 *
 * <p>调用 {@code POST /chat/completions}（默认模型 sonar），
 * 返回 {@code choices[0].message.content} 作为回答文本。
 * 仅在 {@code app.geo.collector.perplexity.enabled=true} 且配置了 API Key 时注册。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.geo.collector.perplexity", name = "enabled", havingValue = "true")
public class PerplexityClient implements AiSearchEngineClient {

    private final GeoCollectorProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String engineName() {
        return "perplexity";
    }

    @Override
    public String probe(String query) throws Exception {
        GeoCollectorProperties.Perplexity cfg = properties.getPerplexity();
        String body = objectMapper.writeValueAsString(Map.of(
                "model", cfg.getModel(),
                "messages", List.of(Map.of("role", "user", "content", query))));

        int timeoutMs = Math.max(5, properties.getTimeoutSeconds()) * 1000;
        try (HttpResponse resp = HttpRequest.post(cfg.getApiUrl())
                .header("Authorization", "Bearer " + cfg.getApiKey())
                .header("Content-Type", "application/json")
                .setConnectionTimeout(timeoutMs)
                .setReadTimeout(timeoutMs)
                .body(body.getBytes(StandardCharsets.UTF_8))
                .execute()) {

            if (!resp.isOk()) {
                throw new IllegalStateException("Perplexity HTTP " + resp.getStatus() + ": "
                        + resp.body().substring(0, Math.min(200, resp.body().length())));
            }
            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new IllegalStateException("Perplexity 响应缺少 choices[0].message.content");
            }
            return content.asText();
        }
    }
}
