package com.geosaa.modules.content.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geosaa.ai.AiAdapterFactory;
import com.geosaa.config.RabbitMqConfig;
import com.geosaa.modules.content.entity.AiArticleContent;
import com.geosaa.modules.content.mapper.AiArticleContentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 内容创作消息监听器 - 监听 RabbitMQ 队列，异步生成文章内容
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true", matchIfMissing = false)
public class ContentMessageListener {

    private final AiArticleContentMapper articleContentMapper;
    private final AiAdapterFactory aiAdapterFactory;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMqConfig.QUEUE_CONTENT_GENERATE)
    public void handleContentGenerate(String message) {
        log.info("接收到内容生成消息: {}", message);
        try {
            Map<String, Object> msgMap = objectMapper.readValue(message, Map.class);
            Long contentId = Long.valueOf(msgMap.get("contentId").toString());
            String prompt = (String) msgMap.get("prompt");
            String contentType = (String) msgMap.get("contentType");
            Integer wordCount = msgMap.get("wordCount") != null ?
                    Integer.valueOf(msgMap.get("wordCount").toString()) : 1000;

            AiArticleContent content = articleContentMapper.selectById(contentId);
            if (content == null) {
                log.warn("内容记录不存在: contentId={}", contentId);
                return;
            }

            // 更新状态为生成中
            content.setStatus(1);
            articleContentMapper.updateById(content);

            // 调用 AI 生成内容
            String result = aiAdapterFactory.getAdapter("openai")
                    .generateContent(prompt, contentType != null ? contentType : "article", wordCount);

            // 尝试解析 JSON 结果
            try {
                Map<String, Object> resultMap = objectMapper.readValue(result, Map.class);
                if (resultMap.containsKey("title")) {
                    content.setTitle((String) resultMap.get("title"));
                }
                if (resultMap.containsKey("content")) {
                    content.setContent((String) resultMap.get("content"));
                }
                if (resultMap.containsKey("keywords") && resultMap.get("keywords") instanceof java.util.List) {
                    content.setKeywords(String.join(",", (java.util.List<String>) resultMap.get("keywords")));
                }
            } catch (Exception e) {
                // 不是 JSON 格式，直接存原始结果
                content.setContent(result);
            }

            content.setStatus(2); // 已完成
            articleContentMapper.updateById(content);
            log.info("内容生成完成: contentId={}, title={}", contentId, content.getTitle());

        } catch (Exception e) {
            log.error("内容生成消息处理失败", e);
        }
    }
}