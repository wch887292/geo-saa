package com.geosaa.modules.rag.dto;

import lombok.Data;

import java.util.List;

/**
 * RAG 检索结果中的单个知识块。
 */
@Data
public class ChunkResult {

    private Long chunkId;

    private Long brandId;

    private Long knowledgeId;

    private String knowledgeType;

    private String title;

    private String content;

    private String source;

    private double similarity;

    private Integer chunkIndex;
}
