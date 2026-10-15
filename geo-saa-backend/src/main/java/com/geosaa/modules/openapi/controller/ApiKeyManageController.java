package com.geosaa.modules.openapi.controller;

import com.geosaa.common.Result;
import com.geosaa.modules.openapi.entity.ApiKey;
import com.geosaa.modules.openapi.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * API Key 管理控制器（管理后台用，JWT 认证）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/system/api-keys")
@RequiredArgsConstructor
public class ApiKeyManageController {

    private final ApiKeyService apiKeyService;

    /**
     * 生成新的 API Key。
     */
    @PostMapping
    public Result<Map<String, String>> generateKey(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        String name = (String) request.get("name");
        Integer rateLimit = request.get("rateLimitPerMin") != null
            ? Integer.parseInt(request.get("rateLimitPerMin").toString()) : null;
        Integer expireDays = request.get("expireDays") != null
            ? Integer.parseInt(request.get("expireDays").toString()) : null;
        String createdBy = authentication != null ? authentication.getName() : "system";
        return Result.success(apiKeyService.generateKey(name, rateLimit, expireDays, createdBy));
    }

    /**
     * 查询 API Key 列表。
     */
    @GetMapping
    public Result<List<ApiKey>> listKeys() {
        return Result.success(apiKeyService.listKeys());
    }

    /**
     * 获取 API Key 详情。
     */
    @GetMapping("/{id}")
    public Result<ApiKey> getKey(@PathVariable Long id) {
        return Result.success(apiKeyService.getKey(id));
    }

    /**
     * 吊销 API Key。
     */
    @DeleteMapping("/{id}")
    public Result<Void> revokeKey(@PathVariable Long id) {
        apiKeyService.revokeKey(id);
        return Result.success(null);
    }
}
