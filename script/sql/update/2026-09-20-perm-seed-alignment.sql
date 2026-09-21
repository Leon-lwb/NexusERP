-- =============================================================================
-- 2026-09-20 权限种子对齐（P0 / PERM-01 ~ PERM-04）—— 存量库增量脚本
-- =============================================================================
-- 用途
--   修复「代码里 @SaCheckPermission 的权限码在 sys_menu 中无种子」导致的
--   「非超管角色一律 403」系统性缺陷。鉴权链路：
--     sys_menu.perms -> sys_role_menu 授权 -> Sa-Token 权限集合
--   代码中的码必须与某个**已授权菜单**的 perms 完全一致（大小写敏感），
--   否则除 userId=1 超管（硬编码 *:*:*）外任何角色都会被拒。
--
-- 执行前提
--   1) 目标库：ry-vue（系统库）。本脚本只动 sys_menu / sys_role_menu 两张表。
--   2) 依赖父菜单已存在：2104 / 2105 / 2107 / 2404 / 2501 / 2510 / 2540 /
--      4170 / 4242 / 4245 / 6103 / 6105 / 6121 / 6176 / 2053846181246812187。
--      若目标库缺失其中某个父菜单，对应按钮会"挂空"（成为孤儿菜单，不影响鉴权但不可见），
--      请先确认这些菜单存在（执行文末「执行前检查」）。
--   3) ID 段 20800-20838 已盘点：本仓库全部 sql 脚本中未被占用。
--
-- 幂等性
--   * 菜单：INSERT IGNORE（menu_id 为主键）
--   * 授权：INSERT IGNORE ... SELECT（按父菜单反查角色）
--   * perms 重指向：UPDATE ... AND perms = '旧值'（重复执行无副作用）
--   本脚本可安全重复执行。
--
-- 缓存
--   sys_menu / sys_dict 等走 Redis 缓存（RuoYi 侧 SysMenuServiceImpl 使用 @Cacheable）。
--   执行后需刷新菜单缓存，否则新按钮最长要等缓存过期才可见：
--     - 应用内：系统管理 -> 菜单管理，任意保存一次（触发缓存清理）；或
--     - Redis：DEL sys_menu 相关键（如 `sys_menu:*` / `SysMenuServiceImpl:*`）；
--     - 最省事：重启应用。
--   注意：**已登录用户的权限集合是登录时写入 Sa-Token Session 的**，
--   刷新缓存后仍需相关用户**重新登录**才会拿到新权限码。
--
-- 等价主脚本：script/sql/ry-vue.sql（文件末尾同内容，全新环境导入即生效）
-- 校验工具：script/tools/check-permission-seeds.ps1（代码权限码 ⊆ 种子权限码）
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 0) 执行前检查：父菜单是否齐备（缺哪个补哪个，或忽略对应按钮行）
-- ---------------------------------------------------------------------------
SELECT m.`menu_id`, m.`menu_name`
FROM `sys_menu` m
WHERE m.`menu_id` IN (2104, 2105, 2107, 2404, 2501, 2510, 2540, 4170, 4242, 4245,
                      6103, 6105, 6121, 6176, 2053846181246812187);

-- ---------------------------------------------------------------------------
-- 1) B 类：把"动作按钮被合并为 :edit"的既有菜单行 perms 重指向真实动作码
--    （角色原本已持有这些菜单行，重指向后自动获得动作权限，无需额外授权）
-- ---------------------------------------------------------------------------
UPDATE `sys_menu` SET `perms` = 'pu:order:submit'       WHERE `menu_id` = 2516 AND `perms` = 'pu:order:edit';
UPDATE `sys_menu` SET `perms` = 'pu:order:approve'      WHERE `menu_id` = 2517 AND `perms` = 'pu:order:edit';
UPDATE `sys_menu` SET `perms` = 'pu:order:receive'      WHERE `menu_id` = 2518 AND `perms` = 'pu:order:edit';
UPDATE `sys_menu` SET `perms` = 'pu:requisition:submit' WHERE `menu_id` = 2507 AND `perms` = 'pu:requisition:edit';
UPDATE `sys_menu` SET `perms` = 'pu:requisition:approve' WHERE `menu_id` = 2508 AND `perms` = 'pu:requisition:edit';
UPDATE `sys_menu` SET `perms` = 'pu:requisition:convert' WHERE `menu_id` = 2509 AND `perms` = 'pu:requisition:edit';
UPDATE `sys_menu` SET `perms` = 'pu:return:confirm'     WHERE `menu_id` = 2526 AND `perms` = 'pu:return:edit';
UPDATE `sys_menu` SET `perms` = 'pu:return:execute'     WHERE `menu_id` = 2527 AND `perms` = 'pu:return:edit';

-- ---------------------------------------------------------------------------
-- 2) C 类：补按钮菜单（F 型），ID 20800-20838
-- ---------------------------------------------------------------------------
-- 物料入库 2105：单据动作 + 入库明细子页
INSERT IGNORE INTO `sys_menu` VALUES (20800, '入库查询', 2105, 6, '', '', NULL, 1, 0, 'F', '0', '0', 'wm:receive:query', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'WmMaterialReceiveController.getInfo');
INSERT IGNORE INTO `sys_menu` VALUES (20801, '入库新增', 2105, 7, '', '', NULL, 1, 0, 'F', '0', '0', 'wm:receive:add', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'WmMaterialReceiveController.add');
INSERT IGNORE INTO `sys_menu` VALUES (20802, '入库修改', 2105, 8, '', '', NULL, 1, 0, 'F', '0', '0', 'wm:receive:edit', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'WmMaterialReceiveController.edit');
INSERT IGNORE INTO `sys_menu` VALUES (20803, '入库删除', 2105, 9, '', '', NULL, 1, 0, 'F', '0', '0', 'wm:receive:remove', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'WmMaterialReceiveController.remove');
INSERT IGNORE INTO `sys_menu` VALUES (20804, '入库导出', 2105, 10, '', '', NULL, 1, 0, 'F', '0', '0', 'wm:receive:export', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'WmMaterialReceiveController.export');
INSERT IGNORE INTO `sys_menu` VALUES (20805, '入库执行', 2105, 11, '', '', NULL, 1, 0, 'F', '0', '0', 'wm:receive:execute', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'WmMaterialReceiveController 入库执行/上架动作');
INSERT IGNORE INTO `sys_menu` VALUES (20806, '入库提交', 2105, 12, '', '', NULL, 1, 0, 'F', '0', '0', 'wm:receive:submit', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'WmMaterialReceiveController 提交走流程');
INSERT IGNORE INTO `sys_menu` VALUES (20807, '入库明细列表', 2105, 13, '', '', NULL, 1, 0, 'F', '0', '0', 'wm:detail:list', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '前端 erp/wm/receive/detail.vue 使用');
INSERT IGNORE INTO `sys_menu` VALUES (20808, '入库明细查询', 2105, 14, '', '', NULL, 1, 0, 'F', '0', '0', 'wm:detail:query', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '前端 erp/wm/receive/detail.vue 使用');
INSERT IGNORE INTO `sys_menu` VALUES (20809, '入库明细新增', 2105, 15, '', '', NULL, 1, 0, 'F', '0', '0', 'wm:detail:add', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '前端 erp/wm/receive/detail.vue 使用');
INSERT IGNORE INTO `sys_menu` VALUES (20810, '入库明细修改', 2105, 16, '', '', NULL, 1, 0, 'F', '0', '0', 'wm:detail:edit', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '前端 erp/wm/receive/detail.vue 使用');
INSERT IGNORE INTO `sys_menu` VALUES (20811, '入库明细删除', 2105, 17, '', '', NULL, 1, 0, 'F', '0', '0', 'wm:detail:remove', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '前端 erp/wm/receive/detail.vue 使用');
INSERT IGNORE INTO `sys_menu` VALUES (20812, '入库明细导出', 2105, 18, '', '', NULL, 1, 0, 'F', '0', '0', 'wm:detail:export', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '前端 erp/wm/receive/detail.vue 使用');
-- 到货通知 2104
INSERT IGNORE INTO `sys_menu` VALUES (20813, '到货通知提交', 2104, 6, '', '', NULL, 1, 0, 'F', '0', '0', 'wm:arrivalnotice:submit', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '前端 erp/wm/arrivalnotice/index.vue 使用');
-- 出库管理 2107
INSERT IGNORE INTO `sys_menu` VALUES (20814, '出库确认', 2107, 6, '', '', NULL, 1, 0, 'F', '0', '0', 'wm:salesIssue:confirm', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'WmSalesIssueController.confirm');
INSERT IGNORE INTO `sys_menu` VALUES (20815, '出库完成', 2107, 7, '', '', NULL, 1, 0, 'F', '0', '0', 'wm:salesIssue:complete', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'WmSalesIssueController.complete');
-- 成本结转 6176（该页调用 /erp/wm/stocktx/list）
INSERT IGNORE INTO `sys_menu` VALUES (20816, '库存流水分页', 6176, 3, '', '', NULL, 1, 0, 'F', '0', '0', 'wm:stocktx:list', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '前端 api/erp/fin/cost/index.ts 调用 /erp/wm/stocktx/list');
-- 采购订单 2510
INSERT IGNORE INTO `sys_menu` VALUES (20817, '采购订单取消', 2510, 9, '', '', NULL, 1, 0, 'F', '0', '0', 'pu:order:cancel', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'PuOrderController.cancel');
INSERT IGNORE INTO `sys_menu` VALUES (20818, '采购订单关闭', 2510, 10, '', '', NULL, 1, 0, 'F', '0', '0', 'pu:order:close', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'PuOrderController.close');
-- 采购申请 2501
INSERT IGNORE INTO `sys_menu` VALUES (20819, '采购申请关闭', 2501, 9, '', '', NULL, 1, 0, 'F', '0', '0', 'pu:requisition:close', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'PuRequisitionController.close');
INSERT IGNORE INTO `sys_menu` VALUES (20820, '采购申请取消', 2501, 10, '', '', NULL, 1, 0, 'F', '0', '0', 'pu:requisition:cancel', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'PuRequisitionController.cancel');
-- 采购合同 2540
INSERT IGNORE INTO `sys_menu` VALUES (20821, '合同导出', 2540, 6, '', '', NULL, 1, 0, 'F', '0', '0', 'pu:contract:export', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'PuContractController.export');
-- 计划管理 2404（班组/班组排班内嵌页，仅缺 list/edit/export）
INSERT IGNORE INTO `sys_menu` VALUES (20822, '计划班组列表', 2404, 17, '', '', NULL, 1, 0, 'F', '0', '0', 'sch:planteam:list', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'SchPlanTeamController.list');
INSERT IGNORE INTO `sys_menu` VALUES (20823, '计划班组修改', 2404, 18, '', '', NULL, 1, 0, 'F', '0', '0', 'sch:planteam:edit', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'SchPlanTeamController.edit');
INSERT IGNORE INTO `sys_menu` VALUES (20824, '计划班组导出', 2404, 19, '', '', NULL, 1, 0, 'F', '0', '0', 'sch:planteam:export', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'SchPlanTeamController.export');
INSERT IGNORE INTO `sys_menu` VALUES (20825, '班组排班列表', 2404, 20, '', '', NULL, 1, 0, 'F', '0', '0', 'sch:teamSchedule:list', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'SchTeamScheduleController.list');
INSERT IGNORE INTO `sys_menu` VALUES (20826, '班组排班修改', 2404, 21, '', '', NULL, 1, 0, 'F', '0', '0', 'sch:teamSchedule:edit', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'SchTeamScheduleController.edit');
INSERT IGNORE INTO `sys_menu` VALUES (20827, '班组排班导出', 2404, 22, '', '', NULL, 1, 0, 'F', '0', '0', 'sch:teamSchedule:export', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'SchTeamScheduleController.export');
-- 收付款单 6121
INSERT IGNORE INTO `sys_menu` VALUES (20828, '收付款单修改', 6121, 5, '', '', NULL, 1, 0, 'F', '0', '0', 'fin:arap:receipt:edit', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'FinReceiptController.edit');
INSERT IGNORE INTO `sys_menu` VALUES (20829, '收付款单删除', 6121, 6, '', '', NULL, 1, 0, 'F', '0', '0', 'fin:arap:receipt:remove', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'FinReceiptController.remove');
INSERT IGNORE INTO `sys_menu` VALUES (20830, '收付款单导出', 6121, 7, '', '', NULL, 1, 0, 'F', '0', '0', 'fin:arap:receipt:export', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'FinReceiptController.export');
-- 币种汇率 6105
INSERT IGNORE INTO `sys_menu` VALUES (20831, '汇率新增', 6105, 9, '', '', NULL, 1, 0, 'F', '0', '0', 'fin:gl:currency:add', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'FinCurrencyController.add');
INSERT IGNORE INTO `sys_menu` VALUES (20832, '汇率修改', 6105, 10, '', '', NULL, 1, 0, 'F', '0', '0', 'fin:gl:currency:edit', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'FinCurrencyController.edit');
INSERT IGNORE INTO `sys_menu` VALUES (20833, '汇率删除', 6105, 11, '', '', NULL, 1, 0, 'F', '0', '0', 'fin:gl:currency:remove', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'FinCurrencyController.remove');
-- 记账凭证 6103
INSERT IGNORE INTO `sys_menu` VALUES (20834, '凭证导出', 6103, 7, '', '', NULL, 1, 0, 'F', '0', '0', 'fin:gl:voucher:export', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'FinVoucherController.export');
-- MES 巡检任务
INSERT IGNORE INTO `sys_menu` VALUES (20835, '任务编辑', 2053846181246812187, 5, '', '', NULL, 1, 0, 'F', '0', '0', 'mes:eq:patrol:task:edit', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '巡检任务编辑（原仅 query/add/execute/remove）');
-- MES NCR
INSERT IGNORE INTO `sys_menu` VALUES (20836, '8D查询', 4245, 3, '', '', NULL, 1, 0, 'F', '0', '0', 'mes:ncr:8d:query', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '8D 详情查询');
INSERT IGNORE INTO `sys_menu` VALUES (20837, '缺陷列表', 4242, 9, '', '', NULL, 1, 0, 'F', '0', '0', 'mes:ncr:defect:list', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'NCR 缺陷明细列表');
-- MES 批次主档（追溯域页面，承载 mes:pro:trace:query）
INSERT IGNORE INTO `sys_menu` VALUES (20838, '追溯查询', 4170, 11, '', '', NULL, 1, 0, 'F', '0', '0', 'mes:pro:trace:query', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'ProTraceabilityController 追溯查询');

-- ---------------------------------------------------------------------------
-- 3) 授权：把新按钮授权给"当前已持有其父菜单"的角色（幂等）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.`role_id`, m.`menu_id`
FROM `sys_menu` m
JOIN `sys_role_menu` rm ON rm.`menu_id` = m.`parent_id`
WHERE m.`menu_id` BETWEEN 20800 AND 20838;

-- ---------------------------------------------------------------------------
-- 4) 执行后校验
--    4.1 应返回 39 行（新按钮菜单）
-- ---------------------------------------------------------------------------
SELECT COUNT(*) AS new_menu_cnt FROM `sys_menu` WHERE `menu_id` BETWEEN 20800 AND 20838;

--    4.2 应返回 0 行（重指向后不应再有动作按钮挂 :edit）
-- ---------------------------------------------------------------------------
SELECT `menu_id`, `menu_name`, `perms` FROM `sys_menu`
WHERE `menu_id` IN (2507, 2508, 2509, 2516, 2517, 2518, 2526, 2527)
  AND `perms` IN ('pu:order:edit', 'pu:requisition:edit', 'pu:return:edit');

--    4.3 抽查授权行数（>0 即有角色获得新按钮）
-- ---------------------------------------------------------------------------
SELECT COUNT(*) AS new_role_menu_cnt FROM `sys_role_menu` WHERE `menu_id` BETWEEN 20800 AND 20838;

--    4.4 抽查代码权限码是否已全部有种子（应为 0 行）
-- ---------------------------------------------------------------------------
SELECT m.`perms` FROM `sys_menu` m
WHERE m.`perms` IN (
  'wm:receive:add','wm:receive:edit','wm:receive:execute','wm:receive:export','wm:receive:query','wm:receive:remove','wm:receive:submit',
  'wm:detail:list','wm:detail:query','wm:detail:add','wm:detail:edit','wm:detail:remove','wm:detail:export',
  'wm:arrivalnotice:submit','wm:salesIssue:confirm','wm:salesIssue:complete','wm:stocktx:list',
  'pu:order:submit','pu:order:approve','pu:order:cancel','pu:order:close','pu:order:receive',
  'pu:requisition:submit','pu:requisition:approve','pu:requisition:convert','pu:requisition:close','pu:requisition:cancel',
  'pu:return:confirm','pu:return:execute','pu:contract:export',
  'sch:planteam:list','sch:planteam:edit','sch:planteam:export',
  'sch:teamSchedule:list','sch:teamSchedule:edit','sch:teamSchedule:export',
  'fin:arap:receipt:edit','fin:arap:receipt:remove','fin:arap:receipt:export',
  'fin:gl:currency:add','fin:gl:currency:edit','fin:gl:currency:remove','fin:gl:voucher:export',
  'mes:eq:patrol:task:edit','mes:ncr:8d:query','mes:ncr:defect:list','mes:pro:trace:query'
)
GROUP BY m.`perms`
HAVING COUNT(*) = 0;
