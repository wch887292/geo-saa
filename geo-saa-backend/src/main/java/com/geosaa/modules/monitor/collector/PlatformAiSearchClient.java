package com.geosaa.modules.monitor.collector;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 通用多平台 AI 搜索客户端。
 *
 * <p>每个实例对应一个大模型平台配置（platformId + apiKey + apiUrl + model），
 * 实现 {@link AiSearchEngineClient} 接口，通过 OpenAI 兼容的 /chat/completions
 * 接口探测品牌提及情况。支持国内主流平台（DeepSeek/通义/豆包/智谱/Kimi/
 * 讯飞/文心/混元/360）及国际平台（OpenAI/Perplexity）。
 *
 * <p>注意：Claude 和 Gemini 使用非标准 API 格式，暂通过 OpenAI 兼容网关调用。
 */
@Slf4j
public class PlatformAiSearchClient implements AiSearchEngineClient {

    private final String platformId;
    private final String platformName;
    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final int timeoutSeconds;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PlatformAiSearchClient(String platformId, String platformName, String apiUrl,
                                   String apiKey, String model, int timeoutSeconds) {
        this.platformId = platformId;
        this.platformName = platformName;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutSeconds = Math.max(5, timeoutSeconds);
    }

    @Override
    public String engineName() {
        return platformId;
    }

    public String getPlatformName() {
        return platformName;
    }

    @Override
    public String probe(String query) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("平台 " + platformName + " 未配置 API Key");
        }
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new IllegalStateException("平台 " + platformName + " 未配置 API URL");
        }

        String body = objectMapper.writeValueAsString(Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", query)),
                "temperature", 0.3));

        int timeoutMs = timeoutSeconds * 1000;
        try (HttpResponse resp = HttpRequest.post(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .setConnectionTimeout(timeoutMs)
                .setReadTimeout(timeoutMs)
                .body(body.getBytes(StandardCharsets.UTF_8))
                .execute()) {

            if (!resp.isOk()) {
                String errBody = resp.body();
                throw new IllegalStateException(platformName + " HTTP " + resp.getStatus() + ": "
                        + (errBody == null ? "" : errBody.substring(0, Math.min(200, errBody.length()))));
            }

            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new IllegalStateException(platformName + " 响应缺少 choices[0].message.content");
            }
            String answer = content.asText();
            log.debug("平台探测成功 platform={}, queryLength={}, answerLength={}",
                    platformId, query.length(), answer.length());
            return answer;
        }
    }
}
