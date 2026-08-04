package com.geosaa.modules.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.geosaa.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("brand_info")
public class BrandInfo extends BaseEntity {

    private String brandName;

    private String brandCode;

    private String industry;

    private String brandDescription;

    private String brandLogo;

    private String website;

    private Integer status;
}