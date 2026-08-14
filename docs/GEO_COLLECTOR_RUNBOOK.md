# GEO 真实采集器 · 首次试采手册（G-01 Runbook）

> 适用：拿到 AI 搜索引擎 API Key 后，3 分钟完成首次真实试采。
> 前置条件：后端可启动（MySQL + Redis），`POST /api/v1/monitor/collect` 已就绪（提交 `0134b0b`）。

## 一、准备 Key（二选一）

| 引擎 | 获取 | 配置变量 |
|------|------|----------|
| **Perplexity**（推荐，真 AI 搜索源） | https://www.perplexity.ai/settings/api 创建，`pplx-` 开头 | `GEO_PERPLEXITY_API_KEY` |
| **OpenAI 兼容网关**（通义/豆包/DeepSeek 等） | 各云厂商控制台 | `GEO_OPENAI_COMPAT_API_KEY` + `_API_URL` + `_MODEL` |

## 二、配置 `.env`（以 Perplexity 为例）

```env
# 采集总开关 + 待采品牌（逗号分隔，品牌名将替换进探测问题模板）
GEO_COLLECTOR_ENABLED=true
GEO_COLLECTOR_BRANDS=飞虹智,klai.top
# 每品牌探测 3 次（决定 mentionRate 分母）
GEO_COLLECTOR_PROBES=3

# Perplexity 引擎
GEO_PERPLEXITY_ENABLED=true
GEO_PERPLEXITY_API_KEY=pplx-你的key
GEO_PERPLEXITY_MODEL=sonar
```

> `.env` 已被 `.gitignore` 忽略，Key 不会入库。

## 三、重启后端（读取新环境变量）

```bash
# Git Bash / PowerShell（二选一，PowerShell 更稳）
$env:MAVEN_HOME="C:\ProgramData\chocolatey\lib\maven\apache-maven-3.9.16"
$env:SPRING_PROFILES_ACTIVE="dev"
cd geo-saa-backend
& "$env:MAVEN_HOME/bin/mvn.cmd" spring-boot:run
```

## 四、手动触发一次采集

```bash
# 登录拿 token
TOKEN=$(curl -s -X POST http://127.0.0.1:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' \
  | python -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

# 触发采集
curl -s -X POST http://127.0.0.1:8080/api/v1/monitor/collect \
  -H "Authorization: Bearer $TOKEN"
```

预期返回（采集成功）：
```json
{
  "code": 200,
  "data": {
    "飞虹智": { "written": true, "probes": 3, "successProbes": 3,
                "engines": "perplexity", "metrics": {"mentionRate": 66, "firstRecommendRate": 33, "collectionCount": 4} },
    "summary": { "date": "2026-08-14", "brands": 2, "ok": 2, "fail": 0 }
  }
}
```

未配置 Key 时的正确降级（不写库）：
```json
{ "code": 200, "data": { "skipped": "未启用任何采集引擎（perplexity / openai-compat）" } }
```

## 五、验证监测端展示

```bash
curl -s "http://127.0.0.1:8080/api/v1/monitor/core-metrics?brandName=飞虹智" \
  -H "Authorization: Bearer $TOKEN"
```

- 成功：`hasData: true`、`simulated: false`，`score` 按
  `mentionRate×0.4 + firstRecommendRate×0.35 + min(100, collectionCount/10)×0.25` 计算；
- 数据真实性闸门生效：模拟模式只打 `simulated` 标，生产关模拟后无数据即返回 0。

## 六、回退/关闭

- 停止采集：`.env` 中 `GEO_COLLECTOR_ENABLED=false` 后重启；定时任务（每日 01:30）与手动接口同时失效。
- 清理某品牌数据：`DELETE FROM data_monitor_stat WHERE stat_key='品牌名' AND dimension='geo-collector';`
  并清 Redis 缓存 `redis-cli del "geo:monitor:stats:core:品牌名"`。

## 链路自检（无 Key 也可验证管道）

本地已实测（2026-08-14）：向 `data_monitor_stat` 写入
`mention_rate=72 / first_recommend_rate=58 / collection_count=460` 后，
`core-metrics` 返回 `hasData=true, score=60`（与公式一致），清理后回退 `hasData=false`——
证明「采集 → 写库 → 监测展示」闭环可用，只差真实引擎凭据。
