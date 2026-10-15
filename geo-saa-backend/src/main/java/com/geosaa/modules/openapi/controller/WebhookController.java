package com.geosaa.modules.openapi.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geosaa.common.Result;
import com.geosaa.modules.openapi.entity.WebhookDelivery;
import com.geosaa.modules.openapi.entity.WebhookSubscription;
import com.geosaa.modules.openapi.mapper.WebhookDeliveryMapper;
import com.geosaa.modules.openapi.service.WebhookSubscriptionService;
import com.geosaa.modules.openapi.webhook.WebhookEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Webhook 订阅管理控制器。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookSubscriptionService subscriptionService;
    private final WebhookDeliveryMapper deliveryMapper;

    // ========== 订阅管理 ==========

    /**
     * 创建订阅。
     */
    @PostMapping("/subscriptions")
    public Result<WebhookSubscription> createSubscription(@RequestBody Map<String, Object> request) {
        String name = (String) request.get("name");
        String url = (String) request.get("url");
        @SuppressWarnings("unchecked")
        List<String> eventTypes = (List<String>) request.get("eventTypes");
        String description = (String) request.get("description");
        return Result.success(subscriptionService.create(name, url, eventTypes, description));
    }

    /**
     * 更新订阅。
     */
    @PutMapping("/subscriptions/{id}")
    public Result<WebhookSubscription> updateSubscription(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        String name = (String) request.get("name");
        String url = (String) request.get("url");
        @SuppressWarnings("unchecked")
        List<String> eventTypes = (List<String>) request.get("eventTypes");
        Integer status = request.get("status") != null ? Integer.parseInt(request.get("status").toString()) : null;
        String description = (String) request.get("description");
        return Result.success(subscriptionService.update(id, name, url, eventTypes, status, description));
    }

    /**
     * 删除订阅。
     */
    @DeleteMapping("/subscriptions/{id}")
    public Result<Void> deleteSubscription(@PathVariable Long id) {
        subscriptionService.delete(id);
        return Result.success(null);
    }

    /**
     * 查询订阅列表。
     */
    @GetMapping("/subscriptions")
    public Result<List<WebhookSubscription>> listSubscriptions() {
        return Result.success(subscriptionService.list());
    }

    /**
     * 获取订阅详情。
     */
    @GetMapping("/subscriptions/{id}")
    public Result<WebhookSubscription> getSubscription(@PathVariable Long id) {
        return Result.success(subscriptionService.get(id));
    }

    /**
     * 轮换签名密钥。
     */
    @PostMapping("/subscriptions/{id}/rotate-secret")
    public Result<Map<String, String>> rotateSecret(@PathVariable Long id) {
        String newSecret = subscriptionService.rotateSecret(id);
        return Result.success(Map.of("secret", newSecret));
    }

    // ========== 事件类型 ==========

    /**
     * 获取支持的事件类型列表。
     */
    @GetMapping("/event-types")
    public Result<List<Map<String, String>>> listEventTypes() {
        List<Map<String, String>> types = Arrays.stream(WebhookEventType.values())
            .map(t -> Map.of("code", t.getCode(), "description", t.getDescription()))
            .collect(Collectors.toList());
        return Result.success(types);
    }

    // ========== 投递记录 ==========

    /**
     * 查询投递记录。
     */
    @GetMapping("/deliveries")
    public Result<List<WebhookDelivery>> listDeliveries(
            @RequestParam(required = false) Long subscriptionId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "20") int limit) {
        LambdaQueryWrapper<WebhookDelivery> wrapper = new LambdaQueryWrapper<>();
        if (subscriptionId != null) wrapper.eq(WebhookDelivery::getSubscriptionId, subscriptionId);
        if (eventType != null) wrapper.eq(WebhookDelivery::getEventType, eventType);
        if (status != null) wrapper.eq(WebhookDelivery::getStatus, status);
        wrapper.orderByDesc(WebhookDelivery::getCreateTime);
        wrapper.last("LIMIT " + Math.min(limit, 100));
        return Result.success(deliveryMapper.selectList(wrapper));
    }

    /**
     * 获取投递记录详情。
     */
    @GetMapping("/deliveries/{id}")
    public Result<WebhookDelivery> getDelivery(@PathVariable Long id) {
        return Result.success(deliveryMapper.selectById(id));
    }
}
