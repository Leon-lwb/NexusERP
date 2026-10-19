# 物料身份、条码与追溯实施及验证记录

日期：2026-10-19。P0–P5对应的后端、PC、PDA、Station、App源码及历史修复工具已落地，最终自动化验证通过。交付状态为代码与验证包；现有业务库迁移、生产切换、实物核实和真机验收尚未执行。未提交或推送代码，未启停生产服务。原仓库受跟踪源码及原已记录的未提交源码改动保留；构建曾对共享原生依赖目录产生副作用，见下文如实记录。

## 基线与交付位置

隔离工作区根为`E:/Nexus/.worktrees/material-trace-20261018`，四仓库分支均为`codex/material-trace-20261018`。本轮所有修改保持未提交，完整文件路径、状态及SHA256见[四仓库变更清单](2026-10-19-material-trace-changes.json)。

| 仓库 | 原分支与基线 |
| --- | --- |
| nexus-erp | plus，1ab08b09 |
| nexus-pda | master，7a27994 |
| nexus-station | codex/station-pass-guard-20261002，0d8ebcb |
| nexus-app | main，d9173cc |

工具为Java21运行时、Maven3.9.11、Java `--release 17`、Node24.21及HBuilderX官方5.15编译器。依赖目录沿用junction，未升级依赖。Windows最终打包使用经实际Electron加载核验的已有N-API预编译模块。

## 实施内容

| 阶段 | 已实现内容 | 实际使用的门禁 |
| --- | --- | --- |
| P0 | 连续原子号段、SN流水传递、出入方向及精确筛选、混批绑定、OCR完整身份、异步打印数据源恢复 | 保留原事实与旧版本证明，新增回归 |
| P1 | common-trace、ERP正式身份和别名、四策略及版本、WO产品/组件冻结策略、ERP批次与MES子lot额度映射 | 注册初始REGISTERED；有使用时禁止裸切策略；严格模式双向核对库存/SN/计价余额 |
| P2 | 原URL来源守卫、冻结SN集合、过账组、原计价范围/尾差/退回原成本、实际用料预留及一次库存效果 | ERP本地事务；已过账库存/流水关闭裸CRUD，批次原码/来源/累计不能经维护接口重写 |
| P3 | 产品与组件批次分离、实际消耗和单件分配、禁止混批锁、受控冲销替换、预领料/倒冲分开、最后一件先入账 | 最终一次交付；不可变产品和用料清单；同库outbox/inbox/ACK、取消与修订握手 |
| P4 | 授权统一扫码、GTIN/结构化OCR断言、明确条码主对象、不可重绑/停用保留、正式SN标签及冻结打印任务、双向追溯和召回 | 歧义不默认选中；逐件/批次可能影响/未分配工单范围分开；深度/容量截断明确 |
| P5 | PC实际表单及详情事实、四端原命令重放、提交与跨模块确认分开、Station离线归属隔离 | 账号/通道/设备或操作员变化隔离旧任务；旧无归属记录不自动认领；业务拒绝停止自动重试 |
| 历史修复 | 35表完整快照、证据/CAS/幂等映射、实盘当前身份转换、原档策略采纳、恢复/补偿包、独立严格约束脚本 | 工具只生成JSON/待审SQL；未知不从当前主数据推算；已有新增业务不恢复旧库存镜像 |

新增表为ERP18、MES12，共30张；完整快照35表，其中18表要求全列投影。主安装结构、分库增量、预检及严格阶段脚本同步。主安装SQL保留原初始化语义，不作为存量迁移入口。

实际字段、接口及事件以[API/事件契约](../guides/material-trace-api.md)为准。历史部分见[对账修复](../guides/material-trace-repair.md)、[当前库存身份转换](../guides/material-trace-stock-conversion.md)、[在途WO原策略采纳](../guides/material-trace-policy-adoption.md)和[结构契约](../guides/material-trace-schema.md)。

## 最终自动化验证

| 范围 | 最终结果 | 原始证据 |
| --- | --- | --- |
| common-trace | 41/41，0失败/错误/跳过 | backend-integration-20261019-10.log |
| ERP | 5581/5581，0失败/错误/跳过 | 同上 |
| MES | 1285/1285，0失败/错误/跳过 | 同上 |
| Station后端 | 188/188，0失败/错误/跳过 | 同上；整个reactor退出0 |
| 管理端跨模块聚合/标签 | 39/39，0失败/错误/跳过 | admin-trace-tests-20261019-04.log；reactor退出0 |
| PC | 完整test:unit2091/2091；material-trace专项119/119；95个TS/Vue定向lint零告警；完整vue-tsc退出0；生产构建4723模块退出0 | plus-ui/MATERIAL_TRACE_PC.md及冻结哈希 |
| PDA | 完整237/237，0挂起；官方h5/app编译退出0 | ERP target/p5-client-submission-state/validation.json |
| App | 完整828/828，0挂起；官方h5/app/mp-weixin编译退出0 | 同上 |
| Station客户端 | 完整256/256，专项117/117；typecheck/build/Windows打包退出0 | Station dist/p5-submission-state/ownership-validation.json |
| 历史离线工具 | 178/178；容器入口安全4/4；实际生成SQL在自有MySQL5.7中32/32 | converter MySQL验证记录及schema-handoff检查点 |
| 通用MySQL5.7矩阵 | 268/268，0失败/阻塞；工具自测57/57；167个SELECT变体，共334次SELECT＋334次EXPLAIN | final-frozen-matrix-20261019-10 |
| 公共取消/journal/兼容及MES ACK | 独立release17编译后63/63、21/21；均零跳过 | final-java-contracts-20261019-04 |

各行及专项用例存在覆盖交集，不把它们相加作为独立测试总数。MySQL矩阵含14项严格tenant门禁，已计入268；不额外累加。SQL矩阵只证明其实际SQL与合成事务/锁/约束，不等同完整HTTP/RabbitMQ或两个独立数据库服务器的端到端证明。

最终主定向集成套件111/111，包含原来源before-lock断言、GTIN主对象三个真实绕过红例、正式数字SN/bare-GS1歧义、101候选溢出、孤立在库SN、零库存残值/反向计价组、实际MapStruct身份版本精确字符串、召回并发分配交接及原终态重放。该集和业务模块全量有交集；日志在`target/trace-integration-review/flowcard-fixture-green.log`。

最终后端命令：

```powershell
mvn -pl ruoyi-modules/ruoyi-erp,ruoyi-modules/ruoyi-mes,ruoyi-modules/ruoyi-station -am test "-DskipTests=false" "-Dprofiles.active="
mvn -pl ruoyi-admin -am test "-DskipTests=false" "-Dprofiles.active=" "-Dtest=MaterialIdentityTraceServiceTest,BatchRecallBoundaryIndependentReviewTest,RecallAllocationHandoffIndependentReviewTest,PdaManufacturingTraceServiceTest,PdaTraceSerializationTest,PdaTraceServiceTest,LabelCanonicalIdentityContextTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

两份Maven日志位于隔离工作区根的上一级`material-trace-20261018`目录。39项管理端定向选集没有代替其他admin无关功能全量测试。选定common-trace/ERP/MES/Station/admin五模块的2357个Java编译源前后SHA256无漂移；PC107个文件、Station归属修复14个文件、PDA/App受保护文件均重新核对哈希。SQL85项/Java96项输入并集144项签署清单无漂移，详见[MySQL最终记录](../guides/material-trace-mysql57-validation.md)。

此前失败保留为排错证据，不当作最终通过：跨模块打印夹具移入admin自含；批次并发夹具改成Java17显式关闭线程池；旧单据夹具补wm_barcode空表；两个MES夹具补必需策略mock且保留原断言。管理端H2完整驱动2.3.232曾与运行时旧拆分MVStore2.1.212混用；只在admin Surefire测试classpath排除旧拆分包，最终39项通过，运行时依赖保持原状。

## Station安装包与共享依赖副作用

最新未签名安装包：`nexus-station/dist/p5-submission-state/windows-ownership/NexusStation Setup 1.0.0.exe`，SHA256为`edeb113d46cb2b863bece8c45a7535cb6f3a38c8b2574a27b288704c6e83029f`。实际Authenticode状态NotSigned；没有执行安装程序。ASAR及共享prebuilt均在真实Electron33.4.11中加载，未枚举或打开设备。

此前一次默认Windows构建误触发native rebuild，经junction写入共享`@serialport/bindings-cpp/build`中的`binding.sln`、`bindings.vcxproj`、`bindings.vcxproj.filters`、`config.gypi`四个配置中间文件。没有事前快照，不能证明更早build产物未被覆盖，未擅自删除或声称完全恢复。此操作违反不rebuild的约束，已明确停止。

最终使用`npmRebuild=false`，当前实际加载prebuilt SHA256仍为`eead9115994f9ad9471cd607ead9aa5c3dbf460c878bafdde41b68b57679fefc`且可加载；后续归属修复轮17项native二进制及build文件内容、大小、mtime均未变。当前本机原生源码重编译仍缺可用Windows SDK，不能将预编译打包通过说成源码native rebuild通过。完整原始审计在Station的`ownership-validation.json`。

## 尚未执行的业务验收

- 未连接或迁移现有业务库，未导入主初始化SQL，未开启生产relay或STRICT。兼容发布默认relay=false，追踪物料不得退化为无身份数量写入。
- 真实历史原档、实物census、独立审批及歧义映射真实性未验；共享旧SN的多件库存、预留/在制、未知成本等不确定项目输出差异任务，不自动选择某一件继承旧身份。
- 没有真实备份恢复、生产消息水位切换、跨独立服务器HTTP/RabbitMQ全链路、现场扫码枪、打印机实际出纸、掉电或真机测试。SENT仅表示传输完成，不能冒充出纸。
- PDA/App官方编译不是签名APK/IPA和商店发布；Station包未签名、未安装。实际发布和人工业务闭环尚未验收。

[切换、回退及现场验收表](../guides/material-trace-rollout-checklist.md)保留逐场景“未执行”，用于目标环境取得授权后填写，不自动升级为通过。无新增业务时按原日志CAS恢复；已有新增业务时保留身份和事件，通过有来源补偿处理，不覆盖原流水、旧标签或已签DHR。
