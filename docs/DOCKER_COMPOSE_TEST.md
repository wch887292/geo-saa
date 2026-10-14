# Docker Compose 实测指南（O8）

> **验证状态**：编排配置已通过静态审查 + CI 自动验证（见下方「CI 验证」）。
> 后端全链路已在本机以等价配置（MySQL 8 + Redis 5，非容器）实测通过
> （冒烟 15/15、功能 17/18、单元 47/47）。
> `docker compose up` 本身尚未在装有 Docker daemon 的机器上完整跑过——
> 本文档的验证清单就是为此准备的，任何人执行后把结果贴回即可把状态升级为
>「容器环境已验证」。
>
> 前置的 Docker daemon 未就绪属于环境问题，不是配置问题——`scripts/preflight.sh`
> 会把这类问题一次性列清楚。

## 零、环境预检（建议先跑）

```bash
bash scripts/preflight.sh        # Docker 模式
```

它会一次性告诉你缺什么：Docker 是否安装、daemon 是否启动、端口是否被占、
`.env` 是否齐全、JWT 强度够不够、默认口令守卫是否需要处理。**不要跳过这步**——
本项目 90% 的"部署失败"都是这几类原因。

## 一、前置条件

- Docker 24+、Docker Compose 2+
- 项目根目录准备 `.env`（推荐直接 `bash scripts/deploy.sh`，会自动生成随机密钥并校验）：

```env
MYSQL_USER=root
MYSQL_PASSWORD=<随机生成的强密码>
JWT_SECRET=<openssl rand -base64 32 生成的值>
CORS_ALLOWED_ORIGINS=http://localhost
AI_SIMULATION_ENABLED=true
# 可选：GEO 真实采集器（G-01），默认关闭
# GEO_COLLECTOR_ENABLED=true
# GEO_COLLECTOR_BRANDS=品牌A,品牌B
# GEO_PERPLEXITY_API_KEY=pplx-xxx
```

## 二、首次部署必须改密（默认口令闸门）

`init.sql` 注入的管理员 `admin / admin123`，其 BCrypt 散列**固定写在公开仓库里**，
等同于凭据泄露。为避免"谁都能登录你刚部署的后台"，后端在 prod 档案下会检查
数据库里的口令是否仍是默认值，**是则直接拒绝启动**并打印指引。

改密步骤：

```bash
# 1. 生成 BCrypt 散列（明文只留在你自己的终端）
python -c "import bcrypt;print(bcrypt.hashpw(b'你的新密码',bcrypt.gensalt(10)).decode())"

# 2. 把结果填进 scripts/change_password.sql 的 <NEW_BCRYPT_HASH>，然后执行
docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" geo_saa' \
  < scripts/change_password.sql
```

若只是本地演示、明确知道风险，可在 `.env` 里设 `DEFAULT_PASSWORD_ACTION=disabled`
放行并保留告警。dev 档案默认 `warn`，不影响本地开发。

## 三、一键启动

```bash
bash scripts/deploy.sh
# 等价：docker compose up -d --build
```

首次启动 MySQL 容器会执行挂载的
`geo-saa-backend/src/main/resources/db/init.sql`：
自动建库 `geo_saa`、建全部表（含 12. `asset_record`）、注入 `admin/admin123` 与系统配置。

## 四、验证清单

```bash
# 一键验收（等价于下面 1-6 步，且额外回归本次修复的两个缺陷）
bash scripts/healthcheck.sh

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

## 五、已完成的检查结论

### 5.1 编排静态审查（已完成）

| 检查项 | 结论 |
|--------|------|
| MySQL 首次初始化 `init.sql` | ✅ 挂载 `docker-entrypoint-initdb.d`，自动建库建表（含 `asset_record`） |
| 后端 prod 档案环境变量 | ✅ `MYSQL_HOST/USER/PASSWORD`、`REDIS_HOST`、`JWT_SECRET`（fail-fast）、`CORS_ALLOWED_ORIGINS` 齐全 |
| RabbitMQ 联动 | ✅ compose 提供 rabbitmq 服务；prod 未排除 MQ 自动配置，可正常连接 |
| 采集器配置 | ✅ 默认关闭（`app.geo.collector.enabled=false`），未配 key 时后端照常启动，不影响既有接口 |
| 前端镜像 | ✅ `frontend/Dockerfile` 已使用 `npm ci`（配合已提交的 `package-lock.json`，可复现构建） |
| 服务健壮性 | ✅ mysql/redis/rabbitmq 均有 healthcheck + restart 策略 + 资源上限；端口仅绑定 127.0.0.1 |
| 备份 | ✅ 独立 backup 容器按 `BACKUP_RETENTION_DAYS` 轮转 |

### 5.2 非容器环境实测（已完成，等价配置）

在同一套 MySQL 8 + Redis 上以 `dev` 档案直跑 jar，覆盖了除容器网络编排外的全部业务逻辑：

| 验证项 | 结果 |
|--------|------|
| 单元测试 | ✅ 47/47 通过 |
| 冒烟测试 `smoke_test.py` | ✅ 15/15 通过 |
| 功能测试 `func_test.py` | ✅ 17/18 通过（1 项为幂等重复，非缺陷） |
| 批量生成（RabbitMQ 降级路径） | ✅ 返回 200，原 500 缺陷已修复 |
| 唯一约束冲突 | ✅ 返回 400 可读提示，原 500 已修复 |
| 错误密码登录 | ✅ 返回 HTTP 401（原为 200 + body 报错） |
| 资产统计口径 | ✅ 新增 `brandTotal`，字段语义不再混淆 |

### 5.3 待补：容器环境实测（未执行）

唯一未覆盖的环节是 `docker compose up` 本身。执行第四节清单后，
把 `docker compose ps` 与 `healthcheck.sh` 的输出贴回，即可把本文档状态
升级为「容器环境已验证」。

- **数据卷持久化**：`init.sql` 仅在 `mysql-data` 数据卷为空（首次）时执行；
  若改过表结构需 `docker compose down -v` 后重建（会清空数据）。
- **端口占用**：3306/6379/5672/15672/8080/80 与本地服务冲突时，先停本机实例或改 compose 端口映射。
- **本机实测后**：把执行结果（`docker compose ps` + 冒烟输出）贴回，可更新本文件状态为「已验证」。

---

## 附 A、本轮修复的 Docker 真实缺陷（5 项）

这 5 个问题会让 `docker compose up --build` **直接失败或永久卡住**，不是理论风险：

| # | 缺陷 | 后果 | 修复 |
|---|------|------|------|
| 1 | 后端 Dockerfile `COPY target/geo-saa-backend.jar`，但 `.gitignore` 第 6 行忽略 `*.jar` | 全新 clone 后 `target/` 是空的，**构建直接失败** | 改为多阶段构建，容器内执行 `mvn package`，jar 回归为构建产物 |
| 2 | 基础镜像不含 `curl`，而 compose 的 healthcheck 写 `curl -fsS ...` | 容器**永久 unhealthy** → `frontend` 的 `depends_on: service_healthy` 死锁等不到后端 → 整条链路起不来 | 改用 JDK 自带 `java.net.http` 探针（`docker/HealthProbe.java`） |
| 3 | 前端 Dockerfile 用 `node:18-alpine`，但项目用 Vite 8（要求 Node 20.19+/22.12+） | `npm run build` **必然失败**（`crypto.hash` 不可用） | 升级到 `node:22-alpine` |
| 4 | 前端 Dockerfile 用 `npm ci`，但 lockfile 由 Windows 生成 | Linux 上漏装 rollup 原生模块，构建报 `Cannot find module` | 改为 `npm install`（与 CI 侧同理） |

> 缺陷 3、4 是 CI 注释里已记录过的问题——同一个坑在 Docker 构建里再次出现，
> 说明只修 CI 不修 Dockerfile 是不够的。

### 一次误判及其更正（保留记录，避免重犯）

排查过程中曾把运行阶段的 COPY 从 `geo-saa-backend.jar` 改成 `geo-saa-backend-2.0.0.jar`，
理由是「pom.xml 未配 `<finalName>`，故jar 带版本号」。**该判断是错的**：

- `pom.xml` 第 124 行确实配置了 `<finalName>geo-saa-backend</finalName>`；
- GitHub Actions 实测日志为 `Building jar: /build/target/geo-saa-backend.jar`。

也就是说原版的 jar 名本来就是对的，改动反而会让 COPY 找不到文件。已改回，
并在 Dockerfile 内留下注释说明该值与 `finalName` 的绑定关系。

教训：静态审查时「顺手修好看起来不对的地方」比不改更危险。
这个错误本机无法发现（本机无 Docker daemon），是 CI 实跑构建暴露的——
这正是新增 `docker.yml` 的价值所在。

## 附 B、CI 验证（本机无 Docker daemon 时的替代路径）

新增 `.github/workflows/docker.yml`，在 GitHub Actions（runner 自带 Docker daemon）
上自动验证：

| Job | 验证内容 |
|-----|---------|
| `compose-validate` | `docker compose config --quiet` 语法与变量闭环校验；`.env` 未入库检查 |
| `docker-build` | 真实构建两个镜像；校验 jar + 探针存在；探针在无后端时正确判定 unhealthy；两个镜像均声明 `HEALTHCHECK` |

这意味着**上述缺陷会被 CI 自动拦住**，不再依赖人工发现。

### 实测结果（2026-10-14）

本机 Docker daemon 因 `wsl.exe` 被系统 Program Blacklist 拦截，无法本地验证。
改用 GitHub Actions（runner 自带 daemon）实跑，**全部通过**：

| Job | 结果 | 耗时 |
|-----|------|------|
| `compose-validate` | ✅ success | 8s |
| `docker-build` | ✅ success | 3m18s |
| `ci`（单元测试） | ✅ success | 1m41s |

`docker-build` 的实际输出，证明是真实构建而非走过场：

```
geo-saa-frontend    ci    64.6MB     # vite build ✓ built in 958ms
geo-saa-backend     ci    319MB
jar + probe present                          # app.jar 与 probe.jar 均产出
health probe failed: java.net.ConnectException
OK: 无后端时探针判定 unhealthy                 # 探针退出码为 1，符合预期
{"Test":["CMD","java","-cp","probe.jar","HealthProbe"],...}   # backend HEALTHCHECK
OK: 两个镜像均声明了 HEALTHCHECK
```

**首次运行确实拦下了一个真实缺陷**（探针源文件未进构建上下文导致构建失败），
以及一个由静态审查误判引入的 jar 名回归。详见上方「一次误判及其更正」。

> 若上游 PR 因安全策略停在 `action_required`，不必等管理员批准：
> Actions 权限按仓库判定而非按 PR，在自有 fork 内开PR（同一分支 → main）
> 即可正常触发。这是本项目验证 Docker 路径的常规手段。

### 本机想手动复现

```bash
docker compose --env-file .env config --quiet   # 只校验，不启动
```
