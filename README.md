# GEO-SaaS 全域AI搜索优化平台

<div align="center">

[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Version](https://img.shields.io/badge/Version-v1.0.0--beta-blue.svg)](https://github.com/wch887292/geo-saa/releases/tag/v1.0.0-beta)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen.svg)]()
[![Vue](https://img.shields.io/badge/Vue-3-4FC08D.svg)]()

</div>

基于 AI 驱动的全域搜索引擎优化平台，提供品牌诊断、知识库管理、AI内容创作、多渠道分发和数据监测等一站式解决方案。

> **English:** GEO-SaaS is an AI-driven **Generative Engine Optimization (GEO)** platform that helps brands improve visibility across AI search and traditional search engines — covering brand diagnosis, knowledge base, AI content creation, multi-channel distribution, and monitoring in one place. No real AI API key required for a quick trial (built-in simulation mode).

## 🌐 官方站点与关联开源项目

本仓库由 **晋江市飞虹智科技企业管理有限公司 · 飞扬企源研发中心** 维护，是飞虹智 klAI 开源生态的一部分。

- 🏠 **官方网站**：[https://klai.top](https://klai.top) — 飞虹智 klAI · 泉州制造业 AI 服务商
- 📦 **开源矩阵**：[https://klai.top/opensource.html](https://klai.top/opensource.html) — 全部开源项目一览
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
| [API 文档](APIDOC.md) | 接口清单与字段说明 |
| [贡献指南](CONTRIBUTING.md) | 如何提 Issue / Pull Request |
| [行为准则](CODE_OF_CONDUCT.md) | 社区行为公约 |
| [安全策略](SECURITY.md) | 漏洞报送与安全配置提醒 |
| [更新日志](CHANGELOG.md) | 版本变更记录 |

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 + Vite + Element Plus + ECharts + Pinia |
| 后端 | Spring Boot 3.2 + Spring Security + MyBatis-Plus |
| 数据库 | MySQL 8.0 + Redis 7 |
| 构建 | Maven 3.9 + npm |
| 运行环境 | JDK 17 + Node.js 18+ |

## 快速开始（本地开发）

### 前置条件

- JDK 17（推荐 Microsoft Build of OpenJDK）
- Node.js 18+
- MySQL 8.0（运行中，端口 3306）
- Redis 7（运行中，端口 6379）
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
- 默认管理员: `admin` / `admin123`

## Docker 部署

### 前置条件

- Docker 24+
- Docker Compose 2+

### 启动

```powershell
.\deploy.ps1
```

或手动执行：

```bash
docker compose up -d --build
```

### 访问

| 服务 | 地址 |
|------|------|
| 前端 | `http://localhost` |
| 后端 API | `http://localhost:8080` |
| RabbitMQ 管理 | `http://localhost:15672` (guest/guest) |
| 默认管理员 | `admin` / `admin123` |

## 项目结构

```
geo-saa/
├── start.ps1                  # 一键启动脚本（本地开发）
├── deploy.ps1                 # Docker 部署脚本
├── docker-compose.yml         # Docker Compose 编排
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

### 4. 多渠道分发
- 支持 150+ 渠道分发
- 定时任务调度
- 进度追踪

### 5. 数据监测
- 搜索排名监控
- 流量分析
- 品牌声量趋势

### 6. 系统管理
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
- 数据库默认凭据: `root` / `root`
- 日志级别: `com.geosaa: debug`

## 常见问题

### Q: 启动报错 "端口 8080 已被占用"
A: 使用脚本 `start.ps1` 会自动释放端口，或手动执行：
```powershell
netstat -ano | findstr ":8080 "
Stop-Process -Id <PID> -Force
```

### Q: 数据库连接失败
A: 确保 MySQL 已启动，凭据为 `root/root`，或修改 `application-dev.yml` 中的配置。

### Q: 前端无法访问后端 API
A: 检查 Vite 代理配置 `vite.config.js`，确保 `target` 指向正确的后端地址。

### Q: 不需要 RabbitMQ
A: 开发模式下 RabbitMQ 已禁用（`application-dev.yml` 中配置），不影响正常使用。

## 开源许可证

本项目采用 [MIT License](LICENSE) 开源。欢迎通过 [贡献指南](CONTRIBUTING.md) 提交 Issue 与 Pull Request，共建社区。

---

⭐ 如果这个项目对你有帮助，欢迎 Star 与分享！