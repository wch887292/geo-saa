# 路线图待办项 · 实施执行方案（Roadmap Execution Plan）

> 版本：v1.0 ｜ 日期：2026-08-29 ｜ 范围：7 项路线图待办（`docs/ROADMAP.md` 中标记未启动的项）
> 基线：Spring Boot 4.1.0 / MyBatis-Plus 3.5.17 / JWT / RabbitMQ；Vue 3.5 + Vite 8 + Element Plus + ECharts 6 + Pinia 4；MySQL 8 + Redis 7；`@wch-klzx/geo-engine`（npm 包）
> 配套文档：`README.md`、`docs/ARCHITECTURE.md`、`APIDOC.md`、`docs/ROADMAP.md`

---

## 0. 总排期建议

| # | 待办项 | 优先级 | 工作量 | 建议阶段 | 状态 |
|---|--------|--------|--------|----------|------|
| 1 | Service 层测试补全 | P0 | M | 一期 | ✅ 已达标（24→113，覆盖 Auth/Content/Knowledge/Distribute/Statistics 五大核心 Service） |
| 2 | i18n 中英切换 | P1 | S | 一期 | ✅ 已达标（后端 MessageSource+AcceptHeader+MessageUtils，前端 vue-i18n+语言切换器+登录/侧边栏/路由 i18n 化，113 测试全绿，Accept-Language 切换验证通过） |
| 3 | 可观测性（结构化日志 / Metrics） | P0 | M | 一期 | ✅ 已达标（logback-spring JSON + TraceIdFilter + BusinessMetrics 5 项 + Actuator/Prometheus，113 测试全绿） |
| 4 | 多租户 SaaS 化 | P1 | XL | 二期 | ✅ 已达标（共享库共享表+tenant_id+MyBatis-Plus TenantLineInnerInterceptor，12张业务表隔离，TenantContext ThreadLocal，JWT携带tenantId/role，超级管理员跨租户，TenantController CRUD，V2迁移脚本，113测试全绿，登录验证tenantId=1返回正常） |
| 5 | RAG 向量库 | P1 | L | 二期 | ✅ 已达标（MySQL knowledge_chunk表+tenant_id隔离，EmbeddingService(OpenAI API+SHA-256模拟模式)，TextChunker分块(500字+50重叠+句子边界)，VectorStoreService索引/检索/删除，RagService检索增强问答，RagController 6端点，V3迁移脚本，42个新测试155全绿，端到端验证语义排序正确） |
| 6 | 开放 API / Webhook | P1 | M | 二期 | ✅ 已达标（API Key认证(gk_前缀+X-API-Key头+内存限流)，/api/v1/open/ 标准化只读接口(品牌/知识/RAG/诊断/分发)，Webhook订阅管理(7种事件+HMAC-SHA256签名+指数退避重试3次+投递记录)，V4迁移脚本，34个新测试189全绿，端到端验证14项全通过） |
| 7 | 私有化信创适配 | P2 | L | 三期 | ✅ 已达标（多数据库支持：MySQL/达梦DM/人大金仓KingbaseES，Maven profile切换+自动方言检测，Docker多架构构建AMD64/ARM64飞腾鲲鹏，docker-compose信创版，一键部署脚本deploy.sh，配置外部化.env.example+application-prod.yml，数据库迁移整合init+V2+V3+V4，私有化部署指南+信创数据库适配指南，189测试全绿） |

> 一期（1~2 周）：#1 测试补全 + #3 可观测性互为保障（有 Metrics 才能发现测试未覆盖的坑），#2 i18n 工作量小、独立可并做。
> 二期（3~6 周）：#6 开放 API/Webhook（复用 MQ 基建，见效快）→ #4 多租户（全表迁移需单独立项评审）→ #5 RAG（与知识库联动，业务价值最高）。
> 三期（按需）：#7 信创适配（有明确国产化客户需求再启动，成本最高）。

---

## 1. Service 层测试补全

### 1.1 现状

- 后端现有 24 个单测，仅覆盖 4 处：`MonitorService`、`GeoContentValidator`、`AaoEngine`、`GeoDataCollectorService`。
- auth / diagnose / knowledge / content / distribute / statistics / asset / system 的 Service 无测试；Controller 无测试；无集成测试。
- CI 当前 `mvn -B test` 快速失败，但无覆盖率门槛。

### 1.2 分层策略

| 层 | 方案 | 说明 |
|----|------|------|
| 纯逻辑类（校验器/引擎/工具） | 纯 JUnit + Mockito | 沿用现有模式 |
| Service 层 | Mockito 单测（mock Mapper/RedisTemplate/RestTemplate） | 不启 Spring 上下文，保持 CI 快速失败 |
| 关键业务链路 | 集成测试（Testcontainers MySQL+Redis 或 H2） | 仅在 CI smoke 阶段启用 |

### 1.3 必补 Service 用例清单（每个 ≥3 场景：正常/边界/异常）

| Service | 关键用例 |
|---|---|
| `AuthService` | 登录成功签发 JWT；密码错误；用户锁定/失败计数；refresh；登出黑名单 |
| `ContentService` | 创建命中敏感词/关键词堆砌返回 400；GEO 校验记日志；批量生成 MQ 入队 |
| `KnowledgeService` | 品牌增删改查；版本历史/回滚；JSON-LD 生成合法 |
| `DiagnoseService` | 创建任务；进度流转；报告结构化；竞品对比 |
| `DistributeService` | 任务创建/取消状态语义（1/2/3→Constant）；151 渠道；统计聚合 |
| `StatisticsService` | 逐项 try-catch 降级；trend 不写 null（历史 NaN 回归） |
| `AssetService` | 聚合四域统计 + asset_record 独立存证；年月过滤 |
| `SystemConfigService` | 模型配置读写；intranet 模式；白名单 CRUD |
| `MonitorService` | 已有 4 例，补 trend/competitor 缓存分支 |

### 1.4 基础设施

- `src/test/java` 新增公共基类 `BaseServiceTest`（统一 Mock 初始化）、`TestDataFactory`。
- CI 接入 `jacoco-maven-plugin` 覆盖率门槛：**Service 层 ≥ 60%，全仓 ≥ 45%**，低于即构建失败。

### 1.5 验收

- 单测总数 ≥ 80；CI 绿门绑定覆盖率；`mvn -B test` 全程 < 2min。

---

## 2. i18n 中英切换

### 2.1 现状

- 前端无 i18n 依赖，路由标题/按钮/提示全中文；后端 message 全中文。
- 英文仅存在于文档层（`README_EN.md` / `FAQ_EN.md`）。

### 2.2 前端方案

- 引入 `vue-i18n@9`（兼容 Vue 3.5）；语言包 `src/locales/zh-CN.js` / `en-US.js`（key 结构一致）。
- 路由 `meta.title` 改为 i18n key（dashboard/diagnose/knowledge/content/asset/distribute/monitor/geo/system…），`document.title` 取翻译值。
- Element Plus 按语言导入 `zh-cn` / `en` locale。
- 语言持久化 `localStorage.geo-saa-lang`；默认跟随 `navigator.language`；layout 顶栏提供「中/EN」切换。
- **边界**：业务内容数据不翻译（知识条目/生成文章/诊断报告原文），仅 UI 框架文案与系统提示翻译。

### 2.3 后端方案

- `GlobalExceptionHandler` 硬编码中文 → **错误码 + `MessageSource`**：`messages.properties`（zh，默认）/ `messages_en.properties`。
- `Result<T>` 的 `message` 按请求头 `Accept-Language` 动态取词；`code` 不变。
- 新增 `LocaleInterceptor` 或复用请求头解析，注入 `LocaleContextHolder`。

### 2.4 验收

- `/login`、`/dashboard`、`/geo` 三页中英切换无漏翻、无 key 直出；后端 400/401/500 message 随语言变化；构建通过。

---

## 3. 可观测性（结构化日志 / Metrics）

### 3.1 现状

- 默认 logback 纯文本日志；无 Metrics 端点；仅 `/system/health` 作存活探针。

### 3.2 结构化日志

- 引入 `logstash-logback-encoder`；`logback-spring.xml` 增加 JSON 格式（`application-json` 分支，dev 保留可读文本）。
- 统一字段：`@timestamp`、`level`、`logger`、`message`、`traceId`、`userId`、`requestId`、`path`、`duration_ms`。
- 新增 **TraceId 过滤器**：入口生成 `traceId` 写入 MDC，贯穿 Controller→Service→Mapper→MQ 消费。
- 现有 `WARN`（采集器失败、simulation 关闭空值）保持语义，仅改格式。

### 3.3 Metrics

- 引入 `spring-boot-starter-actuator` + `micrometer-registry-prometheus`。
- `application-prod.yml` 暴露 `/actuator/prometheus`（内网或独立 token 鉴权），dev 不暴露。
- **自定义业务指标**（`MeterRegistry`）：

| 指标 | 类型 | 含义 |
|---|---|---|
| `geo_http_requests_total{path,status}` | Counter | 接口调用量与错误率 |
| `geo_ai_invoke_duration` | Timer | AI 调用耗时/P99（OpenAiAdapter 埋点） |
| `geo_collector_success_total{brand,engine}` | Counter | 采集器成功率（失败绝不写库的可观测化） |
| `geo_mq_message_total{queue,result}` | Counter | MQ 消费成功/失败 |
| JVM / HTTP 线程池 / 连接池 | 内置 | 资源水位 |

- 健康检查细化：`/actuator/health` 含 db、redis、rabbitmq 组件探针（dev 禁用 rabbitmq 时标记 UP 降级）。

### 3.4 验收

- 日志为合法 JSON、含 traceId 可按单次请求串起调用链；`/actuator/prometheus` 返回 4 类自定义指标；`/actuator/health` 分组件状态。

---

## 4. 多租户 SaaS 化

### 4.1 现状

- 单租户；`user_info` 无租户字段，无隔离。

### 4.2 数据模型

- 新增 `tenant_info` 表（id、code、name、status、quota_*、plan、created_at）。
- `user_info` 增加 `tenant_id`；`brand_info`、`ai_article_content`、`distribute_task`、`ai_diagnose_task`、`data_monitor_stat`、`asset_record` 等业务表统一增加 `tenant_id`。
- **隔离策略**：共享库 + 行级隔离（`tenant_id` 字段）——先做；独立 schema 作为远期可选项（`datasource` 路由扩展）。

### 4.3 应用层

- `TenantContext`（ThreadLocal）持有当前 tenantId；登录时从 JWT 解析写入上下文。
- **MyBatis-Plus `TenantLineInnerInterceptor`** 全局自动追加 `tenant_id = ?`（多租户正确性核心防线）。
- 租户校验过滤器：所有鉴权请求校验「用户.tenant_id == token.tenant_id」。
- 登录/注册支持租户邀请码或子域绑定；`/auth/login` 入参增加 `tenantCode`（可选）。
- 兜底：SQL 层自动注入 + Service 层二次校验（防越权）。

### 4.4 配额与计费埋点

- `tenant_info` 存配额（用户数/内容数/采集次数/API 调用），写操作前做配额检查。
- 埋点：租户维度用量统计表（为 Phase 2 计费做准备）。
- 采集器/调度任务改为「按租户遍历」，采集数据写入各自 tenant。

### 4.5 迁移

- 存量数据 `tenant_id` 补默认租户；`init.sql` 加迁移脚本；提供 `DataMigrationService` 一次性回填。

### 4.6 验收

- A/B 两租户数据完全隔离（互查不到）；注入 `tenant_id` 覆盖率 100%（SQL 日志校验）；配额超限返回可读提示。

---

## 5. RAG 向量库

### 5.1 现状

- knowledge 模块仅为品牌知识 CRUD + 版本历史，无检索增强、无向量化。

### 5.2 技术选型

| 方案 | 说明 | 结论 |
|------|------|------|
| pgvector | Docker 加 postgres 容器；HNSW 索引；SQL 兼容 | **推荐** |
| Milvus | 独立向量库，规模化能力更强 | 备选（数据量大时） |
| MySQL 8 JSON + 应用侧余弦 | 无新增组件，但 N>10 万性能受限 | 过渡方案 |

- **Embedding**：复用现有 AI 适配层（`OpenAiAdapter` / 通义 / 豆包 / OpenAI 兼容网关），配置 `app.rag.embedding-model`；无 Key 时降级关键词检索（不阻塞）。
- **切分（chunking）**：按段落/标题切分 + 重叠窗口（256~512 token），写入 `knowledge_chunk` 表（knowledge_id、seq、content、vector、hash）。

### 5.3 检索链路

- `RagService.retrieve(knowledgeId/brandId, query, topK)`：向量 Top-K + 关键词召回（like/BM25）**混合重排**（RRF 融合）。
- 检索结果注入现有 AI 创作 / 诊断 Prompt（`OpenAiAdapter` 增加 system 上下文注入点）。
- 版本联动：`knowledge_version_history` 回滚时同步重建对应 chunk（增量重算）。

### 5.4 同步任务

- 知识新增/编辑后异步触发：切分 → embedding → 入库（RabbitMQ 异步队列，复用现有 MQ 基建）。
- 失败重试 + 失败绝不写半成品向量（对齐采集器"失败不写库"原则）。

### 5.5 验收

- 10 条品牌知识入库后可语义命中（与关键词检索对比 Top-5 命中率）；知识回滚后检索结果同步更新；断网/无 Key 降级不报错。

---

## 6. 开放 API / Webhook

### 6.1 现状

- 全部接口内网 JWT；无 OpenAPI 文档、无第三方接入、无回调。

### 6.2 OpenAPI 文档

- 引入 `springdoc-openapi-starter-webmvc-ui`（SB4 兼容版本）。
- 暴露 `/swagger-ui` 与 `/v3/api-docs`，按租户/角色鉴权（prod 默认关闭或独立鉴权）。
- 现有 Controller 补 `@Tag`/`@Operation` 注解；`Result<T>` 自动生成响应结构。

### 6.3 开放 API 网关

- 新增 `api_key` / `api_app` 表（appId、appSecret、scope、status、rate_limit）。
- 鉴权过滤器：开放接口走 **AppKey + HMAC-SHA256 签名**（timestamp + nonce 防重放），独立于内部 JWT 链。
- **限流**：Redis 令牌桶按 appId 限流（复用现有 Redis 基建）。
- 首批开放接口建议：`POST /open/v1/content/geo-validate`、`POST /open/v1/geo/aao-validate`、`GET /open/v1/monitor/core-metrics`（将 GEO/AAO 引擎能力对外输出，与 npm 包互为补充）。

### 6.4 Webhook

- 事件定义（第一批）：`content.created`、`distribute.completed`、`collector.completed`、`diagnose.completed`。
- `webhook_subscription` 表（url、secret、events、status）+ 回调配置 CRUD 接口。
- **投递链路**：业务事件 → RabbitMQ `geo.event` 交换机 → `WebhookDispatcher` 异步投递（HMAC 签名、eventId 幂等、指数退避重试、死信告警）。
- 复用现有 `ContentMessageListener` / `DistributeMessageListener` 事件源。

### 6.5 验收

- 签名/重放/限流三项安全用例通过；Webhook 投递含签名、可重试、按 eventId 幂等；`/v3/api-docs` 可导出 OpenAPI JSON。

---

## 7. 私有化信创适配

### 7.1 现状

- Docker Compose（MySQL 8 / Redis 7 / RabbitMQ）+ JDK 17 + Node 18；`init.sql` 使用 MySQL 语法。

### 7.2 数据库适配

- 目标库：达梦 DM8 / 人大金仓 KingbaseES / openGauss / OceanBase / 瀚高。
- MyBatis-Plus **方言自动切换**（`DbType` 由配置驱动）；`init.sql` 中 `ENGINE=InnoDB`、`AUTO_INCREMENT`、`ON UPDATE CURRENT_TIMESTAMP` 等 MySQL 专有写法需方言化。
- 提供 `db/migration/` 各库独立 DDL；ORM 层避免原生 SQL（现有项目基本用 MyBatis-Plus 标准接口，改造面可控）。

### 7.3 OS / CPU / JDK

- 目标：麒麟（Kylin）/ 统信（UOS），x86_64 + ARM64（鲲鹏/飞腾）。
- 容器化多架构：Dockerfile 多 `--platform` 构建；JDK 可用国产发行版（毕昇 BishengJDK / 龙芯 / 腾讯 Kona / 阿里 Dragonwell），JDK 17 基线已满足。
- Redis/RabbitMQ 信创替代：麒麟自带/高斯 Redis 兼容，或降级内置调度（RabbitMQ 已可在 dev 关闭，架构已预留）。

### 7.4 前端与交付

- 国产浏览器适配（360/奇安信/红莲花/龙芯浏览器），Vite 构建 target 兼容 Chromium 内核。
- **离线交付套件**：在 `tooling/` release 脚本基础上产出「一键安装包」（docker-compose 离线镜像 + `docker load` + 初始化脚本，镜像 tar 打包）。
- **国密合规（等保/密评）**：预留 SM2/SM3/SM4 国密适配层（TLS 国密套件、JWT 可选 SM2 签名），JWT 签名算法封装为可插拔接口。

### 7.5 验收

- 麒麟 ARM64 环境跑通「后端 + 前端 + 国产库」完整链路（登录→创建内容→GEO 校验→监测）；离线安装包在无外网机器可一键拉起；`init.sql` 迁移到 DM/Kingbase 无手工改 SQL。

---

## 8. 落地顺序与里程碑

| 里程碑 | 内容 | 出口标准 |
|--------|------|----------|
| M1（一期） | #1 测试补全 + #3 可观测性 + #2 i18n | 单测 ≥80、覆盖率达标；JSON 日志 + traceId；/actuator/prometheus 出指标；三页中英切换可用 |
| M2（二期） | #6 开放 API/Webhook + #4 多租户 + #5 RAG | 开放网关安全用例通过；两租户隔离验证；RAG 语义检索 Top-5 命中 |
| M3（三期） | #7 信创适配 | 麒麟 ARM64 全链路跑通；离线安装包一键拉起 |

> 每里程碑结束跑一次全量回归（现有 15 接口冒烟 + 新增用例），全部通过方可进入下一阶段。
