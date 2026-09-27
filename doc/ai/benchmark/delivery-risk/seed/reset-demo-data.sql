-- 仅重置一次性 ERP 库中归属本工具的 DEMO-AI-DECISION-* 数据。

-- 不得删除 nexus_ai_decision_demo_erp 范围外的数据。

START TRANSACTION;

DELETE FROM `pu_vendor_evaluation` WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND `tenant_id` = '000000' AND `vendor_code` LIKE 'DEMO-AI-DECISION-%';

DELETE FROM `pu_purchase_order_line` WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND `tenant_id` = '000000' AND `material_code` LIKE 'DEMO-AI-DECISION-%';

DELETE FROM `pu_purchase_order` WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND `tenant_id` = '000000' AND `order_no` LIKE 'DEMO-AI-DECISION-%';

DELETE FROM `prd_capacity_calendar` WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND `tenant_id` = '000000' AND `object_code` LIKE 'DEMO-AI-DECISION-%';

DELETE FROM `prd_schedule_task` WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND `tenant_id` = '000000' AND `wo_no` LIKE 'DEMO-AI-DECISION-%';

DELETE FROM `prd_schedule_plan` WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND `tenant_id` = '000000' AND `plan_no` LIKE 'DEMO-AI-DECISION-%';

DELETE FROM `wm_material_stock` WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND `tenant_id` = '000000' AND `material_code` LIKE 'DEMO-AI-DECISION-%';

DELETE FROM `prd_material_requirement_detail` WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND `tenant_id` = '000000' AND `material_code` LIKE 'DEMO-AI-DECISION-%';

DELETE FROM `prd_material_requirement` WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND `tenant_id` = '000000' AND `requirement_no` LIKE 'DEMO-AI-DECISION-%';

DELETE FROM `prd_work_order` WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND `tenant_id` = '000000' AND `wo_no` LIKE 'DEMO-AI-DECISION-%';

DELETE FROM `prd_production_plan` WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND `tenant_id` = '000000' AND `plan_no` LIKE 'DEMO-AI-DECISION-%';

DELETE FROM `bd_routing_step` WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND `tenant_id` = '000000' AND `process_code` LIKE 'DEMO-AI-DECISION-%';

DELETE FROM `bd_routing` WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND `tenant_id` = '000000' AND `routing_code` LIKE 'DEMO-AI-DECISION-%';

DELETE FROM `bd_material` WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND `tenant_id` = '000000' AND `material_code` LIKE 'DEMO-AI-DECISION-%';

DELETE FROM `bd_vendor` WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND `tenant_id` = '000000' AND `vendor_code` LIKE 'DEMO-AI-DECISION-%';

DELETE FROM `bd_process` WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND `tenant_id` = '000000' AND `process_code` LIKE 'DEMO-AI-DECISION-%';

DELETE FROM `bd_workstation` WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND `tenant_id` = '000000' AND `workstation_code` LIKE 'DEMO-AI-DECISION-%';

DELETE FROM `bd_workshop` WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND `tenant_id` = '000000' AND `workshop_code` LIKE 'DEMO-AI-DECISION-%';

DELETE FROM `wm_warehouse` WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND `tenant_id` = '000000' AND `warehouse_code` LIKE 'DEMO-AI-DECISION-%';

DELETE FROM `bd_material_category` WHERE DATABASE() = 'nexus_ai_decision_demo_erp' AND `tenant_id` = '000000' AND `category_code` LIKE 'DEMO-AI-DECISION-%';

COMMIT;
