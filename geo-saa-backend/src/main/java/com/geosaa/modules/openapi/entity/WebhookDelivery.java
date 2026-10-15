package com.geosaa.modules.openapi.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.geosaa.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Webhook 投递记录实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("webhook_delivery")
public class WebhookDelivery extends BaseEntity {

    private Long subscriptionId;

    private String eventType;

    private String url;

    private String payload;

    /** pending / success / failed */
    private String status;

    private Integer responseCode;

    private String responseBody;

    private Long durationMs;

    private Integer retryCount;

    private String deliveryId;
}
