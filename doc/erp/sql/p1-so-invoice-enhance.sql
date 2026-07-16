-- =====================================================================
-- so_invoice 增强(P1 地基)
-- 目标库:ry-erp
--
-- 补齐:发票代码、购买方开票四要素、红冲原发票、审批流程ID(修既有缺陷:
--   SoInvoice 实体有 selectFlowId 但建表 DDL 无此列,从未持久化)、开票渠道、
--   数电票号码、电票 PDF/OFD OSS对象ID、乐观锁版本号。
-- OSS 约定:pdf_oss_id/ofd_oss_id 存 ossId(私有桶),VO 用 @Translation(OSS_ID_TO_URL) 解析。
-- 乐观锁:新建发票 version=0(不初始化则 WHERE version=NULL 永不匹配)。
-- =====================================================================
USE `ry-erp`;

ALTER TABLE `so_invoice`
  ADD COLUMN `invoice_code` varchar(50) NULL DEFAULT NULL COMMENT '发票代码(数电票时代可与号码合并)' AFTER `invoice_no`,
  ADD COLUMN `buyer_tax_id` varchar(30) NULL DEFAULT NULL COMMENT '购买方纳税人识别号' AFTER `client_name`,
  ADD COLUMN `buyer_title` varchar(200) NULL DEFAULT NULL COMMENT '购买方发票抬头' AFTER `buyer_tax_id`,
  ADD COLUMN `buyer_bank_name` varchar(100) NULL DEFAULT NULL COMMENT '购买方开户行' AFTER `buyer_title`,
  ADD COLUMN `buyer_bank_account` varchar(50) NULL DEFAULT NULL COMMENT '购买方银行账号' AFTER `buyer_bank_name`,
  ADD COLUMN `buyer_address_phone` varchar(200) NULL DEFAULT NULL COMMENT '购买方地址、电话' AFTER `buyer_bank_account`,
  ADD COLUMN `original_invoice_id` bigint(20) NULL DEFAULT NULL COMMENT '红冲原发票ID(NULL=蓝字正数)' AFTER `contract_id`,
  ADD COLUMN `red_flag` char(1) NULL DEFAULT '0' COMMENT '是否红字发票 0-否 1-是' AFTER `original_invoice_id`,
  ADD COLUMN `select_flow_id` bigint(20) NULL DEFAULT NULL COMMENT '审批流程ID' AFTER `status`,
  ADD COLUMN `issue_channel` varchar(20) NULL DEFAULT 'MANUAL' COMMENT '开票渠道 MANUAL-手工 ELECTRONIC-电子发票平台' AFTER `select_flow_id`,
  ADD COLUMN `e_invoice_no` varchar(50) NULL DEFAULT NULL COMMENT '数电票号码(平台返回)' AFTER `issue_channel`,
  ADD COLUMN `pdf_oss_id` bigint(20) NULL DEFAULT NULL COMMENT '发票PDF的OSS对象ID(私有桶存ossId)' AFTER `e_invoice_no`,
  ADD COLUMN `ofd_oss_id` bigint(20) NULL DEFAULT NULL COMMENT '发票OFD的OSS对象ID' AFTER `pdf_oss_id`,
  ADD COLUMN `version` int(11) NULL DEFAULT 0 COMMENT '乐观锁版本号' AFTER `ofd_oss_id`,
  ADD INDEX `idx_original_invoice_id`(`original_invoice_id`) USING BTREE,
  ADD INDEX `idx_issue_channel`(`issue_channel`) USING BTREE;

-- 校验(执行后人工核对):
-- SHOW COLUMNS FROM so_invoice LIKE 'version';
-- SHOW COLUMNS FROM so_invoice LIKE 'select_flow_id';
