package com.geosaa.ai;

import lombok.Data;

import java.util.List;

@Data
public class VisibilityResult {
    private String brandName;
    private String platform;
    private int mentionCount;        // 提及次数
    private double mentionRate;      // 提及率 0-100
    private double firstRecommendRate; // 首推占比 0-100
    private List<String> relatedQuestions; // 相关用户提问
    private List<String> competitorMentions; // 竞品提及
    private int score;               // 综合评分 0-100
}