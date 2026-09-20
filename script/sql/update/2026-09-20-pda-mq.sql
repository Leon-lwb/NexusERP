-- =============================================================================
-- 用途: MES PDA / 跨模块 MQ 契约修复配套增量脚本 (2026-09-20-pda-mq)
-- 对应审计: doc/audit/2026-09-20-audit-mes-pda-rel.md (MES-MQ-02)
-- 执行前提: 已执行 script/sql/ry-mes.sql 主脚本
-- 影响库: ry-mes
-- 是否需清 Redis 缓存: 否
-- 幂等性: 是(先查 information_schema 再决定是否 ALTER, 可重复执行)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1) pro_work_order 加租户维度唯一键 uk_tenant_erp_wo(tenant_id, erp_wo_id)
--    背景: 消费幂等原为 hasKey -> 业务处理 -> setCacheObject 非原子, 双实例并发
--          (或 basicNack 重投与首次消费重叠)可创建两张 MES 工单 -> 后续报工/完工回写 ERP 双计。
--          代码侧已改为消费前 setObjectIfAbsent 原子占位(ruoyi-common-mq BaseMessageConsumer),
--          本唯一键为最终兜底(即使占位失效也无法写入第二张)。
--
--    !! 执行前必须检查存量重复数据, 存在重复时 ALTER 会直接失败 !!
--    前置检查 SQL(返回行即存在重复, 需先人工清理/合并):
--      SELECT tenant_id, erp_wo_id, COUNT(*) AS cnt
--        FROM pro_work_order
--       WHERE erp_wo_id IS NOT NULL
--       GROUP BY tenant_id, erp_wo_id
--      HAVING cnt > 1;
--
--    注意:
--      a) erp_wo_id 允许为 NULL(手工创建的 MES 工单), MySQL 唯一键允许多个 NULL, 不受影响;
--      b) 逻辑删除(del_flag='1')的行同样参与唯一约束 —— 若某 ERP 工单的 MES 工单已被逻辑删除
--         又需要重新下达, 请先物理清理该行或复用原工单(业务查重 selectByErpWoId 带逻辑删除过滤,
--         看不到已删除行, 此时插入会因唯一键冲突失败并进入重试/死信, 属 fail-closed 预期行为)。
-- -----------------------------------------------------------------------------
SET @idx_exists := (SELECT COUNT(1) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'pro_work_order'
                      AND INDEX_NAME = 'uk_tenant_erp_wo');
SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE `pro_work_order` ADD UNIQUE INDEX `uk_tenant_erp_wo`(`tenant_id`, `erp_wo_id`) USING BTREE',
  'SELECT ''uk_tenant_erp_wo already exists, skip'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 2) 存量脏数据订正(人工确认后执行, 本脚本默认注释掉)
--    MES-MQ-01: 修复前 ERP 工单下达消息不带 tenantId, 消费端退化为 000000,
--    导致非系统租户的 MES 工单(及其工序/流转卡)落在系统租户 000000。
--    订正方式: 按 ERP 工单号/ID 从 ry-erp 反查真实租户后回填, 示例(需跨库, 请人工核对后再执行):
--
--      -- 步骤1: 找出错落租户的 MES 工单
--      SELECT w.mes_wo_id, w.wo_no, w.erp_wo_id, w.tenant_id
--        FROM pro_work_order w
--       WHERE w.tenant_id = '000000' AND w.erp_wo_id IS NOT NULL;
--      -- 步骤2: 与 ry-erp.prd_work_order 对照(wo_id = erp_wo_id)确认真实 tenant_id
--      SELECT wo_id, wo_no, tenant_id FROM `ry-erp`.prd_work_order
--       WHERE wo_id IN (/* 上一步的 erp_wo_id 列表 */);
--      -- 步骤3: 核对无误后回填(工单 + 工序/派工等从属表需同租户一并订正)
--      -- UPDATE pro_work_order SET tenant_id = '<真实租户>' WHERE mes_wo_id = <mesWoId>;
-- -----------------------------------------------------------------------------
