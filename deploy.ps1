#Requires -Version 5.1
<#
    已移至 scripts/deploy.ps1

    本文件仅为兼容旧文档/旧习惯保留，实际逻辑在 scripts/deploy.ps1。
    与旧版的差别（重要）：
      - 旧版先 mvn package 再交给 compose 重复构建一次，白白多花几分钟
      - 旧版不生成 .env，JWT_SECRET 缺失会导致后端 fail-fast 起不来
      - 旧版固定用 docker-compose（v1），现已统一 v2 语法
      - 旧版固定 Start-Sleep 15 就宣称"部署完成"，不检查健康状态
#>
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)
Write-Host '提示：已转发到 scripts\deploy.ps1' -ForegroundColor Cyan
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'scripts\deploy.ps1') @args
exit $LASTEXITCODE