package com.geosaa.modules.geo.aao;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AAO 引擎：Agent Experience 评估 + llms.txt / agent.json 生成。
 *
 * <p>对标 2026 Agent-Ready 标准：
 * <ul>
 *   <li>llms.txt（llmstxt.org）：LLM 友好站点索引</li>
 *   <li>/.well-known/api-catalog（RFC 9727）、MCP Server Card（SEP-1649）、A2A AgentCard</li>
 *   <li>Content Signals（robots.txt 中 search/ai-train/ai-input 声明）</li>
 *   <li>AX Score 六维度：Crawlability 25 / Structured Data 25 / Content Quality 15 /
 *       Agent Interaction 20 / Discoverability 10 / Security &amp; Trust 5</li>
 * </ul>
 * 纯启发式、无外部依赖，可单测、可解释。
 */
@Component
public class AaoEngine {

    public static final String D_CRAWLABILITY = "crawlability";
    public static final String D_STRUCTURED = "structured_data";
    public static final String D_CONTENT = "content_quality";
    public static final String D_INTERACTION = "agent_interaction";
    public static final String D_DISCOVERABILITY = "discoverability";
    public static final String D_TRUST = "security_trust";

    // ---------- AX Score 评估 ----------

    public AaoReport evaluate(AaoProfile p) {
        AaoReport report = new AaoReport();

        // 1. Crawlability 25%：AI 爬虫可达是前提
        int crawl = 0;
        List<String> crawlMiss = new ArrayList<>();
        if (p.isAllowAiCrawlers()) crawl += 50; else crawlMiss.add("robots.txt 未放行 AI 爬虫（GPTBot/ClaudeBot/PerplexityBot/Google-Extended）");
        if (p.isHasContentSignals()) crawl += 20; else crawlMiss.add("robots.txt 未声明 Content-Signal");
        if (p.isHttps()) crawl += 30; else crawlMiss.add("未启用 HTTPS");
        report.getDimensions().put(D_CRAWLABILITY, new AaoReport.DimensionScore(D_CRAWLABILITY, "可爬取性", 25, crawl, join(crawlMiss)));

        // 2. Structured Data 25%：Agent 理解实体
        int structured = 0;
        List<String> sMiss = new ArrayList<>();
        if (p.isHasStructuredData()) structured += 60; else sMiss.add("未部署 JSON-LD schema（Article/Organization/FAQPage）");
        if (p.isHasFaq()) structured += 40; else sMiss.add("缺少 FAQ 内容（Agent 直接抽取答案）");
        report.getDimensions().put(D_STRUCTURED, new AaoReport.DimensionScore(D_STRUCTURED, "结构化数据", 25, structured, join(sMiss)));

        // 3. Content Quality 15%：机器可消费内容
        int content = 0;
        List<String> cMiss = new ArrayList<>();
        if (p.isDescriptionQuality()) content += 60; else cMiss.add("能力描述不完整（一句话说不清能做什么）");
        if (p.isHasFaq()) content += 40; else cMiss.add("缺少自然语言问答内容");
        report.getDimensions().put(D_CONTENT, new AaoReport.DimensionScore(D_CONTENT, "内容质量", 15, content, join(cMiss)));

        // 4. Agent Interaction 20%：能否被 Agent 调用
        int interact = 0;
        List<String> iMiss = new ArrayList<>();
        if (p.isHasLlmsTxt()) interact += 40; else iMiss.add("未发布 /llms.txt");
        if (p.isHasOpenApi() || p.isHasApiCatalog()) interact += 20; else iMiss.add("未暴露 API 描述（OpenAPI / api-catalog）");
        if (p.isHasMcpCard()) interact += 20; else iMiss.add("未发布 MCP Server Card（/.well-known/mcp/server-card.json）");
        if (p.getApiCount() > 0 || p.getToolCount() > 0) interact += 20; else iMiss.add("无可调用 API/工具");
        report.getDimensions().put(D_INTERACTION, new AaoReport.DimensionScore(D_INTERACTION, "Agent 可调用性", 20, interact, join(iMiss)));

        // 5. Discoverability 10%：Agent 能否发现你
        int discover = 0;
        List<String> dMiss = new ArrayList<>();
        if (p.isHasAgentJson()) discover += 60; else dMiss.add("未发布 A2A AgentCard（/.well-known/agent.json）");
        if (p.isHasLlmsFullTxt()) discover += 40; else dMiss.add("未发布 llms-full.txt（一次性摄入）");
        report.getDimensions().put(D_DISCOVERABILITY, new AaoReport.DimensionScore(D_DISCOVERABILITY, "可发现性", 10, discover, join(dMiss)));

        // 6. Security & Trust 5%：信任信号
        int trust = 0;
        List<String> tMiss = new ArrayList<>();
        if (p.isHttps()) trust += 50; else tMiss.add("无 HTTPS");
        if (p.isHasPrivacyPolicy()) trust += 50; else tMiss.add("缺少隐私政策/条款");
        report.getDimensions().put(D_TRUST, new AaoReport.DimensionScore(D_TRUST, "安全与信任", 5, trust, join(tMiss)));

        // 加权总分
        double ax = 0;
        for (AaoReport.DimensionScore d : report.getDimensions().values()) {
            ax += d.getWeight() / 100.0 * d.getScore();
        }
        report.setAxScore((int) Math.round(ax));
        report.setGrade(grade(ax));
        report.setSuggestions(buildSuggestions(report));
        return report;
    }

    // ---------- llms.txt 生成（llmstxt.org 格式） ----------

    /**
     * 生成 llms.txt：站点名 H1 + blockquote 摘要 + H2 分节链接清单。
     */
    public String generateLlmsTxt(String brandName, String siteUrl, String description, List<String> pages) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(blank(brandName, siteUrl)).append("\n\n");
        if (description != null && !description.isBlank()) {
            sb.append("> ").append(description.trim()).append("\n\n");
        }
        sb.append("## Pages\n\n");
        if (pages != null) {
            for (String page : pages) {
                String s = page.trim();
                if (s.isEmpty()) continue;
                if (s.contains(": ")) {
                    String[] parts = s.split(": ", 2);
                    sb.append("- [").append(parts[0]).append("](").append(parts[1]).append(")\n");
                } else {
                    sb.append("- ").append(s).append("\n");
                }
            }
        }
        sb.append("\n## AI-friendly\n\n");
        sb.append("- llms.txt: ").append(blank(siteUrl, "")).append("/llms.txt\n");
        return sb.toString();
    }

    // ---------- agent.json（A2A AgentCard 骨架） ----------

    /**
     * 生成 /.well-known/agent.json（A2A AgentCard）骨架。
     */
    public String generateAgentJson(String brandName, String siteUrl, String description,
                                    List<String> skills, String jsonRpcUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"name\": \"").append(jsonEscape(blank(brandName, "agent"))).append("\",\n");
        sb.append("  \"description\": \"").append(jsonEscape(blank(description, "AI Agent 能力描述"))).append("\",\n");
        sb.append("  \"url\": \"").append(jsonEscape(blank(siteUrl, ""))).append("\",\n");
        sb.append("  \"version\": \"1.0.0\",\n");
        if (skills != null && !skills.isEmpty()) {
            sb.append("  \"skills\": [");
            for (int i = 0; i < skills.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append("\"").append(jsonEscape(skills.get(i).trim())).append("\"");
            }
            sb.append("],\n");
        } else {
            sb.append("  \"skills\": [],\n");
        }
        sb.append("  \"dispatch\": \"").append(jsonEscape(blank(jsonRpcUrl, "/api/agent"))).append("\"\n");
        sb.append("}\n");
        return sb.toString();
    }

    // ---------- 工具方法 ----------

    private String grade(double ax) {
        if (ax >= 90) return "Excellent";
        if (ax >= 70) return "Good";
        if (ax >= 50) return "NeedsWork";
        return "Poor";
    }

    private List<String> buildSuggestions(AaoReport report) {
        List<String> out = new ArrayList<>();
        for (AaoReport.DimensionScore d : report.getDimensions().values()) {
            if (d.getScore() < 70 && d.getDetail() != null && !d.getDetail().isBlank()) {
                out.add("[" + d.getName() + "] " + d.getDetail());
            }
        }
        if (out.isEmpty()) {
            out.add("Agent 就绪度优秀：站点已可被 AI Agent 发现、理解与调用");
        }
        return out;
    }

    private String join(List<String> items) {
        return String.join("；", items);
    }

    private String blank(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s.trim();
    }

    /**
     * JSON 字符串安全转义：除反斜杠与双引号外，额外处理换行 / 回车 / 制表等
     * 控制字符，避免品牌名或描述含换行时生成非法 JSON（agent.json 解析失败）。
     */
    private String jsonEscape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
