-- =============================================
-- NexusERP - ERP 采购进项发票菜单注册
-- 目标库: ry-vue
-- 创建时间: 2026-07-16
-- 说明: 开票与应收应付优化 P2 - 注册 pu:invoice:* 权限与菜单
-- 幂等: 使用 INSERT IGNORE
-- 父菜单 2972 = 采购执行 (live DB: 2500=采购申请目录, 2972=采购执行[订单/退货], 2973=采购商务[合同/评估];
--            进项发票由采购订单生成、退货红冲,与订单/退货同属执行链,故挂 2972)
-- ID 段位: 2550-2558 (避让 2501-2509 采购申请 / 2510-2518 采购订单 / 2520-2527 采购退货 / 2530-2537 供应商评估 / 2540-2545 采购合同)
-- 权限拆分: 核销/红冲独立 pu:invoice:verify, 认证/抵扣独立 pu:invoice:certify, 三单匹配独立 pu:invoice:match
--           (吸取 so:invoice:verify 待 P3 拆分的教训, pu 一开始即拆分敏感财务权限)
-- =============================================

USE `ry-vue`;

-- 采购进项发票主菜单 (2550) + 8 个按钮权限 (2551-2558)
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(2550, '采购进项发票', 2972, 3, 'invoice', 'erp/pu/invoice/index', NULL, 1, 0, 'C', '0', '0', 'pu:invoice:list', 'documentation', 103, 1, NOW(), NULL, NULL, '采购进项发票管理菜单'),
(2551, '发票查询', 2550, 1, '', '', '', 1, 0, 'F', '0', '0', 'pu:invoice:query', '#', 103, 1, NOW(), NULL, NULL, ''),
(2552, '发票新增', 2550, 2, '', '', '', 1, 0, 'F', '0', '0', 'pu:invoice:add', '#', 103, 1, NOW(), NULL, NULL, ''),
(2553, '发票修改', 2550, 3, '', '', '', 1, 0, 'F', '0', '0', 'pu:invoice:edit', '#', 103, 1, NOW(), NULL, NULL, '含收票确认/作废'),
(2554, '发票删除', 2550, 4, '', '', '', 1, 0, 'F', '0', '0', 'pu:invoice:remove', '#', 103, 1, NOW(), NULL, NULL, ''),
(2555, '发票导出', 2550, 5, '', '', '', 1, 0, 'F', '0', '0', 'pu:invoice:export', '#', 103, 1, NOW(), NULL, NULL, ''),
(2556, '收票核销', 2550, 6, '', '', '', 1, 0, 'F', '0', '0', 'pu:invoice:verify', '#', 103, 1, NOW(), NULL, NULL, '核销/红冲'),
(2557, '认证抵扣', 2550, 7, '', '', '', 1, 0, 'F', '0', '0', 'pu:invoice:certify', '#', 103, 1, NOW(), NULL, NULL, '认证/抵扣/进项转出'),
(2558, '三单匹配', 2550, 8, '', '', '', 1, 0, 'F', '0', '0', 'pu:invoice:match', '#', 103, 1, NOW(), NULL, NULL, 'PO+收货+发票校验');

-- 验证
-- SELECT menu_id, menu_name, perms FROM `sys_menu` WHERE `perms` LIKE 'pu:invoice:%' ORDER BY menu_id;
