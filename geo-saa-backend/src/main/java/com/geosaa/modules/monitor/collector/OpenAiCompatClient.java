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
 * OpenAI 兼容端点采集适配器。
 *
 * <p>用于无法直连 AI 搜索服务的场景：通过可配置的 OpenAI 兼容网关
 * （通义千问 / 豆包 / DeepSeek 等）以问答方式探测品牌提及情况，
 * 作为真实数据源的补充链路。仅当
 * {@code app.geo.collector.openai-compat.enabled=true} 且配置了 API Key/URL 时注册。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.geo.collector.openai-compat", name = "enabled", havingValue = "true")
public class OpenAiCompatClient implements AiSearchEngineClient {

    private final GeoCollectorProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String engineName() {
        return "openai-compat";
    }

    @Override
    public String probe(String query) throws Exception {
        GeoCollectorProperties.OpenaiCompat cfg = properties.getOpenaiCompat();
        if (cfg.getApiUrl() == null || cfg.getApiUrl().isBlank()) {
            throw new IllegalStateException("openai-compat.api-url 未配置");
        }
        String model = cfg.getModel() != null && !cfg.getModel().isBlank() ? cfg.getModel() : "qwen-plus";
        String body = objectMapper.writeValueAsString(Map.of(
                "model", model,
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
                throw new IllegalStateException("OpenAI-compat HTTP " + resp.getStatus() + ": "
                        + resp.body().substring(0, Math.min(200, resp.body().length())));
            }
            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new IllegalStateException("OpenAI-compat 响应缺少 choices[0].message.content");
            }
            return content.asText();
        }
    }
}
