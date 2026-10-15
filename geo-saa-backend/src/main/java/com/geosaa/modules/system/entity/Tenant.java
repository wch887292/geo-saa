package com.geosaa.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.geosaa.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 租户实体（#4 多租户 SaaS 化）。
 *
 * <p>每个租户是一个独立的客户组织，拥有自己的用户、内容、知识、分发任务等数据。
 * 采用共享库共享表方案：所有租户数据存在同一张表中，通过 {@code tenant_id} 字段隔离，
 * MyBatis-Plus {@code TenantLineInnerInterceptor} 自动在 SQL 中追加租户条件。
 *
 * <p>特殊租户：{@code id=0} 为平台级超级管理员租户，SUPER_ADMIN 角色用户属于此租户，
 * 可跨租户查看和管理所有数据（租户拦截器对其跳过）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tenant")
public class Tenant extends BaseEntity {

    /** 租户编码，唯一，用于子域名或 API 标识 */
    private String tenantCode;

    /** 租户名称 */
    private String tenantName;

    /** 联系人 */
    private String contactPerson;

    /** 联系电话 */
    private String contactPhone;

    /** 联系邮箱 */
    private String contactEmail;

    /** 租户状态: 0=禁用, 1=启用, 2=过期 */
    private Integer status;

    /** 到期时间 */
    private java.time.LocalDateTime expireTime;

    /** 租户规模/套餐标识 */
    private String plan;

    /** 备注 */
    private String remark;
}
