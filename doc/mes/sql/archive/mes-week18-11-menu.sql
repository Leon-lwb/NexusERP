-- =============================================
-- NexusERP - MES模块 Week 18 菜单权限补充SQL（修正版）
-- 数据库: ry-vue
-- 说明: 补充MES工单下达按钮权限
-- 修正: 对齐Week17的4000段ID + Controller的mes:pro:work:*权限字符串
-- 创建时间: 2026-06-17
-- =============================================

USE `ry-vue`;

-- -----------------------------------------------------------
-- 补充MES工单按钮权限
-- Week 17 已创建 4010(MES工单) + 4011~4017(按钮)
-- Week 17 权限前缀为 mes:workorder:*，但 Controller 实际使用 mes:pro:work:*
-- 此SQL补充Week17缺失的权限映射 + Week18下达按钮
-- -----------------------------------------------------------

-- 补充与Controller对齐的权限（mes:pro:work:*）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES
(4018, 'MES工单列表(权限修正)', 4010, 8, '', NULL, NULL, 1, 0, 'F', '0', '0', 'mes:pro:work:list', '#', 103, 1, NOW(), '与Controller mes:pro:work:list 对齐'),
(4019, 'MES工单查询(权限修正)', 4010, 9, '', NULL, NULL, 1, 0, 'F', '0', '0', 'mes:pro:work:query', '#', 103, 1, NOW(), '与Controller mes:pro:work:query 对齐'),
(4020, 'MES工单新增(权限修正)', 4010, 10, '', NULL, NULL, 1, 0, 'F', '0', '0', 'mes:pro:work:add', '#', 103, 1, NOW(), '与Controller mes:pro:work:add 对齐'),
(4021, 'MES工单修改(权限修正)', 4010, 11, '', NULL, NULL, 1, 0, 'F', '0', '0', 'mes:pro:work:edit', '#', 103, 1, NOW(), '与Controller mes:pro:work:edit 对齐(含下达)'),
(4022, 'MES工单删除(权限修正)', 4010, 12, '', NULL, NULL, 1, 0, 'F', '0', '0', 'mes:pro:work:remove', '#', 103, 1, NOW(), '与Controller mes:pro:work:remove 对齐'),
(4023, 'MES工单导出(权限修正)', 4010, 13, '', NULL, NULL, 1, 0, 'F', '0', '0', 'mes:pro:work:export', '#', 103, 1, NOW(), '与Controller mes:pro:work:export 对齐')
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);

-- -----------------------------------------------------------
-- 前端路由配置指引
-- -----------------------------------------------------------
-- 路由路径: /mes/pro/workorder
-- 组件路径: mes/pro/workorder/index
-- 父菜单: 生产执行(4001)
--
-- 详情路由: /mes/pro/workorder/detail/:mesWoId
-- 组件路径: mes/pro/workorder/detail
-- 父菜单: MES工单(4010)
-- 是否隐藏: 是
