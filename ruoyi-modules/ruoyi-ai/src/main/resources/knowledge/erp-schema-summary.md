# NexusERP 业务表结构与流程说明

> 本文档为 AI 知识库预置内容，用于 RAG 问答检索。

---

## 一、系统概述

NexusERP 是一套面向中小型制造企业的 ERP 系统，覆盖**基础数据管理、销售管理、采购管理、生产管理、仓储管理、排程管理**六大核心业务模块，共计约 64 张业务表。

---

## 二、核心业务表清单

### 2.1 基础数据模块（bd_*）

| 表名 | 中文说明 | 主键 | 核心字段 |
|------|----------|------|----------|
| bd_material | 物料产品主表 | material_id | material_code 编码, material_name 名称, material_spec 规格, category_id 分类FK, base_uom_id 单位FK, material_type 类型(1:原材料 2:半成品 3:成品 4:辅料), source_type 来源(1:自制 2:外购 3:委外), safety_stock 安全库存, standard_cost 标准成本, status 状态(0:停用 1:启用) |
| bd_material_category | 物料产品分类表 | category_id | category_code 编码, category_name 名称, parent_id 父分类(0=顶级), level_num 层级, path_ids 路径ID |
| bd_vendor | 供应商表 | vendor_id | vendor_code 编码, vendor_name 名称, contact_person 联系人, phone 电话, status 合作状态(ACTIVE/FROZEN) |
| bd_client | 客户表 | client_id | client_code 编码, client_name 名称, client_type 类型(ENTERPRISE等), contact1 联系人, tel 电话 |
| bd_client_credit | 客户信用表 | credit_id | client_id FK, client_credit 信用额度, client_time 账期(天), client_level 等级(NORMAL等), client_score 评分 |
| bd_material_vendor | 物料供应商关系表 | relation_id | material_id FK, vendor_id FK, lead_time 供货周期(天), min_order_qty 最小订货量, price 单价, is_default 是否默认 |
| bd_bom | BOM主表 | bom_id | parent_material_id 父项物料FK, bom_version 版本号, bom_name 名称, is_current 是否当前版本, status 状态 |
| bd_bom_detail | BOM明细表 | detail_id | bom_id FK, child_material_id 子项物料FK, quantity_per_parent 单位父项用量, loss_rate 损耗率, operation_step 工序步骤 |
| bd_unit_of_measure | 计量单位表 | uom_id | uom_code 编码, uom_name 名称, uom_symbol 符号, category 类别(重量/长度/数量等), conversion_rate 转换率 |
| bd_serial_number_rule | 序列号/条码规则表 | rule_id | rule_code 编码, rule_type 类型(PRODUCT_SN/BARCODE/BATCH_NO), prefix 前缀, seq_length 流水号长度, seq_cycle 重置周期(DAY/MONTH/YEAR/NONE) |
| bd_workshop | 车间表 | workshop_id | workshop_code 编码, workshop_name 名称, manager_name 车间主任, capacity 产能(标准工时/天) |
| bd_workstation | 工作站表 | workstation_id | workstation_code 编码, workshop_id FK, standard_capacity 标准产能(件/小时), operator_count 操作工人数 |

### 2.2 销售管理模块（so_*）

| 表名 | 中文说明 | 主键 | 核心字段 |
|------|----------|------|----------|
| so_sales_order_header | 销售订单头 | so_id | so_no 编号, client_id 客户FK, order_date 订单日期, promise_date 承诺交期, total_amount 总金额, order_status 状态(DRAFT/CONFIRMED/FULFILLED/CLOSED/CANCELLED) |
| so_sales_order_line | 销售订单行 | line_id | so_id FK, material_id 物料FK, ordered_qty 订单数量, delivered_qty 已发货数量, unit_price 单价, line_status 行状态 |
| so_delivery_order | 销售发货单 | delivery_id | delivery_no 编号, so_id FK, client_id 客户FK, delivery_date 发货日期, status 状态 |
| so_delivery_order_line | 发货单行 | line_id | delivery_id FK, material_id 物料FK, delivery_qty 发货数量, unit_price 单价 |
| so_return_order | 销售退货单 | return_id | return_no 编号, so_id FK, client_id 客户FK, return_date 退货日期, status 状态, return_reason 退货原因 |
| so_return_order_line | 退货单行 | line_id | return_id FK, material_id 物料FK, return_qty 退货数量, unit_price 单价 |
| so_quotation | 销售报价单 | quotation_id | quotation_no 编号, client_id 客户FK, quotation_date 报价日期, valid_until 有效期至, total_amount 总金额, status 状态 |
| so_quotation_line | 报价单行 | line_id | quotation_id FK, material_id 物料FK, quantity 数量, unit_price 单价 |

### 2.3 采购管理模块（pu_*）

| 表名 | 中文说明 | 主键 | 核心字段 |
|------|----------|------|----------|
| pu_purchase_order | 采购订单 | order_id | order_no 编号, vendor_id 供应商FK, order_date 订单日期, expected_date 预计到货日期, total_amount 总金额, status 状态(DRAFT/SUBMITTED/APPROVING/APPROVED/PARTIALLY_RECEIVED/RECEIVED/CLOSED) |
| pu_purchase_order_line | 采购订单行 | line_id | order_id FK, material_id 物料FK, ordered_qty 订单数量, received_qty 已收货数量, unit_price 单价 |
| pu_purchase_inbound | 采购入库单 | inbound_id | inbound_no 编号, order_id FK, vendor_id FK, inbound_date 入库日期, status 状态 |
| pu_purchase_inbound_line | 入库单行 | line_id | inbound_id FK, material_id 物料FK, received_qty 收货数量, unit_price 单价 |
| pu_purchase_return | 采购退货单 | return_id | return_no 编号, order_id FK, vendor_id FK, return_date 退货日期, status 状态 |
| pu_purchase_return_line | 退货单行 | line_id | return_id FK, material_id 物料FK, return_qty 退货数量, unit_price 单价 |
| po_requisition | 请购单主表 | requisition_id | requisition_no 编号, department_name 部门, applicant_name 申请人, required_date 需求日期, status 状态 |
| po_requisition_detail | 请购单明细 | detail_id | requisition_id FK, material_id FK, required_quantity 需求数量, stock_quantity 库存数量, shortage_quantity 缺料数量 |

### 2.4 生产管理模块（prd_*）

| 表名 | 中文说明 | 主键 | 核心字段 |
|------|----------|------|----------|
| prd_work_order | 生产工单 | work_order_id | work_order_no 编号, material_id 成品物料FK, bom_id BOM FK, workshop_id 车间FK, planned_qty 计划数量, completed_qty 完成数量, plan_start_date 计划开始, plan_end_date 计划结束, actual_start_date 实际开始, actual_end_date 实际结束, status 状态(DRAFT/RELEASED/IN_PROGRESS/COMPLETED/CLOSED) |
| prd_work_order_line | 工单物料需求明细 | line_id | work_order_id FK, material_id 物料FK, required_qty 需求数量, issued_qty 已发料数量, completed_qty 完成数量 |
| prd_work_report | 报工单 | report_id | work_order_id FK, workstation_id 工作站FK, report_date 报工日期, qualified_qty 合格数量, defective_qty 不良数量, operator_name 操作员 |
| prd_production_plan | 生产计划 | plan_id | plan_no 编号, plan_date 计划日期, plan_type 类型, status 状态 |
| prd_production_plan_item | 生产计划明细 | item_id | plan_id FK, material_id FK, planned_qty 计划数量 |
| prd_capacity_calendar | 产能日历表 | calendar_id | calendar_date 日期, object_type 对象类型(WORKSHOP/WORKSTATION/LINE), object_id 对象ID, available_hours 可用工时, planned_hours 已计划工时, actual_hours 实际用工时, efficiency 效率系数 |
| prd_material_requirement | 物料需求单 | requirement_id | requirement_no 编号, plan_id FK, bom_id FK, requirement_date 需求日期, status 状态 |

### 2.5 仓储管理模块（wm_*）

| 表名 | 中文说明 | 主键 | 核心字段 |
|------|----------|------|----------|
| wm_warehouse | 仓库表 | warehouse_id | warehouse_code 编码, warehouse_name 名称, warehouse_type 类型, address 地址, manager_name 负责人, status 状态 |
| wm_storage_location | 库位表 | location_id | location_code 编码, warehouse_id FK, location_name 名称, capacity 容量, status 状态 |
| wm_material_stock | 物料库存表 | stock_id | material_id FK, warehouse_id FK, location_id FK, stock_quantity 库存数量, locked_quantity 锁定数量, available_quantity 可用数量, unit_cost 单位成本, total_value 库存总值 |
| wm_material_stock_tx | 库存事务流水 | tx_id | material_id FK, warehouse_id FK, tx_type 事务类型(INBOUND/OUTBOUND/TRANSFER/ADJUST), tx_quantity 事务数量(+入/-出), tx_date 事务日期, reference_type 来源类型, reference_id 来源ID, batch_no 批次号 |
| wm_inbound_order | 入库单主表 | inbound_id | inbound_no 编号, warehouse_id FK, inbound_type 入库类型(PURCHASE/RETURN/TRANSFER/OTHER), inbound_date 入库日期, status 状态 |
| wm_inbound_order_line | 入库单行 | line_id | inbound_id FK, material_id FK, location_id FK, planned_qty 计划数量, actual_qty 实际数量, batch_no 批次号 |
| wm_outbound_order | 出库单主表 | outbound_id | outbound_no 编号, warehouse_id FK, outbound_type 出库类型(SALES/PRODUCTION/TRANSFER/OTHER), outbound_date 出库日期, status 状态 |
| wm_outbound_order_line | 出库单行 | line_id | outbound_id FK, material_id FK, location_id FK, planned_qty 计划数量, actual_qty 实际数量, batch_no 批次号 |
| wm_stock_check | 盘点单 | check_id | check_no 编号, warehouse_id FK, check_date 盘点日期, status 状态 |
| wm_stock_check_line | 盘点单行 | line_id | check_id FK, material_id FK, location_id FK, system_qty 系统数量, actual_qty 实际数量, diff_qty 差异数量 |
| wm_stock_transfer | 调拨单 | transfer_id | transfer_no 编号, from_warehouse_id FK, to_warehouse_id FK, transfer_date 调拨日期, status 状态 |
| wm_stock_transfer_line | 调拨单行 | line_id | transfer_id FK, material_id FK, from_location_id FK, to_location_id FK, transfer_qty 调拨数量 |

### 2.6 排程管理模块（sch_*）

| 表名 | 中文说明 | 主键 | 核心字段 |
|------|----------|------|----------|
| sch_schedule_plan | 排程计划 | plan_id | plan_no 编号, plan_type 类型, plan_date 计划日期, status 状态 |
| sch_schedule_detail | 排程明细 | detail_id | plan_id FK, work_order_id FK, workstation_id FK, plan_start_time 计划开始, plan_end_time 计划结束 |
| sch_shift_config | 班次配置 | shift_id | shift_code 编码, shift_name 名称, start_time 开始时间, end_time 结束时间, is_night 是否夜班 |

---

## 三、核心业务流程

### 3.1 销售到发货流程

```
销售报价(so_quotation) → 销售订单(so_sales_order_header/line)
  → 发货(so_delivery_order/line) → 退货(可选, so_return_order/line)
```

**状态流转**：
- 销售订单：DRAFT → CONFIRMED → FULFILLED → CLOSED（或 CANCELLED）

### 3.2 采购到入库流程

```
请购(po_requisition/detail) → 采购订单(pu_purchase_order/line)
  → 审批 → 采购入库(pu_purchase_inbound/line) → 采购退货(可选, pu_purchase_return/line)
```

**状态流转**：
- 采购订单：DRAFT → SUBMITTED → APPROVING → APPROVED → PARTIALLY_RECEIVED → RECEIVED → CLOSED

### 3.3 生产流程

```
生产计划(prd_production_plan/item) → 物料需求(prd_material_requirement)
  → 生产工单(prd_work_order) → 领料出库(wm_outbound_order)
  → 报工(prd_work_report) → 完工入库(wm_inbound_order)
```

**状态流转**：
- 生产工单：DRAFT → RELEASED → IN_PROGRESS → COMPLETED → CLOSED

### 3.4 仓储管理流程

```
入库(采购入库/生产入库/调拨入库) → 库存(wm_material_stock)
  → 出库(销售出库/生产领料/调拨出库) → 盘点(wm_stock_check)
```

**库存事务类型**：
- INBOUND: 入库（+）
- OUTBOUND: 出库（-）
- TRANSFER: 调拨
- ADJUST: 调整

---

## 四、核心表关联关系

```
bd_material ←→ bd_material_category (分类)
bd_material ←→ bd_unit_of_measure (单位)
bd_material ←→ bd_material_vendor ←→ bd_vendor (供应商关系)
bd_material ←→ bd_bom ←→ bd_bom_detail (BOM结构)

so_sales_order_header ←→ so_sales_order_line ←→ bd_material
so_sales_order_header ←→ bd_client (客户)

pu_purchase_order ←→ pu_purchase_order_line ←→ bd_material
pu_purchase_order ←→ bd_vendor (供应商)

prd_work_order ←→ bd_material (成品物料)
prd_work_order ←→ bd_bom (BOM)
prd_work_order ←→ bd_workshop (车间)
prd_work_order_line ←→ bd_material (子件物料)

wm_material_stock ←→ bd_material + wm_warehouse + wm_storage_location
wm_material_stock_tx ←→ bd_material + wm_warehouse (库存事务)
```

---

## 五、编码规则说明

| 业务对象 | 编码前缀 | 示例 | 说明 |
|----------|----------|------|------|
| 物料编码 | IF + 日期 + 序号 | IF20251016004 | 按创建日期自动生成 |
| 销售订单号 | SO + 日期 + 序号 | SO20260101001 | 按创建日期自动生成 |
| 采购订单号 | PO + 日期 + 序号 | PO20260101001 | 按创建日期自动生成 |
| 生产工单号 | WO + 日期 + 序号 | WO20260101001 | 按创建日期自动生成 |
| 入库单号 | IN + 日期 + 序号 | IN20260101001 | 按创建日期自动生成 |
| 出库单号 | OUT + 日期 + 序号 | OUT20260101001 | 按创建日期自动生成 |
| 供应商编码 | V + 序号 | V00001 | 手动编码 |
| 客户编码 | C + 序号 | C00001 | 手动编码 |
| 仓库编码 | WH + 序号 | WH001 | 手动编码 |
| 车间编码 | CJ + 序号 | CJ007 | 手动编码 |

---

## 六、状态机说明

### 6.1 销售订单状态
```
DRAFT(草稿) → CONFIRMED(已确认) → FULFILLED(已发货) → CLOSED(已关闭)
                                    ↘ CANCELLED(已取消)
```

### 6.2 采购订单状态
```
DRAFT(草稿) → SUBMITTED(已提交) → APPROVING(审批中) → APPROVED(已审批)
  → PARTIALLY_RECEIVED(部分收货) → RECEIVED(已收货) → CLOSED(已关闭)
```

### 6.3 生产工单状态
```
DRAFT(草稿) → RELEASED(已下达) → IN_PROGRESS(进行中) → COMPLETED(已完成) → CLOSED(已关闭)
```

### 6.4 通用启用/停用状态
- 基础数据表（物料、客户、供应商等）使用 `status` 字段：0=停用，1=启用
- 供应商表使用 `status` 字段：ACTIVE=正常合作，FROZEN=冻结

---

## 七、常见查询场景

### 7.1 库存查询
- **低库存预警**：`wm_material_stock` JOIN `bd_material`，条件 `available_quantity < safety_stock`
- **按仓库统计库存**：`wm_material_stock` GROUP BY `warehouse_id`
- **库存总金额**：`SUM(total_value)` FROM `wm_material_stock`
- **出库趋势**：`wm_material_stock_tx` WHERE `tx_type = 'OUTBOUND'` GROUP BY 月份

### 7.2 采购查询
- **采购金额统计**：`pu_purchase_order` WHERE `status NOT IN ('DRAFT','CLOSED')` GROUP BY `vendor_id`
- **在途订单**：`pu_purchase_order` WHERE `status IN ('APPROVED','PARTIALLY_RECEIVED')`
- **采购价格历史**：`pu_purchase_order_line` JOIN `pu_purchase_order` 按物料和日期排序
- **供应商评估**：交货准时率 = 按时到货订单数 / 总订单数；退货率 = 退货单行数 / 入库单行数

### 7.3 销售查询
- **月度销售趋势**：`so_sales_order_line` JOIN `so_sales_order_header` GROUP BY 月份
- **客户排行**：`so_sales_order_line` JOIN `so_sales_order_header` GROUP BY `client_id` ORDER BY SUM(amount) DESC
- **订单履行率**：`so_sales_order_line` 的 `delivered_qty / ordered_qty` 平均值
- **退货统计**：`so_return_order` WHERE `return_date >= DATE_SUB(NOW(), INTERVAL ? DAY)`

### 7.4 生产查询
- **在产工单**：`prd_work_order` WHERE `status IN ('RELEASED','IN_PROGRESS')`
- **产能利用率**：`prd_capacity_calendar` 的 `actual_hours / available_hours`
- **BOM展开**：`bd_bom` JOIN `bd_bom_detail` JOIN `bd_material` 递归展开
- **生产完成率**：`prd_work_order` 的 `completed_qty / planned_qty` 平均值

---

## 八、数据隔离说明

所有业务表均包含 `tenant_id` 字段，用于多租户数据隔离。查询时**必须**包含 `tenant_id` 条件，确保数据安全性。

---

## 九、通用字段说明

以下字段在大多数表中存在：
- `tenant_id`: 租户ID（默认 '000000'）
- `create_dept`: 创建部门ID
- `create_by`: 创建人ID
- `create_time`: 创建时间
- `update_by`: 更新人ID
- `update_time`: 更新时间
- `del_flag`: 逻辑删除标志（'0'=正常，'1'=已删除）
- `attr1~attr4`: 预留扩展字段
- `remark`: 备注
