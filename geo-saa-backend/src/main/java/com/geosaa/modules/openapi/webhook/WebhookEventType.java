package com.geosaa.modules.openapi.webhook;

/**
 * Webhook 事件类型枚举。
 */
public enum WebhookEventType {

    KNOWLEDGE_CREATED("knowledge.created", "知识创建"),
    KNOWLEDGE_UPDATED("knowledge.updated", "知识更新"),
    KNOWLEDGE_DELETED("knowledge.deleted", "知识删除"),
    DIAGNOSE_COMPLETED("diagnose.completed", "诊断任务完成"),
    DIAGNOSE_FAILED("diagnose.failed", "诊断任务失败"),
    DISTRIBUTE_STATUS_CHANGED("distribute.status_changed", "分发任务状态变更"),
    BRAND_UPDATED("brand.updated", "品牌信息更新");

    private final String code;
    private final String description;

    WebhookEventType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static WebhookEventType fromCode(String code) {
        for (WebhookEventType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
