-- 采购/销售合同 字典补齐
-- 背景:
--   采购合同页面的合同类型/状态一直是硬编码映射(页面注释标注"待补字典后改 dict-tag"),
--   而 `contract_type`(采购合同类型) 与 `pu_contract_status`(采购合同状态) 两个字典
--   2026-06-27 就已建好(种子脚本与存量库均有), 只是前端从未接入。
--   本次补齐销售侧两个缺失字典, 前后端合同页面统一走字典渲染:
--     - so_contract_type  销售合同类型(SALES/FRAMEWORK/SERVICE)
--     - so_contract_status 销售合同状态(DRAFT/APPROVING/EFFECTIVE/REJECTED/EXPIRED/TERMINATED)
--   并把采购侧两个既有字典一并收录, 保证任何存量环境执行后四个字典齐全。
-- 目标库: 系统库(ry-vue)。本脚本幂等(INSERT IGNORE), 可重复执行。
-- 注意: 应用若在运行, 字典走 Redis 缓存, 执行后需在「字典管理」保存任意字典触发缓存刷新,
--       或重启应用 / 清理 sys_dict:* 缓存后生效。

-- 1. 字典类型
INSERT IGNORE INTO `sys_dict_type` VALUES (600318, '000000', '销售合同类型', 'so_contract_type', 103, 1, '2026-09-18 00:00:00', NULL, NULL, '销售合同类型');
INSERT IGNORE INTO `sys_dict_type` VALUES (600319, '000000', '销售合同状态', 'so_contract_status', 103, 1, '2026-09-18 00:00:00', NULL, NULL, '销售合同状态');

-- 2. 销售合同类型
INSERT IGNORE INTO `sys_dict_data` VALUES (600321, '000000', 1, '销售合同', 'SALES', 'so_contract_type', '', 'primary', 'Y', 103, 1, '2026-09-18 00:00:00', NULL, NULL, '单次销售合同');
INSERT IGNORE INTO `sys_dict_data` VALUES (600322, '000000', 2, '框架协议', 'FRAMEWORK', 'so_contract_type', '', 'warning', 'N', 103, 1, '2026-09-18 00:00:00', NULL, NULL, '长期框架协议');
INSERT IGNORE INTO `sys_dict_data` VALUES (600323, '000000', 3, '服务合同', 'SERVICE', 'so_contract_type', '', 'info', 'N', 103, 1, '2026-09-18 00:00:00', NULL, NULL, '服务类合同');

-- 3. 销售合同状态
INSERT IGNORE INTO `sys_dict_data` VALUES (600324, '000000', 1, '草稿', 'DRAFT', 'so_contract_status', '', 'info', 'Y', 103, 1, '2026-09-18 00:00:00', NULL, NULL, '草稿');
INSERT IGNORE INTO `sys_dict_data` VALUES (600325, '000000', 2, '审批中', 'APPROVING', 'so_contract_status', '', 'warning', 'N', 103, 1, '2026-09-18 00:00:00', NULL, NULL, '审批中');
INSERT IGNORE INTO `sys_dict_data` VALUES (600326, '000000', 3, '已生效', 'EFFECTIVE', 'so_contract_status', '', 'success', 'N', 103, 1, '2026-09-18 00:00:00', NULL, NULL, '审批通过后生效');
INSERT IGNORE INTO `sys_dict_data` VALUES (600327, '000000', 4, '已驳回', 'REJECTED', 'so_contract_status', '', 'danger', 'N', 103, 1, '2026-09-18 00:00:00', NULL, NULL, '审批驳回,可修改后重新提交');
INSERT IGNORE INTO `sys_dict_data` VALUES (600328, '000000', 5, '已到期', 'EXPIRED', 'so_contract_status', '', 'danger', 'N', 103, 1, '2026-09-18 00:00:00', NULL, NULL, '失效日期已过');
INSERT IGNORE INTO `sys_dict_data` VALUES (600329, '000000', 6, '已终止', 'TERMINATED', 'so_contract_status', '', 'danger', 'N', 103, 1, '2026-09-18 00:00:00', NULL, NULL, '合同终止');

-- 4. 采购侧既有字典兜底收录(已存在的环境自动忽略)
INSERT IGNORE INTO `sys_dict_type` VALUES (600301, '000000', '合同类型', 'contract_type', 103, 1, '2026-06-27 00:23:08', NULL, NULL, '采购合同类型');
INSERT IGNORE INTO `sys_dict_data` VALUES (600301, '000000', 1, '框架合同', 'FRAMEWORK', 'contract_type', '', 'primary', 'Y', 103, 1, '2026-06-27 00:23:08', NULL, NULL, '长期框架协议');
INSERT IGNORE INTO `sys_dict_data` VALUES (600302, '000000', 2, '现货合同', 'SPOT', 'contract_type', '', 'success', 'N', 103, 1, '2026-06-27 00:23:08', NULL, NULL, '单次现货采购');
INSERT IGNORE INTO `sys_dict_data` VALUES (600303, '000000', 3, '服务合同', 'SERVICE', 'contract_type', '', 'info', 'N', 103, 1, '2026-06-27 00:23:08', NULL, NULL, '服务类合同');
INSERT IGNORE INTO `sys_dict_type` VALUES (600302, '000000', '采购合同状态', 'pu_contract_status', 103, 1, '2026-06-27 00:23:08', NULL, NULL, '采购合同状态');
INSERT IGNORE INTO `sys_dict_data` VALUES (600311, '000000', 1, '草稿', 'DRAFT', 'pu_contract_status', '', 'info', 'Y', 103, 1, '2026-06-27 00:23:08', NULL, NULL, '');
INSERT IGNORE INTO `sys_dict_data` VALUES (600312, '000000', 2, '审批中', 'APPROVING', 'pu_contract_status', '', 'warning', 'N', 103, 1, '2026-06-27 00:23:08', NULL, NULL, '');
INSERT IGNORE INTO `sys_dict_data` VALUES (600313, '000000', 3, '已批准', 'APPROVED', 'pu_contract_status', '', 'success', 'N', 103, 1, '2026-06-27 00:23:08', NULL, NULL, '');
INSERT IGNORE INTO `sys_dict_data` VALUES (600314, '000000', 4, '执行中', 'EXECUTING', 'pu_contract_status', '', 'primary', 'N', 103, 1, '2026-06-27 00:23:08', NULL, NULL, '');
INSERT IGNORE INTO `sys_dict_data` VALUES (600315, '000000', 5, '已完成', 'COMPLETED', 'pu_contract_status', '', 'success', 'N', 103, 1, '2026-06-27 00:23:08', NULL, NULL, '');
INSERT IGNORE INTO `sys_dict_data` VALUES (600316, '000000', 6, '已终止', 'TERMINATED', 'pu_contract_status', '', 'danger', 'N', 103, 1, '2026-06-27 00:23:08', NULL, NULL, '');
INSERT IGNORE INTO `sys_dict_data` VALUES (600317, '000000', 7, '已驳回', 'REJECTED', 'pu_contract_status', '', 'danger', 'N', 103, 1, '2026-06-27 00:23:08', NULL, NULL, '');
