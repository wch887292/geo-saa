package com.geosaa.modules.diagnose.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geosaa.modules.ai.dto.AiChatRequest;
import com.geosaa.modules.ai.dto.AiChatResponse;
import com.geosaa.modules.ai.service.AiChatService;
import com.geosaa.modules.ai.service.AiPromptTemplateService;
import com.geosaa.modules.diagnose.dto.GeoAaoDiagnosisReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * GEO+AAO 诊断评分引擎
 *
 * <p>基于提示词3（GEO/AAO诊断打分评估器），实现：
 * <ul>
 *   <li>规则化基础评分（SEO 30分 + GEO 30分 + AAO 40分 = 100分）</li>
 *   <li>AI 深度诊断（调用大模型生成专业诊断报告）</li>
 *   <li>五阶段方法论分析（基线诊断→技术底座→语义内容→信源池→PDCA）</li>
 *   <li>风险分级（P0自动修复/P1人工校验/P2长期建设）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeoAaoDiagnosisService {

    private final AiChatService aiChatService;
    private final AiPromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;

    /**
     * 执行 GEO+AAO 综合诊断
     */
    public GeoAaoDiagnosisReport diagnose(String siteUrl, String brandName, Map<String, Object> auditData) {
        log.info("开始GEO+AAO诊断: site={}, brand={}", siteUrl, brandName);

        // 1. 规则化基础评分
        GeoAaoDiagnosisReport report = ruleBasedScoring(auditData);

        // 2. AI 深度诊断（补充问题分析和建议）
        try {
            GeoAaoDiagnosisReport aiReport = aiDeepDiagnosis(siteUrl, brandName, auditData);
            if (aiReport != null) {
                // 合并 AI 诊断结果（AI 给出的建议和机会点更专业）
                if (aiReport.getRecommendations() != null && !aiReport.getRecommendations().isEmpty()) {
                    report.setRecommendations(aiReport.getRecommendations());
                }
                if (aiReport.getOpportunities() != null) {
                    report.setOpportunities(aiReport.getOpportunities());
                }
                if (aiReport.getRisks() != null) {
                    if (aiReport.getRisks().getP1() != null) report.getRisks().setP1(aiReport.getRisks().getP1());
                    if (aiReport.getRisks().getP2() != null) report.getRisks().setP2(aiReport.getRisks().getP2());
                }
            }
        } catch (Exception e) {
            log.warn("AI深度诊断失败，使用规则评分结果: {}", e.getMessage());
        }

        // 3. 设置评级和免责声明
        report.setGrade(calculateGrade(report.getTotalScore()));
        report.setDisclaimer("本评估仅预测提升概率，不能保证大模型一定会引用或优先展示答案。GEO/AAO优化目标是提升采信概率与答案捕获概率。");

        return report;
    }

    /**
     * 规则化基础评分
     * SEO技术底座(0-30) + GEO实体可信度(0-30) + AAO答案捕获能力(0-40)
     */
    private GeoAaoDiagnosisReport ruleBasedScoring(Map<String, Object> auditData) {
        GeoAaoDiagnosisReport report = new GeoAaoDiagnosisReport();
        GeoAaoDiagnosisReport.Dimensions dims = new GeoAaoDiagnosisReport.Dimensions();
        GeoAaoDiagnosisReport.Risks risks = new GeoAaoDiagnosisReport.Risks();
        List<GeoAaoDiagnosisReport.Recommendation> recommendations = new ArrayList<>();

        // ===== SEO技术底座（0-30分）=====
        int seoScore = 30;
        List<String> seoIssues = new ArrayList<>();
        List<String> p0Risks = new ArrayList<>();

        if (auditData != null) {
            // 抓取/收录
            if (!getBool(auditData, "seo_crawlable", true)) {
                seoScore -= 5; seoIssues.add("网站存在爬虫抓取障碍，robots.txt或meta robots配置不当");
                p0Risks.add("修复robots.txt和meta robots配置，确保关键页面可被抓取");
            }
            // 死链
            int deadLinks = getInt(auditData, "seo_dead_links", 0);
            if (deadLinks > 10) { seoScore -= 4; seoIssues.add("存在" + deadLinks + "个死链，影响收录和用户体验"); p0Risks.add("批量修复死链，设置301重定向"); }
            else if (deadLinks > 0) { seoScore -= 2; seoIssues.add("存在少量死链(" + deadLinks + "个)"); }
            // 页面性能
            int performanceScore = getInt(auditData, "seo_performance_score", 80);
            if (performanceScore < 50) { seoScore -= 5; seoIssues.add("页面性能评分过低(" + performanceScore + "分)，影响LLM检索和用户体验"); p0Risks.add("优化页面加载速度，压缩图片，启用CDN"); }
            else if (performanceScore < 70) { seoScore -= 3; seoIssues.add("页面性能待优化(" + performanceScore + "分)"); }
            // 移动端适配
            if (!getBool(auditData, "seo_mobile_friendly", true)) {
                seoScore -= 3; seoIssues.add("移动端适配不完善，影响移动搜索和LLM移动端引用");
            }
            // 标签规范
            if (!getBool(auditData, "seo_heading_standard", true)) {
                seoScore -= 3; seoIssues.add("H1/H2标签层级不规范，存在多个H1或层级跳跃");
                p0Risks.add("规范标题层级：单页唯一H1，H2/H3逐级拆分");
            }
            // sitemap
            if (!getBool(auditData, "seo_sitemap_exists", true)) {
                seoScore -= 3; seoIssues.add("缺少sitemap.xml，影响搜索引擎和LLM发现页面");
                p0Risks.add("生成并提交sitemap.xml");
            }
            // Meta标签
            if (!getBool(auditData, "seo_meta_standard", true)) {
                seoScore -= 2; seoIssues.add("Meta标题描述不规范，存在关键词堆砌或缺失");
            }
        }

        GeoAaoDiagnosisReport.DimensionDetail seoDim = new GeoAaoDiagnosisReport.DimensionDetail();
        seoDim.setScore(Math.max(0, seoScore));
        seoDim.setMaxScore(30);
        seoDim.setIssues(seoIssues.isEmpty() ? List.of("SEO技术底座良好，未发现重大问题") : seoIssues);
        dims.setSeo(seoDim);

        // ===== GEO实体可信度（0-30分）=====
        int geoScore = 30;
        List<String> geoIssues = new ArrayList<>();

        if (auditData != null) {
            // 实体信息一致性
            if (!getBool(auditData, "geo_entity_consistent", true)) {
                geoScore -= 8; geoIssues.add("品牌实体信息全网不一致，存在名称/地址/联系方式冲突，大模型可能输出错误描述");
                p0Risks.add("全网统一品牌实体信息（名称、地址、联系方式、产品参数），消除信息冲突");
            }
            // JSON-LD结构化标记
            if (!getBool(auditData, "geo_jsonld_exists", false)) {
                geoScore -= 6; geoIssues.add("缺少JSON-LD结构化标记（Organization/Product/FAQPage/Article），LLM解析成本高");
                p0Risks.add("添加JSON-LD结构化标记：Organization企业、Product产品、FAQPage问答、Article资讯");
            }
            // llms.txt
            if (!getBool(auditData, "geo_llms_txt_exists", false)) {
                geoScore -= 4; geoIssues.add("缺少llms.txt文件，未明确划定大模型可引用页面范围");
                p0Risks.add("新增llms.txt，专门给大模型划定可引用页面范围");
            }
            // EEAT证据
            int eeatEvidenceCount = getInt(auditData, "geo_eeat_evidence_count", 0);
            if (eeatEvidenceCount < 3) { geoScore -= 4; geoIssues.add("EEAT证据不足（案例/数据/证书/实测结果少于3个），大模型交叉验证采信率低"); }
            else if (eeatEvidenceCount < 8) { geoScore -= 2; geoIssues.add("EEAT证据偏少，建议补充更多案例和数据支撑"); }
            // 全网信源冲突
            int sourceConflicts = getInt(auditData, "geo_source_conflicts", 0);
            if (sourceConflicts > 0) { geoScore -= 4; geoIssues.add("检测到" + sourceConflicts + "处全网信源信息冲突，损害实体可信度"); }
        }

        GeoAaoDiagnosisReport.DimensionDetail geoDim = new GeoAaoDiagnosisReport.DimensionDetail();
        geoDim.setScore(Math.max(0, geoScore));
        geoDim.setMaxScore(30);
        geoDim.setIssues(geoIssues.isEmpty() ? List.of("GEO实体可信度良好，实体信息一致，结构化标记完善") : geoIssues);
        dims.setGeo(geoDim);

        // ===== AAO答案捕获能力（0-40分）=====
        int aaoScore = 40;
        List<String> aaoIssues = new ArrayList<>();
        List<String> p1Risks = new ArrayList<>();

        if (auditData != null) {
            // TL;DR摘要
            if (!getBool(auditData, "aao_tldr_exists", false)) {
                aaoScore -= 8; aaoIssues.add("页面缺少TL;DR一句话摘要，LLM难以直接摘取核心结论作为AI回答");
                p1Risks.add("在页面最顶部增加TL;DR简短摘要，一句话给出核心结论");
            }
            // FAQ模块
            if (!getBool(auditData, "aao_faq_exists", false)) {
                aaoScore -= 10; aaoIssues.add("缺少FAQ模块，无法捕获AI问答问句，AAO答案素材缺失");
                p1Risks.add("增加FAQ板块，整理用户口语化问题，一问一答，配套FAQPage结构化数据");
            }
            // 问句匹配度
            int queryMatchScore = getInt(auditData, "aao_query_match_score", 50);
            if (queryMatchScore < 40) { aaoScore -= 6; aaoIssues.add("内容与用户AI自然问句匹配度低(" + queryMatchScore + "分)，关键词堆砌而非问句对齐"); }
            else if (queryMatchScore < 60) { aaoScore -= 3; aaoIssues.add("内容问句匹配度中等，建议增加更多用户口语化问题"); }
            // 内容可摘录性
            if (!getBool(auditData, "aao_extractable", true)) {
                aaoScore -= 5; aaoIssues.add("内容大段纯文本过多，缺少表格/列表/参数对比，LLM片段摘录概率低");
                p1Risks.add("将长文本拆分成短句、列表、对比表格，减少大段连续文字");
            }
            // 目标问句AI答案命中
            int answerHitRate = getInt(auditData, "aao_answer_hit_rate", 20);
            if (answerHitRate < 20) { aaoScore -= 5; aaoIssues.add("目标问句下AI答案命中率低(" + answerHitRate + "%)，我方实体未被优先输出"); }
            else if (answerHitRate < 50) { aaoScore -= 2; aaoIssues.add("AI答案命中率中等(" + answerHitRate + "%)，有提升空间"); }
        }

        GeoAaoDiagnosisReport.DimensionDetail aaoDim = new GeoAaoDiagnosisReport.DimensionDetail();
        aaoDim.setScore(Math.max(0, aaoScore));
        aaoDim.setMaxScore(40);
        aaoDim.setIssues(aaoIssues.isEmpty() ? List.of("AAO答案捕获能力良好，FAQ完善，问句匹配度高") : aaoIssues);
        dims.setAao(aaoDim);

        // ===== 汇总 =====
        int totalScore = dims.getSeo().getScore() + dims.getGeo().getScore() + dims.getAao().getScore();
        report.setTotalScore(totalScore);
        report.setDimensions(dims);

        // 风险分级
        risks.setP0(p0Risks.isEmpty() ? List.of("未发现P0紧急问题") : p0Risks);
        risks.setP1(p1Risks.isEmpty() ? List.of("内容优化建议见下方推荐清单") : p1Risks);
        risks.setP2(List.of("持续建设三级权威信源（行业媒体报道、客户案例、白皮书），提升EEAT权威度", "季度全量实体校准，确保全网品牌事实一致性"));
        report.setRisks(risks);

        // 基础建议
        recommendations.add(buildRecommendation("P0", "规范标题层级：单页唯一H1，H2/H3逐级拆分，把用户高频AI问句直接做成H2标题", "系统自动执行", "提升LLM解析效率和AAO问句捕获率"));
        recommendations.add(buildRecommendation("P0", "添加JSON-LD结构化标记（Organization/Product/FAQPage/Article）", "系统自动执行", "降低LLM解析成本，提升实体信息采信率"));
        recommendations.add(buildRecommendation("P1", "页面顶部增加TL;DR一句话摘要，给出核心结论", "AI辅助人工校验", "便于LLM直接摘取作为AI回答，提升AAO答案命中率"));
        recommendations.add(buildRecommendation("P1", "增加FAQ模块，整理用户口语化问题，一问一答", "AI辅助人工校验", "专门捕获AI问答问句，为AAO提供答案素材"));
        recommendations.add(buildRecommendation("P2", "全网统一品牌实体信息，消除名称/地址/联系方式冲突", "人工操作", "防止大模型输出错误描述，提升GEO实体可信度"));
        report.setRecommendations(recommendations);

        // 机会点
        GeoAaoDiagnosisReport.Opportunities opp = new GeoAaoDiagnosisReport.Opportunities();
        opp.setTargetQueries(List.of(
            brandName(auditData) + "怎么样？",
            brandName(auditData) + "价格是多少？",
            brandName(auditData) + "和竞品比哪个好？",
            brandName(auditData) + "有哪些功能？",
            brandName(auditData) + "适合什么场景？"
        ));
        opp.setRecommendedFaqTopics(List.of("产品选型指南", "价格与套餐对比", "常见问题解答", "客户成功案例", "技术参数说明"));
        report.setOpportunities(opp);

        return report;
    }

    /**
     * AI 深度诊断（调用大模型，使用提示词3）
     */
    private GeoAaoDiagnosisReport aiDeepDiagnosis(String siteUrl, String brandName, Map<String, Object> auditData) {
        // 渲染诊断评估器模板
        Map<String, String> variables = new HashMap<>();
        variables.put("site_info", "站点URL: " + siteUrl + ", 品牌: " + brandName);
        variables.put("seo_audit_data", auditData != null ? auditData.toString() : "暂无详细SEO审计数据");
        variables.put("geo_entity_data", "品牌实体: " + brandName);
        variables.put("aao_query_sample", "抽样问句: " + brandName + "怎么样、价格、对比");

        String renderedPrompt = promptTemplateService.renderTemplate("geo_aao_diagnosis_scorer", variables);
        if (renderedPrompt == null) {
            log.warn("未找到geo_aao_diagnosis_scorer模板，跳过AI深度诊断");
            return null;
        }

        AiChatRequest request = new AiChatRequest();
        request.setUserInput(renderedPrompt);
        request.setBusinessModule("diagnose");
        request.setBusinessType("geo_aao_scoring");
        request.setMaxTokens(3000);
        request.setTemperature(0.2);

        AiChatResponse response = aiChatService.chat(request);
        if (!response.getSuccess() || response.getContent() == null) {
            log.warn("AI深度诊断调用失败: {}", response.getErrorMessage());
            return null;
        }

        // 尝试解析 JSON 响应
        try {
            String content = response.getContent();
            // 提取 JSON 部分（AI 可能在 JSON 前后加了说明文字）
            int jsonStart = content.indexOf('{');
            int jsonEnd = content.lastIndexOf('}');
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                String json = content.substring(jsonStart, jsonEnd + 1);
                return objectMapper.readValue(json, GeoAaoDiagnosisReport.class);
            }
        } catch (Exception e) {
            log.warn("AI诊断报告JSON解析失败，使用规则评分: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 计算评级
     */
    private String calculateGrade(Integer score) {
        if (score == null) return "N/A";
        if (score >= 90) return "S";
        if (score >= 75) return "A";
        if (score >= 60) return "B";
        if (score >= 40) return "C";
        return "D";
    }

    private GeoAaoDiagnosisReport.Recommendation buildRecommendation(String priority, String action, String type, String gain) {
        GeoAaoDiagnosisReport.Recommendation r = new GeoAaoDiagnosisReport.Recommendation();
        r.setPriority(priority);
        r.setAction(action);
        r.setType(type);
        r.setExpectedGain(gain);
        return r;
    }

    private boolean getBool(Map<String, Object> data, String key, boolean defaultValue) {
        if (data == null || !data.containsKey(key)) return defaultValue;
        Object val = data.get(key);
        if (val instanceof Boolean) return (Boolean) val;
        return Boolean.parseBoolean(val.toString());
    }

    private int getInt(Map<String, Object> data, String key, int defaultValue) {
        if (data == null || !data.containsKey(key)) return defaultValue;
        Object val = data.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return defaultValue; }
    }

    private String brandName(Map<String, Object> data) {
        if (data != null && data.containsKey("brand_name")) return data.get("brand_name").toString();
        return "该品牌";
    }
}
