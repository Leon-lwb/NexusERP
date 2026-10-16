# PCB 工程功能合并与可共享系统库验证记录

> 日期：2026-10-17；源码分支 `codex/easyeda-pcb-draft-preview-20261016` → `plus`。本次按用户要求提交现有工作、合并工程功能，再生成可供其他开发者新装的系统库快照。以下结果限定本地与所列隔离环境。

## Git 集成

| 提交 | 内容 |
|---|---|
| `ab0ea8a5` | 保存 `plus` 工作区原有 AI 设计 / 实施文档，以及本地配置、报表迁移验证记录 |
| `50040e80` | 提交工程结果回传、OSS 归档后缀修复、连接诊断显示、响应式任务 / 预览 UI 与返回导航修复，共 31 个文件 |
| `1cab0e47` | 使用非快进合并，将完整工程功能分支并入 `plus` |

此前的 `11fedb39`（本地连接器）、`1e964aab`（任务与私有产物）、`b9dc938e`（线路 / 三维预览）、`0a52da22`（DRC 恢复记录）和 `135163d2`（验证指南）均已包含在合并历史中。

合并出现 7 处 AI 文档冲突：主分支为未实施计划，功能分支已补充实现、协议及验证记录。逐项比较后保留功能分支的更新说明，同时保留 `plus` 独有的四份配置 / 报表计划与验证文档。合并后前端、common、业务模块与连接器代码和已验证功能分支一致。没有删除工作树、撤销他人改动或改写历史。

本地配置、连接器运行目录、DPAPI 身份、日志、浏览器登录状态和一个无关的根目录本地二进制文件未加入源码提交。现有后端 8080、前端 5173 和工程连接器保持运行。

## 本次交付行为

- 连接检查通过现有设备 / 租约 / 所有者校验提交有界诊断摘要，持久化到任务记录；完成事务提交后推送任务事件。前端将连接检查结果与需要产物的 PCB 动作分别呈现，历史缺失版本不会被推断为新测量结果。
- 工程私有文件按类型使用可放入既有 `sys_oss.file_suffix` 字段的后缀，修复导出文件上传后归档失败。未为该修复改动数据库列。
- 工程任务列表、连接卡片、PCB 结果页和三维工作区采用统一的状态与响应式布局；真实 DRC 违规继续显示“部分成功”，可以展开原始任务记录。
- 返回按钮和侧栏进入 `/ai/chat/engineering/task`；`/ai/engineering/task` 按相同权限重定向；详情保持 `/ai/engineering/task/:taskId`。

功能与四张实际截图已加入[根 README](../../../README.md)，安装 / 配对 / 功能开关见[连接器指南](../guides/NexusERP_工程连接器使用指南.md)。

## 合并前后回归

工程核心检查在合并前功能工作树和合并后的主工作树执行，快照关联检查在主工作树执行；下表相关检查共 353 个用例，计数来自本次实际输出与 Surefire XML，失败 / 错误 / 跳过均为 0。[结构化摘要与历史 SQL 基线比较](../evidence/2026-10-17-engineering-integration.json)另行保留。

| 检查 | 命令与结果 |
|---|---|
| 后端工程与系统存储 | 根目录 `mvn -q -pl ruoyi-modules/ruoyi-ai,ruoyi-modules/ruoyi-system -am test -DskipTests=false -Dprofiles.active= -Dtest=Engineering*Test,SysEngineering*Test -Dsurefire.failIfNoSpecifiedTests=false`；20 个测试类，75 个用例 |
| 连接器 | `ruoyi-extend/nexus-engineering-connector/` 执行 `npm test`、`npm run type-check`；49 个用例，类型检查通过 |
| 工程 UI | `plus-ui/` 执行 `npm run test:engineering`、`npm run type-check`；81 个用例，类型检查通过 |
| 生产构建 | `VITE_APP_ENGINEERING_ENABLED=true` 下执行 `npm run build:prod`；4,639 个模块，1m 21s，退出码 0 |
| 快照关联回归 | `npm run test:report`；142 个用例；`node --test scripts/test-wm-rule-import-casing.mjs`；6 个用例，确认报表新装定义及拣货 / 上架菜单路径保留 |
| 差异与文档 | `git diff --check`；新增本地文档 / 截图链接、快照压缩包与主 SQL 的 SHA-256 一致性检查 |

构建仍有仓库已有 Sass 弃用和分块体积提示，不影响本次构建通过。没有将本地通过写成生产部署或人工投产验收。

## 额外历史 SQL 静态检查

补充执行 `python -m unittest discover -s script/sql/tests -p 'test_*.py' -v` 时，旧采购 DDL 契约套件 6 个用例仍有失败。为区分本次引入与已有问题，使用同一测试代码、相同且未修改的 ERP SQL，分别读取合并提交 `1cab0e47` 的旧系统 SQL 和新快照进行比较：旧基线为 33 条失败断言，新快照为 31 条，**没有新增失败标识**，错误与跳过均为 0。

剩余失败包括旧测试要求的显式 `NULL DEFAULT NULL` 写法、未修改的 ERP 安装定义与历史结构 / 索引契约的既有差异，以及种子 INSERT 格式；没有为使旧断言通过而修改采购 SQL 或降低契约要求。本次另用实际恢复后的 information_schema 对系统库全部列的可空性、默认值、排序规则和索引逐项核对，且与源库一致。旧 SQL 静态套件没有被记为通过，具体断言保留在本地忽略目录的基线比较记录中。

## 已完成的实际链路与界面验证

此前同次功能修复在本机正常登录后，经真实 AI 对话、Windows 连接器、嘉立创 EDA 专业版 4.1.60 和专用私有对象存储执行：

- 连接任务 `2111100775348236290`：SUCCEEDED，诊断可在“查看检查结果”中读取。
- PCB 任务 `2111100775474065409`：`temperature-humidity-demo` v1.0.0，50 × 35 mm 双层板，7 个已确认文件，总计 883,063 字节，各文件与源工程下载摘要一致。
- PNG 为 1669 × 1373，BOM 4 项，OBJ / MTL 实际渲染。DRC 有 8 个错误、0 个警告，状态保持 PARTIAL_SUCCESS；该模板仍需工程修改与审核。
- 13 项实际浏览器检查覆盖正常登录、返回 / 旧路由跳转、侧栏、刷新、线路 / DRC / 三维切换、源工程下载、390 px 窄屏和深色主题，浏览器无页面异常。界面验证复用已有任务，没有为截图新增工程任务。

[结果回传与存储修复记录](../plans/2026-10-16-engineering-result-feedback-fix.md)和[UI 导航验证记录](../plans/2026-10-16-engineering-ui-navigation.md)保留原始范围。根 README 的截图排除了全局个人账户栏。

## 系统库快照与恢复

在合并提交之后，仅使用只读事务读取本机 `ry-vue`，生成[标准新装 SQL](../../../script/sql/ry-vue.sql)、[2026-10-17 压缩备份与指南](../../../script/sql/snapshots/2026-10-17/README.md)和[清单](../../../script/sql/snapshots/2026-10-17/manifest.json)。

- 全部 216 张表、3,130 列、568 个索引；包含工程三表和自研报表 34 表。显式保留字符集、表 / 列排序规则和中文说明，移除本机自增计数。
- 21 张表共 9,262 条公开 / 合成初始化记录，其他 195 张表为空。包括菜单 / 管理员授权、字典、行政区划、编号规则、默认停用的内置工具、引擎版本元数据和新生成的演示管理员。
- 实际用户、服务密钥、远程工具配置、设备身份、聊天、任务、文件、报表私有资源、业务与运行记录均排除；不是本机私有数据的无损备份。应用配置与设备配对由新环境自行完成。
- 分别在任务专属 MySQL 5.7.44 和 8.0.46 临时实例中从空库完整恢复，对全部表的行数与种子内容 SHA-256、全部列定义 / 索引 / 中文说明 / 排序规则、字典 / 菜单关系进行核对。仅归一化版本间的整数显示宽度、utf8mb3 名称和 CURRENT_TIMESTAMP 表示，不忽略实际排序规则差异。
- 使用应用 JAR 中实际依赖，验证 Hutool BCrypt 可校验演示密码，并在 `databaseSchemaUpdate=false`、IDM / 异步执行器关闭的配置下启动 Flowable 7.0.0；流程定义、实例和待办均为空。
- `.gitattributes` 为主安装 SQL 固定 LF，保证 Windows 检出后仍与压缩备份及校验和一致。

详细结果在 [MySQL 5.7 恢复记录](../../../script/sql/snapshots/2026-10-17/restore-verification.json)和 [MySQL 8.0 恢复记录](../../../script/sql/snapshots/2026-10-17/mysql8-restore-verification.json)。源数据库写入次数为 0，本次没有导入、迁移或重建现有数据库。临时恢复实例在检查后清理，既有应用服务保持运行。生产部署、其他业务库新装、外部服务配置和工程投产审核不在本次恢复验证范围内。
