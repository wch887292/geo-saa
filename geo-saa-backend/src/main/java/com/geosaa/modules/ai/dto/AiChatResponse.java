package com.geosaa.modules.ai.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * AI 对话响应 DTO
 */
@Data
public class AiChatResponse {

    /** 响应内容 */
    private String content;

    /** 模型名称 */
    private String modelName;

    /** 模型ID */
    private String modelId;

    /** 提供商 */
    private String provider;

    /** 输入token数 */
    private Integer promptTokens;

    /** 输出token数 */
    private Integer completionTokens;

    /** 总token数 */
    private Integer totalTokens;

    /** 估算费用（美元） */
    private BigDecimal costAmount;

    /** 耗时（毫秒） */
    private Long latencyMs;

    /** 调用日志ID */
    private Long callLogId;

    /** 请求ID */
    private String requestId;

    /** 是否成功 */
    private Boolean success;

    /** 错误信息 */
    private String errorMessage;

    /** 完成原因：stop/length/content_filter */
    private String finishReason;
}
