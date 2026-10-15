package com.geosaa.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI Prompt 模板实体
 */
@Data
@TableName("ai_prompt_template")
public class AiPromptTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 模板名称 */
    private String templateName;

    /** 模板编码（唯一标识） */
    private String templateCode;

    /** 所属业务模块 */
    private String businessModule;

    /** 系统提示词 */
    private String systemPrompt;

    /** 用户提示词模板（支持 {{变量}} 占位符） */
    private String userPromptTemplate;

    /** 温度（覆盖模型默认） */
    private BigDecimal temperature;

    /** 最大token（覆盖模型默认） */
    private Integer maxTokens;

    /** 版本号 */
    private Integer version;

    /** 是否启用 0-否 1-是 */
    private Integer isActive;

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
