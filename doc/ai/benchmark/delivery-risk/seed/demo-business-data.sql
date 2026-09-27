-- 演示业务数据，由 scripts/build-seed.py 生成，禁止用于 plus 或生产环境。

-- DATABASE() 字面量护栏确保在其他数据库误执行时不产生写入。

START TRANSACTION;

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750001, '000000', 'DEMO-AI-DECISION-001-PLAN', 'Demo production 001', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750001 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745001, '000000', 'DEMO-AI-DECISION-001', 9007199254750001, 'Demo production 001', 9007199254746001, 'DEMO-AI-DECISION-001-PRODUCT', 'Demo product 001', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785001, 'Demo workshop 001', 9007199254786001, 'Demo resource 001', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745001 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751001, '000000', 'DEMO-AI-DECISION-001-MRP', 9007199254750001, 'DEMO-AI-DECISION-001-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 5, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751001 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752001, '000000', 9007199254751001, 9007199254747001, 'DEMO-AI-DECISION-001-COMP', 'Demo component 001', 5, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752001 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753001, '000000', 9007199254747001, 'DEMO-AI-DECISION-001-COMP', 'Demo component 001', 9007199254789000, 'DEMO-AI-DECISION-WH', 100, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753001 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754001, '000000', 'DEMO-AI-DECISION-001-SCHEDULE', 'Demo baseline schedule 001', 9007199254785001, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754001 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755001, '000000', 9007199254754001, 9007199254745001, 'DEMO-AI-DECISION-001', 1, 9007199254749001, 'WORKSTATION', 9007199254786001, 'Demo resource 001', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 12, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755001 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855104, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786001, 'DEMO-AI-DECISION-001-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855104 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855105, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786001, 'DEMO-AI-DECISION-001-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855105 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855106, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786001, 'DEMO-AI-DECISION-001-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855106 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855107, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786001, 'DEMO-AI-DECISION-001-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855107 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855108, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786001, 'DEMO-AI-DECISION-001-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855108 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855109, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786001, 'DEMO-AI-DECISION-001-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855109 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855110, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786001, 'DEMO-AI-DECISION-001-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855110 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855111, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786001, 'DEMO-AI-DECISION-001-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855111 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855112, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786001, 'DEMO-AI-DECISION-001-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855112 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855113, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786001, 'DEMO-AI-DECISION-001-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855113 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855114, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786001, 'DEMO-AI-DECISION-001-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855114 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855115, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786001, 'DEMO-AI-DECISION-001-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855115 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855116, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786001, 'DEMO-AI-DECISION-001-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855116 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855117, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786001, 'DEMO-AI-DECISION-001-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855117 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855118, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786001, 'DEMO-AI-DECISION-001-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855118 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855119, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786001, 'DEMO-AI-DECISION-001-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855119 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855120, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786001, 'DEMO-AI-DECISION-001-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855120 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855121, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786001, 'DEMO-AI-DECISION-001-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855121 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855122, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786001, 'DEMO-AI-DECISION-001-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855122 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855123, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786001, 'DEMO-AI-DECISION-001-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855123 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855124, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786001, 'DEMO-AI-DECISION-001-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855124 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855125, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786001, 'DEMO-AI-DECISION-001-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855125 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855126, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786001, 'DEMO-AI-DECISION-001-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855126 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855127, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786001, 'DEMO-AI-DECISION-001-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855127 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855128, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786001, 'DEMO-AI-DECISION-001-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855128 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855129, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786001, 'DEMO-AI-DECISION-001-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855129 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855130, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786001, 'DEMO-AI-DECISION-001-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855130 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759001, '000000', 9007199254788001, 'DEMO-AI-DECISION-001-VENDOR', 'Demo supplier 001', DATE_FORMAT(CURDATE(), '%Y-%m'), 98, 100, 98, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759001 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757001, '000000', 'DEMO-AI-DECISION-001-PO', 9007199254788001, 'DEMO-AI-DECISION-001-VENDOR', 'Demo supplier 001', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757001 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758001, '000000', 9007199254757001, 9007199254747001, 'DEMO-AI-DECISION-001-COMP', 'Demo component 001', 1, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758001 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750002, '000000', 'DEMO-AI-DECISION-002-PLAN', 'Demo production 002', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750002 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745002, '000000', 'DEMO-AI-DECISION-002', 9007199254750002, 'Demo production 002', 9007199254746002, 'DEMO-AI-DECISION-002-PRODUCT', 'Demo product 002', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785002, 'Demo workshop 002', 9007199254786002, 'Demo resource 002', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745002 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751002, '000000', 'DEMO-AI-DECISION-002-MRP', 9007199254750002, 'DEMO-AI-DECISION-002-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 5, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751002 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752002, '000000', 9007199254751002, 9007199254747002, 'DEMO-AI-DECISION-002-COMP', 'Demo component 002', 5, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752002 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753002, '000000', 9007199254747002, 'DEMO-AI-DECISION-002-COMP', 'Demo component 002', 9007199254789000, 'DEMO-AI-DECISION-WH', 100, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753002 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754002, '000000', 'DEMO-AI-DECISION-002-SCHEDULE', 'Demo baseline schedule 002', 9007199254785002, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754002 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755002, '000000', 9007199254754002, 9007199254745002, 'DEMO-AI-DECISION-002', 1, 9007199254749002, 'WORKSTATION', 9007199254786002, 'Demo resource 002', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 12, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755002 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855205, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786002, 'DEMO-AI-DECISION-002-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855205 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855206, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786002, 'DEMO-AI-DECISION-002-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855206 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855207, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786002, 'DEMO-AI-DECISION-002-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855207 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855208, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786002, 'DEMO-AI-DECISION-002-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855208 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855209, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786002, 'DEMO-AI-DECISION-002-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855209 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855210, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786002, 'DEMO-AI-DECISION-002-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855210 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855211, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786002, 'DEMO-AI-DECISION-002-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855211 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855212, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786002, 'DEMO-AI-DECISION-002-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855212 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855213, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786002, 'DEMO-AI-DECISION-002-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855213 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855214, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786002, 'DEMO-AI-DECISION-002-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855214 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855215, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786002, 'DEMO-AI-DECISION-002-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855215 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855216, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786002, 'DEMO-AI-DECISION-002-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855216 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855217, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786002, 'DEMO-AI-DECISION-002-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855217 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855218, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786002, 'DEMO-AI-DECISION-002-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855218 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855219, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786002, 'DEMO-AI-DECISION-002-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855219 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855220, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786002, 'DEMO-AI-DECISION-002-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855220 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855221, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786002, 'DEMO-AI-DECISION-002-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855221 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855222, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786002, 'DEMO-AI-DECISION-002-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855222 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855223, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786002, 'DEMO-AI-DECISION-002-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855223 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855224, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786002, 'DEMO-AI-DECISION-002-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855224 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855225, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786002, 'DEMO-AI-DECISION-002-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855225 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855226, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786002, 'DEMO-AI-DECISION-002-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855226 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855227, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786002, 'DEMO-AI-DECISION-002-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855227 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855228, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786002, 'DEMO-AI-DECISION-002-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855228 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855229, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786002, 'DEMO-AI-DECISION-002-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855229 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855230, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786002, 'DEMO-AI-DECISION-002-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855230 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855231, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786002, 'DEMO-AI-DECISION-002-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855231 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759002, '000000', 9007199254788002, 'DEMO-AI-DECISION-002-VENDOR', 'Demo supplier 002', DATE_FORMAT(CURDATE(), '%Y-%m'), 98, 100, 98, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759002 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757002, '000000', 'DEMO-AI-DECISION-002-PO', 9007199254788002, 'DEMO-AI-DECISION-002-VENDOR', 'Demo supplier 002', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757002 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758002, '000000', 9007199254757002, 9007199254747002, 'DEMO-AI-DECISION-002-COMP', 'Demo component 002', 1, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758002 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750003, '000000', 'DEMO-AI-DECISION-003-PLAN', 'Demo production 003', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750003 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745003, '000000', 'DEMO-AI-DECISION-003', 9007199254750003, 'Demo production 003', 9007199254746003, 'DEMO-AI-DECISION-003-PRODUCT', 'Demo product 003', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785003, 'Demo workshop 003', 9007199254786003, 'Demo resource 003', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745003 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751003, '000000', 'DEMO-AI-DECISION-003-MRP', 9007199254750003, 'DEMO-AI-DECISION-003-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 5, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751003 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752003, '000000', 9007199254751003, 9007199254747003, 'DEMO-AI-DECISION-003-COMP', 'Demo component 003', 5, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752003 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753003, '000000', 9007199254747003, 'DEMO-AI-DECISION-003-COMP', 'Demo component 003', 9007199254789000, 'DEMO-AI-DECISION-WH', 100, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753003 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754003, '000000', 'DEMO-AI-DECISION-003-SCHEDULE', 'Demo baseline schedule 003', 9007199254785003, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754003 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755003, '000000', 9007199254754003, 9007199254745003, 'DEMO-AI-DECISION-003', 1, 9007199254749003, 'WORKSTATION', 9007199254786003, 'Demo resource 003', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 12, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755003 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855306, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786003, 'DEMO-AI-DECISION-003-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855306 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855307, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786003, 'DEMO-AI-DECISION-003-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855307 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855308, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786003, 'DEMO-AI-DECISION-003-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855308 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855309, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786003, 'DEMO-AI-DECISION-003-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855309 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855310, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786003, 'DEMO-AI-DECISION-003-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855310 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855311, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786003, 'DEMO-AI-DECISION-003-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855311 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855312, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786003, 'DEMO-AI-DECISION-003-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855312 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855313, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786003, 'DEMO-AI-DECISION-003-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855313 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855314, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786003, 'DEMO-AI-DECISION-003-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855314 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855315, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786003, 'DEMO-AI-DECISION-003-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855315 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855316, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786003, 'DEMO-AI-DECISION-003-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855316 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855317, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786003, 'DEMO-AI-DECISION-003-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855317 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855318, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786003, 'DEMO-AI-DECISION-003-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855318 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855319, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786003, 'DEMO-AI-DECISION-003-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855319 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855320, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786003, 'DEMO-AI-DECISION-003-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855320 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855321, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786003, 'DEMO-AI-DECISION-003-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855321 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855322, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786003, 'DEMO-AI-DECISION-003-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855322 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855323, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786003, 'DEMO-AI-DECISION-003-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855323 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855324, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786003, 'DEMO-AI-DECISION-003-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855324 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855325, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786003, 'DEMO-AI-DECISION-003-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855325 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855326, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786003, 'DEMO-AI-DECISION-003-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855326 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855327, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786003, 'DEMO-AI-DECISION-003-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855327 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855328, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786003, 'DEMO-AI-DECISION-003-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855328 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855329, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786003, 'DEMO-AI-DECISION-003-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855329 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855330, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786003, 'DEMO-AI-DECISION-003-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855330 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855331, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786003, 'DEMO-AI-DECISION-003-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855331 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855332, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786003, 'DEMO-AI-DECISION-003-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855332 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759003, '000000', 9007199254788003, 'DEMO-AI-DECISION-003-VENDOR', 'Demo supplier 003', DATE_FORMAT(CURDATE(), '%Y-%m'), 98, 100, 98, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759003 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757003, '000000', 'DEMO-AI-DECISION-003-PO', 9007199254788003, 'DEMO-AI-DECISION-003-VENDOR', 'Demo supplier 003', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757003 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758003, '000000', 9007199254757003, 9007199254747003, 'DEMO-AI-DECISION-003-COMP', 'Demo component 003', 1, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758003 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750004, '000000', 'DEMO-AI-DECISION-004-PLAN', 'Demo production 004', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750004 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745004, '000000', 'DEMO-AI-DECISION-004', 9007199254750004, 'Demo production 004', 9007199254746004, 'DEMO-AI-DECISION-004-PRODUCT', 'Demo product 004', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785004, 'Demo workshop 004', 9007199254786004, 'Demo resource 004', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745004 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751004, '000000', 'DEMO-AI-DECISION-004-MRP', 9007199254750004, 'DEMO-AI-DECISION-004-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 5, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751004 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752004, '000000', 9007199254751004, 9007199254747004, 'DEMO-AI-DECISION-004-COMP', 'Demo component 004', 5, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752004 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753004, '000000', 9007199254747004, 'DEMO-AI-DECISION-004-COMP', 'Demo component 004', 9007199254789000, 'DEMO-AI-DECISION-WH', 100, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753004 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754004, '000000', 'DEMO-AI-DECISION-004-SCHEDULE', 'Demo baseline schedule 004', 9007199254785004, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754004 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755004, '000000', 9007199254754004, 9007199254745004, 'DEMO-AI-DECISION-004', 1, 9007199254749004, 'WORKSTATION', 9007199254786004, 'Demo resource 004', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 12, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755004 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855407, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786004, 'DEMO-AI-DECISION-004-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855407 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855408, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786004, 'DEMO-AI-DECISION-004-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855408 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855409, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786004, 'DEMO-AI-DECISION-004-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855409 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855410, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786004, 'DEMO-AI-DECISION-004-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855410 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855411, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786004, 'DEMO-AI-DECISION-004-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855411 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855412, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786004, 'DEMO-AI-DECISION-004-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855412 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855413, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786004, 'DEMO-AI-DECISION-004-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855413 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855414, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786004, 'DEMO-AI-DECISION-004-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855414 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855415, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786004, 'DEMO-AI-DECISION-004-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855415 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855416, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786004, 'DEMO-AI-DECISION-004-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855416 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855417, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786004, 'DEMO-AI-DECISION-004-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855417 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855418, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786004, 'DEMO-AI-DECISION-004-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855418 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855419, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786004, 'DEMO-AI-DECISION-004-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855419 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855420, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786004, 'DEMO-AI-DECISION-004-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855420 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855421, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786004, 'DEMO-AI-DECISION-004-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855421 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855422, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786004, 'DEMO-AI-DECISION-004-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855422 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855423, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786004, 'DEMO-AI-DECISION-004-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855423 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855424, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786004, 'DEMO-AI-DECISION-004-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855424 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855425, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786004, 'DEMO-AI-DECISION-004-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855425 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855426, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786004, 'DEMO-AI-DECISION-004-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855426 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855427, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786004, 'DEMO-AI-DECISION-004-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855427 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855428, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786004, 'DEMO-AI-DECISION-004-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855428 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855429, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786004, 'DEMO-AI-DECISION-004-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855429 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855430, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786004, 'DEMO-AI-DECISION-004-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855430 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855431, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786004, 'DEMO-AI-DECISION-004-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855431 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855432, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786004, 'DEMO-AI-DECISION-004-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855432 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855433, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786004, 'DEMO-AI-DECISION-004-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855433 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759004, '000000', 9007199254788004, 'DEMO-AI-DECISION-004-VENDOR', 'Demo supplier 004', DATE_FORMAT(CURDATE(), '%Y-%m'), 98, 100, 98, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759004 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757004, '000000', 'DEMO-AI-DECISION-004-PO', 9007199254788004, 'DEMO-AI-DECISION-004-VENDOR', 'Demo supplier 004', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757004 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758004, '000000', 9007199254757004, 9007199254747004, 'DEMO-AI-DECISION-004-COMP', 'Demo component 004', 1, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758004 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750005, '000000', 'DEMO-AI-DECISION-005-PLAN', 'Demo production 005', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750005 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745005, '000000', 'DEMO-AI-DECISION-005', 9007199254750005, 'Demo production 005', 9007199254746005, 'DEMO-AI-DECISION-005-PRODUCT', 'Demo product 005', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785005, 'Demo workshop 005', 9007199254786005, 'Demo resource 005', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745005 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751005, '000000', 'DEMO-AI-DECISION-005-MRP', 9007199254750005, 'DEMO-AI-DECISION-005-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 5, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751005 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752005, '000000', 9007199254751005, 9007199254747005, 'DEMO-AI-DECISION-005-COMP', 'Demo component 005', 5, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752005 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753005, '000000', 9007199254747005, 'DEMO-AI-DECISION-005-COMP', 'Demo component 005', 9007199254789000, 'DEMO-AI-DECISION-WH', 100, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753005 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754005, '000000', 'DEMO-AI-DECISION-005-SCHEDULE', 'Demo baseline schedule 005', 9007199254785005, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754005 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755005, '000000', 9007199254754005, 9007199254745005, 'DEMO-AI-DECISION-005', 1, 9007199254749005, 'WORKSTATION', 9007199254786005, 'Demo resource 005', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 12, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755005 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855508, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786005, 'DEMO-AI-DECISION-005-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855508 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855509, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786005, 'DEMO-AI-DECISION-005-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855509 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855510, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786005, 'DEMO-AI-DECISION-005-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855510 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855511, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786005, 'DEMO-AI-DECISION-005-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855511 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855512, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786005, 'DEMO-AI-DECISION-005-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855512 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855513, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786005, 'DEMO-AI-DECISION-005-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855513 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855514, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786005, 'DEMO-AI-DECISION-005-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855514 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855515, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786005, 'DEMO-AI-DECISION-005-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855515 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855516, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786005, 'DEMO-AI-DECISION-005-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855516 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855517, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786005, 'DEMO-AI-DECISION-005-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855517 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855518, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786005, 'DEMO-AI-DECISION-005-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855518 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855519, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786005, 'DEMO-AI-DECISION-005-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855519 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855520, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786005, 'DEMO-AI-DECISION-005-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855520 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855521, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786005, 'DEMO-AI-DECISION-005-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855521 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855522, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786005, 'DEMO-AI-DECISION-005-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855522 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855523, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786005, 'DEMO-AI-DECISION-005-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855523 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855524, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786005, 'DEMO-AI-DECISION-005-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855524 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855525, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786005, 'DEMO-AI-DECISION-005-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855525 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855526, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786005, 'DEMO-AI-DECISION-005-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855526 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855527, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786005, 'DEMO-AI-DECISION-005-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855527 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855528, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786005, 'DEMO-AI-DECISION-005-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855528 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855529, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786005, 'DEMO-AI-DECISION-005-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855529 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855530, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786005, 'DEMO-AI-DECISION-005-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855530 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855531, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786005, 'DEMO-AI-DECISION-005-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855531 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855532, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786005, 'DEMO-AI-DECISION-005-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855532 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855533, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786005, 'DEMO-AI-DECISION-005-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855533 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855534, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786005, 'DEMO-AI-DECISION-005-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855534 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759005, '000000', 9007199254788005, 'DEMO-AI-DECISION-005-VENDOR', 'Demo supplier 005', DATE_FORMAT(CURDATE(), '%Y-%m'), 98, 100, 98, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759005 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757005, '000000', 'DEMO-AI-DECISION-005-PO', 9007199254788005, 'DEMO-AI-DECISION-005-VENDOR', 'Demo supplier 005', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757005 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758005, '000000', 9007199254757005, 9007199254747005, 'DEMO-AI-DECISION-005-COMP', 'Demo component 005', 1, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758005 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750006, '000000', 'DEMO-AI-DECISION-006-PLAN', 'Demo production 006', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750006 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745006, '000000', 'DEMO-AI-DECISION-006', 9007199254750006, 'Demo production 006', 9007199254746006, 'DEMO-AI-DECISION-006-PRODUCT', 'Demo product 006', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785006, 'Demo workshop 006', 9007199254786006, 'Demo resource 006', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745006 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751006, '000000', 'DEMO-AI-DECISION-006-MRP', 9007199254750006, 'DEMO-AI-DECISION-006-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 18, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751006 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752006, '000000', 9007199254751006, 9007199254747006, 'DEMO-AI-DECISION-006-COMP', 'Demo component 006', 18, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752006 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753006, '000000', 9007199254747006, 'DEMO-AI-DECISION-006-COMP', 'Demo component 006', 9007199254789000, 'DEMO-AI-DECISION-WH', 0, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753006 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754006, '000000', 'DEMO-AI-DECISION-006-SCHEDULE', 'Demo baseline schedule 006', 9007199254785006, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754006 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755006, '000000', 9007199254754006, 9007199254745006, 'DEMO-AI-DECISION-006', 1, 9007199254749006, 'WORKSTATION', 9007199254786006, 'Demo resource 006', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 17 DAY), 15, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755006 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855609, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786006, 'DEMO-AI-DECISION-006-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855609 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855610, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786006, 'DEMO-AI-DECISION-006-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855610 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855611, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786006, 'DEMO-AI-DECISION-006-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855611 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855612, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786006, 'DEMO-AI-DECISION-006-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855612 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855613, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786006, 'DEMO-AI-DECISION-006-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855613 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855614, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786006, 'DEMO-AI-DECISION-006-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855614 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855615, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786006, 'DEMO-AI-DECISION-006-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855615 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855616, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786006, 'DEMO-AI-DECISION-006-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855616 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855617, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786006, 'DEMO-AI-DECISION-006-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855617 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855618, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786006, 'DEMO-AI-DECISION-006-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855618 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855619, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786006, 'DEMO-AI-DECISION-006-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855619 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855620, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786006, 'DEMO-AI-DECISION-006-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855620 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855621, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786006, 'DEMO-AI-DECISION-006-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855621 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855622, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786006, 'DEMO-AI-DECISION-006-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855622 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855623, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786006, 'DEMO-AI-DECISION-006-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855623 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855624, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786006, 'DEMO-AI-DECISION-006-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855624 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855625, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786006, 'DEMO-AI-DECISION-006-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855625 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855626, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786006, 'DEMO-AI-DECISION-006-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855626 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855627, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786006, 'DEMO-AI-DECISION-006-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855627 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855628, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786006, 'DEMO-AI-DECISION-006-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855628 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855629, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786006, 'DEMO-AI-DECISION-006-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855629 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855630, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786006, 'DEMO-AI-DECISION-006-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855630 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855631, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786006, 'DEMO-AI-DECISION-006-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855631 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855632, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786006, 'DEMO-AI-DECISION-006-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855632 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855633, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786006, 'DEMO-AI-DECISION-006-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855633 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855634, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786006, 'DEMO-AI-DECISION-006-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855634 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855635, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786006, 'DEMO-AI-DECISION-006-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855635 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759006, '000000', 9007199254788006, 'DEMO-AI-DECISION-006-VENDOR', 'Demo supplier 006', DATE_FORMAT(CURDATE(), '%Y-%m'), 60, 100, 60, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759006 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757006, '000000', 'DEMO-AI-DECISION-006-PO', 9007199254788006, 'DEMO-AI-DECISION-006-VENDOR', 'Demo supplier 006', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757006 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758006, '000000', 9007199254757006, 9007199254747006, 'DEMO-AI-DECISION-006-COMP', 'Demo component 006', 18, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758006 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750007, '000000', 'DEMO-AI-DECISION-007-PLAN', 'Demo production 007', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750007 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745007, '000000', 'DEMO-AI-DECISION-007', 9007199254750007, 'Demo production 007', 9007199254746007, 'DEMO-AI-DECISION-007-PRODUCT', 'Demo product 007', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785007, 'Demo workshop 007', 9007199254786007, 'Demo resource 007', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745007 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751007, '000000', 'DEMO-AI-DECISION-007-MRP', 9007199254750007, 'DEMO-AI-DECISION-007-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 20, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751007 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752007, '000000', 9007199254751007, 9007199254747007, 'DEMO-AI-DECISION-007-COMP', 'Demo component 007', 20, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752007 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753007, '000000', 9007199254747007, 'DEMO-AI-DECISION-007-COMP', 'Demo component 007', 9007199254789000, 'DEMO-AI-DECISION-WH', 0, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753007 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754007, '000000', 'DEMO-AI-DECISION-007-SCHEDULE', 'Demo baseline schedule 007', 9007199254785007, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754007 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755007, '000000', 9007199254754007, 9007199254745007, 'DEMO-AI-DECISION-007', 1, 9007199254749007, 'WORKSTATION', 9007199254786007, 'Demo resource 007', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 12, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755007 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855710, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786007, 'DEMO-AI-DECISION-007-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855710 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855711, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786007, 'DEMO-AI-DECISION-007-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855711 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855712, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786007, 'DEMO-AI-DECISION-007-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855712 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855713, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786007, 'DEMO-AI-DECISION-007-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855713 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855714, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786007, 'DEMO-AI-DECISION-007-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855714 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855715, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786007, 'DEMO-AI-DECISION-007-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855715 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855716, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786007, 'DEMO-AI-DECISION-007-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855716 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855717, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786007, 'DEMO-AI-DECISION-007-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855717 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855718, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786007, 'DEMO-AI-DECISION-007-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855718 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855719, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786007, 'DEMO-AI-DECISION-007-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855719 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855720, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786007, 'DEMO-AI-DECISION-007-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855720 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855721, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786007, 'DEMO-AI-DECISION-007-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855721 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855722, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786007, 'DEMO-AI-DECISION-007-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855722 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855723, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786007, 'DEMO-AI-DECISION-007-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855723 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855724, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786007, 'DEMO-AI-DECISION-007-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855724 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855725, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786007, 'DEMO-AI-DECISION-007-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855725 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855726, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786007, 'DEMO-AI-DECISION-007-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855726 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855727, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786007, 'DEMO-AI-DECISION-007-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855727 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855728, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786007, 'DEMO-AI-DECISION-007-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855728 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855729, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786007, 'DEMO-AI-DECISION-007-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855729 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855730, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786007, 'DEMO-AI-DECISION-007-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855730 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855731, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786007, 'DEMO-AI-DECISION-007-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855731 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855732, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786007, 'DEMO-AI-DECISION-007-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855732 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855733, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786007, 'DEMO-AI-DECISION-007-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855733 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855734, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786007, 'DEMO-AI-DECISION-007-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855734 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855735, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786007, 'DEMO-AI-DECISION-007-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855735 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855736, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786007, 'DEMO-AI-DECISION-007-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855736 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759007, '000000', 9007199254788007, 'DEMO-AI-DECISION-007-VENDOR', 'Demo supplier 007', DATE_FORMAT(CURDATE(), '%Y-%m'), 98, 100, 98, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759007 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757007, '000000', 'DEMO-AI-DECISION-007-PO', 9007199254788007, 'DEMO-AI-DECISION-007-VENDOR', 'Demo supplier 007', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757007 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758007, '000000', 9007199254757007, 9007199254747007, 'DEMO-AI-DECISION-007-COMP', 'Demo component 007', 1, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758007 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750008, '000000', 'DEMO-AI-DECISION-008-PLAN', 'Demo production 008', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750008 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745008, '000000', 'DEMO-AI-DECISION-008', 9007199254750008, 'Demo production 008', 9007199254746008, 'DEMO-AI-DECISION-008-PRODUCT', 'Demo product 008', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785008, 'Demo workshop 008', 9007199254786008, 'Demo resource 008', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745008 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751008, '000000', 'DEMO-AI-DECISION-008-MRP', 9007199254750008, 'DEMO-AI-DECISION-008-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 21, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751008 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752008, '000000', 9007199254751008, 9007199254747008, 'DEMO-AI-DECISION-008-COMP', 'Demo component 008', 21, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752008 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753008, '000000', 9007199254747008, 'DEMO-AI-DECISION-008-COMP', 'Demo component 008', 9007199254789000, 'DEMO-AI-DECISION-WH', 0, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753008 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754008, '000000', 'DEMO-AI-DECISION-008-SCHEDULE', 'Demo baseline schedule 008', 9007199254785008, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754008 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755008, '000000', 9007199254754008, 9007199254745008, 'DEMO-AI-DECISION-008', 1, 9007199254749008, 'WORKSTATION', 9007199254786008, 'Demo resource 008', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 12, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755008 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855811, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786008, 'DEMO-AI-DECISION-008-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855811 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855812, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786008, 'DEMO-AI-DECISION-008-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855812 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855813, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786008, 'DEMO-AI-DECISION-008-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855813 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855814, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786008, 'DEMO-AI-DECISION-008-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855814 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855815, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786008, 'DEMO-AI-DECISION-008-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855815 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855816, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786008, 'DEMO-AI-DECISION-008-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855816 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855817, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786008, 'DEMO-AI-DECISION-008-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855817 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855818, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786008, 'DEMO-AI-DECISION-008-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855818 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855819, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786008, 'DEMO-AI-DECISION-008-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855819 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855820, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786008, 'DEMO-AI-DECISION-008-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855820 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855821, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786008, 'DEMO-AI-DECISION-008-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855821 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855822, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786008, 'DEMO-AI-DECISION-008-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855822 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855823, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786008, 'DEMO-AI-DECISION-008-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855823 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855824, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786008, 'DEMO-AI-DECISION-008-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855824 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855825, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786008, 'DEMO-AI-DECISION-008-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855825 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855826, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786008, 'DEMO-AI-DECISION-008-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855826 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855827, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786008, 'DEMO-AI-DECISION-008-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855827 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855828, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786008, 'DEMO-AI-DECISION-008-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855828 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855829, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786008, 'DEMO-AI-DECISION-008-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855829 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855830, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786008, 'DEMO-AI-DECISION-008-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855830 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855831, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786008, 'DEMO-AI-DECISION-008-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855831 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855832, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786008, 'DEMO-AI-DECISION-008-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855832 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855833, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786008, 'DEMO-AI-DECISION-008-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855833 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855834, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786008, 'DEMO-AI-DECISION-008-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855834 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855835, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786008, 'DEMO-AI-DECISION-008-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855835 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855836, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786008, 'DEMO-AI-DECISION-008-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855836 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855837, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786008, 'DEMO-AI-DECISION-008-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855837 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759008, '000000', 9007199254788008, 'DEMO-AI-DECISION-008-VENDOR', 'Demo supplier 008', DATE_FORMAT(CURDATE(), '%Y-%m'), 98, 100, 98, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759008 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757008, '000000', 'DEMO-AI-DECISION-008-PO', 9007199254788008, 'DEMO-AI-DECISION-008-VENDOR', 'Demo supplier 008', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757008 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758008, '000000', 9007199254757008, 9007199254747008, 'DEMO-AI-DECISION-008-COMP', 'Demo component 008', 1, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758008 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750009, '000000', 'DEMO-AI-DECISION-009-PLAN', 'Demo production 009', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750009 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745009, '000000', 'DEMO-AI-DECISION-009', 9007199254750009, 'Demo production 009', 9007199254746009, 'DEMO-AI-DECISION-009-PRODUCT', 'Demo product 009', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785009, 'Demo workshop 009', 9007199254786009, 'Demo resource 009', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745009 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751009, '000000', 'DEMO-AI-DECISION-009-MRP', 9007199254750009, 'DEMO-AI-DECISION-009-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 22, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751009 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752009, '000000', 9007199254751009, 9007199254747009, 'DEMO-AI-DECISION-009-COMP', 'Demo component 009', 22, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752009 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753009, '000000', 9007199254747009, 'DEMO-AI-DECISION-009-COMP', 'Demo component 009', 9007199254789000, 'DEMO-AI-DECISION-WH', 0, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753009 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754009, '000000', 'DEMO-AI-DECISION-009-SCHEDULE', 'Demo baseline schedule 009', 9007199254785009, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754009 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755009, '000000', 9007199254754009, 9007199254745009, 'DEMO-AI-DECISION-009', 1, 9007199254749009, 'WORKSTATION', 9007199254786009, 'Demo resource 009', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 12, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755009 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855912, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786009, 'DEMO-AI-DECISION-009-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855912 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855913, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786009, 'DEMO-AI-DECISION-009-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855913 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855914, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786009, 'DEMO-AI-DECISION-009-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855914 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855915, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786009, 'DEMO-AI-DECISION-009-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855915 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855916, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786009, 'DEMO-AI-DECISION-009-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855916 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855917, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786009, 'DEMO-AI-DECISION-009-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855917 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855918, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786009, 'DEMO-AI-DECISION-009-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855918 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855919, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786009, 'DEMO-AI-DECISION-009-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855919 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855920, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786009, 'DEMO-AI-DECISION-009-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855920 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855921, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786009, 'DEMO-AI-DECISION-009-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855921 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855922, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786009, 'DEMO-AI-DECISION-009-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855922 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855923, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786009, 'DEMO-AI-DECISION-009-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855923 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855924, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786009, 'DEMO-AI-DECISION-009-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855924 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855925, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786009, 'DEMO-AI-DECISION-009-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855925 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855926, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786009, 'DEMO-AI-DECISION-009-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855926 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855927, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786009, 'DEMO-AI-DECISION-009-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855927 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855928, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786009, 'DEMO-AI-DECISION-009-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855928 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855929, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786009, 'DEMO-AI-DECISION-009-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855929 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855930, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786009, 'DEMO-AI-DECISION-009-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855930 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855931, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786009, 'DEMO-AI-DECISION-009-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855931 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855932, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786009, 'DEMO-AI-DECISION-009-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855932 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855933, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786009, 'DEMO-AI-DECISION-009-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855933 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855934, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786009, 'DEMO-AI-DECISION-009-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855934 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855935, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786009, 'DEMO-AI-DECISION-009-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855935 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855936, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786009, 'DEMO-AI-DECISION-009-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855936 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855937, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786009, 'DEMO-AI-DECISION-009-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855937 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254855938, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786009, 'DEMO-AI-DECISION-009-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254855938 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759009, '000000', 9007199254788009, 'DEMO-AI-DECISION-009-VENDOR', 'Demo supplier 009', DATE_FORMAT(CURDATE(), '%Y-%m'), 98, 100, 98, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759009 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757009, '000000', 'DEMO-AI-DECISION-009-PO', 9007199254788009, 'DEMO-AI-DECISION-009-VENDOR', 'Demo supplier 009', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757009 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758009, '000000', 9007199254757009, 9007199254747009, 'DEMO-AI-DECISION-009-COMP', 'Demo component 009', 1, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758009 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750010, '000000', 'DEMO-AI-DECISION-010-PLAN', 'Demo production 010', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750010 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745010, '000000', 'DEMO-AI-DECISION-010', 9007199254750010, 'Demo production 010', 9007199254746010, 'DEMO-AI-DECISION-010-PRODUCT', 'Demo product 010', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785010, 'Demo workshop 010', 9007199254786010, 'Demo resource 010', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745010 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751010, '000000', 'DEMO-AI-DECISION-010-MRP', 9007199254750010, 'DEMO-AI-DECISION-010-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 23, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751010 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752010, '000000', 9007199254751010, 9007199254747010, 'DEMO-AI-DECISION-010-COMP', 'Demo component 010', 23, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752010 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753010, '000000', 9007199254747010, 'DEMO-AI-DECISION-010-COMP', 'Demo component 010', 9007199254789000, 'DEMO-AI-DECISION-WH', 0, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753010 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754010, '000000', 'DEMO-AI-DECISION-010-SCHEDULE', 'Demo baseline schedule 010', 9007199254785010, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754010 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755010, '000000', 9007199254754010, 9007199254745010, 'DEMO-AI-DECISION-010', 1, 9007199254749010, 'WORKSTATION', 9007199254786010, 'Demo resource 010', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 12, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755010 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856013, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786010, 'DEMO-AI-DECISION-010-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856013 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856014, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786010, 'DEMO-AI-DECISION-010-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856014 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856015, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786010, 'DEMO-AI-DECISION-010-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856015 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856016, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786010, 'DEMO-AI-DECISION-010-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856016 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856017, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786010, 'DEMO-AI-DECISION-010-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856017 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856018, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786010, 'DEMO-AI-DECISION-010-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856018 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856019, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786010, 'DEMO-AI-DECISION-010-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856019 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856020, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786010, 'DEMO-AI-DECISION-010-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856020 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856021, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786010, 'DEMO-AI-DECISION-010-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856021 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856022, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786010, 'DEMO-AI-DECISION-010-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856022 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856023, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786010, 'DEMO-AI-DECISION-010-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856023 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856024, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786010, 'DEMO-AI-DECISION-010-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856024 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856025, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786010, 'DEMO-AI-DECISION-010-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856025 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856026, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786010, 'DEMO-AI-DECISION-010-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856026 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856027, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786010, 'DEMO-AI-DECISION-010-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856027 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856028, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786010, 'DEMO-AI-DECISION-010-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856028 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856029, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786010, 'DEMO-AI-DECISION-010-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856029 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856030, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786010, 'DEMO-AI-DECISION-010-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856030 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856031, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786010, 'DEMO-AI-DECISION-010-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856031 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856032, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786010, 'DEMO-AI-DECISION-010-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856032 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856033, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786010, 'DEMO-AI-DECISION-010-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856033 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856034, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786010, 'DEMO-AI-DECISION-010-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856034 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856035, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786010, 'DEMO-AI-DECISION-010-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856035 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856036, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786010, 'DEMO-AI-DECISION-010-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856036 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856037, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786010, 'DEMO-AI-DECISION-010-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856037 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856038, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786010, 'DEMO-AI-DECISION-010-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856038 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856039, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786010, 'DEMO-AI-DECISION-010-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856039 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759010, '000000', 9007199254788010, 'DEMO-AI-DECISION-010-VENDOR', 'Demo supplier 010', DATE_FORMAT(CURDATE(), '%Y-%m'), 98, 100, 98, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759010 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757010, '000000', 'DEMO-AI-DECISION-010-PO', 9007199254788010, 'DEMO-AI-DECISION-010-VENDOR', 'Demo supplier 010', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757010 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758010, '000000', 9007199254757010, 9007199254747010, 'DEMO-AI-DECISION-010-COMP', 'Demo component 010', 1, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758010 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750011, '000000', 'DEMO-AI-DECISION-011-PLAN', 'Demo production 011', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750011 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745011, '000000', 'DEMO-AI-DECISION-011', 9007199254750011, 'Demo production 011', 9007199254746011, 'DEMO-AI-DECISION-011-PRODUCT', 'Demo product 011', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785011, 'Demo workshop 011', 9007199254786011, 'Demo resource 011', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745011 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751011, '000000', 'DEMO-AI-DECISION-011-MRP', 9007199254750011, 'DEMO-AI-DECISION-011-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 24, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751011 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752011, '000000', 9007199254751011, 9007199254747011, 'DEMO-AI-DECISION-011-COMP', 'Demo component 011', 24, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752011 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753011, '000000', 9007199254747011, 'DEMO-AI-DECISION-011-COMP', 'Demo component 011', 9007199254789000, 'DEMO-AI-DECISION-WH', 0, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753011 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754011, '000000', 'DEMO-AI-DECISION-011-SCHEDULE', 'Demo baseline schedule 011', 9007199254785011, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754011 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755011, '000000', 9007199254754011, 9007199254745011, 'DEMO-AI-DECISION-011', 1, 9007199254749011, 'WORKSTATION', 9007199254786011, 'Demo resource 011', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 12, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755011 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856114, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786011, 'DEMO-AI-DECISION-011-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856114 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856115, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786011, 'DEMO-AI-DECISION-011-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856115 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856116, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786011, 'DEMO-AI-DECISION-011-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856116 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856117, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786011, 'DEMO-AI-DECISION-011-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856117 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856118, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786011, 'DEMO-AI-DECISION-011-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856118 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856119, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786011, 'DEMO-AI-DECISION-011-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856119 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856120, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786011, 'DEMO-AI-DECISION-011-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856120 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856121, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786011, 'DEMO-AI-DECISION-011-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856121 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856122, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786011, 'DEMO-AI-DECISION-011-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856122 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856123, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786011, 'DEMO-AI-DECISION-011-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856123 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856124, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786011, 'DEMO-AI-DECISION-011-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856124 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856125, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786011, 'DEMO-AI-DECISION-011-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856125 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856126, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786011, 'DEMO-AI-DECISION-011-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856126 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856127, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786011, 'DEMO-AI-DECISION-011-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856127 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856128, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786011, 'DEMO-AI-DECISION-011-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856128 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856129, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786011, 'DEMO-AI-DECISION-011-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856129 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856130, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786011, 'DEMO-AI-DECISION-011-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856130 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856131, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786011, 'DEMO-AI-DECISION-011-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856131 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856132, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786011, 'DEMO-AI-DECISION-011-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856132 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856133, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786011, 'DEMO-AI-DECISION-011-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856133 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856134, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786011, 'DEMO-AI-DECISION-011-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856134 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856135, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786011, 'DEMO-AI-DECISION-011-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856135 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856136, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786011, 'DEMO-AI-DECISION-011-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856136 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856137, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786011, 'DEMO-AI-DECISION-011-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856137 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856138, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786011, 'DEMO-AI-DECISION-011-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856138 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856139, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786011, 'DEMO-AI-DECISION-011-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856139 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856140, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786011, 'DEMO-AI-DECISION-011-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856140 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759011, '000000', 9007199254788011, 'DEMO-AI-DECISION-011-VENDOR', 'Demo supplier 011', DATE_FORMAT(CURDATE(), '%Y-%m'), 98, 100, 98, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759011 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757011, '000000', 'DEMO-AI-DECISION-011-PO', 9007199254788011, 'DEMO-AI-DECISION-011-VENDOR', 'Demo supplier 011', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757011 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758011, '000000', 9007199254757011, 9007199254747011, 'DEMO-AI-DECISION-011-COMP', 'Demo component 011', 1, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758011 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750012, '000000', 'DEMO-AI-DECISION-012-PLAN', 'Demo production 012', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750012 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745012, '000000', 'DEMO-AI-DECISION-012', 9007199254750012, 'Demo production 012', 9007199254746012, 'DEMO-AI-DECISION-012-PRODUCT', 'Demo product 012', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785012, 'Demo workshop 012', 9007199254786012, 'Demo resource 012', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745012 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751012, '000000', 'DEMO-AI-DECISION-012-MRP', 9007199254750012, 'DEMO-AI-DECISION-012-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 2, 41, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751012 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752012, '000000', 9007199254751012, 9007199254747012, 'DEMO-AI-DECISION-012-COMP', 'Demo component 012', 38, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752012 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254905012, '000000', 9007199254751012, 9007199254895012, 'DEMO-AI-DECISION-012-COMP-2', 'Demo second component 012', 3, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254905012 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254915012, '000000', 9007199254895012, 'DEMO-AI-DECISION-012-COMP-2', 'Demo second component 012', 9007199254789000, 'DEMO-AI-DECISION-WH', 0, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254915012 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753012, '000000', 9007199254747012, 'DEMO-AI-DECISION-012-COMP', 'Demo component 012', 9007199254789000, 'DEMO-AI-DECISION-WH', 0, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753012 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754012, '000000', 'DEMO-AI-DECISION-012-SCHEDULE', 'Demo baseline schedule 012', 9007199254785012, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754012 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755012, '000000', 9007199254754012, 9007199254745012, 'DEMO-AI-DECISION-012', 1, 9007199254749012, 'WORKSTATION', 9007199254786012, 'Demo resource 012', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 16 DAY), 14, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755012 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856215, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786012, 'DEMO-AI-DECISION-012-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856215 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856216, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786012, 'DEMO-AI-DECISION-012-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856216 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856217, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786012, 'DEMO-AI-DECISION-012-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856217 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856218, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786012, 'DEMO-AI-DECISION-012-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856218 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856219, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786012, 'DEMO-AI-DECISION-012-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856219 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856220, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786012, 'DEMO-AI-DECISION-012-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856220 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856221, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786012, 'DEMO-AI-DECISION-012-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856221 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856222, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786012, 'DEMO-AI-DECISION-012-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856222 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856223, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786012, 'DEMO-AI-DECISION-012-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856223 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856224, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786012, 'DEMO-AI-DECISION-012-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856224 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856225, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786012, 'DEMO-AI-DECISION-012-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856225 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856226, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786012, 'DEMO-AI-DECISION-012-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856226 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856227, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786012, 'DEMO-AI-DECISION-012-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856227 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856228, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786012, 'DEMO-AI-DECISION-012-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856228 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856229, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786012, 'DEMO-AI-DECISION-012-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856229 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856230, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786012, 'DEMO-AI-DECISION-012-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856230 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856231, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786012, 'DEMO-AI-DECISION-012-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856231 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856232, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786012, 'DEMO-AI-DECISION-012-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856232 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856233, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786012, 'DEMO-AI-DECISION-012-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856233 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856234, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786012, 'DEMO-AI-DECISION-012-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856234 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856235, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786012, 'DEMO-AI-DECISION-012-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856235 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856236, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786012, 'DEMO-AI-DECISION-012-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856236 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856237, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786012, 'DEMO-AI-DECISION-012-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856237 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856238, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786012, 'DEMO-AI-DECISION-012-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856238 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856239, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786012, 'DEMO-AI-DECISION-012-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856239 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856240, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786012, 'DEMO-AI-DECISION-012-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856240 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856241, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786012, 'DEMO-AI-DECISION-012-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856241 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759012, '000000', 9007199254788012, 'DEMO-AI-DECISION-012-VENDOR', 'Demo supplier 012', DATE_FORMAT(CURDATE(), '%Y-%m'), 98, 100, 98, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759012 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757012, '000000', 'DEMO-AI-DECISION-012-PO', 9007199254788012, 'DEMO-AI-DECISION-012-VENDOR', 'Demo supplier 012', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757012 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758012, '000000', 9007199254757012, 9007199254747012, 'DEMO-AI-DECISION-012-COMP', 'Demo component 012', 1, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758012 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750013, '000000', 'DEMO-AI-DECISION-013-PLAN', 'Demo production 013', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750013 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745013, '000000', 'DEMO-AI-DECISION-013', 9007199254750013, 'Demo production 013', 9007199254746013, 'DEMO-AI-DECISION-013-PRODUCT', 'Demo product 013', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785013, 'Demo workshop 013', 9007199254786013, 'Demo resource 013', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745013 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751013, '000000', 'DEMO-AI-DECISION-013-MRP', 9007199254750013, 'DEMO-AI-DECISION-013-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 2, 42, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751013 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752013, '000000', 9007199254751013, 9007199254747013, 'DEMO-AI-DECISION-013-COMP', 'Demo component 013', 39, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752013 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254905013, '000000', 9007199254751013, 9007199254895013, 'DEMO-AI-DECISION-013-COMP-2', 'Demo second component 013', 3, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254905013 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254915013, '000000', 9007199254895013, 'DEMO-AI-DECISION-013-COMP-2', 'Demo second component 013', 9007199254789000, 'DEMO-AI-DECISION-WH', 0, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254915013 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753013, '000000', 9007199254747013, 'DEMO-AI-DECISION-013-COMP', 'Demo component 013', 9007199254789000, 'DEMO-AI-DECISION-WH', 0, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753013 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754013, '000000', 'DEMO-AI-DECISION-013-SCHEDULE', 'Demo baseline schedule 013', 9007199254785013, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754013 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755013, '000000', 9007199254754013, 9007199254745013, 'DEMO-AI-DECISION-013', 1, 9007199254749013, 'WORKSTATION', 9007199254786013, 'Demo resource 013', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 16 DAY), 14, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755013 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856316, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786013, 'DEMO-AI-DECISION-013-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856316 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856317, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786013, 'DEMO-AI-DECISION-013-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856317 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856318, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786013, 'DEMO-AI-DECISION-013-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856318 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856319, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786013, 'DEMO-AI-DECISION-013-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856319 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856320, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786013, 'DEMO-AI-DECISION-013-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856320 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856321, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786013, 'DEMO-AI-DECISION-013-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856321 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856322, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786013, 'DEMO-AI-DECISION-013-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856322 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856323, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786013, 'DEMO-AI-DECISION-013-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856323 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856324, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786013, 'DEMO-AI-DECISION-013-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856324 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856325, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786013, 'DEMO-AI-DECISION-013-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856325 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856326, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786013, 'DEMO-AI-DECISION-013-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856326 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856327, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786013, 'DEMO-AI-DECISION-013-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856327 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856328, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786013, 'DEMO-AI-DECISION-013-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856328 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856329, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786013, 'DEMO-AI-DECISION-013-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856329 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856330, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786013, 'DEMO-AI-DECISION-013-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856330 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856331, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786013, 'DEMO-AI-DECISION-013-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856331 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856332, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786013, 'DEMO-AI-DECISION-013-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856332 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856333, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786013, 'DEMO-AI-DECISION-013-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856333 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856334, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786013, 'DEMO-AI-DECISION-013-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856334 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856335, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786013, 'DEMO-AI-DECISION-013-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856335 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856336, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786013, 'DEMO-AI-DECISION-013-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856336 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856337, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786013, 'DEMO-AI-DECISION-013-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856337 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856338, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786013, 'DEMO-AI-DECISION-013-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856338 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856339, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786013, 'DEMO-AI-DECISION-013-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856339 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856340, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786013, 'DEMO-AI-DECISION-013-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856340 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856341, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786013, 'DEMO-AI-DECISION-013-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856341 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856342, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786013, 'DEMO-AI-DECISION-013-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856342 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759013, '000000', 9007199254788013, 'DEMO-AI-DECISION-013-VENDOR', 'Demo supplier 013', DATE_FORMAT(CURDATE(), '%Y-%m'), 98, 100, 98, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759013 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757013, '000000', 'DEMO-AI-DECISION-013-PO', 9007199254788013, 'DEMO-AI-DECISION-013-VENDOR', 'Demo supplier 013', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757013 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758013, '000000', 9007199254757013, 9007199254747013, 'DEMO-AI-DECISION-013-COMP', 'Demo component 013', 1, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758013 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750014, '000000', 'DEMO-AI-DECISION-014-PLAN', 'Demo production 014', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750014 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745014, '000000', 'DEMO-AI-DECISION-014', 9007199254750014, 'Demo production 014', 9007199254746014, 'DEMO-AI-DECISION-014-PRODUCT', 'Demo product 014', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785014, 'Demo workshop 014', 9007199254786014, 'Demo resource 014', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745014 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751014, '000000', 'DEMO-AI-DECISION-014-MRP', 9007199254750014, 'DEMO-AI-DECISION-014-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 2, 43, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751014 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752014, '000000', 9007199254751014, 9007199254747014, 'DEMO-AI-DECISION-014-COMP', 'Demo component 014', 40, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752014 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254905014, '000000', 9007199254751014, 9007199254895014, 'DEMO-AI-DECISION-014-COMP-2', 'Demo second component 014', 3, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254905014 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254915014, '000000', 9007199254895014, 'DEMO-AI-DECISION-014-COMP-2', 'Demo second component 014', 9007199254789000, 'DEMO-AI-DECISION-WH', 0, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254915014 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753014, '000000', 9007199254747014, 'DEMO-AI-DECISION-014-COMP', 'Demo component 014', 9007199254789000, 'DEMO-AI-DECISION-WH', 0, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753014 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754014, '000000', 'DEMO-AI-DECISION-014-SCHEDULE', 'Demo baseline schedule 014', 9007199254785014, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754014 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755014, '000000', 9007199254754014, 9007199254745014, 'DEMO-AI-DECISION-014', 1, 9007199254749014, 'WORKSTATION', 9007199254786014, 'Demo resource 014', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 16 DAY), 14, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755014 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856417, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786014, 'DEMO-AI-DECISION-014-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856417 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856418, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786014, 'DEMO-AI-DECISION-014-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856418 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856419, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786014, 'DEMO-AI-DECISION-014-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856419 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856420, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786014, 'DEMO-AI-DECISION-014-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856420 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856421, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786014, 'DEMO-AI-DECISION-014-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856421 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856422, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786014, 'DEMO-AI-DECISION-014-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856422 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856423, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786014, 'DEMO-AI-DECISION-014-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856423 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856424, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786014, 'DEMO-AI-DECISION-014-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856424 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856425, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786014, 'DEMO-AI-DECISION-014-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856425 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856426, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786014, 'DEMO-AI-DECISION-014-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856426 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856427, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786014, 'DEMO-AI-DECISION-014-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856427 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856428, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786014, 'DEMO-AI-DECISION-014-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856428 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856429, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786014, 'DEMO-AI-DECISION-014-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856429 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856430, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786014, 'DEMO-AI-DECISION-014-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856430 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856431, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786014, 'DEMO-AI-DECISION-014-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856431 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856432, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786014, 'DEMO-AI-DECISION-014-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856432 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856433, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786014, 'DEMO-AI-DECISION-014-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856433 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856434, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786014, 'DEMO-AI-DECISION-014-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856434 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856435, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786014, 'DEMO-AI-DECISION-014-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856435 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856436, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786014, 'DEMO-AI-DECISION-014-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856436 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856437, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786014, 'DEMO-AI-DECISION-014-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856437 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856438, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786014, 'DEMO-AI-DECISION-014-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856438 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856439, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786014, 'DEMO-AI-DECISION-014-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856439 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856440, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786014, 'DEMO-AI-DECISION-014-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856440 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856441, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786014, 'DEMO-AI-DECISION-014-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856441 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856442, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786014, 'DEMO-AI-DECISION-014-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856442 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856443, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786014, 'DEMO-AI-DECISION-014-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856443 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759014, '000000', 9007199254788014, 'DEMO-AI-DECISION-014-VENDOR', 'Demo supplier 014', DATE_FORMAT(CURDATE(), '%Y-%m'), 98, 100, 98, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759014 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757014, '000000', 'DEMO-AI-DECISION-014-PO', 9007199254788014, 'DEMO-AI-DECISION-014-VENDOR', 'Demo supplier 014', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757014 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758014, '000000', 9007199254757014, 9007199254747014, 'DEMO-AI-DECISION-014-COMP', 'Demo component 014', 1, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758014 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750015, '000000', 'DEMO-AI-DECISION-015-PLAN', 'Demo production 015', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750015 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745015, '000000', 'DEMO-AI-DECISION-015', 9007199254750015, 'Demo production 015', 9007199254746015, 'DEMO-AI-DECISION-015-PRODUCT', 'Demo product 015', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785015, 'Demo workshop 015', 9007199254786015, 'Demo resource 015', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745015 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751015, '000000', 'DEMO-AI-DECISION-015-MRP', 9007199254750015, 'DEMO-AI-DECISION-015-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 2, 44, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751015 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752015, '000000', 9007199254751015, 9007199254747015, 'DEMO-AI-DECISION-015-COMP', 'Demo component 015', 41, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752015 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254905015, '000000', 9007199254751015, 9007199254895015, 'DEMO-AI-DECISION-015-COMP-2', 'Demo second component 015', 3, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254905015 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254915015, '000000', 9007199254895015, 'DEMO-AI-DECISION-015-COMP-2', 'Demo second component 015', 9007199254789000, 'DEMO-AI-DECISION-WH', 0, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254915015 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753015, '000000', 9007199254747015, 'DEMO-AI-DECISION-015-COMP', 'Demo component 015', 9007199254789000, 'DEMO-AI-DECISION-WH', 0, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753015 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754015, '000000', 'DEMO-AI-DECISION-015-SCHEDULE', 'Demo baseline schedule 015', 9007199254785015, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754015 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755015, '000000', 9007199254754015, 9007199254745015, 'DEMO-AI-DECISION-015', 1, 9007199254749015, 'WORKSTATION', 9007199254786015, 'Demo resource 015', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 16 DAY), 14, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755015 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856518, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786015, 'DEMO-AI-DECISION-015-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856518 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856519, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786015, 'DEMO-AI-DECISION-015-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856519 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856520, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786015, 'DEMO-AI-DECISION-015-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856520 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856521, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786015, 'DEMO-AI-DECISION-015-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856521 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856522, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786015, 'DEMO-AI-DECISION-015-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856522 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856523, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786015, 'DEMO-AI-DECISION-015-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856523 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856524, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786015, 'DEMO-AI-DECISION-015-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856524 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856525, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786015, 'DEMO-AI-DECISION-015-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856525 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856526, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786015, 'DEMO-AI-DECISION-015-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856526 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856527, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786015, 'DEMO-AI-DECISION-015-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856527 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856528, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786015, 'DEMO-AI-DECISION-015-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856528 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856529, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786015, 'DEMO-AI-DECISION-015-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856529 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856530, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786015, 'DEMO-AI-DECISION-015-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856530 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856531, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786015, 'DEMO-AI-DECISION-015-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856531 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856532, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786015, 'DEMO-AI-DECISION-015-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856532 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856533, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786015, 'DEMO-AI-DECISION-015-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856533 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856534, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786015, 'DEMO-AI-DECISION-015-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856534 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856535, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786015, 'DEMO-AI-DECISION-015-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856535 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856536, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786015, 'DEMO-AI-DECISION-015-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856536 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856537, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786015, 'DEMO-AI-DECISION-015-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856537 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856538, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786015, 'DEMO-AI-DECISION-015-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856538 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856539, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786015, 'DEMO-AI-DECISION-015-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856539 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856540, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786015, 'DEMO-AI-DECISION-015-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856540 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856541, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786015, 'DEMO-AI-DECISION-015-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856541 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856542, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786015, 'DEMO-AI-DECISION-015-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856542 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856543, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786015, 'DEMO-AI-DECISION-015-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856543 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856544, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786015, 'DEMO-AI-DECISION-015-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856544 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759015, '000000', 9007199254788015, 'DEMO-AI-DECISION-015-VENDOR', 'Demo supplier 015', DATE_FORMAT(CURDATE(), '%Y-%m'), 98, 100, 98, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759015 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757015, '000000', 'DEMO-AI-DECISION-015-PO', 9007199254788015, 'DEMO-AI-DECISION-015-VENDOR', 'Demo supplier 015', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757015 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758015, '000000', 9007199254757015, 9007199254747015, 'DEMO-AI-DECISION-015-COMP', 'Demo component 015', 1, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758015 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750016, '000000', 'DEMO-AI-DECISION-016-PLAN', 'Demo production 016', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750016 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745016, '000000', 'DEMO-AI-DECISION-016', 9007199254750016, 'Demo production 016', 9007199254746016, 'DEMO-AI-DECISION-016-PRODUCT', 'Demo product 016', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785016, 'Demo workshop 016', 9007199254786016, 'Demo resource 016', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745016 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751016, '000000', 'DEMO-AI-DECISION-016-MRP', 9007199254750016, 'DEMO-AI-DECISION-016-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 26, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751016 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752016, '000000', 9007199254751016, 9007199254747016, 'DEMO-AI-DECISION-016-COMP', 'Demo component 016', 26, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752016 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753016, '000000', 9007199254747016, 'DEMO-AI-DECISION-016-COMP', 'Demo component 016', 9007199254789000, 'DEMO-AI-DECISION-WH', 0, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753016 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754016, '000000', 'DEMO-AI-DECISION-016-SCHEDULE', 'Demo baseline schedule 016', 9007199254785016, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754016 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755016, '000000', 9007199254754016, 9007199254745016, 'DEMO-AI-DECISION-016', 1, 9007199254749016, 'WORKSTATION', 9007199254786016, 'Demo resource 016', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 17 DAY), 15, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755016 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856619, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786016, 'DEMO-AI-DECISION-016-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856619 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856620, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786016, 'DEMO-AI-DECISION-016-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856620 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856621, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786016, 'DEMO-AI-DECISION-016-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856621 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856622, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786016, 'DEMO-AI-DECISION-016-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856622 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856623, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786016, 'DEMO-AI-DECISION-016-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856623 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856624, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786016, 'DEMO-AI-DECISION-016-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856624 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856625, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786016, 'DEMO-AI-DECISION-016-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856625 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856626, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786016, 'DEMO-AI-DECISION-016-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856626 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856627, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786016, 'DEMO-AI-DECISION-016-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856627 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856628, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786016, 'DEMO-AI-DECISION-016-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856628 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856629, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786016, 'DEMO-AI-DECISION-016-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856629 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856630, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786016, 'DEMO-AI-DECISION-016-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856630 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856631, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786016, 'DEMO-AI-DECISION-016-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856631 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856632, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786016, 'DEMO-AI-DECISION-016-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856632 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856633, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786016, 'DEMO-AI-DECISION-016-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856633 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856634, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786016, 'DEMO-AI-DECISION-016-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856634 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856635, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786016, 'DEMO-AI-DECISION-016-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856635 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856636, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786016, 'DEMO-AI-DECISION-016-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856636 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856637, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786016, 'DEMO-AI-DECISION-016-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856637 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856638, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786016, 'DEMO-AI-DECISION-016-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856638 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856639, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786016, 'DEMO-AI-DECISION-016-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856639 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856640, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786016, 'DEMO-AI-DECISION-016-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856640 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856641, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786016, 'DEMO-AI-DECISION-016-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856641 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856642, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786016, 'DEMO-AI-DECISION-016-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856642 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856643, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786016, 'DEMO-AI-DECISION-016-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856643 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856644, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786016, 'DEMO-AI-DECISION-016-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856644 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856645, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786016, 'DEMO-AI-DECISION-016-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856645 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759016, '000000', 9007199254788016, 'DEMO-AI-DECISION-016-VENDOR', 'Demo supplier 016', DATE_FORMAT(CURDATE(), '%Y-%m'), 60, 100, 60, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759016 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757016, '000000', 'DEMO-AI-DECISION-016-PO', 9007199254788016, 'DEMO-AI-DECISION-016-VENDOR', 'Demo supplier 016', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757016 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758016, '000000', 9007199254757016, 9007199254747016, 'DEMO-AI-DECISION-016-COMP', 'Demo component 016', 26, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758016 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750017, '000000', 'DEMO-AI-DECISION-017-PLAN', 'Demo production 017', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750017 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745017, '000000', 'DEMO-AI-DECISION-017', 9007199254750017, 'Demo production 017', 9007199254746017, 'DEMO-AI-DECISION-017-PRODUCT', 'Demo product 017', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785017, 'Demo workshop 017', 9007199254786017, 'Demo resource 017', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745017 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751017, '000000', 'DEMO-AI-DECISION-017-MRP', 9007199254750017, 'DEMO-AI-DECISION-017-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 27, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751017 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752017, '000000', 9007199254751017, 9007199254747017, 'DEMO-AI-DECISION-017-COMP', 'Demo component 017', 27, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752017 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753017, '000000', 9007199254747017, 'DEMO-AI-DECISION-017-COMP', 'Demo component 017', 9007199254789000, 'DEMO-AI-DECISION-WH', 0, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753017 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754017, '000000', 'DEMO-AI-DECISION-017-SCHEDULE', 'Demo baseline schedule 017', 9007199254785017, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754017 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755017, '000000', 9007199254754017, 9007199254745017, 'DEMO-AI-DECISION-017', 1, 9007199254749017, 'WORKSTATION', 9007199254786017, 'Demo resource 017', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 17 DAY), 15, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755017 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856720, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786017, 'DEMO-AI-DECISION-017-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856720 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856721, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786017, 'DEMO-AI-DECISION-017-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856721 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856722, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786017, 'DEMO-AI-DECISION-017-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856722 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856723, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786017, 'DEMO-AI-DECISION-017-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856723 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856724, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786017, 'DEMO-AI-DECISION-017-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856724 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856725, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786017, 'DEMO-AI-DECISION-017-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856725 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856726, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786017, 'DEMO-AI-DECISION-017-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856726 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856727, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786017, 'DEMO-AI-DECISION-017-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856727 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856728, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786017, 'DEMO-AI-DECISION-017-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856728 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856729, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786017, 'DEMO-AI-DECISION-017-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856729 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856730, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786017, 'DEMO-AI-DECISION-017-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856730 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856731, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786017, 'DEMO-AI-DECISION-017-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856731 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856732, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786017, 'DEMO-AI-DECISION-017-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856732 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856733, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786017, 'DEMO-AI-DECISION-017-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856733 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856734, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786017, 'DEMO-AI-DECISION-017-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856734 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856735, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786017, 'DEMO-AI-DECISION-017-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856735 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856736, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786017, 'DEMO-AI-DECISION-017-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856736 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856737, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786017, 'DEMO-AI-DECISION-017-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856737 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856738, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786017, 'DEMO-AI-DECISION-017-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856738 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856739, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786017, 'DEMO-AI-DECISION-017-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856739 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856740, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786017, 'DEMO-AI-DECISION-017-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856740 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856741, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786017, 'DEMO-AI-DECISION-017-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856741 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856742, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786017, 'DEMO-AI-DECISION-017-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856742 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856743, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786017, 'DEMO-AI-DECISION-017-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856743 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856744, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786017, 'DEMO-AI-DECISION-017-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856744 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856745, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786017, 'DEMO-AI-DECISION-017-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856745 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856746, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786017, 'DEMO-AI-DECISION-017-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856746 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759017, '000000', 9007199254788017, 'DEMO-AI-DECISION-017-VENDOR', 'Demo supplier 017', DATE_FORMAT(CURDATE(), '%Y-%m'), 60, 100, 60, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759017 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757017, '000000', 'DEMO-AI-DECISION-017-PO', 9007199254788017, 'DEMO-AI-DECISION-017-VENDOR', 'Demo supplier 017', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757017 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758017, '000000', 9007199254757017, 9007199254747017, 'DEMO-AI-DECISION-017-COMP', 'Demo component 017', 27, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758017 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750018, '000000', 'DEMO-AI-DECISION-018-PLAN', 'Demo production 018', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750018 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745018, '000000', 'DEMO-AI-DECISION-018', 9007199254750018, 'Demo production 018', 9007199254746018, 'DEMO-AI-DECISION-018-PRODUCT', 'Demo product 018', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785018, 'Demo workshop 018', 9007199254786018, 'Demo resource 018', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745018 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751018, '000000', 'DEMO-AI-DECISION-018-MRP', 9007199254750018, 'DEMO-AI-DECISION-018-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 28, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751018 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752018, '000000', 9007199254751018, 9007199254747018, 'DEMO-AI-DECISION-018-COMP', 'Demo component 018', 28, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752018 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753018, '000000', 9007199254747018, 'DEMO-AI-DECISION-018-COMP', 'Demo component 018', 9007199254789000, 'DEMO-AI-DECISION-WH', 0, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753018 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754018, '000000', 'DEMO-AI-DECISION-018-SCHEDULE', 'Demo baseline schedule 018', 9007199254785018, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754018 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755018, '000000', 9007199254754018, 9007199254745018, 'DEMO-AI-DECISION-018', 1, 9007199254749018, 'WORKSTATION', 9007199254786018, 'Demo resource 018', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 17 DAY), 15, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755018 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856821, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786018, 'DEMO-AI-DECISION-018-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856821 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856822, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786018, 'DEMO-AI-DECISION-018-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856822 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856823, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786018, 'DEMO-AI-DECISION-018-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856823 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856824, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786018, 'DEMO-AI-DECISION-018-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856824 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856825, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786018, 'DEMO-AI-DECISION-018-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856825 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856826, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786018, 'DEMO-AI-DECISION-018-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856826 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856827, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786018, 'DEMO-AI-DECISION-018-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856827 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856828, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786018, 'DEMO-AI-DECISION-018-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856828 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856829, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786018, 'DEMO-AI-DECISION-018-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856829 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856830, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786018, 'DEMO-AI-DECISION-018-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856830 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856831, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786018, 'DEMO-AI-DECISION-018-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856831 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856832, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786018, 'DEMO-AI-DECISION-018-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856832 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856833, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786018, 'DEMO-AI-DECISION-018-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856833 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856834, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786018, 'DEMO-AI-DECISION-018-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856834 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856835, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786018, 'DEMO-AI-DECISION-018-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856835 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856836, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786018, 'DEMO-AI-DECISION-018-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856836 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856837, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786018, 'DEMO-AI-DECISION-018-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856837 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856838, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786018, 'DEMO-AI-DECISION-018-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856838 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856839, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786018, 'DEMO-AI-DECISION-018-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856839 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856840, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786018, 'DEMO-AI-DECISION-018-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856840 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856841, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786018, 'DEMO-AI-DECISION-018-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856841 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856842, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786018, 'DEMO-AI-DECISION-018-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856842 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856843, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786018, 'DEMO-AI-DECISION-018-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856843 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856844, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786018, 'DEMO-AI-DECISION-018-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856844 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856845, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786018, 'DEMO-AI-DECISION-018-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856845 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856846, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786018, 'DEMO-AI-DECISION-018-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856846 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856847, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786018, 'DEMO-AI-DECISION-018-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856847 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759018, '000000', 9007199254788018, 'DEMO-AI-DECISION-018-VENDOR', 'Demo supplier 018', DATE_FORMAT(CURDATE(), '%Y-%m'), 60, 100, 60, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759018 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757018, '000000', 'DEMO-AI-DECISION-018-PO', 9007199254788018, 'DEMO-AI-DECISION-018-VENDOR', 'Demo supplier 018', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757018 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758018, '000000', 9007199254757018, 9007199254747018, 'DEMO-AI-DECISION-018-COMP', 'Demo component 018', 28, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758018 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750019, '000000', 'DEMO-AI-DECISION-019-PLAN', 'Demo production 019', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750019 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745019, '000000', 'DEMO-AI-DECISION-019', 9007199254750019, 'Demo production 019', 9007199254746019, 'DEMO-AI-DECISION-019-PRODUCT', 'Demo product 019', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785019, 'Demo workshop 019', 9007199254786019, 'Demo resource 019', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745019 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751019, '000000', 'DEMO-AI-DECISION-019-MRP', 9007199254750019, 'DEMO-AI-DECISION-019-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 29, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751019 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752019, '000000', 9007199254751019, 9007199254747019, 'DEMO-AI-DECISION-019-COMP', 'Demo component 019', 29, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752019 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753019, '000000', 9007199254747019, 'DEMO-AI-DECISION-019-COMP', 'Demo component 019', 9007199254789000, 'DEMO-AI-DECISION-WH', 0, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753019 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754019, '000000', 'DEMO-AI-DECISION-019-SCHEDULE', 'Demo baseline schedule 019', 9007199254785019, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754019 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755019, '000000', 9007199254754019, 9007199254745019, 'DEMO-AI-DECISION-019', 1, 9007199254749019, 'WORKSTATION', 9007199254786019, 'Demo resource 019', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 17 DAY), 15, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755019 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856922, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786019, 'DEMO-AI-DECISION-019-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856922 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856923, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786019, 'DEMO-AI-DECISION-019-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856923 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856924, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786019, 'DEMO-AI-DECISION-019-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856924 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856925, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786019, 'DEMO-AI-DECISION-019-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856925 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856926, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786019, 'DEMO-AI-DECISION-019-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856926 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856927, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786019, 'DEMO-AI-DECISION-019-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856927 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856928, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786019, 'DEMO-AI-DECISION-019-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856928 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856929, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786019, 'DEMO-AI-DECISION-019-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856929 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856930, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786019, 'DEMO-AI-DECISION-019-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856930 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856931, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786019, 'DEMO-AI-DECISION-019-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856931 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856932, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786019, 'DEMO-AI-DECISION-019-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856932 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856933, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786019, 'DEMO-AI-DECISION-019-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856933 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856934, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786019, 'DEMO-AI-DECISION-019-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856934 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856935, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786019, 'DEMO-AI-DECISION-019-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856935 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856936, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786019, 'DEMO-AI-DECISION-019-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856936 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856937, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786019, 'DEMO-AI-DECISION-019-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856937 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856938, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786019, 'DEMO-AI-DECISION-019-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856938 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856939, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786019, 'DEMO-AI-DECISION-019-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856939 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856940, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786019, 'DEMO-AI-DECISION-019-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856940 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856941, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786019, 'DEMO-AI-DECISION-019-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856941 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856942, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786019, 'DEMO-AI-DECISION-019-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856942 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856943, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786019, 'DEMO-AI-DECISION-019-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856943 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856944, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786019, 'DEMO-AI-DECISION-019-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856944 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856945, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786019, 'DEMO-AI-DECISION-019-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856945 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856946, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786019, 'DEMO-AI-DECISION-019-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856946 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856947, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786019, 'DEMO-AI-DECISION-019-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856947 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254856948, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786019, 'DEMO-AI-DECISION-019-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254856948 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759019, '000000', 9007199254788019, 'DEMO-AI-DECISION-019-VENDOR', 'Demo supplier 019', DATE_FORMAT(CURDATE(), '%Y-%m'), 60, 100, 60, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759019 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757019, '000000', 'DEMO-AI-DECISION-019-PO', 9007199254788019, 'DEMO-AI-DECISION-019-VENDOR', 'Demo supplier 019', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757019 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758019, '000000', 9007199254757019, 9007199254747019, 'DEMO-AI-DECISION-019-COMP', 'Demo component 019', 29, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758019 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750020, '000000', 'DEMO-AI-DECISION-020-PLAN', 'Demo production 020', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750020 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745020, '000000', 'DEMO-AI-DECISION-020', 9007199254750020, 'Demo production 020', 9007199254746020, 'DEMO-AI-DECISION-020-PRODUCT', 'Demo product 020', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785020, 'Demo workshop 020', 9007199254786020, 'Demo resource 020', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745020 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751020, '000000', 'DEMO-AI-DECISION-020-MRP', 9007199254750020, 'DEMO-AI-DECISION-020-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 5, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751020 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752020, '000000', 9007199254751020, 9007199254747020, 'DEMO-AI-DECISION-020-COMP', 'Demo component 020', 5, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752020 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753020, '000000', 9007199254747020, 'DEMO-AI-DECISION-020-COMP', 'Demo component 020', 9007199254789000, 'DEMO-AI-DECISION-WH', 100, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753020 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754020, '000000', 'DEMO-AI-DECISION-020-SCHEDULE', 'Demo baseline schedule 020', 9007199254785020, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754020 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755020, '000000', 9007199254754020, 9007199254745020, 'DEMO-AI-DECISION-020', 1, 9007199254749020, 'WORKSTATION', 9007199254786020, 'Demo resource 020', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 17 DAY), 15, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755020 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857023, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786020, 'DEMO-AI-DECISION-020-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857023 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857024, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786020, 'DEMO-AI-DECISION-020-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857024 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857025, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786020, 'DEMO-AI-DECISION-020-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857025 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857026, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786020, 'DEMO-AI-DECISION-020-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857026 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857027, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786020, 'DEMO-AI-DECISION-020-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857027 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857028, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786020, 'DEMO-AI-DECISION-020-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857028 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857029, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786020, 'DEMO-AI-DECISION-020-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857029 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857030, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786020, 'DEMO-AI-DECISION-020-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857030 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857031, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786020, 'DEMO-AI-DECISION-020-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857031 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857032, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786020, 'DEMO-AI-DECISION-020-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857032 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857033, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786020, 'DEMO-AI-DECISION-020-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857033 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857034, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786020, 'DEMO-AI-DECISION-020-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857034 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857035, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786020, 'DEMO-AI-DECISION-020-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857035 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857036, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786020, 'DEMO-AI-DECISION-020-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857036 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857037, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786020, 'DEMO-AI-DECISION-020-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857037 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857038, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786020, 'DEMO-AI-DECISION-020-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857038 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857039, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786020, 'DEMO-AI-DECISION-020-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857039 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857040, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786020, 'DEMO-AI-DECISION-020-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857040 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857041, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786020, 'DEMO-AI-DECISION-020-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857041 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857042, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786020, 'DEMO-AI-DECISION-020-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857042 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857043, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786020, 'DEMO-AI-DECISION-020-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857043 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857044, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786020, 'DEMO-AI-DECISION-020-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857044 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857045, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786020, 'DEMO-AI-DECISION-020-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857045 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857046, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786020, 'DEMO-AI-DECISION-020-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857046 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857047, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786020, 'DEMO-AI-DECISION-020-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857047 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857048, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786020, 'DEMO-AI-DECISION-020-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857048 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857049, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786020, 'DEMO-AI-DECISION-020-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857049 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759020, '000000', 9007199254788020, 'DEMO-AI-DECISION-020-VENDOR', 'Demo supplier 020', DATE_FORMAT(CURDATE(), '%Y-%m'), 98, 100, 98, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759020 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757020, '000000', 'DEMO-AI-DECISION-020-PO', 9007199254788020, 'DEMO-AI-DECISION-020-VENDOR', 'Demo supplier 020', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757020 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758020, '000000', 9007199254757020, 9007199254747020, 'DEMO-AI-DECISION-020-COMP', 'Demo component 020', 1, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758020 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750021, '000000', 'DEMO-AI-DECISION-021-PLAN', 'Demo production 021', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750021 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745021, '000000', 'DEMO-AI-DECISION-021', 9007199254750021, 'Demo production 021', 9007199254746021, 'DEMO-AI-DECISION-021-PRODUCT', 'Demo product 021', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785021, 'Demo workshop 021', 9007199254786021, 'Demo resource 021', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745021 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751021, '000000', 'DEMO-AI-DECISION-021-MRP', 9007199254750021, 'DEMO-AI-DECISION-021-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 5, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751021 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752021, '000000', 9007199254751021, 9007199254747021, 'DEMO-AI-DECISION-021-COMP', 'Demo component 021', 5, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752021 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753021, '000000', 9007199254747021, 'DEMO-AI-DECISION-021-COMP', 'Demo component 021', 9007199254789000, 'DEMO-AI-DECISION-WH', 100, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753021 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754021, '000000', 'DEMO-AI-DECISION-021-SCHEDULE', 'Demo baseline schedule 021', 9007199254785021, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754021 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755021, '000000', 9007199254754021, 9007199254745021, 'DEMO-AI-DECISION-021', 1, 9007199254749021, 'WORKSTATION', 9007199254786021, 'Demo resource 021', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 17 DAY), 15, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755021 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857124, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786021, 'DEMO-AI-DECISION-021-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857124 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857125, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786021, 'DEMO-AI-DECISION-021-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857125 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857126, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786021, 'DEMO-AI-DECISION-021-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857126 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857127, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786021, 'DEMO-AI-DECISION-021-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857127 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857128, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786021, 'DEMO-AI-DECISION-021-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857128 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857129, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786021, 'DEMO-AI-DECISION-021-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857129 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857130, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786021, 'DEMO-AI-DECISION-021-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857130 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857131, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786021, 'DEMO-AI-DECISION-021-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857131 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857132, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786021, 'DEMO-AI-DECISION-021-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857132 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857133, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786021, 'DEMO-AI-DECISION-021-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857133 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857134, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786021, 'DEMO-AI-DECISION-021-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857134 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857135, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786021, 'DEMO-AI-DECISION-021-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857135 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857136, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786021, 'DEMO-AI-DECISION-021-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857136 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857137, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786021, 'DEMO-AI-DECISION-021-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857137 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857138, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786021, 'DEMO-AI-DECISION-021-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857138 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857139, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786021, 'DEMO-AI-DECISION-021-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857139 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857140, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786021, 'DEMO-AI-DECISION-021-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857140 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857141, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786021, 'DEMO-AI-DECISION-021-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857141 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857142, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786021, 'DEMO-AI-DECISION-021-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857142 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857143, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786021, 'DEMO-AI-DECISION-021-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857143 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857144, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786021, 'DEMO-AI-DECISION-021-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857144 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857145, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786021, 'DEMO-AI-DECISION-021-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857145 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857146, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786021, 'DEMO-AI-DECISION-021-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857146 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857147, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786021, 'DEMO-AI-DECISION-021-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857147 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857148, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786021, 'DEMO-AI-DECISION-021-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857148 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857149, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786021, 'DEMO-AI-DECISION-021-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857149 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857150, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786021, 'DEMO-AI-DECISION-021-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857150 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759021, '000000', 9007199254788021, 'DEMO-AI-DECISION-021-VENDOR', 'Demo supplier 021', DATE_FORMAT(CURDATE(), '%Y-%m'), 98, 100, 98, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759021 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757021, '000000', 'DEMO-AI-DECISION-021-PO', 9007199254788021, 'DEMO-AI-DECISION-021-VENDOR', 'Demo supplier 021', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757021 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758021, '000000', 9007199254757021, 9007199254747021, 'DEMO-AI-DECISION-021-COMP', 'Demo component 021', 1, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758021 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750022, '000000', 'DEMO-AI-DECISION-022-PLAN', 'Demo production 022', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750022 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745022, '000000', 'DEMO-AI-DECISION-022', 9007199254750022, 'Demo production 022', 9007199254746022, 'DEMO-AI-DECISION-022-PRODUCT', 'Demo product 022', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785022, 'Demo workshop 022', 9007199254786022, 'Demo resource 022', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745022 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751022, '000000', 'DEMO-AI-DECISION-022-MRP', 9007199254750022, 'DEMO-AI-DECISION-022-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 5, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751022 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752022, '000000', 9007199254751022, 9007199254747022, 'DEMO-AI-DECISION-022-COMP', 'Demo component 022', 5, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752022 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753022, '000000', 9007199254747022, 'DEMO-AI-DECISION-022-COMP', 'Demo component 022', 9007199254789000, 'DEMO-AI-DECISION-WH', 100, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753022 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754022, '000000', 'DEMO-AI-DECISION-022-SCHEDULE', 'Demo baseline schedule 022', 9007199254785022, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754022 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755022, '000000', 9007199254754022, 9007199254745022, 'DEMO-AI-DECISION-022', 1, 9007199254749022, 'WORKSTATION', 9007199254786022, 'Demo resource 022', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 17 DAY), 15, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755022 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857225, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786022, 'DEMO-AI-DECISION-022-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857225 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857226, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786022, 'DEMO-AI-DECISION-022-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857226 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857227, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786022, 'DEMO-AI-DECISION-022-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857227 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857228, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786022, 'DEMO-AI-DECISION-022-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857228 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857229, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786022, 'DEMO-AI-DECISION-022-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857229 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857230, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786022, 'DEMO-AI-DECISION-022-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857230 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857231, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786022, 'DEMO-AI-DECISION-022-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857231 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857232, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786022, 'DEMO-AI-DECISION-022-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857232 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857233, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786022, 'DEMO-AI-DECISION-022-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857233 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857234, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786022, 'DEMO-AI-DECISION-022-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857234 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857235, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786022, 'DEMO-AI-DECISION-022-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857235 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857236, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786022, 'DEMO-AI-DECISION-022-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857236 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857237, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786022, 'DEMO-AI-DECISION-022-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857237 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857238, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786022, 'DEMO-AI-DECISION-022-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857238 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857239, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786022, 'DEMO-AI-DECISION-022-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857239 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857240, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786022, 'DEMO-AI-DECISION-022-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857240 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857241, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786022, 'DEMO-AI-DECISION-022-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857241 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857242, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786022, 'DEMO-AI-DECISION-022-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857242 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857243, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786022, 'DEMO-AI-DECISION-022-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857243 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857244, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786022, 'DEMO-AI-DECISION-022-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857244 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857245, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786022, 'DEMO-AI-DECISION-022-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857245 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857246, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786022, 'DEMO-AI-DECISION-022-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857246 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857247, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786022, 'DEMO-AI-DECISION-022-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857247 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857248, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786022, 'DEMO-AI-DECISION-022-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857248 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857249, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786022, 'DEMO-AI-DECISION-022-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857249 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857250, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786022, 'DEMO-AI-DECISION-022-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857250 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857251, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786022, 'DEMO-AI-DECISION-022-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857251 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759022, '000000', 9007199254788022, 'DEMO-AI-DECISION-022-VENDOR', 'Demo supplier 022', DATE_FORMAT(CURDATE(), '%Y-%m'), 98, 100, 98, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759022 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757022, '000000', 'DEMO-AI-DECISION-022-PO', 9007199254788022, 'DEMO-AI-DECISION-022-VENDOR', 'Demo supplier 022', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757022 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758022, '000000', 9007199254757022, 9007199254747022, 'DEMO-AI-DECISION-022-COMP', 'Demo component 022', 1, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758022 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750023, '000000', 'DEMO-AI-DECISION-023-PLAN', 'Demo production 023', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750023 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745023, '000000', 'DEMO-AI-DECISION-023', 9007199254750023, 'Demo production 023', 9007199254746023, 'DEMO-AI-DECISION-023-PRODUCT', 'Demo product 023', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785023, 'Demo workshop 023', 9007199254786023, 'Demo resource 023', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745023 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751023, '000000', 'DEMO-AI-DECISION-023-MRP', 9007199254750023, 'DEMO-AI-DECISION-023-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 5, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751023 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752023, '000000', 9007199254751023, 9007199254747023, 'DEMO-AI-DECISION-023-COMP', 'Demo component 023', 5, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752023 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753023, '000000', 9007199254747023, 'DEMO-AI-DECISION-023-COMP', 'Demo component 023', 9007199254789000, 'DEMO-AI-DECISION-WH', 100, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753023 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754023, '000000', 'DEMO-AI-DECISION-023-SCHEDULE', 'Demo baseline schedule 023', 9007199254785023, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754023 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755023, '000000', 9007199254754023, 9007199254745023, 'DEMO-AI-DECISION-023', 1, 9007199254749023, 'WORKSTATION', 9007199254786023, 'Demo resource 023', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 17 DAY), 15, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755023 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857326, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786023, 'DEMO-AI-DECISION-023-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857326 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857327, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786023, 'DEMO-AI-DECISION-023-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857327 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857328, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786023, 'DEMO-AI-DECISION-023-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857328 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857329, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786023, 'DEMO-AI-DECISION-023-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857329 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857330, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786023, 'DEMO-AI-DECISION-023-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857330 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857331, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786023, 'DEMO-AI-DECISION-023-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857331 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857332, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786023, 'DEMO-AI-DECISION-023-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857332 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857333, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786023, 'DEMO-AI-DECISION-023-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857333 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857334, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786023, 'DEMO-AI-DECISION-023-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857334 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857335, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786023, 'DEMO-AI-DECISION-023-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857335 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857336, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786023, 'DEMO-AI-DECISION-023-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857336 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857337, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786023, 'DEMO-AI-DECISION-023-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857337 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857338, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786023, 'DEMO-AI-DECISION-023-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857338 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857339, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786023, 'DEMO-AI-DECISION-023-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857339 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857340, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786023, 'DEMO-AI-DECISION-023-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857340 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857341, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786023, 'DEMO-AI-DECISION-023-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857341 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857342, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786023, 'DEMO-AI-DECISION-023-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857342 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857343, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786023, 'DEMO-AI-DECISION-023-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857343 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857344, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786023, 'DEMO-AI-DECISION-023-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857344 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857345, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786023, 'DEMO-AI-DECISION-023-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857345 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857346, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786023, 'DEMO-AI-DECISION-023-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857346 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857347, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786023, 'DEMO-AI-DECISION-023-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857347 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857348, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786023, 'DEMO-AI-DECISION-023-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857348 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857349, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786023, 'DEMO-AI-DECISION-023-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857349 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857350, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786023, 'DEMO-AI-DECISION-023-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857350 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857351, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786023, 'DEMO-AI-DECISION-023-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857351 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857352, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786023, 'DEMO-AI-DECISION-023-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857352 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759023, '000000', 9007199254788023, 'DEMO-AI-DECISION-023-VENDOR', 'Demo supplier 023', DATE_FORMAT(CURDATE(), '%Y-%m'), 98, 100, 98, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759023 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757023, '000000', 'DEMO-AI-DECISION-023-PO', 9007199254788023, 'DEMO-AI-DECISION-023-VENDOR', 'Demo supplier 023', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757023 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758023, '000000', 9007199254757023, 9007199254747023, 'DEMO-AI-DECISION-023-COMP', 'Demo component 023', 1, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758023 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750024, '000000', 'DEMO-AI-DECISION-024-PLAN', 'Demo production 024', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750024 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745024, '000000', 'DEMO-AI-DECISION-024', 9007199254750024, 'Demo production 024', 9007199254746024, 'DEMO-AI-DECISION-024-PRODUCT', 'Demo product 024', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785024, 'Demo workshop 024', 9007199254786024, 'Demo resource 024', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745024 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751024, '000000', 'DEMO-AI-DECISION-024-MRP', 9007199254750024, 'DEMO-AI-DECISION-024-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 5, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751024 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752024, '000000', 9007199254751024, 9007199254747024, 'DEMO-AI-DECISION-024-COMP', 'Demo component 024', 5, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752024 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753024, '000000', 9007199254747024, 'DEMO-AI-DECISION-024-COMP', 'Demo component 024', 9007199254789000, 'DEMO-AI-DECISION-WH', 100, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753024 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754024, '000000', 'DEMO-AI-DECISION-024-SCHEDULE', 'Demo baseline schedule 024', 9007199254785024, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754024 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755024, '000000', 9007199254754024, 9007199254745024, 'DEMO-AI-DECISION-024', 1, 9007199254749024, 'WORKSTATION', 9007199254786024, 'Demo resource 024', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 17 DAY), 15, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755024 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857427, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786024, 'DEMO-AI-DECISION-024-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857427 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857428, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786024, 'DEMO-AI-DECISION-024-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857428 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857429, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786024, 'DEMO-AI-DECISION-024-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857429 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857430, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786024, 'DEMO-AI-DECISION-024-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857430 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857431, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786024, 'DEMO-AI-DECISION-024-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857431 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857432, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786024, 'DEMO-AI-DECISION-024-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857432 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857433, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786024, 'DEMO-AI-DECISION-024-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857433 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857434, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786024, 'DEMO-AI-DECISION-024-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857434 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857435, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786024, 'DEMO-AI-DECISION-024-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857435 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857436, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786024, 'DEMO-AI-DECISION-024-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857436 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857437, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786024, 'DEMO-AI-DECISION-024-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857437 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857438, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786024, 'DEMO-AI-DECISION-024-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857438 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857439, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786024, 'DEMO-AI-DECISION-024-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857439 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857440, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786024, 'DEMO-AI-DECISION-024-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857440 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857441, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786024, 'DEMO-AI-DECISION-024-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857441 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857442, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786024, 'DEMO-AI-DECISION-024-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857442 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857443, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786024, 'DEMO-AI-DECISION-024-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857443 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857444, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786024, 'DEMO-AI-DECISION-024-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857444 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857445, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786024, 'DEMO-AI-DECISION-024-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857445 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857446, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786024, 'DEMO-AI-DECISION-024-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857446 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857447, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786024, 'DEMO-AI-DECISION-024-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857447 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857448, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786024, 'DEMO-AI-DECISION-024-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857448 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857449, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786024, 'DEMO-AI-DECISION-024-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857449 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857450, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786024, 'DEMO-AI-DECISION-024-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857450 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857451, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786024, 'DEMO-AI-DECISION-024-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857451 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857452, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786024, 'DEMO-AI-DECISION-024-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857452 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857453, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786024, 'DEMO-AI-DECISION-024-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857453 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759024, '000000', 9007199254788024, 'DEMO-AI-DECISION-024-VENDOR', 'Demo supplier 024', DATE_FORMAT(CURDATE(), '%Y-%m'), 98, 100, 98, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759024 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757024, '000000', 'DEMO-AI-DECISION-024-PO', 9007199254788024, 'DEMO-AI-DECISION-024-VENDOR', 'Demo supplier 024', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757024 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758024, '000000', 9007199254757024, 9007199254747024, 'DEMO-AI-DECISION-024-COMP', 'Demo component 024', 1, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758024 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750025, '000000', 'DEMO-AI-DECISION-025-PLAN', 'Demo production 025', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750025 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745025, '000000', 'DEMO-AI-DECISION-025', 9007199254750025, 'Demo production 025', 9007199254746025, 'DEMO-AI-DECISION-025-PRODUCT', 'Demo product 025', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785025, 'Demo workshop 025', 9007199254786025, 'Demo resource 025', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745025 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751025, '000000', 'DEMO-AI-DECISION-025-MRP', 9007199254750025, 'DEMO-AI-DECISION-025-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 5, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751025 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752025, '000000', 9007199254751025, 9007199254747025, 'DEMO-AI-DECISION-025-COMP', 'Demo component 025', 5, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752025 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753025, '000000', 9007199254747025, 'DEMO-AI-DECISION-025-COMP', 'Demo component 025', 9007199254789000, 'DEMO-AI-DECISION-WH', 100, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753025 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754025, '000000', 'DEMO-AI-DECISION-025-SCHEDULE', 'Demo baseline schedule 025', 9007199254785025, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754025 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755025, '000000', 9007199254754025, 9007199254745025, 'DEMO-AI-DECISION-025', 1, 9007199254749025, 'WORKSTATION', 9007199254786025, 'Demo resource 025', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 17 DAY), 15, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755025 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857528, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786025, 'DEMO-AI-DECISION-025-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857528 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857529, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786025, 'DEMO-AI-DECISION-025-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857529 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857530, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786025, 'DEMO-AI-DECISION-025-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857530 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857531, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786025, 'DEMO-AI-DECISION-025-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857531 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857532, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786025, 'DEMO-AI-DECISION-025-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857532 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857533, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786025, 'DEMO-AI-DECISION-025-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857533 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857534, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786025, 'DEMO-AI-DECISION-025-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857534 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857535, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786025, 'DEMO-AI-DECISION-025-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857535 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857536, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786025, 'DEMO-AI-DECISION-025-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857536 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857537, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786025, 'DEMO-AI-DECISION-025-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857537 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857538, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786025, 'DEMO-AI-DECISION-025-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857538 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857539, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786025, 'DEMO-AI-DECISION-025-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857539 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857540, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786025, 'DEMO-AI-DECISION-025-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857540 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857541, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786025, 'DEMO-AI-DECISION-025-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857541 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857542, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786025, 'DEMO-AI-DECISION-025-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857542 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857543, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786025, 'DEMO-AI-DECISION-025-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857543 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857544, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786025, 'DEMO-AI-DECISION-025-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857544 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857545, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786025, 'DEMO-AI-DECISION-025-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857545 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857546, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786025, 'DEMO-AI-DECISION-025-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857546 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857547, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786025, 'DEMO-AI-DECISION-025-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857547 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857548, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786025, 'DEMO-AI-DECISION-025-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857548 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857549, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786025, 'DEMO-AI-DECISION-025-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857549 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857550, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786025, 'DEMO-AI-DECISION-025-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857550 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857551, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786025, 'DEMO-AI-DECISION-025-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857551 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857552, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786025, 'DEMO-AI-DECISION-025-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857552 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857553, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786025, 'DEMO-AI-DECISION-025-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857553 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857554, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786025, 'DEMO-AI-DECISION-025-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857554 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759025, '000000', 9007199254788025, 'DEMO-AI-DECISION-025-VENDOR', 'Demo supplier 025', DATE_FORMAT(CURDATE(), '%Y-%m'), 98, 100, 98, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759025 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757025, '000000', 'DEMO-AI-DECISION-025-PO', 9007199254788025, 'DEMO-AI-DECISION-025-VENDOR', 'Demo supplier 025', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757025 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758025, '000000', 9007199254757025, 9007199254747025, 'DEMO-AI-DECISION-025-COMP', 'Demo component 025', 1, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758025 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750026, '000000', 'DEMO-AI-DECISION-026-PLAN', 'Demo production 026', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750026 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745026, '000000', 'DEMO-AI-DECISION-026', 9007199254750026, 'Demo production 026', 9007199254746026, 'DEMO-AI-DECISION-026-PRODUCT', 'Demo product 026', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785026, 'Demo workshop 026', 9007199254786026, 'Demo resource 026', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745026 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751026, '000000', 'DEMO-AI-DECISION-026-MRP', 9007199254750026, 'DEMO-AI-DECISION-026-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 5, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751026 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752026, '000000', 9007199254751026, 9007199254747026, 'DEMO-AI-DECISION-026-COMP', 'Demo component 026', 5, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752026 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753026, '000000', 9007199254747026, 'DEMO-AI-DECISION-026-COMP', 'Demo component 026', 9007199254789000, 'DEMO-AI-DECISION-WH', 100, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753026 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754026, '000000', 'DEMO-AI-DECISION-026-SCHEDULE', 'Demo baseline schedule 026', 9007199254785026, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754026 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755026, '000000', 9007199254754026, 9007199254745026, 'DEMO-AI-DECISION-026', 1, NULL, 'WORKSTATION', 9007199254786026, 'Demo resource 026', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 12, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755026 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857629, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786026, 'DEMO-AI-DECISION-026-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857629 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857630, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786026, 'DEMO-AI-DECISION-026-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857630 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857631, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786026, 'DEMO-AI-DECISION-026-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857631 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857632, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786026, 'DEMO-AI-DECISION-026-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857632 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857633, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786026, 'DEMO-AI-DECISION-026-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857633 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857634, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786026, 'DEMO-AI-DECISION-026-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857634 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857635, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786026, 'DEMO-AI-DECISION-026-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857635 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857636, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786026, 'DEMO-AI-DECISION-026-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857636 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857637, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786026, 'DEMO-AI-DECISION-026-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857637 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857638, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786026, 'DEMO-AI-DECISION-026-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857638 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857639, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786026, 'DEMO-AI-DECISION-026-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857639 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857640, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786026, 'DEMO-AI-DECISION-026-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857640 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857641, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786026, 'DEMO-AI-DECISION-026-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857641 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857642, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786026, 'DEMO-AI-DECISION-026-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857642 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857643, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786026, 'DEMO-AI-DECISION-026-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857643 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857644, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786026, 'DEMO-AI-DECISION-026-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857644 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857645, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786026, 'DEMO-AI-DECISION-026-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857645 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857646, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786026, 'DEMO-AI-DECISION-026-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857646 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857647, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786026, 'DEMO-AI-DECISION-026-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857647 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857648, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786026, 'DEMO-AI-DECISION-026-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857648 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857649, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786026, 'DEMO-AI-DECISION-026-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857649 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857650, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786026, 'DEMO-AI-DECISION-026-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857650 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857651, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786026, 'DEMO-AI-DECISION-026-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857651 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857652, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786026, 'DEMO-AI-DECISION-026-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857652 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857653, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786026, 'DEMO-AI-DECISION-026-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857653 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857654, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786026, 'DEMO-AI-DECISION-026-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857654 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857655, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786026, 'DEMO-AI-DECISION-026-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857655 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759026, '000000', 9007199254788026, 'DEMO-AI-DECISION-026-VENDOR', 'Demo supplier 026', DATE_FORMAT(CURDATE(), '%Y-%m'), 98, 100, 98, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759026 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757026, '000000', 'DEMO-AI-DECISION-026-PO', 9007199254788026, 'DEMO-AI-DECISION-026-VENDOR', 'Demo supplier 026', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757026 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758026, '000000', 9007199254757026, 9007199254747026, 'DEMO-AI-DECISION-026-COMP', 'Demo component 026', 1, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758026 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750027, '000000', 'DEMO-AI-DECISION-027-PLAN', 'Demo production 027', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750027 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745027, '000000', 'DEMO-AI-DECISION-027', 9007199254750027, 'Demo production 027', 9007199254746027, 'DEMO-AI-DECISION-027-PRODUCT', 'Demo product 027', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785027, 'Demo workshop 027', 9007199254786027, 'Demo resource 027', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745027 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751027, '000000', 'DEMO-AI-DECISION-027-MRP', 9007199254750027, 'DEMO-AI-DECISION-027-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 5, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751027 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752027, '000000', 9007199254751027, 9007199254747027, 'DEMO-AI-DECISION-027-COMP', 'Demo component 027', 5, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752027 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753027, '000000', 9007199254747027, 'DEMO-AI-DECISION-027-COMP', 'Demo component 027', 9007199254789000, 'DEMO-AI-DECISION-WH', 100, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753027 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754027, '000000', 'DEMO-AI-DECISION-027-SCHEDULE', 'Demo baseline schedule 027', 9007199254785027, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754027 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755027, '000000', 9007199254754027, 9007199254745027, 'DEMO-AI-DECISION-027', 1, NULL, 'WORKSTATION', 9007199254786027, 'Demo resource 027', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 12, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755027 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857730, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786027, 'DEMO-AI-DECISION-027-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857730 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857731, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786027, 'DEMO-AI-DECISION-027-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857731 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857732, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786027, 'DEMO-AI-DECISION-027-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857732 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857733, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786027, 'DEMO-AI-DECISION-027-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857733 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857734, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786027, 'DEMO-AI-DECISION-027-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857734 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857735, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786027, 'DEMO-AI-DECISION-027-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857735 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857736, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786027, 'DEMO-AI-DECISION-027-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857736 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857737, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786027, 'DEMO-AI-DECISION-027-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857737 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857738, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786027, 'DEMO-AI-DECISION-027-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857738 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857739, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786027, 'DEMO-AI-DECISION-027-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857739 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857740, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786027, 'DEMO-AI-DECISION-027-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857740 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857741, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786027, 'DEMO-AI-DECISION-027-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857741 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857742, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786027, 'DEMO-AI-DECISION-027-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857742 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857743, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786027, 'DEMO-AI-DECISION-027-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857743 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857744, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786027, 'DEMO-AI-DECISION-027-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857744 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857745, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786027, 'DEMO-AI-DECISION-027-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857745 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857746, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786027, 'DEMO-AI-DECISION-027-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857746 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857747, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786027, 'DEMO-AI-DECISION-027-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857747 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857748, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786027, 'DEMO-AI-DECISION-027-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857748 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857749, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786027, 'DEMO-AI-DECISION-027-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857749 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857750, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786027, 'DEMO-AI-DECISION-027-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857750 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857751, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786027, 'DEMO-AI-DECISION-027-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857751 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857752, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786027, 'DEMO-AI-DECISION-027-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857752 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857753, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786027, 'DEMO-AI-DECISION-027-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857753 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857754, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786027, 'DEMO-AI-DECISION-027-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857754 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857755, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786027, 'DEMO-AI-DECISION-027-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857755 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857756, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786027, 'DEMO-AI-DECISION-027-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857756 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759027, '000000', 9007199254788027, 'DEMO-AI-DECISION-027-VENDOR', 'Demo supplier 027', DATE_FORMAT(CURDATE(), '%Y-%m'), 98, 100, 98, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759027 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757027, '000000', 'DEMO-AI-DECISION-027-PO', 9007199254788027, 'DEMO-AI-DECISION-027-VENDOR', 'Demo supplier 027', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757027 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758027, '000000', 9007199254757027, 9007199254747027, 'DEMO-AI-DECISION-027-COMP', 'Demo component 027', 1, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758027 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750028, '000000', 'DEMO-AI-DECISION-028-PLAN', 'Demo production 028', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750028 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745028, '000000', 'DEMO-AI-DECISION-028', 9007199254750028, 'Demo production 028', 9007199254746028, 'DEMO-AI-DECISION-028-PRODUCT', 'Demo product 028', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785028, 'Demo workshop 028', 9007199254786028, 'Demo resource 028', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745028 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751028, '000000', 'DEMO-AI-DECISION-028-MRP', 9007199254750028, 'DEMO-AI-DECISION-028-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 5, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751028 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752028, '000000', 9007199254751028, 9007199254747028, 'DEMO-AI-DECISION-028-COMP', 'Demo component 028', 5, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752028 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753028, '000000', 9007199254747028, 'DEMO-AI-DECISION-028-COMP', 'Demo component 028', 9007199254789000, 'DEMO-AI-DECISION-WH', 100, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753028 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754028, '000000', 'DEMO-AI-DECISION-028-SCHEDULE', 'Demo baseline schedule 028', 9007199254785028, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754028 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755028, '000000', 9007199254754028, 9007199254745028, 'DEMO-AI-DECISION-028', 1, NULL, 'WORKSTATION', 9007199254786028, 'Demo resource 028', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 12, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755028 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857831, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786028, 'DEMO-AI-DECISION-028-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857831 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857832, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786028, 'DEMO-AI-DECISION-028-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857832 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857833, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786028, 'DEMO-AI-DECISION-028-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857833 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857834, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786028, 'DEMO-AI-DECISION-028-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857834 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857835, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786028, 'DEMO-AI-DECISION-028-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857835 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857836, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786028, 'DEMO-AI-DECISION-028-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857836 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857837, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786028, 'DEMO-AI-DECISION-028-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857837 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857838, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786028, 'DEMO-AI-DECISION-028-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857838 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857839, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786028, 'DEMO-AI-DECISION-028-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857839 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857840, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786028, 'DEMO-AI-DECISION-028-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857840 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857841, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786028, 'DEMO-AI-DECISION-028-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857841 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857842, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786028, 'DEMO-AI-DECISION-028-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857842 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857843, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786028, 'DEMO-AI-DECISION-028-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857843 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857844, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786028, 'DEMO-AI-DECISION-028-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857844 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857845, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786028, 'DEMO-AI-DECISION-028-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857845 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857846, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786028, 'DEMO-AI-DECISION-028-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857846 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857847, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786028, 'DEMO-AI-DECISION-028-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857847 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857848, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786028, 'DEMO-AI-DECISION-028-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857848 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857849, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786028, 'DEMO-AI-DECISION-028-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857849 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857850, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786028, 'DEMO-AI-DECISION-028-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857850 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857851, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786028, 'DEMO-AI-DECISION-028-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857851 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857852, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786028, 'DEMO-AI-DECISION-028-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857852 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857853, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786028, 'DEMO-AI-DECISION-028-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857853 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857854, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786028, 'DEMO-AI-DECISION-028-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857854 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857855, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786028, 'DEMO-AI-DECISION-028-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857855 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857856, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786028, 'DEMO-AI-DECISION-028-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857856 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857857, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786028, 'DEMO-AI-DECISION-028-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857857 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759028, '000000', 9007199254788028, 'DEMO-AI-DECISION-028-VENDOR', 'Demo supplier 028', DATE_FORMAT(CURDATE(), '%Y-%m'), 98, 100, 98, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759028 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757028, '000000', 'DEMO-AI-DECISION-028-PO', 9007199254788028, 'DEMO-AI-DECISION-028-VENDOR', 'Demo supplier 028', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757028 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758028, '000000', 9007199254757028, 9007199254747028, 'DEMO-AI-DECISION-028-COMP', 'Demo component 028', 1, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758028 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750029, '000000', 'DEMO-AI-DECISION-029-PLAN', 'Demo production 029', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750029 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745029, '000000', 'DEMO-AI-DECISION-029', 9007199254750029, 'Demo production 029', 9007199254746029, 'DEMO-AI-DECISION-029-PRODUCT', 'Demo product 029', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785029, 'Demo workshop 029', 9007199254786029, 'Demo resource 029', 5, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745029 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751029, '000000', 'DEMO-AI-DECISION-029-MRP', 9007199254750029, 'DEMO-AI-DECISION-029-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 5, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751029 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752029, '000000', 9007199254751029, 9007199254747029, 'DEMO-AI-DECISION-029-COMP', 'Demo component 029', 5, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752029 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753029, '000000', 9007199254747029, 'DEMO-AI-DECISION-029-COMP', 'Demo component 029', 9007199254789000, 'DEMO-AI-DECISION-WH', 100, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753029 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754029, '000000', 'DEMO-AI-DECISION-029-SCHEDULE', 'Demo baseline schedule 029', 9007199254785029, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754029 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755029, '000000', 9007199254754029, 9007199254745029, 'DEMO-AI-DECISION-029', 1, 9007199254749029, 'WORKSTATION', 9007199254786029, 'Demo resource 029', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 18 DAY), 16, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755029 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857932, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786029, 'DEMO-AI-DECISION-029-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857932 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857933, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786029, 'DEMO-AI-DECISION-029-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857933 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857934, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786029, 'DEMO-AI-DECISION-029-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857934 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857935, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786029, 'DEMO-AI-DECISION-029-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857935 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857936, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786029, 'DEMO-AI-DECISION-029-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857936 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857937, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786029, 'DEMO-AI-DECISION-029-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857937 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857938, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786029, 'DEMO-AI-DECISION-029-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857938 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857939, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786029, 'DEMO-AI-DECISION-029-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857939 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857940, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786029, 'DEMO-AI-DECISION-029-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857940 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857941, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786029, 'DEMO-AI-DECISION-029-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857941 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857942, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786029, 'DEMO-AI-DECISION-029-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857942 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857943, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786029, 'DEMO-AI-DECISION-029-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857943 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857944, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786029, 'DEMO-AI-DECISION-029-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857944 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857945, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786029, 'DEMO-AI-DECISION-029-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857945 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857946, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786029, 'DEMO-AI-DECISION-029-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857946 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857947, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786029, 'DEMO-AI-DECISION-029-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857947 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857948, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786029, 'DEMO-AI-DECISION-029-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857948 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857949, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786029, 'DEMO-AI-DECISION-029-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857949 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857950, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786029, 'DEMO-AI-DECISION-029-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857950 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857951, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786029, 'DEMO-AI-DECISION-029-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857951 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857952, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786029, 'DEMO-AI-DECISION-029-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857952 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857953, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786029, 'DEMO-AI-DECISION-029-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857953 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857954, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786029, 'DEMO-AI-DECISION-029-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857954 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857955, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786029, 'DEMO-AI-DECISION-029-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857955 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857956, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786029, 'DEMO-AI-DECISION-029-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857956 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857957, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786029, 'DEMO-AI-DECISION-029-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857957 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254857958, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786029, 'DEMO-AI-DECISION-029-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254857958 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759029, '000000', 9007199254788029, 'DEMO-AI-DECISION-029-VENDOR', 'Demo supplier 029', DATE_FORMAT(CURDATE(), '%Y-%m'), 98, 100, 98, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759029 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757029, '000000', 'DEMO-AI-DECISION-029-PO', 9007199254788029, 'DEMO-AI-DECISION-029-VENDOR', 'Demo supplier 029', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757029 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758029, '000000', 9007199254757029, 9007199254747029, 'DEMO-AI-DECISION-029-COMP', 'Demo component 029', 1, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758029 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750030, '000000', 'DEMO-AI-DECISION-030-PLAN', 'Demo production 030', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750030 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745030, '000000', 'DEMO-AI-DECISION-030', 9007199254750030, 'Demo production 030', 9007199254746030, 'DEMO-AI-DECISION-030-PRODUCT', 'Demo product 030', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785030, 'Demo workshop 030', 9007199254786030, 'Demo resource 030', 5, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745030 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751030, '000000', 'DEMO-AI-DECISION-030-MRP', 9007199254750030, 'DEMO-AI-DECISION-030-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 5, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751030 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752030, '000000', 9007199254751030, 9007199254747030, 'DEMO-AI-DECISION-030-COMP', 'Demo component 030', 5, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752030 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753030, '000000', 9007199254747030, 'DEMO-AI-DECISION-030-COMP', 'Demo component 030', 9007199254789000, 'DEMO-AI-DECISION-WH', 100, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753030 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754030, '000000', 'DEMO-AI-DECISION-030-SCHEDULE', 'Demo baseline schedule 030', 9007199254785030, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754030 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755030, '000000', 9007199254754030, 9007199254745030, 'DEMO-AI-DECISION-030', 1, 9007199254749030, 'WORKSTATION', 9007199254786030, 'Demo resource 030', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 18 DAY), 16, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755030 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858033, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786030, 'DEMO-AI-DECISION-030-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858033 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858034, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786030, 'DEMO-AI-DECISION-030-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858034 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858035, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786030, 'DEMO-AI-DECISION-030-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858035 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858036, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786030, 'DEMO-AI-DECISION-030-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858036 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858037, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786030, 'DEMO-AI-DECISION-030-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858037 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858038, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786030, 'DEMO-AI-DECISION-030-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858038 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858039, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786030, 'DEMO-AI-DECISION-030-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858039 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858040, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786030, 'DEMO-AI-DECISION-030-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858040 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858041, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786030, 'DEMO-AI-DECISION-030-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858041 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858042, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786030, 'DEMO-AI-DECISION-030-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858042 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858043, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786030, 'DEMO-AI-DECISION-030-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858043 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858044, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786030, 'DEMO-AI-DECISION-030-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858044 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858045, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786030, 'DEMO-AI-DECISION-030-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858045 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858046, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786030, 'DEMO-AI-DECISION-030-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858046 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858047, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786030, 'DEMO-AI-DECISION-030-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858047 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858048, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786030, 'DEMO-AI-DECISION-030-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858048 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858049, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786030, 'DEMO-AI-DECISION-030-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858049 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858050, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786030, 'DEMO-AI-DECISION-030-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858050 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858051, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786030, 'DEMO-AI-DECISION-030-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858051 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858052, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786030, 'DEMO-AI-DECISION-030-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858052 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858053, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786030, 'DEMO-AI-DECISION-030-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858053 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858054, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786030, 'DEMO-AI-DECISION-030-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858054 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858055, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786030, 'DEMO-AI-DECISION-030-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858055 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858056, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786030, 'DEMO-AI-DECISION-030-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858056 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858057, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786030, 'DEMO-AI-DECISION-030-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858057 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858058, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786030, 'DEMO-AI-DECISION-030-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858058 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858059, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786030, 'DEMO-AI-DECISION-030-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858059 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759030, '000000', 9007199254788030, 'DEMO-AI-DECISION-030-VENDOR', 'Demo supplier 030', DATE_FORMAT(CURDATE(), '%Y-%m'), 98, 100, 98, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759030 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757030, '000000', 'DEMO-AI-DECISION-030-PO', 9007199254788030, 'DEMO-AI-DECISION-030-VENDOR', 'Demo supplier 030', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757030 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758030, '000000', 9007199254757030, 9007199254747030, 'DEMO-AI-DECISION-030-COMP', 'Demo component 030', 1, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758030 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750031, '000000', 'DEMO-AI-DECISION-031-PLAN', 'Demo production 031', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750031 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745031, '000000', 'DEMO-AI-DECISION-031', 9007199254750031, 'Demo production 031', 9007199254746031, 'DEMO-AI-DECISION-031-PRODUCT', 'Demo product 031', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785031, 'Demo workshop 031', 9007199254786031, 'Demo resource 031', 5, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745031 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751031, '000000', 'DEMO-AI-DECISION-031-MRP', 9007199254750031, 'DEMO-AI-DECISION-031-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 5, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751031 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752031, '000000', 9007199254751031, 9007199254747031, 'DEMO-AI-DECISION-031-COMP', 'Demo component 031', 5, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752031 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753031, '000000', 9007199254747031, 'DEMO-AI-DECISION-031-COMP', 'Demo component 031', 9007199254789000, 'DEMO-AI-DECISION-WH', 100, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753031 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754031, '000000', 'DEMO-AI-DECISION-031-SCHEDULE', 'Demo baseline schedule 031', 9007199254785031, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754031 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755031, '000000', 9007199254754031, 9007199254745031, 'DEMO-AI-DECISION-031', 1, 9007199254749031, 'WORKSTATION', 9007199254786031, 'Demo resource 031', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 18 DAY), 16, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755031 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858134, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786031, 'DEMO-AI-DECISION-031-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858134 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858135, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786031, 'DEMO-AI-DECISION-031-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858135 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858136, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786031, 'DEMO-AI-DECISION-031-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858136 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858137, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786031, 'DEMO-AI-DECISION-031-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858137 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858138, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786031, 'DEMO-AI-DECISION-031-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858138 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858139, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786031, 'DEMO-AI-DECISION-031-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858139 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858140, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786031, 'DEMO-AI-DECISION-031-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858140 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858141, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786031, 'DEMO-AI-DECISION-031-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858141 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858142, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786031, 'DEMO-AI-DECISION-031-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858142 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858143, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786031, 'DEMO-AI-DECISION-031-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858143 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858144, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786031, 'DEMO-AI-DECISION-031-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858144 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858145, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786031, 'DEMO-AI-DECISION-031-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858145 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858146, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786031, 'DEMO-AI-DECISION-031-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858146 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858147, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786031, 'DEMO-AI-DECISION-031-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858147 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858148, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786031, 'DEMO-AI-DECISION-031-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858148 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858149, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786031, 'DEMO-AI-DECISION-031-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858149 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858150, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786031, 'DEMO-AI-DECISION-031-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858150 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858151, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786031, 'DEMO-AI-DECISION-031-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858151 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858152, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786031, 'DEMO-AI-DECISION-031-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858152 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858153, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786031, 'DEMO-AI-DECISION-031-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858153 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858154, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786031, 'DEMO-AI-DECISION-031-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858154 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858155, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786031, 'DEMO-AI-DECISION-031-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858155 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858156, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786031, 'DEMO-AI-DECISION-031-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858156 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858157, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786031, 'DEMO-AI-DECISION-031-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858157 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858158, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786031, 'DEMO-AI-DECISION-031-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858158 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858159, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786031, 'DEMO-AI-DECISION-031-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858159 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858160, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786031, 'DEMO-AI-DECISION-031-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858160 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759031, '000000', 9007199254788031, 'DEMO-AI-DECISION-031-VENDOR', 'Demo supplier 031', DATE_FORMAT(CURDATE(), '%Y-%m'), 98, 100, 98, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759031 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757031, '000000', 'DEMO-AI-DECISION-031-PO', 9007199254788031, 'DEMO-AI-DECISION-031-VENDOR', 'Demo supplier 031', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757031 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758031, '000000', 9007199254757031, 9007199254747031, 'DEMO-AI-DECISION-031-COMP', 'Demo component 031', 1, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758031 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750032, '000000', 'DEMO-AI-DECISION-032-PLAN', 'Demo production 032', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750032 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745032, '000000', 'DEMO-AI-DECISION-032', 9007199254750032, 'Demo production 032', 9007199254746032, 'DEMO-AI-DECISION-032-PRODUCT', 'Demo product 032', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785032, 'Demo workshop 032', 9007199254786032, 'Demo resource 032', 5, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745032 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751032, '000000', 'DEMO-AI-DECISION-032-MRP', 9007199254750032, 'DEMO-AI-DECISION-032-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 5, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751032 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752032, '000000', 9007199254751032, 9007199254747032, 'DEMO-AI-DECISION-032-COMP', 'Demo component 032', 5, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752032 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753032, '000000', 9007199254747032, 'DEMO-AI-DECISION-032-COMP', 'Demo component 032', 9007199254789000, 'DEMO-AI-DECISION-WH', 100, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753032 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754032, '000000', 'DEMO-AI-DECISION-032-SCHEDULE', 'Demo baseline schedule 032', 9007199254785032, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754032 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755032, '000000', 9007199254754032, 9007199254745032, 'DEMO-AI-DECISION-032', 1, 9007199254749032, 'WORKSTATION', 9007199254786032, 'Demo resource 032', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 18 DAY), 16, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755032 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858235, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786032, 'DEMO-AI-DECISION-032-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858235 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858236, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786032, 'DEMO-AI-DECISION-032-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858236 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858237, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786032, 'DEMO-AI-DECISION-032-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858237 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858238, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786032, 'DEMO-AI-DECISION-032-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858238 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858239, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786032, 'DEMO-AI-DECISION-032-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858239 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858240, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786032, 'DEMO-AI-DECISION-032-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858240 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858241, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786032, 'DEMO-AI-DECISION-032-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858241 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858242, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786032, 'DEMO-AI-DECISION-032-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858242 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858243, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786032, 'DEMO-AI-DECISION-032-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858243 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858244, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786032, 'DEMO-AI-DECISION-032-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858244 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858245, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786032, 'DEMO-AI-DECISION-032-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858245 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858246, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786032, 'DEMO-AI-DECISION-032-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858246 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858247, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786032, 'DEMO-AI-DECISION-032-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858247 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858248, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786032, 'DEMO-AI-DECISION-032-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858248 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858249, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786032, 'DEMO-AI-DECISION-032-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858249 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858250, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786032, 'DEMO-AI-DECISION-032-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858250 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858251, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786032, 'DEMO-AI-DECISION-032-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858251 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858252, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786032, 'DEMO-AI-DECISION-032-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858252 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858253, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786032, 'DEMO-AI-DECISION-032-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858253 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858254, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786032, 'DEMO-AI-DECISION-032-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858254 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858255, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786032, 'DEMO-AI-DECISION-032-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858255 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858256, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786032, 'DEMO-AI-DECISION-032-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858256 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858257, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786032, 'DEMO-AI-DECISION-032-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858257 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858258, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786032, 'DEMO-AI-DECISION-032-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858258 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858259, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786032, 'DEMO-AI-DECISION-032-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858259 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858260, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786032, 'DEMO-AI-DECISION-032-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858260 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858261, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786032, 'DEMO-AI-DECISION-032-ST', 8, 6.8, 8, 1.2, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858261 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759032, '000000', 9007199254788032, 'DEMO-AI-DECISION-032-VENDOR', 'Demo supplier 032', DATE_FORMAT(CURDATE(), '%Y-%m'), 98, 100, 98, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759032 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757032, '000000', 'DEMO-AI-DECISION-032-PO', 9007199254788032, 'DEMO-AI-DECISION-032-VENDOR', 'Demo supplier 032', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757032 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758032, '000000', 9007199254757032, 9007199254747032, 'DEMO-AI-DECISION-032-COMP', 'Demo component 032', 1, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758032 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750033, '000000', 'DEMO-AI-DECISION-033-PLAN', 'Demo production 033', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750033 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745033, '000000', 'DEMO-AI-DECISION-033', 9007199254750033, 'Demo production 033', 9007199254746033, 'DEMO-AI-DECISION-033-PRODUCT', 'Demo product 033', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785033, 'Demo workshop 033', 9007199254786033, 'Demo resource 033', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745033 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751033, '000000', 'DEMO-AI-DECISION-033-MRP', 9007199254750033, 'DEMO-AI-DECISION-033-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 63, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751033 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752033, '000000', 9007199254751033, 9007199254747033, 'DEMO-AI-DECISION-033-COMP', 'Demo component 033', 63, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752033 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753033, '000000', 9007199254747033, 'DEMO-AI-DECISION-033-COMP', 'Demo component 033', 9007199254789000, 'DEMO-AI-DECISION-WH', 0, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753033 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754033, '000000', 'DEMO-AI-DECISION-033-SCHEDULE', 'Demo baseline schedule 033', 9007199254785033, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754033 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755033, '000000', 9007199254754033, 9007199254745033, 'DEMO-AI-DECISION-033', 1, 9007199254749033, 'WORKSTATION', 9007199254786033, 'Demo resource 033', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 20 DAY), 18, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755033 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858336, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786033, 'DEMO-AI-DECISION-033-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858336 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858337, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786033, 'DEMO-AI-DECISION-033-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858337 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858338, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786033, 'DEMO-AI-DECISION-033-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858338 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858339, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786033, 'DEMO-AI-DECISION-033-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858339 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858340, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786033, 'DEMO-AI-DECISION-033-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858340 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858341, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786033, 'DEMO-AI-DECISION-033-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858341 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858342, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786033, 'DEMO-AI-DECISION-033-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858342 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858343, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786033, 'DEMO-AI-DECISION-033-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858343 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858344, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786033, 'DEMO-AI-DECISION-033-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858344 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858345, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786033, 'DEMO-AI-DECISION-033-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858345 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858346, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786033, 'DEMO-AI-DECISION-033-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858346 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858347, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786033, 'DEMO-AI-DECISION-033-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858347 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858348, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786033, 'DEMO-AI-DECISION-033-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858348 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858349, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786033, 'DEMO-AI-DECISION-033-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858349 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858350, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786033, 'DEMO-AI-DECISION-033-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858350 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858351, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786033, 'DEMO-AI-DECISION-033-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858351 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858352, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786033, 'DEMO-AI-DECISION-033-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858352 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858353, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786033, 'DEMO-AI-DECISION-033-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858353 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858354, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786033, 'DEMO-AI-DECISION-033-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858354 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858355, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786033, 'DEMO-AI-DECISION-033-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858355 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858356, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786033, 'DEMO-AI-DECISION-033-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858356 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858357, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786033, 'DEMO-AI-DECISION-033-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858357 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858358, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786033, 'DEMO-AI-DECISION-033-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858358 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858359, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786033, 'DEMO-AI-DECISION-033-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858359 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858360, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786033, 'DEMO-AI-DECISION-033-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858360 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858361, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786033, 'DEMO-AI-DECISION-033-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858361 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858362, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786033, 'DEMO-AI-DECISION-033-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858362 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759033, '000000', 9007199254788033, 'DEMO-AI-DECISION-033-VENDOR', 'Demo supplier 033', DATE_FORMAT(CURDATE(), '%Y-%m'), 80, 100, 80, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759033 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757033, '000000', 'DEMO-AI-DECISION-033-PO', 9007199254788033, 'DEMO-AI-DECISION-033-VENDOR', 'Demo supplier 033', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757033 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758033, '000000', 9007199254757033, 9007199254747033, 'DEMO-AI-DECISION-033-COMP', 'Demo component 033', 63, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758033 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750034, '000000', 'DEMO-AI-DECISION-034-PLAN', 'Demo production 034', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750034 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745034, '000000', 'DEMO-AI-DECISION-034', 9007199254750034, 'Demo production 034', 9007199254746034, 'DEMO-AI-DECISION-034-PRODUCT', 'Demo product 034', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785034, 'Demo workshop 034', 9007199254786034, 'Demo resource 034', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745034 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751034, '000000', 'DEMO-AI-DECISION-034-MRP', 9007199254750034, 'DEMO-AI-DECISION-034-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 64, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751034 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752034, '000000', 9007199254751034, 9007199254747034, 'DEMO-AI-DECISION-034-COMP', 'Demo component 034', 64, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752034 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753034, '000000', 9007199254747034, 'DEMO-AI-DECISION-034-COMP', 'Demo component 034', 9007199254789000, 'DEMO-AI-DECISION-WH', 0, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753034 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754034, '000000', 'DEMO-AI-DECISION-034-SCHEDULE', 'Demo baseline schedule 034', 9007199254785034, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754034 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755034, '000000', 9007199254754034, 9007199254745034, 'DEMO-AI-DECISION-034', 1, 9007199254749034, 'WORKSTATION', 9007199254786034, 'Demo resource 034', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 20 DAY), 18, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755034 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858437, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786034, 'DEMO-AI-DECISION-034-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858437 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858438, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786034, 'DEMO-AI-DECISION-034-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858438 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858439, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786034, 'DEMO-AI-DECISION-034-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858439 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858440, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786034, 'DEMO-AI-DECISION-034-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858440 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858441, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786034, 'DEMO-AI-DECISION-034-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858441 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858442, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786034, 'DEMO-AI-DECISION-034-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858442 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858443, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786034, 'DEMO-AI-DECISION-034-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858443 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858444, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786034, 'DEMO-AI-DECISION-034-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858444 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858445, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786034, 'DEMO-AI-DECISION-034-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858445 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858446, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786034, 'DEMO-AI-DECISION-034-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858446 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858447, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786034, 'DEMO-AI-DECISION-034-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858447 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858448, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786034, 'DEMO-AI-DECISION-034-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858448 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858449, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786034, 'DEMO-AI-DECISION-034-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858449 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858450, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786034, 'DEMO-AI-DECISION-034-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858450 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858451, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786034, 'DEMO-AI-DECISION-034-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858451 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858452, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786034, 'DEMO-AI-DECISION-034-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858452 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858453, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786034, 'DEMO-AI-DECISION-034-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858453 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858454, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786034, 'DEMO-AI-DECISION-034-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858454 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858455, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786034, 'DEMO-AI-DECISION-034-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858455 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858456, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786034, 'DEMO-AI-DECISION-034-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858456 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858457, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786034, 'DEMO-AI-DECISION-034-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858457 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858458, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786034, 'DEMO-AI-DECISION-034-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858458 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858459, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786034, 'DEMO-AI-DECISION-034-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858459 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858460, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786034, 'DEMO-AI-DECISION-034-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858460 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858461, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786034, 'DEMO-AI-DECISION-034-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858461 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858462, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786034, 'DEMO-AI-DECISION-034-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858462 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858463, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786034, 'DEMO-AI-DECISION-034-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858463 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759034, '000000', 9007199254788034, 'DEMO-AI-DECISION-034-VENDOR', 'Demo supplier 034', DATE_FORMAT(CURDATE(), '%Y-%m'), 80, 100, 80, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759034 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757034, '000000', 'DEMO-AI-DECISION-034-PO', 9007199254788034, 'DEMO-AI-DECISION-034-VENDOR', 'Demo supplier 034', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757034 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758034, '000000', 9007199254757034, 9007199254747034, 'DEMO-AI-DECISION-034-COMP', 'Demo component 034', 64, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758034 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750035, '000000', 'DEMO-AI-DECISION-035-PLAN', 'Demo production 035', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750035 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745035, '000000', 'DEMO-AI-DECISION-035', 9007199254750035, 'Demo production 035', 9007199254746035, 'DEMO-AI-DECISION-035-PRODUCT', 'Demo product 035', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785035, 'Demo workshop 035', 9007199254786035, 'Demo resource 035', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745035 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751035, '000000', 'DEMO-AI-DECISION-035-MRP', 9007199254750035, 'DEMO-AI-DECISION-035-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 65, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751035 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752035, '000000', 9007199254751035, 9007199254747035, 'DEMO-AI-DECISION-035-COMP', 'Demo component 035', 65, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752035 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753035, '000000', 9007199254747035, 'DEMO-AI-DECISION-035-COMP', 'Demo component 035', 9007199254789000, 'DEMO-AI-DECISION-WH', 0, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753035 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754035, '000000', 'DEMO-AI-DECISION-035-SCHEDULE', 'Demo baseline schedule 035', 9007199254785035, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754035 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755035, '000000', 9007199254754035, 9007199254745035, 'DEMO-AI-DECISION-035', 1, 9007199254749035, 'WORKSTATION', 9007199254786035, 'Demo resource 035', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 20 DAY), 18, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755035 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858538, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786035, 'DEMO-AI-DECISION-035-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858538 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858539, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786035, 'DEMO-AI-DECISION-035-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858539 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858540, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786035, 'DEMO-AI-DECISION-035-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858540 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858541, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786035, 'DEMO-AI-DECISION-035-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858541 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858542, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786035, 'DEMO-AI-DECISION-035-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858542 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858543, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786035, 'DEMO-AI-DECISION-035-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858543 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858544, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786035, 'DEMO-AI-DECISION-035-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858544 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858545, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786035, 'DEMO-AI-DECISION-035-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858545 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858546, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786035, 'DEMO-AI-DECISION-035-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858546 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858547, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786035, 'DEMO-AI-DECISION-035-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858547 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858548, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786035, 'DEMO-AI-DECISION-035-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858548 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858549, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786035, 'DEMO-AI-DECISION-035-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858549 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858550, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786035, 'DEMO-AI-DECISION-035-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858550 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858551, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786035, 'DEMO-AI-DECISION-035-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858551 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858552, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786035, 'DEMO-AI-DECISION-035-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858552 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858553, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786035, 'DEMO-AI-DECISION-035-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858553 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858554, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786035, 'DEMO-AI-DECISION-035-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858554 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858555, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786035, 'DEMO-AI-DECISION-035-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858555 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858556, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786035, 'DEMO-AI-DECISION-035-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858556 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858557, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786035, 'DEMO-AI-DECISION-035-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858557 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858558, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786035, 'DEMO-AI-DECISION-035-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858558 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858559, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786035, 'DEMO-AI-DECISION-035-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858559 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858560, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786035, 'DEMO-AI-DECISION-035-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858560 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858561, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786035, 'DEMO-AI-DECISION-035-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858561 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858562, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786035, 'DEMO-AI-DECISION-035-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858562 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858563, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786035, 'DEMO-AI-DECISION-035-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858563 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858564, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786035, 'DEMO-AI-DECISION-035-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858564 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759035, '000000', 9007199254788035, 'DEMO-AI-DECISION-035-VENDOR', 'Demo supplier 035', DATE_FORMAT(CURDATE(), '%Y-%m'), 80, 100, 80, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759035 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757035, '000000', 'DEMO-AI-DECISION-035-PO', 9007199254788035, 'DEMO-AI-DECISION-035-VENDOR', 'Demo supplier 035', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757035 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758035, '000000', 9007199254757035, 9007199254747035, 'DEMO-AI-DECISION-035-COMP', 'Demo component 035', 65, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758035 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750036, '000000', 'DEMO-AI-DECISION-036-PLAN', 'Demo production 036', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750036 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745036, '000000', 'DEMO-AI-DECISION-036', 9007199254750036, 'Demo production 036', 9007199254746036, 'DEMO-AI-DECISION-036-PRODUCT', 'Demo product 036', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785036, 'Demo workshop 036', 9007199254786036, 'Demo resource 036', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745036 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751036, '000000', 'DEMO-AI-DECISION-036-MRP', 9007199254750036, 'DEMO-AI-DECISION-036-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 66, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751036 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752036, '000000', 9007199254751036, 9007199254747036, 'DEMO-AI-DECISION-036-COMP', 'Demo component 036', 66, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752036 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753036, '000000', 9007199254747036, 'DEMO-AI-DECISION-036-COMP', 'Demo component 036', 9007199254789000, 'DEMO-AI-DECISION-WH', 0, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753036 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754036, '000000', 'DEMO-AI-DECISION-036-SCHEDULE', 'Demo baseline schedule 036', 9007199254785036, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754036 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755036, '000000', 9007199254754036, 9007199254745036, 'DEMO-AI-DECISION-036', 1, 9007199254749036, 'WORKSTATION', 9007199254786036, 'Demo resource 036', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 20 DAY), 18, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755036 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858639, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786036, 'DEMO-AI-DECISION-036-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858639 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858640, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786036, 'DEMO-AI-DECISION-036-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858640 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858641, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786036, 'DEMO-AI-DECISION-036-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858641 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858642, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786036, 'DEMO-AI-DECISION-036-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858642 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858643, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786036, 'DEMO-AI-DECISION-036-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858643 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858644, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786036, 'DEMO-AI-DECISION-036-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858644 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858645, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786036, 'DEMO-AI-DECISION-036-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858645 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858646, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786036, 'DEMO-AI-DECISION-036-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858646 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858647, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786036, 'DEMO-AI-DECISION-036-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858647 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858648, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786036, 'DEMO-AI-DECISION-036-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858648 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858649, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786036, 'DEMO-AI-DECISION-036-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858649 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858650, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786036, 'DEMO-AI-DECISION-036-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858650 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858651, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786036, 'DEMO-AI-DECISION-036-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858651 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858652, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786036, 'DEMO-AI-DECISION-036-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858652 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858653, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786036, 'DEMO-AI-DECISION-036-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858653 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858654, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786036, 'DEMO-AI-DECISION-036-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858654 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858655, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786036, 'DEMO-AI-DECISION-036-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858655 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858656, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786036, 'DEMO-AI-DECISION-036-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858656 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858657, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786036, 'DEMO-AI-DECISION-036-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858657 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858658, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786036, 'DEMO-AI-DECISION-036-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858658 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858659, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786036, 'DEMO-AI-DECISION-036-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858659 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858660, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786036, 'DEMO-AI-DECISION-036-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858660 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858661, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786036, 'DEMO-AI-DECISION-036-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858661 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858662, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786036, 'DEMO-AI-DECISION-036-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858662 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858663, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786036, 'DEMO-AI-DECISION-036-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858663 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858664, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786036, 'DEMO-AI-DECISION-036-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858664 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858665, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786036, 'DEMO-AI-DECISION-036-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858665 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759036, '000000', 9007199254788036, 'DEMO-AI-DECISION-036-VENDOR', 'Demo supplier 036', DATE_FORMAT(CURDATE(), '%Y-%m'), 80, 100, 80, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759036 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757036, '000000', 'DEMO-AI-DECISION-036-PO', 9007199254788036, 'DEMO-AI-DECISION-036-VENDOR', 'Demo supplier 036', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757036 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758036, '000000', 9007199254757036, 9007199254747036, 'DEMO-AI-DECISION-036-COMP', 'Demo component 036', 66, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758036 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750037, '000000', 'DEMO-AI-DECISION-037-PLAN', 'Demo production 037', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750037 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745037, '000000', 'DEMO-AI-DECISION-037', 9007199254750037, 'Demo production 037', 9007199254746037, 'DEMO-AI-DECISION-037-PRODUCT', 'Demo product 037', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785037, 'Demo workshop 037', 9007199254786037, 'Demo resource 037', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745037 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751037, '000000', 'DEMO-AI-DECISION-037-MRP', 9007199254750037, 'DEMO-AI-DECISION-037-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 67, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751037 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752037, '000000', 9007199254751037, 9007199254747037, 'DEMO-AI-DECISION-037-COMP', 'Demo component 037', 67, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752037 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753037, '000000', 9007199254747037, 'DEMO-AI-DECISION-037-COMP', 'Demo component 037', 9007199254789000, 'DEMO-AI-DECISION-WH', 0, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753037 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754037, '000000', 'DEMO-AI-DECISION-037-SCHEDULE', 'Demo baseline schedule 037', 9007199254785037, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754037 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755037, '000000', 9007199254754037, 9007199254745037, 'DEMO-AI-DECISION-037', 1, 9007199254749037, 'WORKSTATION', 9007199254786037, 'Demo resource 037', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 20 DAY), 18, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755037 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858740, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786037, 'DEMO-AI-DECISION-037-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858740 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858741, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786037, 'DEMO-AI-DECISION-037-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858741 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858742, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786037, 'DEMO-AI-DECISION-037-ST', 3, 3, 2.73, 0, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858742 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858743, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786037, 'DEMO-AI-DECISION-037-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858743 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858744, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786037, 'DEMO-AI-DECISION-037-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858744 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858745, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786037, 'DEMO-AI-DECISION-037-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858745 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858746, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786037, 'DEMO-AI-DECISION-037-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858746 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858747, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786037, 'DEMO-AI-DECISION-037-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858747 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858748, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786037, 'DEMO-AI-DECISION-037-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858748 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858749, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786037, 'DEMO-AI-DECISION-037-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858749 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858750, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786037, 'DEMO-AI-DECISION-037-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858750 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858751, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786037, 'DEMO-AI-DECISION-037-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858751 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858752, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786037, 'DEMO-AI-DECISION-037-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858752 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858753, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786037, 'DEMO-AI-DECISION-037-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858753 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858754, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786037, 'DEMO-AI-DECISION-037-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858754 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858755, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786037, 'DEMO-AI-DECISION-037-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858755 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858756, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786037, 'DEMO-AI-DECISION-037-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858756 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858757, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786037, 'DEMO-AI-DECISION-037-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858757 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858758, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786037, 'DEMO-AI-DECISION-037-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858758 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858759, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786037, 'DEMO-AI-DECISION-037-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858759 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858760, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786037, 'DEMO-AI-DECISION-037-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858760 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858761, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786037, 'DEMO-AI-DECISION-037-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858761 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858762, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786037, 'DEMO-AI-DECISION-037-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858762 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858763, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786037, 'DEMO-AI-DECISION-037-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858763 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858764, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786037, 'DEMO-AI-DECISION-037-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858764 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858765, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786037, 'DEMO-AI-DECISION-037-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858765 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858766, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786037, 'DEMO-AI-DECISION-037-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858766 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759037, '000000', 9007199254788037, 'DEMO-AI-DECISION-037-VENDOR', 'Demo supplier 037', DATE_FORMAT(CURDATE(), '%Y-%m'), 80, 100, 80, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759037 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757037, '000000', 'DEMO-AI-DECISION-037-PO', 9007199254788037, 'DEMO-AI-DECISION-037-VENDOR', 'Demo supplier 037', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757037 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758037, '000000', 9007199254757037, 9007199254747037, 'DEMO-AI-DECISION-037-COMP', 'Demo component 037', 67, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758037 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750038, '000000', 'DEMO-AI-DECISION-038-PLAN', 'Demo production 038', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750038 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745038, '000000', 'DEMO-AI-DECISION-038', 9007199254750038, 'Demo production 038', 9007199254746038, 'DEMO-AI-DECISION-038-PRODUCT', 'Demo product 038', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785038, 'Demo workshop 038', 9007199254786038, 'Demo resource 038', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745038 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751038, '000000', 'DEMO-AI-DECISION-038-MRP', 9007199254750038, 'DEMO-AI-DECISION-038-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 5, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751038 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752038, '000000', 9007199254751038, 9007199254747038, 'DEMO-AI-DECISION-038-COMP', 'Demo component 038', 5, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752038 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753038, '000000', 9007199254747038, 'DEMO-AI-DECISION-038-COMP', 'Demo component 038', 9007199254789000, 'DEMO-AI-DECISION-WH', 100, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753038 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858841, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786038, 'DEMO-AI-DECISION-038-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858841 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858842, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786038, 'DEMO-AI-DECISION-038-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858842 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858843, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786038, 'DEMO-AI-DECISION-038-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858843 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858844, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786038, 'DEMO-AI-DECISION-038-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858844 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858845, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786038, 'DEMO-AI-DECISION-038-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858845 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858846, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786038, 'DEMO-AI-DECISION-038-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858846 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858847, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786038, 'DEMO-AI-DECISION-038-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858847 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858848, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786038, 'DEMO-AI-DECISION-038-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858848 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858849, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786038, 'DEMO-AI-DECISION-038-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858849 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858850, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786038, 'DEMO-AI-DECISION-038-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858850 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858851, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786038, 'DEMO-AI-DECISION-038-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858851 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858852, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786038, 'DEMO-AI-DECISION-038-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858852 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858853, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786038, 'DEMO-AI-DECISION-038-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858853 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858854, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786038, 'DEMO-AI-DECISION-038-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858854 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858855, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786038, 'DEMO-AI-DECISION-038-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858855 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858856, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786038, 'DEMO-AI-DECISION-038-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858856 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858857, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786038, 'DEMO-AI-DECISION-038-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858857 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858858, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786038, 'DEMO-AI-DECISION-038-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858858 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858859, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786038, 'DEMO-AI-DECISION-038-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858859 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858860, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786038, 'DEMO-AI-DECISION-038-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858860 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858861, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786038, 'DEMO-AI-DECISION-038-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858861 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858862, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786038, 'DEMO-AI-DECISION-038-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858862 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858863, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786038, 'DEMO-AI-DECISION-038-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858863 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858864, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786038, 'DEMO-AI-DECISION-038-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858864 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858865, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786038, 'DEMO-AI-DECISION-038-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858865 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858866, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786038, 'DEMO-AI-DECISION-038-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858866 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858867, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786038, 'DEMO-AI-DECISION-038-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858867 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759038, '000000', 9007199254788038, 'DEMO-AI-DECISION-038-VENDOR', 'Demo supplier 038', DATE_FORMAT(CURDATE(), '%Y-%m'), 100, 2, 2, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759038 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757038, '000000', 'DEMO-AI-DECISION-038-PO', 9007199254788038, 'DEMO-AI-DECISION-038-VENDOR', 'Demo supplier 038', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757038 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758038, '000000', 9007199254757038, 9007199254747038, 'DEMO-AI-DECISION-038-COMP', 'Demo component 038', 1, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758038 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750039, '000000', 'DEMO-AI-DECISION-039-PLAN', 'Demo production 039', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750039 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745039, '000000', 'DEMO-AI-DECISION-039', 9007199254750039, 'Demo production 039', 9007199254746039, 'DEMO-AI-DECISION-039-PRODUCT', 'Demo product 039', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785039, 'Demo workshop 039', 9007199254786039, 'Demo resource 039', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745039 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753039, '000000', 9007199254747039, 'DEMO-AI-DECISION-039-COMP', 'Demo component 039', 9007199254789000, 'DEMO-AI-DECISION-WH', 100, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753039 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754039, '000000', 'DEMO-AI-DECISION-039-SCHEDULE', 'Demo baseline schedule 039', 9007199254785039, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754039 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755039, '000000', 9007199254754039, 9007199254745039, 'DEMO-AI-DECISION-039', 1, 9007199254749039, 'WORKSTATION', 9007199254786039, 'Demo resource 039', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 12, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755039 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858942, '000000', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'WORKSTATION', 9007199254786039, 'DEMO-AI-DECISION-039-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858942 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858943, '000000', DATE_ADD(CURDATE(), INTERVAL 4 DAY), 'WORKSTATION', 9007199254786039, 'DEMO-AI-DECISION-039-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858943 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858944, '000000', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'WORKSTATION', 9007199254786039, 'DEMO-AI-DECISION-039-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858944 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858945, '000000', DATE_ADD(CURDATE(), INTERVAL 6 DAY), 'WORKSTATION', 9007199254786039, 'DEMO-AI-DECISION-039-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858945 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858946, '000000', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'WORKSTATION', 9007199254786039, 'DEMO-AI-DECISION-039-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858946 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858947, '000000', DATE_ADD(CURDATE(), INTERVAL 8 DAY), 'WORKSTATION', 9007199254786039, 'DEMO-AI-DECISION-039-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858947 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858948, '000000', DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'WORKSTATION', 9007199254786039, 'DEMO-AI-DECISION-039-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858948 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858949, '000000', DATE_ADD(CURDATE(), INTERVAL 10 DAY), 'WORKSTATION', 9007199254786039, 'DEMO-AI-DECISION-039-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858949 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858950, '000000', DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'WORKSTATION', 9007199254786039, 'DEMO-AI-DECISION-039-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858950 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858951, '000000', DATE_ADD(CURDATE(), INTERVAL 12 DAY), 'WORKSTATION', 9007199254786039, 'DEMO-AI-DECISION-039-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858951 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858952, '000000', DATE_ADD(CURDATE(), INTERVAL 13 DAY), 'WORKSTATION', 9007199254786039, 'DEMO-AI-DECISION-039-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858952 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858953, '000000', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'WORKSTATION', 9007199254786039, 'DEMO-AI-DECISION-039-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858953 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858954, '000000', DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'WORKSTATION', 9007199254786039, 'DEMO-AI-DECISION-039-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858954 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858955, '000000', DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'WORKSTATION', 9007199254786039, 'DEMO-AI-DECISION-039-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858955 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858956, '000000', DATE_ADD(CURDATE(), INTERVAL 17 DAY), 'WORKSTATION', 9007199254786039, 'DEMO-AI-DECISION-039-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858956 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858957, '000000', DATE_ADD(CURDATE(), INTERVAL 18 DAY), 'WORKSTATION', 9007199254786039, 'DEMO-AI-DECISION-039-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858957 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858958, '000000', DATE_ADD(CURDATE(), INTERVAL 19 DAY), 'WORKSTATION', 9007199254786039, 'DEMO-AI-DECISION-039-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858958 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858959, '000000', DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'WORKSTATION', 9007199254786039, 'DEMO-AI-DECISION-039-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858959 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858960, '000000', DATE_ADD(CURDATE(), INTERVAL 21 DAY), 'WORKSTATION', 9007199254786039, 'DEMO-AI-DECISION-039-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858960 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858961, '000000', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 'WORKSTATION', 9007199254786039, 'DEMO-AI-DECISION-039-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858961 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858962, '000000', DATE_ADD(CURDATE(), INTERVAL 23 DAY), 'WORKSTATION', 9007199254786039, 'DEMO-AI-DECISION-039-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858962 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858963, '000000', DATE_ADD(CURDATE(), INTERVAL 24 DAY), 'WORKSTATION', 9007199254786039, 'DEMO-AI-DECISION-039-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858963 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858964, '000000', DATE_ADD(CURDATE(), INTERVAL 25 DAY), 'WORKSTATION', 9007199254786039, 'DEMO-AI-DECISION-039-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858964 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858965, '000000', DATE_ADD(CURDATE(), INTERVAL 26 DAY), 'WORKSTATION', 9007199254786039, 'DEMO-AI-DECISION-039-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858965 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858966, '000000', DATE_ADD(CURDATE(), INTERVAL 27 DAY), 'WORKSTATION', 9007199254786039, 'DEMO-AI-DECISION-039-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858966 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858967, '000000', DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'WORKSTATION', 9007199254786039, 'DEMO-AI-DECISION-039-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858967 AND `tenant_id` = '000000');

INSERT INTO `prd_capacity_calendar` (`calendar_id`, `tenant_id`, `calendar_date`, `object_type`, `object_id`, `object_code`, `available_hours`, `planned_hours`, `effective_hours`, `remaining_hours`, `status`, `del_flag`)
SELECT 9007199254858968, '000000', DATE_ADD(CURDATE(), INTERVAL 29 DAY), 'WORKSTATION', 9007199254786039, 'DEMO-AI-DECISION-039-ST', 8, 5.2, 8, 2.8, 'ACTIVE', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_capacity_calendar` WHERE `calendar_id` = 9007199254858968 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759039, '000000', 9007199254788039, 'DEMO-AI-DECISION-039-VENDOR', 'Demo supplier 039', DATE_FORMAT(CURDATE(), '%Y-%m'), 98, 100, 98, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759039 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757039, '000000', 'DEMO-AI-DECISION-039-PO', 9007199254788039, 'DEMO-AI-DECISION-039-VENDOR', 'Demo supplier 039', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757039 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758039, '000000', 9007199254757039, 9007199254747039, 'DEMO-AI-DECISION-039-COMP', 'Demo component 039', 1, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758039 AND `tenant_id` = '000000');

INSERT INTO `prd_production_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `start_date`, `end_date`, `status`, `del_flag`)
SELECT 9007199254750040, '000000', 'DEMO-AI-DECISION-040-PLAN', 'Demo production 040', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_production_plan` WHERE `plan_id` = 9007199254750040 AND `tenant_id` = '000000');

INSERT INTO `prd_work_order` (`wo_id`, `tenant_id`, `wo_no`, `plan_id`, `plan_name`, `material_id`, `material_code`, `material_name`, `planned_qty`, `completed_qty`, `plan_start_date`, `plan_end_date`, `wo_status`, `workshop_id`, `workshop_name`, `workstation_id`, `workstation_name`, `priority`, `del_flag`)
SELECT 9007199254745040, '000000', 'DEMO-AI-DECISION-040', 9007199254750040, 'Demo production 040', 9007199254746040, 'DEMO-AI-DECISION-040-PRODUCT', 'Demo product 040', 10, 0, DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'RELEASED', 9007199254785040, 'Demo workshop 040', 9007199254786040, 'Demo resource 040', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_work_order` WHERE `wo_id` = 9007199254745040 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement` (`requirement_id`, `tenant_id`, `requirement_no`, `plan_id`, `plan_no`, `requirement_date`, `total_material_types`, `total_required_quantity`, `status`, `del_flag`)
SELECT 9007199254751040, '000000', 'DEMO-AI-DECISION-040-MRP', 9007199254750040, 'DEMO-AI-DECISION-040-PLAN', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, 5, 'CONFIRMED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement` WHERE `requirement_id` = 9007199254751040 AND `tenant_id` = '000000');

INSERT INTO `prd_material_requirement_detail` (`detail_id`, `tenant_id`, `requirement_id`, `material_id`, `material_code`, `material_name`, `required_quantity`, `issued_quantity`, `required_date`, `status`, `del_flag`)
SELECT 9007199254752040, '000000', 9007199254751040, 9007199254747040, 'DEMO-AI-DECISION-040-COMP', 'Demo component 040', 5, 0, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_material_requirement_detail` WHERE `detail_id` = 9007199254752040 AND `tenant_id` = '000000');

INSERT INTO `wm_material_stock` (`stock_id`, `tenant_id`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_code`, `stock_quantity`, `reserved_quantity`, `del_flag`)
SELECT 9007199254753040, '000000', 9007199254747040, 'DEMO-AI-DECISION-040-COMP', 'Demo component 040', 9007199254789000, 'DEMO-AI-DECISION-WH', 100, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_material_stock` WHERE `stock_id` = 9007199254753040 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_plan` (`plan_id`, `tenant_id`, `plan_no`, `plan_name`, `workshop_id`, `start_date`, `horizon_days`, `strategy`, `wo_count`, `task_count`, `status`, `version`, `del_flag`)
SELECT 9007199254754040, '000000', 'DEMO-AI-DECISION-040-SCHEDULE', 'Demo baseline schedule 040', 9007199254785040, CURDATE(), 30, 'EDD', 1, 1, 'PUBLISHED', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_plan` WHERE `plan_id` = 9007199254754040 AND `tenant_id` = '000000');

INSERT INTO `prd_schedule_task` (`task_id`, `tenant_id`, `plan_id`, `wo_id`, `wo_no`, `seq_num`, `step_id`, `resource_type`, `resource_id`, `resource_name`, `process_hours`, `start_time`, `end_time`, `duration_days`, `delay_flag`, `del_flag`)
SELECT 9007199254755040, '000000', 9007199254754040, 9007199254745040, 'DEMO-AI-DECISION-040', 1, 9007199254749040, 'WORKSTATION', 9007199254786040, 'Demo resource 040', 2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 12, 0, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `prd_schedule_task` WHERE `task_id` = 9007199254755040 AND `tenant_id` = '000000');

INSERT INTO `pu_vendor_evaluation` (`evaluation_id`, `tenant_id`, `vendor_id`, `vendor_code`, `vendor_name`, `eval_period`, `delivery_rate`, `total_orders`, `on_time_orders`, `update_time`, `del_flag`)
SELECT 9007199254759040, '000000', 9007199254788040, 'DEMO-AI-DECISION-040-VENDOR', 'Demo supplier 040', DATE_FORMAT(CURDATE(), '%Y-%m'), 98, 100, 98, NOW(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_vendor_evaluation` WHERE `evaluation_id` = 9007199254759040 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order` (`order_id`, `tenant_id`, `order_no`, `vendor_id`, `vendor_code`, `vendor_name`, `order_date`, `expected_arrival_date`, `status`, `del_flag`)
SELECT 9007199254757040, '000000', 'DEMO-AI-DECISION-040-PO', 9007199254788040, 'DEMO-AI-DECISION-040-VENDOR', 'Demo supplier 040', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'APPROVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order` WHERE `order_id` = 9007199254757040 AND `tenant_id` = '000000');

INSERT INTO `pu_purchase_order_line` (`line_id`, `tenant_id`, `order_id`, `material_id`, `material_code`, `material_name`, `order_quantity`, `received_quantity`, `receive_status`, `del_flag`)
SELECT 9007199254758040, '000000', 9007199254757040, 9007199254747040, 'DEMO-AI-DECISION-040-COMP', 'Demo component 040', 1, 0, 'NOT_RECEIVED', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `pu_purchase_order_line` WHERE `line_id` = 9007199254758040 AND `tenant_id` = '000000');

COMMIT;
