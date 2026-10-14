#!/usr/bin/env bash
# ===========================================================
# GEO-SaaS 环境就绪预检
#
#   bash scripts/preflight.sh              # Docker 部署模式（默认）
#   bash scripts/preflight.sh --local      # 本机源码运行模式（需 JDK/Maven/Node/MySQL/Redis）
#   bash scripts/preflight.sh --all        # 两种模式都检
#
# 作用：在用户执行部署前，一次性告诉他本机缺什么、为什么缺、怎么补，
#       避免"跑一半失败 → 猜哪里错 → 反复试"的循环。
# 退出码：0 = 必需项全部就绪；1 = 有必需项缺失（附修复建议）
# ===========================================================
set -uo pipefail

cd "$(dirname "$0")/.."
ROOT="$(pwd)"

MODE="docker"
for arg in "$@"; do
  case "$arg" in
    --local) MODE="local" ;;
    --all)   MODE="all" ;;
    -h|--help)
      sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'
      exit 0 ;;
    *) echo "未知参数: $arg（用 --help 查看用法）" >&2; exit 1 ;;
  esac
done

PASS=0; WARN=0; FAIL=0
MISSING=()

c_ok()   { printf '  \033[0;32mOK  \033[0m %-34s %s\n' "$1" "${2:-}"; PASS=$((PASS+1)); }
c_warn() { printf '  \033[0;33mWARN\033[0m %-34s %s\n' "$1" "${2:-}"; WARN=$((WARN+1)); }
c_fail() { printf '  \033[0;31mMISS\033[0m %-34s %s\n' "$1" "${2:-}"; FAIL=$((FAIL+1)); MISSING+=("$1"); }
head1()  { printf '\n\033[1;34m%s\033[0m\n' "$*"; }

# 端口占用检测：优先 ss，退化 netstat，再退化 lsof
port_in_use() {
  local p="$1"
  if command -v ss >/dev/null 2>&1; then
    ss -ltn 2>/dev/null | awk '{print $4}' | grep -qE "[:.]$p\$" && return 0
  elif command -v netstat >/dev/null 2>&1; then
    netstat -an 2>/dev/null | grep -E "[:.]$p .*LISTENING|[:.]$p .*LISTEN" >/dev/null && return 0
  fi
  return 1
}

# ===========================================================
# 1. Docker 部署模式必需项
# ===========================================================
check_docker() {
  head1 "[1/5] Docker 部署模式"

  # --- Docker CLI ---
  if command -v docker >/dev/null 2>&1; then
    c_ok "Docker CLI" "$(docker --version 2>/dev/null | head -1)"
  else
    c_fail "Docker CLI" "未安装 — https://www.docker.com/products/docker-desktop/"
    return
  fi

  # --- Docker daemon（最常见的坑：装了但没启动）---
  if docker info >/dev/null 2>&1; then
    eng="$(docker info --format '{{.OSType}}' 2>/dev/null)"
    if [ "$eng" = "linux" ]; then
      c_ok "Docker daemon" "运行中（Linux 容器）"
    else
      c_warn "Docker daemon" "运行中但为 ${eng:-未知} 引擎，Linux 镜像需切到 Linux containers"
    fi
  else
    c_fail "Docker daemon" "未运行 — 启动 Docker Desktop 后重试（Windows 需确认 WSL2 已启用）"
  fi

  # --- Compose v2 ---
  if docker compose version >/dev/null 2>&1; then
    c_ok "Docker Compose v2" "$(docker compose version --short 2>/dev/null)"
  elif command -v docker-compose >/dev/null 2>&1; then
    c_warn "Docker Compose" "仅 v1（docker-compose），建议升级到 v2：docker compose"
  else
    c_fail "Docker Compose" "缺失 — Docker Desktop 自带，升级 Docker 即可"
  fi

  # --- 端口占用 ---
  head1 "[2/5] 端口占用（compose 默认映射）"
  local ports="80:前端 8080:后端 3306:MySQL 6379:Redis 5672:RabbitMQ 15672:RabbitMQ控制台"
  local busy=""
  for p in 80 8080 3306 6379 5672 15672; do
    if port_in_use "$p"; then busy="$busy $p"; fi
  done
  if [ -z "$busy" ]; then
    c_ok "compose 所需端口" "全部空闲"
  else
    c_warn "端口被占用" "以下端口已有监听:$busy"
    printf '       %s\n' "→ 停止占用进程，或在 .env 中改 FRONTEND_PORT/BACKEND_PORT/MYSQL_PORT/REDIS_PORT 后重试"
  fi

  # --- .env ---
  head1 "[3/5] 配置"
  if [ -f .env ]; then
    c_ok ".env 文件" "已存在"
    local missing="" weakjwt=0
    for k in JWT_SECRET MYSQL_PASSWORD RABBITMQ_PASSWORD CORS_ALLOWED_ORIGINS; do
      v="$(grep -E "^${k}=" .env 2>/dev/null | head -1 | cut -d= -f2- || true)"
      [ -n "$v" ] || missing="$missing $k"
    done
    if [ -n "$missing" ]; then
      c_fail ".env 必填项" "缺失:$missing"
    else
      c_ok ".env 必填项" "齐全"
    fi
    if grep -E "^CORS_ALLOWED_ORIGINS=.*\*" .env >/dev/null 2>&1; then
      c_fail "CORS 配置" "含通配符 * — 请指定确切域名，否则后端拒绝启动"
    else
      c_ok "CORS 配置" "$(grep -E '^CORS_ALLOWED_ORIGINS=' .env | head -1 | cut -d= -f2-)"
    fi
    # JWT 强度（Base64 解码后 >= 32 字节）
    jwt_len="$(grep -E '^JWT_SECRET=' .env 2>/dev/null | cut -d= -f2- | base64 -d 2>/dev/null | wc -c | tr -d ' ')"
    if [ "${jwt_len:-0}" -ge 32 ]; then
      c_ok "JWT_SECRET 强度" "${jwt_len} 字节（>= 32）"
    else
      weakjwt=1
      c_fail "JWT_SECRET 强度" "解码后 ${jwt_len:-0} 字节，需 >= 32 — 生成：openssl rand -base64 32"
    fi
    # 默认口令守卫开关
    dpa="$(grep -E '^DEFAULT_PASSWORD_ACTION=' .env 2>/dev/null | head -1 | cut -d= -f2- || true)"
    if [ -z "$dpa" ]; then
      c_warn "默认口令守卫" "未显式设置 → prod 档案默认 fail：首次部署须先改 admin 口令"
      printf '       %s\n' "→ 见 README「首次部署」，或临时设 DEFAULT_PASSWORD_ACTION=disabled（自行承担风险）"
    else
      c_warn "默认口令守卫" "DEFAULT_PASSWORD_ACTION=$dpa（已放行默认口令，风险自负）"
    fi
  else
    c_warn ".env 文件" "不存在"
    printf '       %s\n' "→ 推荐：bash scripts/deploy.sh（自动生成随机密钥并校验）"
    printf '       %s\n' "  或手动：cp .env.example .env 后按注释填写 JWT_SECRET / MYSQL_PASSWORD 等"
  fi

  # --- 磁盘与内存 ---
  head1 "[4/5] 资源"
  if command -v df >/dev/null 2>&1; then
    avail_gb="$(df -BG . 2>/dev/null | awk 'NR==2{gsub(/G/,"",$4); print $4}')"
    if [ -n "${avail_gb:-}" ] && [ "$avail_gb" -lt 10 ] 2>/dev/null; then
      c_warn "磁盘可用" "${avail_gb}G — 首次构建镜像建议预留 10G 以上"
    else
      c_ok "磁盘可用" "${avail_gb:-未知}G"
    fi
  fi
  if command -v free >/dev/null 2>&1; then
    mem_mb="$(free -m 2>/dev/null | awk '/Mem:/{print $7}')"
    if [ -n "${mem_mb:-}" ] && [ "$mem_mb" -lt 2048 ] 2>/dev/null; then
      c_warn "可用内存" "${mem_mb}MB — 构建前端时可能 OOM，建议关闭其他占内存程序"
    else
      c_ok "可用内存" "${mem_mb:-未知}MB"
    fi
  fi

  head1 "[5/5] 下一步"
  if [ "$FAIL" -eq 0 ]; then
    printf '  \033[0;32m环境就绪\033[0m，可执行：\n\n     bash scripts/deploy.sh\n'
  else
    printf '  \033[0;31m有 %d 项缺失\033[0m，先补齐上面标 MISS 的条目。\n' "$FAIL"
  fi
}

# ===========================================================
# 2. 本机源码运行模式必需项
# ===========================================================
check_local() {
  head1 "[1/5] 运行时"

  # --- JDK（项目要求 17+）---
  if command -v java >/dev/null 2>&1; then
    jv="$(java -version 2>&1 | head -1 | sed 's/.*"\([0-9]*\).*/\1/')"
    if [ -n "${jv:-}" ] && [ "$jv" -ge 17 ] 2>/dev/null; then
      c_ok "JDK" "版本 ${jv}（>= 17）"
    else
      c_fail "JDK" "版本 ${jv:-未知}，项目要求 17+ — https://adoptium.net/"
    fi
  else
    c_fail "JDK" "未安装 — 需要 JDK 17 或更高"
  fi

  # --- Maven（可用 mvnw 兜底）---
  if command -v mvn >/dev/null 2>&1; then
    c_ok "Maven" "$(mvn -v 2>/dev/null | head -1)"
  elif [ -x "./mvnw" ]; then
    c_ok "Maven" "使用项目自带 mvnw"
  else
    c_fail "Maven" "未安装且无 mvnw — https://maven.apache.org/download.cgi"
  fi

  # --- Node（前端构建；Vite 8 要求 20.19+ / 22.12+，否则 crypto.hash 不可用）---
  if command -v node >/dev/null 2>&1; then
    nv="$(node -v 2>/dev/null | tr -d 'v')"
    nmaj="${nv%%.*}"
    nrest="${nv#*.}"; nmin="${nrest%%.*}"
    if { [ "$nmaj" -eq 20 ] && [ "$nmin" -ge 19 ]; } 2>/dev/null \
      || { [ "$nmaj" -eq 22 ] && [ "$nmin" -ge 12 ]; } 2>/dev/null \
      || [ "$nmaj" -ge 23 ] 2>/dev/null; then
      c_ok "Node.js" "v${nv}（满足 Vite 8：20.19+ / 22.12+）"
    else
      c_warn "Node.js" "v${nv:-未知}，需要 20.19+ 或 22.12+（Vite 8 要求，否则前端构建报 crypto.hash 不可用）— https://nodejs.org/"
    fi
  else
    c_warn "Node.js" "未安装（仅构建前端时需要；用 Docker 模式可不装）"
  fi

  head1 "[2/5] 数据存储"
  # --- MySQL ---
  if port_in_use 3306; then
    c_ok "MySQL" "3306 端口有监听"
    if command -v mysql >/dev/null 2>&1; then
      c_ok "mysql 客户端" "$(mysql --version 2>/dev/null | head -1)"
    else
      c_warn "mysql 客户端" "未安装（可选，仅手工查库时需要）"
    fi
  else
    c_fail "MySQL" "3306 无监听 — 本机源码模式必须先起 MySQL 8 并建 geo_saa 库"
  fi

  # --- Redis ---
  if port_in_use 6379; then
    c_ok "Redis" "6379 端口有监听"
  else
    c_fail "Redis" "6379 无监听 — 本机源码模式必须先起 Redis（令牌黑名单/限流依赖）"
  fi

  head1 "[3/5] 可选依赖"
  # --- RabbitMQ（不装会走本地降级，功能不受影响）---
  if port_in_use 5672; then
    c_ok "RabbitMQ" "5672 有监听（批量生成走 MQ 异步）"
  else
    c_warn "RabbitMQ" "未运行 — 可接受：批量生成/分发会自动降级为本地同步执行"
  fi

  # --- Python（验收脚本）---
  if command -v python >/dev/null 2>&1 || command -v python3 >/dev/null 2>&1; then
    py="$(python --version 2>&1 || python3 --version 2>&1)"
    c_ok "Python" "${py:-已安装}"
  else
    c_warn "Python" "未安装（仅运行 smoke_test.py / func_test.py 验收时需要）"
  fi

  head1 "[4/5] 端口"
  if port_in_use 8080; then
    c_warn "后端端口 8080" "已被占用 — 若已有旧实例在跑，先停掉再启动"
  else
    c_ok "后端端口 8080" "空闲"
  fi

  head1 "[5/5] 下一步"
  if [ "$FAIL" -eq 0 ]; then
    printf '  \033[0;32m环境就绪\033[0m，本机运行顺序：\n\n'
    printf '     cd geo-saa-backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev\n'
    printf '     cd geo-saa-frontend && npm install && npm run dev\n'
    printf '     验收：SMOKE_BASE=http://127.0.0.1:8080 python smoke_test.py\n'
  else
    printf '  \033[0;31m有 %d 项缺失\033[0m，先补齐上面标 MISS 的条目。\n' "$FAIL"
  fi
}

echo "======================================================================"
echo " GEO-SaaS 环境预检   目录: ${ROOT}   模式: ${MODE}"
echo "======================================================================"

case "$MODE" in
  docker) check_docker ;;
  local)  check_local ;;
  all)    check_docker; echo; echo "----------------------------------------------------------------------"; echo; check_local ;;
esac

echo
echo "======================================================================"
if [ "$FAIL" -eq 0 ]; then
  printf ' 结论: \033[0;32m就绪\033[0m（PASS %d / WARN %d / MISS %d）\n' "$PASS" "$WARN" "$FAIL"
  echo "======================================================================"
  exit 0
else
  printf ' 结论: \033[0;31m未就绪\033[0m（PASS %d / WARN %d / MISS %d）\n' "$PASS" "$WARN" "$FAIL"
  printf ' 缺失项: %s\n' "${MISSING[*]}"
  echo "======================================================================"
  exit 1
fi