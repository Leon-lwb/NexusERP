param(
    [switch]$Stop,
    [switch]$DisableCaptcha,
    [switch]$ValidateOnly,
    [switch]$ValidateIdentityOnly,
    [switch]$ValidateStopIdentityOnly,
    [switch]$SimulateCimDateTime,
    [switch]$ValidateMetadataOnly,
    [int]$Port = 8080,
    [string]$JarPath,
    [string]$RunDirectory,
    [string]$IdentityCommandLine,
    [string]$IdentityProcessName = 'java',
    [string]$IdentityCreationTime,
    [int]$IdentityPid,
    [string]$ExpectedCreationTime,
    [string]$PidText,
    [string]$MetadataJson
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not ($ValidateOnly -or $ValidateIdentityOnly -or $ValidateStopIdentityOnly -or $ValidateMetadataOnly) -and
    ($PSBoundParameters.ContainsKey('JarPath') -or $PSBoundParameters.ContainsKey('RunDirectory'))) {
    throw '-JarPath and -RunDirectory are only permitted in validation modes.'
}
if ($Stop -and ($ValidateOnly -or $ValidateIdentityOnly -or $ValidateStopIdentityOnly -or $ValidateMetadataOnly)) {
    throw '-Stop cannot be combined with validation modes.'
}
if ($SimulateCimDateTime -and -not ($ValidateIdentityOnly -or $ValidateStopIdentityOnly)) {
    throw '-SimulateCimDateTime is only permitted in identity validation modes.'
}

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptRoot '..\..\..\..')).Path
if ([string]::IsNullOrWhiteSpace($JarPath)) { $JarPath = Join-Path $repoRoot 'ruoyi-admin\target\ruoyi-admin.jar' }
if ([string]::IsNullOrWhiteSpace($RunDirectory)) { $RunDirectory = Join-Path $repoRoot '.codex\full-flow-20260928' }
$jar = [System.IO.Path]::GetFullPath($JarPath)
$runDir = [System.IO.Path]::GetFullPath($RunDirectory)
$pidFile = Join-Path $runDir 'backend.pid'
$backendJson = Join-Path $runDir 'backend.json'
$stdoutLog = Join-Path $runDir 'backend.stdout.log'
$stderrLog = Join-Path $runDir 'backend.stderr.log'

function Quote-ProcessArgument {
    param([Parameter(Mandatory)][string]$Value)
    return '"' + $Value.Replace('"', '\"') + '"'
}

function Build-JavaArguments {
    param(
        [Parameter(Mandatory)][string]$JavaJar,
        [Parameter(Mandatory)][int]$ServerPort,
        [switch]$CaptchaDisabled
    )
    $result = @('-jar', (Quote-ProcessArgument -Value $JavaJar), '--spring.profiles.active=dev', "--server.port=$ServerPort")
    if ($CaptchaDisabled) { $result += '--captcha.enable=false' }
    return [string[]]$result
}

function Get-CommandLineTokens {
    param([Parameter(Mandatory)][string]$CommandLine)
    $tokens = [System.Collections.Generic.List[string]]::new()
    $pattern = '"([^"]*)"|([^\s]+)'
    foreach ($match in [regex]::Matches($CommandLine, $pattern)) {
        if ($match.Groups[1].Success) { $tokens.Add($match.Groups[1].Value) }
        else { $tokens.Add($match.Groups[2].Value) }
    }
    return [string[]]$tokens
}

function Test-ManagedProcessIdentity {
    param(
        [Parameter(Mandatory)]$ProcessInfo,
        [Parameter(Mandatory)][string]$ExpectedJar,
        [Parameter(Mandatory)][int]$ExpectedPort,
        [string]$ExpectedStartTime
    )
    $processName = [System.IO.Path]::GetFileNameWithoutExtension([string]$ProcessInfo.Name)
    if ($processName -notin @('java', 'javaw')) { return $false }
    if ([string]::IsNullOrWhiteSpace($ExpectedStartTime) -or [string]::IsNullOrWhiteSpace([string]$ProcessInfo.CreationDate)) { return $false }
    $creationDate = $ProcessInfo.CreationDate
    $actualCreationTime = if ($creationDate -is [datetime]) {
        $creationDate.ToString('o', [Globalization.CultureInfo]::InvariantCulture)
    } else { [string]$creationDate }
    $actualStart = [datetimeoffset]::MinValue
    $storedStart = [datetimeoffset]::MinValue
    if (-not [datetimeoffset]::TryParse($actualCreationTime, [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::RoundtripKind, [ref]$actualStart) -or
        -not [datetimeoffset]::TryParse($ExpectedStartTime, [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::RoundtripKind, [ref]$storedStart) -or
        $actualStart.UtcTicks -ne $storedStart.UtcTicks) { return $false }
    $tokens = @(Get-CommandLineTokens -CommandLine ([string]$ProcessInfo.CommandLine))
    $jarMatch = $false
    if (@($tokens | Where-Object { $_ -ceq '-jar' }).Count -ne 1) { return $false }
    for ($index = 0; $index -lt ($tokens.Count - 1); $index++) {
        if ([string]::Equals($tokens[$index], '-jar', [System.StringComparison]::OrdinalIgnoreCase) -and
            [string]::Equals($tokens[$index + 1], $ExpectedJar, [System.StringComparison]::OrdinalIgnoreCase)) {
            $jarMatch = $true
            break
        }
    }
    $profiles = @($tokens | Where-Object { $_ -match '^--spring\.profiles\.active(?:=|$)' })
    $ports = @($tokens | Where-Object { $_ -match '^--server\.port(?:=|$)' })
    return $jarMatch -and $profiles.Count -eq 1 -and $profiles[0] -ceq '--spring.profiles.active=dev' -and
        $ports.Count -eq 1 -and $ports[0] -ceq "--server.port=$ExpectedPort"
}

function Assert-ManagedMetadata {
    param([string]$PidValue, [string]$JsonValue, [string]$ExpectedJar)
    $managedId = 0
    if (-not [int]::TryParse($PidValue.Trim(), [Globalization.NumberStyles]::Integer, [Globalization.CultureInfo]::InvariantCulture, [ref]$managedId) -or $managedId -le 0) {
        throw 'backend.pid is invalid; refusing to stop and preserving metadata.'
    }
    try { $metadata = $JsonValue | ConvertFrom-Json }
    catch { throw 'backend.json is invalid; refusing to stop and preserving metadata.' }
    $jsonPid = 0
    if (-not [int]::TryParse([string]$metadata.pid, [Globalization.NumberStyles]::Integer, [Globalization.CultureInfo]::InvariantCulture, [ref]$jsonPid) -or $jsonPid -ne $managedId) {
        throw 'backend.pid and backend.json.pid do not match; refusing to stop and preserving metadata.'
    }
    if (-not $metadata.PSObject.Properties['creationTime'] -or [string]::IsNullOrWhiteSpace([string]$metadata.creationTime)) {
        throw 'backend.json creation time is missing; refusing to stop.'
    }
    $jsonPort = 0
    if (-not [int]::TryParse([string]$metadata.port, [Globalization.NumberStyles]::Integer, [Globalization.CultureInfo]::InvariantCulture, [ref]$jsonPort) -or $jsonPort -le 0 -or $jsonPort -gt 65535 -or
        $metadata.profile -cne 'dev' -or [string]::IsNullOrWhiteSpace([string]$metadata.jar) -or
        [System.IO.Path]::GetFullPath([string]$metadata.jar) -ne $ExpectedJar -or
        [string]::IsNullOrWhiteSpace([string]$metadata.creationTime)) {
        throw 'backend.json has invalid port, profile, jar or creation time; refusing to stop.'
    }
    # PowerShell 7 将 ISO JSON 日期解析为 DateTime；强制转换为字符串会丢失小数秒和时区偏移。
    if ($metadata.creationTime -is [datetime]) {
        $metadata.creationTime = $metadata.creationTime.ToString('o', [Globalization.CultureInfo]::InvariantCulture)
    }
    return $metadata
}

function Get-SimulatedCreationDate {
    if (-not $SimulateCimDateTime) { return $IdentityCreationTime }
    $parsed = [datetime]::MinValue
    if (-not [datetime]::TryParse($IdentityCreationTime, [Globalization.CultureInfo]::InvariantCulture,
            [Globalization.DateTimeStyles]::RoundtripKind, [ref]$parsed)) {
        throw 'simulated CIM creation time must be a valid ISO date.'
    }
    return $parsed
}

if ($ValidateStopIdentityOnly) {
    $validated = Assert-ManagedMetadata -PidValue $PidText -JsonValue $MetadataJson -ExpectedJar $jar
    if ([string]::IsNullOrWhiteSpace($IdentityCommandLine) -or $IdentityPid -le 0) { throw 'simulated process identity is incomplete.' }
    $identityProcess = [pscustomobject]@{ Name = $IdentityProcessName; ProcessId = $IdentityPid; CommandLine = $IdentityCommandLine; CreationDate = (Get-SimulatedCreationDate) }
    $matches = $IdentityPid -eq [int]$validated.pid -and
        (Test-ManagedProcessIdentity -ProcessInfo $identityProcess -ExpectedJar $jar -ExpectedPort ([int]$validated.port) -ExpectedStartTime ([string]$validated.creationTime))
    [pscustomobject]@{ matches = [bool]$matches } | ConvertTo-Json -Compress
    return
}

if ($ValidateMetadataOnly) {
    $validated = Assert-ManagedMetadata -PidValue $PidText -JsonValue $MetadataJson -ExpectedJar $jar
    [pscustomobject]@{ valid = $true; pid = $validated.pid } | ConvertTo-Json -Compress
    return
}

if ($ValidateIdentityOnly) {
    if ([string]::IsNullOrWhiteSpace($IdentityCommandLine)) { throw '-IdentityCommandLine is required.' }
    $identityProcess = [pscustomobject]@{ Name = $IdentityProcessName; CommandLine = $IdentityCommandLine; CreationDate = (Get-SimulatedCreationDate) }
    [pscustomobject]@{ matches = (Test-ManagedProcessIdentity -ProcessInfo $identityProcess -ExpectedJar $jar -ExpectedPort $Port -ExpectedStartTime $ExpectedCreationTime) } | ConvertTo-Json -Compress
    return
}

if ($ValidateOnly) {
    if ($Port -le 0 -or $Port -gt 65535) { throw 'server port must be between 1 and 65535.' }
    $arguments = @(Build-JavaArguments -JavaJar $jar -ServerPort $Port -CaptchaDisabled:$DisableCaptcha)
    [pscustomobject]@{
        filePath = 'java'
        arguments = $arguments
        jar = $jar
        profile = 'dev'
        port = $Port
        captchaDisabled = [bool]$DisableCaptcha
    } | ConvertTo-Json -Depth 3 -Compress
    return
}

New-Item -ItemType Directory -Path $runDir -Force | Out-Null

if ($Stop) {
    if (-not (Test-Path -LiteralPath $pidFile) -or -not (Test-Path -LiteralPath $backendJson)) {
        throw 'managed backend metadata is incomplete; refusing to stop and preserving existing files.'
    }

    $metadata = Assert-ManagedMetadata -PidValue (Get-Content -LiteralPath $pidFile -Raw) -JsonValue (Get-Content -LiteralPath $backendJson -Raw) -ExpectedJar $jar
    $managedPid = [int]$metadata.pid
    $metadataPort = [int]$metadata.port
    $expectedJar = $jar
    $processInfo = Get-CimInstance Win32_Process -Filter "ProcessId = $managedPid" -ErrorAction SilentlyContinue
    if (-not $processInfo -or -not (Test-ManagedProcessIdentity -ProcessInfo $processInfo -ExpectedJar $expectedJar -ExpectedPort $metadataPort -ExpectedStartTime ([string]$metadata.creationTime))) {
        throw 'managed PID is not the expected java/javaw ruoyi-admin dev process; refusing to stop and preserving metadata.'
    }

    Stop-Process -Id $managedPid -Force
    Remove-Item -LiteralPath $pidFile -Force
    Remove-Item -LiteralPath $backendJson -Force
    Write-Output "[stop] stopped managed backend PID $managedPid and removed backend PID metadata."
    return
}

if ($Port -le 0 -or $Port -gt 65535) { throw 'server port must be between 1 and 65535.' }
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw "ruoyi-admin jar not found: $jar" }
$pidExists = Test-Path -LiteralPath $pidFile
$jsonExists = Test-Path -LiteralPath $backendJson
if ($pidExists -xor $jsonExists) { throw 'backend.pid and backend.json must either both exist or both be absent; refusing to alter metadata.' }
if ($pidExists -and $jsonExists) {
    $existingPidText = (Get-Content -LiteralPath $pidFile -Raw).Trim()
    $existingPid = 0
    if (-not [int]::TryParse($existingPidText, [Globalization.NumberStyles]::Integer, [Globalization.CultureInfo]::InvariantCulture, [ref]$existingPid)) {
        throw 'existing backend.pid is invalid; refusing to alter metadata.'
    }
    if (Get-Process -Id $existingPid -ErrorAction SilentlyContinue) { throw "managed backend already running with PID $existingPid; use -Stop first." }
    Remove-Item -LiteralPath $pidFile -Force
    Remove-Item -LiteralPath $backendJson -Force
    Write-Output '[start] stale backend.pid/backend.json cleaned because the recorded process is absent; logs were preserved.'
}

$arguments = @(Build-JavaArguments -JavaJar $jar -ServerPort $Port -CaptchaDisabled:$DisableCaptcha)
$started = $null
try {
    $started = Start-Process -FilePath 'java' -ArgumentList $arguments -WorkingDirectory $repoRoot -WindowStyle Hidden -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog -PassThru
    $launchedInfo = Get-CimInstance Win32_Process -Filter "ProcessId = $($started.Id)" -ErrorAction SilentlyContinue
    if (-not $launchedInfo -or -not $launchedInfo.CreationDate) { throw 'cannot capture launched process creation time; refusing to record unsafe stop metadata.' }
    $creationTime = ([datetimeoffset]$launchedInfo.CreationDate).ToUniversalTime().ToString('o', [Globalization.CultureInfo]::InvariantCulture)
    $started.Id | Set-Content -LiteralPath $pidFile -Encoding ASCII
    [pscustomobject]@{
        pid = $started.Id
        creationTime = $creationTime
        port = $Port
        profile = 'dev'
        jar = $jar
        stdout = $stdoutLog
        stderr = $stderrLog
        captchaDisabled = [bool]$DisableCaptcha
    } | ConvertTo-Json | Set-Content -LiteralPath $backendJson -Encoding UTF8
    Write-Output "[start] backend PID $($started.Id), port $Port, profile dev"
    Write-Output "[start] logs: $stdoutLog ; $stderrLog"
} catch {
    $startupFailure = $_
    # 回滚仅使用本次调用返回的进程对象，绝不使用磁盘中的 PID。
    if ($null -ne $started) {
        $confirmedExited = $false
        try {
            $started.Refresh()
            if (-not $started.HasExited) {
                Stop-Process -InputObject $started -Force -ErrorAction Stop
                if (-not $started.WaitForExit(5000)) { throw 'just-started process did not exit within timeout' }
            }
            $started.Refresh()
            $confirmedExited = $started.HasExited
        } catch {
            Write-Warning 'Cannot confirm termination of just-started process; preserving metadata for manual investigation.'
        }
        if ($confirmedExited) {
            # 启动前的守卫已排除已有元数据；这里只移除本次产生的部分文件。
            try {
                if (Test-Path -LiteralPath $pidFile) { Remove-Item -LiteralPath $pidFile -Force -ErrorAction Stop }
                if (Test-Path -LiteralPath $backendJson) { Remove-Item -LiteralPath $backendJson -Force -ErrorAction Stop }
            } catch {
                Write-Warning 'Could not remove partial startup metadata; inspect the ignored runtime directory.'
            }
        }
    }
    throw $startupFailure
}
