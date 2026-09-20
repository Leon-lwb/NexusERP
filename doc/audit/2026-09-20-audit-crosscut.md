# 跨模块专项审计报告（权限种子 + MQ 通道 + 菜单可达性）

> 审计人: 主代理（机器扫描 + 人工判读） | 日期: 2026-09-20 | 分支: `feature/erp-mes-iot-audit-fix`
> 方法: 全仓提取 `@SaCheckPermission` 权限码（803 个）与 `script/sql/**` 全部种子（含 `ry-vue.sql` 39MB 的 `sys_menu`、各 `update/*.sql`）做差集；
> 全仓提取 `QueueConstants`（22 个常量）与 `@RabbitListener` 做生产者/消费者配对。
> 原始数据: `_raw-perm-check.txt`、`_raw-perm-advice.txt`、`_raw-mq-check.txt`（同目录）。

---

## 0. 概览

| 编号 | severity | 类型 | 一句话结论 | 证据 |
|---|---|---|---|---|
| PERM-01 | **P0** | 权限/功能不可用 | **85 个权限码在代码中被强制校验，却在 `script/sql/**` 全量种子里找不到**（其中 25 个仅大小写不一致）→ 非超管角色调用这些端点**一律 403**，对应功能对业务角色完全不可用 | `_raw-perm-advice.txt:1`（803 码 / 85 未播种）；例：`wm:receive:add` ← `WmMaterialReceiveController.java`，种子只有 `wm:receive:list`/`wm:receive:shelf`（`ry-vue.sql:92046`、`:92097`） |
| PERM-02 | **P0** | 权限/功能不可用 | 大小写不一致成对出现，**整个页面所有按钮全 403**：`wm:arrivalNotice:*` vs 种子 `wm:arrivalnotice:*`；`wm:pickingrule:*` vs 种子 `wm:pickingRule:*`；`wm:putawayrule:*` vs 种子 `wm:putawayRule:*` | `_raw-perm-advice.txt:66-72,86-97`；`ry-vue.sql:92045,92113-92117` |
| PERM-03 | P1 | 权限/功能不可用 | 单据**动作类端点**（提交/审批/取消/关闭/收货/执行/确认/完工/转单）在种子里被合并成 `:edit`，但代码要求独立动作码 → 采购订单/采购申请/采购退货/销售出库/物料入库/班组排班等**动作按钮全部 403** | `_raw-perm-advice.txt:47-59,98-104,111-112` |
| PERM-04 | P1 | 权限/契约 | `wm:arrivalNoticeLine:*`、`wm:detail:*`、`wm:receiveLine:*`、`wm:stocktx:*`、`wm:materialStock:list`、`prd:workOrder:list`、`pu:purchaseOrder:list` 等 **7 组共 23 个权限码完全无任何种子、也无同前缀近义** —— 需逐个判定"补种子"还是"改代码复用现有码" | `_raw-perm-advice.txt:73-85,105-118,45,52` |
| MQ-01 | P1 | 跨模块链路 | 5 个队列常量**全仓无 `@RabbitListener` 消费者**：`STOCK_OUT_QUEUE`、`VENDOR_EVALUATION_QUEUE`、`STOCK_ALERT_QUEUE`、`DELAY_QUEUE`、`EINVOICE_ISSUE_RESULT_QUEUE` → 需判定"发了没人收（链路断点）"还是"仅声明备用" | `_raw-mq-check.txt:4,8,10,22,28` |

---

## 1. PERM-01/02/03：权限码与菜单种子不一致（P0）

### 1.1 机制说明（为什么这是 P0）

本项目的鉴权链路是：`sys_menu.perms` → `sys_role_menu` 授权 → Sa-Token 权限集合。
**代码里的 `@SaCheckPermission("X")` 只有在某个已授权菜单的 `perms` 恰好等于 `X` 时才通过。**
因此"代码权限码"与"种子权限码"只要有一个字符不同，除 `userId=1` 超管（硬编码 `*:*:*`）外，
**任何角色都会 403**。这与 2026-09-14 已修的那次事故（`system:autocode:get/preview` 无种子 → 全站"自动生成编码"开关对普通角色失效）是同一类问题。

**危害等级判定**：`wm:receive:add/edit/execute/remove/export/query` 全部未播种 ⇒
「物料入库」页面对非超管角色**只能看列表，不能新增/修改/执行入库/删除/导出**；
`pu:order:submit/approve/cancel/close/receive` 全部未播种 ⇒ 采购订单**无法提交/审批/收货/关闭**。
这不是"按钮灰掉"，而是请求返回 403（前端会弹"没有权限"）。

### 1.2 A 类：纯大小写不一致（25 个，改代码即可，方向明确）

| 代码中的权限码（错误） | 已播种的正确码 | 涉及文件 | 建议 |
|---|---|---|---|
| `wm:arrivalNotice:{list,query,add,edit,remove,export}` | `wm:arrivalnotice:*` | `WmArrivalNoticeController.java`（7 处） | 改代码为 lowercase（种子 2104 + 按钮 2172-2176 已是 lowercase） |
| `wm:arrivalNotice:submit` | 无对应种子（同前缀仅 lowercase 6 个） | `WmArrivalNoticeController.java` | 改代码为 `wm:arrivalnotice:submit` 并**补按钮菜单** |
| `wm:pickingrule:{list,query,add,edit,remove,export}` | `wm:pickingRule:*` | `WmPickingRuleController.java`（6 处） | 改代码为 camelCase `pickingRule`（种子已存在且已授权给角色） |
| `wm:putawayrule:{list,query,add,edit,remove,export}` | `wm:putawayRule:*` | `WmPutawayRuleController.java`（6 处） | 改代码为 camelCase `putawayRule` |

> 判定依据：`ry-vue.sql` 中 `wm:arrivalnotice:*` 与 `wm:pickingRule:*` / `wm:putawayRule:*` 均已有完整菜单行，
> 且是角色授权所依赖的码；改代码成本最低、不会影响既有角色授权。
> **前端 `v-hasPermi` 用的是哪一侧需在修复时核对**（`_raw-perm-advice.txt` 显示前端对错误码零引用，
> 推测前端用的是种子侧；修复前必须 grep 前端确认，避免"后端改了、前端还是旧码"）。

### 1.3 B 类：动作码被种子合并为 `:edit`（动作端点 403）

| 域 | 代码要求的动作码 | 种子里实际给的 | 影响 |
|---|---|---|---|
| 采购订单 | `pu:order:{submit,approve,cancel,close,receive}` | 只有 `pu:order:list/query/add/edit/remove/export`（按钮 2516-2518 的 perms 全是 `pu:order:edit`） | 提交/审批/取消/关闭/收货 五个动作**全部 403**（`ry-vue.sql:92259-92261`） |
| 采购申请 | `pu:requisition:{submit,approve,convert,close,cancel}` | 按钮 2507-2509 的 perms 全是 `pu:requisition:edit` | 同上（`ry-vue.sql:92250-92252`） |
| 采购退货 | `pu:return:{confirm,execute}` | 无（仅 list/query/add/edit/remove/export） | 退货**确认/执行入库 403** ⇒ 采购退货闭环在 UI 上不可用 |
| 采购合同 | `pu:contract:export` | 无（仅 list/query/add/edit/remove/submit） | 合同导出 403 |
| 物料入库 | `wm:receive:{add,edit,execute,export,query,remove,submit}` | 仅 `wm:receive:list` + `wm:receive:shelf` | 见 §1.1，**入库单据功能整体不可用** |
| 销售出库 | `wm:salesIssue:{confirm,complete}` | 无（仅 list/query/add/edit/remove/export） | 出库**确认/完成 403** |
| 财务-收款单 | `fin:arap:receipt:{edit,remove,export}` | 无（仅 list/query/add/confirm/cancel） | 收款单改/删/导出 403 |
| 财务-汇率 | `fin:gl:currency:{add,edit,remove}` | 无（仅 `fin:gl:currency:list`） | 汇率**新增/修改/删除 403** ⇒ 外币业务无法维护汇率 |
| 财务-凭证 | `fin:gl:voucher:export` | 无 | 凭证导出 403 |
| 排产-班组 | `sch:planteam:{list,edit,export}` | 仅 `sch:planteam:{query,add,remove}` | **班组列表页 403**（连列表权限都没有，页面直接不可达） |
| 排产-班组排班 | `sch:teamSchedule:{list,edit,export}` | 仅 `sch:teamSchedule:{query,add,remove}` | 同上 |
| MES-设备 | `mes:eq:patrol:{route:*,task:*,record:*}`, `mes:eq:spare:log:*`, `mes:eq:maintenance:record:*` | 仅 `mes:eq:patrol:task:execute` 等零星 | 点检路线/点检任务/点检记录/备件流水/保养记录 多动作 403 |
| MES-生产 | `mes:pro:step-check-rule:*`, `mes:pro:step-guide:*`, `mes:pro:trace:query` | 无 | 工序检查规则/工序指导书 整页 403；**追溯查询 403** |
| MES-NCR | `mes:ncr:8d:query`, `mes:ncr:defect:list` | 仅 `mes:ncr:8d:list/edit/complete`、`mes:ncr:defect:add` | 8D 详情/不良代码列表 403 |
| ERP 报表 | `prd:workOrder:list`、`pu:purchaseOrder:list`、`wm:materialStock:list` | 无（**无任何同前缀近义**） | 三个报表接口 403（与 09-06 T1.6 修的 `so` 报表同类问题，但当时只修了 so） |

### 1.4 C 类：完全无种子且无近义（需逐个定性）

`wm:arrivalNoticeLine:{add,edit,export,list,query,remove}`、`wm:detail:{add,edit,export,list,query,remove}`、
`wm:receiveLine:{add,edit,export,list,query,remove}`、`wm:stocktx:{add,edit,export,list,query,remove}`、
`wm:materialStock:list`、`prd:workOrder:list`、`pu:purchaseOrder:list`。

这些多为**子表 Controller**（到货通知行/收货明细/收货行/库存流水）——需判定：
① 这些子表 Controller 是否应整体下线（前端只走主子表聚合接口）→ 删控制器或去掉权限注解；
② 还是确实有独立页面需要补菜单。
**注意**：`wm:detail:*` 与 `wm:receiveLine:*` 前端有 1~2 处引用（`_raw-perm-advice.txt:79-84,105-110` 显示 `wm:detail:*` 除 list 外前端引用=1），说明前端确实在用 → 倾向"补种子"。

---

## 2. MQ-01：5 个队列无消费者（P1，待逐条定性）

| 常量 | 队列名 | 生产者 | 现状 | 需判定 |
|---|---|---|---|---|
| `STOCK_OUT_QUEUE` | `erp.stock.out.queue` | `RabbitMqInitializer` 仅声明 | 无 `@RabbitListener` | 出库事件是否本该触发倒冲/成本结转？若是则**链路断点** |
| `VENDOR_EVALUATION_QUEUE` | `erp.vendor.evaluation.queue` | 同上 | 无消费者 | 供应商评估是否只落库不联动？ |
| `STOCK_ALERT_QUEUE` | `erp.stock.alert.queue` | 同上 | 无消费者 | 库存预警通知链路是否断？ |
| `DELAY_QUEUE` | `erp.delay.queue` | 同上 | 无消费者 | 延迟队列（死信兜底）未接消费者 |
| `EINVOICE_ISSUE_RESULT_QUEUE` | `erp.einvoice.issue.result.queue` | 同上 | 无消费者 | 电子发票开具结果**回调无消费** ⇒ 开票结果不回写业务 |

> **重要限定**：本扫描按"文件内是否出现 `@RabbitListener`"分类，因此**生产者统计里出现的都是 `RabbitMqInitializer`**
> （它声明了队列/交换机绑定），并不代表真的有业务代码在发消息。
> 修复前必须对每个常量做 `grep` 全仓确认"是否真的有人 `convertAndSend`/`send`"，
> 若连生产者都没有，则是"死通道"（声明了但从未使用），危害等级降为 P2（文档/清理问题）。
> 另需反向检查：是否存在**直接用字符串字面量**（不走 `QueueConstants`）的 `@RabbitListener(queues = "erp.xxx")`，
> 造成配对漏检。

---

## 3. 修复建议（批次划分）

- **批次 P0-A（权限种子对齐，建议第一个做）**：① A 类 25 个改代码为种子侧大小写（先 grep 前端 `v-hasPermi` 确认方向）；
  ② B/C 类补 `script/sql/update/2026-09-20-*.sql`（`INSERT IGNORE` 按钮菜单 + `sys_role_menu` 授权给现有角色），
  ③ 同时在 `script/sql/ry-vue.sql` 主脚本同步补种子（全新环境免执行增量脚本），
  ④ 登记 `doc/system/数据库增量变更记录.md`。
  **验收**：写一个"代码权限码 ⊆ 种子权限码"的可复跑校验（本报告 §1 的扫描脚本），差集为空或全部有书面豁免。
- **批次 P1-B（MQ 通道定性）**：对 5 个通道逐条 `grep` 定性（真生产者/仅声明），
  真断点的补消费者或删生产者；仅声明的清理 `QueueConstants` 与 `RabbitMqInitializer`。
