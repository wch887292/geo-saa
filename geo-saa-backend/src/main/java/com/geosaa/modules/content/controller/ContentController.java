package com.geosaa.modules.content.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geosaa.common.PageResult;
import com.geosaa.common.Result;
import com.geosaa.modules.content.dto.ContentGenerateRequest;
import com.geosaa.modules.content.entity.AiArticleContent;
import com.geosaa.modules.content.service.ContentService;
import com.geosaa.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI 创作控制器 - 内容管理、批量生成、模板管理、合规检测
 */
@RestController
@RequestMapping("/api/v1/content")
@RequiredArgsConstructor
public class ContentController {

    private final ContentService contentService;

    /**
     * 分页查询内容列表
     */
    @GetMapping("/list")
    public PageResult<AiArticleContent> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) Integer status) {
        Page<AiArticleContent> page = contentService.listContents(pageNum, pageSize, contentType, status);
        return PageResult.success(page);
    }

    /**
     * 根据ID获取内容详情
     */
    @GetMapping("/{id}")
    public Result<AiArticleContent> getById(@PathVariable Long id) {
        AiArticleContent content = contentService.getContentById(id);
        return Result.success(content);
    }

    /**
     * 创建内容（自动合规检测）
     */
    @PostMapping("/create")
    @PreAuthorize("hasAuthority('content:all')")
    public Result<AiArticleContent> create(@Valid @RequestBody ContentGenerateRequest request) {
        AiArticleContent content = contentService.createContent(request, SecurityUtils.getCurrentUserId());
        return Result.success(content);
    }

    /**
     * 更新内容
     */
    @PutMapping("/update")
    @PreAuthorize("hasAuthority('content:all')")
    public Result<AiArticleContent> update(@Valid @RequestBody AiArticleContent content) {
        contentService.updateContent(content);
        return Result.success(content);
    }

    /**
     * 删除内容
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('content:all')")
    public Result<Void> delete(@PathVariable Long id) {
        contentService.deleteContent(id);
        return Result.success(null);
    }

    /**
     * AI 生成单篇内容
     */
    @PostMapping("/generate/{id}")
    @PreAuthorize("hasAuthority('content:all')")
    public Result<AiArticleContent> generateWithAi(@PathVariable Long id) {
        AiArticleContent content = contentService.generateWithAi(id);
        return Result.success(content);
    }

    /**
     * 批量生成文章（使用 RabbitMQ 异步队列）
     */
    @PostMapping("/batch-generate")
    @PreAuthorize("hasAuthority('content:all')")
    public Result<List<Long>> batchGenerate(@Valid @RequestBody List<ContentGenerateRequest> requests) {
        List<Long> contentIds = contentService.batchGenerateArticles(requests, SecurityUtils.getCurrentUserId());
        return Result.success("批量生成任务已提交，共" + contentIds.size() + "篇", contentIds);
    }

    /**
     * 批量生成短视频脚本
     */
    @PostMapping("/batch-scripts")
    @PreAuthorize("hasAuthority('content:all')")
    public Result<List<AiArticleContent>> batchScripts(@Valid @RequestBody List<ContentGenerateRequest> requests) {
        List<AiArticleContent> contents = contentService.batchGenerateScripts(requests, SecurityUtils.getCurrentUserId());
        return Result.success("脚本生成任务已提交，共" + contents.size() + "个", contents);
    }

    /**
     * 获取行业模板列表
     */
    @GetMapping("/templates")
    public Result<Map<String, String>> getTemplates() {
        Map<String, String> templates = contentService.getIndustryTemplates();
        return Result.success(templates);
    }

    /**
     * 根据行业获取推荐模板
     */
    @GetMapping("/templates/{industry}")
    public Result<String> getTemplateByIndustry(@PathVariable String industry) {
        String template = contentService.getTemplateByIndustry(industry);
        return Result.success(template);
    }

    /**
     * 内容合规检测
     */
    @PostMapping("/check-compliance")
    public Result<String> checkCompliance(@RequestBody Map<String, String> request) {
        String content = request.get("content");
        String result = contentService.checkCompliance(content);
        if (result == null) {
            return Result.success("合规检测通过");
        }
        return Result.error(400, result);
    }

    /**
     * GEO 九战术内容健康度校验（Princeton KDD 2024 九大战术）。
     * 关键词堆砌密度超阈值时 {@code blocked=true}。
     */
    @PostMapping("/geo-validate")
    public Result<com.geosaa.modules.content.geo.GeoValidationResult> geoValidate(
            @RequestBody Map<String, String> request) {
        com.geosaa.modules.content.geo.GeoValidationResult result =
                contentService.validateGeo(request.get("content"), request.get("keywords"));
        return Result.success(result);
    }
}