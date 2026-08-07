package com.geosaa.modules.knowledge.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geosaa.common.PageResult;
import com.geosaa.common.Result;
import com.geosaa.modules.knowledge.entity.BrandInfo;
import com.geosaa.modules.knowledge.entity.BrandKnowledge;
import com.geosaa.modules.knowledge.entity.KnowledgeVersionHistory;
import com.geosaa.modules.knowledge.service.KnowledgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 知识库控制器 - 品牌信息管理、品牌知识管理、GEO知识结构化
 */
@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    // ========== 品牌信息接口 ==========

    /**
     * 分页查询品牌列表
     */
    @GetMapping("/brands")
    public PageResult<BrandInfo> listBrands(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String brandName,
            @RequestParam(required = false) String industry) {
        Page<BrandInfo> page = knowledgeService.listBrands(pageNum, pageSize, brandName, industry);
        return PageResult.success(page);
    }

    /**
     * 根据ID获取品牌信息
     */
    @GetMapping("/brands/{id}")
    public Result<BrandInfo> getBrandById(@PathVariable Long id) {
        BrandInfo brandInfo = knowledgeService.getBrandById(id);
        return Result.success(brandInfo);
    }

    /**
     * 创建品牌信息
     */
    @PostMapping("/brands")
    @PreAuthorize("hasAuthority('knowledge:all')")
    public Result<BrandInfo> createBrand(@Valid @RequestBody BrandInfo brandInfo) {
        knowledgeService.createBrand(brandInfo);
        return Result.success(brandInfo);
    }

    /**
     * 更新品牌信息
     */
    @PutMapping("/brands")
    @PreAuthorize("hasAuthority('knowledge:all')")
    public Result<BrandInfo> updateBrand(@Valid @RequestBody BrandInfo brandInfo) {
        knowledgeService.updateBrand(brandInfo);
        return Result.success(brandInfo);
    }

    /**
     * 删除品牌信息
     */
    @DeleteMapping("/brands/{id}")
    @PreAuthorize("hasAuthority('knowledge:all')")
    public Result<Void> deleteBrand(@PathVariable Long id) {
        knowledgeService.deleteBrand(id);
        return Result.success(null);
    }

    // ========== 品牌知识接口 ==========

    /**
     * 获取品牌下的所有知识
     */
    @GetMapping("/brands/{brandId}/knowledge")
    public Result<List<BrandKnowledge>> listKnowledge(@PathVariable Long brandId) {
        List<BrandKnowledge> list = knowledgeService.listKnowledgeByBrand(brandId);
        return Result.success(list);
    }

    /**
     * 创建品牌知识（自动校验唯一性，初始化版本）
     */
    @PostMapping("/knowledge")
    @PreAuthorize("hasAuthority('knowledge:all')")
    public Result<BrandKnowledge> createKnowledge(@Valid @RequestBody BrandKnowledge knowledge) {
        knowledgeService.createKnowledge(knowledge);
        return Result.success(knowledge);
    }

    /**
     * 更新品牌知识（自动递增版本号）
     */
    @PutMapping("/knowledge")
    @PreAuthorize("hasAuthority('knowledge:all')")
    public Result<BrandKnowledge> updateKnowledge(@Valid @RequestBody BrandKnowledge knowledge) {
        knowledgeService.updateKnowledge(knowledge);
        return Result.success(knowledge);
    }

    /**
     * 删除品牌知识
     */
    @DeleteMapping("/knowledge/{id}")
    @PreAuthorize("hasAuthority('knowledge:all')")
    public Result<Void> deleteKnowledge(@PathVariable Long id) {
        knowledgeService.deleteKnowledge(id);
        return Result.success(null);
    }

    /**
     * 获取知识版本历史
     */
    @GetMapping("/knowledge/{id}/versions")
    public Result<List<KnowledgeVersionHistory>> getKnowledgeVersions(@PathVariable Long id) {
        List<KnowledgeVersionHistory> versions = knowledgeService.getKnowledgeVersions(id);
        return Result.success(versions);
    }

    // ========== GEO 结构化接口 ==========

    /**
     * AI 自动结构化转换：将非结构化文本转为结构化 JSON
     */
    @PostMapping("/auto-structure/{brandId}")
    @PreAuthorize("hasAuthority('knowledge:all')")
    public Result<String> autoStructure(@PathVariable Long brandId, @RequestBody Map<String, String> request) {
        String unstructuredText = request.get("text");
        if (unstructuredText == null || unstructuredText.isEmpty()) {
            return Result.error(400, "文本内容不能为空");
        }
        String result = knowledgeService.autoStructureConversion(brandId, unstructuredText);
        return Result.success(result);
    }

    /**
     * 生成 JSON-LD（Schema.org 标准）
     */
    @GetMapping("/json-ld/{brandId}")
    public Result<String> generateJsonLd(@PathVariable Long brandId) {
        String jsonLd = knowledgeService.generateJsonLd(brandId);
        return Result.success(jsonLd);
    }
}