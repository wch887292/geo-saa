package com.geosaa.modules.ai.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * AI 对话请求 DTO
 */
@Data
public class AiChatRequest {

    /** 模型配置ID（不传则使用默认模型） */
    private Long modelConfigId;

    /** 对话消息列表 */
    private List<Message> messages;

    /** 系统提示词（可选，优先于messages中的system） */
    private String systemPrompt;

    /** 用户输入（简单模式，自动构建messages） */
    private String userInput;

    /** 温度（覆盖模型配置） */
    private Double temperature;

    /** 最大token（覆盖模型配置） */
    private Integer maxTokens;

    /** top_p */
    private Double topP;

    /** 是否流式输出 */
    private Boolean stream;

    /** 业务模块（用于日志记录） */
    private String businessModule;

    /** 业务类型（用于日志记录） */
    private String businessType;

    /** 额外参数 */
    private Map<String, Object> extraParams;

    @Data
    public static class Message {
        /** 角色：system/user/assistant */
        private String role;
        /** 内容 */
        private String content;
    }
}
