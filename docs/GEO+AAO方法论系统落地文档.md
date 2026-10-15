# GEO+AAO 方法论系统落地文档

> 基于《GEO+AAO应用优化方法论》3套提示词，在 GEO-SaaS 平台中完成系统级落地。

## 一、落地总览

| 方法论模块 | 系统落地位置 | 对应提示词 | 状态 |
|-----------|------------|-----------|------|
| 系统策略总控 | `ai_prompt_template` 表 + `AiChatService` | 提示词1 | ✅ 已落地 |
| 内容优化改写器 | `GeoAaoContentService` + ContentController | 提示词2 | ✅ 已落地 |
| 诊断打分评估器 | `GeoAaoDiagnosisService` + DiagnoseController | 提示词3 | ✅ 已落地 |
| 五阶段方法论 | 诊断报告结构 + 风险分级 | 提示词1/3 | ✅ 已落地 |
| 评分模型 | SEO30 + GEO30 + AAO40 = 100分 | 提示词3 | ✅ 已落地 |

## 二、3套系统内置提示词

已存入 `ai_prompt_template` 表，可通过 API 动态调用和渲染：

| 模板编码 | 模板名称 | 业务模块 | 用途 |
|---------|---------|---------|------|
| `geo_aao_strategy_master` | GEO+AAO策略总控 | geo | 系统策略底层方法论基座，五阶段完整分析 |
| `geo_aao_content_rewriter` | GEO+AAO内容改写器 | content | 页面内容优化改写，TL;DR+FAQ+证据锚点 |
| `geo_aao_diagnosis_scorer` | GEO+AAO诊断评估器 | diagnose | 站点诊断打分，SEO30+GEO30+AAO40 |

**查看模板 API**：
```
GET /api/v1/ai/prompt-template/code/geo_aao_strategy_master
GET /api/v1/ai/prompt-template/code/geo_aao_content_rewriter
GET /api/v1/ai/prompt-template/code/geo_aao_diagnosis_scorer
```

## 三、诊断评分引擎（核心）

### 3.1 评分模型

```
总分 0-100 = SEO技术底座(0-30) + GEO实体可信度(0-30) + AAO答案捕获能力(0-40)

评级：S(90+) / A(75~89) / B(60~74) / C(40~59) / D(<40)
```

### 3.2 评分维度明细

**一、SEO技术底座（0-30分）**

| 检查项 | 扣分 | P0自动修复 |
|--------|------|-----------|
| 爬虫抓取障碍 | -5 | ✅ 修复robots.txt/meta robots |
| 死链 >10个 | -4 | ✅ 批量301重定向 |
| 页面性能 <50分 | -5 | ✅ 图片压缩/CDN |
| 移动端不适配 | -3 | - |
| H1/H2层级不规范 | -3 | ✅ 规范标题层级 |
| 缺少sitemap | -3 | ✅ 生成sitemap.xml |
| Meta标签不规范 | -2 | - |

**二、GEO实体可信度（0-30分）**

| 检查项 | 扣分 | P0自动修复 |
|--------|------|-----------|
| 实体信息全网不一致 | -8 | ✅ 全网统一品牌实体信息 |
| 缺少JSON-LD结构化标记 | -6 | ✅ 添加Organization/Product/FAQPage/Article |
| 缺少llms.txt | -4 | ✅ 新增llms.txt |
| EEAT证据 <3个 | -4 | -（人工补充案例/数据/证书） |
| 全网信源冲突 | -4 | -（人工修正） |

**三、AAO答案捕获能力（0-40分）**

| 检查项 | 扣分 | P1人工校验 |
|--------|------|-----------|
| 缺少TL;DR摘要 | -8 | ✅ 页面顶部增加一句话摘要 |
| 缺少FAQ模块 | -10 | ✅ 增加用户口语化问答 |
| 问句匹配度 <40分 | -6 | ✅ 围绕用户AI问句创作内容 |
| 内容不可摘录（大段纯文本） | -5 | ✅ 拆分为短句/列表/表格 |
| AI答案命中率 <20% | -5 | -（长期优化） |

### 3.3 诊断 API

**请求**：
```http
POST /api/v1/diagnose/geo-aao/diagnose
Content-Type: application/json
Authorization: Bearer {token}

{
  "siteUrl": "https://example.com",
  "brandName": "品牌名称",
  "auditData": {
    "seo_crawlable": true,
    "seo_dead_links": 5,
    "seo_performance_score": 75,
    "seo_mobile_friendly": true,
    "seo_heading_standard": false,
    "seo_sitemap_exists": true,
    "seo_meta_standard": true,
    "geo_entity_consistent": false,
    "geo_jsonld_exists": false,
    "geo_llms_txt_exists": false,
    "geo_eeat_evidence_count": 2,
    "geo_source_conflicts": 1,
    "aao_tldr_exists": false,
    "aao_faq_exists": false,
    "aao_query_match_score": 45,
    "aao_extractable": true,
    "aao_answer_hit_rate": 15,
    "brand_name": "品牌名称"
  }
}
```

**响应**：
```json
{
  "code": 200,
  "data": {
    "totalScore": 43,
    "grade": "C",
    "dimensions": {
      "seo": {"score": 25, "maxScore": 30, "issues": ["..."]},
      "geo": {"score": 4, "maxScore": 30, "issues": ["..."]},
      "aao": {"score": 14, "maxScore": 40, "issues": ["..."]}
    },
    "risks": {
      "p0": ["紧急问题（系统可自动修复）"],
      "p1": ["内容优化（AI辅助人工校验）"],
      "p2": ["长期权威信源建设（人工）"]
    },
    "recommendations": [
      {"priority": "P0", "action": "...", "type": "系统自动执行", "expectedGain": "..."}
    ],
    "opportunities": {
      "targetQueries": ["品牌怎么样？", "品牌价格是多少？"],
      "recommendedFaqTopics": ["产品选型指南", "价格与套餐对比"]
    },
    "disclaimer": "本评估仅预测提升概率..."
  }
}
```

### 3.4 双引擎诊断机制

诊断服务采用**规则评分 + AI深度诊断**双引擎：

1. **规则评分引擎**：基于20+项检查点自动打分，确定性高，响应快
2. **AI深度诊断引擎**：调用大模型（使用`geo_aao_diagnosis_scorer`模板），生成专业的问题分析和优化建议
3. **结果合并**：规则评分提供基础分和P0风险，AI诊断补充专业建议和机会点

## 四、内容优化改写器

### 4.1 核心能力

基于提示词2，实现7项强制改写规则：

1. **TL;DR一句话摘要**：页面最顶部，便于LLM直接摘取
2. **实体信息保护**：保留原有业务事实，不编造数据/资质/案例
3. **标题层级规范**：唯一H1，H2/H3逐级拆分，用户高频AI问句做H2
4. **FAQ模块生成**：用户口语化问答，配套FAQPage结构化数据
5. **长文本拆分**：短句、列表、对比表格，减少大段纯文本
6. **证据锚点强化**：案例、参数、证书、实测结果，提升EEAT
7. **自然语言优化**：不堆砌关键词，面向人类提问，适配LLM语义理解

### 4.2 内容改写 API

```http
POST /api/v1/content/geo-aao/rewrite

{
  "pageTitle": "页面标题",
  "content": "原始页面内容",
  "brandEntities": "品牌实体基准信息（名称、地址、联系方式、产品参数）"
}
```

**响应**：
```json
{
  "success": true,
  "rewrittenContent": "优化后完整页面文案（含TL;DR+规范标题+FAQ+证据锚点）",
  "changeNotes": "GEO/AAO改动说明清单",
  "modelName": "使用的AI模型",
  "totalTokens": 1024,
  "latencyMs": 1500
}
```

### 4.3 专项工具 API

| API | 用途 |
|-----|------|
| `POST /api/v1/content/geo-aao/tldr` | 单独生成TL;DR一句话摘要 |
| `POST /api/v1/content/geo-aao/faq` | 单独生成FAQ问答模块 |

## 五、五阶段方法论系统映射

| 方法论阶段 | 系统模块 | 系统能力 |
|-----------|---------|---------|
| **阶段1：基线诊断&资产盘点** | diagnose + monitor | SEO技术审计、GEO实体诊断、信源分级盘点、风险分级(P0/P1/P2) |
| **阶段2：技术底座优化** | geo + content | 扁平化架构、标签标准化、JSON-LD、llms.txt、性能优化（仅本平台建站站点支持自动修改） |
| **阶段3：语义内容重构** | content + GeoAaoContentService | TL;DR摘要、FAQ模块、证据锚点、实体归一、问句对齐、内容改写器 |
| **阶段4：全域信源池搭建** | distribute + knowledge | 三级信源体系、稿件一键分发、实体信息统一对齐 |
| **阶段5：PDCA监测与迭代** | monitor + statistics + GeoAaoDiagnosisService | SEO/GEO/AAO三类指标、PDCA循环、自动批量LLM抽样检测、P0自动修复 |

## 六、四大核心策略系统实现

| 策略 | 系统实现 |
|------|---------|
| **实体归一策略** | `geo_entity_consistent`检查项 + P0自动修复建议 + 全网实体信息统一校验 |
| **结构化降理解成本** | JSON-LD自动生成 + FAQPage结构化数据 + 内容改写器自动拆分表格/列表 |
| **证据增强策略** | `geo_eeat_evidence_count`检查项 + 内容改写器强制证据锚点 + EEAT评分 |
| **问句对齐策略** | `aao_query_match_score`检查项 + 目标问句机会点推荐 + FAQ自动生成 |

## 七、边界约束（系统强制执行）

1. **自动代码优化仅支持本平台建站站点**，外部站点仅诊断，不自动修改代码
2. **系统只能完成**：技术修复、结构化改造、基础内容生成、监测评估；**无法自动生成**媒体报道、权威背书等高阶三级信源，标记为人工任务
3. **GEO/AAO无法承诺100%引用**，所有诊断报告附带免责声明："本评估仅预测提升概率，不能保证大模型一定会引用或优先展示答案"
4. **严禁批量生成低质AI垃圾内容**，内容生成模块集成合规检测，关键词堆砌超阈值时拦截

## 八、数据库变更

- **V6__geo_aao_prompts.sql**：新增3条系统内置Prompt模板记录
- 模板存储在 `ai_prompt_template` 表，支持动态渲染和版本管理

## 九、新增文件清单

| 文件 | 说明 |
|------|------|
| `diagnose/dto/GeoAaoDiagnosisReport.java` | GEO+AAO诊断报告DTO（标准化输出结构） |
| `diagnose/service/GeoAaoDiagnosisService.java` | GEO+AAO诊断评分引擎（规则评分+AI深度诊断） |
| `content/service/GeoAaoContentService.java` | GEO+AAO内容优化改写器（TL;DR+FAQ+证据锚点） |
| `db/V6__geo_aao_prompts.sql` | 3套系统内置提示词迁移脚本 |

## 十、新增 API 清单

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/diagnose/geo-aao/diagnose` | GEO+AAO综合诊断评分（SEO30+GEO30+AAO40） |
| POST | `/api/v1/content/geo-aao/rewrite` | GEO+AAO内容优化改写（完整文案+改动说明） |
| POST | `/api/v1/content/geo-aao/tldr` | 生成TL;DR一句话摘要 |
| POST | `/api/v1/content/geo-aao/faq` | 生成FAQ问答模块 |

## 十一、后续优化方向

1. **前端诊断页面升级**：集成GEO+AAO评分雷达图、三维度得分可视化、风险分级看板
2. **自动修复引擎**：P0问题一键自动修复（标题层级规范化、JSON-LD生成、llms.txt生成）
3. **LLM批量抽样检测**：自动批量调用大模型做GEO/AAO效果复测，生成趋势报告
4. **竞品GEO/AAO对标**：输入竞品URL，自动对比引用率、提及位置、答案命中率
5. **内容健康度评分**：每篇内容自动计算GEO+AAO健康分，低于阈值自动提醒改写
6. **信源池管理**：三级信源台账、分发状态追踪、事实一致性自动校验
