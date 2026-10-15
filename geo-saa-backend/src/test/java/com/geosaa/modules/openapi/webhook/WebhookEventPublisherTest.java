package com.geosaa.modules.openapi.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geosaa.modules.openapi.entity.WebhookSubscription;
import com.geosaa.modules.openapi.mapper.WebhookDeliveryMapper;
import com.geosaa.modules.openapi.service.WebhookSubscriptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * WebhookEventPublisher 单测（O6 Webhook）。
 * 覆盖：无订阅时不投递、有订阅时提交异步任务、HMAC-SHA256 签名计算。
 */
@ExtendWith(MockitoExtension.class)
class WebhookEventPublisherTest {

    @Mock
    private WebhookSubscriptionService subscriptionService;

    @Mock
    private WebhookDeliveryMapper deliveryMapper;

    @Mock
    private RestTemplate restTemplate;

    private WebhookEventPublisher publisher;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        publisher = new WebhookEventPublisher(subscriptionService, deliveryMapper, objectMapper, restTemplate);
        publisher.init();
    }

    @AfterEach
    void tearDown() {
        publisher.shutdown();
    }

    @Test
    void testPublish_noSubscriptions_doesNothing() {
        when(subscriptionService.findByEventType("knowledge.created")).thenReturn(Collections.emptyList());

        publisher.publish("knowledge.created", Map.of("id", 1L));

        verify(deliveryMapper, never()).insert(isA(com.geosaa.modules.openapi.entity.WebhookDelivery.class));
        verify(restTemplate, never()).postForEntity(anyString(), any(), any(Class.class));
    }

    @Test
    void testPublish_withSubscriptions_submitsAsyncTask() throws InterruptedException {
        WebhookSubscription sub = new WebhookSubscription();
        sub.setId(1L);
        sub.setUrl("https://example.com/webhook");
        sub.setSecret("whsec_testsecret123");
        sub.setStatus(1);

        when(subscriptionService.findByEventType("knowledge.created"))
            .thenReturn(List.of(sub));
        when(restTemplate.postForEntity(eq("https://example.com/webhook"), any(), eq(String.class)))
            .thenReturn(org.springframework.http.ResponseEntity.ok("{\"status\":\"received\"}"));
        when(deliveryMapper.insert(isA(com.geosaa.modules.openapi.entity.WebhookDelivery.class))).thenReturn(1);

        publisher.publish("knowledge.created", Map.of("id", 1L, "title", "测试知识"));

        // 等待异步任务完成
        Thread.sleep(500);

        verify(restTemplate, times(1)).postForEntity(eq("https://example.com/webhook"), any(), eq(String.class));
        verify(deliveryMapper, times(1)).insert(isA(com.geosaa.modules.openapi.entity.WebhookDelivery.class));
    }

    @Test
    void testPublish_nullPayload_usesEmptyMap() {
        when(subscriptionService.findByEventType("knowledge.created")).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> publisher.publish("knowledge.created", null));
    }

    @Test
    void testHmacSignature_correctFormat() throws Exception {
        Method signMethod = WebhookEventPublisher.class.getDeclaredMethod(
            "sign", String.class, String.class, String.class);
        signMethod.setAccessible(true);

        String signature = (String) signMethod.invoke(publisher, "whsec_test", "test payload", "1234567890");

        assertNotNull(signature);
        assertFalse(signature.isEmpty());
        // HMAC-SHA256 输出 64 个十六进制字符
        assertEquals(64, signature.length());
        assertTrue(signature.matches("[0-9a-f]+"));
    }

    @Test
    void testHmacSignature_deterministic() throws Exception {
        Method signMethod = WebhookEventPublisher.class.getDeclaredMethod(
            "sign", String.class, String.class, String.class);
        signMethod.setAccessible(true);

        String sig1 = (String) signMethod.invoke(publisher, "secret", "payload", "1000");
        String sig2 = (String) signMethod.invoke(publisher, "secret", "payload", "1000");

        assertEquals(sig1, sig2);
    }

    @Test
    void testHmacSignature_differentSecret_differentSignature() throws Exception {
        Method signMethod = WebhookEventPublisher.class.getDeclaredMethod(
            "sign", String.class, String.class, String.class);
        signMethod.setAccessible(true);

        String sig1 = (String) signMethod.invoke(publisher, "secret1", "payload", "1000");
        String sig2 = (String) signMethod.invoke(publisher, "secret2", "payload", "1000");

        assertNotEquals(sig1, sig2);
    }

    @Test
    void testWebhookEventType_allTypesHaveCodeAndDescription() {
        for (WebhookEventType type : WebhookEventType.values()) {
            assertNotNull(type.getCode());
            assertNotNull(type.getDescription());
            assertFalse(type.getCode().isEmpty());
            assertFalse(type.getDescription().isEmpty());
        }
    }

    @Test
    void testWebhookEventType_fromCode_validCode() {
        assertEquals(WebhookEventType.KNOWLEDGE_CREATED, WebhookEventType.fromCode("knowledge.created"));
        assertEquals(WebhookEventType.DIAGNOSE_COMPLETED, WebhookEventType.fromCode("diagnose.completed"));
        assertEquals(WebhookEventType.DISTRIBUTE_STATUS_CHANGED, WebhookEventType.fromCode("distribute.status_changed"));
    }

    @Test
    void testWebhookEventType_fromCode_invalidCodeReturnsNull() {
        assertNull(WebhookEventType.fromCode("invalid.event"));
        assertNull(WebhookEventType.fromCode(null));
    }

    @Test
    void testEventEnvelope_containsRequiredFields() throws Exception {
        // 验证事件信封结构（通过检查 publish 时构建的 payload）
        WebhookSubscription sub = new WebhookSubscription();
        sub.setId(1L);
        sub.setUrl("https://example.com/webhook");
        sub.setSecret("whsec_test");
        sub.setStatus(1);

        when(subscriptionService.findByEventType("test.event")).thenReturn(List.of(sub));
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
            .thenReturn(org.springframework.http.ResponseEntity.ok("ok"));
        when(deliveryMapper.insert(isA(com.geosaa.modules.openapi.entity.WebhookDelivery.class))).thenReturn(1);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", "value");
        publisher.publish("test.event", data);

        Thread.sleep(300);

        // 验证投递时发送的 payload 包含事件信封字段
        ArgumentCaptor<Object> entityCaptor = ArgumentCaptor.forClass(Object.class);
        verify(restTemplate).postForEntity(anyString(), entityCaptor.capture(), eq(String.class));

        org.springframework.http.HttpEntity<?> entity = (org.springframework.http.HttpEntity<?>) entityCaptor.getValue();
        String body = (String) entity.getBody();
        assertNotNull(body);

        Map<String, Object> event = objectMapper.readValue(body, Map.class);
        assertNotNull(event.get("id"));
        assertEquals("test.event", event.get("event"));
        assertNotNull(event.get("timestamp"));
        assertNotNull(event.get("data"));
        assertEquals("value", ((Map<?, ?>) event.get("data")).get("key"));
    }
}
