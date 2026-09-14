-- =============================================================================
--  ERP/MES/IoT 全量数据清理脚本  (v2  自动生成 2026-09-14)
--  依据：doc/system/2026-09-14-ERP全量数据清理与基础资料重建方案.md (v2)
--  前置：p0_backup_all.sql + mysqldump + redis-cli SAVE + 停机（见方案 §4.5）
--  ⚠️ 尚未执行。请逐段审阅、逐段执行、逐段校验。
--  ⚠️ 顺序不可颠倒：MES → IoT → ERP（MES 有 43 处活引用指向 ERP）
-- =============================================================================

SET SQL_SAFE_UPDATES = 0;

-- =============================================================================
--  【M 批次】ry-mes 清理（含 M8 字典表，v2 改判为重建）
-- =============================================================================
DELETE FROM `ry-mes`.`dc_collection_point`;
DELETE FROM `ry-mes`.`dc_data_record`;
DELETE FROM `ry-mes`.`eq_equipment`;
DELETE FROM `ry-mes`.`eq_health_record`;
DELETE FROM `ry-mes`.`eq_inspection_record`;
DELETE FROM `ry-mes`.`eq_maintenance_plan`;
DELETE FROM `ry-mes`.`eq_maintenance_record`;
DELETE FROM `ry-mes`.`eq_oee_record`;
DELETE FROM `ry-mes`.`eq_patrol_record`;
DELETE FROM `ry-mes`.`eq_patrol_route`;
DELETE FROM `ry-mes`.`eq_patrol_task`;
DELETE FROM `ry-mes`.`eq_repair`;
DELETE FROM `ry-mes`.`eq_repair_spare`;
-- [SKIP] ry-mes.eq_spare_part  -- ★保留(配置)
DELETE FROM `ry-mes`.`eq_spare_stock_log`;
DELETE FROM `ry-mes`.`lt_lot`;
DELETE FROM `ry-mes`.`lt_sn_binding`;
DELETE FROM `ry-mes`.`lt_wo_consumption`;
DELETE FROM `ry-mes`.`mes_content_file`;
DELETE FROM `ry-mes`.`mes_firmware`;
DELETE FROM `ry-mes`.`mes_flash_record`;
DELETE FROM `ry-mes`.`mes_flash_type`;
DELETE FROM `ry-mes`.`mes_label_template`;
DELETE FROM `ry-mes`.`mes_material_load`;
DELETE FROM `ry-mes`.`mes_mbom`;
DELETE FROM `ry-mes`.`mes_mbom_line`;
DELETE FROM `ry-mes`.`mes_release_package`;
DELETE FROM `ry-mes`.`mes_release_pkg_bom_item`;
DELETE FROM `ry-mes`.`mes_release_pkg_config`;
DELETE FROM `ry-mes`.`mes_release_pkg_firmware`;
DELETE FROM `ry-mes`.`mes_resource_item`;
DELETE FROM `ry-mes`.`mes_resource_pool`;
DELETE FROM `ry-mes`.`mes_sn_bind_log`;
DELETE FROM `ry-mes`.`mes_station_device`;
DELETE FROM `ry-mes`.`mes_station_report`;
DELETE FROM `ry-mes`.`mes_test_item`;
DELETE FROM `ry-mes`.`mes_test_record`;
DELETE FROM `ry-mes`.`ncr_8d_report`;
DELETE FROM `ry-mes`.`ncr_defect`;
DELETE FROM `ry-mes`.`ncr_defect_code`;
DELETE FROM `ry-mes`.`ncr_order`;
DELETE FROM `ry-mes`.`ncr_rework_order`;
DELETE FROM `ry-mes`.`pro_andon`;
DELETE FROM `ry-mes`.`pro_dispatch`;
DELETE FROM `ry-mes`.`pro_flow_card`;
DELETE FROM `ry-mes`.`pro_flow_card_step`;
DELETE FROM `ry-mes`.`pro_sn`;
-- [SKIP] ry-mes.pro_step_check_rule  -- ★保留(配置)
-- [SKIP] ry-mes.pro_step_guide  -- ★保留(配置)
DELETE FROM `ry-mes`.`pro_work_order`;
DELETE FROM `ry-mes`.`pro_work_order_bom_line`;
DELETE FROM `ry-mes`.`pro_work_order_step`;
DELETE FROM `ry-mes`.`qc_inspect_detail`;
DELETE FROM `ry-mes`.`qc_inspect_item`;
DELETE FROM `ry-mes`.`qc_inspect_template`;
DELETE FROM `ry-mes`.`qc_ipqc`;
DELETE FROM `ry-mes`.`qc_iqc`;
DELETE FROM `ry-mes`.`qc_oqc`;
DELETE FROM `ry-mes`.`sop_document`;
DELETE FROM `ry-mes`.`sop_process_binding`;
DELETE FROM `ry-mes`.`sop_version`;
DELETE FROM `ry-mes`.`sop_view_log`;
DELETE FROM `ry-mes`.`spc_control_chart`;
DELETE FROM `ry-mes`.`spc_data_point`;
DELETE FROM `ry-mes`.`tg_maintenance_plan`;
DELETE FROM `ry-mes`.`tg_maintenance_record`;
DELETE FROM `ry-mes`.`tg_tooling`;
DELETE FROM `ry-mes`.`tg_usage_record`;

-- =============================================================================
--  【I 批次】ry-iot 清理（v2：iot_product 也重建）
-- =============================================================================
DELETE FROM `ry-iot`.`iot_device`;
DELETE FROM `ry-iot`.`iot_device_data`;
DELETE FROM `ry-iot`.`iot_product`;

-- =============================================================================
--  【E 批次】ry-erp 清理
--  ★★ 3D 演示数据保护（硬验收指标：删 4/8/24/37，留 1/4/104/55）
-- =============================================================================
DELETE FROM `ry-erp`.`bd_bom`;
DELETE FROM `ry-erp`.`bd_bom_alternative`;
DELETE FROM `ry-erp`.`bd_bom_detail`;
DELETE FROM `ry-erp`.`bd_category_attribute`;
DELETE FROM `ry-erp`.`bd_client`;
DELETE FROM `ry-erp`.`bd_client_credit`;
DELETE FROM `ry-erp`.`bd_material`;
DELETE FROM `ry-erp`.`bd_material_attribute_value`;
DELETE FROM `ry-erp`.`bd_material_category`;
DELETE FROM `ry-erp`.`bd_material_vendor`;
DELETE FROM `ry-erp`.`bd_process`;
DELETE FROM `ry-erp`.`bd_routing`;
DELETE FROM `ry-erp`.`bd_routing_step`;
-- [KEEP+RESET] ry-erp.bd_serial_number_rule  -- ★保留表，计数器在 C 批次重置，切勿 DELETE 整表
-- [SKIP] ry-erp.bd_unit_of_measure  -- ★保留
DELETE FROM `ry-erp`.`bd_vendor`;
DELETE FROM `ry-erp`.`bd_workshop`;
DELETE FROM `ry-erp`.`bd_workstation`;
DELETE FROM `ry-erp`.`erp_subcontract_doc`;
DELETE FROM `ry-erp`.`fa_asset`;
-- [SKIP] ry-erp.fa_asset_category  -- ★保留
DELETE FROM `ry-erp`.`fa_depreciation`;
DELETE FROM `ry-erp`.`fa_disposal`;
DELETE FROM `ry-erp`.`fin_close_log`;
DELETE FROM `ry-erp`.`fin_receipt`;
DELETE FROM `ry-erp`.`fin_settlement`;
-- [SKIP] ry-erp.gl_account  -- ★保留
DELETE FROM `ry-erp`.`gl_account_balance`;
DELETE FROM `ry-erp`.`gl_currency_rate`;
DELETE FROM `ry-erp`.`gl_period`;
DELETE FROM `ry-erp`.`gl_voucher`;
DELETE FROM `ry-erp`.`gl_voucher_line`;
DELETE FROM `ry-erp`.`po_requisition`;
DELETE FROM `ry-erp`.`po_requisition_detail`;
DELETE FROM `ry-erp`.`prd_capacity_calendar`;
DELETE FROM `ry-erp`.`prd_material_requirement`;
DELETE FROM `ry-erp`.`prd_material_requirement_detail`;
DELETE FROM `ry-erp`.`prd_production_plan`;
DELETE FROM `ry-erp`.`prd_rework_order`;
DELETE FROM `ry-erp`.`prd_schedule_plan`;
DELETE FROM `ry-erp`.`prd_schedule_task`;
DELETE FROM `ry-erp`.`prd_subcontract`;
DELETE FROM `ry-erp`.`prd_subcontract_bom_line`;
DELETE FROM `ry-erp`.`prd_subcontract_quote`;
DELETE FROM `ry-erp`.`prd_work_order`;
DELETE FROM `ry-erp`.`prd_work_order_resource`;
DELETE FROM `ry-erp`.`prd_work_report`;
DELETE FROM `ry-erp`.`pu_contract`;
DELETE FROM `ry-erp`.`pu_contract_line`;
DELETE FROM `ry-erp`.`pu_invoice`;
DELETE FROM `ry-erp`.`pu_invoice_line`;
DELETE FROM `ry-erp`.`pu_purchase_order`;
DELETE FROM `ry-erp`.`pu_purchase_order_line`;
DELETE FROM `ry-erp`.`pu_purchase_requisition`;
DELETE FROM `ry-erp`.`pu_purchase_requisition_line`;
DELETE FROM `ry-erp`.`pu_purchase_return`;
DELETE FROM `ry-erp`.`pu_purchase_return_line`;
DELETE FROM `ry-erp`.`pu_vendor_evaluation`;
-- [SKIP] ry-erp.report_item  -- ★保留
DELETE FROM `ry-erp`.`sch_attendance`;
DELETE FROM `ry-erp`.`sch_employee_schedule`;
DELETE FROM `ry-erp`.`sch_holiday`;
DELETE FROM `ry-erp`.`sch_leave_apply`;
DELETE FROM `ry-erp`.`sch_outdoor_punch`;
DELETE FROM `ry-erp`.`sch_plan`;
DELETE FROM `ry-erp`.`sch_plan_team`;
DELETE FROM `ry-erp`.`sch_punch_rule`;
DELETE FROM `ry-erp`.`sch_shift`;
DELETE FROM `ry-erp`.`sch_supplement_apply`;
DELETE FROM `ry-erp`.`sch_team`;
DELETE FROM `ry-erp`.`sch_team_member`;
DELETE FROM `ry-erp`.`sch_team_schedule`;
-- [SKIP] ry-erp.sch_worker  -- ★保留
DELETE FROM `ry-erp`.`so_contract`;
DELETE FROM `ry-erp`.`so_contract_line`;
DELETE FROM `ry-erp`.`so_credit_usage`;
DELETE FROM `ry-erp`.`so_customer_order`;
DELETE FROM `ry-erp`.`so_invoice`;
DELETE FROM `ry-erp`.`so_invoice_line`;
DELETE FROM `ry-erp`.`so_price_list`;
DELETE FROM `ry-erp`.`so_price_list_line`;
DELETE FROM `ry-erp`.`so_quotation`;
DELETE FROM `ry-erp`.`so_quotation_line`;
DELETE FROM `ry-erp`.`so_return`;
DELETE FROM `ry-erp`.`so_return_line`;
DELETE FROM `ry-erp`.`so_sales_order_header`;
DELETE FROM `ry-erp`.`so_sales_order_line`;
DELETE FROM `ry-erp`.`wm_arrival_notice`;
DELETE FROM `ry-erp`.`wm_arrival_notice_line`;
DELETE FROM `ry-erp`.`wm_barcode`;
-- [KEEP+RESET] ry-erp.wm_barcode_config  -- ★保留表，计数器在 C 批次重置，切勿 DELETE 整表
DELETE FROM `ry-erp`.`wm_batch`;
DELETE FROM `ry-erp`.`wm_material_handover`;
DELETE FROM `ry-erp`.`wm_material_handover_line`;
DELETE FROM `ry-erp`.`wm_material_receive`;
DELETE FROM `ry-erp`.`wm_material_receive_detail`;
DELETE FROM `ry-erp`.`wm_material_receive_line`;
DELETE FROM `ry-erp`.`wm_material_return`;
DELETE FROM `ry-erp`.`wm_material_return_line`;

-- ★ 3D 保护表：排除 3D 示例仓库(9900000001)
DELETE FROM `ry-erp`.`wm_material_stock` WHERE warehouse_id IS NULL OR warehouse_id <> 9900000001;
DELETE FROM `ry-erp`.`wm_material_stock_tx`;
DELETE FROM `ry-erp`.`wm_misc_issue`;
DELETE FROM `ry-erp`.`wm_misc_issue_line`;
DELETE FROM `ry-erp`.`wm_misc_recpt`;
DELETE FROM `ry-erp`.`wm_misc_recpt_line`;
DELETE FROM `ry-erp`.`wm_picking_rule`;
DELETE FROM `ry-erp`.`wm_production_issue`;
DELETE FROM `ry-erp`.`wm_production_issue_line`;
DELETE FROM `ry-erp`.`wm_production_recpt`;
DELETE FROM `ry-erp`.`wm_putaway_rule`;
DELETE FROM `ry-erp`.`wm_sales_issue`;
DELETE FROM `ry-erp`.`wm_sales_issue_line`;
DELETE FROM `ry-erp`.`wm_sn`;
DELETE FROM `ry-erp`.`wm_stock_alert`;
DELETE FROM `ry-erp`.`wm_stock_reconciliation`;
DELETE FROM `ry-erp`.`wm_stock_taking_line`;
DELETE FROM `ry-erp`.`wm_stock_taking_plan`;

-- ★ 3D 保护表：排除 3D 示例仓库(9900000001)
DELETE FROM `ry-erp`.`wm_storage_area` WHERE warehouse_id IS NULL OR warehouse_id <> 9900000001;

-- ★ 3D 保护表：排除 3D 示例仓库(9900000001)
DELETE FROM `ry-erp`.`wm_storage_location` WHERE warehouse_id IS NULL OR warehouse_id <> 9900000001;
DELETE FROM `ry-erp`.`wm_subcontract_issue`;
DELETE FROM `ry-erp`.`wm_subcontract_issue_line`;
DELETE FROM `ry-erp`.`wm_subcontract_receive`;
DELETE FROM `ry-erp`.`wm_transfer`;
DELETE FROM `ry-erp`.`wm_transfer_line`;

-- ★ 3D 保护表：排除 3D 示例仓库(9900000001)
DELETE FROM `ry-erp`.`wm_warehouse` WHERE warehouse_id IS NULL OR warehouse_id <> 9900000001;

-- =============================================================================
--  【校验1】3D 演示数据必须完好（期望 1 / 4 / 104 / 55）—— 不符即刻回滚
-- =============================================================================
SELECT 'warehouse' AS t, COUNT(*) AS c FROM `ry-erp`.wm_warehouse        WHERE warehouse_id = 9900000001
UNION ALL SELECT 'area',     COUNT(*) FROM `ry-erp`.wm_storage_area     WHERE warehouse_id = 9900000001
UNION ALL SELECT 'location', COUNT(*) FROM `ry-erp`.wm_storage_location WHERE warehouse_id = 9900000001
UNION ALL SELECT 'stock',    COUNT(*) FROM `ry-erp`.wm_material_stock  WHERE warehouse_id = 9900000001;

-- =============================================================================
--  【C 批次】重置 5 套编码计数器（方案 §4.1）
-- =============================================================================
-- C1 引擎主计数器（机制 A）。先导出基线用于回滚（期望 106 行）：
--    SELECT r.rule_code,c.gen_date,c.gen_index,c.last_serial_no,c.last_result
--    FROM `ry-vue`.sys_auto_code_result c JOIN `ry-vue`.sys_auto_code_rule r ON r.rule_id=c.rule_id;
DELETE FROM `ry-vue`.sys_auto_code_result WHERE tenant_id = '000000';
-- C2 条码计数器（机制 C）
UPDATE `ry-erp`.wm_barcode_config SET current_sequence = 0 WHERE tenant_id = '000000';
-- C3 产品级序列号规则（机制 D）
UPDATE `ry-erp`.bd_serial_number_rule SET current_seq = 0, attr1 = NULL WHERE tenant_id = '000000';
-- C4 Redis 序号（机制 B/E）—— 命令行执行：
--   redis-cli -a <pwd> -n 0 --scan --pattern 'erp:batch:seq:*'     | xargs -r redis-cli -a <pwd> -n 0 DEL
--   redis-cli -a <pwd> -n 0 --scan --pattern 'erp:sn:seq:*'        | xargs -r redis-cli -a <pwd> -n 0 DEL
--   redis-cli -a <pwd> -n 0 --scan --pattern 'erp:code:seq:lock:*' | xargs -r redis-cli -a <pwd> -n 0 DEL
--   redis-cli -a <pwd> -n 0 --scan --pattern 'mes:eq:repair:no:*'  | xargs -r redis-cli -a <pwd> -n 0 DEL
-- C5 校验
SELECT 'auto_code_result(期望0)' AS chk, COUNT(*) AS c FROM `ry-vue`.sys_auto_code_result
UNION ALL SELECT 'auto_code_rule(须46)',  COUNT(*) FROM `ry-vue`.sys_auto_code_rule
UNION ALL SELECT 'auto_code_part(须124)', COUNT(*) FROM `ry-vue`.sys_auto_code_part
UNION ALL SELECT 'barcode_seq_nonzero', COUNT(*) FROM `ry-erp`.wm_barcode_config WHERE current_sequence <> 0;

-- =============================================================================
--  【F 批次】遗留缺陷修复（方案 §7.5 / §7.12）—— 必须在 P7 重建之前完成
-- =============================================================================
ALTER TABLE `ry-erp`.wm_material_return      COMMENT '生产退料单';
ALTER TABLE `ry-erp`.wm_material_return_line COMMENT '生产退料单行';
UPDATE `ry-vue`.sys_auto_code_rule SET rule_name='退料单编号' WHERE rule_id=295;
-- F1 WM_RETURN_CODE/WM_HANDOVER_CODE 补 part（否则生成必抛「生成的编码为空！」）
--    执行前确认：SELECT rule_id,COUNT(*) FROM `ry-vue`.sys_auto_code_part WHERE rule_id IN (295,296) GROUP BY rule_id;
INSERT INTO `ry-vue`.sys_auto_code_part
  (part_id, tenant_id, rule_id, part_index, part_type, part_code, part_name, part_length,
   fix_character, seria_start_no, seria_step, cycle_flag, cycle_method, del_flag) VALUES
  (29501,'000000',295,1,'FIXCHAR','PREFIX','前缀',3,'TL',NULL,NULL,NULL,NULL,'0'),
  (29502,'000000',295,2,'NOWDATE','DATE','日期',8,NULL,NULL,NULL,NULL,NULL,'0'),
  (29503,'000000',295,3,'SERIALNO','SERIAL','流水号',4,NULL,1,1,'Y','DAY','0'),
  (29601,'000000',296,1,'FIXCHAR','PREFIX','前缀',2,'HJ',NULL,NULL,NULL,NULL,'0'),
  (29602,'000000',296,2,'NOWDATE','DATE','日期',8,NULL,NULL,NULL,NULL,NULL,'0'),
  (29603,'000000',296,3,'SERIALNO','SERIAL','流水号',4,NULL,1,1,'Y','DAY','0');
-- F2 SCH_TEAM_CODE 首段 INPUTCHAR -> FIXCHAR
UPDATE `ry-vue`.sys_auto_code_part SET part_type='FIXCHAR', part_code='PREFIX', part_name='前缀', fix_character='BZ', part_length=2 WHERE rule_id=270 AND part_index=1;

SET SQL_SAFE_UPDATES = 1;
