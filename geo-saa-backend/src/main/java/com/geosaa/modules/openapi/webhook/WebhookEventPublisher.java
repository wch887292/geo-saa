package com.geosaa.modules.openapi.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geosaa.modules.openapi.entity.WebhookDelivery;
import com.geosaa.modules.openapi.entity.WebhookSubscription;
import com.geosaa.modules.openapi.mapper.WebhookDeliveryMapper;
import com.geosaa.modules.openapi.service.WebhookSubscriptionService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Webhook 事件发布器。
 *
 * <p>负责将事件异步推送到所有匹配的订阅 URL，包含：
 * <ul>
 *   <li>HMAC-SHA256 签名（请求头 X-Webhook-Signature）</li>
 *   <li>指数退避重试（最多 3 次：1s, 5s, 30s）</li>
 *   <li>投递记录（webhook_delivery 表）</li>
 *   <li>异步推送（独立线程池）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookEventPublisher {

    private final WebhookSubscriptionService subscriptionService;
    private final WebhookDeliveryMapper deliveryMapper;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    private ExecutorService executor;

    private static final int MAX_RETRIES = 3;
    private static final long[] RETRY_DELAYS = {1000, 5000, 30000}; // 毫秒
    private static final int TIMEOUT_MS = 10000;

    @PostConstruct
    public void init() {
        executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "webhook-publisher");
            t.setDaemon(true);
            return t;
        });
        log.info("Webhook 事件发布器初始化完成");
    }

    @PreDestroy
    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 发布事件。
     *
     * @param eventType 事件类型（如 knowledge.created）
     * @param payload   事件数据
     */
    public void publish(String eventType, Map<String, Object> payload) {
        List<WebhookSubscription> subscriptions = subscriptionService.findByEventType(eventType);
        if (subscriptions.isEmpty()) {
            log.debug("无匹配的 Webhook 订阅: eventType={}", eventType);
            return;
        }

        // 构建事件信封
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", UUID.randomUUID().toString());
        event.put("event", eventType);
        event.put("timestamp", Instant.now().toString());
        event.put("data", payload != null ? payload : Collections.emptyMap());

        String eventJson;
        try {
            eventJson = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("序列化 Webhook 事件失败", e);
            return;
        }

        for (WebhookSubscription sub : subscriptions) {
            executor.submit(() -> deliverWithRetry(sub, eventType, eventJson));
        }

        log.info("发布 Webhook 事件: eventType={}, subscriptions={}", eventType, subscriptions.size());
    }

    /**
     * 带重试的投递。
     */
    private void deliverWithRetry(WebhookSubscription sub, String eventType, String payload) {
        String deliveryId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();
        int retryCount = 0;
        String status = "failed";
        Integer responseCode = null;
        String responseBody = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            if (attempt > 0) {
                retryCount = attempt;
                try {
                    Thread.sleep(RETRY_DELAYS[Math.min(attempt - 1, RETRY_DELAYS.length - 1)]);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            try {
                String timestamp = String.valueOf(Instant.now().getEpochSecond());
                String signature = sign(sub.getSecret(), payload, timestamp);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("X-Webhook-Event", eventType);
                headers.set("X-Webhook-Delivery-Id", deliveryId);
                headers.set("X-Webhook-Timestamp", timestamp);
                headers.set("X-Webhook-Signature", "sha256=" + signature);

                HttpEntity<String> entity = new HttpEntity<>(payload, headers);
                ResponseEntity<String> response = restTemplate.postForEntity(sub.getUrl(), entity, String.class);

                responseCode = response.getStatusCode().value();
                responseBody = response.getBody();

                if (responseCode >= 200 && responseCode < 300) {
                    status = "success";
                    log.info("Webhook 投递成功: subId={}, event={}, attempt={}, code={}",
                        sub.getId(), eventType, attempt + 1, responseCode);
                    break;
                } else {
                    log.warn("Webhook 投递失败（非2xx）: subId={}, event={}, attempt={}, code={}, body={}",
                        sub.getId(), eventType, attempt + 1, responseCode,
                        responseBody != null && responseBody.length() > 200 ? responseBody.substring(0, 200) : responseBody);
                }
            } catch (Exception e) {
                log.warn("Webhook 投递异常: subId={}, event={}, attempt={}, error={}",
                    sub.getId(), eventType, attempt + 1, e.getMessage());
                responseBody = "Error: " + e.getMessage();
            }
        }

        // 保存投递记录
        try {
            WebhookDelivery delivery = new WebhookDelivery();
            delivery.setSubscriptionId(sub.getId());
            delivery.setEventType(eventType);
            delivery.setUrl(sub.getUrl());
            delivery.setPayload(payload.length() > 5000 ? payload.substring(0, 5000) : payload);
            delivery.setStatus(status);
            delivery.setResponseCode(responseCode);
            delivery.setResponseBody(responseBody != null && responseBody.length() > 2000
                ? responseBody.substring(0, 2000) : responseBody);
            delivery.setDurationMs(System.currentTimeMillis() - startTime);
            delivery.setRetryCount(retryCount);
            delivery.setDeliveryId(deliveryId);
            deliveryMapper.insert(delivery);
        } catch (Exception e) {
            log.error("保存 Webhook 投递记录失败", e);
        }
    }

    /**
     * HMAC-SHA256 签名。
     * 签名内容：timestamp + "." + payload
     */
    private String sign(String secret, String payload, String timestamp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String data = timestamp + "." + payload;
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            log.error("Webhook 签名计算失败", e);
            return "";
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
