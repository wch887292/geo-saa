package com.geosaa.modules.openapi.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geosaa.common.Result;
import com.geosaa.modules.diagnose.entity.AiDiagnoseTask;
import com.geosaa.modules.diagnose.mapper.AiDiagnoseTaskMapper;
import com.geosaa.modules.distribute.entity.DistributeTask;
import com.geosaa.modules.distribute.mapper.DistributeTaskMapper;
import com.geosaa.modules.knowledge.entity.BrandInfo;
import com.geosaa.modules.knowledge.entity.BrandKnowledge;
import com.geosaa.modules.knowledge.mapper.BrandKnowledgeMapper;
import com.geosaa.modules.knowledge.service.KnowledgeService;
import com.geosaa.modules.openapi.dto.OpenApiListResponse;
import com.geosaa.modules.rag.dto.RagQueryRequest;
import com.geosaa.modules.rag.dto.RagQueryResponse;
import com.geosaa.modules.rag.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 开放 API 控制器。
 *
 * <p>提供标准化的只读接口，供第三方系统通过 API Key 集成。
 * 所有接口路径以 /api/v1/open/ 开头，通过 X-API-Key 请求头认证。
 * 自动应用多租户隔离（API Key 绑定租户）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/open")
@RequiredArgsConstructor
public class OpenApiController {

    private final KnowledgeService knowledgeService;
    private final BrandKnowledgeMapper brandKnowledgeMapper;
    private final RagService ragService;
    private final AiDiagnoseTaskMapper diagnoseTaskMapper;
    private final DistributeTaskMapper distributeTaskMapper;

    // ========== 品牌接口 ==========

    /**
     * 获取品牌列表（分页）。
     */
    @GetMapping("/brands")
    public Result<OpenApiListResponse<BrandInfo>> listBrands(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String brandName,
            @RequestParam(required = false) String industry) {
        var page = knowledgeService.listBrands(pageNum, pageSize, brandName, industry);
        return Result.success(OpenApiListResponse.of(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }

    /**
     * 获取品牌详情。
     */
    @GetMapping("/brands/{id}")
    public Result<BrandInfo> getBrand(@PathVariable Long id) {
        return Result.success(knowledgeService.getBrandById(id));
    }

    // ========== 知识接口 ==========

    /**
     * 获取品牌下的知识列表。
     */
    @GetMapping("/brands/{brandId}/knowledge")
    public Result<List<BrandKnowledge>> listKnowledge(@PathVariable Long brandId) {
        return Result.success(knowledgeService.listKnowledgeByBrand(brandId));
    }

    /**
     * 获取知识详情。
     */
    @GetMapping("/knowledge/{id}")
    public Result<BrandKnowledge> getKnowledge(@PathVariable Long id) {
        return Result.success(brandKnowledgeMapper.selectById(id));
    }

    // ========== RAG 检索接口 ==========

    /**
     * RAG 检索增强问答。
     */
    @PostMapping("/rag/query")
    public Result<RagQueryResponse> ragQuery(@RequestBody RagQueryRequest request) {
        return Result.success(ragService.query(request));
    }

    // ========== 诊断任务接口 ==========

    /**
     * 获取诊断任务列表。
     */
    @GetMapping("/diagnose/tasks")
    public Result<OpenApiListResponse<AiDiagnoseTask>> listDiagnoseTasks(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<AiDiagnoseTask> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(AiDiagnoseTask::getStatus, status);
        }
        wrapper.orderByDesc(AiDiagnoseTask::getCreateTime);
        var page = diagnoseTaskMapper.selectPage(
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize), wrapper);
        return Result.success(OpenApiListResponse.of(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }

    /**
     * 获取诊断任务详情。
     */
    @GetMapping("/diagnose/tasks/{id}")
    public Result<AiDiagnoseTask> getDiagnoseTask(@PathVariable Long id) {
        return Result.success(diagnoseTaskMapper.selectById(id));
    }

    // ========== 分发任务接口 ==========

    /**
     * 获取分发任务列表。
     */
    @GetMapping("/distribute/tasks")
    public Result<OpenApiListResponse<DistributeTask>> listDistributeTasks(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String platform) {
        LambdaQueryWrapper<DistributeTask> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(DistributeTask::getStatus, status);
        }
        if (platform != null && !platform.isEmpty()) {
            wrapper.eq(DistributeTask::getTargetPlatform, platform);
        }
        wrapper.orderByDesc(DistributeTask::getCreateTime);
        var page = distributeTaskMapper.selectPage(
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize), wrapper);
        return Result.success(OpenApiListResponse.of(page.getRecords(), page.getTotal(), pageNum, pageSize));
    }

    /**
     * 获取分发任务详情。
     */
    @GetMapping("/distribute/tasks/{id}")
    public Result<DistributeTask> getDistributeTask(@PathVariable Long id) {
        return Result.success(distributeTaskMapper.selectById(id));
    }
}
