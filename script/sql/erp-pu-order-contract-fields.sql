-- ============================================================
-- 采购订单合同关联字段（预留）
-- 说明：为采购订单表增加合同ID和合同编号字段，
--       待采购合同模块开发后正式启用关联。
-- 执行时间：2026-06-15
-- ============================================================

-- 采购订单表新增合同关联字段
ALTER TABLE pu_purchase_order ADD COLUMN contract_id   bigint(20)    NULL COMMENT '合同ID，关联pu_contract表（预留字段）' AFTER select_flow_id;
ALTER TABLE pu_purchase_order ADD COLUMN contract_no   varchar(50)   NULL COMMENT '合同编号（预留字段）' AFTER contract_id;
