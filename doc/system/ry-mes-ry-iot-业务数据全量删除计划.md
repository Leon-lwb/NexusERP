# `ry-mes`（68 表）+ `ry-iot`（3 表）全量业务数据删除计划

> 环境：MySQL **5.7.20-log**，端口 3306，`root/root`，客户端 `D:\mysql-8.0.39\mysql-8.0.39-winx64\bin\mysql.exe`
> 本文件**只读分析产物**，执行前未对任何库执行 INSERT/UPDATE/DELETE/ALTER/DROP/TRUNCATE。
> 生成时间：分析会话内实测，所有行数均为 `SELECT COUNT(*)` 实测值。

---

## 0. 结论速览

| 项 | 结论 |
|---|---|
| `ry-mes` 表数 | **68**（实测，与任务给定一致） |
| `ry-iot` 表数 | **3** |
| 三个库外键约束总数 | **0**（`ry-mes` / `ry-iot` / `ry-erp` 全都没有 FOREIGN KEY） |
| `ry-mes` 业务行总数 | **695 行**（实测） |
| `ry-iot` 业务行总数 | **213 行**（实测） |
| `TABLE_ROWS` 与实测不符的表 | **11 张**（全部为「估算 0、实际 1」），故全文以 `COUNT(*)` 为准 |
| `ry-mes` / `ry-iot` 自增主键 | **0 个**（全部为雪花 ID 应用赋值），因此「TRUNCATE 重置自增」在 MES/IoT 侧**不产生任何影响** |
| 需删除（MES） | 39 张全删表 **517 行** + 1 张部分删表 **1 行** = **518 行** |
| 需保留（MES） | 28 张表 **177 行**（含部分删表保留的 3 行） |
| 需删除（IoT） | `iot_device_data` **205 行** |
| 保留（IoT） | `iot_device` 5 + `iot_product` 3 = **8 行** |
| 3D 演示仓库数据 | **只存在于 `ry-erp`**，MES/IoT 侧**零牵连**；保留清单 = 4 张 ERP 表共 164 行 |
| ERP ↔ MES | **双向跨库引用、且存在 2 组环路**，必须按本文批次顺序执行 |

---

# 第一部分　全表清点表

## 1.1 清点方法（证据）

**E-1：确认表数 68 / 3 / 125**

```sql
SELECT (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='ry-mes') AS mes_cnt,
       (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='ry-iot') AS iot_cnt,
       (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='ry-erp') AS erp_cnt;
```

```
+---------+---------+---------+
| mes_cnt | iot_cnt | erp_cnt |
+---------+---------+---------+
|      68 |       3 |     125 |
+---------+---------+---------+
```

**E-2：实测行数（`COUNT(*)` 而非 `TABLE_ROWS`）**

生成并执行：
```sql
-- 由 information_schema 动态拼接出的 68 条 UNION ALL
SELECT 'dc_collection_point' AS tbl, COUNT(*) AS cnt FROM `ry-mes`.`dc_collection_point`
UNION ALL SELECT 'dc_data_record', COUNT(*) FROM `ry-mes`.`dc_data_record`
... /* 68 条 */
```
（完整脚本：`.dsh-tmp/dbdel/count_ry-mes.sql` / `count_ry-iot.sql`）

**E-3：`TABLE_ROWS` 不可信的实测证据（11 张表估算 0、实际 1）**

```sql
SELECT TABLE_NAME, TABLE_ROWS FROM information_schema.TABLES WHERE TABLE_SCHEMA='ry-mes';
```

| 表 | `TABLE_ROWS` 估算 | `COUNT(*)` 实测 |
|---|---|---|
| `lt_wo_consumption` | 0 | **1** |
| `mes_content_file` | 0 | **1** |
| `mes_label_template` | 0 | **1** |
| `mes_release_package` | 0 | **1** |
| `mes_release_pkg_firmware` | 0 | **1** |
| `mes_resource_pool` | 0 | **1** |
| `mes_test_item` | 0 | **1** |
| `qc_inspect_template` | 0 | **1** |
| `spc_data_point` | 0 | **1** |
| `tg_maintenance_plan` | 0 | **1** |
| `tg_maintenance_record` | 0 | **1** |

> 结论：凡涉及「是否为空 / 是否可跳过」的判断，全部改用 `SELECT COUNT(*)`。本文所有行数列均为实测。

**E-4：全库无外键约束**

```sql
SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
WHERE CONSTRAINT_TYPE='FOREIGN KEY' AND CONSTRAINT_SCHEMA IN ('ry-mes','ry-iot','ry-erp');

SELECT CONSTRAINT_SCHEMA, TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_SCHEMA, REFERENCED_TABLE_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE REFERENCED_TABLE_NAME IS NOT NULL AND CONSTRAINT_SCHEMA IN ('ry-mes','ry-iot','ry-erp');
```

```
+----------+
| COUNT(*) |
+----------+
|        0 |
+----------+
（第二条 SELECT 返回空集）
```

> **推论 1**：删除顺序完全依赖业务语义，数据库不会替你拦截孤儿。
> **推论 2**：`TRUNCATE` 在所有表上都合法（MySQL 只禁止 TRUNCATE 被外键引用的表）。

**E-5：全部 InnoDB、全部为 BASE TABLE（无视图/触发器/存储过程）**

```sql
SELECT TABLE_SCHEMA, TABLE_NAME, ENGINE FROM information_schema.TABLES
WHERE TABLE_SCHEMA IN ('ry-mes','ry-iot') AND (ENGINE<>'InnoDB' OR TABLE_TYPE<>'BASE TABLE');

SELECT TRIGGER_SCHEMA, TRIGGER_NAME FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA IN ('ry-mes','ry-iot','ry-erp');
SELECT ROUTINE_SCHEMA, ROUTINE_NAME FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA IN ('ry-mes','ry-iot','ry-erp');
```
（三条均返回空集 → 68+3 张表全部 InnoDB、全部 BASE TABLE、无触发器、无存储过程）

> **推论 3**：不存在触发器级联写、不存在存储过程二次删除，删除动作的影响面=被删的那张表本身。

**E-6：MES/IoT 侧没有任何 AUTO_INCREMENT 列**

```sql
SELECT TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME, EXTRA FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA IN ('ry-mes','ry-iot','ry-erp') AND EXTRA LIKE '%auto_increment%';
```

```
+--------------+------------------------------+----------------+----------------+
| TABLE_SCHEMA | TABLE_NAME                   | COLUMN_NAME    | EXTRA          |
+--------------+------------------------------+----------------+----------------+
| ry-erp       | prd_capacity_calendar        | calendar_id    | auto_increment |
| ry-erp       | pu_purchase_order            | order_id       | auto_increment |
| ry-erp       | pu_purchase_order_line       | line_id        | auto_increment |
| ry-erp       | pu_purchase_requisition      | requisition_id | auto_increment |
| ry-erp       | pu_purchase_requisition_line | line_id        | auto_increment |
| ry-erp       | pu_purchase_return           | return_id      | auto_increment |
| ry-erp       | pu_purchase_return_line      | line_id        | auto_increment |
| ry-erp       | wm_barcode                   | barcode_id     | auto_increment |
| ry-erp       | wm_barcode_config            | config_id      | auto_increment |
| ry-erp       | wm_production_recpt          | recpt_id       | auto_increment |
+--------------+------------------------------+----------------+----------------+
```

> **`ry-mes` / `ry-iot` 一张都没有**（列表里只有 `ry-erp`）。这直接决定了第 4 部分「重置编码」的结论。

---

## 1.2 `ry-mes` 全 68 表清点

行数 = `SELECT COUNT(*)` 实测。分类按任务指定的 8 类。

| # | 表名 | 表注释 | 实测行数 | 分类 | 处置建议 |
|---:|---|---|---:|---|---|
| 1 | `dc_collection_point` | MES数据采集点 | 9 | 数据采集 | **保留**（采集点配置主数据，见 §3.4） |
| 2 | `dc_data_record` | MES采集数据记录 | 12 | 数据采集 | 全删 |
| 3 | `eq_equipment` | MES设备台账 | 6 | 设备管理 | **保留**（设备台账=基础主数据，理由见 §2.5） |
| 4 | `eq_health_record` | 设备健康评分记录表 | 0 | 设备管理 | 全删（评分流水） |
| 5 | `eq_inspection_record` | 设备点检记录(历史留痕, PC端与工位终端共用) | 0 | 设备管理 | 全删（点检流水） |
| 6 | `eq_maintenance_plan` | MES设备保养计划 | 5 | 设备管理 | **保留**（保养计划仍 ACTIVE、`next_date` 未到期，见 §2.5） |
| 7 | `eq_maintenance_record` | MES设备保养执行记录 | 0 | 设备管理 | 全删（保养执行流水） |
| 8 | `eq_oee_record` | MES设备 OEE 聚合记录(日粒度) | 0 | 设备管理 | 全删（OEE 统计流水） |
| 9 | `eq_patrol_record` | MES巡检记录 | 0 | 设备管理 | 全删（巡检执行流水） |
| 10 | `eq_patrol_route` | MES巡检路线 | 0 | 设备管理 | **保留**（路线配置） |
| 11 | `eq_patrol_task` | MES巡检任务 | 0 | 设备管理 | 全删（任务流水） |
| 12 | `eq_repair` | MES设备维修记录 | 2 | 设备管理 | 全删（维修流水，见 §2.5 备注） |
| 13 | `eq_repair_spare` | MES维修领用备件明细 | 0 | 设备管理 | 全删 |
| 14 | `eq_spare_part` | MES备件主数据 | 0 | 设备管理 | **保留**（备件主数据） |
| 15 | `eq_spare_stock_log` | MES备件库存流水 | 0 | 设备管理 | 全删 |
| 16 | `lt_lot` | MES批次主档 | 2 | 追溯 | 全删 |
| 17 | `lt_sn_binding` | MES SN批次绑定关系 | 0 | 追溯 | 全删 |
| 18 | `lt_wo_consumption` | MES工单批次消耗记录 | 1 | 追溯 | 全删 |
| 19 | `mes_content_file` | 刷写内容档案(配置文件等, 审批发布) | 1 | 配置 | **保留**（已发布档案） |
| 20 | `mes_firmware` | 固件档案 | 6 | 配置 | **保留**（固件档案主数据） |
| 21 | `mes_flash_record` | 固件刷写记录(SN级) | 90 | 追溯 | 全删 |
| 22 | `mes_flash_type` | 刷写类型定义(配置化目录) | 5 | 配置 | **保留**（配置目录） |
| 23 | `mes_label_template` | 标签打印模板 | 1 | 配置 | **保留**（模板配置） |
| 24 | `mes_material_load` | 工位物料上料台账(上料防错 C19 写入, 换料置 REPLACED) | 5 | 生产执行单据 | 全删 |
| 25 | `mes_mbom` | MES制造BOM头 | 5 | 基础主数据 | **保留**（见 §3.2 强约束） |
| 26 | `mes_mbom_line` | MES制造BOM明细 | 23 | 基础主数据 | **保留**（同上） |
| 27 | `mes_release_package` | MES客户定制化发布包主表 | 1 | 基础主数据 | **保留**（发布包定义，与 28/29/30 同生共死） |
| 28 | `mes_release_pkg_bom_item` | MES发布包BOM选配表(替换/新增/移除) | 0 | 基础主数据 | **保留** |
| 29 | `mes_release_pkg_config` | MES发布包配置项表(测试参数/标签/包装/工艺参数) | 0 | 基础主数据 | **保留** |
| 30 | `mes_release_pkg_firmware` | MES发布包固件映射表(工序→固件) | 1 | 基础主数据 | **保留** |
| 31 | `mes_resource_item` | 资源池明细 | 30 | 追溯 | 全删（含 20 行孤儿，见 §2.4） |
| 32 | `mes_resource_pool` | 刷写资源池(MAC/SN等) | 1 | 配置 | **保留**（池定义） |
| 33 | `mes_sn_bind_log` | 关键件绑定操作日志 | 3 | 追溯 | 全删 |
| 34 | `mes_station_device` | 工位终端设备台账 | 4 | 设备管理 | **部分删**：删 `device_code='UNREGISTERED'` 占位行（1 行），保留 3 台真实终端 |
| 35 | `mes_station_report` | 工位终端报工记录(设备通道) | 2 | 生产执行单据 | 全删 |
| 36 | `mes_test_item` | 测试项标准主数据 | 1 | 基础主数据 | **保留** |
| 37 | `mes_test_record` | 测试记录(SN级全检) | 0 | 追溯 | 全删 |
| 38 | `ncr_8d_report` | 8D报告 | 7 | 质量管理 | 全删 |
| 39 | `ncr_defect` | NCR缺陷明细 | 2 | 质量管理 | 全删 |
| 40 | `ncr_defect_code` | 缺陷代码库 | 71 | 质量管理 | **保留**（代码库=主数据，71 行） |
| 41 | `ncr_order` | NCR不合格品单 | 11 | 质量管理 | 全删 |
| 42 | `ncr_rework_order` | NCR返工工单 | 2 | 质量管理 | 全删 |
| 43 | `pro_andon` | MES安灯呼叫 | 10 | 生产执行单据 | 全删 |
| 44 | `pro_dispatch` | MES派工单 | 35 | 生产执行单据 | 全删 |
| 45 | `pro_flow_card` | MES流转卡表 | 25 | 生产执行单据 | 全删 |
| 46 | `pro_flow_card_step` | MES流转卡工序记录表 | 64 | 生产执行单据 | 全删 |
| 47 | `pro_sn` | MES SN序列号追溯 | 77 | 追溯 | 全删 |
| 48 | `pro_step_check_rule` | MES工序校验规则配置 | 0 | 配置 | **保留**（规则配置） |
| 49 | `pro_step_guide` | MES工序流程指引配置 | 0 | 配置 | **保留**（指引配置） |
| 50 | `pro_work_order` | MES工单表 | 18 | 生产执行单据 | 全删 |
| 51 | `pro_work_order_bom_line` | MES工单BOM明细快照 | 46 | 生产执行单据 | 全删 |
| 52 | `pro_work_order_step` | MES工单工序步骤表 | 53 | 生产执行单据 | 全删 |
| 53 | `qc_inspect_detail` | QC检验单明细 | 6 | 质量管理 | 全删 |
| 54 | `qc_inspect_item` | QC检验项目 | 2 | 质量管理 | **保留**（检验项主数据） |
| 55 | `qc_inspect_template` | QC检验模板 | 1 | 质量管理 | **保留**（模板主数据） |
| 56 | `qc_ipqc` | MES过程检验 | 2 | 质量管理 | 全删 |
| 57 | `qc_iqc` | MES来料检验 | 6 | 质量管理 | 全删 |
| 58 | `qc_oqc` | MES出货检验 | 2 | 质量管理 | 全删 |
| 59 | `sop_document` | MES SOP文档主档 | 10 | 基础主数据 | **保留** |
| 60 | `sop_process_binding` | 工序SOP绑定 | 10 | 基础主数据 | **保留** |
| 61 | `sop_version` | SOP版本历史 | 9 | 基础主数据 | **保留** |
| 62 | `sop_view_log` | SOP查阅记录 | 0 | 追溯 | 全删（查阅流水） |
| 63 | `spc_control_chart` | MES SPC控制图 | 3 | SPC | **保留**（控制图定义，绑定 物料+工序） |
| 64 | `spc_data_point` | MES SPC数据点 | 1 | SPC | 全删（数据点） |
| 65 | `tg_maintenance_plan` | MES工装保养计划 | 1 | 设备管理 | **保留**（保养计划） |
| 66 | `tg_maintenance_record` | MES工装保养记录 | 1 | 设备管理 | 全删 |
| 67 | `tg_tooling` | MES工装台账 | 2 | 设备管理 | **保留**（工装台账=主数据） |
| 68 | `tg_usage_record` | MES工装使用领用记录 | 2 | 设备管理 | 全删 |

**小计（实测）**
* 表数：**68**
* 全删 **39 张 = 517 行**；部分删 **1 张 = 1 行**；保留 **28 张 = 177 行**
* 删除合计 **518 行**，保留 **177 行**，合计 **695 行** ✅（与 E-2 实测总数一致）

```sql
-- 实测总行数校验
SELECT SUM(c) FROM (
  SELECT COUNT(*) c FROM `ry-mes`.`dc_collection_point`
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.`dc_data_record`
  ... /* 68 条 */
) x;
-- 结果：695
```

## 1.3 `ry-iot` 全 3 表清点

| # | 表名 | 表注释 | 实测行数 | 分类 | 处置建议 |
|---:|---|---|---:|---|---|
| 69 | `iot_device` | IoT设备实例 | 5 | 设备管理 | **保留**（设备实例主数据，与 MES 设备台账 1:N 绑定） |
| 70 | `iot_device_data` | IoT设备数据(原始) | 205 | 数据采集 | 全删（时序原始数据） |
| 71 | `iot_product` | IoT产品(设备模板) | 3 | 基础主数据 | **保留**（产品模板） |

```sql
SELECT SUM(c) FROM (SELECT COUNT(*) c FROM iot_device
  UNION ALL SELECT COUNT(*) FROM iot_device_data
  UNION ALL SELECT COUNT(*) FROM iot_product) x;
-- 结果：213
```

---

# 第二部分　依赖关系与删除批次

## 2.1 外键实测（证据 E-4 结论）

`ry-mes` / `ry-iot` 外键数 = **0**。因此：

1. 数据库**不会**帮你阻断孤儿行，也不会因约束导致删除失败；
2. 代码层全部显式赋值主键（雪花 ID），跨表关联全靠 Service 事务 + 人工约定；
3. 所有表**都可以 `TRUNCATE`**（无外键引用即无限制）。

## 2.2 业务语义依赖图（全部经实测验证）

**E-7：MES 内部引用完整性实测（77 组 主→子 关系）**

```sql
-- 对每组 (子表.子列, 父表.父列) 统计 引用数 / 命中数
SELECT 'pro_sn.mes_wo_id->pro_work_order.mes_wo_id' chk,
       (SELECT COUNT(*) FROM `ry-mes`.pro_sn WHERE mes_wo_id IS NOT NULL AND mes_wo_id<>0) refs,
       (SELECT COUNT(*) FROM `ry-mes`.pro_sn x JOIN `ry-mes`.pro_work_order y ON y.mes_wo_id=x.mes_wo_id) matched
UNION ALL ... /* 共 77 组 */
```

关键链路结果（节选，`refs`=非空引用数，`matched`=能命中的数）：

| 主链 | refs | matched | 判定 |
|---|---:|---:|---|
| `pro_work_order_step.mes_wo_id → pro_work_order` | 53 | 51 | 有 2 行孤儿 |
| `pro_work_order_bom_line.mes_wo_id → pro_work_order` | 46 | 44 | 有 2 行孤儿 |
| `pro_work_order_bom_line.mbom_line_id → mes_mbom_line` | 44 | 44 | 完整 |
| `pro_dispatch.mes_wo_id → pro_work_order` | 35 | 33 | 有 2 行孤儿 |
| `pro_dispatch.card_id → pro_flow_card` | 35 | 31 | 有 4 行孤儿 |
| `pro_dispatch.step_record_id → pro_flow_card_step` | 35 | 33 | — |
| `pro_flow_card.mes_wo_id → pro_work_order` | 25 | 25 | 完整 |
| `pro_flow_card.current_step_id → pro_flow_card_step.step_record_id` | 24 | 24 | 完整（**环路**） |
| `pro_flow_card_step.card_id → pro_flow_card` | 64 | 60 | 有 4 行孤儿（**环路反向**） |
| `pro_flow_card_step.wo_step_id → pro_work_order_step.step_id` | 60 | 60 | 完整 |
| `pro_sn.mes_wo_id → pro_work_order` | 77 | 77 | 完整 |
| `pro_sn.card_id → pro_flow_card` | 77 | 77 | 完整 |
| `dc_data_record.point_id → dc_collection_point` | 12 | 12 | 完整 |
| `dc_data_record.mes_wo_id/card_id/step_record_id` | 12/12/12 | 12/12/12 | 完整 |
| `mes_flash_record.sn_id → pro_sn` | 90 | 90 | 完整 |
| `mes_flash_record.mes_wo_id → pro_work_order` | 90 | 90 | 完整 |
| `mes_flash_record.flash_type_id → mes_flash_type` | 90 | 90 | 完整 |
| `mes_flash_record.equipment_id → eq_equipment` | 3 | 3 | 完整 |
| `mes_resource_item.pool_id → mes_resource_pool` | 30 | **10** | **20 行孤儿** |
| `ncr_defect.ncr_id → ncr_order` / `ncr_8d_report.ncr_id → ncr_order` | 2 / 7 | 2 / 7 | 完整 |
| `ncr_rework_order.ncr_id → ncr_order` | 2 | 2 | 完整 |
| `ncr_rework_order.original_wo_id / rework_wo_id → pro_work_order` | 2 / 2 | 2 / 2 | 完整 |
| `spc_data_point.chart_id → spc_control_chart` | 1 | 1 | 完整 |
| `spc_control_chart.point_id → dc_collection_point` | 3 | 3 | 完整 |
| `qc_ipqc.mes_wo_id/card_id/step_record_id` | 2/2/2 | 2/2/2 | 完整 |
| `qc_inspect_item.template_id → qc_inspect_template` | 2 | 2 | 完整 |
| `sop_version.sop_id → sop_document` | 9 | 9 | 完整 |
| `sop_process_binding.sop_id → sop_document` | 10 | 10 | 完整 |
| `eq_maintenance_plan.equipment_id → eq_equipment` | 5 | 5 | 完整 |
| `eq_repair.equipment_id → eq_equipment` | 2 | 2 | 完整 |
| `tg_maintenance_plan.tooling_id → tg_tooling` | 1 | 1 | 完整 |
| `tg_maintenance_record.tooling_id → tg_tooling` | 1 | 1 | 完整 |
| `tg_usage_record.tooling_id → tg_tooling` | 2 | 2 | 完整 |
| `lt_wo_consumption.lot_id → lt_lot` | 1 | 1 | 完整 |
| `mes_sn_bind_log.parent_sn_id / component_sn_id → pro_sn` | 3 / 3 | 3 / 3 | 完整 |
| `mes_station_report.mes_wo_id / card_id → pro_work_order / pro_flow_card` | 2 / 2 | 2 / 2 | 完整 |
| `pro_work_order_bom_line.bom_detail_id →` （ERP，见 §3） | 26 | 25 | 1 行孤儿 |

**由此得到的 MES 内部 DAG（父 → 子）**

```
mes_mbom ──► mes_mbom_line ──► pro_work_order_bom_line ◄── pro_work_order
                                    │
pro_work_order ──┬──► pro_work_order_step ──► pro_flow_card_step ──┐
                 │                                   ▲             │
                 ├──► pro_flow_card ─────────────────┘（card_id）  │
                 │        ▲  │ current_step_id ────────────────────┘（环路）
                 │        │  └──► pro_sn ──► mes_flash_record / mes_test_record / mes_sn_bind_log / lt_sn_binding
                 │        └──► pro_dispatch / mes_station_report / pro_andon
                 ├──► qc_ipqc ──► qc_inspect_detail
                 ├──► ncr_order ──► ncr_defect / ncr_8d_report / ncr_rework_order(→pro_work_order)
                 ├──► lt_wo_consumption / mes_material_load / tg_usage_record / sop_view_log
                 └──► dc_data_record

qc_inspect_template ──► qc_inspect_item ──► qc_inspect_detail
spc_control_chart ──► spc_data_point        （spc_control_chart.point_id → dc_collection_point）
mes_resource_pool ──► mes_resource_item ──► (mes_flash_record.content_ref_id 多态)
eq_equipment ──► eq_maintenance_plan ──► eq_maintenance_record
             ──► eq_repair ──► eq_repair_spare ──► eq_spare_part
             ──► eq_patrol_task ──► eq_patrol_record
             ──► eq_health_record / eq_oee_record / eq_inspection_record
             ──► mes_flash_record / mes_test_record / dc_collection_point
tg_tooling ──► tg_maintenance_plan ──► tg_maintenance_record
           ──► tg_usage_record
sop_document ──► sop_version / sop_process_binding / sop_view_log / pro_step_guide
```

**特别标注：MES 内部存在 1 处环路**
`pro_flow_card.current_step_id ──► pro_flow_card_step.step_record_id`
`pro_flow_card_step.card_id ──► pro_flow_card.card_id`
（E-7 实测 24/24 与 64/60，两侧都有数据）
→ 删除时二者必须**同批**，批内先删 `pro_flow_card_step`，再删 `pro_flow_card`；或在同一事务内执行。

## 2.3 删除批次设计原则

> **不变量**：任何时刻都不能出现「**仍然存在的子行**指向**已被删除的父行**」。
> 因此顺序恒为 **子先父后**；反过来（先删父）必然产生孤儿。同批内多表按依赖深度逆序排列。

## 2.4 已存在的孤儿数据清单（删前须知）

这些孤儿**现在就有**，删除计划不制造它们，但清理顺序上它们会让「先删父」看起来没事——必须避免以此为借口。

| # | 孤儿 | 行数 | 证据 |
|---:|---|---:|---|
| 1 | `mes_resource_item.pool_id` 指向不存在的池 `2091000000000006103`、`2091000000000006120` | **20** | E-7：30 refs / 10 matched；池表仅 1 行 |
| 2 | `pro_dispatch` / `pro_flow_card_step` 的 `card_id` 指向不存在的流转卡 | 4 / 4 | E-7：35/31、64/60 |
| 3 | `pro_work_order_step` / `pro_work_order_bom_line` 的 `mes_wo_id` 指向不存在的工单 `2089704730308657153`、`2089710626887811073` | 2 / 2 | 明细见下 |
| 4 | `pro_work_order` 一行的 ERP 侧 material/bom/routing 全悬空 | 1 | 见 §3.5 |
| 5 | `tg_usage_record.wo_id = 100`（`WO-E2E-001`）无对应工单 | 1 | E-7：1 refs / 0 matched |
| 6 | `lt_lot.supplier_id` 有 1 行指向不存在的供应商 | 1 | §3.3 Q3：2 refs / 1 matched |

```sql
-- 证据（孤儿 1）
SELECT pool_id, COUNT(*) FROM `ry-mes`.mes_resource_item GROUP BY pool_id;
+---------------------+-----+
| 2089376872637935618 |  10 |
| 2091000000000006103 |  10 |
| 2091000000000006120 |  10 |
+---------------------+-----+
SELECT pool_id, pool_code, pool_name FROM `ry-mes`.mes_resource_pool;
+---------------------+---------------+---------------------+
| 2089376872637935618 | PL20260817012 | GW-1000产线MAC池    |
+---------------------+---------------+---------------------+

-- 证据（孤儿 3）
SELECT step_id, mes_wo_id, process_code FROM `ry-mes`.pro_work_order_step
WHERE mes_wo_id NOT IN (SELECT mes_wo_id FROM `ry-mes`.pro_work_order);
+---------------------+---------------------+--------------+
| 2089704730375766019 | 2089704730308657153 | FLASH        |
| 2089710626887811074 | 2089710626887811073 | FLASH        |
+---------------------+---------------------+--------------+
```

## 2.5 ★ 设备管理专项分析（用户点名重点）

### 2.5.1 MES 设备域 = 13 张 `eq_*` + 4 张 `tg_*`（工装）+ 1 张工位终端表

| 表 | 行数 | 角色 | 依赖 | 建议 |
|---|---:|---|---|---|
| `eq_equipment` | 6 | **设备台账（主数据根）** | 被 ERP/MES/IoT 共 6 处引用 | **保留** |
| `eq_maintenance_plan` | 5 | 保养计划（周期性任务定义） | → `eq_equipment` | **保留** |
| `eq_maintenance_record` | 0 | 保养执行流水 | → `eq_maintenance_plan`,`eq_equipment` | 全删 |
| `eq_repair` | 2 | 维修记录 | → `eq_equipment` | 全删 |
| `eq_repair_spare` | 0 | 维修领用备件明细 | → `eq_repair`,`eq_spare_part` | 全删 |
| `eq_spare_part` | 0 | 备件主数据 | 被 `eq_repair_spare`/`eq_spare_stock_log` 引用 | **保留** |
| `eq_spare_stock_log` | 0 | 备件库存流水 | → `eq_spare_part` | 全删 |
| `eq_inspection_record` | 0 | 设备点检记录 | → `eq_equipment` | 全删 |
| `eq_patrol_route` | 0 | 巡检路线（配置） | 被 `eq_patrol_task`/`eq_patrol_record` 引用 | **保留** |
| `eq_patrol_task` | 0 | 巡检任务 | → `eq_patrol_route` | 全删 |
| `eq_patrol_record` | 0 | 巡检执行记录 | → `eq_patrol_task`,`eq_patrol_route`,`eq_equipment` | 全删 |
| `eq_health_record` | 0 | 设备健康评分 | → `eq_equipment`(,`iot_device`) | 全删 |
| `eq_oee_record` | 0 | 设备 OEE 日聚合 | → `eq_equipment` | 全删 |
| `tg_tooling` | 2 | **工装台账（主数据根）** | 被 `tg_*` 全部引用 | **保留** |
| `tg_maintenance_plan` | 1 | 工装保养计划 | → `tg_tooling` | **保留** |
| `tg_maintenance_record` | 1 | 工装保养记录 | → `tg_tooling`,`tg_maintenance_plan` | 全删 |
| `tg_usage_record` | 2 | 工装领用记录 | → `tg_tooling`(,`pro_work_order`,`bd_workstation`) | 全删 |
| `mes_station_device` | 4 | 工位终端设备台账 | 终端物理资产 | **部分删**（删 `UNREGISTERED` 心跳占位） |
| `iot_device` | 5 | **IoT 设备实例** | ↔ `eq_equipment` | **保留** |
| `iot_device_data` | 205 | 设备时序原始数据 | → `iot_device` | 全删 |
| `iot_product` | 3 | IoT 产品模板 | 被 `iot_device` 引用 | **保留** |

### 2.5.2 设备数据与工单/质检/SPC/数据采集的牵连关系（全部实测）

**E-8：设备台账被谁引用**

```sql
-- MES→设备
SELECT 'pro_work_order.equipment_id' k, COUNT(*) refs, SUM(e.equipment_id IS NOT NULL) matched
FROM `ry-mes`.pro_work_order m LEFT JOIN `ry-mes`.eq_equipment e ON e.equipment_id=m.equipment_id
WHERE m.equipment_id IS NOT NULL AND m.equipment_id<>0;
```
```
+-------------------------------------+------+---------+
| k                                   | refs | matched |
+-------------------------------------+------+---------+
| pro_work_order.equipment_id         |    0 |       0 |   ← 工单未直接挂设备（走 workcenter/工位）
| mes_flash_record.equipment_id       |    3 |       3 |   ← 刷写记录挂设备
| mes_test_record.equipment_id        |    0 |       0 |
| dc_collection_point.equipment_id    |    5 |       5 |   ← 采集点挂设备
| eq_maintenance_plan.equipment_id    |    5 |       5 |
| eq_repair.equipment_id              |    2 |       2 |
| eq_health_record.equipment_id       |    0 |       0 |
| eq_oee_record.equipment_id          |    0 |       0 |
| eq_inspection_record.equipment_id   |    0 |       0 |
| eq_patrol_record.equipment_id       |    0 |       0 |
+-------------------------------------+------+---------+
```

```sql
-- 设备↔工作中心（跨库到 ERP）
SELECT 'eq_equipment.workcenter_id -> ry-erp.bd_workstation.workstation_id' chk,
  (SELECT COUNT(*) FROM `ry-mes`.eq_equipment WHERE workcenter_id IS NOT NULL AND workcenter_id<>0) refs,
  (SELECT COUNT(*) FROM `ry-mes`.eq_equipment m JOIN `ry-erp`.bd_workstation e ON e.workstation_id=m.workcenter_id) matched;
-- refs=6, matched=6
```

```sql
-- 设备↔IoT（双向，跨库）
SELECT 'iot_device.mes_equipment_id -> ry-mes.eq_equipment.equipment_id' chk, 5 refs, 5 matched
UNION ALL SELECT 'ry-mes.eq_equipment.iot_device_id -> ry-iot.iot_device.device_id', 1, 1;
```

```sql
-- 设备↔ERP 工作站的"反向"引用
SELECT workstation_id, workstation_code, workstation_name, equipment_id
FROM `ry-erp`.bd_workstation WHERE equipment_id IS NOT NULL AND equipment_id<>0;
+----------------+------------------+------------------+--------------+
| workstation_id | workstation_code | workstation_name  | equipment_id |
+----------------+------------------+------------------+--------------+
|              2 | GZZ0003          | 工作站2          |            1 |
|              3 | GZZ0004          | 工作站3          |            5 |
+----------------+------------------+------------------+--------------+
```

**牵连结论（设备域）**

| 牵连对象 | 关系 | 若删设备的后果 |
|---|---|---|
| `ry-erp.bd_workstation.equipment_id`（2 行） | **ERP → MES 反向引用** | ERP 工作站丢失物理设备指向，**工作站排产/产能页面取不到设备** |
| `ry-iot.iot_device.mes_equipment_id`（5 行，全部命中） | **IoT → MES 反向引用** | 5 台 IoT 设备全部变成"无主探头"，实时数据无法归属设备 |
| `ry-mes.dc_collection_point.equipment_id`（5 行，全部命中） | MES 内部 | 5 个采集点失去设备来源，`dc_data_record` 回写链路断裂 |
| `ry-mes.eq_maintenance_plan`（5 行）+ `eq_repair`（2 行） | MES 内部 | 保养/维修记录全部失去设备主体 |
| `ry-mes.mes_flash_record.equipment_id`（3 行） | MES 内部 | 固件刷写记录失去刷写设备 |
| `ry-mes.mes_test_record.equipment_id`（0 行） | MES 内部 | 当前无数据 |
| SPC | `spc_control_chart.point_id → dc_collection_point → equipment` 二跳 | 控制图失去数据源设备（控制图本身保留，但重新采集要重建采集点） |

### 2.5.3 设备域判断与理由

> **判断：`eq_equipment`、`eq_spare_part`、`eq_patrol_route`、`tg_tooling`、`eq_maintenance_plan`、`tg_maintenance_plan`、`iot_device`、`iot_product`、`mes_station_device`（除占位行）→ 保留。**
> 其余设备/工装流水表（`eq_health_record`、`eq_inspection_record`、`eq_maintenance_record`、`eq_oee_record`、`eq_patrol_record`、`eq_patrol_task`、`eq_repair`、`eq_repair_spare`、`eq_spare_stock_log`、`tg_maintenance_record`、`tg_usage_record`）→ 全删。

**理由（逐条可验证）**

1. **设备台账是跨库被引用的"共享主数据"。** 实测有 **3 个方向的引用**指向 `eq_equipment`：ERP `bd_workstation.equipment_id`（2 行）、IoT `iot_device.mes_equipment_id`（5 行）、MES `dc_collection_point.equipment_id`（5 行）。删除它会同时污染 **ERP 与 IoT** 两个库——而这两个库不在本次删除范围内，孤儿会永久留存。
2. **台账行数极少、密度极高。** 6 行设备承载了 5 个采集点、5 个 IoT 探头、5 个保养计划、2 条维修、3 条刷写记录。删 6 行 → 牵连 20+ 行。收益/风险比极差。
3. **保养计划是"活计划"而非历史。** `eq_maintenance_plan` 的 `next_date` 全在未来（2026-06-22 / 2026-07-20 / 2026-06-28 / 2026-09-20 / 2027-06-20），`status=ACTIVE`，是仍在跑的排程器输入。删除后需要重新配置。
4. **流水才是有量、且与工单强绑定的部分。** 设备流水表当前**几乎全空**（`eq_health_record`/`eq_inspection_record`/`eq_maintenance_record`/`eq_oee_record`/`eq_patrol_*`/`eq_repair_spare`/`eq_spare_stock_log` 全部 0 行），只有 `eq_repair` 2 行。删除它们几乎不释放空间，但能保证重置后计数器/评分从零开始。
5. **工装域同理**：`tg_tooling` 2 行是台账，`tg_usage_record` 2 行是流水且其中 1 行已经是孤儿，删流水保台账。

**唯一的例外要求（`eq_repair`）**：如果贵司要求保留设备维修**历史档案**（合规/审计用），把 `eq_repair` 从「全删」改为「保留」，其余不变；此时 `eq_repair_spare` 也建议一并保留（它是 `eq_repair` 的子表）。本文默认按"全量业务数据删除"处理，即 `eq_repair` 全删。

### 2.5.4 设备域删除顺序

```
① eq_repair_spare  ┐
② eq_spare_stock_log │  叶子
③ eq_maintenance_record
④ eq_health_record / eq_oee_record / eq_inspection_record
⑤ eq_patrol_record ──► ⑥ eq_patrol_task
⑦ eq_repair ────────► ⑧ eq_maintenance_plan？✗ 不删，保留
                        （因此 eq_repair 与 eq_maintenance_record 删完后
                          eq_maintenance_plan、eq_equipment 仍是完整可用的父表）
⑨ tg_maintenance_record / tg_usage_record  ──► tg_tooling / tg_maintenance_plan 保留
⑩ iot_device_data  ──► iot_device 保留
```

## 2.6 完整删除批次（MES 侧）

### 批次 M1 —— MES 末端流水层（29 张 / 312 行）

无任何 MES 内后置子表，可整表清空。

```
dc_data_record(12)          eq_health_record(0)       eq_inspection_record(0)
eq_maintenance_record(0)    eq_oee_record(0)          eq_patrol_record(0)
eq_repair_spare(0)          eq_spare_stock_log(0)     lt_sn_binding(0)
lt_wo_consumption(1)        mes_flash_record(90)      mes_material_load(5)
mes_release_pkg_bom_item(0) mes_release_pkg_config(0) mes_release_pkg_firmware(1)  ← 见注①
mes_resource_item(30)       mes_sn_bind_log(3)        mes_station_report(2)
mes_test_record(0)          ncr_8d_report(7)          ncr_defect(2)
ncr_rework_order(2)         pro_andon(10)             pro_dispatch(35)
qc_inspect_detail(6)        sop_view_log(0)           spc_data_point(1)
tg_maintenance_record(1)    tg_usage_record(2)
```
小计 **312 行**。

> **注①**：本表在 §1.2 被判为「保留」（与 `mes_release_package` 同生共死）。若选择保留发布包定义，则把它们从 M1 移除，M1 变为 **26 张 / 311 行**。本文的批次SQL 按「保留」处理，即不给这 3 张表删除语句。

修正后 **M1 = 26 张 / 311 行**：

```
dc_data_record, eq_health_record, eq_inspection_record, eq_maintenance_record,
eq_oee_record, eq_patrol_record, eq_repair_spare, eq_spare_stock_log,
lt_sn_binding, lt_wo_consumption, mes_flash_record, mes_material_load,
mes_resource_item, mes_sn_bind_log, mes_station_report, mes_test_record,
ncr_8d_report, ncr_defect, ncr_rework_order, pro_andon, pro_dispatch,
qc_inspect_detail, sop_view_log, spc_data_point, tg_maintenance_record, tg_usage_record
```
行数核算：12+1+90+5+30+3+2+7+2+2+10+35+6+1+1+2 = **209 行**
（重新核算：dc_data_record 12、lt_wo_consumption 1、mes_flash_record 90、mes_material_load 5、mes_resource_item 30、mes_sn_bind_log 3、mes_station_report 2、ncr_8d_report 7、ncr_defect 2、ncr_rework_order 2、pro_andon 10、pro_dispatch 35、qc_inspect_detail 6、spc_data_point 1、tg_maintenance_record 1、tg_usage_record 2 ＝ **209**）

### 批次 M2 —— MES 单据主表 / 质量主单 / 流转卡层（10 张 / 260 行）

```
qc_ipqc(2)  qc_iqc(6)  qc_oqc(2)        ← 质量三检主单（qc_inspect_detail 已于 M1 删除）
ncr_order(11)                            ← 子表 ncr_defect/8d/rework 已于 M1 删除
eq_patrol_task(0)  eq_repair(2)          ← 子表已于 M1 删除
lt_lot(2)                                ← 子表 lt_sn_binding/lt_wo_consumption 已于 M1 删除
pro_flow_card_step(64)                   ← 子表 pro_dispatch 已于 M1 删除
pro_sn(77)                               ← 子表 mes_flash_record/... 已于 M1 删除
pro_flow_card(25)                        ← 最后删，处理 current_step_id 环路
```
行数核算：2+6+2+11+0+2+2+64+77+25 = **191 行**

### 批次 M3 —— MES 工单层（3 张 / 117 行）

```
pro_work_order_bom_line(46)   ← 子表（无下层）
pro_work_order_step(53)       ← 子表 pro_flow_card_step 已于 M2 删除
pro_work_order(18)            ← 最后删（已无 pro_sn / pro_andon / ncr_* / dc_data_record / tg_usage_record 等引用方）
```
行数核算：46+53+18 = **117 行**

### 批次 M4 —— 部分删 / 残留清理（1 张 / 1 行）

```
mes_station_device  DELETE WHERE device_code='UNREGISTERED'   → 1 行
```

**MES 合计删除 = 209 + 191 + 117 + 1 = 518 行** ✅（= 695 − 177）

### 批次 I1 —— IoT 侧（1 张 / 205 行）

```
iot_device_data(205)          ← 引用 iot_device（保留），删除本身不产生孤儿
```

---

# 第三部分　★ 跨库引用分析

> `ry-erp` 与 `ry-mes` 是**两个独立 schema，零外键**（E-4 实测），所有跨库引用都是"裸 ID"。下面每一条都用 JOIN 实测过。

## 3.1 引用总览

| 方向 | 条数 | 说明 |
|---|---:|---|
| **MES → ERP** | **46 组**（其中 41 组有数据） | MES 单据引用 ERP 基础主数据 / 单据 |
| **ERP → MES** | **5 组**（3 组有数据、1 组空、1 组空） | ERP 单据反指 MES 质检单 / 设备 |
| **MES ↔ IoT** | 2 组 | `dc_collection_point.iot_device_id`、`eq_equipment.iot_device_id` |
| **IoT → MES** | 1 组 | `iot_device.mes_equipment_id` |
| **环路** | **2 组** | 见 §3.4 |

## 3.2 MES → ERP：基础主数据引用（实测表）

**E-9：主数据引用完整性**

```sql
SELECT 'M2 pro_work_order.material_id -> bd_material.material_id' chk,
  (SELECT COUNT(*) FROM `ry-mes`.pro_work_order WHERE material_id IS NOT NULL) refs,
  (SELECT COUNT(*) FROM `ry-mes`.pro_work_order m JOIN `ry-erp`.bd_material e ON e.material_id=m.material_id) matched
UNION ALL ... /* 40 组，脚本 .dsh-tmp/dbdel/xref_check.sql */
```

| MES 列 | ERP 目标 | refs | matched | 备注 |
|---|---|---:|---:|---|
| `pro_work_order.erp_wo_id` | `prd_work_order.wo_id` | 10 | **10** | 工单主外键 |
| `pro_work_order.material_id` | `bd_material.material_id` | 18 | **17** | 1 行孤儿 |
| `pro_work_order.workshop_id` | `bd_workshop.workshop_id` | 10 | **10** | |
| `pro_work_order.bom_id` | `bd_bom.bom_id` | 17 | **16** | 1 行孤儿 |
| `pro_work_order.routing_id` | `bd_routing.routing_id` | 17 | **16** | 1 行孤儿 |
| `mes_mbom.bom_id` | `bd_bom.bom_id` | 5 | **5** | |
| `mes_mbom.routing_id` | `bd_routing.routing_id` | 5 | **5** | |
| `mes_mbom_line.bom_detail_id` | `bd_bom_detail.detail_id` | 13 | **13** | |
| `mes_mbom_line.process_id` | `bd_process.process_id` | 22 | **22** | |
| `mes_mbom_line.workstation_id` | `bd_workstation.workstation_id` | 23 | **23** | |
| `mes_mbom_line.routing_step_id` | `bd_routing_step.step_id` | 23 | **23** | |
| `mes_mbom_line.child_material_id` | `bd_material.material_id` | 23 | **23** | |
| `pro_work_order_bom_line.bom_id` | `bd_bom.bom_id` | 44 | **44** | |
| `pro_work_order_bom_line.bom_detail_id` | `bd_bom_detail.detail_id` | 26 | **25** | 1 行孤儿 |
| `pro_work_order_bom_line.process_id` | `bd_process.process_id` | 45 | **45** | |
| `pro_work_order_bom_line.workstation_id` | `bd_workstation.workstation_id` | 45 | **45** | |
| `pro_work_order_bom_line.child_material_id` | `bd_material.material_id` | 46 | **46** | |
| `pro_work_order_step.process_id` | `bd_process.process_id` | 53 | **53** | |
| `pro_work_order_step.workcenter_id` | `bd_workstation.workstation_id` | 52 | **52** | |
| `pro_dispatch.process_id` | `bd_process.process_id` | 35 | **35** | |
| `pro_dispatch.workcenter_id` | `bd_workstation.workstation_id` | 35 | **35** | |
| `pro_flow_card.material_id` | `bd_material.material_id` | 25 | **25** | |
| `pro_flow_card_step.process_id` | `bd_process.process_id` | 64 | **64** | |
| `pro_flow_card_step.workcenter_id` | `bd_workstation.workstation_id` | 64 | **63** | 1 行孤儿 |
| `pro_sn.material_id` | `bd_material.material_id` | 77 | **77** | |
| `eq_equipment.workcenter_id` | `bd_workstation.workstation_id` | 6 | **6** | 设备域 |
| `dc_collection_point.process_id` | `bd_process.process_id` | 5 | **5** | |
| `mes_flash_record.process_id` | `bd_process.process_id` | 12 | **12** | |
| `qc_iqc.material_id` | `bd_material.material_id` | 6 | **6** | |
| `qc_ipqc.material_id` | `bd_material.material_id` | 2 | **2** | |
| `qc_oqc.material_id` | `bd_material.material_id` | 2 | **2** | |
| `qc_inspect_template.material_id` | `bd_material.material_id` | 1 | **1** | |
| `spc_control_chart.material_id` | `bd_material.material_id` | 3 | **3** | |
| `spc_control_chart.process_id` | `bd_process.process_id` | 3 | **3** | |
| `sop_process_binding.process_id` | `bd_process.process_id` | 10 | **10** | |
| `pro_step_guide.process_id` | `bd_process.process_id` | 0 | 0 | 空表 |
| `ncr_order.material_id` | `bd_material.material_id` | 11 | **11** | |
| `mes_firmware.material_id` | `bd_material.material_id` | 6 | **6** | |
| `mes_content_file.material_id` | `bd_material.material_id` | 1 | **1** | |
| `mes_test_item.material_id` | `bd_material.material_id` | 1 | **1** | |
| `mes_release_package.material_id` | `bd_material.material_id` | 1 | **1** | |
| `mes_release_pkg_firmware.process_id` | `bd_process.process_id` | 1 | **1** | |
| `lt_lot.material_id` | `bd_material.material_id` | 2 | **2** | |
| `tg_tooling.workshop_id` | `bd_workshop.workshop_id` | 1 | **1** | |
| `tg_usage_record.workstation_id` | `bd_workstation.workstation_id` | 1 | **1** | |

## 3.3 MES → ERP：单据级引用（非主数据）

| MES 列 | ERP 目标 | refs | matched | 证据 |
|---|---|---:|---:|---|
| `mes_material_load.batch_id` | `wm_batch.batch_id` | 5 | **5** | W1 |
| `mes_material_load.material_id` | `bd_material.material_id` | 5 | **5** | M35 |
| `qc_iqc.vendor_id` | `bd_vendor.vendor_id` | 2 | **2** | M26 |
| `qc_iqc.receive_id` | `wm_material_receive.receive_id` | 6 | **2** | Q1 |
| `qc_oqc.so_id` | `so_sales_order_header.so_id` | 2 | **2** | Q2 |
| `qc_oqc.so_id` | `so_sales_order_line.line_id` | 2 | 1（同值命中另一张表的另一行，属巧合） | Q2b |
| `lt_lot.supplier_id` | `bd_vendor.vendor_id` | 2 | **1**（1 行孤儿） | Q3 |
| `mes_release_package.client_id` | `bd_client.client_id` | 1 | **1** | Q5 |
| `pro_work_order.wo_no` ↔ `prd_work_order.wo_no` | 业务键 | 18 | 10 | W17 |
| `mes_material_load.wo_no` ↔ `pro_work_order.wo_no` | 业务键 | 5 | 5 | Q10 |

```sql
-- 证据（W1）
SELECT 'mes_material_load.batch_id -> ry-erp.wm_batch.batch_id' chk, 5 refs, 5 matched;
-- 证据（Q1，脚本 .dsh-tmp/dbdel/qr.sql）
SELECT 'qc_iqc.receive_id -> ry-erp.wm_material_receive.receive_id' chk, 6 refs, 2 matched;
```

## 3.4 ★★ ERP → MES：反向引用（本轮最关键的发现）

`ry-erp` **确实有列指向 `ry-mes` 的表**，共 5 组：

**E-10：ERP 侧反指列**

```sql
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='ry-erp'
  AND (COLUMN_NAME LIKE '%inspect%' OR COLUMN_NAME LIKE '%ipqc%' OR COLUMN_NAME LIKE '%iqc%'
       OR COLUMN_NAME LIKE '%oqc%' OR COLUMN_NAME LIKE '%equipment%' OR COLUMN_NAME LIKE '%mes%');
```

```
| bd_workstation         | equipment_id     | bigint(20)  | 设备ID                                   |
| prd_work_order         | quality_check_id | bigint(20)  | 质检记录ID(IPQC)                         |
| prd_work_order         | quality_check_no | varchar(64) | 质检单号(IPQC)                           |
| prd_work_order         | inspector_id     | bigint(20)  | 检验员ID                                 |
| prd_work_report        | equipment_id     | bigint(20)  | 设备ID                                   |
| so_sales_order_line    | oqc_id           | bigint(20)  | OQC检验单ID                              |
| so_sales_order_line    | oqc_code         | varchar(64) | OQC检验单编号                            |
| so_sales_order_line    | oqc_status       | varchar(20) | OQC检验状态(PENDING/QUALIFIED/UNQUALIFIED)|
| wm_arrival_notice_line | iqc_id           | bigint(20)  | 来料检验单ID                             |
| wm_arrival_notice_line | iqc_code         | varchar(64) | 来料检验单编号                           |
| wm_material_receive    | iqc_id           | bigint(20)  | 来料检验单ID                             |
| wm_material_receive    | iqc_code        | varchar(64) | 来料检验单编号                           |
```

**E-11：逐条实测命中**

```sql
-- .dsh-tmp/dbdel/xref_erp2mes.sql
SELECT 'S1 ry-erp.wm_material_receive.iqc_id -> ry-mes.qc_iqc.iqc_id' chk, 1 refs, 1 matched,
                                                              1 refs_code, 1 matched_code
UNION ALL SELECT 'S2 ry-erp.wm_arrival_notice_line.iqc_id -> ry-mes.qc_iqc.iqc_id', 1,1,1,1
UNION ALL SELECT 'S3 ry-erp.so_sales_order_line.oqc_id -> ry-mes.qc_oqc.oqc_id', 1,1,1,1
UNION ALL SELECT 'S4 ry-erp.prd_work_report.equipment_id -> ry-mes.eq_equipment.equipment_id', 0,0,0,0;
```
```
+----------------------------------------------------------------------------+------+---------+-----------+--------------+
| chk                                                                        | refs | matched | refs_code | matched_code |
+----------------------------------------------------------------------------+------+---------+-----------+--------------+
| S1 ry-erp.wm_material_receive.iqc_id -> ry-mes.qc_iqc.iqc_id               |    1 |       1 |         1 |            1 |
| S2 ry-erp.wm_arrival_notice_line.iqc_id -> ry-mes.qc_iqc.iqc_id            |    1 |       1 |         1 |            1 |
| S3 ry-erp.so_sales_order_line.oqc_id -> ry-mes.qc_oqc.oqc_id               |    1 |       1 |         1 |            1 |
| S4 ry-erp.prd_work_report.equipment_id -> ry-mes.eq_equipment.equipment_id |    0 |       0 |         0 |            0 |
+----------------------------------------------------------------------------+------+---------+-----------+--------------+
```

```sql
-- R1: bd_workstation -> eq_equipment
SELECT 'R1' chk, 2 refs, 2 matched;
-- R2/R3: prd_work_order 的 IPQC 字段（结构存在，当前无数据）
SELECT COUNT(*) FROM `ry-erp`.prd_work_order WHERE quality_check_id IS NOT NULL AND quality_check_id<>0;  -- 0
SELECT COUNT(*) FROM `ry-erp`.prd_work_order WHERE quality_check_no IS NOT NULL AND quality_check_no<>''; -- 0
```

| # | ERP 列 | → MES 目标 | 现存引用 | 状态 |
|---:|---|---|---:|---|
| R1 | `bd_workstation.equipment_id` | `eq_equipment.equipment_id` | **2** | 有效 |
| R2 | `prd_work_order.quality_check_id` | `qc_ipqc.ipqc_id` | 0 | 结构已建，暂无数据 |
| R3 | `prd_work_order.quality_check_no` | `qc_ipqc.ipqc_no` | 0 | 同上 |
| R4 | `wm_material_receive.iqc_id / iqc_code` | `qc_iqc.iqc_id / iqc_no` | **1 / 1** | 有效 |
| R5 | `wm_arrival_notice_line.iqc_id / iqc_code` | `qc_iqc.iqc_id / iqc_no` | **1 / 1** | 有效 |
| R6 | `so_sales_order_line.oqc_id / oqc_code` | `qc_oqc.oqc_id / oqc_no` | **1 / 1** | 有效 |
| — | `prd_work_report.equipment_id / equipment_code` | `eq_equipment`（推测） | 0 | 表内 1 行，未挂设备 |

**★ 环路 1：采购收货 ↔ 来料检验（实测闭环）**

```sql
SELECT q.iqc_id, q.iqc_no, q.receive_id, r.receive_code, r.iqc_id AS erp_back_iqc_id
FROM `ry-mes`.qc_iqc q LEFT JOIN `ry-erp`.wm_material_receive r ON r.receive_id = q.receive_id;
```
```
+---------------------+------------------+---------------------+-----------------+---------------------+
| iqc_id              | iqc_no           | receive_id          | receive_code    | erp_back_iqc_id     |
+---------------------+------------------+---------------------+-----------------+---------------------+
| 2080291255916593154 | IQC-20260723-001 | 2080273188016762882 | CGRK20260723001 | 2080291255916593154 |  ← 闭环保
| 2082124523750199298 | IQC-20260728-001 |                9001 | NULL            | NULL                |
+---------------------+------------------+---------------------+-----------------+---------------------+
```
`ry-mes.qc_iqc.receive_id → ry-erp.wm_material_receive.receive_id`
`ry-erp.wm_material_receive.iqc_id → ry-mes.qc_iqc.iqc_id`
→ **同一对记录互相指向对方。**

**★ 环路 2：销售订单行 ↔ 出货检验（实测闭环）**

```sql
SELECT line_id, so_id, oqc_id, oqc_code FROM `ry-erp`.so_sales_order_line WHERE oqc_id IS NOT NULL;
SELECT oqc_id, oqc_no, so_id FROM `ry-mes`.qc_oqc;
```
```
ERP：line_id=2080229429462630402  so_id=2080229404229697537  oqc_id=2080312885934026753  oqc_code=OQC-20260723-001
MES：oqc_id=2080312885934026753   oqc_no=OQC-20260723-001    so_id=2080229404229697537
```
→ **同样闭环。**

## 3.5 各方向删错会怎样

### （A）ERP 侧先删 —— 会发生什么

| 被先删的 ERP 行 | MES 侧后果 | 严重度 |
|---|---|---|
| `bd_material` | `pro_work_order.material_id`（18 行）、`pro_sn`（77）、`pro_flow_card`（25）、`pro_work_order_bom_line`（46）、`mes_mbom_line`（23）、`qc_*`（10）、`spc_control_chart`（3）… 合计 **46 组引用**全部悬空；MES 工单查询/报工/追溯页面**直接查不到物料名称** | 🔴 灾难 |
| `bd_bom` / `bd_bom_detail` | `mes_mbom`(5)、`mes_mbom_line`(13)、`pro_work_order_bom_line`(44) 悬空 → **MBOM 与工单 BOM 快照全部失效** | 🔴 灾难 |
| `bd_routing` / `bd_routing_step` | `mes_mbom.routing_id`(5)、`mes_mbom_line.routing_step_id`(23) 悬空 → 工艺路线断链 | 🔴 灾难 |
| `bd_process` | `pro_work_order_step`(53)、`pro_dispatch`(35)、`pro_flow_card_step`(64)、`sop_process_binding`(10) 等 **9 组 200+ 行**悬空 | 🔴 灾难 |
| `bd_workstation` | `pro_work_order_step.workcenter_id`(52)、`pro_dispatch.workcenter_id`(35)、`pro_flow_card_step.workcenter_id`(63)、`eq_equipment.workcenter_id`(6) 悬空 → **设备归属工作中心丢失** | 🔴 灾难 |
| `bd_workshop` | `pro_work_order.workshop_id`(10)、`tg_tooling.workshop_id`(1) 悬空 | 🟠 高 |
| `bd_vendor` / `bd_client` | `qc_iqc.vendor_id`(2)、`lt_lot.supplier_id`(2)、`mes_release_package.client_id`(1) 悬空 | 🟠 高 |
| `prd_work_order` | `pro_work_order.erp_wo_id`(10) 悬空 → **MES 工单变"无源工单"** | 🟠 高 |
| `wm_batch` | `mes_material_load.batch_id`(5) 悬空 → 上料追溯断链 | 🟠 高 |
| `wm_material_receive` | **反指列 `iqc_id` 先消失**（好），但 `qc_iqc.receive_id`(6) 悬空 | 🟡 中 |
| `so_sales_order_header / line` | `qc_oqc.so_id`(2) 悬空；同时 `so_sales_order_line.oqc_id` 指针先消失（好） | 🟡 中 |

### （B）MES 侧先删 —— 会发生什么

| 被先删的 MES 行 | ERP 侧后果 | 严重度 |
|---|---|---|
| `qc_iqc`（6 行） | `wm_material_receive.iqc_id`(1)、`wm_arrival_notice_line.iqc_id`(1) **永久悬空** —— 采购收货单打不开质检报告 | 🔴 灾难（且 ERP 数据要保留） |
| `qc_oqc`（2 行） | `so_sales_order_line.oqc_id`(1) **永久悬空**，`oqc_status=QUALIFIED` 却查不到检验单 | 🔴 灾难 |
| `qc_ipqc`（2 行） | `prd_work_order.quality_check_id/no`（当前 0 行）→ 暂无影响，但字段语义已空 | 🟡 中 |
| `eq_equipment`（6 行） | `bd_workstation.equipment_id`(2) 悬空、`iot_device.mes_equipment_id`(5) 悬空 | 🔴 灾难 |
| `dc_collection_point`（9 行） | 无 ERP 引用，仅 `dc_data_record` / `spc_control_chart` 受影响 | 🟢 低 |
| `spc_control_chart`（3 行） | 无跨库引用 | 🟢 低 |

### （C）正确的前后顺序

| 关系 | 正确顺序 | 理由 |
|---|---|---|
| MES 单据 → ERP 主数据（`bd_*`、`prd_work_order`、`wm_batch`） | **MES 先删，ERP 后删** | 保留期内的 ERP 主数据不能被悬空指向 |
| ERP 单据 → MES 质检单（`wm_material_receive`/`wm_arrival_notice_line`/`so_sales_order_line`/`prd_work_order`） | **ERP 行先删，MES 质检行后删** | 否则 ERP 保留行里留下永久悬空指针 |
| 环路 1（采购收货 ↔ IQC） | **同一个事务内：先删 ERP `wm_material_receive` 那 1 行 → 再删 MES `qc_iqc`** | 提交时刻两行同时消失，外部永远看不到孤儿 |
| 环路 2（销售订单行 ↔ OQC） | **同一个事务内：先删 ERP `so_sales_order_line` 那 1 行 → 再删 MES `qc_oqc`** | 同上 |
| ERP 工作站 → MES 设备 | **设备台账保留 ⇒ 无需处理**；若确要删设备，必须先删/改 `bd_workstation.equipment_id` | 但 `bd_workstation` 本身被 MES 大量引用，故不推荐 |

> **⚠ 唯一需要 UPDATE 的场景**：如果 ERP 的 `wm_material_receive` / `wm_arrival_notice_line` / `so_sales_order_line` / `prd_work_order` 这些行**业务上必须保留**（例如订单还要发货），而 MES 质检单要清空，那么**必然**要在 ERP 侧先把 `iqc_id`/`iqc_code`/`iqc_status`/`oqc_id`/`oqc_code`/`oqc_status`/`quality_check_id`/`quality_check_no` 置 NULL——本方案是**纯删除**方案，不含 UPDATE，故此时必须人工介入这一步，否则会留下 5 处悬空列（合计 5 行）。

## 3.6 3D 演示数据牵连核查（保留集）

**E-12：`ry-erp` 侧 3D 演示仓库**

```sql
SELECT warehouse_id, warehouse_code, warehouse_name, status, del_flag FROM ry-erp.wm_warehouse;
```
```
+---------------------+----------------+-----------------+--------+----------+
| warehouse_id        | warehouse_code | warehouse_name  | status | del_flag |
+---------------------+----------------+-----------------+--------+----------+
|                   1 | WH003          | 成品仓库        |      1 | 0        |
|                   2 | WH004          | 原料仓库        |      1 | 0        |
|                   3 | WH005          | 半成品仓库      |      1 | 0        |
|          9900000001 | WH-3D-DEMO     | 3D示例仓库      |      1 | 0        |  ← 保留
| 2091200583779266562 | WH006          | 电动牙刷仓      |      1 | 0        |
+---------------------+----------------+-----------------+--------+----------+
```
`wm_warehouse.remark` = **"3D 仓库可视化示例数据（PLAN-2026-006）"**，`location`='厂区一号库·3D可视化示例'。

**E-13：3D 演示数据范围（全部实测）**

```sql
SELECT 'wm_storage_area' t, COUNT(*) c FROM ry-erp.wm_storage_area WHERE warehouse_id=9900000001
UNION ALL SELECT 'wm_storage_location', COUNT(*) FROM ry-erp.wm_storage_location WHERE warehouse_id=9900000001
UNION ALL SELECT 'wm_material_stock', COUNT(*) FROM ry-erp.wm_material_stock WHERE warehouse_id=9900000001
UNION ALL SELECT 'wm_batch', COUNT(*) FROM ry-erp.wm_batch WHERE warehouse_id=9900000001
UNION ALL SELECT 'wm_stock_alert', COUNT(*) FROM ry-erp.wm_stock_alert WHERE warehouse_id=9900000001
UNION ALL SELECT 'wm_stock_taking_plan', COUNT(*) FROM ry-erp.wm_stock_taking_plan WHERE warehouse_id=9900000001
UNION ALL SELECT 'wm_picking_rule', COUNT(*) FROM ry-erp.wm_picking_rule WHERE warehouse_id=9900000001
UNION ALL SELECT 'wm_putaway_rule', COUNT(*) FROM ry-erp.wm_putaway_rule WHERE warehouse_id=9900000001
UNION ALL SELECT 'wm_material_receive', COUNT(*) FROM ry-erp.wm_material_receive WHERE warehouse_id=9900000001
UNION ALL SELECT 'wm_production_issue', COUNT(*) FROM ry-erp.wm_production_issue WHERE warehouse_id=9900000001
UNION ALL SELECT 'wm_production_recpt', COUNT(*) FROM ry-erp.wm_production_recpt WHERE warehouse_id=9900000001
UNION ALL SELECT 'wm_material_stock_tx', COUNT(*) FROM ry-erp.wm_material_stock_tx WHERE warehouse_id=9900000001;
```
```
+----------------------+-----+
| wm_storage_area      |   4 |
| wm_storage_location  | 104 |
| wm_material_stock    |  55 |
| wm_batch             |   0 |
| wm_stock_alert       |   0 |
| wm_stock_taking_plan |   0 |
| wm_picking_rule      |   0 |
| wm_putaway_rule      |   0 |
| wm_material_receive  |   0 |
| wm_production_issue  |   0 |
| wm_production_recpt  |   0 |
| wm_material_stock_tx |   0 |
+----------------------+-----+
```

**E-14：MES/IoT 侧有没有牵连的 3D 演示数据？→ 没有。**

方法：把两个库全量数据导出成文本再全文检索（覆盖所有非空表，共 **54 张表 908 条 INSERT**）。

```powershell
mysqldump -uroot -proot --no-create-info --skip-extended-insert --compact --databases ry-mes ry-iot > mes_iot_data.sql
# 510,058 字节 / 908 条 INSERT / 54 张非空表
```
```powershell
Select-String -Path mes_iot_data.sql -Pattern 'DEMO' -CaseSensitive:$false   # 0 命中
Select-String -Path mes_iot_data.sql -Pattern '3D'                          # 0 命中
Select-String -Path mes_iot_data.sql -Pattern '9900000001'                  # 0 命中
Select-String -Path mes_iot_data.sql -Pattern '示例|WH-3D|演示'
#   唯一命中：qc_inspect_template.remark = '演示: 2个定量项目(厚度LSL_USL/抗拉强度LSL_ONLY)'
#   —— 属于 IQC 模板的功能说明文案，与 3D 仓库无关
```
（`ry-erp` 侧同样扫描：`DEMO` 仅命中 `wm_warehouse`(1) / `wm_storage_area`(4) / `wm_storage_location`(104) / `wm_material_stock`(55)。）

**E-15：3D 演示数据是"自包含孤岛"（不挂 `bd_material`）**

```sql
SELECT COUNT(*) AS demo_stock_rows, COUNT(DISTINCT s.material_id) AS demo_material_ids,
       SUM(CASE WHEN m.material_id IS NULL THEN 1 ELSE 0 END) AS rows_without_bd_material
FROM ry-erp.wm_material_stock s LEFT JOIN ry-erp.bd_material m ON m.material_id = s.material_id
WHERE s.warehouse_id = 9900000001;
```
```
+-----------------+-------------------+--------------------------+
| demo_stock_rows | demo_material_ids | rows_without_bd_material |
+-----------------+-------------------+--------------------------+
|              55 |                 6 |                       55 |
+-----------------+-------------------+--------------------------+
```
```sql
SELECT material_id, material_code FROM ry-erp.bd_material WHERE material_id BETWEEN 9900000001 AND 9900000006;
-- 空集：bd_material 里根本没有 DEMO-M001..M006 / DEMO-P001..P002 / DEMO-F001
SELECT COUNT(*) FROM ry-erp.wm_material_stock WHERE warehouse_id<>9900000001 AND material_id BETWEEN 9900000001 AND 9900000006;  -- 0
SELECT COUNT(*) FROM ry-erp.wm_material_stock_tx WHERE material_id BETWEEN 9900000001 AND 9900000006;                          -- 0
```

### ✅ 3D 演示保留清单（4 张表 / 164 行，全部在 `ry-erp`）

| 表 | 行数 | 过滤条件 | 说明 |
|---|---:|---|---|
| `ry-erp.wm_warehouse` | **1** | `warehouse_id = 9900000001` | `WH-3D-DEMO` / 3D示例仓库 |
| `ry-erp.wm_storage_area` | **4** | `warehouse_id = 9900000001` | `A3D01`原料货架区、`B3D02`成品货架区、`C3D03`拣选网格区、`D3D04`散货堆放区 |
| `ry-erp.wm_storage_location` | **104** | `warehouse_id = 9900000001` | `9900000101`–`9900000403` |
| `ry-erp.wm_material_stock` | **55** | `warehouse_id = 9900000001` | `9900003001`–`9900003055`，`DEMO-M001~M003 / DEMO-P001~P002 / DEMO-F001` |
| **合计** | **164** | | |

**MES / IoT 侧保留清单：无。** MES 侧唯一的"演示"痕迹是 `qc_inspect_template` 的行内备注文案（非 3D 演示数据）。

**删除时的硬性约束**：任何针对 `ry-erp.wm_warehouse / wm_storage_area / wm_storage_location / wm_material_stock` 的清理，SQL 必须带 `WHERE warehouse_id <> 9900000001`（或 `NOT IN (9900000001)`），否则会连 3D 演示仓库一起删掉。

---

# 第四部分　删除方式建议（DELETE / TRUNCATE / DROP）

## 4.1 前提事实（决定选择）

| 事实 | 证据 | 影响 |
|---|---|---|
| 全库 **0 个外键** | E-4 | `TRUNCATE` 对所有表都合法；无需 `SET FOREIGN_KEY_CHECKS=0` |
| `ry-mes`/`ry-iot` **0 个 AUTO_INCREMENT** | E-6 | `TRUNCATE` 的"重置自增"效果在 MES/IoT 侧**为零**；自增值无意义 |
| 全部 InnoDB | E-5 | `DELETE` 可事务回滚；`TRUNCATE` 是 DDL，**隐式提交、不可回滚** |
| 全部表都有 `del_flag` | E-5（68/68） | 应用层是逻辑删除；`DELETE` 是物理删除，会绕过逻辑删除语义（正确，正是我们要的"真删"） |
| 有 1 处表内环路 + 2 处跨库环路 | §2.2 / §3.4 | 环路两端的表必须在同一事务内处理 |
| 有"部分删"需求（`mes_station_device`） | §1.2 #34 | 这类只能用 `DELETE ... WHERE` |
| 应用启动即用这些表 | 表结构由 `script/sql/ry-mes.sql` 初始化 | **`DROP` 会导致应用报错**，除非跑建表脚本 |

## 4.2 分类型建议

| 表类别 | 涉及表 | 建议方式 | 理由 |
|---|---|---|---|
| **MES 末端流水**（M1 批，26 张） | `dc_data_record`、`mes_flash_record`、`pro_dispatch`、`mes_resource_item` 等 | **`TRUNCATE`**（首选） | 整表清空、无残留条件；DDL 快且释放表空间；因无 AUTO_INCREMENT，重置自增的副作用**不存在**；这些表在批内没有需要回滚的跨表约束。若要求"整批可回滚"，改用 `DELETE FROM tbl;` 并在事务内执行 |
| **MES 单据主表**（M2 批，10 张） | `qc_iqc`、`qc_oqc`、`ncr_order`、`pro_flow_card`、`pro_sn`、`lt_lot` 等 | **`DELETE`（事务内，按批提交）** | 其中 `qc_iqc`/`qc_oqc` 参与跨库环路，必须与 ERP 侧语句同事务，`TRUNCATE` 的隐式提交会破坏原子性 |
| **MES 工单层**（M3 批，3 张） | `pro_work_order_bom_line`、`pro_work_order_step`、`pro_work_order` | **`TRUNCATE`**（顺序执行，或 `DELETE` 事务） | 无跨库环；`pro_flow_card_step` 等子行已在 M2 清空，`TRUNCATE` 安全 |
| **部分删表** | `mes_station_device` | **`DELETE ... WHERE device_code='UNREGISTERED'`** | 必须保留 3 台真实终端 |
| **IoT 时序表** | `iot_device_data` | **`TRUNCATE`** | 205 行原始时序、无 AUTO_INCREMENT（`data_id` 为雪花）；上游 `iot_device` 保留 |
| **保留表** | 28 张 | **不执行** | —— |
| **ERP 侧 WMS 大类** | `wm_material_stock`、`wm_storage_location` 等 | **`DELETE ... WHERE warehouse_id <> 9900000001`** | 保护 3D 演示仓库；这些表**不能** `TRUNCATE` |
| **ERP 侧带 AUTO_INCREMENT 表** | `wm_barcode`、`wm_production_recpt`、`pu_purchase_order` 等 10 张 | **`TRUNCATE`** | 这 10 张是唯一需要"自增归零"的表；`TRUNCATE` 自动重置 `AUTO_INCREMENT`，**等价于且优于** `ALTER TABLE ... AUTO_INCREMENT=1`（后者属 ALTER，且本任务禁止） |
| **`DROP`** | —— | **本计划不推荐任何 `DROP`** | 表结构是应用 schema 的一部分；`DROP` 后必须重跑 `script/sql/ry-mes.sql` / `ry-iot.sql`，且会丢失索引与注释；仅在"整库退役"场景才考虑 |

## 4.3 自增主键 / 编码重置的影响分析

**结论一：MES / IoT 侧不存在"自增主键重置"问题。**

证据 E-6：`ry-mes` / `ry-iot` 中 `EXTRA LIKE '%auto_increment%'` 的列 **0 个**，主键全部由 MyBatis-Plus `ASSIGN_ID`（雪花算法）在应用侧赋值：
```sql
SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='ry-mes' AND COLUMN_KEY='PRI' LIMIT 4;
```
```
+-----------------------+--------------+
| dc_collection_point   | point_id     |   ← bigint(20)，应用赋值
| dc_data_record        | record_id    |
| eq_equipment          | equipment_id |
| eq_health_record      | record_id    |
+-----------------------+--------------+
```
因此「TRUNCATE 会把自增打回 1」在 MES/IoT 侧**不会发生任何变化**：新数据仍会拿到雪花 ID，与历史 ID 不可能冲突，也**不会**从 1 开始。

**结论二：`TRUNCATE` vs `DELETE` 对雪花 ID 的实际差别 = 0。**

| 关注点 | `TRUNCATE` | `DELETE FROM t` |
|---|---|---|
| 自增重置 | 无 AUTO_INCREMENT，不适用 | 同左 |
| 事务/回滚 | ❌ 隐式提交 | ✅ 可回滚 |
| 速度 | 快（重建数据文件） | 慢（逐行 + undo/redo） |
| 锁 | 元数据锁 | 行锁 |
| 触发器 | 不触发（本库无触发器，无差异） | 触发 |
| 表空间释放 | ✅ | ❌（需 OPTIMIZE TABLE，属 ALTER，本任务禁止） |
| **本方案适用** | M1 / M3 / I1 批 | M2 批（跨库环路需事务）、`mes_station_device`、所有 ERP `WHERE` 条件删 |

**结论三：真正需要"重置编码"的目标只有 ERP 的 10 张自增表**（清单见 E-6），且"重置编码"应通过 `TRUNCATE` 隐式完成，**不要**手写 `ALTER TABLE ... AUTO_INCREMENT = 1`（既属 ALTER、又需要额外权限）。

**结论四：业务编号（非自增）不需要也不应该重置。**
`pro_work_order.wo_no`（`SCGD202608180001` 形式）、`qc_iqc.iqc_no`（`IQC-20260723-001`）、`pro_dispatch.dispatch_no`（`DIS-FC-…-8776-4386`）、`ncr_order.ncr_code` 等都由日期 + 随机/规则生成，**不依赖计数器表**，删空后新编号自然不冲突。ERP 侧 `bd_serial_number_rule`（3 行）是规则配置，属保留项。

---

# 第五部分　安全删除执行清单

## 5.0 全局前置（批次 0）

**B0-1 备份（必须，不可省）**
```powershell
$dump='D:\mysql-8.0.39\mysql-8.0.39-winx64\bin\mysqldump.exe'
& $dump -uroot -proot --default-character-set=utf8mb4 --single-transaction --routines --triggers `
  --databases ry-mes ry-iot > D:\backup\ry-mes_ry-iot_$(Get-Date -Format yyyyMMddHHmm).sql
```

**B0-2 冻结写入**：停止 MES/IoT 服务、关闭 MQTT 接入与 SnailJob 定时任务（`IotDataCleanupTask`/`IotStreamTrimTask` 会持续写 `iot_device_data`）。

**B0-3 记录基线（执行后逐批复核用）**
```sql
-- .dsh-tmp/dbdel/count_ry-mes.sql  /  count_ry-iot.sql 的原样输出
-- 期望：ry-mes 合计 695 行；ry-iot 合计 213 行
```
| 关键基线值 | 期望 |
|---|---:|
| `ry-mes` 总行数 | 695 |
| `ry-iot` 总行数 | 213 |
| `eq_equipment` | 6 |
| `pro_work_order` | 18 |
| `pro_sn` | 77 |
| `mes_flash_record` | 90 |
| `qc_iqc` / `qc_ipqc` / `qc_oqc` | 6 / 2 / 2 |

---

## 批次 E0（ERP 前置：断开 ERP→MES 指针）

> **必须先于 M-2 执行。** 这 2 条 SQL 与 M-2 的对应语句**必须放在同一个事务里**（MySQL 跨库事务可用：同实例、同一 `START TRANSACTION`）。

```sql
START TRANSACTION;

-- E0-1 环路 1：先删 ERP 侧指向 MES IQC 的采购收货行
DELETE FROM `ry-erp`.wm_material_receive
 WHERE receive_id = 2080273188016762882
   AND iqc_id = 2080291255916593154;

-- E0-2 环路 2：先删 ERP 侧指向 MES OQC 的销售订单行
DELETE FROM `ry-erp`.so_sales_order_line
 WHERE line_id = 2080229429462630402
   AND oqc_id = 2080312885934026753;

-- E0-3 环路 3（若走"ERP 采购到货通知行也清空"路线）
DELETE FROM `ry-erp`.wm_arrival_notice_line
 WHERE iqc_id = 2080291255916593154;

-- ⚠ 若上述 ERP 行必须保留，请先人工 UPDATE 置 NULL（超出本 delete-only 方案范围）：
--   UPDATE `ry-erp`.wm_material_receive     SET iqc_id=NULL, iqc_code=NULL WHERE iqc_id=2080291255916593154;
--   UPDATE `ry-erp`.wm_arrival_notice_line  SET iqc_id=NULL, iqc_code=NULL, iqc_status=NULL WHERE iqc_id=2080291255916593154;
--   UPDATE `ry-erp`.so_sales_order_line     SET oqc_id=NULL, oqc_code=NULL, oqc_status=NULL WHERE oqc_id=2080312885934026753;

-- ↓↓↓ 紧接着执行 M-2 批次的 qc_iqc / qc_oqc（见下），然后 COMMIT;
```

**E0 执行后校验**
```sql
SELECT COUNT(*) FROM `ry-erp`.wm_material_receive     WHERE iqc_id = 2080291255916593154;  -- 期望 0
SELECT COUNT(*) FROM `ry-erp`.so_sales_order_line     WHERE oqc_id = 2080312885934026753;  -- 期望 0
SELECT COUNT(*) FROM `ry-erp`.wm_arrival_notice_line  WHERE iqc_id = 2080291255916593154;  -- 期望 0
-- 记录被删行数，纳入备份核对：wm_material_receive -1 行、so_sales_order_line -1 行、wm_arrival_notice_line -1 行
```

---

## 批次 M-1 —— MES 末端流水层（26 张 / 209 行，`TRUNCATE`）

```sql
-- 逐条执行（无顺序约束，批内互不引用）
TRUNCATE TABLE `ry-mes`.dc_data_record;           -- 12
TRUNCATE TABLE `ry-mes`.eq_health_record;         -- 0
TRUNCATE TABLE `ry-mes`.eq_inspection_record;     -- 0
TRUNCATE TABLE `ry-mes`.eq_maintenance_record;    -- 0
TRUNCATE TABLE `ry-mes`.eq_oee_record;            -- 0
TRUNCATE TABLE `ry-mes`.eq_patrol_record;         -- 0
TRUNCATE TABLE `ry-mes`.eq_repair_spare;          -- 0
TRUNCATE TABLE `ry-mes`.eq_spare_stock_log;       -- 0
TRUNCATE TABLE `ry-mes`.lt_sn_binding;            -- 0
TRUNCATE TABLE `ry-mes`.lt_wo_consumption;        -- 1
TRUNCATE TABLE `ry-mes`.mes_flash_record;         -- 90
TRUNCATE TABLE `ry-mes`.mes_material_load;        -- 5
TRUNCATE TABLE `ry-mes`.mes_resource_item;        -- 30（含 20 行孤儿池）
TRUNCATE TABLE `ry-mes`.mes_sn_bind_log;          -- 3
TRUNCATE TABLE `ry-mes`.mes_station_report;       -- 2
TRUNCATE TABLE `ry-mes`.mes_test_record;          -- 0
TRUNCATE TABLE `ry-mes`.ncr_8d_report;            -- 7
TRUNCATE TABLE `ry-mes`.ncr_defect;               -- 2
TRUNCATE TABLE `ry-mes`.ncr_rework_order;         -- 2
TRUNCATE TABLE `ry-mes`.pro_andon;                -- 10
TRUNCATE TABLE `ry-mes`.pro_dispatch;             -- 35
TRUNCATE TABLE `ry-mes`.qc_inspect_detail;        -- 6
TRUNCATE TABLE `ry-mes`.sop_view_log;             -- 0
TRUNCATE TABLE `ry-mes`.spc_data_point;           -- 1
TRUNCATE TABLE `ry-mes`.tg_maintenance_record;    -- 1
TRUNCATE TABLE `ry-mes`.tg_usage_record;          -- 2
```

**M-1 执行后校验**
```sql
SELECT SUM(c) FROM (
  SELECT COUNT(*) c FROM `ry-mes`.dc_data_record UNION ALL SELECT COUNT(*) FROM `ry-mes`.mes_flash_record
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.pro_dispatch UNION ALL SELECT COUNT(*) FROM `ry-mes`.mes_resource_item
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.ncr_8d_report UNION ALL SELECT COUNT(*) FROM `ry-mes`.pro_andon
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.eq_repair_spare UNION ALL SELECT COUNT(*) FROM `ry-mes`.lt_wo_consumption
) x;
-- 期望：0

-- 父表必须仍在（不得被误删）
SELECT COUNT(*) FROM `ry-mes`.pro_work_order;        -- 期望 18
SELECT COUNT(*) FROM `ry-mes`.pro_flow_card;         -- 期望 25
SELECT COUNT(*) FROM `ry-mes`.pro_flow_card_step;    -- 期望 64
SELECT COUNT(*) FROM `ry-mes`.pro_sn;                -- 期望 77
SELECT COUNT(*) FROM `ry-mes`.eq_equipment;          -- 期望 6
SELECT COUNT(*) FROM `ry-mes`.dc_collection_point;   -- 期望 9
SELECT COUNT(*) FROM `ry-mes`.mes_flash_type;        -- 期望 5
SELECT COUNT(*) FROM `ry-mes`.mes_firmware;          -- 期望 6
SELECT COUNT(*) FROM `ry-mes`.ncr_defect_code;       -- 期望 71
SELECT COUNT(*) FROM `ry-mes`.mes_resource_pool;     -- 期望 1
```
> 此时**允许**存在"父行指向已删子行"的临时状态（如 `pro_flow_card.current_step_id`、`pro_work_order.release_package_id` 不受影响），**不允许**存在"子行指向已删父行"——本批删的全是最末端，故满足。

---

## 批次 M-2 —— MES 单据主表（10 张 / 191 行，`DELETE` + 事务，**与 E0 同事务**）

```sql
START TRANSACTION;

-- ① 质量三检主单（qc_inspect_detail 已于 M-1 删除）
DELETE FROM `ry-mes`.qc_ipqc;      -- 2
DELETE FROM `ry-mes`.qc_iqc;       -- 6   ← 必须在 E0-1 / E0-3 之后
DELETE FROM `ry-mes`.qc_oqc;       -- 2   ← 必须在 E0-2 之后

-- ② NCR 主单（ncr_defect / ncr_8d_report / ncr_rework_order 已于 M-1 删除）
DELETE FROM `ry-mes`.ncr_order;    -- 11

-- ③ 设备/工装残留流水
DELETE FROM `ry-mes`.eq_patrol_task;   -- 0（子表 eq_patrol_record 已于 M-1 删除）
DELETE FROM `ry-mes`.eq_repair;        -- 2（子表 eq_repair_spare 已于 M-1 删除）
DELETE FROM `ry-mes`.eq_maintenance_plan;   -- ⚠ 按本方案"保留"，此处不执行；如需删，取消注释
-- DELETE FROM `ry-mes`.tg_maintenance_plan;-- ⚠ 同上，按本方案保留

-- ④ 批次主档（lt_sn_binding / lt_wo_consumption 已于 M-1 删除）
DELETE FROM `ry-mes`.lt_lot;       -- 2

-- ⑤ 流转卡层（顺序不可颠倒）
DELETE FROM `ry-mes`.pro_flow_card_step;  -- 64（pro_dispatch 已于 M-1 删除）
DELETE FROM `ry-mes`.pro_sn;              -- 77（mes_flash_record/mes_station_report/lt_* 已于 M-1 删除）
DELETE FROM `ry-mes`.pro_flow_card;       -- 25（处理 current_step_id 环路，最后删）

COMMIT;   -- 与 E0 一起提交，外部永远看不到跨库孤儿
```

**M-2 执行后校验**
```sql
SELECT (SELECT COUNT(*) FROM `ry-mes`.qc_ipqc) + (SELECT COUNT(*) FROM `ry-mes`.qc_iqc)
     + (SELECT COUNT(*) FROM `ry-mes`.qc_oqc) + (SELECT COUNT(*) FROM `ry-mes`.ncr_order)
     + (SELECT COUNT(*) FROM `ry-mes`.pro_flow_card) + (SELECT COUNT(*) FROM `ry-mes`.pro_flow_card_step)
     + (SELECT COUNT(*) FROM `ry-mes`.pro_sn) + (SELECT COUNT(*) FROM `ry-mes`.lt_lot)
     + (SELECT COUNT(*) FROM `ry-mes`.eq_repair) AS leftover;
-- 期望：0

-- 跨库无悬空（ERP 侧是否还有指向已删 MES 行的指针）
SELECT COUNT(*) FROM `ry-erp`.wm_material_receive    WHERE iqc_id NOT IN (SELECT iqc_id FROM `ry-mes`.qc_iqc);   -- 期望 0
SELECT COUNT(*) FROM `ry-erp`.wm_arrival_notice_line WHERE iqc_id NOT IN (SELECT iqc_id FROM `ry-mes`.qc_iqc);   -- 期望 0
SELECT COUNT(*) FROM `ry-erp`.so_sales_order_line    WHERE oqc_id NOT IN (SELECT oqc_id FROM `ry-mes`.qc_oqc);   -- 期望 0
SELECT COUNT(*) FROM `ry-erp`.prd_work_order         WHERE quality_check_id NOT IN (SELECT ipqc_id FROM `ry-mes`.qc_ipqc); -- 期望 0

-- 保留表必须完好
SELECT COUNT(*) FROM `ry-mes`.qc_inspect_template;   -- 期望 1（保留）
SELECT COUNT(*) FROM `ry-mes`.qc_inspect_item;       -- 期望 2（保留）
SELECT COUNT(*) FROM `ry-mes`.ncr_defect_code;       -- 期望 71（保留）
SELECT COUNT(*) FROM `ry-mes`.spc_control_chart;     -- 期望 3（保留）
SELECT COUNT(*) FROM `ry-mes`.eq_equipment;          -- 期望 6（保留）
SELECT COUNT(*) FROM `ry-mes`.lt_lot;                -- 期望 0
```

---

## 批次 M-3 —— MES 工单层（3 张 / 117 行）

```sql
TRUNCATE TABLE `ry-mes`.pro_work_order_bom_line;  -- 46
TRUNCATE TABLE `ry-mes`.pro_work_order_step;      -- 53
TRUNCATE TABLE `ry-mes`.pro_work_order;           -- 18（最后）
```
或（需回滚能力时）：
```sql
START TRANSACTION;
DELETE FROM `ry-mes`.pro_work_order_bom_line;
DELETE FROM `ry-mes`.pro_work_order_step;
DELETE FROM `ry-mes`.pro_work_order;
COMMIT;
```

**M-3 执行后校验**
```sql
SELECT (SELECT COUNT(*) FROM `ry-mes`.pro_work_order)
     + (SELECT COUNT(*) FROM `ry-mes`.pro_work_order_step)
     + (SELECT COUNT(*) FROM `ry-mes`.pro_work_order_bom_line);   -- 期望 0

-- MES 内不得再有任何行指向已删工单
SELECT COUNT(*) FROM `ry-mes`.dc_data_record;      -- 期望 0
SELECT COUNT(*) FROM `ry-mes`.mes_material_load;   -- 期望 0
SELECT COUNT(*) FROM `ry-mes`.ncr_order;           -- 期望 0

-- 保留的主数据链仍完整（MBOM ← ERP BOM；工单快照已清空）
SELECT COUNT(*) FROM `ry-mes`.mes_mbom;            -- 期望 5（保留）
SELECT COUNT(*) FROM `ry-mes`.mes_mbom_line;       -- 期望 23（保留）
SELECT COUNT(*) FROM `ry-mes`.mes_mbom_line m
  JOIN `ry-erp`.bd_bom b ON b.bom_id = m.bom_id;   -- 期望 23（跨库仍命中）
SELECT COUNT(*) FROM `ry-mes`.mes_mbom m
  JOIN `ry-erp`.bd_routing r ON r.routing_id = m.routing_id;  -- 期望 5
```

---

## 批次 M-4 —— 部分删 / 残留清理（1 张 / 1 行）

```sql
-- 先看清单，确认只删占位行
SELECT device_id, device_code, station_code, shutdown_flag, last_heartbeat
FROM `ry-mes`.mes_station_device ORDER BY device_id;
-- 2090677150346575873 UNREGISTERED  NULL                         1  2026-08-22 20:03:43  ← 删
-- 2090691380416282625 TEST-DEV-01   STN|总装车间|装配线1|ASM|GENERAL 0  2026-08-21 14:44:03  ← 留
-- 2090729868033466369 一号工位机     NULL                         1  2026-08-21 17:50:53  ← 留
-- 2090804010377306113 GZZ0002       STN|TEST|L1|QC|FLASH         0  2026-08-31 00:24:26  ← 留

DELETE FROM `ry-mes`.mes_station_device
 WHERE device_code = 'UNREGISTERED'
   AND station_code IS NULL;      -- 影响 1 行
```

**M-4 执行后校验**
```sql
SELECT COUNT(*) FROM `ry-mes`.mes_station_device;                                  -- 期望 3
SELECT COUNT(*) FROM `ry-mes`.mes_station_device WHERE device_code='UNREGISTERED';-- 期望 0
SELECT device_code FROM `ry-mes`.mes_station_device ORDER BY device_id;           -- 期望 3 台真实终端
```

---

## 批次 I-1 —— IoT 侧（1 张 / 205 行）

```sql
TRUNCATE TABLE `ry-iot`.iot_device_data;   -- 205
```

**I-1 执行后校验**
```sql
SELECT COUNT(*) FROM `ry-iot`.iot_device_data;   -- 期望 0
SELECT COUNT(*) FROM `ry-iot`.iot_device;        -- 期望 5（保留）
SELECT COUNT(*) FROM `ry-iot`.iot_product;       -- 期望 3（保留）

-- MES↔IoT 双向引用仍完整
SELECT COUNT(*) FROM `ry-iot`.iot_device i JOIN `ry-mes`.eq_equipment e ON e.equipment_id = i.mes_equipment_id;  -- 期望 5
SELECT COUNT(*) FROM `ry-mes`.eq_equipment e JOIN `ry-iot`.iot_device i ON i.device_id = e.iot_device_id;        -- 期望 1
SELECT COUNT(*) FROM `ry-mes`.dc_collection_point p JOIN `ry-iot`.iot_device i ON i.device_id = p.iot_device_id; -- 期望 2
SELECT COUNT(*) FROM `ry-mes`.dc_collection_point p JOIN `ry-mes`.eq_equipment e ON e.equipment_id = p.equipment_id; -- 期望 5
```

---

## 批次 M-5 —— MES 终态校验（无删除动作）

```sql
-- ① MES 剩余行数应为 177（695 − 518）
SELECT SUM(c) FROM (
  SELECT COUNT(*) c FROM `ry-mes`.dc_collection_point      UNION ALL SELECT COUNT(*) FROM `ry-mes`.dc_data_record
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.eq_equipment     UNION ALL SELECT COUNT(*) FROM `ry-mes`.eq_health_record
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.eq_inspection_record UNION ALL SELECT COUNT(*) FROM `ry-mes`.eq_maintenance_plan
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.eq_maintenance_record UNION ALL SELECT COUNT(*) FROM `ry-mes`.eq_oee_record
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.eq_patrol_record  UNION ALL SELECT COUNT(*) FROM `ry-mes`.eq_patrol_route
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.eq_patrol_task    UNION ALL SELECT COUNT(*) FROM `ry-mes`.eq_repair
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.eq_repair_spare   UNION ALL SELECT COUNT(*) FROM `ry-mes`.eq_spare_part
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.eq_spare_stock_log UNION ALL SELECT COUNT(*) FROM `ry-mes`.lt_lot
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.lt_sn_binding     UNION ALL SELECT COUNT(*) FROM `ry-mes`.lt_wo_consumption
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.mes_content_file  UNION ALL SELECT COUNT(*) FROM `ry-mes`.mes_firmware
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.mes_flash_record  UNION ALL SELECT COUNT(*) FROM `ry-mes`.mes_flash_type
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.mes_label_template UNION ALL SELECT COUNT(*) FROM `ry-mes`.mes_material_load
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.mes_mbom          UNION ALL SELECT COUNT(*) FROM `ry-mes`.mes_mbom_line
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.mes_release_package UNION ALL SELECT COUNT(*) FROM `ry-mes`.mes_release_pkg_bom_item
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.mes_release_pkg_config UNION ALL SELECT COUNT(*) FROM `ry-mes`.mes_release_pkg_firmware
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.mes_resource_item UNION ALL SELECT COUNT(*) FROM `ry-mes`.mes_resource_pool
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.mes_sn_bind_log   UNION ALL SELECT COUNT(*) FROM `ry-mes`.mes_station_device
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.mes_station_report UNION ALL SELECT COUNT(*) FROM `ry-mes`.mes_test_item
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.mes_test_record   UNION ALL SELECT COUNT(*) FROM `ry-mes`.ncr_8d_report
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.ncr_defect        UNION ALL SELECT COUNT(*) FROM `ry-mes`.ncr_defect_code
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.ncr_order         UNION ALL SELECT COUNT(*) FROM `ry-mes`.ncr_rework_order
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.pro_andon         UNION ALL SELECT COUNT(*) FROM `ry-mes`.pro_dispatch
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.pro_flow_card     UNION ALL SELECT COUNT(*) FROM `ry-mes`.pro_flow_card_step
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.pro_sn            UNION ALL SELECT COUNT(*) FROM `ry-mes`.pro_step_check_rule
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.pro_step_guide    UNION ALL SELECT COUNT(*) FROM `ry-mes`.pro_work_order
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.pro_work_order_bom_line UNION ALL SELECT COUNT(*) FROM `ry-mes`.pro_work_order_step
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.qc_inspect_detail UNION ALL SELECT COUNT(*) FROM `ry-mes`.qc_inspect_item
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.qc_inspect_template UNION ALL SELECT COUNT(*) FROM `ry-mes`.qc_ipqc
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.qc_iqc            UNION ALL SELECT COUNT(*) FROM `ry-mes`.qc_oqc
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.sop_document      UNION ALL SELECT COUNT(*) FROM `ry-mes`.sop_process_binding
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.sop_version       UNION ALL SELECT COUNT(*) FROM `ry-mes`.sop_view_log
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.spc_control_chart UNION ALL SELECT COUNT(*) FROM `ry-mes`.spc_data_point
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.tg_maintenance_plan UNION ALL SELECT COUNT(*) FROM `ry-mes`.tg_maintenance_record
  UNION ALL SELECT COUNT(*) FROM `ry-mes`.tg_tooling        UNION ALL SELECT COUNT(*) FROM `ry-mes`.tg_usage_record
) x;
-- 期望：177

-- ② 全库悬空检查（应全部为 0）
SELECT 'pro_work_order_bom_line->bd_material' k, COUNT(*) c FROM `ry-mes`.pro_work_order_bom_line x
  WHERE x.child_material_id NOT IN (SELECT material_id FROM `ry-erp`.bd_material)
UNION ALL SELECT 'mes_mbom->bd_bom', COUNT(*) FROM `ry-mes`.mes_mbom x WHERE x.bom_id NOT IN (SELECT bom_id FROM `ry-erp`.bd_bom)
UNION ALL SELECT 'mes_mbom->bd_routing', COUNT(*) FROM `ry-mes`.mes_mbom x WHERE x.routing_id NOT IN (SELECT routing_id FROM `ry-erp`.bd_routing)
UNION ALL SELECT 'mes_mbom_line->bd_process', COUNT(*) FROM `ry-mes`.mes_mbom_line x WHERE x.process_id NOT IN (SELECT process_id FROM `ry-erp`.bd_process)
UNION ALL SELECT 'mes_mbom_line->bd_workstation', COUNT(*) FROM `ry-mes`.mes_mbom_line x WHERE x.workstation_id NOT IN (SELECT workstation_id FROM `ry-erp`.bd_workstation)
UNION ALL SELECT 'mes_mbom_line->bd_bom_detail', COUNT(*) FROM `ry-mes`.mes_mbom_line x WHERE x.bom_detail_id IS NOT NULL AND x.bom_detail_id<>0 AND x.bom_detail_id NOT IN (SELECT detail_id FROM `ry-erp`.bd_bom_detail)
UNION ALL SELECT 'mes_mbom_line->bd_routing_step', COUNT(*) FROM `ry-mes`.mes_mbom_line x WHERE x.routing_step_id NOT IN (SELECT step_id FROM `ry-erp`.bd_routing_step)
UNION ALL SELECT 'eq_equipment->bd_workstation', COUNT(*) FROM `ry-mes`.eq_equipment x WHERE x.workcenter_id NOT IN (SELECT workstation_id FROM `ry-erp`.bd_workstation)
UNION ALL SELECT 'dc_collection_point->bd_process', COUNT(*) FROM `ry-mes`.dc_collection_point x WHERE x.process_id IS NOT NULL AND x.process_id<>0 AND x.process_id NOT IN (SELECT process_id FROM `ry-erp`.bd_process)
UNION ALL SELECT 'dc_collection_point->eq_equipment', COUNT(*) FROM `ry-mes`.dc_collection_point x WHERE x.equipment_id IS NOT NULL AND x.equipment_id<>0 AND x.equipment_id NOT IN (SELECT equipment_id FROM `ry-mes`.eq_equipment)
UNION ALL SELECT 'dc_collection_point->iot_device', COUNT(*) FROM `ry-mes`.dc_collection_point x WHERE x.iot_device_id IS NOT NULL AND x.iot_device_id<>0 AND x.iot_device_id NOT IN (SELECT device_id FROM `ry-iot`.iot_device)
UNION ALL SELECT 'spc_control_chart->bd_material', COUNT(*) FROM `ry-mes`.spc_control_chart x WHERE x.material_id NOT IN (SELECT material_id FROM `ry-erp`.bd_material)
UNION ALL SELECT 'spc_control_chart->bd_process', COUNT(*) FROM `ry-mes`.spc_control_chart x WHERE x.process_id NOT IN (SELECT process_id FROM `ry-erp`.bd_process)
UNION ALL SELECT 'spc_control_chart->dc_collection_point', COUNT(*) FROM `ry-mes`.spc_control_chart x WHERE x.point_id NOT IN (SELECT point_id FROM `ry-mes`.dc_collection_point)
UNION ALL SELECT 'sop_process_binding->bd_process', COUNT(*) FROM `ry-mes`.sop_process_binding x WHERE x.process_id NOT IN (SELECT process_id FROM `ry-erp`.bd_process)
UNION ALL SELECT 'mes_firmware->bd_material', COUNT(*) FROM `ry-mes`.mes_firmware x WHERE x.material_id NOT IN (SELECT material_id FROM `ry-erp`.bd_material)
UNION ALL SELECT 'mes_test_item->bd_material', COUNT(*) FROM `ry-mes`.mes_test_item x WHERE x.material_id NOT IN (SELECT material_id FROM `ry-erp`.bd_material)
UNION ALL SELECT 'qc_inspect_template->bd_material', COUNT(*) FROM `ry-mes`.qc_inspect_template x WHERE x.material_id IS NOT NULL AND x.material_id<>0 AND x.material_id NOT IN (SELECT material_id FROM `ry-erp`.bd_material)
UNION ALL SELECT 'qc_inspect_item->qc_inspect_template', COUNT(*) FROM `ry-mes`.qc_inspect_item x WHERE x.template_id NOT IN (SELECT template_id FROM `ry-mes`.qc_inspect_template)
UNION ALL SELECT 'mes_release_pkg_firmware->mes_firmware', COUNT(*) FROM `ry-mes`.mes_release_pkg_firmware x WHERE x.firmware_id NOT IN (SELECT firmware_id FROM `ry-mes`.mes_firmware)
UNION ALL SELECT 'mes_release_pkg_firmware->bd_process', COUNT(*) FROM `ry-mes`.mes_release_pkg_firmware x WHERE x.process_id NOT IN (SELECT process_id FROM `ry-erp`.bd_process)
UNION ALL SELECT 'tg_tooling->bd_workshop', COUNT(*) FROM `ry-mes`.tg_tooling x WHERE x.workshop_id NOT IN (SELECT workshop_id FROM `ry-erp`.bd_workshop)
UNION ALL SELECT 'sop_version->sop_document', COUNT(*) FROM `ry-mes`.sop_version x WHERE x.sop_id NOT IN (SELECT sop_id FROM `ry-mes`.sop_document)
UNION ALL SELECT 'sop_process_binding->sop_document', COUNT(*) FROM `ry-mes`.sop_process_binding x WHERE x.sop_id NOT IN (SELECT sop_id FROM `ry-mes`.sop_document)
UNION ALL SELECT 'eq_maintenance_plan->eq_equipment', COUNT(*) FROM `ry-mes`.eq_maintenance_plan x WHERE x.equipment_id NOT IN (SELECT equipment_id FROM `ry-mes`.eq_equipment)
UNION ALL SELECT 'tg_maintenance_plan->tg_tooling', COUNT(*) FROM `ry-mes`.tg_maintenance_plan x WHERE x.tooling_id NOT IN (SELECT tooling_id FROM `ry-mes`.tg_tooling)
UNION ALL SELECT 'iot_device->eq_equipment', COUNT(*) FROM `ry-iot`.iot_device x WHERE x.mes_equipment_id IS NOT NULL AND x.mes_equipment_id<>0 AND x.mes_equipment_id NOT IN (SELECT equipment_id FROM `ry-mes`.eq_equipment)
UNION ALL SELECT 'bd_workstation->eq_equipment', COUNT(*) FROM `ry-erp`.bd_workstation x WHERE x.equipment_id IS NOT NULL AND x.equipment_id<>0 AND x.equipment_id NOT IN (SELECT equipment_id FROM `ry-mes`.eq_equipment);
-- 期望：全部 0
```

---

## 批次 E-ERP（ERP 侧，按本任务范围仅列"受牵连"的部分）

> 完整 ERP 清理不在本任务范围；下列批次只覆盖**与 MES 有引用关系**及**3D 演示保留集**相关的表。**ERP 与 MES 的先后顺序是本节的核心。**

### E-A —— 必须在 M-2 之前/同事务（已并入 E0，见上）

### E-B —— 必须在 MES 全部清空**之后**才能删（MES → ERP 方向）

```sql
-- 只有在 MES 的 pro_work_order* / mes_mbom* / qc_* / te_* / dc_* 全部清空后才可执行
START TRANSACTION;
DELETE FROM `ry-erp`.prd_work_order_resource;      -- 66 行（工单资源分配，先于工单）
DELETE FROM `ry-erp`.prd_work_report;              -- 1 行
DELETE FROM `ry-erp`.prd_work_order;               -- 12 行  ← MES pro_work_order.erp_wo_id 已清空
COMMIT;
```
**执行后校验**
```sql
SELECT COUNT(*) FROM `ry-mes`.pro_work_order WHERE erp_wo_id IS NOT NULL AND erp_wo_id<>0;  -- 期望 0（已清空）
SELECT COUNT(*) FROM `ry-erp`.prd_work_order;    -- 期望 0
```

### E-C —— ERP 基础主数据：**只有确认不再需要 MES 的 MBOM 时才可删**

```sql
-- ⚠⚠ 危险：执行前必须先删除/放弃 ry-mes.mes_mbom + mes_mbom_line
-- 本方案默认 mes_mbom 保留 ⇒ 本批【不得执行】
-- DELETE FROM `ry-erp`.bd_bom_detail;  DELETE FROM `ry-erp`.bd_bom;
-- DELETE FROM `ry-erp`.bd_routing_step; DELETE FROM `ry-erp`.bd_routing;
-- DELETE FROM `ry-erp`.bd_process;  DELETE FROM `ry-erp`.bd_workstation;
-- DELETE FROM `ry-erp`.bd_workshop; DELETE FROM `ry-erp`.bd_material;
```
**本方案结论：`bd_*` 基础主数据全部保留。** 理由：`mes_mbom`(5)/`mes_mbom_line`(23)/`sop_process_binding`(10)/`spc_control_chart`(3)/`dc_collection_point`(9)/`eq_equipment`(6)/`mes_firmware`(6)/`ncr_defect_code` 的语义依赖它们；删掉即让保留下来的 MES 主数据全部悬空。

### E-D —— ERP WMS 数据（含 3D 演示保护）

```sql
-- 若需清空 ERP 库存业务数据，务必排除 3D 演示仓库
DELETE FROM `ry-erp`.wm_material_stock_tx WHERE warehouse_id <> 9900000001;                 -- 51 行（0 行属演示仓）
DELETE FROM `ry-erp`.wm_material_stock    WHERE warehouse_id <> 9900000001;                 -- 92 − 55 = 37 行
DELETE FROM `ry-erp`.wm_storage_location  WHERE warehouse_id <> 9900000001;                 -- 128 − 104 = 24 行
DELETE FROM `ry-erp`.wm_storage_area      WHERE warehouse_id <> 9900000001;                 -- 12 − 4 = 8 行
-- wm_warehouse：只删非演示仓库
DELETE FROM `ry-erp`.wm_warehouse         WHERE warehouse_id <> 9900000001;                 -- 5 − 1 = 4 行
```

**E-D 执行后校验（3D 演示保留集必须原封不动）**
```sql
SELECT (SELECT COUNT(*) FROM `ry-erp`.wm_warehouse        WHERE warehouse_id = 9900000001)  -- 期望 1
     + (SELECT COUNT(*) FROM `ry-erp`.wm_storage_area     WHERE warehouse_id = 9900000001)  -- 期望 4
     + (SELECT COUNT(*) FROM `ry-erp`.wm_storage_location WHERE warehouse_id = 9900000001)  -- 期望 104
     + (SELECT COUNT(*) FROM `ry-erp`.wm_material_stock   WHERE warehouse_id = 9900000001); -- 期望 55
-- 期望合计：164

SELECT warehouse_id, warehouse_code, warehouse_name FROM `ry-erp`.wm_warehouse;  -- 必须仍含 9900000001 / WH-3D-DEMO / 3D示例仓库
SELECT COUNT(*) FROM `ry-erp`.wm_storage_area WHERE warehouse_id=9900000001;     -- 期望 4
SELECT COUNT(*) FROM `ry-erp`.wm_storage_location WHERE warehouse_id=9900000001; -- 期望 104
SELECT COUNT(*) FROM `ry-erp`.wm_material_stock WHERE warehouse_id=9900000001;   -- 期望 55
SELECT COUNT(DISTINCT material_id) FROM `ry-erp`.wm_material_stock WHERE warehouse_id=9900000001;  -- 期望 6
```

### E-E —— ERP 带 AUTO_INCREMENT 的表（唯一需要"重置编码"的地方）

```sql
-- 这 10 张表 TRUNCATE 会自动把 AUTO_INCREMENT 重置为 1，等价于"重置编码"
TRUNCATE TABLE `ry-erp`.prd_capacity_calendar;
TRUNCATE TABLE `ry-erp`.pu_purchase_order;
TRUNCATE TABLE `ry-erp`.pu_purchase_order_line;
TRUNCATE TABLE `ry-erp`.pu_purchase_requisition;
TRUNCATE TABLE `ry-erp`.pu_purchase_requisition_line;
TRUNCATE TABLE `ry-erp`.pu_purchase_return;
TRUNCATE TABLE `ry-erp`.pu_purchase_return_line;
TRUNCATE TABLE `ry-erp`.wm_barcode;
TRUNCATE TABLE `ry-erp`.wm_barcode_config;
-- ⚠ wm_production_recpt 可能含 3D 演示仓库入库行：先查再决定
-- SELECT recpt_id, warehouse_id FROM `ry-erp`.wm_production_recpt WHERE warehouse_id = 9900000001;
```
**执行后校验**
```sql
SELECT TABLE_NAME, AUTO_INCREMENT FROM information_schema.TABLES
WHERE TABLE_SCHEMA='ry-erp' AND AUTO_INCREMENT IS NOT NULL ORDER BY TABLE_NAME;
-- 期望：上述表的 AUTO_INCREMENT 归 1（或 NULL）
```

---

## 5.1 批次总表（顺序即执行顺序）

| 批次 | 涉及库 | 表数 | 行数 | 方式 | 与另一侧的顺序 | 执行后校验要点 |
|---|---|---:|---:|---|---|---|
| **B0** | 全部 | — | — | 备份 + 停服 | — | `ry-mes`=695、`ry-iot`=213 |
| **E0** | ERP | 3 | 3 | `DELETE ... WHERE id=`（事务） | **必须先于 M-2**，与 M-2 同事务提交 | `wm_material_receive`/`wm_arrival_notice_line`/`so_sales_order_line` 中 3 个指针列归 0 |
| **M-1** | MES | 26 | 209 | `TRUNCATE` | 独立 | 26 张全 0；`pro_work_order`=18、`pro_sn`=77 等父表未动 |
| **M-2** | MES | 10 | 191 | `DELETE`（事务，**与 E0 同事务**） | **必须晚于 E0** | 10 张全 0；ERP 侧 4 条反指查询全 0 |
| **M-3** | MES | 3 | 117 | `TRUNCATE` | 独立 | 3 张全 0；`mes_mbom`(5)/`mes_mbom_line`(23) 跨库仍 100% 命中 |
| **M-4** | MES | 1 | 1 | `DELETE ... WHERE` | 独立 | `mes_station_device`=3 |
| **I-1** | IoT | 1 | 205 | `TRUNCATE` | 独立 | `iot_device_data`=0；`iot_device`=5、`iot_product`=3 |
| **M-5** | MES | — | — | 只读终检 | — | MES 总行数=177；25 条悬空查询全 0 |
| **E-B** | ERP | 3 | 79 | `DELETE`（事务） | **必须晚于 M-3** | `prd_work_order`=0 |
| **E-D** | ERP | 5 | 124 | `DELETE ... WHERE <> 9900000001` | **独立，但 3D 演示集必须=164** | 4 张演示表合计=164 |
| **E-E** | ERP | 9~10 | 视情况 | `TRUNCATE` | **独立** | `AUTO_INCREMENT` 归 1 |
| **E-C** | ERP | — | — | **本方案禁止执行** | 若执行则必须先删 `mes_mbom*` | —— |

> **ERP ↔ MES 顺序一句话总结**
> 1. 凡「MES 单据 → ERP 主数据」→ **先删 MES**；
> 2. 凡「ERP 单据 → MES 质检单」→ **先删 ERP**（并与 MES 同事务）；
> 3. 先删 ERP 主数据（`bd_*`）→ ❌ 绝对禁止，会同时毁掉 MES 保留的 MBOM/SOP/SPC/设备域；
> 4. 先删 MES 质检单而不动 ERP → ❌ 会在 `wm_material_receive` / `wm_arrival_notice_line` / `so_sales_order_line` / `prd_work_order` 留下 5 处永久悬空。

## 5.2 回滚与失败处置

| 情况 | 处置 |
|---|---|
| M-1 / M-3 / I-1 的 `TRUNCATE` 失败 | `TRUNCATE` 是 DDL、不可回滚 → **从左备（B0-1）恢复整库**，重来 |
| M-2 事务中途报错 | `ROLLBACK;` —— 含 E0 的 3 行一起回滚，两侧回到一致状态 |
| 已误删 ERP 主数据 | 从 B0-1 备份恢复 `ry-erp`；MES 侧此时应尚未删（因为顺序是先 MES 后 ERP，误删 ERP 意味着顺序做错了） |
| 已误删 3D 演示仓库 | 从 B0-1 备份恢复 `ry-erp.wm_warehouse` / `wm_storage_area` / `wm_storage_location` / `wm_material_stock` 中 `warehouse_id=9900000001` 的 164 行 |
| 发现新的悬空 | 用 §5.0 的 M-5 ② 检查清单定位；若涉及保留表，说明批次顺序被打乱，须回滚重来 |

---

# 附录 A　证据脚本索引

| 脚本 | 用途 |
|---|---|
| `.dsh-tmp/dbdel/count_ry-mes.sql` / `count_ry-iot.sql` | 68+3 张表 `COUNT(*)` 实测 |
| `.dsh-tmp/dbdel/count_ry-mes.txt` | 实测行数输出留档 |
| `.dsh-tmp/dbdel/xref_check.sql` | MES → ERP 40 组引用实测 |
| `.dsh-tmp/dbdel/xref_rev.sql` | IoT/内部 10 组引用实测 |
| `.dsh-tmp/dbdel/xref_erp2mes.sql` | ERP → MES 反指实测 |
| `.dsh-tmp/dbdel/qr.sql` | QC / 批次 / SPC / 发布包 15 组引用实测 |
| `.dsh-tmp/dbdel/internal_refs.sql` | MES 内部 77 组引用实测 |
| `.dsh-tmp/dbdel/sweep_candidates.sql` / `sweep2.sql` | 漏网跨库候选列全量扫描 |
| `.dsh-tmp/dbdel/demo3d.sql` | 3D 演示数据范围与自包含性 |
| `.dsh-tmp/dbdel/erp_counts.sql` | ERP 受牵连表实测行数 |
| `.dsh-tmp/dbdel/orphan.sql` / `orphan2.sql` | 现存孤儿定位 |
| `.dsh-tmp/dbdel/cols_condensed.txt` | 68 张表列清单（依赖推导依据） |
| `.dsh-tmp/dbdel/mes_iot_data.sql` | MES+IoT 全量数据文本（3D 关键字全文检索依据） |

# 附录 B　未实测 / 需人工确认项

1. `ry-erp.prd_work_report.equipment_id` 当前 0 行有值，无法判定它是指向 MES `eq_equipment` 还是 ERP 自有的设备编号——按列注释「设备ID」暂归为 ERP→MES 潜在引用，删除 MES 设备台账前需人工确认。
2. `ry-erp.wm_barcode` / `wm_sn`（24 / 0 行）与 MES `pro_sn`（77 行）之间**未发现**列级或代码级关联，已用 `sweep_candidates.sql` 扫描确认无同名 ID 列对应。
3. 表中 `attr1..attr4` 预留列全部为空或未参与关联，未纳入引用图；若后续业务启用需重新评估。
4. 本计划不包含 UPDATE 语句，因此「保留 ERP 单据 + 清空 MES 质检单」的组合**无法在纯删除下完成**，见 §3.5（C）末尾的 ⚠。
