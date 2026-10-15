#!/bin/bash
# ============================================================
# GEO-SaaS 私有化一键部署脚本
# 支持：MySQL / 达梦 DM / 人大金仓 KingbaseES
# 支持：AMD64 / ARM64（飞腾/鲲鹏）
#
# 使用方法：
#   chmod +x deploy.sh
#   ./deploy.sh                    # 默认 MySQL 部署
#   ./deploy.sh --db dm            # 达梦 DM 部署
#   ./deploy.sh --db kingbase      # 人大金仓部署
#   ./deploy.sh --stop             # 停止服务
#   ./deploy.sh --restart          # 重启服务
#   ./deploy.sh --logs             # 查看日志
#   ./deploy.sh --status           # 查看状态
# ============================================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 项目根目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 默认配置
DB_TYPE="mysql"
COMPOSE_FILE="docker-compose.xinchuang.yml"
PROJECT_NAME="geo-saa"

# 打印函数
info() { echo -e "${BLUE}[INFO]${NC} $1"; }
success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

# 检查 Docker
check_docker() {
    if ! command -v docker &> /dev/null; then
        error "Docker 未安装，请先安装 Docker"
    fi
    if ! docker info &> /dev/null; then
        error "Docker 服务未启动，请先启动 Docker"
    fi
    info "Docker 版本: $(docker --version)"
}

# 检查 .env 文件
check_env() {
    if [ ! -f .env ]; then
        warn ".env 文件不存在，从 .env.example 复制..."
        cp .env.example .env
        warn "请编辑 .env 文件配置实际参数（尤其是 JWT_SECRET 和数据库密码）"
        read -p "编辑完成后按回车继续..."
    fi
    # 检查 JWT_SECRET
    if grep -q "please_change_this" .env; then
        error "JWT_SECRET 未修改，请编辑 .env 文件设置安全的 JWT_SECRET"
    fi
}

# 构建镜像
build_images() {
    info "构建后端镜像（数据库类型: $DB_TYPE）..."
    if [ "$DB_TYPE" = "mysql" ]; then
        docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" --profile mysql build backend
    elif [ "$DB_TYPE" = "dm" ]; then
        docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" --profile dm build backend --build-arg DB_PROFILE=dm
    elif [ "$DB_TYPE" = "kingbase" ]; then
        docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" --profile kingbase build backend --build-arg DB_PROFILE=kingbase
    fi
    success "后端镜像构建完成"

    info "构建前端镜像..."
    docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" build frontend
    success "前端镜像构建完成"
}

# 启动服务
start_services() {
    info "启动服务（数据库类型: $DB_TYPE）..."
    if [ "$DB_TYPE" = "mysql" ]; then
        docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" --profile mysql up -d
    elif [ "$DB_TYPE" = "dm" ]; then
        docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" --profile dm up -d
    elif [ "$DB_TYPE" = "kingbase" ]; then
        docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" --profile kingbase up -d
    fi
    success "服务启动完成"
}

# 停止服务
stop_services() {
    info "停止所有服务..."
    docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" --profile mysql --profile dm --profile kingbase --profile rabbitmq down
    success "服务已停止"
}

# 重启服务
restart_services() {
    stop_services
    sleep 3
    start_services
}

# 查看日志
show_logs() {
    docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" logs -f backend
}

# 查看状态
show_status() {
    echo ""
    echo "============================================"
    echo "  GEO-SaaS 服务状态"
    echo "============================================"
    docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" ps
    echo ""
    info "后端健康检查: http://localhost:${SERVER_PORT:-8080}/actuator/health"
    info "前端访问地址: http://localhost"
    info "默认管理员: admin / admin123（首次登录后请修改密码）"
}

# 主函数
main() {
    local action="deploy"

    while [[ $# -gt 0 ]]; do
        case $1 in
            --db)
                DB_TYPE="$2"
                shift 2
                ;;
            --stop)
                action="stop"
                shift
                ;;
            --restart)
                action="restart"
                shift
                ;;
            --logs)
                action="logs"
                shift
                ;;
            --status)
                action="status"
                shift
                ;;
            --help)
                echo "用法: $0 [选项]"
                echo "  --db <类型>       数据库类型: mysql(默认), dm, kingbase"
                echo "  --stop            停止服务"
                echo "  --restart         重启服务"
                echo "  --logs            查看后端日志"
                echo "  --status          查看服务状态"
                echo "  --help            显示帮助"
                exit 0
                ;;
            *)
                error "未知选项: $1，使用 --help 查看帮助"
                ;;
        esac
    done

    check_docker

    case $action in
        deploy)
            info "========== GEO-SaaS 私有化部署 =========="
            info "数据库类型: $DB_TYPE"
            info "项目目录: $SCRIPT_DIR"
            echo ""
            check_env
            build_images
            start_services
            sleep 5
            show_status
            success "部署完成！"
            ;;
        stop)
            stop_services
            ;;
        restart)
            restart_services
            show_status
            ;;
        logs)
            show_logs
            ;;
        status)
            show_status
            ;;
    esac
}

main "$@"
