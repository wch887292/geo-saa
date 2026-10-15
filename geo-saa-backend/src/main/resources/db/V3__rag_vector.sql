-- ============================================================
-- GEO-SaaS V3: RAG 向量知识库
-- 新增 knowledge_chunk 表，存储知识分块 + 向量 embedding
-- ============================================================

CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id       BIGINT       NOT NULL DEFAULT 1 COMMENT '租户ID',
    brand_id        BIGINT       NOT NULL COMMENT '品牌ID',
    knowledge_id    BIGINT       NOT NULL COMMENT '关联知识ID',
    knowledge_type  VARCHAR(50)  DEFAULT NULL COMMENT '知识类型',
    chunk_index     INT          NOT NULL DEFAULT 0 COMMENT '分块序号',
    content         TEXT         NOT NULL COMMENT '分块文本内容',
    embedding       TEXT         NOT NULL COMMENT '向量embedding(JSON数组)',
    embedding_model VARCHAR(100) DEFAULT NULL COMMENT 'embedding模型',
    source          VARCHAR(500) DEFAULT NULL COMMENT '来源',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    KEY idx_tenant_id (tenant_id),
    KEY idx_brand_id (brand_id),
    KEY idx_knowledge_id (knowledge_id),
    KEY idx_tenant_brand (tenant_id, brand_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG知识分块向量表';
