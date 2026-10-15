package com.geosaa.common;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户 ID（#4 多租户 SaaS 化）。
     * 插入时由 MetaObjectHandler 自动从 TenantContext 填充，无需业务代码手动设置。
     * 查询时由 TenantLineInnerInterceptor 自动追加 tenant_id 条件。
     */
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}