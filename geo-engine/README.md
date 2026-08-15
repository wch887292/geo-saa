# @wch-klzx/geo-engine

GEO/AAO 引擎 —— AI 搜索可见度（GEO）与 AI Agent 就绪度（AAO）的工程化校验与生成工具。

- **GEO 校验**：九战术（Princeton KDD 2024 实测）+ 2026 GEO v2 维度（答案前置 / 事实密度 / 结构化数据 / E-E-A-T / 关键引语 / 新鲜度 / 一手来源），关键词堆砌自动拦截
- **AAO 评估**：Agent Experience Score（AX Score）六维度模型，输出 0-100 评分与分级
- **生成器**：`llms.txt`（llmstxt.org 标准）与 `/.well-known/agent.json`（A2A AgentCard）

零运行时依赖，纯启发式、结果可解释，可用于品牌站点 CI 内容门禁、GEO 审计脚本或 Agent 就绪度体检。

## 安装

```bash
npm install @wch-klzx/geo-engine
```

## 用法

### 1. GEO 内容健康度校验

```js
import { validateGeo } from '@wch-klzx/geo-engine';

const result = validateGeo(
  '据《中国AI产业白皮书》显示，2025年中国AI市场规模达 4500 亿元，同比增长 35%（来源：工信部）。专家表示：「AI搜索正在重塑品牌获客方式。」参考 https://klai.top 了解更多。',
  'AI搜索',           // 目标关键词（逗号分隔），用于堆砌检测
  '2026-08-01'        // 发布时间（可选，影响新鲜度评分）
);

console.log(result.totalScore); // 0-100
console.log(result.blocked);    // 关键词堆砌超阈值时为 true
console.log(result.tactics);    // 17 项维度逐项评分
console.log(result.suggestions);// 优化建议
```

### 2. AAO / Agent 就绪度评估

```js
import { evaluateAao } from '@wch-klzx/geo-engine';

const report = evaluateAao({
  allowAiCrawlers: true,      // robots.txt 放行 GPTBot/ClaudeBot/PerplexityBot
  hasLlmsTxt: true,           // 发布 /llms.txt
  hasMcpCard: false,          // 未发布 MCP Server Card
  hasAgentJson: true,         // 发布 /.well-known/agent.json
  hasOpenApi: true,           // 发布 OpenAPI 描述
  hasStructuredData: true,    // JSON-LD schema
  hasFaq: true,
  https: true,
  hasPrivacyPolicy: true,
  apiCount: 12,
  toolCount: 5,
  descriptionQuality: true
});

console.log(report.axScore);       // 0-100
console.log(report.grade);         // Excellent / Good / NeedsWork / Poor
console.log(report.dimensions);    // 六维度逐项
console.log(report.suggestions);   // 待办清单
```

### 3. 生成 llms.txt / agent.json

```js
import { generateLlmsTxt, generateAgentJson } from '@wch-klzx/geo-engine';

const llms = generateLlmsTxt('飞虹智', 'https://klai.top',
  '泉州制造业 AI 服务商。', ['官网: https://klai.top']);

const agent = generateAgentJson('飞虹智', 'https://klai.top', '企业 AI Agent',
  ['品牌诊断', 'GEO 内容生成'], 'https://klai.top/api/agent');
```

## API

| 函数 | 说明 |
|------|------|
| `validateGeo(content, keywords?, publishDate?)` | GEO 健康度校验，返回 `{totalScore, blocked, redFlags, suggestions, tactics}` |
| `evaluateAao(profile)` | AX Score 评估，返回 `{axScore, grade, dimensions, suggestions}` |
| `generateLlmsTxt(brandName, siteUrl, description?, pages?)` | 生成 llms.txt（llmstxt.org 格式） |
| `generateAgentJson(brandName, siteUrl?, description?, skills?, dispatchUrl?)` | 生成 A2A agent.json 骨架 |

## 维度说明

**GEO（17 项）**：专家引语 / 量化数据 / 引用来源 / 流畅度 / 技术术语 / 易于理解 / 权威语气 / 独特词汇（KDD 2024 九战术中八项正向）+ 答案前置 / 事实密度 / 结构化数据 / E-E-A-T / 关键引语 / 新鲜度 / 一手来源（2026 新维度）+ 关键词堆砌（负向，超阈值拦截）。

**AAO（AX Score 六维度）**：可爬取性 25% / 结构化数据 25% / 内容质量 15% / Agent 可调用性 20% / 可发现性 10% / 安全与信任 5%。

## 开发

```bash
npm test        # node:test 单测
npm run prepublishOnly  # 发布前自动跑测试
```

## License

MIT — 晋江市飞虹智科技企业管理有限公司 · 飞扬企源研发中心
