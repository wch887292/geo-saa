-- ============================================
-- Geo-SAA 数据库初始化脚本
-- 数据库: geo_saa
-- 字符集: utf8mb4
-- ============================================

CREATE DATABASE IF NOT EXISTS geo_saa DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE geo_saa;

-- ----------------------------
-- 1. 用户信息表
-- ----------------------------
DROP TABLE IF EXISTS user_info;
CREATE TABLE user_info (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    username    VARCHAR(64)  NOT NULL COMMENT '用户名',
    password    VARCHAR(256) NOT NULL COMMENT '密码(加密)',
    nickname    VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    email       VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    phone       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    avatar      VARCHAR(512) DEFAULT NULL COMMENT '头像URL',
    role        VARCHAR(32)  DEFAULT 'USER' COMMENT '角色',
    status      TINYINT      DEFAULT 1 COMMENT '状态: 0=禁用, 1=启用',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户信息表';

-- ----------------------------
-- 2. 角色权限表
-- ----------------------------
DROP TABLE IF EXISTS role_permission;
CREATE TABLE role_permission (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    role        VARCHAR(32)  NOT NULL COMMENT '角色标识',
    permission  VARCHAR(128) NOT NULL COMMENT '权限标识',
    description VARCHAR(256) DEFAULT NULL COMMENT '权限描述',
    status      TINYINT      DEFAULT 1 COMMENT '状态: 0=禁用, 1=启用',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',
    UNIQUE KEY uk_role_permission (role, permission)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限表';

-- ----------------------------
-- 3. 品牌信息表
-- ----------------------------
DROP TABLE IF EXISTS brand_info;
CREATE TABLE brand_info (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    brand_name       VARCHAR(128) NOT NULL COMMENT '品牌名称',
    brand_code       VARCHAR(64)  DEFAULT NULL COMMENT '品牌编码',
    industry         VARCHAR(64)  DEFAULT NULL COMMENT '所属行业',
    brand_description TEXT         DEFAULT NULL COMMENT '品牌描述',
    brand_logo       VARCHAR(512) DEFAULT NULL COMMENT '品牌Logo',
    website          VARCHAR(256) DEFAULT NULL COMMENT '官方网站',
    status           TINYINT      DEFAULT 1 COMMENT '状态: 0=禁用, 1=启用',
    create_time      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT      DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',
    UNIQUE KEY uk_brand_name (brand_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='品牌信息表';

-- ----------------------------
-- 4. 品牌知识表
-- ----------------------------
DROP TABLE IF EXISTS brand_knowledge;
CREATE TABLE brand_knowledge (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    brand_id      BIGINT       NOT NULL COMMENT '品牌ID',
    knowledge_type VARCHAR(64) DEFAULT NULL COMMENT '知识类型',
    title         VARCHAR(256) NOT NULL COMMENT '知识标题',
    content       TEXT         DEFAULT NULL COMMENT '知识内容',
    source        VARCHAR(256) DEFAULT NULL COMMENT '来源',
    status        TINYINT      DEFAULT 1 COMMENT '状态: 0=禁用, 1=启用',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT      DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',
    KEY idx_brand_id (brand_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='品牌知识表';

-- ----------------------------
-- 5. AI 诊断任务表
-- ----------------------------
DROP TABLE IF EXISTS ai_diagnose_task;
CREATE TABLE ai_diagnose_task (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    task_name     VARCHAR(128) NOT NULL COMMENT '任务名称',
    task_type     VARCHAR(64)  NOT NULL COMMENT '诊断类型',
    brand_name    VARCHAR(128) DEFAULT NULL COMMENT '品牌名称',
    input_params  TEXT         DEFAULT NULL COMMENT '输入参数(JSON)',
    result_content TEXT        DEFAULT NULL COMMENT '诊断结果内容',
    status        TINYINT      DEFAULT 0 COMMENT '状态: 0=待处理, 1=处理中, 2=已完成, 3=失败',
    created_by    BIGINT       DEFAULT NULL COMMENT '创建人ID',
    remark        VARCHAR(512) DEFAULT NULL COMMENT '备注',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT      DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',
    KEY idx_status (status),
    KEY idx_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI诊断任务表';

-- ----------------------------
-- 6. AI 文章内容表
-- ----------------------------
DROP TABLE IF EXISTS ai_article_content;
CREATE TABLE ai_article_content (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    title        VARCHAR(256) NOT NULL COMMENT '文章标题',
    content      MEDIUMTEXT   DEFAULT NULL COMMENT '文章内容',
    content_type VARCHAR(64)  DEFAULT NULL COMMENT '内容类型',
    brand_name   VARCHAR(128) DEFAULT NULL COMMENT '品牌名称',
    keywords     VARCHAR(512) DEFAULT NULL COMMENT '关键词',
    summary      TEXT         DEFAULT NULL COMMENT '摘要',
    word_count   INT          DEFAULT 0 COMMENT '字数',
    status       TINYINT      DEFAULT 0 COMMENT '状态: 0=草稿, 1=已发布, 2=已归档',
    created_by   BIGINT       DEFAULT NULL COMMENT '创建人ID',
    create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT      DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',
    KEY idx_status (status),
    KEY idx_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI文章内容表';

-- ----------------------------
-- 7. 分发任务表
-- ----------------------------
DROP TABLE IF EXISTS distribute_task;
CREATE TABLE distribute_task (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    task_name         VARCHAR(128) NOT NULL COMMENT '任务名称',
    content_id        BIGINT       DEFAULT NULL COMMENT '内容ID',
    target_platform   VARCHAR(64)  NOT NULL COMMENT '目标平台',
    target_account    VARCHAR(128) DEFAULT NULL COMMENT '目标账号',
    distribute_config TEXT         DEFAULT NULL COMMENT '分发配置(JSON)',
    status            TINYINT      DEFAULT 0 COMMENT '状态: 0=待分发, 1=分发中, 2=已完成, 3=已取消, 4=失败',
    scheduled_time    DATETIME     DEFAULT NULL COMMENT '定时发布时间',
    publish_time      DATETIME     DEFAULT NULL COMMENT '实际发布时间',
    result_info       TEXT         DEFAULT NULL COMMENT '结果信息',
    created_by        BIGINT       DEFAULT NULL COMMENT '创建人ID',
    create_time       DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT      DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',
    KEY idx_status (status),
    KEY idx_target_platform (target_platform),
    KEY idx_content_id (content_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分发任务表';

-- ----------------------------
-- 8. 数据监控统计表
-- ----------------------------
DROP TABLE IF EXISTS data_monitor_stat;
CREATE TABLE data_monitor_stat (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    stat_date   DATE         NOT NULL COMMENT '统计日期',
    stat_type   VARCHAR(64)  NOT NULL COMMENT '统计类型',
    stat_key    VARCHAR(128) NOT NULL COMMENT '统计键',
    stat_value  BIGINT       DEFAULT 0 COMMENT '统计值',
    dimension   VARCHAR(64)  DEFAULT NULL COMMENT '维度',
    remark      VARCHAR(512) DEFAULT NULL COMMENT '备注',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',
    UNIQUE KEY uk_stat (stat_date, stat_type, stat_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据监控统计表';

-- ----------------------------
-- 9. 系统审计日志表
-- ----------------------------
DROP TABLE IF EXISTS system_audit_log;
CREATE TABLE system_audit_log (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    username       VARCHAR(64)  DEFAULT NULL COMMENT '操作人',
    module         VARCHAR(64)  DEFAULT NULL COMMENT '所属模块',
    operation      VARCHAR(128) DEFAULT NULL COMMENT '操作名称',
    request_url    VARCHAR(512) DEFAULT NULL COMMENT '请求URL',
    request_method VARCHAR(16)  DEFAULT NULL COMMENT '请求方法',
    request_params TEXT         DEFAULT NULL COMMENT '请求参数',
    duration       INT          DEFAULT 0 COMMENT '耗时(ms)',
    ip_address     VARCHAR(64)  DEFAULT NULL COMMENT 'IP地址',
    result_code    VARCHAR(16)  DEFAULT NULL COMMENT '结果码',
    user_agent     VARCHAR(512) DEFAULT NULL COMMENT 'User-Agent',
    create_time    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT      DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',
    KEY idx_username (username),
    KEY idx_module (module),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统审计日志表';

-- ----------------------------
-- 初始化数据: 默认管理员账号
-- 密码: admin123 (BCrypt加密)
-- ----------------------------
INSERT INTO user_info (username, password, nickname, role, status) VALUES
('admin', '$2b$10$dcgzsX.fhPkxrBlI2T1Md.K1yV2FARNMIJjBCm.8q1EL0LqVpicOu', '系统管理员', 'ADMIN', 1);

-- ----------------------------
-- 初始化数据: 默认角色权限
-- ----------------------------
INSERT INTO role_permission (role, permission, description, status) VALUES
('ADMIN', 'system:all', '系统全部权限', 1),
('ADMIN', 'auth:manage', '认证管理', 1),
('ADMIN', 'diagnose:all', '诊断管理', 1),
('ADMIN', 'knowledge:all', '知识库管理', 1),
('ADMIN', 'content:all', '内容管理', 1),
('ADMIN', 'distribute:all', '分发管理', 1),
('ADMIN', 'monitor:all', '监控管理', 1),
('USER', 'diagnose:view', '查看诊断', 1),
('USER', 'knowledge:view', '查看知识库', 1),
('USER', 'content:view', '查看内容', 1),
('ADMIN', 'menu:dashboard', '仪表盘', 1),
('ADMIN', 'menu:diagnose', 'AI诊断', 1),
('ADMIN', 'menu:knowledge', '知识库', 1),
('ADMIN', 'menu:content', 'AI创作', 1),
('ADMIN', 'menu:distribute', '分发管理', 1),
('ADMIN', 'menu:monitor', '数据监测', 1),
('ADMIN', 'menu:system', '系统设置', 1);

-- ----------------------------
-- 10. 系统配置表 (新增)
-- ----------------------------
DROP TABLE IF EXISTS system_config;
CREATE TABLE system_config (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    config_key   VARCHAR(128) NOT NULL COMMENT '配置键',
    config_value TEXT         DEFAULT NULL COMMENT '配置值',
    config_desc  VARCHAR(256) DEFAULT NULL COMMENT '配置描述',
    config_group VARCHAR(64)  DEFAULT 'default' COMMENT '配置分组',
    status       TINYINT      DEFAULT 1 COMMENT '状态: 0=禁用, 1=启用',
    create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT      DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',
    UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- ----------------------------
-- 11. 知识版本历史表 (新增)
-- ----------------------------
DROP TABLE IF EXISTS knowledge_version_history;
CREATE TABLE knowledge_version_history (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    knowledge_id BIGINT       NOT NULL COMMENT '关联知识ID',
    version      INT          NOT NULL DEFAULT 1 COMMENT '版本号',
    title        VARCHAR(256) DEFAULT NULL COMMENT '标题',
    content      TEXT         DEFAULT NULL COMMENT '内容',
    source       VARCHAR(256) DEFAULT NULL COMMENT '来源',
    change_log   VARCHAR(512) DEFAULT NULL COMMENT '变更说明',
    create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT      DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',
    KEY idx_knowledge_id (knowledge_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识版本历史表';

-- 初始化系统配置
INSERT INTO system_config (config_key, config_value, config_desc, config_group, status) VALUES
('intranet_mode', 'false', '内网模式开关', 'system', 1),
('openaiApiKey', '', 'OpenAI API Key', 'ai_model', 1),
('openaiApiUrl', 'https://api.openai.com/v1', 'OpenAI API URL', 'ai_model', 1),
('openaiModel', 'gpt-4', 'OpenAI 模型', 'ai_model', 1),
('tongyiApiKey', '', '通义千问 API Key', 'ai_model', 1),
('doubaoApiKey', '', '豆包 API Key', 'ai_model', 1),
('simulationEnabled', 'true', '模拟模式开关', 'ai_model', 1);

-- ----------------------------
-- 12. 资产存证记录表 (O7: asset 独立数据模型)
-- 资产视图不再仅靠 content/knowledge/distribute/diagnose 四表内存聚合兜底，
-- 本表提供独立的数据模型；未写入记录时相关接口返回空，不产生虚假数据。
-- ----------------------------
DROP TABLE IF EXISTS asset_record;
CREATE TABLE asset_record (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    asset_key   VARCHAR(128) NOT NULL COMMENT '资产唯一键',
    asset_name  VARCHAR(256) NOT NULL COMMENT '资产名称',
    asset_type  VARCHAR(64)  DEFAULT 'record' COMMENT '资产类型',
    brand_name  VARCHAR(128) DEFAULT NULL COMMENT '所属品牌',
    status      TINYINT      DEFAULT 1 COMMENT '状态: 0=失效, 1=有效',
    stat_value  BIGINT       DEFAULT 0 COMMENT '统计值',
    source      VARCHAR(256) DEFAULT NULL COMMENT '来源',
    remark      VARCHAR(512) DEFAULT NULL COMMENT '备注',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',
    UNIQUE KEY uk_asset_key (asset_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资产存证记录表';