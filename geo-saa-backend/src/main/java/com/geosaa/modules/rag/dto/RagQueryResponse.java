package com.geosaa.modules.rag.dto;

import lombok.Data;

import java.util.List;

/**
 * RAG 检索增强回答响应。
 */
@Data
public class RagQueryResponse {

    /** 用户原始查询 */
    private String query;

    /** AI 生成的增强回答（generateAnswer=false 时为 null） */
    private String answer;

    /** 检索到的相关知识块 */
    private List<ChunkResult> chunks;

    /** 检索耗时（毫秒） */
    private long retrievalTimeMs;

    /** AI 生成耗时（毫秒） */
    private long generationTimeMs;

    /** 使用的 embedding 模型 */
    private String embeddingModel;
}
