package com.geosaa.modules.asset;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geosaa.common.Constant;
import com.geosaa.common.PageResult;
import com.geosaa.modules.content.entity.AiArticleContent;
import com.geosaa.modules.content.mapper.AiArticleContentMapper;
import com.geosaa.modules.diagnose.entity.AiDiagnoseTask;
import com.geosaa.modules.diagnose.mapper.AiDiagnoseTaskMapper;
import com.geosaa.modules.distribute.entity.DistributeTask;
import com.geosaa.modules.distribute.mapper.DistributeTaskMapper;
import com.geosaa.modules.knowledge.entity.BrandKnowledge;
import com.geosaa.modules.knowledge.mapper.BrandKnowledgeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 资产存证聚合服务。
 *
 * <p>把分散在 content / knowledge / distribute / diagnose 四个模块的数据，
 * 统一聚合为“品牌资产”视图所需的数据。每个数据源单独 try-catch 降级，
 * 某张表缺失或异常只让该部分为空，不会让整个接口 500。
 *
 * <p>聚合后的列表在内存中做年/月过滤与分页，避免针对不同数据库写方言 SQL，
 * 同时方便把多张表的结果合并排序。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetService {

    private final AiArticleContentMapper contentMapper;
    private final DistributeTaskMapper distributeTaskMapper;
    private final BrandKnowledgeMapper knowledgeMapper;
    private final AiDiagnoseTaskMapper diagnoseTaskMapper;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int MAX_PER_SOURCE = 500;

    private static final Map<Integer, String> CONTENT_STATUS_TEXT = new LinkedHashMap<>();
    private static final Map<Integer, String> TASK_STATUS_TEXT = new LinkedHashMap<>();

    static {
        CONTENT_STATUS_TEXT.put(Constant.TASK_STATUS_PENDING, "待生成");
        CONTENT_STATUS_TEXT.put(Constant.TASK_STATUS_PROCESSING, "生成中");
        CONTENT_STATUS_TEXT.put(Constant.TASK_STATUS_COMPLETED, "已完成");
        CONTENT_STATUS_TEXT.put(Constant.TASK_STATUS_FAILED, "失败");

        TASK_STATUS_TEXT.put(Constant.TASK_STATUS_PENDING, "待处理");
        TASK_STATUS_TEXT.put(Constant.TASK_STATUS_PROCESSING, "进行中");
        TASK_STATUS_TEXT.put(Constant.TASK_STATUS_COMPLETED, "已完成");
        TASK_STATUS_TEXT.put(Constant.TASK_STATUS_FAILED, "失败");
    }

    public Map<String, Object> getOverview() {
        Map<String, Object> result = new LinkedHashMap<>();
        long contentTotal = safeCount(contentMapper.selectCount(null));
        long knowledgeTotal = safeCount(knowledgeMapper.selectCount(null));
        long distributeTotal = safeCount(distributeTaskMapper.selectCount(null));
        long distributeSuccess = safeDistributeSuccess();
        long diagnoseTotal = safeCount(diagnoseTaskMapper.selectCount(null));

        result.put("contentTotal", contentTotal);
        result.put("knowledgeTotal", knowledgeTotal);
        result.put("distributeTotal", distributeTotal);
        result.put("distributeSuccess", distributeSuccess);
        result.put("diagnoseTotal", diagnoseTotal);
        // 资产存证视角：内容与知识是核心品牌资产
        result.put("totalAssets", contentTotal + knowledgeTotal);
        result.put("published", distributeSuccess);
        result.put("screenshots", 0L); // 暂无截图存证独立数据源

        Map<String, Long> byType = new LinkedHashMap<>();
        byType.put("content", contentTotal);
        byType.put("knowledge", knowledgeTotal);
        byType.put("distribute", distributeTotal);
        byType.put("diagnose", diagnoseTotal);
        result.put("byType", byType);
        return result;
    }

    public PageResult<Map<String, Object>> listAssets(String assetType, Integer year, Integer month,
                                                     int pageNum, int pageSize) {
        List<Map<String, Object>> all = new ArrayList<>();
        if (assetType == null || assetType.isEmpty() || "content".equals(assetType)) {
            all.addAll(safeContentAssets());
        }
        if (assetType == null || assetType.isEmpty() || "knowledge".equals(assetType)) {
            all.addAll(safeKnowledgeAssets());
        }
        if (assetType == null || assetType.isEmpty() || "distribute".equals(assetType)) {
            all.addAll(safeDistributeAssets());
        }
        if (assetType == null || assetType.isEmpty() || "diagnose".equals(assetType)) {
            all.addAll(safeDiagnoseAssets());
        }

        // 按日期倒序（空日期排最后）
        all.sort((a, b) -> {
            String da = (String) a.get("date");
            String db = (String) b.get("date");
            if (da == null) da = "";
            if (db == null) db = "";
            return db.compareTo(da);
        });

        // 年/月过滤（内存过滤，规避 DB 方言差异）
        if (year != null || month != null) {
            final Integer y = year;
            final Integer m = month;
            all = all.stream()
                    .filter(it -> matchYearMonth((String) it.get("date"), y, m))
                    .collect(Collectors.toList());
        }

        long total = all.size();
        int from = (pageNum - 1) * pageSize;
        if (from < 0) from = 0;
        int to = Math.min(from + pageSize, all.size());
        List<Map<String, Object>> pageData = from >= all.size()
                ? Collections.emptyList()
                : new ArrayList<>(all.subList(from, to));
        return PageResult.success(pageData, total, pageNum, pageSize);
    }

    private List<Map<String, Object>> safeContentAssets() {
        try {
            LambdaQueryWrapper<AiArticleContent> wrapper = new LambdaQueryWrapper<>();
            wrapper.orderByDesc(AiArticleContent::getCreateTime).last("LIMIT " + MAX_PER_SOURCE);
            List<AiArticleContent> list = contentMapper.selectList(wrapper);
            List<Map<String, Object>> items = new ArrayList<>();
            for (AiArticleContent c : list) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", c.getId());
                m.put("assetType", "content");
                m.put("title", c.getTitle());
                m.put("description", firstNonBlank(c.getSummary(), c.getContent()));
                m.put("date", fmt(c.getCreateTime()));
                m.put("status", c.getStatus());
                m.put("statusText", CONTENT_STATUS_TEXT.getOrDefault(c.getStatus(), "未知"));
                m.put("typeLabel", c.getContentType());
                m.put("brandName", c.getBrandName());
                Map<String, Object> extra = new LinkedHashMap<>();
                extra.put("contentType", c.getContentType());
                extra.put("wordCount", c.getWordCount());
                m.put("extra", extra);
                items.add(m);
            }
            return items;
        } catch (Exception e) {
            log.warn("资产-内容聚合失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> safeKnowledgeAssets() {
        try {
            LambdaQueryWrapper<BrandKnowledge> wrapper = new LambdaQueryWrapper<>();
            wrapper.orderByDesc(BrandKnowledge::getCreateTime).last("LIMIT " + MAX_PER_SOURCE);
            List<BrandKnowledge> list = knowledgeMapper.selectList(wrapper);
            List<Map<String, Object>> items = new ArrayList<>();
            for (BrandKnowledge k : list) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", k.getId());
                m.put("assetType", "knowledge");
                m.put("title", k.getTitle());
                m.put("description", k.getContent());
                m.put("date", fmt(k.getCreateTime()));
                m.put("status", k.getStatus());
                m.put("statusText", k.getStatus() != null && k.getStatus() == 1 ? "已通过" : "待审核");
                m.put("typeLabel", k.getKnowledgeType());
                m.put("brandId", k.getBrandId());
                Map<String, Object> extra = new LinkedHashMap<>();
                extra.put("source", k.getSource());
                m.put("extra", extra);
                items.add(m);
            }
            return items;
        } catch (Exception e) {
            log.warn("资产-知识聚合失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> safeDistributeAssets() {
        try {
            LambdaQueryWrapper<DistributeTask> wrapper = new LambdaQueryWrapper<>();
            wrapper.orderByDesc(DistributeTask::getCreateTime).last("LIMIT " + MAX_PER_SOURCE);
            List<DistributeTask> list = distributeTaskMapper.selectList(wrapper);
            List<Map<String, Object>> items = new ArrayList<>();
            for (DistributeTask d : list) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", d.getId());
                m.put("assetType", "distribute");
                m.put("title", d.getTaskName());
                m.put("description", "分发至 " + firstNonBlank(d.getTargetPlatform(), "未知渠道"));
                m.put("date", fmt(d.getPublishTime() != null ? d.getPublishTime() : d.getCreateTime()));
                m.put("status", d.getStatus());
                m.put("statusText", TASK_STATUS_TEXT.getOrDefault(d.getStatus(), "未知"));
                m.put("typeLabel", "分发任务");
                Map<String, Object> extra = new LinkedHashMap<>();
                extra.put("targetPlatform", d.getTargetPlatform());
                extra.put("targetAccount", d.getTargetAccount());
                extra.put("publishTime", fmt(d.getPublishTime()));
                m.put("extra", extra);
                items.add(m);
            }
            return items;
        } catch (Exception e) {
            log.warn("资产-分发聚合失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> safeDiagnoseAssets() {
        try {
            LambdaQueryWrapper<AiDiagnoseTask> wrapper = new LambdaQueryWrapper<>();
            wrapper.orderByDesc(AiDiagnoseTask::getCreateTime).last("LIMIT " + MAX_PER_SOURCE);
            List<AiDiagnoseTask> list = diagnoseTaskMapper.selectList(wrapper);
            List<Map<String, Object>> items = new ArrayList<>();
            for (AiDiagnoseTask t : list) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", t.getId());
                m.put("assetType", "diagnose");
                m.put("title", t.getTaskName());
                m.put("description", firstNonBlank(t.getBrandName(), "") + " · " + firstNonBlank(t.getTaskType(), ""));
                m.put("date", fmt(t.getCreateTime()));
                m.put("status", t.getStatus());
                m.put("statusText", TASK_STATUS_TEXT.getOrDefault(t.getStatus(), "未知"));
                m.put("typeLabel", "诊断报告");
                m.put("brandName", t.getBrandName());
                Map<String, Object> extra = new LinkedHashMap<>();
                extra.put("taskType", t.getTaskType());
                extra.put("score", parseScore(t.getResultContent()));
                m.put("extra", extra);
                items.add(m);
            }
            return items;
        } catch (Exception e) {
            log.warn("资产-诊断聚合失败: {}", e.getMessage());
            return Collections.emptyList();
        }
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
            log.warn("资产-分发成功数聚合失败: {}", e.getMessage());
            return 0L;
        }
    }

    private String fmt(LocalDateTime time) {
        return time == null ? null : time.format(DATE_FMT);
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    private Integer parseScore(String resultContent) {
        if (resultContent == null) return null;
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<?, ?> root = om.readValue(resultContent, Map.class);
            Object report = root.get("report");
            if (report instanceof Map) {
                Object score = ((Map<?, ?>) report).get("score");
                if (score instanceof Number) {
                    return ((Number) score).intValue();
                }
            }
        } catch (Exception ignored) {
            // 评分解析失败不影响整体
        }
        return null;
    }

    private boolean matchYearMonth(String date, Integer year, Integer month) {
        if (date == null) return false;
        if (year != null && !date.startsWith(String.valueOf(year))) {
            return false;
        }
        if (month != null) {
            // date 形如 yyyy-MM-dd，取 MM 部分比较
            String[] parts = date.split("-");
            if (parts.length < 2) return false;
            int m;
            try {
                m = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return false;
            }
            if (m != month) return false;
        }
        return true;
    }
}
