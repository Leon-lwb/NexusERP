<#
.SYNOPSIS
    校验后端 @SaCheckPermission 权限码是否全部有 sys_menu 种子（菜单/按钮 perms）。

.DESCRIPTION
    扫描 ruoyi-modules/**/*.java 中所有 @SaCheckPermission 权限码，
    与 script/sql/**/*.sql 中 sys_menu 行的 perms 字段做差集（大小写敏感）。
    差集非空（且不在本脚本的豁免清单内）时以退出码 1 失败，便于接入 CI。

    背景：sys_menu.perms -> sys_role_menu 授权 -> Sa-Token 权限集合。
    代码里的 @SaCheckPermission("X") 只有在某个已授权菜单的 perms 恰好等于 X 时才通过；
    因此"代码权限码"与"菜单种子权限码"只要差一个字符，除超管（userId=1 硬编码 *:*:*）外一律 403。

.PARAMETER RepoRoot
    仓库根目录，默认按脚本位置推断（script/tools -> ../..）。

.PARAMETER ShowSeeded
    额外打印种子侧权限码总数与全部内容（调试用）。

.EXAMPLE
    pwsh -File script/tools/check-permission-seeds.ps1
#>
[CmdletBinding()]
param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [switch]$ShowSeeded
)

$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------------------
# 豁免清单
# 1) $Exempt        : 精确豁免（键 = 权限码，值 = 理由），正常修复不应往这里加。
# 2) $ExemptPrefixes: 按前缀整体豁免，用于"本专项范围外的既有缺口"，
#                     全部已在 doc/audit/2026-09-20-fix-perm-seeds.md 登记为后续批次。
# ---------------------------------------------------------------------------
$Exempt = [ordered]@{}

$ExemptPrefixes = [ordered]@{
    'workflow:' = '工作流(Flowable)模块既有种子缺口，非 2026-09-20 ERP/MES/IoT 权限种子对齐专项范围，已登记后续批次'
    'ai:'       = 'AI 模块既有种子缺口，非本专项范围，已登记后续批次'
    'system:'   = '系统模块既有种子缺口(system:autocode:part:* / system:temp:test / system:user:view 等)，非本专项范围'
    'tool:'     = '代码生成器既有种子缺口，非本专项范围'
    'online:'   = '在线低代码表单既有种子缺口，非本专项范围'
    'pay:'      = '支付模块既有种子缺口，非本专项范围'
    'im:'       = 'IM 模块既有种子缺口，非本专项范围'
    'demo:'     = '演示模块既有种子缺口，非本专项范围'
}

function Get-ExemptReason([string]$Code) {
    if ($Exempt.Contains($Code)) { return $Exempt[$Code] }
    foreach ($prefix in $ExemptPrefixes.Keys) {
        if ($Code.StartsWith($prefix, [System.StringComparison]::Ordinal)) { return $ExemptPrefixes[$prefix] }
    }
    return $null
}

$moduleDir = Join-Path $RepoRoot 'ruoyi-modules'
$sqlDir = Join-Path $RepoRoot 'script\sql'

if (-not (Test-Path -LiteralPath $moduleDir)) { throw "找不到模块目录: $moduleDir" }
if (-not (Test-Path -LiteralPath $sqlDir)) { throw "找不到 SQL 目录: $sqlDir" }

# ---------------------------------------------------------------------------
# 1) 代码侧：@SaCheckPermission("x") / @SaCheckPermission(value = "x")
# ---------------------------------------------------------------------------
$codePerms = New-Object 'System.Collections.Generic.HashSet[string]'
$javaFiles = @(Get-ChildItem -LiteralPath $moduleDir -Recurse -Filter *.java -File |
    Where-Object { $_.FullName -notmatch '[\\/](target|build)[\\/]' } |
    Select-Object -ExpandProperty FullName)
if ($javaFiles.Count -gt 0) {
    $codeHits = Select-String -Path $javaFiles `
        -Pattern '@SaCheckPermission\s*\(\s*(?:value\s*=\s*)?"([^"]+)"' -AllMatches
    foreach ($hit in $codeHits) {
        foreach ($m in $hit.Matches) { [void]$codePerms.Add($m.Groups[1].Value) }
    }
}

# ---------------------------------------------------------------------------
# 2) 种子侧：script/sql/**/*.sql 中 INSERT INTO sys_menu VALUES (...) 行里的权限码
#    按需流式匹配（Select-String 不会把 39MB 的 ry-vue.sql 整个读进内存再逐字符 Contains）
#    权限段允许连字符（如 mes:pro:step-check-rule:list）。
# ---------------------------------------------------------------------------
$seedPerms = New-Object 'System.Collections.Generic.HashSet[string]'
$seedFileCount = 0
foreach ($sqlFile in (Get-ChildItem -LiteralPath $sqlDir -Recurse -Filter *.sql -File)) {
    $seedFileCount++
    $rows = Select-String -LiteralPath $sqlFile.FullName `
        -Pattern 'INSERT\s+INTO\s+`?sys_menu`?\s+VALUES' -AllMatches
    foreach ($row in $rows) {
        # sys_menu 行里的单引号字符串中，形如 a:b / a:b:c 的即为 perms 字段
        foreach ($m in [regex]::Matches($row.Line, "'([A-Za-z][A-Za-z0-9_]*(?::[A-Za-z0-9_-]+){1,4})'")) {
            [void]$seedPerms.Add($m.Groups[1].Value)
        }
    }
}

# ---------------------------------------------------------------------------
# 3) 差集
# ---------------------------------------------------------------------------
$missing = @($codePerms | Where-Object { -not $seedPerms.Contains($_) } | Sort-Object)
$unexempt = @($missing | Where-Object { -not (Get-ExemptReason $_) })
$exempted = @($missing | Where-Object { Get-ExemptReason $_ })

Write-Host '=== 权限码种子校验 (check-permission-seeds) ==='
Write-Host ("仓库根        : {0}" -f $RepoRoot)
Write-Host ("扫描 Java 文件: {0} 个" -f $javaFiles.Count)
Write-Host ("扫描 SQL 文件 : {0} 个" -f $seedFileCount)
Write-Host ("代码权限码    : {0} 个" -f $codePerms.Count)
Write-Host ("种子权限码    : {0} 个" -f $seedPerms.Count)

if ($ShowSeeded) {
    Write-Host '--- 种子侧全部权限码 ---'
    $seedPerms | Sort-Object | ForEach-Object { Write-Host $_ }
}

if ($missing.Count -eq 0) {
    Write-Host '结果: OK —— 代码权限码全部已播种，差集为空。' -ForegroundColor Green
    exit 0
}

Write-Host ("未播种权限码  : {0} 个" -f $missing.Count) -ForegroundColor Yellow
foreach ($code in $missing) {
    $reason = Get-ExemptReason $code
    if ($reason) {
        Write-Host ("  [豁免] {0}    -- {1}" -f $code, $reason) -ForegroundColor DarkGray
    } else {
        Write-Host ("  [缺失] {0}" -f $code) -ForegroundColor Red
    }
}

if ($exempted.Count -gt 0) {
    Write-Host ("其中书面豁免  : {0} 个" -f $exempted.Count)
}

if ($unexempt.Count -gt 0) {
    Write-Host ("结果: FAIL —— {0} 个权限码无菜单种子且未豁免（非超管调用将 403）。" -f $unexempt.Count) -ForegroundColor Red
    exit 1
}

Write-Host '结果: OK —— 差集全部有书面豁免。' -ForegroundColor Green
exit 0
