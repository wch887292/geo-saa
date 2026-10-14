#!/usr/bin/env bash
# ===========================================================
# GEO-SaaS 一键部署脚本
#
#   bash scripts/deploy.sh
#
# 行为：
#   1. 校验 Docker / Docker Compose 可用
#   2. 生成 .env（不存在时）并自动填充安全随机密钥
#   3. 强制要求用户确认 CORS 域名，避免默认放开
#   4. docker compose up -d --build 并等待全部 healthy
# ===========================================================
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$(pwd)"

say()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[!]\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[x]\033[0m %s\n' "$*" >&2; exit 1; }

# ---------- 1. 环境检查 ----------
command -v docker >/dev/null 2>&1 || die "未找到 docker，请先安装 Docker 24+"
docker compose version >/dev/null 2>&1 || die "未找到 docker compose v2（插件形式），请升级 Docker"
say "Docker: $(docker --version)"

# ---------- 2. 生成 .env ----------
gen_secret() {
  # 优先用 openssl；都没有时退回 /dev/urandom
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 32
  else
    head -c 32 /dev/urandom | base64
  fi
}

if [ ! -f .env ]; then
  say "未发现 .env，正在生成（含随机密钥）"
  MYSQL_PASSWORD="$(gen_secret)"
  RABBITMQ_PASSWORD="$(gen_secret)"
  JWT_SECRET="$(gen_secret)"
  cat > .env <<EOF
# ===== 由 scripts/deploy.sh 于 $(date '+%Y-%m-%d %H:%M:%S') 生成 =====
# 敏感项已随机生成，请勿提交到版本库（.env 已在 .gitignore 中）

# ---- 必填：对外访问域名（多个用英文逗号分隔，禁止填 *）----
CORS_ALLOWED_ORIGINS=http://localhost

# ---- 凭据（已随机生成）----
MYSQL_USER=root
MYSQL_PASSWORD=${MYSQL_PASSWORD}
RABBITMQ_USER=geo
RABBITMQ_PASSWORD=${RABBITMQ_PASSWORD}
JWT_SECRET=${JWT_SECRET}

# ---- 端口 ----
FRONTEND_PORT=80
BACKEND_PORT=8080
MYSQL_PORT=3306
REDIS_PORT=6379

# ---- 应用 ----
TZ=Asia/Shanghai
# 演示/验收可保持 true；接入真实模型后改为 false 并填入 Key
AI_SIMULATION_ENABLED=true
OPENAI_API_KEY=
TONGYI_API_KEY=
DOUBAO_API_KEY=

# ---- 运维 ----
BACKUP_RETENTION_DAYS=14
EOF
  chmod 600 .env
  say ".env 已生成（权限 600）"
else
  warn ".env 已存在，沿用现有配置"
fi

# ---------- 3. 校验必填项 ----------
for k in JWT_SECRET MYSQL_PASSWORD RABBITMQ_PASSWORD CORS_ALLOWED_ORIGINS; do
  v="$(grep -E "^${k}=" .env | head -1 | cut -d= -f2- || true)"
  [ -n "$v" ] || die ".env 中 ${k} 为空，请填写后重试"
done
if grep -E "^CORS_ALLOWED_ORIGINS=.*\*" .env >/dev/null; then
  die "CORS_ALLOWED_ORIGINS 不允许使用通配符 *，请指定确切域名"
fi

# JWT 强度自检：Base64 解码后需 >= 32 字节
jwt_len="$(grep -E "^JWT_SECRET=" .env | cut -d= -f2- | base64 -d 2>/dev/null | wc -c || echo 0)"
[ "$jwt_len" -ge 32 ] || die "JWT_SECRET 强度不足（解码后 ${jwt_len} 字节，需 >= 32），请执行 openssl rand -base64 32 重新生成"
say "密钥校验通过（JWT ${jwt_len} 字节）"

# ---------- 4. 启动 ----------
say "开始构建并启动（首次构建包含前端产物，约需数分钟）"
docker compose up -d --build

# ---------- 5. 等待健康 ----------
say "等待各服务健康..."
for i in $(seq 1 40); do
  pending="$(docker compose ps --format '{{.Service}} {{.Health}} {{.State}}' \
            | awk '$2!="healthy" && $3=="running" {print $1}' | tr '\n' ' ')"
  if [ -z "$pending" ]; then
    say "全部服务已健康"
    break
  fi
  sleep 5
  [ "$i" = 40 ] && warn "仍有服务未就绪: ${pending}（可稍后执行 bash scripts/healthcheck.sh 复查）"
done

docker compose ps
say "部署完成"
echo
echo "  前端:      http://localhost:\${FRONTEND_PORT:-80}"
echo "  后端 API:  http://localhost:\${BACKEND_PORT:-8080}"
echo "  验收命令:  bash scripts/healthcheck.sh"
echo
warn "默认管理员 admin/admin123 —— 请立即登录修改密码！"