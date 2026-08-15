package com.geosaa.modules.content.geo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GEO 九战术校验器单测（O5 / G-02）。
 *
 * <p>覆盖：高质量内容高分、关键词堆砌拦截（论文唯一负向战术 −8%~−10%）、
 * 空内容低分不拦截、低密度关键词不误伤。
 */
class GeoContentValidatorTest {

    private final GeoContentValidator validator = new GeoContentValidator();

    /** 高质量内容：统计 + 引用 + 引语 + 术语 + 权威词齐备 */
    private static final String GOOD_CONTENT =
            "据《中国AI产业白皮书》显示，2025年中国AI市场规模达 4500 亿元，同比增长 35%（来源：工信部）。" +
            "飞虹智CEO吴赐虹表示：「AI搜索正在改变品牌营销方式。」" +
            "公司采用大模型与SaaS平台技术，为制造企业提供数字化增长方案。" +
            "方案已通过第三方权威认证，数据来源于官方研究报告，符合监管要求。" +
            "参考 https://klai.top/opensource.html 查看更多开源项目。" +
            "该方案成本降低 34%，部署周期缩短至 3 天，覆盖 200+ 家企业用户。" +
            "专家分析认为，未来三年AI Agent将进入生产环境，企业应提前布局。" +
            "差异化优势在于算法能力与供应链协同，转化率提升 18%，ROI 达 4.2 倍。";

    @Test
    void goodContentScoresHigh() {
        GeoValidationResult result = validator.validate(GOOD_CONTENT, "AI搜索,品牌营销");

        assertFalse(result.isBlocked(), "高质量内容不应被拦截");
        assertTrue(result.getTotalScore() >= 60, "高质量内容总分应 >= 60，实际 " + result.getTotalScore());
        assertTrue(result.getTactics().get(GeoContentValidator.T_STATISTICS).getScore() >= 70);
        assertTrue(result.getTactics().get(GeoContentValidator.T_QUOTATIONS).getScore() >= 70);
        assertTrue(result.getTactics().get(GeoContentValidator.T_CITE_SOURCES).getScore() >= 70);
    }

    @Test
    void geoV2DimensionsArePresent() {
        GeoValidationResult result = validator.validate(GOOD_CONTENT, "AI搜索,品牌营销");

        // GEO v2 新维度必须出现在报告中
        for (String code : new String[]{
                GeoContentValidator.T_ANSWER_FIRST,
                GeoContentValidator.T_FACT_DENSITY,
                GeoContentValidator.T_STRUCTURED_DATA,
                GeoContentValidator.T_EEAT,
                GeoContentValidator.T_KEY_QUOTE,
                GeoContentValidator.T_FRESHNESS,
                GeoContentValidator.T_OWN_CITATIONS}) {
            assertNotNull(result.getTactics().get(code), "缺少维度 " + code);
        }
        // GOOD 内容：答案前置(含"达"+数字)、事实密度(数字多)、E-E-A-T(公司/官方/认证)、一手来源(URL+官方) 应高分
        assertTrue(result.getTactics().get(GeoContentValidator.T_ANSWER_FIRST).getScore() >= 70);
        assertTrue(result.getTactics().get(GeoContentValidator.T_FACT_DENSITY).getScore() >= 70);
        assertTrue(result.getTactics().get(GeoContentValidator.T_EEAT).getScore() >= 70);
        assertTrue(result.getTactics().get(GeoContentValidator.T_FRESHNESS).getScore() >= 70);
    }

    @Test
    void oldContentScoresLowOnFreshness() {
        // 一年前的旧内容 → 新鲜度低分
        GeoValidationResult result = validator.validate(GOOD_CONTENT, "AI搜索",
                java.time.LocalDate.now().minusYears(1));

        assertTrue(result.getTactics().get(GeoContentValidator.T_FRESHNESS).getScore() < 70,
                "一年前内容新鲜度应低分，实际 "
                        + result.getTactics().get(GeoContentValidator.T_FRESHNESS).getScore());
    }

    @Test
    void keywordStuffingIsBlocked() {
        // 关键词「AI搜索」反复出现 → 密度超阈值
        String stuffing = "AI搜索很重要，AI搜索能帮助企业，企业必须做AI搜索，AI搜索是趋势，"
                + "我们专注于AI搜索，AI搜索营销，AI搜索优化，AI搜索案例，AI搜索报告，"
                + "AI搜索增长，AI搜索策略，AI搜索方案，AI搜索效果，AI搜索团队";
        GeoValidationResult result = validator.validate(stuffing, "AI搜索");

        assertTrue(result.isBlocked(), "关键词堆砌应触发拦截");
        assertFalse(result.getRedFlags().isEmpty());
        assertTrue(result.getRedFlags().get(0).contains("堆砌"));
        // 拦截时总分折半
        assertTrue(result.getTotalScore() <= 50);
    }

    @Test
    void emptyContentScoresZeroNotBlocked() {
        GeoValidationResult result = validator.validate("   ", "AI");

        assertEquals(0, result.getTotalScore());
        assertFalse(result.isBlocked());
    }

    @Test
    void lowKeywordDensityNotBlocked() {
        // 长文 + 关键词仅出现 1 次 → 密度远低于阈值，不应误拦截
        String normal = "本文介绍AI搜索对品牌营销的影响。据《2025年AI营销研究报告》显示，"
                + "超过60%的企业已开始布局生成式引擎优化（GEO），其中制造业渗透率增速最快，同比增长45%。"
                + "专家表示，AI搜索正在重塑用户的消费决策路径，品牌需要从关键词思维转向内容质量思维。"
                + "参考 https://example.com 了解更多详情。该报告还指出，结合专家引语与权威数据的内容，"
                + "在AI答案中的引用率显著更高，且中小品牌获得的增益尤为明显。"
                + "因此，内容团队应将资源投向可验证的量化数据与可信来源，而非重复堆叠目标关键词。";
        GeoValidationResult result = validator.validate(normal, "品牌营销");

        assertFalse(result.isBlocked(), "正常密度不应误拦截");
        assertTrue(result.getTactics().get(GeoContentValidator.T_KEYWORD_STUFFING).getScore() >= 50,
                "正常密度堆砌分应 >= 50，实际 "
                        + result.getTactics().get(GeoContentValidator.T_KEYWORD_STUFFING).getScore());
    }

    @Test
    void noKeywordsSkipsStuffingCheck() {
        GeoValidationResult result = validator.validate("一些普通内容，没有任何特殊结构。", null);

        assertFalse(result.isBlocked());
        assertEquals(100, result.getTactics().get(GeoContentValidator.T_KEYWORD_STUFFING).getScore());
    }

    @Test
    void singleKeywordMentionInShortTextNotBlocked() {
        // 回归：短文中关键词仅出现 1 次（密度可能 >3%），属于正常写作，不应判定堆砌
        String shortText = "本文围绕AI搜索展开讨论。据研究报告显示，市场规模增长 45%，"
                + "专家表示AI将重塑搜索生态。参考 https://example.com 了解详情。";
        GeoValidationResult result = validator.validate(shortText, "AI搜索");

        assertFalse(result.isBlocked(), "单次提及不应判定堆砌");
    }

    @Test
    void nullContentHandled() {
        GeoValidationResult result = validator.validate(null, "AI");

        assertEquals(0, result.getTotalScore());
        assertFalse(result.isBlocked());
    }
}
