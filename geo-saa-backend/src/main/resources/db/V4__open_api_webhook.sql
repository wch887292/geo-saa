-- ============================================================
-- GEO-SaaS V4: 开放 API / Webhook
-- 新增 api_key（API Key 管理）、webhook_subscription（事件订阅）、webhook_delivery（投递记录）
-- ============================================================

-- ========== API Key 表 ==========
CREATE TABLE IF NOT EXISTS api_key (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id         BIGINT       NOT NULL DEFAULT 1 COMMENT '租户ID',
    name              VARCHAR(100) NOT NULL COMMENT 'Key名称',
    api_key           VARCHAR(64)  NOT NULL COMMENT 'API Key（gk_前缀）',
    api_secret        VARCHAR(128) NOT NULL COMMENT 'API Secret（用于HMAC签名）',
    status            TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    rate_limit_per_min INT         NOT NULL DEFAULT 60 COMMENT '每分钟速率限制',
    expire_time       DATETIME     DEFAULT NULL COMMENT '过期时间（null=永不过期）',
    last_used_at      DATETIME     DEFAULT NULL COMMENT '最后使用时间',
    created_by        VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    create_time       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted           TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_api_key (api_key),
    KEY idx_tenant_id (tenant_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开放API Key表';

-- ========== Webhook 事件订阅表 ==========
CREATE TABLE IF NOT EXISTS webhook_subscription (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id         BIGINT       NOT NULL DEFAULT 1 COMMENT '租户ID',
    name              VARCHAR(100) NOT NULL COMMENT '订阅名称',
    url               VARCHAR(500) NOT NULL COMMENT '回调URL',
    event_types       TEXT         NOT NULL COMMENT '订阅事件类型（JSON数组，如["knowledge.created","diagnose.completed"]）',
    secret            VARCHAR(128) NOT NULL COMMENT '签名密钥（HMAC-SHA256）',
    status            TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    description       VARCHAR(500) DEFAULT NULL COMMENT '描述',
    create_time       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted           TINYINT      NOT NULL DEFAULT 0,
    KEY idx_tenant_id (tenant_id),
    KEY idx_status (status),
    KEY idx_url (url(100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Webhook事件订阅表';

-- ========== Webhook 投递记录表 ==========
CREATE TABLE IF NOT EXISTS webhook_delivery (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id         BIGINT       NOT NULL DEFAULT 1 COMMENT '租户ID',
    subscription_id   BIGINT       NOT NULL COMMENT '订阅ID',
    event_type        VARCHAR(100) NOT NULL COMMENT '事件类型',
    url               VARCHAR(500) NOT NULL COMMENT '投递URL',
    payload           TEXT         NOT NULL COMMENT '投递内容（JSON）',
    status            VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT '状态：pending/success/failed',
    response_code     INT          DEFAULT NULL COMMENT 'HTTP响应码',
    response_body     TEXT         DEFAULT NULL COMMENT '响应内容',
    duration_ms       BIGINT       DEFAULT NULL COMMENT '耗时（毫秒）',
    retry_count       INT          NOT NULL DEFAULT 0 COMMENT '重试次数',
    delivery_id       VARCHAR(64)  NOT NULL COMMENT '投递唯一ID（UUID）',
    create_time       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_tenant_id (tenant_id),
    KEY idx_subscription_id (subscription_id),
    KEY idx_event_type (event_type),
    KEY idx_status (status),
    KEY idx_delivery_id (delivery_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Webhook投递记录表';
