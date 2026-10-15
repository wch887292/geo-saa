package com.geosaa.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 调用日志实体
 */
@Data
@TableName("ai_call_log")
public class AiCallLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long userId;

    /** 使用的模型配置ID */
    private Long modelConfigId;

    /** 模型名称快照 */
    private String modelName;

    /** 提供商快照 */
    private String provider;

    /** 模型ID快照 */
    private String modelId;

    /** 业务模块：content/knowledge/diagnose/rag/geo/other */
    private String businessModule;

    /** 业务类型：generate/analyze/embedding/chat/other */
    private String businessType;

    /** 请求prompt（截断存储） */
    private String promptText;

    /** 响应内容（截断存储） */
    private String responseText;

    /** 输入token数 */
    private Integer promptTokens;

    /** 输出token数 */
    private Integer completionTokens;

    /** 总token数 */
    private Integer totalTokens;

    /** 估算费用（美元） */
    private BigDecimal costAmount;

    /** 耗时（毫秒） */
    private Integer latencyMs;

    /** 状态：success/failed/timeout/cancelled */
    private String status;

    /** 错误信息 */
    private String errorMessage;

    /** 请求ID（链路追踪） */
    private String requestId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
