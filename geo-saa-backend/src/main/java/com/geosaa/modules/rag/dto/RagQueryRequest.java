package com.geosaa.modules.rag.dto;

import lombok.Data;

import java.util.List;

/**
 * RAG 检索请求。
 */
@Data
public class RagQueryRequest {

    /** 查询文本 */
    private String query;

    /** 品牌ID过滤（可选） */
    private Long brandId;

    /** 知识类型过滤（可选） */
    private String knowledgeType;

    /** 返回 Top-K 结果，默认 5 */
    private Integer topK = 5;

    /** 相似度阈值，默认 0.3 */
    private Double minSimilarity = 0.3;

    /** 是否调用 AI 生成增强回答，默认 true */
    private Boolean generateAnswer = true;
}
