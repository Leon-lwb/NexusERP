-- 2026-08-16 wm 条码/批次/序列号关联修复（问题⑥：库存事务增加 SN 维度，按 SN 可追溯出入库流水）
ALTER TABLE `wm_material_stock_tx`
    ADD COLUMN `sn_id`   bigint      NULL COMMENT '序列号ID' AFTER `batch_code`,
    ADD COLUMN `sn_code` varchar(64) NULL COMMENT '序列号编码' AFTER `sn_id`,
    ADD INDEX `idx_wm_stock_tx_sn_id` (`sn_id`);