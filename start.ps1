# =====================================================
# GEO-SaaS 全域AI搜索优化平台 - 一键启动脚本 (Windows)
# =====================================================
# 使用方法: 右键 -> 使用 PowerShell 运行
# =====================================================

$ErrorActionPreference = "Continue"
$GREEN = "Green"; $YELLOW = "Yellow"; $RED = "Red"; $CYAN = "Cyan"

# ---------- 配置 ----------
$BACKEND_DIR = Join-Path $PSScriptRoot "geo-saa-backend"
$FRONTEND_DIR = Join-Path $PSScriptRoot "geo-saa-frontend"
$BACKEND_JAR = Join-Path $BACKEND_DIR "target\geo-saa-backend.jar"
$BACKEND_PORT = 8080
$FRONTEND_PORT = 3000
$JDK17_PATH = "C:\Program Files\Microsoft\jdk-17.0.12.7-hotspot\bin\java"
$JDK17_DIR  = "C:\Program Files\Microsoft\jdk-17.0.12.7-hotspot"
$MAVEN_DIR  = "C:\ProgramData\chocolatey\lib\maven\apache-maven-3.9.16"
$G_JDK = $JDK17_PATH  # global mutable

function Write-Info  { param($m) Write-Host "  [INFO] $m" -ForegroundColor $GREEN }
function Write-Warn  { param($m) Write-Host "  [WARN] $m" -ForegroundColor $YELLOW }
function Write-Error { param($m) Write-Host "  [ERROR] $m" -ForegroundColor $RED }

function Show-Banner {
    Clear-Host
    Write-Host @"

╔══════════════════════════════════════════════════╗
║           GEO-SaaS 全域AI搜索优化平台              ║
║           一键启动脚本 v1.0                        ║
╚══════════════════════════════════════════════════╝

"@ -ForegroundColor $CYAN
}

# ===========================================
# 1. 环境检查
# ===========================================
function Check-Prerequisites {
    Write-Host "`n========== [1/6] 环境检查 ==========" -ForegroundColor $CYAN
    $allPass = $true

    # JDK 17
    if (Test-Path $G_JDK) {
        $ver = & $G_JDK -version 2>&1
        Write-Info "JDK 17: $($ver[0])"
    } else {
        Write-Warn "默认路径未找到 JDK 17，尝试从系统 PATH 查找..."
        $j = Get-Command java -ErrorAction SilentlyContinue
        if ($j) {
            $ver = & $j.Path -version 2>&1
            if ($ver -match "17") {
                $script:G_JDK = $j.Path
                Write-Info "JDK 17: $($ver[0])"
            } else {
                Write-Error "系统 Java 版本不是 17 (当前: $($ver[0]))"
                $allPass = $false
            }
        } else {
            Write-Error "JDK 17 未安装，请安装 Microsoft Build of OpenJDK 17"
            $allPass = $false
        }
    }

    # Node.js
    $n = Get-Command node -ErrorAction SilentlyContinue
    if ($n) {
        Write-Info "Node.js: $(node -v)"
    } else {
        Write-Error "Node.js 未安装"
        $allPass = $false
    }

    # MySQL
    try {
        $sock = New-Object System.Net.Sockets.TcpClient
        $sock.Connect("localhost", 3306)
        $sock.Close()
        Write-Info "MySQL: 已连接 (localhost:3306)"
    } catch {
        Write-Warn "MySQL 未连接 (localhost:3306)，请确保 MySQL 已启动"
    }

    # Redis
    try {
        $sock = New-Object System.Net.Sockets.TcpClient
        $sock.Connect("localhost", 6379)
        $sock.Close()
        Write-Info "Redis: 已连接 (localhost:6379)"
    } catch {
        Write-Warn "Redis 未连接 (localhost:6379)"
    }

    if (-not $allPass) { Write-Error "环境检查失败，请安装缺失依赖后重试"; exit 1 }
    Write-Info "环境检查通过"
}

# ===========================================
# 2. 释放端口
# ===========================================
function Free-Port {
    param($Port)
    $lines = netstat -ano | Select-String ":$Port\s"
    foreach ($line in $lines) {
        $parts = $line.ToString() -split '\s+'
        $pid = $parts[-1]
        if ($pid -match '^\d+$' -and $pid -ne '0') {
            Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
            Write-Info "端口 $Port 已释放 (PID: $pid)"
        }
    }
    Start-Sleep -Milliseconds 500
}

# ===========================================
# 3. 构建后端
# ===========================================
function Build-Backend {
    Write-Host "`n========== [2/6] 构建后端 ==========" -ForegroundColor $CYAN
    # 先释放后端端口，避免 JAR 文件被锁定
    Free-Port $BACKEND_PORT
    Start-Sleep -Seconds 1
    if (Test-Path $BACKEND_JAR) {
        Write-Info "后端 JAR 已存在，跳过构建"
        return
    }
    Push-Location $BACKEND_DIR
    try {
        $env:JAVA_HOME = $JDK17_DIR
        $env:Path = "$JDK17_DIR\bin;$MAVEN_DIR\bin;$env:Path"
        Write-Info "正在编译后端..."
        mvn clean package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw "Maven 构建失败" }
        Write-Info "后端构建成功"
    } finally { Pop-Location }
}

# ===========================================
# 4. 启动后端
# ===========================================
function Start-Backend {
    Write-Host "`n========== [3/6] 启动后端服务 ==========" -ForegroundColor $CYAN
    Free-Port $BACKEND_PORT

    if (-not (Test-Path $BACKEND_JAR)) {
        Write-Error "后端 JAR 不存在: $BACKEND_JAR"; exit 1
    }

    # 检查是否已在运行
    $procs = Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue
    $already = $procs | Where-Object { $_.CommandLine -like "*geo-saa-backend*" }
    if ($already) {
        Write-Info "后端服务已在运行 (PID: $($already.ProcessId))"
        return
    }

    $logFile = Join-Path $PSScriptRoot "backend.log"
    $procArgs = "-jar `"$BACKEND_JAR`" --spring.profiles.active=dev"
    $p = Start-Process -FilePath $G_JDK -ArgumentList $procArgs -WorkingDirectory $BACKEND_DIR -NoNewWindow -RedirectStandardOutput $logFile -RedirectStandardError "${logFile}.err" -PassThru
    Start-Sleep -Seconds 8

    if (Get-Process -Id $p.Id -ErrorAction SilentlyContinue) {
        Write-Info "后端服务已启动 (PID: $($p.Id), 日志: backend.log)"
        Write-Info "后端地址: http://localhost:$BACKEND_PORT"
    } else {
        Write-Error "后端启动失败，查看日志: backend.log 和 backend.log.err"
        exit 1
    }
}

# ===========================================
# 5. 安装前端依赖
# ===========================================
function Install-Frontend {
    Write-Host "`n========== [4/6] 安装前端依赖 ==========" -ForegroundColor $CYAN
    $nodeModules = Join-Path $FRONTEND_DIR "node_modules"
    if (Test-Path $nodeModules) {
        Write-Info "前端依赖已安装，跳过"
        return
    }
    Push-Location $FRONTEND_DIR
    try {
        npm install
        if ($LASTEXITCODE -ne 0) { throw "npm install 失败" }
        Write-Info "前端依赖安装完成"
    } finally { Pop-Location }
}

# ===========================================
# 6. 启动前端
# ===========================================
function Start-Frontend {
    Write-Host "`n========== [5/6] 启动前端服务 ==========" -ForegroundColor $CYAN
    Free-Port $FRONTEND_PORT

    # 检查是否已运行
    $procs = Get-CimInstance Win32_Process -Filter "Name='node.exe'" -ErrorAction SilentlyContinue
    $already = $procs | Where-Object { $_.CommandLine -like "*vite*" -and $_.CommandLine -like "*geo-saa-frontend*" }
    if ($already) {
        Write-Info "前端服务已在运行 (PID: $($already.ProcessId))"
        return
    }

    Push-Location $FRONTEND_DIR
    try {
        $logFile = Join-Path $PSScriptRoot "frontend.log"
        $p = Start-Process -FilePath "cmd.exe" -ArgumentList "/c npx vite --host" -WorkingDirectory $FRONTEND_DIR -NoNewWindow -RedirectStandardOutput $logFile -RedirectStandardError "${logFile}.err" -PassThru
        Start-Sleep -Seconds 5
        Write-Info "前端服务正在启动 (PID: $($p.Id), 日志: frontend.log)"
    } finally { Pop-Location }
}

# ===========================================
# 主流程
# ===========================================
Show-Banner
Check-Prerequisites
Build-Backend
Start-Backend
Install-Frontend
Start-Frontend

Write-Host "`n========== [6/6] 启动完成 ==========" -ForegroundColor $CYAN
Write-Host @"

╔══════════════════════════════════════════════════╗
║                   启动完成                        ║
╠══════════════════════════════════════════════════╣
║                                                  ║
║  前端地址:  http://localhost:3000                ║
║  后端地址:  http://localhost:8080                ║
║  后端日志:  backend.log                          ║
║  前端日志:  frontend.log                         ║
║                                                  ║
║  默认管理员: admin / admin123                    ║
║                                                  ║
║  提示: 如果前端端口 3000 被占用，                  ║
║        Vite 会自动切换到下一个可用端口。            ║
║                                                  ║
╚══════════════════════════════════════════════════╝

"@ -ForegroundColor $GREEN

Start-Sleep -Seconds 2
Start-Process "http://localhost:3000"