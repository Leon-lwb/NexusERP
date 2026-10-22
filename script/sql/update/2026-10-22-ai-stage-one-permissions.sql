-- AI 第一阶段：仅补菜单权限元数据，适用于包含 sys_menu 的系统 master 数据库。
-- 本文件仅写入仓库供审阅；开发与测试期间未执行。
-- 另获执行授权后，先核对目标环境、备份、父页面身份及菜单 ID 占用。
-- 不授予任何角色或租户套餐权限；角色授权须由管理员另行审核。
-- ID/权限冲突或父级身份不符时跳过插入；跳过不代表迁移成功。
-- 请按下列 ID、父级及权限核对前后查询结果；禁止向存量数据库导入
-- ry-vue.sql 主初始化脚本；本增量不涉及 ERP/MES/IoT 业务数据库。

-- 执行前核对：只读取相关菜单元数据，不含凭据或业务数据。
SELECT menu_id, parent_id, menu_type, status, path, component, perms
FROM sys_menu
WHERE menu_id IN (1702, 1703, 1704, 1831, 1725, 1734, 1745, 1843)
   OR BINARY perms IN ('ai:model:export', 'ai:provider:export', 'ai:knowledge:export', 'ai:flow:query');

START TRANSACTION;

INSERT INTO sys_menu
(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
 menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 1725, '模型导出', 1702, 6, '', '', '', 1, 0,
       'F', '0', '0', 'ai:model:export', '#', 103, 1, NOW(), NULL, NULL, 'AI 第一阶段权限补齐'
FROM sys_menu parent
WHERE parent.menu_id = 1702 AND parent.parent_id = 1707
  AND parent.menu_type = 'C' AND parent.status = '0'
  AND BINARY parent.path = BINARY 'model'
  AND BINARY parent.component = BINARY 'ai/model/index'
  AND BINARY parent.perms = BINARY 'ai:model:list'
  AND NOT EXISTS (SELECT 1 FROM sys_menu current_menu
                  WHERE current_menu.menu_id = 1725
                     OR BINARY current_menu.perms = BINARY 'ai:model:export');

INSERT INTO sys_menu
(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
 menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 1734, '厂商导出', 1703, 5, '', '', '', 1, 0,
       'F', '0', '0', 'ai:provider:export', '#', 103, 1, NOW(), NULL, NULL, 'AI 第一阶段权限补齐'
FROM sys_menu parent
WHERE parent.menu_id = 1703 AND parent.parent_id = 1707
  AND parent.menu_type = 'C' AND parent.status = '0'
  AND BINARY parent.path = BINARY 'provider'
  AND BINARY parent.component = BINARY 'ai/model/provider'
  AND BINARY parent.perms = BINARY 'ai:provider:list'
  AND NOT EXISTS (SELECT 1 FROM sys_menu current_menu
                  WHERE current_menu.menu_id = 1734
                     OR BINARY current_menu.perms = BINARY 'ai:provider:export');

INSERT INTO sys_menu
(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
 menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 1745, '知识库导出', 1704, 9, '', '', '', 1, 0,
       'F', '0', '0', 'ai:knowledge:export', '#', 103, 1, NOW(), NULL, NULL, 'AI 第一阶段权限补齐'
FROM sys_menu parent
WHERE parent.menu_id = 1704 AND parent.parent_id = 1708
  AND parent.menu_type = 'C' AND parent.status = '0'
  AND BINARY parent.path = BINARY 'knowledge'
  AND BINARY parent.component = BINARY 'ai/knowledge/index'
  AND BINARY parent.perms = BINARY 'ai:knowledge:list'
  AND NOT EXISTS (SELECT 1 FROM sys_menu current_menu
                  WHERE current_menu.menu_id = 1745
                     OR BINARY current_menu.perms = BINARY 'ai:knowledge:export');

INSERT INTO sys_menu
(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
 menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 1843, '流程查询', 1831, 6, '', '', '', 1, 0,
       'F', '0', '0', 'ai:flow:query', '#', 103, 1, NOW(), NULL, NULL, 'AI 第一阶段权限补齐'
FROM sys_menu parent
WHERE parent.menu_id = 1831 AND parent.parent_id = 1830
  AND parent.menu_type = 'C' AND parent.status = '0'
  AND BINARY parent.path = BINARY 'index'
  AND BINARY parent.component = BINARY 'ai/flow/index'
  AND BINARY parent.perms = BINARY 'ai:flow:list'
  AND NOT EXISTS (SELECT 1 FROM sys_menu current_menu
                  WHERE current_menu.menu_id = 1843
                     OR BINARY current_menu.perms = BINARY 'ai:flow:query');

COMMIT;

-- 每个权限应恰有一条启用的 F 按钮行，且 ID 与既有父页面须逐项一致。
-- 缺失、错挂或重复行均须人工核查；本脚本不自动修复既有记录。
SELECT menu_id, parent_id, order_num, menu_type, status, path, component, perms
FROM sys_menu
WHERE menu_id IN (1725, 1734, 1745, 1843)
   OR BINARY perms IN ('ai:model:export', 'ai:provider:export', 'ai:knowledge:export', 'ai:flow:query')
ORDER BY menu_id;
