# 销售模块（so）优化计划

> 创建: 2026-09-19 | 范围: 后端 `ruoyi-modules/ruoyi-erp/.../so`（审计时 85 文件 / 9541 行；本次改造后 87 文件 / 10716 行 —— 新增 `SoInvoiceLineVo`/`SoContractLineVo`）+ 14 张 `so_` 表 + 前端 `plus-ui` 销售页面（15 vue / 16 api ts）
> 📄 **收尾交接报告见 [2026-09-19-so-module-handover.md](./2026-09-19-so-module-handover.md)**（交付清单 / 存量库脚本 / 遗留风险 / 复审建议）
>
> 注：本文件早期版本把规模写成「85 文件 / 8395 行」，其中行数是错的（审计子任务的统计口径有误，
> 用 `git show <commit>:<file>` 逐文件计数可复现正确值 9541）。已在 2026-09-19 复核修正。
> 方法: 5 路并行只读审计 + 主链路人工复核；结论均带 `文件:行号`
> 已排除: [09-06 全项目审查报告](../system/2026-09-06-全项目审查报告.md) 已修复项（E4 SO 条件更新 / E5 退货 CAS / E8 预留消耗 / C4 单号分布式化 / N6 发票核销权限），不重复列出
> 图例: ✅ 已人工复核确认 | ⚠️ 需运行时实测 | 🔴 阻断 | 🟠 高 | 🟡 中 | 🟢 低

---

## 一、结论摘要

销售模块**成熟度极不均衡**，不是"整体待优化"，而是三条互不相通的断层：

| 层次 | 现状 |
|------|------|
| **做得好的** | 发票（乐观锁 + 原子累加 + 红冲 + 防超开）、销售合同（服务端重算金额 + 状态 CAS + 白名单拷贝）、`contract/detail.vue`（竞态令牌、权限显式提示）、`ClientSelectDialog`/`CustomerOrderSelectDialog` |
| **基本不可用的** | ① 价目表 + 定价引擎（引擎生产代码零调用、前端 materialId 恒空、无菜单无权限码、字典不存在）② 销售报表（SQL 列名错误 100% 500、权限码不存在、无菜单）③ 退货闭环（与订单零数量关联）④ 订单改单（重建行 ID，击穿全部下游关联） |
| **静默失效的** | 后端入参校验（BO 零注解，`@Validated` 空转）、`SoLineProducedEvent`（空发布）、`SoCreditUsageVo`（死 VO）、`convertQuotationToOrder`（前端零调用）、13 个死字段、`creditStatus='PASSED'`（字典不匹配） |

**数量分布**：🔴 6 项 / 🟠 12 项 / 🟡 15 项 / 🟢 12 项。

**最该先修的一件事**：`SoSalesOrderHeaderServiceImpl.updateByBo()` 的"改单即全删行重插"。它把 `so_sales_order_line` 的 `line_id` 全部换新，而生产计划、出库回写、发票行都按 `line_id` 关联 —— 这是唯一会**静默损坏已有数据**的缺陷，其余多数是"功能缺失"或"报错可见"。

---

## 二、现状盘点

### 2.1 表结构（14 张，定义于 `script/sql/ry-erp.sql`）

| 表 | 注释(行) | 实体 | 备注 |
|---|---|---|---|
| `so_sales_order_header` | 销售订单主表(5557) | SoSalesOrderHeader | `uk_so_no(so_no)` 不含 tenant_id |
| `so_sales_order_line` | 销售订单行表(5620) | SoSalesOrderLine | `@TableLogic` 逻辑删除 |
| `so_customer_order` | 客户订单表(5171) | SoCustomerOrder | `uk_co_no(co_no)` 不含 tenant_id |
| `so_quotation` / `_line` | 报价单(5399/5440) | SoQuotation(Line) | 单号无 UNIQUE |
| `so_price_list` / `_line` | 价格表(5305/5335) | SoPriceList(Line) | **无 del_flag**，物理删除 |
| `so_contract` / `_line` | 合同(5068/5107) | SoContract(Line) | 行执行字段全死 |
| `so_return` / `_line` | 退货(5477/5519) | SoReturn(Line) | 单号无 UNIQUE |
| `so_invoice` / `_line` | 发票(5210/5266) | SoInvoice(Line) | `version`+`@Version` 正常 |
| `so_credit_usage` | 信用占用(5147) | SoCreditUsage | **无 del_flag**，无列注释 |

**14 张表全部无外键约束**（`ry-erp.sql` 全文 `FOREIGN KEY` 零命中）。**无索引的引用列**：`so_invoice_line.so_line_id`(5282)、`so_invoice_line.contract_line_id`(5283)、`so_return_line.so_line_id`(5522)。

### 2.2 业务线连通性矩阵（关键结论）

| 链路 | 状态 | 证据 |
|---|---|---|
| 报价 → 销售订单 | ✅ **活** | `SoQuotationServiceImpl.java:145-210` new SO 落库，回写 `order.attr1=报价单号`；但前端 `convertQuotationToOrder`（`api/erp/so/quotation/index.ts:21`）**全前端零调用** → 后端活、UI 断 |
| 价目表 → 订单/报价 | 🔴 **断** | `IPricingEngineService.resolvePrice` 生产代码零调用，仅 `PricingEngineServiceImplTest` 引用 |
| 合同 → 订单 | 🔴 **断** | 全仓无 SO↔合同引用；仅 `so_invoice.contract_id` 裸 ID |
| 退货 → 订单 | 🔴 **断** | `SoReturnLine.soLineId` 全仓无读取，`so_sales_order_line` 无 `returned_qty` 列 |
| 订单 → 出库 → 已发货量 | ✅ **活** | `SalesOutStrategy.java:188` / `WmSalesIssueServiceImpl.java:355` → `addShippedQty` |
| 订单 → 生产 → 已投产 | ✅ **活** | `PrdWorkOrderServiceImpl.java:538` → `addProducedQty` |
| 订单 → 发票 → 已开票量 | ✅ **活（单路径）** | `SalesOrderFulfilledEventListener`（AFTER_COMMIT+`@Async`+`TenantHelper.dynamic`，实现规范）→ `generateDraftFromOrder` |

### 2.3 测试覆盖

ERP 模块 15 个测试类，`so` 仅 3 个：`PricingEngineServiceImplTest`、`SoContractServiceImplTest`、`SoContractSignStatusFillerTest`。
**讽刺点**：唯一有测试的定价引擎是死代码；而订单头/行、发票、退货、报价这些核心单据服务**零测试**。

---

## 三、问题清单

> **行号时效性提示（2026-09-19 复核补充）**：本章是**审计当时（commit `cd882df9`）的状态记录**，
> 其中的 `文件:行号` 引用对应那时的代码。本次改造后行号已大幅变动，且经抽查复核发现
> **部分行号本身就不准确**：抽查 12 处，8 处准确、4 处有偏差 —— `SoSalesOrderHeaderServiceImpl.java`
> 的 `:246-252` 实为 `:256-262`、`:625` 实为 `:635`、`:696` 实为 `:706`，
> `insertByBo:228-239` 实为 `:238`（已就地修正；`confirm:298` 等经核验准确，未改）。
> **请以方法名与描述为准**；需要看原状用 `git show cd882df9:<file>`。
> 各问题的**修复状态**见 §九 执行进度。

### 🔴 P0 阻断级

#### P0-1 ✅ 改单重建行 ID，击穿全部下游关联
`SoSalesOrderHeaderServiceImpl.java:256-262`（`updateByBo`；审计原文误写为 `:246-252`）：
```java
soSalesOrderLineMapper.delete(... eq(so_id, ...));   // 逻辑删除（@TableLogic，SoSalesOrderLine.java:118）
insertOrUpdateLines(entity.getSoId(), ...);          // 且强制 line.setLineId(null) → 全新 ID（:635）
```
`updateByBo` **无任何状态门禁**，前端 `orderheader/index.vue:120` 的修改按钮对所有状态可见。

后果链（均为按 `line_id` 关联）：
- `prd_production_plan.order_line_id`（`SoSalesOrderHeaderServiceImpl.java:706` 写入；审计原文误写为 `:696`）→ 关联计划/工单在详情页消失，`closeLinkedProductionPlans()` 失效
- 出库回写 `addShippedQty(soLineId)`（`WmSalesIssueServiceImpl.java:355`）打到已逻辑删除的行 → **新行 `delivered_qty` 永远为 0，订单永远无法履行**
- `so_invoice_line.so_line_id` → 已删除行
- 表单快照回写还会把并发的 `delivered_qty` 覆盖成旧值（丢失更新）

**修复**：立即加状态门禁（仅 DRAFT 可改）；中期改差量更新（按 lineId 增/改/删，参考 `SoInvoiceServiceImpl.diffUpdateLines:306-332` 已实现范式）。

#### P0-2 ✅ 销售报表 100% 不可用
- `SoSalesReportServiceImpl.java:37,38` 用 `so.status`、`:57` 用 `so.order_id` —— `so_sales_order_header` 只有 `order_status`(5583)/`so_id`(5558) → 两个接口必抛 `SQLSyntaxErrorException`
- 来源：照抄 pu 报表（`pu_purchase_order` 确有 `order_id`/`status`），改名时漏改列名
- `SoSalesReportController.java:22,29` 用 `so:salesOrder:list`，`ry-vue.sql` 中**不存在**该权限码（仅 `so:orderheader:*`）→ 非超管 403；报表页也无菜单
- 多租户：`JdbcTemplate` 原生 SQL 绕过 MyBatis-Plus 租户插件，无 `tenant_id` 条件 → 跨租户聚合

#### P0-3 ✅ 退货与订单零闭环 + 状态门禁缺失
- `SoReturnServiceImpl.java:154-186` `execute()` 只拦 `RECEIVED` → DRAFT/APPROVING/CLOSED 均可入库；不回写订单任何数量，无超退校验
- `deleteByIds(:125-130)` / `updateByBo(:113-118)` 无状态校验 → **已入库（RECEIVED）的退货单可删除或改量**，库存已加回却无痕迹
- 对照：采购侧 `PuPurchaseReturnServiceImpl.java:216-221` 有完整实现

#### P0-4 ✅ 手工开票路径绕过防超开
- 防超开门禁（`.apply("invoiced_qty + {0} <= delivered_qty", ...)` + 原子累加）**只存在于** `generateDraftFromOrder:235-242`
- `insertByBo:113-135` 与 `updateByBo/diffUpdateLines:306-332` 手工建票/改票**既不累加也不校验** → 重复开票完全绕过
- `rollbackInvoicedQty:531-543` 无条件减（被 `:347`、`:402` 调用）→ 手工票未累加却被减，`invoiced_qty` 变负 → 反向放大可开票量
- 另：`redFlush:430-432` 只校验原票 `redFlag`，原票状态不变 → **同一蓝票可无限红冲**；`verifyInvoice:373-374` 核销额可超价税合计

#### P0-5 ✅ **经实测为误判**（原判"提交审批在事务内跨库会失败"不成立）
> **结论（2026-09-19 实测复核 + 用户确认"可用"）**：提交审批**功能正常**，不是缺陷。原判断只看了
> `@DS("erp")` + `@Transactional` 的表象，**漏看了被调用方的事务传播行为**。

`SoSalesOrderHeaderServiceImpl.java:66(@DS("erp")) + :526(@Transactional)`，方法体先 `baseMapper.selectById()` 把连接钉在 erp，随后 `:551` 调 `WfProcessServiceImpl`（`WfProcessServiceImpl.java:105` `@DS("master")`）。

**为什么实际可用**：`WfProcessServiceImpl.startProcessByDataId` 标了
`@Transactional(propagation = Propagation.REQUIRES_NEW)`（`WfProcessServiceImpl.java:1947`）。
`REQUIRES_NEW` 会**挂起外层事务并重新获取连接**，此时 `@DS("master")` 才真正生效 ——
拿到 master 连接去查 `ry-vue`，而 `wf_*` 表（13 张）正在 `ry-vue`（`ry-erp.sql` 中为 0 张）。
仓库规范里那句「任何 `@DS` 都切不过去（连接已绑定）」描述的是**同一事务内**的情形，
`REQUIRES_NEW` 恰好是它的例外。

**原子性复核（也不构成问题）**：4 个 `submit()`（订单/报价/合同/发票/退货）在调用
`startProcessByDataId` **之后都没有任何 erp 写操作** —— 只读校验 + 返回结果，
状态流转全部由流程回调 `afterFlowHandle`（`REQUIRES_NEW`）完成。
即外层 erp 事务里本就没有需要回滚的写入，跨库非原子性无实际暴露面。

**保留的注意事项（供后续维护）**：若今后在 `submit()` 中于工作流调用**之后**追加 erp 写操作，
就会真正引入跨库非原子风险（流程实例已提交、业务写入回滚）；届时应把流程启动移到事务外，
或用 `@DSTransactional`。

#### P0-6 ✅ 价目表整体不可用（5 重故障叠加）
1. 定价引擎生产代码零调用（见 2.2）
2. `PricingEngineServiceImpl.java:172` 要求 `max_qty >= qty`，而 `so_price_list_line.max_qty` 默认 NULL(5342) → 未填上限的行**永不命中**
3. `:173` 用 `selectOne`，但 `so_price_list_line` 只有普通索引 `idx_list_material`(5350)，无 `(price_list_id,material_id,min_qty)` UNIQUE → 多行即 `TooManyResultsException`
4. 前端 `price-list/form.vue:93-110` 明细行只填文本物料，`materialId` 恒空 → 而引擎按 `materialId` 匹配
5. `price-list/index.vue:20` 用不存在的字典 `so_price_list_status`；页面无菜单、无 `so:priceList:*` 权限码

---

### 🟠 P1 高

| # | 问题 | 证据 |
|---|------|------|
| P1-1 | **后端入参校验空转**：两个 BO 无任何 Jakarta Validation 注解，而 Controller 有 `@Validated` | `SoSalesOrderHeaderBo.java` 全文、`SoSalesOrderLineBo.java` 全文、`SoSalesOrderHeaderController.java:37,138,154` |
| P1-2 | **客户端信任金额**：`totalAmount/taxAmount/discountAmount/discountRate` 全量取自请求体，后端从不重算；而 `confirm()` 用 `totalAmount` 做信用检查 → 传 0 即可绕过额度 | `insertByBo:238`、`updateByBo:256`、`confirm:298`（原文档误写为 `insertByBo:228-239`、`updateByBo:246-252`） |
| P1-3 | `creditStatus` 写 `"PASSED"`，字典 `so_credit_status` 只有 `NOT_CHECKED/CHECKED_OK/CHECKED_BLOCKED` → dict-tag 显示原始码 | `SoSalesOrderHeaderServiceImpl.java:300,310` |
| P1-4 | 订单删除无状态门禁且不释放副作用：`deleteByIds:259-263` 删行+删头，**不释放信用额度、不释放库存预留、不关闭生产计划**（`close`/`cancel` 都做了） | `SoSalesOrderHeaderServiceImpl.java:256-263` |
| P1-5 | 报价行可跨租户写入：`SoQuotationBo:87` 直接复用实体 `List<SoQuotationLine>`，服务只清 `lineId/quotationId`，未清 `tenantId/delFlag/createBy` | `SoQuotationServiceImpl.java:220-223`（合同侧已用白名单拷贝规避：`SoContractServiceImpl.java:319-323,347-374`） |
| P1-6 | 报价状态机可绕过：`insertByBo/updateByBo` 直接落客户端 `status`，不强制 DRAFT；`convertToSalesOrder` 只拦 CONVERTED → DRAFT 可跳过 confirm 直接转换 | `SoQuotationServiceImpl.java:90,105,150` |
| P1-7 | 报价转单无幂等：先查后插，无锁、无 `@RepeatSubmit`、无唯一约束 → 双击/并发重复生成订单 | `SoQuotationServiceImpl.java:145-210`、`SoQuotationController.java:147-153` |
| P1-8 | 合同行执行字段全死：`deliveredQty/invoicedQty/lineStatus` 全仓无读写，每次编辑被重置 | `SoContractLine.java:102,107,112`、`SoContractServiceImpl.java:347-374` |
| P1-9 | `uk_so_no(5608)` / `uk_co_no(5199)` 不含 `tenant_id`，而同模块 `uk_contract_no(5098)`/`uk_invoice_no(5253)` 含 → **跨租户互相阻塞**；且软删除后单号仍被占用 | DDL |
| P1-10 | 退货 `return_no`、报价 `quotation_no` NOT NULL 但**无 UNIQUE**，且服务端仅空值时生成 → 重复单号可入库 | `ry-erp.sql:5479,5401`；`SoReturnServiceImpl.java:100-102` |
| P1-11 | 税率/折扣率口径分叉且未标单位：DB 注释无单位；`orderheader/form.vue` 的 `calcLine` 按「净额×税率÷100」，而 `contract/index.vue:512`、`invoice/index.vue:524` 按「含税额×税率」；历史数据混用 `3` 与 `0.03` | `plus-ui/src/views/erp/so/orderheader/form.vue`(calcLine)、`sys_oper_log` 实测数据、DDL 注释 |
| P1-12 | 死字段：原列 13 个（`project_id`/`is_pa_enabled`/`pa_group_id`/…），**2026-09-19 复核修正为仅 5 个真死** | 见附录 A |

### 🟡 P2 中（摘要）

- **空发布事件**：`SoLineProducedEvent` 唯一发布点 `SoFulfillmentWritebackServiceImpl.java:133`，全仓无监听者
- **死 VO / 死接口**：`SoCreditUsageVo`（`clientName`/`soCode` 无填充）、`SoQuotationLineVo` 无返回路径、`convertQuotationToOrder` 前端零调用
- **孤儿行**：`SoQuotationServiceImpl.java:106-108`、`SoPriceListServiceImpl.java:116-119` 删行→插行→`updateById`，头不存在时返回 0 不抛异常，事务照提交
- **发票红冲/核销**：红冲可重复、核销无上限（见 P0-4）
- **前端状态门禁分叉**：`return/index.vue:41,44`、`invoice/index.vue:45,48`、`quotation/index.vue:47,50` 工具栏只按 `single/multiple` 禁用；`orderheader/index.vue:248-250` `canConfirmRow` 仅 APPROVED 而后端允许 DRAFT/APPROVED → 草稿单行按钮不可确认
- **选中项残留**：`orderheader/index.vue:189-196,224-227` 搜索/重置不清 `ids`/`selectedRow`
- **幻影字段**：`customerorder/index.vue:69,226` 用 `deptId/deptName`，但 `SoCustomerOrderVo`/`Bo`/实体/表**均无此字段** → 列表恒空、表单选择被静默丢弃
- **列表页不可达过滤条件**：`buildQueryWrapper:752` 的 `projectId` 过滤、`:735` 的 `deptId` 过滤，前端列表页从不传 → 死分支
- **循环单条 SQL**：`SoReturnServiceImpl.java:367-371`、`SoInvoiceServiceImpl.java:129-132,230-243,316-325,340-351,492-502`、`SoQuotationServiceImpl.java:220-224`、`SoContractServiceImpl.java:107-110,138-141,155-171`；N+1：`SoSalesOrderHeaderServiceImpl.java:653,683`
- **性能**：`CreditCheckServiceImpl.getUsedAmount:149-162` 全量载入内存求和（应 `SUM()`）；`release:104-108`/`convertToUsed:130-134` 逐行 update（应批量）
- **信用额度 TOCTOU**：`checkAndFreeze:54-85` 先查已用再插入，幂等守卫只覆盖同一 `soId`；`so_credit_usage` 无 `(tenant_id,so_id,status)` 唯一约束 → 并发确认两张单可双占额度
- **实体直出**：`SoQuotationVo:78`、`SoContractVo:142`、`SoInvoiceVo` 明细用实体，泄漏 `tenantId/delFlag/createBy`；`SoQuotationVo:77`/`SoReturnVo:4,72` 的 `@JSONField` 是 fastjson2 注解，项目 HTTP 走 Jackson → **无效**
- **异常口径**：报价/退货服务抛 `RuntimeException`（→500），合同/发票抛 `ServiceException` → 不一致
- **权限粒度**：`so:return:execute`、`so:quotation:confirm`、`so:quotation:convert` 复用 `edit`，无独立权限点

### 🟢 P3 低（摘要）

- **索引缺失**：`so_return.return_no/so_no/client_id`、`so_quotation.quotation_no`、`so_customer_order.priority/customer_po_no/source_channel`、`so_contract.contract_type`、`so_price_list.client_id/status/list_type`、`so_credit_usage.(so_id,status)/(client_id,status)`、`so_invoice_line.so_line_id`、`so_return_line.so_line_id`
- **`so_invoice` 14 处乱码注释**（GBK 误读为 UTF-8，如 `:5214 '鍙戠エ浠ｇ爜'`）
- **精度不一致**：金额 header `(18,2)` vs 报价/退货 `(18,4)`（`SoQuotationServiceImpl.java:171-174` 逐字段赋值静默丢小数）；`unit_price` `(18,6)`/`(14,4)`/`(18,4)` 三种；`tax_rate` `(18,6)`/`(10,4)`/`(5,2)` 三种；`discount_rate` 三种
- **3 张表缺 `del_flag`**：`so_credit_usage`/`so_price_list`/`so_price_list_line`（走物理删除，违反项目 `@TableLogic` 规范）
- **`sort_order` 类型不统一**：`so_sales_order_line` 是 `bigint`，其余表 `int`
- **`tenant_id` 允许 NULL**（14 表均 `NULL DEFAULT '000000'`）→ 可能漏租户过滤
- **死常量**：`ISoQuotationService:24 CLOSED`、`ISoContractService:28,30 EXPIRED/TERMINATED` 无引用；`ISoReturnService:27 CLOSED` 定义未使用
- **死配置**：`orderheader/index.vue:216` 传入未绑定的 `ref<FormInstance>()`；`SoPriceListBo:54`/`SoContractBo:152`/`SoInvoiceBo:156` 的 `ids` 无读取
- **报表页前端**：`report/index.vue:47-76` echarts 重复 init、resize 监听泄漏、无 dispose、未消费 `completionRate`
- **注释与语义不符**：`so_customer_order.client_code` 注释"预留字段1"实为已用；`so_customer_order.total_amount` 注释"未税"而 `so_sales_order_header.total_amount` 是"含税"；`so_invoice.status` 注释缺 `APPROVING/REJECTED`
- **`SoSalesOrderLineServiceImpl.updateOqcStatus:104-113`** 按 `materialId` 更新，同订单同物料多行会被一起改写

---

## 四、优化方案（6 批次）

> 原则：**先止血、再补功能、后清理**。每批次独立可交付、独立 commit、独立验收。

### 批次 0：建立回归基线（0.5d，先做）
- 为订单头/行、发票、退货补最小单测（现有 3 个测试类覆盖不到核心链路）
- 价值：后续 P0 修复没有测试网，改一处不知道碰坏哪
- 验收：`mvn -pl ruoyi-modules/ruoyi-erp -am test -DskipTests=false` 全绿

### 批次 1：数据正确性（P0，3-4d）
| 任务 | 改动 |
|---|---|
| T1.1 改单差量更新 | `SoSalesOrderHeaderServiceImpl.updateByBo` 改为按 `lineId` 差量更新（设计规格见 **§八**）；加状态门禁；前端 `orderheader/index.vue` 修改/删除按钮按状态禁用 |
| T1.2 删除止血 | `deleteByIds` 加状态门禁 + 补 `creditCheckService.release()` + `stockReservationService.unreserve()` + 关闭关联生产计划 |
| T1.3 退货门禁 | `SoReturnServiceImpl.execute` 要求 CONFIRMED；`deleteByIds`/`updateByBo` 限 DRAFT |
| T1.4 开票收口 | 抽 `occupyInvoicedQty()`/`releaseInvoicedQty()` 统一入口，`insertByBo`/`updateByBo` 一并走门禁；`rollbackInvoicedQty` 加下界守卫 |
| T1.5 红冲/核销 | 红冲后置原票标记或加唯一约束；核销额加 `<= totalAmount` 上限 |
| T1.6 报表修复 | `SoSalesReportServiceImpl` 列名改 `so_id`/`order_status`；补 `tenant_id` 条件与 CANCELLED 排除；权限码改 `so:orderheader:list` |
| T1.7 跨库事务 ⚠️ | 先实测 `POST /erp/so/orderheader/{id}/submit` 确认；确认后 4 类单据统一改 `REQUIRES_NEW` 入口 |
| T1.8 金额重算 | 后端在 `insertByBo/updateByBo` 服务端重算行金额与表头合计（复用现有口径），不再信任客户端 |

**验收**：改单后 `prd_production_plan.order_line_id` 仍可解析；退货单入库后无法删除；手工重复开票被拒；报表接口 200 且不跨租户。

### 批次 2：契约与校验（P1，2d）
- BO 补 Jakarta Validation（Header：`clientId/orderDate/currency/totalAmount/exchangeRate/orderStatus`；Line：`soId/materialId/orderedQty/unitPrice/lineStatus`）
- `orderStatus` 白名单校验，禁止客户端直写终态
- `creditStatus` 改 `CHECKED_OK`，与字典对齐
- 报价行改白名单拷贝（对齐合同侧），堵跨租户写入
- 报价状态机：`insertByBo` 强制 DRAFT，`convertToSalesOrder` 加 `@RepeatSubmit` + 仅 CONFIRMED 可转
- 金额/税率/折扣率单位注释与前端口径统一（折扣率已定 D2=折扣百分比；税率口径待 Q3 决策）

### 批次 3：功能补齐（P1-P2，4-6d，按决策取舍）
| 任务 | 说明 |
|---|---|
| T3.1 价目表接线 | 修 `maxQty` NULL 兜底 + `selectOne`→有序 `selectList` + 前端物料选择器 + 补菜单/权限码/字典；或在 `resolvePrice` 接入订单行取价 |
| T3.2 报价转单接线 | 前端 `quotation/index.vue` 接 `convertQuotationToOrder` |
| T3.3 退货闭环 | 加 `returned_qty` 列 + 行级累计校验 + 退货红冲（需 DDL） |
| T3.4 销售报表 | 补菜单 + 前端补 `completionRate` 消费 + echarts 生命周期修复 |
| T3.5 死事件/死 VO | `SoLineProducedEvent` 接出库单自动创建，或删除；`SoCreditUsageVo` 补填充或删除 |

### 批次 4：DDL 收敛（2d，需登记 `doc/system/数据库增量变更记录.md`）
见 §五。

### 批次 5：清理与一致性（1-2d）
- 清理 13 个死字段（实体/BO/VO/前端同步）
- 报表/价目表/退货等页面的工具栏状态门禁对齐行内门禁
- 清除列表页选中项残留、幻影字段 `deptId/deptName`
- 统一异常口径为 `ServiceException`
- 补 `@ExcelIgnoreUnannotated`、修 `@JSONField` 无效注解、明细改 VO 直出

---

## 五、DDL 变更清单

> 所有项需写入 `script/sql/ry-erp.sql` 并在 `doc/system/数据库增量变更记录.md` 登记；存量库需附 `ALTER` 语句。

**批次 4 必做**
1. `so_price_list_line` 加 `UNIQUE(price_list_id, material_id, min_qty)`（配合 T3.1）
2. `so_sales_order_header.uk_so_no` → `(so_no, tenant_id)`
3. `so_customer_order.uk_co_no` → `(co_no, tenant_id)`
4. `so_quotation.quotation_no` 加 UNIQUE；`so_return.return_no` 加 UNIQUE
5. `so_invoice` 14 处乱码 `COMMENT` 重建
6. 补索引：`so_return(return_no/so_no/client_id)`、`so_quotation(quotation_no)`、`so_customer_order(priority/customer_po_no/source_channel)`、`so_contract(contract_type)`、`so_price_list(client_id/status/list_type)`、`so_credit_usage(so_id,status)/(client_id,status)`、`so_invoice_line(so_line_id)`、`so_return_line(so_line_id)`
7. `tax_rate`/`discount_rate`/`discount_percent` 注释补「百分数（5 表示 5%）」
8. 退货闭环（T3.3）：`so_sales_order_line` 加 `returned_qty decimal(18,4) DEFAULT 0`
9. **折扣率语义修正（D2）**：`so_price_list_line.discount_rate` `DEFAULT 100.00` → `DEFAULT 0.00`；存量库 `UPDATE so_price_list_line SET discount_rate = 0 WHERE discount_rate = 100;`（执行前须确认无真实让利 100% 的行）

**可选（风险较高，单独评估）**
10. 3 张表补 `del_flag` + 实体加 `@TableLogic`（注意 `so_credit_usage` 是流水，可能有意物理删除）
11. 金额精度统一 `(18,4)`（需数据迁移）
12. `tenant_id` 改 `NOT NULL`（影响 14 表，需全量评估）
13. `sort_order` 统一为 `int`

---

## 六、验收与回归

- **后端**：`mvn -pl ruoyi-modules/ruoyi-erp -am compile -DskipTests` 编译通过；批次 0 后 `test -DskipTests=false` 全绿
- **前端**：`npx eslint --fix`（prettier 门禁）+ `npm run build:prod`
- **端到端必测**（每批次后）：
  1. 建单 → 确认 → 出库回写 `delivered_qty` → 履行 → 自动生成发票草稿
  2. 改单后再走出库/履行，验证 `line_id` 关联不断
  3. 退货入库后尝试删除/改量，应被拒
  4. 手工建发票 → 重复建票，应被拒
  5. 报表接口返回 200 且只含本租户数据
  6. `POST /erp/so/orderheader/{id}/submit` 确认跨库事务实际行为
- **多租户专项**：用带 `tenantId` 的请求实测报价行写入、报表聚合、唯一索引冲突

---

## 七、决策记录

### 7.1 已决策（2026-09-19）

> **编号约定**：本节用 `D*` 编号已决策项；`Q*` 编号待决策项（见 §7.2）。
> 用户在推进过程中可能用 `Q*` 指代已决策项 —— 例如**本节的 D4（改单状态门禁仅 DRAFT）
> 即用户所述的 Q6**；`D2`（折扣率语义）、`D3`（差量更新）与用户清单中的同名编号一致。
> 源码注释统一引用 `D*` 编号。

#### D1 价目表 → **修**（不删）
按 T3.1 全量接线：引擎公式修正 + `maxQty` NULL 兜底 + `selectOne`→有序 `selectList` + 前端物料选择器（`MaterialSelectDialog`）+ 补菜单/`so:priceList:*` 权限码/`so_price_list_status` 字典 + `resolvePrice` 接入订单行取价。

#### D2 折扣率语义 → **折扣百分比**（`5` 表示让利 5%，实付 95%）

**重要连锁影响**——订单侧已经是这个语义，价目表侧是反的，必须一起改：

| 位置 | 现状 | 按 D2 应改为 |
|---|---|---|
| `orderheader/form.vue` `calcLine`/`calcHeader` | `金额 × rate / 100` | ✅ **已符合**，无需改 |
| `PricingEngineServiceImpl.java:177-179` | `unitPrice × rate / 100` → `rate=5` 算出 `price×0.05`（打 0.5 折，等于让利 95%）**方向完全相反** | `unitPrice × (1 - rate/100)` |
| `so_price_list_line.discount_rate` DDL(5343) | `DEFAULT 100.00` → 在 D2 语义下 **100 = 免费** | `DEFAULT 0.00` |
| `so_price_list_line` 种子数据(5357-5360) | 4 行全为 `100.00` → 迁移后全部变免费 | 迁移为 `0.00` |
| `price-list/form.vue:129` 标签 | `折扣率(%)` `min=0 max=100` | ✅ 区间正确，补"让利比例，0=不让利"提示 |
| `SoQuotationServiceImpl.java:174` | 报价 `discountRate` 原样复制到订单，不参与计算 | 无需改，但需核对报价存量数据是否符合 D2 |

**存量库迁移**：`UPDATE so_price_list_line SET discount_rate = 0 WHERE discount_rate = 100;`（需先确认无真实"让利 100%"的行）

#### D3 改单策略 → **直接做差量更新，保留行 ID**

设计规格见 §八。核心是"按 `lineId` 增/改/删 + 履约字段白名单 + 已履约行禁删"，不能简单套用 `SoInvoiceServiceImpl.diffUpdateLines`（发票行无履约数量，订单行需要更强守卫）。

#### D4 改单状态门禁 → **仅 DRAFT 可改**
`updateByBo` 仅允许 `orderStatus = DRAFT` 的单据修改；`APPROVED/CONFIRMED/FULFILLED/CLOSED/CANCELLED` 一律拒绝。注意：当前系统无"状态回退"入口，已确认单若需改需先走取消/关闭再建新单。

### 7.2 待决策

| # | 问题 | 影响批次 |
|---|---|---|
| Q1 | **销售报表是否保留？** 保留需修列名+权限+菜单+多租户（约 1d）；不需要则删除（当前 100% 报错且无入口） | 批次 3 / T3.4 |
| Q2 | **退货闭环是否做完整？** 需 DDL（`returned_qty`）+ 红冲联动，属独立需求（约 2d）。<br>**补充发现（T1.3 时）**：后端已有 `PUT /erp/so/return/confirm/{id}` 与 `/execute/{id}`，但前端 `api/erp/so/return/index.ts` **只有 list/get/add/update/del/submitFlow/proc 六个函数，没有 confirm/execute** → 退货入库功能在 UI 上完全不可达，后端能力闲置 | 批次 3 / T3.3 |
| Q3 | **税率口径**：统一为百分数（`13`）还是小数（`0.13`）？历史数据两种并存（`sys_oper_log` 实测 `"taxRate":"0.03"` 与 `"3"`） | 批次 2 |
| Q4 | **13 个死字段**：删除（清理实体/BO/VO/DDL）还是保留待用？其中 `project_id`/`is_pa_enabled`/`pa_group_id` 属从未实现的"项目配套发料" | 批次 5 |
| Q5 | **跨库事务**（P0-5）是否已有人实测？若线上"提交审批"一直可用，说明判断有误需重新定位 | 批次 1 / T1.7 |

---

## 八、T1.1 差量更新设计规格（D3）

### 8.1 目标
`SoSalesOrderHeaderServiceImpl.updateByBo()` 从「逻辑删除全部行 + 重新插入新行」改为「按 `lineId` 差量更新」，**保证 `so_sales_order_line.line_id` 在改单前后不变**，从而不破坏：
- `prd_production_plan.order_line_id`（生产计划/工单关联）
- 出库回写 `addShippedQty(soLineId)`（`WmSalesIssueServiceImpl.java:355`、`SalesOutStrategy.java:188`）
- `so_invoice_line.so_line_id`
- `wm` 出库单的 `source_doc_line_id`

### 8.2 分派规则
| 请求行状态 | 动作 |
|---|---|
| 有 `lineId` 且 DB 存在且属于本单 | **UPDATE**（仅白名单字段） |
| 无 `lineId` | **INSERT**（`soId` 由服务端设置，忽略客户端传入） |
| DB 有、请求未包含 | **DELETE（逻辑）**——但须先过 8.3 守卫，否则拒绝整单提交 |

### 8.3 字段白名单与守卫（关键，防"修好一个 bug 引入另一个"）

**可更新字段**（客户端可改）：
`materialId / materialCode / materialName / materialSpec / orderedQty / unit / unitPrice / lineAmount / taxRate / taxAmount / discountPercent / discountAmount / requestedDate / promiseDate / warehouseId / deliveryPriority / customerLineRef / customerProductCode / customerProductName / sortOrder / lineNo / remark`

**永不覆盖字段**（服务端权威，请求里传了也忽略）：
`deliveredQty / invoicedQty / fulfilledQty / productionQty / purchaseQty / reservedQty / lineStatus / oqcStatus / oqcId / oqcCode / oqcTime / linkedMoId / linkedPoId / reservedBatchNo / reservedSerials / paGroupId`

> 这一条同时修掉"表单快照覆盖并发已发货量"的丢失更新（当前 `updateByBo` 全量覆盖）。

**删除守卫**：任一行满足以下任一条件即**禁止删除**，抛 `ServiceException` 并提示行号：
- `deliveredQty > 0` / `invoicedQty > 0` / `productionQty > 0` / `reservedQty > 0`
- 存在 `prd_production_plan.order_line_id = lineId` 的计划
- 存在 `so_invoice_line.so_line_id = lineId` 的发票行

**关键字段变更守卫**：已履约行（`deliveredQty > 0` 或 `productionQty > 0`）禁止改 `materialId` / `orderedQty`（改小会与已发数量矛盾），只允许改交期/备注等非履约字段。

### 8.4 状态门禁
按 **D4** 决策：`updateByBo` 仅允许 `orderStatus = DRAFT` 的单据修改，其余状态（`APPROVED`/`CONFIRMED`/`FULFILLED`/`CLOSED`/`CANCELLED`）一律抛 `ServiceException`。前端 `orderheader/index.vue` 的修改/删除按钮同步按状态禁用。

### 8.5 参考实现与差异
`SoInvoiceServiceImpl.diffUpdateLines:306-332` 提供了「按 lineId 增/改/删」的骨架，可直接借鉴其分派逻辑；差异在于订单行必须额外实现 8.3 的白名单与守卫（发票行无履约数量，不需要）。

### 8.6 验收
1. 建单（3 行）→ 记录 3 个 `line_id` → 改单（改第 1 行数量、删第 3 行、加第 4 行）→ 断言：第 1、2 行 `line_id` **不变**，第 3 行 `del_flag='1'`，第 4 行为新 ID
2. 确认订单 → 生成生产计划 → 改单改交期 → 断言 `prd_production_plan.order_line_id` 仍可解析、关联计划在详情页仍显示
3. 出库回写 `delivered_qty` → 改单 → 断言 `delivered_qty` **未被覆盖**
4. 尝试删除已发货行 → 断言被拒绝且提示行号
5. 并发：改单同时出库回写 → 断言不丢更新

---

## 九、执行进度

| 批次/任务 | 状态 | commit | 验证 |
|---|---|---|---|
| 批次 0 回归基线 | ✅ 完成 | `4dddf2e9` | 新增 43 用例；`Tests run: 68, Failures: 0` |
| T1.1 改单差量更新 | ✅ 完成 | `336dd856` | 新增 11 用例；`Tests run: 79, Failures: 0`；前端 `eslint` + `build:prod` 通过 |
| T1.2 删除止血 | ✅ 完成 | 见本批次 commit | 新增 3 用例；`Tests run: 33`（本类） |
| T1.3 退货门禁 | ✅ 完成 | 见本批次 commit | 新增 5 用例；`Tests run: 87, Failures: 0` |
| T1.4 开票收口 | ✅ 完成 | `8f5ab354` | 新增 8 用例 |
| T1.5 红冲/核销 | ✅ 完成 | `8f5ab354` | 新增 3 用例 |
| T1.6 报表修复 | ✅ 完成（SQL/租户/权限） | 见本批次 commit | 新增 6 用例；`Tests run: 106, Failures: 0`。菜单与前端属 T3.4，待 Q1 |
| T1.7 跨库事务（**已实测：可用，原判为误判**） | ✅ 完成（无需修复） | — | 见 §三 P0-5 的更正说明：被调用方是 `REQUIRES_NEW`，`@DS("master")` 实际生效；且 4 个 `submit()` 在工作流调用后无 erp 写入，跨库非原子性无暴露面 |
| T1.8 金额服务端重算 | ✅ 完成 | 见本批次 commit | 新增 2 用例；`Tests run: 100, Failures: 0` |
| 批次 2：契约与校验 | ✅ 完成（除 Q3 相关项） | 见本批次 commit | 新增 14 用例；`Tests run: 119, Failures: 0`；前端 `eslint` + `build:prod` 通过 |
| 批次 3：功能补齐 | 🚧 进行中 | | |
| └ T3.1a 价目表引擎修复（D2 公式 + NULL 边界 + selectList + 优先级） | ✅ 完成 | 见本批次 commit | 新增 4 用例；`Tests run: 123, Failures: 0` |
| └ T3.1b `resolvePrice` 接口 + 前端取价接线 | ✅ 完成 | 见本批次 commit | 后端编译通过；前端 `eslint` + `build:prod` 通过 |
| └ T3.1c 价目表前端（物料选择器/菜单/权限码/字典） | ✅ 完成 | 见本批次 commit | 新增 5 用例；`Tests run: 128, Failures: 0`；前端 `eslint` + `build:prod` 通过 |
| └ T3.2 报价转单前端接线 | ✅ 完成（勘误：本就已接线） | 见本批次 commit | **审计勘误**：`convertQuotationToOrder` 在 `quotation/index.vue:220` 早已被调用，原结论「前端零调用」是 grep 模式（`convertToOrder`）匹配不到函数名 `convertQuotationToOrder` 造成的误报。本次实际只补了工具栏按钮的状态门禁 |
| └ T3.3 退货闭环 + 退货 UI 接线（Q2 尽量全闭环） | ✅ 完成 | 见本批次 commit | 新增 `returned_qty` + 回写与超退守卫 + 前端 confirm/execute 接线；新增 3 用例；`Tests run: 259, Failures: 0` |
| └ T3.4 报表菜单 + 前端（Q1 保留并修完） | ✅ 完成 | 见本批次 commit | 菜单 2237 + 角色授权 + 报表页 4 个前端缺陷修复；前端 `eslint` + `build:prod` 通过 |
| └ T3.5 死事件/死 VO 清理 | ✅ 完成 | 见本批次 commit | 删 `SoLineProducedEvent`（全仓无监听者）+ `SoCreditUsageVo` 两个永不填充字段 |
| └ §8.6 T1.1 验收规格落为可执行测试 | ✅ 完成 | 见本批次 commit | 新增 2 个 acceptance 用例（3 行改单保 ID / 改交期不动生产计划）；`Tests run: 261, Failures: 0` |
| 批次 4：DDL 收敛 | ✅ 完成（§五 第 1~7 项） | 见本批次 commit | 主脚本 14 张 so_ 表括号平衡校验通过；存量库脚本 `2026-09-19-so-ddl-hardening.sql`；`Tests run: 128, Failures: 0`；前端 `eslint` + `build:prod` 通过 |
| 批次 5：清理与一致性 | 🚧 进行中（仅剩 Q4 相关项） | | |
| └ 客户订单幻影字段清理 | ✅ 完成 | 见本批次 commit | 前端 `eslint` + `build:prod` 通过 |
| └ 列表页选中项残留修复 | ✅ 完成 | 见本批次 commit | 同上 |
| └ 退货/发票工具栏状态门禁对齐行内 | ✅ 完成 | 见本批次 commit | 前端 `eslint` + `build:prod` 通过 |
| └ 报价/退货明细行改 VO 直出（去掉 `@JSONField` 无效注解） | ✅ 完成 | 见本批次 commit | 新增 2 用例 |
| └ 补齐 `@ExcelIgnoreUnannotated`（4 个 VO） | ✅ 完成 | 见本批次 commit | 复核全部带 `@ExcelProperty` 的 VO 均已覆盖 |
| └ 发票/合同明细行改 VO（新建 LineVo） | ✅ 完成 | 见本批次 commit | 新增 2 用例；`Tests run: 132, Failures: 0` |
| └ 异常口径统一为 `ServiceException` | ✅ 完成 | 见本批次 commit | so 模块 `throw new RuntimeException` 已清零 |
| └ 明细「未提交」语义加固（null vs 空集合） | ✅ 完成 | 见本批次 commit | 新增 6 用例；`Tests run: 138, Failures: 0` |
| └ DDL ↔ Java 实体一致性静态校验 | ✅ 通过 | — | 14 张 so_ 表列与实体字段完全一致 |
| └ 服务端权威字段加固（状态/时间戳/信用/红冲标记） | ✅ 完成 | 见本批次 commit | 新增 5 用例；`Tests run: 143, Failures: 0` |
| └ 高风险改动补真实场景用例 + 强化断言强度 | ✅ 完成 | 见本批次 commit | 新增 2 用例 + 强化 2 处断言 |
| └ 发票表头金额服务端汇总（修 `totalAmount` 伪造绕过核销上限） | ✅ 完成 | 见本批次 commit | 新增 3 用例；`Tests run: 148, Failures: 0` |
| └ 流程回调状态改写守卫（防"复活"已取消/已确认订单） | ✅ 完成 | 见本批次 commit | 新增 5 用例；`Tests run: 153, Failures: 0` |
| └ 同类守卫补全到发票/退货/合同（含防重复过账） | ✅ 完成 | 见本批次 commit | 新增 11 用例；so 171 / 全模块 252 全绿 |
| └ 客户订单引用守卫（被销售订单引用时禁止删除）+ 新建测试类 | ✅ 完成 | 见本批次 commit | 新增 7 用例（测试类 9→10）；`Tests run: 160, Failures: 0` |
| └ 13 个死字段清理（待 Q4） | ✅ 完成（**复核后实际只删 5 个**） | 见本批次 commit | 原清单 13 个里 8 个是前端在用的 UI 字段；`Tests run: 252, Failures: 0`；前端 `eslint` + `build:prod` 通过 |
| └ Q3 税率口径统一为百分数 | ✅ 完成 | 见本批次 commit | DDL 单位注释 + 发票区间校验补齐 + 退货表单改数值输入 + 更正「含税」误判；新增 4 用例；`Tests run: 256, Failures: 0` |

**加固轮要点**：
- **「客户端可伪造服务端权威字段」是系统性问题**，逐个服务排查后修复：
  | 位置 | 可伪造字段 | 后果 |
  |---|---|---|
  | `SoInvoiceServiceImpl.insertByBo` | `status` | 直建 ISSUED 发票，而 insertByBo 不发布 `SoInvoiceIssuedEvent`（只有 afterFlowHandle / redFlush 发布）→ 发票标记「已开」却从未过账总账 |
  | 同上 | `verifiedAmount` | `verifyInvoice` 以「已核销额 + 本次」判断是否置为已核销，预置大值即可用很小金额把发票置为已核销 |
  | 同上 | 明细负数量 | 走 `occupyInvoicedQty` 的**释放**分支 → 绕过红冲流程释放已开票量，重新打开超开缺口 |
  | `SoReturnServiceImpl.insertByBo` | `status` | 直建 RECEIVED 退货单 → 界面显示「已收货」但库存从未加回；且 T1.3 后 execute 要求 CONFIRMED，这张单永远无法真正执行 |
  | `SoSalesOrderHeaderServiceImpl.insertByBo` | 4 个生命周期时间戳、`creditStatus`/`creditCheckTime`/`creditBlockedBy` | 草稿单上出现假的「确认时间/履行时间」或「已检查」 |
  | `SoSalesOrderHeaderServiceImpl.updateByBo` | 同上 | 改单可改写信用检查结果 |
  已全部改为服务端权威：新增时复位为初值（订单 `creditStatus=NOT_CHECKED`），改单时从库中还原。
  另 `insertByBo` 对留空发票号做兜底生成（`invoice_no` 为 NOT NULL + 唯一键，空串会让第二张手工票撞唯一键；
  前端有必填校验所以 UI 不受影响，这是给 API 客户端的兜底）。
- **`updateByBo` 传 null 明细会删光所有行**（真实数据丢失风险）：`diffUpdateLines` 把「请求中未出现的
  已有行」一律逻辑删除，而 `lineBoList == null` 时该分支整体被跳过 → 所有已有行都成了「未出现」→ 全删；
  同时 `recalcAmounts` 按空明细把表头金额算成 0。即 API 客户端做部分更新（例如只改备注）会
  **静默清空整单明细与金额**。已改为与合同模块一致的口径：**null = 本次不提交明细（明细与金额都保持
  库中原值）；空数组 = 显式清空**。
- 修复过程中一度只做了「null 时跳过重算」，那会让客户端的 `totalAmount` 直接落库 ——
  等于重新打开 T1.8 堵掉的伪造口子。已补「从库中还原金额」分支并加断言锁定。
- 新增时 null 明细视作空明细、金额归零（不能因为没传明细就留下客户端伪造的金额）。
- **同类问题在另外三个服务里也存在**（同一轮一并修复）：`SoQuotationServiceImpl` / `SoReturnServiceImpl` /
  `SoPriceListServiceImpl` 的 `updateByBo` 都是无条件「删行 + 重插」，传 null 明细同样会删光明细、
  并把报价/退货的金额清零。四处（含销售订单）已统一为「null = 不提交明细，空数组 = 显式清空」。
- **DDL ↔ Java 实体一致性静态校验通过**：提取 14 张 so_ 表的列名与对应实体字段（camelCase→snake_case）
  做双向 diff，结果**完全一致**（审计基础列 tenant_id/create_dept/create_by/create_time/
  update_by/update_time/del_flag 按继承处理）。检查器本身用「喂一个不存在的列」做了负向测试自证有效
  —— 避免重蹈上一轮 PowerShell 按 GBK 解码 UTF-8 导致括号计数失真的覆辙。

**批次 5 第三轮要点**：
- 新建 `SoInvoiceLineVo` / `SoContractLineVo`（字段集 = 实体减审计字段），
  `SoInvoiceVo` / `SoContractVo` 的明细行列表改 VO 类型，Service 里用 `MapstructUtils` 转换。
- **关键点：`SoInvoiceLineVo` 必须保留 `soLineId`**。前端不直接用它，但后端
  `adjustInvoicedQtyOnEdit` 靠它判断明细是否换绑订单行 —— VO 若丢掉，编辑自动生成的草稿发票
  会被误判为「换绑」，先释放旧订单行占用、再按 null 不占用，导致 `invoiced_qty` 归零而发票仍在，
  重新打开超开缺口（即 T1.4 刚堵上的洞会被这个 VO 改动重新捅开）。已加断言测试锁定。
- 两个前端类型（`InvoiceLineForm` / `ContractLineForm`）本就声明了全部业务字段（含 `soLineId`/
  `sortOrder`），说明是后端 VO 少字段，本次改动方向与前端契约一致，无需改前端。

**批次 5 第二轮已完成项要点**：
- **明细行改 VO 直出**（报价 / 退货）：原实现返回实体，会把 `tenantId`/`delFlag`/`createBy`
  暴露给前端；且原先的 `@JSONField(serialize = false)` 是 fastjson2 注解，本项目 HTTP 走 Jackson，
  **完全不生效**（即便生效也会让表单读不到明细行 —— 注解意图本身也是错的）。
  **踩坑提醒**：`SoReturnLineVo` 比实体少了 `baseUomId`/`baseUomName`/`taxRate`/`taxAmount`/`sortOrder`，
  而退货表单实际绑定 `baseUomId` 与 `taxRate` —— 直接换类型会让表单加载丢字段、保存回写 null。
  已先补齐 VO 字段再换类型，并加了「字段不丢」的断言测试锁定。
- **工具栏状态门禁对齐**：退货（改/删仅 DRAFT）、发票（改限 DRAFT/REJECTED、删仅 DRAFT）的工具栏按钮
  此前只按 `single`/`multiple` 禁用，与行内按钮及后端状态机分叉。统一改为按选中行状态门禁，
  并加 `watch(list)` 清空勾选防旧选中残留。

**批次 5 已完成项要点**：
- **客户订单「销售部门」幻影字段**：`so_customer_order` 表 / 实体 / BO / VO **都没有** `deptId`/`deptName`，
  而前端有列表列（恒空）+ 表单 `dept-select`（选择被静默丢弃）。改为**本地筛选项**（不落库），
  仅用于筛选销售代表候选，并改名为「部门筛选」+ 补注释说明；移除列表列与隐藏的 `deptName` 输入；
  新增/修改时通过 `useCrud` 的 `extraInit` 回调清空该筛选项。
  （部门归属本就存在客户档案 `bd_client.dept_id`，不需要在客户订单上冗余一份。）
- **列表页选中项残留**：`orderheader/index.vue` 搜索/重置/翻页/删除刷新后，`ids`/`selectedRow`
  仍指向旧行，工具栏「确认/履行/关闭/取消/修改/删除」按一张已不在列表里的单据判断可用态。
  改为 `watch(orderheaderList)` 统一清空本地选中态并调用 `clearSelection()`，一处覆盖所有刷新路径。

**批次 4 实施要点**：
- 唯一键补租户维度：`uk_so_no` / `uk_co_no` 改为 `(单号, tenant_id)`（同模块 contract/invoice/price_list 早已带 tenant_id）
- 补单号唯一约束：`uk_quotation_no` / `uk_return_no` = `(单号, tenant_id)`
- `so_price_list_line`：`min_qty` 去 NULL 并补 `uk_pl_line_tier(price_list_id, material_id, min_qty)`
  —— MySQL 唯一索引允许多个 NULL，不去 NULL 则约束对重复行不生效
- 补索引 14 个（另加 5 个唯一键，见上）；重建 `so_invoice` 14 处乱码列注释；`tax_rate`/`discount_rate` 补「百分数」单位说明
  （原文档写「13 个」系计数笔误：提交信息里列了 14 项却写成 13，已按脚本实际 `ADD INDEX` 语句逐条复核修正）
- **顺带修掉批次 2 的遗漏**：`so` 模块的 BO 行列表字段此前都没有 `@Valid`，
  行级 `@NotNull` 其实是死代码（只有 wm 模块 3 个 BO 用了嵌套校验）。
  已给 `SoSalesOrderHeaderBo` / `SoPriceListBo` 的行列表补 `@Valid`，并同步补前端单价必填校验。

**T1.1 实施要点**（`SoSalesOrderHeaderServiceImpl`）：- `updateByBo` 仅允许 `DRAFT`；单号与状态强制还原为库中值，客户端改写无效
- `diffUpdateLines`：按 `lineId` 区分「更新 / 新增 / 删除」，保留已有行 `line_id`
- 只在本单已有行中匹配 `lineId`，他单/伪造的 `lineId` 按新增处理（重置为 null），防越权改写
- `applyEditableLineFields`：白名单更新，`deliveredQty/productionQty/invoicedQty/lineStatus/linked*/oqc*/reserved*` 一律保持库中原值（顺带修掉"表单快照覆盖并发已发货量"的丢失更新）
- `assertLineDeletable`：已履约行、已关联生产计划的行禁止删除；先全量校验再执行，避免删一半失败
- 新增行 `resetServerOwnedLineFields` 强制初始化履约/关联字段
- 前端 `orderheader/index.vue` 工具栏与行内「修改/删除」按状态门禁，`handleUpdate` 二次守卫

**T1.4/T1.5 实施要点**（`SoInvoiceServiceImpl`）：
- 统一原语 `adjustInvoicedQty(soLineId, delta, strict)`：占用严格（超开即抛）、释放宽容（历史票未占用过则跳过，不阻断作废/删除/红冲）
- 四个写入口全部收口：`insertByBo`、`diffUpdateLines`（新增/改量/换绑/删除）、`generateDraftFromOrder`、`redFlush`
- 红冲防重复按 `original_invoice_id` 判重，**无需新增 DDL**（原实现只校验原票 `redFlag`，而红冲后原票状态与标记都不变）
- 核销上限 = 价税合计；顺带修 `verifiedAmount` 为 null 的 NPE

**T1.8 实施要点**（`SoSalesOrderHeaderServiceImpl.recalcAmounts`）：
- 口径与前端 `calcLine`/`calcHeader` 逐字一致（折扣税前扣，Model A），税率/折扣率均按**百分数**（13 = 13%）
- `insertByBo`/`updateByBo` 一律重算，客户端传 `totalAmount=0` 会被覆盖 —— 堵住「传 0 绕过 `confirm()` 信用额度校验」
- 重算结果同时写回 lineBo，供后续白名单落库使用

**批次 1 剩余**：
- T1.6 报表修复（列名/租户/权限）—— SQL 与租户部分与 Q1 无关可直接修；菜单与前端属 T3.4 待 Q1
- T1.7 跨库事务 —— 待 Q5 实测结果

**批次 2 实施要点**：
- **BO 补 Jakarta Validation**（此前两个 BO 零注解，Controller 的 `@Validated` 是空转）：
  Header `clientId`/`orderDate` `@NotNull`，`exchangeRate`/`discountRate` 范围；
  Line `materialId` `@NotNull`、`orderedQty` `@DecimalMin("0.0001")`、`unitPrice`/`taxRate`/`discountPercent` 范围。
  采用**空值安全**的范围注解（Hibernate 忽略 null），避免误伤历史可空字段；写法对齐 `SoContractBo`。
- **状态由服务端强制**：`SoSalesOrderHeaderServiceImpl.insertByBo` 无条件置 DRAFT（原实现只在为空时兜底，
  允许客户端直写终态 —— `sys_oper_log` 中确有 `orderStatus=CONFIRMED` 直接建单记录，会绕过信用检查、
  库存预留与生产计划生成）；前端表单状态框改为只读 `dict-tag`。
- **`creditStatus` 写 `CHECKED_OK`** 而非 `PASSED`（字典 `so_credit_status` 只有
  NOT_CHECKED/CHECKED_OK/CHECKED_BLOCKED，原值前端只能显示原始码）。
- **`confirm()` 要求 `clientId` 非空**：`checkAndFreeze` 对 `clientId=null` 会静默跳过，等于绕过额度控制。
- **报价单**（`SoQuotationServiceImpl`）：`insertByBo` 强制 DRAFT；`updateByBo`/`deleteByIds` 限 DRAFT；
  `convertToSalesOrder` 要求 CONFIRMED（原实现只拦 CONVERTED，草稿可跳过确认直接转单）+ Controller 加 `@RepeatSubmit`；
  明细行改**白名单重建**（丢弃 lineId/tenantId/delFlag/审计字段，防跨租户写入）+ 行金额与表头金额服务端重算；
  异常口径由 `RuntimeException` 改 `ServiceException`。

---

## 附录 A：死字段判定依据（**2026-09-19 已修正 —— 原判定有缺陷**）

> ⚠️ **修正说明**：原判定为「全仓 `getProjectId|setProjectId|...` 等 getter/setter 零命中」，
> 并据此列出 13 个死字段。**该口径有缺陷**：Jackson 是**反射**序列化/反序列化，
> 且字段可能**只被前端 JSON 使用** —— 在 Java 源码里 grep 不到 getter 并不等于字段是死的。
> 用户决策「死字段删」后按新口径（**Java 双向 + 前端 `plus-ui` 全量**）复核，
> 结论是 **13 个里只有 5 个真死**，另 8 个是前端真实在用的 UI 字段。

**真死（已删除，5 个）**：只有字段声明 + 前端 `types.ts` 类型声明 + 表单 `initForm` 默认值，
无任何界面绑定、不参与计算、不参与查询。

| 列 | 表 | 说明 |
|---|---|---|
| `delivery_address_id` | `so_sales_order_header` | 同表 `delivery_address`（文本详情）在用，仅 ID 无人用 |
| `invoice_address_id` | `so_sales_order_header` | 同上 |
| `linked_po_id` | `so_sales_order_line` | 同表 `linked_mo_id` 在用 |
| `reserved_serials` | `so_sales_order_line` | 同表 `reserved_batch_no` 在用（前端有展示列） |
| `pa_group_id` | `so_sales_order_line` | — |

**并非死字段（保留，8 个）** —— 前端均有 `v-model` 绑定或参与计算：

| 字段 | 位置 | 前端用途 |
|---|---|---|
| `project_id` | 表头 | 表单项「项目ID」`v-model` |
| `is_pa_enabled` | 表头 | 表单项「是否启用项目配套发料」`v-model` |
| `distribution_channel` | 表头 | 表单项「分销渠道」`v-model`；并从客户订单 `sourceChannel` 带入 |
| `customer_ref_no` | 表头 | 表单项「客户参考号」`v-model`；并从客户订单 `customerPoNo` 带入 |
| `discount_percent` | 行 | 行折扣率 `v-model`，**参与 `calcLine` 行金额计算**（`form.vue:1235/1269`） |
| `delivery_priority` | 行 | 行「交付优先级」下拉 `v-model` |
| `customer_line_ref` | 行 | 行「客户行参考号」输入框 `v-model`；生产计划表单也读取它 |
| `reserved_batch_no` | 行 | 行「预留批次号」展示列 |

> 附注：`dept_id`/`dept_name` 也不是死字段 —— 前端表单会写入并经 MapStruct 落库，
> 只是列表页从不作为查询条件（`buildQueryWrapper` 分支不可达）。

> **方法论教训**：判定"死代码/死字段"时，**不能只 grep 后端**。本项目 HTTP 走 Jackson，
> 字段可以通过反射被前端使用而完全不出现在 Java 调用点上；
> 正确做法是 Java 与前端**双向**检索，并优先看 `v-model` 绑定与计算表达式。

## 附录 B：已核实**不是**问题的项（避免重复排查）

- **E4/E5/E8**（SO 条件更新、退货 CAS、预留消耗）：已在 09-06 P1 批次修复，当前代码确认为条件更新（`confirm:306-313`、`fulfill:417-422`、`close:469-474`）
- **`SalesOrderFulfilledEventListener`**：`AFTER_COMMIT` + `@Async("financialExecutor")` + `TenantHelper.dynamic` + 异常吞掉，实现规范（`SalesOrderFulfilledEventListener.java:41-55`）
- **`previewCode` 前端直接赋值**：`SysAutoCodeController.java:56` 返回裸字符串、拦截器透传，非 bug
- **VO→实体 MapStruct 不会置空未映射字段**：MP 默认 `NOT_NULL` 更新策略跳过 null，不丢数据（前提：不要改成 `FieldStrategy.IGNORED`）
- **`so_invoice` 乐观锁**：`version` + `@Version` + 插件配置正常（`MybatisPlusConfig.java:52,85`）
- **`SoInvoiceServiceImpl` 的原子累加/门禁范式**：是本模块写得最好的部分，应作为其他单据的参考实现
- **`tax_rate decimal(5,2)`**：可存至 `999.99`，**不存在**"存不下 13.55%"的问题
