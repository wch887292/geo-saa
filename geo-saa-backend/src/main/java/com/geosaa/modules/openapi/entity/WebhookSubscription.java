package com.geosaa.modules.openapi.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.geosaa.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Webhook 事件订阅实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("webhook_subscription")
public class WebhookSubscription extends BaseEntity {

    private String name;

    private String url;

    /** 订阅事件类型，JSON 数组格式，如 ["knowledge.created","diagnose.completed"] */
    private String eventTypes;

    /** HMAC-SHA256 签名密钥 */
    private String secret;

    private Integer status;

    private String description;
}
