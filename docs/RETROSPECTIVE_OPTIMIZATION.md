# geo-saa 复盘优化报告（Retrospective & Optimization）

> 版本：v1.0 ｜ 日期：2026-08-12 ｜ 范围：8/6–8/12 交付物体检 + 缺陷修复 + 优化 Backlog
> 配套文档：`docs/GEO_AAO_DEV_GUIDE.md`（GEO/AAO 战略开发指南）

---

## 一、复盘背景

8/6–8/12 已完成的工作链：

1. 端到端冒烟测试 **15/15 全绿**（9 视图 15 接口）
2. GitHub 就绪：声明文件（CODE_OF_CONDUCT / CONTRIBUTING / SECURITY / CHANGELOG / .gitattributes）+ 热门文档（ARCHITECTURE / ROADMAP）
3. 推送 GitHub（分支保护 + 仓库元数据 + Community 100%）
4. CI 流水线 + dependabot
5. GEO/AAO 战略开发文档

本次「复盘优化」对已交付物做一次**真实体检**，把"可用"推向"可对外交付/可维护"。

---

## 二、项目体检结果（事实，非推测）

| 编号 | 维度 | 发现 | 严重度 |
|------|------|------|--------|
| **F1** | 数据真实性 | `MonitorService` 在无数据库统计时**硬编码 `Math.random()`** 冒充"AI 引用率实测"，`score` 正由随机数算出（第 82/92/102 行）。且**未读取 `ai.simulation.enabled` 配置**，配置项形同虚设。 | **P0** |
| **F2** | 构建可复现 | 前端无 `package-lock.json` / `pnpm-lock.yaml` / `yarn.lock`，CI 用 `npm install`，依赖树不可锁定、不可复现。 | P1 |
| **F3** | 测试覆盖 | 仅 `smoke_test.py`（15 接口连通性），**零单元测试 / 集成测试**；核心算法（score 公式、装箱拆箱）无保护网。 | P1 |
| **F4** | 仓库卫生 | `__pycache__/`（py_compile 垃圾）未忽略且未提交；`docs/GEO_AAO_DEV_GUIDE.md` 已写未入库。 | P1 |
| **F5** | 薄模块 | `asset` / `statistics` 模块仅 `Controller` + `Service`，**无独立 entity/mapper**，依赖其他域兜底聚合。 | P2 |
| **F6** | 演示/生产混淆 | 模拟数据无 `simulated` 标识，前端无法区分"真测量"还是"演示"，存在对外误导风险。 | P0（与 F1 同源） |

---

## 三、本轮已实施的优化（已交付并通过编译）

### ✅ O1 — P0 数据真实性防护（已修复）
`MonitorService` 接入 `ai.simulation.enabled` 配置闸门：
- **演示模式（默认 `true`）**：仍出数，但返回体携带 `simulated: true` + `hasData` 标识，前端可显式标注"演示数据"；
- **生产模式（`ai.simulation.enabled=false`）**：DB 无数据时返回**真实空值**（`hasData: false`、`score=0`），绝不再用随机数冒充测量值；并在日志 `WARN` 提示。
- 修复后 `mvn compile` → **BUILD SUCCESS**。

### ✅ O3 — P1 依赖可复现（已修复）
生成并提交前端 `package-lock.json`（37KB），CI 可由 `npm install` 升级为 `npm ci` 锁定版本。

### ✅ O4 — P1 仓库卫生（已修复）
`.gitignore` 增加 `__pycache__/` + `*.pyc`；提交此前遗漏的 GEO 战略文档。

---

## 四、待办优化 Backlog（分级，建议后续排期）

### P0 — 上线/对外交付前必须完成
- **G-01 真实采集器**：从 AI 引擎（Perplexity / 秘塔 AI 搜索 / 天工 AI / Google AI Overviews）采集真实 `mentionRate / firstRecommendRate / collectionCount`，写入 `DataMonitorStat`。这是**根治 F1 的根本手段**，需确定数据源策略（官方 API / 合规爬虫 / 人工导入）。
- **G-02 九战术校验器**：依据 Princeton KDD 2024（arXiv:2311.09735）九大战术，校验内容是否覆盖"专家引述(+41%) / 统计(+33%) / 引用源(+28%)"，输出 GEO 健康度建议（**关键词堆砌 −10% 须拦截**）。

### P1 — 工程健壮性
- **O5 核心算法单元测试**：覆盖 score 公式、装箱 `Number.longValue()` 拆箱边界、simulation 开关分支。
- **O6 CI 升级 `npm ci`**：lock 已就绪，将 `ci.yml` 前端步骤改为 `npm ci`。

### P2 — 架构与运维
- **O7 asset/statistics 独立数据模型**：按产品是否需要"资产存证 / 统计聚合"独立实体决定是否补 entity+mapper（当前兜底可用）。
- **O8 docker-compose 实测**：本地拉起 MySQL+Redis 联合验证部署链路。

---

## 五、量化收益

| 指标 | 优化前 | 优化后 |
|------|--------|--------|
| 数据真实性 | 随机冒充实测 | 模拟可标识 + 生产真实空值 |
| 构建可复现 | 无 lock | 锁定依赖树（npm ci 可用） |
| 仓库卫生 | 编译垃圾可能入库 | `__pycache__` 已忽略 |
| 对外误导风险 | 高（演示=实测） | 低（显式 simulated 标识） |

---

## 六、下一步建议（三选一或组合）

- **(a)** 提交并推送本轮成果（`MonitorService` 修复 + `package-lock.json` + 复盘文档 + `.gitignore`）
- **(b)** 启动 **G-01 真实采集器** 技术方案与代码骨架（根治 F1）
- **(c)** 补 **O5 核心算法单元测试** + **O6 CI `npm ci`**

> 说明：O1 已消除"随机数冒充实测"的致命风险，但仅为**防护层**；要让平台真正产生价值，必须落地 G-01 真实数据采集。
