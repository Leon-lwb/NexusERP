# ERP 前端专项审计报告（2026-09-20）

> 审计人: 子代理（只读，前端专项） | 范围: `plus-ui/src/views/erp/**`（137 vue）、`plus-ui/src/api/erp/**`（178 ts）、`plus-ui/src/views/cv/**`、`plus-ui/src/components/cv/**`、`plus-ui/src/views/index.vue` | 后端对照: `ruoyi-modules/ruoyi-erp/**` 的 Controller / BO / VO / 实体 / DDL | 分支: `feature/erp-mes-iot-audit-fix`
> 口径: `doc/audit/2026-09-20-审计执行规范.md`（§2.3 前端视角、§4.5 上下文预算、§4.6 批量调用、§6 双向不一致表）
> 先行上下文（不重复报告）: `doc/system/2026-09-06-全项目审查报告.md` §2.5/§10（F1-F7、P3 已修）、`doc/erp/plans/2026-09-19-so-module-handover.md`（so 已优化）、`doc/audit/2026-09-20-audit-crosscut.md`（权限种子）、`doc/audit/2026-09-20-audit-erp-bd.md`（`supply_type` 已在 ERP-BD-01 报出，本报告不复述）
> 方法: 全仓 `grep` 字段名双向核对 + 机器扫描（`ruoyi-erp` 全部 `*Vo.java` 字段 × `plus-ui/src` 全量标识符集合、前端 `form.*` 绑定 × 后端 domain 字段全集、表格列对齐/宽度、图表与监听器释放、删除二次确认）+ 抽样人工判读。所有行号均为本次实际打开文件所得。

## 0. 概览

| 编号 | severity | 类型 | 一句话结论 | 证据 |
|---|---|---|---|---|
| FE-ERP-01 | **P0** | 契约(前后端不一致) | 供应商评估页提交的「评估日期 `evalDate`」后端根本不存在（后端只有 `evalPeriod`）→ 评估日期静默丢失，且列表「评估日期」列恒为空 | `plus-ui/src/views/erp/pu/vendor-evaluation/form.vue:93`、`ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/pu/domain/bo/PuVendorEvaluationBo.java:43` |
| FE-ERP-02 | **P0** | 功能缺失/可达性 | 「工单报工」模块 UI 完全不可用：菜单 2304 + 5 个按钮权限 + 后端 6 个端点全齐，但前端 api 只有 2 个图表函数、页面是纯图表页，`PrdWorkReportVo` 的 29 个业务字段在 `plus-ui/src` 全仓 0 命中 | `script/sql/ry-vue.sql:92177`、`plus-ui/src/api/erp/prd/report/index.ts:4`、`plus-ui/src/views/erp/prd/report/index.vue:1`、`ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/prd/controller/PrdWorkReportController.java:46` |
| FE-ERP-03 | P1 | 契约(前端字段) | 采购订单「运费 `freightAmount`」BO/VO/实体三层都有，前端全仓 0 命中 → 运费无法录入、列表也无法展示，价税合计与运费脱节 | `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/pu/domain/bo/PuPurchaseOrderBo.java:86`、`plus-ui/src/views/erp/pu/order/form.vue:107` |
| FE-ERP-04 | P1 | 契约(前端字段) | 盘点方式 `countMode`（明盘 OPEN / 盲盘 BLIND）后端 BO/VO/实体都有、PDA 端已按它分支，PC 盘点计划页无该字段 → 只能建明盘，盲盘不可用 | `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/domain/bo/WmStockTakingPlanBo.java:88`、`ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/impl/PdaStocktakingServiceImpl.java:79` |
| FE-ERP-05 | P1 | 契约(前端字段) | 另有 8 组后端 VO 字段在 `plus-ui/src` 全仓 0 命中（年初余额、质检单号、条码当前流水、委外收货目标仓/收货人、资产剩余月数等）→ 页面信息缺失（见 §6） | `GlAccountBalanceVo.java:54`、`PrdWorkOrderVo.java:287`、`WmBarcodeConfigVo.java:87`、`WmSubcontractReceiveVo.java:54`、`FaAssetVo.java:37` |
| FE-ERP-06 | P1 | 金额精度 | 采购订单表单在模板内联用 `数量×单价×汇率` 重算行金额，且改汇率只重算 `lineAmount`、不重算税额/价税合计 → 界面金额与落库金额不一致 | `plus-ui/src/views/erp/pu/order/form.vue:121` |
| FE-ERP-07 | P1 | 前端健壮性 | 3 个 ERP 报表页 `echarts.init` 无 `dispose`、`resize` 监听无 `removeEventListener`，每次查询重复注册 → 图表实例与监听器泄漏，无 loading/空态 | `plus-ui/src/views/erp/prd/report/index.vue:48`、`:64`、`:84` |
| FE-ERP-08 | P1 | 契约(字典硬编码) | 固定资产页同时用字典 `fa_asset_status` 与本地硬编码 `statusLabel/statusType` 两套映射；`prd/subcontract/detail.vue` 状态标签全硬编码 → 后端改字典前端不同步 | `plus-ui/src/views/erp/fin/fa/asset.vue:21`、`:203`、`plus-ui/src/views/erp/prd/subcontract/detail.vue:435` |
| FE-ERP-09 | P2 | 前端布局 | 63 处金额/数量列用 `align="center"`（同项目其他页如 `fin/fa/asset.vue:49` 用 `align="right"`）→ 数字不右对齐，无法按位比较 | `plus-ui/src/views/erp/pu/invoice/index.vue:70`、`prd/subcontract/index.vue:57` |
| FE-ERP-10 | P2 | 前端布局 | 多个列表/明细表整表无 `width`/`min-width`（`prdwork/form.vue` 30 列、`receive/detail.vue` 22 列、`sch/worker` 20 列）→ 横向溢出，与列表页标准 §一.2 明确要求相悖 | `plus-ui/src/views/erp/prd/prdwork/form.vue`、`plus-ui/src/views/erp/wm/receive/detail.vue` |
| FE-ERP-11 | P2 | 前端字段 | 固定资产列表只展示 `methodLabel`，VO 的 `categoryMethod`（类别默认折旧方法）无任何展示；处置损益科目只显示 ID 不显示名称 | `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/fin/fa/domain/vo/FaAssetVo.java:31`、`plus-ui/src/views/erp/fin/fa/category.vue:84` |
| FE-ERP-12 | P2 | 前端字段 | `views/erp/po/requisition/index.vue` 状态筛选项硬编码小写 `draft/submitted/approved/rejected/closed`，与同项目其他单据的大写状态常量风格相反，未与后端状态常量核对（**未完成验证，见 §5 未覆盖**） | `plus-ui/src/views/erp/po/requisition/index.vue:25`、`:123` |

> 说明: 本报告**未**重复报告 `so` 模块已优化项、`ERP-BD-01 supply_type`（`doc/audit/2026-09-20-audit-erp-bd.md:11`）、以及 crosscut 已覆盖的权限种子问题。另：crosscut §1.2「A 类 25 个大小写不一致」**在当前代码中已不复现**（详见 §5）。

## 1. P0 详述

### FE-ERP-01 供应商评估「评估日期」是前端虚构字段，提交即丢

- **证据**:
  - 前端表单: `plus-ui/src/views/erp/pu/vendor-evaluation/form.vue:93`（`<el-form-item label="评估日期" prop="evalDate">`）、`:94`（`v-model="form.evalDate"` 日期选择器）、`:138`、`:191`（`evalDate: null` 初始化）
  - 前端列表: `plus-ui/src/views/erp/pu/vendor-evaluation/index.vue:95`（`<el-table-column label="评估日期" prop="evalDate">`）、`:97`（`parseTime(scope.row.evalDate, ...)`）
  - 前端类型: `plus-ui/src/api/erp/pu/vendor-evaluation/types.ts:17`（`evalDate?: string;`）
  - 后端: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/pu/domain/bo/PuVendorEvaluationBo.java:42`（`@NotBlank(message = "评估周期不能为空")`）、`:43`（`private String evalPeriod;`）
  - 后端**无** `evalDate`: `grep "evalDate|evaluationDate" ruoyi-modules/ruoyi-erp/src/main/java` → **0 命中**（本次实测）
- **现状**: 后端评估单的日期语义字段是 `evalPeriod`（`@NotBlank`，语义为「评估周期」），前端 `form.vue:30-31` 也确有 `evalPeriod` 月选择器且 `:144` 有必填规则 —— 但同一表单**又多出一个** `evalDate` 日选择器，且列表列绑定的也是 `evalDate`。
- **触发路径**: 供应商评估 → 新增 → 填「评估日期」→ 保存。
- **后果**: ① `evalDate` 不在 BO 中，Jackson 反序列化后无处可存 → **用户填写的评估日期静默丢弃**（无报错，因为未知属性不报错）；② 列表「评估日期」列读 `row.evalDate`，而 VO 只回 `evalPeriod` → **该列永远空白**，用户以为数据没保存；③ 同一表单同时存在「评估周期（月）」与「评估日期（日）」两个近义必填控件，业务语义重复且互相矛盾。
- **修复建议**: 删除 `form.vue:93-94`、`:138`、`:191` 的 `evalDate` 及 `index.vue:95-97` 的该列，列表改绑 `evalPeriod`（`value-format="YYYY-MM"`，展示用 `parseTime(row.evalPeriod, '{y}-{m}')`）；同步删除 `types.ts:17` 的 `evalDate?: string`。若业务确实需要「评估完成日」，则属新增字段需求，需 `ALTER TABLE pu_vendor_evaluation ADD COLUMN eval_date date NULL` 并登记增量脚本。
- **影响面**: `plus-ui/src/views/erp/pu/vendor-evaluation/{form,index}.vue`、`plus-ui/src/api/erp/pu/vendor-evaluation/types.ts`；**无需 DDL**（推荐方案）；建议补一条「表单字段 ⊆ BO 字段」的前端契约测试。
- **测试现状**: `ruoyi-modules/ruoyi-erp/src/test/java/...` 无前端契约测试（前端无测试工程）。

### FE-ERP-02 「工单报工」菜单指向纯图表页，报工单 CRUD 在 UI 上完全不可达

- **证据**:
  - 菜单与权限齐备: `script/sql/ry-vue.sql:92177`（菜单 2304「工单报工」，`component='erp/prd/report/index'`，`perms='prd:report:list'`）、`:92193-92197`（`prd:report:{query,add,edit,remove,export}` 五个按钮权限全部已播种）
  - 后端端点齐备: `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/prd/controller/PrdWorkReportController.java:31`（`@RequestMapping("/erp/prd/report")`）、`:45-46`（`prd:report:list` + `@GetMapping("/list")`）、`:59-61`（export）、`:75-77`（`/{reportId}`）、`:90-93`（add）、`:106-109`（edit）、`:122-125`（remove）
  - 前端 api 层只有 2 个图表函数: `plus-ui/src/api/erp/prd/report/index.ts:4-6`（`completionRate`）、`:8-10`（`efficiency`）—— **无 list/export/get/add/edit/remove**
  - 前端页面是纯图表页: `plus-ui/src/views/erp/prd/report/index.vue:1-26`（只有「月数」输入 + 两个 400px 图表容器），无表格、无表单、无分页
  - 字段全仓 0 命中: `grep "reportedQty|reportNo|reportQty" plus-ui/src` → **0 命中**；机器扫描显示 `PrdWorkReportVo` 的 29 个业务字段（`reportNo, reportStatus, woName, unitCode, routeCode, operationSeq, taskNo, lotNumber, batchNo, reportedQty, laborScrapQty, materialScrapQty, otherScrapQty, uncheckQty, cumulativeQty, reportDate, laborHours, machineHours, waitHours, pauseHours, helperIds, helperNames, qualityCheckId, qualityCheckNo, reportChannel, submitBy, submitTime, auditBy, auditTime, auditRemark`，见 `PrdWorkReportVo.java:362`）在 `plus-ui/src` 全部 0 命中
- **现状**: 后端 `PrdWorkReport`（报工单：报工数量、良品/工废/料废/其他废、工时、报工渠道、审核人/审核时间）具备完整 CRUD 与权限种子，菜单也指向 `/erp/prd/report/index`；但该路径下的页面实现的是「工单完工率/生产效率」统计图表（对应 `/erp/prd/report/completionRate`、`/efficiency` 两个**不在** `PrdWorkReportController` 里的报表端点），报工单本身没有任何列表、详情或录入界面。
- **触发路径**: 侧边栏「生产管理 → 工单报工」→ 看到的是两张统计图；无法查询任何一条报工记录，也无法录入/修改/审核报工。
- **后果**: ① 报工业务在 PC 端完全不可用（报工数据只能来自 MES/PDA 等其他入口，ERP 侧报工单成为只写不读的孤岛）；② 菜单名称（「工单报工」）与页面内容（统计图表）语义不符，用户与实施人员会误判功能已具备；③ 29 个报工字段（含审核、工时、四类废品数量）在界面上完全不可见，报工异常无法在 ERP 侧排查。
- **修复建议**: 二选一并明确登记 —— ①**补页面**（推荐）: 新增 `views/erp/prd/report/list.vue` 作为报工单列表（列: `reportNo/woName/operationSeq/reportedQty/laborScrapQty/materialScrapQty/reportStatus/reportDate`，操作列 `查看`），`api/erp/prd/report/index.ts` 补 `listReport/getReport/delReport` 等函数，菜单 2304 的 `component` 改为 `erp/prd/report/list`，把现有图表页挂到独立菜单（如「生产报表」）并复用 `prd:report:list` 之外的报表权限；②**改菜单**：把 2304 改名为「生产报表」并把 5 个按钮权限（`ry-vue.sql:92193-92197`）改为 `prd:report:chart`，同时明确「报工录入只在 MES/PDA」。
- **影响面**: `plus-ui/src/api/erp/prd/report/{index,types}.ts`、`plus-ui/src/views/erp/prd/report/*`、`script/sql/ry-vue.sql:92177,92193-92197`；**无需 DDL**；需在 `doc/system/数据库增量变更记录.md` 登记菜单变更。
- **测试现状**: 无前端测试；后端 `PrdWorkReportController` 的端点无 UI 消费者（属"后端有端点但 api 层缺函数"的典型）。

## 2. P1 详述

### FE-ERP-03 采购订单「运费」字段三层齐备、前端全无

- **证据**: 后端 `PuPurchaseOrderBo.java:86`（`private BigDecimal freightAmount;`）、`PuPurchaseOrderVo.java:87`（同）、`PuPurchaseOrder.java:103`（同，实体列）；前端 `grep "freightAmount" plus-ui/src` → **0 命中**；`plus-ui/src/views/erp/pu/order/form.vue:107`（`<el-divider>财务信息</el-divider>`）分组内仅 `:109-149` 币别/汇率/审批流程三个字段。
- **触发路径**: 采购 → 采购订单 → 新增/修改 → 「财务信息」分组。
- **后果**: 采购运费无处录入（后端 `freightAmount` 恒为 null/0），采购订单的到岸成本、后续应付/对账的运费分摊缺失；列表也不展示运费，业务人员只能写在备注里。
- **修复建议**: 在 `form.vue:107` 的「财务信息」分组内 `:134-148` 之后增加「运费」`el-input-number`（`:precision="2"`、`:min="0"`、`style="width:100%"`）绑定 `form.freightAmount`，并把该 `el-col` 从 3 列布局改为 4 列（或另起一行，避免 `:span="8"` 挤压）；`views/erp/pu/order/index.vue` 金额区补「运费」列（`align="right"`）。若后端价税合计未含运费，需与后端确认口径后一并修正展示。
- **影响面**: `plus-ui/src/views/erp/pu/order/{form,index}.vue`；**无需 DDL**（列已存在）。
- **测试现状**: 无。

### FE-ERP-04 盘点方式 `countMode` 前端全无，盲盘不可用

- **证据**: 后端 `WmStockTakingPlanBo.java:88`、`WmStockTakingPlanVo.java:93`、`WmStockTakingPlan.java:85`（`private String countMode;`）；PDA 侧已按它分支且做值域校验: `PdaStocktakingServiceImpl.java:79`（`String countMode = StringUtils.isNotBlank(bo.getCountMode()) ? bo.getCountMode() : "OPEN";`）、`:80`（仅允许 `OPEN`/`BLIND`）、`:91`（回写）；`PdaStocktakingCreateBo.java:25`、`PdaStocktakingDetailVo.java:20` 也有该字段。前端 `grep "countMode" plus-ui/src` → **0 命中**。
- **触发路径**: 仓储 → 盘点计划 → 新增（PC 端）。
- **后果**: PC 建单时 `countMode` 永远为空，后端落库取默认 `OPEN` → **PC 端只能创建明盘计划，盲盘（账面数量对盘点人隐藏）无法从 PC 发起**；盘点单列表也看不到盘点方式，仓管无法分辨当前计划是明盘还是盲盘，直接影响盘点准确性。
- **修复建议**: `views/erp/wm/stocktaking/index.vue` 搜索区与列表加「盘点方式」列（字典 `erp_stocktaking_count_mode`，`OPEN=明盘/BLIND=盲盘`；若字典不存在需先补 `sys_dict_type`+`sys_dict_data`），`form.vue` 新增 `el-select`（必填）绑定 `form.countMode`，默认 `OPEN`。PDA 侧已兼容，无需改后端。
- **影响面**: `plus-ui/src/views/erp/wm/stocktaking/{index,form}.vue`、`api/erp/wm/stocktaking/types.ts`；**需补字典种子**（DDL 无变更）。
- **测试现状**: `PdaStocktakingServiceImpl` 有 PDA 侧覆盖，PC 侧无。

### FE-ERP-05 后端 VO 有、前端全仓 0 命中的字段（8 组）

- **证据**: 机器扫描（`ruoyi-erp` 全部 `*Vo.java` 字段名 × `plus-ui/src` 全量标识符集合，16276 个 token）+ 逐个 `grep` 复核，结果见 §6 表。逐条后端证据: `GlAccountBalanceVo.java:54`（`yearOpeningDebit`）、`:57`（`yearOpeningCredit`）、`GlAccountBalance.java:55,58`；`PrdWorkOrderVo.java:287`（`qualityCheckNo`）、`PrdWorkOrder.java:298`、`PrdWorkOrderBo.java:253`；`WmBarcodeConfigVo.java:87`（`currentSequence`）、`WmBarcodeConfigBo.java:77`、`WmBarcodeConfig.java:58`；`WmSubcontractReceiveVo.java:54,70`（`targetWarehouseCode`/`receiverName`）、`WmSubcontractReceiveBo.java:51,67`、`WmSubcontractReceive.java:54,70`；`FaAssetVo.java:37`（`remainingMonths`）、`:31`（`categoryMethod`）；`FaAssetCategoryVo.java:33`（`disposalLossAccountName`）；`PdaTodoItemVo.java`（`lineCount`/`doneLineCount`，PDA 端，非 PC 缺口）。
- **后果**（逐条具体化）:
  - `yearOpeningDebit/Credit`：科目余额表/试算平衡表无法展示年初借贷方余额，期初-发生-期末无法在界面自洽核对（`views/erp/fin/gl/report/trialBalance.vue`）。
  - `qualityCheckNo`：生产工单列表/详情看不到关联质检单号，工单与 IQC/IPQC 的关联只能靠人工比对（`views/erp/prd/prdwork/index.vue`、`form.vue`）。
  - `currentSequence`：条码规则页看不到当前流水号，改规则前无法判断是否会撞号（`views/erp/wm/barcode/config/index.vue`）。
  - `targetWarehouseCode`/`receiverName`：委外收货单看不到目标仓与收货人，收货过账目标不透明（委外相关页面）。
  - `remainingMonths`：固定资产卡片无「剩余折旧月数」，无法判断何时提足折旧（`views/erp/fin/fa/asset.vue`）。
  - `categoryMethod`：资产列表只显示 `methodLabel`（实际使用的折旧方法），看不到类别默认方法，改类别时的默认值来源不可见。
- **修复建议**: 按 §6 表逐条在对应 `types.ts` + 列表列/表单/详情补字段；金额类列统一 `align="right"` + 千分位格式化；`yearOpeningDebit/Credit` 与 `remainingMonths` 属纯展示，前端即可完成，**无需 DDL**。
- **影响面**: 上列各页面与其 `api/erp/**/types.ts`；无 DDL；建议同步补前端类型（当前多个 `types.ts` 缺失这些字段）。
- **测试现状**: 无。

### FE-ERP-06 采购订单在模板内联重算金额，改汇率后税额/价税合计不同步

- **证据**: `plus-ui/src/views/erp/pu/order/form.vue:118`（汇率 `el-input`）、`:121-131`（`@change` 内联 `lineList.forEach(r => r.lineAmount = (数量 × 单价 × 汇率).toFixed(2))`）。同一文件 `:107`「财务信息」分组只处理币别/汇率/审批流程。
- **触发路径**: 采购订单 → 修改「汇率」→ 行金额被就地改写，但行税额、价税合计、订单总额不随之更新。
- **后果**: ① 用户在界面上看到的是**前端自算**的行金额，与后端保存时重算的金额可能出现分位差异（前端 `toFixed(2)` 四舍五入时机与后端 BigDecimal 舍入模式不一定一致）；② 改汇率后 `taxAmount`/`totalAmount` 仍是旧值，界面自相矛盾，用户可能据此判断是否审批；③ 行金额由模板内联表达式维护，后续任何"重新计算"入口都必须复制这段逻辑（可维护性差，且已出现漏算）。
- **修复建议**: 把行金额计算收敛到一个 `recalcLines()` 函数，一次重算 `lineAmount/taxAmount/totalAmount/订单总额`；金额与税率口径以后端为准，保存成功后用后端返回值回填；建议后端在 add/edit 响应中返回重算后的 VO，前端不再本地兜底。
- **影响面**: `plus-ui/src/views/erp/pu/order/form.vue`；若后端未重算税额则需后端配合（本报告未深读后端实现，属**待与后端口径核对**项）；无需 DDL。
- **测试现状**: 无。

### FE-ERP-07 三个 ERP 报表页图表与监听器未释放、无加载/空态

- **证据**: `plus-ui/src/views/erp/prd/report/index.vue:48`（`echarts.init(completionChartRef.value)`）、`:64`（`window.addEventListener('resize', () => chart.resize())`）、`:84`（第二处同样注册）；机器扫描 `views/erp/{prd,pu,wm}/report/index.vue` 三个文件均为「有 `echarts.init` 无 `dispose`」且「有 `addEventListener` 无 `removeEventListener`」；`prd/report/index.vue:37-45` 的 `loadData()` 每次点「查询」都会重新 `init` 并再注册一个 resize 监听。
- **触发路径**: 进入报表页 → 反复点「查询」→ 切换菜单/标签页 → 再进入。
- **后果**: ① 每次查询新增一个 `resize` 监听且不注销 → 窗口拖动时 resize 回调数线性增长（卡顿），组件卸载后回调仍持有已销毁 DOM 的引用（内存泄漏）；② 同一 DOM 反复 `echarts.init` 而不 `dispose`，ECharts 会告警并累积实例；③ `loadData` 无 try/catch、无 loading 状态、接口返回空数组时图表静默空白，用户无法区分"没数据"和"加载失败"。
- **修复建议**: 在 `onBeforeUnmount` 中 `chart.dispose()` 并 `removeEventListener`；把 resize 处理改成 `ResizeObserver` 或在 `init` 前先 `echarts.getInstanceByDom(el)?.dispose()`；`loadData` 加 `loading` ref + `try/catch/finally` + 空数据提示（`el-empty`）。
- **影响面**: `plus-ui/src/views/erp/prd/report/index.vue`、`plus-ui/src/views/erp/pu/report/index.vue`、`plus-ui/src/views/erp/wm/report/index.vue`（同型问题）；无需 DDL。
- **测试现状**: 无。

### FE-ERP-08 状态标签双份维护：字典与硬编码映射并存

- **证据**: `plus-ui/src/views/erp/fin/fa/asset.vue:21`（`<el-option v-for="dict in fa_asset_status" ...>`，搜索区用字典）、`:118`（`fa_depreciation_method` 用字典）、`:154`（`proxy.useDict('fa_asset_status','fa_depreciation_method')`），但同一文件 `:203-209` 又硬编码 `statusLabel/statusType`（`IN_USE/CWIP/DISPOSED` → 中文/颜色），`:197-201` 硬编码 `methodLabel`（`STRAIGHT_LINE/DOUBLE_DECLINING/SUM_OF_YEARS/MANUAL`）→ 同一状态两套来源；`plus-ui/src/views/erp/prd/subcontract/detail.vue:435`（`statusLabelMap` 硬编码）+`:444`（`statusLabelMap[s] || s`，未命中直接显示原始码）。
- **触发路径**: 系统管理 → 字典管理 → 修改 `fa_asset_status` 的标签/颜色 → 回到资产页。
- **后果**: 搜索下拉与状态列/折旧方法列显示不一致（下拉走字典、单元格走硬编码）；字典新增一个状态值后，列表该行显示英文原始码（如 `DISPOSED`）而非中文，运维改了字典却"没生效"。`prd/subcontract/detail.vue:444` 的 `|| s` 兜底把原始码直接暴露给用户。
- **修复建议**: 统一走字典 —— 单元格改用 `<dict-tag :options="fa_asset_status" :value="scope.row.status" />`，折旧方法列改用 `fa_depreciation_method` 字典；删除本地 `statusLabel/statusType/methodLabel`（`:197-209`）与 `statusLabelMap`（`detail.vue:435`）。同型硬编码 option 另见 `views/erp/fin/cost/index.vue:8-12`、`views/erp/prd/capacity-calendar/index.vue:133-135,206-208`、`views/erp/wm/barcode/index.vue:25-27,164-166`、`views/erp/bd/serial-number-rule/index.vue:19-29,127-129,155-158,167-169`、`views/erp/wm/productionissue/form.vue:63-64`。
- **影响面**: 上列各页面；需确认对应字典类型是否已存在（`fa_asset_status`/`fa_depreciation_method` 已存在，其余需补种子）；无需 DDL。
- **测试现状**: 无。

## 3. P2 详述

| 编号 | 结论 | 证据 | 修复要点 |
|---|---|---|---|
| FE-ERP-09 | 金额/数量列未右对齐（63 处 `align="center"`），同项目其他页已用 `align="right"`，全站不一致 | `views/erp/pu/invoice/index.vue:70-73`、`views/erp/prd/subcontract/index.vue:57-59`、`views/erp/prd/prdwork/index.vue:92-93`、`views/erp/prd/rework/index.vue:57,63,64`、`views/erp/fin/settlement/index.vue:40`、`views/erp/bd/material/index.vue:415,418`、`views/erp/so/invoice/index.vue:77-80`；正例 `views/erp/fin/fa/asset.vue:49,52,55` | 金额/数量/汇率列统一 `align="right"`，并统一千分位格式化（`asset.vue:211-213` 的 `fmt` 只有 `toFixed(2)`，无千分位） |
| FE-ERP-10 | 整表无列宽，横向溢出（与 `doc/erp/specs/2026-06-30-list-page-standard-design.md:12` 明确批评的"全不设 width 必然横向溢出"一致） | `views/erp/prd/prdwork/form.vue`（30 列）、`wm/receive/detail.vue`（22）、`sch/worker/index.vue`（20）、`sch/team/index.vue`（19）、`wm/receive/form.vue`（17）、`so/customerorder/index.vue`（16）、`wm/productionrecpt/index.vue`（15）、`prd/prdwork/index.vue`（14）、`bd/workstation/index.vue`（13）、`bd/measure/index.vue`（13）、`wm/arrivalnotice/index.vue`（12） | 按标准 §一.2 给业务列设 `min-width`、日期列 160~180、操作列固定 `width` + `fixed="right"` |
| FE-ERP-11 | 资产列表只有 `methodLabel`，`categoryMethod`（类别默认折旧方法）无展示；处置损益科目只显示 ID 不显示名称 | `ruoyi-modules/.../fin/fa/domain/vo/FaAssetVo.java:31`、`FaAssetCategoryVo.java:33`；`plus-ui/src/api/erp/fin/fa/index.ts:14`（只有 `disposalLossAccountId`）、`views/erp/fin/fa/category.vue:84-85` | 列表加「类别折旧方法」列；处置损益科目用后端返回的 `*Name` 展示（当前前端拿不到名称，需后端 VO 已返回 —— 已返回，只是前端没用） |
| FE-ERP-12 | `po/requisition` 状态筛选项硬编码小写码，与同项目其他单据大写风格相反，未与后端状态常量核对 | `views/erp/po/requisition/index.vue:25-29`、`:123-127` | **待复核**（见 §5 未覆盖）：若后端状态为 `DRAFT/SUBMITTED/...` 则筛选永远无结果，属 P0；否则仅为风格统一问题 |
| FE-ERP-13 | 固定资产编辑弹窗 `width="640px"`、`label-width="110px"`，`el-input-number` 均 `style="width:100%"` 尚可，但「状态」用 `el-radio-group` 只给 `IN_USE/CWIP` 两项，而已处置（`DISPOSED`）资产在列表仍显示「修改」按钮 | `views/erp/fin/fa/asset.vue:91`、`:132-135`、`:66` | 「修改」按钮加 `v-if="scope.row.status !== 'DISPOSED'"`；状态单选项与 `fa_asset_status` 字典对齐 |
| FE-ERP-14 | 详情页/明细表信息缺失：`wm/receive/detail.vue` 22 列无宽度且未见收货行关键状态列分组；`prd/prdwork/form.vue` 单页 30 列明细无分组无锚点 | `views/erp/wm/receive/detail.vue`、`views/erp/prd/prdwork/form.vue:451`（分配车间明细表） | 长表单按业务分组 + 折叠；明细表按"物料/数量/批次/状态"分组列 |
| FE-ERP-15 | 删除二次确认**不是**问题：`plus-ui/src/composables/useCrud.ts:217`（`handleDelete`）`:220`（`ElMessageBox.confirm`）统一提供确认框，各页 `handleDelete` 均来自该 composable | `plus-ui/src/composables/useCrud.ts:217,220` | 无需修改（列出以证明已核查，避免后续重复排查） |

## 4. 功能缺失清单

| # | 缺失功能 | 现状证据 | 已有资产（表/字段/枚举/半成品） | 补齐工作量估计 |
|---|---|---|---|---|
| 1 | 工单报工（`PrdWorkReport`）列表/详情/录入/审核界面 | `api/erp/prd/report/index.ts:4-10` 只有 2 个图表函数；`views/erp/prd/report/index.vue:1-26` 纯图表页；`PrdWorkReportVo` 29 字段在 `plus-ui/src` 0 命中 | 后端 6 个端点 + 菜单 2304 + 5 个按钮权限（`ry-vue.sql:92177,92193-92197`）+ 完整 BO/VO | 2~3 人日（列表+详情+表单+api 层+菜单拆分） |
| 2 | 盘点方式（明盘/盲盘）维护 | 前端 `countMode` 0 命中（`WmStockTakingPlanBo.java:88` 有） | 列 + BO/VO + PDA 侧已消费（`PdaStocktakingServiceImpl.java:79-91`） | 0.5 人日（+字典种子） |
| 3 | 采购运费录入与展示 | 前端 `freightAmount` 0 命中（`PuPurchaseOrderBo.java:86` 有） | 列 + BO/VO/实体三层齐备 | 0.5 人日 |
| 4 | 科目余额表年初余额展示 | 前端 `yearOpeningDebit/Credit` 0 命中（`GlAccountBalanceVo.java:54,57`） | 列 + VO + 实体 | 0.5 人日 |
| 5 | 条码规则当前流水号展示 | 前端 `currentSequence` 0 命中（`WmBarcodeConfigVo.java:87`） | 列 + BO/VO + 实体 | 0.2 人日 |
| 6 | 委外收货目标仓/收货人展示 | 前端 `targetWarehouseCode/receiverName` 0 命中（`WmSubcontractReceiveVo.java:54,70`） | 列 + BO/VO + 实体 | 0.3 人日 |
| 7 | 固定资产剩余折旧月数 | 前端 `remainingMonths` 0 命中（`FaAssetVo.java:37`） | 列 + VO | 0.2 人日 |
| 8 | 子表 Controller 的前端可达性定性 | `doc/audit/_raw-reachability2.txt:145-151,172-178,183-186`（到货通知行/收货行/库存流水） | 后端端点存在、前端 api 层无函数 | 需与 crosscut PERM-04 合并定性（下线或补页面） |

## 5. 已核查确认无问题

- **crosscut §1.2「A 类 25 个大小写不一致」在当前代码中已不复现（已修复/原判定过期）**：`WmArrivalNoticeController.java:46,60,76,91,107,123,140,154` 全部为小写 `wm:arrivalnotice:*`，与前端 `views/erp/wm/arrivalnotice/index.vue:57,60,63,68,71,76,105,111,120,124` 的 `v-hasPermi` 及 `router/index.ts:212` 的 `wm:arrivalnotice:query` **三方一致**；`WmPickingRuleController.java:43,56,70,82,95,108` 与 `WmPutawayRuleController.java:43,56,70,82,95,108` 均为 camelCase `wm:pickingRule:*`/`wm:putawayRule:*`，与前端 `views/erp/wm/pickingrule/index.vue:33,36,39,44,69,70`、`views/erp/wm/putawayrule/index.vue:33,36,39,44,69,70` 一致。**建议主代理据此更新 `2026-09-20-audit-crosscut.md` §1.2（否则会把已修项当 P0 重做）。**
- 到货通知/退料/上料交接的菜单与权限种子完整且与前端一致：`ry-vue.sql:92796`（`wm:materialReturn:list` + `component='erp/wm/return/index'`）、`:92797-92801`（query/add/edit/remove/export）、`:92802`（`wm:materialHandover:list` + `component='erp/wm/handover/index'`）、`:92803-92806`；前端对应 `views/erp/wm/return/index.vue:35,61`、`views/erp/wm/handover/index.vue:33,55`，`component` 路径与实际文件一致（`views/erp/wm/return/index.vue`、`views/erp/wm/handover/index.vue` 均存在）。
- `views/erp/wm/valuation/index.vue` 与 `form.vue` 均存在，菜单 2631（`ry-vue.sql:92318`，`component='erp/wm/valuation/index'`）可达；`api/erp/wm/valuation/` 目录下 `index.ts` 与 `types.ts` 齐备（机器扫描"有 types.ts 无 index.ts"结果为**空集**，即 api 层无整目录缺失）。
- 删除二次确认：`plus-ui/src/composables/useCrud.ts:217,220` 统一封装 `ElMessageBox.confirm`，40 个含 `handleDelete` 的页面均复用该实现 —— 机器初筛的"无 confirm"告警经复核**全部为误判**（confirm 在 composable 内）。
- `form.workshopList`（`views/erp/prd/prdwork/form.vue:140,143`、`api/erp/prd/work/types.ts:60,111`）**不是**前后端不一致：它是前端 UI 侧模型（车间多选），实际通过 `/erp/prd/workOrderResource/batch`（`PrdWorkOrderResourceController.java:32,46`）提交，后端 `PrdWorkOrderBo` 本就不该有该字段 —— 已核查排除。
- `form.payType`（`views/erp/fin/pay/mock.vue:26-28`）**不是**无效字段：后端 `PayMockController.java:72` 的内部类确有 `private String payType;`（`:107,110-111` 有 getter/setter）—— 已核查排除。
- 固定资产「折旧方法」列显示的 `methodLabel` 后端**确实返回**（`FaAssetVo.java:34`），该列不会空白 —— 已核查，真正缺的是 `categoryMethod`（见 FE-ERP-05）。
- `views/erp/wm/handover/index.vue:69`、`views/erp/wm/return/index.vue:75` 使用本地 `constants.ts` 标签映射而非 `dict-tag`：这是本项目的既有约定写法（与 `handover/constants.ts` 同目录），本报告未按"字典硬编码"计为缺陷；仅 FE-ERP-08 中"同一页字典与硬编码并存"的 `fin/fa/asset.vue` 计为缺陷。
- 搜索栏网格标准（`doc/erp/specs/2026-06-30-list-page-standard-design.md:19-97`）在 `views/erp/wm/handover/index.vue:3-29`、`views/erp/wm/return/index.vue:3-31`、`views/erp/fin/fa/asset.vue:4-32` 均符合（`el-row` + `:span="6"` + `label-width="100px"` + `el-select style="width:100%"`）—— 搜索栏布局未发现问题，本报告的布局问题集中在**表格列**。

## 6. 前后端字段双向不一致清单

| 字段 | 后端(BO/VO/表) | 前端(types/表单/列表) | 结论 |
|---|---|---|---|
| `evalDate` | **不存在**（`grep evalDate ruoyi-erp/src/main/java` → 0 命中；日期语义字段是 `evalPeriod`，`PuVendorEvaluationBo.java:42-43`） | `views/erp/pu/vendor-evaluation/form.vue:93-94,138,191`；`index.vue:95,97`；`api/erp/pu/vendor-evaluation/types.ts:17` | **前端有、后端没有 → 提交静默丢弃、列表列恒空**（FE-ERP-01，P0） |
| `evalPeriod` | `PuVendorEvaluationBo.java:43`（`@NotBlank`） | `form.vue:30-31,128,144,181`；`index.vue:11-12,64,129`；`types.ts:6,25,32` | 一致（注意与 `evalDate` 语义重复） |
| `freightAmount` | `PuPurchaseOrderBo.java:86`、`PuPurchaseOrderVo.java:87`、`PuPurchaseOrder.java:103` | **0 命中** | 后端有、前端没接（FE-ERP-03，P1） |
| `countMode` | `WmStockTakingPlanBo.java:88`、`WmStockTakingPlanVo.java:93`、`WmStockTakingPlan.java:85`、`PdaStocktakingCreateBo.java:25`、`PdaStocktakingDetailVo.java:20` | **0 命中**（PC 端） | 后端有、前端没接 → PC 只能建明盘（FE-ERP-04，P1） |
| `yearOpeningDebit` / `yearOpeningCredit` | `GlAccountBalanceVo.java:54,57`、`GlAccountBalance.java:55,58` | **0 命中** | 后端有、前端没接（FE-ERP-05，P1） |
| `currentSequence` | `WmBarcodeConfigVo.java:87`、`WmBarcodeConfigBo.java:77`、`WmBarcodeConfig.java:58` | **0 命中** | 后端有、前端没接（FE-ERP-05，P1） |
| `targetWarehouseCode` / `receiverName` | `WmSubcontractReceiveVo.java:54,70`、`WmSubcontractReceiveBo.java:51,67`、`WmSubcontractReceive.java:54,70` | **0 命中** | 后端有、前端没接（FE-ERP-05，P1） |
| `remainingMonths` | `FaAssetVo.java:37` | **0 命中** | 后端有、前端没接（FE-ERP-05/11，P1/P2） |
| `categoryMethod` | `FaAssetVo.java:31` | **0 命中**（前端用 `methodLabel`，`:34`） | 后端有、前端没接（FE-ERP-11，P2） |
| `disposalLossAccountName` | `FaAssetCategoryVo.java:33` | 只用 ID：`api/erp/fin/fa/index.ts:14`（`disposalLossAccountId`）、`views/erp/fin/fa/category.vue:84-85` | 后端返回了名称但前端未用（FE-ERP-11，P2） |
| `qualityCheckNo` | `PrdWorkOrderVo.java:287`、`PrdWorkOrder.java:298`、`PrdWorkOrderBo.java:253`、`PrdWorkReport.java:423`、`PrdWorkReportBo.java:360` | **0 命中** | 后端有、前端没接（FE-ERP-05，P1） |
| `PrdWorkReportVo` 29 字段（`reportNo/reportStatus/reportedQty/laborScrapQty/materialScrapQty/reportDate/laborHours/auditBy/...`） | `PrdWorkReportVo.java:362` 等 | **0 命中** | 整模块无 UI（FE-ERP-02，P0） |
| `oqcCode` / `oqcTime` | `SoSalesOrderLineVo` | **0 命中** | 后端有、前端没接（so 模块残留，P2；未在本报告单列编号） |
| `supplyType` | `BdBomDetail.java:109`（**BO/VO 无**）、`script/sql/ry-erp.sql:419` | `views/erp/prd/subcontract/detail.vue:58` 仅展示快照值 | **已在 `doc/audit/2026-09-20-audit-erp-bd.md:11` ERP-BD-01 报出，本报告不复述** |
| `workshopList` | 后端无（设计如此） | `api/erp/prd/work/types.ts:60,111`、`views/erp/prd/prdwork/form.vue:140,143,451,514` | **非缺陷**（前端 UI 侧模型，经 `workOrderResource/batch` 提交，已核查） |
| `payType` | `PayMockController.java:72,107,110-111`（内部类） | `views/erp/fin/pay/mock.vue:26-28` | 一致（已核查排除） |
| `methodLabel` | `FaAssetVo.java:34` | `views/erp/fin/fa/asset.vue:47`（`methodLabel(scope.row.methodLabel)`） | 一致（已核查，列不空） |
| 状态值 `draft/submitted/approved/rejected/closed`（小写） | **未核对**（`erp/po/requisition` 后端状态常量未读） | `views/erp/po/requisition/index.vue:25-29,123-127` | **待复核**：若后端为大写则筛选永远无结果（FE-ERP-12） |
| 状态映射双份：`fa_asset_status` 字典 vs 硬编码 `statusLabel/statusType` | 字典 `fa_asset_status` 已存在（前端 `useDict` 可加载） | `views/erp/fin/fa/asset.vue:21`（字典）与 `:203-209`（硬编码）并存 | 前端内部不一致（FE-ERP-08，P1） |

## 7. 建议修复批次划分

- **批次 A（P0，可独立提交）**:
  1. `FE-ERP-01`：删除供应商评估页 `evalDate`（form/index/types 三处），列表改绑 `evalPeriod`。**无 DDL**。
  2. `FE-ERP-02`：拆分「工单报工」菜单与图表页，补 `PrdWorkReport` 列表/详情 + api 层函数（或改菜单为「生产报表」并明确报工只在 MES/PDA）。**无 DDL，需登记菜单增量脚本**。
- **批次 B（P1，字段补齐，可一次提交）**: `FE-ERP-03`（运费）、`FE-ERP-04`（盘点方式，需补字典）、`FE-ERP-05`（8 组只读字段展示）、`FE-ERP-06`（金额重算收敛）、`FE-ERP-07`（图表 dispose/监听器/loading）、`FE-ERP-08`（状态统一走字典）。
- **批次 C（P2，布局与体验）**: `FE-ERP-09`（金额列右对齐 + 千分位）、`FE-ERP-10`（列宽）、`FE-ERP-11`（类别折旧方法/科目名称）、`FE-ERP-12`（`po/requisition` 状态码复核后修）、`FE-ERP-13`（已处置资产禁用修改）、`FE-ERP-14`（长表单/明细分组）。
- **需主代理协调**: ① 更新 crosscut §1.2（A 类已修复，勿重复施工）；② `FE-ERP-02` 的菜单归属需产品确认；③ `FE-ERP-06` 的后端金额/税额口径需后端子代理确认。

## 8. 未覆盖范围（诚实声明）

- **未读**: `plus-ui/src/views/cv/**`、`plus-ui/src/components/cv/**`、`plus-ui/src/views/index.vue` 的图表/轮询实现（仅读了前 120 行，未发现可判定问题）、`views/erp/so/**`（按任务要求避开已优化模块）、`views/erp/wm/warehouse/3d.vue` 与 `Wm3dConfigDrawer.vue` 的 3D 交互、`views/erp/sch/attendance/index.vue` 的人脸打卡流程。
- **未验证**: `views/erp/po/requisition/index.vue:25-29` 的小写状态值与后端常量是否一致（FE-ERP-12 标为待复核，**未作为确定结论**）。
- **未系统化**: 后端 `@SaCheckPermission` × 前端 `v-hasPermi` 的完整差集（本次只核了 `wm:arrivalnotice/pickingRule/putawayRule` 三组，结论见 §5）；`_raw-reachability2.txt` 的 353 条只抽样复核（`/erp/prd/report/*`、`/erp/wm/valuation`、`/erp/wm/storagearea|storagelocation/list-by-*`、`/erp/bd/*/byPrimary|costRollup|allChildrenIds`、`/erp/wm/sn/unbindStock`、`/erp/sch/attendance/*/face/mark-*` 等经 grep 证实**后端确实存在**，故其"无前端匹配"多为 api 路径拼接形式差异，不宜直接当缺失）。本次自建的机器 diff 因正则未覆盖无参注解（`@PostMapping` 不带括号）与模板字符串拼接，**假阳性偏高，未采信**，相关结论以逐条 `grep` 复核为准。
- **未做**: `npm run build`（按要求禁止）；未运行任何前端测试。
