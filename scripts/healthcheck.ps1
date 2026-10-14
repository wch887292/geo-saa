#Requires -Version 5.1
<#
.SYNOPSIS
    GEO-SaaS 部署验收脚本（Windows）

.DESCRIPTION
    与 scripts/healthcheck.sh 等价。对运行中的实例做端到端检查：
      1. 容器状态（仅 Docker Compose 环境）
      2. 健康端点（后端 / 前端 / 前端 /healthz）
      3. 认证（登录成功、错误密码返回 401）
      4. 鉴权边界（无 token / 非法 token 均被拒）
      5. 核心业务接口可访问
      6. 已修复缺陷的回归验证（批量生成不再 500、重复提交返回 400）

    退出码 0 = 全部通过；1 = 有失败项

.EXAMPLE
    .\scripts\healthcheck.ps1
    $env:BACKEND_URL='http://127.0.0.1:8080'; .\scripts\healthcheck.ps1
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Continue'
# PS5.1 的 Invoke-WebRequest 默认会往控制台刷进度条（"正在读取 Web 响应"），
# 在非交互输出里既噪音又可能被当作错误内容，这里全局关掉。
# 注意：不能用 -ProgressAction 参数，那是 PS6+ 才有的，PS5.1 传入会直接抛参数绑定错误。
$ProgressPreference = 'SilentlyContinue'

$Backend = if ($env:BACKEND_URL) { $env:BACKEND_URL } else { 'http://localhost:8080' }
$Frontend = if ($env:FRONTEND_URL) { $env:FRONTEND_URL } else { 'http://localhost' }
$AdminUser = if ($env:ADMIN_USERNAME) { $env:ADMIN_USERNAME } else { 'admin' }
$AdminPass = if ($env:ADMIN_PASSWORD) { $env:ADMIN_PASSWORD } else { 'admin123' }

$script:Pass = 0
$script:Fail = 0

function Ok  ([string]$n, [string]$d = '') {
    $script:Pass++
    Write-Host ('  {0,-4} {1,-48} {2}' -f 'PASS', $n, $d) -ForegroundColor Green
}
function Bad ([string]$n, [string]$d = '') {
    $script:Fail++
    Write-Host ('  {0,-4} {1,-48} {2}' -f 'FAIL', $n, $d) -ForegroundColor Red
}

# 返回 HTTP 状态码；异常时返回 0
function Get-Code([string]$Url, [hashtable]$Headers = @{}, [string]$Method = 'GET', [string]$Body = $null) {
    try {
        $params = @{
            Uri             = $Url
            Method          = $Method
            Headers         = $Headers
            TimeoutSec      = 30
            UseBasicParsing = $true
            ErrorAction     = 'Stop'
        }
        if ($Body) {
            $params.Body = $Body
            $params.ContentType = 'application/json'
        }
        $resp = Invoke-WebRequest @params
        return [int]$resp.StatusCode
    } catch [System.Net.WebException] {
        if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
        return 0
    } catch {
        return 0
    }
}

function Get-Body([string]$Url, [hashtable]$Headers = @{}, [string]$Method = 'GET', [string]$Body = $null) {
    try {
        $params = @{
            Uri             = $Url
            Method          = $Method
            Headers         = $Headers
            TimeoutSec      = 30
            UseBasicParsing = $true
            ErrorAction     = 'Stop'
        }
        if ($Body) {
            $params.Body = $Body
            $params.ContentType = 'application/json'
        }
        $resp = Invoke-WebRequest @params
        return $resp.Content
    } catch {
        return ''
    }
}

Write-Host '======================================================================'
Write-Host (" GEO-SaaS 部署验收   backend={0}  frontend={1}" -f $Backend, $Frontend)
Write-Host '======================================================================'

# ---------- 1. 容器状态 ----------
Write-Host ''
Write-Host '[1/6] 容器状态' -ForegroundColor Cyan
$hasCompose = $false
if (Get-Command docker -ErrorAction SilentlyContinue) {
    try { & docker compose ps *> $null; $hasCompose = ($LASTEXITCODE -eq 0) } catch { $hasCompose = $false }
}
if ($hasCompose) {
    foreach ($svc in @('mysql', 'redis', 'rabbitmq', 'backend', 'frontend')) {
        $health = ((& docker compose ps --format '{{.Health}}' $svc 2>$null) | Select-Object -First 1) -join ''
        $state = ((& docker compose ps --format '{{.State}}' $svc 2>$null) | Select-Object -First 1) -join ''
        if ($health -eq 'healthy') { Ok "$svc healthy" }
        elseif ([string]::IsNullOrWhiteSpace($state) -or $state -eq 'exited') { Bad "$svc 未运行" $state }
        else { Bad "$svc 状态异常" "$state/$health" }
    }
} else {
    Write-Host '  —— 跳过（非 Docker Compose 环境）' -ForegroundColor DarkGray
}

# ---------- 2. 健康端点 ----------
Write-Host ''
Write-Host '[2/6] 健康检查' -ForegroundColor Cyan
$hc = Get-Code "$Backend/api/v1/system/health"
if ($hc -eq 200) { Ok '后端健康检查' 'HTTP 200' } else { Bad '后端健康检查' "HTTP $hc" }

$fc = Get-Code "$Frontend/"
if ($fc -eq 200) { Ok '前端可访问' 'HTTP 200' } else { Bad '前端可访问' "HTTP $fc" }

$hz = Get-Code "$Frontend/healthz"
if ($hz -eq 200) { Ok '前端容器探活端点' '/healthz' } else { Bad '前端容器探活端点' "HTTP $hz" }

# ---------- 3. 认证 ----------
Write-Host ''
Write-Host '[3/6] 认证' -ForegroundColor Cyan
$loginBody = (@{ username = $AdminUser; password = $AdminPass } | ConvertTo-Json -Compress)
$loginResp = Get-Body "$Backend/api/v1/auth/login" @{} 'POST' $loginBody
$token = ''
if ($loginResp) {
    try { $token = ($loginResp | ConvertFrom-Json).data.token } catch { $token = '' }
}
if ($token) {
    Ok '管理员登录' '已获取 token'
} else {
    Bad '管理员登录' "登录失败（若已改密，请设`$env:ADMIN_PASSWORD 后重试）"
    Write-Host ''
    Write-Host '======================================================================'
    Write-Host (" 结果: {0} 通过 / {1} 失败 —— 登录失败，后续检查已跳过" -f $script:Pass, $script:Fail)
    Write-Host '======================================================================'
    exit 1
}

# 登录失败必须返回 401，而不是 200 + body 报错
$wrongCode = Get-Code "$Backend/api/v1/auth/login" @{} 'POST' (@{ username = $AdminUser; password = 'definitely_wrong_password' } | ConvertTo-Json -Compress)
if ($wrongCode -eq 401) { Ok '错误密码返回 401' '' } else { Bad '错误密码返回 401' "实际 HTTP $wrongCode" }

$auth = @{ Authorization = "Bearer $token" }

# ---------- 4. 鉴权边界 ----------
Write-Host ''
Write-Host '[4/6] 鉴权边界' -ForegroundColor Cyan
$noTok = Get-Code "$Backend/api/v1/content/list?pageNum=1&pageSize=5"
if ($noTok -eq 401) { Ok '无 token 访问被拒' 'HTTP 401' } else { Bad '无 token 访问被拒' "HTTP $noTok" }

$badTok = Get-Code "$Backend/api/v1/content/list?pageNum=1&pageSize=5" @{ Authorization = 'Bearer invalid.token.value' }
if ($badTok -eq 401) { Ok '非法 token 被拒' 'HTTP 401' } else { Bad '非法 token 被拒' "HTTP $badTok" }

# ---------- 5. 核心业务 ----------
Write-Host ''
Write-Host '[5/6] 核心业务' -ForegroundColor Cyan
foreach ($api in @('/api/v1/statistics/dashboard', '/api/v1/content/templates',
    '/api/v1/distribute/channels', '/api/v1/system/model-config')) {
    $c = Get-Code "$Backend$api" $auth
    if ($c -eq 200) { Ok "GET $api" '' } else { Bad "GET $api" "HTTP $c" }
}

# ---------- 6. 已修复缺陷回归验证 ----------
Write-Host ''
Write-Host '[6/6] 已修复缺陷回归验证' -ForegroundColor Cyan
$stamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()

# 注意：PS 的 ConvertTo-Json 会把单元素数组压成对象（@(...) | ConvertTo-Json 得到 {...}），
# 而 batch-generate 的契约是"顶层数组"，压成对象会被后端判为格式错误返回 400。
# 因此这里手工包一层方括号，保证序列化结果是 [{"..."}]。
$batchBody = '[{"title":"验收探针-' + $stamp + '","contentType":"ARTICLE","brandName":"验收","keywords":"验收"}]'
$batchCode = Get-Code "$Backend/api/v1/content/batch-generate" $auth 'POST' $batchBody
if ($batchCode -eq 200) { Ok '批量生成不再 500（原缺陷：AmqpConnectException）' 'HTTP 200' }
else { Bad '批量生成不再 500（原缺陷：AmqpConnectException）' "HTTP $batchCode" }

# 唯一约束冲突应为 400 而非 500
$brand = @{ brandName = "验收探针品牌-$stamp"; industry = '科技' } | ConvertTo-Json -Compress
$code1 = Get-Code "$Backend/api/v1/knowledge/brands" $auth 'POST' $brand
$code2 = Get-Code "$Backend/api/v1/knowledge/brands" $auth 'POST' $brand
if ($code2 -eq 400) { Ok '重复提交返回 400（原为 500 服务器内部错误）' "首次=$code1 重复=$code2" }
else { Bad '重复提交返回 400（原为 500 服务器内部错误）' "首次=$code1 重复=$code2" }

Write-Host ''
Write-Host '======================================================================'
Write-Host (" 结果: {0} 通过 / {1} 失败" -f $script:Pass, $script:Fail)
Write-Host '======================================================================'
if ($script:Fail -gt 0) { exit 1 } else { exit 0 }