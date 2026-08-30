-- M2 批次2: 生产退料单权限点种子 (sys_menu)
-- 背景: WmMaterialReturnController / AppPdaReturnController 引用 wm:materialReturn:* 权限点,
--       但此前无种子数据, 除超级管理员外任何角色均无法授权 (PDA 退料核销会 403)。
-- 目标库: 系统库 (sys_menu 所在库, 即 ry-vue); ERP 业务的 4 个 2026-08-30 脚本在 erp 库执行。
-- 菜单本体隐藏 (visible='1'): PC 建单页待 plus-ui 接入后排期, 先放开权限点供接口联调。
-- 父菜单 2965 = 库存管理; 菜单 ID 20210~20215 已确认空闲。

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES (20210, '生产退料', 2965, 9, 'materialReturn', 'erp/wm/return/index', NULL, 1, 0, 'C', '1', '0', 'wm:materialReturn:list', 'form', 103, 1, NOW(), 'PC建单页待plus-ui接入, 先隐藏');

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES
    (20211, '退料查询', 20210, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'wm:materialReturn:query',  '#', 103, 1, NOW(), ''),
    (20212, '退料新增', 20210, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'wm:materialReturn:add',    '#', 103, 1, NOW(), ''),
    (20213, '退料修改', 20210, 3, '', NULL, NULL, 1, 0, 'F', '0', '0', 'wm:materialReturn:edit',   '#', 103, 1, NOW(), 'PDA扫码核销/过账使用'),
    (20214, '退料删除', 20210, 4, '', NULL, NULL, 1, 0, 'F', '0', '0', 'wm:materialReturn:remove', '#', 103, 1, NOW(), ''),
    (20215, '退料导出', 20210, 5, '', NULL, NULL, 1, 0, 'F', '0', '0', 'wm:materialReturn:export', '#', 103, 1, NOW(), '');

-- 提示: 菜单入库后需在 [系统管理-角色管理] 为仓储作业角色勾选上述按钮, PDA 退料核销方可通过权限校验。
