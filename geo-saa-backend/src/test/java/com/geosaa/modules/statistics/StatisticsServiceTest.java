package com.geosaa.modules.statistics;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * StatisticsService 单测（O5 测试补全）。
 *
 * <p>覆盖：首页统计全量成功、各指标独立 try-catch 降级（诊断表异常/分发表异常）、
 * 可见度评分解析（合法 JSON/null/异常）、分发成功率计算、趋势数据连续填充、
 * 运行中任务+进度、最近报告、待办生成（各条件分支+全满足正向提示）。
 */
@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock private AiDiagnoseTaskMapper diagnoseTaskMapper;
    @Mock private AiArticleContentMapper contentMapper;
    @Mock private DistributeTaskMapper distributeTaskMapper;
    @Mock private BrandKnowledgeMapper knowledgeMapper;
    @Mock private TaskProgressService taskProgressService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private StatisticsService statisticsService;

    @BeforeEach
    void setUp() {
        statisticsService = new StatisticsService(diagnoseTaskMapper, contentMapper,
                distributeTaskMapper, knowledgeMapper, taskProgressService, objectMapper);
        // 默认：模拟模式开、采集器关 → 产生 system + monitor 待办
        ReflectionTestUtils.setField(statisticsService, "simulationEnabled", true);
        ReflectionTestUtils.setField(statisticsService, "collectorEnabled", false);
    }

    private AiDiagnoseTask diagnoseTask(Long id, Integer status, String resultContent, LocalDateTime createTime) {
        AiDiagnoseTask t = new AiDiagnoseTask();
        t.setId(id);
        t.setTaskName("诊断任务" + id);
        t.setStatus(status);
        t.setResultContent(resultContent);
        t.setCreateTime(createTime);
        return t;
    }

    private String reportJson(int score) {
        return "{\"report\":{\"score\":" + score + ",\"summary\":\"测试报告\"}}";
    }

    // ---------- 全量成功 ----------

    @Test
    void getDashboardStatisticsFullSuccess() {
        when(diagnoseTaskMapper.selectOne(any())).thenReturn(
                diagnoseTask(1L, Constant.TASK_STATUS_COMPLETED, reportJson(85), LocalDateTime.now()));
        when(contentMapper.selectCount(null)).thenReturn(10L);
        when(distributeTaskMapper.selectCount(any())).thenReturn(5L);
        when(distributeTaskMapper.selectCount(null)).thenReturn(10L);
        when(knowledgeMapper.selectCount(null)).thenReturn(3L);
        when(diagnoseTaskMapper.selectList(any())).thenReturn(List.of(
                diagnoseTask(2L, Constant.TASK_STATUS_COMPLETED, reportJson(80), LocalDateTime.now().minusDays(1))
        ));

        Map<String, Object> result = statisticsService.getDashboardStatistics();

        assertNotNull(result);
        assertEquals(85, result.get("visibilityScore"));
        assertEquals(10L, result.get("contentTotal"));
        assertEquals(5L, result.get("distributeSuccess"));
        assertEquals(50, result.get("distributeRate")); // 5/10*100
        assertNotNull(result.get("trendData"));
        assertNotNull(result.get("todos"));
        assertNotNull(result.get("runningTasks"));
        assertNotNull(result.get("recentReports"));
    }

    // ---------- 降级：诊断表异常 ----------

    @Test
    void getDashboardStatisticsDiagnoseExceptionDegradesGracefully() {
        when(diagnoseTaskMapper.selectOne(any())).thenThrow(new RuntimeException("DB 连接失败"));
        when(contentMapper.selectCount(null)).thenReturn(5L);
        when(distributeTaskMapper.selectCount(any())).thenReturn(2L);
        when(distributeTaskMapper.selectCount(null)).thenReturn(4L);
        when(knowledgeMapper.selectCount(null)).thenReturn(1L);
        when(diagnoseTaskMapper.selectList(any())).thenThrow(new RuntimeException("DB 连接失败"));

        Map<String, Object> result = statisticsService.getDashboardStatistics();

        // 诊断相关指标降级为 null/空，但整个接口不 500
        assertNull(result.get("visibilityScore"));
        assertEquals(5L, result.get("contentTotal"));
        Map<String, Object> trend = (Map<String, Object>) result.get("trendData");
        assertTrue(((List<?>) trend.get("categories")).isEmpty());
        assertTrue(((List<?>) trend.get("series")).isEmpty());
    }

    // ---------- 降级：分发表异常 ----------

    @Test
    void getDashboardStatisticsDistributeExceptionDegradesToZero() {
        when(diagnoseTaskMapper.selectOne(any())).thenReturn(null);
        when(contentMapper.selectCount(null)).thenReturn(3L);
        when(distributeTaskMapper.selectCount(any())).thenThrow(new RuntimeException("分发表异常"));
        // selectCount(null) 已被 any() 覆盖，不再单独 stub（否则记录调用时自身会抛异常）
        when(knowledgeMapper.selectCount(null)).thenReturn(2L);
        when(diagnoseTaskMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> result = statisticsService.getDashboardStatistics();

        assertEquals(0L, result.get("distributeSuccess"));
        assertEquals(0, result.get("distributeRate"));
    }

    // ---------- 可见度评分解析 ----------

    @Test
    void visibilityScoreParsesValidJson() {
        when(diagnoseTaskMapper.selectOne(any())).thenReturn(
                diagnoseTask(1L, Constant.TASK_STATUS_COMPLETED, reportJson(92), LocalDateTime.now()));
        when(contentMapper.selectCount(null)).thenReturn(0L);
        when(distributeTaskMapper.selectCount(any())).thenReturn(0L);
        when(distributeTaskMapper.selectCount(null)).thenReturn(0L);
        when(knowledgeMapper.selectCount(null)).thenReturn(0L);
        when(diagnoseTaskMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> result = statisticsService.getDashboardStatistics();
        assertEquals(92, result.get("visibilityScore"));
    }

    @Test
    void visibilityScoreTaskNullReturnsNull() {
        when(diagnoseTaskMapper.selectOne(any())).thenReturn(null);
        when(contentMapper.selectCount(null)).thenReturn(0L);
        when(distributeTaskMapper.selectCount(any())).thenReturn(0L);
        when(distributeTaskMapper.selectCount(null)).thenReturn(0L);
        when(knowledgeMapper.selectCount(null)).thenReturn(0L);
        when(diagnoseTaskMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> result = statisticsService.getDashboardStatistics();
        assertNull(result.get("visibilityScore"));
    }

    @Test
    void visibilityScoreResultContentNullReturnsNull() {
        when(diagnoseTaskMapper.selectOne(any())).thenReturn(
                diagnoseTask(1L, Constant.TASK_STATUS_COMPLETED, null, LocalDateTime.now()));
        when(contentMapper.selectCount(null)).thenReturn(0L);
        when(distributeTaskMapper.selectCount(any())).thenReturn(0L);
        when(distributeTaskMapper.selectCount(null)).thenReturn(0L);
        when(knowledgeMapper.selectCount(null)).thenReturn(0L);
        when(diagnoseTaskMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> result = statisticsService.getDashboardStatistics();
        assertNull(result.get("visibilityScore"));
    }

    @Test
    void visibilityScoreInvalidJsonReturnsNull() {
        when(diagnoseTaskMapper.selectOne(any())).thenReturn(
                diagnoseTask(1L, Constant.TASK_STATUS_COMPLETED, "不是合法JSON{{{", LocalDateTime.now()));
        when(contentMapper.selectCount(null)).thenReturn(0L);
        when(distributeTaskMapper.selectCount(any())).thenReturn(0L);
        when(distributeTaskMapper.selectCount(null)).thenReturn(0L);
        when(knowledgeMapper.selectCount(null)).thenReturn(0L);
        when(diagnoseTaskMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> result = statisticsService.getDashboardStatistics();
        assertNull(result.get("visibilityScore"));
    }

    // ---------- 分发成功率 ----------

    @Test
    void distributeRateTotalZeroReturnsZero() {
        when(diagnoseTaskMapper.selectOne(any())).thenReturn(null);
        when(contentMapper.selectCount(null)).thenReturn(0L);
        when(distributeTaskMapper.selectCount(any())).thenReturn(0L);
        when(distributeTaskMapper.selectCount(null)).thenReturn(0L);
        when(knowledgeMapper.selectCount(null)).thenReturn(0L);
        when(diagnoseTaskMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> result = statisticsService.getDashboardStatistics();
        assertEquals(0, result.get("distributeRate"));
    }

    // ---------- 趋势数据：连续填充（无 null 断点） ----------

    @Test
    void trendDataFillsZeroForUnparseableScores() {
        when(diagnoseTaskMapper.selectOne(any())).thenReturn(null);
        when(contentMapper.selectCount(null)).thenReturn(0L);
        when(distributeTaskMapper.selectCount(any())).thenReturn(0L);
        when(distributeTaskMapper.selectCount(null)).thenReturn(0L);
        when(knowledgeMapper.selectCount(null)).thenReturn(0L);
        // 一个合法评分 + 一个 resultContent 为 null（应填充 0，不产生 NaN）
        when(diagnoseTaskMapper.selectList(any())).thenReturn(List.of(
                diagnoseTask(1L, Constant.TASK_STATUS_COMPLETED, reportJson(70), LocalDateTime.of(2026, 8, 1, 10, 0)),
                diagnoseTask(2L, Constant.TASK_STATUS_COMPLETED, null, LocalDateTime.of(2026, 8, 2, 10, 0))
        ));

        Map<String, Object> result = statisticsService.getDashboardStatistics();
        Map<String, Object> trend = (Map<String, Object>) result.get("trendData");
        List<String> categories = (List<String>) trend.get("categories");
        List<Map<String, Object>> series = (List<Map<String, Object>>) trend.get("series");

        assertEquals(2, categories.size());
        assertTrue(categories.get(0).contains("08-01"));
        List<Integer> data = (List<Integer>) series.get(0).get("data");
        assertEquals(70, data.get(0));
        assertEquals(0, data.get(1)); // null 评分填充 0，不产生 NaN
    }

    // ---------- 运行中任务 + 进度 ----------

    @Test
    void runningTasksIncludesProgressFromTaskProgressService() {
        TaskProgress tp = new TaskProgress();
        tp.setPercentage(45);
        tp.setMessage("诊断进行中");
        when(taskProgressService.getProgress("diagnose:1")).thenReturn(tp);

        when(diagnoseTaskMapper.selectOne(any())).thenReturn(null);
        when(contentMapper.selectCount(null)).thenReturn(0L);
        when(distributeTaskMapper.selectCount(any())).thenReturn(0L);
        when(distributeTaskMapper.selectCount(null)).thenReturn(0L);
        when(knowledgeMapper.selectCount(null)).thenReturn(0L);
        when(diagnoseTaskMapper.selectList(any())).thenReturn(List.of());
        // runningTasks 查询：status=PROCESSING
        when(diagnoseTaskMapper.selectList(argThat(w -> true))).thenAnswer(inv -> {
            // 第二次 selectList（runningTasks）返回处理中任务
            return List.of(diagnoseTask(1L, Constant.TASK_STATUS_PROCESSING, null, LocalDateTime.now()));
        });

        Map<String, Object> result = statisticsService.getDashboardStatistics();
        List<Map<String, Object>> running = (List<Map<String, Object>>) result.get("runningTasks");
        assertEquals(1, running.size());
        assertEquals(45, running.get(0).get("progress"));
        assertEquals("诊断进行中", running.get(0).get("detail"));
        assertEquals("running", running.get(0).get("status"));
    }

    // ---------- 最近报告 ----------

    @Test
    void recentReportsReturnsCompletedTasks() {
        when(diagnoseTaskMapper.selectOne(any())).thenReturn(null);
        when(contentMapper.selectCount(null)).thenReturn(0L);
        when(distributeTaskMapper.selectCount(any())).thenReturn(0L);
        when(distributeTaskMapper.selectCount(null)).thenReturn(0L);
        when(knowledgeMapper.selectCount(null)).thenReturn(0L);
        when(diagnoseTaskMapper.selectList(any())).thenReturn(List.of(
                diagnoseTask(10L, Constant.TASK_STATUS_COMPLETED, reportJson(88), LocalDateTime.of(2026, 8, 25, 10, 0))
        ));

        Map<String, Object> result = statisticsService.getDashboardStatistics();
        List<Map<String, Object>> reports = (List<Map<String, Object>>) result.get("recentReports");
        assertEquals(1, reports.size());
        assertEquals(10L, reports.get(0).get("id"));
        assertEquals("诊断任务10", reports.get(0).get("name"));
        assertNotNull(reports.get(0).get("date"));
    }

    // ---------- 待办：各条件分支 ----------

    @Test
    void todosIncludeKnowledgeWhenKnowledgeZero() {
        when(diagnoseTaskMapper.selectOne(any())).thenReturn(null);
        when(contentMapper.selectCount(null)).thenReturn(5L);
        when(distributeTaskMapper.selectCount(any())).thenReturn(3L);
        when(distributeTaskMapper.selectCount(null)).thenReturn(5L);
        when(knowledgeMapper.selectCount(null)).thenReturn(0L);
        when(diagnoseTaskMapper.selectList(any())).thenReturn(List.of());
        // 关闭模拟+开启采集，避免产生 system/monitor 待办
        ReflectionTestUtils.setField(statisticsService, "simulationEnabled", false);
        ReflectionTestUtils.setField(statisticsService, "collectorEnabled", true);

        Map<String, Object> result = statisticsService.getDashboardStatistics();
        List<Map<String, Object>> todos = (List<Map<String, Object>>) result.get("todos");
        assertTrue(todos.stream().anyMatch(t -> "knowledge".equals(t.get("type"))));
    }

    @Test
    void todosIncludeContentWhenContentZero() {
        when(diagnoseTaskMapper.selectOne(any())).thenReturn(null);
        when(contentMapper.selectCount(null)).thenReturn(0L);
        when(distributeTaskMapper.selectCount(any())).thenReturn(0L);
        when(distributeTaskMapper.selectCount(null)).thenReturn(0L);
        when(knowledgeMapper.selectCount(null)).thenReturn(5L);
        when(diagnoseTaskMapper.selectList(any())).thenReturn(List.of());
        ReflectionTestUtils.setField(statisticsService, "simulationEnabled", false);
        ReflectionTestUtils.setField(statisticsService, "collectorEnabled", true);

        Map<String, Object> result = statisticsService.getDashboardStatistics();
        List<Map<String, Object>> todos = (List<Map<String, Object>>) result.get("todos");
        assertTrue(todos.stream().anyMatch(t -> "content".equals(t.get("type"))));
    }

    @Test
    void todosIncludeDistributeWhenContentExistsButNoSuccess() {
        when(diagnoseTaskMapper.selectOne(any())).thenReturn(null);
        when(contentMapper.selectCount(null)).thenReturn(5L);
        when(distributeTaskMapper.selectCount(any())).thenReturn(0L);
        when(distributeTaskMapper.selectCount(null)).thenReturn(3L);
        when(knowledgeMapper.selectCount(null)).thenReturn(5L);
        when(diagnoseTaskMapper.selectList(any())).thenReturn(List.of());
        ReflectionTestUtils.setField(statisticsService, "simulationEnabled", false);
        ReflectionTestUtils.setField(statisticsService, "collectorEnabled", true);

        Map<String, Object> result = statisticsService.getDashboardStatistics();
        List<Map<String, Object>> todos = (List<Map<String, Object>>) result.get("todos");
        assertTrue(todos.stream().anyMatch(t -> "distribute".equals(t.get("type"))));
    }

    @Test
    void todosIncludeSystemWhenSimulationEnabled() {
        when(diagnoseTaskMapper.selectOne(any())).thenReturn(null);
        when(contentMapper.selectCount(null)).thenReturn(5L);
        when(distributeTaskMapper.selectCount(any())).thenReturn(3L);
        when(distributeTaskMapper.selectCount(null)).thenReturn(5L);
        when(knowledgeMapper.selectCount(null)).thenReturn(5L);
        when(diagnoseTaskMapper.selectList(any())).thenReturn(List.of());
        // simulationEnabled=true（默认）, collectorEnabled=true
        ReflectionTestUtils.setField(statisticsService, "collectorEnabled", true);

        Map<String, Object> result = statisticsService.getDashboardStatistics();
        List<Map<String, Object>> todos = (List<Map<String, Object>>) result.get("todos");
        assertTrue(todos.stream().anyMatch(t -> "system".equals(t.get("type"))));
    }

    @Test
    void todosIncludeMonitorWhenCollectorDisabled() {
        when(diagnoseTaskMapper.selectOne(any())).thenReturn(null);
        when(contentMapper.selectCount(null)).thenReturn(5L);
        when(distributeTaskMapper.selectCount(any())).thenReturn(3L);
        when(distributeTaskMapper.selectCount(null)).thenReturn(5L);
        when(knowledgeMapper.selectCount(null)).thenReturn(5L);
        when(diagnoseTaskMapper.selectList(any())).thenReturn(List.of());
        // simulationEnabled=false, collectorEnabled=false（默认）
        ReflectionTestUtils.setField(statisticsService, "simulationEnabled", false);

        Map<String, Object> result = statisticsService.getDashboardStatistics();
        List<Map<String, Object>> todos = (List<Map<String, Object>>) result.get("todos");
        assertTrue(todos.stream().anyMatch(t -> "monitor".equals(t.get("type"))));
    }

    @Test
    void todosAllSatisfiedReturnsPositiveDoneMessage() {
        when(diagnoseTaskMapper.selectOne(any())).thenReturn(null);
        when(contentMapper.selectCount(null)).thenReturn(5L);
        when(distributeTaskMapper.selectCount(any())).thenReturn(3L);
        when(distributeTaskMapper.selectCount(null)).thenReturn(5L);
        when(knowledgeMapper.selectCount(null)).thenReturn(5L);
        when(diagnoseTaskMapper.selectList(any())).thenReturn(List.of());
        // 全部满足：knowledge>0, content>0, distributeSuccess>0, simulation off, collector on
        ReflectionTestUtils.setField(statisticsService, "simulationEnabled", false);
        ReflectionTestUtils.setField(statisticsService, "collectorEnabled", true);

        Map<String, Object> result = statisticsService.getDashboardStatistics();
        List<Map<String, Object>> todos = (List<Map<String, Object>>) result.get("todos");
        assertEquals(1, todos.size());
        assertEquals("done", todos.get(0).get("type"));
        assertTrue(((String) todos.get(0).get("text")).contains("运行良好"));
    }
}
