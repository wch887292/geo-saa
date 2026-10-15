package com.geosaa.modules.openapi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geosaa.common.exception.BusinessException;
import com.geosaa.modules.openapi.entity.WebhookSubscription;
import com.geosaa.modules.openapi.mapper.WebhookSubscriptionMapper;
import com.geosaa.modules.openapi.webhook.WebhookEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * Webhook 订阅管理服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookSubscriptionService {

    private final WebhookSubscriptionMapper subscriptionMapper;
    private final ObjectMapper objectMapper;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 创建订阅。
     */
    @Transactional(rollbackFor = Exception.class)
    public WebhookSubscription create(String name, String url, List<String> eventTypes,
                                       String description) {
        if (url == null || !url.startsWith("http")) {
            throw new BusinessException("回调 URL 格式不正确");
        }
        if (eventTypes == null || eventTypes.isEmpty()) {
            throw new BusinessException("至少订阅一个事件类型");
        }
        // 验证事件类型
        for (String et : eventTypes) {
            if (WebhookEventType.fromCode(et) == null) {
                throw new BusinessException("未知的事件类型: " + et);
            }
        }

        WebhookSubscription sub = new WebhookSubscription();
        sub.setName(name);
        sub.setUrl(url);
        sub.setEventTypes(toJson(eventTypes));
        sub.setSecret(generateSecret());
        sub.setStatus(1);
        sub.setDescription(description);
        subscriptionMapper.insert(sub);

        log.info("创建 Webhook 订阅: id={}, name={}, url={}, events={}", sub.getId(), name, url, eventTypes);
        return sub;
    }

    /**
     * 更新订阅。
     */
    @Transactional(rollbackFor = Exception.class)
    public WebhookSubscription update(Long id, String name, String url, List<String> eventTypes,
                                      Integer status, String description) {
        WebhookSubscription sub = subscriptionMapper.selectById(id);
        if (sub == null) {
            throw new BusinessException("订阅不存在");
        }
        if (name != null) sub.setName(name);
        if (url != null) {
            if (!url.startsWith("http")) throw new BusinessException("回调 URL 格式不正确");
            sub.setUrl(url);
        }
        if (eventTypes != null && !eventTypes.isEmpty()) {
            for (String et : eventTypes) {
                if (WebhookEventType.fromCode(et) == null) throw new BusinessException("未知的事件类型: " + et);
            }
            sub.setEventTypes(toJson(eventTypes));
        }
        if (status != null) sub.setStatus(status);
        if (description != null) sub.setDescription(description);
        subscriptionMapper.updateById(sub);
        return sub;
    }

    /**
     * 删除订阅。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        subscriptionMapper.deleteById(id);
        log.info("删除 Webhook 订阅: id={}", id);
    }

    /**
     * 查询订阅列表。
     */
    public List<WebhookSubscription> list() {
        LambdaQueryWrapper<WebhookSubscription> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(WebhookSubscription::getCreateTime);
        return subscriptionMapper.selectList(wrapper);
    }

    /**
     * 获取订阅详情。
     */
    public WebhookSubscription get(Long id) {
        WebhookSubscription sub = subscriptionMapper.selectById(id);
        if (sub == null) throw new BusinessException("订阅不存在");
        return sub;
    }

    /**
     * 轮换签名密钥。
     */
    @Transactional(rollbackFor = Exception.class)
    public String rotateSecret(Long id) {
        WebhookSubscription sub = subscriptionMapper.selectById(id);
        if (sub == null) throw new BusinessException("订阅不存在");
        String newSecret = generateSecret();
        sub.setSecret(newSecret);
        subscriptionMapper.updateById(sub);
        log.info("轮换 Webhook 签名密钥: id={}", id);
        return newSecret;
    }

    /**
     * 获取订阅某事件类型的所有启用订阅。
     */
    public List<WebhookSubscription> findByEventType(String eventType) {
        List<WebhookSubscription> all = list();
        return all.stream()
            .filter(sub -> sub.getStatus() != null && sub.getStatus() == 1)
            .filter(sub -> {
                List<String> types = fromJson(sub.getEventTypes());
                return types.contains(eventType) || types.contains("*");
            })
            .toList();
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
