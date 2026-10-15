@echo off
chcp 65001 >nul
title GEO-SaaS Backend - Local (0.0.0.0:6007)

echo ============================================
echo   GEO-SaaS 后端本地部署
echo   监听地址: 0.0.0.0:6007
echo   访问地址: http://localhost:6007
echo   健康检查: http://localhost:6007/actuator/health
echo ============================================
echo.

cd /d "%~dp0geo-saa-backend"

REM 检查 JAR 是否存在，不存在则构建
if not exist "target\geo-saa-backend.jar" (
    echo [INFO] 未找到 JAR 包，开始构建...
    call mvn clean package -DskipTests -B
    if errorlevel 1 (
        echo [ERROR] 构建失败！
        pause
        exit /b 1
    )
    echo [SUCCESS] 构建完成
    echo.
)

REM 启动服务
echo [INFO] 启动 GEO-SaaS 后端服务...
echo [INFO] 按 Ctrl+C 停止服务
echo.

java -XX:+UseG1GC -Xms512m -Xmx1024m -Dfile.encoding=UTF-8 ^
     -jar target\geo-saa-backend.jar ^
     --spring.profiles.active=local

pause
