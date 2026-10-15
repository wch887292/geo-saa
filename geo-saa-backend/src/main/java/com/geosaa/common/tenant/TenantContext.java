package com.geosaa.common.tenant;

/**
 * 租户上下文（#4 多租户 SaaS 化）。
 *
 * <p>基于 ThreadLocal 存储当前请求的租户 ID 与超级管理员标记。
 * 由 {@code JwtAuthenticationFilter} 在请求入口从 JWT token 中解析并设置，
 * 请求结束时在 finally 中清理，防止线程池复用导致租户串号。
 *
 * <p>MyBatis-Plus {@code TenantLineInnerInterceptor} 在执行 SQL 前从本上下文读取租户 ID，
 * 自动追加 {@code tenant_id = ?} 条件；超级管理员（{@code superAdmin=true}）跳过租户过滤。
 *
 * <p>默认租户 ID = 1（平台默认租户），未登录或 token 无效时回退到此值。
 */
public final class TenantContext {

    /** 平台默认租户 ID */
    public static final Long DEFAULT_TENANT_ID = 1L;

    /** 平台超级管理员租户 ID（不属于任何业务租户） */
    public static final Long SUPER_ADMIN_TENANT_ID = 0L;

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SUPER_ADMIN = ThreadLocal.withInitial(() -> false);

    private TenantContext() {
    }

    /**
     * 设置当前租户 ID。
     */
    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    /**
     * 获取当前租户 ID。未设置时返回默认租户 ID。
     */
    public static Long getTenantId() {
        Long id = TENANT_ID.get();
        return id != null ? id : DEFAULT_TENANT_ID;
    }

    /**
     * 设置当前用户是否为超级管理员。
     * 超级管理员跳过租户过滤，可查看所有租户数据。
     */
    public static void setSuperAdmin(boolean superAdmin) {
        SUPER_ADMIN.set(superAdmin);
    }

    /**
     * 当前用户是否为超级管理员。
     */
    public static boolean isSuperAdmin() {
        return Boolean.TRUE.equals(SUPER_ADMIN.get());
    }

    /**
     * 清理当前线程的租户上下文。
     * 必须在请求结束时调用（filter 的 finally 块），防止线程池复用串号。
     */
    public static void clear() {
        TENANT_ID.remove();
        SUPER_ADMIN.remove();
    }
}
