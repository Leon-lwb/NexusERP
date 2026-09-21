-- =============================================================================
-- 2026-09-20 ERP 排产考勤(sch) / 视觉(cv) 修复增量脚本
-- -----------------------------------------------------------------------------
-- 用途   : 修复 doc/audit/2026-09-20-audit-erp-sch.md 的 ERP-SCH-01/06/07、ERP-CV-02/03
--          所需的 DDL 与 sys_menu / sys_role_menu 种子(主脚本合并前的临时执行版)
-- 执行前提: 已存在 sch_attendance / sch_employee_schedule / sch_punch_rule / sys_menu / sys_role_menu
-- 影响的库: ry-erp(sch_* 表) + ry-vue(sys_menu / sys_role_menu)
-- Redis  : 不需要清缓存;但菜单/权限变更后需让相关角色重新登录(或清 sa-token 会话)才会刷新权限
-- 幂等性 : 全部语句可重复执行(ADD COLUMN / ADD INDEX 走 information_schema 判断,菜单用 INSERT IGNORE)
-- 注意   : 本文件由修复子代理产出,主代理合并进 ry-erp.sql / ry-vue.sql 后应从 update/ 移除
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1) ERP-SCH-07: 人脸降级补跑尝试计数(防无照片记录永久占用每轮 200 条配额)
-- -----------------------------------------------------------------------------
SET @ddl := (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sch_attendance' AND COLUMN_NAME = 'face_verify_attempts') = 0,
  'ALTER TABLE `sch_attendance` ADD COLUMN `face_verify_attempts` int NULL DEFAULT 0 COMMENT ''人脸验证尝试次数(降级补跑计数,达上限后不再进入补跑候选集)'' AFTER `face_image_oss_id`',
  'SELECT 1'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sch_attendance' AND COLUMN_NAME = 'face_verify_last_attempt_time') = 0,
  'ALTER TABLE `sch_attendance` ADD COLUMN `face_verify_last_attempt_time` datetime NULL DEFAULT NULL COMMENT ''人脸验证最近一次尝试时间'' AFTER `face_verify_attempts`',
  'SELECT 1'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 补跑候选集查询索引: face_verify_status=NONE AND face_verify_time >= ? ORDER BY face_verify_time
SET @ddl := (SELECT IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sch_attendance' AND INDEX_NAME = 'idx_face_verify_backfill') = 0,
  'ALTER TABLE `sch_attendance` ADD INDEX `idx_face_verify_backfill`(`face_verify_status`, `face_verify_time`) USING BTREE',
  'SELECT 1'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 2) ERP-SCH-06: 同一人同日只允许一条活跃排班
--    说明: 该表使用逻辑删除(@TableLogic del_flag),普通 UNIQUE(tenant_id, worker_id, scheduled_date)
--          会让"生成排班→逻辑删旧→重插"在第 2 次生成时直接唯一键冲突。
--          因此改用「生成列 + 唯一索引」: 只有 del_flag='0' 的活跃行参与唯一性,逻辑删除行取 NULL
--          (MySQL 唯一索引允许多个 NULL),既兜住重复又不破坏重生成。
-- -----------------------------------------------------------------------------
-- 前置检查(存量重复数据会导致唯一索引创建失败,先执行确认返回 0 行):
-- SELECT tenant_id, worker_id, DATE(scheduled_date) AS d, COUNT(*) AS c
--   FROM `sch_employee_schedule` WHERE del_flag = '0'
--  GROUP BY tenant_id, worker_id, DATE(scheduled_date) HAVING c > 1;
-- 若返回重复行,清理示例(同一天保留 schedule_id 最大的一条;执行前请先备份):
-- DELETE s1 FROM `sch_employee_schedule` s1
--   JOIN `sch_employee_schedule` s2
--     ON s1.tenant_id = s2.tenant_id AND s1.worker_id = s2.worker_id
--    AND DATE(s1.scheduled_date) = DATE(s2.scheduled_date)
--    AND s1.del_flag = '0' AND s2.del_flag = '0' AND s1.schedule_id < s2.schedule_id;
SET @ddl := (SELECT IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sch_employee_schedule' AND COLUMN_NAME = 'active_worker_date_key') = 0,
  'ALTER TABLE `sch_employee_schedule` ADD COLUMN `active_worker_date_key` varchar(64) GENERATED ALWAYS AS (IF(`del_flag` = ''0'', CONCAT(`worker_id`, ''#'', DATE(`scheduled_date`)), NULL)) VIRTUAL COMMENT ''同一人同日活跃排班唯一键(逻辑删除行为 NULL,不参与唯一性)''',
  'SELECT 1'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sch_employee_schedule' AND INDEX_NAME = 'uk_worker_date_active') = 0,
  'ALTER TABLE `sch_employee_schedule` ADD UNIQUE INDEX `uk_worker_date_active`(`tenant_id`, `active_worker_date_key`) USING BTREE',
  'SELECT 1'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 3) ERP-SCH-04: WiFi/蓝牙白名单字段明确标注"未实现（预留）",避免管理者误以为已生效
-- -----------------------------------------------------------------------------
ALTER TABLE `sch_punch_rule`
  MODIFY COLUMN `allow_wifi_ssids` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL
    COMMENT '允许的Wi-Fi网络SSID列表（JSON数组）【未实现-预留】打卡主流程未读取/校验，配置后不生效',
  MODIFY COLUMN `allow_bluetooth_devices` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL
    COMMENT '允许的蓝牙设备列表（JSON数组）【未实现-预留】打卡主流程未读取/校验，配置后不生效';

-- -----------------------------------------------------------------------------
-- 4) ERP-SCH-01 / ERP-CV-03: 权限码 sys_menu 种子
--    菜单 ID 6191~6194 为本次新增(6181~6190 已被占用),父级沿用既有菜单:
--      6183 = 人脸底库(C 类页面) / 2405 = 考勤记录(C 类页面)
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `ry-vue`.`sys_menu` VALUES (6191, '人脸1:1验证', 6183, 3, '', '', '', 1, 0, 'F', '0', '0', 'cv:face:verify', '#', 103, 1, NOW(), NULL, NULL, '1:1 人脸验证(ERP-CV-03)');
INSERT IGNORE INTO `ry-vue`.`sys_menu` VALUES (6192, '人脸1:N识别', 6183, 4, '', '', '', 1, 0, 'F', '0', '0', 'cv:face:recognize', '#', 103, 1, NOW(), NULL, NULL, '1:N 人脸识别/底库候选(ERP-CV-03)');
INSERT IGNORE INTO `ry-vue`.`sys_menu` VALUES (6193, '操作台刷脸识别', 6183, 5, '', '', '', 1, 0, 'F', '0', '0', 'cv:ops:identify', '#', 103, 1, NOW(), NULL, NULL, '操作台 1:N top-3 候选(ERP-CV-03)');
INSERT IGNORE INTO `ry-vue`.`sys_menu` VALUES (6194, 'App考勤打卡', 2405, 3, '', '', '', 1, 0, 'F', '0', '0', 'sch:attendance:clock', '#', 103, 1, NOW(), NULL, NULL, 'App 上下班打卡(ERP-SCH-01)');

-- -----------------------------------------------------------------------------
-- 5) 兼容授权: 新增权限码后,既有角色若未授权会立刻 403(App 打卡直接不可用)。
--    这里按"最小惊讶"原则,把新权限码授予本来就能访问对应功能的角色:
--      a) sch:attendance:clock  → 已拥有 考勤记录 菜单(2405) 的角色
--      b) cv:face:verify / cv:face:recognize / cv:ops:identify → 已拥有 人脸底库 菜单(6183) 的角色
--      c) cv:detect:query(6190 既有菜单) → 已拥有任意 wm:*(仓储,标签拍照入口) 或 cv:* 权限的角色
--    管理员可在「角色管理」中按需回收;不需要兼容授权时删除本节即可。
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `ry-vue`.`sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.`role_id`, 6194 FROM `ry-vue`.`sys_role_menu` rm WHERE rm.`menu_id` = 2405;

INSERT IGNORE INTO `ry-vue`.`sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.`role_id`, m.`menu_id`
  FROM `ry-vue`.`sys_role_menu` rm
  JOIN `ry-vue`.`sys_menu` m ON m.`menu_id` IN (6191, 6192, 6193)
 WHERE rm.`menu_id` = 6183;

INSERT IGNORE INTO `ry-vue`.`sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.`role_id`, 6190
  FROM `ry-vue`.`sys_role_menu` rm
  JOIN `ry-vue`.`sys_menu` m ON m.`menu_id` = rm.`menu_id`
 WHERE m.`perms` LIKE 'wm:%' OR m.`perms` LIKE 'cv:%';

-- -----------------------------------------------------------------------------
-- 6) ERP-CV-02: 底库 subject 已改为 T{tenantId}-EMP-{工号},历史裸 EMP-工号 样本需重注册
--    （vision 底库在外部服务,无法用 SQL 迁移）:
--    1. 服务启动后由管理员调用 POST /cv/face/batch-register 重注册(头像来源 sys_user.avatar);
--    2. 旧 subject "EMP-*" 需在 vision 服务侧删除,否则跨租户样本仍可被 1:N 命中;
--    3. 删除前建议先 GET /cv/face/list 导出底库清单留档。
-- -----------------------------------------------------------------------------
