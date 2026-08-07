package com.geosaa.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Component
public class OpenAiAdapter implements AiAdapter {

    @Value("${ai.openai.api-key:}")
    private String apiKey;

    @Value("${ai.openai.api-url:https://api.openai.com/v1}")
    private String apiUrl;

    @Value("${ai.openai.model:gpt-4}")
    private String model;

    @Value("${ai.simulation.enabled:true}")
    private boolean simulationEnabled;

    /** 带超时配置的 RestTemplate，见 {@link com.geosaa.config.RestTemplateConfig} */
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiAdapter(@Qualifier("aiRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String getType() {
        return "openai";
    }

    @Override
    public String analyzeIntent(String brandName, String keywords, String platform) {
        if (simulationEnabled || apiKey == null || apiKey.isEmpty()) {
            return simulateAnalyzeIntent(brandName, keywords, platform);
        }
        String prompt = String.format(
            "你是一个GEO(生成式引擎优化)分析专家。请分析品牌「%s」在关键词「%s」下在AI搜索平台「%s」中的可见度表现。\n" +
            "请按以下格式输出JSON：\n" +
            "{\"summary\":\"分析摘要\",\"mentionCount\":\"提及次数\",\"mentionRate\":\"提及率百分比\",\"questions\":[\"用户可能问的相关问题1\",\"问题2\"],\"gap\":\"流量缺口分析\"}",
            brandName, keywords, platform
        );
        return callAiApi(prompt);
    }

    @Override
    public String generateContent(String prompt, String contentType, int wordCount) {
        if (simulationEnabled || apiKey == null || apiKey.isEmpty()) {
            return simulateGenerateContent(prompt, contentType, wordCount);
        }
        String systemPrompt = "你是一个GEO优化内容创作专家。请生成" + contentType + "类型的内容，约" + wordCount + "字。\n" +
            "要求：\n" +
            "1. 内容结构化，包含标题、正文、关键词\n" +
            "2. 适配大模型检索偏好，信息密度高\n" +
            "3. 输出JSON格式：{\"title\":\"标题\",\"content\":\"正文\",\"keywords\":[\"关键词1\",\"关键词2\"]}";
        return callAiApi(systemPrompt + "\n\n" + prompt);
    }

    @Override
    public VisibilityResult checkVisibility(String brandName, String platform) {
        if (simulationEnabled || apiKey == null || apiKey.isEmpty()) {
            return simulateCheckVisibility(brandName, platform);
        }
        // 真实模式下调用 API 获取可见度数据
        String prompt = String.format(
            "请分析品牌「%s」在AI搜索平台「%s」中的可见度，返回JSON格式：{\"mentionCount\":数字,\"mentionRate\":数字,\"firstRecommendRate\":数字,\"relatedQuestions\":[\"问题1\"],\"competitorMentions\":[\"竞品1\"],\"score\":数字}",
            brandName, platform
        );
        String response = callAiApi(prompt);
        try {
            JsonNode root = objectMapper.readTree(response);
            if (root.has("choices")) {
                String content = root.get("choices").get(0).get("message").get("content").asText();
                return objectMapper.readValue(content, VisibilityResult.class);
            }
        } catch (Exception e) {
            log.error("解析AI可见度响应失败", e);
        }
        return simulateCheckVisibility(brandName, platform);
    }

    /**
     * 模拟意图分析结果
     */
    private String simulateAnalyzeIntent(String brandName, String keywords, String platform) {
        log.info("模拟模式：分析品牌[{}]在[{}]平台的可见度，关键词[{}]", brandName, platform, keywords);
        return String.format(
            "{\"summary\":\"品牌「%s」在「%s」平台中可见度中等，在关键词「%s」下出现频率一般。建议加强GEO优化，提升内容密度和结构化程度。\",\"mentionCount\":%d,\"mentionRate\":%.1f,\"questions\":[\"%s怎么样？\",\"%s价格是多少？\",\"%s和竞品比哪个好？\",\"%s有哪些功能？\",\"%s适合什么场景？\"],\"gap\":\"流量缺口分析：在长尾关键词覆盖不足，建议增加场景化内容\"}",
            brandName, platform, keywords,
            new Random().nextInt(80) + 10,
            Math.random() * 60 + 20,
            brandName, brandName, brandName, brandName, brandName
        );
    }

    /**
     * 模拟内容生成结果
     */
    private String simulateGenerateContent(String prompt, String contentType, int wordCount) {
        log.info("模拟模式：生成[{}]类型内容，字数[{}]", contentType, wordCount);
        return String.format(
            "{\"title\":\"%s优化指南\",\"content\":\"这是关于%s的GEO优化内容，共约%d字。内容涵盖品牌介绍、核心优势、应用场景、用户评价等维度，结构化呈现以适配大模型检索偏好。\",\"keywords\":[\"%s\",\"GEO优化\",\"AI搜索\",\"品牌可见度\",\"内容策略\"]}",
            prompt, prompt, wordCount, prompt
        );
    }

    /**
     * 模拟可见度检测结果
     */
    private VisibilityResult simulateCheckVisibility(String brandName, String platform) {
        log.info("模拟模式：检测品牌[{}]在[{}]平台的可见度", brandName, platform);
        VisibilityResult result = new VisibilityResult();
        result.setBrandName(brandName);
        result.setPlatform(platform);
        result.setMentionCount(new Random().nextInt(100));
        result.setMentionRate(Math.random() * 100);
        result.setFirstRecommendRate(Math.random() * 100);
        result.setRelatedQuestions(Arrays.asList(
            brandName + "怎么样？",
            brandName + "价格是多少？",
            brandName + "和竞品比哪个好？",
            brandName + "有哪些功能？",
            brandName + "适合什么场景？"
        ));
        result.setCompetitorMentions(Arrays.asList("竞品A", "竞品B", "竞品C"));
        result.setScore((int) (Math.random() * 100));
        return result;
    }

    private String callAiApi(String prompt) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            Map<String, String> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);
            requestBody.put("messages", Collections.singletonList(message));
            requestBody.put("temperature", 0.7);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

            org.springframework.http.HttpEntity<Map<String, Object>> entity = new org.springframework.http.HttpEntity<>(requestBody, headers);
            String response = restTemplate.postForObject(apiUrl + "/chat/completions", entity, String.class);
            return response;
        } catch (Exception e) {
            log.error("调用AI API失败", e);
            return "{\"error\":\"调用失败: " + e.getMessage() + "\"}";
        }
    }
}