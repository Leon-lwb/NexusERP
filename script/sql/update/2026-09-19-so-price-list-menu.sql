-- 销售价目表上线接线（菜单 / 权限码 / 状态字典）
-- 背景:
--   价目表模块（so_price_list / so_price_list_line + 定价引擎 + 前端页面）此前**整体不可达**：
--     1) sys_menu 中没有任何价目表菜单，也没有 so:priceList:* 权限码
--        —— 而 SoPriceListController 的 6 个端点都标了 @SaCheckPermission("so:priceList:xxx")，
--           除超管（硬编码 *:*:*）外任何角色调用均 403；
--     2) 前端 price-list/index.vue 与 form.vue 引用的字典 so_price_list_status 在库中不存在
--        —— 状态下拉框与 dict-tag 全空；
--     3) 订单侧取价（T3.1b）已接通，价目表需要可维护的入口。
-- 目标库: 系统库 (ry-vue)。本脚本幂等(INSERT IGNORE)，可重复执行。
-- 注意: 应用若在运行，菜单与字典走 Redis 缓存，执行后需在「菜单管理 / 字典管理」保存任意一项
--       触发缓存刷新，或重启应用 / 清理 sys_dict:* 与 sys_menu 相关缓存后生效。
-- 授权说明: 菜单授权给角色 3 与 2054472456595238913（与「销售订单」菜单 2202 同批角色）。
--       其他角色如需使用价目表，请在「角色管理」中另行勾选。

-- 1. 价目表菜单（C 型，挂销售订单管理 2200 下）
INSERT IGNORE INTO `sys_menu` VALUES (2225, '价目表', 2200, 4, 'priceList', 'erp/so/price-list/index', NULL, 1, 0, 'C', '0', '0', 'so:priceList:list', 'money', 103, 1, '2026-09-19 00:00:00', NULL, NULL, '销售价目表（取价引擎数据源）');

-- 2. 价目表按钮权限（与 Controller 的 @SaCheckPermission 一一对应）
INSERT IGNORE INTO `sys_menu` VALUES (2226, '价目表查询', 2225, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'so:priceList:query', '#', 103, 1, '2026-09-19 00:00:00', NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` VALUES (2227, '价目表新增', 2225, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'so:priceList:add', '#', 103, 1, '2026-09-19 00:00:00', NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` VALUES (2228, '价目表修改', 2225, 3, '', NULL, NULL, 1, 0, 'F', '0', '0', 'so:priceList:edit', '#', 103, 1, '2026-09-19 00:00:00', NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` VALUES (2229, '价目表删除', 2225, 4, '', NULL, NULL, 1, 0, 'F', '0', '0', 'so:priceList:remove', '#', 103, 1, '2026-09-19 00:00:00', NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` VALUES (2236, '价目表导出', 2225, 5, '', NULL, NULL, 1, 0, 'F', '0', '0', 'so:priceList:export', '#', 103, 1, '2026-09-19 00:00:00', NULL, NULL, '');

-- 3. 角色授权
INSERT IGNORE INTO `sys_role_menu` VALUES (3, 2225);
INSERT IGNORE INTO `sys_role_menu` VALUES (3, 2226);
INSERT IGNORE INTO `sys_role_menu` VALUES (3, 2227);
INSERT IGNORE INTO `sys_role_menu` VALUES (3, 2228);
INSERT IGNORE INTO `sys_role_menu` VALUES (3, 2229);
INSERT IGNORE INTO `sys_role_menu` VALUES (3, 2236);
INSERT IGNORE INTO `sys_role_menu` VALUES (2054472456595238913, 2225);
INSERT IGNORE INTO `sys_role_menu` VALUES (2054472456595238913, 2226);
INSERT IGNORE INTO `sys_role_menu` VALUES (2054472456595238913, 2227);
INSERT IGNORE INTO `sys_role_menu` VALUES (2054472456595238913, 2228);
INSERT IGNORE INTO `sys_role_menu` VALUES (2054472456595238913, 2229);
INSERT IGNORE INTO `sys_role_menu` VALUES (2054472456595238913, 2236);

-- 4. 价格表状态字典（仅 ACTIVE 参与取价）
INSERT IGNORE INTO `sys_dict_type` VALUES (400002, '000000', '价格表状态', 'so_price_list_status', 103, 1, '2026-09-19 00:00:00', NULL, NULL, '销售价格表状态：仅 ACTIVE 参与取价');
INSERT IGNORE INTO `sys_dict_data` VALUES (400004, '000000', 1, '启用', 'ACTIVE', 'so_price_list_status', '', 'success', 'Y', 103, 1, '2026-09-19 00:00:00', NULL, NULL, '启用（参与取价）');
INSERT IGNORE INTO `sys_dict_data` VALUES (400005, '000000', 2, '停用', 'INACTIVE', 'so_price_list_status', '', 'danger', 'N', 103, 1, '2026-09-19 00:00:00', NULL, NULL, '停用（不参与取价）');
