# 2026-10-19 本地测试数据库完整快照

本目录按仓库所有者2026-10-19明确指示，原样归档工作区现有的四个数据库SQL文件。所有者已确认这些内容是本地测试数据，并要求保留原始数据；没有做脱敏、重新导出、连接数据库或执行SQL。

## 文件和用途

| 压缩文件 | 来源 | 表数 | INSERT语句数 |
| --- | --- | ---: | ---: |
| `ry-vue-local-test-2026-10-19.sql.gz` | `script/sql/ry-vue.sql`，系统库/master | 216 | 25,984 |
| `ry-erp-local-test-2026-10-19.sql.gz` | `script/sql/ry-erp.sql`，ERP业务库 | 162 | 7,516 |
| `ry-mes-local-test-2026-10-19.sql.gz` | `script/sql/ry-mes.sql`，MES业务库 | 89 | 2,162 |
| `ry-iot-local-test-2026-10-19.sql.gz` | `script/sql/ry-iot.sql`，IoT业务库 | 16 | 16 |

压缩包保留源SQL原始字节与换行，已逐个解压并核对一致。Git对主SQL的文本换行规范化不改变SQL内容；`manifest.json`分别记录原始文件摘要、LF规范化后的摘要、压缩包摘要、大小与结构统计。`SHA256SUMS`用于核对本目录四个压缩文件。

这批完整测试快照包含当时的配置、测试账号和运行记录，与[2026-10-17公开初始化快照](../2026-10-17/README.md)的范围不同；旧快照保留不变，不能再假定它与当前主SQL完全一致。数据库记录中的文件引用不等于OSS文件、向量数据或本机工程文件也已备份。

## 下载后校验

Linux/macOS在本目录执行：

```sh
sha256sum -c SHA256SUMS
```

Windows PowerShell在本目录执行：

```powershell
Get-Content -LiteralPath '.\SHA256SUMS' | ForEach-Object {
    $parts = $_ -split '  ', 2
    $actual = (Get-FileHash -LiteralPath $parts[1] -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $parts[0]) { throw "校验失败：$($parts[1])" }
    Write-Host "校验通过：$($parts[1])"
}
```

有Python时，可解压到新目录，避免覆盖已有文件：

```sh
python -c "import gzip,pathlib; src=pathlib.Path('.'); dst=pathlib.Path('restored-sql'); dst.mkdir(); [(dst/p.name.replace('-local-test-2026-10-19.sql.gz','.sql')).write_bytes(gzip.decompress(p.read_bytes())) for p in src.glob('*-local-test-2026-10-19.sql.gz')]"
```

## 恢复边界

这批脚本含 `DROP TABLE`，只用于明确授权的全新空库或隔离测试恢复。系统、ERP、MES三个脚本不自带建库或USE语句，导入时须明确选择各自目标库；IoT脚本自带建库和 `USE ry-iot`，会选择该库，运行前须核对目标实例。不要把所有文件导入同一个库。恢复前先检查编码、目标MySQL版本和排序规则。MES脚本包含两个既有存储过程及DELIMITER/DEFINER定义，导入工具须支持分隔符切换，目标账户须具备相应权限。

存量环境使用[数据库变更记录](../../../../doc/system/数据库增量变更记录.md)核对增量，不直接重导这些主脚本。2026-10-19连接器配对Redis键修复没有数据库迁移；下载或升级Windows连接器无需导入任何SQL。

本次静态检查覆盖483张表、全部INSERT的列值数量及文件结束边界，未发现宽度不一致或表/INSERT截断。四库相对ba0fdc79均未新增/删除表，列集合与顺序不变。系统库运行记录中原有20个Unicode替换字符保留不动，已在manifest登记。执行了压缩解压和摘要核对，没有执行真实数据库恢复，也不代表服务器数据库已经更新。
