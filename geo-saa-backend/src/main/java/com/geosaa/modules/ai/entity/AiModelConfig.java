package com.geosaa.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 模型配置实体（自定义大模型设置）
 */
@Data
@TableName("ai_model_config")
public class AiModelConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 模型名称（用户自定义） */
    private String modelName;

    /** 提供商：openai/deepseek/tongyi/doubao/claude/ollama/custom */
    private String provider;

    /** API Key（加密存储） */
    private String apiKey;

    /** API 基础URL */
    private String apiUrl;

    /** 模型ID（如 gpt-4o、deepseek-chat） */
    private String modelId;

    /** 温度参数 0-2 */
    private BigDecimal temperature;

    /** 最大生成token数 */
    private Integer maxTokens;

    /** top_p采样参数 */
    private BigDecimal topP;

    /** 存在惩罚 */
    private BigDecimal presencePenalty;

    /** 频率惩罚 */
    private BigDecimal frequencyPenalty;

    /** 超时时间（秒） */
    private Integer timeoutSeconds;

    /** 是否默认模型 0-否 1-是 */
    private Integer isDefault;

    /** 状态：active/inactive/testing */
    private String status;

    /** 优先级（数字越大越优先） */
    private Integer priority;

    /** 备注 */
    private String remark;

    private Long createdBy;
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
