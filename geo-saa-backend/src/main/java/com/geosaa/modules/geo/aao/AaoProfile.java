package com.geosaa.modules.geo.aao;

import lombok.Data;

/**
 * AAO（AI Agent Optimization）就绪度画像。
 *
 * <p>对标 2026 Agent-Ready 标准（llmstxt.org / RFC 9727 api-catalog /
 * SEP-1649 MCP Server Card / A2A AgentCard / Content Signals / AX Score 六维度）。
 * 由用户提交或系统采集得到，经 {@link AaoEngine#evaluate} 计算 Agent Experience Score。
 */
@Data
public class AaoProfile {

    /** 站点域名（如 klai.top） */
    private String domain;

    /** 是否发布 /llms.txt（LLM 友好索引） */
    private boolean hasLlmsTxt;

    /** 是否发布 llms-full.txt（全量文本） */
    private boolean hasLlmsFullTxt;

    /** 是否发布 /.well-known/api-catalog（RFC 9727 API 目录） */
    private boolean hasApiCatalog;

    /** 是否发布 /.well-known/mcp/server-card.json（MCP Server 卡片） */
    private boolean hasMcpCard;

    /** 是否发布 /.well-known/agent.json（A2A AgentCard） */
    private boolean hasAgentJson;

    /** 是否发布 /api/openapi.json（OpenAPI 3.1 描述） */
    private boolean hasOpenApi;

    /** robots.txt 是否允许 AI 爬虫（GPTBot/ClaudeBot/PerplexityBot/Google-Extended） */
    private boolean allowAiCrawlers;

    /** robots.txt 是否声明 Content-Signal（search/ai-train/ai-input） */
    private boolean hasContentSignals;

    /** 页面是否部署 JSON-LD 结构化数据（Article/Organization/FAQPage 等） */
    private boolean hasStructuredData;

    /** 是否含 FAQ 内容（对话式问答） */
    private boolean hasFaq;

    /** 站点是否 HTTPS */
    private boolean https;

    /** 是否发布隐私政策与条款（信任信号） */
    private boolean hasPrivacyPolicy;

    /** 对外暴露的 API 数量 */
    private int apiCount;

    /** Agent 可用工具/技能数量 */
    private int toolCount;

    /** 能力描述是否完整（一句话能说清"能做什么/为谁服务"） */
    private boolean descriptionQuality;
}
