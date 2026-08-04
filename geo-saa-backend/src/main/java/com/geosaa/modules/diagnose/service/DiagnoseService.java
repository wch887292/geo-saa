package com.geosaa.modules.diagnose.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geosaa.ai.AiAdapterFactory;
import com.geosaa.ai.VisibilityResult;
import com.geosaa.common.Constant;
import com.geosaa.modules.common.TaskProgressService;
import com.geosaa.modules.diagnose.dto.DiagnoseRequest;
import com.geosaa.modules.diagnose.entity.AiDiagnoseTask;
import com.geosaa.modules.diagnose.mapper.AiDiagnoseTaskMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnoseService {

    private final AiDiagnoseTaskMapper diagnoseTaskMapper;
    private final AiAdapterFactory aiAdapterFactory;
    private final TaskProgressService taskProgressService;
    private final ObjectMapper objectMapper;

    public Page<AiDiagnoseTask> listTasks(int pageNum, int pageSize, String taskType, Integer status) {
        LambdaQueryWrapper<AiDiagnoseTask> wrapper = new LambdaQueryWrapper<>();
        if (taskType != null) {
            wrapper.eq(AiDiagnoseTask::getTaskType, taskType);
        }
        if (status != null) {
            wrapper.eq(AiDiagnoseTask::getStatus, status);
        }
        wrapper.orderByDesc(AiDiagnoseTask::getCreateTime);
        return diagnoseTaskMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public AiDiagnoseTask getTaskById(Long id) {
        return diagnoseTaskMapper.selectById(id);
    }

    public AiDiagnoseTask createTask(DiagnoseRequest request, Long userId) {
        AiDiagnoseTask task = new AiDiagnoseTask();
        task.setTaskName(request.getTaskName());
        task.setTaskType(request.getTaskType());
        task.setBrandName(request.getBrandName());
        task.setInputParams(request.getInputParams());
        task.setRemark(request.getRemark());
        task.setStatus(Constant.TASK_STATUS_PENDING);
        task.setCreatedBy(userId);
        diagnoseTaskMapper.insert(task);

        // 异步执行诊断
        executeDiagnosisAsync(task.getId(), request.getBrandName(), request.getTaskType(), request.getInputParams());

        return task;
    }

    public void deleteTask(Long id) {
        diagnoseTaskMapper.deleteById(id);
        taskProgressService.deleteProgress("diagnose:" + id);
    }

    /**
     * 异步执行诊断
     */
    @Async
    public void executeDiagnosisAsync(Long taskId, String brandName, String taskType, String inputParams) {
        String progressId = "diagnose:" + taskId;
        try {
            // 更新任务状态为处理中
            AiDiagnoseTask task = diagnoseTaskMapper.selectById(taskId);
            if (task == null) {
                log.warn("诊断任务不存在: taskId={}", taskId);
                return;
            }
            task.setStatus(Constant.TASK_STATUS_PROCESSING);
            diagnoseTaskMapper.updateById(task);

            // 初始化进度
            taskProgressService.initProgress(progressId, 5);

            // 步骤1: 分析品牌可见度
            taskProgressService.updateProgress(progressId, 1, "正在分析品牌可见度...");
            String platform = taskType != null ? taskType : "通用AI搜索";
            VisibilityResult visibility = aiAdapterFactory.getAdapter("openai")
                    .checkVisibility(brandName != null ? brandName : "未知品牌", platform);

            // 步骤2: 意图分析
            taskProgressService.updateProgress(progressId, 2, "正在进行意图分析...");
            String intentResult = aiAdapterFactory.getAdapter("openai")
                    .analyzeIntent(brandName != null ? brandName : "未知品牌", inputParams != null ? inputParams : "", platform);

            // 步骤3: 生成诊断报告
            taskProgressService.updateProgress(progressId, 3, "正在生成诊断报告...");
            Map<String, Object> report = generateDiagnosisReport(visibility, intentResult);

            // 步骤4: 生成竞品对比数据
            taskProgressService.updateProgress(progressId, 4, "正在生成竞品对比数据...");
            List<Map<String, Object>> competitorData = generateCompetitorComparison(brandName);

            // 步骤5: 完成
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("visibility", visibility);
            resultMap.put("intentAnalysis", intentResult);
            resultMap.put("report", report);
            resultMap.put("competitorComparison", competitorData);

            String resultJson = objectMapper.writeValueAsString(resultMap);

            task.setResultContent(resultJson);
            task.setStatus(Constant.TASK_STATUS_COMPLETED);
            diagnoseTaskMapper.updateById(task);

            taskProgressService.completeProgress(progressId, resultJson);
            log.info("诊断任务完成: taskId={}", taskId);

        } catch (Exception e) {
            log.error("诊断任务执行失败: taskId={}", taskId, e);
            AiDiagnoseTask task = diagnoseTaskMapper.selectById(taskId);
            if (task != null) {
                task.setStatus(Constant.TASK_STATUS_FAILED);
                task.setResultContent("{\"error\":\"" + e.getMessage() + "\"}");
                diagnoseTaskMapper.updateById(task);
            }
            taskProgressService.failProgress(progressId, "诊断失败: " + e.getMessage());
        }
    }

    /**
     * 获取诊断进度
     */
    public Object getTaskProgress(Long taskId) {
        return taskProgressService.getProgress("diagnose:" + taskId);
    }

    /**
     * 获取诊断报告
     */
    public Map<String, Object> getDiagnosisReport(Long taskId) {
        AiDiagnoseTask task = diagnoseTaskMapper.selectById(taskId);
        if (task == null || task.getResultContent() == null) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(task.getResultContent(), Map.class);
        } catch (JsonProcessingException e) {
            log.error("解析诊断报告失败", e);
            return Collections.singletonMap("raw", task.getResultContent());
        }
    }

    /**
     * 生成诊断报告
     */
    private Map<String, Object> generateDiagnosisReport(VisibilityResult visibility, String intentResult) {
        Map<String, Object> report = new HashMap<>();
        report.put("score", visibility.getScore());
        report.put("mentionRate", visibility.getMentionRate());
        report.put("firstRecommendRate", visibility.getFirstRecommendRate());

        // 缺口分析
        List<Map<String, Object>> gaps = new ArrayList<>();
        if (visibility.getMentionRate() < 30) {
            gaps.add(createGap("品牌提及率低", "品牌在AI搜索中被提及的频率较低，需要增加品牌相关内容", "high"));
        }
        if (visibility.getFirstRecommendRate() < 20) {
            gaps.add(createGap("首推占比不足", "品牌在AI搜索结果中首推比例较低，需要优化内容相关性", "high"));
        }
        if (visibility.getScore() < 50) {
            gaps.add(createGap("综合评分偏低", "品牌综合可见度评分偏低，建议全面优化GEO策略", "medium"));
        }
        if (gaps.isEmpty()) {
            gaps.add(createGap("表现良好", "品牌在当前平台的可见度表现良好，建议持续优化", "low"));
        }
        report.put("gaps", gaps);

        // 优化建议
        List<String> suggestions = new ArrayList<>();
        suggestions.add("增加结构化数据标记，提升AI模型内容理解");
        suggestions.add("创建高频问答内容，覆盖用户常见问题");
        suggestions.add("优化品牌关键词密度和信息完整性");
        suggestions.add("建立权威外链和品牌引用");
        report.put("suggestions", suggestions);

        return report;
    }

    private Map<String, Object> createGap(String title, String description, String level) {
        Map<String, Object> gap = new HashMap<>();
        gap.put("title", title);
        gap.put("description", description);
        gap.put("level", level);
        return gap;
    }

    /**
     * 生成竞品对比数据
     */
    private List<Map<String, Object>> generateCompetitorComparison(String brandName) {
        List<Map<String, Object>> competitors = new ArrayList<>();
        String[] competitorsNames = {"竞品A", "竞品B", "竞品C"};
        for (String competitor : competitorsNames) {
            Map<String, Object> comp = new HashMap<>();
            comp.put("name", competitor);
            comp.put("mentionRate", String.format("%.1f", Math.random() * 100) + "%");
            comp.put("firstRecommendRate", String.format("%.1f", Math.random() * 100) + "%");
            comp.put("score", (int) (Math.random() * 100));
            comp.put("advantage", competitor + "在内容覆盖面上更广");
            comp.put("disadvantage", competitor + "在品牌专业度上不足");
            competitors.add(comp);
        }
        return competitors;
    }
}