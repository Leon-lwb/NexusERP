-- ====================================================================
-- S2 QC 检验标准自动判定: 检验模板 + 检验项目 + 检验单明细 三张表
-- 分段执行说明(混合库脚本, 不能整文件灌进一个库):
--   第一段(ry-mes 库): 建表 + 演示种子模板/项目 + 已有 IQC 单补明细
--   第二段(ry-vue 库): 菜单/按钮(sys_menu, 挂质量管理 4002 下)
-- ====================================================================

-- ----------------------------------------------------------------------
-- 第一段 >>> ry-mes 库
-- ----------------------------------------------------------------------

-- ----------------------------
-- 1. 检验模板头
-- ----------------------------
DROP TABLE IF EXISTS `qc_inspect_template`;
CREATE TABLE `qc_inspect_template`  (
  `template_id` bigint(20) NOT NULL COMMENT '检验模板ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `template_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板编码(唯一)',
  `template_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板名称',
  `template_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板类型: IQC/IPQC/OQC',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '物料ID(空=通用模板)',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料编码(冗余)',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '物料名称(冗余)',
  `version_no` int(11) NOT NULL DEFAULT 1 COMMENT '版本号',
  `status` varchar(16) NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/ACTIVE',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`template_id`) USING BTREE,
  UNIQUE INDEX `uk_template_code`(`template_code`) USING BTREE,
  INDEX `idx_template_type`(`template_type`) USING BTREE,
  INDEX `idx_material_id`(`material_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'QC检验模板' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- 2. 检验项目
-- ----------------------------
DROP TABLE IF EXISTS `qc_inspect_item`;
CREATE TABLE `qc_inspect_item`  (
  `item_id` bigint(20) NOT NULL COMMENT '检验项目ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `template_id` bigint(20) NOT NULL COMMENT '关联qc_inspect_template.template_id',
  `item_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目编码',
  `item_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目名称',
  `data_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'QUANTITATIVE' COMMENT '数据类型: QUANTITATIVE定量/QUALITATIVE定性',
  `unit` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '计量单位',
  `standard_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '标准类型: NOMINAL名义值/LSL_ONLY/USL_ONLY/LSL_USL',
  `nominal_value` decimal(18, 6) NULL DEFAULT NULL COMMENT '名义值(NOMINAL用)',
  `lsl_value` decimal(18, 6) NULL DEFAULT NULL COMMENT '下规格限LSL',
  `usl_value` decimal(18, 6) NULL DEFAULT NULL COMMENT '上规格限USL',
  `tol_upper` decimal(18, 6) NULL DEFAULT NULL COMMENT '上公差(NOMINAL用, 名义+tolUpper)',
  `tol_lower` decimal(18, 6) NULL DEFAULT NULL COMMENT '下公差(NOMINAL用, 名义-tolLower)',
  `aql_level` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '检验水平(如II)',
  `sample_plan_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '抽样方案(如AQL1.0)',
  `criticality` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'MAJOR' COMMENT '关键度: CRITICAL关键(一票否决)/MAJOR/MINOR',
  `seq_num` int(11) NULL DEFAULT 1 COMMENT '序号',
  `enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '1' COMMENT '是否启用 1是0否',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`item_id`) USING BTREE,
  INDEX `idx_template_id`(`template_id`) USING BTREE,
  INDEX `idx_item_code`(`item_code`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'QC检验项目' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- 3. 检验单明细(挂在 IQC/IPQC/OQC 单上, 生成时快照标准)
-- ----------------------------
DROP TABLE IF EXISTS `qc_inspect_detail`;
CREATE TABLE `qc_inspect_detail`  (
  `detail_id` bigint(20) NOT NULL COMMENT '检验明细ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `check_id` bigint(20) NOT NULL COMMENT '检验单ID(qc_iqc.iqc_id/qc_ipqc.ipqc_id/qc_oqc.oqc_id)',
  `check_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '检验类别: IQC/IPQC/OQC',
  `template_id` bigint(20) NULL DEFAULT NULL COMMENT '来源模板ID(快照溯源)',
  `item_id` bigint(20) NULL DEFAULT NULL COMMENT '来源检验项目ID',
  `item_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目编码(快照)',
  `item_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目名称(快照)',
  `data_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'QUANTITATIVE' COMMENT '数据类型(快照): QUANTITATIVE/QUALITATIVE',
  `standard_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '标准类型(快照): NOMINAL/LSL_ONLY/USL_ONLY/LSL_USL',
  `standard_desc` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '标准快照描述(如10±0.05mm)',
  `unit` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位(快照)',
  `criticality` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'MAJOR' COMMENT '关键度(快照): CRITICAL/MAJOR/MINOR',
  `sample_qty` decimal(14, 4) NULL DEFAULT NULL COMMENT '样本量',
  `measured_value` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '实测值(定量:数值/多值; 定性:OK/NG等)',
  `nominal_value` decimal(18, 6) NULL DEFAULT NULL COMMENT '名义值(快照, 判定用)',
  `lsl_value` decimal(18, 6) NULL DEFAULT NULL COMMENT '下限(快照, 判定用)',
  `usl_value` decimal(18, 6) NULL DEFAULT NULL COMMENT '上限(快照, 判定用)',
  `tol_upper` decimal(18, 6) NULL DEFAULT NULL COMMENT '上公差(快照, 判定用)',
  `tol_lower` decimal(18, 6) NULL DEFAULT NULL COMMENT '下公差(快照, 判定用)',
  `judge_result` varchar(16) NULL DEFAULT 'PENDING' COMMENT '判定结果: PASS/FAIL/PENDING',
  `seq_num` int(11) NULL DEFAULT 1 COMMENT '序号',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`detail_id`) USING BTREE,
  INDEX `idx_check`(`check_type`, `check_id`) USING BTREE,
  INDEX `idx_check_id`(`check_id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'QC检验单明细' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- 4. 演示种子: 给现有 IQC 物料造 1 个含 2 个定量项目的 ACTIVE 模板
--    物料取 qc_iqc 已有种子数据(1001 铝合金板材 / 2001 钢管)
--    主键用 947xxx 固定段, INSERT IGNORE 可重复执行
-- ----------------------------
INSERT IGNORE INTO `qc_inspect_template`
(`template_id`, `tenant_id`, `template_code`, `template_name`, `template_type`, `material_id`, `material_code`, `material_name`, `version_no`, `status`, `remark`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`) VALUES
(947001, '000000', 'IQC-TPL-1001', '铝合金板材来料检验模板', 'IQC', 1001, 'MAT-1001', '铝合金板材', 1, 'ACTIVE', '演示: 2个定量项目(厚度LSL_USL/抗拉强度LSL_ONLY)', 103, 1, NOW(), NULL, NULL, '0');

INSERT IGNORE INTO `qc_inspect_item`
(`item_id`, `tenant_id`, `template_id`, `item_code`, `item_name`, `data_type`, `unit`, `standard_type`, `nominal_value`, `lsl_value`, `usl_value`, `tol_upper`, `tol_lower`, `aql_level`, `sample_plan_code`, `criticality`, `seq_num`, `enabled`, `remark`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`) VALUES
(947011, '000000', 947001, 'THK-001', '板材厚度', 'QUANTITATIVE', 'mm', 'LSL_USL', NULL, 2.900000, 3.100000, NULL, NULL, 'II', 'AQL1.0', 'CRITICAL', 1, '1', '关键尺寸, 一票否决', 103, 1, NOW(), NULL, NULL, '0'),
(947012, '000000', 947001, 'TS-001', '抗拉强度', 'QUANTITATIVE', 'MPa', 'LSL_ONLY', NULL, 310.000000, NULL, NULL, NULL, 'II', 'AQL1.0', 'MAJOR', 2, '1', NULL, 103, 1, NOW(), NULL, NULL, '0');

-- 5. 为已有 PENDING 状态的 IQC 单按模板补生成明细(幂等: 已有明细的跳过)
--    明细主键用 9471xxxxx 固定段顺序生成(雪花 iqc_id ×10 会溢出 bigint, 且 5.7 无窗口函数, 用用户变量)
SET @rn := 0;
INSERT IGNORE INTO `qc_inspect_detail`
(`detail_id`, `tenant_id`, `check_id`, `check_type`, `template_id`, `item_id`, `item_code`, `item_name`, `data_type`, `standard_type`, `standard_desc`, `unit`, `criticality`, `sample_qty`, `measured_value`, `nominal_value`, `lsl_value`, `usl_value`, `tol_upper`, `tol_lower`, `judge_result`, `seq_num`, `remark`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
SELECT 947100000 + (@rn := @rn + 1), i.tenant_id, i.iqc_id, 'IQC',
       t.template_id, it.item_id, it.item_code, it.item_name, it.data_type,
       it.standard_type,
       CASE it.standard_type
         WHEN 'LSL_USL' THEN CONCAT('大于等于', it.lsl_value, '且小于等于', it.usl_value, IFNULL(CONCAT(' ', it.unit), ''))
         WHEN 'LSL_ONLY' THEN CONCAT('大于等于', it.lsl_value, IFNULL(CONCAT(' ', it.unit), ''))
         WHEN 'USL_ONLY' THEN CONCAT('小于等于', it.usl_value, IFNULL(CONCAT(' ', it.unit), ''))
         WHEN 'NOMINAL' THEN CONCAT(it.nominal_value, '正负', GREATEST(IFNULL(it.tol_upper, 0), IFNULL(it.tol_lower, 0)), IFNULL(CONCAT(' ', it.unit), ''))
         ELSE NULL END,
       it.unit, it.criticality, NULL, NULL,
       it.nominal_value, it.lsl_value, it.usl_value, it.tol_upper, it.tol_lower,
       'PENDING', it.seq_num, NULL, 103, 1, NOW(), NULL, NULL, '0'
FROM qc_iqc i
JOIN qc_inspect_template t ON t.tenant_id = i.tenant_id AND t.template_type = 'IQC' AND t.status = 'ACTIVE'
     AND (t.material_id = i.material_id OR t.material_id IS NULL)
JOIN qc_inspect_item it ON it.template_id = t.template_id AND it.enabled = '1'
WHERE i.status IN ('PENDING', 'INSPECTING')
  AND i.del_flag = '0'
  AND NOT EXISTS (SELECT 1 FROM qc_inspect_detail d WHERE d.check_type = 'IQC' AND d.check_id = i.iqc_id)
ORDER BY i.iqc_id, it.seq_num;
SET @rn := NULL;

-- ----------------------------------------------------------------------
-- 第二段 >>> ry-vue 库 (菜单/按钮, 挂质量管理 4002 下)
--    4102~4109 与 4342~4347 均空闲, 采用 4342~4347
-- ----------------------------------------------------------------------

INSERT IGNORE INTO `sys_menu` VALUES
(4342, '检验标准模板', 4002, 4, 'template', 'mes/qc/template/index', '', 1, 0, 'C', '0', '0', 'mes:qc:template:list', 'education', 103, 1, '2026-09-02 12:00:00', NULL, NULL, 'QC检验模板(项目/标准/抽样方案)'),
(4343, '模板查询', 4342, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:qc:template:query', '#', 103, 1, '2026-09-02 12:00:00', NULL, NULL, ''),
(4344, '模板新增', 4342, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:qc:template:add', '#', 103, 1, '2026-09-02 12:00:00', NULL, NULL, ''),
(4345, '模板修改', 4342, 3, '', '', '', 1, 0, 'F', '0', '0', 'mes:qc:template:edit', '#', 103, 1, '2026-09-02 12:00:00', NULL, NULL, ''),
(4346, '模板删除', 4342, 4, '', '', '', 1, 0, 'F', '0', '0', 'mes:qc:template:remove', '#', 103, 1, '2026-09-02 12:00:00', NULL, NULL, ''),
(4347, '模板复制版本', 4342, 5, '', '', '', 1, 0, 'F', '0', '0', 'mes:qc:template:copy', '#', 103, 1, '2026-09-02 12:00:00', NULL, NULL, '生成新版本草稿');

-- ----------------------------------------------------------------------
-- 校验
-- ----------------------------------------------------------------------
-- ry-mes:  SELECT template_code, status FROM qc_inspect_template;
--          SELECT item_code, criticality FROM qc_inspect_item WHERE template_id = 947001;
--          SELECT check_id, item_code, judge_result FROM qc_inspect_detail;
-- ry-vue:  SELECT menu_id, menu_name, perms FROM sys_menu WHERE menu_id BETWEEN 4342 AND 4347;
