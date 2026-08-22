-- ============================================================
-- 客户定制化发布包: 4 新表 + 4 表加列 + 字典 + 菜单权限 + 编码规则 + 审批注册
-- 库: MES 业务表 -> ry-mes 业务库 ; ERP 业务表 -> ry-erp 业务库 ;
--     字典/菜单/编码规则/审批注册 -> ry-vue 系统库
-- 日期: 2026-08-22
-- 配套方案: doc/mes/plans/2026-08-22-mes-release-package-plan.md
-- 段位避让: 字典类型 600910-600913 / 字典数据 601011-601044 (6009xx 已占);
--          菜单 4310-4320 (固件/刷写 4280-4307 已占);
--          编码规则 rule_id 294 / 部件 2058014518088437816-818 (289-293 已占);
--          wf_custom_form 2091000000000005002 (固件 …001 已占);
-- 幂等: 表/字典/菜单/编码规则/审批 INSERT IGNORE + CREATE TABLE IF NOT EXISTS;
--       ALTER TABLE 加列不幂等, 重复执行前需核对列已不存在
-- ============================================================

-- ==================== 一、ry-mes 业务库: 4 张新表 ====================

CREATE TABLE IF NOT EXISTS `mes_release_package`  (
  `package_id` bigint(20) NOT NULL COMMENT '发布包ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `package_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '发布包编码(自动编码 MES_RP_CODE)',
  `client_id` bigint(20) NOT NULL COMMENT '客户ID(bd_client.client_id)',
  `client_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户名称(冗余)',
  `material_id` bigint(20) NOT NULL COMMENT '成品物料ID(bd_material.material_id)',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码(冗余)',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称(冗余)',
  `customer_product_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户产品代号(客户对该产品的内部编号, 匹配键)',
  `customer_product_name` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户产品名称',
  `version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '版本号(如 2.1)',
  `release_notes` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '发布说明',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/IN_REVIEW/RELEASED/ARCHIVED',
  `effective_date` datetime(0) NULL DEFAULT NULL COMMENT '生效日期(空=发布即生效)',
  `expiry_date` datetime(0) NULL DEFAULT NULL COMMENT '过期日期(空=永久有效)',
  `is_default` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '是否客户+物料默认发布包(0否/1是, 客户+物料内唯一)',
  `select_flow_id` bigint(20) NULL DEFAULT NULL COMMENT '审批流配置ID(wf_custom_form.id)',
  `published_by` bigint(20) NULL DEFAULT NULL COMMENT '发布人ID',
  `publish_time` datetime(0) NULL DEFAULT NULL COMMENT '发布时间',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`package_id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_package_code`(`tenant_id`, `package_code`) USING BTREE,
  INDEX `idx_client_material`(`client_id`, `material_id`) USING BTREE,
  INDEX `idx_material`(`material_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES客户定制化发布包主表' ROW_FORMAT = DYNAMIC;
-- 说明: 同客户+物料+版本唯一由 Service 层校验(同固件先例), 不建 DB 唯一键以避开逻辑删后重建冲突

CREATE TABLE IF NOT EXISTS `mes_release_pkg_firmware`  (
  `mapping_id` bigint(20) NOT NULL COMMENT '主键ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `package_id` bigint(20) NOT NULL COMMENT '发布包ID(mes_release_package.package_id)',
  `routing_step_id` bigint(20) NULL DEFAULT NULL COMMENT '工艺路线步骤ID(bd_routing_step.step_id, 精确定位)',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT '工序ID(bd_process.process_id, 宽泛定位)',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序编码(冗余)',
  `process_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序名称(冗余)',
  `pcba_material_id` bigint(20) NULL DEFAULT NULL COMMENT 'PCBA物料ID(固件物理载体, 追溯用, 可空)',
  `pcba_material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'PCBA物料编码(冗余)',
  `pcba_material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'PCBA物料名称(冗余)',
  `firmware_id` bigint(20) NOT NULL COMMENT '固件ID(mes_firmware.firmware_id, 仅RELEASED)',
  `firmware_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '固件编码(冗余)',
  `firmware_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '固件名称(冗余)',
  `firmware_version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '固件版本(冗余)',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '排序',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`mapping_id`) USING BTREE,
  INDEX `idx_package`(`package_id`) USING BTREE,
  INDEX `idx_process`(`process_id`) USING BTREE,
  INDEX `idx_firmware`(`firmware_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES发布包固件映射表(工序→固件)' ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `mes_release_pkg_bom_item`  (
  `item_id` bigint(20) NOT NULL COMMENT '主键ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `package_id` bigint(20) NOT NULL COMMENT '发布包ID(mes_release_package.package_id)',
  `action_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作类型: REPLACE/ADD/REMOVE',
  `child_material_id` bigint(20) NULL DEFAULT NULL COMMENT '原物料ID(REPLACE/REMOVE 定位键, 按子件物料定位抗BOM版本变更)',
  `child_material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '原物料编码(冗余)',
  `child_material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '原物料名称(冗余)',
  `bom_detail_id` bigint(20) NULL DEFAULT NULL COMMENT '标准BOM明细行ID(可选精确定位, 空则按原物料定位)',
  `target_material_id` bigint(20) NULL DEFAULT NULL COMMENT '目标物料ID(REPLACE/ADD 必填)',
  `target_material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '目标物料编码(冗余)',
  `target_material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '目标物料名称(冗余)',
  `quantity_per_parent` decimal(18, 6) NULL DEFAULT NULL COMMENT '单位用量(空=REPLACE沿用原行/ADD必填)',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '排序',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`item_id`) USING BTREE,
  INDEX `idx_package`(`package_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES发布包BOM选配表(替换/新增/移除)' ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `mes_release_pkg_config`  (
  `config_id` bigint(20) NOT NULL COMMENT '主键ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `package_id` bigint(20) NOT NULL COMMENT '发布包ID(mes_release_package.package_id)',
  `config_group` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置分组: FLASH/TEST_PARAM/LABEL/PACKAGING/PROCESS',
  `config_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置键',
  `config_value` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '配置值',
  `config_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'STRING' COMMENT '值类型: STRING/NUMBER/BOOLEAN/JSON',
  `process_id` bigint(20) NULL DEFAULT NULL COMMENT '关联工序ID(空=全局生效)',
  `process_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工序编码(冗余)',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '排序',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`config_id`) USING BTREE,
  INDEX `idx_package`(`package_id`) USING BTREE,
  INDEX `idx_group`(`config_group`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES发布包配置项表(测试参数/标签/包装/工艺参数)' ROW_FORMAT = DYNAMIC;

-- ==================== 二、ry-mes 业务库: 工单/工序扩展 ====================

-- MES工单记录发布包快照(可追溯) + 配置项快照
ALTER TABLE `pro_work_order`
    ADD COLUMN `release_package_id` bigint(20) NULL DEFAULT NULL COMMENT '发布包ID(mes_release_package, 工单按此包生成)',
    ADD COLUMN `release_package_code` varchar(64) NULL DEFAULT NULL COMMENT '发布包编码(冗余)',
    ADD COLUMN `release_package_ver` varchar(32) NULL DEFAULT NULL COMMENT '发布包版本(冗余快照)',
    ADD COLUMN `release_config` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '发布包配置项快照(JSON数组, 供工位/报表消费)';

-- 工序固件来源标记(前端展示: 发布包/工艺路线/物料默认/手动指定)
ALTER TABLE `pro_work_order_step`
    ADD COLUMN `firmware_source` varchar(32) NULL DEFAULT NULL COMMENT '固件来源: PACKAGE/ROUTING/MATERIAL_DEFAULT/MANUAL';

-- ==================== 三、ry-erp 业务库: 销售订单行 / ERP工单 扩展 ====================

-- 销售订单行: 客户产品代号(MES发布包精确匹配键)
ALTER TABLE `so_sales_order_line`
    ADD COLUMN `customer_product_code` varchar(128) NULL DEFAULT NULL COMMENT '客户产品代号(用于MES发布包精确匹配)',
    ADD COLUMN `customer_product_name` varchar(256) NULL DEFAULT NULL COMMENT '客户产品名称';

-- ERP工单: 客户信息落点(生成工单时从销售订单头/行一次性落库, MQ透传给MES)
ALTER TABLE `prd_work_order`
    ADD COLUMN `client_id` bigint(20) NULL DEFAULT NULL COMMENT '客户ID(bd_client, 来源销售订单头)',
    ADD COLUMN `client_name` varchar(128) NULL DEFAULT NULL COMMENT '客户名称(冗余)',
    ADD COLUMN `customer_product_code` varchar(128) NULL DEFAULT NULL COMMENT '客户产品代号(来源销售订单行)';

-- ==================== 四、ry-vue 系统库: 字典 ====================

-- 字典类型 (600910-600913, 6009xx 段延续)
INSERT IGNORE INTO `sys_dict_type`(`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(600910, '000000', '发布包状态',   'mes_release_pkg_status', 103, 1, NOW(), NULL, NULL, 'MES发布包生命周期状态(DRAFT/IN_REVIEW/RELEASED/ARCHIVED)'),
(600911, '000000', 'BOM选配操作',  'mes_release_pkg_action', 103, 1, NOW(), NULL, NULL, 'MES发布包BOM选配操作类型(REPLACE/ADD/REMOVE)'),
(600912, '000000', '发布包配置分组', 'mes_release_cfg_group', 103, 1, NOW(), NULL, NULL, 'MES发布包配置项分组(FLASH/TEST_PARAM/LABEL/PACKAGING/PROCESS)'),
(600913, '000000', '固件来源',     'mes_firmware_source',    103, 1, NOW(), NULL, NULL, 'MES工单工序固件绑定来源(PACKAGE/ROUTING/MATERIAL_DEFAULT/MANUAL)');

-- 字典数据 (6010xx 段, 6009xx 数据段已满)
INSERT IGNORE INTO `sys_dict_data`(`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
-- mes_release_pkg_status
(601011, '000000', 1, '草稿',   'DRAFT',     'mes_release_pkg_status', NULL, 'info',    'Y', 103, 1, NOW(), NULL, NULL, '发布包状态-草稿(可编辑/提交审批)'),
(601012, '000000', 2, '审批中', 'IN_REVIEW', 'mes_release_pkg_status', NULL, 'warning', 'N', 103, 1, NOW(), NULL, NULL, '发布包状态-审批中(Flowable流转中)'),
(601013, '000000', 3, '已发布', 'RELEASED',  'mes_release_pkg_status', NULL, 'success', 'N', 103, 1, NOW(), NULL, NULL, '发布包状态-已发布(可匹配生成工单)'),
(601014, '000000', 4, '已归档', 'ARCHIVED',  'mes_release_pkg_status', NULL, 'default', 'N', 103, 1, NOW(), NULL, NULL, '发布包状态-已归档(不可再用于新工单)'),
-- mes_release_pkg_action
(601021, '000000', 1, '替换', 'REPLACE', 'mes_release_pkg_action', NULL, 'warning', 'Y', 103, 1, NOW(), NULL, NULL, '替换标准BOM中的原物料'),
(601022, '000000', 2, '新增', 'ADD',     'mes_release_pkg_action', NULL, 'primary', 'N', 103, 1, NOW(), NULL, NULL, '在BOM快照中新增一行'),
(601023, '000000', 3, '移除', 'REMOVE',  'mes_release_pkg_action', NULL, 'danger',  'N', 103, 1, NOW(), NULL, NULL, '从BOM快照中移除原物料行'),
-- mes_release_cfg_group
(601031, '000000', 1, '刷写绑定', 'FLASH',      'mes_release_cfg_group', NULL, 'primary', 'N', 103, 1, NOW(), NULL, NULL, '注入工单工序stepFlashConfig(键=flashTypeCode, 值=contentRefId)'),
(601032, '000000', 2, '测试参数', 'TEST_PARAM', 'mes_release_cfg_group', NULL, 'warning', 'N', 103, 1, NOW(), NULL, NULL, '测试工序参数(如高温85℃/老化时长)'),
(601033, '000000', 3, '标签规格', 'LABEL',      'mes_release_cfg_group', NULL, 'success', 'N', 103, 1, NOW(), NULL, NULL, '标签/铭牌模板规格'),
(601034, '000000', 4, '包装规格', 'PACKAGING',  'mes_release_cfg_group', NULL, 'info',    'N', 103, 1, NOW(), NULL, NULL, '包装/卡通箱规格'),
(601035, '000000', 5, '工艺参数', 'PROCESS',    'mes_release_cfg_group', NULL, 'default', 'N', 103, 1, NOW(), NULL, NULL, '其他工艺执行参数'),
-- mes_firmware_source
(601041, '000000', 1, '发布包',   'PACKAGE',         'mes_firmware_source', NULL, 'primary', 'N', 103, 1, NOW(), NULL, NULL, '客户定制化发布包固件映射'),
(601042, '000000', 2, '工艺路线', 'ROUTING',         'mes_firmware_source', NULL, 'warning', 'N', 103, 1, NOW(), NULL, NULL, '工艺路线步骤显式指定固件'),
(601043, '000000', 3, '物料默认', 'MATERIAL_DEFAULT', 'mes_firmware_source', NULL, 'info',    'N', 103, 1, NOW(), NULL, NULL, '物料默认RELEASED固件兜底'),
(601044, '000000', 4, '手动指定', 'MANUAL',          'mes_firmware_source', NULL, 'success', 'N', 103, 1, NOW(), NULL, NULL, '生产负责人在工单上手动更改');

-- ==================== 五、ry-vue 系统库: 菜单/按钮权限 ====================
-- MES(4000) -> 发布包管理(4310, order 14) -> 发布包档案(4311) + 按钮(4312-4320)
INSERT IGNORE INTO `sys_menu`(`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(4310, '发布包管理', 4000, 14, 'release', NULL, NULL, 1, 0, 'M', '0', '0', NULL, 'component', 103, 1, NOW(), NULL, NULL, 'MES客户定制化发布包(固件/BOM选配/配置)'),
(4311, '发布包档案', 4310, 1, 'package', 'mes/release/package/index', NULL, 1, 0, 'C', '0', '0', 'mes:release:package:list', 'component', 103, 1, NOW(), NULL, NULL, '客户定制化发布包(版本/审批/固件映射/BOM选配/配置项)'),
(4312, '发布包查询', 4311, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:release:package:query',   '#', 103, 1, NOW(), NULL, NULL, ''),
(4313, '发布包新增', 4311, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:release:package:add',     '#', 103, 1, NOW(), NULL, NULL, '新增发布包(自动编码, 状态DRAFT)'),
(4314, '发布包修改', 4311, 3, '', '', '', 1, 0, 'F', '0', '0', 'mes:release:package:edit',    '#', 103, 1, NOW(), NULL, NULL, '仅 DRAFT/ARCHIVED 可编辑'),
(4315, '发布包删除', 4311, 4, '', '', '', 1, 0, 'F', '0', '0', 'mes:release:package:remove',  '#', 103, 1, NOW(), NULL, NULL, '仅 DRAFT 可删'),
(4316, '提交审批',   4311, 5, '', '', '', 1, 0, 'F', '0', '0', 'mes:release:package:submit',  '#', 103, 1, NOW(), NULL, NULL, 'DRAFT -> IN_REVIEW (Flowable)'),
(4317, '审批通过',   4311, 6, '', '', '', 1, 0, 'F', '0', '0', 'mes:release:package:approve', '#', 103, 1, NOW(), NULL, NULL, '兜底通过: IN_REVIEW -> RELEASED'),
(4318, '审批驳回',   4311, 7, '', '', '', 1, 0, 'F', '0', '0', 'mes:release:package:reject',  '#', 103, 1, NOW(), NULL, NULL, '兜底驳回: IN_REVIEW -> DRAFT'),
(4319, '发布包归档', 4311, 8, '', '', '', 1, 0, 'F', '0', '0', 'mes:release:package:archive', '#', 103, 1, NOW(), NULL, NULL, 'RELEASED -> ARCHIVED(归档后不参与匹配)'),
(4320, '设为默认',   4311, 9, '', '', '', 1, 0, 'F', '0', '0', 'mes:release:package:default', '#', 103, 1, NOW(), NULL, NULL, '客户+物料内默认包唯一(仅RELEASED)');

-- ==================== 六、ry-vue 系统库: 编码规则 ====================
-- MES_RP_CODE  RP+yyyyMMdd+3位日循环流水 (仿 MES_FW_CODE)
INSERT IGNORE INTO `sys_auto_code_rule`(`rule_id`, `tenant_id`, `rule_code`, `rule_name`, `rule_desc`, `max_length`, `is_padded`, `padded_char`, `padded_method`, `enable_flag`, `remark`, `attr1`, `attr2`, `attr3`, `attr4`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`) VALUES
(294, '000000', 'MES_RP_CODE', 'MES发布包编码', 'RP+日期+3位日循环流水号', 15, 'N', NULL, 'L', 'Y', '', NULL, NULL, 0, 0, NULL, NULL, NOW(), NULL, NULL, '0');

INSERT IGNORE INTO `sys_auto_code_part`(`part_id`, `tenant_id`, `rule_id`, `part_index`, `part_type`, `part_code`, `part_name`, `part_length`, `date_format`, `input_character`, `fix_character`, `seria_start_no`, `seria_step`, `seria_now_no`, `cycle_flag`, `cycle_method`, `remark`, `attr1`, `attr2`, `attr3`, `attr4`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`) VALUES
-- MES_RP_CODE (294)
(2058014518088437816, '000000', 294, 1, 'FIXCHAR',  'PREFIX', '前缀',   2, NULL, NULL, 'RP', NULL, NULL, NULL, NULL, NULL, '', NULL, NULL, 0, 0, NULL, NULL, NOW(), NULL, NULL, '0'),
(2058014518088437817, '000000', 294, 2, 'NOWDATE',  'DATE',   '日期',   8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '', NULL, NULL, 0, 0, NULL, NULL, NOW(), NULL, NULL, '0'),
(2058014518088437818, '000000', 294, 3, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', '', NULL, NULL, 0, 0, NULL, NULL, NOW(), NULL, NULL, '0');

-- ==================== 七、ry-vue 系统库: 审批自定义表单注册 ====================
-- BPMN 流程本体在「流程设计」中创建并发布(同固件先例), 此处登记业务服务名, 提交审批时按 serviceName 拉取可选流程
INSERT IGNORE INTO `wf_custom_form`(`id`, `business_name`, `business_service`, `flow_name`, `deploy_id`, `route_name`, `component`, `table_id`, `create_by`, `create_time`, `update_by`, `update_time`, `tenant_id`, `create_dept`) VALUES
(2091000000000005002, '发布包发布审批', 'mesReleasePackageService', '', '', 'mes/release/package/index', '', NULL, '1', NOW(), NULL, NULL, '000000', 103);

-- ==================== 校验 ====================
-- SELECT dict_type, dict_label, dict_value FROM sys_dict_data WHERE dict_type LIKE 'mes_release%' OR dict_type='mes_firmware_source' ORDER BY dict_type, dict_sort;
-- SELECT menu_id, menu_name, parent_id, perms FROM sys_menu WHERE perms LIKE 'mes:release:%' ORDER BY menu_id;
-- SELECT rule_code, rule_name FROM sys_auto_code_rule WHERE rule_id = 294;
-- SELECT id, business_name, business_service FROM wf_custom_form WHERE business_service='mesReleasePackageService';
-- SHOW COLUMNS FROM pro_work_order LIKE 'release_%';
-- SHOW COLUMNS FROM pro_work_order_step LIKE 'firmware_source';
-- SHOW COLUMNS FROM so_sales_order_line LIKE 'customer_product%';
-- SHOW COLUMNS FROM prd_work_order LIKE 'client%';
