package com.geosaa.modules.distribute.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geosaa.common.PageResult;
import com.geosaa.common.Result;
import com.geosaa.modules.common.TaskProgress;
import com.geosaa.modules.distribute.dto.DistributeRequest;
import com.geosaa.modules.distribute.entity.DistributeTask;
import com.geosaa.modules.distribute.service.DistributeService;
import com.geosaa.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 分发控制器 - 分发任务管理、渠道管理、进度追踪
 */
@RestController
@RequestMapping("/api/v1/distribute")
@RequiredArgsConstructor
public class DistributeController {

    private final DistributeService distributeService;

    /**
     * 分页查询分发任务列表
     */
    @GetMapping("/list")
    public PageResult<DistributeTask> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String targetPlatform,
            @RequestParam(required = false) Integer status) {
        Page<DistributeTask> page = distributeService.listTasks(pageNum, pageSize, targetPlatform, status);
        return PageResult.success(page);
    }

    /**
     * 根据ID获取分发任务详情
     */
    @GetMapping("/{id}")
    public Result<DistributeTask> getById(@PathVariable Long id) {
        DistributeTask task = distributeService.getTaskById(id);
        return Result.success(task);
    }

    /**
     * 创建分发任务（推送到 RabbitMQ 队列异步处理）
     */
    @PostMapping("/create")
    @PreAuthorize("hasAuthority('distribute:all')")
    public Result<DistributeTask> create(@Valid @RequestBody DistributeRequest request) {
        DistributeTask task = distributeService.createTask(request, SecurityUtils.getCurrentUserId());
        return Result.success(task);
    }

    /**
     * 取消分发任务
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('distribute:all')")
    public Result<Void> cancel(@PathVariable Long id) {
        distributeService.cancelTask(id);
        return Result.success(null);
    }

    /**
     * 删除分发任务
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('distribute:all')")
    public Result<Void> delete(@PathVariable Long id) {
        distributeService.deleteTask(id);
        return Result.success(null);
    }

    /**
     * 获取渠道列表（模拟 151+ 渠道）
     */
    @GetMapping("/channels")
    public Result<Map<String, String>> getChannels() {
        Map<String, String> channels = distributeService.getChannelList();
        return Result.success("共" + distributeService.getChannelCount() + "个渠道", channels);
    }

    /**
     * 获取分发任务进度
     */
    @GetMapping("/{id}/progress")
    public Result<TaskProgress> getProgress(@PathVariable Long id) {
        TaskProgress progress = (TaskProgress) distributeService.getTaskProgress(id);
        if (progress == null) {
            return Result.error(404, "未找到进度信息");
        }
        return Result.success(progress);
    }

    /**
     * 分发结果回调接口。
     *
     * <p>注意：该接口会改写任务状态，属于写操作，因此同样要求 {@code distribute:all} 权限。
     * 若后续需要对接第三方渠道的无状态回调，应改为独立的签名校验（HMAC + 时间戳 + 幂等号），
     * 而不是放开为 permitAll。
     */
    @PostMapping("/callback/{id}")
    @PreAuthorize("hasAuthority('distribute:all')")
    public Result<Void> callback(@PathVariable Long id,
                                  @RequestParam boolean success,
                                  @RequestParam String resultInfo) {
        distributeService.handleCallback(id, success, resultInfo);
        return Result.success(null);
    }

    /**
     * 获取分发统计数据
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        Map<String, Object> stats = distributeService.getDistributeStats();
        return Result.success(stats);
    }
}