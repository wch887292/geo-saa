package com.geosaa.modules.ai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geosaa.common.Result;
import com.geosaa.modules.ai.dto.AiChatResponse;
import com.geosaa.modules.ai.entity.AiModelConfig;
import com.geosaa.modules.ai.service.AiModelConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 大模型设置 Controller（自定义大模型管理）
 */
@RestController
@RequestMapping("/api/v1/ai/model")
@RequiredArgsConstructor
public class AiModelController {

    private final AiModelConfigService modelConfigService;

    @GetMapping("/page")
    public Result<Page<AiModelConfig>> page(@RequestParam(defaultValue = "1") int pageNum,
                                        @RequestParam(defaultValue = "10") int pageSize,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String provider,
                                        @RequestParam(required = false) String status) {
        return Result.success(modelConfigService.page(pageNum, pageSize, keyword, provider, status));
    }

    @GetMapping("/available")
    public Result<List<AiModelConfig>> available() {
        return Result.success(modelConfigService.getAvailableModels());
    }

    @GetMapping("/{id}")
    public Result<AiModelConfig> getById(@PathVariable Long id) {
        return Result.success(modelConfigService.getById(id));
    }

    @GetMapping("/default")
    public Result<AiModelConfig> getDefault() {
        return Result.success(modelConfigService.getDefaultModel());
    }

    @PostMapping
    public Result<AiModelConfig> create(@RequestBody AiModelConfig config) {
        return Result.success(modelConfigService.create(config));
    }

    @PutMapping
    public Result<AiModelConfig> update(@RequestBody AiModelConfig config) {
        return Result.success(modelConfigService.update(config));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        modelConfigService.delete(id);
        return Result.success(null);
    }

    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable Long id) {
        modelConfigService.setDefault(id);
        return Result.success(null);
    }

    @PostMapping("/{id}/test")
    public Result<AiChatResponse> testConnection(@PathVariable Long id) {
        return Result.success(modelConfigService.testConnection(id));
    }
}
