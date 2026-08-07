package com.geosaa.modules.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geosaa.common.PageResult;
import com.geosaa.common.Result;
import com.geosaa.modules.system.entity.SystemAuditLog;
import com.geosaa.modules.system.entity.SystemConfig;
import com.geosaa.modules.system.service.SystemConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统配置控制器 - 系统配置管理、私有大模型配置、内网模式、API白名单
 *
 * <p>本控制器涉及密钥、内网开关、IP 白名单等高危配置，整体限定为管理员角色。
 * 修复前项目中没有任何方法级鉴权，任意登录用户都可读写这些配置。
 */
@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    /**
     * 分页查询审计日志
     */
    @GetMapping("/audit-log")
    public PageResult<SystemAuditLog> listAuditLogs(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String username) {
        Page<SystemAuditLog> page = systemConfigService.listAuditLogs(pageNum, pageSize, module, username);
        return PageResult.success(page);
    }

    /**
     * 健康检查（匿名可访问，需覆盖类级别的管理员限制）
     */
    @GetMapping("/health")
    @PreAuthorize("permitAll()")
    public Result<String> health() {
        return Result.success("ok");
    }

    // ========== 系统配置管理 ==========

    /**
     * 获取所有系统配置
     */
    @GetMapping("/configs")
    public Result<Map<String, List<SystemConfig>>> getAllConfigs() {
        return Result.success(systemConfigService.getAllConfigs());
    }

    /**
     * 更新单个系统配置
     */
    @PutMapping("/configs")
    public Result<Void> updateConfig(@Valid @RequestBody SystemConfig config) {
        systemConfigService.updateConfig(config);
        return Result.success(null);
    }

    // ========== 私有大模型对接配置 ==========

    /**
     * 获取私有大模型对接配置
     */
    @GetMapping("/model-config")
    public Result<Map<String, Object>> getModelConfig() {
        Map<String, Object> config = systemConfigService.getModelConfig();
        return Result.success(config);
    }

    /**
     * 更新私有大模型对接配置
     */
    @PutMapping("/model-config")
    public Result<Void> updateModelConfig(@RequestBody Map<String, String> configMap) {
        systemConfigService.updateModelConfig(configMap);
        return Result.success(null);
    }

    // ========== 内网模式 ==========

    /**
     * 获取内网模式状态
     */
    @GetMapping("/intranet-mode")
    public Result<Boolean> getIntranetMode() {
        boolean enabled = systemConfigService.isIntranetMode();
        return Result.success(enabled);
    }

    /**
     * 设置内网模式
     */
    @PutMapping("/intranet-mode")
    public Result<Void> setIntranetMode(@RequestParam boolean enabled) {
        systemConfigService.setIntranetMode(enabled);
        return Result.success(null);
    }

    // ========== API 白名单管理 ==========

    /**
     * 获取 API 白名单列表
     */
    @GetMapping("/api-whitelist")
    public Result<List<String>> getApiWhitelist() {
        List<String> whitelist = systemConfigService.getApiWhitelist();
        return Result.success(whitelist);
    }

    /**
     * 添加 API 白名单
     */
    @PostMapping("/api-whitelist")
    public Result<Void> addApiWhitelist(@RequestParam String ip) {
        systemConfigService.addApiWhitelist(ip);
        return Result.success(null);
    }

    /**
     * 删除 API 白名单
     */
    @DeleteMapping("/api-whitelist/{id}")
    public Result<Void> deleteApiWhitelist(@PathVariable Long id) {
        systemConfigService.deleteApiWhitelist(id);
        return Result.success(null);
    }
}