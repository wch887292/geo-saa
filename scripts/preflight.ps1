#Requires -Version 5.1
<#
.SYNOPSIS
    GEO-SaaS 环境就绪预检（Windows）

.DESCRIPTION
    与 scripts/preflight.sh 等价，Windows 用户无需 Git Bash / WSL 即可运行。
    检查 Docker 部署模式所需的本机依赖、端口占用、.env 配置完整性与资源余量，
    一次性列出"缺什么、为什么缺、怎么补"，避免反复试错。

.PARAMETER Local
    切换为本机源码运行模式（检查 JDK / Maven / Node / MySQL / Redis）。

.PARAMETER All
    两种模式都检查。

.EXAMPLE
    .\scripts\preflight.ps1
    .\scripts\preflight.ps1 -Local
    .\scripts\preflight.ps1 -All
#>
[CmdletBinding()]
param(
    [switch]$Local,
    [switch]$All
)

$ErrorActionPreference = 'Continue'
Set-StrictMode -Version Latest

$script:Pass = 0
$script:Warn = 0
$script:Fail = 0
$script:Missing = @()

# ---------- 输出helpers ----------
function Write-Ok([string]$Name, [string]$Detail = '') {
    $script:Pass++
    Write-Host ('  {0,-4} {1,-34} {2}' -f 'OK', $Name, $Detail) -ForegroundColor Green
}
function Write-Warn2([string]$Name, [string]$Detail = '') {
    $script:Warn++
    Write-Host ('  {0,-4} {1,-34} {2}' -f 'WARN', $Name, $Detail) -ForegroundColor Yellow
}
function Write-Miss([string]$Name, [string]$Detail = '') {
    $script:Fail++
    $script:Missing += $Name
    Write-Host ('  {0,-4} {1,-34} {2}' -f 'MISS', $Name, $Detail) -ForegroundColor Red
}
function Write-Section([string]$Text) {
    Write-Host ''
    Write-Host $Text -ForegroundColor Cyan
}
function Write-Hint([string]$Text) {
    Write-Host ('       {0}' -f $Text) -ForegroundColor DarkGray
}

# ---------- 工具 ----------
function Get-PortInUse([int]$Port) {
    try {
        return [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
    } catch {
        # 旧版 Windows 没有 Get-NetTCPConnection
        $lines = & netstat.exe -ano 2>$null | Select-String -Pattern ":$Port\s+.*LISTENING"
        return [bool]$lines
    }
}

function Get-RepoRoot {
    return (Split-Path -Parent $PSScriptRoot)
}

# ===========================================================
# Docker 部署模式
# ===========================================================
function Test-DockerMode {
    Write-Section '[1/5] Docker 部署模式'

    # --- Docker CLI ---
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $docker) {
        Write-Miss 'Docker CLI' '未安装 — https://www.docker.com/products/docker-desktop/'
        return
    }
    $ver = (& docker --version 2>$null) -join ' '
    Write-Ok 'Docker CLI' $ver.Trim()

    # --- daemon（最常见的坑：装了但没启动 / 引擎切错）---
    $daemonAlive = $false
    try { & docker info *> $null; $daemonAlive = ($LASTEXITCODE -eq 0) } catch { $daemonAlive = $false }

    if ($daemonAlive) {
        $osType = (& docker info --format '{{.OSType}}' 2>$null) -join ''
        if ($osType -eq 'linux') {
            Write-Ok 'Docker daemon' '运行中（Linux 容器）'
        } else {
            Write-Warn2 'Docker daemon' "运行中但为 $osType 引擎 — 右键托盘图标切到 Switch to Linux containers"
        }
    } else {
        Write-Miss 'Docker daemon' '未运行 — 启动 Docker Desktop；若报 WSL 相关错误，先执行 wsl --install 并重启'
    }

    # --- Compose v2 ---
    $composeOk = $false
    try { & docker compose version *> $null; $composeOk = ($LASTEXITCODE -eq 0) } catch { $composeOk = $false }
    if ($composeOk) {
        $cv = (& docker compose version --short 2>$null) -join ''
        Write-Ok 'Docker Compose v2' $cv.Trim()
    } elseif (Get-Command docker-compose -ErrorAction SilentlyContinue) {
        Write-Warn2 'Docker Compose' '仅 v1（docker-compose），建议升级 Docker Desktop 自带 v2'
    } else {
        Write-Miss 'Docker Compose' '缺失 — 升级 Docker Desktop 即自带 v2'
    }

    # --- 端口占用 ---
    Write-Section '[2/5] 端口占用（compose 默认映射）'
    $ports = @(80, 8080, 3306, 6379, 5672, 15672)
    $busy = @($ports | Where-Object { Get-PortInUse $_ })
    if ($busy.Count -eq 0) {
        Write-Ok 'compose 所需端口' '全部空闲'
    } else {
        Write-Warn2 '端口被占用' "以下端口已有监听: $($busy -join ', ')"
        Write-Hint '→ 关闭占用进程（Get-NetTCPConnection -LocalPort 8080 查 PID），'
        Write-Hint '  或在 .env 里改 FRONTEND_PORT / BACKEND_PORT / MYSQL_PORT / REDIS_PORT'
    }

    # --- .env ---
    Write-Section '[3/5] 配置'
    $root = Get-RepoRoot
    $envFile = Join-Path $root '.env'
    if (Test-Path $envFile) {
        Write-Ok '.env 文件' '已存在'

        $content = Get-Content $envFile -Encoding UTF8
        $kv = @{}
        foreach ($line in $content) {
            if ($line -match '^\s*([A-Z0-9_]+)\s*=\s*(.*)$') { $kv[$Matches[1]] = $Matches[2] }
        }

        $mustHave = @('JWT_SECRET', 'MYSQL_PASSWORD', 'RABBITMQ_PASSWORD', 'CORS_ALLOWED_ORIGINS')
        $absent = @($mustHave | Where-Object { -not $kv.ContainsKey($_) -or [string]::IsNullOrWhiteSpace($kv[$_]) })
        if ($absent.Count -gt 0) {
            Write-Miss '.env 必填项' "缺失: $($absent -join ', ')"
        } else {
            Write-Ok '.env 必填项' '齐全'
        }

        # CORS 通配符检查
        $cors = if ($kv.ContainsKey('CORS_ALLOWED_ORIGINS')) { $kv['CORS_ALLOWED_ORIGINS'] } else { '' }
        if ($cors -like '*`**') {
            Write-Miss 'CORS 配置' '含通配符 * — 请指定确切域名，否则后端拒绝启动'
        } else {
            Write-Ok 'CORS 配置' $cors
        }

        # JWT 强度：Base64 解码后 >= 32 字节
        $jwt = if ($kv.ContainsKey('JWT_SECRET')) { $kv['JWT_SECRET'] } else { '' }
        $jwtBytes = 0
        if ($jwt) {
            try {
                $jwtBytes = ([Convert]::FromBase64String($jwt)).Length
            } catch { $jwtBytes = 0 }
        }
        if ($jwtBytes -ge 32) {
            Write-Ok 'JWT_SECRET 强度' "$jwtBytes 字节（>= 32）"
        } else {
            Write-Miss 'JWT_SECRET 强度' "解码后 $jwtBytes 字节，需 >= 32 — 生成：openssl rand -base64 32"
        }

        # 默认口令守卫开关
        if ($kv.ContainsKey('DEFAULT_PASSWORD_ACTION') -and -not [string]::IsNullOrWhiteSpace($kv['DEFAULT_PASSWORD_ACTION'])) {
            Write-Warn2 '默认口令守卫' "DEFAULT_PASSWORD_ACTION=$($kv['DEFAULT_PASSWORD_ACTION'])（已放行默认口令，风险自负）"
        } else {
            Write-Warn2 '默认口令守卫' '未显式设置 → prod 档案默认 fail：首次部署须先改 admin 口令'
            Write-Hint '→ 见 README「首次部署」，或临时设 DEFAULT_PASSWORD_ACTION=disabled（自行承担风险）'
        }
    } else {
        Write-Warn2 '.env 文件' '不存在'
        Write-Hint '→ 推荐：.\scripts\deploy.ps1（自动生成随机密钥并校验）'
        Write-Hint '  或手动：Copy-Item .env.example .env 后按注释填写 JWT_SECRET / MYSQL_PASSWORD 等'
    }

    # --- 资源 ---
    Write-Section '[4/5] 资源'
    try {
        $free = (Get-PSDrive -Name ($root.Substring(0, 1)) -ErrorAction SilentlyContinue)
        if ($free) {
            $freeGb = [math]::Floor($free.Free / 1GB)
            if ($freeGb -lt 10) {
                Write-Warn2 '磁盘可用' "${freeGb}G — 首次构建镜像建议预留 10G 以上"
            } else {
                Write-Ok '磁盘可用' "${freeGb}G"
            }
        }
    } catch { }

    $os = Get-CimInstance Win32_OperatingSystem -ErrorAction SilentlyContinue
    if ($os) {
        $freeMemMb = [math]::Floor($os.FreePhysicalMemory / 1024)
        if ($freeMemMb -lt 2048) {
            Write-Warn2 '可用内存' "${freeMemMb}MB — 构建前端时可能 OOM，建议关闭其他占内存程序"
        } else {
            Write-Ok '可用内存' "${freeMemMb}MB"
        }
    }

    Write-Section '[5/5] 下一步'
    if ($script:Fail -eq 0) {
        Write-Host '  环境就绪，可执行：' -ForegroundColor Green
        Write-Host ''
        Write-Host '     .\scripts\deploy.ps1' -ForegroundColor Green
        Write-Host ''
    } else {
        Write-Host "  有 $script:Fail 项缺失，先补齐上面标 MISS 的条目。" -ForegroundColor Red
    }
}

# ===========================================================
# 本机源码运行模式
# ===========================================================
function Test-LocalMode {
    Write-Section '[1/5] 运行时'

    # --- JDK ---
    $java = Get-Command java -ErrorAction SilentlyContinue
    if (-not $java) {
        Write-Miss 'JDK' '未安装 — 需要 JDK 17 或更高（https://adoptium.net/）'
    } else {
        $jv = ((& java -version 2>&1) | Select-Object -First 1) -join ''
        if ($jv -match '"(\d+)') {
            $major = [int]$Matches[1]
            if ($major -ge 17) { Write-Ok 'JDK' "版本 $major（>= 17）" }
            else { Write-Miss 'JDK' "版本 $major，项目要求 17+ — https://adoptium.net/" }
        } else {
            Write-Warn2 'JDK' "已安装但无法解析版本: $jv"
        }
    }

    # --- Maven ---
    if (Get-Command mvn -ErrorAction SilentlyContinue) {
        $mv = ((& mvn -v 2>&1) | Select-Object -First 1) -join ''
        Write-Ok 'Maven' $mv
    } elseif (Test-Path (Join-Path (Get-RepoRoot) 'mvnw.cmd')) {
        Write-Ok 'Maven' '使用项目自带 mvnw.cmd'
    } else {
        Write-Miss 'Maven' '未安装且无 mvnw.cmd — https://maven.apache.org/download.cgi'
    }

    # --- Node（Vite 8 要求 20.19+ / 22.12+，否则前端构建报 crypto.hash 不可用）---
    $node = Get-Command node -ErrorAction SilentlyContinue
    if (-not $node) {
        Write-Warn2 'Node.js' '未安装（仅构建前端时需要；用 Docker 模式可不装）'
    } else {
        $nv = ((& node -v 2>&1) | Select-Object -First 1) -join ''
        $nodeOk = $false
        if ($nv -match '^v(\d+)\.(\d+)') {
            $majNode = [int]$Matches[1]; $minNode = [int]$Matches[2]
            if (($majNode -eq 20 -and $minNode -ge 19) -or ($majNode -eq 22 -and $minNode -ge 12) -or ($majNode -ge 23)) { $nodeOk = $true }
        }
        if ($nodeOk) { Write-Ok 'Node.js' "$nv（满足 Vite 8：20.19+ / 22.12+）" }
        else { Write-Warn2 'Node.js' "$nv，需要 20.19+ 或 22.12+（Vite 8 要求，否则前端构建报 crypto.hash 不可用）— https://nodejs.org/" }
    }

    Write-Section '[2/5] 数据存储'
    if (Get-PortInUse 3306) {
        Write-Ok 'MySQL' '3306 端口有监听'
        if (Get-Command mysql -ErrorAction SilentlyContinue) {
            Write-Ok 'mysql 客户端' (((& mysql --version 2>&1) | Select-Object -First 1) -join '')
        } else {
            Write-Warn2 'mysql 客户端' '未安装（可选，仅手工查库时需要）'
        }
    } else {
        Write-Miss 'MySQL' '3306 无监听 — 本机源码模式必须先起 MySQL 8 并建 geo_saa 库'
    }

    if (Get-PortInUse 6379) {
        Write-Ok 'Redis' '6379 端口有监听'
    } else {
        Write-Miss 'Redis' '6379 无监听 — 本机源码模式必须先起 Redis（令牌黑名单/限流依赖）'
    }

    Write-Section '[3/5] 可选依赖'
    if (Get-PortInUse 5672) {
        Write-Ok 'RabbitMQ' '5672 有监听（批量生成走 MQ 异步）'
    } else {
        Write-Warn2 'RabbitMQ' '未运行 — 可接受：批量生成/分发会自动降级为本地同步执行'
    }

    $py = Get-Command python -ErrorAction SilentlyContinue
    if (-not $py) { $py = Get-Command python3 -ErrorAction SilentlyContinue }
    if ($py) {
        Write-Ok 'Python' (((& python --version 2>&1) | Select-Object -First 1) -join '')
    } else {
        Write-Warn2 'Python' '未安装（仅运行 smoke_test.py / func_test.py 验收时需要）'
    }

    Write-Section '[4/5] 端口'
    if (Get-PortInUse 8080) {
        Write-Warn2 '后端端口 8080' '已被占用 — 若已有旧实例在跑，先停掉再启动'
    } else {
        Write-Ok '后端端口 8080' '空闲'
    }

    Write-Section '[5/5] 下一步'
    if ($script:Fail -eq 0) {
        Write-Host '  环境就绪，本机运行顺序：' -ForegroundColor Green
        Write-Host ''
        Write-Host '     cd geo-saa-backend; mvn spring-boot:run "-Dspring-boot.run.profiles=dev"' -ForegroundColor Green
        Write-Host '     cd geo-saa-frontend; npm install; npm run dev' -ForegroundColor Green
        Write-Host '     验收：$env:SMOKE_BASE="http://127.0.0.1:8080"; python smoke_test.py' -ForegroundColor Green
        Write-Host ''
    } else {
        Write-Host "  有 $script:Fail 项缺失，先补齐上面标 MISS 的条目。" -ForegroundColor Red
    }
}

# ===========================================================
# 入口
# ===========================================================
Write-Host '======================================================================' -ForegroundColor DarkGray
Write-Host " GEO-SaaS 环境预检   目录: $(Get-RepoRoot)" -ForegroundColor DarkGray
Write-Host '======================================================================' -ForegroundColor DarkGray

if ($Local) {
    Test-LocalMode
} elseif ($All) {
    Test-DockerMode
    Write-Host ''
    Write-Host ('-' * 70) -ForegroundColor DarkGray
    Test-LocalMode
} else {
    Test-DockerMode
}

Write-Host ''
Write-Host '======================================================================' -ForegroundColor DarkGray
if ($script:Fail -eq 0) {
    Write-Host ("  结论: 就绪（PASS {0} / WARN {1} / MISS {2}）" -f $script:Pass, $script:Warn, $script:Fail) -ForegroundColor Green
    Write-Host '======================================================================' -ForegroundColor DarkGray
    exit 0
} else {
    Write-Host ("  结论: 未就绪（PASS {0} / WARN {1} / MISS {2}）" -f $script:Pass, $script:Warn, $script:Fail) -ForegroundColor Red
    Write-Host ("  缺失项: {0}" -f ($script:Missing -join ' ')) -ForegroundColor Red
    Write-Host '======================================================================' -ForegroundColor DarkGray
    exit 1
}