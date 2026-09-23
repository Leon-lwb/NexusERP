-- ============================================================================
-- 供应商字典口径对齐（bd_vendor）
-- 日期   : 2026-09-23
-- 规格   : doc/erp/specs/2026-09-23-bd-vendor-dict-align-impl-spec.md
-- 落库   : ry-vue（字典） + ry-erp（供应商数据）
-- DDL    : 仅 1 条列注释更新（bd_vendor.industry），无结构/类型/索引变更
-- 幂等   : 是（字典 INSERT IGNORE；数据 UPDATE 带 <=> 空值安全守卫，重跑 0 行）
-- 备份   : 执行前自动建立 ry-erp.bd_vendor_bak_20260923
-- 回滚   : 见文件末尾 §4
-- 注意   : 执行后必须清 Redis 字典缓存 000000:sys_dict
-- ============================================================================

-- ---------------------------------------------------------------------------
-- §0 备份（首次执行时灌数据；重跑因主键冲突被 INSERT IGNORE 跳过）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ry-erp`.`bd_vendor_bak_20260923` LIKE `ry-erp`.`bd_vendor`;
INSERT IGNORE INTO `ry-erp`.`bd_vendor_bak_20260923` SELECT * FROM `ry-erp`.`bd_vendor`;

-- ---------------------------------------------------------------------------
-- §1 新增字典：供应商供货品类 bd_vendor_industry
--    值复用 bd_material_category.category_code，使「按品类找供应商」可 join
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `ry-vue`.`sys_dict_type` VALUES
  (2075000000000000011, '000000', '供应商供货品类', 'bd_vendor_industry', 103, 1,
   '2026-09-23 09:00:00', NULL, NULL, '供应商供货品类，值对齐 bd_material_category.category_code');

INSERT IGNORE INTO `ry-vue`.`sys_dict_data` VALUES
  (2075000000000000132, '000000',  1, '金属材料',       'METAL',       'bd_vendor_industry', NULL, 'default', 'N', 103, 1, '2026-09-23 09:00:00', NULL, NULL, NULL),
  (2075000000000000133, '000000',  2, '塑料材料',       'PLASTIC',     'bd_vendor_industry', NULL, 'default', 'N', 103, 1, '2026-09-23 09:00:00', NULL, NULL, NULL),
  (2075000000000000134, '000000',  3, '橡胶与复合材料', 'RUBBER_COMP', 'bd_vendor_industry', NULL, 'default', 'N', 103, 1, '2026-09-23 09:00:00', NULL, NULL, NULL),
  (2075000000000000135, '000000',  4, '电子元器件',     'ELECTRONIC',  'bd_vendor_industry', NULL, 'default', 'N', 103, 1, '2026-09-23 09:00:00', NULL, NULL, NULL),
  (2075000000000000136, '000000',  5, '电池与电源',     'BATTERY',     'bd_vendor_industry', NULL, 'default', 'N', 103, 1, '2026-09-23 09:00:00', NULL, NULL, NULL),
  (2075000000000000137, '000000',  6, '五金件',         'HARDWARE',    'bd_vendor_industry', NULL, 'default', 'N', 103, 1, '2026-09-23 09:00:00', NULL, NULL, NULL),
  (2075000000000000138, '000000',  7, '包装材料',       'PACKAGING',   'bd_vendor_industry', NULL, 'default', 'N', 103, 1, '2026-09-23 09:00:00', NULL, NULL, NULL),
  (2075000000000000139, '000000',  8, '化工辅料',       'CHEMICAL',    'bd_vendor_industry', NULL, 'default', 'N', 103, 1, '2026-09-23 09:00:00', NULL, NULL, NULL),
  (2075000000000000140, '000000',  9, '铸锻件',         'SM_CAST',     'bd_vendor_industry', NULL, 'default', 'N', 103, 1, '2026-09-23 09:00:00', NULL, NULL, NULL),
  (2075000000000000141, '000000', 10, '其他',           'OTHER',       'bd_vendor_industry', NULL, 'info',    'Y', 103, 1, '2026-09-23 09:00:00', NULL, NULL, NULL);

-- ---------------------------------------------------------------------------
-- §2 列注释修正（原注释「行业」正是本次口径混乱的成因之一）
-- ---------------------------------------------------------------------------
ALTER TABLE `ry-erp`.`bd_vendor`
  MODIFY COLUMN `industry` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
  NULL DEFAULT NULL COMMENT '供货品类（字典 bd_vendor_industry，值对齐 bd_material_category.category_code）';

-- ---------------------------------------------------------------------------
-- §3 存量数据修复
--    逐行显式映射（不做模糊匹配），用临时表 + <=> 空值安全守卫保证幂等
--    临时表无法用库名限定，必须先选库（本脚本其余语句均显式限定库名，故不受影响）
-- ---------------------------------------------------------------------------
USE `ry-erp`;

DROP TEMPORARY TABLE IF EXISTS `_bd_vendor_map`;
CREATE TEMPORARY TABLE `_bd_vendor_map` (
  `tenant_id`            varchar(20) NOT NULL,
  `vendor_code`          varchar(64) NOT NULL,
  `industry`             varchar(64) NOT NULL,
  `region`               varchar(64) NOT NULL,
  `category`             varchar(64) NOT NULL,
  `status`               varchar(64) NOT NULL,
  `default_invoice_type` varchar(20) NOT NULL,
  `province_area_id`     bigint NULL,
  `city_area_id`         bigint NULL,
  `district_area_id`     bigint NULL,
  PRIMARY KEY (`tenant_id`, `vendor_code`)
) ENGINE = InnoDB;

INSERT INTO `_bd_vendor_map` VALUES
  ('000000', 'V00001', 'METAL',       '2', '1', 'ACTIVE', 'VAT_SPECIAL', 330000, 330100, 330110),
  ('000000', 'V00002', 'HARDWARE',    '2', '2', 'ACTIVE', 'VAT_SPECIAL', 330000, 330100, 330110),
  ('000000', 'V00003', 'PLASTIC',     '2', '1', 'ACTIVE', 'VAT_SPECIAL', 330000, 330100, 330110),
  ('000000', 'V00004', 'ELECTRONIC',  '2', '1', 'ACTIVE', 'VAT_SPECIAL', 330000, 330100, 330110),
  ('000000', 'V00005', 'ELECTRONIC',  '2', '1', 'ACTIVE', 'VAT_SPECIAL', 330000, 330100, 330110),
  ('000000', 'V00006', 'BATTERY',     '2', '1', 'ACTIVE', 'VAT_SPECIAL', 330000, 330100, 330110),
  ('000000', 'V00007', 'HARDWARE',    '2', '2', 'ACTIVE', 'VAT_SPECIAL', 330000, 330100, 330110),
  ('000000', 'V00008', 'CHEMICAL',    '2', '1', 'ACTIVE', 'VAT_SPECIAL', 330000, 330100, 330110),
  ('000000', 'V00009', 'RUBBER_COMP', '2', '1', 'ACTIVE', 'VAT_SPECIAL', 330000, 330100, 330110),
  ('000000', 'V00010', 'PACKAGING',   '2', '1', 'ACTIVE', 'VAT_SPECIAL', 330000, 330100, 330110),
  ('000000', 'V00011', 'METAL',       '2', '1', 'ACTIVE', 'VAT_SPECIAL', 330000, 330100, 330110),
  ('000000', 'V00012', 'SM_CAST',     '2', '1', 'ACTIVE', 'VAT_SPECIAL', 330000, 330100, 330110);

UPDATE `ry-erp`.`bd_vendor` v
  JOIN `_bd_vendor_map` m
    ON m.`vendor_code` = v.`vendor_code` AND m.`tenant_id` = v.`tenant_id`
   SET v.`industry`             = m.`industry`,
       v.`region`               = m.`region`,
       v.`category`             = m.`category`,
       v.`status`               = m.`status`,
       v.`default_invoice_type` = m.`default_invoice_type`,
       v.`province_area_id`     = m.`province_area_id`,
       v.`city_area_id`         = m.`city_area_id`,
       v.`district_area_id`     = m.`district_area_id`
 WHERE NOT (v.`industry`             <=> m.`industry`)
    OR NOT (v.`region`               <=> m.`region`)
    OR NOT (v.`category`             <=> m.`category`)
    OR NOT (v.`status`               <=> m.`status`)
    OR NOT (v.`default_invoice_type` <=> m.`default_invoice_type`)
    OR NOT (v.`province_area_id`     <=> m.`province_area_id`)
    OR NOT (v.`city_area_id`         <=> m.`city_area_id`)
    OR NOT (v.`district_area_id`     <=> m.`district_area_id`);

DROP TEMPORARY TABLE `_bd_vendor_map`;

-- ---------------------------------------------------------------------------
-- §3.1 自检：以下查询必须返回 0 行
-- ---------------------------------------------------------------------------
SELECT 'region'   AS field, v.vendor_id, v.region      AS bad_value FROM `ry-erp`.`bd_vendor` v LEFT JOIN `ry-vue`.`sys_dict_data` d ON d.dict_type='bd_region'          AND d.dict_value=v.region               WHERE d.dict_value IS NULL
UNION ALL
SELECT 'industry', v.vendor_id, v.industry             FROM `ry-erp`.`bd_vendor` v LEFT JOIN `ry-vue`.`sys_dict_data` d ON d.dict_type='bd_vendor_industry' AND d.dict_value=v.industry             WHERE d.dict_value IS NULL
UNION ALL
SELECT 'category', v.vendor_id, v.category             FROM `ry-erp`.`bd_vendor` v LEFT JOIN `ry-vue`.`sys_dict_data` d ON d.dict_type='bd_vendor_category' AND d.dict_value=v.category             WHERE d.dict_value IS NULL
UNION ALL
SELECT 'status',   v.vendor_id, v.status               FROM `ry-erp`.`bd_vendor` v LEFT JOIN `ry-vue`.`sys_dict_data` d ON d.dict_type='bd_vendor_status'   AND d.dict_value=v.status               WHERE d.dict_value IS NULL
UNION ALL
SELECT 'invoice',  v.vendor_id, v.default_invoice_type FROM `ry-erp`.`bd_vendor` v LEFT JOIN `ry-vue`.`sys_dict_data` d ON d.dict_type='pu_invoice_type'     AND d.dict_value=v.default_invoice_type WHERE d.dict_value IS NULL;

-- ============================================================================
-- §4 回滚（仅在需要撤销时手工执行）
-- ============================================================================
-- UPDATE `ry-erp`.`bd_vendor` v JOIN `ry-erp`.`bd_vendor_bak_20260923` b ON b.vendor_id = v.vendor_id
--    SET v.industry = b.industry, v.region = b.region, v.category = b.category,
--        v.status = b.status, v.default_invoice_type = b.default_invoice_type,
--        v.province_area_id = b.province_area_id, v.city_area_id = b.city_area_id,
--        v.district_area_id = b.district_area_id;
-- DELETE FROM `ry-vue`.`sys_dict_data` WHERE dict_type = 'bd_vendor_industry';
-- DELETE FROM `ry-vue`.`sys_dict_type` WHERE dict_type = 'bd_vendor_industry';
-- ALTER TABLE `ry-erp`.`bd_vendor` MODIFY COLUMN `industry` varchar(64)
--   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '行业';
-- -- 备份表确认无误后删除：DROP TABLE `ry-erp`.`bd_vendor_bak_20260923`;
