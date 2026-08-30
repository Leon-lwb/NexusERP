-- M3 批次2: 上料交接单权限点种子 (sys_menu)
-- 目标库: 系统库 (sys_menu 所在库, 即 ry-vue); 建表脚本 2026-08-30-pda-material-handover.sql 在 erp 库执行。
-- 菜单本体隐藏 (visible='1'): PC 建单页待 plus-ui 接入后排期, 先放开权限点供接口联调。
-- 父菜单 2965 = 库存管理; 菜单 ID 20216~20220 已确认空闲 (20210~20215 为退料)。

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES (20216, '上料交接', 2965, 10, 'materialHandover', 'erp/wm/handover/index', NULL, 1, 0, 'C', '1', '0', 'wm:materialHandover:list', 'swap', 103, 1, NOW(), 'PC建单页待plus-ui接入, 先隐藏');

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES
    (20217, '交接查询', 20216, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'wm:materialHandover:query',  '#', 103, 1, NOW(), ''),
    (20218, '交接新增', 20216, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'wm:materialHandover:add',    '#', 103, 1, NOW(), ''),
    (20219, '交接修改', 20216, 3, '', NULL, NULL, 1, 0, 'F', '0', '0', 'wm:materialHandover:edit',   '#', 103, 1, NOW(), 'PDA行级核销/签收过账使用'),
    (20220, '交接删除', 20216, 4, '', NULL, NULL, 1, 0, 'F', '0', '0', 'wm:materialHandover:remove', '#', 103, 1, NOW(), '');

-- 交接单号自动编码规则 (genSerialCode("WM_HANDOVER_CODE"))
INSERT INTO `sys_auto_code_rule` VALUES (296, '000000', 'WM_HANDOVER_CODE', '上料交接单编号', NULL, 14, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, NULL, 1, '2026-08-30 00:00:00', NULL, NULL, '0');

-- 提示: 菜单入库后需在 [系统管理-角色管理] 为仓储作业角色勾选上述按钮, PDA 交接核销/签收方可通过权限校验。
