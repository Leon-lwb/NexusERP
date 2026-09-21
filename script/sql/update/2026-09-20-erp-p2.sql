-- =====================================================================================
-- NexusERP ERP 收尾 P2 批次 增量脚本
-- 文件: script/sql/update/2026-09-20-erp-p2.sql
-- 用途: ERP-PRD-01 无 DDL; 本脚本承载 ERP-DASH-01(权限码种子+索引)、
--       ERP-SCH-11(申请单号唯一键)、ERP-SCH-09(打卡唯一键已存在, 无需 DDL)、
--       ERP-CV-05(打卡定位精度列)、ERP-SO-05(价目表逻辑删除列)、ERP-SO-07(独立权限码种子)
-- 执行前提: MySQL 8.0; 应用已升级到本次代码版本(实体已加 @TableLogic del_flag 的 so_price_list*
--           必须在应用启动前执行 §5, 否则查询会因缺列报 Unknown column 'del_flag')
-- 影响库:   ry-vue(sys_menu 种子) / ry-erp(业务表结构)
-- Redis:    需要清理字典/菜单缓存(菜单权限走 DB 查询, 但前端菜单缓存建议刷新; 无需清 Redis 键)
-- 幂等性:   全部语句幂等(先查 information_schema 再 ALTER / INSERT IGNORE / 加索引前查 STATISTICS)
--           唯一键部分见 §2 前置检查, 存量重复数据会导致加键失败
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- §1 ERP-CV-05: sch_attendance 增加打卡定位精度列(前端已上报 accuracy, 原后端无列直接丢弃)
-- -------------------------------------------------------------------------------------
SET @db := DATABASE();

SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `sch_attendance` ADD COLUMN `clock_in_accuracy` decimal(10, 2) NULL COMMENT ''上班打卡定位精度(米, 客户端上报; ERP-CV-05)'' AFTER `clock_in_address`',
  'DO 0') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sch_attendance' AND COLUMN_NAME = 'clock_in_accuracy');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `sch_attendance` ADD COLUMN `clock_out_accuracy` decimal(10, 2) NULL COMMENT ''下班打卡定位精度(米, 客户端上报; ERP-CV-05)'' AFTER `clock_out_address`',
  'DO 0') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sch_attendance' AND COLUMN_NAME = 'clock_out_accuracy');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- -------------------------------------------------------------------------------------
-- §2 ERP-SCH-11: 申请单号唯一键(单号为"前缀+秒级时间", 同秒并发会重号)
-- 前置检查(必须先执行, 有结果说明存量重复, 需人工去重后再加键):
--   SELECT tenant_id, apply_no, COUNT(*) FROM sch_supplement_apply
--    GROUP BY tenant_id, apply_no HAVING COUNT(*) > 1;
--   SELECT tenant_id, leave_no, COUNT(*) FROM sch_leave_apply
--    GROUP BY tenant_id, leave_no HAVING COUNT(*) > 1;
-- 去重建议(保留 apply_id/leave_id 最大的一条, 其余把单号追加 -DUP<id> 后再人工核对):
--   UPDATE sch_supplement_apply a JOIN (SELECT tenant_id, apply_no, MAX(apply_id) keep_id, COUNT(*) c
--     FROM sch_supplement_apply GROUP BY tenant_id, apply_no HAVING c > 1) d
--     ON a.tenant_id = d.tenant_id AND a.apply_no = d.apply_no AND a.apply_id <> d.keep_id
--    SET a.apply_no = CONCAT(a.apply_no, '-DUP', a.apply_id);
-- -------------------------------------------------------------------------------------
SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `sch_supplement_apply` ADD UNIQUE KEY `uk_tenant_apply_no` (`tenant_id`, `apply_no`)',
  'DO 0') FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sch_supplement_apply' AND INDEX_NAME = 'uk_tenant_apply_no');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `sch_leave_apply` ADD UNIQUE KEY `uk_tenant_leave_no` (`tenant_id`, `leave_no`)',
  'DO 0') FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sch_leave_apply' AND INDEX_NAME = 'uk_tenant_leave_no');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- -------------------------------------------------------------------------------------
-- §3 权限码种子(ERP-DASH-01 / ERP-SO-07)
--   erp:dashboard:stats      首页工作台统计(原端点无权限码, 任意登录用户可见全租户销售额)
--   so:return:confirm       销售退货确认(原复用 so:return:edit)
--   so:return:execute       销售退货执行出库(原复用 so:return:edit)
--   so:quotation:confirm    销售报价确认(原复用 so:quotation:edit)
--   so:quotation:convert    销售报价转单(原复用 so:quotation:edit)
--   so:contract:terminate   销售合同终止(原复用 so:contract:edit)
-- 父菜单按 perms 反查, 找不到父级则该条不插入(不产生悬挂按钮), 需人工确认父菜单后再执行。
-- menu_id 取 20950~20955 段(避开其他批次已占用的 20828~20850)。
-- -------------------------------------------------------------------------------------
INSERT IGNORE INTO `ry-vue`.`sys_menu`
  (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`,
   `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
   `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 20950, '首页统计', 0, 20, '', '', NULL, 1, 0, 'F', '0', '0', 'erp:dashboard:stats', '#',
       103, 1, NOW(), NULL, NULL, 'ErpDashboardController.getStats (ERP-DASH-01)'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT 1) t WHERE EXISTS (
  SELECT 1 FROM `ry-vue`.`sys_menu` WHERE `perms` = 'erp:dashboard:stats'));

INSERT IGNORE INTO `ry-vue`.`sys_menu`
  (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`,
   `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
   `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 20951, '退货确认', m.`menu_id`, 10, '', '', NULL, 1, 0, 'F', '0', '0', 'so:return:confirm', '#',
       103, 1, NOW(), NULL, NULL, 'SoReturnController.confirm (ERP-SO-07)'
FROM `ry-vue`.`sys_menu` m WHERE m.`perms` = 'so:return:list' LIMIT 1;

INSERT IGNORE INTO `ry-vue`.`sys_menu`
  (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`,
   `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
   `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 20952, '退货执行', m.`menu_id`, 11, '', '', NULL, 1, 0, 'F', '0', '0', 'so:return:execute', '#',
       103, 1, NOW(), NULL, NULL, 'SoReturnController.execute (ERP-SO-07)'
FROM `ry-vue`.`sys_menu` m WHERE m.`perms` = 'so:return:list' LIMIT 1;

INSERT IGNORE INTO `ry-vue`.`sys_menu`
  (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`,
   `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
   `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 20953, '报价确认', m.`menu_id`, 10, '', '', NULL, 1, 0, 'F', '0', '0', 'so:quotation:confirm', '#',
       103, 1, NOW(), NULL, NULL, 'SoQuotationController.confirm (ERP-SO-07)'
FROM `ry-vue`.`sys_menu` m WHERE m.`perms` = 'so:quotation:list' LIMIT 1;

INSERT IGNORE INTO `ry-vue`.`sys_menu`
  (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`,
   `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
   `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 20954, '报价转单', m.`menu_id`, 11, '', '', NULL, 1, 0, 'F', '0', '0', 'so:quotation:convert', '#',
       103, 1, NOW(), NULL, NULL, 'SoQuotationController.convertToOrder (ERP-SO-07)'
FROM `ry-vue`.`sys_menu` m WHERE m.`perms` = 'so:quotation:list' LIMIT 1;

INSERT IGNORE INTO `ry-vue`.`sys_menu`
  (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`,
   `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
   `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 20955, '合同终止', m.`menu_id`, 12, '', '', NULL, 1, 0, 'F', '0', '0', 'so:contract:terminate', '#',
       103, 1, NOW(), NULL, NULL, 'SoContractController.terminate (ERP-SO-07)'
FROM `ry-vue`.`sys_menu` m WHERE m.`perms` = 'so:contract:list' LIMIT 1;

-- -------------------------------------------------------------------------------------
-- §4 ERP-DASH-01: 首页聚合查询索引(收敛全表扫描)
--   wm_stock_alert(tenant_id, handled_flag)      → 未处理预警 count/前5
--   so_sales_order_header(tenant_id, create_time)→ 本月销售额/12 个月趋势
-- 前置检查: 大表加索引会锁表, 请在维护窗口执行; 确认无同名索引(脚本已判断)
-- -------------------------------------------------------------------------------------
SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `wm_stock_alert` ADD INDEX `idx_tenant_handled` (`tenant_id`, `handled_flag`)',
  'DO 0') FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'wm_stock_alert' AND INDEX_NAME = 'idx_tenant_handled');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `so_sales_order_header` ADD INDEX `idx_tenant_create_time` (`tenant_id`, `create_time`)',
  'DO 0') FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'so_sales_order_header' AND INDEX_NAME = 'idx_tenant_create_time');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- -------------------------------------------------------------------------------------
-- §5 ERP-SO-05: so_price_list / so_price_list_line 增加 del_flag(物理删除 → 逻辑删除)
-- 前置检查: 两表当前均无 del_flag 列(脚本已判断); 存量数据默认 '0' 即未删除, 无需回填
-- 注意: 实体已加 @TableLogic, 应用启动后所有查询都会带 del_flag='0', 必须先执行本段
-- -------------------------------------------------------------------------------------
SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `so_price_list` ADD COLUMN `del_flag` char(1) NOT NULL DEFAULT ''0'' COMMENT ''删除标志(0存在 2删除; ERP-SO-05 逻辑删除)'' AFTER `status`',
  'DO 0') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'so_price_list' AND COLUMN_NAME = 'del_flag');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `so_price_list_line` ADD COLUMN `del_flag` char(1) NOT NULL DEFAULT ''0'' COMMENT ''删除标志(0存在 2删除; ERP-SO-05 逻辑删除)'' AFTER `discount_rate`',
  'DO 0') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'so_price_list_line' AND COLUMN_NAME = 'del_flag');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- -------------------------------------------------------------------------------------
-- §6 回滚(仅结构, 数据不可逆部分已标注)
-- -------------------------------------------------------------------------------------
-- ALTER TABLE `sch_attendance` DROP COLUMN `clock_in_accuracy`;
-- ALTER TABLE `sch_attendance` DROP COLUMN `clock_out_accuracy`;
-- ALTER TABLE `sch_supplement_apply` DROP INDEX `uk_tenant_apply_no`;
-- ALTER TABLE `sch_leave_apply` DROP INDEX `uk_tenant_leave_no`;
-- ALTER TABLE `wm_stock_alert` DROP INDEX `idx_tenant_handled`;
-- ALTER TABLE `so_sales_order_header` DROP INDEX `idx_tenant_create_time`;
-- ALTER TABLE `so_price_list` DROP COLUMN `del_flag`;
-- ALTER TABLE `so_price_list_line` DROP COLUMN `del_flag`;
-- DELETE FROM `ry-vue`.`sys_menu` WHERE `menu_id` IN (20950, 20951, 20952, 20953, 20954, 20955);
-- 回滚注意: 执行 §5 后若已有逻辑删除数据(del_flag='2'), 回滚会使其重新可见, 需人工确认。
