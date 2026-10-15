package com.geosaa.modules.content.service;

import com.geosaa.modules.ai.dto.AiChatRequest;
import com.geosaa.modules.ai.dto.AiChatResponse;
import com.geosaa.modules.ai.service.AiChatService;
import com.geosaa.modules.ai.service.AiPromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * GEO+AAO 内容优化改写服务
 *
 * <p>基于提示词2（GEO/AAO页面内容优化改写器），实现：
 * <ul>
 *   <li>TL;DR 一句话摘要生成</li>
 *   <li>标题层级规范化（唯一H1，H2/H3逐级拆分）</li>
 *   <li>FAQ 模块自动生成（用户口语化问答）</li>
 *   <li>长文本拆分（短句、列表、对比表格）</li>
 *   <li>证据锚点强化（案例、参数、证书、实测结果）</li>
 *   <li>实体信息一致性校验</li>
 *   <li>输出：优化后完整文案 + GEO/AAO改动说明清单</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeoAaoContentService {

    private final AiChatService aiChatService;
    private final AiPromptTemplateService promptTemplateService;

    /**
     * GEO+AAO 内容优化改写
     *
     * @param pageTitle     页面标题
     * @param originalContent 原始内容
     * @param brandEntities 品牌实体基准信息（名称、地址、联系方式、产品参数等）
     * @return 优化后的内容 + 改动说明
     */
    public ContentRewriteResult rewriteContent(String pageTitle, String originalContent, String brandEntities) {
        log.info("开始GEO+AAO内容改写: pageTitle={}, contentLength={}", pageTitle,
            originalContent != null ? originalContent.length() : 0);

        // 渲染内容改写器模板
        Map<String, String> variables = new HashMap<>();
        variables.put("page_title", pageTitle != null ? pageTitle : "未命名页面");
        variables.put("original_content", originalContent != null ? originalContent : "（无原始内容）");
        variables.put("brand_entities", brandEntities != null ? brandEntities : "品牌实体信息待补充");

        String renderedPrompt = promptTemplateService.renderTemplate("geo_aao_content_rewriter", variables);
        if (renderedPrompt == null) {
            log.warn("未找到geo_aao_content_rewriter模板，使用通用改写提示词");
            renderedPrompt = buildFallbackPrompt(pageTitle, originalContent, brandEntities);
        }

        // 调用 AI 大模型改写
        AiChatRequest request = new AiChatRequest();
        request.setUserInput(renderedPrompt);
        request.setBusinessModule("content");
        request.setBusinessType("geo_aao_rewrite");
        request.setMaxTokens(4096);
        request.setTemperature(0.5);

        AiChatResponse response = aiChatService.chat(request);

        ContentRewriteResult result = new ContentRewriteResult();
        result.setOriginalContent(originalContent);
        result.setPageTitle(pageTitle);

        if (response.getSuccess() && response.getContent() != null) {
            // 解析优化后内容和改动说明
            String content = response.getContent();
            result.setRewrittenContent(extractSection(content, "优化后完整页面文案", "GEO/AAO改动说明"));
            result.setChangeNotes(extractSection(content, "GEO/AAO改动说明", null));
            if (result.getRewrittenContent() == null || result.getRewrittenContent().isEmpty()) {
                result.setRewrittenContent(content);
            }
            result.setSuccess(true);
            result.setModelName(response.getModelName());
            result.setTotalTokens(response.getTotalTokens());
            result.setLatencyMs(response.getLatencyMs());
            log.info("GEO+AAO内容改写完成: tokens={}, latency={}ms", response.getTotalTokens(), response.getLatencyMs());
        } else {
            result.setSuccess(false);
            result.setErrorMessage(response.getErrorMessage());
            result.setRewrittenContent(originalContent);
            log.warn("GEO+AAO内容改写失败: {}", response.getErrorMessage());
        }

        return result;
    }

    /**
     * 生成 TL;DR 摘要
     */
    public String generateTldr(String content, String brandName) {
        String prompt = String.format(
            "请为以下页面内容生成一句话TL;DR摘要，放在页面最顶部，便于LLM直接摘取作为AI回答。\n" +
            "要求：1.一句话给出核心结论 2.包含品牌「%s」 3.不超过80字 4.信息密度高\n\n" +
            "页面内容：\n%s",
            brandName, content
        );
        return aiChatService.generateText("你是GEO+AAO内容优化专家", prompt, "content", "tldr_generate");
    }

    /**
     * 生成 FAQ 模块
     */
    public FaqResult generateFaq(String content, String brandName, int faqCount) {
        String prompt = String.format(
            "请基于以下页面内容，为品牌「%s」生成%d个FAQ问答，专门捕获AI问答问句。\n" +
            "要求：\n" +
            "1.问题使用用户口语化表达（选型、优缺点、价格、适用场景、注意事项、对比）\n" +
            "2.回答简洁准确，基于页面内容，不编造信息\nn" +
            "3.每个问答包含问题和答案\n" +
            "4.输出JSON格式：[{\"question\":\"...\",\"answer\":\"...\"}]\n\n" +
            "页面内容：\n%s",
            brandName, faqCount, content
        );
        String result = aiChatService.generateText("你是GEO+AAO内容优化专家，擅长FAQ问答设计", prompt, "content", "faq_generate");

        FaqResult faqResult = new FaqResult();
        faqResult.setBrandName(brandName);
        faqResult.setRawResponse(result);
        return faqResult;
    }

    /**
     * 兜底提示词（模板未找到时使用）
     */
    private String buildFallbackPrompt(String pageTitle, String content, String brandEntities) {
        return String.format(
            "你是GEO&AAO内容优化专家，请基于GEO+AAO方法论改写以下页面文案。\n\n" +
            "改写强制规则：\n" +
            "1.页面最开头增加TL;DR简短摘要，一句话给出核心结论\n" +
            "2.保留原有业务事实、参数、品牌信息，不得编造\n" +
            "3.标题层级规范：唯一H1，H2/H3逐级拆分，把用户高频AI问句直接做成H2标题\n" +
            "4.增加FAQ板块，整理用户口语化问题，一问一答\n" +
            "5.长文本拆分成短句、列表、对比表格\n" +
            "6.优先强化证据锚点：案例、参数、证书、实测结果\n" +
            "7.不堆砌关键词，语言自然\n\n" +
            "页面标题：%s\n品牌实体信息：%s\n原始内容：\n%s\n\n" +
            "请输出：【优化后完整页面文案】+【GEO/AAO改动说明清单】",
            pageTitle, brandEntities, content
        );
    }

    /**
     * 从 AI 响应中提取指定段落
     */
    private String extractSection(String content, String startMarker, String endMarker) {
        if (content == null) return null;
        int start = content.indexOf(startMarker);
        if (start < 0) return null;
        start = content.indexOf('\n', start);
        if (start < 0) return null;
        start++;

        int end;
        if (endMarker != null) {
            end = content.indexOf(endMarker, start);
            if (end < 0) end = content.length();
        } else {
            end = content.length();
        }

        return content.substring(start, end).trim();
    }

    /**
     * 内容改写结果
     */
    @lombok.Data
    public static class ContentRewriteResult {
        private boolean success;
        private String pageTitle;
        private String originalContent;
        private String rewrittenContent;
        private String changeNotes;
        private String errorMessage;
        private String modelName;
        private Integer totalTokens;
        private Long latencyMs;
    }

    /**
     * FAQ 生成结果
     */
    @lombok.Data
    public static class FaqResult {
        private String brandName;
        private String rawResponse;
    }
}
