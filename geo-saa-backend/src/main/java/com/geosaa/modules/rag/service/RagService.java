package com.geosaa.modules.rag.service;

import com.geosaa.ai.AiAdapterFactory;
import com.geosaa.modules.rag.dto.ChunkResult;
import com.geosaa.modules.rag.dto.RagQueryRequest;
import com.geosaa.modules.rag.dto.RagQueryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 检索增强生成服务。
 *
 * <p>流程：用户查询 → 向量检索 Top-K 相关知识 → 拼入 prompt → AI 生成回答 → 返回引用来源。
 * 当 generateAnswer=false 时仅返回检索结果，不调用 AI。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final VectorStoreService vectorStoreService;
    private final EmbeddingService embeddingService;
    private final AiAdapterFactory aiAdapterFactory;

    @Value("${rag.answer.max-word-count:800}")
    private int maxWordCount;

    @Value("${rag.default-top-k:5}")
    private int defaultTopK;

    @Value("${rag.default-min-similarity:0.3}")
    private double defaultMinSimilarity;

    /**
     * RAG 检索增强问答。
     */
    public RagQueryResponse query(RagQueryRequest request) {
        long start = System.currentTimeMillis();
        RagQueryResponse response = new RagQueryResponse();
        response.setQuery(request.getQuery());
        response.setEmbeddingModel(embeddingService.getEmbeddingModel());

        int topK = request.getTopK() != null ? request.getTopK() : defaultTopK;
        double minSim = request.getMinSimilarity() != null ? request.getMinSimilarity() : defaultMinSimilarity;

        // 1. 向量检索
        List<ChunkResult> chunks = vectorStoreService.search(
            request.getQuery(),
            request.getBrandId(),
            request.getKnowledgeType(),
            topK,
            minSim
        );
        response.setChunks(chunks);
        response.setRetrievalTimeMs(System.currentTimeMillis() - start);

        // 2. 可选：AI 生成增强回答
        boolean generate = request.getGenerateAnswer() == null || request.getGenerateAnswer();
        if (generate && !chunks.isEmpty()) {
            long genStart = System.currentTimeMillis();
            String answer = generateAnswer(request.getQuery(), chunks);
            response.setAnswer(answer);
            response.setGenerationTimeMs(System.currentTimeMillis() - genStart);
        } else if (generate && chunks.isEmpty()) {
            response.setAnswer("未检索到与问题相关的知识库内容，请尝试换一种问法，或先在知识库中添加相关内容。");
            response.setGenerationTimeMs(0);
        }

        log.info("RAG 查询完成: query='{}', chunks={}, generate={}, totalMs={}",
            request.getQuery().length() > 50 ? request.getQuery().substring(0, 50) + "..." : request.getQuery(),
            chunks.size(), generate, System.currentTimeMillis() - start);
        return response;
    }

    /**
     * 仅检索不生成回答。
     */
    public List<ChunkResult> retrieveOnly(String query, Long brandId, String knowledgeType, int topK, double minSimilarity) {
        return vectorStoreService.search(query, brandId, knowledgeType, topK, minSimilarity);
    }

    /**
     * 构建 RAG prompt 并调用 AI 生成回答。
     */
    private String generateAnswer(String query, List<ChunkResult> chunks) {
        StringBuilder context = new StringBuilder();
        context.append("你是一个专业的品牌知识助手。请基于以下检索到的知识库内容回答用户问题。\n");
        context.append("要求：\n");
        context.append("1. 仅使用提供的知识库内容回答，不要编造信息\n");
        context.append("2. 如果知识库内容不足以回答问题，请明确说明\n");
        context.append("3. 回答要简洁、准确、结构化\n");
        context.append("4. 引用来源时注明知识标题\n\n");
        context.append("【知识库内容】\n");

        for (int i = 0; i < chunks.size(); i++) {
            ChunkResult chunk = chunks.get(i);
            context.append(String.format("[%d] 标题: %s | 类型: %s | 相似度: %.2f\n",
                i + 1,
                chunk.getTitle() != null ? chunk.getTitle() : "未知",
                chunk.getKnowledgeType() != null ? chunk.getKnowledgeType() : "未知",
                chunk.getSimilarity()));
            context.append("内容: ").append(chunk.getContent()).append("\n\n");
        }

        context.append("【用户问题】\n").append(query).append("\n\n");
        context.append("请基于以上知识库内容回答：");

        try {
            String rawResponse = aiAdapterFactory.getAdapter("openai")
                .generateContent(context.toString(), "RAG知识问答", maxWordCount);
            return extractAnswer(rawResponse);
        } catch (Exception e) {
            log.error("RAG AI 回答生成失败", e);
            return "AI 回答生成失败: " + e.getMessage();
        }
    }

    /**
     * 从 AI 原始响应中提取回答文本。
     * AI 可能返回 JSON 格式 {"content": "..."} 或纯文本。
     */
    private String extractAnswer(String rawResponse) {
        if (rawResponse == null || rawResponse.isEmpty()) {
            return "";
        }
        // 尝试解析 JSON 格式
        try {
            com.fasterxml.jackson.databind.JsonNode root =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(rawResponse);
            if (root.has("choices") && root.get("choices").size() > 0) {
                return root.get("choices").get(0).get("message").get("content").asText();
            }
            if (root.has("content")) {
                return root.get("content").asText();
            }
            if (root.has("answer")) {
                return root.get("answer").asText();
            }
        } catch (Exception ignored) {
            // 不是 JSON，返回原文
        }
        return rawResponse;
    }
}
