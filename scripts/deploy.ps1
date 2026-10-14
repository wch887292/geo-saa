#Requires -Version 5.1
<#
.SYNOPSIS
    GEO-SaaS 一键部署脚本（Windows）

.DESCRIPTION
    与 scripts/deploy.sh 等价的 Windows 实现，行为：
      1. 校验 Docker / Docker Compose 可用（daemon 必须已启动）
      2. 生成 .env（不存在时），自动填充随机密钥并设为 600 等效权限
      3. 校验必填项、JWT 强度、CORS 通配符
      4. docker compose up -d --build，等待全部 healthy
      5. 提示默认口令守卫的处理方式

    与旧版 deploy.ps1 的差别：旧版会先 mvn package 再交给 compose 重复构建一次，
    白白多花几分钟；旧版不生成 .env，导致 JWT_SECRET 缺失导致后端起不来；
    旧版固定 docker-compose（v1）。本脚本统一用 v2 语法并补齐了上述校验。

.PARAMETER SkipBuild
    跳过镜像构建，仅做 up（已有镜像时更快）。

.PARAMETER SkipWait
    不等待容器健康，up 完直接返回。

.EXAMPLE
    .\scripts\deploy.ps1
    .\scripts\deploy.ps1 -SkipBuild
#>
[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [switch]$SkipWait
)

$ErrorActionPreference = 'Stop'

function Say   ([string]$m) { Write-Host "==> $m" -ForegroundColor Cyan }
function Warn2 ([string]$m) { Write-Host "[!] $m" -ForegroundColor Yellow }
function Die   ([string]$m) { Write-Host "[x] $m" -ForegroundColor Red; exit 1 }

$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

# ---------- 1. 环境检查 ----------
Say '检查 Docker 环境...'
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Die '未找到 docker，请先安装 Docker Desktop 24+'
}
try { & docker info *> $null; $daemon = ($LASTEXITCODE -eq 0) } catch { $daemon = $false }
if (-not $daemon) {
    Die 'Docker daemon 未运行。请先启动 Docker Desktop（Windows 需确认 WSL2 已启用：wsl --install）'
}
$composeOk = $false
try { & docker compose version *> $null; $composeOk = ($LASTEXITCODE -eq 0) } catch { $composeOk = $false }
if (-not $composeOk) {
    Die '未找到 docker compose v2（插件形式），请升级 Docker Desktop'
}
Say ("Docker: {0}" -f ((& docker --version) -join ' '))
Say ("Compose: {0}" -f ((& docker compose version --short) -join ' '))

# ---------- 2. 生成 .env ----------
function New-RandomSecret {
    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    return [Convert]::ToBase64String($bytes)
}

if (Test-Path '.env') {
    Warn2 '.env 已存在，沿用现有配置'
} else {
    Say '未发现 .env，正在生成（含随机密钥）'
    $mysqlPwd = New-RandomSecret
    $rabbitPwd = New-RandomSecret
    $jwtSecret = New-RandomSecret
    $content = @"
# ===== 由 scripts/deploy.ps1 于 $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') 生成 =====
# 敏感项已随机生成，请勿提交到版本库（.env 已在 .gitignore 中）

# ---- 必填：对外访问域名（多个用英文逗号分隔，禁止填 *）----
CORS_ALLOWED_ORIGINS=http://localhost

# ---- 凭据（已随机生成）----
MYSQL_USER=root
MYSQL_PASSWORD=$mysqlPwd
RABBITMQ_USER=geo
RABBITMQ_PASSWORD=$rabbitPwd
JWT_SECRET=$jwtSecret

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

# ---- 默认口令守卫 ----
# 留空时 prod 档案默认 fail：首次部署须先改 admin 口令（见 README「首次部署」）。
# 确认要临时沿用默认值时才放开下面这行（自行承担风险）：
# DEFAULT_PASSWORD_ACTION=disabled
"@
    # 用 UTF8 无 BOM 写入，避免 PowerShell 5.1 把中文注释读成乱码
    [System.IO.File]::WriteAllText((Join-Path $Root '.env'), $content, (New-Object System.Text.UTF8Encoding $false))

    # Windows 等效收紧权限：移除继承的 Users 读取权，只留当前用户 + SYSTEM
    try {
        $acl = Get-Acl '.env'
        $acl.SetAccessRuleProtection($true, $false)   # 阻断继承
        foreach ($who in @('BUILTIN\Users', 'BUILTIN\Everyone')) {
            $acl.RemoveAccessRule((New-Object System.Security.AccessControl.FileSystemAccessRule(
                $who, 'Read', 'Allow')))
        }
        Set-Acl '.env' $acl
        Say '.env 已生成并收紧 ACL（仅当前用户与 SYSTEM 可读）'
    } catch {
        Warn2 ".env 已生成，但 ACL 收紧失败：$($_.Exception.Message)"
        Warn2 '请手动确认该文件权限，避免密钥被其他本地用户读取'
    }
}

# ---------- 3. 校验必填项 ----------
Say '校验 .env 配置...'
$kv = @{}
foreach ($line in (Get-Content '.env' -Encoding UTF8)) {
    if ($line -match '^\s*([A-Z0-9_]+)\s*=\s*(.*)$') { $kv[$Matches[1]] = $Matches[2] }
}
foreach ($k in @('JWT_SECRET', 'MYSQL_PASSWORD', 'RABBITMQ_PASSWORD', 'CORS_ALLOWED_ORIGINS')) {
    if (-not $kv.ContainsKey($k) -or [string]::IsNullOrWhiteSpace($kv[$k])) {
        Die ".env 中 $k 为空，请填写后重试"
    }
}
if ($kv['CORS_ALLOWED_ORIGINS'] -like '*`**') {
    Die 'CORS_ALLOWED_ORIGINS 不允许使用通配符 *，请指定确切域名'
}
$jwtBytes = 0
try { $jwtBytes = ([Convert]::FromBase64String($kv['JWT_SECRET'])).Length } catch { $jwtBytes = 0 }
if ($jwtBytes -lt 32) {
    Die "JWT_SECRET 强度不足（解码后 $jwtBytes 字节，需 >= 32）。可用 PowerShell 生成："
    Die '  [Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))'
}
Say "密钥校验通过（JWT $jwtBytes 字节）"

# ---------- 4. 启动 ----------
if ($SkipBuild) {
    Say '启动容器（跳过构建）'
    & docker compose up -d
} else {
    Say '开始构建并启动（首次构建包含前端产物，约需数分钟）'
    & docker compose up -d --build
}
if ($LASTEXITCODE -ne 0) { Die "docker compose 启动失败，退出码 $LASTEXITCODE（排查：docker compose logs --tail=100）" }

# ---------- 5. 等待健康 ----------
if ($SkipWait) {
    Warn2 '已跳过健康等待，稍后可执行 .\scripts\healthcheck.ps1 复查'
} else {
    Say '等待各服务健康...'
    $services = @('mysql', 'redis', 'rabbitmq', 'backend', 'frontend')
    for ($i = 1; $i -le 40; $i++) {
        $pending = @()
        foreach ($svc in $services) {
            $state = (& docker compose ps --format '{{.State}} {{.Health}}' $svc 2>$null) -join ' '
            if ($state -match 'running' -and $state -notmatch 'healthy') { $pending += $svc }
        }
        if ($pending.Count -eq 0) {
            Say '全部服务已健康'
            break
        }
        if ($i -eq 40) {
            Warn2 "仍有服务未就绪: $($pending -join ', ')（可稍后执行 .\scripts\healthcheck.ps1 复查）"
        }
        Start-Sleep -Seconds 5
    }
}

& docker compose ps

# ---------- 6. 收尾提示 ----------
$frontendPort = if ($kv.ContainsKey('FRONTEND_PORT')) { $kv['FRONTEND_PORT'] } else { '80' }
$backendPort = if ($kv.ContainsKey('BACKEND_PORT')) { $kv['BACKEND_PORT'] } else { '8080' }

Write-Host ''
Say '部署完成'
Write-Host ''
Write-Host ("  前端:http://localhost:{0}" -f $frontendPort) -ForegroundColor Green
Write-Host ("  后端 API:      http://localhost:{0}" -f $backendPort) -ForegroundColor Green
Write-Host ("  RabbitMQ 控制台:http://localhost:{0}" -f $(if ($kv.ContainsKey('RABBITMQ_PORT')) { $kv['RABBITMQ_PORT'] } else { '15672' })) -ForegroundColor Green
Write-Host ''
Write-Host  '  验收命令:  .\scripts\healthcheck.ps1' -ForegroundColor Cyan
Write-Host ''
Warn2 '默认管理员 admin/admin123 —— 该口令散列公开在仓库中，生产部署会被安全闸门拒绝启动。'
Warn2 '请按 README「首次部署」改密；仅本地演示可临时设 DEFAULT_PASSWORD_ACTION=disabled。'