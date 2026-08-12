package com.geosaa.modules.content.geo;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GEO 内容校验结果。
 *
 * <p>{@code blocked=true} 表示内容存在必须拦截的问题（目前仅关键词堆砌），
 * 此时总分已被折半并带有 redFlag 说明。
 */
@Data
public class GeoValidationResult {

    /** 综合 GEO 健康度评分 0-100（八项正策略加权；关键词堆砌触发时折半） */
    private int totalScore;

    /** 是否触发拦截（关键词堆砌密度超阈值） */
    private boolean blocked;

    /** 问题清单（如「关键词堆砌」），正常为空 */
    private List<String> redFlags = new ArrayList<>();

    /** 优化建议（基于低分项生成） */
    private List<String> suggestions = new ArrayList<>();

    /** 九大战术逐项评分 */
    private Map<String, TacticScore> tactics = new LinkedHashMap<>();
}
