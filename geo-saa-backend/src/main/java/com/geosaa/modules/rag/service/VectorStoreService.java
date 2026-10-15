package com.geosaa.modules.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geosaa.common.exception.BusinessException;
import com.geosaa.modules.knowledge.entity.BrandKnowledge;
import com.geosaa.modules.knowledge.mapper.BrandKnowledgeMapper;
import com.geosaa.modules.rag.dto.ChunkResult;
import com.geosaa.modules.rag.entity.KnowledgeChunk;
import com.geosaa.modules.rag.mapper.KnowledgeChunkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 向量存储服务。
 *
 * <p>负责知识的索引（分块 + embedding + 存储）、检索（余弦相似度 Top-K）、删除。
 * 向量存储在 MySQL knowledge_chunk 表的 embedding 字段（JSON 数组），
 * 检索时加载候选块到内存计算余弦相似度。品牌知识库场景数据量小，性能够用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreService {

    private final KnowledgeChunkMapper chunkMapper;
    private final BrandKnowledgeMapper knowledgeMapper;
    private final EmbeddingService embeddingService;
    private final TextChunker textChunker;

    /**
     * 索引一条品牌知识：分块 → 生成 embedding → 存储。
     * 先删除该知识的旧 chunk，再重新索引。
     *
     * @param knowledgeId 知识ID
     * @return 索引的 chunk 数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int indexKnowledge(Long knowledgeId) {
        BrandKnowledge knowledge = knowledgeMapper.selectById(knowledgeId);
        if (knowledge == null) {
            throw new BusinessException("知识记录不存在: " + knowledgeId);
        }
        if (knowledge.getContent() == null || knowledge.getContent().trim().isEmpty()) {
            log.warn("知识内容为空，跳过索引: knowledgeId={}", knowledgeId);
            return 0;
        }

        // 删除旧 chunk
        deleteByKnowledgeId(knowledgeId);

        // 分块
        List<String> chunks = textChunker.chunk(knowledge.getContent());
        if (chunks.isEmpty()) {
            return 0;
        }

        // 批量生成 embedding 并存储
        String model = embeddingService.getEmbeddingModel();
        int count = 0;
        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            float[] vector = embeddingService.embed(chunkText);

            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setBrandId(knowledge.getBrandId());
            chunk.setKnowledgeId(knowledgeId);
            chunk.setKnowledgeType(knowledge.getKnowledgeType());
            chunk.setChunkIndex(i);
            chunk.setContent(chunkText);
            chunk.setEmbedding(embeddingService.toJson(vector));
            chunk.setEmbeddingModel(model);
            chunk.setSource(knowledge.getSource());
            chunkMapper.insert(chunk);
            count++;
        }

        log.info("知识索引完成: knowledgeId={}, brandId={}, chunks={}, model={}",
            knowledgeId, knowledge.getBrandId(), count, model);
        return count;
    }

    /**
     * 检索与查询最相似的 Top-K 知识块。
     *
     * @param query          查询文本
     * @param brandId        品牌ID过滤（可选）
     * @param knowledgeType  知识类型过滤（可选）
     * @param topK           返回数量
     * @param minSimilarity  最小相似度阈值
     * @return 按相似度降序排列的知识块列表
     */
    public List<ChunkResult> search(String query, Long brandId, String knowledgeType,
                                     int topK, double minSimilarity) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // 生成查询向量
        float[] queryVector = embeddingService.embed(query);

        // 从数据库加载候选 chunk（按品牌/类型过滤，减少内存计算量）
        LambdaQueryWrapper<KnowledgeChunk> wrapper = new LambdaQueryWrapper<>();
        if (brandId != null) {
            wrapper.eq(KnowledgeChunk::getBrandId, brandId);
        }
        if (knowledgeType != null && !knowledgeType.isEmpty()) {
            wrapper.eq(KnowledgeChunk::getKnowledgeType, knowledgeType);
        }
        wrapper.orderByDesc(KnowledgeChunk::getCreateTime);
        // 限制候选数量，避免全表加载
        wrapper.last("LIMIT 500");

        List<KnowledgeChunk> candidates = chunkMapper.selectList(wrapper);
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        // 内存计算余弦相似度
        List<ChunkResult> results = new ArrayList<>();
        for (KnowledgeChunk chunk : candidates) {
            float[] chunkVector = embeddingService.fromJson(chunk.getEmbedding());
            double similarity = embeddingService.cosineSimilarity(queryVector, chunkVector);
            if (similarity >= minSimilarity) {
                ChunkResult result = new ChunkResult();
                result.setChunkId(chunk.getId());
                result.setBrandId(chunk.getBrandId());
                result.setKnowledgeId(chunk.getKnowledgeId());
                result.setKnowledgeType(chunk.getKnowledgeType());
                result.setContent(chunk.getContent());
                result.setSource(chunk.getSource());
                result.setSimilarity(Math.round(similarity * 10000) / 10000.0);
                result.setChunkIndex(chunk.getChunkIndex());
                // 关联知识标题
                BrandKnowledge knowledge = knowledgeMapper.selectById(chunk.getKnowledgeId());
                if (knowledge != null) {
                    result.setTitle(knowledge.getTitle());
                }
                results.add(result);
            }
        }

        // 按相似度降序，取 Top-K
        results.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
        if (results.size() > topK) {
            results = results.subList(0, topK);
        }

        log.info("RAG 检索: query='{}', candidates={}, results={}, topK={}",
            query.length() > 50 ? query.substring(0, 50) + "..." : query,
            candidates.size(), results.size(), topK);
        return results;
    }

    /**
     * 删除某条知识的所有 chunk。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteByKnowledgeId(Long knowledgeId) {
        LambdaQueryWrapper<KnowledgeChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeChunk::getKnowledgeId, knowledgeId);
        int deleted = chunkMapper.delete(wrapper);
        if (deleted > 0) {
            log.info("删除知识 chunk: knowledgeId={}, deleted={}", knowledgeId, deleted);
        }
    }

    /**
     * 删除某品牌的所有 chunk。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteByBrandId(Long brandId) {
        LambdaQueryWrapper<KnowledgeChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeChunk::getBrandId, brandId);
        int deleted = chunkMapper.delete(wrapper);
        log.info("删除品牌 chunk: brandId={}, deleted={}", brandId, deleted);
    }

    /**
     * 获取某知识的 chunk 数量。
     */
    public long countByKnowledgeId(Long knowledgeId) {
        LambdaQueryWrapper<KnowledgeChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeChunk::getKnowledgeId, knowledgeId);
        return chunkMapper.selectCount(wrapper);
    }

    /**
     * 获取某品牌的 chunk 数量。
     */
    public long countByBrandId(Long brandId) {
        LambdaQueryWrapper<KnowledgeChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeChunk::getBrandId, brandId);
        return chunkMapper.selectCount(wrapper);
    }

    /**
     * 批量索引品牌下所有知识。
     */
    @Transactional(rollbackFor = Exception.class)
    public int indexBrandAll(Long brandId) {
        LambdaQueryWrapper<BrandKnowledge> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BrandKnowledge::getBrandId, brandId);
        wrapper.eq(BrandKnowledge::getStatus, 1);
        List<BrandKnowledge> knowledgeList = knowledgeMapper.selectList(wrapper);

        int total = 0;
        for (BrandKnowledge k : knowledgeList) {
            total += indexKnowledge(k.getId());
        }
        log.info("品牌全量索引完成: brandId={}, knowledgeCount={}, totalChunks={}",
            brandId, knowledgeList.size(), total);
        return total;
    }
}
