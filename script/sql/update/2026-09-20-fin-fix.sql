-- =============================================================================
-- NexusERP 增量脚本: fin 财务域修复(2026-09-20, B-fin 批次)
--
-- 用途:
--   ① 补 `fin:arap:settlement:unsettle` 按钮菜单种子(FIN-ARAP-02 新增反核销端点, 规范 §2.7)
--   ② FIN-FA-01 存量数据修复: 重算 fa_asset.net_book_value(修复前每计提一期多扣一个本期折旧额)
--
-- 影响库: ① ry-vue(sys_menu)  ② ry-erp(fa_asset)
-- 执行前提:
--   - 已应用主脚本 script/sql/ry-vue.sql 与 script/sql/ry-erp.sql;
--   - 父菜单 6122(核销管理, perms=fin:arap:settlement:list)已存在(主脚本 92747 行);
--   - ② 段仅针对固定资产折旧口径, 与 MES/IoT 无关。
-- 是否需清 Redis 缓存: 需要 —— 菜单/权限变更后清理系统菜单缓存与在线用户权限缓存
--   (或在「系统监控-缓存监控」清 `sys_menu` 相关键), 相关用户重新登录后按钮权限才生效。
-- 可重复执行: 是(① 按 perms 判存在 + INSERT IGNORE;② 为重算式 UPDATE, 重复执行结果一致)
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. FIN-ARAP-02: 反核销按钮权限种子(父菜单 6122 = 核销管理)
--    幂等: NOT EXISTS 按 perms 判重; 固定 menu_id 20850 避免与其它增量脚本撞号,
--    INSERT IGNORE 兜底(若 20850 已被占用则跳过, 此时请人工改号后重跑)。
--    MySQL 8 允许 INSERT ... SELECT 自引用目标表(内部物化临时表);
--    若上游 MySQL 版本报 1093, 请改用下面注释掉的固定行 INSERT IGNORE 写法。
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`,
                               `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`,
                               `perms`, `icon`, `create_dept`, `create_by`, `create_time`,
                               `update_by`, `update_time`, `remark`)
SELECT 20850, '反核销', 6122, 3, '', '', NULL, 1, 0, 'F', '0', '0',
       'fin:arap:settlement:unsettle', '#', 103, 1, NOW(), NULL, NULL, 'FinSettlementController.unsettle'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`perms` = 'fin:arap:settlement:unsettle');

-- 备选写法(上面语句被 1093 拒绝时使用, 同样是幂等的):
-- INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`,
--                                `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`,
--                                `perms`, `icon`, `create_dept`, `create_by`, `create_time`,
--                                `update_by`, `update_time`, `remark`)
-- VALUES (20850, '反核销', 6122, 3, '', '', NULL, 1, 0, 'F', '0', '0',
--         'fin:arap:settlement:unsettle', '#', 103, 1, NOW(), NULL, NULL, 'FinSettlementController.unsettle');


-- -----------------------------------------------------------------------------
-- 2. FIN-FA-01 存量数据修复: 重算 fa_asset.net_book_value
--
--    缺陷: 修复前折旧回写语句在同一条 UPDATE 的 SET 中先执行
--          `cumulative_depreciation = cumulative_depreciation + X`, 随后
--          `net_book_value = original_value - cumulative_depreciation - X`。
--          MySQL 单表 UPDATE 的 SET 从左到右求值, 第二个表达式读到的是刚写入的新值,
--          故实际净值 = original_value - cum_old - 2X —— 每计提一期净值多扣一个 X,
--          误差逐月累积(原值 10000 / 每期 1000 的资产, 第 3 期后净值 4000 而非 7000)。
--
--    修复口径: net_book_value = original_value - cumulative_depreciation(唯一正确口径)。
--    排除已处置资产: FaDisposalServiceImpl 处置时有意把 net_book_value 置 0, 不参与重算。
--    影响行数: 见 2.1 前置检查输出(mismatch_rows)。
-- -----------------------------------------------------------------------------

-- 2.1 前置检查: 按资产状态统计与正确口径不一致的行数 / 差额合计(差额 ≈ 累计折旧, 可佐证缺陷)
SELECT a.`status`                                                                    AS `status`,
       COUNT(*)                                                                      AS `mismatch_rows`,
       SUM(a.`original_value` - a.`cumulative_depreciation` - a.`net_book_value`)     AS `total_missing_amount`,
       MIN(a.`net_book_value`)                                                       AS `min_net_book_value`,
       MAX(a.`net_book_value`)                                                       AS `max_net_book_value`
FROM `fa_asset` a
WHERE a.`del_flag` = '0'
  AND a.`status` <> 'DISPOSED'
  AND a.`net_book_value` <> a.`original_value` - a.`cumulative_depreciation`
GROUP BY a.`status`;

-- 2.2 执行重算(WHERE 与 2.1 完全一致; 只影响不一致的行, 可重复执行)
UPDATE `fa_asset`
SET `net_book_value` = `original_value` - `cumulative_depreciation`
WHERE `del_flag` = '0'
  AND `status` <> 'DISPOSED'
  AND `net_book_value` <> `original_value` - `cumulative_depreciation`;

-- 2.3 执行后核对(应返回 0)
SELECT COUNT(*) AS `still_mismatch`
FROM `fa_asset`
WHERE `del_flag` = '0'
  AND `status` <> 'DISPOSED'
  AND `net_book_value` <> `original_value` - `cumulative_depreciation`;


-- -----------------------------------------------------------------------------
-- 3. FIN-GL-01 存量复核提示(无 DDL, 仅人工核对清单)
--    外币重估差额符号修复前, 贷方余额的外币科目会生成方向相反、金额约 2 倍的重估凭证,
--    且重估幂等键(periodId*100+2)使同期间重复调用复用旧凭证, 不会自动纠正。
--    请对以下已过账的 ADJUSTMENT 重估凭证人工复核并按需红冲重做:
-- -----------------------------------------------------------------------------
-- SELECT v.`voucher_id`, v.`voucher_no`, v.`voucher_date`, v.`source_no`, v.`total_debit`, v.`total_credit`
-- FROM `gl_voucher` v
-- WHERE v.`voucher_source` = 'ADJUSTMENT'
--   AND v.`del_flag` = '0'
--   AND EXISTS (SELECT 1 FROM `gl_voucher_line` l
--               WHERE l.`voucher_id` = v.`voucher_id`
--                 AND l.`currency` IS NOT NULL AND l.`currency` <> 'CNY')
-- ORDER BY v.`voucher_date` DESC;
