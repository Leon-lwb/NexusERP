-- =============================================
-- NexusERP - ERP 采购合同菜单注册
-- 目标库: ry-vue
-- 创建时间: 2026-06-26
-- 说明: Wave2 P1 补全 - 注册 pu:contract:* 权限与菜单
-- 幂等: 使用 INSERT IGNORE
-- 父菜单 2500 = 采购管理
-- ID 调整说明: 计划原拟 2530-2535, 但 2530-2537 已被"供应商评估"菜单占用,
--            故调整至 2540-2545; order_num 由 5 调整为 4 (供应商评估已占 order_num=5,
--            采购合同填入空闲的 order_num=4 槽位, 排在采购退货[3]与供应商评估[5]之间)
-- =============================================

USE `ry-vue`;

-- 采购合同主菜单 (2540) + 5 个按钮权限 (2541-2545)
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(2540, '采购合同', 2500, 4, 'contract', 'erp/pu/contract/index', NULL, 1, 0, 'C', '0', '0', 'pu:contract:list', 'documentation', 103, 1, NOW(), NULL, NULL, '采购合同管理菜单'),
(2541, '合同查询', 2540, 1, '', '', '', 1, 0, 'F', '0', '0', 'pu:contract:query', '#', 103, 1, NOW(), NULL, NULL, ''),
(2542, '合同新增', 2540, 2, '', '', '', 1, 0, 'F', '0', '0', 'pu:contract:add', '#', 103, 1, NOW(), NULL, NULL, ''),
(2543, '合同修改', 2540, 3, '', '', '', 1, 0, 'F', '0', '0', 'pu:contract:edit', '#', 103, 1, NOW(), NULL, NULL, ''),
(2544, '合同删除', 2540, 4, '', '', '', 1, 0, 'F', '0', '0', 'pu:contract:remove', '#', 103, 1, NOW(), NULL, NULL, ''),
(2545, '合同审批', 2540, 5, '', '', '', 1, 0, 'F', '0', '0', 'pu:contract:submit', '#', 103, 1, NOW(), NULL, NULL, '');

-- 验证
-- SELECT menu_id, menu_name, perms FROM `sys_menu` WHERE `perms` LIKE 'pu:contract:%';
