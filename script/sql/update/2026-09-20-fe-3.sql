-- =====================================================================================
-- NexusERP 增量脚本：FE-3 批次（前端收尾）配套种子
--
-- 用途：
--   ① 新增字典 `mes_tooling_calibration_result`（工装台账「校准结果」列 + 「登记校准」
--      下拉依赖；MES-TG-01 校准结论 PASS/FAIL/ADJUSTED）。
--   ② 兜底播种工装校准登记按钮权限 `mes:tg:tooling:calibrate`（menu 4196）并派生授权。
--      与 `2026-09-20-tg-sop.sql` §6.2/6.3 内容等价，二者任一生效即可（INSERT IGNORE）。
--
-- 执行前提：
--   `2026-09-20-tg-sop.sql` §1 必须先执行（tg_tooling 的校准列、sop_version.file_hash、
--   以及 `mes_tooling_status` 字典的 CALIBRATING 项），否则工装台账页查询会报 Unknown column。
--
-- 影响库：ry-vue（仅 DML，无 DDL）
-- 是否需清 Redis 缓存：**是**（菜单/字典缓存）。执行后重新登录，或
--   `DEL sys_dict:mes_tooling_calibration_result` / 重启应用以刷新 `sys_menu` 路由缓存。
--
-- 幂等性：本文件全部为 INSERT IGNORE / WHERE NOT EXISTS 形式，**可重复执行**。
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- ① 字典类型：工装校准结果
-- -------------------------------------------------------------------------------------
INSERT IGNORE INTO `sys_dict_type`
(`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES
(2075000000000000010, '000000', '工装校准结果', 'mes_tooling_calibration_result', 103, 1, NOW(), NULL, NULL, 'MES-TG-01 工装校准结论(PASS/FAIL/ADJUSTED)');

-- -------------------------------------------------------------------------------------
-- ② 字典数据：PASS / FAIL / ADJUSTED（与 TgToolingServiceImpl 的 CALIBRATION_* 常量一致）
--    FAIL 语义：本次校准未通过，服务端把下次校准日置为本次日期 → 工装立即校准超期不可用
-- -------------------------------------------------------------------------------------
INSERT IGNORE INTO `sys_dict_data`
(`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES
(2075000000000000011, '000000', 1, '合格', 'PASS', 'mes_tooling_calibration_result', NULL, 'success', 'Y', 103, 1, NOW(), NULL, NULL, 'MES-TG-01 校准通过(默认)'),
(2075000000000000012, '000000', 2, '不合格', 'FAIL', 'mes_tooling_calibration_result', NULL, 'danger', 'N', 103, 1, NOW(), NULL, NULL, 'MES-TG-01 校准未通过: 下次校准日置为本次日期, 立即超期不可用'),
(2075000000000000013, '000000', 3, '调整后合格', 'ADJUSTED', 'mes_tooling_calibration_result', NULL, 'warning', 'N', 103, 1, NOW(), NULL, NULL, 'MES-TG-01 调整后合格');

-- -------------------------------------------------------------------------------------
-- ③ 按钮权限：工装校准登记（menu 4196，父菜单 4172 工装台账）
--    与 2026-09-20-tg-sop.sql §6.2 等价；若该脚本已执行，本行被 IGNORE，无副作用。
-- -------------------------------------------------------------------------------------
INSERT IGNORE INTO `sys_menu`
(`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES
(4196, '工装校准登记', 4172, 8, '', '', '', 1, 0, 'F', '0', '0', 'mes:tg:tooling:calibrate', '#', 103, 1, NOW(), NULL, NULL, 'MES-TG-01 登记校准(IDLE->CALIBRATING->IDLE, 服务端算下次校准日)');

-- -------------------------------------------------------------------------------------
-- ④ 角色授权派生：把 4196 授予已拥有工装台账菜单(4172)的角色
-- -------------------------------------------------------------------------------------
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT `role_id`, 4196 FROM `sys_role_menu` WHERE `menu_id` = 4172;

-- -------------------------------------------------------------------------------------
-- 校验 SQL（人工执行，期望：4 行字典数据 + 1 行菜单 + 若干角色授权）
-- -------------------------------------------------------------------------------------
-- SELECT dict_code, dict_label, dict_value FROM `sys_dict_data` WHERE dict_type = 'mes_tooling_calibration_result' ORDER BY dict_sort;
-- SELECT menu_id, menu_name, parent_id, perms FROM `sys_menu` WHERE perms = 'mes:tg:tooling:calibrate';
-- SELECT role_id, menu_id FROM `sys_role_menu` WHERE menu_id = 4196;

-- -------------------------------------------------------------------------------------
-- 回滚（仅当确认本批次要整体回退时执行）
-- -------------------------------------------------------------------------------------
-- DELETE FROM `sys_role_menu` WHERE menu_id = 4196;
-- DELETE FROM `sys_menu` WHERE menu_id = 4196;
-- DELETE FROM `sys_dict_data` WHERE dict_type = 'mes_tooling_calibration_result';
-- DELETE FROM `sys_dict_type` WHERE dict_type = 'mes_tooling_calibration_result';
-- 注意：若 2026-09-20-tg-sop.sql 仍在生效，删除 4196 / mes_tooling_status 的 CALIBRATING 会导致
--       后端校准端点权限码悬空与工装状态字典缺项，请一并回退该脚本。
