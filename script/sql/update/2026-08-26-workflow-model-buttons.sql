-- 2026-08-26 流程模型按钮权限补全
-- 背景：WfModelController 声明了 workflow:model:add/edit/save/remove/config/export 等权限，
-- 前端 model/index.vue 也用 v-hasPermi 控制 新增/编辑/删除/保存/导出/设计 按钮，
-- 但 sys_menu 只有 workflow:model:list（菜单 20002）与 workflow:model:deploy（按钮 20009），
-- 未授权这些按钮权限会导致相应操作 403（后端）且按钮永不显示（前端）。
-- 幂等：先删后插，可重复执行。

DELETE FROM `sys_menu` WHERE `menu_id` BETWEEN 2070003 AND 2070008;
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(2070003, '流程模型新增', 20002, 1, '#', '', '', 1, 0, 'F', '0', '0', 'workflow:model:add', '#', 103, 1, NOW(), NULL, NULL, ''),
(2070004, '流程模型修改', 20002, 2, '#', '', '', 1, 0, 'F', '0', '0', 'workflow:model:edit', '#', 103, 1, NOW(), NULL, NULL, ''),
(2070005, '流程模型删除', 20002, 3, '#', '', '', 1, 0, 'F', '0', '0', 'workflow:model:remove', '#', 103, 1, NOW(), NULL, NULL, ''),
(2070006, '流程模型保存', 20002, 4, '#', '', '', 1, 0, 'F', '0', '0', 'workflow:model:save', '#', 103, 1, NOW(), NULL, NULL, ''),
(2070007, '流程模型导出', 20002, 5, '#', '', '', 1, 0, 'F', '0', '0', 'workflow:model:export', '#', 103, 1, NOW(), NULL, NULL, ''),
(2070008, '流程模型设计', 20002, 6, '#', '', '', 1, 0, 'F', '0', '0', 'workflow:model:designer', '#', 103, 1, NOW(), NULL, NULL, '');
