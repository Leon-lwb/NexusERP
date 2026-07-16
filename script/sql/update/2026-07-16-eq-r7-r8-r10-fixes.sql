-- ============================================================
-- MES 设备管理 R7/R8/R10 增量脚本 (幂等)
-- 日期: 2026-07-16
-- 适用: 已部署的 ry-vue 系统库
-- 内容:
--   1. 新增字典 mes_maintenance_status (启用/停用)                       [R8]
--   2. 修复"故障分析"菜单: 维修记录(4120) -> 设备管理(4003)              [R7]
--   3. 新增导出按钮权限 mes:eq:maintenance:record:export                [R10]
--      及 mes:eq:spare:log:export                                       [R10]
--
-- 说明:
--   - 主键均为雪花 ID (非自增), 用 MAX(PK)+1 动态分配, 避免硬编码冲突。
--   - 全部写操作带幂等守卫 (WHERE NOT EXISTS / UPDATE), 可安全重复执行。
--   - 第 2 项: RuoYi buildMenus 仅对 TYPE_DIR(M 型) 菜单递归生成子路由,
--     "故障分析" 原挂在 C 型"维修记录"(4120) 下, 其路由被丢弃导致页面不可达,
--     移至目录型"设备管理"(4003) 后作为 /mes/eq/statistics 正常访问。
--   - 主脚本 ry-vue.sql 已同步这些变更, 本脚本供已部署环境增量执行。
-- ============================================================

-- 1. 新增字典类型 mes_maintenance_status (按 dict_type 幂等)
SET @dictId = (SELECT IFNULL(MAX(dict_id), 0) + 1 FROM sys_dict_type);
INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_dept, create_by, create_time, remark)
SELECT @dictId, '000000', '保养计划状态', 'mes_maintenance_status', 103, 1, NOW(), 'MES保养计划启用状态'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE tenant_id = '000000' AND dict_type = 'mes_maintenance_status');

-- 2. 新增字典数据 启用/停用 (每次重算 dict_code, 按 dict_type+dict_value 幂等)
SET @dc = (SELECT IFNULL(MAX(dict_code), 0) + 1 FROM sys_dict_data);
INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, remark)
SELECT @dc, '000000', 1, '启用', 'ACTIVE', 'mes_maintenance_status', NULL, 'success', 'Y', 103, 1, NOW(), NULL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = '000000' AND dict_type = 'mes_maintenance_status' AND dict_value = 'ACTIVE');

SET @dc = (SELECT IFNULL(MAX(dict_code), 0) + 1 FROM sys_dict_data);
INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, remark)
SELECT @dc, '000000', 2, '停用', 'INACTIVE', 'mes_maintenance_status', NULL, 'info', 'N', 103, 1, NOW(), NULL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE tenant_id = '000000' AND dict_type = 'mes_maintenance_status' AND dict_value = 'INACTIVE');

-- 3. 修复"故障分析"菜单挂载点 (C 型父菜单不生成子路由, 移至目录型设备管理下; UPDATE 天然幂等)
UPDATE sys_menu SET parent_id = 4003, order_num = 14
WHERE menu_id = 2053846181246812196 AND menu_name = '故障分析';

-- 4. 新增导出按钮权限 (按 perms 幂等)
SET @menuId = (SELECT IFNULL(MAX(menu_id), 0) + 1 FROM sys_menu);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT @menuId, '记录导出', 2053846181246812166, 3, '', '', '', 1, 0, 'F', '0', '0', 'mes:eq:maintenance:record:export', '#', 103, 1, NOW(), NULL, NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'mes:eq:maintenance:record:export');

SET @menuId = (SELECT IFNULL(MAX(menu_id), 0) + 1 FROM sys_menu);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT @menuId, '流水导出', 2053846181246812180, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:eq:spare:log:export', '#', 103, 1, NOW(), NULL, NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'mes:eq:spare:log:export');
