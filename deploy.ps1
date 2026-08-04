# GEO-SaaS 平台部署脚本 (Windows)
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  GEO-SaaS 全域AI搜索优化平台 部署脚本" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

# 检查 Docker
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "错误: Docker 未安装" -ForegroundColor Red
    exit 1
}

# 检查 docker-compose
$COMPOSE_CMD = "docker-compose"
if (-not (Get-Command docker-compose -ErrorAction SilentlyContinue)) {
    Write-Host "docker-compose 未找到，尝试 docker compose" -ForegroundColor Yellow
    $COMPOSE_CMD = "docker compose"
}

Write-Host "[1/4] 构建后端项目" -ForegroundColor Green
Push-Location geo-saa-backend
if (Get-Command mvn -ErrorAction SilentlyContinue) {
    mvn clean package -DskipTests
} else {
    Write-Host "Maven 未安装，使用 Docker 编译" -ForegroundColor Yellow
    docker run --rm -v "${PWD}:/app" -w /app maven:3.9-openjdk-17 mvn clean package -DskipTests
}
Pop-Location

Write-Host "[2/4] 构建并启动所有服务" -ForegroundColor Green
Invoke-Expression "$COMPOSE_CMD up -d --build"

Write-Host "[3/4] 等待服务启动" -ForegroundColor Green
Write-Host "等待数据库初始化..." -ForegroundColor Yellow
Start-Sleep -Seconds 15

Write-Host "[4/4] 部署完成" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  前端地址: http://localhost" -ForegroundColor Green
Write-Host "  后端地址: http://localhost:8080" -ForegroundColor Green
Write-Host "  RabbitMQ管理: http://localhost:15672" -ForegroundColor Green
Write-Host ""
Write-Host "  默认管理员账号: admin" -ForegroundColor Yellow
Write-Host "  默认管理员密码: admin123" -ForegroundColor Yellow
Write-Host "=========================================" -ForegroundColor Cyan