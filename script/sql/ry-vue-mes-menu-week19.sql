-- =============================================
-- NexusERP - MES模块 Week 19 菜单权限补充SQL
-- 数据库: ry-vue
-- 说明: 补充MES主数据只读菜单 + submit/proc按钮权限
-- 修正: 对齐Week17的4000段ID + Controller的mes:pro:work:*权限字符串
-- 创建时间: 2026-06-18
-- =============================================

USE `ry-vue`;

-- -----------------------------------------------------------
-- 1. MES主数据只读菜单（物料/BOM/工艺路线）
-- 父菜单: MES系统(4000)
-- -----------------------------------------------------------
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES
(4030, '物料查询', 4000, 10, 'md-material', 'mes/md/material/index', NULL, 1, 0, 'C', '0', '0', 'mes:md:material:list', '#', 103, 1, NOW(), 'MES物料只读查询'),
(4031, 'BOM查询', 4000, 11, 'md-bom', 'mes/md/bom/index', NULL, 1, 0, 'C', '0', '0', 'mes:md:bom:list', '#', 103, 1, NOW(), 'MES BOM只读查询'),
(4032, '工艺路线查询', 4000, 12, 'md-routing', 'mes/md/routing/index', NULL, 1, 0, 'C', '0', '0', 'mes:md:routing:list', '#', 103, 1, NOW(), 'MES工艺路线只读查询')
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name);

-- -----------------------------------------------------------
-- 2. MES工单审批按钮权限（submit + proc）
-- 父菜单: MES工单(4010)
-- -----------------------------------------------------------
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES
(4024, 'MES工单提交审批', 4010, 14, '', NULL, NULL, 1, 0, 'F', '0', '0', 'mes:pro:work:submit', '#', 103, 1, NOW(), 'MES工单提交审批按钮')
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name);

-- 注意: mes:pro:work:query 权限已在Week18修正SQL中创建(4019)，查看流程复用此权限

-- -----------------------------------------------------------
-- 3. 前端路由配置指引
-- -----------------------------------------------------------
-- 物料查询: /mes/md/material -> mes/md/material/index
-- BOM查询: /mes/md/bom -> mes/md/bom/index
-- 工艺路线查询: /mes/md/routing -> mes/md/routing/index
-- MES工单详情(已有): /mes/pro/workorder/detail/:mesWoId -> mes/pro/workorder/detail
