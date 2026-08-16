# GEO SaaS 平台 · 开发文档 v2（2026 规则引擎）

> **文档定位**：geo-saa 平台如何把「三代搜索增长」理论工程化，并落地 **2026 最新 GEO/AAO 规则**。
> 面向对象：产品 / 研发 / 运营。
>
> 关联文档：`README.md`、`docs/ARCHITECTURE.md`、`APIDOC.md`、`docs/NPM_PUBLISH_GUIDE.md`、`geo-engine/`（npm 包）

---

## 0. 版本说明

| 版本 | 说明 |
|------|------|
| v1.0 | 基于 Princeton KDD 2024《GEO: Generative Engine Optimization》九战术 |
| **v2.0（2026-08-16）** | 升级为 **16 维度**（九战术 + 7 个 2026 新维度）+ 全新 **AAO 引擎**（AX Score 六维度 / llms.txt / agent.json）；前端新增 **GEO/AAO 体检工作台**；核心引擎发布为 npm 包 `@wch-klzx/geo-engine` |

---

## 1. GEO 引擎 v2：16 维度评分

### 1.1 维度与权重（后端 `GeoContentValidator`，JS 版 `geo-engine/lib/geo-validator.js`）

| # | 维度 | 权重 | 来源/依据 |
|---|------|------|-----------|
| 1 | 专家引语 quotations | 0.12 | KDD 2024（+41%） |
| 2 | 量化数据 statistics | 0.10 | KDD 2024（+31%） |
| 3 | 引用来源 cite_sources | 0.08 | KDD 2024（+28%） |
| 4 | 流畅度 fluency | 0.07 | KDD 2024（+28%） |
| 5 | 技术术语 technical_terms | 0.05 | KDD 2024（+18%） |
| 6 | 易于理解 easy_to_understand | 0.05 | KDD 2024（+14%） |
| 7 | 权威语气 authoritative | 0.04 | KDD 2024（+10%） |
| 8 | 独特词汇 unique_words | 0.02 | KDD 2024（+6%） |
| 9 | **答案前置 answer_first** | 0.10 | 2026：AI 常只读前 200 字，核心答案必须前置 |
| 10 | **事实密度 fact_density** | 0.10 | 2026：每节 ≥1 可引用事实（数字/名称/日期） |
| 11 | **结构化数据 structured_data** | 0.09 | 2026：JSON-LD 引用概率 +2.5x、AI Overview +40% |
| 12 | **E-E-A-T eeat** | 0.07 | 2026：作者/日期/机构/资质四类信任信号 |
| 13 | **关键引语 key_quote** | 0.04 | 2026：blockquote 被 AI 视为 key takeaway |
| 14 | **新鲜度 freshness** | 0.04 | 2026：30/90/365 天三档，AI 偏好新鲜来源 |
| 15 | **一手来源 own_citations** | 0.03 | 2026：外链一手来源提升可引用性 |
| — | 关键词堆砌 keyword_stuffing | 负向 | 密度 >3% **且** 提及 ≥3 次 → `blocked=true`、总分折半 |

> 接口：`POST /api/v1/content/geo-validate`（支持可选 `publishDate`）。

### 1.2 拦截规则（不变）

- 关键词堆砌是唯一负向战术（论文实测可见度 −8%~−10%），触发即 `blocked`；
- `createContent` 对堆砌内容直接返回 400，AI 生成内容只告警不拦。

---

## 2. AAO 引擎（2026 Agentic SEO）

### 2.1 AX Score 六维度模型（后端 `AaoEngine`，JS 版 `geo-engine/lib/aao-engine.js`）

| 维度 | 权重 | 衡量 | 关键信号 |
|------|------|------|----------|
| 可爬取性 Crawlability | 25% | Agent 能否访问 | robots.txt 放行 AI 爬虫、Content-Signal、HTTPS |
| 结构化数据 Structured Data | 25% | Agent 能否理解 | JSON-LD schema、FAQ 内容 |
| 内容质量 Content Quality | 15% | 是否机器可消费 | 能力描述完整、自然语言问答 |
| Agent 可调用性 Interaction | 20% | 能否被调用 | llms.txt、OpenAPI/api-catalog、MCP Server Card、可用工具 |
| 可发现性 Discoverability | 10% | 能否被发现 | A2A agent.json、llms-full.txt |
| 安全与信任 Security & Trust | 5% | 是否可信 | HTTPS、隐私政策 |

分级：Excellent(90+) / Good(70-89) / NeedsWork(50-69) / Poor(0-49)。

### 2.2 Agent-Ready 文件（站点应部署）

| 文件 | 标准 | 用途 |
|------|------|------|
| `/llms.txt` | llmstxt.org | LLM 友好索引（H1+blockquote+Pages+AI-friendly） |
| `/llms-full.txt` | llmstxt.org | 全量文本，一次性摄入 |
| `/.well-known/api-catalog` | RFC 9727 | API 目录（linkset） |
| `/.well-known/mcp/server-card.json` | SEP-1649 | MCP Server 描述 |
| `/.well-known/agent.json` | A2A | Agent 身份/技能/端点 |
| `robots.txt` | — | 放行 GPTBot/ClaudeBot/PerplexityBot/Google-Extended + Content-Signal |

> 接口：`POST /api/v1/geo/aao-validate`、`GET /api/v1/geo/llms-txt`、`GET /api/v1/geo/agent-json`。

---

## 3. 产品功能映射

| 平台功能 | GEO/AAO 能力 | 2026 规则依据 |
|----------|--------------|---------------|
| AI 内容创作 → GEO 校验 | 16 维度评分 + 堆砌拦截 | 九战术 + 答案前置/事实密度/结构化数据 |
| 新「GEO/AAO 体检」工作台 | 内容校验 / AX 评估 / llms.txt·agent.json 生成 | 六维度模型 + Agent-Ready 文件标准 |
| 数据监测 | mentionRate / 首推率 / 收录量（真实采集器 G-01） | 引文审计（4 引擎 × 旗舰查询） |
| 诊断中心 | 可见度评分 | E-E-A-T / 实体权威（Topical Gravity） |

---

## 4. npm 包（能力外溢）

`@wch-klzx/geo-engine`（`geo-engine/`，零依赖 ESM）：
- `validateGeo(content, keywords?, publishDate?)` — 16 维度 GEO 校验
- `evaluateAao(profile)` — AX Score 六维度
- `generateLlmsTxt / generateAgentJson` — 生成器
- 发布：`cd geo-engine && npm version patch && git push origin main --tags`（CI 自动发布）

---

## 5. 待办（2026-08-16）

| # | 项 | 优先级 |
|---|-----|--------|
| 1 | G-01 真实采集器配 Perplexity Key 首次试采 | P0（待 Key） |
| 2 | klai.top 站点实际部署 llms.txt / agent.json / api-catalog | P1 |
| 3 | 站点 robots.txt 放行 AI 爬虫 + Content-Signal | P1 |
| 4 | 前端大版本依赖升级（vite8 等，见 DEPENDENCY_ASSESSMENT） | P2 |
| 5 | Docker compose 实测（O8） | P2 |
