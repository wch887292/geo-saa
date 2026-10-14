# 更新日志（Changelog）

本文件记录 GEO-SaaS 各版本的显著变更。格式参考 [Keep a Changelog](https://keepachangelog.com/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased] — 2026-10

本轮为一次完整的落地测试评估 + 针对性加固。全部变更均经过实机验证（单元 47/47、冒烟 15/15、功能 17/18、PowerShell 验收 13/13），尚未发布，随下个版本号合并。

### 安全（Security）

- **默认管理员口令闸门**：新增 `DefaultPasswordGuard`（`ApplicationRunner`，在端口对外服务前执行）。`init.sql` 内置的管理员口令散列固定写在公开仓库中，等同凭据泄露——任何按文档部署的人都拿同一份凭据，公网扫描器可直接登录后台。现在 `prod` 档案检测到口令仍是默认值时**直接拒绝启动**并打印改密指引；`dev` 档案降级为告警，不影响本地开发。可用 `DEFAULT_PASSWORD_ACTION=disabled` 显式放行（自行承担风险）。
  - 识别方式为「字面量比对 + `PasswordEncoder.matches` 兜底」：BCrypt 每次加盐结果都不同，只比字面量会在 init.sql 重新生成散列后静默失效；兜底比对能识别换盐后的同一默认口令。空散列视为命中（比默认口令更危险）。非法散列格式不抛异常，避免误伤。
  - 选择「显式失败」而非「自动生成随机密码」：自动改密会把新密码打进启动日志，而日志通常被收集到集中式日志平台，等于把凭据换个地方再泄露一次，且用户往往注意不到。
- **改密助手** `scripts/change_password.sql`：给出 BCrypt 散列的本地生成方式（明文不经过任何在线服务与 CI 日志）与执行命令。

### 修复（Fixed）

- **批量生成接口 500**：dev 档案的 AMQP 自动配置 exclude 沿用 Spring Boot 3 的旧全限定名，在 Boot 4.1 下该类已不存在，Spring 静默忽略无效 exclude 导致 `RabbitTemplate` 仍被创建；本机无 RabbitMQ 时 `convertAndSend` 抛 `AmqpConnectException`，`POST /api/v1/content/batch-generate` 与分发接口直接 500。改用 Boot 4 正确类名，并在 Service 层捕获 `AmqpException` 后本地降级执行（不再留下PENDING 孤儿记录）。实测返回 200。
- **唯一约束冲突返回 500**：重复创建同名品牌时原始 SQL 异常未被友好包装，前端只能显示「服务器内部错误」。`GlobalExceptionHandler` 新增 `DataIntegrityViolationException` → 400 +「记录已存在，请勿重复提交」。
- **鉴权失败仍返回 HTTP 200**：登录失败时 HTTP 状态码是 200、错误码只在 body 里，导致监控与网关无法识别爆破行为。改为真正的 HTTP 401。
- **登录无防爆破**：新增 IP 维度滑动窗口限流（20 次/15 分钟）。原实现只按用户名计数，既挡不住「同一批密码遍历大量用户名」的撞库，反过来只要知道某个存在的用户名就能 5 次错误把账号锁死 30 分钟。IP 配额独立于账号锁定，只在登录成功时回收（否则攻击者可用一次成功登录重置撞库进度）。
- **资产统计口径混淆**：`/api/v1/asset/overview` 的 `knowledgeTotal` 统计的是 `brand_knowledge` 表（知识库条目），但字段名容易被理解成品牌数——新增品牌后该值仍为 0 而 `totalAssets` 有值，看起来像 bug。新增 `brandTotal` 字段独立返回 `brand_info` 表计数，`byType` 同步增加 `brand` 键，`totalAssets` 语义不变。

### 新增（Added）

- **环境就绪预检** `scripts/preflight.sh` / `preflight.ps1`：部署前一次性列出缺什么、为什么缺、怎么补——Docker 是否安装、**daemon 是否启动**（最常见的坑）、端口占用、`.env` 必填项、JWT 强度（Base64 解码后 ≥ 32 字节）、CORS 通配符、默认口令守卫状态、磁盘与内存余量。支持 `--docker`（默认）/ `--local`（JDK/Maven/Node/MySQL/Redis 源码模式）/ `--all`。退出码非 0 表示未就绪。
- **PowerShell 部署与验收脚本** `scripts/deploy.ps1` / `healthcheck.ps1`：此前 Windows 用户必须装 Git Bash 或 WSL 才能用 bash 脚本。两个新脚本与 bash 版行为等价，含随机密钥生成、`.env` ACL 收紧、健康轮询、以及对已修复缺陷的回归验证。
- **回归测试 4 → 47 项**：新增 `ContentServiceBatchGenerateTest`、`GlobalExceptionHandlerTest`、`AuthServiceRateLimitTest`、`AssetServiceOverviewTest`、`DefaultPasswordGuardTest` 等，覆盖批量生成降级、异常状态码映射、登录限流、资产统计口径、默认口令守卫（含换盐识别、空散列、非法散列容错、空库跳过）。

### 变更（Changed）

- **部署文档去除「待验证」自述**：`docs/DOCKER_COMPOSE_TEST.md` 原开头写「状态：待环境验证」，会让读者质疑整条部署路径。改为准确区分「已验证」与「待验证」两个层次——编排配置已通过静态审查 + CI 构建，后端全链路已在等价配置（MySQL 8 + Redis 5，非容器）实测通过；唯一未覆盖的是 `docker compose up` 本身，并给出执行后的回填指引。
- **重复的部署脚本收敛**：根目录 `deploy.sh` / `deploy.ps1` 改为转发到 `scripts/` 下的规范版本。旧版会先 `mvn package` 再交给 compose 重复构建一次（白白多花几分钟），不生成 `.env`（`JWT_SECRET` 缺失导致后端 fail-fast 起不来），固定用 docker-compose v1，且 `sleep 15` 就宣称「部署完成」而不检查健康状态。
- **版本标注对齐实际**：`README` / `README_EN` 通篇写 Spring Boot 3.2、Redis 7，实际 `pom.xml` 为 Spring Boot 4.1、Redis 5+。
- **默认口令的文档表述统一**：README / README_EN / FAQ_EN / 部署指南统一标注为「仅本地演示，生产必须改密」，并链接到改密步骤。

### 修复之外的一处观察

`init.sql` 的默认口令散列与早期文档中标注的明文曾存在不一致（README 写 `admin/admin123`，另一处注释写不同值）。实测确认 init.sql 中散列对应的明文为 `admin123`，所有文档已按此统一。

## [Unreleased] — 2026-08

本轮为工作区修复 + 系统性优化（尚未发布，随下个版本号合并）。

### 修复（Fixed）
- **恢复 8 个被误删的配置类**：`SecurityConfig`（JWT 安全链 / 方法级鉴权 / 统一 JSON 401/403）、`RabbitMqConfig`（队列/交换机声明，被两个 MQ 监听器与 Service 引用）、`AsyncConfig`、`CorsConfig`、`RedisConfig`、`MybatisPlusConfig`（分页拦截器）、`PasswordEncoderConfig`。此前这些类被删除但代码仍引用，后端无法编译、认证/跨域/异步/分页全部失效；`WebMvcConfig` 为空壳且无引用，保持删除。
- **统一任务状态语义**：`ContentMessageListener`、`DistributeMessageListener`、`DistributeService.cancelTask` 硬编码的 `1/2/3` 改为引用 `Constant.TASK_STATUS_*`，消除与 `Constant` 两套状态语义的隐患。
- **敏感词库去占位化**：`ContentService` 中写死的占位词（“敏感词1/敏感词2”）改为可通过 `app.content.sensitive-words` 配置的真实默认词表。
- **`.env` 变量名对齐**：`RABBITMQ_PASS` → `RABBITMQ_PASSWORD`（与 docker-compose / 后端读取一致，需在部署机上同步修改本地 `.env`）。

### 优化（Optimized）
- **CORS 挂入 Spring Security 链**：`CorsConfig` 暴露 `CorsConfigurationSource` Bean，`SecurityConfig` 通过 `.cors(Customizer.withDefaults())` 启用，修复跨域预检（OPTIONS）被安全链以 401 拒绝的问题。
- **异步线程池单例化**：`AsyncConfig.getAsyncExecutor()` 不再每次调用新建线程池实例，未指定执行器的 `@Async` 方法（如审计日志）统一复用同一线程池。
- **共享 ObjectMapper**：`OpenAiAdapter` 改为注入 `JacksonConfig` 提供的共享 Jackson 2 `ObjectMapper`，不再各自 `new` 独立实例。
- **清理 Spring Boot 4 死配置**：移除 Boot 3.2 起已不生效的 `spring.mvc.throw-exception-if-no-handler-found`（404 由 `GlobalExceptionHandler` 统一处理）。

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
- **Spring Boot 4 升级**：parent `3.2.12 → 4.0.7`（Spring Framework 7.0.8 / Tomcat 11.0.22 / Jackson 3.x）；MyBatis-Plus starter 切至 `mybatis-plus-spring-boot4-starter`；`spring-boot-starter-aop` 替换为 SB4 新名 `spring-boot-starter-aspectj`；`RestTemplateConfig` 去掉被移除的 `RestTemplateBuilder`，改为手动 `SimpleClientHttpRequestFactory` 配置超时；新增 `JacksonConfig` 显式注册 Jackson 2 `ObjectMapper` bean，与 SB4 自动配置的 Jackson 3 共存（避免 Jackson 3.x 包名迁移导致的注入缺失）。运行时验证：`mvn test` 24/24 通过；冒烟 15 接口 15 通过；后端在 JDK 17 下编译/运行正常，无需 Java 21。
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
