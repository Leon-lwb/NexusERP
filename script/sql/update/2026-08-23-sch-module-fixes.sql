-- ============================================================
-- SCH 排班调度模块修复: 补菜单权限 + 修正字典数据 + 多租户唯一索引
-- 库: 菜单/字典 -> ry-vue 系统库 ; 唯一索引 -> ry-erp 业务库
-- 日期: 2026-08-23
-- 配套: SCH 模块前后端问题修复(2026-08-23)
-- 段位避让: 菜单 4330-4352 (发布包 4310-4320 / 固件刷写 4280-4307 已占;
--          sch 既有菜单 2401-2442, 考勤复查按钮在 6187 已存在)
-- 幂等: 菜单/字典 INSERT IGNORE + UPDATE; 索引存在性判断后再创建
-- 前置: 已核对本库无 (team_id,worker_id)/(worker_id,record_date) 重复数据
-- ============================================================

-- ==================== 一、ry-vue 系统库: 菜单权限 ====================
-- 1. 员工排班明细页面(原为无菜单入口的死页面, 后端 sch:employeeSchedule:* 权限无种子)
INSERT IGNORE INTO `sys_menu`(`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `remark`) VALUES
(4330, '排班明细', 2971, 2, 'employee-schedule', 'erp/sch/employee-schedule/index', NULL, 1, 0, 'C', '0', '0', 'sch:employeeSchedule:list', 'date-range', 103, 1, NOW(), '员工排班明细(按班组排班模板生成/手工维护)'),
(4331, '排班明细查询', 4330, 1, '', '', '', 1, 0, 'F', '0', '0', 'sch:employeeSchedule:query',  '#', 103, 1, NOW(), ''),
(4332, '排班明细新增', 4330, 2, '', '', '', 1, 0, 'F', '0', '0', 'sch:employeeSchedule:add',    '#', 103, 1, NOW(), '含"生成排班"接口(按计划生成)'),
(4333, '排班明细修改', 4330, 3, '', '', '', 1, 0, 'F', '0', '0', 'sch:employeeSchedule:edit',   '#', 103, 1, NOW(), ''),
(4334, '排班明细删除', 4330, 4, '', '', '', 1, 0, 'F', '0', '0', 'sch:employeeSchedule:remove', '#', 103, 1, NOW(), ''),
(4335, '排班明细导出', 4330, 5, '', '', '', 1, 0, 'F', '0', '0', 'sch:employeeSchedule:export', '#', 103, 1, NOW(), '');

-- 2. 班组排班/计划班组 API 按钮(排班计划页内嵌组件调用, 原无权限种子导致非超管角色无法使用)
INSERT IGNORE INTO `sys_menu`(`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `remark`) VALUES
(4336, '班组排班查询', 2404, 11, '', '', '', 1, 0, 'F', '0', '0', 'sch:teamSchedule:query',  '#', 103, 1, NOW(), '计划页内嵌-班组排班穿梭框'),
(4337, '班组排班维护', 2404, 12, '', '', '', 1, 0, 'F', '0', '0', 'sch:teamSchedule:add',    '#', 103, 1, NOW(), ''),
(4338, '班组排班删除', 2404, 13, '', '', '', 1, 0, 'F', '0', '0', 'sch:teamSchedule:remove', '#', 103, 1, NOW(), ''),
(4339, '计划班组查询', 2404, 14, '', '', '', 1, 0, 'F', '0', '0', 'sch:planteam:query',      '#', 103, 1, NOW(), '计划页内嵌-计划班组管理'),
(4340, '计划班组维护', 2404, 15, '', '', '', 1, 0, 'F', '0', '0', 'sch:planteam:add',        '#', 103, 1, NOW(), ''),
(4341, '计划班组移除', 2404, 16, '', '', '', 1, 0, 'F', '0', '0', 'sch:planteam:remove',     '#', 103, 1, NOW(), '');

-- ==================== 二、ry-vue 系统库: 修正被班组数据污染的考勤状态字典 ====================
-- 原 label 误存班组类别(装配班组/焊接班组...), 考勤状态显示完全错乱
UPDATE `sys_dict_data` SET `dict_label` = '正常',   `remark` = '正常出勤' WHERE `dict_type` = 'sch_attendance_status' AND `dict_value` = 'normal';
UPDATE `sys_dict_data` SET `dict_label` = '迟到',   `remark` = '超过迟到阈值打卡' WHERE `dict_type` = 'sch_attendance_status' AND `dict_value` = 'late';
UPDATE `sys_dict_data` SET `dict_label` = '早退',   `remark` = '早于班次结束下班' WHERE `dict_type` = 'sch_attendance_status' AND `dict_value` = 'early';
UPDATE `sys_dict_data` SET `dict_label` = '旷工',   `remark` = '全天无打卡记录' WHERE `dict_type` = 'sch_attendance_status' AND `dict_value` = 'absent';
UPDATE `sys_dict_data` SET `dict_label` = '请假',   `remark` = '批准请假' WHERE `dict_type` = 'sch_attendance_status' AND `dict_value` = 'leave';
UPDATE `sys_dict_data` SET `dict_label` = '迟到早退', `remark` = '当日既迟到又早退' WHERE `dict_type` = 'sch_attendance_status' AND `dict_value` = 'late_early_leave';

-- ==================== 三、ry-erp 业务库: 多租户唯一索引 ====================
-- 原单列唯一键(code/date)不含 tenant_id: 多租户下跨租户互斥, 必然冲突
-- 注: MySQL 5.7 无 IF EXISTS 标量函数, 用 information_schema 计数变量条件执行
SET @idx = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sch_plan' AND INDEX_NAME='uk_plan_code');
SET @s = IF(@idx > 0, 'ALTER TABLE `sch_plan` DROP INDEX `uk_plan_code`, ADD UNIQUE INDEX `uk_tenant_plan_code`(`tenant_id`, `plan_code`)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sch_team' AND INDEX_NAME='uk_team_code');
SET @s = IF(@idx > 0, 'ALTER TABLE `sch_team` DROP INDEX `uk_team_code`, ADD UNIQUE INDEX `uk_tenant_team_code`(`tenant_id`, `team_code`)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sch_shift' AND INDEX_NAME='uk_shift_code');
SET @s = IF(@idx > 0, 'ALTER TABLE `sch_shift` DROP INDEX `uk_shift_code`, ADD UNIQUE INDEX `uk_tenant_shift_code`(`tenant_id`, `shift_code`)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sch_worker' AND INDEX_NAME='uk_employee_id');
SET @s = IF(@idx > 0, 'ALTER TABLE `sch_worker` DROP INDEX `uk_employee_id`, ADD UNIQUE INDEX `uk_tenant_employee_id`(`tenant_id`, `employee_id`)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sch_holiday' AND INDEX_NAME='uk_holiday_date');
SET @s = IF(@idx > 0, 'ALTER TABLE `sch_holiday` DROP INDEX `uk_holiday_date`, ADD UNIQUE INDEX `uk_tenant_holiday_date`(`tenant_id`, `holiday_date`)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 业务唯一性兜底(已核实本库无重复数据):
-- 同班组同工人仅一条成员关系(防重复入组产生重复排班行)
SET @idx = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sch_team_member' AND INDEX_NAME='uk_team_worker');
SET @s = IF(@idx = 0, 'ALTER TABLE `sch_team_member` ADD UNIQUE INDEX `uk_team_worker`(`team_id`, `worker_id`)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- 同工人同日仅一条考勤主记录(防打卡并发双击产生重复行, 配合 upsert)
SET @idx = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sch_attendance' AND INDEX_NAME='uk_worker_date');
SET @s = IF(@idx = 0, 'ALTER TABLE `sch_attendance` ADD UNIQUE INDEX `uk_worker_date`(`tenant_id`, `worker_id`, `record_date`)', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ==================== 校验 ====================
-- SELECT menu_id, menu_name, perms FROM sys_menu WHERE menu_id BETWEEN 4330 AND 4341;
-- SELECT dict_label, dict_value FROM sys_dict_data WHERE dict_type='sch_attendance_status';
-- SHOW INDEX FROM sch_team_member WHERE Key_name='uk_team_worker';
