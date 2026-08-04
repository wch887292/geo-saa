package com.geosaa.modules.monitor.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geosaa.common.PageResult;
import com.geosaa.common.Result;
import com.geosaa.modules.monitor.entity.DataMonitorStat;
import com.geosaa.modules.monitor.service.MonitorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 数据监测控制器 - 核心指标、趋势数据、竞品对比
 */
@RestController
@RequestMapping("/api/v1/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final MonitorService monitorService;

    /**
     * 分页查询统计数据
     */
    @GetMapping("/list")
    public PageResult<DataMonitorStat> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String statType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Page<DataMonitorStat> page = monitorService.listStats(pageNum, pageSize, statType, startDate, endDate);
        return PageResult.success(page);
    }

    /**
     * 按日期获取统计数据
     */
    @GetMapping("/date/{date}")
    public Result<List<DataMonitorStat>> getByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String statType) {
        List<DataMonitorStat> list = monitorService.getStatsByDate(date, statType);
        return Result.success(list);
    }

    /**
     * 添加统计数据
     */
    @PostMapping("/add")
    public Result<Void> add(@Valid @RequestBody DataMonitorStat stat) {
        monitorService.addStat(stat);
        return Result.success(null);
    }

    /**
     * 获取核心指标（AI 提及率、首推占比、收录量）- 带 Redis 缓存
     */
    @GetMapping("/core-metrics")
    public Result<Map<String, Object>> getCoreMetrics(@RequestParam(required = false) String brandName) {
        Map<String, Object> metrics = monitorService.getCoreMetrics(brandName);
        return Result.success(metrics);
    }

    /**
     * 获取趋势数据（按日/周/月聚合）- 带 Redis 缓存
     */
    @GetMapping("/trend")
    public Result<Map<String, Object>> getTrend(
            @RequestParam(defaultValue = "mention_rate") String statType,
            @RequestParam(defaultValue = "day") String period,
            @RequestParam(defaultValue = "30") int days) {
        Map<String, Object> trend = monitorService.getTrendData(statType, period, days);
        return Result.success(trend);
    }

    /**
     * 获取竞品对比数据 - 带 Redis 缓存
     */
    @GetMapping("/competitor")
    public Result<List<Map<String, Object>>> getCompetitor(@RequestParam(required = false) String brandName) {
        List<Map<String, Object>> competitors = monitorService.getCompetitorComparison(brandName);
        return Result.success(competitors);
    }
}