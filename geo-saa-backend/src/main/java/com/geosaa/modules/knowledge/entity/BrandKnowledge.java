package com.geosaa.modules.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.geosaa.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("brand_knowledge")
public class BrandKnowledge extends BaseEntity {

    private Long brandId;

    private String knowledgeType;

    private String title;

    private String content;

    private String source;

    private Integer status;
}