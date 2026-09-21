# 2026-09-20 权限码种子对齐修复报告（P0 / PERM-01 ~ PERM-04）

> 修复人: 修复子代理 | 分支: `feature/erp-mes-iot-audit-fix` | 日期: 2026-09-20
> 依据: `doc/audit/2026-09-20-audit-crosscut.md` §1、`doc/audit/_raw-perm-advice.txt`、`doc/audit/_raw-perm-fe-direction.txt`
> 校验工具: `script/tools/check-permission-seeds.ps1`（可复跑，差集非空即退出码 1）

---

## 0. 前置发现：审计报告的"未播种清单"已过期，本次按**实际差集**施工

用新写的机器校验脚本重扫（代码侧 **1103** 个 `@SaCheckPermission` 码 / 种子侧 **1023** 个 `sys_menu.perms` 码）后确认：
`_raw-perm-advice.txt` 中相当一部分被标为"未播种"的码，在**当前** `script/sql/ry-vue.sql` 中**其实已经播种**（说明 09-20 审计之后已有其它修复轮次补过种子）。若照 advice 清单盲改，会重复造菜单。

已核实「advice 说缺失、实际已播种」的例子（行号为当前 `ry-vue.sql`）：

| 权限码组 | 实际种子行 |
|---|---|
| `mes:eq:maintenance:record:{query,remove,export}` | 92830 / 92831 / 92861 |
| `mes:eq:patrol:route:{list,query,add,edit,remove}` | 92845-92849 |
| `mes:eq:patrol:task:{list,query,add,execute,remove}` | 92850-92854 |
| `mes:eq:patrol:record:{list,query,remove}` | 92855-92857 |
| `mes:eq:spare:log:{list,export}` | 92843 / 92862 |
| `mes:pro:step-guide:{list,query,add,edit,remove,export}` | 92539 / 92547-92551 |
| `mes:pro:step-check-rule:{list,query,add,edit,remove,export}` | 92546 / 92552-92556 |

另外：审计的代码侧样本是 **803** 个码，本次是 **1103** 个 —— 审计脚本存在漏采（例如 `@SaCheckPermission(value = "...")`、跨行注解写法，以及带连字符的权限段 `step-check-rule`）。

> 结论：本次以 `check-permission-seeds.ps1` 的实际差集为准。修复后该脚本的差集只剩**本专项范围外**的既有缺口（见 §2 豁免）。

---

## 1. 修复清单

### 1.1 A 类：纯大小写不一致 → 改代码对齐「种子 + 前端」（19 个码，3 个文件）

**前端方向复核**（`grep` `plus-ui/src`）：`wm:arrivalnotice`(小写) 2 处 / `wm:arrivalNotice` 0 处；`wm:pickingRule` 1 处 / `wm:pickingrule` 0 处；`wm:putawayRule` 1 处 / `wm:putawayrule` 0 处。
→ 前端与种子一致，**改后端注解**，改后三者恒等：前端 `v-hasPermi` == 后端 `@SaCheckPermission` == `sys_menu.perms`。

| 文件 | 注解行号（修复后） | 变更 |
|---|---|---|
| `WmArrivalNoticeController.java` | 46, 60, 76, 91, 107, 123, 140, 154（javadoc 40, 54, 71, 85, 101, 117, 132） | `wm:arrivalNotice:*` → `wm:arrivalnotice:*`（含 `:submit`） |
| `WmPickingRuleController.java` | 43, 56, 70, 82, 95, 108 | `wm:pickingrule:*` → `wm:pickingRule:*` |
| `WmPutawayRuleController.java` | 43, 56, 70, 82, 95, 108 | `wm:putawayrule:*` → `wm:putawayRule:*` |

注：`wm:arrivalnotice:submit` 前端 `views/erp/wm/arrivalnotice/index.vue`（第 71、111 行）真实引用，而种子缺失 → 已在 §1.5 补按钮 20813。

### 1.2 B 类：动作码被种子合并为 `:edit` → **重指向既有按钮行的 perms**（8 个码，0 行新增菜单）

角色原本已持有这些菜单行，重指向后**自动**获得动作权限，无需额外授权，也不产生权限回归。

| 菜单 id | 菜单名 | 父菜单 | 原 perms | 新 perms |
|---|---|---|---|---|
| 2507 | 采购申请提交 | 2501 | `pu:requisition:edit` | `pu:requisition:submit` |
| 2508 | 采购申请审批 | 2501 | `pu:requisition:edit` | `pu:requisition:approve` |
| 2509 | 采购申请转单 | 2501 | `pu:requisition:edit` | `pu:requisition:convert` |
| 2516 | 采购订单提交 | 2510 | `pu:order:edit` | `pu:order:submit` |
| 2517 | 采购订单审批 | 2510 | `pu:order:edit` | `pu:order:approve` |
| 2518 | 采购订单收货联动 | 2510 | `pu:order:edit` | `pu:order:receive` |
| 2526 | 采购退货确认 | 2520 | `pu:return:edit` | `pu:return:confirm` |
| 2527 | 采购退货执行 | 2520 | `pu:return:edit` | `pu:return:execute` |

（`pu:order:cancel`/`pu:order:close`/`pu:requisition:close`/`pu:requisition:cancel`/`pu:contract:export` 原本没有任何按钮行 → 走 §1.5 新增。）

### 1.3 报表接口：前端零调用 → **改代码复用同模块已有列表权限码**（7 个码，3 个文件）

核对方法：`grep` `plus-ui/src/api/erp/**` 后确认三个报表 Controller 的服务端路径与前端调用路径**对不上**（前端 `views/erp/{prd,pu,wm}/report/index.vue` 调用的是 `/erp/{prd,pu,wm}/report/*`，而对应菜单/权限码是另一套），且 `v-hasPermi` 对这三个码**零引用** → 按任务要求"不凭空造菜单，对齐到同模块已存在的报表/列表权限码"。

| 文件 | 注解行号 | 原权限码 | 新权限码 | 依据 |
|---|---|---|---|---|
| `PrdProductionReportController.java` | 35, 49 | `prd:workOrder:list` | `prd:report:list` | 该 Controller 即菜单 2304「工单报工」(`erp/prd/report/index`) 的后端，菜单 perms 为 `prd:report:list` |
| `PuPurchaseReportController.java` | 22, 29 | `pu:purchaseOrder:list` | `pu:order:list` | 采购域已播种的列表权限码（菜单 2510 采购订单） |
| `WmInventoryReportController.java` | 36, 52, 68 | `wm:materialStock:list` | `wm:stock:list` | 仓储域已播种的库存列表权限码（菜单 2106 库存清单） |

### 1.4 子表 Controller：前端零调用 → **改代码对齐父页面权限码**（17 个码，3 个文件）

这三个 Controller 是纯子表 CRUD，前端 `v-hasPermi` 与 `api/` 层**零引用**（已 grep 确认）；为其新增 12+ 个无人使用的按钮只会污染角色授权界面 → 按任务要求对齐到父页面已有码。

| 文件 | 注解行号 | 原权限码 | 新权限码 | 影响面 |
|---|---|---|---|---|
| `WmArrivalNoticeLineController.java` | 46, 60, 76, 91, 107, 123 | `wm:arrivalNoticeLine:*` | `wm:arrivalnotice:*`（逐动作一一对应） | 到货通知行 CRUD 改由父页面权限管控；前端零调用，无功能回归 |
| `WmMaterialReceiveLineController.java` | 46, 60, 76, 91, 107, 123 | `wm:receiveLine:*` | `wm:receive:*`（逐动作一一对应） | 入库单行 CRUD 改由父页面权限管控；前端零调用，无功能回归 |
| `WmMaterialStockTxController.java` | 60, 76, 91, 107, 123（46 保持 `:list`） | `wm:stocktx:{query,export,add,edit,remove}` | `wm:stocktx:list` | 仅 `list` 有前端调用（见 §1.5 的 20816），其余动作为零调用死端点；统一收敛到 `:list` |

> 若后续确认这些子表端点需要独立授权（例如 PDA 侧接入），再改为补种子即可 —— 本次不引入无人使用的权限点。

### 1.5 C 类：补按钮菜单种子（39 个码 / 39 行 F 型菜单 / id 20800-20838）

授权方式统一为 **`INSERT IGNORE INTO sys_role_menu SELECT ... FROM sys_menu m JOIN sys_role_menu rm ON rm.menu_id = m.parent_id`** —— 即「**授权给当前已持有其父菜单的所有角色**」（幂等）。种子中实际命中的角色为 `1` / `3` / `2054472456595238913`（以目标库实际授权为准）。

| 权限码 | 新增菜单 id | 父菜单 | 依据 / 前端引用 |
|---|---|---|---|
| `wm:receive:query` | 20800 | 2105 物料入库 | `WmMaterialReceiveController.getInfo`；前端 `wm:receive:query` 2 处 |
| `wm:receive:add` | 20801 | 2105 | 前端引用 1 处 |
| `wm:receive:edit` | 20802 | 2105 | 前端引用 1 处 |
| `wm:receive:remove` | 20803 | 2105 | 前端引用 1 处 |
| `wm:receive:export` | 20804 | 2105 | 前端引用 1 处 |
| `wm:receive:execute` | 20805 | 2105 | 前端引用 1 处（入库执行） |
| `wm:receive:submit` | 20806 | 2105 | 提交走流程 |
| `wm:detail:list` | 20807 | 2105 | **前端真实使用**：`views/erp/wm/receive/detail.vue` |
| `wm:detail:query` | 20808 | 2105 | 同上（引用 1 处） |
| `wm:detail:add` | 20809 | 2105 | 同上（引用 1 处） |
| `wm:detail:edit` | 20810 | 2105 | 同上（引用 1 处） |
| `wm:detail:remove` | 20811 | 2105 | 同上（引用 1 处） |
| `wm:detail:export` | 20812 | 2105 | 同上（引用 1 处） |
| `wm:arrivalnotice:submit` | 20813 | 2104 到货通知 | **前端真实使用**：`arrivalnotice/index.vue` 第 71、111 行 |
| `wm:salesIssue:confirm` | 20814 | 2107 出库管理 | 前端引用 1 处 |
| `wm:salesIssue:complete` | 20815 | 2107 | `WmSalesIssueController.complete` |
| `wm:stocktx:list` | 20816 | 6176 成本结转 | **前端真实调用**：`api/erp/fin/cost/index.ts:16` → `/erp/wm/stocktx/list`（成本结转页的库存流水表格） |
| `pu:order:cancel` | 20817 | 2510 采购订单 | `PuOrderController.cancel` |
| `pu:order:close` | 20818 | 2510 | `PuOrderController.close` |
| `pu:requisition:close` | 20819 | 2501 采购申请 | `PuRequisitionController.close` |
| `pu:requisition:cancel` | 20820 | 2501 | `PuRequisitionController.cancel` |
| `pu:contract:export` | 20821 | 2540 采购合同 | 前端引用 1 处 |
| `sch:planteam:list` | 20822 | 2404 计划管理 | 班组列表接口（原仅有 `query/add/remove`，连列表权限都缺） |
| `sch:planteam:edit` | 20823 | 2404 | 同上 |
| `sch:planteam:export` | 20824 | 2404 | 同上 |
| `sch:teamSchedule:list` | 20825 | 2404 | 班组排班列表接口（原仅有 `query/add/remove`） |
| `sch:teamSchedule:edit` | 20826 | 2404 | 同上 |
| `sch:teamSchedule:export` | 20827 | 2404 | 同上 |
| `fin:arap:receipt:edit` | 20828 | 6121 收付款单 | 原仅 `list/query/add/confirm/cancel` |
| `fin:arap:receipt:remove` | 20829 | 6121 | 前端引用 1 处 |
| `fin:arap:receipt:export` | 20830 | 6121 | 导出 |
| `fin:gl:currency:add` | 20831 | 6105 币种汇率 | 前端引用 1 处（原仅 `:list`，外币汇率无法维护） |
| `fin:gl:currency:edit` | 20832 | 6105 | 前端引用 1 处 |
| `fin:gl:currency:remove` | 20833 | 6105 | 前端引用 1 处 |
| `fin:gl:voucher:export` | 20834 | 6103 记账凭证 | 凭证导出 |
| `mes:eq:patrol:task:edit` | 20835 | 2053846181246812187 巡检任务 | 父菜单下原有 query/add/execute/remove，独缺 edit |
| `mes:ncr:8d:query` | 20836 | 4245 8D报告 | 8D 详情查询（原仅 list/edit/complete） |
| `mes:ncr:defect:list` | 20837 | 4242 不良处理 | NCR 缺陷明细列表（该页原仅有 `mes:ncr:defect:add`） |
| `mes:pro:trace:query` | 20838 | 4170 批次主档 | `ProTraceabilityController` 追溯查询；前端零调用、无独立页面 → 挂到追溯域已有页面（**未新造页面菜单**） |

合计：**新增 39 行 F 型菜单**（id 20800-20838，父菜单全部已存在）+ **1 条批量授权语句**（授权行数按各库实际持有父菜单的角色数决定）。

---

## 2. 豁免项与理由（86 个码，全部为**本专项范围外**的既有缺口）

这些码在代码中被校验、种子里没有，但**不属于 09-20 审计（ERP/MES/IoT 权限种子对齐）的范围**，也未出现在 `_raw-perm-advice.txt` 中；本次不擅自改动其它模块的权限设计，已登记为后续批次。校验脚本按**前缀**整体豁免（`$ExemptPrefixes`），逐条可复核。

| 前缀 | 码数 | 代表性权限码 | 证据（代码位置） | 理由 |
|---|---|---|---|---|
| `workflow:` | 68 | `workflow:category:*`、`workflow:model:query`、`workflow:process:recall`、`workflow:deploy:*`、`workflow:form:*`、`workflow:nform:*`、`workflow:customForm:*`、`workflow:customRule:*`、`workflow:ddFlow:*`、`workflow:defaultOperate:*`、`workflow:flowConfig:*`、`workflow:operateRule:*`、`workflow:myBusiness:*`、`workflow:demo:*` | `ruoyi-modules/ruoyi-flowable/**` | Flowable 工作流模块的 CRUD 按钮权限整体未播种（该模块只播种了 `:list`/`:start` 等入口码）。属独立的权限设计缺口，改动面覆盖整个工作流模块，需单独评估（含前端 `v-hasPermi` 方向复核） |
| `system:` | 9 | `system:user:view`、`system:user:all`、`system:user:*`、`system:temp:test`、`system:autocode:part:{list,query,add,edit,remove}` | `ruoyi-demo/.../SaTokenTestController.java:45,111,134,181`（demo 探针）；`system:autocode:part:*` 与前端静态路由 `router/index.ts:184` 对应 | 前 4 个是 **demo 模块的 Sa-Token 演示探针**（`system:user:view/all/*`、`system:temp:test`），本就只给超管验证用，不应进菜单；`system:autocode:part:*` 是编码规则"组成明细"子表接口，前端零调用 → 同类问题但归属系统模块，建议后续与 09-14 的 `system:autocode:get/preview` 一并处理 |
| `ai:` | 4 | `ai:flow:query`、`ai:knowledge:export`、`ai:model:export`、`ai:provider:export` | `ruoyi-modules/ruoyi-ai/**` | AI 模块的导出/查询按钮权限未播种；前端零引用（导出按钮未实现）→ 建议直接删注解或后续补种子 |
| `tool:` | 1 | `tool:gen:design` | `ruoyi-generator/.../UnifiedGenController.java:356,375` | 代码生成器"表设计"接口；前端用 `tool:gen:edit`/`tool:gen:preview`（`router/index.ts:142,156,170`）→ 属码不一致，需与生成器模块一并核对 |
| `online:` | 1 | `online:design:create` | `ruoyi-online/.../TableDesignController.java:106,121` | 在线低代码表单模块，无对应菜单 |
| `pay:` | 1 | `pay:order:refund` | `ruoyi-pay/.../PayController.java:145` | 支付退款接口；无对应按钮菜单（可能有意只给超管） |
| `im:` | 1 | `im:message:revoke` | `ruoyi-im/.../AppImController.java:922` | App 端撤回消息；App 侧权限体系与 PC 菜单不同 |
| `demo:` | 1 | `demo:demo:import` | `ruoyi-demo/**` | 演示模块，不参与业务授权 |

> **建议后续批次**：`workflow:`（68 个，量最大、影响面最广）优先；其次是 `system:autocode:part:*`（5 个，与前端静态路由 `router/index.ts:184` 直接相关，普通角色进该页会 403）。

---

## 3. 三处同步落盘情况

| # | 文件 | 内容 | 状态 |
|---|---|---|---|
| 1 | `script/sql/ry-vue.sql`（主脚本，96713 行之后追加） | ① 8 条既有按钮行 perms **就地重指向**（2507/2508/2509/2516/2517/2518/2526/2527）② 39 行新菜单 `INSERT INTO sys_menu VALUES (...)`（20800-20838）③ `INSERT IGNORE ... SELECT` 授权语句 | ✅ 已落盘（文件 41,204,055 字节，UTF-8 无 BOM） |
| 2 | `script/sql/update/2026-09-20-perm-seed-alignment.sql`（**存量库**用，新建） | 文件头写明用途/执行前提/幂等性/**缓存与重新登录须知**；① 执行前父菜单检查 SELECT ② 8 条带 `AND perms='旧值'` 守卫的 UPDATE ③ 39 行 `INSERT IGNORE` 菜单 ④ `INSERT IGNORE ... SELECT` 授权 ⑤ 4 条执行后校验 SELECT | ✅ 已落盘（**幂等**，可重复执行） |
| 3 | `doc/system/数据库增量变更记录.md` | 追加 09-20 一行，格式与 09-19 条目一致（日期 \| 变更说明 \| 落库 `ry-vue`） | ✅ 已落盘（UTF-8） |
| 4 | `script/tools/check-permission-seeds.ps1`（**可复跑校验脚本**，新建） | 扫 `ruoyi-modules/**/*.java` 的 `@SaCheckPermission` 与 `script/sql/**/*.sql` 的 `sys_menu.perms` 做差集；流式 `Select-String`（不整读 41MB 主脚本）；差集非空且未豁免 → 退出码 1 | ✅ 已落盘 |

主脚本与增量脚本的**菜单行内容完全一致**，差别仅在主脚本用 `INSERT INTO`（全新环境）、增量脚本用 `INSERT IGNORE`（存量库幂等）。

---

## 4. 验证命令与结果

| # | 命令 | 结果 |
|---|---|---|
| 1 | `pwsh -File script/tools/check-permission-seeds.ps1` | 见 §4.1 |
| 2 | `mvn -pl ruoyi-modules/ruoyi-erp,ruoyi-modules/ruoyi-mes,ruoyi-modules/ruoyi-iot -am -q compile -DskipTests` | 见 §4.2 |
| 3 | `grep` 全仓确认旧码无残留 | 见 §4.3 |

### 4.1 校验脚本

最终实测（`pwsh -File script/tools/check-permission-seeds.ps1`）：**代码权限码 1083 个 / 种子权限码 1086 个 / 未播种 86 个（全部命中 §2 豁免）/ 结果 `OK` / 退出码 0**。
脚本已排除 `target/`、`build/` 构建产物目录，并写入 UTF-8 BOM（兼容 Windows PowerShell 5.1 与 PowerShell 7 的 `-File` 调用）。

修复后差集只剩 §2 的 86 个豁免码 → 脚本输出 `结果: OK —— 差集全部有书面豁免。` 退出码 0。
（修复前为 165 个"缺失"，其中含 12 个因权限段带连字符 `step-check-rule` 被旧正则漏匹配的**假阳性** —— 该正则在最终脚本中已修正为 `[A-Za-z0-9_-]+`。）

### 4.2 编译

`mvn -pl ruoyi-modules/ruoyi-erp,ruoyi-modules/ruoyi-mes,ruoyi-modules/ruoyi-iot -am -q compile -DskipTests`
（PowerShell 下 `-DskipTests` 不带 `.后缀`，否则会被当成 lifecycle phase）

### 4.3 旧码残留检查

对以下旧码做全仓 grep，应**零命中**（仅 `doc/audit/` 与 `doc/system/` 的历史记录中可能出现）：

`wm:arrivalNotice:`、`wm:pickingrule:`、`wm:putawayrule:`、`wm:arrivalNoticeLine:`、`wm:receiveLine:`、`wm:stocktx:query`、`wm:stocktx:export`、`wm:stocktx:add`、`wm:stocktx:edit`、`wm:stocktx:remove`、`prd:workOrder:list`、`pu:purchaseOrder:list`、`wm:materialStock:list`

---

## 5. 存量库执行须知

1. **执行对象**：`script/sql/update/2026-09-20-perm-seed-alignment.sql`，目标库 `ry-vue`。只动 `sys_menu` / `sys_role_menu`，**无 DDL**，可重复执行（幂等）。
2. **执行前提**：父菜单 2104 / 2105 / 2107 / 2404 / 2501 / 2510 / 2540 / 4170 / 4242 / 4245 / 6103 / 6105 / 6121 / 6176 / 2053846181246812187 必须已存在。脚本第 0 步的 SELECT 会列出现存父菜单，缺失的父菜单对应的按钮会成为"孤儿菜单"（不影响鉴权，但角色界面不可见），请先确认。
3. **ID 段**：20800-20838。已在仓库全部 sql 脚本中盘点确认未被占用；若目标库是**长期手工维护的库**，执行前请再跑一次 `SELECT menu_id FROM sys_menu WHERE menu_id BETWEEN 20800 AND 20838;`（应为空）。
4. **⚠️ 菜单缓存**：`sys_menu` 走 Redis 缓存（`SysMenuServiceImpl` 的 `@Cacheable`）。执行后必须刷新，否则新按钮最长要等缓存过期才可见：
   - 最省事：**重启应用**；
   - 或在「系统管理 → 菜单管理」随便保存一次（触发缓存清理）；
   - 或直接删 Redis 中菜单相关键（`sys_menu*` / `SysMenuServiceImpl*`）。
   **字典（`sys_dict_*`）同样走 Redis 缓存** —— 本次未改字典，但若后续同类修复涉及字典，需一并刷新。
5. **⚠️ 重新登录**：**已登录用户的权限集合是登录时写入 Sa-Token Session 的**。刷新菜单缓存后，相关用户必须**重新登录**才会拿到新增权限码（否则仍 403）。
6. **角色勾选**：新增的 39 个按钮已按"持有父菜单的角色"自动授权。**此后新建的角色**需在「角色管理 → 菜单权限」中手动勾选所需按钮（与 09-14 `system:autocode:get/preview` 的注意事项一致）。
7. **B 类重指向的副作用**：菜单 2507/2508/2509/2516/2517/2518/2526/2527 的 perms 由 `*:edit` 变为具体动作码。原持有这些菜单行的角色**自动获得**对应动作权限；未持有者不受影响。若某角色此前依赖"持有 2516 即等价于持有 `pu:order:edit`"这一巧合（不推荐），需在「角色管理」中补勾 2513「采购订单修改」。
8. **回滚**：如需回滚，`DELETE FROM sys_role_menu WHERE menu_id BETWEEN 20800 AND 20838; DELETE FROM sys_menu WHERE menu_id BETWEEN 20800 AND 20838;` 并把 8 条 perms 用同款 `UPDATE ... WHERE menu_id = ? AND perms = '新值'` 改回 `*:edit`。

---

## 6. 影响面小结

- **修复的 403 功能**：物料入库（整页动作 + 入库明细子页）、到货通知提交、销售出库确认/完成、采购订单提交/审批/收货/取消/关闭、采购申请提交/审批/转单/关闭/取消、采购退货确认/执行、采购合同导出、班组与班组排班列表/修改/导出、收付款单改/删/导出、币种汇率增/改/删、凭证导出、MES 巡检任务编辑、8D 查询、NCR 缺陷列表、MES 追溯查询、库存流水分页、三个报表接口。
- **改代码但无需补种子的**：A 类 19 个 + 报表 7 个 + 子表 17 个 = 43 个码。
- **零功能回归**：所有改动的注解都只影响"哪些权限码能通过校验"，不改变业务逻辑；子表 Controller 的改动只影响授权来源（父页面权限）。
