package com.geosaa.modules.rag.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.geosaa.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * RAG 知识分块向量实体。
 *
 * <p>每条 BrandKnowledge 会被分块为多个 KnowledgeChunk，
 * 每个 chunk 存储文本内容和对应的 embedding 向量（JSON 数组）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_chunk")
public class KnowledgeChunk extends BaseEntity {

    private Long brandId;

    private Long knowledgeId;

    private String knowledgeType;

    private Integer chunkIndex;

    private String content;

    /** embedding 向量，JSON 数组格式，如 "[0.1, 0.2, ...]" */
    private String embedding;

    private String embeddingModel;

    private String source;
}
