# 一次性修复脚本：修正主脚本 script/sql/*.sql 的全新安装缺陷（2026-09-21）
# 缺陷来源：增量批次把 ALTER 后的列合并进主脚本 CREATE TABLE，但未同步位置式 INSERT 的取值个数；
#           以及把依赖表放在其建表语句之前 / 重复合并种子行 / 括号错位。
# 幂等：位置式 INSERT 仅在“取值个数 == 修复前个数”时改写；结尾逗号仅在存在时移除。
$ErrorActionPreference = 'Stop'
$enc = New-Object System.Text.UTF8Encoding($false)
$sqlDir = "D:\workspace\nexus-erp\script\sql"

function Read-Sql([string]$p) { return ,([System.IO.File]::ReadAllLines($p, $enc)) }
function Write-Sql([string]$p, [string[]]$l) { [System.IO.File]::WriteAllText($p, ($l -join "`r`n") + "`r`n", $enc) }

# 扫描 VALUES 元组，返回 @{ Count = 顶层取值个数; CommaAt = 每个顶层逗号的下标 }
function Scan-Values([string]$stmt) {
    $open = $stmt.IndexOf("VALUES (")
    if ($open -lt 0) { throw "no VALUES in: $stmt" }
    $i = $open + 7 + 1          # 跳过 "VALUES (" 的右括号，从第一个取值字符开始
    $depth = 0; $inStr = $false; $count = 1; $commas = New-Object 'System.Collections.Generic.List[int]'
    for (; $i -lt $stmt.Length; $i++) {
        $c = $stmt[$i]
        if ($inStr) {
            if ($c -eq "'") {
                if ($i + 1 -lt $stmt.Length -and $stmt[$i + 1] -eq "'") { $i++; continue } else { $inStr = $false }
            }
            continue
        }
        if ($c -eq "'") { $inStr = $true; continue }
        if ($c -eq "(") { $depth++; continue }
        if ($c -eq ")") { if ($depth -eq 0) { break } else { $depth--; continue } }
        if ($c -eq "," -and $depth -eq 0) { $commas.Add($i); $count++ }
    }
    return @{ Count = $count; Commas = $commas }
}

# 在 VALUES 元组的第 n 个取值之后插入文本
function Insert-AfterValue([string]$stmt, [int]$n, [string]$text) {
    $s = Scan-Values $stmt
    if ($n -ge $s.Count) { throw "cannot insert after value $n of $($s.Count)" }
    $at = $s.Commas[$n - 1]
    return $stmt.Substring(0, $at + 1) + " " + $text + "," + $stmt.Substring($at + 1)
}

function Fix-PositionalInserts([string]$file, [string]$table, [int]$afterValue, [int]$expected, [string]$insertText) {
    $p = Join-Path $sqlDir $file
    $lines = Read-Sql $p
    $pat = "^INSERT INTO ``$table`` VALUES \("
    $fixed = 0; $skipped = 0
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match $pat) {
            $s = Scan-Values $lines[$i]
            if ($s.Count -eq $expected) { $lines[$i] = Insert-AfterValue $lines[$i] $afterValue $insertText; $fixed++ }
            else { $skipped++ }
        }
    }
    Write-Sql $p $lines
    Write-Output ("  {0,-14} {1,-20} 补齐 {2,-11} 改写 {3,-4} 跳过(已修) {4}" -f $file, $table, $insertText, $fixed, $skipped)
}

# ─────────────────────────── ry-erp.sql ───────────────────────────
Write-Output "== ry-erp.sql =="
$p = Join-Path $sqlDir "ry-erp.sql"
$lines = Read-Sql $p
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match "^\s*UNIQUE INDEX ``uk_worker_date_active``" -and $lines[$i].TrimEnd().EndsWith(",")) {
        $lines[$i] = $lines[$i].TrimEnd().TrimEnd(",")
        Write-Output "  sch_employee_schedule 结尾逗号已移除 (L$($i+1)) -> 该表此前因 ERROR 1064 完全未创建"
    }
}
Write-Sql $p $lines
Fix-PositionalInserts "ry-erp.sql" "so_price_list"      11 16 "'0'"    # del_flag AFTER status
Fix-PositionalInserts "ry-erp.sql" "so_price_list_line"  8 13 "'0'"    # del_flag AFTER discount_rate

# ─────────────────────────── ry-mes.sql ───────────────────────────
Write-Output "== ry-mes.sql =="
Fix-PositionalInserts "ry-mes.sql" "eq_equipment"        17 24 "NULL"        # planned_minutes_per_day AFTER iot_device_id
Fix-PositionalInserts "ry-mes.sql" "qc_inspect_template"  8 17 "NULL, NULL"  # process_id, customer_id AFTER material_name
Fix-PositionalInserts "ry-mes.sql" "sop_version"          6 17 "NULL"        # file_hash AFTER file_type

# ─────────────────────────── ry-vue.sql ───────────────────────────
Write-Output "== ry-vue.sql =="
$p = Join-Path $sqlDir "ry-vue.sql"
$lines = Read-Sql $p

$ddlAt = @{}
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match "^CREATE TABLE ``([^``]+)``") { if (-not $ddlAt.ContainsKey($Matches[1])) { $ddlAt[$Matches[1]] = $i } }
}
$ddlDictType = $ddlAt["sys_dict_type"]
$ddlJobExec = $ddlAt["sj_job_executor"]
Write-Output "  sys_dict_type 建表行 L$($ddlDictType+1) ; sj_job_executor 建表行 L$($ddlJobExec+1)"

$moveRanges = @()
$i = 0
while ($i -lt $lines.Count) {
    if ($i -lt $ddlDictType -and $lines[$i] -match '^\s*(INSERT\s+(IGNORE\s+)?INTO|UPDATE)\s+`sys_dict_type`') {
        $start = $i
        while ($i -lt $lines.Count -and -not $lines[$i].TrimEnd().EndsWith(";")) { $i++ }
        $moveRanges += , @($start, $i); $i++; continue
    }
    if ($i -lt $ddlJobExec -and $lines[$i] -match '^\s*INSERT\s+INTO\s+`sj_job_executor`') {
        $start = $i
        while ($i -lt $lines.Count -and -not $lines[$i].TrimEnd().EndsWith(";")) { $i++ }
        $moveRanges += , @($start, $i); $i++; continue
    }
    $i++
}
Write-Output "  待迁移语句: $($moveRanges.Count) 条（依赖表尚未建表）"

$seen = @{}; $dupRanges = @()
$i = 0
while ($i -lt $lines.Count) {
    $isSjJob = ($lines[$i] -match '^INSERT INTO `sj_job`\s*$') -or ($lines[$i] -match '^INSERT INTO `sj_job` VALUES \(')
    if ($isSjJob) {
        $start = $i
        while ($i -lt $lines.Count -and -not $lines[$i].TrimEnd().EndsWith(";")) { $i++ }
        $block = $lines[$start..$i] -join " "
        if ($block -match "'([^']*)'\s*,\s*'([^']*)'\s*,\s*'ruoyi_group'") {
            $key = "$($Matches[1])|$($Matches[2])"
            if ($seen.ContainsKey($key)) { $dupRanges += , @($start, $i); Write-Output "  重复种子删除: $key (L$($start+1)-L$($i+1))" }
            else { $seen[$key] = $start }
        }
        $i++; continue
    }
    $i++
}

$notifFixed = 0
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match '^INSERT INTO `sys_user_notification` VALUES \(' -and $lines[$i] -match 'NULL\), NULL\);\s*$') {
        $lines[$i] = $lines[$i] -replace 'NULL\), NULL\);\s*$', 'NULL, NULL);'; $notifFixed++
    }
}
Write-Output "  sys_user_notification 右括号错位修复: $notifFixed 行"

$toDelete = New-Object 'System.Collections.Generic.HashSet[int]'
$moved = @()
foreach ($r in $moveRanges) { for ($k = $r[0]; $k -le $r[1]; $k++) { [void]$toDelete.Add($k) }; $moved += ($lines[$r[0]..$r[1]]) }
foreach ($r in $dupRanges)  { for ($k = $r[0]; $k -le $r[1]; $k++) { [void]$toDelete.Add($k) } }

$out = New-Object 'System.Collections.Generic.List[string]'
for ($i = 0; $i -lt $lines.Count; $i++) { if (-not $toDelete.Contains($i)) { $out.Add($lines[$i]) } }
if ($moved.Count -gt 0) {
    $out.Add("")
    $out.Add("-- ----------------------------------------------------------------------------")
    $out.Add("-- 2026-09-21 修复：以下语句原先被合并到 sys_dict_data / sj_job 记录区，位于其依赖表")
    $out.Add("-- （sys_dict_type / sj_job_executor）的建表语句之前，全新安装时必然报")
    $out.Add("-- ERROR 1146 Table ... doesn't exist 而中断导入。统一移到文件末尾（所有建表之后）执行；")
    $out.Add("-- 语句本身均为幂等（INSERT IGNORE / 条件 UPDATE），重复执行安全。")
    $out.Add("-- ----------------------------------------------------------------------------")
    foreach ($m in $moved) { $out.Add($m) }
    $out.Add("")
}
Write-Sql $p $out.ToArray()
Write-Output "  ry-vue.sql: 迁移 $($moved.Count) 行（$($moveRanges.Count) 条语句）到末尾；删除重复种子 $($dupRanges.Count) 处"
Write-Output ""
Write-Output "全部完成。"
