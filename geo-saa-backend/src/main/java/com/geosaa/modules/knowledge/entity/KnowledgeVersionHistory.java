package com.geosaa.modules.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.geosaa.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识版本历史
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_version_history")
public class KnowledgeVersionHistory extends BaseEntity {

    /** 关联知识ID */
    private Long knowledgeId;

    /** 版本号 */
    private Integer version;

    /** 标题 */
    private String title;

    /** 内容 */
    private String content;

    /** 来源 */
    private String source;

    /** 变更说明 */
    private String changeLog;
}