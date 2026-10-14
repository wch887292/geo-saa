> **English / 英文文档**：[README_EN.md](README_EN.md) · [FAQ (English)](FAQ_EN.md)

# GEO-SaaS 全域AI搜索优化平台

<div align="center">

[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Version](https://img.shields.io/badge/Version-v2.0.0-blue.svg)](https://github.com/wch887292/geo-saa/releases/tag/v2.0.0)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen.svg)]()
[![Vue](https://img.shields.io/badge/Vue-3-4FC08D.svg)]()
[![CI](https://github.com/wch887292/geo-saa/actions/workflows/ci.yml/badge.svg)](https://github.com/wch887292/geo-saa/actions/workflows/ci.yml)
[![Docker](https://github.com/wch887292/geo-saa/actions/workflows/docker.yml/badge.svg)](https://github.com/wch887292/geo-saa/actions/workflows/docker.yml)

</div>

基于 AI 驱动的全域搜索引擎优化平台，提供品牌诊断、知识库管理、AI内容创作、多渠道分发和数据监测等一站式解决方案。

> **English:** GEO-SaaS is an AI-driven **Generative Engine Optimization (GEO)** platform that helps brands improve visibility across AI search and traditional search engines — covering brand diagnosis, knowledge base, AI content creation, multi-channel distribution, and monitoring in one place. No real AI API key required for a quick trial (built-in simulation mode).

## 🌐 官方站点与关联开源项目

本仓库由 **晋江市飞虹智科技企业管理有限公司 · 飞扬企源研发中心** 维护（负责人：吴赐虹），是飞虹智 klAI 开源生态的一部分。

- 🏠 **官方网站**：[https://www.klai.top](https://www.klai.top) — 飞虹智 klAI · 泉州制造业 AI 服务商
- 📦 **开源矩阵**：[https://www.klai.top/opensource.html](https://www.klai.top/opensource.html) — 全部开源项目一览
- 📚 **AI 知识库**：[https://kb.klai.top](https://kb.klai.top) — 产品文档与智能问答（MaxKB 驱动）

**关联项目**：

| 项目 | 简介 |
|------|------|
| [GEO-SaaS](https://github.com/wch887292/geo-saa) | AI 驱动的全域 GEO 搜索优化平台（本仓库） |
| [飞虹智·企业AI平台](https://github.com/wch887292/fyqy-ai-agent) | 中小制造企业 AI 原生一体化管理平台 |
| [FyqyClaw](https://github.com/wch887292/FyqyClaw) | 全流程 AI 驱动开发工具（IDE + AI Agent） |
| [星眠AI](https://github.com/wch887292/xmai) | 睡眠健康管理微信小程序 + 私有部署后端 |

> ⭐ 如果这个项目对你有帮助，欢迎 **Star** 并分享，让更多人发现飞虹智开源生态！


## 📚 文档导航

| 文档 | 说明 |
|------|------|
| [架构说明](docs/ARCHITECTURE.md) | 系统架构、模块划分与数据流 |
| [开发路线图](docs/ROADMAP.md) | 未来规划与社区计划 |
| [GEO/AAO 开发指南](docs/GEO_AAO_DEV_GUIDE.md) | 三代搜索优化（SEO/AEO/GEO/AAO）战略与产品映射 |
| [复盘优化报告](docs/RETROSPECTIVE_OPTIMIZATION.md) | 体检发现与工程化优化记录 |
| [Docker 实测指南](docs/DOCKER_COMPOSE_TEST.md) | Compose 一键部署验证清单 |
| [依赖升级评估](docs/DEPENDENCY_ASSESSMENT.md) | 前端大版本升级风险与验收标准 |
| [GEO 采集器试采手册](docs/GEO_COLLECTOR_RUNBOOK.md) | 配真实 Key 后 3 分钟完成首次试采 |
| [npm 发布手册](docs/NPM_PUBLISH_GUIDE.md) | @feihong/geo-engine 发布流程与 token 配置 |
| [API 文档](APIDOC.md) | 接口清单与字段说明 |
| [贡献指南](CONTRIBUTING.md) | 如何提 Issue / Pull Request |
| [行为准则](CODE_OF_CONDUCT.md) | 社区行为公约 |
| [安全策略](SECURITY.md) | 漏洞报送与安全配置提醒 |
| [更新日志](CHANGELOG.md) | 版本变更记录 |

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 + Vite + Element Plus + ECharts + Pinia |
| 后端 | Spring Boot 4.1 + Spring Security + MyBatis-Plus |
| 数据库 | MySQL 8.0 + Redis 5+ |
| 构建 | Maven 3.9 + npm |
| 运行环境 | JDK 17 + Node.js 20.19+ / 22.12+ |

## 快速开始（本地开发）

### 前置条件

- JDK 17（推荐 Microsoft Build of OpenJDK）
- Node.js 20.19+ 或 22.12+（Vite 8 要求；更低版本构建时报 crypto.hash 不可用）
- MySQL 8.0（运行中，端口 3306）
- Redis 5+（运行中，端口 6379）
- Maven 3.9（可选，脚本会自动处理）

### 一键启动

在项目根目录执行 PowerShell 脚本：

```powershell
.\start.ps1
```

脚本会自动完成以下步骤：
1. 检查 JDK 17、Node.js、MySQL、Redis 环境
2. 构建后端 JAR 包
3. 启动后端服务（端口 8080）
4. 安装前端 npm 依赖
5. 启动前端开发服务器（端口 3000）
6. 打开浏览器

### 手动启动

#### 1. 初始化数据库

确保 MySQL 已运行，执行初始化脚本：

```sql
source geo-saa-backend/src/main/resources/db/init.sql
```

默认会创建 `geo_saa` 数据库并初始化表结构和默认数据。

#### 2. 启动后端

```powershell
# 构建
cd geo-saa-backend
mvn clean package -DskipTests

# 启动
java -jar target/geo-saa-backend.jar --spring.profiles.active=dev
```

后端将在 `http://localhost:8080` 启动。

#### 3. 启动前端

```powershell
cd geo-saa-frontend
npm install
npx vite --host
```

前端将在 `http://localhost:3000` 启动，Vite 自动将 `/api` 请求代理到后端。

### 访问系统

- 前端地址: `http://localhost:3000`
- 后端地址: `http://localhost:8080`
- 默认管理员: `admin` / `admin123`（仅限本地开发，见 [Docker 部署](#docker-部署)的改密说明）

## Docker 部署

### 前置条件

- Docker 24+
- Docker Compose 2+

### 第0 步：环境预检（别跳过）

```bash
bash scripts/preflight.sh          # Linux/macOS/Git Bash
.\scripts\preflight.ps1           # Windows PowerShell
```

一次性列出缺什么：Docker 是否安装、**daemon 是否启动**、端口是否被占、`.env` 是否齐全、
JWT 强度够不够。90% 的"部署失败"都是这几类原因，先跑这步能省掉大量试错。

### 第 1 步：首次部署必须改密 ⚠️

`init.sql` 注入的管理员 `admin / admin123`，其 BCrypt 散列**固定写在公开仓库里**，
等同于凭据泄露。后端在生产档案下会检查数据库口令是否仍是默认值，**是则拒绝启动**。
**这一步不做，容器起不来**——这不是可选项。

```bash
# 0) 先只起 MySQL 并等它就绪（改密要连库；后端起不来是闸门的预期行为，不用管）
docker compose up -d mysql
docker compose ps mysql        # Status 出现 healthy 再继续

# 1) 生成 BCrypt 散列（明文只留在你的终端；需先 pip install bcrypt）
python -c "import bcrypt;print(bcrypt.hashpw(b'你的新密码',bcrypt.gensalt(10)).decode())"

# 2) 填进 scripts/change_password.sql 的 <NEW_BCRYPT_HASH>，然后执行
docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" geo_saa' \
  < scripts/change_password.sql
```

仅本地演示、明确知道风险时，可在 `.env` 里设 `DEFAULT_PASSWORD_ACTION=disabled` 放行。

### 第 2 步：启动

```bash
bash scripts/deploy.sh          # Linux/macOS/Git Bash（自动生成随机密钥并校验）
.\scripts\deploy.ps1                # Windows PowerShell
```

或手动执行（需自行准备 `.env`）：

```bash
cp .env.example .env    # 按注释填写 JWT_SECRET / MYSQL_PASSWORD 等
docker compose up -d --build
```

### 第 3 步：验收

```bash
bash scripts/healthcheck.sh         # 或 .\scripts\healthcheck.ps1
```

覆盖容器状态、健康端点、登录、鉴权边界、核心业务，并回归验证已修复的缺陷
（批量生成不再 500、重复提交返回 400、错误密码返回 401）。

### 访问

| 服务 | 地址 |
|------|------|
| 前端 | `http://localhost` |
| 后端 API | `http://localhost:8080` |
| RabbitMQ 管理 | `http://localhost:15672` (guest/guest) |
| 默认管理员 | `admin` / `admin123` — **仅本地演示，生产必须改密** |

## 项目结构

```
geo-saa/
├── start.ps1                  # 一键启动脚本（本地开发）
├── deploy.sh / deploy.ps1     # 兼容入口，转发到 scripts/deploy.*
├── docker-compose.yml         # Docker Compose 编排
├── scripts/                   # 部署与验收脚本
│   ├── preflight.sh / .ps1    # 环境就绪预检（部署前先跑）
│   ├── deploy.sh / .ps1       # 一键部署（生成随机密钥 + 健康等待）
│   ├── healthcheck.sh / .ps1  # 部署验收（含已修复缺陷回归）
│   └── change_password.sql    # 首次部署改密助手
├── geo-saa-backend/           # 后端服务
│   ├── pom.xml
│   ├── docker/
│   │   └── Dockerfile
│   └── src/main/
│       ├── java/com/geosaa/
│       │   ├── GeoApplication.java        # 启动类
│       │   ├── config/                     # 配置类
│       │   ├── security/                   # JWT 安全认证
│       │   ├── common/                     # 公共工具
│       │   ├── ai/                         # AI 适配器
│       │   └── modules/
│       │       ├── auth/                   # 认证模块
│       │       ├── diagnose/               # 品牌诊断
│       │       ├── knowledge/              # 知识库
│       │       ├── content/                # AI 内容创作
│       │       ├── distribute/             # 多渠道分发
│       │       └── monitor/                # 数据监测
│       └── resources/
│           ├── application.yml             # 主配置
│           ├── application-dev.yml         # 开发环境配置
│           └── db/init.sql                 # 数据库初始化
├── geo-saa-frontend/          # 前端服务
│   ├── package.json
│   ├── Dockerfile
│   ├── vite.config.js
│   ├── nginx/
│   │   └── default.conf        # Nginx 部署配置
│   └── src/
│       ├── api/                 # API 请求封装
│       ├── views/               # 页面视图
│       │   ├── dashboard/       # 仪表盘
│       │   ├── diagnose/        # AI 品牌诊断
│       │   ├── knowledge/       # 知识库管理
│       │   ├── content/         # AI 内容创作
│       │   ├── distribute/      # 分发管理
│       │   ├── monitor/         # 数据监测
│       │   └── system/          # 系统设置
│       ├── router/              # 路由配置
│       ├── store/               # 状态管理
│       └── components/          # 公共组件
```

## 功能模块

### 1. 品牌诊断
- 输入品牌关键词，AI 自动分析搜索引擎表现
- 生成 SEO 健康报告和优化建议

### 2. 知识库管理
- 管理品牌信息、核心关键词、产品优势
- 知识版本历史追踪

### 3. AI 内容创作
- 多行业模板支持（科技、医疗、教育、金融、电商、法律）
- 批量生成 AI 优化文章
- 敏感词过滤
- **GEO 九战术健康度校验**（`POST /content/geo-validate`）：按 Princeton KDD 2024 论文九大战术
  （专家引语 / 量化数据 / 引用来源 / 流畅度 / 技术术语等）加权评分；
  **关键词堆砌自动拦截**（论文实测可见度 −8%~−10%），创建内容时返回 400

### 4. 多渠道分发
- 支持 150+ 渠道分发
- 定时任务调度
- 进度追踪

### 5. 数据监测
- 搜索排名监控
- 流量分析
- 品牌声量趋势
- 核心指标含**数据真实性闸门**：`hasData=false` 时不再用随机数冒充
  （模拟模式打 `simulated` 标，生产关闭模拟返回真实空值）
- **GEO 真实数据采集器**（可配 Perplexity / OpenAI 兼容网关，每日自动采集
  mentionRate/首推率/收录量写入统计表，失败绝不写入模拟数据）

### 6. 资产存证
- 独立 `asset_record` 数据模型（内容/知识/分发/诊断聚合 + 独立存证双视图）
- 按年/月过滤、分页浏览品牌资产

### 7. 系统管理
- 用户认证与权限管理
- AI 模型配置（OpenAI / 通义千问 / 豆包）
- 模拟模式（无需 API Key 即可体验）

## 配置说明

### AI 模型配置

在 `application.yml` 中配置 AI 模型：

```yaml
ai:
  openai:
    api-key: "sk-xxx"           # OpenAI API Key
    api-url: https://api.openai.com/v1
    model: gpt-4
  tongyi:
    api-key: "sk-xxx"           # 通义千问 API Key
  doubao:
    api-key: "sk-xxx"           # 豆包 API Key
  simulation:
    enabled: true               # 模拟模式，无 API Key 时返回模拟数据
```

### 开发模式

- RabbitMQ 默认禁用，不影响核心功能
- 数据库开发凭据: `root` / `root`
  - 来源是 `.env` 的 `MYSQL_USER` / `MYSQL_PASSWORD`，`.env.example` 已预置这组开发默认值，
    `cp .env.example .env` 后可直接启动。
  - **这组值只适用于本地开发**。生产部署前必须改成强口令：compose 用的是
    `${MYSQL_PASSWORD}` 无默认值语法，改完 `.env` 后重启 `mysql` 容器生效。
  - 注意 `application-dev.yml` **不包含 datasource 段**（无本地 MySQL 时用 `.env` 即可）；
    datasource 只在 `application-prod.yml` 中配置，且全部为 `${...}` 变量引用、缺失即启动失败。
- 日志级别: `com.geosaa: debug`

## 常见问题

### Q: 启动报错 "端口 8080 已被占用"
A: 使用脚本 `start.ps1` 会自动释放端口，或手动执行：
```powershell
netstat -ano | findstr ":8080 "
Stop-Process -Id <PID> -Force
```

### Q: 数据库连接失败
A: 先确认 MySQL 容器已启动（`docker compose ps`），再核对 `.env` 里的
`MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_USER` / `MYSQL_PASSWORD`。
用 Docker 时 `MYSQL_HOST` 应为 `mysql`（服务名）而非 `localhost`；
连本机 MySQL 才用 `localhost`，此时需在 `application-dev.yml` 的
`spring.datasource` 段补充配置——该段默认不存在，属刻意留空。

### Q: 前端无法访问后端 API
A: 检查 Vite 代理配置 `vite.config.js`，确保 `target` 指向正确的后端地址。

### Q: 不需要 RabbitMQ
A: 开发模式下 RabbitMQ 已禁用（`application-dev.yml` 中配置），不影响正常使用。


---

## 🤝 社区支持

关注飞虹智 klAI 动态，获取最新开源项目更新与技术教程：

![社区支持二维码](https://github.com/geo-saa/releases/download/v1.0.0-community/qrcode-community.png)

扫码加入 **飞虹智企微小助手**，获取：
- 技术答疑与部署指导
- 开源项目更新通知
- 本地化服务预约（泉州地区）
- 企业 AI 数字化咨询

---

*晋江市飞虹智科技企业管理有限公司 · 飞扬企源研发中心 · 负责人：吴赐虹*

## 开源许可证

本项目采用 [MIT License](LICENSE) 开源。欢迎通过 [贡献指南](CONTRIBUTING.md) 提交 Issue 与 Pull Request，共建社区。

---

⭐ 如果这个项目对你有帮助，欢迎 Star 与分享！

---

## 组织与署名

<!-- FYQY-ENTITY-BLOCK v1 -->

**公司**：晋江市飞虹智科技企业管理有限公司
**中心**：飞扬企源研发中心
**负责人**：吴赐虹

- 官网：https://www.klai.top
- 知识库：https://kb.klai.top
- 开源矩阵：https://github.com/wch887292
- 实体权威声明（机器可读）：https://www.klai.top/entity.jsonld

> 本项目版权归 晋江市飞虹智科技企业管理有限公司 所有，由 飞扬企源研发中心 研发交付。
> Entity: `https://www.klai.top/#organization` · Author: `https://www.klai.top/#person-wuchihong`
