# ERP库存管理菜单、业务数据与流程核验记录

核验时间：2026-10-21至2026-10-22（Asia/Shanghai）。分支：plus。起始提交：43ae767a7964045f8c24be86ab26042bbde1bd06；初始工作区干净。2026-10-22用户已授权提交本轮变更并推送至Gitee；交付状态以Git提交及远端记录为准。

## 范围和环境

实际导航为“ERP系统 → 库存管理”，共有8项：库存清单、调拨管理、盘点管理、安全库存预警、库存台账校验、库存预留、生产退料、上料交接。已逐页通过浏览器进入并检查实际数据与状态。

原8080后端启动环境为dev，保持原进程运行。验证使用单独的18080后端和18081前端，后端local配置复用共享连接契约，连接本机TCP3306的ry-vue/ry-erp，租户000000。临时验证入口禁用了Scheduled注解处理器、SnailJob和Rabbit监听，未启停用户原有服务；连接和凭据没有写入本报告。数据库核验通道仅运行SELECT，业务写入通过正常认证API完成，未执行初始化、清库或改账脚本。

业务数据使用正常名称，由既有物料、批次、仓库和库位衔接；本机流程数据不等于外部真实交付、实物盘点或生产环境验收。申请和审批由本机同一个管理员账号完成，不代表生产双人审批验收。

## 八个菜单的结果

| 菜单 | 本轮数据/动作 | 最终结果 |
|---|---|---|
| 库存清单 | 平垫圈12 A级镀锌，IF20261004005；原料仓及半成品仓4个正库存维度 | 合计9.0000件，预留0；只读库存由来源单据形成 |
| 调拨管理 | TR202610210001，装配车间十月紧固件补料调拨，4件 | DRAFT → APPROVING → APPROVED → EXECUTED；原料仓L001减少4、半成品仓L020增加4 |
| 盘点管理 | PD202610220001，十月装配一线循环盘点，半成品仓钣金件区L022 | PC生成原行 → 保存实盘2 → 提交 → 审批；COMPLETED，原行BALANCED，账面2/实盘2/差异0 |
| 安全库存预警 | 交流接触器25A，IF20261010008，执行检查、处理登记、复检 | 原记录已处理；实存3/阈值4的缺口仍存在，复检保留1条新活动预警 |
| 库存台账校验 | 主物料单独核对及全量核对 | 主物料book=9、ledger=9、diff=0；另记录14项历史差异 |
| 库存预留 | 以上料交接实际单头和行作为来源，预留2、整单释放2、再按行释放 | 返回2.0000、2.0000、0；释放流水保留真实来源行，最终预留为0 |
| 生产退料 | TL202610220001，装配工序紧固件余料退库，1件；来源SCLL202610100001 | 核销并COMPLETED；批次B202610100004回原料仓L001，真实来源WM_MATERIAL_RETURN |
| 上料交接 | HJ202610220001，装配一线紧固件上料交接，2件 | 核销并SIGNED；仅从半成品仓L020扣2，向同仓L022入2，真实来源WM_MATERIAL_HANDOVER |

### 单据和明细主键

| 类型 | 编号 | 主键 |
|---|---|---|
| 调拨 | TR202610210001 | 2112935942397456386 |
| 交接 | HJ202610220001 | 2112946441067524098 |
| 退料 | TL202610220001 | 2112946441390485507 |
| 盘点 | PD202610220001 | 2112949414178852866 |

盘点原行ID为2112950023351177218，生成后保存实盘数没有删除或重建原行。交接预留、释放、出入库均绑定原交接行2112946441134632961。完整回读见同目录的inventory-workflow-evidence JSON文件。

### 独立SELECT核对的最终正库存

| 仓库ID | 库区ID | 库位ID | 批次 | 结存 | 预留 |
|---|---|---|---|---|---|
| 1 | 1 | 1 | B202610100004 | 1.0000 | 0.0000 |
| 1 | 1 | 1 | B202610100007 | 4.0000 | 0.0000 |
| 2 | 5 | 20 | B202610100007 | 2.0000 | 0.0000 |
| 2 | 6 | 22 | B202610100007 | 2.0000 | 0.0000 |

调拨和交接的成对流水各自数量守恒；预留/释放流水tx_flag=0；退料增加1件。SUM(stock_quantity)=SUM(tx_flag*tx_quantity)=9.0000。

## 已复现并修复的缺陷

| 问题 | 修复和验证 |
|---|---|
| 库存列表不在首次挂载加载；部分页面仅依赖onActivated | 调拨、盘点、退料、交接统一使用useListRefresh；真实Vue/KeepAlive回归由14通过10失败变为24通过，浏览器首开直接显示正式单据 |
| PC调拨只有库位编码，缺执行所需ID；详情丢失仓库/库区身份 | 保存与执行按本租户主数据解析编码、验证归属/启用/冻结/数量；VO保留身份，自动汇总数量 |
| 可把来源仓库的库位挂到另一目标仓库 | 在库存效果前核对来源/目标库位及库区所属关系 |
| SN适配持ERP单头锁调用独立流程事务，同步回调会等待外层锁 | 实物提交准备在独立ERP事务完成后再启动审批，保留原实物校验 |
| GENERATED盘点不能保存实盘数，放开整单编辑又会破坏快照 | 已生成状态只按原行ID更新完整实盘集合，重算差异；不接受客户端替换物料、仓库、账面数或状态 |
| 审批中盘点可以手工抢占过账 | 手工通道仅允许未配置审批流的GENERATED计划；审批计划由通过回调执行 |
| 退料/交接跨用户或不同幂等键重复完成会重复过账；零计划可无限核销 | 核销和完成共用单头FOR UPDATE锁，复核状态、正计划/非负实际量及4位小数，完成前再验实际不超计划 |
| 退料/交接包装成不存在的杂项单 | 真实8080接口复现“原始来源缺失或不唯一”，头仍DRAFT、0库存流水；修复后传递真实来源，补全交接追溯目录，最新实例完成过账。回归包含真实策略、选择服务、追溯切面和H2 Mapper |
| 交接按全仓FIFO误扣其他仓库，且目标库区ID丢失 | 出库约束来自原交接单头仓库，目标库区来自真实目标库位；真实流水为仓2/区5/位20 → 仓2/区6/位22 |
| PC建单可传tenantId和审计字段，跨租户插入孤立行或伪造创建者 | 退料/交接新增行清理客户端租户和审计字段，再由服务端注入；真实租户插件的隔离H2回归通过 |
| 并发释放使用旧净额，或整单释放后按行再次释放，能挪用他单预留 | 稳定顺序锁库存、当前读重算来源净额、整单也按真实来源行记录；无法核对的历史负粒度记录拒绝继续释放 |
| 过期库存仍计入可用量、可预留 | 可用量查询与预留分配同样排除过期批次，沿用当天有效的既有效期规则 |
| 只设置最高库存的物料漏扫、活动预警阈值过期 | 扫描包含maxStock，刷新活动预警的安全/最低/最高阈值 |
| 冻结库存被当作账面消失；没有库存行的孤立流水漏核；0.0001差异被容差掩盖 | 对账包含冻结库存和两侧物料并集，按数据库4位小数精度识别差异，避免Number转double损失 |
| 交接SN标准查询/冻结缺少权限映射；SN可覆盖原行目标；HANDOVER证明误当单向 | 使用原交接query/edit权限；冻结和过账均禁止改变原行目标；证明保留双边转移方向。新增5例先失败后通过，最终实际版本查询200 |
| 台账diffQty为字符串时0.0000被标红、缺失主数据的差异无法识别 | 按校验状态设置语义颜色，缺失名称时展示稳定物料ID和主数据缺失提示 |
| 退料/交接自动编号NOWDATE缺date_format | 真实建单复现NullPointerException: pattern；经编号配置API修复29502、29602为yyyyMMdd，主安装SQL同步，受限增量脚本已登记但未执行 |
| 调拨模板只有会自动完成的发起人任务，提交后无审批待办 | 经正常模型API保存并部署“调拨申请 → 仓库主管审批”模板，新部署绑定原业务配置，真实产生待办并完成；XML保存在doc/erp/workflows |

## 检查命令与结果

后端在仓库根运行：

```powershell
mvn -pl ruoyi-modules/ruoyi-erp -am test -DskipTests=false "-Dprofiles.active=" "-Dtest=Wm*Test,Pda*Test,InventoryMonitoringSafetyTest" -Dsurefire.failIfNoSpecifiedTests=false
```

最终1371用例，Failures=0、Errors=0、Skipped=0，BUILD SUCCESS。包括并发、事务回滚、真实Mapper租户隔离、混合粒度释放、状态防绕过以及严格单件边界回归。各轮重复运行的数字没有累加到最终计数。

前端在plus-ui运行：

```powershell
npm run type-check
node --test --test-concurrency=1 scripts/test-list-initialization.mjs scripts/test-warehouse-safety-test-wiring.mjs scripts/test-production-issue-warehouse.mjs scripts/test-certified-warehouse-workflow.mjs scripts/test-certified-warehouse-pages.mjs scripts/test-certified-warehouse-dialog.mjs scripts/test-material-trace.mjs scripts/test-material-trace-api.mjs scripts/test-material-trace-ui.mjs scripts/test-material-trace-paging.mjs
npm run build:prod
npm exec --no -- eslint src/views/erp/wm/stocktaking/form.vue src/views/erp/wm/stocktaking/index.vue src/views/erp/wm/transfer/index.vue src/views/erp/wm/return/index.vue src/views/erp/wm/handover/index.vue src/views/erp/wm/reconciliation/index.vue scripts/test-list-initialization.mjs
```

相关前端252例全部通过（含24例列表生命周期），0跳过；类型检查、最终生产构建及定向ESLint均退出0。仅对实际改动文件运行Prettier。另运行过npm run test:full-flow-ui：436例中434通过，2例既有失败均为仓储策略菜单2621/2616的主安装SQL种子缺失；它们不属于本次8个库存菜单，未改动这些菜单种子或失败断言。

## 尚未消除的业务数据差异

全量校验查出14项历史差异，其中6项未找到有效物料主数据。保留原库存和原流水，未补造期初、退库、领用或采购凭证，也未通过盘点把差异强行清零。以下记录需要按原业务凭据核对，不可当作已解决的数据验收。

| 物料ID | 物料编码 | 名称 | 账面 | 流水余额 | 差异 |
|---|---|---|---|---|---|
| 9900000001 | 主数据未找到 | 按物料ID核对 | 2830.0000 | 0.0000 | 2830.0000 |
| 9900000002 | 主数据未找到 | 按物料ID核对 | 925.0000 | 0.0000 | 925.0000 |
| 9900000003 | 主数据未找到 | 按物料ID核对 | 2535.0000 | 0.0000 | 2535.0000 |
| 9900000004 | 主数据未找到 | 按物料ID核对 | 1673.0000 | 0.0000 | 1673.0000 |
| 9900000005 | 主数据未找到 | 按物料ID核对 | 1375.0000 | 0.0000 | 1375.0000 |
| 9900000006 | 主数据未找到 | 按物料ID核对 | 1990.0000 | 0.0000 | 1990.0000 |
| 2108783860164546561 | IF20261010001 | 深沟球轴承 6204-2RS | 21.0000 | 26.0000 | -5.0000 |
| 2108783863096365058 | IF20261010002 | 深沟球轴承 6205-2RS | 17.0000 | 20.0000 | -3.0000 |
| 2108783865059299329 | IF20261010003 | 同步带 550-5M-15 | 14.0000 | 17.0000 | -3.0000 |
| 2108783866938347521 | IF20261010004 | 电感式接近开关 M12 | 9.0000 | 12.0000 | -3.0000 |
| 2108783868733509633 | IF20261010005 | 二位五通电磁阀 5V210-08 | 15.0000 | 19.0000 | -4.0000 |
| 2108783870507700226 | IF20261010006 | 标准气缸 SC32×100 | 6.0000 | 8.0000 | -2.0000 |
| 2108783872487411714 | IF20261010007 | 过滤减压阀 AFR2000 | 8.0000 | 10.0000 | -2.0000 |
| 2108783874236436481 | IF20261010008 | 交流接触器 25A | 3.0000 | 6.0000 | -3.0000 |

交流接触器仍有1件安全库存缺口；处理记录表示已核查和登记跟进，并不表示已采购或补货完成。此次没有清理或重命名用户原有演示/历史数据。

## 证据和实际边界

- 结构化证据：2026-10-22-inventory-workflow-evidence.json。
- 截图：screenshots/2026-10-22-inventory/stocktaking-count-saved.jpg、handover-signed.jpg、reconciliation-matched.jpg；仅保留业务区域。
- 初始检查、独立代码复核及后续补修都有对应回归；最后一轮1371例包含复核指出的3项严格交接边界。
- 原8080后端未重启，需加载最新构建后才具有本轮后端修复。18080/18081为本任务独立验证入口，未构建或部署生产JAR。
- 普通数量流程已在本机MySQL和PC界面验证。严格SN目标/证明及并发越权在隔离回归中验证，未进行真实SN实物、PDA真机、生产双人审批或生产部署验收。
- 数据库增量仅提供脚本；本机两个编号配置通过API更正，不代表其他环境已迁移。

## 改动文件

```text
doc/erp/plans/2026-10-21-inventory-workflow-verification.md
doc/erp/reports/2026-10-22-inventory-workflow-evidence.json
doc/erp/reports/2026-10-22-inventory-workflow-verification.md
doc/erp/reports/screenshots/2026-10-22-inventory/handover-signed.jpg
doc/erp/reports/screenshots/2026-10-22-inventory/reconciliation-matched.jpg
doc/erp/reports/screenshots/2026-10-22-inventory/stocktaking-count-saved.jpg
doc/erp/workflows/inventory-transfer-approval.bpmn20.xml
doc/system/数据库增量变更记录.md
plus-ui/scripts/test-list-initialization.mjs
plus-ui/src/views/erp/wm/handover/index.vue
plus-ui/src/views/erp/wm/reconciliation/index.vue
plus-ui/src/views/erp/wm/return/index.vue
plus-ui/src/views/erp/wm/stocktaking/form.vue
plus-ui/src/views/erp/wm/stocktaking/index.vue
plus-ui/src/views/erp/wm/transfer/index.vue
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/controller/WmMaterialHandoverController.java
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/controller/WmMaterialReturnController.java
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/controller/WmSerialSelectionController.java
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/controller/serial/SerialBusinessRoute.java
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/controller/serial/WmSerialBusinessAdapter.java
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/controller/serial/WmSerialBusinessAspect.java
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/controller/serial/WmSerialBusinessStore.java
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/domain/WmMiscIssueLine.java
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/domain/WmMiscRecptLine.java
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/domain/vo/WmTransferVo.java
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/WmSerialInventoryRepository.java
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/WmSerialInventoryService.java
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/PdaHandoverServiceImpl.java
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/PdaReturnServiceImpl.java
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/StockReconciliationServiceImpl.java
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmStockAlertServiceImpl.java
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmStockReservationServiceImpl.java
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmStockTakingPlanServiceImpl.java
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/WmTransferServiceImpl.java
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/strategy/impl/MiscIssueStrategy.java
ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/strategy/impl/MiscRecptStrategy.java
ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/WmHandoverStrictContractTest.java
ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/controller/serial/WmSerialSubmissionTransactionTest.java
ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/service/impl/InventoryMonitoringSafetyTest.java
ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/service/impl/PdaMaterialMovementSafetyTest.java
ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/service/impl/WmStockReservationServiceImplTest.java
ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/service/impl/WmStockReservationTransactionTest.java
ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/service/impl/WmStockTakingCountSafetyTest.java
ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/service/impl/WmStockTakingPlanServiceImplTest.java
ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/service/impl/WmTransferLocationSafetyTest.java
script/sql/ry-vue.sql
script/sql/update/2026-10-22-wm-inventory-code-date-format-ry-vue.sql
```
