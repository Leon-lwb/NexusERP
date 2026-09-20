-- =====================================================================================
-- 2026-09-20-uk-tenant-dimension.sql
-- 多租户唯一键维度修复 + 幂等唯一约束补齐（存量库升级脚本）
-- =====================================================================================
--
-- 【用途】
--   1) 把 50 个「编码/单号/业务键」性质的 UNIQUE 索引补上 `tenant_id` 维度，
--      使唯一性约束与应用层（多租户插件过滤后）的租户内查重口径一致；
--   2) 补齐 5 个审计报告指出的缺失幂等唯一键：
--        qc_iqc.uk_iqc_receive_material / qc_ipqc.uk_ipqc_wo_process / qc_oqc.uk_oqc_so_material
--        （MES-QC-04 自动建单幂等）
--        bd_client_credit.uk_client_tenant（ERP-BD-09 客户信用按 client_id 唯一）
--        so_credit_usage.uk_credit_so_status（ERP-SO-03 信用额度 TOCTOU）
--   3) 把 8 张表 `tenant_id` 由 varchar(64) 收敛为规范 §5 的 varchar(20)
--      （pu_* 6 张 + wm_barcode / wm_barcode_config）。
--
-- 【执行前提】
--   * MySQL 5.7 / 8.0（脚本用 information_schema + PREPARE/EXECUTE 实现幂等，
--     不使用 `ADD INDEX IF NOT EXISTS`——MySQL 5.7 不支持该语法）；
--   * 客户端必须支持 `DELIMITER` 指令（mysql CLI / Navicat / DBeaver / DataGrip 均可；
--     部分 Web 版 SQL 控制台不支持，请改用命令行）；
--   * 需要 CREATE ROUTINE / ALTER 权限，且 `information_schema` 可读；
--   * 执行前必须完整备份 ry-erp / ry-mes 两个库（mysqldump）。
--
-- 【影响库】
--   第 0 部分（辅助存储过程）：在 ry-erp 与 ry-mes **各执行一次**（存储过程建在“当前库”）。
--   第 A 部分：在 **ry-erp** 库执行（bd_/po_/prd_/pu_/sch_/so_/wm_ 表）。
--   第 B 部分：在 **ry-mes** 库执行（dc_/eq_/lt_/mes_/ncr_/pro_/qc_/sop_/tg_ 表）。
--   ry-iot / ry-vue 不在本脚本范围（ry-iot 由 IoT 修复代理负责；ry-vue 唯一键全部为全局/第三方表，豁免）。
--
-- 【是否需要停写 / 低峰执行】
--   **建议低峰执行，无需停写。**
--   - 45 个“给已有唯一键补 tenant_id”的改动：旧键是新键的严格子集 ⇒ 新键不可能产生重复，
--     DROP INDEX + ADD UNIQUE INDEX 属 INPLACE 在线 DDL，但 MySQL 5.7 在“同一 ALTER 内 DROP+ADD”
--     场景可能退化为 COPY 算法（重建整表 + 持写锁）。大表（pu_purchase_order_line、
--     dc_data_record、pro_work_order、wm_barcode 等）请安排在低峰执行并预留锁等待时间。
--   - 5 个新增唯一键：需要先按第 0 部分的前置检查清理存量重复数据，否则 ADD UNIQUE 会直接失败。
--   - 生产大表若不能接受锁，可改用 pt-online-schema-change / gh-ost 执行同一条 DDL。
--
-- 【是否需要清缓存】
--   不需要。多租户拦截器（PlusTenantLineHandler）与 MyBatis-Plus 均不缓存表索引元数据，
--   执行完无需重启应用或清理 Redis；新约束在下一次 INSERT 时即时生效。
--
-- 【幂等性】
--   本脚本是**幂等**的：辅助过程先查 information_schema，已含 tenant_id / 索引已存在 / 类型已合规
--   时输出 [SKIP] 并跳过，因此可重复执行（重复执行不会在 DROP/ADD 处报错）。
--   注意：若在**执行过程中途失败**，已成功的表保持新定义，重跑会跳过它们，属预期行为。
--
-- =====================================================================================


-- =====================================================================================
-- 第 0 部分：前置检查（只读，两个库各跑一次；确认无重复数据后再执行变更）
-- =====================================================================================

-- 0.1 【仅 ry-erp】bd_client_credit：同一租户下 client_id 是否已有多行（含逻辑删除行，它们同样占用唯一键）
-- SELECT tenant_id, client_id, COUNT(*) AS c, GROUP_CONCAT(credit_id) AS ids
--   FROM `bd_client_credit` GROUP BY tenant_id, client_id HAVING c > 1;

-- 0.2 【仅 ry-erp】so_credit_usage：同一租户 + 订单 + 状态是否已有多行
-- SELECT tenant_id, so_id, status, COUNT(*) AS c, GROUP_CONCAT(usage_id) AS ids
--   FROM `so_credit_usage` GROUP BY tenant_id, so_id, status HAVING c > 1;
-- 另需确认是否存在同一订单的多条 FROZEN（应用层已做前置查重，正常应为空）：
-- SELECT tenant_id, so_id, COUNT(*) AS c FROM `so_credit_usage`
--  WHERE status = 'FROZEN' GROUP BY tenant_id, so_id HAVING c > 1;

-- 0.3 【仅 ry-mes】qc_iqc：同一租户 + 收货单 + 物料是否已有多张 IQC（receive_id 为 NULL 的行不受唯一键约束）
-- SELECT tenant_id, receive_id, material_id, COUNT(*) AS c, GROUP_CONCAT(iqc_id) AS ids
--   FROM `qc_iqc` GROUP BY tenant_id, receive_id, material_id HAVING c > 1;
-- SELECT COUNT(*) AS iqc_receive_id_null FROM `qc_iqc` WHERE receive_id IS NULL;

-- 0.4 【仅 ry-mes】qc_ipqc：同一租户 + MES工单 + 工序是否已有多张 IPQC
-- SELECT tenant_id, mes_wo_id, process_id, COUNT(*) AS c, GROUP_CONCAT(ipqc_id) AS ids
--   FROM `qc_ipqc` GROUP BY tenant_id, mes_wo_id, process_id HAVING c > 1;
-- 注意（MES-QC-16）：process_id 为 NULL 时 MySQL 唯一键不去重，需应用层按 step_record_id 兜底。
-- SELECT COUNT(*) AS ipqc_process_id_null FROM `qc_ipqc` WHERE process_id IS NULL;

-- 0.5 【仅 ry-mes】qc_oqc：同一租户 + 销售订单 + 物料是否已有多张 OQC（so_id 为 NULL 的行不受约束）
-- SELECT tenant_id, so_id, material_id, COUNT(*) AS c, GROUP_CONCAT(oqc_id) AS ids
--   FROM `qc_oqc` GROUP BY tenant_id, so_id, material_id HAVING c > 1;
-- SELECT COUNT(*) AS oqc_so_id_null FROM `qc_oqc` WHERE so_id IS NULL;

-- 0.6 【仅 ry-erp】tenant_id 为 NULL 的存量行（类型收敛为 varchar(20) 后建议再统一回填 '000000'）
-- SELECT 'pu_purchase_order' t, COUNT(*) c FROM `pu_purchase_order` WHERE tenant_id IS NULL
-- UNION ALL SELECT 'pu_purchase_order_line', COUNT(*) FROM `pu_purchase_order_line` WHERE tenant_id IS NULL
-- UNION ALL SELECT 'pu_purchase_requisition', COUNT(*) FROM `pu_purchase_requisition` WHERE tenant_id IS NULL
-- UNION ALL SELECT 'pu_purchase_requisition_line', COUNT(*) FROM `pu_purchase_requisition_line` WHERE tenant_id IS NULL
-- UNION ALL SELECT 'pu_purchase_return', COUNT(*) FROM `pu_purchase_return` WHERE tenant_id IS NULL
-- UNION ALL SELECT 'pu_purchase_return_line', COUNT(*) FROM `pu_purchase_return_line` WHERE tenant_id IS NULL
-- UNION ALL SELECT 'wm_barcode', COUNT(*) FROM `wm_barcode` WHERE tenant_id IS NULL
-- UNION ALL SELECT 'wm_barcode_config', COUNT(*) FROM `wm_barcode_config` WHERE tenant_id IS NULL;
-- 若上面出现 tenant_id 长度 > 20 的脏数据（理论上不存在，租户编码为 6 位），先修正再执行类型收敛：
-- SELECT DISTINCT tenant_id, LENGTH(tenant_id) FROM `pu_purchase_order` WHERE LENGTH(tenant_id) > 20;


-- =====================================================================================
-- 第 0 部分续：辅助存储过程（幂等核心；两个库各执行一次）
-- =====================================================================================

DROP PROCEDURE IF EXISTS `_nexus_fix_uk_tenant`;
DROP PROCEDURE IF EXISTS `_nexus_add_uk`;
DROP PROCEDURE IF EXISTS `_nexus_fix_tenant_varchar20`;
DROP PROCEDURE IF EXISTS `_nexus_restore_uk`;

DELIMITER $$

-- 给“已存在的唯一索引”补 tenant_id：索引不存在 → SKIP；已含 tenant_id → SKIP；否则 DROP + ADD
CREATE PROCEDURE `_nexus_fix_uk_tenant`(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64),
    IN p_cols  VARCHAR(512)
)
BEGIN
    DECLARE v_exists     INT DEFAULT 0;
    DECLARE v_has_tenant INT DEFAULT 0;

    SELECT COUNT(*) INTO v_exists
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index;

    SELECT COUNT(*) INTO v_has_tenant
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index
       AND COLUMN_NAME = 'tenant_id';

    IF v_exists = 0 THEN
        SELECT CONCAT('[SKIP] ', p_table, '.', p_index, ' 不存在（表缺失或索引已改名），请人工核对') AS result;
    ELSEIF v_has_tenant > 0 THEN
        SELECT CONCAT('[SKIP] ', p_table, '.', p_index, ' 已含 tenant_id') AS result;
    ELSE
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` DROP INDEX `', p_index,
                          '`, ADD UNIQUE INDEX `', p_index, '` (', p_cols, ') USING BTREE');
        PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
        SELECT CONCAT('[OK] ', p_table, '.', p_index, ' -> (', p_cols, ')') AS result;
    END IF;
END$$

-- 新增唯一索引：索引已存在 → SKIP
CREATE PROCEDURE `_nexus_add_uk`(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64),
    IN p_cols  VARCHAR(512)
)
BEGIN
    DECLARE v_exists INT DEFAULT 0;
    DECLARE v_tbl    INT DEFAULT 0;

    SELECT COUNT(*) INTO v_tbl
      FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table;

    SELECT COUNT(*) INTO v_exists
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index;

    IF v_tbl = 0 THEN
        SELECT CONCAT('[SKIP] ', p_table, ' 不在当前库(', DATABASE(), ')，请在对应库重跑本脚本') AS result;
    ELSEIF v_exists > 0 THEN
        SELECT CONCAT('[SKIP] ', p_table, '.', p_index, ' 已存在') AS result;
    ELSE
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD UNIQUE INDEX `', p_index, '` (', p_cols, ') USING BTREE');
        PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
        SELECT CONCAT('[OK] ', p_table, '.', p_index, ' added (', p_cols, ')') AS result;
    END IF;
END$$

-- tenant_id 类型收敛为 varchar(20)（保留 NULL 语义，避免存量 NULL 行导致 MODIFY 失败）
CREATE PROCEDURE `_nexus_fix_tenant_varchar20`(IN p_table VARCHAR(64))
BEGIN
    DECLARE v_type VARCHAR(64) DEFAULT NULL;
    DECLARE v_len  BIGINT DEFAULT NULL;

    SELECT DATA_TYPE, CHARACTER_MAXIMUM_LENGTH INTO v_type, v_len
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = 'tenant_id';

    IF v_type IS NULL THEN
        SELECT CONCAT('[SKIP] ', p_table, ' 无 tenant_id 列') AS result;
    ELSEIF v_type = 'varchar' AND v_len = 20 THEN
        SELECT CONCAT('[SKIP] ', p_table, '.tenant_id 已是 varchar(20)') AS result;
    ELSE
        SET @ddl = CONCAT('ALTER TABLE `', p_table,
            '` MODIFY COLUMN `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
            ' NULL DEFAULT ''000000'' COMMENT ''租户ID''');
        PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
        SELECT CONCAT('[OK] ', p_table, '.tenant_id -> varchar(20)（原 ', v_type, '(', IFNULL(v_len, 0), ')）') AS result;
    END IF;
END$$

-- 回滚用：把索引恢复为指定列（p_cols 传“不含 tenant_id 的原始定义”）
CREATE PROCEDURE `_nexus_restore_uk`(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64),
    IN p_cols  VARCHAR(512)
)
BEGIN
    DECLARE v_exists INT DEFAULT 0;
    SELECT COUNT(*) INTO v_exists
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index;
    IF v_exists = 0 THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD UNIQUE INDEX `', p_index, '` (', p_cols, ') USING BTREE');
    ELSE
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` DROP INDEX `', p_index,
                          '`, ADD UNIQUE INDEX `', p_index, '` (', p_cols, ') USING BTREE');
    END IF;
    PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
    SELECT CONCAT('[ROLLBACK] ', p_table, '.', p_index, ' -> (', p_cols, ')') AS result;
END$$

DELIMITER ;


-- =====================================================================================
-- 第 A 部分：ry-erp 库（bd_ / po_ / prd_ / pu_ / sch_ / so_ / wm_）
--   说明：以下 25 条均为“给已存在的唯一键补 tenant_id”，旧键是新键的严格子集，
--   因此新键不可能产生重复数据，无需清数（前置检查仅作留痕）。
--   索引名保持不变，仅扩展列；列顺序沿用 ry-erp 现有风格：tenant_id 置于末位
--   （参照 ry-erp.sql:2035 `uk_routing_code_version(routing_code, routing_version, tenant_id)`）。
-- =====================================================================================

-- bd_bom.uk_parent_version : (parent_material_id, bom_version) -> (parent_material_id, bom_version, tenant_id)
--   前置检查: SHOW INDEX FROM `bd_bom`;
--   重复检查(必为空): SELECT parent_material_id,bom_version,tenant_id,COUNT(*) c FROM `bd_bom` GROUP BY 1,2,3 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('bd_bom', 'uk_parent_version', '`parent_material_id`, `bom_version`, `tenant_id`');

-- bd_material.uk_material_code : (material_code) -> (material_code, tenant_id)   [ERP-BD-06]
--   前置检查: SHOW INDEX FROM `bd_material`;
--   重复检查(必为空): SELECT material_code,tenant_id,COUNT(*) c FROM `bd_material` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('bd_material', 'uk_material_code', '`material_code`, `tenant_id`');

-- bd_material_vendor.uk_material_supplier : (material_id, vendor_id) -> (+ tenant_id)   [ERP-BD-06]
--   前置检查: SHOW INDEX FROM `bd_material_vendor`;
--   重复检查(必为空): SELECT material_id,vendor_id,tenant_id,COUNT(*) c FROM `bd_material_vendor` GROUP BY 1,2,3 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('bd_material_vendor', 'uk_material_supplier', '`material_id`, `vendor_id`, `tenant_id`');

-- bd_serial_number_rule.uk_rule_code : (rule_code) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `bd_serial_number_rule`;
--   重复检查(必为空): SELECT rule_code,tenant_id,COUNT(*) c FROM `bd_serial_number_rule` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('bd_serial_number_rule', 'uk_rule_code', '`rule_code`, `tenant_id`');

-- bd_unit_of_measure.uk_uom_code : (uom_code) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `bd_unit_of_measure`;
--   重复检查(必为空): SELECT uom_code,tenant_id,COUNT(*) c FROM `bd_unit_of_measure` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('bd_unit_of_measure', 'uk_uom_code', '`uom_code`, `tenant_id`');

-- bd_vendor.uk_vendor_code : (vendor_code) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `bd_vendor`;
--   重复检查(必为空): SELECT vendor_code,tenant_id,COUNT(*) c FROM `bd_vendor` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('bd_vendor', 'uk_vendor_code', '`vendor_code`, `tenant_id`');

-- bd_workshop.uk_workshop_code : (workshop_code) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `bd_workshop`;
--   重复检查(必为空): SELECT workshop_code,tenant_id,COUNT(*) c FROM `bd_workshop` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('bd_workshop', 'uk_workshop_code', '`workshop_code`, `tenant_id`');

-- bd_workstation.uk_workstation_code : (workstation_code) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `bd_workstation`;
--   重复检查(必为空): SELECT workstation_code,tenant_id,COUNT(*) c FROM `bd_workstation` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('bd_workstation', 'uk_workstation_code', '`workstation_code`, `tenant_id`');

-- po_requisition.uk_po_requisition_no : (requisition_no) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `po_requisition`;
--   重复检查(必为空): SELECT requisition_no,tenant_id,COUNT(*) c FROM `po_requisition` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('po_requisition', 'uk_po_requisition_no', '`requisition_no`, `tenant_id`');

-- prd_material_requirement.uk_requirement_no : (requirement_no) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `prd_material_requirement`;
--   重复检查(必为空): SELECT requirement_no,tenant_id,COUNT(*) c FROM `prd_material_requirement` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('prd_material_requirement', 'uk_requirement_no', '`requirement_no`, `tenant_id`');

-- prd_production_plan.uk_plan_no : (plan_no) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `prd_production_plan`;
--   重复检查(必为空): SELECT plan_no,tenant_id,COUNT(*) c FROM `prd_production_plan` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('prd_production_plan', 'uk_plan_no', '`plan_no`, `tenant_id`');

-- prd_work_order.uk_wo_no : (wo_no) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `prd_work_order`;
--   重复检查(必为空): SELECT wo_no,tenant_id,COUNT(*) c FROM `prd_work_order` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('prd_work_order', 'uk_wo_no', '`wo_no`, `tenant_id`');

-- prd_work_report.uk_report_no : (report_no) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `prd_work_report`;
--   重复检查(必为空): SELECT report_no,tenant_id,COUNT(*) c FROM `prd_work_report` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('prd_work_report', 'uk_report_no', '`report_no`, `tenant_id`');

-- pu_purchase_order.uk_order_no : (order_no) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `pu_purchase_order`;
--   重复检查(必为空): SELECT order_no,tenant_id,COUNT(*) c FROM `pu_purchase_order` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('pu_purchase_order', 'uk_order_no', '`order_no`, `tenant_id`');

-- pu_purchase_requisition.uk_requisition_no : (requisition_no) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `pu_purchase_requisition`;
--   重复检查(必为空): SELECT requisition_no,tenant_id,COUNT(*) c FROM `pu_purchase_requisition` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('pu_purchase_requisition', 'uk_requisition_no', '`requisition_no`, `tenant_id`');

-- pu_purchase_return.uk_return_no : (return_no) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `pu_purchase_return`;
--   重复检查(必为空): SELECT return_no,tenant_id,COUNT(*) c FROM `pu_purchase_return` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('pu_purchase_return', 'uk_return_no', '`return_no`, `tenant_id`');

-- sch_team_member.uk_team_worker : (team_id, worker_id) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `sch_team_member`;
--   重复检查(必为空): SELECT team_id,worker_id,tenant_id,COUNT(*) c FROM `sch_team_member` GROUP BY 1,2,3 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('sch_team_member', 'uk_team_worker', '`team_id`, `worker_id`, `tenant_id`');

-- so_price_list_line.uk_pl_line_tier : (price_list_id, material_id, min_qty) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `so_price_list_line`;
--   重复检查(必为空): SELECT price_list_id,material_id,min_qty,tenant_id,COUNT(*) c FROM `so_price_list_line` GROUP BY 1,2,3,4 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('so_price_list_line', 'uk_pl_line_tier', '`price_list_id`, `material_id`, `min_qty`, `tenant_id`');

-- wm_barcode.uk_barcode_code : (barcode_code) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `wm_barcode`;
--   重复检查(必为空): SELECT barcode_code,tenant_id,COUNT(*) c FROM `wm_barcode` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('wm_barcode', 'uk_barcode_code', '`barcode_code`, `tenant_id`');

-- wm_barcode_config.uk_barcode_config_code : (config_code) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `wm_barcode_config`;
--   重复检查(必为空): SELECT config_code,tenant_id,COUNT(*) c FROM `wm_barcode_config` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('wm_barcode_config', 'uk_barcode_config_code', '`config_code`, `tenant_id`');

-- wm_batch.uk_batch_code : (batch_code) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `wm_batch`;
--   重复检查(必为空): SELECT batch_code,tenant_id,COUNT(*) c FROM `wm_batch` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('wm_batch', 'uk_batch_code', '`batch_code`, `tenant_id`');

-- wm_material_handover.uk_handover_no : (handover_no) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `wm_material_handover`;
--   重复检查(必为空): SELECT handover_no,tenant_id,COUNT(*) c FROM `wm_material_handover` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('wm_material_handover', 'uk_handover_no', '`handover_no`, `tenant_id`');

-- wm_sales_issue.uk_issue_code : (issue_code) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `wm_sales_issue`;
--   重复检查(必为空): SELECT issue_code,tenant_id,COUNT(*) c FROM `wm_sales_issue` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('wm_sales_issue', 'uk_issue_code', '`issue_code`, `tenant_id`');

-- wm_sn.uk_sn_code : (sn_code) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `wm_sn`;
--   重复检查(必为空): SELECT sn_code,tenant_id,COUNT(*) c FROM `wm_sn` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('wm_sn', 'uk_sn_code', '`sn_code`, `tenant_id`');

-- wm_warehouse.uk_warehouse_code : (warehouse_code) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `wm_warehouse`;
--   重复检查(必为空): SELECT warehouse_code,tenant_id,COUNT(*) c FROM `wm_warehouse` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('wm_warehouse', 'uk_warehouse_code', '`warehouse_code`, `tenant_id`');


-- -------------------------------------------------------------------------------------
-- A-新增 1：bd_client_credit 按 (client_id, tenant_id) 唯一   [ERP-BD-09]
--   CreditCheckServiceImpl:47 用 selectOne(clientId)，多行即抛 TooManyResultsException（销售订单确认 500）。
--   前置检查（必须先跑，有重复行则先人工合并，否则 ADD UNIQUE 失败）：
--     SHOW INDEX FROM `bd_client_credit`;
--     SELECT tenant_id, client_id, COUNT(*) c, GROUP_CONCAT(credit_id) ids, GROUP_CONCAT(del_flag) flags
--       FROM `bd_client_credit` GROUP BY tenant_id, client_id HAVING c > 1;
--   注意：del_flag='2'（逻辑删除）的行同样占用唯一键，若存在“已删行 + 新行”同 client_id 的情况，
--         需先把历史行改为不同 client_id 或物理清理，再建索引（本表当前无写入 Service，风险极低）。
CALL `_nexus_add_uk`('bd_client_credit', 'uk_client_tenant', '`client_id`, `tenant_id`');

-- -------------------------------------------------------------------------------------
-- A-新增 2：so_credit_usage 按 (tenant_id, so_id, status) 唯一   [ERP-SO-03 信用额度 TOCTOU]
--   目的：堵住“两个并发 confirm() 同时读到相同已用额度并各自 INSERT FROZEN 行”的竞态
--   （CreditCheckServiceImpl:71-85 是“先 selectCount 再 insert”）。
--   前置检查（必须先跑）：
--     SHOW INDEX FROM `so_credit_usage`;
--     SELECT tenant_id, so_id, status, COUNT(*) c, GROUP_CONCAT(usage_id) ids
--       FROM `so_credit_usage` GROUP BY tenant_id, so_id, status HAVING c > 1;
--     SELECT tenant_id, so_id, COUNT(*) c FROM `so_credit_usage`
--       WHERE status = 'FROZEN' GROUP BY tenant_id, so_id HAVING c > 1;
--   语义说明（与审计建议 uk_credit_so(so_id, tenant_id) 的差异）：
--     本脚本采用 (tenant_id, so_id, status)，因为现有代码在同一订单上会**原地更新状态**
--     （release/convertToUsed 用 updateById），且 checkAndFreeze 的前置查重条件是 so_id + status=FROZEN。
--     若用 (so_id, tenant_id)，则订单 release 后再 confirm 会直接撞键；
--     用 (tenant_id, so_id, status) 则只拦截“同一订单同一状态的第二行”，与现有幂等分支完全对齐
--     （应用层只需在 checkAndFreeze 捕获 DuplicateKeyException 后按“已冻结”返回即可）。
--   残余风险：订单完成“冻结→已用→释放”后若再次冻结并再次释放，第二行 RELEASED 会撞键
--     （release() 只更新 FROZEN/USED 行，正常情况下不会触发；如需支持反复周期，请改应用层为“复用同一行”）。
CALL `_nexus_add_uk`('so_credit_usage', 'uk_credit_so_status', '`tenant_id`, `so_id`, `status`');


-- -------------------------------------------------------------------------------------
-- A-类型收敛：tenant_id varchar(64) -> varchar(20)（规范 §5）
--   前置检查：第 0.6 部分的 NULL / 超长检查。
--   保留 NULL 语义（不收紧为 NOT NULL），避免存量 NULL 行导致 MODIFY 失败；
--   待存量 NULL 行回填 '000000' 后，可再执行：
--     UPDATE `pu_purchase_order` SET tenant_id = '000000' WHERE tenant_id IS NULL;   -- 其余 7 张同理
--     ALTER TABLE `pu_purchase_order` MODIFY COLUMN `tenant_id` varchar(20) CHARACTER SET utf8mb4
--       COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID';
-- -------------------------------------------------------------------------------------
CALL `_nexus_fix_tenant_varchar20`('pu_purchase_order');
CALL `_nexus_fix_tenant_varchar20`('pu_purchase_order_line');
CALL `_nexus_fix_tenant_varchar20`('pu_purchase_requisition');
CALL `_nexus_fix_tenant_varchar20`('pu_purchase_requisition_line');
CALL `_nexus_fix_tenant_varchar20`('pu_purchase_return');
CALL `_nexus_fix_tenant_varchar20`('pu_purchase_return_line');
CALL `_nexus_fix_tenant_varchar20`('wm_barcode');
CALL `_nexus_fix_tenant_varchar20`('wm_barcode_config');


-- =====================================================================================
-- 第 B 部分：ry-mes 库（dc_ / eq_ / lt_ / mes_ / ncr_ / pro_ / qc_ / sop_ / tg_）
--   说明：25 条“给已存在的唯一键补 tenant_id”，旧键为新键的严格子集 ⇒ 无重复数据风险。
--   列顺序沿用 ry-mes 现有风格：tenant_id 置于首位
--   （参照 ry-mes.sql:657 `uk_tenant_content_code(tenant_id, content_code)`）。
-- =====================================================================================

-- dc_collection_point.uk_point_code : (point_code) -> (tenant_id, point_code)   [MES-LT-05]
--   前置检查: SHOW INDEX FROM `dc_collection_point`;
--   重复检查(必为空): SELECT tenant_id,point_code,COUNT(*) c FROM `dc_collection_point` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('dc_collection_point', 'uk_point_code', '`tenant_id`, `point_code`');

-- dc_data_record.uk_point_collect_time : (point_id, collect_time) -> (+ tenant_id 前置)
--   前置检查: SHOW INDEX FROM `dc_data_record`;
--   重复检查(必为空): SELECT tenant_id,point_id,collect_time,COUNT(*) c FROM `dc_data_record` GROUP BY 1,2,3 HAVING c>1;
--   大表提示：dc_data_record 数据量大且无清理策略（MES-DC-05），务必低峰执行。
CALL `_nexus_fix_uk_tenant`('dc_data_record', 'uk_point_collect_time', '`tenant_id`, `point_id`, `collect_time`');

-- eq_equipment.uk_equipment_code : (equipment_code) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `eq_equipment`;
--   重复检查(必为空): SELECT tenant_id,equipment_code,COUNT(*) c FROM `eq_equipment` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('eq_equipment', 'uk_equipment_code', '`tenant_id`, `equipment_code`');

-- eq_repair.uk_repair_no : (repair_no) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `eq_repair`;
--   重复检查(必为空): SELECT tenant_id,repair_no,COUNT(*) c FROM `eq_repair` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('eq_repair', 'uk_repair_no', '`tenant_id`, `repair_no`');

-- lt_lot.uk_lot_code : (lot_code) -> (+ tenant_id)   [MES-LT-05]
--   前置检查: SHOW INDEX FROM `lt_lot`;
--   重复检查(必为空): SELECT tenant_id,lot_code,COUNT(*) c FROM `lt_lot` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('lt_lot', 'uk_lot_code', '`tenant_id`, `lot_code`');

-- lt_sn_binding.uk_sn_material : (sn_id, material_id) -> (+ tenant_id)   [MES-LT-05 要求一并核对]
--   前置检查: SHOW INDEX FROM `lt_sn_binding`;
--   重复检查(必为空): SELECT tenant_id,sn_id,material_id,COUNT(*) c FROM `lt_sn_binding` GROUP BY 1,2,3 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('lt_sn_binding', 'uk_sn_material', '`tenant_id`, `sn_id`, `material_id`');

-- mes_label_template.uk_template_code : (template_code) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `mes_label_template`;
--   重复检查(必为空): SELECT tenant_id,template_code,COUNT(*) c FROM `mes_label_template` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('mes_label_template', 'uk_template_code', '`tenant_id`, `template_code`');

-- mes_mbom.uk_bom_id : (bom_id) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `mes_mbom`;
--   重复检查(必为空): SELECT tenant_id,bom_id,COUNT(*) c FROM `mes_mbom` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('mes_mbom', 'uk_bom_id', '`tenant_id`, `bom_id`');

-- mes_station_device.uk_device_code : (device_code) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `mes_station_device`;
--   重复检查(必为空): SELECT tenant_id,device_code,COUNT(*) c FROM `mes_station_device` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('mes_station_device', 'uk_device_code', '`tenant_id`, `device_code`');

-- ncr_defect_code.uk_code : (code) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `ncr_defect_code`;
--   重复检查(必为空): SELECT tenant_id,code,COUNT(*) c FROM `ncr_defect_code` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('ncr_defect_code', 'uk_code', '`tenant_id`, `code`');

-- ncr_order.uk_ncr_code : (ncr_code) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `ncr_order`;
--   重复检查(必为空): SELECT tenant_id,ncr_code,COUNT(*) c FROM `ncr_order` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('ncr_order', 'uk_ncr_code', '`tenant_id`, `ncr_code`');

-- ncr_order.uk_source : (source_type, source_id) -> (+ tenant_id 前置)   [MES-NCR-05 的 DDL 部分]
--   前置检查: SHOW INDEX FROM `ncr_order`;
--   重复检查(必为空): SELECT tenant_id,source_type,source_id,COUNT(*) c FROM `ncr_order` GROUP BY 1,2,3 HAVING c>1;
--   ⚠ MES-NCR-05 的根因是 source_id 可为 NULL（PROCESS 来源传 null），而 MySQL 唯一键对 NULL 不去重。
--     本 DDL **无法**单独修好该问题：若把 source_id 改为 NOT NULL DEFAULT 0，则所有 PROCESS 来源
--     的 NCR 会共用 source_id=0 而互相撞键（反而只能存在一张 PROCESS NCR）。
--     正确修法在应用侧：NcrOrderServiceImpl.autoCreateFromExit 的 PROCESS 分支传 `stepRecordId`
--     作为 source_id（见 MES-NCR-05 修复建议），本脚本只负责补 tenant_id 维度。
CALL `_nexus_fix_uk_tenant`('ncr_order', 'uk_source', '`tenant_id`, `source_type`, `source_id`');

-- ncr_rework_order.uk_rework_code : (rework_code) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `ncr_rework_order`;
--   重复检查(必为空): SELECT tenant_id,rework_code,COUNT(*) c FROM `ncr_rework_order` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('ncr_rework_order', 'uk_rework_code', '`tenant_id`, `rework_code`');

-- pro_andon.uk_andon_no : (andon_no) -> (+ tenant_id)   [MES-PRO-10]
--   前置检查: SHOW INDEX FROM `pro_andon`;
--   重复检查(必为空): SELECT tenant_id,andon_no,COUNT(*) c FROM `pro_andon` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('pro_andon', 'uk_andon_no', '`tenant_id`, `andon_no`');

-- pro_dispatch.uk_dispatch_no : (dispatch_no) -> (+ tenant_id)   [MES-PRO-10]
--   前置检查: SHOW INDEX FROM `pro_dispatch`;
--   重复检查(必为空): SELECT tenant_id,dispatch_no,COUNT(*) c FROM `pro_dispatch` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('pro_dispatch', 'uk_dispatch_no', '`tenant_id`, `dispatch_no`');

-- pro_flow_card.uk_card_no : (card_no) -> (+ tenant_id)   [MES-PRO-10]
--   前置检查: SHOW INDEX FROM `pro_flow_card`;
--   重复检查(必为空): SELECT tenant_id,card_no,COUNT(*) c FROM `pro_flow_card` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('pro_flow_card', 'uk_card_no', '`tenant_id`, `card_no`');

-- pro_flow_card.uk_barcode : (barcode) -> (+ tenant_id)   [MES-PRO-10]
--   前置检查: SHOW INDEX FROM `pro_flow_card`;
--   重复检查(必为空): SELECT tenant_id,barcode,COUNT(*) c FROM `pro_flow_card` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('pro_flow_card', 'uk_barcode', '`tenant_id`, `barcode`');

-- pro_sn.uk_sn_code : (sn_code) -> (+ tenant_id)   [MES-PRO-10]
--   前置检查: SHOW INDEX FROM `pro_sn`;
--   重复检查(必为空): SELECT tenant_id,sn_code,COUNT(*) c FROM `pro_sn` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('pro_sn', 'uk_sn_code', '`tenant_id`, `sn_code`');

-- pro_work_order.uk_wo_no : (wo_no) -> (+ tenant_id)   [MES-PRO-10]
--   前置检查: SHOW INDEX FROM `pro_work_order`;
--   重复检查(必为空): SELECT tenant_id,wo_no,COUNT(*) c FROM `pro_work_order` GROUP BY 1,2 HAVING c>1;
--   大表提示：pro_work_order 建议低峰执行。
CALL `_nexus_fix_uk_tenant`('pro_work_order', 'uk_wo_no', '`tenant_id`, `wo_no`');

-- qc_inspect_template.uk_template_code : (template_code) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `qc_inspect_template`;
--   重复检查(必为空): SELECT tenant_id,template_code,COUNT(*) c FROM `qc_inspect_template` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('qc_inspect_template', 'uk_template_code', '`tenant_id`, `template_code`');

-- qc_ipqc.uk_ipqc_no : (ipqc_no) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `qc_ipqc`;
--   重复检查(必为空): SELECT tenant_id,ipqc_no,COUNT(*) c FROM `qc_ipqc` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('qc_ipqc', 'uk_ipqc_no', '`tenant_id`, `ipqc_no`');

-- qc_iqc.uk_iqc_no : (iqc_no) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `qc_iqc`;
--   重复检查(必为空): SELECT tenant_id,iqc_no,COUNT(*) c FROM `qc_iqc` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('qc_iqc', 'uk_iqc_no', '`tenant_id`, `iqc_no`');

-- qc_oqc.uk_oqc_no : (oqc_no) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `qc_oqc`;
--   重复检查(必为空): SELECT tenant_id,oqc_no,COUNT(*) c FROM `qc_oqc` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('qc_oqc', 'uk_oqc_no', '`tenant_id`, `oqc_no`');

-- sop_document.uk_sop_code : (sop_code) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `sop_document`;
--   重复检查(必为空): SELECT tenant_id,sop_code,COUNT(*) c FROM `sop_document` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('sop_document', 'uk_sop_code', '`tenant_id`, `sop_code`');

-- tg_tooling.uk_tooling_code : (tooling_code) -> (+ tenant_id)
--   前置检查: SHOW INDEX FROM `tg_tooling`;
--   重复检查(必为空): SELECT tenant_id,tooling_code,COUNT(*) c FROM `tg_tooling` GROUP BY 1,2 HAVING c>1;
CALL `_nexus_fix_uk_tenant`('tg_tooling', 'uk_tooling_code', '`tenant_id`, `tooling_code`');


-- -------------------------------------------------------------------------------------
-- B-新增 1~3：QC 自动建单幂等唯一键   [MES-QC-04]
--   目的：IqcAutoCreateConsumer / QcIpqcServiceImpl:144 / OqcAutoCreateConsumer 目前是
--   “先 selectCount 再 insert”的 TOCTOU，MQ 重投或并发消费会建出重复检验单。
--   前置检查（必须先跑第 0.3~0.5 部分，有重复行先人工合并，否则 ADD UNIQUE 失败）。
--   ⚠ 三张表均有 del_flag，但当前代码**没有**对 iqc/ipqc/oqc 的删除入口
--     （仅 QcTemplateServiceImpl:105 有 deleteByIds），因此逻辑删除行占用唯一键的风险可忽略；
--     若后续新增删除功能，需同步改为“物理删除”或把 del_flag 纳入唯一键设计。
--   ⚠ receive_id / so_id / process_id 允许为 NULL，MySQL 唯一键对 NULL 不去重，
--     因此“无收货单的 IQC”“无销售订单的 OQC”“无工序的 IPQC”不受本约束保护：
--       - MES-QC-16：process_id 为 NULL 时 IPQC 幂等失效，建议应用层按 step_record_id 兜底；
--       - 如需 DB 兜底，可再追加 `uk_ipqc_wo_step(tenant_id, mes_wo_id, step_record_id)`（本批次未加）。
-- -------------------------------------------------------------------------------------
-- 前置检查:
--   SHOW INDEX FROM `qc_iqc`;
--   SELECT tenant_id, receive_id, material_id, COUNT(*) c, GROUP_CONCAT(iqc_id) ids
--     FROM `qc_iqc` GROUP BY tenant_id, receive_id, material_id HAVING c > 1;
CALL `_nexus_add_uk`('qc_iqc', 'uk_iqc_receive_material', '`tenant_id`, `receive_id`, `material_id`');

-- 前置检查:
--   SHOW INDEX FROM `qc_ipqc`;
--   SELECT tenant_id, mes_wo_id, process_id, COUNT(*) c, GROUP_CONCAT(ipqc_id) ids
--     FROM `qc_ipqc` GROUP BY tenant_id, mes_wo_id, process_id HAVING c > 1;
CALL `_nexus_add_uk`('qc_ipqc', 'uk_ipqc_wo_process', '`tenant_id`, `mes_wo_id`, `process_id`');

-- 前置检查:
--   SHOW INDEX FROM `qc_oqc`;
--   SELECT tenant_id, so_id, material_id, COUNT(*) c, GROUP_CONCAT(oqc_id) ids
--     FROM `qc_oqc` GROUP BY tenant_id, so_id, material_id HAVING c > 1;
CALL `_nexus_add_uk`('qc_oqc', 'uk_oqc_so_material', '`tenant_id`, `so_id`, `material_id`');


-- =====================================================================================
-- 第 C 部分：执行后核验（只读）
-- =====================================================================================

-- C.1 【ry-erp】确认下列索引的列定义均已含 tenant_id
-- SELECT TABLE_NAME, INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
--   FROM information_schema.STATISTICS
--  WHERE TABLE_SCHEMA = DATABASE() AND NON_UNIQUE = 0
--    AND TABLE_NAME IN ('bd_bom','bd_material','bd_material_vendor','bd_serial_number_rule',
--         'bd_unit_of_measure','bd_vendor','bd_workshop','bd_workstation','po_requisition',
--         'prd_material_requirement','prd_production_plan','prd_work_order','prd_work_report',
--         'pu_purchase_order','pu_purchase_requisition','pu_purchase_return','sch_team_member',
--         'so_price_list_line','wm_barcode','wm_barcode_config','wm_batch','wm_material_handover',
--         'wm_sales_issue','wm_sn','wm_warehouse','bd_client_credit','so_credit_usage')
--  GROUP BY TABLE_NAME, INDEX_NAME ORDER BY TABLE_NAME, INDEX_NAME;

-- C.2 【ry-mes】同上
-- SELECT TABLE_NAME, INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
--   FROM information_schema.STATISTICS
--  WHERE TABLE_SCHEMA = DATABASE() AND NON_UNIQUE = 0
--    AND TABLE_NAME IN ('dc_collection_point','dc_data_record','eq_equipment','eq_repair','lt_lot',
--         'lt_sn_binding','mes_label_template','mes_mbom','mes_station_device','ncr_defect_code',
--         'ncr_order','ncr_rework_order','pro_andon','pro_dispatch','pro_flow_card','pro_sn',
--         'pro_work_order','qc_inspect_template','qc_ipqc','qc_iqc','qc_oqc','sop_document','tg_tooling')
--  GROUP BY TABLE_NAME, INDEX_NAME ORDER BY TABLE_NAME, INDEX_NAME;

-- C.3 【ry-erp】确认 tenant_id 类型收敛结果
-- SELECT TABLE_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
--   FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'tenant_id'
--    AND TABLE_NAME IN ('pu_purchase_order','pu_purchase_order_line','pu_purchase_requisition',
--         'pu_purchase_requisition_line','pu_purchase_return','pu_purchase_return_line',
--         'wm_barcode','wm_barcode_config');

-- C.4 遗留统计：全库仍缺 tenant_id 的唯一键（应只剩 sys_/sj_/jimu_/onl_/act_/flw_/ai_/im_/wf_/mq_ 等全局表）
-- SELECT TABLE_NAME, INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
--   FROM information_schema.STATISTICS s
--  WHERE TABLE_SCHEMA = DATABASE() AND NON_UNIQUE = 0
--    AND INDEX_NAME <> 'PRIMARY'
--    AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS t
--                     WHERE t.TABLE_SCHEMA = s.TABLE_SCHEMA AND t.TABLE_NAME = s.TABLE_NAME
--                       AND t.INDEX_NAME = s.INDEX_NAME AND t.COLUMN_NAME = 'tenant_id')
--    AND EXISTS (SELECT 1 FROM information_schema.COLUMNS c
--                 WHERE c.TABLE_SCHEMA = s.TABLE_SCHEMA AND c.TABLE_NAME = s.TABLE_NAME
--                   AND c.COLUMN_NAME = 'tenant_id')
--  GROUP BY TABLE_NAME, INDEX_NAME ORDER BY TABLE_NAME, INDEX_NAME;


-- =====================================================================================
-- 第 D 部分：回滚
--   回滚方式：DROP 掉新索引 + 恢复原索引定义（原定义 = 各表节注释中 “->” 左侧的列清单）。
--   本脚本**不会**回滚 tenant_id 的类型收敛（varchar(20) -> varchar(64) 无业务意义，如确需回滚：
--     ALTER TABLE `pu_purchase_order` MODIFY COLUMN `tenant_id` varchar(64)
--       CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000';
--   ）。
--   必须在 **目标库**（ry-erp / ry-mes）执行，且需先执行第 0 部分创建 `_nexus_restore_uk`。
-- =====================================================================================

-- ---- ry-erp 回滚（取消注释后执行）----
-- CALL `_nexus_restore_uk`('bd_bom', 'uk_parent_version', '`parent_material_id`, `bom_version`');
-- CALL `_nexus_restore_uk`('bd_material', 'uk_material_code', '`material_code`');
-- CALL `_nexus_restore_uk`('bd_material_vendor', 'uk_material_supplier', '`material_id`, `vendor_id`');
-- CALL `_nexus_restore_uk`('bd_serial_number_rule', 'uk_rule_code', '`rule_code`');
-- CALL `_nexus_restore_uk`('bd_unit_of_measure', 'uk_uom_code', '`uom_code`');
-- CALL `_nexus_restore_uk`('bd_vendor', 'uk_vendor_code', '`vendor_code`');
-- CALL `_nexus_restore_uk`('bd_workshop', 'uk_workshop_code', '`workshop_code`');
-- CALL `_nexus_restore_uk`('bd_workstation', 'uk_workstation_code', '`workstation_code`');
-- CALL `_nexus_restore_uk`('po_requisition', 'uk_po_requisition_no', '`requisition_no`');
-- CALL `_nexus_restore_uk`('prd_material_requirement', 'uk_requirement_no', '`requirement_no`');
-- CALL `_nexus_restore_uk`('prd_production_plan', 'uk_plan_no', '`plan_no`');
-- CALL `_nexus_restore_uk`('prd_work_order', 'uk_wo_no', '`wo_no`');
-- CALL `_nexus_restore_uk`('prd_work_report', 'uk_report_no', '`report_no`');
-- CALL `_nexus_restore_uk`('pu_purchase_order', 'uk_order_no', '`order_no`');
-- CALL `_nexus_restore_uk`('pu_purchase_requisition', 'uk_requisition_no', '`requisition_no`');
-- CALL `_nexus_restore_uk`('pu_purchase_return', 'uk_return_no', '`return_no`');
-- CALL `_nexus_restore_uk`('sch_team_member', 'uk_team_worker', '`team_id`, `worker_id`');
-- CALL `_nexus_restore_uk`('so_price_list_line', 'uk_pl_line_tier', '`price_list_id`, `material_id`, `min_qty`');
-- CALL `_nexus_restore_uk`('wm_barcode', 'uk_barcode_code', '`barcode_code`');
-- CALL `_nexus_restore_uk`('wm_barcode_config', 'uk_barcode_config_code', '`config_code`');
-- CALL `_nexus_restore_uk`('wm_batch', 'uk_batch_code', '`batch_code`');
-- CALL `_nexus_restore_uk`('wm_material_handover', 'uk_handover_no', '`handover_no`');
-- CALL `_nexus_restore_uk`('wm_sales_issue', 'uk_issue_code', '`issue_code`');
-- CALL `_nexus_restore_uk`('wm_sn', 'uk_sn_code', '`sn_code`');
-- CALL `_nexus_restore_uk`('wm_warehouse', 'uk_warehouse_code', '`warehouse_code`');
-- -- 新增唯一键直接删除：
-- ALTER TABLE `bd_client_credit` DROP INDEX `uk_client_tenant`;
-- ALTER TABLE `so_credit_usage` DROP INDEX `uk_credit_so_status`;

-- ---- ry-mes 回滚（取消注释后执行）----
-- CALL `_nexus_restore_uk`('dc_collection_point', 'uk_point_code', '`point_code`');
-- CALL `_nexus_restore_uk`('dc_data_record', 'uk_point_collect_time', '`point_id`, `collect_time`');
-- CALL `_nexus_restore_uk`('eq_equipment', 'uk_equipment_code', '`equipment_code`');
-- CALL `_nexus_restore_uk`('eq_repair', 'uk_repair_no', '`repair_no`');
-- CALL `_nexus_restore_uk`('lt_lot', 'uk_lot_code', '`lot_code`');
-- CALL `_nexus_restore_uk`('lt_sn_binding', 'uk_sn_material', '`sn_id`, `material_id`');
-- CALL `_nexus_restore_uk`('mes_label_template', 'uk_template_code', '`template_code`');
-- CALL `_nexus_restore_uk`('mes_mbom', 'uk_bom_id', '`bom_id`');
-- CALL `_nexus_restore_uk`('mes_station_device', 'uk_device_code', '`device_code`');
-- CALL `_nexus_restore_uk`('ncr_defect_code', 'uk_code', '`code`');
-- CALL `_nexus_restore_uk`('ncr_order', 'uk_ncr_code', '`ncr_code`');
-- CALL `_nexus_restore_uk`('ncr_order', 'uk_source', '`source_type`, `source_id`');
-- CALL `_nexus_restore_uk`('ncr_rework_order', 'uk_rework_code', '`rework_code`');
-- CALL `_nexus_restore_uk`('pro_andon', 'uk_andon_no', '`andon_no`');
-- CALL `_nexus_restore_uk`('pro_dispatch', 'uk_dispatch_no', '`dispatch_no`');
-- CALL `_nexus_restore_uk`('pro_flow_card', 'uk_card_no', '`card_no`');
-- CALL `_nexus_restore_uk`('pro_flow_card', 'uk_barcode', '`barcode`');
-- CALL `_nexus_restore_uk`('pro_sn', 'uk_sn_code', '`sn_code`');
-- CALL `_nexus_restore_uk`('pro_work_order', 'uk_wo_no', '`wo_no`');
-- CALL `_nexus_restore_uk`('qc_inspect_template', 'uk_template_code', '`template_code`');
-- CALL `_nexus_restore_uk`('qc_ipqc', 'uk_ipqc_no', '`ipqc_no`');
-- CALL `_nexus_restore_uk`('qc_iqc', 'uk_iqc_no', '`iqc_no`');
-- CALL `_nexus_restore_uk`('qc_oqc', 'uk_oqc_no', '`oqc_no`');
-- CALL `_nexus_restore_uk`('sop_document', 'uk_sop_code', '`sop_code`');
-- CALL `_nexus_restore_uk`('tg_tooling', 'uk_tooling_code', '`tooling_code`');
-- -- 新增唯一键直接删除：
-- ALTER TABLE `qc_iqc` DROP INDEX `uk_iqc_receive_material`;
-- ALTER TABLE `qc_ipqc` DROP INDEX `uk_ipqc_wo_process`;
-- ALTER TABLE `qc_oqc` DROP INDEX `uk_oqc_so_material`;


-- =====================================================================================
-- 第 E 部分：清理辅助存储过程（两个库各执行一次；回滚前不要执行）
-- =====================================================================================
-- DROP PROCEDURE IF EXISTS `_nexus_fix_uk_tenant`;
-- DROP PROCEDURE IF EXISTS `_nexus_add_uk`;
-- DROP PROCEDURE IF EXISTS `_nexus_fix_tenant_varchar20`;
-- DROP PROCEDURE IF EXISTS `_nexus_restore_uk`;
