param(
    [Alias('Host')]
    [string]$DbHost = '127.0.0.1',
    [int]$Port = 3306,
    [string]$Profile = 'dev'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptRoot '..\..\..\..')).Path

function Assert-AllowedRuntime {
    param([string]$TargetHost, [int]$TargetPort, [string]$TargetProfile)
    $allowedHosts = @('localhost', '127.0.0.1')
    if ($allowedHosts -notcontains $TargetHost.Trim().ToLowerInvariant()) { throw '数据库主机必须是 localhost 或 127.0.0.1。' }
    if ($TargetPort -ne 3306) { throw '数据库端口必须是 3306。' }
    if ($TargetProfile.Trim().ToLowerInvariant() -ne 'dev') { throw '运行 profile 必须是 dev。' }
}

function Test-TcpEndpoint {
    param([string]$TargetHost, [int]$TargetPort, [int]$TimeoutMilliseconds = 1000)
    $client = New-Object System.Net.Sockets.TcpClient
    $waitHandle = $null
    try {
        $asyncResult = $client.BeginConnect($TargetHost, $TargetPort, $null, $null)
        $waitHandle = $asyncResult.AsyncWaitHandle
        if (-not $waitHandle.WaitOne($TimeoutMilliseconds)) { return $false }
        $client.EndConnect($asyncResult)
        return $true
    } catch {
        return $false
    } finally {
        if ($waitHandle) { $waitHandle.Dispose() }
        $client.Dispose()
    }
}

Assert-AllowedRuntime -TargetHost $DbHost -TargetPort $Port -TargetProfile $Profile

$required = @('mysql', 'java', 'mvn', 'node', 'python')
$optionalMqtt = @('mosquitto_pub', 'mqttx')
Write-Output "[preflight] target: $($DbHost.ToLowerInvariant()):$Port; profile: dev"
$jar = Join-Path $repoRoot 'ruoyi-admin\target\ruoyi-admin.jar'
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw "ruoyi-admin jar not found: $jar" }
Write-Output "[ok] ruoyi-admin jar exists: $jar"


foreach ($name in $required) {
    $command = Get-Command $name -ErrorAction SilentlyContinue
    if (-not $command) { throw "缺少依赖命令：$name" }
    Write-Output "[ok] ${name}: $($command.Source)"
}

$mqttCommand = $null
foreach ($name in $optionalMqtt) {
    $mqttCommand = Get-Command $name -ErrorAction SilentlyContinue
    if ($mqttCommand) { break }
}
if (-not $mqttCommand) { throw '缺少 MQTT CLI：mosquitto_pub 或 mqttx' }
Write-Output "[ok] mqtt-cli: $($mqttCommand.Source)"

$serviceEndpoints = @(
    @{ Name = 'Redis'; Host = '127.0.0.1'; Port = 6379 },
    @{ Name = 'RabbitMQ'; Host = '127.0.0.1'; Port = 5672 },
    @{ Name = 'embedded MQTT'; Host = '127.0.0.1'; Port = 1883 },
    @{ Name = 'backend'; Host = '127.0.0.1'; Port = 8080 }
)
foreach ($endpoint in $serviceEndpoints) {
    if (Test-TcpEndpoint -TargetHost $endpoint.Host -TargetPort $endpoint.Port) {
        Write-Output "[ok] $($endpoint.Name) TCP $($endpoint.Host):$($endpoint.Port) is reachable"
    } else {
        Write-Output "[warn] $($endpoint.Name) TCP $($endpoint.Host):$($endpoint.Port) is not listening; this is not a MySQL failure"
    }
}

$mysqlArgs = @('--protocol=TCP', '--host', $DbHost, '--port', $Port, '--connect-timeout=3', '--batch', '--skip-column-names', '--execute', 'SHOW DATABASES;')
$schemaOutput = & mysql @mysqlArgs 2>$null
if ($LASTEXITCODE -ne 0) { throw '无法通过 MySQL 客户端连接本机 3306；未执行写 SQL。' }
$schemas = @($schemaOutput | Where-Object { $_ -and $_.Trim() })
if ($schemas.Count -eq 0) { throw 'MySQL 可连接，但未返回 schema 名称。' }
Write-Output '[ok] MySQL read-only SHOW DATABASES'
Write-Output ('[schemas] ' + ($schemas -join ', '))
Write-Output '[result] preflight passed; no database writes were attempted.'
