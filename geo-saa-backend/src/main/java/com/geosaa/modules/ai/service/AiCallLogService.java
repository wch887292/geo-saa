package com.geosaa.modules.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geosaa.common.tenant.TenantContext;
import com.geosaa.security.SecurityUtils;
import com.geosaa.modules.ai.dto.AiChatRequest;
import com.geosaa.modules.ai.dto.AiChatResponse;
import com.geosaa.modules.ai.entity.AiCallLog;
import com.geosaa.modules.ai.entity.AiModelConfig;
import com.geosaa.modules.ai.mapper.AiCallLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI 调用日志服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCallLogService {

    private final AiCallLogMapper callLogMapper;

    /**
     * 记录调用日志
     */
    public Long recordCall(AiModelConfig modelConfig, AiChatRequest request,
                           List<AiChatRequest.Message> messages, AiChatResponse response,
                           String requestId, int latencyMs) {
        try {
            AiCallLog callLog = new AiCallLog();
            callLog.setTenantId(TenantContext.getTenantId());
            callLog.setUserId(SecurityUtils.getCurrentUserIdOrNull());
            callLog.setModelConfigId(modelConfig.getId());
            callLog.setModelName(modelConfig.getModelName());
            callLog.setProvider(modelConfig.getProvider());
            callLog.setModelId(modelConfig.getModelId());
            callLog.setBusinessModule(request.getBusinessModule());
            callLog.setBusinessType(request.getBusinessType());

            // 拼接 prompt
            StringBuilder promptBuilder = new StringBuilder();
            if (request.getSystemPrompt() != null) {
                promptBuilder.append("[System] ").append(request.getSystemPrompt()).append("\n");
            }
            if (messages != null) {
                for (AiChatRequest.Message msg : messages) {
                    promptBuilder.append("[").append(msg.getRole()).append("] ")
                                 .append(msg.getContent()).append("\n");
                }
            }
            String promptText = promptBuilder.toString();
            if (promptText.length() > 2000) {
                promptText = promptText.substring(0, 2000) + "...";
            }
            callLog.setPromptText(promptText);

            // 响应内容
            String responseText = response.getContent();
            if (responseText != null && responseText.length() > 3000) {
                responseText = responseText.substring(0, 3000) + "...";
            }
            callLog.setResponseText(responseText);

            callLog.setPromptTokens(response.getPromptTokens() != null ? response.getPromptTokens() : 0);
            callLog.setCompletionTokens(response.getCompletionTokens() != null ? response.getCompletionTokens() : 0);
            callLog.setTotalTokens(response.getTotalTokens() != null ? response.getTotalTokens() : 0);
            callLog.setCostAmount(response.getCostAmount() != null ? response.getCostAmount() : BigDecimal.ZERO);
            callLog.setLatencyMs(latencyMs);
            callLog.setStatus(response.getSuccess() ? "success" : "failed");
            callLog.setErrorMessage(response.getErrorMessage());
            callLog.setRequestId(requestId);
            callLog.setCreatedAt(LocalDateTime.now());

            callLogMapper.insert(callLog);
            return callLog.getId();
        } catch (Exception e) {
            log.error("记录AI调用日志失败", e);
            return null;
        }
    }

    /**
     * 分页查询调用日志
     */
    public Page<AiCallLog> page(int pageNum, int pageSize, String modelName, String businessModule,
                                 String status, LocalDateTime startTime, LocalDateTime endTime) {
        Long tenantId = TenantContext.getTenantId();
        LambdaQueryWrapper<AiCallLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiCallLog::getTenantId, tenantId)
               .orderByDesc(AiCallLog::getCreatedAt);
        if (modelName != null && !modelName.isEmpty()) {
            wrapper.like(AiCallLog::getModelName, modelName);
        }
        if (businessModule != null && !businessModule.isEmpty()) {
            wrapper.eq(AiCallLog::getBusinessModule, businessModule);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(AiCallLog::getStatus, status);
        }
        if (startTime != null) {
            wrapper.ge(AiCallLog::getCreatedAt, startTime);
        }
        if (endTime != null) {
            wrapper.le(AiCallLog::getCreatedAt, endTime);
        }
        return callLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 获取调用统计
     */
    public Map<String, Object> getStatistics(LocalDateTime startTime, LocalDateTime endTime) {
        Long tenantId = TenantContext.getTenantId();
        LambdaQueryWrapper<AiCallLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiCallLog::getTenantId, tenantId);
        if (startTime != null) wrapper.ge(AiCallLog::getCreatedAt, startTime);
        if (endTime != null) wrapper.le(AiCallLog::getCreatedAt, endTime);

        List<AiCallLog> logs = callLogMapper.selectList(wrapper);
        long totalCalls = logs.size();
        long successCalls = logs.stream().filter(l -> "success".equals(l.getStatus())).count();
        long failedCalls = totalCalls - successCalls;
        int totalTokens = logs.stream().mapToInt(l -> l.getTotalTokens() != null ? l.getTotalTokens() : 0).sum();
        BigDecimal totalCost = logs.stream()
            .map(l -> l.getCostAmount() != null ? l.getCostAmount() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        double avgLatency = logs.stream()
            .mapToInt(l -> l.getLatencyMs() != null ? l.getLatencyMs() : 0)
            .average().orElse(0);

        return Map.of(
            "totalCalls", totalCalls,
            "successCalls", successCalls,
            "failedCalls", failedCalls,
            "successRate", totalCalls > 0 ? (double) successCalls / totalCalls * 100 : 0,
            "totalTokens", totalTokens,
            "totalCost", totalCost,
            "avgLatencyMs", Math.round(avgLatency)
        );
    }
}
