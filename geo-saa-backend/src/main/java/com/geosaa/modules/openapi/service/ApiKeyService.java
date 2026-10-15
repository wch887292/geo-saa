package com.geosaa.modules.openapi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geosaa.common.exception.BusinessException;
import com.geosaa.modules.openapi.entity.ApiKey;
import com.geosaa.modules.openapi.mapper.ApiKeyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * API Key 管理服务。
 *
 * <p>负责 API Key 的生成、验证、吊销、速率限制。
 * 速率限制基于内存计数器（滑动窗口），Redis 不可用时也能正常工作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyMapper apiKeyMapper;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String KEY_PREFIX = "gk_";

    /** 速率限制计数器：apiKey -> (分钟窗口起始时间, 计数) */
    private final Map<String, RateLimitEntry> rateLimitMap = new ConcurrentHashMap<>();

    /**
     * 生成新的 API Key。
     *
     * @param name           Key 名称
     * @param rateLimitPerMin 每分钟速率限制
     * @param expireDays     过期天数（null=永不过期）
     * @param createdBy      创建人
     * @return 包含 apiKey 和 apiSecret 的 Map
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, String> generateKey(String name, Integer rateLimitPerMin, Integer expireDays, String createdBy) {
        String apiKey = KEY_PREFIX + randomString(32);
        String apiSecret = randomString(48);

        ApiKey key = new ApiKey();
        key.setName(name);
        key.setApiKey(apiKey);
        key.setApiSecret(apiSecret);
        key.setStatus(1);
        key.setRateLimitPerMin(rateLimitPerMin != null ? rateLimitPerMin : 60);
        if (expireDays != null && expireDays > 0) {
            key.setExpireTime(LocalDateTime.now().plusDays(expireDays));
        }
        key.setCreatedBy(createdBy);
        apiKeyMapper.insert(key);

        log.info("生成 API Key: id={}, name={}, createdBy={}", key.getId(), name, createdBy);
        return Map.of("apiKey", apiKey, "apiSecret", apiSecret, "id", String.valueOf(key.getId()));
    }

    /**
     * 验证 API Key 有效性，并检查速率限制。
     *
     * @param apiKey API Key
     * @return 有效的 ApiKey 实体
     * @throws BusinessException Key 无效、禁用、过期或超限
     */
    public ApiKey validateKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new BusinessException("API Key 不能为空");
        }

        LambdaQueryWrapper<ApiKey> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiKey::getApiKey, apiKey);
        wrapper.eq(ApiKey::getStatus, 1);
        ApiKey key = apiKeyMapper.selectOne(wrapper);

        if (key == null) {
            throw new BusinessException("API Key 无效或已禁用");
        }

        if (key.getExpireTime() != null && key.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("API Key 已过期");
        }

        // 速率限制检查
        if (!checkRateLimit(apiKey, key.getRateLimitPerMin())) {
            throw new BusinessException("请求频率超过限制（" + key.getRateLimitPerMin() + "次/分钟）");
        }

        // 更新最后使用时间
        key.setLastUsedAt(LocalDateTime.now());
        apiKeyMapper.updateById(key);

        return key;
    }

    /**
     * 吊销 API Key。
     */
    @Transactional(rollbackFor = Exception.class)
    public void revokeKey(Long id) {
        ApiKey key = apiKeyMapper.selectById(id);
        if (key == null) {
            throw new BusinessException("API Key 不存在");
        }
        key.setStatus(0);
        apiKeyMapper.updateById(key);
        rateLimitMap.remove(key.getApiKey());
        log.info("吊销 API Key: id={}, name={}", id, key.getName());
    }

    /**
     * 查询当前租户的 API Key 列表。
     */
    public List<ApiKey> listKeys() {
        LambdaQueryWrapper<ApiKey> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(ApiKey::getCreateTime);
        return apiKeyMapper.selectList(wrapper);
    }

    /**
     * 获取 API Key 详情（不返回 apiSecret）。
     */
    public ApiKey getKey(Long id) {
        ApiKey key = apiKeyMapper.selectById(id);
        if (key == null) {
            throw new BusinessException("API Key 不存在");
        }
        key.setApiSecret(null); // 不返回密钥
        return key;
    }

    /**
     * 速率限制检查（滑动窗口，每分钟）。
     *
     * @return true=允许, false=超限
     */
    private boolean checkRateLimit(String apiKey, int limit) {
        long now = System.currentTimeMillis();
        long windowStart = now - (now % 60000); // 当前分钟起始

        RateLimitEntry entry = rateLimitMap.get(apiKey);
        if (entry == null || entry.windowStart != windowStart) {
            // 新窗口，重置计数
            rateLimitMap.put(apiKey, new RateLimitEntry(windowStart, new AtomicInteger(1)));
            return true;
        }

        int count = entry.count.incrementAndGet();
        return count <= limit;
    }

    private String randomString(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, length);
    }

    private static class RateLimitEntry {
        final long windowStart;
        final AtomicInteger count;

        RateLimitEntry(long windowStart, AtomicInteger count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
