-- =============================================================================
-- 2026-09-21-iot-menu-tree.sql  —  IoT 平台菜单改为「二级分类目录 + 三级功能菜单」
-- -----------------------------------------------------------------------------
-- 背景  : 原 11 个功能菜单（产品管理/设备管理/告警记录/告警规则/设备分组/设备标签/
--         设备事件/设备指令/数据转发/固件管理/升级任务）**全部直接挂在 `IoT平台`(6000) 下**，
--         侧边栏是一长串平铺菜单，无功能分区。
-- 目标  : 新增 4 个二级目录按功能分类，原 11 个菜单降为三级挂到对应目录下。
--
-- 目标结构（层级与既有 ERP 财务菜单 2000 erp → 6100 fin → 6101 gl → 6102 account 一致）:
--   6000 IoT平台                (M, path=iot)
--   ├─ 6001 设备资产            (M, path=asset)        → /iot/asset
--   │   ├─ 6010 产品管理  (C)   /iot/asset/product
--   │   ├─ 6020 设备管理  (C)   /iot/asset/device
--   │   ├─ 6050 设备分组  (C)   /iot/asset/deviceGroup
--   │   └─ 6055 设备标签  (C)   /iot/asset/deviceTag
--   ├─ 6002 消息通信            (M, path=message)      → /iot/message
--   │   ├─ 6060 设备事件  (C)   /iot/message/event
--   │   ├─ 6070 设备指令  (C)   /iot/message/command
--   │   └─ 6080 数据转发  (C)   /iot/message/forwardRule
--   ├─ 6003 告警中心            (M, path=alertCenter)  → /iot/alertCenter
--   │   ├─ 6030 告警记录  (C)   /iot/alertCenter/alert
--   │   └─ 6040 告警规则  (C)   /iot/alertCenter/alertRule
--   └─ 6004 固件升级            (M, path=ota)          → /iot/ota
--       ├─ 6157 固件管理  (C)   /iot/ota/firmware
--       └─ 6158 升级任务  (C)   /iot/ota/task
--
-- 影响库: ry-vue（sys_menu / sys_role_menu）
-- 幂等性: 是。目录用 INSERT IGNORE（固定主键 6001-6004）；re-parent 用等值 UPDATE（重复执行无副作用）；
--         授权用 INSERT IGNORE + SELECT DISTINCT（sys_role_menu 主键为 role_id+menu_id）。
-- Redis : 需清菜单/权限缓存或让用户**重新登录**（菜单树由 getRouters 返回，登录后缓存）。
-- 重要  : **URL 会变化**（目录嵌套使子路由前缀变化），已在同批次同步前端硬编码跳转与
--         activeMenu（plus-ui/src/router/index.ts、views/iot/event、views/iot/ota）。
-- =============================================================================

SET NAMES utf8mb4;

-- =============================================================================
-- 1. 新增 4 个二级分类目录（M 型；component 留空由前端用 Layout 承载）
--    icon 均为 plus-ui/src/assets/icons/svg 下已存在的图标（computer/link/bell/deploy）
-- =============================================================================
INSERT IGNORE INTO `ry-vue`.`sys_menu` VALUES
 (6001, '设备资产', 6000, 1, 'asset', NULL, '', 1, 0, 'M', '0', '0', '', 'computer', 103, 1, '2026-09-21 00:00:00', NULL, NULL, 'IoT设备资产: 产品模板/设备实例/分组/标签'),
 (6002, '消息通信', 6000, 2, 'message', NULL, '', 1, 0, 'M', '0', '0', '', 'link', 103, 1, '2026-09-21 00:00:00', NULL, NULL, 'IoT消息通信: 设备事件(上行)/设备指令(下行)/数据转发'),
 (6003, '告警中心', 6000, 3, 'alertCenter', NULL, '', 1, 0, 'M', '0', '0', '', 'bell', 103, 1, '2026-09-21 00:00:00', NULL, NULL, 'IoT告警中心: 告警记录/告警规则'),
 (6004, '固件升级', 6000, 4, 'ota', NULL, '', 1, 0, 'M', '0', '0', '', 'deploy', 103, 1, '2026-09-21 00:00:00', NULL, NULL, 'IoT固件升级(OTA): 固件登记/升级任务');

-- =============================================================================
-- 2. 原二级菜单降为三级：改 parent_id 并重排 order_num
--    仅改 parent_id/order_num（以及 6158 的 path，见下），menu_id 不变 →
--    **既有 sys_role_menu 授权、前端组件路径、@SaCheckPermission 权限码全部不受影响**。
-- =============================================================================
-- 6001 设备资产
UPDATE `ry-vue`.`sys_menu` SET `parent_id` = 6001, `order_num` = 1 WHERE `menu_id` = 6010;
UPDATE `ry-vue`.`sys_menu` SET `parent_id` = 6001, `order_num` = 2 WHERE `menu_id` = 6020;
UPDATE `ry-vue`.`sys_menu` SET `parent_id` = 6001, `order_num` = 3 WHERE `menu_id` = 6050;
UPDATE `ry-vue`.`sys_menu` SET `parent_id` = 6001, `order_num` = 4 WHERE `menu_id` = 6055;
-- 6002 消息通信
UPDATE `ry-vue`.`sys_menu` SET `parent_id` = 6002, `order_num` = 1 WHERE `menu_id` = 6060;
UPDATE `ry-vue`.`sys_menu` SET `parent_id` = 6002, `order_num` = 2 WHERE `menu_id` = 6070;
UPDATE `ry-vue`.`sys_menu` SET `parent_id` = 6002, `order_num` = 3 WHERE `menu_id` = 6080;
-- 6003 告警中心
UPDATE `ry-vue`.`sys_menu` SET `parent_id` = 6003, `order_num` = 1 WHERE `menu_id` = 6030;
UPDATE `ry-vue`.`sys_menu` SET `parent_id` = 6003, `order_num` = 2 WHERE `menu_id` = 6040;
-- 6004 固件升级（6158 的 path 由 'ota' 改为 'task'，否则会拼成 /iot/ota/ota）
UPDATE `ry-vue`.`sys_menu` SET `parent_id` = 6004, `order_num` = 1 WHERE `menu_id` = 6157;
UPDATE `ry-vue`.`sys_menu` SET `parent_id` = 6004, `order_num` = 2, `path` = 'task' WHERE `menu_id` = 6158;

-- =============================================================================
-- 3. 角色授权派生：新目录必须授权，否则非超管登录后侧边栏拿不到该目录
--    （RuoYi 的路由树按 sys_role_menu 过滤，只授子菜单而不授父目录会导致整棵子树不可见）
--    规则：某角色只要拥有该目录下**任一**子菜单，就为其补授该目录。
-- =============================================================================
INSERT IGNORE INTO `ry-vue`.`sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT `role_id`, 6001 FROM `ry-vue`.`sys_role_menu` WHERE `menu_id` IN (6010, 6020, 6050, 6055);
INSERT IGNORE INTO `ry-vue`.`sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT `role_id`, 6002 FROM `ry-vue`.`sys_role_menu` WHERE `menu_id` IN (6060, 6070, 6080);
INSERT IGNORE INTO `ry-vue`.`sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT `role_id`, 6003 FROM `ry-vue`.`sys_role_menu` WHERE `menu_id` IN (6030, 6040);
INSERT IGNORE INTO `ry-vue`.`sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT `role_id`, 6004 FROM `ry-vue`.`sys_role_menu` WHERE `menu_id` IN (6157, 6158);

-- =============================================================================
-- 校验（执行后人工核对）
-- =============================================================================
-- -- 期望：4 个 M 目录 + 11 个 C 菜单，且每个 C 的 parent 落在 6001-6004
-- SELECT p.menu_id AS pid, p.menu_name AS pname, p.menu_type AS ptype,
--        c.menu_id AS cid, c.menu_name AS cname, c.order_num, c.path
--   FROM `ry-vue`.`sys_menu` p
--   LEFT JOIN `ry-vue`.`sys_menu` c ON c.parent_id = p.menu_id
--  WHERE p.menu_id BETWEEN 6001 AND 6004 ORDER BY p.menu_id, c.order_num;
-- -- 期望：6000 下只剩 4 个 M 目录（不再直接挂 C 菜单）
-- SELECT menu_id, menu_name, menu_type FROM `ry-vue`.`sys_menu` WHERE parent_id = 6000 ORDER BY order_num;
-- -- 期望：新目录的授权角色数 = 拥有其任一子菜单的角色数
-- SELECT 6001 AS menu_id, COUNT(DISTINCT role_id) FROM `ry-vue`.`sys_role_menu` WHERE menu_id = 6001
--  UNION ALL SELECT 6002, COUNT(DISTINCT role_id) FROM `ry-vue`.`sys_role_menu` WHERE menu_id = 6002
--  UNION ALL SELECT 6003, COUNT(DISTINCT role_id) FROM `ry-vue`.`sys_role_menu` WHERE menu_id = 6003
--  UNION ALL SELECT 6004, COUNT(DISTINCT role_id) FROM `ry-vue`.`sys_role_menu` WHERE menu_id = 6004;

-- =============================================================================
-- 回滚语句（恢复到「11 个菜单直接挂 6000」的平铺结构）
-- =============================================================================
-- UPDATE `ry-vue`.`sys_menu` SET `parent_id` = 6000, `order_num` = 1  WHERE `menu_id` = 6010;
-- UPDATE `ry-vue`.`sys_menu` SET `parent_id` = 6000, `order_num` = 2  WHERE `menu_id` = 6020;
-- UPDATE `ry-vue`.`sys_menu` SET `parent_id` = 6000, `order_num` = 3  WHERE `menu_id` = 6030;
-- UPDATE `ry-vue`.`sys_menu` SET `parent_id` = 6000, `order_num` = 4  WHERE `menu_id` = 6040;
-- UPDATE `ry-vue`.`sys_menu` SET `parent_id` = 6000, `order_num` = 5  WHERE `menu_id` = 6050;
-- UPDATE `ry-vue`.`sys_menu` SET `parent_id` = 6000, `order_num` = 6  WHERE `menu_id` = 6055;
-- UPDATE `ry-vue`.`sys_menu` SET `parent_id` = 6000, `order_num` = 7  WHERE `menu_id` = 6060;
-- UPDATE `ry-vue`.`sys_menu` SET `parent_id` = 6000, `order_num` = 8  WHERE `menu_id` = 6070;
-- UPDATE `ry-vue`.`sys_menu` SET `parent_id` = 6000, `order_num` = 9  WHERE `menu_id` = 6080;
-- UPDATE `ry-vue`.`sys_menu` SET `parent_id` = 6000, `order_num` = 10 WHERE `menu_id` = 6157;
-- UPDATE `ry-vue`.`sys_menu` SET `parent_id` = 6000, `order_num` = 11, `path` = 'ota' WHERE `menu_id` = 6158;
-- DELETE FROM `ry-vue`.`sys_role_menu` WHERE `menu_id` BETWEEN 6001 AND 6004;
-- DELETE FROM `ry-vue`.`sys_menu`      WHERE `menu_id` BETWEEN 6001 AND 6004;
