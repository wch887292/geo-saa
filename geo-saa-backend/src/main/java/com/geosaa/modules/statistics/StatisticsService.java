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
import com.geosaa.modules.knowledge.mapper.BrandKnowledgeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final BrandKnowledgeMapper knowledgeMapper;
    private final TaskProgressService taskProgressService;
    private final ObjectMapper objectMapper;

    @Value("${ai.simulation.enabled:true}")
    private boolean simulationEnabled;

    @Value("${app.geo.collector.enabled:false}")
    private boolean collectorEnabled;

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
        result.put("todos", buildTodos());
        result.put("runningTasks", safeRunningTasks());
        result.put("recentReports", safeRecentReports());

        return result;
    }

    /**
     * 基于真实业务数据与配置状态生成首页待办（T4：替代恒空占位）。
     *
     * <p>每项结构 {@code {text, done, type, action}}，与前端 dashboard 待办卡片兼容；
     * 全部满足时给一条正向提示，避免空态。
     */
    private List<Map<String, Object>> buildTodos() {
        List<Map<String, Object>> todos = new ArrayList<>();
        long contentTotal = safeCount(contentMapper.selectCount(null));
        long knowledgeTotal = safeCount(knowledgeMapper.selectCount(null));
        long distributeSuccess = safeDistributeSuccess();

        if (knowledgeTotal == 0) {
            todos.add(todo("完善品牌知识库（核心关键词 / 产品优势 / 权威数据）", "knowledge"));
        }
        if (contentTotal == 0) {
            todos.add(todo("创建首批 AI 内容，为 GEO 引擎提供可引用的信源", "content"));
        }
        if (contentTotal > 0 && distributeSuccess == 0) {
            todos.add(todo("将已生成内容加入多渠道分发，扩大曝光", "distribute"));
        }
        if (simulationEnabled) {
            todos.add(todo("配置真实 AI API Key 并关闭模拟模式，获取真实诊断与采集数据", "system"));
        }
        if (!collectorEnabled) {
            todos.add(todo("启用 GEO 真实数据采集器（GEO_COLLECTOR_ENABLED=true），让监测指标可追溯", "monitor"));
        }
        if (todos.isEmpty()) {
            todos.add(todo("系统运行良好，保持内容产出与监测节奏", "done"));
        }
        return todos;
    }

    private Map<String, Object> todo(String text, String type) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("text", text);
        item.put("done", false);
        item.put("type", type);
        return item;
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
