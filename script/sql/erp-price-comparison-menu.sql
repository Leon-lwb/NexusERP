-- ==========================================================
-- 采购价格对比菜单数据
-- 菜单ID范围: 2090-2091 (已确认无冲突)
-- 父菜单: 2200 (销售管理) 或 2000 (ERP管理)
-- 权限前缀: pu:priceComparison
-- ==========================================================

-- 价格对比菜单（放在采购管理下）
INSERT INTO `sys_menu` VALUES (2090, '价格对比', 2000, 7, 'priceComparison', 'erp/pu/price-comparison/index', NULL, 1, 0, 'C', '0', '0', 'pu:priceComparison:list', 'chart', 103, 1, NOW(), NULL, NULL, '采购价格对比菜单');

-- 价格对比查询按钮
INSERT INTO `sys_menu` VALUES (2091, '价格对比查询', 2090, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'pu:priceComparison:query', '#', 103, 1, NOW(), NULL, NULL, '');
