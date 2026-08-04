package com.geosaa.modules.content.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.geosaa.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_article_content")
public class AiArticleContent extends BaseEntity {

    private String title;

    private String content;

    private String contentType;

    private String brandName;

    private String keywords;

    private String summary;

    private Integer wordCount;

    private Integer status;

    private Long createdBy;
}