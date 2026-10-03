-- 仅适用于 ERP。任务 D，2026-10-03。Agent 未执行此脚本。
-- 审查基线/权限并完成备份后，仅执行一次；有意不支持静默重复执行。
-- 不回填历史订单：认证列为 NULL 不能证明已认证。
-- 不 INSERT 供应商报价/确认记录，不授予权限，也不写入 master 数据库。
-- 全新安装主 SQL 不在本任务范围内；发布负责人必须登记此迁移。
-- 所需应用权限（由不同操作人行使）：pu:order:terms:add, pu:order:terms:confirm,
-- pu:order:price, pu:order:approve, pu:order:close, pu:order:cancel。须通过经审查的管理流程授权。
ALTER TABLE pu_purchase_order
    ADD COLUMN certified_version INT NULL DEFAULT NULL COMMENT '可信订单CAS版本；历史NULL不认证',
    ADD COLUMN certified_terms_id BIGINT NULL DEFAULT NULL COMMENT '已独立确认的不可变报价版本ID';

CREATE TABLE pu_certified_purchase_order_terms (
    quote_id BIGINT NOT NULL,
    tenant_id VARCHAR(20) COLLATE utf8mb4_bin NOT NULL,
    order_id BIGINT NOT NULL,
    quote_version INT NOT NULL,
    request_key VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
    request_hash CHAR(64) COLLATE utf8mb4_bin NOT NULL,
    entered_by BIGINT NOT NULL,
    entered_at DATETIME(3) NOT NULL,
    vendor_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    base_uom_id BIGINT NOT NULL,
    source_request_key VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
    source_fingerprint CHAR(64) COLLATE utf8mb4_bin NOT NULL,
    catalog_fingerprint CHAR(64) COLLATE utf8mb4_bin NOT NULL,
    order_fingerprint CHAR(64) COLLATE utf8mb4_bin NOT NULL,
    quantity DECIMAL(18,4) NOT NULL,
    unit_price_excluding_tax DECIMAL(18,4) NOT NULL,
    tax_rate DECIMAL(10,4) NOT NULL,
    currency VARCHAR(16) COLLATE utf8mb4_bin NOT NULL,
    exchange_rate DECIMAL(18,6) NOT NULL,
    promised_arrival_date DATE NOT NULL,
    valid_from DATE NOT NULL,
    valid_until DATE NOT NULL,
    supplier_quote_reference VARCHAR(128) NOT NULL,
    supplier_contact VARCHAR(128) NOT NULL,
    evidence_reference VARCHAR(255) NOT NULL,
    evidence_sha256 CHAR(64) COLLATE utf8mb4_bin NOT NULL,
    payment_terms VARCHAR(255) NOT NULL,
    delivery_terms VARCHAR(255) NOT NULL,
    rounding_policy VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    quote_hash CHAR(64) COLLATE utf8mb4_bin NOT NULL,
    PRIMARY KEY (quote_id),
    UNIQUE KEY uk_quote_request (tenant_id,request_key),
    UNIQUE KEY uk_order_quote_version (tenant_id,order_id,quote_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购供应确认条款不可变版本；无UPDATE/DELETE API';

CREATE TABLE pu_certified_purchase_order_terms_confirmation (
    confirmation_id BIGINT NOT NULL,
    tenant_id VARCHAR(20) COLLATE utf8mb4_bin NOT NULL,
    quote_id BIGINT NOT NULL,
    quote_hash CHAR(64) COLLATE utf8mb4_bin NOT NULL,
    request_key VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
    request_hash CHAR(64) COLLATE utf8mb4_bin NOT NULL,
    actor_id BIGINT NOT NULL,
    permission_code VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    confirmed_at DATETIME(3) NOT NULL,
    review_note VARCHAR(500) NOT NULL,
    PRIMARY KEY (confirmation_id),
    UNIQUE KEY uk_confirmation_request (tenant_id,request_key),
    UNIQUE KEY uk_quote_confirmation (tenant_id,quote_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不同操作人独立确认审计；只追加不替换';

CREATE TABLE pu_certified_purchase_order_operation (
    command_id BIGINT NOT NULL,
    tenant_id VARCHAR(20) COLLATE utf8mb4_bin NOT NULL,
    order_id BIGINT NOT NULL,
    request_key VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
    request_hash CHAR(64) COLLATE utf8mb4_bin NOT NULL,
    actor_id BIGINT NOT NULL,
    operation VARCHAR(32) COLLATE utf8mb4_bin NOT NULL,
    expected_version INT NOT NULL,
    result_version INT NOT NULL,
    result_status VARCHAR(32) COLLATE utf8mb4_bin NOT NULL,
    quote_id BIGINT NULL,
    quote_hash CHAR(64) COLLATE utf8mb4_bin NULL,
    expected_arrival_date DATE NULL,
    total_amount DECIMAL(18,2) NULL,
    amount_excluding_tax DECIMAL(18,2) NULL,
    tax_amount DECIMAL(18,2) NULL,
    result_fingerprint CHAR(64) COLLATE utf8mb4_bin NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    PRIMARY KEY (command_id),
    UNIQUE KEY uk_operation_request (tenant_id,request_key),
    UNIQUE KEY uk_order_operation (tenant_id,order_id,operation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ERP提交结果永久重放；不依赖AI回写成功';
