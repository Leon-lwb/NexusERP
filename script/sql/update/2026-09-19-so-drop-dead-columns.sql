-- 销售模块（so）死字段清理（决策 Q4）
-- 背景（2026-09-19 销售模块审计 + 用户决策「死字段删」）:
--   下列 5 个列在**前后端均无任何读写逻辑**：只有实体/BO/VO 的字段声明、
--   前端 types.ts 的类型声明与表单 initForm 里的默认值，没有任何界面绑定、
--   也没有参与计算或查询。
--   注：审计原报告列了「13 个死字段」，但其判定依据是「Java getter/setter 全仓零命中」——
--       该口径有缺陷：Jackson 是反射序列化、且字段可能只被前端 JSON 使用，
--       在 Java 源码里 grep 不到 getter 并不等于字段是死的。
--       复核后确认其中 8 个是**前端真实在用的 UI 字段**（v-model 双向绑定 / 参与行金额计算 /
--       从客户订单带入），**不能删**，已从清单剔除。详见计划文档 §九 与交接报告。
-- 目标库: ERP 业务库 (ry-erp)。
-- 执行方式: 一次性脚本。DROP COLUMN 不支持 IF EXISTS 的幂等写法（MySQL 8.0 以下），
--           重复执行会在 DROP COLUMN 处报错；执行前请先跑下面的前置检查。

-- ============================================================
-- 前置检查：确认这 5 列确实全为 NULL（若已有数据，说明存在未纳入审计的写入方，应先排查再删）
-- 期望输出：5 行，每行计数均为 0
-- ============================================================
-- SELECT 'so_sales_order_header.delivery_address_id' AS col, COUNT(*) AS non_null FROM so_sales_order_header WHERE delivery_address_id IS NOT NULL
-- UNION ALL SELECT 'so_sales_order_header.invoice_address_id', COUNT(*) FROM so_sales_order_header WHERE invoice_address_id IS NOT NULL
-- UNION ALL SELECT 'so_sales_order_line.linked_po_id',          COUNT(*) FROM so_sales_order_line    WHERE linked_po_id IS NOT NULL
-- UNION ALL SELECT 'so_sales_order_line.reserved_serials',      COUNT(*) FROM so_sales_order_line    WHERE reserved_serials IS NOT NULL
-- UNION ALL SELECT 'so_sales_order_line.pa_group_id',           COUNT(*) FROM so_sales_order_line    WHERE pa_group_id IS NOT NULL;

-- ============================================================
-- 1. so_sales_order_header：删 2 个死列（送货地址ID / 发票地址ID）
--    说明：同表的 delivery_address / invoice_address（文本详情）是**在用的**，不要删
-- ============================================================
ALTER TABLE `so_sales_order_header`
    DROP COLUMN `delivery_address_id`,
    DROP COLUMN `invoice_address_id`;

-- ============================================================
-- 2. so_sales_order_line：删 3 个死列（关联采购订单ID / 预留序列号 / 配套组ID）
--    说明：同表的 linked_mo_id（关联生产工单）与 reserved_batch_no（预留批次号，
--          前端有展示列）是**在用的**，不要删
-- ============================================================
ALTER TABLE `so_sales_order_line`
    DROP COLUMN `linked_po_id`,
    DROP COLUMN `reserved_serials`,
    DROP COLUMN `pa_group_id`;

-- ============================================================
-- 3. 复核：确认列已删除（期望 0 行）
-- ============================================================
-- SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND ((TABLE_NAME = 'so_sales_order_header' AND COLUMN_NAME IN ('delivery_address_id','invoice_address_id'))
--      OR (TABLE_NAME = 'so_sales_order_line'   AND COLUMN_NAME IN ('linked_po_id','reserved_serials','pa_group_id')));
