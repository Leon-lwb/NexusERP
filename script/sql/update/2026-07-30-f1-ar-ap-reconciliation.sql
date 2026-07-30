-- ============================================================
-- F1 AR/AP 核销:收付款单 + 核销记录
-- 库: ry-erp (业务库)
-- 日期: 2026-07-30
-- 依赖: F0 GL 总账(gl_voucher/gl_voucher_line 已就绪,未清项字段 open_item/cleared/cleared_by 由 F1 写)
-- ============================================================
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ------------------------------------------------------------
-- 1. 收付款单 fin_receipt (AR收款 / AP付款 合一,direction 区分)
--    来源:spec P3 第七节;AR/AP 合二为一设计
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `fin_receipt`;
CREATE TABLE `fin_receipt` (
  `receipt_id`        bigint(20)    NOT NULL                COMMENT '收付款单ID(雪花)',
  `tenant_id`         varchar(20)   DEFAULT '000000'        COMMENT '租户ID',
  `receipt_no`        varchar(64)   NOT NULL                COMMENT '收付款单号',
  `receipt_type`      varchar(20)   DEFAULT NULL            COMMENT '收付款类型 CASH/BANK/ALIPAY/WECHAT/OTHER',
  `direction`         char(4)       NOT NULL                COMMENT '方向 AR=应收收款 AP=应付付款',
  `client_id`         bigint(20)    DEFAULT NULL            COMMENT '客户ID(direction=AR)',
  `vendor_id`         bigint(20)    DEFAULT NULL            COMMENT '供应商ID(direction=AP)',
  `payer_name`        varchar(128)  DEFAULT NULL            COMMENT '付款方名称',
  `amount`            decimal(18,4) NOT NULL DEFAULT 0.0000 COMMENT '收付款金额',
  `settled_amount`    decimal(18,4) NOT NULL DEFAULT 0.0000 COMMENT '已核销金额',
  `currency`          varchar(10)   DEFAULT 'CNY'           COMMENT '币种',
  `exchange_rate`     decimal(18,6) DEFAULT 1.000000        COMMENT '汇率',
  `third_party_no`    varchar(64)   DEFAULT NULL            COMMENT '第三方流水号',
  `pay_order_number`  varchar(64)   DEFAULT NULL            COMMENT '支付订单号(跨库关联 pay_order,不JOIN)',
  `source`            varchar(20)   DEFAULT 'MANUAL'        COMMENT '来源 MANUAL/PAY_CALLBACK',
  `status`            varchar(20)   DEFAULT 'DRAFT'         COMMENT '状态 DRAFT/CONFIRMED/SETTLED/CANCELLED',
  `receipt_date`      date          DEFAULT NULL            COMMENT '收付款日期(凭证日期)',
  `remark`            varchar(500)  DEFAULT NULL            COMMENT '备注',
  `version`           int(11)       DEFAULT 0               COMMENT '乐观锁版本号',
  `create_dept`       bigint(20)    DEFAULT NULL,
  `create_by`         bigint(20)    DEFAULT NULL,
  `create_time`       datetime      DEFAULT CURRENT_TIMESTAMP,
  `update_by`         bigint(20)    DEFAULT NULL,
  `update_time`       datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag`          char(1)       DEFAULT '0',
  PRIMARY KEY (`receipt_id`) USING BTREE,
  UNIQUE INDEX `uk_receipt_no`(`receipt_no`, `tenant_id`) USING BTREE,
  INDEX `idx_client`(`client_id`) USING BTREE,
  INDEX `idx_vendor`(`vendor_id`) USING BTREE,
  INDEX `idx_pay_order`(`pay_order_number`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '收付款单(AR收款/AP付款合一)' ROW_FORMAT = DYNAMIC;

-- ------------------------------------------------------------
-- 2. 核销记录 fin_settlement (发票 <-> 收付款单 的核销明细)
--    gl_voucher_line.cleared_by 反向指向本表 settlement_id(全额核销时清账)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `fin_settlement`;
CREATE TABLE `fin_settlement` (
  `settlement_id`     bigint(20)    NOT NULL                COMMENT '核销记录ID(雪花)',
  `tenant_id`         varchar(20)   DEFAULT '000000'        COMMENT '租户ID',
  `settlement_no`     varchar(64)   NOT NULL                COMMENT '核销单号',
  `direction`         char(4)       NOT NULL                COMMENT '方向 AR/AP',
  `invoice_id`        bigint(20)    NOT NULL                COMMENT '发票ID',
  `invoice_no`        varchar(64)   DEFAULT NULL            COMMENT '发票号(冗余)',
  `invoice_type`      varchar(20)   DEFAULT NULL            COMMENT '发票类型(冗余)',
  `receipt_id`        bigint(20)    NOT NULL                COMMENT '收付款单ID',
  `settle_amount`     decimal(18,4) NOT NULL                COMMENT '核销金额',
  `settle_date`       date          DEFAULT NULL            COMMENT '核销日期',
  `operator`          varchar(64)   DEFAULT NULL            COMMENT '操作人',
  `remark`            varchar(500)  DEFAULT NULL            COMMENT '备注',
  `create_dept`       bigint(20)    DEFAULT NULL,
  `create_by`         bigint(20)    DEFAULT NULL,
  `create_time`       datetime      DEFAULT CURRENT_TIMESTAMP,
  `update_by`         bigint(20)    DEFAULT NULL,
  `update_time`       datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag`          char(1)       DEFAULT '0',
  PRIMARY KEY (`settlement_id`) USING BTREE,
  UNIQUE INDEX `uk_settlement_no`(`settlement_no`, `tenant_id`) USING BTREE,
  INDEX `idx_invoice`(`invoice_id`) USING BTREE,
  INDEX `idx_receipt`(`receipt_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '应收应付核销记录' ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;

-- 校验
-- SHOW TABLES LIKE 'fin_%';
-- DESCRIBE fin_receipt;
-- DESCRIBE fin_settlement;
