-- 销售价格表头
CREATE TABLE so_price_list (
    price_list_id    BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    list_code        VARCHAR(64) NOT NULL,
    list_name        VARCHAR(128) NOT NULL,
    list_type        VARCHAR(16) NOT NULL DEFAULT 'BASE',
    client_id        BIGINT,
    currency         VARCHAR(8) DEFAULT 'CNY',
    priority         INT DEFAULT 0,
    valid_from       DATE,
    valid_to         DATE,
    status           VARCHAR(16) DEFAULT 'ACTIVE',
    create_by        BIGINT,
    create_dept      BIGINT,
    create_time      DATETIME,
    update_by        BIGINT,
    update_time      DATETIME,
    PRIMARY KEY (price_list_id),
    UNIQUE KEY uk_code_tenant (list_code, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售价格表头';

-- 销售价格表行
CREATE TABLE so_price_list_line (
    line_id          BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    price_list_id    BIGINT NOT NULL,
    material_id      BIGINT NOT NULL,
    unit_price       DECIMAL(14,4) NOT NULL,
    min_qty          DECIMAL(14,4) DEFAULT 0,
    max_qty          DECIMAL(14,4),
    discount_rate    DECIMAL(5,2) DEFAULT 100.00,
    create_by        BIGINT,
    create_time      DATETIME,
    update_time      DATETIME,
    PRIMARY KEY (line_id),
    INDEX idx_list_material (price_list_id, material_id),
    INDEX idx_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售价格表行';

-- 字典类型
INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_dept, create_by, create_time, remark)
VALUES (400001, '000000', '价格表类型', 'so_price_list_type', 103, 1, NOW(), '销售价格表分类');

-- 字典数据
INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, remark)
VALUES
(400001, '000000', 1, '基价', 'BASE', 'so_price_list_type', '', 'primary', 'Y', 103, 1, NOW(), '标准基价'),
(400002, '000000', 2, '客户专属', 'CUSTOMER', 'so_price_list_type', '', 'success', 'N', 103, 1, NOW(), '客户专属价'),
(400003, '000000', 3, '期间促销', 'PROMOTION', 'so_price_list_type', '', 'warning', 'N', 103, 1, NOW(), '期间促销价');

-- 信用占用记录表
CREATE TABLE so_credit_usage (
    usage_id         BIGINT NOT NULL,
    tenant_id        BIGINT NOT NULL DEFAULT 0,
    client_id        BIGINT NOT NULL,
    so_id            BIGINT NOT NULL,
    amount           DECIMAL(14,2) NOT NULL,
    status           VARCHAR(16) DEFAULT 'FROZEN',
    freeze_time      DATETIME,
    release_time     DATETIME,
    create_time      DATETIME,
    update_time      DATETIME,
    PRIMARY KEY (usage_id),
    INDEX idx_client (client_id),
    INDEX idx_so (so_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='信用占用记录';
