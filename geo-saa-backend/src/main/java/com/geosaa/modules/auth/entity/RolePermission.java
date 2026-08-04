package com.geosaa.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.geosaa.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("role_permission")
public class RolePermission extends BaseEntity {

    private String role;

    private String permission;

    private String description;

    private Integer status;
}