package com.geosaa.modules.diagnose.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * GEO+AAO 诊断评分报告 DTO
 * 基于提示词3（GEO/AAO诊断打分评估器）的标准化输出结构
 */
@Data
public class GeoAaoDiagnosisReport {

    /** 总分 0-100 */
    private Integer totalScore;

    /** 评级：S(90+)/A(75~89)/B(60~74)/C(40~59)/D(<40) */
    private String grade;

    /** 各维度得分 */
    private Dimensions dimensions;

    /** 风险分级清单 */
    private Risks risks;

    /** 优化建议清单 */
    private List<Recommendation> recommendations;

    /** GEO+AAO 机会点 */
    private Opportunities opportunities;

    /** 免责声明 */
    private String disclaimer;

    @Data
    public static class Dimensions {
        private DimensionDetail seo;
        private DimensionDetail geo;
        private DimensionDetail aao;
    }

    @Data
    public static class DimensionDetail {
        private Integer score;
        private Integer maxScore;
        private List<String> issues;
    }

    @Data
    public static class Risks {
        private List<String> p0;
        private List<String> p1;
        private List<String> p2;
    }

    @Data
    public static class Recommendation {
        private String priority;
        private String action;
        private String type;
        private String expectedGain;
    }

    @Data
    public static class Opportunities {
        private List<String> targetQueries;
        private List<String> recommendedFaqTopics;
    }
}
