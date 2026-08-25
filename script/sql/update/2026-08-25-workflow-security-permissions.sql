-- ----------------------------------------------------------------------------
-- 工作流模块安全修复配套权限菜单（增量脚本）
-- 背景：
--   1) /workflow/instance/* 与 /workflow/process/instance/* 原先无任何权限注解，
--      任意登录用户可跨租户终止/删除流程实例（安全漏洞，代码已补 @SaCheckPermission）。
--   2) 控制器使用 workflow:process:myCopyList，但菜单库中从未配置该权限码，
--      “我的抄送”接口（/workflow/process/myCopyList）此前无法被正常授权。
-- 说明：
--   - workflow:process:cancel / remove / query 复用已有权限码（20014/20013/20012），无需新增。
--   - workflow:instance:updateState 为新增权限码（实例激活/挂起，管理性质操作）。
--   - 执行后需在【系统管理-角色管理】为相应角色勾选新按钮权限。
-- ----------------------------------------------------------------------------

-- 1. 补充缺失的“我的抄送”权限按钮（挂到 抄送任务 20008 下）
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (2070001, '我的抄送列表', 20008, 5, '#', '', '', 1, 0, 'F', '0', '0', 'workflow:process:myCopyList', '#', 103, 1, NOW(), NULL, NULL, '我的抄送列表（原权限码缺失，补配）');

-- 2. 新增“流程实例状态变更”权限按钮（挂到 我的流程 20004 下，实例激活/挂起管理操作）
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (2070002, '实例状态变更', 20004, 9, '#', '', '', 1, 0, 'F', '0', '0', 'workflow:instance:updateState', '#', 103, 1, NOW(), NULL, NULL, '流程实例激活/挂起（/workflow/instance/updateState）');
