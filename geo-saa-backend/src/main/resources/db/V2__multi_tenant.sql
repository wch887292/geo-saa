-- ============================================
-- V2 多租户 SaaS 化升级脚本
-- 适用：在 V1 init.sql 基础上升级
-- 新增：tenant 表、所有业务表 tenant_id 字段、默认租户、超级管理员
-- ============================================

USE geo_saa;

-- ----------------------------
-- 1. 租户表
-- ----------------------------
DROP TABLE IF EXISTS tenant;
CREATE TABLE tenant (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id     BIGINT       NOT NULL DEFAULT 1 COMMENT '租户ID(全局表,默认1)',
    tenant_code   VARCHAR(64)  NOT NULL COMMENT '租户编码',
    tenant_name   VARCHAR(128) NOT NULL COMMENT '租户名称',
    contact_person VARCHAR(64)  DEFAULT NULL COMMENT '联系人',
    contact_phone VARCHAR(20)   DEFAULT NULL COMMENT '联系电话',
    contact_email VARCHAR(128)  DEFAULT NULL COMMENT '联系邮箱',
    status        TINYINT      DEFAULT 1 COMMENT '状态: 0=禁用, 1=启用, 2=过期',
    expire_time   DATETIME     DEFAULT NULL COMMENT '到期时间',
    plan          VARCHAR(32)  DEFAULT 'standard' COMMENT '套餐: free/standard/pro/enterprise',
    remark        VARCHAR(512) DEFAULT NULL COMMENT '备注',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT      DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',
    UNIQUE KEY uk_tenant_code (tenant_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户表';

-- ----------------------------
-- 2. 为所有业务表添加 tenant_id 字段
--    tenant 表和 role_permission 表不加（全局表）
-- ----------------------------

-- user_info: 用户名唯一键改为 (tenant_id, username)
ALTER TABLE user_info ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id;
ALTER TABLE user_info DROP INDEX uk_username;
ALTER TABLE user_info ADD UNIQUE KEY uk_tenant_username (tenant_id, username);
ALTER TABLE user_info ADD KEY idx_tenant_id (tenant_id);

-- brand_info
ALTER TABLE brand_info ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id;
ALTER TABLE brand_info DROP INDEX uk_brand_name;
ALTER TABLE brand_info ADD UNIQUE KEY uk_tenant_brand (tenant_id, brand_name);
ALTER TABLE brand_info ADD KEY idx_tenant_id (tenant_id);

-- brand_knowledge
ALTER TABLE brand_knowledge ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id;
ALTER TABLE brand_knowledge ADD KEY idx_tenant_id (tenant_id);

-- ai_diagnose_task
ALTER TABLE ai_diagnose_task ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id;
ALTER TABLE ai_diagnose_task ADD KEY idx_tenant_id (tenant_id);

-- ai_article_content
ALTER TABLE ai_article_content ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id;
ALTER TABLE ai_article_content ADD KEY idx_tenant_id (tenant_id);

-- distribute_task
ALTER TABLE distribute_task ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id;
ALTER TABLE distribute_task ADD KEY idx_tenant_id (tenant_id);

-- data_monitor_stat: 唯一键改为 (tenant_id, stat_date, stat_type, stat_key)
ALTER TABLE data_monitor_stat ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id;
ALTER TABLE data_monitor_stat DROP INDEX uk_stat;
ALTER TABLE data_monitor_stat ADD UNIQUE KEY uk_tenant_stat (tenant_id, stat_date, stat_type, stat_key);
ALTER TABLE data_monitor_stat ADD KEY idx_tenant_id (tenant_id);

-- system_audit_log
ALTER TABLE system_audit_log ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id;
ALTER TABLE system_audit_log ADD KEY idx_tenant_id (tenant_id);

-- system_config: 配置键唯一改为 (tenant_id, config_key)
ALTER TABLE system_config ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id;
ALTER TABLE system_config DROP INDEX uk_config_key;
ALTER TABLE system_config ADD UNIQUE KEY uk_tenant_config (tenant_id, config_key);
ALTER TABLE system_config ADD KEY idx_tenant_id (tenant_id);

-- knowledge_version_history
ALTER TABLE knowledge_version_history ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id;
ALTER TABLE knowledge_version_history ADD KEY idx_tenant_id (tenant_id);

-- asset_record: 资产键唯一改为 (tenant_id, asset_key)
ALTER TABLE asset_record ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id;
ALTER TABLE asset_record DROP INDEX uk_asset_key;
ALTER TABLE asset_record ADD UNIQUE KEY uk_tenant_asset (tenant_id, asset_key);
ALTER TABLE asset_record ADD KEY idx_tenant_id (tenant_id);

-- role_permission: 全局表也需 tenant_id 列(因实体继承 BaseEntity),但不参与租户过滤
ALTER TABLE role_permission ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id;
ALTER TABLE role_permission ADD KEY idx_tenant_id (tenant_id);

-- ----------------------------
-- 3. 初始化默认租户
-- ----------------------------
INSERT INTO tenant (id, tenant_code, tenant_name, contact_person, status, plan, remark) VALUES
(1, 'default', '默认租户', '系统管理员', 1, 'enterprise', '系统默认租户，平台超级管理员所属');

-- ----------------------------
-- 4. 更新默认管理员为超级管理员（SUPER_ADMIN），属于默认租户
-- ----------------------------
UPDATE user_info SET tenant_id = 1, role = 'SUPER_ADMIN' WHERE username = 'admin';

-- ----------------------------
-- 5. 初始化超级管理员角色权限
-- ----------------------------
INSERT INTO role_permission (role, permission, description, status) VALUES
('SUPER_ADMIN', 'system:all', '系统全部权限', 1),
('SUPER_ADMIN', 'system:tenant:view', '查看租户', 1),
('SUPER_ADMIN', 'system:tenant:create', '创建租户', 1),
('SUPER_ADMIN', 'system:tenant:update', '更新租户', 1),
('SUPER_ADMIN', 'system:tenant:delete', '删除租户', 1),
('SUPER_ADMIN', 'auth:manage', '认证管理', 1),
('SUPER_ADMIN', 'diagnose:all', '诊断管理', 1),
('SUPER_ADMIN', 'knowledge:all', '知识库管理', 1),
('SUPER_ADMIN', 'content:all', '内容管理', 1),
('SUPER_ADMIN', 'distribute:all', '分发管理', 1),
('SUPER_ADMIN', 'monitor:all', '监控管理', 1),
('SUPER_ADMIN', 'menu:dashboard', '仪表盘', 1),
('SUPER_ADMIN', 'menu:diagnose', 'AI诊断', 1),
('SUPER_ADMIN', 'menu:knowledge', '知识库', 1),
('SUPER_ADMIN', 'menu:content', 'AI创作', 1),
('SUPER_ADMIN', 'menu:distribute', '分发管理', 1),
('SUPER_ADMIN', 'menu:monitor', '数据监测', 1),
('SUPER_ADMIN', 'menu:system', '系统设置', 1),
('SUPER_ADMIN', 'menu:tenant', '租户管理', 1);
