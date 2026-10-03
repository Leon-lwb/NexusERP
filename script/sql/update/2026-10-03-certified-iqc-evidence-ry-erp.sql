-- 任务 Q / 2026-10-03：仅适用于 ry-erp。执行前先核对环境和基线，并备份。
-- 代理不执行此文件。此文件不修改主初始化 SQL 或任何既有表。
-- 此表仅用于 V1 兼容和审计：不保存原生 MES 原始数量单位或
-- 创建时来源范围契约。合格 IQC 证据写入与认证入库保持关闭，
-- 直到实现独立且真实的创建时来源采集契约。不得从当前 ERP 回填。
-- 同一收货单和物料的相等数量合计、当前 ERP 基本单位以及 qc_inspect_detail 的测量
-- 单位，都不能证明历史 IQC 数量单位，即使所有数量均为 12。
-- 运行时核验原生 DECIMAL、InnoDB 以及完整永久唯一索引定义；此表结构核验
-- 本身绝不能补足缺失的单位来源证明。既有有效 V1 摘要不会升级授权。
-- 收货单头或增删改查接口的 iqc_status、消息信封中的合格结论和数量、外部已验证 DTO
-- 均不得用于填充此表。不得从历史展示字段回填授权。
-- 主线程维护全新安装定义和变更登记。代理不执行此增量脚本。

CREATE TABLE `wm_certified_iqc_evidence` (
  `evidence_id` bigint(20) NOT NULL COMMENT 'ERP不可变IQC证据ID',
  `tenant_id` varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '真实消息与MES/ERP一致租户',
  `inspection_id` bigint(20) NOT NULL COMMENT '真实MES qc_iqc.iqc_id',
  `inspection_code` varchar(64) NOT NULL COMMENT '真实MES IQC编号',
  `receive_id` bigint(20) NOT NULL COMMENT '原真实ERP收货单',
  `material_id` bigint(20) NOT NULL COMMENT '原收货全物料范围',
  `base_uom_id` bigint(20) NOT NULL COMMENT 'V1当前ERP观察单位；非原MES数量单位证明，不得授权',
  `quantity_scale` tinyint(3) unsigned NOT NULL COMMENT '已核可信本单位精度；此来源仅支持0至4',
  `inspected_quantity` decimal(14,4) NOT NULL COMMENT 'MES真实检验量；须等于原收货同料全量',
  `inspect_result` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '真实QUALIFIED或UNQUALIFIED',
  `inspector_id` bigint(20) NOT NULL COMMENT '真实MES完成检验员',
  `source_kind` varchar(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '固定MES_QC_IQC_RECEIVE_MATERIAL_V1',
  `source_version` varchar(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'QC_IQC_CURRENT_V1与实际数据主体摘要',
  `actual_body_sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'MES原始当前单头及明细完整数据主体摘要',
  `receipt_scope_sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原ERP收货同料全行数量单位来源摘要',
  `evidence_sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '本不可变证据字段完整性摘要',
  `first_message_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '首个真实消息标识；不是质量授权',
  `source_created_at` datetime(3) NOT NULL COMMENT 'MES真实创建时间',
  `completed_at` datetime(3) NOT NULL COMMENT 'MES真实检验完成时间',
  `source_updated_at` datetime(3) DEFAULT NULL COMMENT 'MES当前更新时间；无乐观版本列不据此猜最新',
  `observed_at` datetime(3) NOT NULL COMMENT '独立MES只读可重复读事务观察完成时间',
  `certified_at` datetime(3) NOT NULL COMMENT '独立ERP自主管理的可重复读事务证据落账时间',
  PRIMARY KEY (`evidence_id`),
  UNIQUE KEY `uk_certified_iqc_inspection` (`tenant_id`, `inspection_id`),
  UNIQUE KEY `uk_certified_iqc_scope` (`tenant_id`, `receive_id`, `material_id`),
  UNIQUE KEY `uk_certified_iqc_message` (`tenant_id`, `first_message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MES真实完成来源的ERP不可变IQC证据';
