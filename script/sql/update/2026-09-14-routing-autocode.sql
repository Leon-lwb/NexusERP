-- 工艺路线编码自动生成规则 + 补齐自动编码接口权限点
-- 背景:
--   1. bd_routing.routing_code 一直是纯手工录入, 本次按 RT+yyyyMMdd+3位日循环流水号 的规则生成
--      (例: RT20260914001), 后端 BdRoutingServiceImpl 在编码为空时兜底调用
--      autoCodeUtil.genSerialCode("ROUTING_CODE", null)。
--   2. SysAutoCodeController 上的 system:autocode:get / system:autocode:preview 两个权限点此前
--      在 sys_menu 中完全没有定义, 除 userId=1 的超管(硬编码 *:*:*)外, 任何角色调用预览接口
--      都会被 Sa-Token 拦成 403 —— 即全站 30+ 个页面的"自动生成编码"开关对普通角色是坏的。
--      本次补齐这两个 F 型权限点并授权给业务角色。
-- 目标库: 系统库(ry-vue)。本脚本幂等(INSERT IGNORE), 可重复执行。
-- 注意: 规则必须 is_padded='N'。AutoCodeUtil.paddingStr() 在 is_padded='Y' 时会断言
--       maxLength > 实际长度, 而 RT(2)+yyyyMMdd(8)+3位流水 恰好等于 max_length=13,
--       一旦改成 'Y' 生成时必抛 "生成的编码…已经超出规则中配置的最大长度"。

-- 1. 编码规则主表 (rule_id=301, 承接现有最大值 300)
INSERT IGNORE INTO `sys_auto_code_rule`
VALUES (301, '000000', 'ROUTING_CODE', '工艺路线编码', 'RT+日期+3位日循环流水号', 13, 'N', NULL, 'L', 'Y', NULL, NULL, NULL, 0, 0, NULL, 1, '2026-09-14 00:00:00', NULL, NULL, '0');

-- 2. 编码规则组成 (part_id=3004~3006, 承接主脚本中 <100000 的最大值 3003)
--    组成顺序: FIXCHAR 'RT' -> NOWDATE yyyyMMdd -> SERIALNO 3位(按天循环, 跨天归零)
INSERT IGNORE INTO `sys_auto_code_part`
VALUES (3004, '000000', 301, 1, 'FIXCHAR', 'PREFIX', '前缀', 2, NULL, NULL, 'RT', NULL, NULL, NULL, NULL, NULL, '', NULL, NULL, 0, 0, NULL, 1, '2026-09-14 00:00:00', NULL, NULL, '0');
INSERT IGNORE INTO `sys_auto_code_part`
VALUES (3005, '000000', 301, 2, 'NOWDATE', 'DATE', '日期', 8, 'yyyyMMdd', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '', NULL, NULL, 0, 0, NULL, 1, '2026-09-14 00:00:00', NULL, NULL, '0');
INSERT IGNORE INTO `sys_auto_code_part`
VALUES (3006, '000000', 301, 3, 'SERIALNO', 'SERIAL', '流水号', 3, NULL, NULL, NULL, 1, 1, NULL, 'Y', 'DAY', '', NULL, NULL, 0, 0, NULL, 1, '2026-09-14 00:00:00', NULL, NULL, '0');

-- 3. 补齐自动编码接口权限点 (挂在 20026 '编码规则' 菜单下, F 型仅为权限点, 不出现在侧边栏)
INSERT IGNORE INTO `sys_menu`
VALUES (20037, '编码生成', 20026, 5, '', NULL, NULL, 1, 0, 'F', '0', '0', 'system:autocode:get', '#', 103, 1, '2026-09-14 00:00:00', NULL, NULL, '自动编码生成(消耗流水号)');
INSERT IGNORE INTO `sys_menu`
VALUES (20038, '编码预览', 20026, 6, '', NULL, NULL, 1, 0, 'F', '0', '0', 'system:autocode:preview', '#', 103, 1, '2026-09-14 00:00:00', NULL, NULL, '自动编码预览(不消耗流水号)');

-- 4. 授权: 3=本部门及以下(业务主角色, 持有全部 bd:routing:*), 2054472456595238913=租户管理员, 1=超级管理员
INSERT IGNORE INTO `sys_role_menu` VALUES (1, 20037);
INSERT IGNORE INTO `sys_role_menu` VALUES (1, 20038);
INSERT IGNORE INTO `sys_role_menu` VALUES (3, 20037);
INSERT IGNORE INTO `sys_role_menu` VALUES (3, 20038);
INSERT IGNORE INTO `sys_role_menu` VALUES (2054472456595238913, 20037);
INSERT IGNORE INTO `sys_role_menu` VALUES (2054472456595238913, 20038);
