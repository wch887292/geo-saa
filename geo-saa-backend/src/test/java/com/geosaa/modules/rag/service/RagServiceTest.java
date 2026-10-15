package com.geosaa.modules.rag.service;

import com.geosaa.ai.AiAdapter;
import com.geosaa.ai.AiAdapterFactory;
import com.geosaa.modules.rag.dto.ChunkResult;
import com.geosaa.modules.rag.dto.RagQueryRequest;
import com.geosaa.modules.rag.dto.RagQueryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RagService 单测（O5 RAG）。
 * 覆盖：RAG 查询（检索+AI生成）、仅检索模式、无检索结果时的回答、检索耗时统计。
 */
@ExtendWith(MockitoExtension.class)
class RagServiceTest {

    @Mock
    private VectorStoreService vectorStoreService;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private AiAdapterFactory aiAdapterFactory;

    @Mock
    private AiAdapter aiAdapter;

    private RagService ragService;

    @BeforeEach
    void setUp() {
        ragService = new RagService(vectorStoreService, embeddingService, aiAdapterFactory);
        ReflectionTestUtils.setField(ragService, "maxWordCount", 500);
        ReflectionTestUtils.setField(ragService, "defaultTopK", 5);
        ReflectionTestUtils.setField(ragService, "defaultMinSimilarity", 0.3);
    }

    @Test
    void testQuery_withResults_andGenerateAnswer() {
        ChunkResult chunk1 = createChunk(1L, "产品介绍", "这是产品功能介绍", 0.95);
        ChunkResult chunk2 = createChunk(2L, "技术规格", "产品技术规格参数", 0.82);

        when(vectorStoreService.search(anyString(), any(), any(), anyInt(), anyDouble()))
            .thenReturn(Arrays.asList(chunk1, chunk2));
        when(embeddingService.getEmbeddingModel()).thenReturn("simulation-hash-v1");
        when(aiAdapterFactory.getAdapter("openai")).thenReturn(aiAdapter);
        when(aiAdapter.generateContent(anyString(), eq("RAG知识问答"), eq(500)))
            .thenReturn("{\"content\":\"基于知识库，产品具备以下功能...\"}");

        RagQueryRequest request = new RagQueryRequest();
        request.setQuery("产品有哪些功能？");
        request.setGenerateAnswer(true);

        RagQueryResponse response = ragService.query(request);

        assertNotNull(response);
        assertEquals("产品有哪些功能？", response.getQuery());
        assertEquals(2, response.getChunks().size());
        assertNotNull(response.getAnswer());
        assertTrue(response.getAnswer().contains("产品"));
        assertTrue(response.getRetrievalTimeMs() >= 0);
        assertTrue(response.getGenerationTimeMs() >= 0);
        assertEquals("simulation-hash-v1", response.getEmbeddingModel());

        verify(vectorStoreService, times(1)).search(eq("产品有哪些功能？"), isNull(), isNull(), eq(5), eq(0.3));
        verify(aiAdapter, times(1)).generateContent(anyString(), eq("RAG知识问答"), eq(500));
    }

    @Test
    void testQuery_generateAnswerFalse_returnsOnlyChunks() {
        ChunkResult chunk = createChunk(1L, "标题", "内容", 0.88);
        when(vectorStoreService.search(anyString(), any(), any(), anyInt(), anyDouble()))
            .thenReturn(Collections.singletonList(chunk));
        when(embeddingService.getEmbeddingModel()).thenReturn("simulation-hash-v1");

        RagQueryRequest request = new RagQueryRequest();
        request.setQuery("测试查询");
        request.setGenerateAnswer(false);

        RagQueryResponse response = ragService.query(request);

        assertNotNull(response);
        assertEquals(1, response.getChunks().size());
        assertNull(response.getAnswer());
        assertEquals(0, response.getGenerationTimeMs());

        verify(aiAdapterFactory, never()).getAdapter(anyString());
    }

    @Test
    void testQuery_noResults_returnsFallbackMessage() {
        when(vectorStoreService.search(anyString(), any(), any(), anyInt(), anyDouble()))
            .thenReturn(Collections.emptyList());
        when(embeddingService.getEmbeddingModel()).thenReturn("simulation-hash-v1");

        RagQueryRequest request = new RagQueryRequest();
        request.setQuery("不存在的内容");
        request.setGenerateAnswer(true);

        RagQueryResponse response = ragService.query(request);

        assertNotNull(response);
        assertTrue(response.getChunks().isEmpty());
        assertNotNull(response.getAnswer());
        assertTrue(response.getAnswer().contains("未检索到") || response.getAnswer().contains("知识库"));

        verify(aiAdapterFactory, never()).getAdapter(anyString());
    }

    @Test
    void testQuery_usesCustomTopKAndMinSimilarity() {
        when(vectorStoreService.search(anyString(), any(), any(), anyInt(), anyDouble()))
            .thenReturn(Collections.emptyList());
        when(embeddingService.getEmbeddingModel()).thenReturn("test-model");

        RagQueryRequest request = new RagQueryRequest();
        request.setQuery("测试");
        request.setTopK(10);
        request.setMinSimilarity(0.5);
        request.setGenerateAnswer(false);

        ragService.query(request);

        verify(vectorStoreService, times(1)).search(eq("测试"), isNull(), isNull(), eq(10), eq(0.5));
    }

    @Test
    void testQuery_withBrandAndTypeFilter() {
        when(vectorStoreService.search(anyString(), any(), any(), anyInt(), anyDouble()))
            .thenReturn(Collections.emptyList());
        when(embeddingService.getEmbeddingModel()).thenReturn("test-model");

        RagQueryRequest request = new RagQueryRequest();
        request.setQuery("测试");
        request.setBrandId(100L);
        request.setKnowledgeType("product");
        request.setGenerateAnswer(false);

        ragService.query(request);

        verify(vectorStoreService, times(1)).search(eq("测试"), eq(100L), eq("product"), eq(5), eq(0.3));
    }

    @Test
    void testRetrieveOnly_delegatesToVectorStore() {
        ChunkResult chunk = createChunk(1L, "标题", "内容", 0.9);
        when(vectorStoreService.search("查询", 100L, "type", 3, 0.5))
            .thenReturn(Collections.singletonList(chunk));

        List<ChunkResult> results = ragService.retrieveOnly("查询", 100L, "type", 3, 0.5);

        assertEquals(1, results.size());
        assertEquals(0.9, results.get(0).getSimilarity());
    }

    @Test
    void testQuery_aiGenerationFailure_returnsErrorMessage() {
        ChunkResult chunk = createChunk(1L, "标题", "内容", 0.9);
        when(vectorStoreService.search(anyString(), any(), any(), anyInt(), anyDouble()))
            .thenReturn(Collections.singletonList(chunk));
        when(embeddingService.getEmbeddingModel()).thenReturn("test-model");
        when(aiAdapterFactory.getAdapter("openai")).thenReturn(aiAdapter);
        when(aiAdapter.generateContent(anyString(), anyString(), anyInt()))
            .thenThrow(new RuntimeException("AI服务不可用"));

        RagQueryRequest request = new RagQueryRequest();
        request.setQuery("测试");
        request.setGenerateAnswer(true);

        RagQueryResponse response = ragService.query(request);

        assertNotNull(response.getAnswer());
        assertTrue(response.getAnswer().contains("失败") || response.getAnswer().contains("AI"));
    }

    @Test
    void testQuery_defaultGenerateAnswerIsTrue() {
        // 当 generateAnswer 为 null 时，默认为 true
        ChunkResult chunk = createChunk(1L, "标题", "内容", 0.9);
        when(vectorStoreService.search(anyString(), any(), any(), anyInt(), anyDouble()))
            .thenReturn(Collections.singletonList(chunk));
        when(embeddingService.getEmbeddingModel()).thenReturn("test-model");
        when(aiAdapterFactory.getAdapter("openai")).thenReturn(aiAdapter);
        when(aiAdapter.generateContent(anyString(), anyString(), anyInt()))
            .thenReturn("{\"content\":\"回答\"}");

        RagQueryRequest request = new RagQueryRequest();
        request.setQuery("测试");
        // generateAnswer 不设置，默认为 null → true

        RagQueryResponse response = ragService.query(request);

        assertNotNull(response.getAnswer());
        verify(aiAdapter, times(1)).generateContent(anyString(), anyString(), anyInt());
    }

    private ChunkResult createChunk(Long id, String title, String content, double similarity) {
        ChunkResult chunk = new ChunkResult();
        chunk.setChunkId(id);
        chunk.setBrandId(100L);
        chunk.setKnowledgeId(id);
        chunk.setKnowledgeType("product");
        chunk.setTitle(title);
        chunk.setContent(content);
        chunk.setSimilarity(similarity);
        chunk.setChunkIndex(0);
        return chunk;
    }
}
