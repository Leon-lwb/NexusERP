-- ============================================================
-- M1 固件管理: 4 表建表 + pro_work_order_step 扩展 + 字典 + 菜单权限 + 编码规则 + 配置 + 审批注册
-- 库: MES 业务表 -> ry-erp 业务库(ry-mes 所在库) ; 字典/菜单/编码规则/配置 -> ry-vue 系统库
-- 日期: 2026-08-13
-- 配套方案: doc/mes/plans/2026-08-13-mes-firmware-flash-test-plan-v2.0.md
-- 段位避让: 字典 6009xx (MES SOP=雪花ID / F2资产=6008xx / F0=6005xx / F1=6006xx);
--          菜单 4280-4299 (MES 4000 下, 4200-4218 pro, 4241-4275 NCR/SOP 已占);
--          编码规则 rule_id 289-291 (286-288 MES 已占);
-- 幂等: 全部 INSERT IGNORE / CREATE TABLE IF NOT EXISTS
-- ============================================================

-- ==================== 一、MES 业务库: 4 张新表 + 工单工序扩展 ====================

CREATE TABLE IF NOT EXISTS `fw_firmware`  (
  `firmware_id` bigint(20) NOT NULL COMMENT '固件ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `firmware_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '固件编码(自动编码 MES_FW_CODE)',
  `firmware_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '固件名称',
  `material_id` bigint(20) NOT NULL COMMENT '适用物料ID(产品)',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '适用物料编码',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '适用物料名称',
  `version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '版本号(如 1.2.3)',
  `build_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '构建号',
  `oss_id` bigint(20) NULL DEFAULT NULL COMMENT '固件文件OSS文件ID(sys_oss)',
  `file_md5` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '固件文件MD5',
  `file_size` bigint(20) NULL DEFAULT NULL COMMENT '固件文件大小(字节)',
  `release_note` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '发布说明',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/IN_REVIEW/RELEASED/ARCHIVED',
  `is_default` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '是否物料默认版本(0否/1是, 物料内唯一)',
  `select_flow_id` bigint(20) NULL DEFAULT NULL COMMENT '审批流配置ID(wf_custom_form.id)',
  `published_by` bigint(20) NULL DEFAULT NULL COMMENT '发布人ID',
  `publish_time` datetime NULL DEFAULT NULL COMMENT '发布时间',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`firmware_id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_firmware_code`(`tenant_id`, `firmware_code`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE,
  INDEX `idx_material`(`material_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '固件档案' ROW_FORMAT = DYNAMIC;

-- 工单工序扩展: 工序类型打标 + FLASH 工序固件绑定 (M2 门禁前置)
ALTER TABLE `pro_work_order_step`
    ADD COLUMN `step_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'GENERAL' COMMENT '工序类型: GENERAL/FLASH/TEST' AFTER `sequence_num`,
    ADD COLUMN `firmware_id` bigint(20) NULL DEFAULT NULL COMMENT '固件ID(仅FLASH工序, 只可选RELEASED)' AFTER `step_type`;

CREATE TABLE IF NOT EXISTS `fw_flash_record`  (
  `flash_record_id` bigint(20) NOT NULL COMMENT '刷写记录ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `flash_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '刷写记录号(自动编码 MES_FLASH_NO)',
  `sn_id` bigint(20) NOT NULL COMMENT 'SN ID',
  `sn_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SN编码',
  `mes_wo_id` bigint(20) NOT NULL COMMENT 'MES工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工单号',
  `card_id` bigint(20) NULL DEFAULT NULL COMMENT '流转卡ID',
  `step_record_id` bigint(20) NULL DEFAULT NULL COMMENT '流转卡工序记录ID(pro_flow_card_step)',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT '工序ID',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序编码',
  `process_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序名称',
  `firmware_id` bigint(20) NOT NULL COMMENT '固件ID',
  `firmware_version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '固件版本',
  `file_md5` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '实际刷写文件MD5',
  `equipment_id` bigint(20) NULL DEFAULT NULL COMMENT '刷写设备ID(eq_equipment)',
  `equipment_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '刷写设备编码',
  `operator_id` bigint(20) NULL DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '操作人姓名',
  `flash_time` datetime NULL DEFAULT NULL COMMENT '刷写时间',
  `result` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '结果: PASS/FAIL',
  `fail_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '失败原因',
  `retry_count` int(11) NOT NULL DEFAULT 0 COMMENT '重试次数',
  `flash_mode` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MANUAL' COMMENT '刷写模式: AUTO/MANUAL',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`flash_record_id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_flash_no`(`tenant_id`, `flash_no`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE,
  INDEX `idx_sn`(`sn_id`) USING BTREE,
  INDEX `idx_firmware`(`firmware_id`) USING BTREE,
  INDEX `idx_wo`(`mes_wo_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '固件刷写记录(SN级)' ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `fw_test_item`  (
  `test_item_id` bigint(20) NOT NULL COMMENT '测试项ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `item_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '测试项编码(如 RF-TXPWR)',
  `item_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '测试项名称',
  `material_id` bigint(20) NOT NULL COMMENT '适用物料ID',
  `firmware_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '固件ID(0=该物料所有版本通用)',
  `data_type` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NUMERIC' COMMENT '数据类型: NUMERIC/TEXT/ENUM',
  `unit` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位',
  `upper_limit` decimal(18, 4) NULL DEFAULT NULL COMMENT '上限',
  `lower_limit` decimal(18, 4) NULL DEFAULT NULL COMMENT '下限',
  `target_value` decimal(18, 4) NULL DEFAULT NULL COMMENT '目标值',
  `pass_enum` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'ENUM合格值(JSON数组)',
  `is_required` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '1' COMMENT '是否必测(0否/1是)',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '排序',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`test_item_id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_material_item`(`tenant_id`, `material_id`, `firmware_id`, `item_code`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE,
  INDEX `idx_material`(`material_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '测试项标准主数据' ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `fw_test_record`  (
  `test_record_id` bigint(20) NOT NULL COMMENT '测试记录ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `test_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '测试单号(一次SN测试一条单号, 共享多行)',
  `sn_id` bigint(20) NOT NULL COMMENT 'SN ID',
  `sn_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SN编码',
  `mes_wo_id` bigint(20) NOT NULL COMMENT 'MES工单ID',
  `wo_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工单号',
  `card_id` bigint(20) NULL DEFAULT NULL COMMENT '流转卡ID',
  `step_record_id` bigint(20) NULL DEFAULT NULL COMMENT '流转卡工序记录ID(测试工序)',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT '工序ID',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序编码',
  `process_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序名称',
  `firmware_id` bigint(20) NULL DEFAULT NULL COMMENT '测试时固件ID',
  `firmware_version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '测试时固件版本',
  `test_item_id` bigint(20) NOT NULL COMMENT '测试项ID',
  `item_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '测试项编码(快照)',
  `item_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '测试项名称(快照)',
  `data_type` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据类型(快照)',
  `unit` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位',
  `numeric_value` decimal(18, 4) NULL DEFAULT NULL COMMENT '实测数值',
  `text_value` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '实测文本',
  `upper_limit` decimal(18, 4) NULL DEFAULT NULL COMMENT '上限(快照)',
  `lower_limit` decimal(18, 4) NULL DEFAULT NULL COMMENT '下限(快照)',
  `target_value` decimal(18, 4) NULL DEFAULT NULL COMMENT '目标值(快照)',
  `pass_enum` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'ENUM合格值(快照)',
  `result` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '结果: PASS/FAIL',
  `tester_id` bigint(20) NULL DEFAULT NULL COMMENT '测试人ID',
  `tester_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '测试人姓名',
  `equipment_id` bigint(20) NULL DEFAULT NULL COMMENT '测试设备ID',
  `equipment_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '测试设备编码',
  `test_time` datetime NULL DEFAULT NULL COMMENT '测试时间',
  `test_mode` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MANUAL' COMMENT '测试模式: AUTO/MANUAL',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`test_record_id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_test_no_item`(`tenant_id`, `test_no`, `test_item_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE,
  INDEX `idx_sn`(`sn_id`) USING BTREE,
  INDEX `idx_firmware`(`firmware_id`) USING BTREE,
  INDEX `idx_wo`(`mes_wo_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '测试记录(SN级全检)' ROW_FORMAT = DYNAMIC;

-- ==================== 二、ry-vue 系统库: 字典 ====================

-- 字典类型 (6009xx)
INSERT IGNORE INTO `sys_dict_type`(`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(600901, '000000', '工序类型',     'mes_step_type',      103, 1, NOW(), NULL, NULL, 'MES工单工序类型(GENERAL/FLASH/TEST)'),
(600902, '000000', '固件状态',     'mes_fw_status',      103, 1, NOW(), NULL, NULL, 'MES固件生命周期状态(DRAFT/IN_REVIEW/RELEASED/ARCHIVED)'),
(600903, '000000', '刷写/测试模式', 'mes_fw_mode',        103, 1, NOW(), NULL, NULL, 'MES固件刷写/测试模式(AUTO/MANUAL)'),
(600904, '000000', '刷写/测试结果', 'mes_fw_result',      103, 1, NOW(), NULL, NULL, 'MES固件刷写/测试结果(PASS/FAIL)'),
(600905, '000000', '测试数据类型', 'mes_test_data_type', 103, 1, NOW(), NULL, NULL, 'MES测试项数据类型(NUMERIC/TEXT/ENUM)');

-- 字典数据 (6009xx)
INSERT IGNORE INTO `sys_dict_data`(`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
-- mes_step_type
(600911, '000000', 1, '普通',   'GENERAL', 'mes_step_type', NULL, 'info',    'Y', 103, 1, NOW(), NULL, NULL, '普通工序(默认,无固件/测试语义)'),
(600912, '000000', 2, '固件刷写', 'FLASH',   'mes_step_type', NULL, 'primary', 'N', 103, 1, NOW(), NULL, NULL, '固件刷写工序(必绑RELEASED固件)'),
(600913, '000000', 3, '整机测试', 'TEST',    'mes_step_type', NULL, 'warning', 'N', 103, 1, NOW(), NULL, NULL, '整机测试工序(全检门禁)'),
-- mes_fw_status
(600921, '000000', 1, '草稿',   'DRAFT',     'mes_fw_status', NULL, 'info',    'Y', 103, 1, NOW(), NULL, NULL, '固件状态-草稿(可编辑/上传/提交审批)'),
(600922, '000000', 2, '审批中', 'IN_REVIEW', 'mes_fw_status', NULL, 'warning', 'N', 103, 1, NOW(), NULL, NULL, '固件状态-审批中(Flowable流转中)'),
(600923, '000000', 3, '已发布', 'RELEASED',  'mes_fw_status', NULL, 'success', 'N', 103, 1, NOW(), NULL, NULL, '固件状态-已发布(可绑定/刷写/征集)'),
(600924, '000000', 4, '已归档', 'ARCHIVED',  'mes_fw_status', NULL, 'default', 'N', 103, 1, NOW(), NULL, NULL, '固件状态-已归档(废止终态,不可再绑定)'),
-- mes_fw_mode
(600931, '000000', 1, '自动', 'AUTO',   'mes_fw_mode', NULL, 'primary', 'Y', 103, 1, NOW(), NULL, NULL, '设备/软件自动确认'),
(600932, '000000', 2, '人工', 'MANUAL', 'mes_fw_mode', NULL, 'info',    'N', 103, 1, NOW(), NULL, NULL, '人工确认'),
-- mes_fw_result
(600941, '000000', 1, '合格',   'PASS', 'mes_fw_result', NULL, 'success', 'Y', 103, 1, NOW(), NULL, NULL, '合格'),
(600942, '000000', 2, '不合格', 'FAIL', 'mes_fw_result', NULL, 'danger',  'N', 103, 1, NOW(), NULL, NULL, '不合格'),
-- mes_test_data_type
(600951, '000000', 1, '数值', 'NUMERIC', 'mes_test_data_type', NULL, 'primary', 'Y', 103, 1, NOW(), NULL, NULL, '数值(上下限判定)'),
(600952, '000000', 2, '文本', 'TEXT',    'mes_test_data_type', NULL, 'info',    'N', 103, 1, NOW(), NULL, NULL, '文本(仅记录)'),
(600953, '000000', 3, '枚举', 'ENUM',    'mes_test_data_type', NULL, 'warning', 'N', 103, 1, NOW(), NULL, NULL, '枚举(pass_enum 判定)');

-- ==================== 三、ry-vue 系统库: 菜单/按钮权限 ====================
-- MES(4000) -> 固件管理(4280, order 13) -> 固件档案(4281) + 按钮(4286-4295)
-- 4282-4285 保留: 刷写作业/测试作业/测试记录/测试项标准 (M2/M3 挂菜单)
INSERT IGNORE INTO `sys_menu`(`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(4280, '固件管理', 4000, 13, 'fw', NULL, NULL, 1, 0, 'M', '0', '0', NULL, 'cpu', 103, 1, NOW(), NULL, NULL, 'MES固件管理(档案/刷写/测试)'),
(4281, '固件档案', 4280, 1, 'firmware', 'mes/fw/firmware/index', NULL, 1, 0, 'C', '0', '0', 'mes:fw:firmware:list', 'cpu', 103, 1, NOW(), NULL, NULL, '固件档案主档(上传/审批/版本/归档)'),
(4286, '固件查询',  4281, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:fw:firmware:query',   '#', 103, 1, NOW(), NULL, NULL, ''),
(4287, '固件新增',  4281, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:fw:firmware:add',     '#', 103, 1, NOW(), NULL, NULL, '新增固件(自动编码,状态DRAFT)'),
(4288, '固件修改',  4281, 3, '', '', '', 1, 0, 'F', '0', '0', 'mes:fw:firmware:edit',    '#', 103, 1, NOW(), NULL, NULL, 'DRAFT/ARCHIVED 可编辑'),
(4289, '固件删除',  4281, 4, '', '', '', 1, 0, 'F', '0', '0', 'mes:fw:firmware:remove',  '#', 103, 1, NOW(), NULL, NULL, '仅 DRAFT 可删;IN_REVIEW/RELEASED 禁删'),
(4290, '固件上传',  4281, 5, '', '', '', 1, 0, 'F', '0', '0', 'mes:fw:firmware:upload',  '#', 103, 1, NOW(), NULL, NULL, '上传固件文件(MinIO,MD5/大小回填)'),
(4291, '提交审批',  4281, 6, '', '', '', 1, 0, 'F', '0', '0', 'mes:fw:firmware:submit',  '#', 103, 1, NOW(), NULL, NULL, 'DRAFT -> IN_REVIEW (Flowable)'),
(4292, '审批通过',  4281, 7, '', '', '', 1, 0, 'F', '0', '0', 'mes:fw:firmware:approve', '#', 103, 1, NOW(), NULL, NULL, '兜底通过: IN_REVIEW -> RELEASED'),
(4293, '审批驳回',  4281, 8, '', '', '', 1, 0, 'F', '0', '0', 'mes:fw:firmware:reject',  '#', 103, 1, NOW(), NULL, NULL, '兜底驳回: IN_REVIEW -> DRAFT'),
(4294, '固件归档',  4281, 9, '', '', '', 1, 0, 'F', '0', '0', 'mes:fw:firmware:archive', '#', 103, 1, NOW(), NULL, NULL, 'RELEASED -> ARCHIVED(被绑定时禁归档)'),
(4295, '设为默认',  4281, 10, '', '', '', 1, 0, 'F', '0', '0', 'mes:fw:firmware:default', '#', 103, 1, NOW(), NULL, NULL, '物料内默认版本唯一(仅RELEASED)');

-- ==================== 四、ry-vue 系统库: 编码规则 ====================
-- MES_FW_CODE      FW+yyyyMMdd+3位日循环流水 (仿 MES_SOP_CODE)
-- MES_FLASH_NO     FL+yyyyMMdd+4位日循环流水
-- MES_TEST_NO      FT+yyyyMMdd+4位日循环流水
INSERT IGNORE INTO `sys_auto_code_rule`(`rule_id`, `tenant_id`, `rule_code`, `rule_name`, `rule_desc`, `max_length`, `is_padded`, `padded_char`, `padded_method`, `enable_flag`, `remark`, `attr1`, `attr2`, `attr3`, `attr4`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`) VALUES
(289, '000000', 'MES_FW_CODE',    'MES固件编码',   'FW+日期+3位日循环流水号', 15, 'N', NULL, 'L', 'Y', '', NULL, NULL, 0, 0, NULL, NULL, NOW(), NULL, NULL, '0'),
(290, '000000', 'MES_FLASH_NO',   'MES刷写记录号', 'FL+日期+4位日循环流水号', 14, 'N', NULL, 'L', 'Y', '', NULL, NULL, 0, 0, NULL, NULL, NOW(), NULL, NULL, '0'),
(291, '000000', 'MES_TEST_NO',    'MES测试单号',   'FT+日期+4位日循环流水号', 14, 'N', NULL, 'L', 'Y', '', NULL, NULL, 0, 0, NULL, NULL, NOW(), NULL, NULL, '0');

INSERT IGNORE INTO `sys_auto_code_part`(`part_id`, `tenant_id`, `rule_id`, `part_index`, `part_type`, `part_code`, `part_name`, `part_length`, `date_format`, `input_character`, `fix_character`, `seria_start_no`, `seria_step`, `seria_now_no`, `cycle_flag`, `cycle_method`, `remark`, `attr1`, `attr2`, `attr3`, `attr4`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`) VALUES
-- MES_FW_CODE (289)
(2058014518088437800, '000000', 289, 1, 'FIXCHAR',  'PREFIX', '前缀',   2, NULL, NULL, 'FW', NULL, NULL, NULL, NULL, NULL, '', NULL, NULL, 0, 0, NULL, NULL, NOW(), NULL, NULL, '0'),
(2058014518088437801, '000000', 289, 2, 'NOWDATE',  'DATE',   '日期',   8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '', NULL, NULL, 0, 0, NULL, NULL, NOW(), NULL, NULL, '0'),
(2058014518088437802, '000000', 289, 3, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', '', NULL, NULL, 0, 0, NULL, NULL, NOW(), NULL, NULL, '0'),
-- MES_FLASH_NO (290)
(2058014518088437803, '000000', 290, 1, 'FIXCHAR',  'PREFIX', '前缀',   2, NULL, NULL, 'FL', NULL, NULL, NULL, NULL, NULL, '', NULL, NULL, 0, 0, NULL, NULL, NOW(), NULL, NULL, '0'),
(2058014518088437804, '000000', 290, 2, 'NOWDATE',  'DATE',   '日期',   8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '', NULL, NULL, 0, 0, NULL, NULL, NOW(), NULL, NULL, '0'),
(2058014518088437805, '000000', 290, 3, 'SERIALNO', 'SERIAL', '流水号', 4, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', '', NULL, NULL, 0, 0, NULL, NULL, NOW(), NULL, NULL, '0'),
-- MES_TEST_NO (291)
(2058014518088437806, '000000', 291, 1, 'FIXCHAR',  'PREFIX', '前缀',   2, NULL, NULL, 'FT', NULL, NULL, NULL, NULL, NULL, '', NULL, NULL, 0, 0, NULL, NULL, NOW(), NULL, NULL, '0'),
(2058014518088437807, '000000', 291, 2, 'NOWDATE',  'DATE',   '日期',   8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '', NULL, NULL, 0, 0, NULL, NULL, NOW(), NULL, NULL, '0'),
(2058014518088437808, '000000', 291, 3, 'SERIALNO', 'SERIAL', '流水号', 4, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', '', NULL, NULL, 0, 0, NULL, NULL, NOW(), NULL, NULL, '0');

-- ==================== 五、ry-vue 系统库: 配置种子 ====================
INSERT IGNORE INTO `sys_config`(`config_id`, `tenant_id`, `config_name`, `config_key`, `config_value`, `config_type`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(2091000000000005000, '000000', '固件刷写/测试门禁模式', 'mes.fw.gate.mode', 'FORCE', 'Y', 103, 1, NOW(), NULL, NULL, 'M2门禁开关: FORCE=FAIL拦截 / WARN=告警放行(无SN工单始终放行)');

-- ==================== 六、ry-vue 系统库: 审批自定义表单注册 ====================
-- BPMN 流程本体在「流程设计」中创建并发布(同 SOP/工单), 此处登记业务服务名, 提交审批时按 serviceName 拉取可选流程
INSERT IGNORE INTO `wf_custom_form`(`id`, `business_name`, `business_service`, `flow_name`, `deploy_id`, `route_name`, `component`, `table_id`, `create_by`, `create_time`, `update_by`, `update_time`, `tenant_id`, `create_dept`) VALUES
(2091000000000005001, '固件发布审批', 'fwFirmwareService', '', '', 'mes/fw/firmware/index', '', NULL, '1', NOW(), NULL, NULL, '000000', 103);

-- ==================== 校验 ====================
-- SELECT dict_type, dict_label, dict_value FROM sys_dict_data WHERE dict_type LIKE 'mes_fw%' OR dict_type='mes_step_type' OR dict_type='mes_test_data_type' ORDER BY dict_type, dict_sort;
-- SELECT menu_id, menu_name, parent_id, perms FROM sys_menu WHERE perms LIKE 'mes:fw:%' ORDER BY menu_id;
-- SELECT rule_code, rule_name FROM sys_auto_code_rule WHERE rule_id BETWEEN 289 AND 291;
-- SELECT config_key, config_value FROM sys_config WHERE config_key='mes.fw.gate.mode';
-- SELECT id, business_name, business_service FROM wf_custom_form WHERE business_service='fwFirmwareService';