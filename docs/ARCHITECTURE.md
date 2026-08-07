# 系统架构（Architecture）

> 本文档帮助开发者快速理解 GEO-SaaS 的整体结构、技术选型与数据流，便于二次开发与部署。

## 1. 总体架构

GEO-SaaS 采用**前后端分离**架构：

```
┌─────────────────┐      HTTPS /api/v1       ┌──────────────────────────┐
│   前端 (Vue 3)   │ ───────────────────────▶ │   后端 (Spring Boot 3)   │
│  Vite + Element  │ ◀─────────────────────── │  JWT 认证 + 业务模块      │
│  Pinia + ECharts │      JSON {code,data}     └────────────┬─────────────┘
└─────────────────┘                                         │
                                                          │
                              ┌──────────────┼──────────────┐
                              ▼              ▼              ▼
                        ┌──────────┐  ┌──────────┐  ┌──────────┐
                        │  MySQL  │  │  Redis   │  │ AI 模型   │
                        │  8.0     │  │  7       │  │ OpenAI等 │
                        └──────────┘  └──────────┘  └──────────┘
                                              (可选，模拟模式可省)
```

- 前端通过 Vite 代理将 `/api` 转发至后端 `:8080`。
- 后端所有接口统一返回 `{ code, message, data }`，业务成功 `code=200`。
- 除登录/刷新/健康检查外，所有请求需携带 `Authorization: Bearer <JWT>`。

## 2. 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 + Vite + Element Plus + ECharts + Pinia + Vue Router |
| 后端 | Spring Boot 3.2 + Spring Security + MyBatis-Plus |
| 存储 | MySQL 8.0（业务数据）+ Redis 7（会话/缓存/限流） |
| 构建 | Maven 3.9 + npm |
| 部署 | Docker / Docker Compose（含 Nginx 反向代理） |

## 3. 后端模块划分

位于 `com.geosaa.modules` 下，按业务垂直拆分：

| 模块 | 职责 | 关键接口前缀 |
|------|------|--------------|
| `auth` | 登录、JWT 签发/刷新、动态菜单、用户信息 | `/auth` |
| `diagnose` | AI 品牌诊断、进度、报告、竞品对比 | `/diagnose` |
| `knowledge` | 品牌知识库、版本历史、JSON-LD | `/knowledge` |
| `content` | AI 内容创作、批量生成、模板、导出 | `/content` |
| `distribute` | 多渠道分发任务、渠道列表、重试 | `/distribute` |
| `monitor` | 核心指标、趋势、竞品监测 | `/monitor` |
| `asset` | 资产总览聚合（内容/知识/分发/诊断统计） | `/asset` |
| `statistics` | 首页仪表盘聚合指标 | `/statistics` |
| `system` | 系统配置、AI 模型配置、审计日志 | `/system` |

**公共能力**（`com.geosaa` 根包）：
- `config/`：CORS、Security、Async、RestTemplate、PasswordEncoder
- `security/`：`JwtTokenProvider`、`JwtAuthenticationFilter`、`CustomUserDetailsService`、`TokenBlacklistService`（登出黑名单）、`LoginUser`、`SecurityUtils`
- `common/`：统一响应 `Result<T>` / `PageResult<T>`、异常处理器
- `ai/`：AI 适配器（OpenAI / 通义千问 / 豆包），支持模拟模式

## 4. 关键数据流

### 4.1 认证流程
1. `POST /auth/login` → 校验账号密码 → 写 Redis 登录失败计数与 refresh token → 返回 JWT。
2. 后续请求经 `JwtAuthenticationFilter` 解析 Token，注入 `SecurityContext`。
3. 登出将 Token 加入 Redis 黑名单（`TokenBlacklistService`）。

### 4.2 AI 调用（模拟模式）
- `ai.simulation.enabled=true` 时，适配器直接返回内置模拟数据，**无需任何 API Key**。
- 关闭后走真实模型 HTTP 调用（`RestTemplateConfig` 配置超时）。

### 4.3 数据监测缓存
- `MonitorService` 使用 Redis 缓存核心指标/趋势/竞品结果，降低重复计算。

## 5. 目录结构（精简）

```
geo-saa/
├── start.ps1 / deploy.ps1      # 本地 / Docker 启动脚本
├── docker-compose.yml          # 编排 mysql/redis/backend/frontend
├── geo-saa-backend/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/geosaa/   # 见上文模块划分
│       └── resources/
│           ├── application.yml / -dev.yml / -prod.yml
│           └── db/init.sql    # 建库建表与初始数据
└── geo-saa-frontend/
    ├── src/
    │   ├── api/               # 接口封装（request.js 统一注入 Token）
    │   ├── views/             # 页面（dashboard/diagnose/knowledge/...）
    │   ├── router/ store/ components/
    ├── vite.config.js         # 代理 / 构建分包
    └── nginx/default.conf     # 生产部署配置
```

## 6. 部署拓扑（Docker）

```
浏览器 ──▶ Nginx(:80) ──▶ frontend 静态资源
                     └──▶ /api ──▶ backend(:8080) ──▶ MySQL / Redis
```

详见 [README](README.md) 的「Docker 部署」章节。
