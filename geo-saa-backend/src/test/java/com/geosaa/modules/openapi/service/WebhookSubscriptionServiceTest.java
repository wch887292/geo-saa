package com.geosaa.modules.openapi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geosaa.common.exception.BusinessException;
import com.geosaa.modules.openapi.entity.WebhookSubscription;
import com.geosaa.modules.openapi.mapper.WebhookSubscriptionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WebhookSubscriptionService 单测（O6 Webhook）。
 * 覆盖：创建订阅、更新订阅、删除订阅、事件类型匹配、密钥轮换。
 */
@ExtendWith(MockitoExtension.class)
class WebhookSubscriptionServiceTest {

    @Mock
    private WebhookSubscriptionMapper subscriptionMapper;

    private WebhookSubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new WebhookSubscriptionService(subscriptionMapper, new ObjectMapper());
    }

    @Test
    void testCreate_success() {
        when(subscriptionMapper.insert(any(WebhookSubscription.class))).thenReturn(1);

        WebhookSubscription result = service.create(
            "测试订阅",
            "https://example.com/webhook",
            List.of("knowledge.created", "diagnose.completed"),
            "测试描述"
        );

        assertNotNull(result);
        assertEquals("测试订阅", result.getName());
        assertEquals("https://example.com/webhook", result.getUrl());
        assertEquals(1, result.getStatus());
        assertNotNull(result.getSecret());
        assertTrue(result.getSecret().startsWith("whsec_"));
        assertNotNull(result.getEventTypes());

        ArgumentCaptor<WebhookSubscription> captor = ArgumentCaptor.forClass(WebhookSubscription.class);
        verify(subscriptionMapper).insert(captor.capture());
        assertTrue(captor.getValue().getEventTypes().contains("knowledge.created"));
    }

    @Test
    void testCreate_invalidUrlThrowsException() {
        assertThrows(BusinessException.class, () ->
            service.create("测试", "not-a-url", List.of("knowledge.created"), null));
    }

    @Test
    void testCreate_emptyEventTypesThrowsException() {
        assertThrows(BusinessException.class, () ->
            service.create("测试", "https://example.com", List.of(), null));
    }

    @Test
    void testCreate_unknownEventTypeThrowsException() {
        assertThrows(BusinessException.class, () ->
            service.create("测试", "https://example.com", List.of("unknown.event"), null));
    }

    @Test
    void testUpdate_success() {
        WebhookSubscription existing = new WebhookSubscription();
        existing.setId(1L);
        existing.setName("旧名称");
        existing.setStatus(1);

        when(subscriptionMapper.selectById(1L)).thenReturn(existing);
        when(subscriptionMapper.updateById(any(WebhookSubscription.class))).thenReturn(1);

        WebhookSubscription result = service.update(1L, "新名称", null, null, 0, null);

        assertEquals("新名称", result.getName());
        assertEquals(0, result.getStatus());
    }

    @Test
    void testUpdate_notFoundThrowsException() {
        when(subscriptionMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () ->
            service.update(999L, "名称", null, null, null, null));
    }

    @Test
    void testDelete_success() {
        when(subscriptionMapper.deleteById(1L)).thenReturn(1);

        service.delete(1L);

        verify(subscriptionMapper).deleteById(1L);
    }

    @Test
    void testList() {
        WebhookSubscription sub1 = new WebhookSubscription();
        sub1.setId(1L);
        WebhookSubscription sub2 = new WebhookSubscription();
        sub2.setId(2L);

        when(subscriptionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(sub1, sub2));

        List<WebhookSubscription> result = service.list();

        assertEquals(2, result.size());
    }

    @Test
    void testGet_success() {
        WebhookSubscription sub = new WebhookSubscription();
        sub.setId(1L);
        sub.setName("测试");

        when(subscriptionMapper.selectById(1L)).thenReturn(sub);

        WebhookSubscription result = service.get(1L);

        assertEquals("测试", result.getName());
    }

    @Test
    void testGet_notFoundThrowsException() {
        when(subscriptionMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.get(999L));
    }

    @Test
    void testRotateSecret_success() {
        WebhookSubscription sub = new WebhookSubscription();
        sub.setId(1L);
        sub.setSecret("old_secret");

        when(subscriptionMapper.selectById(1L)).thenReturn(sub);
        when(subscriptionMapper.updateById(any(WebhookSubscription.class))).thenReturn(1);

        String newSecret = service.rotateSecret(1L);

        assertNotNull(newSecret);
        assertNotEquals("old_secret", newSecret);
        assertTrue(newSecret.startsWith("whsec_"));
    }

    @Test
    void testFindByEventType_matchingSubscriptions() {
        WebhookSubscription sub1 = new WebhookSubscription();
        sub1.setId(1L);
        sub1.setStatus(1);
        sub1.setEventTypes("[\"knowledge.created\",\"diagnose.completed\"]");

        WebhookSubscription sub2 = new WebhookSubscription();
        sub2.setId(2L);
        sub2.setStatus(1);
        sub2.setEventTypes("[\"knowledge.updated\"]");

        WebhookSubscription sub3 = new WebhookSubscription();
        sub3.setId(3L);
        sub3.setStatus(0); // 禁用
        sub3.setEventTypes("[\"knowledge.created\"]");

        when(subscriptionMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(sub1, sub2, sub3));

        List<WebhookSubscription> result = service.findByEventType("knowledge.created");

        assertEquals(1, result.size()); // 只有 sub1 匹配且启用
        assertEquals(1L, result.get(0).getId());
    }
}
