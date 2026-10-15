package com.geosaa.modules.monitor.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * GEO 实体信息验证器。
 *
 * <p>从大模型回答中检测品牌实体信息的正确性，与基准信息对比，
 * 计算实体准确率和冲突数量。支持的实体字段：
 * <ul>
 *   <li>name - 品牌名称</li>
 *   <li>founded_year - 成立年份</li>
 *   <li>address - 地址</li>
 *   <li>phone - 联系电话</li>
 *   <li>products - 产品/服务（逗号分隔）</li>
 *   <li>employees - 员工规模</li>
 *   <li>revenue - 营收规模</li>
 *   <li>certifications - 资质认证（逗号分隔）</li>
 * </ul>
 *
 * <p>基准信息通过 JSON 格式配置，例如：
 * {"name":"飞虹智","founded_year":"2015","address":"北京市朝阳区","products":"CRM,ERP,OA"}
 */
@Slf4j
public class GeoEntityValidator {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** 可检测的实体字段列表 */
    public static final List<String> SUPPORTED_FIELDS = List.of(
            "name", "founded_year", "address", "phone",
            "products", "employees", "revenue", "certifications");

    /**
     * 验证单个回答中的实体信息正确性。
     *
     * @param answer 大模型回答文本
     * @param entityJson 品牌实体基准信息（JSON 字符串）
     * @return 验证结果，包含 matchedFields（匹配的字段）、conflictFields（冲突的字段）、accuracy（准确率0-100）
     */
    public static EntityValidationResult validate(String answer, String entityJson) {
        EntityValidationResult result = new EntityValidationResult();
        if (answer == null || answer.isBlank() || entityJson == null || entityJson.isBlank()) {
            result.accuracy = 0;
            return result;
        }

        try {
            JsonNode entity = objectMapper.readTree(entityJson);
            String answerLower = answer.toLowerCase();
            int totalFields = 0;
            int matchedFields = 0;

            for (String field : SUPPORTED_FIELDS) {
                if (!entity.has(field) || entity.get(field).isNull()) continue;
                String expected = entity.get(field).asText().trim();
                if (expected.isEmpty()) continue;
                totalFields++;

                boolean matched = checkFieldMatch(answerLower, expected.toLowerCase(), field);
                if (matched) {
                    matchedFields++;
                    result.matchedFields.add(field);
                } else {
                    // 检测是否存在冲突信息（回答中包含该字段但值不同）
                    if (hasConflictIndicator(answer, field, expected)) {
                        result.conflictFields.add(field);
                    }
                }
            }

            result.totalFields = totalFields;
            result.accuracy = totalFields > 0 ? (int) Math.round(matchedFields * 100.0 / totalFields) : 0;

        } catch (Exception e) {
            log.warn("实体信息验证失败: {}", e.getMessage());
            result.accuracy = 0;
        }
        return result;
    }

    /**
     * 批量验证多个回答，返回聚合结果。
     */
    public static AggregatedEntityResult validateAll(List<String> answers, String entityJson) {
        AggregatedEntityResult agg = new AggregatedEntityResult();
        if (answers == null || answers.isEmpty()) {
            agg.avgAccuracy = 0;
            agg.totalConflicts = 0;
            return agg;
        }

        int totalAccuracy = 0;
        int validAnswers = 0;
        Map<String, Integer> fieldMatchCount = new HashMap<>();
        Map<String, Integer> fieldConflictCount = new HashMap<>();

        for (String answer : answers) {
            EntityValidationResult r = validate(answer, entityJson);
            if (r.totalFields > 0) {
                totalAccuracy += r.accuracy;
                validAnswers++;
                for (String f : r.matchedFields) {
                    fieldMatchCount.merge(f, 1, Integer::sum);
                }
                for (String f : r.conflictFields) {
                    fieldConflictCount.merge(f, 1, Integer::sum);
                    agg.totalConflicts++;
                }
            }
        }

        agg.avgAccuracy = validAnswers > 0 ? (int) Math.round(totalAccuracy * 1.0 / validAnswers) : 0;
        agg.validAnswers = validAnswers;
        agg.fieldMatchCount = fieldMatchCount;
        agg.fieldConflictCount = fieldConflictCount;
        return agg;
    }

    /**
     * 检查字段值是否在回答中出现（匹配）。
     */
    private static boolean checkFieldMatch(String answerLower, String expectedLower, String field) {
        // 对于 products/certifications 等多值字段，只要有一个值匹配就算匹配
        if ("products".equals(field) || "certifications".equals(field)) {
            String[] values = expectedLower.split("[,，、;；]");
            for (String v : values) {
                String trimmed = v.trim();
                if (!trimmed.isEmpty() && answerLower.contains(trimmed)) {
                    return true;
                }
            }
            return false;
        }
        // 对于 phone 字段，去除空格和横线后匹配
        if ("phone".equals(field)) {
            String normalized = expectedLower.replaceAll("[\\s\\-()]", "");
            String answerNormalized = answerLower.replaceAll("[\\s\\-()]", "");
            return answerNormalized.contains(normalized);
        }
        // 其他字段直接包含匹配
        return answerLower.contains(expectedLower);
    }

    /**
     * 检测回答中是否存在与基准值冲突的信息。
     * 通过检测回答中是否包含该字段的常见表述模式但值不同来判断。
     */
    private static boolean hasConflictIndicator(String answer, String field, String expected) {
        String answerLower = answer.toLowerCase();
        switch (field) {
            case "founded_year":
                // 检测回答中是否有其他年份（4位数字，且不是预期年份）
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b(19|20)\\d{2}\\b").matcher(answer);
                while (m.find()) {
                    if (!m.group().equals(expected)) return true;
                }
                return false;
            case "name":
                // 品牌名称冲突较难自动检测，返回 false
                return false;
            case "address":
                // 检测是否有其他城市名（简单检测）
                return false;
            default:
                return false;
        }
    }

    /** 单回答验证结果 */
    public static class EntityValidationResult {
        public int totalFields = 0;
        public int accuracy = 0;
        public List<String> matchedFields = new ArrayList<>();
        public List<String> conflictFields = new ArrayList<>();
    }

    /** 批量验证聚合结果 */
    public static class AggregatedEntityResult {
        public int avgAccuracy = 0;
        public int validAnswers = 0;
        public int totalConflicts = 0;
        public Map<String, Integer> fieldMatchCount = new HashMap<>();
        public Map<String, Integer> fieldConflictCount = new HashMap<>();
    }
}
