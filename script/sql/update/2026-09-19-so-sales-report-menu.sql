-- 销售报表菜单上线（销售模块优化决策 Q1「报表保留并修完」/ T3.4）
-- 背景:
--   销售报表（SoSalesReportController + views/erp/so/report/index.vue）此前**无菜单入口**，
--   且后端两个接口的 SQL 列名错误（so.order_id/so.status 实为 so_id/order_status）必然抛
--   SQLSyntaxErrorException、权限码 so:salesOrder:list 在库中不存在（非超管 403）
--   —— 这些后端问题已在 T1.6 修复（列名 + tenant_id 隔离 + 权限码改用 so:orderheader:list）。
--   本脚本补齐缺失的**菜单入口**，使已修好的报表可被访问。
-- 目标库: 系统库 (ry-vue)。本脚本幂等(INSERT IGNORE)，可重复执行。
-- 注意: 应用若在运行，菜单走 Redis 缓存，执行后需在「菜单管理」保存任意一项触发缓存刷新，
--       或重启应用后生效。
-- 权限说明: 报表菜单的 perms 沿用 **so:orderheader:list**（与 SoSalesReportController 的
--       @SaCheckPermission 完全一致），不引入新权限码 —— 避免出现「有菜单但调用 403」的错配。
--       即：能查看销售订单列表的角色即可查看该报表。
-- 授权说明: 菜单授权给角色 3 与 2054472456595238913（与「销售订单」菜单 2202 同批角色）。
--       其他角色如需使用，请在「角色管理」中另行勾选。

-- 1. 销售报表菜单（C 型，挂销售订单管理 2200 下，order_num=5）
INSERT IGNORE INTO `sys_menu` VALUES (2237, '销售报表', 2200, 5, 'report', 'erp/so/report/index', NULL, 1, 0, 'C', '0', '0', 'so:orderheader:list', 'chart', 103, 1, '2026-09-19 00:00:00', NULL, NULL, '销售执行率与客户销售排行（权限沿用销售订单列表，与 SoSalesReportController 的 @SaCheckPermission 一致）');

-- 2. 角色授权
INSERT IGNORE INTO `sys_role_menu` VALUES (3, 2237);
INSERT IGNORE INTO `sys_role_menu` VALUES (2054472456595238913, 2237);

-- 3. 复核：确认菜单已建立（期望 1 行）
-- SELECT menu_id, menu_name, parent_id, path, component, perms FROM sys_menu WHERE menu_id = 2237;
