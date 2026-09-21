-- ============================================================================
-- 增量脚本：DHR（Device History Record / 批记录）后端补齐
-- 日期      : 2026-09-20
-- 对应审计  : doc/audit/2026-09-20-audit-mes-tg-sop.md §4 功能缺失清单第 7 项
-- 分支      : feature/erp-mes-iot-audit-fix
-- 用途      : 1) 新建 DHR 主表 dhr_record + 分册表 dhr_section（库 ry-mes）
--             2) 登记菜单与权限（库 ry-vue：sys_menu 4370-4380）
--             3) 登记字典（库 ry-vue：sys_dict_type 600212/600213、
--                sys_dict_data 600254-600258 / 600264-600273）
-- 影响库    : ry-mes（DDL）、ry-vue（DML：菜单/字典）
-- 执行前提  : 1) 已导入 script/sql/ry-mes.sql 与 script/sql/ry-vue.sql
--             2) 菜单 ID 4370-4380、字典 ID 600212/600213/600254-600258/
--                600264-600273 均已核对为空闲（ry-vue.sql 现最大菜单为 4366、
--                已用字典 data 最大 600252）
--             3) 跨库 DML 一律写全限定名（`ry-vue`.sys_menu 等），
--                因为本脚本的默认库取决于执行时的 USE 语句
-- 幂等性    : CREATE TABLE IF NOT EXISTS + INSERT IGNORE，可重复执行
-- 是否需清 Redis: 否（无缓存键依赖；菜单/字典由前端刷新或重新登录后重新拉取）
-- 回滚      : DROP TABLE `ry-mes`.`dhr_section`; DROP TABLE `ry-mes`.`dhr_record`;
--             DELETE FROM `ry-vue`.sys_menu WHERE menu_id BETWEEN 4370 AND 4380;
--             DELETE FROM `ry-vue`.sys_dict_data WHERE dict_code BETWEEN 600254 AND 600258;
--             DELETE FROM `ry-vue`.sys_dict_data WHERE dict_code BETWEEN 600264 AND 600273;
--             DELETE FROM `ry-vue`.sys_dict_type WHERE dict_id IN (600212, 600213);
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. DHR 主表（库 ry-mes）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ry-mes`.`dhr_record` (
  `dhr_id` bigint(20) NOT NULL COMMENT 'DHR主键',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `dhr_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'DHR编号',
  `work_order_id` bigint(20) NULL DEFAULT NULL COMMENT 'MES工单ID(pro_work_order.mes_wo_id)',
  `work_order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工单编号(冗余快照)',
  `material_id` bigint(20) NULL DEFAULT NULL COMMENT '产品物料ID',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '产品物料编码(冗余快照)',
  `material_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '产品物料名称(冗余快照)',
  `lot_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '批次号',
  `sn` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'SN序列号',
  `planned_qty` decimal(14, 4) NULL DEFAULT NULL COMMENT '计划数量',
  `completed_qty` decimal(14, 4) NULL DEFAULT NULL COMMENT '完成数量',
  `scrap_qty` decimal(14, 4) NULL DEFAULT NULL COMMENT '报废数量',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/SUBMITTED/RELEASED/REJECTED/ARCHIVED',
  `snapshot_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '快照总哈希(SM3)',
  `frozen_at` datetime(0) NULL DEFAULT NULL COMMENT '冻结时间(提交时写入)',
  `frozen_by` bigint(20) NULL DEFAULT NULL COMMENT '冻结人',
  `submitted_at` datetime(0) NULL DEFAULT NULL COMMENT '提交时间',
  `submitted_by` bigint(20) NULL DEFAULT NULL COMMENT '提交人',
  `submit_signature_id` bigint(20) NULL DEFAULT NULL COMMENT '提交签名ID(es_signature, biz_type=DHR_SUBMIT)',
  `released_at` datetime(0) NULL DEFAULT NULL COMMENT '放行时间',
  `released_by` bigint(20) NULL DEFAULT NULL COMMENT '放行人',
  `release_signature_id` bigint(20) NULL DEFAULT NULL COMMENT '放行签名ID(biz_type=DHR_RELEASE)',
  `rejected_at` datetime(0) NULL DEFAULT NULL COMMENT '驳回时间',
  `rejected_by` bigint(20) NULL DEFAULT NULL COMMENT '驳回人',
  `reject_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '驳回原因',
  `reject_signature_id` bigint(20) NULL DEFAULT NULL COMMENT '驳回签名ID(biz_type=DHR_REJECT)',
  `archived_at` datetime(0) NULL DEFAULT NULL COMMENT '归档时间',
  `version` int(11) NULL DEFAULT 1 COMMENT '版本号(每次状态流转+1)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除,仅DRAFT可删)',
  PRIMARY KEY (`dhr_id`) USING BTREE,
  UNIQUE INDEX `uk_dhr_no`(`tenant_id`, `dhr_no`) USING BTREE,
  INDEX `idx_dhr_wo`(`tenant_id`, `work_order_id`) USING BTREE,
  INDEX `idx_dhr_status`(`tenant_id`, `status`) USING BTREE,
  INDEX `idx_dhr_lot`(`tenant_id`, `lot_no`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'DHR批记录主表' ROW_FORMAT = Dynamic;

-- ----------------------------------------------------------------------------
-- 2. DHR 分册表（库 ry-mes；冻结时写入，append-only）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ry-mes`.`dhr_section` (
  `section_id` bigint(20) NOT NULL COMMENT '分册主键',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `dhr_id` bigint(20) NOT NULL COMMENT '所属DHR主表ID',
  `section_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分册编码(10个固定值)',
  `section_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '分册名称',
  `item_count` int(11) NULL DEFAULT 0 COMMENT '条目数(无数据时为0,分册仍落库)',
  `content_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '分册条目JSON',
  `content_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '正文哈希SM3(content_json)',
  `sort_order` int(11) NULL DEFAULT 0 COMMENT '排序号(1基)',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '写入时间',
  PRIMARY KEY (`section_id`) USING BTREE,
  UNIQUE INDEX `uk_dhr_section`(`tenant_id`, `dhr_id`, `section_code`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'DHR批记录分册(append-only)' ROW_FORMAT = Dynamic;

-- ----------------------------------------------------------------------------
-- 3. 菜单与权限（库 ry-vue；sys_menu 无 status 列以外的特殊列，整行 INSERT）
--    4370 C 菜单（parent_id=4000 MES 分组）；4371-4380 F 按钮
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `ry-vue`.`sys_menu` VALUES (4370, 'DHR批记录', 4000, 15, 'dhr', 'mes/dhr/record/index', '', 1, 0, 'C', '0', '0', 'mes:dhr:record:list', 'monitor', 103, 1, SYSDATE(), NULL, NULL, 'DHR批记录(设备历史记录)');
INSERT IGNORE INTO `ry-vue`.`sys_menu` VALUES (4371, 'DHR查询', 4370, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:dhr:record:list', '#', 103, 1, SYSDATE(), NULL, NULL, '');
INSERT IGNORE INTO `ry-vue`.`sys_menu` VALUES (4372, 'DHR详情', 4370, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:dhr:record:query', '#', 103, 1, SYSDATE(), NULL, NULL, '');
INSERT IGNORE INTO `ry-vue`.`sys_menu` VALUES (4373, 'DHR构建', 4370, 3, '', '', '', 1, 0, 'F', '0', '0', 'mes:dhr:record:add', '#', 103, 1, SYSDATE(), NULL, NULL, '');
INSERT IGNORE INTO `ry-vue`.`sys_menu` VALUES (4374, 'DHR重建', 4370, 4, '', '', '', 1, 0, 'F', '0', '0', 'mes:dhr:record:edit', '#', 103, 1, SYSDATE(), NULL, NULL, '');
INSERT IGNORE INTO `ry-vue`.`sys_menu` VALUES (4375, 'DHR删除', 4370, 5, '', '', '', 1, 0, 'F', '0', '0', 'mes:dhr:record:remove', '#', 103, 1, SYSDATE(), NULL, NULL, '');
INSERT IGNORE INTO `ry-vue`.`sys_menu` VALUES (4376, 'DHR导出', 4370, 6, '', '', '', 1, 0, 'F', '0', '0', 'mes:dhr:record:export', '#', 103, 1, SYSDATE(), NULL, NULL, '');
INSERT IGNORE INTO `ry-vue`.`sys_menu` VALUES (4377, 'DHR提交复核', 4370, 7, '', '', '', 1, 0, 'F', '0', '0', 'mes:dhr:record:submit', '#', 103, 1, SYSDATE(), NULL, NULL, '需二级认证电子签名');
INSERT IGNORE INTO `ry-vue`.`sys_menu` VALUES (4378, 'DHR放行', 4370, 8, '', '', '', 1, 0, 'F', '0', '0', 'mes:dhr:record:release', '#', 103, 1, SYSDATE(), NULL, NULL, '需二级认证电子签名');
INSERT IGNORE INTO `ry-vue`.`sys_menu` VALUES (4379, 'DHR驳回', 4370, 9, '', '', '', 1, 0, 'F', '0', '0', 'mes:dhr:record:reject', '#', 103, 1, SYSDATE(), NULL, NULL, '需二级认证电子签名');
INSERT IGNORE INTO `ry-vue`.`sys_menu` VALUES (4380, 'DHR归档', 4370, 10, '', '', '', 1, 0, 'F', '0', '0', 'mes:dhr:record:archive', '#', 103, 1, SYSDATE(), NULL, NULL, '');

-- ----------------------------------------------------------------------------
-- 4. 字典（库 ry-vue）
--    注意: sys_dict_type / sys_dict_data 没有 status 列，字段以 ry-vue.sql 现有整行为准
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `ry-vue`.`sys_dict_type` VALUES (600212, '000000', 'DHR批记录状态', 'mes_dhr_status', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'DHR批记录状态(DRAFT/SUBMITTED/RELEASED/REJECTED/ARCHIVED)');
INSERT IGNORE INTO `ry-vue`.`sys_dict_type` VALUES (600213, '000000', 'DHR批记录分册', 'mes_dhr_section', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'DHR批记录10个固定分册编码');

INSERT IGNORE INTO `ry-vue`.`sys_dict_data` VALUES (600254, '000000', 1, '草稿', 'DRAFT', 'mes_dhr_status', NULL, 'info', 'Y', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '可重建/可删除，尚未冻结');
INSERT IGNORE INTO `ry-vue`.`sys_dict_data` VALUES (600255, '000000', 2, '已提交', 'SUBMITTED', 'mes_dhr_status', NULL, 'primary', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '已签名冻结，快照不可变');
INSERT IGNORE INTO `ry-vue`.`sys_dict_data` VALUES (600256, '000000', 3, '已放行', 'RELEASED', 'mes_dhr_status', NULL, 'success', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '已签名放行');
INSERT IGNORE INTO `ry-vue`.`sys_dict_data` VALUES (600257, '000000', 4, '已驳回', 'REJECTED', 'mes_dhr_status', NULL, 'danger', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '可退回 DRAFT 重建');
INSERT IGNORE INTO `ry-vue`.`sys_dict_data` VALUES (600258, '000000', 5, '已归档', 'ARCHIVED', 'mes_dhr_status', NULL, 'warning', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '终态，不可再流转');

INSERT IGNORE INTO `ry-vue`.`sys_dict_data` VALUES (600264, '000000', 1, '工单信息', 'WO_INFO', 'mes_dhr_section', NULL, 'primary', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'pro_work_order/pro_work_order_step/pro_work_order_bom_line');
INSERT IGNORE INTO `ry-vue`.`sys_dict_data` VALUES (600265, '000000', 2, '生产过程', 'PROCESS', 'mes_dhr_section', NULL, 'primary', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'pro_flow_card/pro_flow_card_step/pro_dispatch/mes_station_report');
INSERT IGNORE INTO `ry-vue`.`sys_dict_data` VALUES (600266, '000000', 3, '物料批次', 'MATERIAL', 'mes_dhr_section', NULL, 'primary', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'lt_wo_consumption/lt_lot/mes_material_load');
INSERT IGNORE INTO `ry-vue`.`sys_dict_data` VALUES (600267, '000000', 4, '设备记录', 'EQUIPMENT', 'mes_dhr_section', NULL, 'primary', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'mes_station_device/eq_maintenance_record/eq_repair/eq_inspection_record');
INSERT IGNORE INTO `ry-vue`.`sys_dict_data` VALUES (600268, '000000', 5, '工装记录', 'TOOLING', 'mes_dhr_section', NULL, 'primary', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'tg_usage_record/tg_tooling(含校准字段)');
INSERT IGNORE INTO `ry-vue`.`sys_dict_data` VALUES (600269, '000000', 6, '作业指导书', 'SOP', 'mes_dhr_section', NULL, 'primary', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'sop_view_log + es_signature(SOP_VIEW)摘要');
INSERT IGNORE INTO `ry-vue`.`sys_dict_data` VALUES (600270, '000000', 7, '质量检验', 'QC', 'mes_dhr_section', NULL, 'primary', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'qc_iqc/qc_ipqc/qc_oqc/qc_inspect_detail');
INSERT IGNORE INTO `ry-vue`.`sys_dict_data` VALUES (600271, '000000', 8, '数据采集', 'DATA_COLLECT', 'mes_dhr_section', NULL, 'primary', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'dc_data_record');
INSERT IGNORE INTO `ry-vue`.`sys_dict_data` VALUES (600272, '000000', 9, 'SN追溯', 'SN_TRACE', 'mes_dhr_section', NULL, 'primary', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'pro_sn/lt_sn_binding/mes_sn_bind_log');
INSERT IGNORE INTO `ry-vue`.`sys_dict_data` VALUES (600273, '000000', 10, '不合格品处理', 'NCR', 'mes_dhr_section', NULL, 'primary', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'ncr_order/ncr_defect/ncr_rework_order');
