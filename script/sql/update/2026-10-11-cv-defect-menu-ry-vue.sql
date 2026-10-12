-- Additive CV defect menu only, executed in the SYSTEM database containing sys_menu.
-- Verify parent6182 is the ERP CV directory and menu6195 is unoccupied before execution.
-- Preserve existing menus, roles and business data. Never import the full initialization dump.
-- Idempotent insert; a conflict skips insertion. Verify the final SELECT instead of assuming success.
START TRANSACTION;
INSERT INTO sys_menu
(menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
 menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 6195, '工业缺陷检测', 6182, 4, 'defect', 'cv/defect/index', NULL, 1, 0,
       'C', '0', '0', 'cv:detect:query', 'monitor', 103, 1, NOW(), NULL, NULL,
       '金属表面/PCB 缺陷检测（Vision 场景模型）'
FROM sys_menu parent
WHERE parent.menu_id = 6182 AND parent.menu_type = 'M' AND BINARY parent.path = BINARY 'cv'
  AND NOT EXISTS (SELECT 1 FROM sys_menu current_menu
                  WHERE current_menu.menu_id = 6195
                     OR (current_menu.parent_id = 6182 AND BINARY current_menu.path = BINARY 'defect'));
COMMIT;
SELECT menu_id, menu_name, parent_id, path, component, perms
FROM sys_menu WHERE menu_id = 6195 OR (parent_id = 6182 AND path = 'defect');
