-- ----------------------------
-- 支付模块菜单 SQL
-- 适用于 ry-vue 库
-- 菜单ID范围: 2000~2020
-- ----------------------------

-- 支付管理父菜单
INSERT INTO sys_menu VALUES('2000', '支付管理', '0', '7', 'pay', NULL, '', 1, 0, 'M', '0', '0', '', 'pay', 103, 1, sysdate(), NULL, NULL, '支付管理目录');

-- 订单管理
INSERT INTO sys_menu VALUES('2001', '订单管理', '2000', '1', 'order', 'pay/order/index', '', 1, 0, 'C', '0', '0', 'pay:order:list', '#', 103, 1, sysdate(), NULL, NULL, '订单管理菜单');
INSERT INTO sys_menu VALUES('2002', '订单查询', '2001', '1', '#', '', '', 1, 0, 'F', '0', '0', 'pay:order:query', '#', 103, 1, sysdate(), NULL, NULL, '');
INSERT INTO sys_menu VALUES('2003', '订单新增', '2001', '2', '#', '', '', 1, 0, 'F', '0', '0', 'pay:order:add', '#', 103, 1, sysdate(), NULL, NULL, '');
INSERT INTO sys_menu VALUES('2004', '订单修改', '2001', '3', '#', '', '', 1, 0, 'F', '0', '0', 'pay:order:edit', '#', 103, 1, sysdate(), NULL, NULL, '');
INSERT INTO sys_menu VALUES('2005', '订单删除', '2001', '4', '#', '', '', 1, 0, 'F', '0', '0', 'pay:order:remove', '#', 103, 1, sysdate(), NULL, NULL, '');
INSERT INTO sys_menu VALUES('2006', '订单导出', '2001', '5', '#', '', '', 1, 0, 'F', '0', '0', 'pay:order:export', '#', 103, 1, sysdate(), NULL, NULL, '');

-- 发票管理
INSERT INTO sys_menu VALUES('2007', '发票管理', '2000', '2', 'invoice', 'pay/invoice/index', '', 1, 0, 'C', '0', '0', 'pay:invoice:list', '#', 103, 1, sysdate(), NULL, NULL, '发票管理菜单');
INSERT INTO sys_menu VALUES('2008', '发票查询', '2007', '1', '#', '', '', 1, 0, 'F', '0', '0', 'pay:invoice:query', '#', 103, 1, sysdate(), NULL, NULL, '');
INSERT INTO sys_menu VALUES('2009', '发票新增', '2007', '2', '#', '', '', 1, 0, 'F', '0', '0', 'pay:invoice:add', '#', 103, 1, sysdate(), NULL, NULL, '');
INSERT INTO sys_menu VALUES('2010', '发票修改', '2007', '3', '#', '', '', 1, 0, 'F', '0', '0', 'pay:invoice:edit', '#', 103, 1, sysdate(), NULL, NULL, '');
INSERT INTO sys_menu VALUES('2011', '发票删除', '2007', '4', '#', '', '', 1, 0, 'F', '0', '0', 'pay:invoice:remove', '#', 103, 1, sysdate(), NULL, NULL, '');
INSERT INTO sys_menu VALUES('2012', '导出发票', '2007', '5', '#', '', '', 1, 0, 'F', '0', '0', 'pay:invoice:export', '#', 103, 1, sysdate(), NULL, NULL, '');

-- 支付测试
INSERT INTO sys_menu VALUES('2013', '支付测试', '2000', '3', 'paytest', 'pay/paytest/index', '', 1, 0, 'C', '0', '0', 'pay:paytest:list', '#', 103, 1, sysdate(), NULL, NULL, '支付测试菜单');

-- ----------------------------
-- 字典数据
-- 适用于 ry-vue 库
-- 字典ID范围: 16~18
-- ----------------------------

-- 订单状态字典
INSERT INTO sys_dict_type VALUES(16, '000000', '订单状态', 'pay_order_status', 103, 1, sysdate(), null, null, '支付订单状态');
INSERT INTO sys_dict_data VALUES(60, '000000', 1, '未支付', 'waiting', 'pay_order_status', '', 'warning', 'N', 103, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(61, '000000', 2, '已支付', 'success', 'pay_order_status', '', 'success', 'N', 103, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(62, '000000', 3, '已关闭', 'closed', 'pay_order_status', '', 'danger', 'N', 103, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(63, '000000', 4, '已退款', 'refund', 'pay_order_status', '', 'info', 'N', 103, 1, sysdate(), null, null, '');

-- 支付方式字典
INSERT INTO sys_dict_type VALUES(17, '000000', '支付方式', 'pay_pay_type', 103, 1, sysdate(), null, null, '支付方式列表');
INSERT INTO sys_dict_data VALUES(64, '000000', 1, '支付宝', 'alipay', 'pay_pay_type', '', 'success', 'N', 103, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(65, '000000', 2, '微信支付', 'wechat', 'pay_pay_type', '', 'success', 'N', 103, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(66, '000000', 3, '收钱吧', 'sqb', 'pay_pay_type', '', 'primary', 'N', 103, 1, sysdate(), null, null, '');

-- 发票类型字典
INSERT INTO sys_dict_type VALUES(18, '000000', '发票类型', 'pay_invoice_type', 103, 1, sysdate(), null, null, '发票类型列表');
INSERT INTO sys_dict_data VALUES(67, '000000', 1, '普通发票', 'normal', 'pay_invoice_type', '', 'primary', 'N', 103, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(68, '000000', 2, '专用发票', 'special', 'pay_invoice_type', '', 'success', 'N', 103, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data VALUES(69, '000000', 3, '电子发票', 'electronic', 'pay_invoice_type', '', 'warning', 'N', 103, 1, sysdate(), null, null, '');
