-- ============================================================
-- V5: AI 大模型底层架构
-- 包含：AI模型配置表、AI调用日志表、Prompt模板表
-- ============================================================

-- ---------- AI 模型配置表（自定义大模型设置） ----------
CREATE TABLE IF NOT EXISTS ai_model_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    model_name VARCHAR(100) NOT NULL COMMENT '模型名称（用户自定义，如"我的GPT-4"）',
    provider VARCHAR(50) NOT NULL COMMENT '提供商：openai/deepseek/tongyi/doubao/claude/ollama/custom',
    api_key VARCHAR(500) NOT NULL COMMENT 'API Key（加密存储）',
    api_url VARCHAR(500) NOT NULL COMMENT 'API 基础URL',
    model_id VARCHAR(100) NOT NULL COMMENT '模型ID（如 gpt-4o、deepseek-chat、qwen-max）',
    temperature DECIMAL(3,2) DEFAULT 0.70 COMMENT '温度参数 0-2',
    max_tokens INT DEFAULT 4096 COMMENT '最大生成token数',
    top_p DECIMAL(3,2) DEFAULT 1.00 COMMENT 'top_p采样参数',
    presence_penalty DECIMAL(3,2) DEFAULT 0.00 COMMENT '存在惩罚',
    frequency_penalty DECIMAL(3,2) DEFAULT 0.00 COMMENT '频率惩罚',
    timeout_seconds INT DEFAULT 60 COMMENT '超时时间（秒）',
    is_default TINYINT DEFAULT 0 COMMENT '是否默认模型 0-否 1-是',
    status VARCHAR(20) DEFAULT 'active' COMMENT '状态：active/inactive/testing',
    priority INT DEFAULT 0 COMMENT '优先级（数字越大越优先，用于降级切换）',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    created_by BIGINT DEFAULT NULL COMMENT '创建人ID',
    updated_by BIGINT DEFAULT NULL COMMENT '更新人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除 0-未删除 1-已删除',
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_provider (provider),
    INDEX idx_is_default (is_default),
    INDEX idx_status (status),
    UNIQUE KEY uk_tenant_model_name (tenant_id, model_name, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI模型配置表（自定义大模型设置）';

-- ---------- AI 调用日志表 ----------
CREATE TABLE IF NOT EXISTS ai_call_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    user_id BIGINT DEFAULT NULL COMMENT '调用用户ID',
    model_config_id BIGINT DEFAULT NULL COMMENT '使用的模型配置ID',
    model_name VARCHAR(100) DEFAULT NULL COMMENT '模型名称快照',
    provider VARCHAR(50) DEFAULT NULL COMMENT '提供商快照',
    model_id VARCHAR(100) DEFAULT NULL COMMENT '模型ID快照',
    business_module VARCHAR(50) DEFAULT NULL COMMENT '业务模块：content/knowledge/diagnose/rag/geo/other',
    business_type VARCHAR(50) DEFAULT NULL COMMENT '业务类型：generate/analyze/embedding/chat/other',
    prompt_text TEXT COMMENT '请求prompt（截断存储）',
    response_text TEXT COMMENT '响应内容（截断存储）',
    prompt_tokens INT DEFAULT 0 COMMENT '输入token数',
    completion_tokens INT DEFAULT 0 COMMENT '输出token数',
    total_tokens INT DEFAULT 0 COMMENT '总token数',
    cost_amount DECIMAL(10,6) DEFAULT 0.000000 COMMENT '估算费用（美元）',
    latency_ms INT DEFAULT 0 COMMENT '耗时（毫秒）',
    status VARCHAR(20) DEFAULT 'success' COMMENT '状态：success/failed/timeout/cancelled',
    error_message VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
    request_id VARCHAR(100) DEFAULT NULL COMMENT '请求ID（链路追踪）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_user_id (user_id),
    INDEX idx_model_config_id (model_config_id),
    INDEX idx_business_module (business_module),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI调用日志表';

-- ---------- Prompt 模板表 ----------
CREATE TABLE IF NOT EXISTS ai_prompt_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    template_name VARCHAR(100) NOT NULL COMMENT '模板名称',
    template_code VARCHAR(100) NOT NULL COMMENT '模板编码（唯一标识，如 content_generate）',
    business_module VARCHAR(50) DEFAULT NULL COMMENT '所属业务模块',
    system_prompt TEXT COMMENT '系统提示词',
    user_prompt_template TEXT COMMENT '用户提示词模板（支持 {{变量}} 占位符）',
    temperature DECIMAL(3,2) DEFAULT NULL COMMENT '温度（覆盖模型默认）',
    max_tokens INT DEFAULT NULL COMMENT '最大token（覆盖模型默认）',
    version INT DEFAULT 1 COMMENT '版本号',
    is_active TINYINT DEFAULT 1 COMMENT '是否启用 0-否 1-是',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    created_by BIGINT DEFAULT NULL COMMENT '创建人ID',
    updated_by BIGINT DEFAULT NULL COMMENT '更新人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_template_code (template_code),
    INDEX idx_business_module (business_module),
    UNIQUE KEY uk_tenant_code_version (tenant_id, template_code, version, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Prompt模板表';

-- ---------- 初始化默认 Prompt 模板 ----------
INSERT INTO ai_prompt_template (tenant_id, template_name, template_code, business_module, system_prompt, user_prompt_template, temperature, max_tokens, version, is_active) VALUES
(1, '内容生成', 'content_generate', 'content', '你是一个GEO优化内容创作专家，擅长生成适配大模型检索偏好的高质量内容。要求内容结构化、信息密度高、包含关键词。', '请根据以下要求生成内容：\n主题：{{topic}}\n内容类型：{{content_type}}\n字数要求：{{word_count}}字\n品牌：{{brand_name}}\n\n输出JSON格式：{"title":"标题","content":"正文","keywords":["关键词1","关键词2"]}', 0.7, 4096, 1, 1),
(1, 'AI诊断分析', 'diagnose_analyze', 'diagnose', '你是一个GEO诊断分析专家，擅长分析品牌在AI搜索平台的表现并给出优化建议。', '请分析品牌「{{brand_name}}」的GEO表现：\n关键词：{{keywords}}\n平台：{{platform}}\n\n请输出JSON格式的诊断报告，包含：现状评估、问题诊断、优化建议、优先级排序。', 0.5, 2048, 1, 1),
(1, '知识问答', 'knowledge_qa', 'knowledge', '你是一个知识库问答助手，基于提供的知识库内容回答用户问题。如果知识库中没有相关信息，请如实告知。', '知识库内容：\n{{context}}\n\n用户问题：{{question}}\n\n请基于知识库内容回答，回答要准确、简洁，并标注信息来源。', 0.3, 2048, 1, 1),
(1, 'RAG检索增强', 'rag_retrieval', 'rag', '你是一个检索增强生成(RAG)助手，结合检索到的文档片段和用户问题生成准确回答。', '参考文档：\n{{documents}}\n\n用户问题：{{query}}\n\n请基于参考文档回答问题，要求：1.只使用参考文档中的信息 2.标注答案来源 3.如果文档中没有答案请说明。', 0.3, 2048, 1, 1),
(1, 'GEO优化建议', 'geo_optimize', 'geo', '你是一个GEO(生成式引擎优化)专家，帮助品牌提升在AI搜索平台的可见度和推荐率。', '品牌：{{brand_name}}\n当前可见度评分：{{score}}\n主要问题：{{issues}}\n\n请给出具体的GEO优化建议，按优先级排序，每条建议包含：问题描述、优化方案、预期效果、实施难度。', 0.6, 3000, 1, 1);
