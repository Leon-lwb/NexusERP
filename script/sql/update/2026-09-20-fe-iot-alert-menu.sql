-- =====================================================================================
-- 用途      : FE-2 批次 —— IoT 告警记录 / 告警规则 前端页面所需的 sys_menu 菜单与按钮权限种子
--             （配合 plus-ui/src/views/iot/alert/index.vue、plus-ui/src/views/iot/alertRule/index.vue）
-- 影响库    : ry-vue（sys_menu 为系统库表，且已在多租户排除清单中）
-- 执行前提  : 无（仅 DML）。后端端点见 ruoyi-modules/ruoyi-iot/.../alert/controller/
--             IotAlertController（/iot/alert/**）、IotAlertRuleController（/iot/alertRule/**）。
-- 幂等性    : 可重复执行（INSERT IGNORE + 固定 menu_id）。
--             menu_id 6030~6034 / 6040~6045 已由 2026-09-20-iot-alert.sql §5 预留并逐个校验未被占用
--             （ry-vue.sql 中 6000/6010-6015/6020-6026/6100-6201 已占用）；本脚本内容与之等价，
--             二者任一生效即可，重复执行不会产生重复菜单。
-- 是否需清 Redis 缓存: 是。菜单/权限由 Sa-Token 与前端权限路由缓存，执行后需清理缓存或重新登录。
--             `DEL sys_menu:*`（或重启应用 / 让用户重新登录以刷新 /getRouters 与权限集）。
-- 注意      : 本文件不修改 script/sql/ry-vue.sql（该文件为独占文件，由主代理串行合并）。
-- =====================================================================================

-- ---------------------------------------------------------------------------
-- 1. IoT 告警记录菜单（父菜单 6000「IoT物联网」），组件路径 iot/alert/index
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `sys_menu` VALUES (6030, '告警记录', 6000, 3, 'alert', 'iot/alert/index', '', 1, 0, 'C', '0', '0', 'iot:alert:list', 'warning', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'IoT告警记录(查询/确认/恢复)');
INSERT IGNORE INTO `sys_menu` VALUES (6031, '告警查询', 6030, 1, '', '', '', 1, 0, 'F', '0', '0', 'iot:alert:query', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` VALUES (6032, '告警确认', 6030, 2, '', '', '', 1, 0, 'F', '0', '0', 'iot:alert:ack', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'ACTIVE -> ACKNOWLEDGED');
INSERT IGNORE INTO `sys_menu` VALUES (6033, '告警恢复', 6030, 3, '', '', '', 1, 0, 'F', '0', '0', 'iot:alert:recover', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'ACTIVE/ACKNOWLEDGED -> RESOLVED');
INSERT IGNORE INTO `sys_menu` VALUES (6034, '告警导出', 6030, 4, '', '', '', 1, 0, 'F', '0', '0', 'iot:alert:export', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');

-- ---------------------------------------------------------------------------
-- 2. IoT 告警规则菜单（父菜单 6000），组件路径 iot/alertRule/index
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `sys_menu` VALUES (6040, '告警规则', 6000, 4, 'alertRule', 'iot/alertRule/index', '', 1, 0, 'C', '0', '0', 'iot:alertRule:list', 'set', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'IoT告警规则(阈值/持续时长/去抖/级别)');
INSERT IGNORE INTO `sys_menu` VALUES (6041, '规则查询', 6040, 1, '', '', '', 1, 0, 'F', '0', '0', 'iot:alertRule:query', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` VALUES (6042, '规则新增', 6040, 2, '', '', '', 1, 0, 'F', '0', '0', 'iot:alertRule:add', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` VALUES (6043, '规则修改', 6040, 3, '', '', '', 1, 0, 'F', '0', '0', 'iot:alertRule:edit', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` VALUES (6044, '规则删除', 6040, 4, '', '', '', 1, 0, 'F', '0', '0', 'iot:alertRule:remove', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');
INSERT IGNORE INTO `sys_menu` VALUES (6045, '规则启停', 6040, 5, '', '', '', 1, 0, 'F', '0', '0', 'iot:alertRule:changeStatus', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '启用/停用规则(1/0)');

-- ---------------------------------------------------------------------------
-- 3. 执行后校验（人工核对，非脚本语句）
-- ---------------------------------------------------------------------------
-- SELECT menu_id, menu_name, parent_id, perms, component FROM `ry-vue`.`sys_menu`
--  WHERE perms LIKE 'iot:alert%' ORDER BY menu_id;
-- 期望：6030~6034（告警记录）、6040~6045（告警规则）共 11 行。
--
-- 若「告警记录 / 告警规则」菜单已由 2026-09-20-iot-alert.sql 建好，本脚本的 INSERT IGNORE
-- 会全部静默跳过（不报错、不重复），符合幂等要求。
