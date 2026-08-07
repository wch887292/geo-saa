package com.geosaa.modules.diagnose.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geosaa.common.PageResult;
import com.geosaa.common.Result;
import com.geosaa.modules.common.TaskProgress;
import com.geosaa.modules.diagnose.dto.DiagnoseRequest;
import com.geosaa.modules.diagnose.entity.AiDiagnoseTask;
import com.geosaa.modules.diagnose.service.DiagnoseService;
import com.geosaa.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 诊断控制器 - 品牌可见度诊断、进度查询、报告获取
 */
@RestController
@RequestMapping("/api/v1/diagnose")
@RequiredArgsConstructor
public class DiagnoseController {

    private final DiagnoseService diagnoseService;

    /**
     * 分页查询诊断任务列表
     */
    @GetMapping("/list")
    public PageResult<AiDiagnoseTask> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) Integer status) {
        Page<AiDiagnoseTask> page = diagnoseService.listTasks(pageNum, pageSize, taskType, status);
        return PageResult.success(page);
    }

    /**
     * 根据ID获取诊断任务详情
     */
    @GetMapping("/{id}")
    public Result<AiDiagnoseTask> getById(@PathVariable Long id) {
        AiDiagnoseTask task = diagnoseService.getTaskById(id);
        return Result.success(task);
    }

    /**
     * 创建诊断任务（异步执行，立即返回）
     */
    @PostMapping("/create")
    @PreAuthorize("hasAuthority('diagnose:all')")
    public Result<AiDiagnoseTask> create(@Valid @RequestBody DiagnoseRequest request) {
        AiDiagnoseTask task = diagnoseService.createTask(request, SecurityUtils.getCurrentUserId());
        return Result.success(task);
    }

    /**
     * 删除诊断任务
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('diagnose:all')")
    public Result<Void> delete(@PathVariable Long id) {
        diagnoseService.deleteTask(id);
        return Result.success(null);
    }

    /**
     * 获取诊断任务进度
     */
    @GetMapping("/{id}/progress")
    public Result<TaskProgress> getProgress(@PathVariable Long id) {
        TaskProgress progress = (TaskProgress) diagnoseService.getTaskProgress(id);
        if (progress == null) {
            return Result.error(404, "未找到进度信息");
        }
        return Result.success(progress);
    }

    /**
     * 获取诊断报告
     */
    @GetMapping("/{id}/report")
    public Result<Map<String, Object>> getReport(@PathVariable Long id) {
        Map<String, Object> report = diagnoseService.getDiagnosisReport(id);
        return Result.success(report);
    }
}