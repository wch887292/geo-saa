package com.geosaa.modules.openapi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geosaa.common.exception.BusinessException;
import com.geosaa.modules.openapi.entity.ApiKey;
import com.geosaa.modules.openapi.mapper.ApiKeyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ApiKeyService 单测（O6 开放 API）。
 * 覆盖：生成 Key、验证 Key、吊销 Key、速率限制、过期检查。
 */
@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyMapper apiKeyMapper;

    private ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        apiKeyService = new ApiKeyService(apiKeyMapper);
    }

    @Test
    void testGenerateKey_success() {
        when(apiKeyMapper.insert(any(ApiKey.class))).thenReturn(1);

        Map<String, String> result = apiKeyService.generateKey("测试Key", 100, 30, "admin");

        assertNotNull(result);
        assertTrue(result.get("apiKey").startsWith("gk_"));
        assertNotNull(result.get("apiSecret"));
        assertNotNull(result.get("id"));

        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyMapper).insert(captor.capture());
        ApiKey key = captor.getValue();
        assertEquals("测试Key", key.getName());
        assertEquals(100, key.getRateLimitPerMin());
        assertEquals(1, key.getStatus());
        assertNotNull(key.getExpireTime());
        assertEquals("admin", key.getCreatedBy());
    }

    @Test
    void testGenerateKey_noExpiration() {
        when(apiKeyMapper.insert(any(ApiKey.class))).thenReturn(1);

        apiKeyService.generateKey("永不过期Key", null, null, "admin");

        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyMapper).insert(captor.capture());
        assertNull(captor.getValue().getExpireTime());
        assertEquals(60, captor.getValue().getRateLimitPerMin()); // 默认值
    }

    @Test
    void testValidateKey_success() {
        ApiKey key = new ApiKey();
        key.setId(1L);
        key.setApiKey("gk_test123");
        key.setStatus(1);
        key.setRateLimitPerMin(60);
        key.setTenantId(1L);

        when(apiKeyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(key);
        when(apiKeyMapper.updateById(any(ApiKey.class))).thenReturn(1);

        ApiKey result = apiKeyService.validateKey("gk_test123");

        assertNotNull(result);
        assertEquals(1L, result.getId());
        verify(apiKeyMapper).updateById(any(ApiKey.class)); // 更新最后使用时间
    }

    @Test
    void testValidateKey_emptyKeyThrowsException() {
        assertThrows(BusinessException.class, () -> apiKeyService.validateKey(""));
        assertThrows(BusinessException.class, () -> apiKeyService.validateKey(null));
    }

    @Test
    void testValidateKey_invalidKeyThrowsException() {
        when(apiKeyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThrows(BusinessException.class, () -> apiKeyService.validateKey("gk_invalid"));
    }

    @Test
    void testValidateKey_expiredKeyThrowsException() {
        ApiKey key = new ApiKey();
        key.setId(1L);
        key.setStatus(1);
        key.setRateLimitPerMin(60);
        key.setExpireTime(LocalDateTime.now().minusDays(1)); // 已过期

        when(apiKeyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(key);

        assertThrows(BusinessException.class, () -> apiKeyService.validateKey("gk_expired"));
    }

    @Test
    void testRevokeKey_success() {
        ApiKey key = new ApiKey();
        key.setId(1L);
        key.setApiKey("gk_revoke");
        key.setStatus(1);

        when(apiKeyMapper.selectById(1L)).thenReturn(key);
        when(apiKeyMapper.updateById(any(ApiKey.class))).thenReturn(1);

        apiKeyService.revokeKey(1L);

        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyMapper).updateById(captor.capture());
        assertEquals(0, captor.getValue().getStatus());
    }

    @Test
    void testRevokeKey_notFoundThrowsException() {
        when(apiKeyMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> apiKeyService.revokeKey(999L));
    }

    @Test
    void testListKeys() {
        ApiKey key1 = new ApiKey();
        key1.setId(1L);
        key1.setName("Key1");
        ApiKey key2 = new ApiKey();
        key2.setId(2L);
        key2.setName("Key2");

        when(apiKeyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(key1, key2));

        List<ApiKey> result = apiKeyService.listKeys();

        assertEquals(2, result.size());
    }

    @Test
    void testGetKey_masksSecret() {
        ApiKey key = new ApiKey();
        key.setId(1L);
        key.setName("Test");
        key.setApiSecret("secret123");

        when(apiKeyMapper.selectById(1L)).thenReturn(key);

        ApiKey result = apiKeyService.getKey(1L);

        assertNotNull(result);
        assertNull(result.getApiSecret()); // 密钥不应返回
    }

    @Test
    void testGetKey_notFoundThrowsException() {
        when(apiKeyMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> apiKeyService.getKey(999L));
    }

    @Test
    void testRateLimit_allowsWithinLimit() {
        ApiKey key = new ApiKey();
        key.setId(1L);
        key.setStatus(1);
        key.setRateLimitPerMin(5);
        key.setApiKey("gk_rate");

        when(apiKeyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(key);
        when(apiKeyMapper.updateById(any(ApiKey.class))).thenReturn(1);

        // 在限制内应该都成功
        for (int i = 0; i < 5; i++) {
            ApiKey result = apiKeyService.validateKey("gk_rate");
            assertNotNull(result);
        }
    }
}
