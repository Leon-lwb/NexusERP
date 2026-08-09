-- =============================================
-- NexusERP - AI 流程编排 P8 引擎增强: 增量 DDL
-- 目标库: ry-vue
-- 创建时间: 2026-08-09
-- 所属: AI 流程编排系统 P8 (feature/ai-flow-orchestration)
-- 幂等: 仅当列不存在时添加 (MySQL 8 无 ADD COLUMN IF NOT EXISTS, 用存储过程判断)
-- =============================================

USE `ry-vue`;

-- ai_flow_node_run: 循环批次索引（split 循环场景同一节点多次执行的轮次, 非循环=0）
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'ry-vue' AND TABLE_NAME = 'ai_flow_node_run' AND COLUMN_NAME = 'batch_index');
SET @sql := IF(@exist = 0,
  'ALTER TABLE `ai_flow_node_run` ADD COLUMN `batch_index` int(11) NOT NULL DEFAULT 0 COMMENT ''循环批次索引(非循环执行=0)'', ADD INDEX `idx_instance_batch` (`instance_id`, `batch_index`)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ai_flow_instance: 画布配置快照（续跑 retry 用）
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'ry-vue' AND TABLE_NAME = 'ai_flow_instance' AND COLUMN_NAME = 'config_snapshot');
SET @sql := IF(@exist = 0,
  'ALTER TABLE `ai_flow_instance` ADD COLUMN `config_snapshot` longtext NULL COMMENT ''启动时画布配置快照(续跑用)''',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 验证
-- SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='ry-vue' AND TABLE_NAME='ai_flow_node_run' AND COLUMN_NAME='batch_index';
-- SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='ry-vue' AND TABLE_NAME='ai_flow_instance' AND COLUMN_NAME='config_snapshot';
