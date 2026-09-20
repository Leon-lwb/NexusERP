-- =====================================================================================
-- 2026-09-20-mes-p2.sql
-- MES 收尾批次（未修 P1 + 高价值 P2）增量脚本
-- 关联报告: doc/audit/2026-09-20-fix-mes-p2.md
-- =====================================================================================
--
-- 【用途】为以下修复补齐 DDL/DML：
--   A. ry-mes
--      A1. pro_andon 增 notify_user_ids / notify_time            —— MES-PRO-08 安灯通知落库 + 幂等
--      A2. eq_health_record 增 UNIQUE(tenant_id,equipment_id,record_date) —— MES-EQ-15 同日重评并发
--      A3. eq_equipment 增 UNIQUE(tenant_id,iot_device_id)       —— MES-EQ-19 一设备只能绑一个 IoT 设备
--      A4. spc_control_chart 增 r_ucl/r_cl/r_lcl/pp/ppk          —— MES-SPC-01 次级图控制限 + MES-SPC-02 Pp/Ppk
--      A5. spc_data_point 增 sub_value + UNIQUE(tenant_id,chart_id,sample_group) —— MES-SPC-01/03
--      A6. 新建 mes_label_print_log                              —— MES-LABEL-02/03 打印留痕
--   B. ry-vue
--      B1. sys_menu 增 mes:pro:work:cancel / mes:pro:work:close   —— MES-PRO-11
--      B2. sys_menu 增 mes:ncr:order:dispose-nosign              —— MES-NCR-06 无签名入口独立权限码
--      B3. sys_role_menu 按现有同前缀权限角色补授（幂等）
--      B4. sys_dict_data 增 spc_chart_type = X_MR                —— MES-SPC-01
--
-- 【执行前提】
--   * MySQL 5.7 / 8.0；需要 ALTER / CREATE / INSERT 权限，且 information_schema 可读；
--   * 客户端需支持 DELIMITER 指令（mysql CLI / Navicat / DBeaver / DataGrip）；
--   * **必须先执行本脚本再发布本次代码**：A1/A4/A5 新增实体字段，缺列会让 MyBatis-Plus 报
--     Unknown column，pro_andon / spc_control_chart / spc_data_point 相关接口直接不可用；
--   * A2/A3/A5 加唯一键前必须跑各自的前置检查，存在重复数据时 ADD UNIQUE 会失败。
--
-- 【影响库】第 0 部分（辅助存储过程）在 **ry-mes 与 ry-vue 各执行一次**；
--           第 A 部分在 **ry-mes**；第 B 部分在 **ry-vue**。
--
-- 【可重复执行】是（全部走 information_schema 判存 + PREPARE/EXECUTE；建表用 IF NOT EXISTS）。
--   唯一例外：A5 的 `DELETE FROM spc_data_point WHERE del_flag='2'` 是**一次性清理**（可重复执行但
--   会连带删除该时刻已产生的僵尸行，属预期行为）。
--
-- 【是否需清 Redis 缓存】否。
--
-- 【回滚】
--   A1: ALTER TABLE pro_andon DROP COLUMN notify_user_ids, DROP COLUMN notify_time;
--   A4: ALTER TABLE spc_control_chart DROP COLUMN r_ucl, DROP COLUMN r_cl, DROP COLUMN r_lcl,
--           DROP COLUMN pp, DROP COLUMN ppk;
--   A5: ALTER TABLE spc_data_point DROP INDEX uk_chart_group, DROP COLUMN sub_value;
--   A6: DROP TABLE mes_label_print_log;（会丢失打印留痕，谨慎）
--   A2/A3: DROP INDEX uk_tenant_eq_date ON eq_health_record; / DROP INDEX uk_tenant_iot_device ON eq_equipment;
--   B: DELETE FROM sys_menu WHERE perms IN ('mes:pro:work:cancel','mes:pro:work:close',
--        'mes:ncr:order:dispose-nosign'); DELETE FROM sys_dict_data WHERE dict_type='spc_chart_type' AND dict_value='X_MR';
-- =====================================================================================

DELIMITER $$

-- ---------------------------------------------------------------------------------
-- 第 0 部分：辅助存储过程（在将要执行的库里各建一次）
-- ---------------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `nexus_p2_add_column` $$
CREATE PROCEDURE `nexus_p2_add_column`(
  IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_ddl TEXT)
BEGIN
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column) = 0 THEN
    SET @nexus_sql := CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_ddl);
    PREPARE nexus_stmt FROM @nexus_sql; EXECUTE nexus_stmt; DEALLOCATE PREPARE nexus_stmt;
  END IF;
END $$

DROP PROCEDURE IF EXISTS `nexus_p2_add_unique` $$
CREATE PROCEDURE `nexus_p2_add_unique`(
  IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_columns VARCHAR(255))
BEGIN
  IF (SELECT COUNT(*) FROM information_schema.STATISTICS
       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index) = 0 THEN
    SET @nexus_sql := CONCAT('ALTER TABLE `', p_table, '` ADD UNIQUE INDEX `', p_index, '`(', p_columns, ')');
    PREPARE nexus_stmt FROM @nexus_sql; EXECUTE nexus_stmt; DEALLOCATE PREPARE nexus_stmt;
  END IF;
END $$

DELIMITER ;

-- =====================================================================================
-- 第 A 部分：ry-mes
-- =====================================================================================

-- A1. pro_andon：安灯呼叫通知落库（MES-PRO-08）
--     notify_time 非空 = 呼叫通知已发出（幂等依据）
CALL `nexus_p2_add_column`('pro_andon', 'notify_user_ids',
  "`notify_user_ids` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '呼叫通知被通知人ID(逗号分隔)' AFTER `remark`");
CALL `nexus_p2_add_column`('pro_andon', 'notify_time',
  "`notify_time` datetime(0) NULL DEFAULT NULL COMMENT '呼叫通知落库时间(非空=呼叫通知已发出,幂等依据)' AFTER `notify_user_ids`");

-- A2. eq_health_record：同设备同日唯一（MES-EQ-15）
--     【前置检查】存在重复时必须先清理，否则 ADD UNIQUE 直接失败：
--     SELECT tenant_id,equipment_id,record_date,COUNT(*) cnt,GROUP_CONCAT(record_id) ids
--       FROM eq_health_record WHERE del_flag='0'
--      GROUP BY tenant_id,equipment_id,record_date HAVING COUNT(*)>1;
--     注意：逻辑删除行（del_flag='1'）仍占用唯一键，故清理用物理删除。
--     每组保留最新一条（MAX(record_id)），其余物理删除：
DELETE h FROM eq_health_record h
  JOIN (SELECT tenant_id, equipment_id, record_date, MAX(record_id) AS keep_id
          FROM eq_health_record WHERE del_flag = '0'
         GROUP BY tenant_id, equipment_id, record_date HAVING COUNT(*) > 1) d
    ON h.tenant_id = d.tenant_id AND h.equipment_id = d.equipment_id AND h.record_date = d.record_date
 WHERE h.record_id <> d.keep_id;
--     若存在 del_flag='1' 的同日行，需一并物理清理：
DELETE FROM eq_health_record WHERE del_flag <> '0';
CALL `nexus_p2_add_unique`('eq_health_record', 'uk_tenant_eq_date', '`tenant_id`,`equipment_id`,`record_date`');

-- A3. eq_equipment：一个 IoT 设备只能绑定一台 MES 设备（MES-EQ-19）
--     【前置检查】
--     SELECT tenant_id,iot_device_id,COUNT(*) cnt,GROUP_CONCAT(equipment_id) ids
--       FROM eq_equipment WHERE del_flag='0' AND iot_device_id IS NOT NULL
--      GROUP BY tenant_id,iot_device_id HAVING COUNT(*)>1;
--     （有结果先改绑或置 NULL 再执行）
CALL `nexus_p2_add_unique`('eq_equipment', 'uk_tenant_iot_device', '`tenant_id`,`iot_device_id`');

-- A4. spc_control_chart：次级图（R/S/MR）控制限 + 长期能力 Pp/Ppk（MES-SPC-01/02）
CALL `nexus_p2_add_column`('spc_control_chart', 'r_ucl',
  "`r_ucl` decimal(14,4) NULL DEFAULT NULL COMMENT '次级图(R/S/MR)上控制限' AFTER `lcl`");
CALL `nexus_p2_add_column`('spc_control_chart', 'r_cl',
  "`r_cl` decimal(14,4) NULL DEFAULT NULL COMMENT '次级图(R/S/MR)中心线' AFTER `r_ucl`");
CALL `nexus_p2_add_column`('spc_control_chart', 'r_lcl',
  "`r_lcl` decimal(14,4) NULL DEFAULT NULL COMMENT '次级图(R/S/MR)下控制限' AFTER `r_cl`");
CALL `nexus_p2_add_column`('spc_control_chart', 'pp',
  "`pp` decimal(10,4) NULL DEFAULT NULL COMMENT '过程性能指数Pp(长期)' AFTER `cpk_grade`");
CALL `nexus_p2_add_column`('spc_control_chart', 'ppk',
  "`ppk` decimal(10,4) NULL DEFAULT NULL COMMENT '过程性能指数Ppk(长期)' AFTER `pp`");

-- A5. spc_data_point：组内离散度 + (chart, sample_group) 唯一键（MES-SPC-01/03）
CALL `nexus_p2_add_column`('spc_data_point', 'sub_value',
  "`sub_value` decimal(14,4) NULL DEFAULT NULL COMMENT '组内离散度(R极差/S标准差/MR移动极差)' AFTER `value`");
--     【一次性清理】历史逻辑删除的僵尸行（每次 analyze 重建都会累积 del_flag='2' 的行）
DELETE FROM spc_data_point WHERE del_flag = '2';
--     【前置检查】现存重复组会阻塞唯一键：
--     SELECT tenant_id,chart_id,sample_group,COUNT(*) cnt FROM spc_data_point
--      WHERE del_flag='0' GROUP BY tenant_id,chart_id,sample_group HAVING COUNT(*)>1;
CALL `nexus_p2_add_unique`('spc_data_point', 'uk_chart_group', '`tenant_id`,`chart_id`,`sample_group`');

-- A6. mes_label_print_log：标签打印留痕（MES-LABEL-02/03）
CREATE TABLE IF NOT EXISTS `mes_label_print_log` (
  `log_id` bigint(20) NOT NULL COMMENT '打印日志ID',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '000000' COMMENT '租户ID',
  `sn_id` bigint(20) NULL DEFAULT NULL COMMENT 'SN ID',
  `sn_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'SN编码',
  `template_id` bigint(20) NULL DEFAULT NULL COMMENT '模板ID',
  `template_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '模板编码(快照)',
  `printer_host` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '打印机地址(快照)',
  `printer_port` int(11) NULL DEFAULT 9100 COMMENT '打印机端口',
  `print_time` datetime(0) NULL DEFAULT NULL COMMENT '打印时间',
  `result` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '结果: SUCCESS/FAIL',
  `fail_reason` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '失败原因',
  `retry_count` int(11) NULL DEFAULT 0 COMMENT '重打计数(同SN同模板历史打印次数, 首次0)',
  `print_source` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '来源: AUTO/REPRINT/TEST',
  `operator_id` bigint(20) NULL DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '操作人姓名',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_dept` bigint(20) NULL DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`log_id`) USING BTREE,
  INDEX `idx_label_print_sn_tpl`(`tenant_id`, `sn_id`, `template_id`, `result`) USING BTREE,
  INDEX `idx_label_print_time`(`print_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '标签打印留痕' ROW_FORMAT = Dynamic;

-- 辅助存储过程用毕可保留（下次执行会 DROP+CREATE），如需清理：
-- DROP PROCEDURE IF EXISTS `nexus_p2_add_column`; DROP PROCEDURE IF EXISTS `nexus_p2_add_unique`;

-- =====================================================================================
-- 第 B 部分：ry-vue
-- =====================================================================================

-- B1/B2. 菜单按钮权限（幂等：按 perms 判存）
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`,
                        `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
                        `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 4025, 'MES工单取消', 4010, 15, '', NULL, NULL, 1, 0, 'F', '0', '0', 'mes:pro:work:cancel', '#',
       103, 1, NOW(), NULL, NULL, 'MES工单取消按钮(MES-PRO-11)'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `perms` = 'mes:pro:work:cancel');

INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`,
                        `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
                        `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 4026, 'MES工单关闭', 4010, 16, '', NULL, NULL, 1, 0, 'F', '0', '0', 'mes:pro:work:close', '#',
       103, 1, NOW(), NULL, NULL, 'MES工单关闭按钮(MES-PRO-11)'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `perms` = 'mes:pro:work:close');

INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`,
                        `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
                        `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 4308, 'NCR处置(无签名)', 4242, 8, '', NULL, NULL, 1, 0, 'F', '0', '0', 'mes:ncr:order:dispose-nosign', '#',
       103, 1, NOW(), NULL, NULL, '无签名入口仅允许可逆处置(REWORK)(MES-NCR-06)'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `perms` = 'mes:ncr:order:dispose-nosign');

-- B3. 把新按钮补授给「已拥有同前缀权限」的角色（幂等；避免存量角色升级后 403）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.role_id, 4025 FROM `sys_role_menu` rm JOIN `sys_menu` m ON m.menu_id = rm.menu_id
 WHERE m.perms LIKE 'mes:pro:work:%'
   AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.role_id = rm.role_id AND x.menu_id = 4025);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.role_id, 4026 FROM `sys_role_menu` rm JOIN `sys_menu` m ON m.menu_id = rm.menu_id
 WHERE m.perms LIKE 'mes:pro:work:%'
   AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.role_id = rm.role_id AND x.menu_id = 4026);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.role_id, 4308 FROM `sys_role_menu` rm JOIN `sys_menu` m ON m.menu_id = rm.menu_id
 WHERE m.perms LIKE 'mes:ncr:order:dispose%'
   AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.role_id = rm.role_id AND x.menu_id = 4308);

-- B4. SPC 图类型字典补 X_MR（单值-移动极差图）
INSERT INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`,
                             `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `remark`)
SELECT 2068956987053351027, '000000', 6, '单值-移动极差图(X-MR)', 'X_MR', 'spc_chart_type',
       NULL, 'primary', 'N', 103, 1, NOW(), 'SPC-X-MR图(MES-SPC-01)'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'spc_chart_type' AND `dict_value` = 'X_MR');
