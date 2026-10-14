#!/usr/bin/env bash
# ===========================================================
# 部署验收脚本：对运行中的实例做端到端检查
#
#   bash scripts/healthcheck.sh
#
# 覆盖：容器状态、健康端点、登录、鉴权边界、
#       本次修复的关键回归点（批量生成不再 500、唯一约束返回 400）
# 退出码 0 = 全部通过
# ===========================================================
set -uo pipefail

cd "$(dirname "$0")/.."

BACKEND="${BACKEND_URL:-http://localhost:8080}"
FRONTEND="${FRONTEND_URL:-http://localhost}"

PASS=0
FAIL=0

ok()   { printf '  \033[0;32mPASS\033[0m %-46s %s\n' "$1" "${2:-}"; PASS=$((PASS+1)); }
bad()  { printf '  \033[0;31mFAIL\033[0m %-46s %s\n' "$1" "${2:-}"; FAIL=$((FAIL+1)); }

echo "======================================================================"
echo " GEO-SaaS 部署验收   backend=${BACKEND}  frontend=${FRONTEND}"
echo "======================================================================"

# ---------- 容器状态（仅在 compose 环境下可用）----------
if command -v docker >/dev/null 2>&1 && docker compose ps >/dev/null 2>&1; then
  echo
  echo "[1/6] 容器状态"
  for svc in mysql redis rabbitmq backend frontend; do
    st="$(docker compose ps --format '{{.Health}}' "$svc" 2>/dev/null | head -1)"
    case "$st" in
      healthy)   ok "$svc healthy" ;;
      "")        bad "$svc 未运行" ;;
      *)         bad "$svc 状态异常" "$st" ;;
    esac
  done
else
  echo
  echo "[1/6] 容器状态 —— 跳过（非 Docker Compose 环境）"
fi

# ---------- 健康端点 ----------
echo
echo "[2/6] 健康检查"
code=$(curl -s -o /tmp/hc.json -w '%{http_code}' --max-time 10 "${BACKEND}/api/v1/system/health" 2>/dev/null)
if [ "$code" = "200" ] && grep -q '"code":200' /tmp/hc.json 2>/dev/null; then
  ok "后端健康检查" "HTTP 200"
else
  bad "后端健康检查" "HTTP ${code:-无响应}"
fi

code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "${FRONTEND}/" 2>/dev/null)
[ "$code" = "200" ] && ok "前端可访问" "HTTP 200" || bad "前端可访问" "HTTP ${code:-无响应}"

code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "${FRONTEND}/healthz" 2>/dev/null)
[ "$code" = "200" ] && ok "前端容器探活端点" "/healthz" || bad "前端容器探活端点" "HTTP ${code:-无响应}"

# ---------- 登录 ----------
echo
echo "[3/6] 认证"
code=$(curl -s -o /tmp/login.json -w '%{http_code}' --max-time 15 \
  -X POST "${BACKEND}/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' 2>/dev/null)
TOKEN=$(sed -n 's/.*"token":"\([^"]*\)".*/\1/p' /tmp/login.json 2>/dev/null | head -1)
if [ "$code" = "200" ] && [ -n "$TOKEN" ]; then
  ok "管理员登录" "已获取 token"
else
  bad "管理员登录" "HTTP ${code:-无响应}（默认账号若已改密码，请用实际账号验收）"
  echo
  echo "======================================================================"
  echo " 结果: ${PASS} 通过 / ${FAIL} 失败 —— 登录失败，后续检查已跳过"
  echo "======================================================================"
  exit 1
fi

# 登录失败必须返回 4xx，而不是 200 + body 报错（否则监控会漏记爆破）
code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 \
  -X POST "${BACKEND}/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"definitely_wrong_password"}' 2>/dev/null)
[ "$code" = "401" ] && ok "错误密码返回 401" "" || bad "错误密码返回 401" "实际 HTTP ${code:-无响应}"

# ---------- 鉴权边界 ----------
echo
echo "[4/6] 鉴权边界"
code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 \
  "${BACKEND}/api/v1/content/list?pageNum=1&pageSize=5" 2>/dev/null)
[ "$code" = "401" ] && ok "无 token 访问被拒" "HTTP 401" || bad "无 token 访问被拒" "HTTP ${code}"

code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 \
  -H "Authorization: Bearer invalid.token.value" \
  "${BACKEND}/api/v1/content/list?pageNum=1&pageSize=5" 2>/dev/null)
[ "$code" = "401" ] && ok "非法 token 被拒" "HTTP 401" || bad "非法 token 被拒" "HTTP ${code}"

# ---------- 核心业务 ----------
echo
echo "[5/6] 核心业务"
for api in "/api/v1/statistics/dashboard" "/api/v1/content/templates" \
           "/api/v1/distribute/channels" "/api/v1/system/model-config"; do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 \
    -H "Authorization: Bearer ${TOKEN}" "${BACKEND}${api}" 2>/dev/null)
  [ "$code" = "200" ] && ok "GET ${api}" "" || bad "GET ${api}" "HTTP ${code:-无响应}"
done

# ---------- 本次修复的回归点 ----------
echo
echo "[6/6] 已修复缺陷回归验证"
STAMP=$(date +%s)

code=$(curl -s -o /tmp/batch.json -w '%{http_code}' --max-time 90 \
  -X POST "${BACKEND}/api/v1/content/batch-generate" \
  -H "Authorization: Bearer ${TOKEN}" -H 'Content-Type: application/json' \
  -d "[{\"title\":\"验收探针-${STAMP}\",\"contentType\":\"ARTICLE\",\"brandName\":\"验收\",\"keywords\":\"验收\"}]" 2>/dev/null)
if [ "$code" = "200" ]; then
  ok "批量生成不再 500（原缺陷：AmqpConnectException）" "HTTP 200"
else
  bad "批量生成不再 500（原缺陷：AmqpConnectException）" "HTTP ${code:-无响应}"
fi

# 唯一约束冲突应为 400 而非 500（用一个专用探针品牌名重复提交两次）
BRAND="验收探针品牌-${STAMP}"
for i in 1 2; do
  eval "code_${i}=$(curl -s -o /dev/null -w '%{http_code}' --max-time 30 \
    -X POST "${BACKEND}/api/v1/knowledge/brands" \
    -H "Authorization: Bearer ${TOKEN}" -H 'Content-Type: application/json' \
    -d "{\"brandName\":\"${BRAND}\",\"industry\":\"科技\"}" 2>/dev/null)"
done
if [ "$code_2" = "400" ]; then
  ok "重复提交返回 400（原为 500 服务器内部错误）" "首次=${code_1} 重复=${code_2}"
else
  bad "重复提交返回 400（原为 500 服务器内部错误）" "首次=${code_1} 重复=${code_2}"
fi

echo
echo "======================================================================"
echo " 结果: ${PASS} 通过 / ${FAIL} 失败"
echo "======================================================================"
[ "$FAIL" -eq 0 ] || exit 1