# 更新日志（Changelog）

本文件记录 GEO-SaaS 各版本的显著变更。格式参考 [Keep a Changelog](https://keepachangelog.com/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

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
