# GEO SaaS 平台 · 升级开发文档（v1.1）

> **文档定位**：基于原《SEO / GEO / AAO 三代搜索增长策略》市场理念稿，升级为可落地的**产品开发与工程对齐文档**。
> 面向对象：产品 / 研发 / 运营。本文定义 geo-saa 平台如何把"三代搜索增长"理论工程化，并校准原稿中的概念偏差。
>
> 关联文档：`README.md`、`docs/ARCHITECTURE.md`、`docs/ROADMAP.md`、`APIDOC.md`

---

## 0. 修订说明（相对原稿的升级点）

| # | 原稿问题 | 本次升级 |
|---|----------|----------|
| 1 | AEO 与 GEO 概念混用、AAO 定义模糊 | 明确 **SEO → AEO → GEO → AAO** 四层演进与边界，AAO 正式定义为 *AI Agent Optimization（智能体可调用优化）* |
| 2 | GEO 无权威依据、战术空泛 | 补充 **Princeton KDD 2024（arXiv:2311.09735）** 学术来源与**九大战术实测数据** |
| 3 | 策略与产品脱节 | 将四层策略映射到 geo-saa **真实模块**与**真实指标字段**（mentionRate / firstRecommendRate / collectionCount / score） |
| 4 | 三阶段路线偏营销 | 改为**开发视角**的分阶段 Backlog + 优先级 + 阶段目标 |
| 5 | 流量估算表当作事实 | 标注为**目标基线（示意）**，以平台 monitor 真实指标为准 |

---

## 1. 概念升级：四层搜索增长模型

### 1.1 术语与边界（已校准）

| 层级 | 全称 | 目标 | 优化对象 | 用户行为 | 成熟度 |
|------|------|------|----------|----------|--------|
| **SEO** | Search Engine Optimization | 搜索结果**排名** | 网页 / 链接 | 人点链接查看 | 成熟 |
| **AEO** | Answer Engine Optimization | 成为**直接答案** | 结构化问答 | 零点击获取 | 增长 |
| **GEO** | Generative Engine Optimization | 被 **AI 答案引用** | 结构化知识 | 直接读答案 | 快速增长 |
| **AAO** | AI Agent Optimization（智能体可调用优化） | 被 **Agent 调用** | 服务 / API | 委托决策 | 早期 |

> **说明**：AEO 与 GEO 在实践中高度重叠（Yext、ZGM 等均将 AEO 视为 GEO 的子集），geo-saa 对其**合并治理、对外统称 GEO**；AAO 是 2025 年起浮现的新层（Yext 正式以 "AI Agent Optimization (AAO)" 命名），原稿"让 Agent 用你"即指此层。三者并非替代关系，而是**作用层递进**。

### 1.2 GEO 的权威依据（原稿缺失，本次补齐）

- **来源**：Aggarwal et al., *GEO: Generative Engine Optimization*, **ACM SIGKDD 2024**（arXiv:2311.09735），Princeton / Georgia Tech / Allen AI / IIT Delhi。
- **方法**：GEO-bench，10,000+ 查询 × 多领域，在类 Bing Chat 引擎 + Perplexity 实测。
- **核心结论**：内容侧改造最高可提升 **40%** AI 答案可见度；**对低排名站点增益最大**（引用源策略对第 5 名站点提升达 +115%）—— 小品牌反超窗口期。
- **最强判据**：**Information Gain（信息增益）** 是引用概率的最强预测因子，强于域名权威、外链、关键词密度。

### 1.3 GEO 九大战术（论文实测，融入原稿四大策略）

论文实测 9 条战术，5 条显著有效、1 条负向、其余中性：

| 战术 | 可见度影响 | 归类 |
|------|-----------|------|
| 专家引述 Quotation Addition | **+41%**（最强） | ✅ 有效 |
| 统计 / 数据 Statistics Addition | +33% | ✅ 有效 |
| 引用来源 Cite Sources | +28%（小品牌可达 +115%） | ✅ 有效 |
| 流畅度 Fluency Optimization | +29% | ✅ 有效 |
| 权威语调 Authoritative Tone | 正向但弱于证据 | ✅ 弱有效 |
| 技术术语 / 易读性 / 独特词 | 中性（取决于领域） | ⚠️ 中性 |
| **关键词堆砌 Keyword Stuffing** | **−10%** | ❌ **损害** |

→ 映射回原稿四大策略：

- **策略① 权威信源建设** ← Quotation + Statistics + Cite Sources（**证据密度决定引用**，而非说服力度）
- **策略② 语义结构化输出** ← Fluency + 结构化（核心结论 → 论据 → 数据 → 延伸阅读）
- **策略③ 品牌提及矩阵** ← 多平台品牌存在（被多源同时索引 = AI 眼中的权威品牌）
- **策略④ 多模态内容覆盖** ← 图表 / 数据可视化 / 短视频被模型索引为引用素材

> **反直觉要点**：原稿未强调"少做关键词堆砌"。论文证明关键词密度在生成式引擎中不仅无用反而有害——这与传统 SEO 肌肉记忆相反，需在内容生成器中显式规避。

---

## 2. geo-saa 产品能力映射（真实模块）

平台现有能力已天然覆盖 SEO→GEO 三层，AAO 为规划层：

| 四层策略 | geo-saa 模块 | 真实接口 / 字段 | 当前状态 |
|----------|--------------|-----------------|----------|
| 权威信源建设 | 品牌诊断 `diagnose` | `GET /api/v1/diagnose/list`；`AiDiagnoseTask.resultContent`（含 `score/gaps/suggestions/competitorComparison`） | ✅ 已落地 |
| 语义结构化输出 | 知识库 `knowledge` | `GET /api/v1/knowledge/brands` | ✅ 已落地 |
| 语义结构化输出 | AI 内容创作 `content` | `GET /api/v1/content/list`、`/content/templates` | ✅ 已落地 |
| 品牌提及矩阵 | 多渠道分发 `distribute` | `GET /api/v1/distribute/list`、`/channels`、`/stats` | ✅ 已落地 |
| **GEO 指标度量** | 数据监测 `monitor` | `GET /api/v1/monitor/core-metrics`：`mentionRate` / `firstRecommendRate` / `collectionCount` / `score` | ✅ 已落地（含拆箱修复） |
| GEO 趋势 | 数据监测 `monitor` | `GET /api/v1/monitor/trend`（日/周/月聚合） | ✅ |
| 竞品对标 | 数据监测 `monitor` | `GET /api/v1/monitor/competitor` | ✅ |
| 信源资产管理 | 资产 `asset` | `GET /api/v1/asset/overview`、`/asset/list` | ✅ 已落地（真实接口） |
| 协同效应看板 | 统计 `statistics` | `GET /api/v1/statistics/dashboard` | ✅ |
| **AAO 服务可调用化** | ——（规划中） | OpenAPI / MCP endpoint | ⬜ Phase 3 |

### 2.1 关键发现：平台本身是 GEO 原生设计

`MonitorService.getCoreMetrics` 的评分公式：

```
score = mentionRate * 0.4
      + firstRecommendRate * 0.35
      + min(100, collectionCount / 10) * 0.25
```

这已是一个**现成的 GEO 健康度评分**，直接对应原稿"GEO 流量 / AI 引用率"目标。三个底层字段的语义与论文完全吻合：

- `mentionRate`（提及率）= AI 答案中提及品牌的占比 → GEO 核心指标
- `firstRecommendRate`（首推率）= 被作为首选推荐的占比 → GEO 进阶指标
- `collectionCount`（收录量）= 被 AI 索引收录的内容数 → 信源资产指标

**结论**：geo-saa 的架构从第一天起就是 GEO 原生设计，无需重构，只需"填充真实数据与扩展 AAO 层"。

### 2.2 ⚠️ 数据真实性风险（上线前必须解决）

当前 `getCoreMetrics` 在无 DB 数据时回退到 `Math.random()`（AI 模拟模式，受 `ai.simulation.enabled` 控制）：

```java
metrics.put("mentionRate", mentionStat != null ? mentionStat.getStatValue() : Math.round(Math.random() * 100));
```

→ **模拟数据不可用于对外交付 / 客户报告**。Phase 2 的 P0 任务即打通真实采集（见 §4）。

---

## 3. 指标体系（开发口径，锚定真实字段）

| 指标 | 字段 | 含义 | 对应原稿目标 | 采集方式（规划） |
|------|------|------|--------------|------------------|
| 提及率 | `mentionRate` (`mention_rate`) | AI 答案中提及品牌的占比 | GEO 流量 | LLM 检索 / 平台 API |
| 首推率 | `firstRecommendRate` (`first_recommend_rate`) | 被作为首选推荐的占比 | GEO 心智 | 同上 |
| 收录量 | `collectionCount` (`collection_count`) | 被 AI 索引收录的内容数 | 信源资产 | 爬虫 / 主动提交 |
| 综合健康度 | `score` | 加权评分 (0–100) | 总览 | 公式计算 |
| 趋势 | `/monitor/trend` | 日/周/月聚合 | 监测 | 聚合 |
| 竞品 | `/monitor/competitor` | 竞品对标 | 差异化 | 对标 |

> 现存 `DataMonitorStat` 表以 `stat_type` + `stat_key` + `stat_date` + `stat_value` 建模，天然支持上述指标的时序存储与聚合，无需改表结构。

---

## 4. 分阶段落地路线图（开发视角）

### Phase 1 — SEO 基础建设（M1–3）：地基与可观测

- [ ] 技术 SEO 审计模块（`diagnose` 扩展 `seo` 子类型）：可抓取性、Schema 标记检测
- [ ] 关键词矩阵 + 结构化数据部署（对接 `statistics/dashboard`）
- [ ] 高质量内容生产启动（`content` 模块深化模板）
- 🎯 **目标**：核心词进入搜索结果 TOP 10；`monitor` 指标基线建立（结束随机模拟）

### Phase 2 — GEO 策略部署（M2–6）：引用率起量（核心窗口）

- [ ] **G-01 真实 GEO 数据采集器**（替代 `Math.random` 回退）：接 Perplexity / 秘塔 AI 搜索 / 天工 AI / Google AI Overviews 检索 API，计算真实 `mentionRate` / `firstRecommendRate`
- [ ] **G-02 内容 GEO 九战术校验器**：`content` 生成时按论文战术校验（专家引述、统计、引用源、流畅度；**拦截关键词堆砌**）
- [ ] **G-03 品牌矩阵分发渠道扩展**：`distribute` 接入知乎 / 36氪 / 行业垂直媒体
- [ ] **G-04 诊断报告白皮书导出**：`diagnose` 生成可发布的行业报告级 PDF（用 `resultContent` 结构化结论）
- [ ] 监测 AI 引用率并迭代信源策略（`monitor` 看板）
- 🎯 **目标**：AI 答案引用率进入行业 TOP 3

### Phase 3 — AAO 生态接入（M6–12）：锁定 Agent 决策链

- [ ] **A-01 核心服务 API 化**：统一 OpenAPI 规范 + 鉴权（基于现有 `system` 模块）
- [ ] **A-02 MCP server 封装**：将预约 / 库存 / 报价 / 比价等事务型接口暴露为 Agent 可调用端点
- [ ] **A-03 Agent 调用度量模块**：新增 `monitor(aao)` 指标（调用量、成功率、任务完成率）
- [ ] Agent 平台注册与发现（微信元器 / 扣子 / Dify / Coze）
- 🎯 **目标**：Agent 调用量月增 ≥ 20%

---

## 5. 开发 Backlog（优先级）

| 编号 | 功能 | 模块 | 优先级 | 阶段 |
|------|------|------|--------|------|
| **G-01** | 真实 GEO 数据采集器（替代模拟） | monitor | **P0** | P2 |
| **G-02** | 内容 GEO 九战术校验器 | content | **P0** | P2 |
| **A-01** | OpenAPI 规范化 | system/api | **P0** | P3 |
| **A-02** | MCP server 封装 | integration | **P0** | P3 |
| G-03 | 品牌矩阵分发渠道扩展 | distribute | P1 | P2 |
| G-04 | 诊断报告白皮书导出 | diagnose | P1 | P2 |
| A-03 | Agent 调用度量模块 | monitor(aao) | P1 | P3 |
| G-05 | 竞品对标自动化 | monitor | P2 | P2 |
| S-01 | 技术 SEO 审计 | diagnose/seo | P2 | P1 |

---

## 6. KPI 与度量（原稿估算表升级版）

原稿协同效应估算保留为**目标基线（示意）**，不作为交付承诺；geo-saa 以 `monitor` 真实指标为准：

| 阶段 | SEO 流量 | GEO 流量 | AAO 流量 | 总增幅（示意） |
|------|----------|----------|----------|----------------|
| 第 1 月 | 100% | — | — | 基准线 |
| 第 3 月 | 100% | +30% | — | +30% |
| 第 6 月 | 100% | +50% | +10% | +60% |
| 第 12 月 | 110% | +80% | +30% | +120% |

> ⚠️ 数据为估算参考，实际效果因行业与投入规模而异。对外材料须标注"示意"。

---

## 7. 风险与开放问题

1. **AAO 标准未定**：Agent protocol（OpenAI / Google / Anthropic）仍在演进，MCP 已成事实标准但非唯一；Phase 3 需预留**适配层**，避免被单一协议锁定。
2. **数据真实性**：当前 `monitor` 模拟数据不可用于对外交付，P0 打通真实采集（G-01）。
3. **合规与反垃圾**：品牌提及矩阵需避免被平台判为 spam；引用需真实可溯源（呼应 `SECURITY.md` 与合规要求）。
4. **原稿概念偏差**：已校准 AEO / GEO / AAO 边界，对外统一口径为"SEO + GEO + AAO"三层。

---

## 8. 下一步建议

本文为**开发对齐文档**。后续可推进：

1. **产品 PRD**：将 §4 Phase 2 细化为功能规格（G-01 / G-02 详细设计）
2. **真实采集器技术方案**：检索 API 选型、采样策略、成本控制
3. **AAO 接口契约**：OpenAPI / MCP 资源定义草案

文档随迭代更新版本号（当前 v1.1）。
