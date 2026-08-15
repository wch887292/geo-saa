package com.geosaa.modules.geo.aao;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AAO 评估报告：Agent Experience Score（AX Score，0-100）。
 *
 * <p>六维度模型（对标 2026 AEO/AAO 框架）：
 * Crawlability 25% / Structured Data 25% / Content Quality 15% /
 * Agent Interaction 20% / Discoverability 10% / Security &amp; Trust 5%。
 */
@Data
public class AaoReport {

    /** AX 总分 0-100 */
    private int axScore;

    /** 分级：Excellent(90+) / Good(70-89) / NeedsWork(50-69) / Poor(0-49) */
    private String grade;

    /** 六维度评分 */
    private Map<String, DimensionScore> dimensions = new LinkedHashMap<>();

    /** 优化建议 */
    private List<String> suggestions = new ArrayList<>();

    @Data
    public static class DimensionScore {
        private String code;
        private String name;
        private int weight;
        private int score;
        private String detail;

        public DimensionScore(String code, String name, int weight, int score, String detail) {
            this.code = code;
            this.name = name;
            this.weight = weight;
            this.score = score;
            this.detail = detail;
        }
    }
}
