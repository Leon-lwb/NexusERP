-- =============================================================================
-- NexusERP MES 刷写/测试/标签域修复增量脚本（2026-09-20，批次 B-flash）
-- 用途: MES-FLASH-03 把 mes_flash_record.content_ref_id 的语义统一为
--       "工序 step_flash_config / content_ref_id 配置的内容源ID"（与完工门禁比对口径一致），
--       原列注释"池=mes_resource_item_id"与该语义冲突，此处仅修正列注释。
-- 影响库: ry-mes（表 mes_flash_record）
-- 执行前提: 无。本脚本不改列类型、不改索引、不动业务数据（纯 COMMENT 变更）。
-- 可重复执行: 是（ALTER ... MODIFY COLUMN 幂等，重跑结果一致）。
-- 是否需清 Redis 缓存: 否。
-- 关联报告: doc/audit/2026-09-20-fix-mes-flash.md
-- 回滚: 把 COMMENT 改回 '内容源记录ID(固件=mes_firmware_id, 池=mes_resource_item_id)' 即可。
-- =============================================================================

-- 前置检查（可选，确认列存在与当前注释）：
-- SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
--   FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = 'ry-mes' AND TABLE_NAME = 'mes_flash_record' AND COLUMN_NAME = 'content_ref_id';

ALTER TABLE `mes_flash_record`
  MODIFY COLUMN `content_ref_id` bigint(20) NULL DEFAULT NULL
  COMMENT '内容源记录ID(固件=mes_firmware_id; 池类=工序配置的 contentRefId, 与完工门禁比对口径一致; 工序未配置时落本次分配的资源项ID)';
