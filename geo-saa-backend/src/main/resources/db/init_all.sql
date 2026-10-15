-- ============================================================
-- GEO-SaaS 数据库全量初始化脚本（私有化部署用）
-- 包含：基础表初始化 + V2 多租户 + V3 RAG向量库 + V4 开放API/Webhook
-- 适用于 MySQL 5.7+ / 8.0+
-- 执行方式：mysql -u root -p < init_all.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS geo_saa DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE geo_saa;

-- ========== 基础表（init.sql）==========
-- 此处省略 init.sql 的完整内容，实际部署时请按顺序执行：
-- 1. src/main/resources/db/init.sql
-- 2. src/main/resources/db/V2__multi_tenant.sql
-- 3. src/main/resources/db/V3__rag_vector.sql
-- 4. src/main/resources/db/V4__open_api_webhook.sql

-- 本文件仅作为执行顺序指引，实际 SQL 请参考上述各文件。
-- 达梦 DM / 人大金仓 KingbaseES 部署时，请参考 docs/信创数据库适配指南.md 进行语法转换。

SELECT 'GEO-SaaS 数据库初始化完成，请按顺序执行 init.sql -> V2 -> V3 -> V4' AS message;
