package com.geosaa.modules.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geosaa.common.tenant.TenantContext;
import com.geosaa.security.SecurityUtils;
import com.geosaa.modules.ai.dto.AiChatRequest;
import com.geosaa.modules.ai.dto.AiChatResponse;
import com.geosaa.modules.ai.entity.AiCallLog;
import com.geosaa.modules.ai.entity.AiModelConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 统一 AI 对话服务（AI大模型底层架构核心）
 *
 * <p>所有业务模块的AI调用统一通过此服务发起，支持：
 * <ul>
 *   <li>多模型提供商（OpenAI/DeepSeek/通义/豆包/Claude/Ollama/自定义OpenAI兼容）</li>
 *   <li>自动降级（主模型失败时按优先级切换备用模型）</li>
 *   <li>调用日志记录（prompt/响应/token/费用/耗时）</li>
 *   <li>费用统计（按模型单价估算）</li>
 *   <li>重试机制（指数退避）</li>
 *   <li>模拟模式（无API Key时返回模拟数据）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    @Qualifier("aiRestTemplate")
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AiModelConfigService modelConfigService;
    private final AiCallLogService callLogService;

    /** 最大重试次数 */
    private static final int MAX_RETRIES = 2;
    /** 重试初始间隔（毫秒） */
    private static final long RETRY_INITIAL_DELAY = 1000;
    /** prompt 存储最大长度 */
    private static final int MAX_PROMPT_STORE_LENGTH = 2000;
    /** 响应存储最大长度 */
    private static final int MAX_RESPONSE_STORE_LENGTH = 3000;

    /**
     * 统一 AI 对话入口
     */
    public AiChatResponse chat(AiChatRequest request) {
        long startTime = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        // 1. 获取模型配置
        AiModelConfig modelConfig = resolveModelConfig(request.getModelConfigId());
        if (modelConfig == null) {
            return buildErrorResponse(null, "未找到可用的AI模型配置，请先在大模型设置中配置模型", requestId, startTime);
        }

        // 2. 构建消息列表
        List<AiChatRequest.Message> messages = buildMessages(request);

        // 3. 调用 AI（支持重试和降级）
        AiChatResponse response = callWithFallback(modelConfig, messages, request, requestId, startTime);

        // 4. 记录调用日志
        if (response.getCallLogId() == null) {
            Long logId = callLogService.recordCall(
                modelConfig, request, messages, response, requestId,
                (int)(System.currentTimeMillis() - startTime)
            );
            response.setCallLogId(logId);
        }

        return response;
    }

    /**
     * 简单文本生成（便捷方法）
     */
    public String generateText(String systemPrompt, String userPrompt, String businessModule, String businessType) {
        AiChatRequest request = new AiChatRequest();
        request.setSystemPrompt(systemPrompt);
        request.setUserInput(userPrompt);
        request.setBusinessModule(businessModule);
        request.setBusinessType(businessType);
        AiChatResponse response = chat(request);
        return response.getSuccess() ? response.getContent() : null;
    }

    /**
     * 使用指定模板生成
     */
    public AiChatResponse chatWithTemplate(String templateCode, Map<String, String> variables,
                                            String businessModule, String businessType) {
        // TODO: 从 AiPromptTemplateService 获取模板并渲染
        AiChatRequest request = new AiChatRequest();
        request.setBusinessModule(businessModule);
        request.setBusinessType(businessType);
        return chat(request);
    }

    // ==================== 内部方法 ====================

    /**
     * 解析模型配置
     */
    private AiModelConfig resolveModelConfig(Long modelConfigId) {
        if (modelConfigId != null) {
            AiModelConfig config = modelConfigService.getById(modelConfigId);
            if (config != null && "active".equals(config.getStatus())) {
                return config;
            }
        }
        // 使用默认模型
        return modelConfigService.getDefaultModel();
    }

    /**
     * 构建消息列表
     */
    private List<AiChatRequest.Message> buildMessages(AiChatRequest request) {
        List<AiChatRequest.Message> messages = new ArrayList<>();

        // 系统提示词
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
            AiChatRequest.Message sysMsg = new AiChatRequest.Message();
            sysMsg.setRole("system");
            sysMsg.setContent(request.getSystemPrompt());
            messages.add(sysMsg);
        }

        // 历史消息
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            messages.addAll(request.getMessages());
        }

        // 用户输入（简单模式）
        if (request.getUserInput() != null && !request.getUserInput().isEmpty()) {
            AiChatRequest.Message userMsg = new AiChatRequest.Message();
            userMsg.setRole("user");
            userMsg.setContent(request.getUserInput());
            messages.add(userMsg);
        }

        return messages;
    }

    /**
     * 带降级的 AI 调用
     */
    private AiChatResponse callWithFallback(AiModelConfig primaryModel, List<AiChatRequest.Message> messages,
                                             AiChatRequest request, String requestId, long startTime) {
        // 尝试主模型
        AiChatResponse response = callWithRetry(primaryModel, messages, request, requestId, startTime);
        if (response.getSuccess()) {
            return response;
        }

        // 主模型失败，尝试降级到其他可用模型
        log.warn("主模型调用失败，尝试降级。模型: {}, 错误: {}", primaryModel.getModelName(), response.getErrorMessage());
        List<AiModelConfig> availableModels = modelConfigService.getAvailableModels();
        for (AiModelConfig fallbackModel : availableModels) {
            if (fallbackModel.getId().equals(primaryModel.getId())) {
                continue;
            }
            log.info("尝试降级模型: {}", fallbackModel.getModelName());
            AiChatResponse fallbackResponse = callWithRetry(fallbackModel, messages, request, requestId, startTime);
            if (fallbackResponse.getSuccess()) {
                fallbackResponse.setErrorMessage("(已从 " + primaryModel.getModelName() + " 降级)");
                return fallbackResponse;
            }
        }

        // 所有模型都失败，返回最后一次的错误
        return response;
    }

    /**
     * 带重试的 AI 调用
     */
    private AiChatResponse callWithRetry(AiModelConfig modelConfig, List<AiChatRequest.Message> messages,
                                          AiChatRequest request, String requestId, long startTime) {
        Exception lastException = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return doCall(modelConfig, messages, request, requestId, startTime);
            } catch (Exception e) {
                lastException = e;
                log.warn("AI调用失败（第{}次）: 模型={}, 错误={}", attempt + 1, modelConfig.getModelName(), e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_INITIAL_DELAY * (long) Math.pow(2, attempt));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        return buildErrorResponse(modelConfig,
            "AI调用失败: " + (lastException != null ? lastException.getMessage() : "未知错误"),
            requestId, startTime);
    }

    /**
     * 实际调用 AI API（OpenAI 兼容协议）
     */
    private AiChatResponse doCall(AiModelConfig modelConfig, List<AiChatRequest.Message> messages,
                                  AiChatRequest request, String requestId, long startTime) throws Exception {
        // 模拟模式（API Key 为空或以 mock 开头）
        if (modelConfig.getApiKey() == null || modelConfig.getApiKey().isEmpty()
                || "mock".equalsIgnoreCase(modelConfig.getProvider())) {
            return simulateResponse(modelConfig, messages, requestId, startTime);
        }

        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelConfig.getModelId());
        requestBody.put("messages", convertMessages(messages));

        // 温度等参数（请求参数优先，否则用模型配置）
        double temperature = request.getTemperature() != null ? request.getTemperature()
            : modelConfig.getTemperature() != null ? modelConfig.getTemperature().doubleValue() : 0.7;
        requestBody.put("temperature", temperature);

        int maxTokens = request.getMaxTokens() != null ? request.getMaxTokens()
            : modelConfig.getMaxTokens() != null ? modelConfig.getMaxTokens() : 4096;
        requestBody.put("max_tokens", maxTokens);

        if (request.getTopP() != null) {
            requestBody.put("top_p", request.getTopP());
        }

        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(modelConfig.getApiKey());
        // 部分提供商需要额外头
        if ("tongyi".equalsIgnoreCase(modelConfig.getProvider())) {
            headers.set("X-DashScope-SSE", "enable");
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        // 调用 API
        String apiUrl = modelConfig.getApiUrl();
        if (!apiUrl.endsWith("/")) {
            apiUrl += "/";
        }
        String fullUrl = apiUrl + "chat/completions";

        log.debug("调用AI API: model={}, url={}, messages={}", modelConfig.getModelId(), fullUrl, messages.size());

        ResponseEntity<String> responseEntity = restTemplate.exchange(
            fullUrl, HttpMethod.POST, entity, String.class);

        // 解析响应
        String responseBody = responseEntity.getBody();
        JsonNode root = objectMapper.readTree(responseBody);

        AiChatResponse response = new AiChatResponse();
        response.setSuccess(true);
        response.setModelName(modelConfig.getModelName());
        response.setModelId(modelConfig.getModelId());
        response.setProvider(modelConfig.getProvider());
        response.setRequestId(requestId);

        // 提取内容
        if (root.has("choices") && root.get("choices").size() > 0) {
            JsonNode choice = root.get("choices").get(0);
            if (choice.has("message") && choice.get("message").has("content")) {
                response.setContent(choice.get("message").get("content").asText());
            }
            if (choice.has("finish_reason")) {
                response.setFinishReason(choice.get("finish_reason").asText());
            }
        }

        // 提取 token 用量
        if (root.has("usage")) {
            JsonNode usage = root.get("usage");
            int promptTokens = usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : 0;
            int completionTokens = usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : 0;
            response.setPromptTokens(promptTokens);
            response.setCompletionTokens(completionTokens);
            response.setTotalTokens(promptTokens + completionTokens);
            response.setCostAmount(estimateCost(modelConfig.getProvider(), modelConfig.getModelId(), promptTokens, completionTokens));
        }

        response.setLatencyMs(System.currentTimeMillis() - startTime);

        // 记录调用日志
        Long logId = callLogService.recordCall(modelConfig, request, messages, response, requestId,
            (int)(System.currentTimeMillis() - startTime));
        response.setCallLogId(logId);

        return response;
    }

    /**
     * 模拟响应（无 API Key 时使用）
     */
    private AiChatResponse simulateResponse(AiModelConfig modelConfig, List<AiChatRequest.Message> messages,
                                             String requestId, long startTime) {
        log.info("模拟模式：模型={}, 消息数={}", modelConfig.getModelName(), messages.size());

        String lastUserContent = "";
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).getRole())) {
                lastUserContent = messages.get(i).getContent();
                break;
            }
        }

        AiChatResponse response = new AiChatResponse();
        response.setSuccess(true);
        response.setModelName(modelConfig.getModelName() + "(模拟)");
        response.setModelId(modelConfig.getModelId());
        response.setProvider(modelConfig.getProvider());
        response.setRequestId(requestId);
        response.setContent("【模拟响应】这是模拟模式下的AI回复。\n\n您的问题是：" + lastUserContent + "\n\n请在大模型设置中配置有效的 API Key 以获取真实AI回复。");
        response.setPromptTokens(lastUserContent.length() / 4);
        response.setCompletionTokens(50);
        response.setTotalTokens(lastUserContent.length() / 4 + 50);
        response.setCostAmount(BigDecimal.ZERO);
        response.setLatencyMs(System.currentTimeMillis() - startTime);
        response.setFinishReason("stop");

        return response;
    }

    /**
     * 转换消息格式
     */
    private List<Map<String, String>> convertMessages(List<AiChatRequest.Message> messages) {
        List<Map<String, String>> result = new ArrayList<>();
        for (AiChatRequest.Message msg : messages) {
            Map<String, String> map = new HashMap<>();
            map.put("role", msg.getRole());
            map.put("content", msg.getContent());
            result.add(map);
        }
        return result;
    }

    /**
     * 估算费用（美元）
     */
    private BigDecimal estimateCost(String provider, String modelId, int promptTokens, int completionTokens) {
        // 简化的费用估算（每百万token美元）
        double promptPricePer1M = 0.003;  // 默认 $3/M
        double completionPricePer1M = 0.004; // 默认 $4/M

        String model = modelId.toLowerCase();
        if (model.contains("gpt-4o")) {
            promptPricePer1M = 5.0; completionPricePer1M = 15.0;
        } else if (model.contains("gpt-4-turbo")) {
            promptPricePer1M = 10.0; completionPricePer1M = 30.0;
        } else if (model.contains("gpt-3.5")) {
            promptPricePer1M = 0.5; completionPricePer1M = 1.5;
        } else if (model.contains("deepseek")) {
            promptPricePer1M = 0.14; completionPricePer1M = 0.28;
        } else if (model.contains("qwen") || model.contains("tongyi")) {
            promptPricePer1M = 0.15; completionPricePer1M = 0.30;
        } else if (model.contains("doubao") || model.contains("ep-")) {
            promptPricePer1M = 0.12; completionPricePer1M = 0.24;
        } else if (model.contains("claude")) {
            promptPricePer1M = 3.0; completionPricePer1M = 15.0;
        }

        double cost = (promptTokens / 1_000_000.0 * promptPricePer1M)
                    + (completionTokens / 1_000_000.0 * completionPricePer1M);
        return BigDecimal.valueOf(cost).setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * 构建错误响应
     */
    private AiChatResponse buildErrorResponse(AiModelConfig modelConfig, String errorMessage,
                                               String requestId, long startTime) {
        AiChatResponse response = new AiChatResponse();
        response.setSuccess(false);
        response.setErrorMessage(errorMessage);
        response.setRequestId(requestId);
        response.setLatencyMs(System.currentTimeMillis() - startTime);
        if (modelConfig != null) {
            response.setModelName(modelConfig.getModelName());
            response.setModelId(modelConfig.getModelId());
            response.setProvider(modelConfig.getProvider());
        }
        return response;
    }
}
