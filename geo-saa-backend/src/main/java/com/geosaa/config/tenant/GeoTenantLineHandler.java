package com.geosaa.config.tenant;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.geosaa.common.tenant.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * GEO-SaaS 租户行级处理器（#4 多租户 SaaS 化）。
 *
 * <p>MyBatis-Plus {@code TenantLineInnerInterceptor} 在执行每条 SQL 前调用本处理器：
 * <ul>
 *   <li>{@link #getTenantId()} — 返回当前租户 ID，自动追加到 WHERE/INSERT 条件</li>
 *   <li>{@link #ignoreTable(String)} — 返回 true 时跳过该表的租户过滤</li>
 * </ul>
 *
 * <p>超级管理员（{@link TenantContext#isSuperAdmin()}）跳过所有表的租户过滤，
 * 可跨租户查看和管理数据。
 *
 * <p>全局表（不参与租户隔离）：
 * <ul>
 *   <li>{@code tenant} — 租户表本身，超级管理员管理所有租户</li>
 *   <li>{@code role_permission} — 角色权限定义，全局共享</li>
 * </ul>
 */
@Component
public class GeoTenantLineHandler implements TenantLineHandler {

    /** 租户字段名 */
    private static final String TENANT_ID_COLUMN = "tenant_id";

    /**
     * 忽略租户过滤的全局表。
     * tenant 表：管理租户本身，不能加租户条件（否则只能看到自己）
     * role_permission：角色权限定义，全局共享
     */
    private static final Set<String> IGNORE_TABLES = Set.of(
            "tenant",
            "role_permission"
    );

    @Override
    public Expression getTenantId() {
        return new LongValue(TenantContext.getTenantId());
    }

    @Override
    public String getTenantIdColumn() {
        return TENANT_ID_COLUMN;
    }

    /**
     * 是否忽略该表的租户过滤。
     * 超级管理员跳过所有表；全局表（tenant/role_permission）始终跳过。
     */
    @Override
    public boolean ignoreTable(String tableName) {
        if (TenantContext.isSuperAdmin()) {
            return true;
        }
        return IGNORE_TABLES.contains(tableName.toLowerCase());
    }
}
