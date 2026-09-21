<#
.SYNOPSIS
    主脚本「全新建库」验证：把 script/sql 下的四个主初始化脚本分别导入一次性临时库，
    收集**全部** SQL 错误并核对建表数量。

.DESCRIPTION
    为什么需要本脚本（2026-09-21 审查结论）：
      1. 既有批次的验证方式是「把 26 个增量脚本跑在**存量库**上」，从未验证主脚本本身。
         而增量批次把 ALTER 后的列并进了 CREATE TABLE，却没同步位置式种子 INSERT 的取值个数，
         于是**只有全新安装才会暴露**（`ERROR 1136 Column count doesn't match`）。
      2. mysql 客户端在 `-e "source <file>"` 形式下**遇到第一个错误就中断**（`--force` 也不继续），
         所以那种跑法只能看到 1 个错误、且看不到「后面 74 张表一张都没建」。
         本脚本改用 **stdin 重定向 + --force**，一次拿全错误清单。
      3. 2026-09-21 实测未修复前：ry-vue 195 张表只建 121、ry-erp 127 只建 60、
         **ry-mes 74 只建 3**，合计 227 个错误。

    用法：
        pwsh -File script/tools/verify-main-sql-fresh-install.ps1 -MysqlExe "D:\software\mysql-5.7.14\bin\mysql.exe" -User root
        # 保留临时库以便人工排查：
        pwsh -File script/tools/verify-main-sql-fresh-install.ps1 -MysqlExe <path> -User root -KeepDatabases

    退出码：0 = 四个脚本全部零错误且建表数达标；1 = 存在错误（明细写入 -ReportDir）。

.PARAMETER MysqlExe
    mysql 客户端可执行文件路径。

.PARAMETER Server / Port / User / Password
    目标实例连接参数（默认 127.0.0.1:3306 / root / 空口令）。口令也可用环境变量 MYSQL_PWD 提供。

.PARAMETER ReportDir
    错误明细输出目录（默认 doc/audit）。

.PARAMETER KeepDatabases
    保留临时库（默认验证后 DROP）。

.NOTES
    只读地对待仓库；只在目标实例上创建/删除 `_nexus_freshcheck_*` 前缀的临时库。
    请勿指向生产实例。
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$MysqlExe,
    [string]$Server = '127.0.0.1',
    [int]$Port = 3306,
    [string]$User = 'root',
    [string]$Password = '',
    [string]$SqlDir = '',
    [string]$ReportDir = '',
    [switch]$KeepDatabases
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if ([string]::IsNullOrWhiteSpace($SqlDir)) { $SqlDir = Join-Path $repoRoot 'script\sql' }
if ([string]::IsNullOrWhiteSpace($ReportDir)) { $ReportDir = Join-Path $repoRoot 'doc\audit' }
if (-not (Test-Path $ReportDir)) { New-Item -ItemType Directory -Path $ReportDir | Out-Null }

# 每个主脚本的期望建表数（脚本内 CREATE TABLE 语句条数），用于识别「中断在建表之前」
$scripts = @(
    @{ File = 'ry-vue.sql'; Db = '_nexus_freshcheck_vue' },
    @{ File = 'ry-erp.sql'; Db = '_nexus_freshcheck_erp' },
    @{ File = 'ry-mes.sql'; Db = '_nexus_freshcheck_mes' },
    @{ File = 'ry-iot.sql'; Db = '_nexus_freshcheck_iot' }
)

$env:MYSQL_PWD = $Password
$mysqlArgs = @('-h', $Server, '-P', "$Port", '-u', $User, '--default-character-set=utf8mb4')

function Invoke-Mysql([string[]]$ExtraArgs, [string]$Sql) {
    $all = $mysqlArgs + $ExtraArgs
    if ($Sql) { return & $MysqlExe @all -e $Sql 2>&1 }
    return & $MysqlExe @all 2>&1
}

$failed = @()
Write-Host "主脚本全新建库验证：$SqlDir"
Write-Host ("=" * 78)

foreach ($s in $scripts) {
    $path = Join-Path $SqlDir $s.File
    if (-not (Test-Path $path)) { Write-Warning "跳过（不存在）：$($s.File)"; continue }

    $expected = ([regex]::Matches([System.IO.File]::ReadAllText($path, (New-Object System.Text.UTF8Encoding($false))),
        '(?m)^CREATE TABLE ')).Count

    Invoke-Mysql @('-e', "DROP DATABASE IF EXISTS ``$($s.Db)``; CREATE DATABASE ``$($s.Db)`` DEFAULT CHARACTER SET utf8mb4;") | Out-Null

    $errFile = Join-Path $ReportDir ("_freshcheck-" + ($s.File -replace '\.sql$', '') + "-errors.txt")
    if (Test-Path $errFile) { Remove-Item $errFile -Force }

    # 关键：stdin 重定向 + --force —— 否则 mysql 会在第一个错误处中断，看不到真实规模
    $cmd = "`"$MysqlExe`" -h $Server -P $Port -u $User --default-character-set=utf8mb4 --force --database=$($s.Db) < `"$path`" 2> `"$errFile`""
    cmd /c $cmd | Out-Null

    $errors = @()
    if (Test-Path $errFile) {
        $errors = Get-Content $errFile -Encoding UTF8 | Where-Object { $_ -match 'ERROR \d+' }
        # 零错误时不留下空文件，避免污染 doc/audit
        if ($errors.Count -eq 0) { Remove-Item $errFile -Force -ErrorAction SilentlyContinue }
    }
    $tables = (Invoke-Mysql @('-N', '-B', '-e', "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$($s.Db)';")) -join ''
    $tables = "$tables".Trim()

    $ok = ($errors.Count -eq 0) -and ("$tables" -eq "$expected")
    $status = if ($ok) { 'PASS' } else { 'FAIL' }
    if (-not $ok) { $failed += $s.File }

    "{0,-14} {1}  errors={2,-4} tables={3}/{4}" -f $s.File, $status, $errors.Count, $tables, $expected
    if ($errors.Count -gt 0) {
        Write-Host "   错误明细：$errFile"
        $errors | Select-Object -First 5 | ForEach-Object { "     " + $_.Substring(0, [Math]::Min(150, $_.Length)) }
        if ($errors.Count -gt 5) { "     ... 其余 $($errors.Count - 5) 条见明细文件" }
    }
    if ("$tables" -ne "$expected") {
        "     ⚠ 建表数不足：脚本含 $expected 条 CREATE TABLE，实际只建成 $tables 张 —— 导入在中途中断了"
    }

    if (-not $KeepDatabases) {
        Invoke-Mysql @('-e', "DROP DATABASE IF EXISTS ``$($s.Db)``;") | Out-Null
    }
}

Write-Host ("=" * 78)
if ($failed.Count -eq 0) {
    Write-Host "结果：OK —— 四个主脚本全新导入零错误、建表数达标。" -ForegroundColor Green
    exit 0
}
Write-Warning "结果：FAIL —— 以下脚本存在导入错误：$($failed -join ', ')"
exit 1
