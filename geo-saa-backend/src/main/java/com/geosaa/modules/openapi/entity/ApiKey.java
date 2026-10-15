package com.geosaa.modules.openapi.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.geosaa.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 开放 API Key 实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("api_key")
public class ApiKey extends BaseEntity {

    private String name;

    private String apiKey;

    private String apiSecret;

    private Integer status;

    private Integer rateLimitPerMin;

    private LocalDateTime expireTime;

    private LocalDateTime lastUsedAt;

    private String createdBy;
}
