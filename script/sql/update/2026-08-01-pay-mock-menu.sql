-- =============================================
-- NexusERP - 最小 pay 收款入口(解决断链1): 菜单
-- 目标库: ry-vue
-- 创建时间: 2026-08-01
-- 幂等: 使用 INSERT IGNORE
-- 菜单段位: 6181 (避让 6100-6117 F0 / 6120-6129 F1 / 6130-6160 F5 / 6161-6175 F2 / 6176-6178 F3-a)
-- =============================================

USE `ry-vue`;

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(6181, '模拟收款', 6100, 7, 'payMock', 'erp/fin/pay/mock', NULL, 1, 0, 'C', '0', '0', 'fin:pay:callback', 'money', 103, 1, NOW(), NULL, NULL, '模拟支付渠道收款回调(解决pay断链)');

-- 验证
-- SELECT menu_id, menu_name, perms FROM `sys_menu` WHERE menu_id=6181;
