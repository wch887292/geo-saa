# 2026-08-13 全面复盘 · 查漏补缺报告

> 项目：GEO-SaaS（geo-saa）｜范围：全仓体检 + 文档时效 + 依赖健康 + 工程化补缺

## 一、体检结论（全绿项）

| 检查项 | 结果 |
|--------|------|
| Git 工作树 / 远程同步 | ✅ 干净，`origin/main` 0/0（HEAD=`b5ab8f1`，含你的 klai.top URL 统一化提交） |
| 后端编译 + 单测 | ✅ `mvn test` BUILD SUCCESS，17/17（升级依赖前基线） |
| 前端生产构建 | ✅ `vite build` 8.36s ✓ built |
| 代码质量 | ✅ 无空 catch / System.out / printStackTrace / 硬编码密钥 |
| 密钥审计 | ✅ 源码无密钥泄漏，`.env` 已被忽略 |

## 二、发现并修复的问题

### R1（P1）APIDOC 严重失真 —— 已重写
- **问题**：原 `APIDOC.md` 路径与实现脱节（写 `/diagnose`、`/content/batch`、`/monitor/stats`，
  实际是 `/api/v1/diagnose/list` 等），且未收录 8 月以来新增的 G-01/G-02/asset/statistics 接口。
- **修复**：从 9 个 Controller 提取真实路由，重写为完整可用的接口清单（约 60 端点），
  新增 `POST /content/geo-validate`、`/asset/*`、`/statistics/dashboard`、`/monitor/core-metrics` 等，
  并附 GEO 采集器环境变量表。

### R2（P1）README 文档导航与功能模块过期 —— 已补齐
- **修复**：文档导航补 `GEO_AAO_DEV_GUIDE / RETROSPECTIVE_OPTIMIZATION / DOCKER_COMPOSE_TEST` 三项；
  功能模块补「GEO 九战术校验」「GEO 真实采集器」「资产存证」并说明数据真实性闸门。

### R3（P2）依赖健康：14 个 dependabot PR 悬置 —— 低风险已手动升级
- **已升级并验证**：
  - `hutool-all 5.8.25 → 5.8.47`（补丁级，含安全修复）
  - `mybatis-plus 3.5.5 → 3.5.17`（⚠️ 需新增 `mybatis-plus-jsqlparser` 依赖，3.5.9+ 分页插件拆分）
  - `jjwt 0.12.3 → 0.13.0`（安全相关 minor）
  - GitHub Actions：`checkout@v4→v7`、`setup-java@v4→v5`、`setup-node@v4→v7`、`setup-python@v5→v7`（CI 工具链）
- **明确跳过（大版本，需人工评估，勿盲目合并）**：
  - `spring-boot 3.2 → 4.1`（Spring Boot 4 破坏性变更极大）
  - `vite 5 → 8`、`@vitejs/plugin-vue 5→6`（构建链大版本，可能需改配置）
  - `vue-router 4 → 5`、`pinia 2 → 4`、`vue-echarts 6→8`、`echarts 5→6`（前端运行库主版本）
- **建议动作**：上述大版本 PR 保持 open，待后续专门升级分支验证；或直接在 GitHub 关闭并择机批量处理。

## 三、遗留待办（未在本轮处理）

| 编号 | 优先级 | 说明 |
|------|--------|------|
| T1 | P0 | G-01 采集器配置真实引擎（Perplexity API Key）后首次试采，验证端到端链路 |
| T2 | P2 | `frontend/Dockerfile` 镜像构建未实测（O8 待有 Docker 环境执行验证清单） |
| T3 | P2 | `WebMvcConfig` 遗留 TODO（拦截器/消息转换器）无实际需求，可清理 |
| T4 | P2 | dashboard `todos` 恒为空列表（前端有待办卡片），需数据源或下线占位 |
| T5 | P2 | 前端大版本依赖升级（vite8/router5/pinia4 等）需专门分支评估 |
| T6 | P3 | `application-dev.yml` 固定 JWT secret 仅限开发；生产已 fail-fast 强制注入，符合预期 |

## 四、版本变更速览

- `APIDOC.md` 全量重写（真实接口 + 新功能）
- `README.md` 文档导航 + 功能模块更新
- `pom.xml` 三依赖升级 + 新增 `mybatis-plus-jsqlparser`
- `.github/workflows/ci.yml` Actions 版本升级（6 处）
