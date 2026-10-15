package com.geosaa.modules.ai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geosaa.common.Result;
import com.geosaa.modules.ai.entity.AiPromptTemplate;
import com.geosaa.modules.ai.service.AiPromptTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI Prompt 模板 Controller
 */
@RestController
@RequestMapping("/api/v1/ai/prompt-template")
@RequiredArgsConstructor
public class AiPromptTemplateController {

    private final AiPromptTemplateService templateService;

    @GetMapping("/page")
    public Result<Page<AiPromptTemplate>> page(@RequestParam(defaultValue = "1") int pageNum,
                                           @RequestParam(defaultValue = "10") int pageSize,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String businessModule) {
        return Result.success(templateService.page(pageNum, pageSize, keyword, businessModule));
    }

    @GetMapping("/code/{code}")
    public Result<AiPromptTemplate> getByCode(@PathVariable String code) {
        return Result.success(templateService.getByCode(code));
    }

    @PostMapping("/render/{code}")
    public Result<String> render(@PathVariable String code, @RequestBody Map<String, String> variables) {
        return Result.success(templateService.renderTemplate(code, variables));
    }

    @PostMapping
    public Result<AiPromptTemplate> create(@RequestBody AiPromptTemplate template) {
        return Result.success(templateService.create(template));
    }

    @PutMapping
    public Result<AiPromptTemplate> update(@RequestBody AiPromptTemplate template) {
        return Result.success(templateService.update(template));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return Result.success(null);
    }
}
