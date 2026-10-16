# 自研报表本地迁移与基础数据初始化验证

日期：2026-10-16（Asia/Shanghai）。代码分支：plus；核对基线：183667d1。

## 授权与目标

用户先要求将plus分支新报表增量执行到本地并更新根README，随后明确指示：“不用备份旧表，直接删除积木报表相关物理表，系统菜单表进行更改，加入新报表相关表和数据”。因此本次没有创建数据库备份，按现行P0脚本的明确清单删除旧表，使用正常服务初始化新报表数据。

实际连接为本机 `localhost:3306/ry-vue`，MySQL **5.7.20-log**、可写master。初始 `rpt_*` 表为0，存在16张积木表及3张样例/临时表；不能套用历史报告中另一MySQL 5.7.14环境的迁移结果。凭证从本地现有加密配置在内存解析，不写入参数文件、文档或命令输出。

DDL执行窗口：2026-10-16T11:21:38.312682+08:00 至 2026-10-16T11:21:39.657216+08:00。

## 实际执行

三份仓库增量原文依次在同一系统主库会话执行；只在首次执行P0时进行历史清理，没有重导 `ry-vue.sql`，没有写入ERP/MES/IoT业务数据源。

| 增量文件（script/sql/update/） | 执行语句数 | 影响行数 | SHA-256 |
| --- | ---: | ---: | --- |
| `2026-10-14-report-p0.sql` | 66 | 60 | `0dfbb5013d6bb769f7d68de403fb17967cf55ff6b81a270c588eb78d69e3df1d` |
| `2026-10-14-report-e0-e1.sql` | 20 | 0 | `965a3d4bea88e5bae3965d9475263ec11bffd957f8906ad8037625cc3227c2fc` |
| `2026-10-15-report-e2-e7.sql` | 57 | 32 | `e0e212df07d58b9585f5a277f54cc792493568675110ed63cf79c2cf48ca4cc2` |

P0当前建表定义已包含E0/E1的5个导出进度字段，所以E0/E1走存在性保护，影响行数为0。DDL为MySQL隐式提交操作，未将其描述为可整体事务回滚。

P0删除16张 `jimu_*` 表以及 `test_monthly_report`、`tmp_report_data_1`、`tmp_report_data_income`。旧菜单ID3020、3021、3022、2053846181246812161及其角色、套餐引用已按脚本修复，新菜单根为23000。19张旧表最终均不存在；没有迁移旧积木模板内容。

## 结构、菜单与幂等验证

| 检查 | 实际结果 |
| --- | --- |
| 原生报表表 | 34张，名称与P0/E2–E7完整清单一致 |
| 字段及说明 | 332列，列顺序及中文COMMENT与脚本一致；34张表的引擎及中文表说明一致 |
| 主键及索引 | 68个，索引名与索引列顺序与脚本一致 |
| 旧报表表 | 0张 |
| 新菜单与管理员授权 | 33条菜单、role_id=1的33条菜单授权；全部非空组件路径均存在对应Vue文件 |
| 其他菜单与授权 | 排除旧资源和新报表菜单范围后，1278条菜单、1007条角色菜单关联逐行摘要一致 |
| 租户套餐 | 仅移除旧菜单标识，其他字段和菜单标识保留 |
| 扩展重放 | E0/E1的20条、E2–E7的57条再次执行，影响行数均0；34张报表表及菜单/授权/套餐摘要不变 |

P0的历史删除仅执行一次，扩展验证不重复清理P0。

## 基础数据初始化

将本次编译的报表模块、当前源码中的14个ERP/MES数据提供者和 `SystemReportAuthorizationAdapter` 装配到临时初始化器中，读取本地有效管理员账户及租户，并调用 `ReportDefinitionService.bootstrap`。报表创建、数据集登记、编译和发布沿用实际服务流程。提供者只读取代码契约，未配置业务查询数据源，因而不会触发业务取数或业务写入。

基础初始化在系统主库READ COMMITTED事务中执行，内部验证通过后提交：

| 数据 | 最终回读 |
| --- | ---: |
| 默认租户 | 000000 |
| 报表根目录 | 1 |
| ACTIVE数据集 / 固定数据集版本 | 14 / 14 |
| PUBLISHED基础报表 / 发布版本 | 17 / 17 |
| 有效固定数据集版本关联 | 17 |
| 已检查可读取的发布契约 | 17 |
| 运行 / 导出任务 | 0 / 0 |
| 查询、仪表盘、批次、订阅与交付实例 | 0；未创建合成业务结果或真实通知 |
| 正常服务审计 | 62条 |

基础模板涵盖库存、销售/采购执行及分区汇总、订单明细、采购/送货单据、物料/客户/供应商台账、工单跟踪、IQC记录和追溯，完整清单以[基础模板目录](../guides/报表基础模板目录与筛选口径.md)为准。

同一事务内再次调用bootstrap，返回仍为17份；`rpt_catalog`、`rpt_definition`、`rpt_version`、`rpt_dataset`、`rpt_dataset_version`、`rpt_acl`逐字段快照一致，没有重复定义或版本。审计按正常服务追加，不纳入元数据幂等比较。事务提交后，通过独立只读连接再次核对34表、14数据集、17已发布报表和17条有效发布关联。

## 验证命令与本地证据

- `python .codex/tmp/report-local-migration/run.py inspect`：只读识别目标与初始基线。
- `python .codex/tmp/report-local-migration/apply.py`：限定本机数据库身份，执行三份SQL并检查结构、菜单边界与扩展重放。该执行器要求初始无rpt表，本机完成后不可当作重放入口。
- `mvn -pl ruoyi-modules/ruoyi-report -am test -DskipTests=false "-Dprofiles.active=" "-Dtest=ReportFoundationTemplateTest,ReportDefinitionServiceTest,ReportDefinitionSecurityTest,ReportStoreTest,ReportEngineCompatibilityTest" -Dsurefire.failIfNoSpecifiedTests=false`：5个测试类共71项，失败0、错误0、跳过0；实际Surefire XML逐项汇总。
- 临时Java初始化器：使用同次编译的类和依赖，通过标准输入仅在内存接收连接参数，执行真实bootstrap、发布契约与元数据重放检查；返回0。
- 独立PyMySQL回读：14个启用数据集、17份已发布报表、17条有效固定版本关联，扩展菜单组件文件全部存在。
- `git diff --check -- README.md AGENTS.md doc/system/数据库增量变更记录.md doc/system/guides/NexusERP_自研报表模块使用指南.md`：通过；13个本次新增本地链接全部存在，新计划及验证记录的尾随空白检查通过。

本地执行器、日志及脱敏汇总位于Git忽略目录 `.codex/tmp/report-local-migration/`：`execution-evidence.json`、`bootstrap-evidence.json`、`final-readback.json`、`report-tests.log`。这些本地产物不作为跨环境迁移脚本，也不包含旧表备份。

## 文档与边界

根README已补充当前报表能力、基础模板、界面、技术栈、模块职责及按基线升级的顺序；数据库变更登记、使用指南和AGENTS中的本机现状同步更新，旧验证报告保留其历史日期及环境。

本次没有修改Java/Vue业务源码，没有重启或启停用户服务，没有启用独立SELECT-only查询账户、S3/清单硬配额、订阅调度或真实通知，没有进行打印机和完整浏览器业务端到端验收。生产和其他数据库未执行本次迁移。前端源码未变，本次未重复执行前端全量类型检查与构建。
