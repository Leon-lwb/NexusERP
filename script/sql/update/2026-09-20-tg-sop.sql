-- ============================================================================
-- NexusERP MES 工装夹具（tg）+ eSOP（sop）修复增量脚本（2026-09-20，B-tg-sop 批次）
-- 对应审计: doc/audit/2026-09-20-audit-mes-tg-sop.md
--           MES-TG-01（工装校准维度：列 + 状态 + 字典 + 权限）
--           MES-TG-02（pro_step_check_rule 门禁种子）
--           MES-TG-05（工装到期提醒定时任务 sj_job 定义）
--           MES-SOP-01（工位侧查阅登记权限码 mes:sop:viewlog:add）
--           MES-SOP-03（查阅记录删除独立权限码 mes:sop:viewlog:remove）
--           MES-SOP-04（sop_version 种子非法状态 'PUBLISHED' -> 'CURRENT' 数据订正）
--           MES-SOP-05（sop_version.file_hash：签名绑定正文 SM3）
--           MES-TG-07 / MES-SOP-07（唯一索引）
-- 修复报告: doc/audit/2026-09-20-fix-tg-sop.md
-- ----------------------------------------------------------------------------
-- 用途
--   1) ry-mes：tg_tooling 补 6 个校准列（量具/检具的校准有效期此前在系统里根本不存在）。
--   2) ry-mes：sop_version 补 file_hash（SOP_VIEW 逻辑签名绑定正文 SM3）。
--   3) ry-mes：sop_version 状态值订正 'PUBLISHED' -> 'CURRENT'（值域外非法值，使
--      CURRENT 删除守卫失效、doPublish 报「无待发布版本」）。
--   4) ry-mes：pro_step_check_rule 补 TOOLING_AVAILABLE / SOP_ACKNOWLEDGED 门禁种子。
--      **默认 enabled='0'（停用）**：启用会立即拦截在产工单（前端尚不能传 toolingId /
--      不能产生「已阅确认」签名），启用方式见第 6 节。代码侧已修掉「不传参数静默放行」。
--   5) ry-mes：sop_version / tg_maintenance_plan 唯一索引（含 del_flag，兼容逻辑删除）。
--   6) ry-vue：mes_tooling_status 字典补 CALIBRATING；补 3 个按钮权限种子
--      （mes:tg:tooling:calibrate / mes:sop:viewlog:add / mes:sop:viewlog:remove）
--      并派生授予「已拥有对应上级菜单」的角色；补 tgMaintenanceDueTask 的 sj_job 定义。
--
-- 执行前提
--   * 目标库：ry-mes（第 1~5 节）；ry-vue（第 6 节）。两库需在同一 MySQL 实例上分别执行。
--   * 第 1、2 节必须**先于应用启动**执行：TgTooling / SopVersion 实体已新增字段，
--     未加列时工装台账与 SOP 版本查询会报 Unknown column。
--   * 第 6 节 sys_menu 若已占用 4196/4276/4277，请改用空闲 id（INSERT IGNORE 冲突时静默跳过，
--     需人工补按钮权限，或由主脚本合并时分配 id）。
--   * 无需清 Redis 缓存；但执行第 6 节后建议重启 ruoyi-snailjob-server 或控制台刷新任务列表
--     （服务端有任务本地缓存）。
--
-- 幂等性
--   * ALTER 前先查 information_schema，重复执行不会报 Duplicate column / Duplicate key。
--   * 唯一索引创建前先做存量重复检查，有重复则**跳过建索引**（避免整个脚本中断）。
--   * DML 用 INSERT IGNORE / 条件 UPDATE，重复执行不产生重复行。
--
-- 唯一索引前置检查（人工核对用，脚本内已自动判断）：
--   -- sop_version 重复版本号：
--   SELECT sop_id, version_no, tenant_id, del_flag, COUNT(*) c FROM `sop_version`
--    GROUP BY sop_id, version_no, tenant_id, del_flag HAVING c > 1;
--   -- tg_maintenance_plan 重复计划：
--   SELECT tooling_id, trigger_type, tenant_id, del_flag, COUNT(*) c FROM `tg_maintenance_plan`
--    GROUP BY tooling_id, trigger_type, tenant_id, del_flag HAVING c > 1;
--   （有重复行时需先人工合并/删除，否则唯一索引不会创建，MES-TG-07/MES-SOP-07 仅剩应用层校验）
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1) ry-mes: tg_tooling 补校准维度 6 列（MES-TG-01）
--    next_calibration_date 为 NULL 表示「未纳入校准管理」（存量行），不参与超期拦截。
-- ----------------------------------------------------------------------------
SET @ddl_tg_c1 := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `tg_tooling` ADD COLUMN `calibration_cycle_days` int(11) NULL DEFAULT NULL COMMENT ''校准周期(天), 空=未纳入校准管理'' AFTER `last_maintain_time`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tg_tooling' AND COLUMN_NAME = 'calibration_cycle_days'
);
PREPARE stmt_tg_c1 FROM @ddl_tg_c1; EXECUTE stmt_tg_c1; DEALLOCATE PREPARE stmt_tg_c1;

SET @ddl_tg_c2 := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `tg_tooling` ADD COLUMN `last_calibration_date` date NULL DEFAULT NULL COMMENT ''上次校准日期'' AFTER `calibration_cycle_days`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tg_tooling' AND COLUMN_NAME = 'last_calibration_date'
);
PREPARE stmt_tg_c2 FROM @ddl_tg_c2; EXECUTE stmt_tg_c2; DEALLOCATE PREPARE stmt_tg_c2;

SET @ddl_tg_c3 := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `tg_tooling` ADD COLUMN `next_calibration_date` date NULL DEFAULT NULL COMMENT ''下次校准日期, 到期/超期即拦截领用与进站'' AFTER `last_calibration_date`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tg_tooling' AND COLUMN_NAME = 'next_calibration_date'
);
PREPARE stmt_tg_c3 FROM @ddl_tg_c3; EXECUTE stmt_tg_c3; DEALLOCATE PREPARE stmt_tg_c3;

SET @ddl_tg_c4 := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `tg_tooling` ADD COLUMN `calibrated_by` bigint(20) NULL DEFAULT NULL COMMENT ''校准人ID'' AFTER `next_calibration_date`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tg_tooling' AND COLUMN_NAME = 'calibrated_by'
);
PREPARE stmt_tg_c4 FROM @ddl_tg_c4; EXECUTE stmt_tg_c4; DEALLOCATE PREPARE stmt_tg_c4;

SET @ddl_tg_c5 := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `tg_tooling` ADD COLUMN `calibration_result` varchar(16) NULL DEFAULT NULL COMMENT ''校准结果: PASS/FAIL/ADJUSTED'' AFTER `calibrated_by`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tg_tooling' AND COLUMN_NAME = 'calibration_result'
);
PREPARE stmt_tg_c5 FROM @ddl_tg_c5; EXECUTE stmt_tg_c5; DEALLOCATE PREPARE stmt_tg_c5;

SET @ddl_tg_c6 := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `tg_tooling` ADD COLUMN `calibration_remark` varchar(500) NULL DEFAULT NULL COMMENT ''校准备注'' AFTER `calibration_result`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tg_tooling' AND COLUMN_NAME = 'calibration_remark'
);
PREPARE stmt_tg_c6 FROM @ddl_tg_c6; EXECUTE stmt_tg_c6; DEALLOCATE PREPARE stmt_tg_c6;

-- 状态列注释补 CALIBRATING（值域本身是 varchar，无需改类型）
ALTER TABLE `tg_tooling` MODIFY COLUMN `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci
  NULL DEFAULT 'IDLE' COMMENT '状态: IDLE/IN_USE/CALIBRATING/MAINTENANCE/SCRAPPED';

-- ----------------------------------------------------------------------------
-- 2) ry-mes: sop_version 补 file_hash（MES-SOP-05，签名绑定正文 SM3）
-- ----------------------------------------------------------------------------
SET @ddl_sv_hash := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `sop_version` ADD COLUMN `file_hash` varchar(64) NULL DEFAULT NULL COMMENT ''该版本正文的 SM3(签名绑定正文用), 空表示尚未计算'' AFTER `file_type`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sop_version' AND COLUMN_NAME = 'file_hash'
);
PREPARE stmt_sv_hash FROM @ddl_sv_hash; EXECUTE stmt_sv_hash; DEALLOCATE PREPARE stmt_sv_hash;

-- ----------------------------------------------------------------------------
-- 3) ry-mes: sop_version 状态值订正（MES-SOP-04）
--    合法值域 PENDING/CURRENT/HISTORICAL；种子 54 行写成了值域外 'PUBLISHED'，
--    导致 CURRENT 删除守卫失效 + doPublish 找不到 PENDING 版本。
--    ⚠ 不可重复执行无副作用（条件 UPDATE 幂等）。
-- ----------------------------------------------------------------------------
UPDATE `sop_version` SET `status` = 'CURRENT' WHERE `status` = 'PUBLISHED';

-- ----------------------------------------------------------------------------
-- 4) ry-mes: pro_step_check_rule 门禁种子（MES-TG-02 / MES-SOP-02）
--    为「已在实际流转卡中出现过」的工序补 2 条规则，默认 enabled='0'（停用）：
--      TOOLING_AVAILABLE  —— 工装可用性(含校准/保养到期拦截)
--      SOP_ACKNOWLEDGED   —— 操作员已阅确认(存在 SOP_VIEW 电子签名)
--    rule_id 用 process_id*100+序号 派生，稳定且可重复执行（INSERT IGNORE）。
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `pro_step_check_rule`
  (`rule_id`, `tenant_id`, `process_id`, `check_type`, `check_stage`, `enabled`, `error_msg`, `sort_order`, `del_flag`, `create_time`)
SELECT s.`process_id` * 100 + 90, s.`tenant_id`, s.`process_id`, 'TOOLING_AVAILABLE', 'ENTRY', '0',
       '工装不可用(未提供工装信息 / 校准超期 / 保养到期)', 90, '0', NOW()
FROM (SELECT DISTINCT `process_id`, `tenant_id` FROM `pro_flow_card_step`
      WHERE `process_id` IS NOT NULL AND `del_flag` = '0') s;

INSERT IGNORE INTO `pro_step_check_rule`
  (`rule_id`, `tenant_id`, `process_id`, `check_type`, `check_stage`, `enabled`, `error_msg`, `sort_order`, `del_flag`, `create_time`)
SELECT s.`process_id` * 100 + 91, s.`tenant_id`, s.`process_id`, 'SOP_ACKNOWLEDGED', 'ENTRY', '0',
       '未确认作业指导书, 请先打开SOP查看器完成「已阅确认」', 91, '0', NOW()
FROM (SELECT DISTINCT `process_id`, `tenant_id` FROM `pro_flow_card_step`
      WHERE `process_id` IS NOT NULL AND `del_flag` = '0') s;

-- ----------------------------------------------------------------------------
-- 5) ry-mes: 唯一索引（MES-SOP-07 / MES-TG-07）
--    含 del_flag：兼容 @TableLogic 逻辑删除（软删后同号可重建）。
--    存量有重复数据时自动跳过（脚本不中断），需人工合并后重跑本脚本。
-- ----------------------------------------------------------------------------
SET @dup_sv := (SELECT COUNT(*) FROM (
  SELECT 1 FROM `sop_version` GROUP BY `sop_id`, `version_no`, `tenant_id`, `del_flag` HAVING COUNT(*) > 1
) t);
SET @idx_sv := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sop_version' AND INDEX_NAME = 'uk_sop_version_no');
SET @ddl_uk_sv := IF(@dup_sv = 0 AND @idx_sv = 0,
  'ALTER TABLE `sop_version` ADD UNIQUE INDEX `uk_sop_version_no`(`sop_id`, `version_no`, `tenant_id`, `del_flag`)',
  'SELECT 1');
PREPARE stmt_uk_sv FROM @ddl_uk_sv; EXECUTE stmt_uk_sv; DEALLOCATE PREPARE stmt_uk_sv;

SET @dup_tp := (SELECT COUNT(*) FROM (
  SELECT 1 FROM `tg_maintenance_plan` GROUP BY `tooling_id`, `trigger_type`, `tenant_id`, `del_flag` HAVING COUNT(*) > 1
) t);
SET @idx_tp := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tg_maintenance_plan' AND INDEX_NAME = 'uk_tooling_trigger');
SET @ddl_uk_tp := IF(@dup_tp = 0 AND @idx_tp = 0,
  'ALTER TABLE `tg_maintenance_plan` ADD UNIQUE INDEX `uk_tooling_trigger`(`tooling_id`, `trigger_type`, `tenant_id`, `del_flag`)',
  'SELECT 1');
PREPARE stmt_uk_tp FROM @ddl_uk_tp; EXECUTE stmt_uk_tp; DEALLOCATE PREPARE stmt_uk_tp;

-- ============================================================================
-- 6) ry-vue：字典 / 菜单权限 / 定时任务定义
--    以下语句请连到 **ry-vue** 库执行（前 5 节连 ry-mes）。
--    列顺序与 ry-vue.sql 既有种子完全一致（位置化 VALUES）。
-- ============================================================================

-- 6.1 mes_tooling_status 字典补「校准中 / CALIBRATING」（MES-TG-01）
INSERT IGNORE INTO `sys_dict_data` VALUES
(2075000000000000002, '000000', 5, '校准中', 'CALIBRATING', 'mes_tooling_status', NULL, 'warning', 'N', 103, 1, NOW(), NULL, NULL, 'MES-TG-01 工装校准中(校准期间不可领用/进站)');

-- 6.2 按钮权限种子
--     mes:tg:tooling:calibrate —— 工装校准登记端点 PUT /mes/tg/tooling/calibration/{toolingId}
--     mes:sop:viewlog:add      —— 工位终端查阅登记 POST /mes/sop/view-log（MES-SOP-01 核心修复）
--     mes:sop:viewlog:remove   —— 查阅记录删除 DELETE /mes/sop/view-log/{ids}（MES-SOP-03）
INSERT IGNORE INTO `sys_menu` VALUES
(4196, '工装校准登记', 4172, 8, '', '', '', 1, 0, 'F', '0', '0', 'mes:tg:tooling:calibrate', '#', 103, 1, NOW(), NULL, NULL, 'MES-TG-01 登记校准(IDLE->CALIBRATING->IDLE, 服务端算下次校准日)');

INSERT IGNORE INTO `sys_menu` VALUES
(4276, '查阅登记', 4273, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:sop:viewlog:add', '#', 103, 1, NOW(), NULL, NULL, 'MES-SOP-01 工位/终端侧查阅登记(产生 SOP_VIEW 电子签名)');

INSERT IGNORE INTO `sys_menu` VALUES
(4277, '查阅记录删除', 4273, 3, '', '', '', 1, 0, 'F', '0', '0', 'mes:sop:viewlog:remove', '#', 103, 1, NOW(), NULL, NULL, 'MES-SOP-03 查阅记录删除(已签名记录服务端一律拒绝)');

-- 6.3 把新按钮派生授予「已拥有对应上级菜单」的角色：
--     工位终端账号通常只授了 SOP 查看器(4275)/SOP文档(4261)，它们与查阅记录页同属 SOP 主菜单；
--     这里按「已有 4172 工装台账 / 4261 SOP文档 / 4275 SOP查看器」的角色集合派生，
--     避免默认只超管可用而工位侧继续 403（MES-SOP-01 的根因）。
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT `role_id`, 4196 FROM `sys_role_menu` WHERE `menu_id` = 4172;

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT `role_id`, 4276 FROM `sys_role_menu` WHERE `menu_id` IN (4261, 4275, 4273);

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT `role_id`, 4277 FROM `sys_role_menu` WHERE `menu_id` IN (4261, 4273);

-- 6.4 工装到期提醒定时任务定义（MES-TG-05）
INSERT INTO `sj_job`
(`namespace_id`, `biz_id`, `group_name`, `job_name`, `args_str`, `args_type`, `next_trigger_at`,
 `job_status`, `task_type`, `route_key`, `executor_type`, `executor_info`, `trigger_type`, `trigger_interval`,
 `block_strategy`, `executor_timeout`, `max_retry_times`, `parallel_num`, `retry_interval`, `bucket_index`,
 `resident`, `notify_ids`, `owner_id`, `labels`, `description`, `ext_attrs`, `deleted`)
SELECT 'dev', 'biz_203', 'ruoyi_group', '工装校准/保养到期扫描任务', NULL, 1, UNIX_TIMESTAMP(NOW()) * 1000,
  1, 1, 4, 1, 'tgMaintenanceDueTask', 3, '0 30 8 * * ?',
  1, 300, 3, 1, 60, 116,
  0, '', 1, '{}',
  'MES-TG-05 补种: 每日08:30扫描工装校准(next_calibration_date)/保养(计划阈值)到期, 分级+限频后SSE提醒', '', 0
FROM DUAL WHERE NOT EXISTS (
  SELECT 1 FROM `sj_job` WHERE `namespace_id` = 'dev' AND `biz_id` = 'biz_203'
);

-- ----------------------------------------------------------------------------
-- 7) 可选：启用门禁（默认停用，需前端先支持，否则会拦截在产工单）
--    前端支持后，按工序逐个启用（示例：全部工序启用 SOP 已阅门禁）：
--      UPDATE `pro_step_check_rule` SET `enabled` = '1' WHERE `check_type` = 'SOP_ACKNOWLEDGED';
--      UPDATE `pro_step_check_rule` SET `enabled` = '1'
--       WHERE `check_type` = 'TOOLING_AVAILABLE' AND `process_id` IN (<需要工装的工序ID>);
-- ----------------------------------------------------------------------------

-- ----------------------------------------------------------------------------
-- 8) 回滚（如需）
-- ----------------------------------------------------------------------------
-- DELETE FROM `sj_job` WHERE `namespace_id` = 'dev' AND `biz_id` = 'biz_203';
-- DELETE FROM `sys_role_menu` WHERE `menu_id` IN (4196, 4276, 4277);
-- DELETE FROM `sys_menu` WHERE `menu_id` IN (4196, 4276, 4277);
-- DELETE FROM `sys_dict_data` WHERE `dict_code` = 2075000000000000002;
-- DELETE FROM `pro_step_check_rule` WHERE `check_type` IN ('TOOLING_AVAILABLE', 'SOP_ACKNOWLEDGED')
--   AND `rule_id` = `process_id` * 100 + 90 OR `rule_id` = `process_id` * 100 + 91;
-- ALTER TABLE `sop_version` DROP INDEX `uk_sop_version_no`;
-- ALTER TABLE `tg_maintenance_plan` DROP INDEX `uk_tooling_trigger`;
-- ALTER TABLE `sop_version` DROP COLUMN `file_hash`;
-- ALTER TABLE `tg_tooling` DROP COLUMN `calibration_remark`, DROP COLUMN `calibration_result`,
--   DROP COLUMN `calibrated_by`, DROP COLUMN `next_calibration_date`,
--   DROP COLUMN `last_calibration_date`, DROP COLUMN `calibration_cycle_days`;
-- 注：sop_version 状态订正（'PUBLISHED'->'CURRENT'）**不建议回滚** —— 原值属值域外非法值。
