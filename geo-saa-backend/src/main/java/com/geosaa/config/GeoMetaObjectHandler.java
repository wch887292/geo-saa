package com.geosaa.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.geosaa.common.tenant.TenantContext;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器（#4 多租户 SaaS 化）。
 *
 * <p>在 INSERT 时自动填充：
 * <ul>
 *   <li>{@code tenantId} — 从 {@link TenantContext} 获取当前租户 ID，确保每条数据都归属正确租户</li>
 *   <li>{@code createTime} — 当前时间（数据库也有默认值，这里双重保障）</li>
 *   <li>{@code updateTime} — 当前时间</li>
 * </ul>
 *
 * <p>在 UPDATE 时自动填充：
 * <ul>
 *   <li>{@code updateTime} — 当前时间</li>
 * </ul>
 *
 * <p>注意：tenantId 仅在 INSERT 时填充，UPDATE 时不修改（数据不能跨租户迁移）。
 * 超级管理员插入数据时，TenantContext.getTenantId() 返回默认租户 ID，
 * 如需为指定租户创建数据，应在 Service 层显式设置 tenantId。
 */
@Component
public class GeoMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        // tenantId：仅在字段为 null 时填充，允许 Service 层显式指定
        this.strictInsertFill(metaObject, "tenantId", Long.class, TenantContext.getTenantId());
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
