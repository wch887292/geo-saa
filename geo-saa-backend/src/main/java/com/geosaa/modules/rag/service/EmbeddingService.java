package com.geosaa.modules.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Embedding 向量生成服务。
 *
 * <p>支持两种模式：
 * <ul>
 *   <li>真实模式：调用 OpenAI text-embedding API（复用 aiRestTemplate）</li>
 *   <li>模拟模式：无 API Key 时用确定性哈希生成伪向量，保证测试可复现</li>
 * </ul>
 *
 * <p>向量维度：OpenAI text-embedding-3-small = 1536 维；模拟模式默认 256 维。
 */
@Slf4j
@Service
public class EmbeddingService {

    @Value("${ai.openai.api-key:}")
    private String apiKey;

    @Value("${ai.openai.api-url:https://api.openai.com/v1}")
    private String apiUrl;

    @Value("${rag.embedding.model:text-embedding-3-small}")
    private String embeddingModel;

    @Value("${rag.embedding.dimensions:1536}")
    private int dimensions;

    @Value("${rag.embedding.simulation-dimensions:256}")
    private int simulationDimensions;

    @Value("${ai.simulation.enabled:true}")
    private boolean simulationEnabled;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public EmbeddingService(@Qualifier("aiRestTemplate") RestTemplate restTemplate,
                            ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 为单条文本生成 embedding 向量。
     */
    public float[] embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new float[getDimension()];
        }
        if (shouldSimulate()) {
            return simulateEmbed(text);
        }
        try {
            return callOpenAiEmbed(text);
        } catch (Exception e) {
            log.warn("OpenAI embedding 调用失败，降级为模拟模式: {}", e.getMessage());
            return simulateEmbed(text);
        }
    }

    /**
     * 批量生成 embedding。
     */
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> results = new ArrayList<>();
        for (String text : texts) {
            results.add(embed(text));
        }
        return results;
    }

    /**
     * 向量转 JSON 字符串存储。
     */
    public String toJson(float[] vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (Exception e) {
            log.error("向量序列化失败", e);
            return "[]";
        }
    }

    /**
     * JSON 字符串转向量。
     */
    public float[] fromJson(String json) {
        if (json == null || json.isEmpty() || "[]".equals(json)) {
            return new float[getDimension()];
        }
        try {
            return objectMapper.readValue(json, float[].class);
        } catch (Exception e) {
            log.warn("向量反序列化失败，返回零向量: {}", e.getMessage());
            return new float[getDimension()];
        }
    }

    /**
     * 余弦相似度计算。
     */
    public double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public int getDimension() {
        return shouldSimulate() ? simulationDimensions : dimensions;
    }

    public String getEmbeddingModel() {
        return shouldSimulate() ? "simulation-hash-v1" : embeddingModel;
    }

    private boolean shouldSimulate() {
        return simulationEnabled || apiKey == null || apiKey.isEmpty();
    }

    /**
     * 调用 OpenAI text-embedding API。
     */
    private float[] callOpenAiEmbed(String text) throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", embeddingModel);
        requestBody.put("input", text);
        requestBody.put("dimensions", dimensions);

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        org.springframework.http.HttpEntity<Map<String, Object>> entity =
            new org.springframework.http.HttpEntity<>(requestBody, headers);
        String response = restTemplate.postForObject(apiUrl + "/embeddings", entity, String.class);

        JsonNode root = objectMapper.readTree(response);
        JsonNode embeddingNode = root.path("data").get(0).path("embedding");
        float[] vector = new float[dimensions];
        for (int i = 0; i < dimensions && i < embeddingNode.size(); i++) {
            vector[i] = (float) embeddingNode.get(i).asDouble();
        }
        return vector;
    }

    /**
     * 模拟 embedding：基于文本 SHA-256 哈希生成确定性伪向量。
     * 相同文本永远生成相同向量，保证测试可复现和检索一致性。
     */
    private float[] simulateEmbed(String text) {
        float[] vector = new float[simulationDimensions];
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            // 多轮哈希填充整个向量空间
            byte[] seed = text.getBytes(StandardCharsets.UTF_8);
            int filled = 0;
            int round = 0;
            while (filled < simulationDimensions) {
                byte[] input = (round == 0) ? seed :
                    concat(md.digest(seed), String.valueOf(round).getBytes(StandardCharsets.UTF_8));
                byte[] hash = md.digest(input);
                // 每个 byte 映射为 [-1, 1] 范围的 float
                for (int i = 0; i < hash.length && filled < simulationDimensions; i++) {
                    vector[filled++] = ((hash[i] & 0xFF) / 127.5f) - 1.0f;
                }
                round++;
            }
            // L2 归一化
            double norm = 0;
            for (float v : vector) {
                norm += v * v;
            }
            norm = Math.sqrt(norm);
            if (norm > 0) {
                for (int i = 0; i < vector.length; i++) {
                    vector[i] /= (float) norm;
                }
            }
        } catch (Exception e) {
            log.error("模拟 embedding 生成失败", e);
        }
        return vector;
    }

    private byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
