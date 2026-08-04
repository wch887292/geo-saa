package com.geosaa.modules.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geosaa.ai.AiAdapterFactory;
import com.geosaa.common.exception.BusinessException;
import com.geosaa.config.RabbitMqConfig;
import com.geosaa.modules.content.dto.ContentGenerateRequest;
import com.geosaa.modules.content.entity.AiArticleContent;
import com.geosaa.modules.content.mapper.AiArticleContentMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentService {

    private final AiArticleContentMapper articleContentMapper;
    private final AiAdapterFactory aiAdapterFactory;
    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    // 敏感词库
    private static final List<String> SENSITIVE_WORDS = Arrays.asList(
        "敏感词1", "敏感词2", "违法内容", "赌博", "色情", "暴力"
    );

    // 行业模板
    private static final Map<String, String> INDUSTRY_TEMPLATES = new LinkedHashMap<>();

    static {
        INDUSTRY_TEMPLATES.put("科技", "科技行业GEO内容模板：聚焦技术创新、产品优势、行业解决方案");
        INDUSTRY_TEMPLATES.put("医疗", "医疗行业GEO内容模板：强调专业资质、临床数据、患者口碑");
        INDUSTRY_TEMPLATES.put("教育", "教育行业GEO内容模板：突出教学成果、师资力量、课程体系");
        INDUSTRY_TEMPLATES.put("金融", "金融行业GEO内容模板：展示风控能力、收益表现、合规资质");
        INDUSTRY_TEMPLATES.put("电商", "电商行业GEO内容模板：突出商品品质、物流服务、用户评价");
        INDUSTRY_TEMPLATES.put("法律", "法律行业GEO内容模板：强调专业领域、成功案例、服务流程");
    }

    public Page<AiArticleContent> listContents(int pageNum, int pageSize, String contentType, Integer status) {
        LambdaQueryWrapper<AiArticleContent> wrapper = new LambdaQueryWrapper<>();
        if (contentType != null) {
            wrapper.eq(AiArticleContent::getContentType, contentType);
        }
        if (status != null) {
            wrapper.eq(AiArticleContent::getStatus, status);
        }
        wrapper.orderByDesc(AiArticleContent::getCreateTime);
        return articleContentMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public AiArticleContent getContentById(Long id) {
        return articleContentMapper.selectById(id);
    }

    public AiArticleContent createContent(ContentGenerateRequest request, Long userId) {
        // 合规检测
        String complianceResult = checkCompliance(request.getContent());
        if (complianceResult != null) {
            throw new BusinessException("内容合规检测未通过: " + complianceResult);
        }

        AiArticleContent content = new AiArticleContent();
        content.setTitle(request.getTitle());
        content.setContentType(request.getContentType());
        content.setBrandName(request.getBrandName());
        content.setKeywords(request.getKeywords());
        content.setSummary(request.getSummary());
        content.setContent(request.getContent());
        content.setStatus(0);
        content.setCreatedBy(userId);
        articleContentMapper.insert(content);
        return content;
    }

    public AiArticleContent updateContent(AiArticleContent content) {
        articleContentMapper.updateById(content);
        return content;
    }

    public void deleteContent(Long id) {
        articleContentMapper.deleteById(id);
    }

    /**
     * 批量生成文章（使用 RabbitMQ 异步队列）
     */
    public List<Long> batchGenerateArticles(List<ContentGenerateRequest> requests, Long userId) {
        List<Long> contentIds = new ArrayList<>();
        for (ContentGenerateRequest request : requests) {
            // 先创建空内容记录
            AiArticleContent content = new AiArticleContent();
            content.setTitle(request.getTitle());
            content.setContentType(request.getContentType() != null ? request.getContentType() : "article");
            content.setBrandName(request.getBrandName());
            content.setKeywords(request.getKeywords());
            content.setSummary(request.getSummary());
            content.setStatus(0); // 待生成
            content.setCreatedBy(userId);
            articleContentMapper.insert(content);

            contentIds.add(content.getId());

            // 发送到消息队列异步生成
            Map<String, Object> message = new HashMap<>();
            message.put("contentId", content.getId());
            message.put("prompt", request.getTitle());
            message.put("contentType", request.getContentType());
            message.put("wordCount", 1000);
            message.put("userId", userId);
            if (rabbitTemplate != null) {
                try {
                    rabbitTemplate.convertAndSend(
                        RabbitMqConfig.EXCHANGE_CONTENT,
                        RabbitMqConfig.ROUTING_KEY_CONTENT_GENERATE,
                        objectMapper.writeValueAsString(message)
                    );
                } catch (JsonProcessingException e) {
                    log.error("序列化消息失败", e);
                }
            } else {
                log.warn("RabbitMQ 未启用，内容生成消息已跳过");
            }
        }
        log.info("批量生成文章: count={}, userId={}", requests.size(), userId);
        return contentIds;
    }

    /**
     * 批量生成短视频脚本
     */
    public List<AiArticleContent> batchGenerateScripts(List<ContentGenerateRequest> requests, Long userId) {
        return requests.stream().map(request -> {
            AiArticleContent content = new AiArticleContent();
            content.setTitle(request.getTitle());
            content.setContentType("script");
            content.setBrandName(request.getBrandName());
            content.setKeywords(request.getKeywords());
            content.setSummary(request.getSummary());
            content.setStatus(0);
            content.setCreatedBy(userId);
            articleContentMapper.insert(content);

            // 异步生成脚本内容
            asyncGenerateScript(content.getId(), request.getTitle(), request.getBrandName(), request.getKeywords());
            return content;
        }).collect(Collectors.toList());
    }

    @Async
    public void asyncGenerateScript(Long contentId, String title, String brandName, String keywords) {
        try {
            String prompt = "为品牌「" + (brandName != null ? brandName : "") + "」生成一个关于「" + title + "」的短视频脚本，关键词：" + keywords;
            String result = aiAdapterFactory.getAdapter("openai").generateContent(prompt, "短视频脚本", 800);

            AiArticleContent content = articleContentMapper.selectById(contentId);
            if (content != null) {
                content.setContent(result);
                content.setStatus(1);
                articleContentMapper.updateById(content);
            }
        } catch (Exception e) {
            log.error("异步生成脚本失败: contentId={}", contentId, e);
            AiArticleContent content = articleContentMapper.selectById(contentId);
            if (content != null) {
                content.setStatus(3);
                articleContentMapper.updateById(content);
            }
        }
    }

    /**
     * 行业模板管理 - 获取所有行业模板
     */
    public Map<String, String> getIndustryTemplates() {
        return INDUSTRY_TEMPLATES;
    }

    /**
     * 根据行业获取推荐模板
     */
    public String getTemplateByIndustry(String industry) {
        return INDUSTRY_TEMPLATES.getOrDefault(industry, "通用GEO内容模板：突出品牌核心价值、用户痛点解决方案、差异化优势");
    }

    /**
     * 内容合规检测（敏感词过滤）
     */
    public String checkCompliance(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        for (String word : SENSITIVE_WORDS) {
            if (content.contains(word)) {
                log.warn("内容包含敏感词: {}", word);
                return "内容包含敏感词汇: " + word;
            }
        }
        return null;
    }

    /**
     * AI 生成单篇内容（同步）
     */
    public AiArticleContent generateWithAi(Long contentId) {
        AiArticleContent content = articleContentMapper.selectById(contentId);
        if (content == null) {
            throw new BusinessException("内容记录不存在");
        }
        try {
            content.setStatus(1); // 生成中
            articleContentMapper.updateById(content);

            String prompt = content.getTitle() + " - " + (content.getSummary() != null ? content.getSummary() : "");
            String result = aiAdapterFactory.getAdapter("openai")
                    .generateContent(prompt, content.getContentType() != null ? content.getContentType() : "article", 1000);

            // 尝试解析 JSON 结果
            try {
                Map<String, Object> resultMap = objectMapper.readValue(result, Map.class);
                if (resultMap.containsKey("title")) {
                    content.setTitle((String) resultMap.get("title"));
                }
                if (resultMap.containsKey("content")) {
                    content.setContent((String) resultMap.get("content"));
                }
                if (resultMap.containsKey("keywords") && resultMap.get("keywords") instanceof List) {
                    content.setKeywords(String.join(",", (List<String>) resultMap.get("keywords")));
                }
            } catch (Exception e) {
                // 如果不是 JSON，直接存原始结果
                content.setContent(result);
            }

            content.setStatus(2); // 已完成
            articleContentMapper.updateById(content);
        } catch (Exception e) {
            log.error("AI生成内容失败: contentId={}", contentId, e);
            content.setStatus(3); // 失败
            articleContentMapper.updateById(content);
        }
        return content;
    }
}