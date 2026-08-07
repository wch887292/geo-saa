package com.geosaa.modules.statistics;

import com.geosaa.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 工作台首页统计接口。
 *
 * <p>聚合 diagnose / content / distribute 等模块的汇总数据，供前端 dashboard 使用。
 * 每个指标在 service 内独立降级，接口本身不会因单表缺失而整体失败。
 */
@RestController
@RequestMapping("/api/v1/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        return Result.success(statisticsService.getDashboardStatistics());
    }
}
