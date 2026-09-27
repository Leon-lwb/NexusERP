# 启动隔离演示应用，不停止或重新配置已有日常开发后端。
param(
    [ValidateRange(1, 65535)][int]$Port = 8080,
    [switch]$PreflightOnly
)
$ErrorActionPreference = 'Stop'
$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..\..\..'))
$jar = Join-Path $root 'ruoyi-admin\target\ruoyi-admin.jar'
$secret = Join-Path $PSScriptRoot '..\.env'
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
    throw 'Missing backend jar. From the ERP repository run: mvn -pl ruoyi-admin -am package -DskipTests'
}
if (-not (Test-Path -LiteralPath $secret -PathType Leaf)) {
    throw 'Missing gitignored demo .env. Run prepare-decision-demo.py --apply first.'
}

$settings = @{}
foreach ($line in Get-Content -LiteralPath $secret -Encoding UTF8) {
    if ($line -notmatch '^(DECISION_DEMO_[A-Z_]+)=([A-Za-z0-9_./-]+)$') {
        throw 'Unexpected demo .env format; refusing to load credentials'
    }
    if ($settings.ContainsKey($Matches[1])) { throw 'Duplicate demo .env key' }
    $settings[$Matches[1]] = $Matches[2]
}
# 不向应用传递仅供环境准备使用的 MySQL root 凭据。
$required = @(
    'DECISION_DEMO_DB_HOST', 'DECISION_DEMO_DB_PORT', 'DECISION_DEMO_DB_USER', 'DECISION_DEMO_DB_PASSWORD',
    'DECISION_DEMO_REDIS_HOST', 'DECISION_DEMO_REDIS_PORT', 'DECISION_DEMO_REDIS_PASSWORD',
    'DECISION_DEMO_MQ_HOST', 'DECISION_DEMO_MQ_PORT', 'DECISION_DEMO_MQ_USER',
    'DECISION_DEMO_MQ_PASSWORD', 'DECISION_DEMO_MQ_VHOST'
)
if ($settings.Count -ne ($required.Count + 1) -or -not $settings.ContainsKey('DECISION_DEMO_MYSQL_ROOT_PASSWORD')) {
    throw 'Unexpected demo .env keys'
}
foreach ($key in $required) {
    if (-not $settings.ContainsKey($key)) { throw "Missing demo config key $key" }
}
& python (Join-Path $PSScriptRoot 'prepare-decision-demo.py') --verify
if ($LASTEXITCODE -ne 0) { throw 'Isolated runtime verification failed; backend was not started' }
if (@(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue).Count -gt 0) {
    throw "Port $Port is occupied. Stop only the existing dev backend, or choose -Port 8081 and update the frontends."
}
if ($PreflightOnly) {
    Write-Host "Demo dependencies ready; port $Port free. No backend was started."
    return
}
foreach ($key in $required) {
    [System.Environment]::SetEnvironmentVariable($key, $settings[$key], 'Process')
}
Write-Host "Starting demo backend on port $Port with dev,decision-demo (secrets hidden)."
& java -jar $jar '--spring.profiles.active=dev,decision-demo' "--server.port=$Port"
exit $LASTEXITCODE
