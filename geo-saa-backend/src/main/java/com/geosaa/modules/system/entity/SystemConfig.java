package com.geosaa.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.geosaa.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统配置实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("system_config")
public class SystemConfig extends BaseEntity {

    /** 配置键 */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 配置描述 */
    private String configDesc;

    /** 配置分组 */
    private String configGroup;

    /** 状态 0-禁用 1-启用 */
    private Integer status;
}