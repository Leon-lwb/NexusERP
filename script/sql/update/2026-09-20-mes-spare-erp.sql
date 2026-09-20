-- =============================================================================
-- 2026-09-20-mes-spare-erp.sql  —  MES 备件与 ERP 库存打通（MES-EQ-04 MVP）
-- -----------------------------------------------------------------------------
-- 用途  : ① eq_spare_part 增加 ERP 物料/仓库映射列(material_id / warehouse_id) + 索引
--         ② 新增 MES→ERP 过账同步台账 eq_spare_erp_sync(含业务幂等键唯一约束)
--         ③ 存量迁移: 按 spare_code = material_code 回填 material_id(含前置检查与影响行数)
--         ④ 菜单/权限种子: mes:eq:spare:purchase(低库存一键生成采购申请)
--         ⑤ 建议(待人工确认, 默认注释): wm_material_stock_tx 的 MES 备件过账唯一索引
-- 执行前提: 应用启动前执行第 1、2 节(实体已含新列, 缺列会导致备件域查询报 Unknown column);
--           第 3 节回填需 ry-mes 与 ry-erp 位于同一 MySQL 实例且当前账号可跨库读写。
-- 影响库  : ry-mes(eq_spare_part / eq_spare_erp_sync)、ry-erp(bd_material / wm_material_stock_tx)、
--           ry-vue(sys_menu)
-- 幂等性  : 是。第 1、5 节用 information_schema 预检 + PREPARE 动态 DDL;
--           第 2 节 CREATE TABLE IF NOT EXISTS; 第 3 节 UPDATE 带 material_id IS NULL 守卫;
--           第 4 节 INSERT IGNORE(需在 ry-vue 库执行)。
-- Redis   : 无需清理缓存(不涉及字典/缓存键); 若历史 MES 备件曾按旧口径出入库,
--           建议在回填后对差异备件调用 POST /mes/eq/spare-part/erp-sync/{id}/retry 或人工核对。
-- 回滚    : 见文件末尾「回滚语句」。
-- =============================================================================

-- =============================================================================
-- 1. eq_spare_part: 增加 ERP 映射列 + 索引（库: ry-mes；幂等）
-- =============================================================================
-- 前置检查(执行前可先跑，确认列尚不存在；返回 0 表示需要执行 ADD COLUMN):
--   SELECT COUNT(*) FROM information_schema.COLUMNS
--    WHERE TABLE_SCHEMA = 'ry-mes' AND TABLE_NAME = 'eq_spare_part' AND COLUMN_NAME = 'material_id';

SET @ddl := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `eq_spare_part`
       ADD COLUMN `material_id` bigint(20) NULL DEFAULT NULL COMMENT ''ERP物料ID(bd_material.material_id), NULL=未映射'' AFTER `current_stock`,
       ADD COLUMN `warehouse_id` bigint(20) NULL DEFAULT NULL COMMENT ''ERP仓库ID(wm_warehouse.warehouse_id), 过账目标仓库'' AFTER `material_id`,
       ADD INDEX `idx_material_id`(`material_id`)',
    'SELECT ''eq_spare_part.material_id 已存在, 跳过第1节'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'eq_spare_part' AND COLUMN_NAME = 'material_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 存量口径更正(纯注释): current_stock 由「MES 自建账本」降级为「ERP 库存的派生冗余缓存」
ALTER TABLE `eq_spare_part`
  MODIFY COLUMN `current_stock` decimal(14, 4) NULL DEFAULT 0.0000
  COMMENT '当前库存(ERP库存的派生冗余缓存; 权威账本在 wm_material_stock, 可用量=在库-已预留且frozen_flag=0)';

-- =============================================================================
-- 2. eq_spare_erp_sync: MES→ERP 过账同步台账（库: ry-mes；幂等）
-- =============================================================================
CREATE TABLE IF NOT EXISTS `eq_spare_erp_sync` (
  `sync_id` bigint(20) NOT NULL COMMENT '同步台账ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `biz_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务幂等键 EQSP:<tenantId>:<logId>',
  `spare_id` bigint(20) NOT NULL COMMENT '备件ID',
  `spare_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备件编码(冗余)',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT 'ERP物料ID',
  `warehouse_id` bigint(20) NULL DEFAULT NULL COMMENT 'ERP仓库ID',
  `transaction_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'IN/OUT/REPAIR_USE/RETURN',
  `quantity` decimal(14, 4) NOT NULL COMMENT '变动数量(正数)',
  `ref_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '关联单据类型',
  `ref_id` bigint(20) NULL DEFAULT NULL COMMENT '关联单据ID',
  `log_id` bigint(20) NULL DEFAULT NULL COMMENT 'MES库存流水ID(eq_spare_stock_log.log_id)',
  `sync_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/SYNCED/FAILED',
  `retry_count` int(11) NULL DEFAULT 0 COMMENT '重投次数',
  `last_error` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '最近一次失败原因',
  `last_publish_time` datetime(0) NULL DEFAULT NULL COMMENT '最近一次投递时间',
  `erp_available_after` decimal(14, 4) NULL DEFAULT NULL COMMENT 'ERP过账后可用量快照',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0',
  PRIMARY KEY (`sync_id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_biz_key`(`tenant_id`, `biz_key`) USING BTREE,
  INDEX `idx_spare_id`(`spare_id`) USING BTREE,
  INDEX `idx_sync_status`(`sync_status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES备件→ERP过账同步台账' ROW_FORMAT = Dynamic;

-- =============================================================================
-- 3. 存量迁移: 按 spare_code = material_code 回填 material_id（库: 跨 ry-mes / ry-erp）
-- =============================================================================
-- 前置检查(先看影响行数; 若同名物料在不同租户下重复, 以 material_id 最小者为准):
--   SELECT COUNT(*) AS total_spare,
--          SUM(CASE WHEN sp.material_id IS NULL THEN 1 ELSE 0 END) AS unmapped_spare
--     FROM `ry-mes`.`eq_spare_part` sp WHERE sp.del_flag = '0';
--   SELECT sp.spare_id, sp.spare_code, COUNT(m.material_id) AS matched_material
--     FROM `ry-mes`.`eq_spare_part` sp
--     LEFT JOIN `ry-erp`.`bd_material` m
--            ON m.material_code = sp.spare_code AND m.tenant_id = sp.tenant_id AND m.del_flag = '0'
--    WHERE sp.del_flag = '0' AND sp.material_id IS NULL
--    GROUP BY sp.spare_id, sp.spare_code
--   HAVING matched_material > 1;   -- 期望 0 行; 有行则说明编码在 ERP 侧重复, 需人工确认映射

UPDATE `ry-mes`.`eq_spare_part` sp
  JOIN `ry-erp`.`bd_material` m
    ON m.material_code = sp.spare_code
   AND m.tenant_id = sp.tenant_id
   AND m.del_flag = '0'
   AND m.status = 1
   SET sp.material_id = m.material_id
 WHERE sp.material_id IS NULL
   AND sp.del_flag = '0';

-- 影响行数(执行后核对): SELECT ROW_COUNT();

-- 同实例不同库名时按实际库名替换; 若 ry-mes / ry-erp 不在同一实例, 请改为
-- 在 ry-erp 导出 `SELECT tenant_id, material_code, material_id FROM bd_material WHERE del_flag='0' AND status=1`
-- 后在 ry-mes 侧用临时表 JOIN 回填。

-- 3.1 仓库映射回填(需人工指定备件默认仓库; 默认不执行)
--     请把 @warehouseId 替换为实际仓库ID后取消注释执行:
-- UPDATE `ry-mes`.`eq_spare_part`
--    SET warehouse_id = @warehouseId
--  WHERE warehouse_id IS NULL AND del_flag = '0';

-- 3.2 未映射清单(必须为空, 否则这些备件在严格模式下无法出入库):
--   SELECT spare_id, spare_code, spare_name FROM `ry-mes`.`eq_spare_part`
--    WHERE material_id IS NULL AND del_flag = '0';

-- =============================================================================
-- 4. 菜单/权限种子: mes:eq:spare:purchase（库: ry-vue；幂等）
-- =============================================================================
-- 父菜单取同页既有按钮 mes:eq:spare:stock 的 parent_id, 避免硬编码菜单ID;
-- menu_id 若已被占用请先改号(本脚本用 4128)。
INSERT IGNORE INTO `ry-vue`.`sys_menu`
  (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`,
   `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
   `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 4128, '备件采购申请', parent_id, 9, '', '', '', 1, 0, 'F', '0', '0',
       'mes:eq:spare:purchase', '#', 103, 1, NOW(), NULL, NULL,
       'MES-EQ-04 低库存一键生成 ERP 采购申请'
  FROM `ry-vue`.`sys_menu` WHERE perms = 'mes:eq:spare:stock' LIMIT 1;

-- 核对: SELECT menu_id, menu_name, perms FROM `ry-vue`.`sys_menu` WHERE perms = 'mes:eq:spare:purchase';

-- =============================================================================
-- 5. 建议(默认注释, 需人工确认后执行): MES 备件过账流水唯一索引（库: ry-erp）
-- -----------------------------------------------------------------------------
-- 说明: ERP 侧消费幂等已由「Redis 原子占位 + 流水查重」实现; 该唯一索引是持久层兜底。
--       执行前必须确认存量无重复 (tenant_id, source_doc_type, source_doc_code)。
-- 前置检查:
--   SELECT tenant_id, source_doc_type, source_doc_code, COUNT(*) c
--     FROM `ry-erp`.`wm_material_stock_tx`
--    WHERE source_doc_type = 'MES_SPARE'
--    GROUP BY tenant_id, source_doc_type, source_doc_code HAVING c > 1;   -- 期望 0 行
-- ALTER TABLE `ry-erp`.`wm_material_stock_tx`
--   ADD UNIQUE INDEX `uk_mes_spare_biz`(`tenant_id`, `source_doc_type`, `source_doc_code`);

-- =============================================================================
-- 回滚语句
-- =============================================================================
-- DROP TABLE IF EXISTS `eq_spare_erp_sync`;
-- ALTER TABLE `eq_spare_part` DROP INDEX `idx_material_id`;
-- ALTER TABLE `eq_spare_part` DROP COLUMN `warehouse_id`, DROP COLUMN `material_id`;
-- DELETE FROM `ry-vue`.`sys_menu` WHERE `menu_id` = 4128 AND `perms` = 'mes:eq:spare:purchase';
