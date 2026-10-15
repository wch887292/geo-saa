package com.geosaa.modules.rag.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EmbeddingService 单测（O5 RAG）。
 * 覆盖：模拟模式向量生成、确定性（相同文本相同向量）、余弦相似度、JSON 序列化/反序列化、空文本处理。
 */
class EmbeddingServiceTest {

    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        embeddingService = new EmbeddingService(null, new com.fasterxml.jackson.databind.ObjectMapper());
        // 强制模拟模式
        ReflectionTestUtils.setField(embeddingService, "simulationEnabled", true);
        ReflectionTestUtils.setField(embeddingService, "apiKey", "");
        ReflectionTestUtils.setField(embeddingService, "simulationDimensions", 256);
        ReflectionTestUtils.setField(embeddingService, "dimensions", 1536);
    }

    @Test
    void testEmbed_returnsCorrectDimension() {
        float[] vector = embeddingService.embed("测试文本");
        assertNotNull(vector);
        assertEquals(256, vector.length);
    }

    @Test
    void testEmbed_deterministic_sameTextSameVector() {
        float[] v1 = embeddingService.embed("相同的文本内容");
        float[] v2 = embeddingService.embed("相同的文本内容");
        assertArrayEquals(v1, v2, 0.0001f);
    }

    @Test
    void testEmbed_differentTextDifferentVector() {
        float[] v1 = embeddingService.embed("文本A");
        float[] v2 = embeddingService.embed("完全不同的文本B");
        boolean hasDifference = false;
        for (int i = 0; i < v1.length; i++) {
            if (Math.abs(v1[i] - v2[i]) > 0.001) {
                hasDifference = true;
                break;
            }
        }
        assertTrue(hasDifference, "不同文本应生成不同向量");
    }

    @Test
    void testEmbed_emptyTextReturnsZeroVector() {
        float[] vector = embeddingService.embed("");
        assertNotNull(vector);
        assertEquals(256, vector.length);
        for (float v : vector) {
            assertEquals(0f, v, 0.0001f);
        }
    }

    @Test
    void testEmbed_nullTextReturnsZeroVector() {
        float[] vector = embeddingService.embed(null);
        assertNotNull(vector);
        assertEquals(256, vector.length);
    }

    @Test
    void testCosineSimilarity_identicalVectorsReturnsOne() {
        float[] v = embeddingService.embed("测试");
        double sim = embeddingService.cosineSimilarity(v, v);
        assertEquals(1.0, sim, 0.0001);
    }

    @Test
    void testCosineSimilarity_zeroVectorReturnsZero() {
        float[] v1 = new float[256];
        float[] v2 = embeddingService.embed("测试");
        double sim = embeddingService.cosineSimilarity(v1, v2);
        assertEquals(0.0, sim, 0.0001);
    }

    @Test
    void testCosineSimilarity_differentDimensionsReturnsZero() {
        float[] v1 = new float[100];
        float[] v2 = new float[200];
        double sim = embeddingService.cosineSimilarity(v1, v2);
        assertEquals(0.0, sim, 0.0001);
    }

    @Test
    void testToJson_andFromJson_roundTrip() {
        float[] original = embeddingService.embed("往返测试");
        String json = embeddingService.toJson(original);
        assertNotNull(json);
        assertTrue(json.startsWith("["));
        assertTrue(json.endsWith("]"));

        float[] restored = embeddingService.fromJson(json);
        assertArrayEquals(original, restored, 0.0001f);
    }

    @Test
    void testFromJson_emptyJsonReturnsZeroVector() {
        float[] vector = embeddingService.fromJson("[]");
        assertNotNull(vector);
        assertEquals(256, vector.length);
    }

    @Test
    void testFromJson_nullReturnsZeroVector() {
        float[] vector = embeddingService.fromJson(null);
        assertNotNull(vector);
        assertEquals(256, vector.length);
    }

    @Test
    void testEmbedBatch_returnsCorrectCount() {
        List<float[]> results = embeddingService.embedBatch(List.of("文本1", "文本2", "文本3"));
        assertEquals(3, results.size());
        for (float[] v : results) {
            assertEquals(256, v.length);
        }
    }

    @Test
    void testGetEmbeddingModel_simulationMode() {
        assertEquals("simulation-hash-v1", embeddingService.getEmbeddingModel());
    }

    @Test
    void testGetDimension_simulationMode() {
        assertEquals(256, embeddingService.getDimension());
    }
}
