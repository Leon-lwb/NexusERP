# `ry-erp` 全量业务数据删除计划

> **性质**：**只读分析产出的计划书**，本文档**未执行任何写操作**。全部结论均由实测 SQL 支撑。
> **生成环境**：MySQL 5.7.20-log @ localhost:3306，账号 `root`
> **目标库**：`ry-erp`（业务，125 张表）；关联库 `ry-mes`(68) / `ry-vue`(194) / `ry-iot`(3)
> **客户端**：`D:\mysql-8.0.39\mysql-8.0.39-winx64\bin\mysql.exe`

---

## 0. 执行摘要（先看这 7 条）

| # | 结论 | 关键证据 |
|---|---|---|
| 1 | **`ry-erp` / `ry-mes` / `ry-iot` 三库的外键约束数均为 0**，`ry-vue` 的 46 个外键全部是库内自引用，**全库不存在任何跨库外键** | §4.1 |
| 2 | 因此删除顺序**无数据库层保护**，必须靠本文档的批次表手工保证 | §2 |
| 3 | **3D 演示数据精确落在 4 张表、共 164 行**，且对 `ry-erp` 其余 121 张表、以及 `ry-mes`/`ry-vue`/`ry-iot` **零引用** | §4.2 |
| 4 | **最大的坑是 `ry-mes` → `ry-erp` 的 43 处活引用**，删 `ry-erp` 主数据前必须先解绑，否则 MES 立刻产生孤儿 | §4.3 |
| 5 | **已经存在 1 条跨库孤儿**：`ry-mes.pro_work_order#2089742340792041473` 的 `material_id` 在 `bd_material` 里不存在 | §4.4 |
| 6 | **125 张表中只有 10 张带 `AUTO_INCREMENT`**，其余 115 张是雪花 ID（`ASSIGN_ID`）——这直接决定 `TRUNCATE` 的风险面很小 | §3.2 |
| 7 | **保留集必须闭包**：`wm_picking_rule` / `wm_putaway_rule` 虽然像"配置表"，但全部挂在待删仓库上，必须一起删 | §4.5 |

---

## 1. 全表清点表（125 张，实测行数）

**取数方法**（一次性批量实测，非 `TABLE_ROWS`）：

```sql
-- 由 information_schema 生成 125 条 COUNT(*) 的 UNION ALL 后执行
SELECT 'bd_bom' AS tbl, COUNT(*) AS n FROM `bd_bom`
UNION ALL SELECT 'bd_bom_alternative', COUNT(*) FROM `bd_bom_alternative`
... -- 共 125 条
```

结果落盘 `125` 行，与表数一致。`TABLE_ROWS` 估算确实不可信（例：`bd_bom_alternative` 估算 1 实际 1；`wm_material_stock` 估算 92 实际 92 巧合一致；但 `wm_storage_location` 估算 128 实际 128，而 `sch_employee_schedule` 估算 416 实际 416 —— 本库恰好接近，仍全部以实测为准）。

**处置图例**：`保留` = 不动；`全删` = `DELETE`/`TRUNCATE` 清空；`部分删` = 带 `WHERE` 条件删；`重置编码` = 保留结构、重置序列计数器。

### 1.1 基础主数据（`bd_*`，18 张）

| 表名 | 表注释 | 实测行数 | 存活/软删 | 分类 | 处置 | 理由 |
|---|---|---|---|---|---|---|
| `bd_bom` | BOM主表 | 15 | 11 / 4 | 基础主数据 | **全删** | 产品工程数据，非系统字典；4 条 `del_flag=1` 脏数据 |
| `bd_bom_alternative` | BOM替代料表 | 1 | 1 / 0 | 基础主数据 | **全删** | 依附 BOM |
| `bd_bom_detail` | BOM明细表 | 72 | 32 / 40 | 基础主数据 | **全删** | 依附 `bd_bom`，必须先删 |
| `bd_category_attribute` | 物料分类属性模板 | 0 | — | 配置规则 | **保留** | 分类属性模板属系统预置结构 |
| `bd_client` | 客户表 | 13 | 13 / 0 | 基础主数据 | **全删** | 业务主数据；⚠️ MES 有 1 处活引用 |
| `bd_client_credit` | 客户信用表 | 6 | 6 / 0 | 基础主数据 | **全删** | 依附 `bd_client` |
| `bd_material` | 物料产品主表 | 52 | 52 / 0 | 基础主数据 | **全删** | ⚠️ 被 MES 大量引用（见 §4.3） |
| `bd_material_attribute_value` | 物料扩展属性值 | 0 | — | 基础主数据 | **全删** | 空表，依附物料 |
| `bd_material_category` | 物料产品分类表 | 55 | 53 / 2 | 基础主数据（字典） | **保留** | 分类字典，重建成本高；自引用树已闭包 51/51 |
| `bd_material_vendor` | 物料供应商关系表 | 7 | 7 / 0 | 基础主数据 | **全删** | 关系表，依附物料+供应商 |
| `bd_process` | 工序定义表 | 13 | 13 / 0 | 基础主数据 | **全删** | ⚠️ MES 引用工序主数据 |
| `bd_routing` | 工艺路线主表 | 6 | 6 / 0 | 基础主数据 | **全删** | 工程数据 |
| `bd_routing_step` | 工艺路线步骤表 | 23 | — | 基础主数据 | **全删** | 依附 `bd_routing`；**该表无 `del_flag`** |
| `bd_serial_number_rule` | 产品序列号/条码规则表 | 3 | — | 配置规则 | **重置编码** | 规则本体保留，`current_seq` 全部为 1，需重置 |
| `bd_unit_of_measure` | 计量单位表 | 54 | — | 基础主数据（字典） | **保留** | 系统预置计量单位字典 |
| `bd_vendor` | 供应商表 | 6 | 5 / 1 | 基础主数据 | **全删** | ⚠️ MES `qc_iqc.vendor_id` 有 2 处活引用 |
| `bd_workshop` | 车间表 | 7 | — | 基础主数据 | **全删** | ⚠️ MES 有 11 处活引用 |
| `bd_workstation` | 工作站表 | 21 | — | 基础主数据 | **全删** | ⚠️ MES 有 70 处活引用 |

### 1.2 销售（`so_*`，14 张）

| 表名 | 表注释 | 实测行数 | 存活/软删 | 分类 | 处置 |
|---|---|---|---|---|---|
| `so_contract` | 销售合同表 | 0 | — | 业务单据 | **全删** |
| `so_contract_line` | 销售合同明细行表 | 0 | — | 业务单据 | **全删** |
| `so_credit_usage` | 信用占用记录 | 7 | — | 业务单据 | **全删** |
| `so_customer_order` | 客户订单表 | 22 | 21 / 1 | 业务单据 | **全删** |
| `so_invoice` | 销售发票表 | 6 | 6 / 0 | 业务单据 | **全删** |
| `so_invoice_line` | 销售发票明细行表 | 4 | 4 / 0 | 业务单据 | **全删** |
| `so_price_list` | 销售价格表头 | 0 | — | 业务单据 | **全删**（无 `del_flag`） |
| `so_price_list_line` | 销售价格表行 | 0 | — | 业务单据 | **全删**（无 `del_flag`） |
| `so_quotation` | 销售报价单表 | 1 | 1 / 0 | 业务单据 | **全删** |
| `so_quotation_line` | 销售报价行表 | 1 | 1 / 0 | 业务单据 | **全删** |
| `so_return` | 销售退货单表 | 0 | — | 业务单据 | **全删** |
| `so_return_line` | 销售退货行表 | 0 | — | 业务单据 | **全删** |
| `so_sales_order_header` | 销售订单主表 | 18 | 13 / 5 | 业务单据 | **全删** |
| `so_sales_order_line` | 销售订单行表 | 25 | 12 / 13 | 业务单据 | **全删** |

### 1.3 采购（`pu_*` / `po_*`，13 张）

| 表名 | 表注释 | 实测行数 | 存活/软删 | 分类 | 处置 |
|---|---|---|---|---|---|
| `po_requisition` | 请购单主表 | 0 | — | 业务单据 | **全删** |
| `po_requisition_detail` | 请购单明细表 | 0 | — | 业务单据 | **全删** |
| `pu_contract` | 采购合同头表 | 0 | — | 业务单据 | **全删** |
| `pu_contract_line` | 采购合同行表 | 0 | — | 业务单据 | **全删** |
| `pu_invoice` | 采购进项发票表 | 0 | — | 业务单据 | **全删** |
| `pu_invoice_line` | 采购进项发票明细行表 | 0 | — | 业务单据 | **全删** |
| `pu_purchase_order` | 采购订单表 | 7 | 7 / 0 | 业务单据 | **全删** |
| `pu_purchase_order_line` | 采购订单明细表 | 9 | 9 / 0 | 业务单据 | **全删** |
| `pu_purchase_requisition` | 采购申请表 | 12 | 7 / 5 | 业务单据 | **全删** |
| `pu_purchase_requisition_line` | 采购申请明细表 | 25 | 8 / 17 | 业务单据 | **全删** |
| `pu_purchase_return` | 采购退货表 | 1 | 1 / 0 | 业务单据 | **全删** |
| `pu_purchase_return_line` | 采购退货行表 | 1 | 1 / 0 | 业务单据 | **全删** |
| `pu_vendor_evaluation` | 供应商评估记录表 | 6 | 6 / 0 | 业务单据 | **全删** |

### 1.4 生产计划（`prd_*`，13 张）

| 表名 | 表注释 | 实测行数 | 存活/软删 | 分类 | 处置 |
|---|---|---|---|---|---|
| `prd_capacity_calendar` | 产能日历表 | 0 | — | 配置规则 | **全删**（带 `AUTO_INCREMENT=1`） |
| `prd_material_requirement` | 物料需求单表 | 27 | 27 / 0 | 业务单据 | **全删** |
| `prd_material_requirement_detail` | 物料需求单明细表 | 37 | 37 / 0 | 业务单据 | **全删** |
| `prd_production_plan` | 生产计划表 | 18 | 16 / 2 | 业务单据 | **全删** |
| `prd_rework_order` | 返工订单表 | 0 | — | 业务单据 | **全删** |
| `prd_schedule_plan` | APS排程方案表 | 1 | 1 / 0 | 业务单据 | **全删** |
| `prd_schedule_task` | APS排程工序任务表 | 12 | 12 / 0 | 业务单据 | **全删** |
| `prd_subcontract` | 委外订单表 | 1 | 1 / 0 | 业务单据 | **全删** |
| `prd_subcontract_bom_line` | 委外订单BOM快照行 | 0 | — | 业务单据 | **全删** |
| `prd_subcontract_quote` | 委外订单报价记录 | 0 | — | 业务单据 | **全删** |
| `prd_work_order` | 生产工单表 | 12 | 11 / 1 | 业务单据 | **全删** ⚠️ MES 核心引用 |
| `prd_work_order_resource` | 生产工单资源分配表 | 66 | 50 / 16 | 业务单据 | **全删** |
| `prd_work_report` | 生产报工表 | 1 | 1 / 0 | 业务单据 | **全删** |

### 1.5 仓储（`wm_*`，38 张）

| 表名 | 表注释 | 实测行数 | 存活/软删 | 分类 | 处置 |
|---|---|---|---|---|---|
| `wm_arrival_notice` | 到货通知单表 | 8 | 7 / 1 | 业务单据 | **全删** |
| `wm_arrival_notice_line` | 到货通知单行表 | 19 | 13 / 6 | 业务单据 | **全删** |
| `wm_barcode` | 条码实例表 | 24 | — | 业务单据 | **全删** ⚠️ 见 §4.6 |
| `wm_barcode_config` | 条码配置表 | 2 | — | 配置规则 | **重置编码** ⚠️ 见 §4.6 |
| `wm_batch` | 批次管理表 | 19 | — | 业务单据 | **全删** |
| `wm_material_handover` | 上料交接单 | 0 | — | 业务单据 | **全删** |
| `wm_material_handover_line` | 上料交接单行 | 0 | — | 业务单据 | **全删** |
| `wm_material_receive` | 物料入库单表 | 25 | 15 / 10 | 业务单据 | **全删** |
| `wm_material_receive_detail` | 物料入库单明细表 | 23 | 14 / 9 | 业务单据 | **全删** |
| `wm_material_receive_line` | 物料入库单行表 | 79 | 24 / 55 | 业务单据 | **全删** |
| `wm_material_return` | 生产退料单（注释为乱码 `ç”Ÿäº§é€€æ–™å•`） | 0 | — | 业务单据 | **全删**（无 `del_flag`） |
| `wm_material_return_line` | 生产退料单行 | 0 | — | 业务单据 | **全删**（无 `del_flag`） |
| `wm_material_stock` | 库存物料记录表 | 92 | 82 / 10 | 业务单据 | **部分删**：保留 3D 55 行（见 §4.2） |
| `wm_material_stock_tx` | 库存事务表 | 51 | — | 业务单据 | **全删** |
| `wm_misc_issue` | 杂项出库单表 | 0 | — | 业务单据 | **全删** |
| `wm_misc_issue_line` | 杂项出库行表 | 0 | — | 业务单据 | **全删** |
| `wm_misc_recpt` | 杂项入库单表 | 0 | — | 业务单据 | **全删** |
| `wm_misc_recpt_line` | 杂项入库行表 | 0 | — | 业务单据 | **全删** |
| `wm_picking_rule` | 拣货规则配置表 | 3 | — | 配置规则 | **全删** ⚠️ 挂在待删仓库 2/3 上，见 §4.5 |
| `wm_production_issue` | 生产领料单表 | 18 | 16 / 2 | 业务单据 | **全删** |
| `wm_production_issue_line` | 生产领料行表 | 53 | 45 / 8 | 业务单据 | **全删** |
| `wm_production_recpt` | 生产完工入库表 | 6 | — | 业务单据 | **全删**（带 `AUTO_INCREMENT`） |
| `wm_putaway_rule` | 上架规则配置表 | 3 | — | 配置规则 | **全删** ⚠️ 同上，见 §4.5 |
| `wm_sales_issue` | 销售出库主表 | 3 | — | 业务单据 | **全删** |
| `wm_sales_issue_line` | 销售出库明细表 | 3 | — | 业务单据 | **全删** |
| `wm_sn` | 序列号管理表 | 0 | — | 业务单据 | **全删** |
| `wm_stock_alert` | 安全库存预警表 | 12 | 9 / 3 | 业务单据 | **全删** |
| `wm_stock_reconciliation` | 库存台账校验表 | 40 | 8 / 32 | 报表 | **全删** |
| `wm_stock_taking_line` | 盘点行表 | 16 | 14 / 2 | 业务单据 | **全删** |
| `wm_stock_taking_plan` | 盘点计划表 | 9 | 8 / 1 | 业务单据 | **全删** |
| `wm_storage_area` | 库区表 | 12 | — | 基础主数据（仓储） | **部分删**：保留 3D 4 行 |
| `wm_storage_location` | 库位表 | 128 | — | 基础主数据（仓储） | **部分删**：保留 3D 104 行 |
| `wm_subcontract_issue` | 委外发料单 | 0 | — | 业务单据 | **全删** |
| `wm_subcontract_issue_line` | 委外发料单行 | 0 | — | 业务单据 | **全删** |
| `wm_subcontract_receive` | 委外收货单 | 0 | — | 业务单据 | **全删** |
| `wm_transfer` | 库存调拨单表 | 1 | — | 业务单据 | **全删** |
| `wm_transfer_line` | 库存调拨行表 | 1 | — | 业务单据 | **全删** |
| `wm_warehouse` | 仓库表 | 5 | — | 基础主数据（仓储） | **部分删**：保留 3D 1 行 |

### 1.6 财务（`gl_*` / `fin_*` / `fa_*`，13 张）

| 表名 | 表注释 | 实测行数 | 存活/软删 | 分类 | 处置 |
|---|---|---|---|---|---|
| `fa_asset` | 固定资产卡片 | 0 | — | 财务 | **全删** |
| `fa_asset_category` | 固定资产类别(折旧政策) | 3 | — | 财务（字典） | **保留** |
| `fa_depreciation` | 固定资产折旧记录 | 0 | — | 财务 | **全删** |
| `fa_disposal` | 固定资产处置记录 | 0 | — | 财务 | **全删** |
| `fin_close_log` | 期末关账执行日志 | 22 | — | 财务 | **全删** |
| `fin_receipt` | 收付款单(AR收款/AP付款合一) | 3 | — | 财务 | **全删** ⚠️ 已有 2 条 `client_id` 悬空 |
| `fin_settlement` | 应收应付核销记录 | 2 | — | 财务 | **全删** |
| `gl_account` | 会计科目表 | 31 | — | 财务（字典） | **保留** |
| `gl_account_balance` | 科目余额表(试算/报表基座) | 16 | — | 财务 | **全删** |
| `gl_currency_rate` | 币种汇率 | 0 | — | 财务 | **全删** |
| `gl_period` | 会计期间 | 24 | — | 财务（字典） | **保留** |
| `gl_voucher` | 记账凭证头 | 9 | — | 财务 | **全删** |
| `gl_voucher_line` | 记账凭证明细行 | 20 | — | 财务 | **全删** |

### 1.7 排班考勤（`sch_*`，14 张）

| 表名 | 表注释 | 实测行数 | 存活/软删 | 分类 | 处置 | 理由 |
|---|---|---|---|---|---|---|
| `sch_attendance` | 考勤记录表 | 14 | — | 排班考勤 | **全删** | 业务流水 |
| `sch_employee_schedule` | 员工排班明细表 | 416 | — | 排班考勤 | **全删** | 全库最大表 |
| `sch_holiday` | 节假日设置表 | 3 | — | 排班考勤（字典） | **保留** | 法定节假日日历，年度基础数据，无任何引用 |
| `sch_leave_apply` | 考勤请假申请表 | 0 | — | 排班考勤 | **全删** | |
| `sch_outdoor_punch` | 外勤打卡记录表 | 0 | — | 排班考勤 | **全删** | |
| `sch_plan` | 排班计划表 | 4 | 3 / 1 | 排班考勤 | **全删** | ⚠️ 被 `sch_shift` 引用 |
| `sch_plan_team` | 计划班组关联表 | 6 | — | 排班考勤 | **全删** | |
| `sch_punch_rule` | 打卡规则表 | 6 | — | 排班考勤 | **全删** | 挂在 `sch_shift` 上，随班次一起删 |
| `sch_shift` | 班次定义表 | 9 | — | 排班考勤 | **全删** | ⚠️ **非字典**：`plan_id`→`sch_plan`，5/5 活引用（§4.5） |
| `sch_supplement_apply` | 考勤补卡申请表 | 0 | — | 排班考勤 | **全删** | |
| `sch_team` | 班组设置表 | 14 | 8 / 6 | 排班考勤 | **全删** | 组织数据，可重建 |
| `sch_team_member` | 班组成员表 | 32 | 19 / 13 | 排班考勤 | **全删** | |
| `sch_team_schedule` | 班组排班（模板）表 | 24 | — | 排班考勤 | **全删** | |
| `sch_worker` | 生产工人档案表 | 37 | — | 排班考勤 | **全删** | 工人档案随业务一起清，可由人事重导 |

### 1.8 其余（报表 / 委外，2 张）

| 表名 | 表注释 | 实测行数 | 存活/软删 | 分类 | 处置 |
|---|---|---|---|---|---|
| `report_item` | 报表项目-科目映射 | 26 | — | 报表 | **保留**（用 `account_code_match` 字符串匹配，**不是外键**，与 `gl_account` 无硬绑定） |
| `erp_subcontract_doc` | 委外工程文件 | 0 | — | 业务单据 | **全删** |

> 说明：`po_requisition` / `po_requisition_detail` 已计入 §1.3；`erp_subcontract_doc` 计入本表。全库 125 张已全部覆盖。

### 1.9 处置汇总

| 处置 | 表数 | 说明 |
|---|---|---|
| **保留** | **8** | `bd_category_attribute`、`bd_material_category`、`bd_unit_of_measure`、`fa_asset_category`、`gl_account`、`gl_period`、`report_item`、`sch_holiday` |
| **重置编码** | **2** | `bd_serial_number_rule`、`wm_barcode_config` |
| **部分删** | **4** | `wm_warehouse`、`wm_storage_area`、`wm_storage_location`、`wm_material_stock`（3D 保留） |
| **全删** | **111** | 其余全部 |
| 合计 | **125** | ✅ |

**空表（实测 0 行）共 38 张**：`bd_category_attribute`、`bd_material_attribute_value`、`erp_subcontract_doc`、`fa_asset`、`fa_depreciation`、`fa_disposal`、`gl_currency_rate`、`po_requisition`、`po_requisition_detail`、`prd_capacity_calendar`、`prd_rework_order`、`prd_subcontract_bom_line`、`prd_subcontract_quote`、`pu_contract`、`pu_contract_line`、`pu_invoice`、`pu_invoice_line`、`sch_leave_apply`、`sch_outdoor_punch`、`sch_supplement_apply`、`so_contract`、`so_contract_line`、`so_price_list`、`so_price_list_line`、`so_return`、`so_return_line`、`wm_material_handover`、`wm_material_handover_line`、`wm_material_return`、`wm_material_return_line`、`wm_misc_issue`、`wm_misc_issue_line`、`wm_misc_recpt`、`wm_misc_recpt_line`、`wm_sn`、`wm_subcontract_issue`、`wm_subcontract_issue_line`、`wm_subcontract_receive`。

---

## 2. 依赖关系与删除批次

### 2.1 前提：数据库层没有任何保护

```sql
SELECT 'FKEYS_IN_ry-erp' chk, COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_TYPE='FOREIGN KEY' AND TABLE_SCHEMA='ry-erp';
```
```
FKEYS_IN_ry-erp     0
FKEYS_IN_ry-mes     0
FKEYS_IN_ry-iot     0
FKEYS_IN_ry-vue    46
```

再有跨库外键检查：

```sql
SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE
  WHERE REFERENCED_TABLE_SCHEMA IS NOT NULL
    AND CONSTRAINT_SCHEMA <> REFERENCED_TABLE_SCHEMA;
```
```
0
```

**结论**：`ry-erp` 无任何外键，跨库无任何外键；`ry-vue` 的 46 个外键全部指向自身库内（Flowable 引擎自带）。**删除顺序完全靠业务语义手工维护。**

### 2.2 引用关系推导方法（实测，非猜测）

用「包含依赖发现（Inclusion Dependency Discovery）」自动推导：对每个子表列 `C`、每个候选父表 `P`（`P` 的主键列名恰为 `C`），实测

```sql
SELECT COUNT(*) FROM child WHERE C NOT IN (SELECT P.pk FROM P);
```

若子表该列全部取值都能在父表主键中找到，即判定为真实引用。共探测 261 对，**验证出 155 条真实引用边**。样例证据：

```sql
SELECT 'bd_bom_detail' child, 'bom_id' col, 'bd_bom' parent,
       (SELECT COUNT(*) FROM bd_bom_detail WHERE bom_id IS NOT NULL AND bom_id<>0) filled,
       (SELECT COUNT(*) FROM bd_bom_detail x WHERE x.bom_id<>0
          AND x.bom_id IN (SELECT bom_id FROM bd_bom)) matched;
```
```
child           col      parent    filled  matched
bd_bom_detail   bom_id   bd_bom    72      72      -- 完全命中 → 真实引用
```

**关键推导结果（父表 ← 子表）：**

| 父表（主表） | 子表（必须先删） |
|---|---|
| `wm_warehouse` | `wm_storage_area`、`wm_storage_location`、`wm_material_stock`、`wm_material_stock_tx`、`wm_batch`、`wm_stock_taking_plan`、`wm_production_issue(_line)`、`wm_production_recpt`、`wm_material_receive(_detail/_line)`、`wm_sales_issue`、`wm_picking_rule`、`wm_putaway_rule`、`so_sales_order_line` |
| `wm_storage_area` | `wm_storage_location`、`wm_material_stock`、`wm_material_receive(_detail/_line)`、`wm_stock_taking_plan` |
| `wm_storage_location` | `wm_material_stock`、`wm_material_stock_tx`、`wm_material_receive(_detail/_line)`、`wm_stock_taking_line`、`wm_production_issue_line`、`wm_production_recpt`、`wm_sales_issue_line` |
| `wm_material_stock` | `wm_barcode`、`wm_stock_taking_line` |
| `wm_batch` | `wm_material_stock`、`wm_material_stock_tx`、`wm_barcode`、`wm_material_receive_detail`、`wm_production_issue_line`、`wm_production_recpt`、`wm_sales_issue_line`、`wm_stock_taking_line` |
| `wm_arrival_notice` | `wm_arrival_notice_line`、`wm_material_receive` |
| `wm_material_receive` | `wm_material_receive_line`、`wm_material_receive_detail` |
| `wm_material_receive_line` | `wm_material_receive_detail` |
| `wm_stock_taking_plan` | `wm_stock_taking_line` |
| `wm_transfer` | `wm_transfer_line` |
| `wm_production_issue` | `wm_production_issue_line` |
| `wm_sales_issue` | `wm_sales_issue_line` |
| `bd_bom` | `bd_bom_detail`、`prd_material_requirement`、`prd_production_plan`、`prd_work_order`、`bd_bom_alternative` |
| `bd_material` | `bd_material_vendor`、`bd_routing`、`prd_material_requirement(_detail)`、`prd_work_order`、`prd_work_report`、`so_sales_order_line`、`so_invoice_line`、`so_quotation_line`、`pu_purchase_order_line`、`pu_purchase_requisition_line`、`wm_batch`、`wm_barcode`、`wm_arrival_notice_line`、`wm_material_stock`、`wm_material_receive(_line/_detail)`、`wm_production_issue_line`、`wm_production_recpt`、`wm_sales_issue_line`、`wm_stock_alert`、`wm_stock_reconciliation`、`wm_stock_taking_line`、`wm_transfer_line`（共 23 张） |
| `bd_routing` | `bd_bom`、`bd_routing_step` |
| `bd_routing_step` | `prd_schedule_task` |
| `bd_process` | `bd_routing_step`、`prd_work_report` |
| `bd_workstation` | `bd_process`、`bd_routing_step`、`prd_work_report` |
| `bd_workshop` | `bd_workstation`、`sch_team`、`sch_worker`、`prd_work_order`、`prd_work_report` |
| `bd_client` | `bd_client_credit`、`so_customer_order`、`so_credit_usage`、`so_invoice`、`so_quotation`、`so_sales_order_header`、`fin_receipt`、`wm_sales_issue`、`prd_work_order` |
| `bd_vendor` | `bd_material_vendor`、`pu_purchase_order`、`pu_purchase_return`、`pu_vendor_evaluation`、`prd_subcontract`、`wm_arrival_notice`、`wm_material_receive`、`wm_material_stock` |
| `so_sales_order_header` | `so_sales_order_line`、`so_credit_usage`、`so_invoice`、`wm_sales_issue` |
| `so_customer_order` | `so_sales_order_header` |
| `so_quotation` | `so_quotation_line` |
| `so_invoice` | `so_invoice_line`、`fin_settlement` |
| `fin_receipt` | `fin_settlement` |
| `pu_purchase_requisition` | `pu_purchase_order`、`pu_purchase_requisition_line` |
| `pu_purchase_order` | `pu_purchase_order_line`、`pu_purchase_return` |
| `pu_purchase_return` | `pu_purchase_return_line` |
| `prd_production_plan` | `prd_work_order`、`prd_material_requirement` |
| `prd_work_order` | `prd_work_order_resource`、`prd_work_report`、`prd_schedule_task`、`wm_production_issue`、`wm_production_recpt`、`wm_material_stock` |
| `prd_material_requirement` | `prd_material_requirement_detail` |
| `prd_schedule_plan` | `prd_schedule_task` |
| `sch_plan` | `sch_plan_team`、`sch_team_schedule`、`sch_employee_schedule`、`sch_shift` |
| `sch_shift` | `sch_punch_rule`、`sch_employee_schedule`、`sch_team_schedule` |
| `sch_team` | `sch_team_member`、`sch_plan_team`、`sch_team_schedule`、`prd_work_order`、`prd_work_report` |
| `sch_worker` | `sch_team_member`、`sch_attendance`、`sch_employee_schedule`、`prd_work_report` |
| `sch_employee_schedule` | `sch_attendance` |
| `gl_period` | `gl_voucher`、`gl_account_balance`、`fin_close_log` |
| `gl_voucher` | `gl_voucher_line` |
| `gl_account` | `gl_voucher_line`、`gl_account_balance` |

### 2.3 方向原则（回应「谁先于谁删」）

- **子表必须先删，主表后删**：数据库无外键，无法级联，也无人替你拦。若先删主表（如 `bd_material`），子表（如 `wm_material_stock`）立刻变成孤儿，且**事后无法自证哪些行是孤儿**。
- **唯一例外：跨库引用要在删 `ry-erp` 主表「之前」单独处理**（见批次 1）。MES 的表不在本次删除范围内，所以它们的引用必须**先解绑**，而不是"先删子表"。
- **`wm_material_receive_detail` 必须先于 `wm_material_receive_line` 删**（实测 `detail.line_id` 命中 `line.line_id` 23/23，是真实父子，不是并列）。
- **`fin_settlement` 必须在 `so_invoice` 和 `fin_receipt` 之后删**（它对两者都有引用）。
- **`pu_purchase_*` 内部顺序**：`pu_purchase_return` → `pu_purchase_order` → `pu_purchase_requisition`。

### 2.4 分批删除顺序（7 批）

| 批次 | 名称 | 表数 | 目标 |
|---|---|---|---|
| **B0** | 备份与前置校验 | 0 | 可回滚基线 |
| **B1** | 跨库解绑（`ry-mes`） | 0（改 MES，涉及 28 张 MES 表） | 断掉 43 处 ERP 引用 |
| **B2** | 叶子层（明细/行/事务） | 45（全删） | 无子表的末端 |
| **B3** | 单据头层 | 38（全删） | 明细的父项 |
| **B4** | 库存核心层 | 2（1 全删 + 1 部分删） | 含 3D 保留过滤 |
| **B5** | 业务主数据与订单层 | 27（全删） | 被大量引用的主数据 |
| **B6** | 仓储结构层 | 3（部分删） | 含 3D 保留过滤 |
| **B7** | 计数器重置与终验 | 2（重置编码） | 编码序列归零 |

> **计数校验**：全删 111 = B2(45) + B3(38) + B4 中 `wm_batch`(1) + B5(27)；部分删 4 = B4 中 `wm_material_stock`(1) + B6(3)；保留 8 + 重置 2。合计 **125** ✅

**B2 —— 叶子层（45 张，全删）**

`bd_bom_detail`、`bd_bom_alternative`、`bd_material_attribute_value`、`gl_voucher_line`、`prd_material_requirement_detail`、`prd_subcontract_bom_line`、`prd_subcontract_quote`、`prd_schedule_task`、`prd_work_order_resource`、`prd_work_report`、`erp_subcontract_doc`、`fa_depreciation`、`fa_disposal`、`so_credit_usage`、`so_contract_line`、`so_invoice_line`、`so_price_list_line`、`so_quotation_line`、`so_return_line`、`so_sales_order_line`、`pu_contract_line`、`pu_invoice_line`、`pu_purchase_order_line`、`pu_purchase_requisition_line`、`pu_purchase_return_line`、`po_requisition_detail`、`wm_arrival_notice_line`、`wm_barcode`、`wm_material_handover_line`、`wm_material_receive_detail`、`wm_material_receive_line`、`wm_material_return_line`、`wm_material_stock_tx`、`wm_misc_issue_line`、`wm_misc_recpt_line`、`wm_production_issue_line`、`wm_sales_issue_line`、`wm_stock_taking_line`、`wm_subcontract_issue_line`、`wm_transfer_line`、`sch_employee_schedule`、`sch_plan_team`、`sch_team_member`、`sch_team_schedule`、`wm_sn`

> ⚠️ 批内顺序：`wm_material_receive_detail` 必须排在 `wm_material_receive_line` 之前。

**B3 —— 单据头层（38 张，全删）**

`wm_arrival_notice`、`wm_material_receive`、`wm_material_return`、`wm_stock_taking_plan`、`wm_transfer`、`wm_production_issue`、`wm_production_recpt`、`wm_sales_issue`、`wm_misc_issue`、`wm_misc_recpt`、`wm_material_handover`、`wm_subcontract_issue`、`wm_subcontract_receive`、`wm_stock_alert`、`wm_stock_reconciliation`、`wm_picking_rule`、`wm_putaway_rule`、`so_contract`、`so_invoice`、`so_price_list`、`so_quotation`、`so_return`、`pu_contract`、`pu_invoice`、`pu_vendor_evaluation`、`pu_purchase_return`、`pu_purchase_order`、`pu_purchase_requisition`、`po_requisition`、`prd_schedule_plan`、`prd_material_requirement`、`prd_subcontract`、`prd_rework_order`、`prd_capacity_calendar`、`gl_voucher`、`fin_receipt`、`fin_settlement`、`fin_close_log`

> ⚠️ 批内顺序：`pu_purchase_return` → `pu_purchase_order` → `pu_purchase_requisition`；`fin_settlement` 排在 `so_invoice`、`fin_receipt` 之后。

**B4 —— 库存核心层（2 张）**

`wm_material_stock`（**部分删**）、`wm_batch`（全删）

**B5 —— 业务主数据与订单层（27 张，全删）**

`prd_work_order`、`prd_production_plan`、`so_sales_order_header`、`so_customer_order`、`sch_attendance`、`sch_leave_apply`、`sch_outdoor_punch`、`sch_supplement_apply`、`sch_shift`、`sch_punch_rule`、`sch_plan`、`sch_team`、`sch_worker`、`gl_account_balance`、`gl_currency_rate`、`fa_asset`、`bd_material`、`bd_material_vendor`、`bd_bom`、`bd_routing_step`、`bd_routing`、`bd_process`、`bd_workstation`、`bd_workshop`、`bd_client_credit`、`bd_client`、`bd_vendor`

> 批内顺序要点：`prd_work_order` 先于 `prd_production_plan`；`so_sales_order_header` 先于 `so_customer_order`；排班族按 `sch_attendance` → `sch_shift`/`sch_punch_rule` → `sch_plan` → `sch_team`/`sch_worker` 顺序；`bd_material` / `bd_bom` 等主数据放在本批最后。

**B6 —— 仓储结构层（3 张，部分删，保留 3D）**

`wm_storage_location`、`wm_storage_area`、`wm_warehouse`

**B7 —— 计数器重置（2 张）**

`bd_serial_number_rule`、`wm_barcode_config`

---

## 3. 删除方式建议

### 3.1 三种方式的取舍

| 方式 | 可回滚 | 速度 | 自增计数器 | 适用 |
|---|---|---|---|---|
| `DELETE` | ✅（事务内） | 慢（逐行写 undo/redo） | **不重置**，从当前值继续 | 部分删、需要回滚的批次 |
| `TRUNCATE` | ❌（隐式 DDL，自动提交） | 快（重建空表） | **重置为 1** | 全删且该表无自增、或自增重置无害 |
| `DROP` | ❌ | 最快 | 表没了 | **本任务不推荐** |

### 3.2 自增键实测：只有 10 张表有 `AUTO_INCREMENT`

```sql
SELECT TABLE_NAME, AUTO_INCREMENT FROM information_schema.TABLES
 WHERE TABLE_SCHEMA='ry-erp' AND AUTO_INCREMENT IS NOT NULL;
```
```
prd_capacity_calendar         1
pu_purchase_order             2080272156951982084
pu_purchase_order_line        2080272463601741827
pu_purchase_requisition       2080271642264743938
pu_purchase_requisition_line  2080272041260494851
pu_purchase_return            8800401
pu_purchase_return_line       8800502
wm_barcode                    2099000000000000612
wm_barcode_config             2062021060516102147
wm_production_recpt           2089705590124199939
```

**这一点非常重要**：`ry-erp` 的主键策略是 MyBatis-Plus `ASSIGN_ID`（雪花算法），**115/125 张表的主键根本没有自增计数器**。对它们而言 `TRUNCATE` 与 `DELETE` 在"计数器"层面**没有区别**（没有计数器可重置），所以 `TRUNCATE` 的经典副作用在这里几乎不存在。

**逐表建议：**

| 表 | 建议 | 理由 |
|---|---|---|
| 上述 10 张自增表（除 `wm_barcode_config`） | **`DELETE` 优先** | 重置自增会让新数据 ID 与历史备份冲突；`pu_purchase_order` 的计数器已到 `2.08e18`，重置后若从 1 开始，与旧数据 ID 段重叠风险高 |
| `wm_barcode_config` | **`DELETE` 不用** → 用 `UPDATE` 重置 `current_sequence` | 见 §4.6，规则本体必须保留 |
| `bd_serial_number_rule` | **`UPDATE`** | 同上 |
| 其余 113 张无自增表 | **`DELETE`（推荐）**，数据量小者也可 `TRUNCATE` | 全库最大表仅 416 行，`DELETE` 性能完全够；`DELETE` 留在事务里可回滚，安全性显著优于 `TRUNCATE` |
| ✅ 全部 125 张 | **不要 `DROP`** | 删表会丢失 schema，重建需重跑 `script/sql/ry-erp.sql`，且会连带丢失索引/注释；本任务只需要"清数据" |

> **强烈建议全程用 `DELETE`**：本库总数据量很小（125 张表合计约 2,800 行），`DELETE` 的开销可以忽略，而它换来的是**事务内可回滚**与**可分批校验**。`TRUNCATE` 的唯一收益是速度，收益为零、代价是非回滚。

### 3.3 软删 vs 硬删

**119/125 张表有 `del_flag`**，仅 6 张没有：`bd_routing_step`、`so_credit_usage`、`so_price_list`、`so_price_list_line`、`wm_material_return`、`wm_material_return_line`。

当前已有大量软删行（实测）：

| 表 | 存活 | 已软删 |
|---|---|---|
| `wm_material_receive_line` | 24 | 55 |
| `bd_bom_detail` | 32 | 40 |
| `wm_stock_reconciliation` | 8 | 32 |
| `pu_purchase_requisition_line` | 8 | 17 |
| `prd_work_order_resource` | 50 | 16 |
| `so_sales_order_line` | 12 | 13 |
| `sch_team_member` | 19 | 13 |
| `wm_material_receive` | 15 | 10 |
| （其余略，共 30 张表存在软删行） | | |

**建议**：本任务是"全量业务数据删除"，**用硬删 `DELETE`**。若上游只想让数据"不可见"，可用 `UPDATE ... SET del_flag='1'`，但那样 **3D 演示数据之外的 121 张表仍占空间，且唯一性约束/编码生成仍会受影响**，不满足"重置编码"目标。

---

## 4. 风险清单（全部实测）

### 4.1 外键：`ry-erp` 零外键，跨库零外键 ✅

见 §2.1。**风险不在数据库层，全在数据层。**

### 4.2 3D 演示数据范围（保留清单）

**自查证方法**：不放信文档，直接对全库所有 `*_id` / `*_code` 列做「值域扫描」，查找落在 3D 演示 ID 段（`9900000000`–`9900009999`）的行。

```sql
-- 自动生成：对 ry-erp 中 884 个 *_id / *_code 列逐一扫描
SELECT 'wm_material_stock.stock_id' AS ref, COUNT(*) AS c FROM `wm_material_stock` WHERE `stock_id` BETWEEN '9900000000' AND '9900009999'
UNION ALL SELECT 'wm_storage_area.area_id', COUNT(*) FROM `wm_storage_area` WHERE `area_id` BETWEEN '9900000000' AND '9900009999'
... -- 共 884 条
```

**非零命中只有 12 条，全部落在 4 张表：**

```
wm_material_stock.stock_id        55
wm_material_stock.material_id     55
wm_material_stock.warehouse_id    55
wm_material_stock.area_id         55
wm_material_stock.location_id     55
wm_storage_area.area_id            4
wm_storage_area.warehouse_id       4
wm_storage_location.location_id  104
wm_storage_location.warehouse_id 104
wm_storage_location.area_id      104
wm_warehouse.warehouse_id          1
```

**跨库扫描同样为零**（对 `ry-mes`(774 列) / `ry-iot` / `ry-vue` 做同一 ID 段扫描）：

```
=== NON-ZERO hits in ry-mes/ry-iot/ry-vue for demo ID range ===
(none - 3D demo is fully isolated to ry-erp)
```

**逐表实测确认：**

```sql
SELECT 'wm_warehouse' t, COUNT(*) n FROM wm_warehouse WHERE warehouse_id=9900000001
UNION ALL SELECT 'wm_storage_area', COUNT(*) FROM wm_storage_area WHERE warehouse_id=9900000001
UNION ALL SELECT 'wm_storage_location', COUNT(*) FROM wm_storage_location WHERE warehouse_id=9900000001
UNION ALL SELECT 'wm_material_stock', COUNT(*) FROM wm_material_stock WHERE warehouse_id=9900000001;
```
```
wm_warehouse          1
wm_storage_area       4
wm_storage_location 104
wm_material_stock    55
```

#### ✅ 保留清单（3D 演示，共 164 行 / 4 张表）

| 表 | 保留行数 | 保留条件 | 内容 |
|---|---|---|---|
| `wm_warehouse` | 1 | `warehouse_id = 9900000001` | `WH-3D-DEMO` / 3D示例仓库 |
| `wm_storage_area` | 4 | `warehouse_id = 9900000001` | `area_id` 9900000011–9900000014（原料货架区/成品货架区/拣选网格区/散货堆放区） |
| `wm_storage_location` | 104 | `warehouse_id = 9900000001` | `location_id` 9900000101–9900000160(A区60)、9900000201–9900000230(B区30)、9900000301–9900000310(C区10)、9900000401–9900000404(D区4) |
| `wm_material_stock` | 55 | `warehouse_id = 9900000001` | `stock_id` 9900003001–9900003055 |

**重要细节**：3D 演示的 6 个物料（`DEMO-M001`~`M003`、`DEMO-P001`、`DEMO-P002`、`DEMO-F001`，`material_id` 9900000001–9900000006）**在 `bd_material` 中根本不存在**：

```sql
SELECT COUNT(*) FROM bd_material WHERE material_id BETWEEN 9900000001 AND 9900000099;
```
```
0
```

这是**设计如此**，不是缺陷——演示库存只按 `material_id/material_code` 做字符串冗余，不依赖物料主数据。**因此清空 `bd_material` 不会破坏 3D 演示**，反之亦然。保留的 55 行演示库存的 `batch_id`/`wo_id`/`vendor_id`/`category_id` 全部为 0/NULL（实测），**不与任何待删表产生引用**：

```sql
SELECT COUNT(*) FROM wm_material_stock WHERE warehouse_id=9900000001 AND batch_id<>0;  -- 0
SELECT COUNT(*) FROM wm_material_stock WHERE warehouse_id=9900000001 AND wo_id<>0;     -- 0
SELECT COUNT(*) FROM wm_material_stock WHERE warehouse_id=9900000001 AND vendor_id<>0; -- 0
SELECT COUNT(*) FROM wm_material_stock WHERE warehouse_id=9900000001 AND category_id<>0; -- 0
```

### 4.3 🔴 最大风险：`ry-mes` → `ry-erp` 的 43 处活引用

**探测方法**：对 `ry-mes`(68 表) / `ry-iot`(3 表) / `ry-vue`(194 表) 中所有「列名与 `ry-erp` 主键列名相同」的整型列，实测其取值有多少能在 `ry-erp` 父表中找到：

```sql
SELECT 'ry-mes.mes_mbom.bom_id' AS ref, 'bd_bom.bom_id' AS parent,
       (SELECT COUNT(*) FROM `ry-mes`.`mes_mbom` WHERE `bom_id` IS NOT NULL AND `bom_id`<>0) AS filled,
       (SELECT COUNT(*) FROM `ry-mes`.`mes_mbom` x WHERE x.`bom_id` IN (SELECT `bom_id` FROM `ry-erp`.`bd_bom`)) AS live_refs;
```

**命中的活引用（`live_refs > 0`）：**

| MES 侧列 | → ERP 父表 | filled | live_refs |
|---|---|---|---|
| `mes_mbom_line.bom_id` | `bd_bom` | 23 | **23** |
| `pro_work_order_bom_line.bom_id` | `bd_bom` | 44 | **44** |
| `mes_mbom.bom_id` | `bd_bom` | 5 | **5** |
| `pro_work_order.bom_id` | `bd_bom` | 17 | 16 |
| `pro_work_order_bom_line.bom_detail_id` | `bd_bom_detail` | 26 | 25 |
| `mes_mbom_line.bom_detail_id` | `bd_bom_detail` | 13 | **13** |
| `mes_mbom.routing_id` | `bd_routing` | 5 | **5** |
| `pro_work_order.routing_id` | `bd_routing` | 17 | 16 |
| `pro_sn.material_id` | `bd_material` | 77 | **77** |
| `pro_flow_card.material_id` | `bd_material` | 25 | **25** |
| `ncr_order.material_id` | `bd_material` | 11 | **11** |
| `pro_work_order.material_id` | `bd_material` | 18 | 17 |
| `mes_firmware.material_id` | `bd_material` | 6 | **6** |
| `mes_material_load.material_id` | `bd_material` | 5 | **5** |
| `qc_iqc.material_id` | `bd_material` | 6 | **6** |
| `spc_control_chart.material_id` | `bd_material` | 3 | **3** |
| `lt_lot.material_id` | `bd_material` | 2 | **2** |
| `qc_ipqc.material_id` / `qc_oqc.material_id` | `bd_material` | 2 / 2 | **2 / 2** |
| `mes_release_package.material_id` | `bd_material` | 1 | **1** |
| `mes_test_item.material_id` | `bd_material` | 1 | **1** |
| `mes_content_file.material_id` | `bd_material` | 1 | **1** |
| `lt_wo_consumption.material_id` | `bd_material` | 1 | **1** |
| `qc_inspect_template.material_id` | `bd_material` | 1 | **1** |
| `pro_work_order_bom_line.child_material_id` | `bd_material` | 46 | **46** |
| `pro_flow_card_step.process_id` | `bd_process` | 64 | **64** |
| `pro_work_order_step.process_id` | `bd_process` | 53 | **53** |
| `pro_work_order_bom_line.process_id` | `bd_process` | 45 | **45** |
| `pro_dispatch.process_id` | `bd_process` | 35 | **35** |
| `mes_mbom_line.process_id` | `bd_process` | 22 | **22** |
| `mes_flash_record.process_id` | `bd_process` | 12 | **12** |
| `sop_process_binding.process_id` | `bd_process` | 10 | **10** |
| `dc_collection_point.process_id` | `bd_process` | 5 | **5** |
| `spc_control_chart.process_id` | `bd_process` | 3 | **3** |
| `qc_ipqc.process_id` | `bd_process` | 2 | **2** |
| `mes_release_pkg_firmware.process_id` | `bd_process` | 1 | **1** |
| `pro_work_order_bom_line.workstation_id` | `bd_workstation` | 45 | **45** |
| `mes_mbom_line.workstation_id` | `bd_workstation` | 23 | **23** |
| `tg_usage_record.workstation_id` | `bd_workstation` | 1 | **1** |
| `pro_work_order.workshop_id` | `bd_workshop` | 10 | **10** |
| `tg_tooling.workshop_id` | `bd_workshop` | 1 | **1** |
| `mes_release_package.client_id` | `bd_client` | 1 | **1** |
| `qc_iqc.vendor_id` | `bd_vendor` | 2 | **2** |
| `pro_work_order.erp_wo_id` | `prd_work_order` | 10 | **10** |
| `ry-vue.wf_category.category_id` | `bd_material_category` | 5 | 1 ⚠️ |

> ⚠️ 最后一条 `ry-vue.wf_category.category_id` 是 **Flowable 工作流分类**的 ID，与 `bd_material_category.category_id` 只是**数值巧合**（1 行），**不是真实引用**，无需处理。

**结论：删 `ry-erp` 的 `bd_material` / `bd_bom` / `bd_process` / `bd_workstation` / `bd_workshop` / `bd_client` / `bd_vendor` / `bd_bom_detail` / `bd_routing` / `prd_work_order`，会让 MES 侧立即产生孤儿数据。** 必须先执行批次 B1。

### 4.4 🔴 已存在的跨库孤儿（1 条，删除前先处理）

```sql
SELECT mes_wo_id, wo_no, material_id, material_code FROM `ry-mes`.pro_work_order
 WHERE material_id<>0 AND material_id NOT IN (SELECT material_id FROM `ry-erp`.bd_material);
```
```
+---------------------+------------------+---------------------+---------------+
| mes_wo_id           | wo_no            | material_id         | material_code |
+---------------------+------------------+---------------------+---------------+
| 2089742340792041473 | SCGD202608180002 | 2089376872126230500 | IF20260817011 |
+---------------------+------------------+---------------------+---------------+
```

该 MES 工单状态为 `CANCELLED`，其 `material_id` 与 `bom_id`、`routing_id` 均为**同批次缺失**（`2089376872126230500` / `...400` / `...200`，比正常雪花值少 29），推测为历史上一次失败的演示构造残留。**它已经是孤儿，与本次删除无关，但说明 MES 侧确实无人维护引用完整性。**

其余 3 处 `filled ≠ live` 差异（`pro_work_order.bom_id` 17/16、`routing_id` 17/16、`pro_work_order_bom_line.bom_detail_id` 26/25）**由同一条工单造成**，无需单独处理。

### 4.5 🟠 保留集必须闭包：3 张"看起来像配置"的表其实是业务数据

若只按表名判断，容易把下面这些放进保留集，从而制造孤儿：

**(1) `wm_picking_rule` / `wm_putaway_rule` —— 必须一起删**

```sql
SELECT 'wm_picking_rule' t, rule_id id, warehouse_id FROM wm_picking_rule
UNION ALL SELECT 'wm_putaway_rule', rule_id, warehouse_id FROM wm_putaway_rule;
```
```
wm_picking_rule   1  2
wm_picking_rule   2  2
wm_picking_rule   3  3
wm_putaway_rule   1  1
wm_putaway_rule   2  3
wm_putaway_rule   3  1
```

这 6 行**全部挂在待删仓库（1/2/3）上**。若保留它们，仓库删除后立即产生 6 条孤儿。它们是"仓库级作业规则"，不是全局字典，**应随仓库一起删**。

**(2) `sch_shift` / `sch_punch_rule` —— 必须一起删**

```sql
SELECT shift_id, shift_code, shift_name, plan_id FROM sch_shift ORDER BY shift_id;
```
```
1                   PBJH2025001-1  早班        1
2                   PBJH2025001-2  中班        1
3                   PBJH2025001-3  晚班        1
4                   PBJH2025002-1  长白班      2
30001               TEST-DAY       测试白班    30001
30002               TEST-SWING     测试中班    30001
30003               TEST-NIGHT     测试夜班    30001
2091200579727568897 PBJH2026TB01-1 早班        2091200579463327746
2091200580490932225 PBJH2026TB01-2 晚班        2091200579463327746
```

`sch_shift.plan_id` 指向 `sch_plan.plan_id`，且 `shift_code` 是**按排班计划号派生**的（`PBJH2025001-1`），说明班次是**业务数据而非字典**。实测 `sch_punch_rule.shift_id → sch_shift` 5/5 活引用。**两者都必须删**。

> 教训：**判定"字典 vs 业务"不能看表名，要看它有没有指向业务表的外键、以及编码是否由业务单据派生。**

**(3) 保留集闭包已实测确认**：保留的 8 张表中，只有 `bd_material_category.parent_id`（自引用树 51/51）和 `report_item` 的 `account_code_match`。`report_item` 用的是**字符串匹配科目编码**（`account_code_match`），**不是外键**，与 `gl_account` 无硬绑定：

```sql
-- report_item 的列清单里没有 account_id
item_id / report_type / item_code / item_name / sort_order / parent_item_id /
is_summary / account_code_match / sign / status / version / ... / del_flag
```

`sch_holiday` 经列清单确认**不含任何 `*_id` 引用列**。**保留集不指向任何待删表，闭包成立。** ✅

### 4.6 🟠 编码/序列相关表的副作用

**(a) `bd_serial_number_rule`（3 条规则）**

```sql
SELECT rule_id, rule_code, rule_name, rule_type, prefix, seq_cycle, current_seq FROM bd_serial_number_rule;
```
```
1  SN-FG-001    成品序列号规则  PRODUCT_SN  SN  DAY    current_seq=1
2  SN-BATCH-001 批次号规则      BATCH_NO    B      MONTH  current_seq=1
3  SN-BC-001    条码规则        BARCODE     BC     NONE   current_seq=1
```

- 删除后副作用：**所有 SN / 批次号 / 条码生成功能停摆**（Service 层依赖规则表取号）。
- 当前 `current_seq` 全为 1，**没有历史递增包袱**。
- 处置：**保留规则本体，仅把 `current_seq` 重置为 1**。若连同规则一起删，必须重跑 `script/sql/ry-erp.sql` 才能恢复。

**(b) `wm_barcode_config`（2 条配置）**

```sql
SELECT config_id, config_code, biz_type, prefix, current_sequence FROM wm_barcode_config;
```
```
900001              WM_RECEIVE_DEFAULT   RECEIVE_IN  RC  current_sequence=23
2062021060516102146 SO_SALESOUT_DEFAULT  SALES_OUT   SO  current_sequence=0
```

- `WM_RECEIVE_DEFAULT` 的 `current_sequence=23`，而 `wm_barcode` 恰好有 24 行（若含 1 行起始偏移则完全对应）。
- 删除后副作用：收货入库/销售出库**无法再自动生成条码**。
- 处置：**保留配置，把 `current_sequence` 重置为 0**。⚠️ 若同时清空了 `wm_barcode`（全删），重置是**必须的**，否则新条码会从 23 继续，与历史已删除的条码号段割裂。

**(c) `wm_barcode`（24 行条码实例）**

- **必须与 `wm_barcode_config` 的计数器重置同批处理**。
- 实测 `wm_barcode.stock_id → wm_material_stock.stock_id` 仅 **5/23 命中**，即 **18 行条码已经指向不存在的库存**（既有脏数据）。删除 `wm_material_stock` 前先删 `wm_barcode`，正好清理这批孤儿。

### 4.7 🟡 其他已存在的脏数据（删除时会被顺带清掉，列出备查）

| 现象 | 实测 | 影响 |
|---|---|---|
| `wm_material_stock.material_id` 悬空 | filled=92，live=36 → **56 行悬空**（55 行是 3D 演示的"设计性悬空"，1 行是 `8803001 P2-MAT-001`） | `8803001` 属真孤儿，可一并清 |
| `wm_material_stock.vendor_id` | filled=18，live=10 → 8 行悬空 | — |
| `wm_material_receive.vendor_id` | filled=24，live=8 → **16 行悬空** | — |
| `fin_receipt.client_id` | filled=3，live=1 → 2 行悬空 | — |
| `so_sales_order_line.material_id` | filled=25，live=23 → 2 行悬空 | — |
| `so_invoice.client_id` | filled=6，live=4 → 2 行悬空 | — |
| `so_sales_order_header.co_id` | filled=16，live=15 → 1 行悬空 | — |
| `pu_purchase_order_line.material_id` | filled=9，live=7 → 2 行悬空 | — |
| `prd_production_plan.bom_id` | filled=16，live=15 → 1 行悬空 | — |
| `wm_arrival_notice.vendor_id` | filled=8，live=6 → 2 行悬空 | — |
| `wm_stock_taking_line.material_id` | filled=16，live=9 → 7 行悬空 | — |
| `sch_employee_schedule.shift_id` | filled=416，live=362 → **54 行悬空** | 最大一处 |
| `wm_storage_location` 12 行无仓库归属 | `warehouse_id IS NULL`（`location_id` 1–12，`area_id` 1–5） | 删除时必须用 `IS NULL OR <>` 才能覆盖 |
| `wm_storage_location` 12 行挂在非演示仓库 | `warehouse_id` ∈ {1,2,3,2091200583779266562} | — |
| 表注释乱码 | `wm_material_return` / `wm_material_return_line` 注释为 `ç”Ÿäº§é€€æ–™å•`（实为"生产退料单"，UTF-8 被按 latin1 存储） | 建议顺手 `ALTER TABLE ... COMMENT` 修正（**本次不执行**） |
| 跨库空壳表 | `ry-vue` 中存在 `pu_contract`、`pu_contract_line`（均 0 行） | 与 `ry-erp` 同名，属历史遗留空壳，建议单独清理（**本次不执行**） |

### 4.8 删除对 MES / IoT 的孤儿影响总结

- **`ry-iot`：零影响。** 对 `iot_product` / `iot_device` / `iot_device_data` 全部列做过 ERP ID 段扫描与 ERP 实体列探测，**无任何引用**。
- **`ry-mes`：43 处活引用**（§4.3）。若不解绑直接删 ERP，会产生孤儿的具体表：
  `pro_work_order`、`pro_work_order_bom_line`、`pro_work_order_step`、`pro_flow_card`、`pro_flow_card_step`、`pro_dispatch`、`pro_sn`、`mes_mbom`、`mes_mbom_line`、`mes_firmware`、`mes_material_load`、`mes_release_package`、`mes_release_pkg_firmware`、`mes_test_item`、`mes_content_file`、`mes_flash_record`、`qc_iqc`、`qc_ipqc`、`qc_oqc`、`qc_inspect_template`、`spc_control_chart`、`sop_process_binding`、`dc_collection_point`、`lt_lot`、`lt_wo_consumption`、`ncr_order`、`tg_tooling`、`tg_usage_record`。
- **`ry-vue`：零影响**（`wf_category` 命中为巧合，见 §4.3）。

---

## 5. 安全删除执行清单

> **总原则**：每批执行前先跑校验、执行后再校验；**全程用 `DELETE` 而非 `TRUNCATE`**；**任何一批出现非预期行数立即 `ROLLBACK` 并停止**。
> 建议每批包在 `START TRANSACTION; ... COMMIT;` 中（`UPDATE`/`DELETE` 均支持事务；本库全为 InnoDB，实测 `ENGINE=InnoDB` × 125 ✅）。

### 批次 B0 —— 备份与前置校验

```sql
-- B0.1 环境自检
SELECT VERSION();                                   -- 预期 5.7.20-log
SELECT COUNT(*) FROM information_schema.TABLES
 WHERE TABLE_SCHEMA='ry-erp' AND TABLE_TYPE='BASE TABLE';   -- 预期 125

-- B0.2 确认无外键（预期 0，若非 0 必须重新评估顺序）
SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
 WHERE CONSTRAINT_TYPE='FOREIGN KEY' AND TABLE_SCHEMA='ry-erp';

-- B0.3 备份（在 mysql 客户端外执行）
-- mysqldump -uroot -proot --default-character-set=utf8mb4 --single-transaction \
--   ry-erp > E:\backup\ry-erp_before_wipe_YYYYMMDD.sql
-- mysqldump -uroot -proot --default-character-set=utf8mb4 --single-transaction \
--   ry-mes > E:\backup\ry-mes_before_wipe_YYYYMMDD.sql

-- B0.4 记录删除前基线（保存输出，用于 B7 比对）
SELECT 'wm_warehouse' t, COUNT(*) n FROM wm_warehouse
UNION ALL SELECT 'wm_storage_area', COUNT(*) FROM wm_storage_area
UNION ALL SELECT 'wm_storage_location', COUNT(*) FROM wm_storage_location
UNION ALL SELECT 'wm_material_stock', COUNT(*) FROM wm_material_stock;
-- 预期 5 / 12 / 128 / 92
```
**校验**：125 张表、0 外键、4 个基线数已抄录、dump 文件存在且 >0 字节。

---

### 批次 B1 —— 跨库解绑（`ry-mes`，★ 必做且最先做）

**策略二选一**：
- **策略 A（MES 数据也一并废弃）**：直接清 MES 业务表。简单，但超出本任务范围。
- **策略 B（推荐：仅解绑引用）**：把 MES 侧指向 ERP 的外键列置 `NULL`/`0`，保留 MES 单据本体。

```sql
-- 策略 B：解绑 43 处引用中的「主数据类」引用（BOM / 物料 / 工序 / 工位 / 车间 / 客户 / 供应商 / 工艺路线）
USE `ry-mes`;

UPDATE `mes_mbom`                 SET bom_id=NULL, routing_id=NULL;
UPDATE `mes_mbom_line`            SET bom_id=NULL, bom_detail_id=NULL, process_id=NULL, workstation_id=NULL;
UPDATE `pro_work_order`           SET bom_id=NULL, routing_id=NULL, workshop_id=NULL, erp_wo_id=NULL;
-- 注意：pro_work_order.material_id 若置 NULL，需先确认该列允许 NULL
UPDATE `pro_work_order_bom_line`  SET bom_id=NULL, bom_detail_id=NULL, process_id=NULL, workstation_id=NULL, child_material_id=NULL;
UPDATE `pro_work_order_step`      SET process_id=NULL;
UPDATE `pro_flow_card_step`       SET process_id=NULL;
UPDATE `pro_dispatch`             SET process_id=NULL;
UPDATE `pro_sn`                   SET material_id=NULL;
UPDATE `pro_flow_card`            SET material_id=NULL;
UPDATE `mes_firmware`             SET material_id=NULL;
UPDATE `mes_material_load`        SET material_id=NULL;
UPDATE `mes_release_package`      SET client_id=NULL, material_id=NULL;
UPDATE `mes_release_pkg_firmware` SET process_id=NULL;
UPDATE `mes_test_item`            SET material_id=NULL;
UPDATE `mes_content_file`         SET material_id=NULL;
UPDATE `mes_flash_record`         SET process_id=NULL;
UPDATE `qc_iqc`                   SET material_id=NULL, vendor_id=NULL;
UPDATE `qc_ipqc`                  SET material_id=NULL, process_id=NULL;
UPDATE `qc_oqc`                   SET material_id=NULL;
UPDATE `qc_inspect_template`      SET material_id=NULL;
UPDATE `spc_control_chart`        SET material_id=NULL, process_id=NULL;
UPDATE `sop_process_binding`      SET process_id=NULL;
UPDATE `dc_collection_point`      SET process_id=NULL;
UPDATE `lt_lot`                   SET material_id=NULL;
UPDATE `lt_wo_consumption`        SET material_id=NULL;
UPDATE `ncr_order`                SET material_id=NULL;
UPDATE `tg_tooling`               SET workshop_id=NULL;
UPDATE `tg_usage_record`          SET workstation_id=NULL;

-- 同时处理已存在的孤儿（§4.4）
UPDATE `pro_work_order` SET material_id=NULL WHERE mes_wo_id=2089742340792041473;
```

**校验（必须全部为 0）**：
```sql
SELECT 'mes_mbom',       COUNT(*) FROM `ry-mes`.mes_mbom                WHERE bom_id IN (SELECT bom_id FROM `ry-erp`.bd_bom)
UNION ALL SELECT 'mes_mbom_line',      COUNT(*) FROM `ry-mes`.mes_mbom_line`           WHERE bom_id IN (SELECT bom_id FROM `ry-erp`.bd_bom)
UNION ALL SELECT 'pro_wo_bom_line',    COUNT(*) FROM `ry-mes`.pro_work_order_bom_line` WHERE bom_id IN (SELECT bom_id FROM `ry-erp`.bd_bom)
UNION ALL SELECT 'pro_work_order_mat', COUNT(*) FROM `ry-mes`.pro_work_order`          WHERE material_id IN (SELECT material_id FROM `ry-erp`.bd_material)
UNION ALL SELECT 'pro_sn_mat',         COUNT(*) FROM `ry-mes`.pro_sn`                  WHERE material_id IN (SELECT material_id FROM `ry-erp`.bd_material)
UNION ALL SELECT 'process_all',        COUNT(*) FROM `ry-mes`.pro_flow_card_step`      WHERE process_id IN (SELECT process_id FROM `ry-erp`.bd_process)
UNION ALL SELECT 'workstation_all',    COUNT(*) FROM `ry-mes`.pro_work_order_bom_line` WHERE workstation_id IN (SELECT workstation_id FROM `ry-erp`.bd_workstation`)
UNION ALL SELECT 'workshop_all',       COUNT(*) FROM `ry-mes`.pro_work_order`          WHERE workshop_id IN (SELECT workshop_id FROM `ry-erp`.bd_workshop`);
```

> ⚠️ **置 NULL 前先确认列可空**：`SELECT COLUMN_NAME, IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='ry-mes' AND IS_NULLABLE='NO' AND COLUMN_NAME IN (...)`。若不可空，改为 `UPDATE` 为 `0`，或改用策略 A。

---

### 批次 B2 —— 叶子层（45 张）

```sql
USE `ry-erp`;
START TRANSACTION;

-- 批内顺序：receive_detail 先于 receive_line
DELETE FROM `wm_material_receive_detail`;
DELETE FROM `wm_material_receive_line`;

DELETE FROM `bd_bom_detail`;
DELETE FROM `bd_bom_alternative`;
DELETE FROM `bd_material_attribute_value`;
DELETE FROM `gl_voucher_line`;
DELETE FROM `prd_material_requirement_detail`;
DELETE FROM `prd_subcontract_bom_line`;
DELETE FROM `prd_subcontract_quote`;
DELETE FROM `prd_schedule_task`;
DELETE FROM `prd_work_order_resource`;
DELETE FROM `prd_work_report`;
DELETE FROM `erp_subcontract_doc`;
DELETE FROM `fa_depreciation`;
DELETE FROM `fa_disposal`;
DELETE FROM `so_credit_usage`;
DELETE FROM `so_contract_line`;
DELETE FROM `so_invoice_line`;
DELETE FROM `so_price_list_line`;
DELETE FROM `so_quotation_line`;
DELETE FROM `so_return_line`;
DELETE FROM `so_sales_order_line`;
DELETE FROM `pu_contract_line`;
DELETE FROM `pu_invoice_line`;
DELETE FROM `pu_purchase_order_line`;
DELETE FROM `pu_purchase_requisition_line`;
DELETE FROM `pu_purchase_return_line`;
DELETE FROM `po_requisition_detail`;
DELETE FROM `wm_arrival_notice_line`;
DELETE FROM `wm_barcode`;
DELETE FROM `wm_material_handover_line`;
DELETE FROM `wm_material_return_line`;
DELETE FROM `wm_material_stock_tx`;
DELETE FROM `wm_misc_issue_line`;
DELETE FROM `wm_misc_recpt_line`;
DELETE FROM `wm_production_issue_line`;
DELETE FROM `wm_sales_issue_line`;
DELETE FROM `wm_stock_taking_line`;
DELETE FROM `wm_subcontract_issue_line`;
DELETE FROM `wm_transfer_line`;
DELETE FROM `sch_employee_schedule`;
DELETE FROM `sch_plan_team`;
DELETE FROM `sch_team_member`;
DELETE FROM `sch_team_schedule`;
DELETE FROM `wm_sn`;

COMMIT;
```

**执行后校验**（全部应为 0；抽样确认 3D 未被波及）：
```sql
SELECT SUM(c) FROM (
  SELECT (SELECT COUNT(*) FROM wm_material_receive_detail) c UNION ALL
  SELECT (SELECT COUNT(*) FROM wm_barcode) c UNION ALL
  SELECT (SELECT COUNT(*) FROM sch_employee_schedule) c UNION ALL
  SELECT (SELECT COUNT(*) FROM wm_sales_issue_line) c
) x;                                                        -- 预期 0
SELECT COUNT(*) FROM wm_material_stock WHERE warehouse_id=9900000001;   -- ★ 必须仍为 55
```

---

### 批次 B3 —— 单据头层（38 张）

```sql
USE `ry-erp`;
START TRANSACTION;

DELETE FROM `wm_arrival_notice`;
DELETE FROM `wm_material_receive`;
DELETE FROM `wm_material_return`;
DELETE FROM `wm_stock_taking_plan`;
DELETE FROM `wm_transfer`;
DELETE FROM `wm_production_issue`;
DELETE FROM `wm_production_recpt`;
DELETE FROM `wm_sales_issue`;
DELETE FROM `wm_misc_issue`;
DELETE FROM `wm_misc_recpt`;
DELETE FROM `wm_material_handover`;
DELETE FROM `wm_subcontract_issue`;
DELETE FROM `wm_subcontract_receive`;
DELETE FROM `wm_stock_alert`;
DELETE FROM `wm_stock_reconciliation`;
DELETE FROM `wm_picking_rule`;        -- ★ 挂在待删仓库上，必须删
DELETE FROM `wm_putaway_rule`;        -- ★ 同上
DELETE FROM `so_contract`;
DELETE FROM `so_price_list`;
DELETE FROM `so_quotation`;
DELETE FROM `so_return`;
DELETE FROM `pu_contract`;
DELETE FROM `pu_invoice`;
-- 供应商评估记录（引用 bd_vendor，须先于 B5 的 bd_vendor 删除）
DELETE FROM `pu_vendor_evaluation`;
-- 采购批内顺序：return → order → requisition
DELETE FROM `pu_purchase_return`;
DELETE FROM `pu_purchase_order`;
DELETE FROM `pu_purchase_requisition`;
DELETE FROM `po_requisition`;
DELETE FROM `prd_schedule_plan`;
DELETE FROM `prd_material_requirement`;
DELETE FROM `prd_subcontract`;
DELETE FROM `prd_rework_order`;
DELETE FROM `prd_capacity_calendar`;
DELETE FROM `gl_voucher`;
-- 财务批内顺序：so_invoice / fin_receipt 之后才删 fin_settlement
DELETE FROM `so_invoice`;
DELETE FROM `fin_receipt`;
DELETE FROM `fin_settlement`;
DELETE FROM `fin_close_log`;

COMMIT;
```

**执行后校验**：
```sql
SELECT 'wm_picking_rule' t, COUNT(*) n FROM wm_picking_rule
UNION ALL SELECT 'wm_putaway_rule', COUNT(*) FROM wm_putaway_rule
UNION ALL SELECT 'fin_settlement',  COUNT(*) FROM fin_settlement
UNION ALL SELECT 'pu_purchase_order', COUNT(*) FROM pu_purchase_order
UNION ALL SELECT 'wm_material_receive', COUNT(*) FROM wm_material_receive;
-- 预期全部 0

-- 抽查无孤儿：任何 wm_* 明细的父单据都应已不存在（不存在引用即正确）
SELECT COUNT(*) FROM wm_material_stock_tx WHERE warehouse_id IN (SELECT warehouse_id FROM wm_warehouse);
```

---

### 批次 B4 —— 库存核心层（2 张，★ 含 3D 保留过滤）

```sql
USE `ry-erp`;
START TRANSACTION;

-- ★ 4.1 库存：保留 3D 演示 55 行，删其余 37 行
--    注意必须显式处理 warehouse_id IS NULL，否则漏删
DELETE FROM `wm_material_stock`
 WHERE warehouse_id IS NULL OR warehouse_id <> 9900000001;

-- ★ 4.2 批次表：全删（演示库存的 batch_id 全为 0，实测，不受影响）
DELETE FROM `wm_batch`;

COMMIT;
```

**执行后校验**：
```sql
SELECT COUNT(*) AS demo_stock_kept FROM wm_material_stock WHERE warehouse_id=9900000001;  -- ★ 必须 = 55
SELECT COUNT(*) AS total_stock     FROM wm_material_stock;                                 -- 预期 = 55
SELECT COUNT(*) AS batch_left      FROM wm_batch;                                          -- 预期 = 0
-- 3D 演示的库存不应引用已删批次
SELECT COUNT(*) FROM wm_material_stock WHERE warehouse_id=9900000001 AND batch_id<>0;      -- 预期 0
```

---

### 批次 B5 —— 业务主数据与订单层（27 张）

```sql
USE `ry-erp`;
START TRANSACTION;

-- 工单（必须在 wm_* 引用清空之后）
DELETE FROM `prd_work_order`;
DELETE FROM `prd_production_plan`;
-- 销售订单
DELETE FROM `so_sales_order_header`;
DELETE FROM `so_customer_order`;
-- 排班考勤（★ 批内顺序：punch_rule → shift → plan）
DELETE FROM `sch_attendance`;
DELETE FROM `sch_leave_apply`;
DELETE FROM `sch_outdoor_punch`;
DELETE FROM `sch_supplement_apply`;
DELETE FROM `sch_punch_rule`;      -- 子：引用 sch_shift
DELETE FROM `sch_shift`;           -- 子：引用 sch_plan
DELETE FROM `sch_plan`;            -- 父
DELETE FROM `sch_team`;
DELETE FROM `sch_worker`;
-- 财务
DELETE FROM `gl_account_balance`;
DELETE FROM `gl_currency_rate`;
DELETE FROM `fa_asset`;
-- 基础主数据（★ 批内顺序：关系表 → 引用方 → 被引用方）
DELETE FROM `bd_material_vendor`;  -- 子：引用 bd_material + bd_vendor
DELETE FROM `bd_client_credit`;    -- 子：引用 bd_client
DELETE FROM `bd_bom`;              -- 子：引用 bd_routing
DELETE FROM `bd_routing_step`;     -- 子：引用 bd_routing + bd_process + bd_workstation
DELETE FROM `bd_routing`;          -- 父（对 bd_bom / bd_routing_step）
DELETE FROM `bd_process`;          -- 子：引用 bd_workstation
DELETE FROM `bd_workstation`;      -- 子：引用 bd_workshop
DELETE FROM `bd_workshop`;         -- 顶层父
DELETE FROM `bd_material`;         -- 父（对 bd_material_vendor 等）
DELETE FROM `bd_client`;           -- 父
DELETE FROM `bd_vendor`;           -- 父

COMMIT;
```

**执行后校验**（保留集必须完好、删除集必须空）：
```sql
-- 删除集：预期全 0
SELECT SUM(c) FROM (
  SELECT (SELECT COUNT(*) FROM bd_material) c UNION ALL
  SELECT (SELECT COUNT(*) FROM bd_bom) c UNION ALL
  SELECT (SELECT COUNT(*) FROM prd_work_order) c UNION ALL
  SELECT (SELECT COUNT(*) FROM so_sales_order_header) c UNION ALL
  SELECT (SELECT COUNT(*) FROM sch_shift) c
) x;

-- 保留集：预期 55 / 54 / 31 / 24 / 26 / 3 / 0 / 3
SELECT 'bd_material_category' t, COUNT(*) n FROM bd_material_category
UNION ALL SELECT 'bd_unit_of_measure',  COUNT(*) FROM bd_unit_of_measure
UNION ALL SELECT 'gl_account',          COUNT(*) FROM gl_account
UNION ALL SELECT 'gl_period',           COUNT(*) FROM gl_period
UNION ALL SELECT 'report_item',         COUNT(*) FROM report_item
UNION ALL SELECT 'sch_holiday',         COUNT(*) FROM sch_holiday
UNION ALL SELECT 'bd_category_attribute', COUNT(*) FROM bd_category_attribute
UNION ALL SELECT 'fa_asset_category',   COUNT(*) FROM fa_asset_category;

-- 保留集闭包：保留表不应指向已删表
SELECT COUNT(*) FROM bd_material_category x
 WHERE x.parent_id<>0 AND x.parent_id NOT IN (SELECT category_id FROM bd_material_category);  -- 预期 0
```

---

### 批次 B6 —— 仓储结构层（3 张，★ 含 3D 保留过滤）

```sql
USE `ry-erp`;
START TRANSACTION;

-- ★ 顺序：库位 → 库区 → 仓库
-- ★ 保留 3D 演示：location 104 行 / area 4 行 / warehouse 1 行
-- ★ wm_storage_location 有 12 行 warehouse_id IS NULL，必须显式处理

DELETE FROM `wm_storage_location`
 WHERE warehouse_id IS NULL OR warehouse_id <> 9900000001;

DELETE FROM `wm_storage_area`
 WHERE warehouse_id <> 9900000001;          -- 4 行全为演示，此列无 NULL

DELETE FROM `wm_warehouse`
 WHERE warehouse_id <> 9900000001;          -- 删 1 / 2 / 3 / 2091200583779266562

COMMIT;
```

**执行后校验**（★ 本批是 3D 演示的最终验收点）：
```sql
-- 保留量（严格等于文档 §4.2）
SELECT 'wm_warehouse' t, COUNT(*) n FROM wm_warehouse
UNION ALL SELECT 'wm_storage_area',     COUNT(*) FROM wm_storage_area
UNION ALL SELECT 'wm_storage_location', COUNT(*) FROM wm_storage_location
UNION ALL SELECT 'wm_material_stock',   COUNT(*) FROM wm_material_stock;
-- ★ 预期 1 / 4 / 104 / 55  —— 任何偏差都必须回滚

-- 3D 仓库本体仍在
SELECT warehouse_id, warehouse_code, warehouse_name FROM wm_warehouse;   -- 预期仅 9900000001 / WH-3D-DEMO / 3D示例仓库

-- 演示库存的三级引用全部有效（预期 0 条悬空）
SELECT COUNT(*) AS dangling FROM wm_material_stock s
 WHERE s.warehouse_id=9900000001
   AND (s.warehouse_id NOT IN (SELECT warehouse_id FROM wm_warehouse)
     OR s.area_id      NOT IN (SELECT area_id      FROM wm_storage_area)
     OR s.location_id  NOT IN (SELECT location_id  FROM wm_storage_location));   -- 预期 0

-- 库位→库区、库区→仓库 全部有效
SELECT COUNT(*) FROM wm_storage_location l
 WHERE l.area_id NOT IN (SELECT area_id FROM wm_storage_area);                 -- 预期 0
SELECT COUNT(*) FROM wm_storage_area a
 WHERE a.warehouse_id NOT IN (SELECT warehouse_id FROM wm_warehouse);          -- 预期 0

-- 全库残留仓库检查
SELECT COUNT(*) FROM wm_warehouse WHERE warehouse_id<>9900000001;              -- 预期 0
```

---

### 批次 B7 —— 计数器重置与终验收尾

```sql
USE `ry-erp`;

-- B7.1 序列号规则：保留规则本体，重置计数器（本次 current_seq 均为 1，属幂等）
UPDATE `bd_serial_number_rule` SET current_seq = 1;

-- B7.2 条码配置：重置计数器（★ WM_RECEIVE_DEFAULT 原为 23，必须归零）
UPDATE `wm_barcode_config` SET current_sequence = 0;

COMMIT;   -- 若前面未开事务，此处单独 COMMIT
```

**总校验**：
```sql
-- 1) 表数未变
SELECT COUNT(*) FROM information_schema.TABLES
 WHERE TABLE_SCHEMA='ry-erp' AND TABLE_TYPE='BASE TABLE';                     -- 预期 125

-- 2) 计数器已归零
SELECT rule_code, current_seq FROM bd_serial_number_rule;                      -- 预期全 1
SELECT config_code, current_sequence FROM wm_barcode_config;                   -- 预期全 0

-- 3) 全库行数汇总：应只剩「保留 8 张 + 重置 2 张 + 3D 4 张」的数据
--    预期非空表仅 11 张左右
SELECT TABLE_NAME, TABLE_ROWS FROM information_schema.TABLES
 WHERE TABLE_SCHEMA='ry-erp' AND TABLE_ROWS > 0 ORDER BY TABLE_NAME;

-- 4) ★ 3D 演示四条硬指标
SELECT
 (SELECT COUNT(*) FROM wm_warehouse        WHERE warehouse_id=9900000001) AS wh,   -- 1
 (SELECT COUNT(*) FROM wm_storage_area     WHERE warehouse_id=9900000001) AS ar,   -- 4
 (SELECT COUNT(*) FROM wm_storage_location WHERE warehouse_id=9900000001) AS lo,   -- 104
 (SELECT COUNT(*) FROM wm_material_stock   WHERE warehouse_id=9900000001) AS st;   -- 55

-- 5) ★ 跨库复核：MES 侧不应再有任何指向已删 ERP 表的活引用
SELECT 'bd_bom' t, COUNT(*) FROM `ry-mes`.mes_mbom WHERE bom_id IN (SELECT bom_id FROM bd_bom)
UNION ALL SELECT 'bd_material',  COUNT(*) FROM `ry-mes`.pro_sn WHERE material_id IN (SELECT material_id FROM bd_material)
UNION ALL SELECT 'bd_process',   COUNT(*) FROM `ry-mes`.pro_flow_card_step WHERE process_id IN (SELECT process_id FROM bd_process);
-- 预期全 0（若不为 0，说明 B1 有遗漏）

-- 6) 应用冒烟测试（在 ERP 界面手工验证）
--    · 3D 仓库可视化页面能正常打开、渲染 4 库区 / 104 库位 / 55 条库存
--    · 新建物料、新建仓库、新建工单能正常保存（验证编码生成器已复位）
--    · 新增一条收货入库，确认能自动生成条码（验证 wm_barcode_config 可用）
```

---

## 附录 A：证据 SQL 索引

| 编号 | 用途 | 关键语句 |
|---|---|---|
| A1 | 全表清单 | `SELECT TABLE_NAME, TABLE_COMMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA='ry-erp'` |
| A2 | 实测行数 | 由 `information_schema` 生成 125 条 `COUNT(*)` 的 `UNION ALL` |
| A3 | 外键检查 | `SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_TYPE='FOREIGN KEY' AND TABLE_SCHEMA=?` |
| A4 | 跨库外键 | `... WHERE CONSTRAINT_SCHEMA <> REFERENCED_TABLE_SCHEMA` |
| A5 | 3D 演示范围 | 对 884 个 `*_id`/`*_code` 列做 `BETWEEN '9900000000' AND '9900009999'` 值域扫描 |
| A6 | 跨库 3D 检查 | 同上，对 `ry-mes`/`ry-iot`/`ry-vue` 774 列重复 |
| A7 | 引用关系推导 | 包含依赖发现：261 对 `child.col → parent.pk` 的 `IN (SELECT pk)` 子集测试 |
| A8 | 跨库引用探测 | 对 MES/IoT/Vue 中与 ERP 主键同名的整型列做 `filled` vs `live_refs` 对比 |
| A9 | 自增清单 | `SELECT TABLE_NAME, AUTO_INCREMENT FROM information_schema.TABLES WHERE AUTO_INCREMENT IS NOT NULL` |
| A10 | 软删统计 | 对 119 张含 `del_flag` 的表做 `SUM(del_flag='0')` / `SUM(del_flag='1')` |
| A11 | 保留集闭包 | 对保留表逐列做引用探测，确认不指向待删表 |

## 附录 B：本计划的已知边界

1. **本文档只覆盖 `ry-erp`。** `ry-mes`(68 表) 的解绑是**前置动作**，具体执行策略需负责人确认（策略 A 清表 vs 策略 B 置 NULL）。
2. **保留集（8 张）是"技术判定"，不是"业务决策"。** 尤其 `bd_material_category` / `bd_unit_of_measure` / `gl_account` / `gl_period` 若业务方另有规划，可移出保留集——但**移出前必须重跑附录 A11 的闭包检查**。
3. **`sch_worker`(工人档案) 与 `bd_client`(客户) 的删除是业务决策**：若它们同时承载 HR/CRM 主数据职能，应改为"部分删"或保留。本计划按"随业务数据一起清、可由人事/CRM 重导"处置。
4. **未验证运行期缓存。** 删除后需重启应用或清理 Redis（Sa-Token / 缓存注解），否则可能出现"库里没了、界面还在"的假象。
5. **未执行任何写操作。** 全部 125 张表、以及 `ry-mes` / `ry-vue` / `ry-iot` 的检查均为 `SELECT` / `SHOW` / `DESCRIBE`（`information_schema` 查询）。
