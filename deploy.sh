#!/bin/bash
# GEO-SaaS 平台部署脚本
set -e

echo "========================================="
echo "  GEO-SaaS 全域AI搜索优化平台 部署脚本"
echo "========================================="

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 检查 Docker
if ! command -v docker &> /dev/null; then
    echo -e "${RED}错误: Docker 未安装${NC}"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo -e "${YELLOW}docker-compose 未找到，尝试使用 docker compose${NC}"
    COMPOSE_CMD="docker compose"
else
    COMPOSE_CMD="docker-compose"
fi

# 检查环境变量文件
if [ ! -f .env ]; then
    echo -e "${YELLOW}.env 文件不存在，从模板创建${NC}"
    cp .env.example .env 2>/dev/null || true
fi

echo -e "${GREEN}[1/4] 构建后端项目${NC}"
cd geo-saa-backend
if command -v mvn &> /dev/null; then
    mvn clean package -DskipTests
else
    echo -e "${YELLOW}Maven 未安装，使用 Docker 编译${NC}"
    docker run --rm -v "$(pwd)":/app -w /app maven:3.9-openjdk-17 mvn clean package -DskipTests
fi
cd ..

echo -e "${GREEN}[2/4] 构建并启动所有服务${NC}"
$COMPOSE_CMD up -d --build

echo -e "${GREEN}[3/4] 等待服务启动${NC}"
echo "等待数据库初始化..."
sleep 15

echo -e "${GREEN}[4/4] 部署完成${NC}"
echo "========================================="
echo -e "  前端地址: ${GREEN}http://localhost${NC}"
echo -e "  后端地址: ${GREEN}http://localhost:8080${NC}"
echo -e "  RabbitMQ管理: ${GREEN}http://localhost:15672${NC}"
echo ""
echo "  默认管理员账号: admin"
echo "  默认管理员密码: admin123"
echo "========================================="