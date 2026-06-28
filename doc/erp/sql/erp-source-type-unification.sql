-- =====================================================================
-- 阶段一：source_type 编码体系统一 + 字典补齐
-- 目标：
--   1. 补齐 prd_material_source_type 字典（委外/虚拟件/客户供料/调拨）
--   2. 物料表 bd_material.source_type 从 tinyint(1/2/3) 迁移到 varchar(20)
--      编码统一为 PROCESS/PROCUREMENT/SUBCONTRACT/PHANTOM/CFM/TRANSFER
--   3. 修正 BOM 表历史空数据 source_type
--   4. 废弃旧字典 bd_source_type
-- 前置条件：无
-- 回滚：见文件末尾
-- =====================================================================

-- ----------------------------
-- 1.1 补齐 prd_material_source_type 字典数据
-- ----------------------------
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, remark)
VALUES
  (3, '委外',     'SUBCONTRACT', 'prd_material_source_type', '', 'warning', 'N', 103, 1, NOW(), '委外加工'),
  (4, '虚拟件',   'PHANTOM',     'prd_material_source_type', '', 'info',    'N', 103, 1, NOW(), '虚拟件：展开时跳层，不入库不生成工单'),
  (5, '客户供料', 'CFM',         'prd_material_source_type', '', 'danger',  'N', 103, 1, NOW(), '客户提供的物料'),
  (6, '调拨',     'TRANSFER',    'prd_material_source_type', '', 'primary', 'N', 103, 1, NOW(), '从其他工厂/仓库调拨');

-- ----------------------------
-- 1.2 物料表 source_type 数据迁移：数字编码 → 字符串编码
-- 注意：先迁移数据，再改字段类型，避免类型转换丢失
-- ----------------------------
UPDATE bd_material SET source_type = CASE
    WHEN source_type = '1' THEN 'PROCESS'
    WHEN source_type = '2' THEN 'PROCUREMENT'
    WHEN source_type = '3' THEN 'SUBCONTRACT'
    WHEN source_type IS NULL THEN 'PROCESS'
    ELSE source_type
END;

-- 修正 BOM 主表/明细表历史空字符串数据
UPDATE bd_bom SET source_type = 'PROCESS' WHERE source_type = '' OR source_type IS NULL;
UPDATE bd_bom_detail SET source_type = 'PROCESS' WHERE source_type = '' OR source_type IS NULL;

-- ----------------------------
-- 1.3 修改物料表 source_type 字段类型：tinyint → varchar(20)
-- ----------------------------
ALTER TABLE bd_material MODIFY COLUMN source_type varchar(20) NULL DEFAULT 'PROCESS'
    COMMENT '来源类型: PROCESS-加工 PROCUREMENT-采购 SUBCONTRACT-委外 PHANTOM-虚拟件 CFM-客户供料 TRANSFER-调拨';

-- ----------------------------
-- 1.4 废弃旧字典 bd_source_type（保留数据但标注废弃，避免影响存量引用）
-- ----------------------------
UPDATE sys_dict_type SET status = '1', remark = '已废弃，统一使用 prd_material_source_type' WHERE dict_type = 'bd_source_type';
UPDATE sys_dict_data SET status = '1' WHERE dict_type = 'bd_source_type';

-- =====================================================================
-- 回滚脚本（如需回退）
-- =====================================================================
-- ALTER TABLE bd_material MODIFY COLUMN source_type tinyint(4) NULL DEFAULT 1 COMMENT '来源类型 1:自制 2:外购 3:委外';
-- UPDATE bd_material SET source_type = CASE
--     WHEN source_type = 'PROCESS' THEN 1
--     WHEN source_type = 'PROCUREMENT' THEN 2
--     WHEN source_type = 'SUBCONTRACT' THEN 3
--     ELSE 1
-- END;
-- DELETE FROM sys_dict_data WHERE dict_type = 'prd_material_source_type' AND dict_value IN ('SUBCONTRACT','PHANTOM','CFM','TRANSFER');
-- UPDATE sys_dict_type SET status = '0', remark = NULL WHERE dict_type = 'bd_source_type';
-- UPDATE sys_dict_data SET status = '0' WHERE dict_type = 'bd_source_type';
-- ALTER TABLE bd_material DROP COLUMN is_phantom;


-- =====================================================================
-- 阶段二：物料虚拟件标识
-- 目标：在物料级增加 is_phantom 字段，标识虚拟件
-- =====================================================================

-- ----------------------------
-- 2.1 物料表增加 is_phantom 字段
-- ----------------------------
ALTER TABLE bd_material ADD COLUMN is_phantom tinyint(1) NULL DEFAULT 0
    COMMENT '是否虚拟件: 0-否 1-是（虚拟件展开时跳层，不入库不生成工单）';

