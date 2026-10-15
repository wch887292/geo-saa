package com.geosaa.modules.rag.controller;

import com.geosaa.common.Result;
import com.geosaa.modules.rag.dto.ChunkResult;
import com.geosaa.modules.rag.dto.RagQueryRequest;
import com.geosaa.modules.rag.dto.RagQueryResponse;
import com.geosaa.modules.rag.service.RagService;
import com.geosaa.modules.rag.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * RAG 检索增强生成 API。
 *
 * <p>提供知识索引、向量检索、RAG 问答等接口。
 */
@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;
    private final VectorStoreService vectorStoreService;

    /** RAG 检索增强问答：检索相关知识并调用 AI 生成回答 */
    @PostMapping("/query")
    public Result<RagQueryResponse> query(@RequestBody RagQueryRequest request) {
        return Result.success(ragService.query(request));
    }

    /** 纯向量检索：仅检索相关知识块，不调用 AI 生成回答 */
    @PostMapping("/retrieve")
    public Result<List<ChunkResult>> retrieve(@RequestBody Map<String, Object> params) {
        String query = (String) params.get("query");
        Long brandId = params.get("brandId") != null ? Long.valueOf(params.get("brandId").toString()) : null;
        String knowledgeType = (String) params.get("knowledgeType");
        int topK = params.get("topK") != null ? Integer.parseInt(params.get("topK").toString()) : 5;
        double minSim = params.get("minSimilarity") != null ? Double.parseDouble(params.get("minSimilarity").toString()) : 0.3;
        return Result.success(ragService.retrieveOnly(query, brandId, knowledgeType, topK, minSim));
    }

    /** 索引知识：将指定品牌知识分块并生成向量索引 */
    @PostMapping("/index/{knowledgeId}")
    public Result<Map<String, Object>> indexKnowledge(@PathVariable Long knowledgeId) {
        int chunks = vectorStoreService.indexKnowledge(knowledgeId);
        return Result.success(Map.of("knowledgeId", knowledgeId, "indexedChunks", chunks));
    }

    /** 品牌全量索引：重新索引指定品牌下所有已启用的知识 */
    @PostMapping("/index/brand/{brandId}")
    public Result<Map<String, Object>> indexBrand(@PathVariable Long brandId) {
        int chunks = vectorStoreService.indexBrandAll(brandId);
        return Result.success(Map.of("brandId", brandId, "indexedChunks", chunks));
    }

    /** 删除知识索引：删除指定知识的所有向量分块 */
    @DeleteMapping("/index/{knowledgeId}")
    public Result<Map<String, Object>> deleteIndex(@PathVariable Long knowledgeId) {
        vectorStoreService.deleteByKnowledgeId(knowledgeId);
        return Result.success(Map.of("knowledgeId", knowledgeId, "deleted", true));
    }

    /** 查询索引状态：查询指定知识的向量分块数量 */
    @GetMapping("/index/status/{knowledgeId}")
    public Result<Map<String, Object>> indexStatus(@PathVariable Long knowledgeId) {
        long count = vectorStoreService.countByKnowledgeId(knowledgeId);
        return Result.success(Map.of("knowledgeId", knowledgeId, "chunkCount", count));
    }
}
