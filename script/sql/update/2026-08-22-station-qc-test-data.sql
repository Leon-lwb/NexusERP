-- =====================================================================
-- 工位机 QC 测试工位完整测试数据 (2026-08-22)
--
-- 测试工位码(5段): STN|TEST|L1|QC|GENERAL      工序: QC(质检, process_id=4)
-- 完整6段码(工位机设置页录入, 第6段设备编码自动剥离回填):
--   STN|TEST|L1|QC|GENERAL|GZZ0002
-- 刷写测试可把第5段形态改为 FLASH: STN|TEST|L1|QC|FLASH|GZZ0002
--
-- 覆盖场景:
--   C1  上岗        工号 SCZX20251222017(张明, 复用现有档案)
--   C2  过站校验    扫 SNQCTEST0001~0005 (四重校验全过)
--   C3  单件报工    PASS 推进工序 / FAIL 留待处理(不良代码复用 ncr_defect_code)
--   C4  批量报工    QC 工序恰好唯一在制(计数工位语义)
--   C5/C6 绑定/解绑 父 SNQCTEST0001 × 组件 SNQCCOMP0001~0006
--   C13 追溯        SNQCTEST0001 工序链 + 报工/绑定时间线
--   C14/C15 点检    设备 QC-CHECK-001(挂 QC 默认工作中心 GZZ0017, 3 项点检)
--   C16-C18 SOP     2 份 PUBLISHED 文档(PDF+图片, 复用 MinIO 已有文件)
--   C19/C20 上料    B20260822001(主料) / B20260822002(替代料) /
--                   B202606040001(错误料, 应拦截) / RC20260822000001(条码实例)
--   刷写            工单含 FLASH 步骤绑定 FW20260817001(RELEASED, 物料=1001 与工单一致,
--                   OSS 文件存在) — FLASH 形态工位扫 SNQCTEST0001 可拉待刷清单
--
-- 重跑说明: 脚本可重复执行。开头清理段会删除本套测试数据与测试产生的业务痕迹
--   (报工/绑定/点检/上料台账/安灯/SOP查阅日志), 不触碰其它任何现有数据。
-- 执行: mysql --default-character-set=utf8mb4 -uroot -p < 本文件
--   注意: 本机 MySQL 服务端为 latin1 且忽略客户端握手字符集(--default-character-set
--   不生效), 文件内 SET NAMES utf8mb4 是中文正确入库的关键, 勿删。
-- =====================================================================

-- 强制会话字符集: 保证本文件 UTF-8 中文字面量不被 latin1 会话双重编码成乱码
SET NAMES utf8mb4;

-- ---------- 0. 清理(仅限本套测试数据 ID 前缀 2099 与测试痕迹) ----------
DELETE FROM `ry-mes`.mes_material_load    WHERE mes_wo_id = 2099000000000000001;
DELETE FROM `ry-mes`.mes_station_report   WHERE sn_code LIKE 'SNQCTEST%' OR sn_code LIKE 'SNQCCOMP%'
   OR station_code IN ('STN|TEST|L1|QC|GENERAL', 'STN|TEST|L1|QC|FLASH');
DELETE FROM `ry-mes`.mes_sn_bind_log      WHERE parent_sn_code LIKE 'SNQCTEST%' OR component_sn_code LIKE 'SNQCCOMP%';
DELETE FROM `ry-mes`.eq_inspection_record WHERE equipment_id = 2099000000000000401;
DELETE FROM `ry-mes`.pro_andon            WHERE station_code IN ('STN|TEST|L1|QC|GENERAL', 'STN|TEST|L1|QC|FLASH');
DELETE FROM `ry-mes`.sop_view_log         WHERE sop_id IN (2099000000000000501, 2099000000000000502);
DELETE FROM `ry-mes`.pro_sn               WHERE sn_id BETWEEN 2099000000000000101 AND 2099000000000000206;
DELETE FROM `ry-mes`.pro_flow_card_step   WHERE card_id = 2099000000000000021;
DELETE FROM `ry-mes`.pro_flow_card        WHERE card_id = 2099000000000000021;
DELETE FROM `ry-mes`.pro_work_order_bom_line WHERE mes_wo_id = 2099000000000000001;
DELETE FROM `ry-mes`.pro_work_order_step  WHERE mes_wo_id = 2099000000000000001;
DELETE FROM `ry-mes`.pro_work_order       WHERE mes_wo_id = 2099000000000000001;
DELETE FROM `ry-mes`.eq_equipment         WHERE equipment_id = 2099000000000000401;
DELETE FROM `ry-mes`.sop_document         WHERE sop_id IN (2099000000000000501, 2099000000000000502);
DELETE FROM `ry-mes`.sop_process_binding  WHERE binding_id IN (2099000000000000511, 2099000000000000512);
DELETE FROM `ry-erp`.wm_batch             WHERE batch_id IN (2099000000000000601, 2099000000000000602);
DELETE FROM `ry-erp`.wm_barcode           WHERE barcode_id = 2099000000000000611;
DELETE FROM `ry-erp`.bd_bom_alternative   WHERE alternative_id = 2099000000000000621;

-- ---------- 1. ry-erp: 物料批次 / 条码实例 / 替代料 ----------

-- 批次1: 主料 BOLT_M12(material_id=7) — 扫码 B20260822001 应匹配
INSERT INTO `ry-erp`.wm_batch
  (batch_id, tenant_id, batch_code, material_id, material_code, material_name, material_spec,
   vendor_name, production_date, expiry_date, quantity, status, create_dept, create_by, create_time, del_flag)
SELECT 2099000000000000601, '000000', 'B20260822001', m.material_id, m.material_code, m.material_name, m.material_spec,
  '测试供应商', DATE_SUB(CURDATE(), INTERVAL 10 DAY), DATE_ADD(CURDATE(), INTERVAL 365 DAY), 1000, 'ACTIVE', -1, -1, NOW(), '0'
FROM `ry-erp`.bd_material m WHERE m.material_id = 7;

-- 批次2: 替代料 BEARING6204(material_id=8) — 扫码 B20260822002 应以"替代料"匹配
INSERT INTO `ry-erp`.wm_batch
  (batch_id, tenant_id, batch_code, material_id, material_code, material_name, material_spec,
   vendor_name, production_date, expiry_date, quantity, status, create_dept, create_by, create_time, del_flag)
SELECT 2099000000000000602, '000000', 'B20260822002', m.material_id, m.material_code, m.material_name, m.material_spec,
  '测试供应商', DATE_SUB(CURDATE(), INTERVAL 10 DAY), DATE_ADD(CURDATE(), INTERVAL 365 DAY), 1000, 'ACTIVE', -1, -1, NOW(), '0'
FROM `ry-erp`.bd_material m WHERE m.material_id = 8;

-- 条码实例: RC 前缀(物料 BOLT_M12) — 扫码 RC20260822000001 应匹配
INSERT INTO `ry-erp`.wm_barcode
  (barcode_id, tenant_id, barcode_code, biz_type, material_id, batch_id, batch_code, status, generate_time,
   create_dept, create_by, create_time, del_flag)
VALUES (2099000000000000611, '000000', 'RC20260822000001', 'MATERIAL', 7, 2099000000000000601, 'B20260822001',
  'ACTIVE', NOW(), -1, -1, NOW(), '0');

-- 替代料关系: BOM明细 2099000000000000631(BOLT_M12) 可用 BEARING6204 全量替代
INSERT INTO `ry-erp`.bd_bom_alternative
  (alternative_id, tenant_id, detail_id, bom_id, alt_material_id, alt_material_code, alt_material_name,
   alt_quantity, priority, substitution_type, effective_date, expiry_date, status, remark,
   create_dept, create_by, create_time, del_flag)
SELECT 2099000000000000621, '000000', 2099000000000000631, 99990001, m.material_id, m.material_code, m.material_name,
  2, 1, 'FULL', NULL, NULL, 1, 'QC测试工位: BOLT_M12 可用 BEARING6204 替代', -1, -1, NOW(), '0'
FROM `ry-erp`.bd_material m WHERE m.material_id = 8;

-- ---------- 2. ry-mes: 工单 / 工单工序(刷写) / 流转卡 / 工序步骤 ----------

-- 工单(成品=自行车 material 1001, 与固件 FW20260817001 物料一致)
INSERT INTO `ry-mes`.pro_work_order
  (mes_wo_id, tenant_id, wo_no, material_id, material_code, material_name,
   planned_qty, completed_qty, scrap_qty, unit_id, unit_name, plan_start_date, plan_end_date,
   wo_status, priority, remark, create_dept, create_by, create_time, update_by, update_time, del_flag, source_type)
SELECT 2099000000000000001, '000000', 'WO-QCTEST-001', m.material_id, m.material_code, m.material_name,
  10, 0, 0, 26, '个', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 7 DAY),
  'IN_PROGRESS', 5, '工位机QC测试工位专用工单', -1, -1, NOW(), NULL, NOW(), '0', 'MANUAL'
FROM `ry-erp`.bd_material m WHERE m.material_id = 1001;

-- 工单刷写工序: 绑定 RELEASED 固件 FW20260817001(物料匹配, OSS 文件存在)
-- (FLASH 形态工位扫主 SN 即可拉到待刷清单, mock 驱动可完整走通)
INSERT INTO `ry-mes`.pro_work_order_step
  (step_id, tenant_id, mes_wo_id, sequence_num, step_type, content_ref_type, content_ref_id,
   process_id, process_code, process_name, workcenter_id, workcenter_code,
   planned_qty, completed_qty, scrap_qty, step_status, is_key_process, remark,
   create_dept, create_by, create_time, update_by, update_time, del_flag)
VALUES (2099000000000000011, '000000', 2099000000000000001, 50, 'FLASH', 'FIRMWARE', 2091000000000006101,
  90002001, 'FLASH', '固件烧录', NULL, NULL,
  10, 0, 0, 'PENDING', 1, '测试固件 FW20260817001', -1, -1, NOW(), NULL, NOW(), '0');

-- 流转卡(在制, 当前工序=QC)
INSERT INTO `ry-mes`.pro_flow_card
  (card_id, tenant_id, card_no, mes_wo_id, wo_no, material_id, material_code, material_name,
   planned_qty, completed_qty, scrap_qty, current_step_id, current_step_seq, card_status,
   barcode, remark, create_dept, create_by, create_time, update_by, update_time, del_flag)
SELECT 2099000000000000021, '000000', 'FC-QCTEST-0001', 2099000000000000001, 'WO-QCTEST-001',
  m.material_id, m.material_code, m.material_name,
  10, 0, 0, 2099000000000000033, 30, 'IN_PROGRESS',
  'BC-QCTEST-0001', '工位机QC测试工位专用流转卡', -1, -1, NOW(), NULL, NOW(), '0'
FROM `ry-erp`.bd_material m WHERE m.material_id = 1001;

-- 工序步骤: CUT/WELD 已完成(前道工序校验可过), QC 在制(唯一, 满足计数/上料定位)
INSERT INTO `ry-mes`.pro_flow_card_step
  (step_record_id, tenant_id, card_id, card_no, mes_wo_id, wo_step_id, sequence_num,
   process_id, process_code, process_name, workcenter_id, workcenter_code, is_key_process, step_status,
   operator_id, operator_name, planned_qty, good_qty, defect_qty, scrap_qty,
   actual_start_time, actual_end_time, remark, create_dept, create_by, create_time, update_by, update_time, del_flag)
VALUES
  (2099000000000000031, '000000', 2099000000000000021, 'FC-QCTEST-0001', 2099000000000000001, NULL, 10,
   1, 'CUT', '切割', 13, 'GZZ0014', 0, 'COMPLETED', NULL, NULL, 10, 10, 0, 0,
   DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), 'QC测试数据: 前道已完成',
   -1, -1, NOW(), NULL, NOW(), '0'),
  (2099000000000000032, '000000', 2099000000000000021, 'FC-QCTEST-0001', 2099000000000000001, NULL, 20,
   2, 'WELD', '焊接', 11, 'GZZ0012', 1, 'COMPLETED', NULL, NULL, 10, 10, 0, 0,
   DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 'QC测试数据: 前道已完成',
   -1, -1, NOW(), NULL, NOW(), '0'),
  (2099000000000000033, '000000', 2099000000000000021, 'FC-QCTEST-0001', 2099000000000000001, NULL, 30,
   4, 'QC', '质检', 16, 'GZZ0017', 1, 'IN_PROGRESS', NULL, NULL, 10, 0, 0, 0,
   NOW(), NULL, 'QC测试工位当前在制工序',
   -1, -1, NOW(), NULL, NOW(), '0');

-- ---------- 3. ry-mes: SN(5 个主件在制 + 6 个组件供绑定) ----------
INSERT INTO `ry-mes`.pro_sn
  (sn_id, tenant_id, sn_code, mes_wo_id, wo_no, card_id, card_no,
   material_id, material_code, material_name, current_step_seq, sn_status, source, production_date,
   create_dept, create_by, create_time, update_by, update_time, del_flag)
SELECT x.sn_id, '000000', x.sn_code, 2099000000000000001, 'WO-QCTEST-001', 2099000000000000021, 'FC-QCTEST-0001',
  m.material_id, m.material_code, m.material_name, 30, x.sn_status, 'MANUAL', CURDATE(),
  -1, -1, NOW(), NULL, NOW(), '0'
FROM `ry-erp`.bd_material m
JOIN (
  SELECT 2099000000000000101 AS sn_id, 'SNQCTEST0001' AS sn_code, 'IN_PRODUCTION' AS sn_status UNION ALL
  SELECT 2099000000000000102, 'SNQCTEST0002', 'IN_PRODUCTION' UNION ALL
  SELECT 2099000000000000103, 'SNQCTEST0003', 'IN_PRODUCTION' UNION ALL
  SELECT 2099000000000000104, 'SNQCTEST0004', 'IN_PRODUCTION' UNION ALL
  SELECT 2099000000000000105, 'SNQCTEST0005', 'IN_PRODUCTION' UNION ALL
  SELECT 2099000000000000201, 'SNQCCOMP0001', 'COMPLETED'   UNION ALL
  SELECT 2099000000000000202, 'SNQCCOMP0002', 'COMPLETED'   UNION ALL
  SELECT 2099000000000000203, 'SNQCCOMP0003', 'COMPLETED'   UNION ALL
  SELECT 2099000000000000204, 'SNQCCOMP0004', 'COMPLETED'   UNION ALL
  SELECT 2099000000000000205, 'SNQCCOMP0005', 'COMPLETED'   UNION ALL
  SELECT 2099000000000000206, 'SNQCCOMP0006', 'COMPLETED'
) x ON m.material_id = 1001;

-- ---------- 4. ry-mes: 工单 MBOM 快照(上料防错基准) ----------
-- 行1: 主料 BOLT_M12, QC 工序行(带 bom_detail_id → 替代料关系)
INSERT INTO `ry-mes`.pro_work_order_bom_line
  (wo_bom_line_id, tenant_id, mes_wo_id, bom_detail_id, child_material_id, child_material_code, child_material_name,
   quantity_per_parent, loss_rate, total_quantity, sequence_num, process_id, process_code, process_name,
   workstation_id, workstation_code, workstation_name, backflush_flag, sort_order,
   create_dept, create_by, create_time, update_by, update_time, del_flag)
SELECT 2099000000000000301, '000000', 2099000000000000001, 2099000000000000631,
  m.material_id, m.material_code, m.material_name,
  2, 0, 20, 1, p.process_id, p.process_code, p.process_name,
  p.workstation_id, w.workstation_code, w.workstation_name, 0, 1,
  -1, -1, NOW(), NULL, NOW(), '0'
FROM `ry-erp`.bd_material m
JOIN `ry-erp`.bd_process p ON p.process_code = 'QC'
JOIN `ry-erp`.bd_workstation w ON w.workstation_id = p.workstation_id
WHERE m.material_id = 7;

-- 行2: 兜底行(工序为 NULL 时并入任意工序的允许清单), 主料 HOUSING
INSERT INTO `ry-mes`.pro_work_order_bom_line
  (wo_bom_line_id, tenant_id, mes_wo_id, bom_detail_id, child_material_id, child_material_code, child_material_name,
   quantity_per_parent, loss_rate, total_quantity, sequence_num, process_id, process_code, process_name,
   workstation_id, workstation_code, workstation_name, backflush_flag, sort_order,
   create_dept, create_by, create_time, update_by, update_time, del_flag)
SELECT 2099000000000000302, '000000', 2099000000000000001, NULL,
  m.material_id, m.material_code, m.material_name,
  1, 0, 10, 2, NULL, NULL, NULL,
  NULL, NULL, NULL, 0, 2,
  -1, -1, NOW(), NULL, NOW(), '0'
FROM `ry-erp`.bd_material m WHERE m.material_id = 6;

-- ---------- 5. ry-mes: 点检设备(挂 QC 默认工作中心 GZZ0017) ----------
-- inspection_items 用新格式 [{"item","standard"}](工位机 C14 解析口径)
INSERT INTO `ry-mes`.eq_equipment
  (equipment_id, tenant_id, equipment_code, equipment_name, equipment_type, model, manufacturer, serial_no,
   workcenter_id, workcenter_code, location, purchase_date, warranty_date, status,
   inspection_items, last_inspection, iot_device_id, remark, create_dept, create_by, create_time, del_flag)
VALUES (2099000000000000401, '000000', 'QC-CHECK-001', '质检工作台#1', 'INSPECTION', 'QC-BENCH-01', '测试设备', 'QC20260001',
  16, 'GZZ0017', '测试车间-QC工位', '2026-01-01', '2028-01-01', 'NORMAL',
  '[{"item": "卡尺校准标签", "standard": "在有效期内"}, {"item": "外观检查灯亮度", "standard": "正常"}, {"item": "静电手环导通", "standard": "小于1兆欧"}]',
  NULL, NULL, 'QC测试工位点检设备', -1, -1, NOW(), '0');

-- ---------- 6. ry-mes: SOP 已发布文档 + 工序绑定(复用 MinIO 已有文件) ----------
INSERT INTO `ry-mes`.sop_document
  (sop_id, tenant_id, sop_code, sop_name, sop_type, current_version, status, file_oss_id, file_type,
   description, approval_by, approval_time, create_dept, create_by, create_time, del_flag)
VALUES
  (2099000000000000501, '000000', 'SOP-QC-TEST-001', 'QC测试工位作业指导书(PDF)', 'PRODUCTION', 'V1.0', 'PUBLISHED',
   2081355254821195778, 'PDF', '工位机SOP测试: PDF 在线预览', 1, NOW(), -1, -1, NOW(), '0'),
  (2099000000000000502, '000000', 'SOP-QC-TEST-002', 'QC测试工位作业指导书(图片)', 'PRODUCTION', 'V1.0', 'PUBLISHED',
   2083823493513863169, 'IMAGE', '工位机SOP测试: 图片展示', 1, NOW(), -1, -1, NOW(), '0');

INSERT INTO `ry-mes`.sop_process_binding
  (binding_id, tenant_id, process_id, process_code, process_name, sop_id, version_no, is_default, sort_order,
   create_dept, create_by, create_time, del_flag)
VALUES
  (2099000000000000511, '000000', 4, 'QC', '质检', 2099000000000000501, 'V1.0', '1', 1, -1, -1, NOW(), '0'),
  (2099000000000000512, '000000', 4, 'QC', '质检', 2099000000000000502, 'V1.0', '0', 2, -1, -1, NOW(), '0');

-- ---------- 7. 完成提示 ----------
SELECT '工位码(6段)' AS item, 'STN|TEST|L1|QC|GENERAL|GZZ0002' AS value_
UNION ALL SELECT '刷写形态工位码', 'STN|TEST|L1|QC|FLASH|GZZ0002'
UNION ALL SELECT '主SN(过站)', 'SNQCTEST0001 ~ SNQCTEST0005'
UNION ALL SELECT '组件SN(绑定)', 'SNQCCOMP0001 ~ SNQCCOMP0006'
UNION ALL SELECT '上岗工号', 'SCZX20251222017 (张明)'
UNION ALL SELECT '上料批次', 'B20260822001(主料) / B20260822002(替代料) / B202606040001(错误料应拦截)'
UNION ALL SELECT '上料条码', 'RC20260822000001';
