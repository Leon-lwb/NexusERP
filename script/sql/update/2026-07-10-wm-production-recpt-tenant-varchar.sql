-- =====================================================================
-- 修复 wm_production_recpt.tenant_id 列类型：bigint(20) -> varchar(20)
-- 日期：2026-07-10
-- 关联：doc/erp/plans/2026-07-10-mes-completion-linkage-fix.md
--
-- 根因：
--   wm_production_recpt.tenant_id 原为 bigint(20) NOT NULL，与标准多租户列
--   varchar(20) DEFAULT '000000' 不一致。租户拦截器(PlusTenantLineHandler)
--   插入字符串 '000000'，经 bigint 列落库为 0（非 '000000'）。
--   （wm_production_issue / wm_material_stock 已是 varchar，故未受影响。）
--
-- 修复：
--   1. 列类型改为 varchar(20) DEFAULT '000000'，与全局多租户列对齐。
--   2. 存量数据还原：bigint 期间落库的数值(0/1/...)用 LPAD 还原为 6 位租户编码字符串。
--      LPAD('0',6,'0')='000000'、LPAD('1',6,'0')='000001'，多租户数据可正确还原。
-- =====================================================================

ALTER TABLE `wm_production_recpt`
    MODIFY COLUMN `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '000000' COMMENT '租户ID';

-- 存量数据修正：bigint 期间落库的数值还原为 6 位租户编码字符串
UPDATE `wm_production_recpt` SET `tenant_id` = LPAD(`tenant_id`, 6, '0') WHERE `tenant_id` IS NOT NULL;
UPDATE `wm_production_recpt` SET `tenant_id` = '000000' WHERE `tenant_id` IS NULL;
