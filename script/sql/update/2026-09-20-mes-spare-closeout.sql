-- =============================================================================
-- 2026-09-20-mes-spare-closeout.sql  —  MES 备件↔ERP 打通收口（后端）
-- -----------------------------------------------------------------------------
-- 对应审计: doc/audit/2026-09-20-feat-mes-spare-erp.md §2「未修复项」
-- 修复报告: doc/audit/2026-09-20-fix-mes-spare-closeout.md
-- 分支    : feature/erp-mes-iot-audit-fix
-- -----------------------------------------------------------------------------
-- 用途（6 节）
--   1) eq_spare_part 增加 `unit_cost` decimal(18,4) NULL DEFAULT 0（备件参考单价, 人工维护）
--      —— 出入库过账时由服务端读该列填入 MQ 消息, 供 ERP 侧 IInventoryValuationService 做移动平均计价
--   2) 新增备件退库单表 eq_spare_return（单头）+ eq_spare_return_line（明细）
--   3) unit_cost 一次性存量回填（跨库取 bd_material.reference_price / standard_cost）
--   4) 菜单种子: 过账台账(4381) / 备件退库单(4383) + 6 个按钮(4384-4389)
--   5) 字典种子: mes_spare_return_status / mes_spare_return_type
--   6) 定时任务种子: sj_job id=30(biz_30, eqSpareErpSyncTask) + sj_job_executor id=46
--
-- 执行前提
--   * 目标库: ry-mes（第 1/2 节 DDL、第 3 节回填主体）、ry-erp（第 3 节回填数据源,
--     需与 ry-mes 位于同一 MySQL 实例且当前账号可跨库读写）、ry-vue（第 4/5/6 节种子）。
--   * 第 3 节的回填<b>依赖 material_id 已回填</b>：必须先执行完
--     `script/sql/update/2026-09-20-mes-spare-erp.sql` §3（按 spare_code = material_code 回填 material_id），
--     否则本脚本第 3 节命中 0 行（属正常，material_id 为空的行不参与回填）。
--   * 第 1/2 节必须在应用启动前执行：实体已含 `unit_cost` 且服务会读写 2 张新表,
--     缺列/缺表时备件主数据与退库单接口全部报 Unknown column / doesn't exist。
--   * 第 3 节为一次性成本口径初始化, <b>建议在应用停机或低峰执行</b>（大表 UPDATE 会持锁）。
--
-- 幂等性: 是。
--   第 1 节 information_schema 预检 + PREPARE 动态 DDL;
--   第 2 节 CREATE TABLE IF NOT EXISTS;
--   第 3 节 WHERE 带 `unit_cost IS NULL OR unit_cost = 0` 与 `单价 > 0` 守卫(重复执行不产生额外变更);
--   第 4/5/6 节 INSERT IGNORE(固定主键)。
--
-- Redis : 需清理 1 类缓存 —— 新增了字典 mes_spare_return_status / mes_spare_return_type,
--         若前端字典缓存已加载过, 执行后请在「系统管理 → 字典管理」点一次刷新,
--         或删除 Redis 键 `sys_dict:*`（后端 DictService 按 dict_type 缓存）。
--         其余（菜单、任务）无需清缓存, 菜单由前端重新登录/刷新路由生效。
--
-- 回滚  : 见文件末尾「回滚语句」。
-- =============================================================================

-- =============================================================================
-- 1. eq_spare_part: 增加备件参考单价 unit_cost（库: ry-mes；幂等）
-- =============================================================================
-- 前置检查(返回 0 表示需要执行 ADD COLUMN):
--   SELECT COUNT(*) FROM information_schema.COLUMNS
--    WHERE TABLE_SCHEMA = 'ry-mes' AND TABLE_NAME = 'eq_spare_part' AND COLUMN_NAME = 'unit_cost';

SET @ddl := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `eq_spare_part`
       ADD COLUMN `unit_cost` decimal(18, 4) NULL DEFAULT 0.0000
       COMMENT ''备件参考单价(人工维护; 出入库过账时由服务端读库填入 MQ, 供 ERP 移动平均计价)'' AFTER `warehouse_id`',
    'SELECT ''eq_spare_part.unit_cost 已存在, 跳过第1节'' AS msg')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'eq_spare_part' AND COLUMN_NAME = 'unit_cost'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =============================================================================
-- 2. 备件退库单: eq_spare_return（单头） + eq_spare_return_line（明细）（库: ry-mes；幂等）
-- =============================================================================
-- return_type: REPAIR_RETURN / EXCESS_RETURN / OTHER
-- status     : DRAFT / CONFIRMED / CANCELLED
CREATE TABLE IF NOT EXISTS `eq_spare_return` (
  `return_id` bigint(20) NOT NULL COMMENT '退库单ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `return_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '退库单号 MESRT+yyyyMMdd+4位当日序号',
  `warehouse_id` bigint(20) NULL DEFAULT NULL COMMENT '退库目标ERP仓库ID(wm_warehouse.warehouse_id)',
  `return_date` datetime(0) NULL DEFAULT NULL COMMENT '退库日期',
  `return_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'REPAIR_RETURN/EXCESS_RETURN/OTHER',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/CONFIRMED/CANCELLED',
  `total_qty` decimal(14, 4) NULL DEFAULT 0.0000 COMMENT '明细数量合计',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `confirm_by` bigint(20) NULL DEFAULT NULL COMMENT '确认人ID',
  `confirm_time` datetime(0) NULL DEFAULT NULL COMMENT '确认时间',
  `version` int(11) NULL DEFAULT 0 COMMENT '版本号(确认时CAS自增)',
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0',
  PRIMARY KEY (`return_id`) USING BTREE,
  UNIQUE INDEX `uk_spare_return_no`(`tenant_id`, `return_no`) USING BTREE,
  INDEX `idx_spare_return_status`(`tenant_id`, `status`) USING BTREE,
  INDEX `idx_spare_return_date`(`tenant_id`, `return_date`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES备件退库单' ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `eq_spare_return_line` (
  `line_id` bigint(20) NOT NULL COMMENT '明细行ID(主键)',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000',
  `return_id` bigint(20) NOT NULL COMMENT '退库单ID',
  `spare_id` bigint(20) NOT NULL COMMENT '备件ID',
  `spare_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备件编码(冗余)',
  `spare_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备件名称(冗余)',
  `return_qty` decimal(14, 4) NOT NULL COMMENT '退库数量(>0)',
  `unit_cost` decimal(18, 4) NULL DEFAULT NULL COMMENT '备件参考单价快照(服务端从eq_spare_part读取)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `create_dept` bigint(20) NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime(0) NULL DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0',
  PRIMARY KEY (`line_id`) USING BTREE,
  INDEX `idx_spare_return_line`(`tenant_id`, `return_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MES备件退库单明细' ROW_FORMAT = Dynamic;

-- =============================================================================
-- 3. unit_cost 一次性存量回填（跨库 ry-mes ← ry-erp；幂等）
-- -----------------------------------------------------------------------------
-- 背景: 第 1 节 ADD COLUMN 带 DEFAULT 0, 存量行 unit_cost 均为 0（不是 NULL）;
--       若不回填, 移动平均计价上线后所有备件单价为 0 = 计价等于没做。
-- 取值口径: bd_material.reference_price 优先(0 视为未维护), 其次 standard_cost;
--           两者都为 0/NULL 的行<b>不更新</b>(保持 0, 由业务在备件主数据中人工维护)。
-- 依赖    : material_id 已由 `2026-09-20-mes-spare-erp.sql` §3 回填完成。
-- =============================================================================
-- 前置检查 ①(回填影响行数预览, 期望与 ② 的 unmapped 相加 = 备件总数):
--   SELECT COUNT(*) AS total_spare,
--          SUM(CASE WHEN sp.material_id IS NULL THEN 1 ELSE 0 END)                       AS unmapped_spare,
--          SUM(CASE WHEN sp.material_id IS NOT NULL
--                    AND (sp.unit_cost IS NULL OR sp.unit_cost = 0)
--                    AND COALESCE(NULLIF(m.reference_price, 0), m.standard_cost) > 0
--                   THEN 1 ELSE 0 END)                                                   AS to_backfill
--     FROM `ry-mes`.`eq_spare_part` sp
--     LEFT JOIN `ry-erp`.`bd_material` m
--            ON m.material_id = sp.material_id AND m.tenant_id = sp.tenant_id AND m.del_flag = '0'
--    WHERE sp.del_flag = '0';
--
-- 前置检查 ②(回填明细抽样, 人工确认单价合理性后再执行 UPDATE):
--   SELECT sp.spare_id, sp.spare_code, m.material_code,
--          m.reference_price, m.standard_cost,
--          COALESCE(NULLIF(m.reference_price, 0), m.standard_cost) AS new_unit_cost
--     FROM `ry-mes`.`eq_spare_part` sp
--     JOIN `ry-erp`.`bd_material` m
--       ON m.material_id = sp.material_id AND m.tenant_id = sp.tenant_id AND m.del_flag = '0'
--    WHERE sp.del_flag = '0'
--      AND (sp.unit_cost IS NULL OR sp.unit_cost = 0)
--      AND COALESCE(NULLIF(m.reference_price, 0), m.standard_cost) > 0
--    LIMIT 50;

UPDATE `ry-mes`.`eq_spare_part` sp
  JOIN `ry-erp`.`bd_material` m
    ON m.material_id = sp.material_id
   AND m.tenant_id = sp.tenant_id
   AND m.del_flag = '0'
   SET sp.unit_cost = COALESCE(NULLIF(m.reference_price, 0), m.standard_cost)
 WHERE sp.del_flag = '0'
   AND sp.material_id IS NOT NULL
   AND (sp.unit_cost IS NULL OR sp.unit_cost = 0)
   AND COALESCE(NULLIF(m.reference_price, 0), m.standard_cost) > 0;

-- 影响行数(执行后核对): SELECT ROW_COUNT();
-- 回填后抽查: SELECT spare_id, spare_code, material_id, unit_cost FROM `ry-mes`.`eq_spare_part`
--              WHERE del_flag = '0' ORDER BY unit_cost DESC LIMIT 20;
-- 仍为 0 的备件清单(需人工在「备件主数据」中维护参考单价):
--   SELECT spare_id, spare_code, spare_name FROM `ry-mes`.`eq_spare_part`
--    WHERE del_flag = '0' AND (unit_cost IS NULL OR unit_cost = 0);

-- =============================================================================
-- 4. 菜单种子（库: ry-vue；幂等）
--    4381 C 过账台账(spare-erp-sync)  —— 复用既有权限码 mes:eq:spare:query, 不新建权限
--    4383 C 备件退库单(spare-return)  + 4384..4389 六个按钮
--    父菜单 4203「备件管理」(M, 设备管理拆分) 已存在于主脚本。
--    若 menu_id 已被占用, INSERT IGNORE 会静默跳过, 请在主脚本中改号后重跑。
-- =============================================================================
INSERT IGNORE INTO `ry-vue`.`sys_menu` VALUES (4381, '过账台账', 4203, 10, 'spare-erp-sync', 'mes/eq/spare-erp-sync/index', '', 1, 0, 'C', '0', '0', 'mes:eq:spare:query', 'component', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'MES备件→ERP过账同步台账(失败可见/人工重投)');
INSERT IGNORE INTO `ry-vue`.`sys_menu` VALUES (4383, '备件退库单', 4203, 11, 'spare-return', 'mes/eq/spare-return/index', '', 1, 0, 'C', '0', '0', 'mes:eq:spare:return:list', 'component', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'MES备件退库单(草稿→确认, ERP侧按入库过账)');
INSERT IGNORE INTO `ry-vue`.`sys_menu` VALUES (4384, '退库单列表', 4383, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:eq:spare:return:list', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');
INSERT IGNORE INTO `ry-vue`.`sys_menu` VALUES (4385, '退库单查询', 4383, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:eq:spare:return:query', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '');
INSERT IGNORE INTO `ry-vue`.`sys_menu` VALUES (4386, '退库单新增', 4383, 3, '', '', '', 1, 0, 'F', '0', '0', 'mes:eq:spare:return:add', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '新增草稿(含明细)');
INSERT IGNORE INTO `ry-vue`.`sys_menu` VALUES (4387, '退库单修改', 4383, 4, '', '', '', 1, 0, 'F', '0', '0', 'mes:eq:spare:return:edit', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '仅 DRAFT 可改');
INSERT IGNORE INTO `ry-vue`.`sys_menu` VALUES (4388, '退库单删除', 4383, 5, '', '', '', 1, 0, 'F', '0', '0', 'mes:eq:spare:return:remove', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '仅 DRAFT 可删');
INSERT IGNORE INTO `ry-vue`.`sys_menu` VALUES (4389, '退库单确认', 4383, 6, '', '', '', 1, 0, 'F', '0', '0', 'mes:eq:spare:return:confirm', '#', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'CAS确认并逐行退库过账(幂等)');

-- 核对: SELECT menu_id, menu_name, parent_id, perms FROM `ry-vue`.`sys_menu` WHERE menu_id BETWEEN 4381 AND 4389;

-- =============================================================================
-- 5. 字典种子（库: ry-vue；幂等）
--    mes_spare_return_status: dict_id=600214, 数据 600274/600275/600276
--    mes_spare_return_type  : dict_id=600215, 数据 600277/600278/600279
--    注: sys_dict_type/sys_dict_data 均无 status 列, 列顺序以主脚本同类 INSERT 为准。
-- =============================================================================
INSERT IGNORE INTO `ry-vue`.`sys_dict_type` VALUES (600214, '000000', '备件退库单状态', 'mes_spare_return_status', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'MES备件退库单状态: DRAFT/CONFIRMED/CANCELLED');
INSERT IGNORE INTO `ry-vue`.`sys_dict_type` VALUES (600215, '000000', '备件退库类型', 'mes_spare_return_type', 103, 1, '2026-09-20 00:00:00', NULL, NULL, 'MES备件退库类型: REPAIR_RETURN/EXCESS_RETURN/OTHER');

INSERT IGNORE INTO `ry-vue`.`sys_dict_data` VALUES (600274, '000000', 1, '草稿', 'DRAFT', 'mes_spare_return_status', '', 'info', 'Y', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '可修改/删除/确认');
INSERT IGNORE INTO `ry-vue`.`sys_dict_data` VALUES (600275, '000000', 2, '已确认', 'CONFIRMED', 'mes_spare_return_status', '', 'success', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '已逐行过账, 终态');
INSERT IGNORE INTO `ry-vue`.`sys_dict_data` VALUES (600276, '000000', 3, '已作废', 'CANCELLED', 'mes_spare_return_status', '', 'danger', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '作废, 终态');
INSERT IGNORE INTO `ry-vue`.`sys_dict_data` VALUES (600277, '000000', 1, '维修退库', 'REPAIR_RETURN', 'mes_spare_return_type', '', 'primary', 'Y', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '维修换件后剩余/拆回备件退库');
INSERT IGNORE INTO `ry-vue`.`sys_dict_data` VALUES (600278, '000000', 2, '余料退库', 'EXCESS_RETURN', 'mes_spare_return_type', '', 'success', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '领用未用完退回仓库');
INSERT IGNORE INTO `ry-vue`.`sys_dict_data` VALUES (600279, '000000', 3, '其他', 'OTHER', 'mes_spare_return_type', '', 'info', 'N', 103, 1, '2026-09-20 00:00:00', NULL, NULL, '其他退库情形');

-- 核对: SELECT d.dict_code, d.dict_label, d.dict_value, t.dict_type
--         FROM `ry-vue`.`sys_dict_data` d JOIN `ry-vue`.`sys_dict_type` t ON t.dict_type = d.dict_type
--        WHERE d.dict_type IN ('mes_spare_return_status', 'mes_spare_return_type');

-- =============================================================================
-- 6. 定时任务种子: MES 备件 ERP 过账对账/自动重投（库: ry-vue；幂等）
-- -----------------------------------------------------------------------------
-- 背景: 生产 snail-job.enabled=false 时 SnailJob 客户端不启动, 任务类另有
--       @Scheduled(cron="0 */10 * * * ?") 本地兜底（SchedulerFallbackUtils: 开关判断 + Lock4j 锁）;
--       本节仅补齐 SnailJob 侧定义, 使 snail-job.enabled=true 的环境也能调度。
-- id 占用核对: sj_job 现有 1-30 中的 1-3,200,29; 本批次用 30 / biz_30。
--              sj_job_executor 现有 1-45,200 → 用 46。
-- 注意: deleted 必须为 0(0=未删除); 写成 1 会被 SnailJob 视为已删除而不触发。
-- =============================================================================
INSERT IGNORE INTO `ry-vue`.`sj_job` VALUES (30, 'dev', 'biz_30', 'ruoyi_group', 'MES备件ERP过账对账任务', '', 1, UNIX_TIMESTAMP(NOW()) * 1000, 1, 1, 4, 1, 'eqSpareErpSyncTask', 3, '0 */10 * * * ?', 1, 300, 3, 1, 60, 116, 0, '', 1, '{}', 'MES-EQ-04 收口: 每10分钟扫描 eq_spare_erp_sync 中 PENDING/SENT/FAILED 且停滞的记录, 先对账(ERP流水已落账则只标SYNCED)再重投; 单条失败只记WARN不中断整批', '', 0, '2026-09-20 00:00:00', '2026-09-20 00:00:00');
INSERT IGNORE INTO `ry-vue`.`sj_job_executor` VALUES (46, 'dev', 'ruoyi_group', 'eqSpareErpSyncTask', '1', '2026-09-20 00:00:00', '2026-09-20 00:00:00');

-- 核对: SELECT j.id, j.biz_id, j.executor_info, j.trigger_interval, j.job_status, j.deleted
--         FROM `ry-vue`.`sj_job` j WHERE j.executor_info = 'eqSpareErpSyncTask';
--       SELECT id, group_name, executor_name FROM `ry-vue`.`sj_job_executor` WHERE executor_name = 'eqSpareErpSyncTask';

-- =============================================================================
-- 回滚语句（按需反向执行）
-- =============================================================================
-- 6) DELETE FROM `ry-vue`.`sj_job` WHERE `id` = 30 AND `executor_info` = 'eqSpareErpSyncTask';
--    DELETE FROM `ry-vue`.`sj_job_executor` WHERE `id` = 46 AND `executor_name` = 'eqSpareErpSyncTask';
-- 5) DELETE FROM `ry-vue`.`sys_dict_data` WHERE `dict_code` BETWEEN 600274 AND 600279;
--    DELETE FROM `ry-vue`.`sys_dict_type` WHERE `dict_id` IN (600214, 600215);
-- 4) DELETE FROM `ry-vue`.`sys_menu` WHERE `menu_id` BETWEEN 4384 AND 4389;
--    DELETE FROM `ry-vue`.`sys_menu` WHERE `menu_id` IN (4381, 4383);
-- 3) 回填不可逆(无法区分人工维护与脚本回填的单价):
--    如需彻底回滚, 先备份 `SELECT spare_id, unit_cost FROM ry-mes.eq_spare_part;`, 再执行
--    UPDATE `ry-mes`.`eq_spare_part` SET `unit_cost` = 0 WHERE `del_flag` = '0';
--    （注意: 该语句会一并清掉人工维护的单价, 请谨慎。）
-- 2) DROP TABLE IF EXISTS `eq_spare_return_line`;
--    DROP TABLE IF EXISTS `eq_spare_return`;
-- 1) ALTER TABLE `ry-mes`.`eq_spare_part` DROP COLUMN `unit_cost`;
