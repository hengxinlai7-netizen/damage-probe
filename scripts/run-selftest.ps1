param(
    [string]$ServerDir = 'C:\Users\hengx\Documents\deepseek-harness\default-workspace\_dp_server',
    [string]$Jar = '',
    [string]$LogName = 'damageprobe-selftest.log'
)

# 在隔离服务端目录里装 jar，跑 -Ddamageprobe.selftest=true，等服务器自己 halt。
# 注意：不要用 Start-Process（本机环境有 NO_PROXY/no_proxy 大小写重复键会炸），
# 这里用 cmd /c + 重定向，日志按 UTF-8 原样落盘。

$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
if ([string]::IsNullOrWhiteSpace($Jar)) {
    $Jar = Join-Path $project 'build\libs\damageprobe-1.2.1.jar'
}
if (!(Test-Path -LiteralPath $Jar)) {
    throw "jar not found: $Jar"
}
if (!(Test-Path -LiteralPath $ServerDir)) {
    throw "server dir not found: $ServerDir"
}

$mods = Join-Path $ServerDir 'mods'
$log = Join-Path $ServerDir $LogName
New-Item -ItemType Directory -Force $mods | Out-Null
Copy-Item -LiteralPath $Jar (Join-Path $mods (Split-Path $Jar -Leaf)) -Force
if (Test-Path -LiteralPath (Join-Path $ServerDir 'dpworld')) {
    Remove-Item -Recurse -Force -LiteralPath (Join-Path $ServerDir 'dpworld')
}

$java = 'C:\Program Files\Microsoft\jdk-21.0.8.9-hotspot\bin\java.exe'
$cmdFile = Join-Path $ServerDir 'run-damageprobe.cmd'
$cmdText = @"
@echo off
cd /d "%~dp0"
"$java" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Ddamageprobe.selftest=true @user_jvm_args.txt @libraries/net/minecraftforge/forge/1.20.1-47.2.20/win_args.txt nogui > "$log" 2>&1
"@
Set-Content -LiteralPath $cmdFile -Value $cmdText -Encoding ascii

Write-Output "running: $cmdFile"
cmd /c $cmdFile
Write-Output "server exit=$LASTEXITCODE"
if (Test-Path -LiteralPath $log) {
    Write-Output '--- SELFTEST ---'
    Select-String -LiteralPath $log -Pattern 'SELFTEST (FAIL|RESULT|DONE|PASS command.damageprobe|PASS enabled.off)' -Encoding utf8 |
        ForEach-Object { $_.Line }
    Write-Output '--- probe summaries ---'
    Select-String -LiteralPath $log -Pattern '\[co\.he\.da\.DamageProbe/\]' -Encoding utf8 |
        ForEach-Object { $_.Line }
    Write-Output "log: $log"
}
