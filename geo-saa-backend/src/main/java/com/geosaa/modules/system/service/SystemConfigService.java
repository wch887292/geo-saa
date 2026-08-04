package com.geosaa.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geosaa.common.exception.BusinessException;
import com.geosaa.modules.system.entity.SystemAuditLog;
import com.geosaa.modules.system.entity.SystemConfig;
import com.geosaa.modules.system.mapper.SystemAuditLogMapper;
import com.geosaa.modules.system.mapper.SystemConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemAuditLogMapper auditLogMapper;
    private final SystemConfigMapper systemConfigMapper;

    public Page<SystemAuditLog> listAuditLogs(int pageNum, int pageSize, String module, String username) {
        LambdaQueryWrapper<SystemAuditLog> wrapper = new LambdaQueryWrapper<>();
        if (module != null) {
            wrapper.eq(SystemAuditLog::getModule, module);
        }
        if (username != null) {
            wrapper.like(SystemAuditLog::getUsername, username);
        }
        wrapper.orderByDesc(SystemAuditLog::getCreateTime);
        return auditLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    // ========== 系统配置管理 ==========

    /**
     * 获取私有大模型对接配置
     */
    public Map<String, Object> getModelConfig() {
        List<SystemConfig> configs = systemConfigMapper.selectByGroup("ai_model");
        Map<String, Object> modelConfig = new HashMap<>();
        for (SystemConfig config : configs) {
            modelConfig.put(config.getConfigKey(), config.getConfigValue());
        }
        // 如果数据库没有配置，返回默认值
        if (modelConfig.isEmpty()) {
            modelConfig.put("openaiApiKey", "");
            modelConfig.put("openaiApiUrl", "https://api.openai.com/v1");
            modelConfig.put("openaiModel", "gpt-4");
            modelConfig.put("tongyiApiKey", "");
            modelConfig.put("doubaoApiKey", "");
            modelConfig.put("simulationEnabled", "true");
        }
        return modelConfig;
    }

    /**
     * 更新私有大模型对接配置
     */
    public void updateModelConfig(Map<String, String> configMap) {
        for (Map.Entry<String, String> entry : configMap.entrySet()) {
            SystemConfig config = systemConfigMapper.selectByKey(entry.getKey());
            if (config != null) {
                config.setConfigValue(entry.getValue());
                systemConfigMapper.updateById(config);
            } else {
                config = new SystemConfig();
                config.setConfigKey(entry.getKey());
                config.setConfigValue(entry.getValue());
                config.setConfigGroup("ai_model");
                config.setConfigDesc("AI模型配置-" + entry.getKey());
                config.setStatus(1);
                systemConfigMapper.insert(config);
            }
        }
        log.info("AI模型配置已更新: {}", configMap.keySet());
    }

    /**
     * 获取内网模式开关
     */
    public boolean isIntranetMode() {
        SystemConfig config = systemConfigMapper.selectByKey("intranet_mode");
        return config != null && "true".equals(config.getConfigValue());
    }

    /**
     * 设置内网模式
     */
    public void setIntranetMode(boolean enabled) {
        SystemConfig config = systemConfigMapper.selectByKey("intranet_mode");
        if (config != null) {
            config.setConfigValue(String.valueOf(enabled));
            systemConfigMapper.updateById(config);
        } else {
            config = new SystemConfig();
            config.setConfigKey("intranet_mode");
            config.setConfigValue(String.valueOf(enabled));
            config.setConfigGroup("system");
            config.setConfigDesc("内网模式开关");
            config.setStatus(1);
            systemConfigMapper.insert(config);
        }
        log.info("内网模式已设置为: {}", enabled);
    }

    /**
     * API 白名单管理 - 获取白名单列表
     */
    public List<String> getApiWhitelist() {
        List<SystemConfig> configs = systemConfigMapper.selectByGroup("api_whitelist");
        return configs.stream()
                .map(SystemConfig::getConfigValue)
                .collect(Collectors.toList());
    }

    /**
     * API 白名单管理 - 添加白名单
     */
    public void addApiWhitelist(String ip) {
        SystemConfig config = new SystemConfig();
        config.setConfigKey("whitelist_ip_" + ip.hashCode());
        config.setConfigValue(ip);
        config.setConfigGroup("api_whitelist");
        config.setConfigDesc("API白名单-IP: " + ip);
        config.setStatus(1);
        systemConfigMapper.insert(config);
        log.info("API白名单已添加: {}", ip);
    }

    /**
     * API 白名单管理 - 删除白名单
     */
    public void deleteApiWhitelist(Long id) {
        systemConfigMapper.deleteById(id);
        log.info("API白名单已删除: id={}", id);
    }

    /**
     * 获取所有系统配置分组
     */
    public Map<String, List<SystemConfig>> getAllConfigs() {
        Map<String, List<SystemConfig>> grouped = new HashMap<>();
        List<SystemConfig> allConfigs = systemConfigMapper.selectList(
            new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getStatus, 1)
        );
        for (SystemConfig config : allConfigs) {
            grouped.computeIfAbsent(config.getConfigGroup(), k -> new java.util.ArrayList<>()).add(config);
        }
        return grouped;
    }

    /**
     * 更新单个配置
     */
    public void updateConfig(SystemConfig config) {
        SystemConfig existing = systemConfigMapper.selectById(config.getId());
        if (existing == null) {
            throw new BusinessException("配置不存在");
        }
        existing.setConfigValue(config.getConfigValue());
        existing.setConfigDesc(config.getConfigDesc());
        existing.setStatus(config.getStatus());
        systemConfigMapper.updateById(existing);
        log.info("系统配置已更新: key={}, value={}", existing.getConfigKey(), existing.getConfigValue());
    }
}