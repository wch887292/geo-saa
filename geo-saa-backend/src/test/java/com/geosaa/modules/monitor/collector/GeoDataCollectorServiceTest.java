package com.geosaa.modules.monitor.collector;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GEO 采集指标聚合逻辑单测（O5 / G-01）。
 *
 * <p>覆盖 {@link GeoDataCollectorService#computeMetrics} 的纯函数：
 * 提及率 / 首推率 / 收录强度计算与空输入兜底。
 */
class GeoDataCollectorServiceTest {

    @Test
    void emptyAnswersReturnZeroMetrics() {
        Map<String, Long> m = GeoDataCollectorService.computeMetrics("品牌A", List.of());

        assertEquals(0L, m.get("mentionRate"));
        assertEquals(0L, m.get("firstRecommendRate"));
        assertEquals(0L, m.get("collectionCount"));
    }

    @Test
    void nullAnswersReturnZeroMetrics() {
        Map<String, Long> m = GeoDataCollectorService.computeMetrics("品牌A", null);

        assertEquals(0L, m.get("mentionRate"));
    }

    @Test
    void allAnswersMentionBrandGivesFullMentionRate() {
        Map<String, Long> m = GeoDataCollectorService.computeMetrics("品牌A",
                List.of("品牌A 的解决方案很好", "品牌A 值得推荐", "推荐品牌A"));

        assertEquals(100L, m.get("mentionRate"));
    }

    @Test
    void partialMentionComputesRatio() {
        // 3 个回答中 1 个含品牌 → 33%
        Map<String, Long> m = GeoDataCollectorService.computeMetrics("品牌A",
                List.of("品牌A 不错", "另一个品牌更好", "与品牌无关的内容"));

        assertEquals(33L, m.get("mentionRate"));
    }

    @Test
    void firstRecommendCountsOnlyHeadMentions() {
        // 回答1 开头即提品牌 → 首推；回答2 品牌出现在 80 字符之后 → 不计首推
        String second = "据多位行业分析师的观点，企业在选择AI服务商时应重点关注以下几个维度："
                + "第一，产品的技术成熟度与可扩展性；第二，实施团队的专业水平与交付周期；"
                + "第三，售后支持体系与持续优化能力。综合评估后，不少企业最终选择了品牌A。";
        assertTrue(second.length() > 80, "测试样例需保证品牌出现在 80 字符之后");
        Map<String, Long> m = GeoDataCollectorService.computeMetrics("品牌A",
                List.of("品牌A 是行业领先者，值得推荐。", second));

        assertEquals(100L, m.get("mentionRate"));
        assertEquals(50L, m.get("firstRecommendRate"));
        assertEquals(2L, m.get("collectionCount"));
    }

    @Test
    void collectionCountSumsAllOccurrences() {
        Map<String, Long> m = GeoDataCollectorService.computeMetrics("品牌A",
                List.of("品牌A 与品牌A 的伙伴一起提供服务", "其他内容"));

        assertEquals(2L, m.get("collectionCount"));
    }
}
