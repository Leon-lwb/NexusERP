-- 演示主数据，由 scripts/build-seed.py 生成，禁止用于 plus 或生产环境。

-- DATABASE() 字面量护栏确保在其他数据库误执行时不产生写入。

START TRANSACTION;

INSERT INTO `bd_material_category` (`category_id`, `tenant_id`, `category_code`, `category_name`, `status`, `del_flag`)
SELECT 9007199254790000, '000000', 'DEMO-AI-DECISION-CAT', 'Demo decision materials', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material_category` WHERE `category_id` = 9007199254790000 AND `tenant_id` = '000000');

INSERT INTO `wm_warehouse` (`warehouse_id`, `tenant_id`, `warehouse_code`, `warehouse_name`, `status`, `del_flag`)
SELECT 9007199254789000, '000000', 'DEMO-AI-DECISION-WH', 'Demo decision warehouse', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `wm_warehouse` WHERE `warehouse_id` = 9007199254789000 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785001, '000000', 'DEMO-AI-DECISION-001-WS', 'Demo workshop 001', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785001 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786001, '000000', 'DEMO-AI-DECISION-001-ST', 'Demo resource 001', 9007199254785001, 'DEMO-AI-DECISION-001-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786001 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787001, '000000', 'DEMO-AI-DECISION-001-PROCESS', 'Demo process 001', 9007199254786001, 'DEMO-AI-DECISION-001-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787001 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788001, '000000', 'DEMO-AI-DECISION-001-VENDOR', 'Demo supplier 001', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788001 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746001, '000000', 'DEMO-AI-DECISION-001-PRODUCT', 'Demo product 001', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746001 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747001, '000000', 'DEMO-AI-DECISION-001-COMP', 'Demo component 001', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747001 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748001, '000000', 'DEMO-AI-DECISION-001-ROUTE', 'Demo routing 001', 9007199254746001, 'DEMO-AI-DECISION-001-PRODUCT', 'Demo product 001', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748001 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749001, '000000', 9007199254748001, 1, 9007199254787001, 'DEMO-AI-DECISION-001-PROCESS', 9007199254786001, 'DEMO-AI-DECISION-001-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749001 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785002, '000000', 'DEMO-AI-DECISION-002-WS', 'Demo workshop 002', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785002 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786002, '000000', 'DEMO-AI-DECISION-002-ST', 'Demo resource 002', 9007199254785002, 'DEMO-AI-DECISION-002-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786002 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787002, '000000', 'DEMO-AI-DECISION-002-PROCESS', 'Demo process 002', 9007199254786002, 'DEMO-AI-DECISION-002-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787002 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788002, '000000', 'DEMO-AI-DECISION-002-VENDOR', 'Demo supplier 002', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788002 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746002, '000000', 'DEMO-AI-DECISION-002-PRODUCT', 'Demo product 002', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746002 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747002, '000000', 'DEMO-AI-DECISION-002-COMP', 'Demo component 002', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747002 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748002, '000000', 'DEMO-AI-DECISION-002-ROUTE', 'Demo routing 002', 9007199254746002, 'DEMO-AI-DECISION-002-PRODUCT', 'Demo product 002', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748002 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749002, '000000', 9007199254748002, 1, 9007199254787002, 'DEMO-AI-DECISION-002-PROCESS', 9007199254786002, 'DEMO-AI-DECISION-002-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749002 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785003, '000000', 'DEMO-AI-DECISION-003-WS', 'Demo workshop 003', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785003 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786003, '000000', 'DEMO-AI-DECISION-003-ST', 'Demo resource 003', 9007199254785003, 'DEMO-AI-DECISION-003-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786003 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787003, '000000', 'DEMO-AI-DECISION-003-PROCESS', 'Demo process 003', 9007199254786003, 'DEMO-AI-DECISION-003-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787003 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788003, '000000', 'DEMO-AI-DECISION-003-VENDOR', 'Demo supplier 003', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788003 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746003, '000000', 'DEMO-AI-DECISION-003-PRODUCT', 'Demo product 003', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746003 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747003, '000000', 'DEMO-AI-DECISION-003-COMP', 'Demo component 003', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747003 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748003, '000000', 'DEMO-AI-DECISION-003-ROUTE', 'Demo routing 003', 9007199254746003, 'DEMO-AI-DECISION-003-PRODUCT', 'Demo product 003', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748003 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749003, '000000', 9007199254748003, 1, 9007199254787003, 'DEMO-AI-DECISION-003-PROCESS', 9007199254786003, 'DEMO-AI-DECISION-003-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749003 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785004, '000000', 'DEMO-AI-DECISION-004-WS', 'Demo workshop 004', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785004 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786004, '000000', 'DEMO-AI-DECISION-004-ST', 'Demo resource 004', 9007199254785004, 'DEMO-AI-DECISION-004-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786004 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787004, '000000', 'DEMO-AI-DECISION-004-PROCESS', 'Demo process 004', 9007199254786004, 'DEMO-AI-DECISION-004-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787004 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788004, '000000', 'DEMO-AI-DECISION-004-VENDOR', 'Demo supplier 004', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788004 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746004, '000000', 'DEMO-AI-DECISION-004-PRODUCT', 'Demo product 004', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746004 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747004, '000000', 'DEMO-AI-DECISION-004-COMP', 'Demo component 004', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747004 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748004, '000000', 'DEMO-AI-DECISION-004-ROUTE', 'Demo routing 004', 9007199254746004, 'DEMO-AI-DECISION-004-PRODUCT', 'Demo product 004', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748004 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749004, '000000', 9007199254748004, 1, 9007199254787004, 'DEMO-AI-DECISION-004-PROCESS', 9007199254786004, 'DEMO-AI-DECISION-004-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749004 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785005, '000000', 'DEMO-AI-DECISION-005-WS', 'Demo workshop 005', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785005 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786005, '000000', 'DEMO-AI-DECISION-005-ST', 'Demo resource 005', 9007199254785005, 'DEMO-AI-DECISION-005-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786005 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787005, '000000', 'DEMO-AI-DECISION-005-PROCESS', 'Demo process 005', 9007199254786005, 'DEMO-AI-DECISION-005-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787005 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788005, '000000', 'DEMO-AI-DECISION-005-VENDOR', 'Demo supplier 005', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788005 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746005, '000000', 'DEMO-AI-DECISION-005-PRODUCT', 'Demo product 005', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746005 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747005, '000000', 'DEMO-AI-DECISION-005-COMP', 'Demo component 005', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747005 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748005, '000000', 'DEMO-AI-DECISION-005-ROUTE', 'Demo routing 005', 9007199254746005, 'DEMO-AI-DECISION-005-PRODUCT', 'Demo product 005', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748005 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749005, '000000', 9007199254748005, 1, 9007199254787005, 'DEMO-AI-DECISION-005-PROCESS', 9007199254786005, 'DEMO-AI-DECISION-005-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749005 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785006, '000000', 'DEMO-AI-DECISION-006-WS', 'Demo workshop 006', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785006 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786006, '000000', 'DEMO-AI-DECISION-006-ST', 'Demo resource 006', 9007199254785006, 'DEMO-AI-DECISION-006-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786006 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787006, '000000', 'DEMO-AI-DECISION-006-PROCESS', 'Demo process 006', 9007199254786006, 'DEMO-AI-DECISION-006-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787006 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788006, '000000', 'DEMO-AI-DECISION-006-VENDOR', 'Demo supplier 006', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788006 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746006, '000000', 'DEMO-AI-DECISION-006-PRODUCT', 'Demo product 006', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746006 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747006, '000000', 'DEMO-AI-DECISION-006-COMP', 'Demo component 006', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747006 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748006, '000000', 'DEMO-AI-DECISION-006-ROUTE', 'Demo routing 006', 9007199254746006, 'DEMO-AI-DECISION-006-PRODUCT', 'Demo product 006', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748006 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749006, '000000', 9007199254748006, 1, 9007199254787006, 'DEMO-AI-DECISION-006-PROCESS', 9007199254786006, 'DEMO-AI-DECISION-006-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749006 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785007, '000000', 'DEMO-AI-DECISION-007-WS', 'Demo workshop 007', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785007 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786007, '000000', 'DEMO-AI-DECISION-007-ST', 'Demo resource 007', 9007199254785007, 'DEMO-AI-DECISION-007-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786007 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787007, '000000', 'DEMO-AI-DECISION-007-PROCESS', 'Demo process 007', 9007199254786007, 'DEMO-AI-DECISION-007-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787007 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788007, '000000', 'DEMO-AI-DECISION-007-VENDOR', 'Demo supplier 007', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788007 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746007, '000000', 'DEMO-AI-DECISION-007-PRODUCT', 'Demo product 007', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746007 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747007, '000000', 'DEMO-AI-DECISION-007-COMP', 'Demo component 007', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747007 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748007, '000000', 'DEMO-AI-DECISION-007-ROUTE', 'Demo routing 007', 9007199254746007, 'DEMO-AI-DECISION-007-PRODUCT', 'Demo product 007', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748007 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749007, '000000', 9007199254748007, 1, 9007199254787007, 'DEMO-AI-DECISION-007-PROCESS', 9007199254786007, 'DEMO-AI-DECISION-007-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749007 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785008, '000000', 'DEMO-AI-DECISION-008-WS', 'Demo workshop 008', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785008 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786008, '000000', 'DEMO-AI-DECISION-008-ST', 'Demo resource 008', 9007199254785008, 'DEMO-AI-DECISION-008-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786008 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787008, '000000', 'DEMO-AI-DECISION-008-PROCESS', 'Demo process 008', 9007199254786008, 'DEMO-AI-DECISION-008-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787008 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788008, '000000', 'DEMO-AI-DECISION-008-VENDOR', 'Demo supplier 008', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788008 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746008, '000000', 'DEMO-AI-DECISION-008-PRODUCT', 'Demo product 008', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746008 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747008, '000000', 'DEMO-AI-DECISION-008-COMP', 'Demo component 008', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747008 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748008, '000000', 'DEMO-AI-DECISION-008-ROUTE', 'Demo routing 008', 9007199254746008, 'DEMO-AI-DECISION-008-PRODUCT', 'Demo product 008', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748008 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749008, '000000', 9007199254748008, 1, 9007199254787008, 'DEMO-AI-DECISION-008-PROCESS', 9007199254786008, 'DEMO-AI-DECISION-008-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749008 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785009, '000000', 'DEMO-AI-DECISION-009-WS', 'Demo workshop 009', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785009 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786009, '000000', 'DEMO-AI-DECISION-009-ST', 'Demo resource 009', 9007199254785009, 'DEMO-AI-DECISION-009-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786009 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787009, '000000', 'DEMO-AI-DECISION-009-PROCESS', 'Demo process 009', 9007199254786009, 'DEMO-AI-DECISION-009-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787009 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788009, '000000', 'DEMO-AI-DECISION-009-VENDOR', 'Demo supplier 009', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788009 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746009, '000000', 'DEMO-AI-DECISION-009-PRODUCT', 'Demo product 009', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746009 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747009, '000000', 'DEMO-AI-DECISION-009-COMP', 'Demo component 009', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747009 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748009, '000000', 'DEMO-AI-DECISION-009-ROUTE', 'Demo routing 009', 9007199254746009, 'DEMO-AI-DECISION-009-PRODUCT', 'Demo product 009', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748009 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749009, '000000', 9007199254748009, 1, 9007199254787009, 'DEMO-AI-DECISION-009-PROCESS', 9007199254786009, 'DEMO-AI-DECISION-009-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749009 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785010, '000000', 'DEMO-AI-DECISION-010-WS', 'Demo workshop 010', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785010 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786010, '000000', 'DEMO-AI-DECISION-010-ST', 'Demo resource 010', 9007199254785010, 'DEMO-AI-DECISION-010-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786010 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787010, '000000', 'DEMO-AI-DECISION-010-PROCESS', 'Demo process 010', 9007199254786010, 'DEMO-AI-DECISION-010-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787010 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788010, '000000', 'DEMO-AI-DECISION-010-VENDOR', 'Demo supplier 010', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788010 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746010, '000000', 'DEMO-AI-DECISION-010-PRODUCT', 'Demo product 010', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746010 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747010, '000000', 'DEMO-AI-DECISION-010-COMP', 'Demo component 010', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747010 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748010, '000000', 'DEMO-AI-DECISION-010-ROUTE', 'Demo routing 010', 9007199254746010, 'DEMO-AI-DECISION-010-PRODUCT', 'Demo product 010', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748010 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749010, '000000', 9007199254748010, 1, 9007199254787010, 'DEMO-AI-DECISION-010-PROCESS', 9007199254786010, 'DEMO-AI-DECISION-010-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749010 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785011, '000000', 'DEMO-AI-DECISION-011-WS', 'Demo workshop 011', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785011 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786011, '000000', 'DEMO-AI-DECISION-011-ST', 'Demo resource 011', 9007199254785011, 'DEMO-AI-DECISION-011-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786011 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787011, '000000', 'DEMO-AI-DECISION-011-PROCESS', 'Demo process 011', 9007199254786011, 'DEMO-AI-DECISION-011-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787011 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788011, '000000', 'DEMO-AI-DECISION-011-VENDOR', 'Demo supplier 011', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788011 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746011, '000000', 'DEMO-AI-DECISION-011-PRODUCT', 'Demo product 011', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746011 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747011, '000000', 'DEMO-AI-DECISION-011-COMP', 'Demo component 011', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747011 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748011, '000000', 'DEMO-AI-DECISION-011-ROUTE', 'Demo routing 011', 9007199254746011, 'DEMO-AI-DECISION-011-PRODUCT', 'Demo product 011', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748011 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749011, '000000', 9007199254748011, 1, 9007199254787011, 'DEMO-AI-DECISION-011-PROCESS', 9007199254786011, 'DEMO-AI-DECISION-011-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749011 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785012, '000000', 'DEMO-AI-DECISION-012-WS', 'Demo workshop 012', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785012 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786012, '000000', 'DEMO-AI-DECISION-012-ST', 'Demo resource 012', 9007199254785012, 'DEMO-AI-DECISION-012-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786012 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787012, '000000', 'DEMO-AI-DECISION-012-PROCESS', 'Demo process 012', 9007199254786012, 'DEMO-AI-DECISION-012-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787012 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788012, '000000', 'DEMO-AI-DECISION-012-VENDOR', 'Demo supplier 012', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788012 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746012, '000000', 'DEMO-AI-DECISION-012-PRODUCT', 'Demo product 012', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746012 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747012, '000000', 'DEMO-AI-DECISION-012-COMP', 'Demo component 012', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747012 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254895012, '000000', 'DEMO-AI-DECISION-012-COMP-2', 'Demo second component 012', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254895012 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748012, '000000', 'DEMO-AI-DECISION-012-ROUTE', 'Demo routing 012', 9007199254746012, 'DEMO-AI-DECISION-012-PRODUCT', 'Demo product 012', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748012 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749012, '000000', 9007199254748012, 1, 9007199254787012, 'DEMO-AI-DECISION-012-PROCESS', 9007199254786012, 'DEMO-AI-DECISION-012-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749012 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785013, '000000', 'DEMO-AI-DECISION-013-WS', 'Demo workshop 013', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785013 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786013, '000000', 'DEMO-AI-DECISION-013-ST', 'Demo resource 013', 9007199254785013, 'DEMO-AI-DECISION-013-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786013 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787013, '000000', 'DEMO-AI-DECISION-013-PROCESS', 'Demo process 013', 9007199254786013, 'DEMO-AI-DECISION-013-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787013 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788013, '000000', 'DEMO-AI-DECISION-013-VENDOR', 'Demo supplier 013', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788013 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746013, '000000', 'DEMO-AI-DECISION-013-PRODUCT', 'Demo product 013', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746013 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747013, '000000', 'DEMO-AI-DECISION-013-COMP', 'Demo component 013', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747013 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254895013, '000000', 'DEMO-AI-DECISION-013-COMP-2', 'Demo second component 013', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254895013 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748013, '000000', 'DEMO-AI-DECISION-013-ROUTE', 'Demo routing 013', 9007199254746013, 'DEMO-AI-DECISION-013-PRODUCT', 'Demo product 013', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748013 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749013, '000000', 9007199254748013, 1, 9007199254787013, 'DEMO-AI-DECISION-013-PROCESS', 9007199254786013, 'DEMO-AI-DECISION-013-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749013 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785014, '000000', 'DEMO-AI-DECISION-014-WS', 'Demo workshop 014', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785014 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786014, '000000', 'DEMO-AI-DECISION-014-ST', 'Demo resource 014', 9007199254785014, 'DEMO-AI-DECISION-014-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786014 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787014, '000000', 'DEMO-AI-DECISION-014-PROCESS', 'Demo process 014', 9007199254786014, 'DEMO-AI-DECISION-014-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787014 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788014, '000000', 'DEMO-AI-DECISION-014-VENDOR', 'Demo supplier 014', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788014 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746014, '000000', 'DEMO-AI-DECISION-014-PRODUCT', 'Demo product 014', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746014 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747014, '000000', 'DEMO-AI-DECISION-014-COMP', 'Demo component 014', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747014 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254895014, '000000', 'DEMO-AI-DECISION-014-COMP-2', 'Demo second component 014', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254895014 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748014, '000000', 'DEMO-AI-DECISION-014-ROUTE', 'Demo routing 014', 9007199254746014, 'DEMO-AI-DECISION-014-PRODUCT', 'Demo product 014', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748014 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749014, '000000', 9007199254748014, 1, 9007199254787014, 'DEMO-AI-DECISION-014-PROCESS', 9007199254786014, 'DEMO-AI-DECISION-014-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749014 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785015, '000000', 'DEMO-AI-DECISION-015-WS', 'Demo workshop 015', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785015 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786015, '000000', 'DEMO-AI-DECISION-015-ST', 'Demo resource 015', 9007199254785015, 'DEMO-AI-DECISION-015-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786015 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787015, '000000', 'DEMO-AI-DECISION-015-PROCESS', 'Demo process 015', 9007199254786015, 'DEMO-AI-DECISION-015-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787015 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788015, '000000', 'DEMO-AI-DECISION-015-VENDOR', 'Demo supplier 015', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788015 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746015, '000000', 'DEMO-AI-DECISION-015-PRODUCT', 'Demo product 015', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746015 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747015, '000000', 'DEMO-AI-DECISION-015-COMP', 'Demo component 015', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747015 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254895015, '000000', 'DEMO-AI-DECISION-015-COMP-2', 'Demo second component 015', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254895015 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748015, '000000', 'DEMO-AI-DECISION-015-ROUTE', 'Demo routing 015', 9007199254746015, 'DEMO-AI-DECISION-015-PRODUCT', 'Demo product 015', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748015 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749015, '000000', 9007199254748015, 1, 9007199254787015, 'DEMO-AI-DECISION-015-PROCESS', 9007199254786015, 'DEMO-AI-DECISION-015-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749015 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785016, '000000', 'DEMO-AI-DECISION-016-WS', 'Demo workshop 016', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785016 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786016, '000000', 'DEMO-AI-DECISION-016-ST', 'Demo resource 016', 9007199254785016, 'DEMO-AI-DECISION-016-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786016 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787016, '000000', 'DEMO-AI-DECISION-016-PROCESS', 'Demo process 016', 9007199254786016, 'DEMO-AI-DECISION-016-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787016 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788016, '000000', 'DEMO-AI-DECISION-016-VENDOR', 'Demo supplier 016', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788016 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746016, '000000', 'DEMO-AI-DECISION-016-PRODUCT', 'Demo product 016', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746016 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747016, '000000', 'DEMO-AI-DECISION-016-COMP', 'Demo component 016', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747016 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748016, '000000', 'DEMO-AI-DECISION-016-ROUTE', 'Demo routing 016', 9007199254746016, 'DEMO-AI-DECISION-016-PRODUCT', 'Demo product 016', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748016 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749016, '000000', 9007199254748016, 1, 9007199254787016, 'DEMO-AI-DECISION-016-PROCESS', 9007199254786016, 'DEMO-AI-DECISION-016-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749016 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785017, '000000', 'DEMO-AI-DECISION-017-WS', 'Demo workshop 017', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785017 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786017, '000000', 'DEMO-AI-DECISION-017-ST', 'Demo resource 017', 9007199254785017, 'DEMO-AI-DECISION-017-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786017 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787017, '000000', 'DEMO-AI-DECISION-017-PROCESS', 'Demo process 017', 9007199254786017, 'DEMO-AI-DECISION-017-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787017 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788017, '000000', 'DEMO-AI-DECISION-017-VENDOR', 'Demo supplier 017', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788017 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746017, '000000', 'DEMO-AI-DECISION-017-PRODUCT', 'Demo product 017', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746017 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747017, '000000', 'DEMO-AI-DECISION-017-COMP', 'Demo component 017', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747017 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748017, '000000', 'DEMO-AI-DECISION-017-ROUTE', 'Demo routing 017', 9007199254746017, 'DEMO-AI-DECISION-017-PRODUCT', 'Demo product 017', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748017 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749017, '000000', 9007199254748017, 1, 9007199254787017, 'DEMO-AI-DECISION-017-PROCESS', 9007199254786017, 'DEMO-AI-DECISION-017-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749017 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785018, '000000', 'DEMO-AI-DECISION-018-WS', 'Demo workshop 018', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785018 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786018, '000000', 'DEMO-AI-DECISION-018-ST', 'Demo resource 018', 9007199254785018, 'DEMO-AI-DECISION-018-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786018 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787018, '000000', 'DEMO-AI-DECISION-018-PROCESS', 'Demo process 018', 9007199254786018, 'DEMO-AI-DECISION-018-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787018 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788018, '000000', 'DEMO-AI-DECISION-018-VENDOR', 'Demo supplier 018', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788018 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746018, '000000', 'DEMO-AI-DECISION-018-PRODUCT', 'Demo product 018', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746018 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747018, '000000', 'DEMO-AI-DECISION-018-COMP', 'Demo component 018', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747018 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748018, '000000', 'DEMO-AI-DECISION-018-ROUTE', 'Demo routing 018', 9007199254746018, 'DEMO-AI-DECISION-018-PRODUCT', 'Demo product 018', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748018 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749018, '000000', 9007199254748018, 1, 9007199254787018, 'DEMO-AI-DECISION-018-PROCESS', 9007199254786018, 'DEMO-AI-DECISION-018-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749018 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785019, '000000', 'DEMO-AI-DECISION-019-WS', 'Demo workshop 019', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785019 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786019, '000000', 'DEMO-AI-DECISION-019-ST', 'Demo resource 019', 9007199254785019, 'DEMO-AI-DECISION-019-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786019 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787019, '000000', 'DEMO-AI-DECISION-019-PROCESS', 'Demo process 019', 9007199254786019, 'DEMO-AI-DECISION-019-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787019 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788019, '000000', 'DEMO-AI-DECISION-019-VENDOR', 'Demo supplier 019', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788019 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746019, '000000', 'DEMO-AI-DECISION-019-PRODUCT', 'Demo product 019', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746019 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747019, '000000', 'DEMO-AI-DECISION-019-COMP', 'Demo component 019', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747019 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748019, '000000', 'DEMO-AI-DECISION-019-ROUTE', 'Demo routing 019', 9007199254746019, 'DEMO-AI-DECISION-019-PRODUCT', 'Demo product 019', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748019 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749019, '000000', 9007199254748019, 1, 9007199254787019, 'DEMO-AI-DECISION-019-PROCESS', 9007199254786019, 'DEMO-AI-DECISION-019-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749019 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785020, '000000', 'DEMO-AI-DECISION-020-WS', 'Demo workshop 020', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785020 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786020, '000000', 'DEMO-AI-DECISION-020-ST', 'Demo resource 020', 9007199254785020, 'DEMO-AI-DECISION-020-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786020 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787020, '000000', 'DEMO-AI-DECISION-020-PROCESS', 'Demo process 020', 9007199254786020, 'DEMO-AI-DECISION-020-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787020 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788020, '000000', 'DEMO-AI-DECISION-020-VENDOR', 'Demo supplier 020', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788020 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746020, '000000', 'DEMO-AI-DECISION-020-PRODUCT', 'Demo product 020', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746020 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747020, '000000', 'DEMO-AI-DECISION-020-COMP', 'Demo component 020', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747020 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748020, '000000', 'DEMO-AI-DECISION-020-ROUTE', 'Demo routing 020', 9007199254746020, 'DEMO-AI-DECISION-020-PRODUCT', 'Demo product 020', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748020 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749020, '000000', 9007199254748020, 1, 9007199254787020, 'DEMO-AI-DECISION-020-PROCESS', 9007199254786020, 'DEMO-AI-DECISION-020-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749020 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785021, '000000', 'DEMO-AI-DECISION-021-WS', 'Demo workshop 021', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785021 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786021, '000000', 'DEMO-AI-DECISION-021-ST', 'Demo resource 021', 9007199254785021, 'DEMO-AI-DECISION-021-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786021 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787021, '000000', 'DEMO-AI-DECISION-021-PROCESS', 'Demo process 021', 9007199254786021, 'DEMO-AI-DECISION-021-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787021 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788021, '000000', 'DEMO-AI-DECISION-021-VENDOR', 'Demo supplier 021', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788021 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746021, '000000', 'DEMO-AI-DECISION-021-PRODUCT', 'Demo product 021', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746021 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747021, '000000', 'DEMO-AI-DECISION-021-COMP', 'Demo component 021', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747021 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748021, '000000', 'DEMO-AI-DECISION-021-ROUTE', 'Demo routing 021', 9007199254746021, 'DEMO-AI-DECISION-021-PRODUCT', 'Demo product 021', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748021 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749021, '000000', 9007199254748021, 1, 9007199254787021, 'DEMO-AI-DECISION-021-PROCESS', 9007199254786021, 'DEMO-AI-DECISION-021-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749021 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785022, '000000', 'DEMO-AI-DECISION-022-WS', 'Demo workshop 022', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785022 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786022, '000000', 'DEMO-AI-DECISION-022-ST', 'Demo resource 022', 9007199254785022, 'DEMO-AI-DECISION-022-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786022 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787022, '000000', 'DEMO-AI-DECISION-022-PROCESS', 'Demo process 022', 9007199254786022, 'DEMO-AI-DECISION-022-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787022 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788022, '000000', 'DEMO-AI-DECISION-022-VENDOR', 'Demo supplier 022', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788022 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746022, '000000', 'DEMO-AI-DECISION-022-PRODUCT', 'Demo product 022', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746022 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747022, '000000', 'DEMO-AI-DECISION-022-COMP', 'Demo component 022', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747022 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748022, '000000', 'DEMO-AI-DECISION-022-ROUTE', 'Demo routing 022', 9007199254746022, 'DEMO-AI-DECISION-022-PRODUCT', 'Demo product 022', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748022 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749022, '000000', 9007199254748022, 1, 9007199254787022, 'DEMO-AI-DECISION-022-PROCESS', 9007199254786022, 'DEMO-AI-DECISION-022-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749022 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785023, '000000', 'DEMO-AI-DECISION-023-WS', 'Demo workshop 023', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785023 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786023, '000000', 'DEMO-AI-DECISION-023-ST', 'Demo resource 023', 9007199254785023, 'DEMO-AI-DECISION-023-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786023 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787023, '000000', 'DEMO-AI-DECISION-023-PROCESS', 'Demo process 023', 9007199254786023, 'DEMO-AI-DECISION-023-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787023 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788023, '000000', 'DEMO-AI-DECISION-023-VENDOR', 'Demo supplier 023', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788023 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746023, '000000', 'DEMO-AI-DECISION-023-PRODUCT', 'Demo product 023', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746023 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747023, '000000', 'DEMO-AI-DECISION-023-COMP', 'Demo component 023', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747023 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748023, '000000', 'DEMO-AI-DECISION-023-ROUTE', 'Demo routing 023', 9007199254746023, 'DEMO-AI-DECISION-023-PRODUCT', 'Demo product 023', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748023 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749023, '000000', 9007199254748023, 1, 9007199254787023, 'DEMO-AI-DECISION-023-PROCESS', 9007199254786023, 'DEMO-AI-DECISION-023-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749023 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785024, '000000', 'DEMO-AI-DECISION-024-WS', 'Demo workshop 024', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785024 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786024, '000000', 'DEMO-AI-DECISION-024-ST', 'Demo resource 024', 9007199254785024, 'DEMO-AI-DECISION-024-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786024 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787024, '000000', 'DEMO-AI-DECISION-024-PROCESS', 'Demo process 024', 9007199254786024, 'DEMO-AI-DECISION-024-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787024 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788024, '000000', 'DEMO-AI-DECISION-024-VENDOR', 'Demo supplier 024', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788024 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746024, '000000', 'DEMO-AI-DECISION-024-PRODUCT', 'Demo product 024', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746024 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747024, '000000', 'DEMO-AI-DECISION-024-COMP', 'Demo component 024', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747024 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748024, '000000', 'DEMO-AI-DECISION-024-ROUTE', 'Demo routing 024', 9007199254746024, 'DEMO-AI-DECISION-024-PRODUCT', 'Demo product 024', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748024 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749024, '000000', 9007199254748024, 1, 9007199254787024, 'DEMO-AI-DECISION-024-PROCESS', 9007199254786024, 'DEMO-AI-DECISION-024-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749024 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785025, '000000', 'DEMO-AI-DECISION-025-WS', 'Demo workshop 025', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785025 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786025, '000000', 'DEMO-AI-DECISION-025-ST', 'Demo resource 025', 9007199254785025, 'DEMO-AI-DECISION-025-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786025 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787025, '000000', 'DEMO-AI-DECISION-025-PROCESS', 'Demo process 025', 9007199254786025, 'DEMO-AI-DECISION-025-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787025 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788025, '000000', 'DEMO-AI-DECISION-025-VENDOR', 'Demo supplier 025', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788025 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746025, '000000', 'DEMO-AI-DECISION-025-PRODUCT', 'Demo product 025', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746025 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747025, '000000', 'DEMO-AI-DECISION-025-COMP', 'Demo component 025', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747025 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748025, '000000', 'DEMO-AI-DECISION-025-ROUTE', 'Demo routing 025', 9007199254746025, 'DEMO-AI-DECISION-025-PRODUCT', 'Demo product 025', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748025 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749025, '000000', 9007199254748025, 1, 9007199254787025, 'DEMO-AI-DECISION-025-PROCESS', 9007199254786025, 'DEMO-AI-DECISION-025-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749025 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785026, '000000', 'DEMO-AI-DECISION-026-WS', 'Demo workshop 026', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785026 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786026, '000000', 'DEMO-AI-DECISION-026-ST', 'Demo resource 026', 9007199254785026, 'DEMO-AI-DECISION-026-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786026 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787026, '000000', 'DEMO-AI-DECISION-026-PROCESS', 'Demo process 026', 9007199254786026, 'DEMO-AI-DECISION-026-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787026 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788026, '000000', 'DEMO-AI-DECISION-026-VENDOR', 'Demo supplier 026', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788026 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746026, '000000', 'DEMO-AI-DECISION-026-PRODUCT', 'Demo product 026', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746026 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747026, '000000', 'DEMO-AI-DECISION-026-COMP', 'Demo component 026', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747026 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785027, '000000', 'DEMO-AI-DECISION-027-WS', 'Demo workshop 027', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785027 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786027, '000000', 'DEMO-AI-DECISION-027-ST', 'Demo resource 027', 9007199254785027, 'DEMO-AI-DECISION-027-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786027 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787027, '000000', 'DEMO-AI-DECISION-027-PROCESS', 'Demo process 027', 9007199254786027, 'DEMO-AI-DECISION-027-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787027 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788027, '000000', 'DEMO-AI-DECISION-027-VENDOR', 'Demo supplier 027', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788027 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746027, '000000', 'DEMO-AI-DECISION-027-PRODUCT', 'Demo product 027', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746027 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747027, '000000', 'DEMO-AI-DECISION-027-COMP', 'Demo component 027', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747027 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785028, '000000', 'DEMO-AI-DECISION-028-WS', 'Demo workshop 028', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785028 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786028, '000000', 'DEMO-AI-DECISION-028-ST', 'Demo resource 028', 9007199254785028, 'DEMO-AI-DECISION-028-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786028 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787028, '000000', 'DEMO-AI-DECISION-028-PROCESS', 'Demo process 028', 9007199254786028, 'DEMO-AI-DECISION-028-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787028 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788028, '000000', 'DEMO-AI-DECISION-028-VENDOR', 'Demo supplier 028', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788028 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746028, '000000', 'DEMO-AI-DECISION-028-PRODUCT', 'Demo product 028', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746028 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747028, '000000', 'DEMO-AI-DECISION-028-COMP', 'Demo component 028', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747028 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785029, '000000', 'DEMO-AI-DECISION-029-WS', 'Demo workshop 029', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785029 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786029, '000000', 'DEMO-AI-DECISION-029-ST', 'Demo resource 029', 9007199254785029, 'DEMO-AI-DECISION-029-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786029 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787029, '000000', 'DEMO-AI-DECISION-029-PROCESS', 'Demo process 029', 9007199254786029, 'DEMO-AI-DECISION-029-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787029 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788029, '000000', 'DEMO-AI-DECISION-029-VENDOR', 'Demo supplier 029', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788029 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746029, '000000', 'DEMO-AI-DECISION-029-PRODUCT', 'Demo product 029', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746029 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747029, '000000', 'DEMO-AI-DECISION-029-COMP', 'Demo component 029', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747029 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748029, '000000', 'DEMO-AI-DECISION-029-ROUTE', 'Demo routing 029', 9007199254746029, 'DEMO-AI-DECISION-029-PRODUCT', 'Demo product 029', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748029 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749029, '000000', 9007199254748029, 1, 9007199254787029, 'DEMO-AI-DECISION-029-PROCESS', 9007199254786029, 'DEMO-AI-DECISION-029-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749029 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785030, '000000', 'DEMO-AI-DECISION-030-WS', 'Demo workshop 030', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785030 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786030, '000000', 'DEMO-AI-DECISION-030-ST', 'Demo resource 030', 9007199254785030, 'DEMO-AI-DECISION-030-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786030 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787030, '000000', 'DEMO-AI-DECISION-030-PROCESS', 'Demo process 030', 9007199254786030, 'DEMO-AI-DECISION-030-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787030 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788030, '000000', 'DEMO-AI-DECISION-030-VENDOR', 'Demo supplier 030', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788030 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746030, '000000', 'DEMO-AI-DECISION-030-PRODUCT', 'Demo product 030', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746030 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747030, '000000', 'DEMO-AI-DECISION-030-COMP', 'Demo component 030', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747030 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748030, '000000', 'DEMO-AI-DECISION-030-ROUTE', 'Demo routing 030', 9007199254746030, 'DEMO-AI-DECISION-030-PRODUCT', 'Demo product 030', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748030 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749030, '000000', 9007199254748030, 1, 9007199254787030, 'DEMO-AI-DECISION-030-PROCESS', 9007199254786030, 'DEMO-AI-DECISION-030-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749030 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785031, '000000', 'DEMO-AI-DECISION-031-WS', 'Demo workshop 031', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785031 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786031, '000000', 'DEMO-AI-DECISION-031-ST', 'Demo resource 031', 9007199254785031, 'DEMO-AI-DECISION-031-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786031 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787031, '000000', 'DEMO-AI-DECISION-031-PROCESS', 'Demo process 031', 9007199254786031, 'DEMO-AI-DECISION-031-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787031 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788031, '000000', 'DEMO-AI-DECISION-031-VENDOR', 'Demo supplier 031', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788031 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746031, '000000', 'DEMO-AI-DECISION-031-PRODUCT', 'Demo product 031', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746031 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747031, '000000', 'DEMO-AI-DECISION-031-COMP', 'Demo component 031', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747031 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748031, '000000', 'DEMO-AI-DECISION-031-ROUTE', 'Demo routing 031', 9007199254746031, 'DEMO-AI-DECISION-031-PRODUCT', 'Demo product 031', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748031 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749031, '000000', 9007199254748031, 1, 9007199254787031, 'DEMO-AI-DECISION-031-PROCESS', 9007199254786031, 'DEMO-AI-DECISION-031-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749031 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785032, '000000', 'DEMO-AI-DECISION-032-WS', 'Demo workshop 032', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785032 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786032, '000000', 'DEMO-AI-DECISION-032-ST', 'Demo resource 032', 9007199254785032, 'DEMO-AI-DECISION-032-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786032 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787032, '000000', 'DEMO-AI-DECISION-032-PROCESS', 'Demo process 032', 9007199254786032, 'DEMO-AI-DECISION-032-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787032 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788032, '000000', 'DEMO-AI-DECISION-032-VENDOR', 'Demo supplier 032', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788032 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746032, '000000', 'DEMO-AI-DECISION-032-PRODUCT', 'Demo product 032', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746032 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747032, '000000', 'DEMO-AI-DECISION-032-COMP', 'Demo component 032', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747032 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748032, '000000', 'DEMO-AI-DECISION-032-ROUTE', 'Demo routing 032', 9007199254746032, 'DEMO-AI-DECISION-032-PRODUCT', 'Demo product 032', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748032 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749032, '000000', 9007199254748032, 1, 9007199254787032, 'DEMO-AI-DECISION-032-PROCESS', 9007199254786032, 'DEMO-AI-DECISION-032-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749032 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785033, '000000', 'DEMO-AI-DECISION-033-WS', 'Demo workshop 033', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785033 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786033, '000000', 'DEMO-AI-DECISION-033-ST', 'Demo resource 033', 9007199254785033, 'DEMO-AI-DECISION-033-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786033 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787033, '000000', 'DEMO-AI-DECISION-033-PROCESS', 'Demo process 033', 9007199254786033, 'DEMO-AI-DECISION-033-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787033 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788033, '000000', 'DEMO-AI-DECISION-033-VENDOR', 'Demo supplier 033', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788033 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746033, '000000', 'DEMO-AI-DECISION-033-PRODUCT', 'Demo product 033', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746033 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747033, '000000', 'DEMO-AI-DECISION-033-COMP', 'Demo component 033', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747033 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748033, '000000', 'DEMO-AI-DECISION-033-ROUTE', 'Demo routing 033', 9007199254746033, 'DEMO-AI-DECISION-033-PRODUCT', 'Demo product 033', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748033 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749033, '000000', 9007199254748033, 1, 9007199254787033, 'DEMO-AI-DECISION-033-PROCESS', 9007199254786033, 'DEMO-AI-DECISION-033-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749033 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785034, '000000', 'DEMO-AI-DECISION-034-WS', 'Demo workshop 034', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785034 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786034, '000000', 'DEMO-AI-DECISION-034-ST', 'Demo resource 034', 9007199254785034, 'DEMO-AI-DECISION-034-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786034 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787034, '000000', 'DEMO-AI-DECISION-034-PROCESS', 'Demo process 034', 9007199254786034, 'DEMO-AI-DECISION-034-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787034 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788034, '000000', 'DEMO-AI-DECISION-034-VENDOR', 'Demo supplier 034', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788034 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746034, '000000', 'DEMO-AI-DECISION-034-PRODUCT', 'Demo product 034', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746034 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747034, '000000', 'DEMO-AI-DECISION-034-COMP', 'Demo component 034', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747034 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748034, '000000', 'DEMO-AI-DECISION-034-ROUTE', 'Demo routing 034', 9007199254746034, 'DEMO-AI-DECISION-034-PRODUCT', 'Demo product 034', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748034 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749034, '000000', 9007199254748034, 1, 9007199254787034, 'DEMO-AI-DECISION-034-PROCESS', 9007199254786034, 'DEMO-AI-DECISION-034-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749034 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785035, '000000', 'DEMO-AI-DECISION-035-WS', 'Demo workshop 035', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785035 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786035, '000000', 'DEMO-AI-DECISION-035-ST', 'Demo resource 035', 9007199254785035, 'DEMO-AI-DECISION-035-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786035 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787035, '000000', 'DEMO-AI-DECISION-035-PROCESS', 'Demo process 035', 9007199254786035, 'DEMO-AI-DECISION-035-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787035 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788035, '000000', 'DEMO-AI-DECISION-035-VENDOR', 'Demo supplier 035', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788035 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746035, '000000', 'DEMO-AI-DECISION-035-PRODUCT', 'Demo product 035', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746035 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747035, '000000', 'DEMO-AI-DECISION-035-COMP', 'Demo component 035', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747035 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748035, '000000', 'DEMO-AI-DECISION-035-ROUTE', 'Demo routing 035', 9007199254746035, 'DEMO-AI-DECISION-035-PRODUCT', 'Demo product 035', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748035 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749035, '000000', 9007199254748035, 1, 9007199254787035, 'DEMO-AI-DECISION-035-PROCESS', 9007199254786035, 'DEMO-AI-DECISION-035-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749035 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785036, '000000', 'DEMO-AI-DECISION-036-WS', 'Demo workshop 036', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785036 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786036, '000000', 'DEMO-AI-DECISION-036-ST', 'Demo resource 036', 9007199254785036, 'DEMO-AI-DECISION-036-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786036 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787036, '000000', 'DEMO-AI-DECISION-036-PROCESS', 'Demo process 036', 9007199254786036, 'DEMO-AI-DECISION-036-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787036 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788036, '000000', 'DEMO-AI-DECISION-036-VENDOR', 'Demo supplier 036', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788036 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746036, '000000', 'DEMO-AI-DECISION-036-PRODUCT', 'Demo product 036', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746036 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747036, '000000', 'DEMO-AI-DECISION-036-COMP', 'Demo component 036', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747036 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748036, '000000', 'DEMO-AI-DECISION-036-ROUTE', 'Demo routing 036', 9007199254746036, 'DEMO-AI-DECISION-036-PRODUCT', 'Demo product 036', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748036 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749036, '000000', 9007199254748036, 1, 9007199254787036, 'DEMO-AI-DECISION-036-PROCESS', 9007199254786036, 'DEMO-AI-DECISION-036-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749036 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785037, '000000', 'DEMO-AI-DECISION-037-WS', 'Demo workshop 037', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785037 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786037, '000000', 'DEMO-AI-DECISION-037-ST', 'Demo resource 037', 9007199254785037, 'DEMO-AI-DECISION-037-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786037 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787037, '000000', 'DEMO-AI-DECISION-037-PROCESS', 'Demo process 037', 9007199254786037, 'DEMO-AI-DECISION-037-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787037 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788037, '000000', 'DEMO-AI-DECISION-037-VENDOR', 'Demo supplier 037', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788037 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746037, '000000', 'DEMO-AI-DECISION-037-PRODUCT', 'Demo product 037', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746037 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747037, '000000', 'DEMO-AI-DECISION-037-COMP', 'Demo component 037', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747037 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748037, '000000', 'DEMO-AI-DECISION-037-ROUTE', 'Demo routing 037', 9007199254746037, 'DEMO-AI-DECISION-037-PRODUCT', 'Demo product 037', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748037 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749037, '000000', 9007199254748037, 1, 9007199254787037, 'DEMO-AI-DECISION-037-PROCESS', 9007199254786037, 'DEMO-AI-DECISION-037-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749037 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785038, '000000', 'DEMO-AI-DECISION-038-WS', 'Demo workshop 038', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785038 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786038, '000000', 'DEMO-AI-DECISION-038-ST', 'Demo resource 038', 9007199254785038, 'DEMO-AI-DECISION-038-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786038 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787038, '000000', 'DEMO-AI-DECISION-038-PROCESS', 'Demo process 038', 9007199254786038, 'DEMO-AI-DECISION-038-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787038 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788038, '000000', 'DEMO-AI-DECISION-038-VENDOR', 'Demo supplier 038', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788038 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746038, '000000', 'DEMO-AI-DECISION-038-PRODUCT', 'Demo product 038', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746038 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747038, '000000', 'DEMO-AI-DECISION-038-COMP', 'Demo component 038', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747038 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748038, '000000', 'DEMO-AI-DECISION-038-ROUTE', 'Demo routing 038', 9007199254746038, 'DEMO-AI-DECISION-038-PRODUCT', 'Demo product 038', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748038 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749038, '000000', 9007199254748038, 1, 9007199254787038, 'DEMO-AI-DECISION-038-PROCESS', 9007199254786038, 'DEMO-AI-DECISION-038-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749038 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785039, '000000', 'DEMO-AI-DECISION-039-WS', 'Demo workshop 039', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785039 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786039, '000000', 'DEMO-AI-DECISION-039-ST', 'Demo resource 039', 9007199254785039, 'DEMO-AI-DECISION-039-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786039 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787039, '000000', 'DEMO-AI-DECISION-039-PROCESS', 'Demo process 039', 9007199254786039, 'DEMO-AI-DECISION-039-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787039 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788039, '000000', 'DEMO-AI-DECISION-039-VENDOR', 'Demo supplier 039', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788039 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746039, '000000', 'DEMO-AI-DECISION-039-PRODUCT', 'Demo product 039', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746039 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747039, '000000', 'DEMO-AI-DECISION-039-COMP', 'Demo component 039', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747039 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748039, '000000', 'DEMO-AI-DECISION-039-ROUTE', 'Demo routing 039', 9007199254746039, 'DEMO-AI-DECISION-039-PRODUCT', 'Demo product 039', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748039 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749039, '000000', 9007199254748039, 1, 9007199254787039, 'DEMO-AI-DECISION-039-PROCESS', 9007199254786039, 'DEMO-AI-DECISION-039-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749039 AND `tenant_id` = '000000');

INSERT INTO `bd_workshop` (`workshop_id`, `tenant_id`, `workshop_code`, `workshop_name`, `manager_id`, `manager_name`, `status`, `del_flag`)
SELECT 9007199254785040, '000000', 'DEMO-AI-DECISION-040-WS', 'Demo workshop 040', 1, 'Demo manager', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workshop` WHERE `workshop_id` = 9007199254785040 AND `tenant_id` = '000000');

INSERT INTO `bd_workstation` (`workstation_id`, `tenant_id`, `workstation_code`, `workstation_name`, `workshop_id`, `workshop_code`, `status`, `del_flag`)
SELECT 9007199254786040, '000000', 'DEMO-AI-DECISION-040-ST', 'Demo resource 040', 9007199254785040, 'DEMO-AI-DECISION-040-WS', 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_workstation` WHERE `workstation_id` = 9007199254786040 AND `tenant_id` = '000000');

INSERT INTO `bd_process` (`process_id`, `tenant_id`, `process_code`, `process_name`, `workstation_id`, `workstation_code`, `standard_hours`, `status`, `del_flag`)
SELECT 9007199254787040, '000000', 'DEMO-AI-DECISION-040-PROCESS', 'Demo process 040', 9007199254786040, 'DEMO-AI-DECISION-040-ST', 2, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_process` WHERE `process_id` = 9007199254787040 AND `tenant_id` = '000000');

INSERT INTO `bd_vendor` (`vendor_id`, `tenant_id`, `vendor_code`, `vendor_name`, `status`, `del_flag`)
SELECT 9007199254788040, '000000', 'DEMO-AI-DECISION-040-VENDOR', 'Demo supplier 040', '0', '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_vendor` WHERE `vendor_id` = 9007199254788040 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `status`, `del_flag`)
SELECT 9007199254746040, '000000', 'DEMO-AI-DECISION-040-PRODUCT', 'Demo product 040', 9007199254790000, 3, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254746040 AND `tenant_id` = '000000');

INSERT INTO `bd_material` (`material_id`, `tenant_id`, `material_code`, `material_name`, `category_id`, `material_type`, `safety_stock`, `status`, `del_flag`)
SELECT 9007199254747040, '000000', 'DEMO-AI-DECISION-040-COMP', 'Demo component 040', 9007199254790000, 1, 0, 1, '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_material` WHERE `material_id` = 9007199254747040 AND `tenant_id` = '000000');

INSERT INTO `bd_routing` (`routing_id`, `tenant_id`, `routing_code`, `routing_name`, `material_id`, `material_code`, `material_name`, `is_current`, `status`, `total_steps`, `effective_date`, `del_flag`)
SELECT 9007199254748040, '000000', 'DEMO-AI-DECISION-040-ROUTE', 'Demo routing 040', 9007199254746040, 'DEMO-AI-DECISION-040-PRODUCT', 'Demo product 040', 1, 1, 1, CURDATE(), '0'
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing` WHERE `routing_id` = 9007199254748040 AND `tenant_id` = '000000');

INSERT INTO `bd_routing_step` (`step_id`, `tenant_id`, `routing_id`, `sequence_num`, `process_id`, `process_code`, `workstation_id`, `workstation_code`, `standard_hours`)
SELECT 9007199254749040, '000000', 9007199254748040, 1, 9007199254787040, 'DEMO-AI-DECISION-040-PROCESS', 9007199254786040, 'DEMO-AI-DECISION-040-ST', 2
WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND NOT EXISTS (SELECT 1 FROM `bd_routing_step` WHERE `step_id` = 9007199254749040 AND `tenant_id` = '000000');

COMMIT;
