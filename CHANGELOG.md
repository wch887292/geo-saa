# 更新日志（Changelog）

本文件记录 GEO-SaaS 各版本的显著变更。格式参考 [Keep a Changelog](https://keepachangelog.com/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [v2.0.0] — 2026-08

首个正式稳定版（Stable）。在 v1.0.0-beta 基础上完成安全加固、版本统一与文档规范化。

### 新增（Added）
- **GEO v2 十六维评分**：在九战术（Princeton KDD 2024）基础上新增答案前置、事实密度、结构化数据、E-E-A-T、关键引语、新鲜度、一手来源等维度；关键词堆砌双条件（密度 >3% 且占用 ≥12 字符）拦截。
- **AAO 引擎**：AI Agent Optimization AX Score 六维评估（Crawlability / StructuredData / Content / Interaction / Discover / Trust），自动生成 `llms.txt` 与 `agent.json`。
- **GEO/AAO 体检工作台**：前端新增诊断视图（3 Tab），对接 4 个后端接口。
- **真实采集器**：Perplexity / OpenAI 兼容适配器 + 每日调度，失败绝不写模拟数据。

### 修复（Fixed）
- 修复 `StatisticsService` 趋势 series 可能写入 null 导致前端图表 NaN 的问题。
- 修复 `AaoEngine.jsonEscape` 未转义换行/控制字符导致 `agent.json` 非法 JSON 的问题。
- 修复 Perplexity / OpenAI 客户端响应体未判空导致的极端 NPE。
- 修复 `CollectorScheduler` 跳过时日志打印 `null` 的误导。

### 优化（Optimized）
- **依赖安全升级**：Spring Boot `3.2.0 → 3.2.12`（同系安全补丁，修 Spring Framework / Tomcat CVE）；axios 实际解析 `1.19.0`（远超低版本漏洞）。
- **版本号统一**：前端 / 后端 / geo-engine / 文档 全部对齐至 `v2.0.0`。
- **文档与署名规范化**：README / CHANGELOG / LICENSE / 社区文件统一署名为 晋江市飞虹智科技企业管理有限公司 · 飞扬企源研发中心（负责人：吴赐虹）。
- 发布 `@wch-klzx/geo-engine@2.0.0`（npm）。

> 本版本为正式稳定版，接口与数据结构已趋稳定，可用于生产环境（建议先行充分测试）。

## [v1.0.0-beta] — 2026-08

首个社区开发测试版（Community Beta）。

### 新增（Added）
- **品牌诊断（Diagnose）**：输入品牌关键词，AI 自动分析搜索表现并生成健康报告与优化建议。
- **知识库（Knowledge）**：管理品牌信息、核心关键词、产品优势，支持版本历史与回滚、JSON-LD 导出。
- **AI 内容创作（Content）**：内置科技/医疗/教育/金融/电商/法律六大行业模板，支持批量生成与敏感词过滤。
- **多渠道分发（Distribute）**：支持 150+ 渠道分发、定时调度与进度追踪。
- **数据监测（Monitor）**：搜索排名、流量、品牌声量趋势与竞品对比。
- **资产总览（Asset）**：聚合内容、知识、分发、诊断的资产视图与统计（新增真实后端接口）。
- **统计看板（Statistics）**：首页仪表盘可见性评分、内容增长、分发成功率等核心指标聚合。
- **系统管理（System）**：JWT 用户认证与权限、AI 模型配置（OpenAI / 通义千问 / 豆包）、模拟模式。
- **一键启动脚本** `start.ps1` 与 **Docker Compose** 部署（`deploy.ps1` / `docker-compose.yml`）。

### 修复（Fixed）
- 修复 `MonitorService.getCoreMetrics` 中装箱数值（`Long/Integer`）强制转型导致的 `ClassCastException`（500 错误），改为 `Number.longValue()` 安全拆箱。
- 修复前端多视图 `import` 与本地函数同名导致的 `Identifier 'x' has already been declared` 编译错误。
- 修复 element-plus 按需引入时全量 barrel 导入导致首屏包体固化在 809KB 的问题，改为按组件级分包后降至约 297KB。

### 优化（Optimized）
- 后端统一 `Result<T>` / `PageResult<T>` 响应包装，鉴权失败返回 JSON 而非重定向。
- 前端统一 `request.js` 拦截器注入 `Bearer` Token，视图直接消费 `.data` / `.total`。
- 提供端到端冒烟脚本 `smoke_test.py`，覆盖 9 视图 15 接口。

> ⚠️ Beta 阶段接口与数据结构仍可能调整，生产使用前请充分测试。
