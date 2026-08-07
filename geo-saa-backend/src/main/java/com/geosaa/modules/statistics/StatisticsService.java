package com.geosaa.modules.statistics;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geosaa.common.Constant;
import com.geosaa.modules.common.TaskProgress;
import com.geosaa.modules.common.TaskProgressService;
import com.geosaa.modules.content.mapper.AiArticleContentMapper;
import com.geosaa.modules.diagnose.entity.AiDiagnoseTask;
import com.geosaa.modules.diagnose.mapper.AiDiagnoseTaskMapper;
import com.geosaa.modules.distribute.entity.DistributeTask;
import com.geosaa.modules.distribute.mapper.DistributeTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 工作台首页统计聚合服务。
 *
 * <p>首页需要一组汇总指标，但项目目前并没有一个统一的“统计快照”表，
 * 各类数据分散在 diagnose / content / distribute 等模块里。这里按需聚合，
 * 并且每个指标都单独 try-catch：某张表缺失或某次解析失败时，只让该指标降级为
 * 0 / 空集合，绝不让整个首页接口 500。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final AiDiagnoseTaskMapper diagnoseTaskMapper;
    private final AiArticleContentMapper contentMapper;
    private final DistributeTaskMapper distributeTaskMapper;
    private final TaskProgressService taskProgressService;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM-dd");

    public Map<String, Object> getDashboardStatistics() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("visibilityScore", safeVisibilityScore());
        result.put("visibilityChange", null);            // 暂无历史基线，前端展示占位
        result.put("contentTotal", safeCount(contentMapper.selectCount(null)));
        result.put("contentGrowth", null);
        result.put("distributeSuccess", safeDistributeSuccess());
        result.put("distributeRate", safeDistributeRate());
        result.put("rank", null);
        result.put("rankChange", null);
        result.put("trendData", safeVisibilityTrend());
        result.put("todos", Collections.emptyList());   // 暂无待办数据源
        result.put("runningTasks", safeRunningTasks());
        result.put("recentReports", safeRecentReports());

        return result;
    }

    private Integer safeVisibilityScore() {
        try {
            LambdaQueryWrapper<AiDiagnoseTask> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AiDiagnoseTask::getStatus, Constant.TASK_STATUS_COMPLETED)
                    .orderByDesc(AiDiagnoseTask::getCreateTime)
                    .last("LIMIT 1");
            AiDiagnoseTask task = diagnoseTaskMapper.selectOne(wrapper);
            if (task == null || task.getResultContent() == null) {
                return null;
            }
            return parseScore(task.getResultContent());
        } catch (Exception e) {
            log.warn("统计-可见度评分聚合失败: {}", e.getMessage());
            return null;
        }
    }

    private Integer parseScore(String resultContent) {
        try {
            Map<?, ?> root = objectMapper.readValue(resultContent, Map.class);
            Object report = root.get("report");
            if (report instanceof Map) {
                Object score = ((Map<?, ?>) report).get("score");
                if (score instanceof Number) {
                    return ((Number) score).intValue();
                }
            }
        } catch (Exception e) {
            log.warn("解析诊断报告评分失败: {}", e.getMessage());
        }
        return null;
    }

    private long safeCount(Long count) {
        return count == null ? 0L : count;
    }

    private long safeDistributeSuccess() {
        try {
            LambdaQueryWrapper<DistributeTask> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(DistributeTask::getStatus, Constant.TASK_STATUS_COMPLETED);
            return safeCount(distributeTaskMapper.selectCount(wrapper));
        } catch (Exception e) {
            log.warn("统计-分发成功数聚合失败: {}", e.getMessage());
            return 0L;
        }
    }

    private int safeDistributeRate() {
        try {
            long total = safeCount(distributeTaskMapper.selectCount(null));
            if (total == 0) {
                return 0;
            }
            long success = safeDistributeSuccess();
            return (int) (success * 100 / total);
        } catch (Exception e) {
            log.warn("统计-分发成功率聚合失败: {}", e.getMessage());
            return 0;
        }
    }

    private Map<String, Object> safeVisibilityTrend() {
        Map<String, Object> trend = new LinkedHashMap<>();
        try {
            LambdaQueryWrapper<AiDiagnoseTask> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AiDiagnoseTask::getStatus, Constant.TASK_STATUS_COMPLETED)
                    .orderByAsc(AiDiagnoseTask::getCreateTime)
                    .last("LIMIT 7");
            List<AiDiagnoseTask> tasks = diagnoseTaskMapper.selectList(wrapper);
            List<String> categories = new ArrayList<>();
            List<Integer> series = new ArrayList<>();
            for (AiDiagnoseTask task : tasks) {
                if (task.getCreateTime() != null) {
                    categories.add(task.getCreateTime().format(DATE_FMT));
                } else {
                    categories.add("-");
                }
                series.add(parseScore(task.getResultContent()));
            }
            trend.put("categories", categories);
            trend.put("series", Collections.singletonList(new LinkedHashMap<String, Object>() {{
                put("name", "本品牌");
                put("data", series);
            }}));
        } catch (Exception e) {
            log.warn("统计-可见度趋势聚合失败: {}", e.getMessage());
            trend.put("categories", Collections.emptyList());
            trend.put("series", Collections.emptyList());
        }
        return trend;
    }

    private List<Map<String, Object>> safeRunningTasks() {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            LambdaQueryWrapper<AiDiagnoseTask> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AiDiagnoseTask::getStatus, Constant.TASK_STATUS_PROCESSING)
                    .orderByDesc(AiDiagnoseTask::getCreateTime);
            List<AiDiagnoseTask> tasks = diagnoseTaskMapper.selectList(wrapper);
            for (AiDiagnoseTask task : tasks) {
                int progress = 0;
                String detail = "诊断处理中";
                try {
                    TaskProgress tp = (TaskProgress) taskProgressService.getProgress("diagnose:" + task.getId());
                    if (tp != null) {
                        progress = tp.getPercentage();
                        detail = tp.getMessage();
                    }
                } catch (Exception ignored) {
                    // 进度取不到就用 0，不影响整体
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", task.getId());
                item.put("name", task.getTaskName());
                item.put("progress", progress);
                item.put("status", "running");
                item.put("detail", detail);
                list.add(item);
            }
        } catch (Exception e) {
            log.warn("统计-运行中的任务聚合失败: {}", e.getMessage());
        }
        return list;
    }

    private List<Map<String, Object>> safeRecentReports() {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            LambdaQueryWrapper<AiDiagnoseTask> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AiDiagnoseTask::getStatus, Constant.TASK_STATUS_COMPLETED)
                    .orderByDesc(AiDiagnoseTask::getCreateTime)
                    .last("LIMIT 5");
            List<AiDiagnoseTask> tasks = diagnoseTaskMapper.selectList(wrapper);
            for (AiDiagnoseTask task : tasks) {
                list.add(new LinkedHashMap<String, Object>() {{
                    put("id", task.getId());
                    put("name", task.getTaskName());
                    put("date", task.getCreateTime() != null ? task.getCreateTime().toString() : "-");
                }});
            }
        } catch (Exception e) {
            log.warn("统计-最近报告聚合失败: {}", e.getMessage());
        }
        return list;
    }
}
