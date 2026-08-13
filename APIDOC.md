# GEO-SaaS 平台 API 文档

> 本文档依据 `geo-saa-backend/src/main/java/com/geosaa/modules/**/controller` 实际实现生成（2026-08-13 校订），
> 若接口与代码不一致，以代码为准。

## 基础信息

- Base URL: `/api/v1`
- 认证方式: `Authorization: Bearer {token}`（JWT，登录后获取）
- 响应格式: `{ code: int, message: string, data: object }`，成功 `code=200`；鉴权失败 `401`；业务错误 `400/500`
- 分页接口: 通用 `{ records[], total, size, current }` 结构
- 免鉴权接口: `POST /auth/login`、`POST /auth/refresh`、`GET /system/health`

## 认证接口 `/auth`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/auth/login` | 登录，请求 `{username, password}`，响应 `{token, refreshToken, username, nickname, role, permissions}` |
| POST | `/auth/logout` | 退出登录 |
| GET  | `/auth/me` | 当前用户信息 |
| GET  | `/auth/user-info` | 当前用户详细信息 |
| POST | `/auth/refresh` | 刷新 token |
| GET  | `/auth/menus` | 动态菜单树 |

## 品牌诊断 `/diagnose`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | `/diagnose/list` | 诊断任务列表（分页，参数 `pageNum/pageSize/brandName/status`） |
| GET  | `/diagnose/{id}` | 诊断任务详情（`resultContent` 内含 `.report.score / gaps / suggestions / competitorComparison`） |
| POST | `/diagnose/create` | 发起 AI 诊断（`{taskName, taskType, brandName, inputParams}`） |
| DELETE | `/diagnose/{id}` | 删除诊断任务 |
| GET  | `/diagnose/{id}/progress` | 诊断进度 |
| GET  | `/diagnose/{id}/report` | 诊断报告（结构化） |

## 知识库 `/knowledge`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | `/knowledge/brands` | 品牌列表 |
| GET  | `/knowledge/brands/{id}` | 品牌详情 |
| POST | `/knowledge/brands` | 新增品牌 |
| PUT  | `/knowledge/brands` | 编辑品牌 |
| DELETE | `/knowledge/brands/{id}` | 删除品牌 |
| GET  | `/knowledge/brands/{brandId}/knowledge` | 品牌知识列表 |
| POST | `/knowledge/knowledge` | 新增知识 |
| PUT  | `/knowledge/knowledge` | 编辑知识 |
| DELETE | `/knowledge/knowledge/{id}` | 删除知识 |
| GET  | `/knowledge/knowledge/{id}/versions` | 知识版本历史 |
| POST | `/knowledge/auto-structure/{brandId}` | AI 自动结构化知识 |
| GET  | `/knowledge/json-ld/{brandId}` | 输出 JSON-LD（供结构化数据部署） |

## AI 创作 `/content`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | `/content/list` | 内容列表（分页，参数 `pageNum/pageSize/contentType/status`） |
| GET  | `/content/{id}` | 内容详情 |
| POST | `/content/create` | 创建内容（自动合规检测 + **GEO 九战术校验**，关键词堆砌返回 400） |
| PUT  | `/content/update` | 更新内容 |
| DELETE | `/content/{id}` | 删除内容 |
| POST | `/content/generate/{id}` | AI 生成单篇内容（同步，完成后自动 GEO 校验并记日志） |
| POST | `/content/batch-generate` | 批量生成文章（RabbitMQ 异步） |
| POST | `/content/batch-scripts` | 批量生成短视频脚本 |
| GET  | `/content/templates` | 行业模板列表（科技/医疗/教育/金融/电商/法律） |
| GET  | `/content/templates/{industry}` | 按行业取推荐模板 |
| POST | `/content/check-compliance` | 敏感词合规检测 |
| **POST** | **`/content/geo-validate`** | **★GEO 九战术内容健康度校验**：请求 `{content, keywords}`，响应 `{totalScore, blocked, redFlags, suggestions, tactics{...}}`；九战术 = 专家引语/量化数据/流畅度/引用来源/技术术语/易于理解/权威语气/独特词汇/关键词堆砌（论文依据 KDD 2024） |

## 分发 `/distribute`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | `/distribute/list` | 分发任务列表（分页） |
| GET  | `/distribute/{id}` | 任务详情 |
| POST | `/distribute/create` | 创建分发任务 |
| POST | `/distribute/{id}/cancel` | 取消任务 |
| DELETE | `/distribute/{id}` | 删除任务 |
| GET  | `/distribute/channels` | 渠道列表（150+ 渠道） |
| GET  | `/distribute/{id}/progress` | 分发进度 |
| POST | `/distribute/callback/{id}` | 渠道回调 |
| GET  | `/distribute/stats` | 分发统计（byPlatform/status 分布） |

## 数据监测 `/monitor`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | `/monitor/list` | 统计记录（分页，参数 `pageNum/pageSize/statType/startDate/endDate`） |
| GET  | `/monitor/date/{date}` | 指定日期统计 |
| POST | `/monitor/add` | 手动写入统计 |
| GET  | `/monitor/core-metrics` | 核心指标：`{mentionRate, firstRecommendRate, collectionCount, score, simulated, hasData}`；`hasData=false` 表示无真实数据（模拟模式打 `simulated` 标，生产关闭模拟时返回 0） |
| GET  | `/monitor/trend` | 趋势数据（参数 `statType/period/days`） |
| GET  | `/monitor/competitor` | 竞品对比 |
| **POST** | **`/monitor/collect`** | **★手动触发 GEO 真实采集**（G-01）：立即执行一次全品牌采集并写库；未启用/未配置时返回 `skipped` 说明不写库（需 `monitor:all` 权限） |

## 资产存证 `/asset` ★（O7 独立数据模型）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | `/asset/overview` | 资产总览：`{contentTotal, knowledgeTotal, distributeTotal, distributeSuccess, diagnoseTotal, recordedAssets, totalAssets, published, byType}`；`recordedAssets` 来自独立 `asset_record` 表 |
| GET  | `/asset/list` | 资产列表（分页，参数 `assetType=content|knowledge|distribute|diagnose|record`、`year/month`） |

## 数据看板 `/statistics` ★

| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | `/statistics/dashboard` | 仪表盘聚合：`{visibilityScore, visibilityChange, contentTotal, contentGrowth, distributeSuccess, distributeRate, rank, rankChange, todos}`（逐项 try-catch 降级） |

## 系统配置 `/system`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | `/system/health` | 健康检查（免鉴权） |
| GET  | `/system/configs` | 配置分组列表 |
| PUT  | `/system/configs` | 保存配置 |
| GET  | `/system/model-config` | AI 模型配置（openai/tongyi/doubao/simulationEnabled） |
| PUT  | `/system/model-config` | 更新模型配置 |
| GET  | `/system/intranet-mode` | 内网模式 |
| PUT  | `/system/intranet-mode` | 切换内网模式 |
| GET  | `/system/api-whitelist` | API 白名单 |
| POST | `/system/api-whitelist` | 新增白名单 |
| DELETE | `/system/api-whitelist/{id}` | 删除白名单 |
| GET  | `/system/audit-log` | 审计日志（分页） |

## 附录：GEO 真实数据采集器配置（G-01）

采集器默认**关闭**，需显式启用并配置引擎后由定时任务（默认每日 01:30）从 AI 搜索源采集真实数据写入 `data_monitor_stat`。任何引擎失败只记日志不写库，绝不写入模拟数据。

| 环境变量 | 默认 | 说明 |
|----------|------|------|
| `GEO_COLLECTOR_ENABLED` | `false` | 采集总开关 |
| `GEO_COLLECTOR_BRANDS` | 空 | 待采集品牌（逗号分隔） |
| `GEO_COLLECTOR_PROBES` | `3` | 每品牌每引擎探测次数 |
| `GEO_COLLECTOR_TIMEOUT` | `20` | 单次请求超时（秒） |
| `GEO_PERPLEXITY_ENABLED` | `false` | Perplexity 引擎开关 |
| `GEO_PERPLEXITY_API_KEY` | 空 | Perplexity API Key |
| `GEO_PERPLEXITY_MODEL` | `sonar` | Perplexity 模型 |
| `GEO_OPENAI_COMPAT_ENABLED` | `false` | OpenAI 兼容网关（通义/豆包等）开关 |
| `GEO_OPENAI_COMPAT_API_KEY` / `_API_URL` / `_MODEL` | 空 | 网关凭据与模型 |
