# ry-vue 可共享安装快照（2026-10-17）

本目录供其他开发者初始化**全新系统空库**。快照在工程功能合并到 `plus` 后，从本机 `ry-vue` 的 MySQL 5.7.20-log 只读导出完整表结构，再加入公开基础目录和重新生成的演示账号。它不包含现有用户的私有配置与业务历史，不作为本机私有环境的无损恢复备份。

## 文件

| 文件 | 用途 |
|---|---|
| [../../ry-vue.sql](../../ry-vue.sql) | 当前标准新装入口，UTF-8 SQL，包含建表和公开初始化数据 |
| [ry-vue-public-2026-10-17.sql.gz](ry-vue-public-2026-10-17.sql.gz) | 本日期的压缩备份；解压内容与当前主 SQL 一致，导入时二选一 |
| [manifest.json](manifest.json) | 来源提交、版本、216 张表清单、列数 / 索引数、种子行数和内容摘要 |
| [SHA256SUMS](SHA256SUMS) | SQL 与压缩文件的 SHA-256，路径相对本目录 |
| [restore-verification.json](restore-verification.json) | MySQL 5.7.44 独立恢复、逐表核对及引擎启动结果 |
| [mysql8-restore-verification.json](mysql8-restore-verification.json) | MySQL 8.0.46 的补充恢复及引擎启动结果 |

表结构保留列、索引、中文说明、字符集和排序规则；显式写入源库的表 / 列排序规则，避免在不同 MySQL 版本上采用新的默认规则。只移除本机 `AUTO_INCREMENT` 计数。此备份覆盖系统库全部 216 张表、3,130 列和 568 个索引，包括工程三表、自研报表 34 表及工作流引擎表。ERP、MES、IoT 和独立 App 库仍分别使用对应安装 SQL。

## 初始化数据

21 张表提供 9,262 条公开 / 合成初始化记录，其余 195 张表只保留结构：

| 数据 | 数量 / 处理 |
|---|---|
| 菜单与管理员菜单授权 | 各 1,318 条；包含 7 条工程菜单 / 权限及当前原生报表入口 |
| 字典 | 249 类、1,038 项，仅默认租户 |
| 行政区划 | 5,112 条公共基础目录 |
| 编号规则 | 48 套规则、136 个组成；生成结果不导出，当前流水状态清零 |
| AI 内置工具 | 11 个；全部停用，配置 JSON 清空，管理员按需启用 |
| 默认租户 / 组织 / 岗位 / 角色 / 用户 | 重新构造的演示数据，租户 `000000`、用户 `admin` |
| 登录客户端 | 保留前端及 App 使用的公开 clientId，仅密码登录；client_secret 为空，无第三方 app_secret |
| 系统参数 | 5 项合成默认值；自助注册和文件列表预览关闭 |
| Flowable 版本元数据 | 引擎属性、Liquibase 变更集及未锁定记录；无流程实例或部署资源 |

演示密码为 **`admin123`**，使用新生成的 BCrypt 摘要，没有复制原有用户的密码摘要。首次登录后修改密码；演示账户及公开 clientId 用于新环境起步。

没有导出：真实人员 / 企业资料、模型与厂商凭据、远程 MCP 配置、OSS 凭据与对象记录、工程设备身份与任务产物、聊天 / 知识库 / 消息记录、签章及业务记录、报表定义 / 运行 / 私有交付、工作流实例、调度任务、日志及编号生成结果。截图展示的测试任务不会随 SQL 安装出现。

## 校验备份

在本目录执行：

```powershell
Get-FileHash ./ry-vue-public-2026-10-17.sql.gz -Algorithm SHA256
Get-FileHash ../../ry-vue.sql -Algorithm SHA256
```

结果应与 `SHA256SUMS` 和 `manifest.json` 一致。Linux / Git Bash 也可执行 `sha256sum -c SHA256SUMS`。

若只取得压缩备份，先解压为 SQL。仓库根目录可用 Python 解压到一个新文件：

```bash
python -c "import gzip,pathlib; p=pathlib.Path('script/sql/snapshots/2026-10-17/ry-vue-public-2026-10-17.sql.gz'); pathlib.Path('ry-vue-public.sql').write_bytes(gzip.decompress(p.read_bytes()))"
```

完整检出仓库时，直接使用 `script/sql/ry-vue.sql` 即可，无需解压或重复导入。

## 导入全新数据库

本 SQL **含 `DROP TABLE`**，仅用于全新空库。存量系统按[数据库变更记录](../../../../doc/system/数据库增量变更记录.md)核对基线并单独迁移，不能用此文件覆盖。

1. 为新环境准备独立 MySQL 实例 / 空库。源库 `lower_case_table_names=1`，根 Docker Compose 也使用该配置；工作流 SQL 使用大小写一致的表查找，新实例应按相同约定初始化。保留显式 `utf8mb4` 排序规则。
2. 从仓库根目录打开 MySQL 客户端，使用密码交互输入，避免将密码写入命令行：

   ```bash
   mysql --host=127.0.0.1 --port=3306 --user=root --password --default-character-set=utf8mb4
   ```

3. 选择一个尚不存在的系统库名，例如 `nexus_ry_vue_new`。先创建并确认空库，再执行导入；若建库失败或表数不为 0，停止并核对目标。

   ```sql
   CREATE DATABASE `nexus_ry_vue_new` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
   USE `nexus_ry_vue_new`;
   SELECT COUNT(*) AS existing_tables
     FROM information_schema.tables WHERE table_schema = DATABASE();
   -- existing_tables 必须为 0。以下相对路径以客户端启动目录为准。
   SOURCE script/sql/ry-vue.sql;
   SELECT COUNT(*) AS installed_tables
     FROM information_schema.tables WHERE table_schema = DATABASE();
   -- installed_tables 应为 216。
   ```

4. 将应用 `DB_MASTER_NAME` 指向该库并配置自己的账号 / 密码，按根 README 分别初始化其他业务库、Redis 和必要服务。数据库名也可以使用其他名称，但配置与实际目标须一致。
5. 使用租户 `000000`、`admin / admin123` 登录。报表中心点击“创建基础报表”生成代码内置模板；模型与 OSS 管理中填写自己的服务配置。按[工程连接器指南](../../../../doc/ai/guides/NexusERP_工程连接器使用指南.md)启用双端开关和工程工具，重新配对 Windows 设备。

## 恢复验证范围

本次分别在任务专属的 **MySQL 5.7.44 与 8.0.46** 空库实际恢复，两个版本均通过，见 [5.7 记录](restore-verification.json)与 [8.0 记录](mysql8-restore-verification.json)。检查包括全部表 / 列 / 索引 / 中文说明 / 排序规则，逐表行数及初始化内容 SHA-256、默认密码、字典与菜单引用、空白私有配置和运行表，并使用应用实际依赖验证 BCrypt 与 Flowable 启动。

两个临时实例已在验证后清理，应用后端 8080 与前端 5173 保持运行。恢复只在临时独立数据库执行；源 `ry-vue` 仅使用只读连接，未导入或改写现有库。外部模型、私有对象存储、ERP / MES / IoT 数据源、真实通知与生产环境仍需在各自环境配置和验证。
