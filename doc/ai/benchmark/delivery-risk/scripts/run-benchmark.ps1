<#
Offline-owned 40-case runner. Requires a DISPOSABLE, isolated demo ERP and a live AI/Flow service.
No SQL is executed by this script. CONTROL-* safety cases are service-level tests, not HTTP runs.
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [Security.SecureString]$Token,
    [string]$ClientId = $env:AI_DECISION_BENCHMARK_CLIENT_ID,
    [string]$Cases = (Join-Path $PSScriptRoot '../cases.jsonl'),
    [string]$Expected = (Join-Path $PSScriptRoot '../expected-results.jsonl'),
    [string]$Output = (Join-Path $PSScriptRoot '../reports/latest.md'),
    [ValidateRange(1, 90)][int]$TimeoutSeconds = 90,
    [ValidateRange(10, 10000)][int]$PollIntervalMilliseconds = 500
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Read-Lines([string]$path) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "缺少 Benchmark 文件：$path" }
    return @(Get-Content -LiteralPath $path -Encoding utf8 | Where-Object { $_.Trim() } | ForEach-Object {
        ConvertFrom-Json -InputObject $_ -Depth 50
    })
}
function Unique-Lines([object[]]$rows, [string]$name) {
    $ids = @{}
    $businessIds = @{}
    foreach ($row in $rows) {
        if ($null -eq $row -or [string]$row.caseId -notmatch '^DELIVERY-[0-9]{3}$' -or
            $row.bizId -isnot [string] -or $row.bizId -notmatch '^[1-9][0-9]{0,18}$' -or
            $row.bizCode -notmatch '^DEMO-AI-DECISION-[0-9]{3}$' -or
            [string]$row.bizCode -cne ('DEMO-AI-DECISION-' + ([string]$row.caseId).Substring(9)) -or
            $ids.ContainsKey([string]$row.caseId) -or $businessIds.ContainsKey([string]$row.bizId)) {
            throw "$name 包含无效/重复的 caseId 或字符串 bizId；未发起请求"
        }
        $ids[[string]$row.caseId] = $true
        $businessIds[[string]$row.bizId] = $true
    }
    return $ids
}
function Percentile([double[]]$values, [double]$percent) {
    if ($values.Count -eq 0) { return 'N/A' }
    $sorted = @($values | Sort-Object)
    $idx = [math]::Max(0, [math]::Ceiling($percent * $sorted.Count) - 1)
    return ('{0:N3}' -f $sorted[$idx])
}
function Markdown([object]$value) {
    return ([string]$value -replace '[\r\n]+', ' ' -replace '\|', '\|' -replace '[\x00-\x1f]', ' ').Trim()
}
function Api([string]$method, [string]$path, [object]$body) {
    $parameters = @{ Method = $method; Uri = "$url$path"; Headers = $headers;
        TimeoutSec = 10; ErrorAction = 'Stop' }
    if ($null -ne $body) {
        $parameters.Body = ConvertTo-Json -InputObject $body -Compress -Depth 8
        $parameters.ContentType = 'application/json; charset=utf-8'
    }
    try { $response = Invoke-RestMethod @parameters }
    catch { throw 'HTTP/连接异常（详情隐藏，以免泄露凭证）' }
    if ($null -eq $response -or $response.code -ne 200 -or $null -eq $response.data) {
        throw 'API 响应不是成功的 R{code:200,data}（详情隐藏）'
    }
    return $response.data
}

$rows = @(Read-Lines $Cases)
$expectedRows = @(Read-Lines $Expected)
if ($rows.Count -eq 0 -or $rows.Count -ne $expectedRows.Count) { throw '案例/期望记录数不一致' }
$null = Unique-Lines $rows 'cases'
$null = Unique-Lines $expectedRows 'expected-results'
$evidenceFailureCodes = @('ROUTING_EVIDENCE_MISSING', 'SCHEDULE_EVIDENCE_MISSING',
    'SHORTAGE_SIMULATION_INCOMPLETE', 'CAPACITY_EVIDENCE_MISSING')
$golden = @{}
foreach ($item in $expectedRows) { $golden[[string]$item.caseId] = $item }
foreach ($item in $rows) {
    $ref = $golden[[string]$item.caseId]
    if ($null -eq $ref -or $ref.bizId -cne $item.bizId -or $ref.bizCode -cne $item.bizCode) {
        throw 'cases/expected-results 业务对象不一致；未发起请求'
    }
    if ($ref.expectedStatus -ceq 'FAILED') {
        $expectedCode = $ref.PSObject.Properties['expectedFailureCode']
        if ($null -eq $expectedCode -or [string]$expectedCode.Value -cnotin $evidenceFailureCodes) {
            throw 'v2 证据负例必须声明有限白名单内的精确 expectedFailureCode；未发起请求'
        }
    }
}
if ([string]::IsNullOrWhiteSpace($env:AI_DECISION_BENCHMARK_TOKEN)) {
    if ($null -eq $Token) { $Token = Read-Host '输入临时 Benchmark Token（不回显）' -AsSecureString }
    $plainToken = [System.Net.NetworkCredential]::new('', $Token).Password
} else {
    if ($null -ne $Token) { throw '请只使用环境变量或 SecureString 中的一种 Token 来源' }
    $plainToken = $env:AI_DECISION_BENCHMARK_TOKEN
}
if ([string]::IsNullOrWhiteSpace($plainToken)) { throw 'Token 不能为空' }
if ($ClientId -cnotmatch '^[a-f0-9]{32}$') { throw '需要 32 位 PC 客户端 ID（与登录时的 clientid 一致）' }
$baseUri = [uri]$BaseUrl
if ($baseUri.Scheme -notin @('http', 'https') -or -not $baseUri.IsAbsoluteUri -or
    $baseUri.UserInfo -or $baseUri.Query -or $baseUri.Fragment -or $baseUri.AbsolutePath -ne '/') {
    throw 'BaseUrl 必须是无账号、参数和路径的 http(s) 服务根地址'
}
$url = $BaseUrl.TrimEnd('/')
$headers = @{ Authorization = "Bearer $plainToken"; clientid = $ClientId }
$results = [System.Collections.Generic.List[object]]::new()
$latencies = [System.Collections.Generic.List[double]]::new()
$terminal = @('WAITING_APPROVAL', 'SUCCEEDED', 'FAILED', 'REJECTED', 'CANCELLED', 'SUPERSEDED')
foreach ($sample in $rows) {
    $ref = $golden[[string]$sample.caseId]
    $timer = [System.Diagnostics.Stopwatch]::StartNew()
    $status = 'NOT_STARTED'
    $cause = ''
    $ok = $false
    try {
        if ($sample.bizType -ne 'WORK_ORDER' -or $sample.horizonDays -lt 1 -or $sample.horizonDays -gt 90) {
            throw '不支持的业务类型或范围天数'
        }
        # 在 ERP 独立解析工单；run.bizCode 当前为可选字段。
        $work = Api 'GET' "/erp/prd/work/$([string]$sample.bizId)" $null
        if ([string]$work.woId -cne [string]$sample.bizId -or
            [string]$work.woNo -cne [string]$sample.bizCode) {
            throw 'BIZ_ID_MISMATCH：隔离 ERP 工单 ID/编号与案例不符'
        }
        $accepted = Api 'POST' '/ai/decision/delivery-risk/run' @{
            bizType = [string]$sample.bizType; bizId = [string]$sample.bizId;
            horizonDays = [int]$sample.horizonDays; forceRestart = $true
        }
        if ([string]$accepted.runId -notmatch '^[1-9][0-9]{0,18}$') {
            throw '启动接口未返回合法的字符串 runId'
        }
        $runId = [string]$accepted.runId
        $deadline = [datetime]::UtcNow.AddSeconds($TimeoutSeconds)
        $detail = $null
        while ([datetime]::UtcNow -lt $deadline) {
            $detail = Api 'GET' "/ai/decision/run/$runId" $null
            $codeField = if ($null -ne $detail.run) { $detail.run.PSObject.Properties['bizCode'] } else { $null }
            $actualCode = if ($null -eq $codeField) { '' } else { [string]$codeField.Value }
            if ($null -eq $detail.run -or [string]$detail.run.bizId -cne [string]$sample.bizId -or
                ($actualCode -and $actualCode -cne [string]$sample.bizCode) -or
                [string]$detail.run.bizType -cne [string]$sample.bizType) {
                throw 'BIZ_ID_MISMATCH：精确业务 ID/代码/类型无法验证'
            }
            $status = [string]$detail.run.status
            if ($status -in $terminal) { break }
            if ($status -notin @('CREATED', 'RUNNING')) {
                throw '非法 Run 状态（AI_DECISION_READY 仅为 SSE 事件）'
            }
            Start-Sleep -Milliseconds $PollIntervalMilliseconds
        }
        if ($status -notin $terminal) { throw 'TIMEOUT：分析未在限时内完成' }
        if ($status -cne $ref.expectedStatus) { throw "状态不匹配：期望 $($ref.expectedStatus)，实际 $status" }
        if ($status -eq 'FAILED') {
            if ($ref.expectedRiskLevel -ne $null -or [string]::IsNullOrWhiteSpace([string]$ref.expectedFailurePattern)) {
                throw '缺失可验真的预期失败规则（不把任意异常计为 PASS）'
            }
            $failure = [string]$detail.run.errorMessage
            $actualCode = $detail.run.PSObject.Properties['errorCode']
            $hasConclusion = $false
            foreach ($field in @('riskLevel', 'riskScore', 'recommendedScenarioCode', 'resultJson')) {
                $value = $detail.run.PSObject.Properties[$field]
                if ($null -ne $value -and -not [string]::IsNullOrWhiteSpace([string]$value.Value)) {
                    $hasConclusion = $true
                }
            }
            if ($null -eq $actualCode -or [string]$actualCode.Value -cne [string]$ref.expectedFailureCode -or
                $failure -notmatch [string]$ref.expectedFailurePattern -or $detail.actions.Count -gt 0 -or $hasConclusion) {
                throw '安全拒绝错误码/消息不符，或失败运行产生了风险结论/动作'
            }
            $ok = $true
            $cause = '预期证据拒绝（无执行动作）'
        } else {
            if ([string]::IsNullOrWhiteSpace([string]$detail.run.resultJson)) {
                throw '缺少可复核的结构化决策 resultJson'
            }
            $computed = ConvertFrom-Json -InputObject $detail.run.resultJson -Depth 50
            if ([string]$detail.run.riskLevel -cne [string]$ref.expectedRiskLevel -or
                [string]$detail.run.confidenceLevel -cne [string]$ref.expectedConfidence -or
                [string]$detail.run.recommendedScenarioCode -cne [string]$ref.recommendedScenarioCode -or
                [string]$computed.riskLevel -cne [string]$ref.expectedRiskLevel) {
                throw '风险/置信度/推荐方案不符'
            }
            $actualCauses = @($computed.rootCauseCodes | Where-Object { $null -ne $_ } | Sort-Object -Unique)
            $expectedCauses = @($ref.expectedRootCauses | Where-Object { $null -ne $_ } | Sort-Object -Unique)
            if (($actualCauses -join ',') -cne ($expectedCauses -join ',')) {
                throw '根因与黄金期望不符'
            }
            $actualActions = @($detail.actions | ForEach-Object { $_.actionType } | Sort-Object)
            $required = @($ref.requiredActions | Sort-Object)
            $forbidden = @($ref.forbiddenActions)
            if (($actualActions -join ',') -cne ($required -join ',') -or
                @($actualActions | Where-Object { $_ -in $forbidden }).Count -ne 0) {
                throw '动作集合不符或包含禁止动作'
            }
            $ok = $true
            $cause = '结构化风险/根因/场景/动作符合期望'
        }
    } catch {
        # 仅记录本地生成且有界的错误分类，不输出 HTTP 原始错误或 JWT。
        $cause = [string]$_.Exception.Message
        if ($cause.Length -gt 180) { $cause = $cause.Substring(0, 180) }
        $cause = $cause.Replace($plainToken, '[REDACTED]')
    } finally {
        $timer.Stop()
        if ($ok) { $latencies.Add($timer.Elapsed.TotalSeconds) }
        $results.Add([pscustomobject]@{ id = [string]$sample.caseId; bizId = [string]$sample.bizId;
            status = $status; passed = $ok; seconds = $timer.Elapsed.TotalSeconds; reason = $cause })
    }
}
$headers.Clear()
$plainToken = $null
$passes = @($results | Where-Object { $_.passed }).Count
$rate = '{0:N1}' -f (100.0 * $passes / $results.Count)
$lines = [System.Collections.Generic.List[string]]::new()
$lines.Add('# 工业交付风险 Benchmark 运行报告')
$lines.Add('')
$lines.Add("- 运行时间：$([datetime]::Now.ToString('yyyy-MM-dd HH:mm:ss zzz'))")
$lines.Add("- 案例范围：$($results.Count) 条 DELIVERY；CONTROL-001..005 仅在服务级单测中验证，未执行 HTTP 审批/执行。")
$lines.Add("- 通过率：$passes/$($results.Count)（$rate%）；未执行/超时/不可解析均不计 PASS。")
$lines.Add("- P50/P95（仅通过案例，秒）：$(Percentile $latencies.ToArray() 0.5) / $(Percentile $latencies.ToArray() 0.95)")
$lines.Add('')
$lines.Add('| 案例 | 业务ID | 最终状态 | 结果 | 时长(s) | 违规/失败原因 |')
$lines.Add('|---|---|---|---|---:|---|')
foreach ($item in $results) {
    $label = if ($item.passed) { 'PASS' } else { 'FAIL' }
    $lines.Add("| $(Markdown $item.id) | $(Markdown $item.bizId) | $(Markdown $item.status) | $label | $('{0:N3}' -f $item.seconds) | $(Markdown $item.reason) |")
}
$parent = Split-Path -Parent $Output
if ($parent -and -not (Test-Path -LiteralPath $parent)) { $null = New-Item -ItemType Directory -Path $parent -Force }
[System.IO.File]::WriteAllText([System.IO.Path]::GetFullPath($Output),
    ($lines -join "`n") + "`n", [System.Text.UTF8Encoding]::new($false))
Write-Output "Benchmark: $passes/$($results.Count) PASS; report: $Output"
if ($passes -ne $results.Count) { exit 1 }