package com.geosaa.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.geosaa.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("system_audit_log")
public class SystemAuditLog extends BaseEntity {

    private String username;

    private String module;

    private String operation;

    private String requestUrl;

    private String requestMethod;

    private String requestParams;

    private Integer duration;

    private String ipAddress;

    private String resultCode;

    private String userAgent;
}