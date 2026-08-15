# GEO-SaaS FAQ (English)

> AI-driven Generative Engine Optimization (GEO) platform

---

## Table of Contents

- [Basics](#basics)
- [Quick Start](#quick-start)
- [Docker Deployment](#docker-deployment)
- [AI & Simulation Mode](#ai--simulation-mode)
- [Content & GEO Validation](#content--geo-validation)
- [Data Monitoring](#data-monitoring)
- [Configuration & Troubleshooting](#configuration--troubleshooting)

---

## Basics

### Q: What is GEO-SaaS?

An AI-driven **Generative Engine Optimization (GEO)** platform that helps brands improve visibility across AI search and traditional search engines. It covers brand diagnosis, knowledge base, AI content creation, multi-channel distribution, and monitoring in one place.

### Q: Do I need a paid AI API key to try it?

No. It ships with a **built-in simulation mode** (`ai.simulation.enabled: true`) so you can experience the full flow without any API key. Configure a real key (OpenAI / Qwen / Doubao) in `application.yml` to use live models.

### Q: What license is it under?

MIT License.

---

## Quick Start

### Q: What are the prerequisites for local dev?

- JDK 17
- Node.js 18+
- MySQL 8.0 (running, port 3306)
- Redis 7 (running, port 6379)
- Maven 3.9 (scripts can handle it)

### Q: What's the fastest way to start locally?

Run `.\start.ps1` at the project root. It checks the environment, builds the backend JAR, starts it on port 8080, installs frontend deps, starts Vite on port 3000, and opens the browser.

### Q: How do I start manually?

1. Init DB: `source geo-saa-backend/src/main/resources/db/init.sql` (creates `geo_saa`).
2. Backend: `cd geo-saa-backend && mvn clean package -DskipTests && java -jar target/geo-saa-backend.jar --spring.profiles.active=dev`.
3. Frontend: `cd geo-saa-frontend && npm install && npx vite --host`.
4. Open http://localhost:3000; default admin `admin / admin123`.

---

## Docker Deployment

### Q: How do I deploy with Docker?

```bash
docker compose up -d --build
```
Or run `.\deploy.ps1`. Frontend at http://localhost, backend at http://localhost:8080, RabbitMQ management at http://localhost:15672 (guest/guest), default admin `admin / admin123`.

### Q: Is RabbitMQ required?

No. In dev mode RabbitMQ is disabled and core features are unaffected.

---

## AI & Simulation Mode

### Q: Which AI models are supported?

OpenAI, Qwen (Tongyi), and Doubao, configured in `application.yml`. Simulation mode returns mock data when no key is set.

### Q: How do I switch to a real model?

Set the provider's `api-key` in `application.yml` and disable simulation (`ai.simulation.enabled: false`). For OpenAI also set `api-url` and `model`.

---

## Content & GEO Validation

### Q: What is the "GEO nine-tactic health check"?

On content creation, `POST /content/geo-validate` scores the text using the nine tactics from the Princeton KDD 2024 GEO paper (expert quotes, quantitative data, citations, fluency, technical terms, etc.). **Keyword stuffing is auto-blocked** (measured −8%~−10% visibility) and returns HTTP 400.

### Q: What content templates are available?

Multi-industry templates: tech, medical, education, finance, e-commerce, legal. Batch generation with sensitive-word filtering is supported.

---

## Data Monitoring

### Q: How is data authenticity ensured?

Core metrics include a **data-truth gate**: when `hasData=false`, the system never fakes data with random numbers (simulation mode tags results `simulated`; production returns real empties). A **GEO real-data collector** (configurable Perplexity / OpenAI-compatible gateway) auto-collects mentionRate / first-recommendation rate / index count daily and, on failure, never writes simulated data.

### Q: What does monitoring cover?

Search ranking, traffic analysis, and brand-volume trends.

---

## Configuration & Troubleshooting

### Q: Port 8080 already in use?

`start.ps1` auto-frees it, or manually:
```powershell
netstat -ano | findstr ":8080 "
Stop-Process -Id <PID> -Force
```

### Q: Database connection failed?

Ensure MySQL is running with credentials `root/root`, or edit `application-dev.yml`.

### Q: Frontend can't reach the backend API?

Check Vite proxy config `vite.config.js`; ensure `target` points to the correct backend address.

### Q: What are the default DB credentials in dev?

`root` / `root`. Change them for any non-dev environment.

---

*Jinjiang Feihongzhi Technology Enterprise Management Co., Ltd. · Feiyang Qiyuan R&D Center · Lead: Wu Cihong*
