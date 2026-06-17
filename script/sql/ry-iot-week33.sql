-- =============================================
-- NexusERP - IoT模块 Week 33 初始化SQL
-- 数据库: ry-iot
-- 说明: IoT阶段一最小链路 3 张核心表（产品/设备/设备数据）
-- 创建时间: 2026-06-17
-- 主键策略: 与ERP/MES保持一致，雪花算法(bigint)，无自增
-- ---------------------------------------------
-- 与 IoT文档(script/doc/NexusERP_IoT文档.md 第二节) DDL 的差异说明（对齐项目规范）：
--   A. tenant_id 由 BIGINT DEFAULT 0 改为 VARCHAR(20) DEFAULT '000000'
--      （与 ERP/MES 全表一致，实体 TenantEntity.tenantId 为 String）
--   B. 业务表 iot_product / iot_device 补 del_flag + 完整审计列
--      （全局 enableLogicDelete:true，业务表宜软删）
--   C. iot_device_data 为高频时序数据，不补 del_flag/审计列，仅 tenant_id + create_time
--      （对应实体 IotDeviceData 不继承 BaseEntity/TenantEntity，独立声明字段）
--   D. 文档 DDL 列顺序/审计列不完整，本脚本补齐 create_dept/create_by/update_by/update_time
-- ---------------------------------------------
-- 执行方式: 由开发者本地执行
--   CREATE DATABASE 与 3 张表 DDL 均在本脚本内，直接整文件执行即可
-- =============================================

-- -----------------------------------------------------------
-- 1. 建库
-- -----------------------------------------------------------
CREATE DATABASE IF NOT EXISTS `ry-iot`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `ry-iot`;

-- -----------------------------------------------------------
-- 2. IoT产品表 (iot_product) — 设备模板
-- -----------------------------------------------------------
-- product_type: DEVICE(直连设备) / GATEWAY(网关)
-- protocol_type: MQTT/OPC_UA/MODBUS/HTTP
DROP TABLE IF EXISTS `iot_product`;
CREATE TABLE `iot_product` (
    `product_id`       bigint(20)     NOT NULL COMMENT '产品ID(主键)',
    `tenant_id`        varchar(20)    NOT NULL DEFAULT '000000' COMMENT '租户ID',
    `product_code`     varchar(64)    NOT NULL DEFAULT '' COMMENT '产品编码',
    `product_name`     varchar(128)   NOT NULL DEFAULT '' COMMENT '产品名称',
    `product_type`     varchar(32)    NULL DEFAULT 'DEVICE' COMMENT '产品类型: DEVICE/GATEWAY',
    `protocol_type`    varchar(32)    NOT NULL DEFAULT 'MQTT' COMMENT '协议类型: MQTT/OPC_UA/MODBUS/HTTP',
    `description`      varchar(500)   NULL DEFAULT NULL COMMENT '描述',
    `status`           varchar(16)    NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/INACTIVE',
    `remark`           varchar(500)   NULL DEFAULT NULL COMMENT '备注',
    `create_dept`      bigint(20)     NULL DEFAULT NULL COMMENT '创建部门',
    `create_by`        bigint(20)     NULL DEFAULT NULL COMMENT '创建人',
    `create_time`      datetime       NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        bigint(20)     NULL DEFAULT NULL COMMENT '更新人',
    `update_time`      datetime       NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag`         char(1)        NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
    PRIMARY KEY (`product_id`) USING BTREE,
    UNIQUE INDEX `uk_code` (`product_code`, `tenant_id`) USING BTREE,
    INDEX `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'IoT产品(设备模板)' ROW_FORMAT = DYNAMIC;

-- -----------------------------------------------------------
-- 3. IoT设备实例表 (iot_device)
-- -----------------------------------------------------------
-- connection_status: ONLINE/OFFLINE
-- mes_equipment_id: 关联MES设备ID（接口查询，无外键）
DROP TABLE IF EXISTS `iot_device`;
CREATE TABLE `iot_device` (
    `device_id`          bigint(20)     NOT NULL COMMENT '设备ID(主键)',
    `tenant_id`          varchar(20)    NOT NULL DEFAULT '000000' COMMENT '租户ID',
    `device_code`        varchar(64)    NOT NULL DEFAULT '' COMMENT '设备编码(MQTT ClientID)',
    `device_name`        varchar(128)   NOT NULL DEFAULT '' COMMENT '设备名称',
    `product_id`         bigint(20)     NOT NULL COMMENT '产品ID',
    `product_name`       varchar(128)   NULL DEFAULT NULL COMMENT '产品名称(冗余)',
    `protocol_type`      varchar(32)    NOT NULL DEFAULT 'MQTT' COMMENT '协议类型: MQTT/OPC_UA/MODBUS/HTTP',
    `connection_status`  varchar(16)    NULL DEFAULT 'OFFLINE' COMMENT '连接状态: ONLINE/OFFLINE',
    `last_online_time`   datetime       NULL DEFAULT NULL COMMENT '最后在线时间',
    `device_secret`      varchar(128)   NULL DEFAULT NULL COMMENT '设备密钥(MQTT认证)',
    `ip_address`         varchar(64)    NULL DEFAULT NULL COMMENT 'IP地址',
    `firmware_version`   varchar(64)    NULL DEFAULT NULL COMMENT '固件版本',
    `location`           varchar(256)   NULL DEFAULT NULL COMMENT '位置',
    `mes_equipment_id`   bigint(20)     NULL DEFAULT NULL COMMENT '关联MES设备ID(接口查询,无外键)',
    `status`             varchar(16)    NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/INACTIVE',
    `remark`             varchar(500)   NULL DEFAULT NULL COMMENT '备注',
    `create_dept`        bigint(20)     NULL DEFAULT NULL COMMENT '创建部门',
    `create_by`          bigint(20)     NULL DEFAULT NULL COMMENT '创建人',
    `create_time`        datetime       NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`          bigint(20)     NULL DEFAULT NULL COMMENT '更新人',
    `update_time`        datetime       NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag`           char(1)        NULL DEFAULT '0' COMMENT '删除标志(0正常 1删除)',
    PRIMARY KEY (`device_id`) USING BTREE,
    UNIQUE INDEX `uk_code` (`device_code`, `tenant_id`) USING BTREE,
    INDEX `idx_product` (`product_id`) USING BTREE,
    INDEX `idx_status` (`connection_status`) USING BTREE,
    INDEX `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'IoT设备实例' ROW_FORMAT = DYNAMIC;

-- -----------------------------------------------------------
-- 4. IoT设备数据表 (iot_device_data) — 原始采集数据
-- -----------------------------------------------------------
-- 高频时序数据：不补 del_flag/审计列，仅 tenant_id + create_time
-- 触发迁移条件: 表行数超过1000万 → 引入 TDengine（阶段二按需）
-- quality: GOOD/BAD/UNCERTAIN
-- source: MQTT/OPC_UA/MODBUS/HTTP
DROP TABLE IF EXISTS `iot_device_data`;
CREATE TABLE `iot_device_data` (
    `data_id`          bigint(20)     NOT NULL COMMENT '数据ID(主键)',
    `tenant_id`        varchar(20)    NOT NULL DEFAULT '000000' COMMENT '租户ID',
    `device_id`        bigint(20)     NOT NULL COMMENT '设备ID',
    `device_code`      varchar(64)    NULL DEFAULT NULL COMMENT '设备编码(冗余)',
    `property_code`    varchar(64)    NOT NULL DEFAULT '' COMMENT '属性编码',
    `property_name`    varchar(128)   NULL DEFAULT NULL COMMENT '属性名称',
    `data_type`        varchar(16)    NULL DEFAULT NULL COMMENT '数据类型: NUMERIC/TEXT/BOOL',
    `numeric_value`    decimal(14,4)  NULL DEFAULT NULL COMMENT '数值型值',
    `text_value`       varchar(500)   NULL DEFAULT NULL COMMENT '文本型值',
    `bool_value`       tinyint(1)     NULL DEFAULT NULL COMMENT '布尔型值(0/1)',
    `collect_time`     datetime       NOT NULL COMMENT '采集时间(设备侧)',
    `receive_time`     datetime       NOT NULL COMMENT '接收时间(服务端)',
    `quality`          varchar(16)    NULL DEFAULT 'GOOD' COMMENT '数据质量: GOOD/BAD/UNCERTAIN',
    `source`           varchar(32)    NULL DEFAULT 'MQTT' COMMENT '数据来源: MQTT/OPC_UA/MODBUS/HTTP',
    `create_time`      datetime       NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`data_id`) USING BTREE,
    INDEX `idx_device_time` (`device_id`, `collect_time`) USING BTREE,
    INDEX `idx_property` (`device_id`, `property_code`, `collect_time`) USING BTREE,
    INDEX `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'IoT设备数据(原始)' ROW_FORMAT = DYNAMIC;

-- =============================================
-- Week 33 已完成: 建库 + 3 张核心表 DDL
-- 待 Week 34: Redis Streams 消费者组 (XGROUP CREATE iot:data:stream iot-consumers 0 MKSTREAM)
-- =============================================
