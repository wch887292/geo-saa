# GEO-SaaS · Global AI Search Optimization Platform

<div align="center">

[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Version](https://img.shields.io/badge/Version-v2.0.0-blue.svg)](https://github.com/wch887292/geo-saa/releases/tag/v2.0.0)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen.svg)]()
[![Vue](https://img.shields.io/badge/Vue-3-4FC08D.svg)]()

</div>

An AI-driven **Generative Engine Optimization (GEO)** platform that helps brands improve visibility across AI search and traditional search engines — covering brand diagnosis, knowledge base, AI content creation, multi-channel distribution, and monitoring in one place. No real AI API key required for a quick trial (built-in simulation mode).

> **中文**: [README.md](README.md)

## 🌐 Official Site & Related Open-Source Projects

Maintained by **Jinjiang Feihongzhi Technology Enterprise Management Co., Ltd. · Feiyang Qiyuan R&D Center** (Lead: Wu Cihong), part of the Feihongzhi klAI open-source ecosystem.

- 🏠 **Official site**: [https://www.klai.top](https://www.klai.top) — Feihongzhi klAI · Quanzhou manufacturing-AI service provider
- 📦 **Open-source matrix**: [https://www.klai.top/opensource.html](https://www.klai.top/opensource.html)
- 📚 **AI knowledge base**: [https://kb.klai.top](https://kb.klai.top) — product docs & smart Q&A (MaxKB-powered)

**Related projects**:

| Project | Description |
|------|------|
| [GEO-SaaS](https://github.com/wch887292/geo-saa) | AI-driven GEO search optimization platform (this repo) |
| [Feihongzhi Enterprise AI Platform](https://github.com/wch887292/fyqy-ai-agent) | AI-native integrated management platform for SME manufacturers |
| [FyqyClaw](https://github.com/wch887292/FyqyClaw) | Full-lifecycle AI-driven dev tool (IDE + AI Agent) |
| [Xingmian AI](https://github.com/wch887292/xmai) | Sleep-health WeChat mini-program + private-deployable backend |

> ⭐ If this project helps you, please **Star** and share it so more people discover the Feihongzhi open-source ecosystem!

## 📚 Documentation

| Document | Description |
|------|------|
| [Architecture](docs/ARCHITECTURE.md) | System architecture, module breakdown, data flow |
| [Roadmap](docs/ROADMAP.md) | Future planning & community plan |
| [GEO/AAO Dev Guide](docs/GEO_AAO_DEV_GUIDE.md) | 3-generation search optimization (SEO/AEO/GEO/AAO) strategy & product mapping |
| [Retrospective Optimization](docs/RETROSPECTIVE_OPTIMIZATION.md) | Findings & engineering optimization log |
| [Docker Compose Test](docs/DOCKER_COMPOSE_TEST.md) | Compose one-click deployment verification checklist |
| [Dependency Assessment](docs/DEPENDENCY_ASSESSMENT.md) | Frontend major-version upgrade risk & acceptance |
| [API Docs](APIDOC.md) | Endpoint list & field descriptions |
| [Contributing](CONTRIBUTING.md) | How to file Issues / Pull Requests |
| [Code of Conduct](CODE_OF_CONDUCT.md) | Community covenant |
| [Security](SECURITY.md) | Vulnerability reporting & security config reminders |
| [Changelog](CHANGELOG.md) | Version change log |

## Tech Stack

| Layer | Technology |
|------|------|
| Frontend | Vue 3 + Vite + Element Plus + ECharts + Pinia |
| Backend | Spring Boot 3.2 + Spring Security + MyBatis-Plus |
| Database | MySQL 8.0 + Redis 7 |
| Build | Maven 3.9 + npm |
| Runtime | JDK 17 + Node.js 18+ |

## Quick Start (Local Dev)

### Prerequisites

- JDK 17 (Microsoft Build of OpenJDK recommended)
- Node.js 18+
- MySQL 8.0 (running, port 3306)
- Redis 7 (running, port 6379)
- Maven 3.9 (optional; scripts handle it)

### One-click start

Run the PowerShell script at the project root:

```powershell
.\start.ps1
```

The script automatically:
1. Checks JDK 17, Node.js, MySQL, Redis
2. Builds the backend JAR
3. Starts the backend (port 8080)
4. Installs frontend npm deps
5. Starts the frontend dev server (port 3000)
6. Opens the browser

### Manual start

#### 1. Initialize database

Ensure MySQL is running, then execute the init script:

```sql
source geo-saa-backend/src/main/resources/db/init.sql
```

This creates the `geo_saa` database and initializes schema and default data.

#### 2. Start backend

```powershell
cd geo-saa-backend
mvn clean package -DskipTests
java -jar target/geo-saa-backend.jar --spring.profiles.active=dev
```

Backend starts at `http://localhost:8080`.

#### 3. Start frontend

```powershell
cd geo-saa-frontend
npm install
npx vite --host
```

Frontend starts at `http://localhost:3000`; Vite proxies `/api` to the backend.

### Access

- Frontend: `http://localhost:3000`
- Backend: `http://localhost:8080`
- Default admin: `admin` / `admin123`

## Docker Deployment

### Prerequisites

- Docker 24+
- Docker Compose 2+

### Start

```powershell
.\deploy.ps1
```

Or manually:

```bash
docker compose up -d --build
```

### Access

| Service | Address |
|------|------|
| Frontend | `http://localhost` |
| Backend API | `http://localhost:8080` |
| RabbitMQ management | `http://localhost:15672` (guest/guest) |
| Default admin | `admin` / `admin123` |

## Project Structure

```
geo-saa/
├── start.ps1                  # one-click start (local dev)
├── deploy.ps1                 # Docker deploy script
├── docker-compose.yml         # Docker Compose orchestration
├── geo-saa-backend/           # backend service
│   ├── pom.xml
│   ├── docker/Dockerfile
│   └── src/main/
│       ├── java/com/geosaa/
│       │   ├── GeoApplication.java        # bootstrap
│       │   ├── config/                     # config
│       │   ├── security/                   # JWT auth
│       │   ├── common/                     # utilities
│       │   ├── ai/                         # AI adapter
│       │   └── modules/
│       │       ├── auth/                   # auth
│       │       ├── diagnose/               # brand diagnosis
│       │       ├── knowledge/              # knowledge base
│       │       ├── content/                # AI content creation
│       │       ├── distribute/             # multi-channel distribution
│       │       └── monitor/                # data monitoring
│       └── resources/
│           ├── application.yml             # main config
│           ├── application-dev.yml         # dev config
│           └── db/init.sql                 # DB init
├── geo-saa-frontend/          # frontend service
│   ├── package.json
│   ├── Dockerfile
│   ├── vite.config.js
│   ├── nginx/default.conf        # Nginx deploy config
│   └── src/
│       ├── api/                 # API wrappers
│       ├── views/               # pages
│       │   ├── dashboard/       # dashboard
│       │   ├── diagnose/        # AI brand diagnosis
│       │   ├── knowledge/       # knowledge base
│       │   ├── content/         # AI content creation
│       │   ├── distribute/      # distribution
│       │   ├── monitor/         # monitoring
│       │   └── system/          # settings
│       ├── router/              # routes
│       ├── store/               # state
│       └── components/          # shared components
```

## Feature Modules

### 1. Brand Diagnosis
- Input brand keywords; AI auto-analyzes search-engine performance
- Generates SEO health report and optimization suggestions

### 2. Knowledge Base
- Manage brand info, core keywords, product advantages
- Knowledge version-history tracking

### 3. AI Content Creation
- Multi-industry templates (tech, medical, education, finance, e-commerce, legal)
- Batch-generate AI-optimized articles
- Sensitive-word filtering
- **GEO nine-tactic health check** (`POST /content/geo-validate`): weighted scoring per the Princeton KDD 2024 paper's nine tactics (expert quotes / quantitative data / citations / fluency / technical terms, etc.); **keyword-stuffing auto-block** (paper measured −8%~−10% visibility) returns HTTP 400 on content creation

### 4. Multi-channel Distribution
- 150+ channels supported
- Scheduled-task dispatch
- Progress tracking

### 5. Data Monitoring
- Search ranking monitoring
- Traffic analysis
- Brand-volume trends
- Core metrics include a **data-truth gate**: when `hasData=false`, no random numbers are used to fake data (simulation mode tagged `simulated`; production disables simulation and returns real empties)
- **GEO real-data collector** (configurable Perplexity / OpenAI-compatible gateway, daily auto-collects mentionRate / first-recommendation rate / index count into stats; on failure never writes simulated data)

### 6. Asset Notarization
- Standalone `asset_record` data model (content/knowledge/distribution/diagnosis aggregation + independent dual-view notarization)
- Filter by year/month, paginated browsing of brand assets

### 7. System Management
- User auth & permission management
- AI model config (OpenAI / Qwen / Doubao)
- Simulation mode (experience without an API Key)

## Configuration

### AI model config

In `application.yml`:

```yaml
ai:
  openai:
    api-key: "sk-xxx"
    api-url: https://api.openai.com/v1
    model: gpt-4
  tongyi:
    api-key: "sk-xxx"
  doubao:
    api-key: "sk-xxx"
  simulation:
    enabled: true
```

### Dev mode

- RabbitMQ disabled by default (core features unaffected)
- Default DB credentials: `root` / `root`
- Log level: `com.geosaa: debug`

## FAQ

### Q: Startup error "port 8080 already in use"
A: The `start.ps1` script auto-frees the port, or manually:
```powershell
netstat -ano | findstr ":8080 "
Stop-Process -Id <PID> -Force
```

### Q: Database connection failed
A: Ensure MySQL is running, credentials `root/root`, or edit `application-dev.yml`.

### Q: Frontend can't reach backend API
A: Check Vite proxy config `vite.config.js`; ensure `target` points to the correct backend.

### Q: Don't need RabbitMQ?
A: In dev mode RabbitMQ is disabled (`application-dev.yml`), no impact on normal use.

---

## 🤝 Community Support

Stay tuned to Feihongzhi klAI for the latest open-source updates and tutorials.

*Jinjiang Feihongzhi Technology Enterprise Management Co., Ltd. · Feiyang Qiyuan R&D Center · Lead: Wu Cihong*

## Open-Source License

This project is open-sourced under the [MIT License](LICENSE). Contributions via Issues and Pull Requests are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md).

---

⭐ If this project helps you, please Star and share!
