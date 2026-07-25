-- IQC 门禁: 来料检验结果按物料行落库
-- 配合 confirmShelf(待上架->待入库) 硬门禁: 需检物料必须 IQC 合格才允许上架
-- wm_arrival_notice_line 原有 iqc_check(需检标志)/iqc_id/iqc_code/quantity_quanlified, 缺检验结果列
-- ErpIqcCompletedConsumer 回写时按 (notice_id, material_id) 定位行, 写 iqc_status

ALTER TABLE `wm_arrival_notice_line`
    ADD COLUMN `iqc_status` varchar(16) NULL COMMENT 'IQC检验结果(QUALIFIED/UNQUALIFIED, NULL=未完成)' AFTER `iqc_code`;
