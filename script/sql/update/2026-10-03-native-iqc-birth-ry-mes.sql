-- 2026-10-03：MES 原生 IQC 出生绑定，仅准备脚本，不自动执行。
-- 适用：人工核实目标为 ry-mes 对应业务库，qc_iqc/qc_inspect_detail 为 InnoDB，
-- qc_iqc.quantity 为 DECIMAL(14,4)，永久唯一 (tenant_id,receive_id,material_id) 已存在。
-- 执行前须备份、审核适用基线与维护窗口。MySQL 5.7 的 DDL 不承诺事务回滚。
-- 本表只由原生新建入口同事务 INSERT；不向旧 IQC 补录、不根据当前 ERP 单位或明细测量单位回填。
-- 故意不用 IF NOT EXISTS：已存在时应人工核对结构，不能掩盖不完整旧表或误重放。
CREATE TABLE `qc_receipt_iqc_birth` (
  `iqc_id` bigint(20) NOT NULL COMMENT '同事务新建的IQC身份',
  `tenant_id` varchar(20) NOT NULL COMMENT '实际租户',
  `birth_id` bigint(20) NOT NULL COMMENT 'ERP永久出生登记身份',
  `receive_id` bigint(20) NOT NULL COMMENT '原始收货身份',
  `material_id` bigint(20) NOT NULL COMMENT '原始物料身份',
  `base_uom_id` bigint(20) NOT NULL COMMENT '出生原单位，不从未来当前单位反推',
  `quantity_scale` int NOT NULL COMMENT '出生时原单位小数精度，取值0至4',
  `quantity` decimal(14,4) NOT NULL COMMENT 'ERP冻结的原单位检验数量',
  `receipt_scope_sha256` varchar(64) NOT NULL COMMENT '冻结收货完整范围摘要',
  `birth_sha256` varchar(64) NOT NULL COMMENT 'ERP出生事实完整性摘要，不独立授权',
  `captured_at` bigint(20) NOT NULL COMMENT 'ERP实际冻结毫秒时间戳',
  `receive_code` varchar(64) NOT NULL COMMENT '出生收货编码',
  `material_code` varchar(64) NOT NULL COMMENT '出生物料编码',
  `material_name` varchar(255) NOT NULL COMMENT '出生物料名称',
  `vendor_id` bigint(20) NOT NULL COMMENT '出生供应商身份',
  `vendor_name` varchar(255) NOT NULL COMMENT '出生供应商名称',
  `bound_at` datetime(3) NOT NULL COMMENT '同连接采样的全新INSERT出生绑定时间，保留毫秒',
  `binding_sha256` varchar(64) NOT NULL COMMENT 'IQC与出生事实不可变绑定摘要',
  PRIMARY KEY (`iqc_id`) USING BTREE,
  UNIQUE KEY `uk_native_iqc` (`tenant_id`,`iqc_id`) USING BTREE,
  UNIQUE KEY `uk_native_birth` (`tenant_id`,`birth_id`) USING BTREE,
  UNIQUE KEY `uk_native_scope` (`tenant_id`,`receive_id`,`material_id`) USING BTREE,
  CONSTRAINT `fk_native_iqc_birth` FOREIGN KEY (`iqc_id`) REFERENCES `qc_iqc` (`iqc_id`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='永久原生出生绑定，旧检验无绑定仍不具单位来源证明';
-- create_time 不改列、不改旧值：全新INSERT按实际原生DATETIME精度对齐；bound_at独立保留毫秒。
-- 没有INSERT SELECT回填，没有UPDATE认证标志，也不认证历史单据。
