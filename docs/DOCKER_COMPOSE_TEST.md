# Docker Compose 实测指南（O8）

> 状态：**待环境验证** —— 本机沙箱无 Docker 守护进程（`docker: command not found`），
> 以下步骤基于 `docker-compose.yml` 静态检查结论编写，需在装有 Docker 24+ / Docker Compose 2+ 的机器上执行。
> 检查已确认：编排结构、环境变量注入、`init.sql` 自动初始化（含新增 `asset_record` 表）均无缺失。

## 一、前置条件

- Docker 24+、Docker Compose 2+
- 项目根目录准备 `.env`（`JWT_SECRET` **必填**，缺失时后端 fail-fast 拒绝启动）：

```env
MYSQL_USER=root
MYSQL_PASSWORD=root
JWT_SECRET=<openssl rand -base64 32 生成的值>
CORS_ALLOWED_ORIGINS=http://localhost
AI_SIMULATION_ENABLED=true
# 可选：GEO 真实采集器（G-01），默认关闭
# GEO_COLLECTOR_ENABLED=true
# GEO_COLLECTOR_BRANDS=品牌A,品牌B
# GEO_PERPLEXITY_API_KEY=pplx-xxx
```

## 二、一键启动

```bash
docker compose up -d --build
```

首次启动 MySQL 容器会执行挂载的
`geo-saa-backend/src/main/resources/db/init.sql`：
自动建库 `geo_saa`、建全部表（含 12. `asset_record`）、注入 `admin/admin123` 与系统配置。

## 三、验证清单

```bash
# 1. 全部容器健康
docker compose ps
#    期望：mysql/redis/rabbitmq healthy，backend/frontend running

# 2. 后端健康检查（无需鉴权）
curl http://localhost:8080/api/v1/system/health
#    期望：HTTP 200，{"code":200,...}

# 3. 登录拿 token
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'
#    期望：返回 data.token

# 4. 端到端冒烟（复用本地脚本，直连容器后端）
SMOKE_BASE=http://127.0.0.1:8080 python smoke_test.py
#    期望：SUMMARY: 15 passed, 0 failed

# 5. 前端可达
curl -I http://localhost/    # 期望 200，Nginx 托管 dist

# 6.（可选）GEO 采集器验证：置 GEO_COLLECTOR_ENABLED=true 并配好引擎后
docker compose logs backend | grep "GEO 定时采集"   # 期望出现采集完成日志
```

## 四、已完成的静态检查结论（本机执行前先核对）

| 检查项 | 结论 |
|--------|------|
| MySQL 首次初始化 `init.sql` | ✅ 挂载 `docker-entrypoint-initdb.d`，自动建库建表（含 `asset_record`） |
| 后端 prod 档案环境变量 | ✅ `MYSQL_HOST/USER/PASSWORD`、`REDIS_HOST`、`JWT_SECRET`（fail-fast）、`CORS_ALLOWED_ORIGINS` 齐全 |
| RabbitMQ 联动 | ✅ compose 提供 rabbitmq 服务；prod 未排除 MQ 自动配置，可正常连接 |
| 采集器配置 | ✅ 默认关闭（`app.geo.collector.enabled=false`），未配 key 时后端照常启动，不影响既有接口 |
| 前端镜像 | ✅ `frontend/Dockerfile` 已使用 `npm ci`（配合已提交的 `package-lock.json`，可复现构建） |

## 五、注意事项

- **数据卷持久化**：`init.sql` 仅在 `mysql-data` 数据卷为空（首次）时执行；
  若改过表结构需 `docker compose down -v` 后重建（会清空数据）。
- **端口占用**：3306/6379/5672/15672/8080/80 与本地服务冲突时，先停本机实例或改 compose 端口映射。
- **本机实测后**：把执行结果（`docker compose ps` + 冒烟输出）贴回，可更新本文件状态为「已验证」。
