-- =====================================================================
-- Nexus-ERP tenant_id 列类型统一修复脚本 (bigint/varbinary -> varchar(20))
-- 日期：2026-07-10
-- 关联规范：doc/system/specs/charset-encoding-standard.md §5
-- 关联记忆：tenant-id-bigint-schema-trap（本脚本为该陷阱的批量修复，含此前
--           仅写未执行的 2026-07-10-wm-production-recpt-tenant-varchar.sql）
--
-- 根因：
--   多租户拦截器 PlusTenantLineHandler.getTenantId() 返回 StringValue("000000")，
--   插入 bigint 列时 MySQL 截断为 0；varbinary 列存为字节。落库值 0 与标准
--   varchar '000000' 不一致，跨表 JOIN tenant_id 会失败，多租户下非默认租户脆弱。
--
-- 修复范围（20 张表，均为应用 TenantEntity 管理的表）：
--   ry-erp (14): po_requisition, po_requisition_detail, prd_capacity_calendar,
--                prd_rework_order, prd_subcontract, pu_vendor_evaluation,
--                so_credit_usage, so_price_list, so_price_list_line,
--                wm_picking_rule, wm_production_recpt, wm_putaway_rule,
--                wm_stock_alert, wm_stock_reconciliation
--   ry-vue  (6): ai_ab_test, ai_ab_test_result, ai_prompt_template,
--                ai_sql_example, ai_token_usage, wf_nform(varbinary)
--
-- 不在本次范围（第三方模块自管，勿动）：
--   ry-vue: jimu_report_icon_lib, onl_drag_dataset_head, onl_drag_page,
--           onl_drag_table_relation —— 积木报表/在线低代码第三方表，无应用
--           Java 实体，不经 PlusTenantLineHandler，int 类型由模块自身约定。
--
-- 修复方式：
--   1) ALTER TABLE ... MODIFY tenant_id varchar(20) DEFAULT '000000'
--      （bigint 0 -> 字符串 '0'；varbinary 0x30 -> '0'、0x303030303030 -> '000000'）
--   2) UPDATE ... SET tenant_id=LPAD(tenant_id,6,'0')  归一为 6 位租户编码
--      LPAD('0',6,'0')='000000'、LPAD('000000',6,'0')='000000'，多租户安全。
--   3) UPDATE ... SET tenant_id='000000' WHERE tenant_id IS NULL
--
-- 数据安全：执行前已核查 20 表 tenant_id 实际取值均为 0 / 000000（默认租户），
--   LPAD 归一无数据丢失风险。执行前请备份。
-- =====================================================================

USE `ry-erp`;

-- ---------- ry-erp: 14 张 bigint ----------
ALTER TABLE `po_requisition`          MODIFY COLUMN `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID';
UPDATE `po_requisition`          SET `tenant_id` = LPAD(`tenant_id`, 6, '0') WHERE `tenant_id` IS NOT NULL;
UPDATE `po_requisition`          SET `tenant_id` = '000000' WHERE `tenant_id` IS NULL;

ALTER TABLE `po_requisition_detail`   MODIFY COLUMN `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID';
UPDATE `po_requisition_detail`   SET `tenant_id` = LPAD(`tenant_id`, 6, '0') WHERE `tenant_id` IS NOT NULL;
UPDATE `po_requisition_detail`   SET `tenant_id` = '000000' WHERE `tenant_id` IS NULL;

ALTER TABLE `prd_capacity_calendar`   MODIFY COLUMN `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID';
UPDATE `prd_capacity_calendar`   SET `tenant_id` = LPAD(`tenant_id`, 6, '0') WHERE `tenant_id` IS NOT NULL;
UPDATE `prd_capacity_calendar`   SET `tenant_id` = '000000' WHERE `tenant_id` IS NULL;

ALTER TABLE `prd_rework_order`        MODIFY COLUMN `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID';
UPDATE `prd_rework_order`        SET `tenant_id` = LPAD(`tenant_id`, 6, '0') WHERE `tenant_id` IS NOT NULL;
UPDATE `prd_rework_order`        SET `tenant_id` = '000000' WHERE `tenant_id` IS NULL;

ALTER TABLE `prd_subcontract`         MODIFY COLUMN `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID';
UPDATE `prd_subcontract`         SET `tenant_id` = LPAD(`tenant_id`, 6, '0') WHERE `tenant_id` IS NOT NULL;
UPDATE `prd_subcontract`         SET `tenant_id` = '000000' WHERE `tenant_id` IS NULL;

ALTER TABLE `pu_vendor_evaluation`    MODIFY COLUMN `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID';
UPDATE `pu_vendor_evaluation`    SET `tenant_id` = LPAD(`tenant_id`, 6, '0') WHERE `tenant_id` IS NOT NULL;
UPDATE `pu_vendor_evaluation`    SET `tenant_id` = '000000' WHERE `tenant_id` IS NULL;

ALTER TABLE `so_credit_usage`         MODIFY COLUMN `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID';
UPDATE `so_credit_usage`         SET `tenant_id` = LPAD(`tenant_id`, 6, '0') WHERE `tenant_id` IS NOT NULL;
UPDATE `so_credit_usage`         SET `tenant_id` = '000000' WHERE `tenant_id` IS NULL;

ALTER TABLE `so_price_list`           MODIFY COLUMN `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID';
UPDATE `so_price_list`           SET `tenant_id` = LPAD(`tenant_id`, 6, '0') WHERE `tenant_id` IS NOT NULL;
UPDATE `so_price_list`           SET `tenant_id` = '000000' WHERE `tenant_id` IS NULL;

ALTER TABLE `so_price_list_line`      MODIFY COLUMN `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID';
UPDATE `so_price_list_line`      SET `tenant_id` = LPAD(`tenant_id`, 6, '0') WHERE `tenant_id` IS NOT NULL;
UPDATE `so_price_list_line`      SET `tenant_id` = '000000' WHERE `tenant_id` IS NULL;

ALTER TABLE `wm_picking_rule`         MODIFY COLUMN `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID';
UPDATE `wm_picking_rule`         SET `tenant_id` = LPAD(`tenant_id`, 6, '0') WHERE `tenant_id` IS NOT NULL;
UPDATE `wm_picking_rule`         SET `tenant_id` = '000000' WHERE `tenant_id` IS NULL;

ALTER TABLE `wm_production_recpt`     MODIFY COLUMN `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID';
UPDATE `wm_production_recpt`     SET `tenant_id` = LPAD(`tenant_id`, 6, '0') WHERE `tenant_id` IS NOT NULL;
UPDATE `wm_production_recpt`     SET `tenant_id` = '000000' WHERE `tenant_id` IS NULL;

ALTER TABLE `wm_putaway_rule`         MODIFY COLUMN `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID';
UPDATE `wm_putaway_rule`         SET `tenant_id` = LPAD(`tenant_id`, 6, '0') WHERE `tenant_id` IS NOT NULL;
UPDATE `wm_putaway_rule`         SET `tenant_id` = '000000' WHERE `tenant_id` IS NULL;

ALTER TABLE `wm_stock_alert`          MODIFY COLUMN `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID';
UPDATE `wm_stock_alert`          SET `tenant_id` = LPAD(`tenant_id`, 6, '0') WHERE `tenant_id` IS NOT NULL;
UPDATE `wm_stock_alert`          SET `tenant_id` = '000000' WHERE `tenant_id` IS NULL;

ALTER TABLE `wm_stock_reconciliation` MODIFY COLUMN `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID';
UPDATE `wm_stock_reconciliation` SET `tenant_id` = LPAD(`tenant_id`, 6, '0') WHERE `tenant_id` IS NOT NULL;
UPDATE `wm_stock_reconciliation` SET `tenant_id` = '000000' WHERE `tenant_id` IS NULL;


USE `ry-vue`;

-- ---------- ry-vue: 5 张 bigint (ai_*) ----------
ALTER TABLE `ai_ab_test`          MODIFY COLUMN `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID';
UPDATE `ai_ab_test`          SET `tenant_id` = LPAD(`tenant_id`, 6, '0') WHERE `tenant_id` IS NOT NULL;
UPDATE `ai_ab_test`          SET `tenant_id` = '000000' WHERE `tenant_id` IS NULL;

ALTER TABLE `ai_ab_test_result`   MODIFY COLUMN `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID';
UPDATE `ai_ab_test_result`   SET `tenant_id` = LPAD(`tenant_id`, 6, '0') WHERE `tenant_id` IS NOT NULL;
UPDATE `ai_ab_test_result`   SET `tenant_id` = '000000' WHERE `tenant_id` IS NULL;

ALTER TABLE `ai_prompt_template`  MODIFY COLUMN `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID';
UPDATE `ai_prompt_template`  SET `tenant_id` = LPAD(`tenant_id`, 6, '0') WHERE `tenant_id` IS NOT NULL;
UPDATE `ai_prompt_template`  SET `tenant_id` = '000000' WHERE `tenant_id` IS NULL;

ALTER TABLE `ai_sql_example`      MODIFY COLUMN `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID';
UPDATE `ai_sql_example`      SET `tenant_id` = LPAD(`tenant_id`, 6, '0') WHERE `tenant_id` IS NOT NULL;
UPDATE `ai_sql_example`      SET `tenant_id` = '000000' WHERE `tenant_id` IS NULL;

ALTER TABLE `ai_token_usage`      MODIFY COLUMN `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID';
UPDATE `ai_token_usage`      SET `tenant_id` = LPAD(`tenant_id`, 6, '0') WHERE `tenant_id` IS NOT NULL;
UPDATE `ai_token_usage`      SET `tenant_id` = '000000' WHERE `tenant_id` IS NULL;

-- ---------- ry-vue: 1 张 varbinary (wf_nform) ----------
-- varbinary 0x30('0') / 0x303030303030('000000') 转 varchar 后 LPAD 归一为 '000000'
ALTER TABLE `wf_nform`           MODIFY COLUMN `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID';
UPDATE `wf_nform`           SET `tenant_id` = LPAD(`tenant_id`, 6, '0') WHERE `tenant_id` IS NOT NULL;
UPDATE `wf_nform`           SET `tenant_id` = '000000' WHERE `tenant_id` IS NULL;
