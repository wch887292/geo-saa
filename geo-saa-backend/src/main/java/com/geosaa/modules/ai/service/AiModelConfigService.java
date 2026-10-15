package com.geosaa.modules.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geosaa.common.tenant.TenantContext;
import com.geosaa.modules.ai.dto.AiChatRequest;
import com.geosaa.modules.ai.dto.AiChatResponse;
import com.geosaa.modules.ai.entity.AiModelConfig;
import com.geosaa.modules.ai.mapper.AiModelConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI 模型配置管理 Service
 */
@Slf4j
@Service
public class AiModelConfigService {

    private final AiModelConfigMapper modelConfigMapper;
    private final AiChatService aiChatService;

    public AiModelConfigService(AiModelConfigMapper modelConfigMapper, @Lazy AiChatService aiChatService) {
        this.modelConfigMapper = modelConfigMapper;
        this.aiChatService = aiChatService;
    }

    /**
     * 分页查询模型列表
     */
    public Page<AiModelConfig> page(int pageNum, int pageSize, String keyword, String provider, String status) {
        Long tenantId = TenantContext.getTenantId();
        LambdaQueryWrapper<AiModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiModelConfig::getTenantId, tenantId)
               .orderByDesc(AiModelConfig::getIsDefault)
               .orderByDesc(AiModelConfig::getPriority)
               .orderByDesc(AiModelConfig::getCreatedAt);
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(AiModelConfig::getModelName, keyword)
                   .or().like(AiModelConfig::getModelId, keyword);
        }
        if (provider != null && !provider.isEmpty()) {
            wrapper.eq(AiModelConfig::getProvider, provider);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(AiModelConfig::getStatus, status);
        }
        return modelConfigMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 获取模型详情
     */
    public AiModelConfig getById(Long id) {
        AiModelConfig config = modelConfigMapper.selectById(id);
        if (config != null) {
            config.setApiKey(maskApiKey(config.getApiKey()));
        }
        return config;
    }

    /**
     * 获取默认模型
     */
    public AiModelConfig getDefaultModel() {
        Long tenantId = TenantContext.getTenantId();
        LambdaQueryWrapper<AiModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiModelConfig::getTenantId, tenantId)
               .eq(AiModelConfig::getIsDefault, 1)
               .eq(AiModelConfig::getStatus, "active")
               .orderByDesc(AiModelConfig::getPriority)
               .last("LIMIT 1");
        return modelConfigMapper.selectOne(wrapper);
    }

    /**
     * 获取所有可用模型（按优先级排序，用于降级切换）
     */
    public List<AiModelConfig> getAvailableModels() {
        Long tenantId = TenantContext.getTenantId();
        LambdaQueryWrapper<AiModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiModelConfig::getTenantId, tenantId)
               .eq(AiModelConfig::getStatus, "active")
               .orderByDesc(AiModelConfig::getIsDefault)
               .orderByDesc(AiModelConfig::getPriority);
        return modelConfigMapper.selectList(wrapper);
    }

    /**
     * 新增模型
     */
    @Transactional
    public AiModelConfig create(AiModelConfig config) {
        Long tenantId = TenantContext.getTenantId();
        config.setTenantId(tenantId);
        if (config.getIsDefault() != null && config.getIsDefault() == 1) {
            clearDefault(tenantId);
        }
        if (config.getTemperature() == null) config.setTemperature(new java.math.BigDecimal("0.7"));
        if (config.getMaxTokens() == null) config.setMaxTokens(4096);
        if (config.getTimeoutSeconds() == null) config.setTimeoutSeconds(60);
        if (config.getStatus() == null) config.setStatus("active");
        if (config.getPriority() == null) config.setPriority(0);
        modelConfigMapper.insert(config);
        log.info("创建AI模型配置: id={}, name={}, provider={}", config.getId(), config.getModelName(), config.getProvider());
        return config;
    }

    /**
     * 更新模型
     */
    @Transactional
    public AiModelConfig update(AiModelConfig config) {
        Long tenantId = TenantContext.getTenantId();
        AiModelConfig existing = modelConfigMapper.selectById(config.getId());
        if (existing == null || !existing.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("模型配置不存在或无权限");
        }
        // 如果 API Key 是掩码形式，不更新
        if (config.getApiKey() != null && config.getApiKey().contains("****")) {
            config.setApiKey(existing.getApiKey());
        }
        if (config.getIsDefault() != null && config.getIsDefault() == 1) {
            clearDefault(tenantId);
        }
        modelConfigMapper.updateById(config);
        log.info("更新AI模型配置: id={}, name={}", config.getId(), config.getModelName());
        return modelConfigMapper.selectById(config.getId());
    }

    /**
     * 删除模型
     */
    @Transactional
    public void delete(Long id) {
        AiModelConfig config = modelConfigMapper.selectById(id);
        if (config != null) {
            modelConfigMapper.deleteById(id);
            log.info("删除AI模型配置: id={}, name={}", id, config.getModelName());
        }
    }

    /**
     * 设置默认模型
     */
    @Transactional
    public void setDefault(Long id) {
        Long tenantId = TenantContext.getTenantId();
        clearDefault(tenantId);
        AiModelConfig config = new AiModelConfig();
        config.setId(id);
        config.setIsDefault(1);
        modelConfigMapper.updateById(config);
        log.info("设置默认AI模型: id={}", id);
    }

    /**
     * 测试模型连接
     */
    public AiChatResponse testConnection(Long modelConfigId) {
        AiChatRequest request = new AiChatRequest();
        request.setModelConfigId(modelConfigId);
        request.setUserInput("请回复'连接成功'四个字，不要回复其他内容。");
        request.setBusinessModule("system");
        request.setBusinessType("test");
        request.setMaxTokens(50);
        request.setTemperature(0.1);
        return aiChatService.chat(request);
    }

    /**
     * 清除租户下的默认模型标记
     */
    private void clearDefault(Long tenantId) {
        LambdaQueryWrapper<AiModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiModelConfig::getTenantId, tenantId)
               .eq(AiModelConfig::getIsDefault, 1);
        List<AiModelConfig> defaults = modelConfigMapper.selectList(wrapper);
        for (AiModelConfig m : defaults) {
            m.setIsDefault(0);
            modelConfigMapper.updateById(m);
        }
    }

    /**
     * 掩码 API Key（只显示前4后4）
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
