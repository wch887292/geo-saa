package com.geosaa.modules.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geosaa.common.exception.BusinessException;
import com.geosaa.modules.knowledge.entity.BrandKnowledge;
import com.geosaa.modules.knowledge.mapper.BrandKnowledgeMapper;
import com.geosaa.modules.rag.dto.ChunkResult;
import com.geosaa.modules.rag.entity.KnowledgeChunk;
import com.geosaa.modules.rag.mapper.KnowledgeChunkMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * VectorStoreService 单测（O5 RAG）。
 * 覆盖：索引知识（分块+embedding+存储）、检索（余弦相似度Top-K）、删除、空内容处理。
 */
@ExtendWith(MockitoExtension.class)
class VectorStoreServiceTest {

    @Mock
    private KnowledgeChunkMapper chunkMapper;

    @Mock
    private BrandKnowledgeMapper knowledgeMapper;

    private EmbeddingService embeddingService;
    private TextChunker textChunker;
    private VectorStoreService vectorStoreService;

    @BeforeEach
    void setUp() {
        embeddingService = new EmbeddingService(null, new com.fasterxml.jackson.databind.ObjectMapper());
        ReflectionTestUtils.setField(embeddingService, "simulationEnabled", true);
        ReflectionTestUtils.setField(embeddingService, "apiKey", "");
        ReflectionTestUtils.setField(embeddingService, "simulationDimensions", 64);

        textChunker = new TextChunker();
        ReflectionTestUtils.setField(textChunker, "chunkSize", 50);
        ReflectionTestUtils.setField(textChunker, "overlap", 10);

        vectorStoreService = new VectorStoreService(chunkMapper, knowledgeMapper, embeddingService, textChunker);
    }

    @Test
    void testIndexKnowledge_success() {
        BrandKnowledge knowledge = new BrandKnowledge();
        knowledge.setId(1L);
        knowledge.setBrandId(100L);
        knowledge.setKnowledgeType("product");
        knowledge.setTitle("测试知识");
        knowledge.setContent("这是一段测试知识内容，用于验证索引功能是否正常工作。包含足够多的文字以确保会被分块处理。");
        knowledge.setSource("测试来源");

        when(knowledgeMapper.selectById(1L)).thenReturn(knowledge);
        when(chunkMapper.insert(isA(KnowledgeChunk.class))).thenReturn(1);

        int count = vectorStoreService.indexKnowledge(1L);

        assertTrue(count > 0, "应索引至少1个chunk");
        verify(chunkMapper, times(count)).insert(isA(KnowledgeChunk.class));

        // 验证插入的 chunk 包含正确的元数据
        ArgumentCaptor<KnowledgeChunk> captor = ArgumentCaptor.forClass(KnowledgeChunk.class);
        verify(chunkMapper, atLeastOnce()).insert(captor.capture());
        KnowledgeChunk firstChunk = captor.getAllValues().get(0);
        assertEquals(100L, firstChunk.getBrandId());
        assertEquals(1L, firstChunk.getKnowledgeId());
        assertEquals("product", firstChunk.getKnowledgeType());
        assertEquals(0, firstChunk.getChunkIndex());
        assertNotNull(firstChunk.getEmbedding());
        assertTrue(firstChunk.getEmbedding().startsWith("["));
    }

    @Test
    void testIndexKnowledge_knowledgeNotFoundThrowsException() {
        when(knowledgeMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> vectorStoreService.indexKnowledge(999L));
    }

    @Test
    void testIndexKnowledge_emptyContentReturnsZero() {
        BrandKnowledge knowledge = new BrandKnowledge();
        knowledge.setId(1L);
        knowledge.setBrandId(100L);
        knowledge.setContent("");

        when(knowledgeMapper.selectById(1L)).thenReturn(knowledge);

        int count = vectorStoreService.indexKnowledge(1L);
        assertEquals(0, count);
        verify(chunkMapper, never()).insert(isA(KnowledgeChunk.class));
    }

    @Test
    void testSearch_returnsResultsSortedBySimilarity() {
        // 准备候选 chunk
        KnowledgeChunk chunk1 = createChunk(1L, 100L, "产品介绍", "这是关于产品功能的详细介绍，包含使用方法和技术规格。");
        KnowledgeChunk chunk2 = createChunk(2L, 100L, "公司新闻", "公司宣布获得新一轮融资，将用于研发投入和市场扩张。");
        KnowledgeChunk chunk3 = createChunk(3L, 100L, "产品介绍", "产品支持多种接口，兼容主流操作系统和硬件平台。");

        when(chunkMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(chunk1, chunk2, chunk3));
        when(knowledgeMapper.selectById(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            BrandKnowledge k = new BrandKnowledge();
            k.setId(id);
            k.setTitle("知识" + id);
            return k;
        });

        List<ChunkResult> results = vectorStoreService.search("产品功能和使用方法", 100L, null, 5, 0.0);

        assertNotNull(results);
        assertTrue(results.size() > 0);
        // 验证按相似度降序
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i - 1).getSimilarity() >= results.get(i).getSimilarity(),
                "结果应按相似度降序排列");
        }
    }

    @Test
    void testSearch_emptyQueryReturnsEmpty() {
        List<ChunkResult> results = vectorStoreService.search("", 100L, null, 5, 0.3);
        assertTrue(results.isEmpty());
        verify(chunkMapper, never()).selectList(any());
    }

    @Test
    void testSearch_noCandidatesReturnsEmpty() {
        when(chunkMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        List<ChunkResult> results = vectorStoreService.search("测试查询", 100L, null, 5, 0.3);
        assertTrue(results.isEmpty());
    }

    @Test
    void testSearch_respectsTopK() {
        List<KnowledgeChunk> candidates = Arrays.asList(
            createChunk(1L, 100L, "type1", "内容1"),
            createChunk(2L, 100L, "type1", "内容2"),
            createChunk(3L, 100L, "type1", "内容3")
        );
        when(chunkMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(candidates);
        when(knowledgeMapper.selectById(anyLong())).thenAnswer(invocation -> {
            BrandKnowledge k = new BrandKnowledge();
            k.setId(invocation.getArgument(0));
            k.setTitle("t");
            return k;
        });

        List<ChunkResult> results = vectorStoreService.search("查询", 100L, null, 2, 0.0);
        assertTrue(results.size() <= 2);
    }

    @Test
    void testDeleteByKnowledgeId() {
        when(chunkMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(3);

        vectorStoreService.deleteByKnowledgeId(1L);

        verify(chunkMapper, times(1)).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    void testCountByKnowledgeId() {
        when(chunkMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);

        long count = vectorStoreService.countByKnowledgeId(1L);
        assertEquals(5L, count);
    }

    @Test
    void testIndexBrandAll() {
        BrandKnowledge k1 = new BrandKnowledge();
        k1.setId(1L);
        k1.setBrandId(100L);
        k1.setStatus(1);
        k1.setContent("品牌知识1的内容，足够长以确保分块。");

        BrandKnowledge k2 = new BrandKnowledge();
        k2.setId(2L);
        k2.setBrandId(100L);
        k2.setStatus(1);
        k2.setContent("品牌知识2的内容，也足够长以确保分块处理。");

        when(knowledgeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(k1, k2));
        when(knowledgeMapper.selectById(1L)).thenReturn(k1);
        when(knowledgeMapper.selectById(2L)).thenReturn(k2);
        when(chunkMapper.insert(isA(KnowledgeChunk.class))).thenReturn(1);

        int total = vectorStoreService.indexBrandAll(100L);

        assertTrue(total > 0);
        verify(chunkMapper, atLeast(2)).insert(isA(KnowledgeChunk.class));
    }

    private KnowledgeChunk createChunk(Long id, Long brandId, String type, String content) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId(id);
        chunk.setBrandId(brandId);
        chunk.setKnowledgeId(id);
        chunk.setKnowledgeType(type);
        chunk.setChunkIndex(0);
        chunk.setContent(content);
        float[] vector = embeddingService.embed(content);
        chunk.setEmbedding(embeddingService.toJson(vector));
        return chunk;
    }
}
