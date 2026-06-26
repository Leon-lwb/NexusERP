-- =============================================
-- NexusERP - ERP BOM 清理 + 成本滚算支持
-- 目标库: ry-erp
-- 说明:
--   1. bd_bom_detail 退回纯物料+用量+成本,删除制造执行关联字段
--      (workstation_id/process_id/routing_step_id 迁移至 MES MBOM,见 ry-mes-week28.sql)
--      保留 operation_step 作为 ERP 侧软引用工艺路线步骤序号
--   2. bd_bom.is_current 默认值由 1 改为 0,避免新增 BOM 误置当前版本
--   3. bd_routing_step 新增 labor_rate/machine_rate,支撑 BOM 成本滚算(工序成本)
-- 注意: MySQL 不支持 DROP COLUMN IF EXISTS,本脚本为一次性迁移,重复执行需先核对列是否存在
-- 创建时间: 2026-06-20
-- =============================================
USE `ry-erp`;

-- ----------------------------
-- 1. bd_bom_detail 删除制造执行关联字段(职责迁移至 MES MBOM)
-- ----------------------------
ALTER TABLE `bd_bom_detail` DROP COLUMN `workstation_id`;
ALTER TABLE `bd_bom_detail` DROP COLUMN `process_id`;
ALTER TABLE `bd_bom_detail` DROP COLUMN `routing_step_id`;

-- ----------------------------
-- 2. bd_bom.is_current 默认值 1 -> 0
--    原默认 1 会导致每个新建 BOM 都被置为"当前版本",引发多当前版本隐患
-- ----------------------------
ALTER TABLE `bd_bom` MODIFY COLUMN `is_current` tinyint(4) NULL DEFAULT 0 COMMENT '是否当前版本 0:否 1:是';

-- ----------------------------
-- 3. bd_routing_step 新增人工/机器费率(成本滚算依赖)
--    工序成本 = 标准工时 × (人工费率 × 人数 + 机器费率 × 机台数)
-- ----------------------------
ALTER TABLE `bd_routing_step` ADD COLUMN `labor_rate` decimal(18, 6) NULL DEFAULT NULL COMMENT '人工费率(元/小时)' AFTER `machine_count`;
ALTER TABLE `bd_routing_step` ADD COLUMN `machine_rate` decimal(18, 6) NULL DEFAULT NULL COMMENT '机器费率(元/小时)' AFTER `labor_rate`;
