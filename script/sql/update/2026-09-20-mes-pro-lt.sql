-- =============================================================================
-- 2026-09-20 MES 生产(pro)/批次消耗(lt) 修复增量脚本
-- 用途: ① pro_flow_card 新增 aggregated_flag —— 流转卡完工数量"一次性入账"标记(MES-PRO-01)
--         卡完工时以 CAS(0 -> 1)抢入账权, 成功才向工单聚合 completed_qty/scrap_qty,
--         从而 ①重复完工不重复入账(幂等) ②工序已全完成而卡曾完工未入账时可自愈补入账。
-- 执行前提: 先备份 ry-mes 库; MySQL 8.0; 需在部署含本次代码改动的版本之前执行。
-- 影响库: ry-mes
-- 是否需清 Redis 缓存: 否
-- 可重复执行: 是(ALTER 前先查 information_schema; 数据订正语句本身幂等)
-- =============================================================================

-- 1) 新增列(幂等)
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'pro_flow_card'
    AND COLUMN_NAME = 'aggregated_flag'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE pro_flow_card ADD COLUMN aggregated_flag TINYINT NOT NULL DEFAULT 0 COMMENT ''完工数量是否已一次性入账到工单(0=未入账 1=已入账)'' AFTER scrap_qty',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 存量数据订正(必须执行): 已完工/已关闭的历史卡其完工数量在旧逻辑中已聚合过工单,
--    标记为已入账, 避免本次代码上线后再次触发"完工"造成工单完工数量重复累加。
UPDATE pro_flow_card
SET aggregated_flag = 1
WHERE aggregated_flag = 0
  AND card_status IN ('COMPLETED', 'CLOSED');

-- 3) 可选修复(默认注释, 按需人工执行): MES-PRO-01 历史"手工提前完工"卡 ——
--    卡 COMPLETED 但 completed_qty = 0(完工数曾被重算为0, 工单永久 PARTIAL_COMPLETED)。
--    执行下方语句把这类卡的入账标记清零, 之后对该卡再点一次「完工」
--    (此时工序已全部完成, 门禁通过)即会按工序实际报工数补入账并重新判定工单完工, 自愈卡死。
--    执行前请先用 SELECT 核对行数与影响范围。
-- SELECT card_id, card_no, card_status, completed_qty, planned_qty
-- FROM pro_flow_card
-- WHERE card_status IN ('COMPLETED', 'CLOSED') AND (completed_qty IS NULL OR completed_qty = 0);
-- UPDATE pro_flow_card SET aggregated_flag = 0
-- WHERE card_status IN ('COMPLETED', 'CLOSED') AND (completed_qty IS NULL OR completed_qty = 0);

-- 说明: 本次修复的其余项(批次原子扣减 MES-LT-01、物料一致性/超领 MES-LT-02、
--       效期拦截 MES-LT-03、QC 放行门禁 MES-LT-06、工单状态服务端强制 MES-PRO-04、
--       MBOM 事务自调用 MES-PRO-05)均为代码级修复, 不需要 DDL。
--       MES-PRO-09(权限种子)与 MES-PRO-10/LT-05(唯一索引补 tenant_id)已由其它批次处理, 本脚本不重复。
