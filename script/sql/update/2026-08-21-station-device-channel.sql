-- =====================================================================
-- 工位终端设备通道 (nexus-station) 后端支持 — 增量脚本
-- 文档: doc/station/plans/2026-08-21-station-device-channel-plan.md
-- 目标库: ry-mes
-- 日期: 2026-08-21
-- 说明: 建表前先用 IF NOT EXISTS 兜底; ALTER 前用存在性判断, 可重复执行
-- =====================================================================

-- 1. 安灯增加工位编码(C7/C8 设备通道呼叫按工位查询)
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pro_andon' AND COLUMN_NAME = 'station_code');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE pro_andon ADD COLUMN station_code varchar(64) NULL COMMENT ''工位编码(设备通道呼叫)'' AFTER step_record_id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pro_andon' AND INDEX_NAME = 'idx_station_code');
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE pro_andon ADD INDEX idx_station_code (station_code)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 工位终端设备台账(C10 心跳 upsert 承载)
CREATE TABLE IF NOT EXISTS mes_station_device (
  device_id       bigint       NOT NULL COMMENT '设备ID',
  tenant_id       varchar(20)  NULL DEFAULT '000000',
  device_code     varchar(64)  NOT NULL COMMENT '设备编码(工控机)',
  station_code    varchar(64)  NULL COMMENT '绑定工位编码',
  process_code    varchar(64)  NULL COMMENT '绑定工序编码',
  app_version     varchar(32)  NULL COMMENT '终端版本',
  last_heartbeat  datetime     NULL COMMENT '最后心跳时间',
  create_dept     bigint       NULL,
  create_by       bigint       NULL,
  create_time     datetime     NULL DEFAULT CURRENT_TIMESTAMP,
  update_by       bigint       NULL,
  update_time     datetime     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag        char(1)      NULL DEFAULT '0',
  PRIMARY KEY (device_id),
  UNIQUE INDEX uk_device_code (device_code)
) COMMENT '工位终端设备台账';

-- 3. 设备通道报工记录(C3/C4 幂等承载, 唯一索引兜底离线队列重放去重)
CREATE TABLE IF NOT EXISTS mes_station_report (
  report_id       bigint        NOT NULL COMMENT '报工记录ID',
  tenant_id       varchar(20)   NULL DEFAULT '000000',
  report_type     varchar(16)   NOT NULL COMMENT '报工类型: SINGLE(单件过站)/BATCH(批量计数)',
  sn_code         varchar(64)   NULL COMMENT 'SN编码(SINGLE)',
  station_code    varchar(64)   NOT NULL COMMENT '工位编码',
  card_id         bigint        NULL COMMENT '流转卡ID',
  step_record_id  bigint        NULL COMMENT '流转卡工序记录ID',
  mes_wo_id       bigint        NULL COMMENT 'MES工单ID',
  wo_no           varchar(64)   NULL COMMENT '工单编号(冗余)',
  operator_id     bigint        NULL COMMENT '操作员用户ID',
  operator_code   varchar(32)   NULL COMMENT '操作员工号',
  operator_name   varchar(64)   NULL COMMENT '操作员姓名',
  result          varchar(8)    NULL COMMENT '结果: PASS/FAIL(SINGLE)',
  defect_codes    varchar(512)  NULL COMMENT '不良代码(逗号分隔)',
  good_qty        decimal(18,4) NULL DEFAULT 0 COMMENT '良品数量',
  defect_qty      decimal(18,4) NULL DEFAULT 0 COMMENT '不良数量',
  client_ts       bigint        NOT NULL COMMENT '客户端时间戳(毫秒)',
  client_key      varchar(160)  NOT NULL COMMENT '幂等键: SINGLE|sn|station|ts 或 BATCH|station|ts',
  create_dept     bigint        NULL,
  create_by       bigint        NULL,
  create_time     datetime      NULL DEFAULT CURRENT_TIMESTAMP,
  update_by       bigint        NULL,
  update_time     datetime      NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag        char(1)       NULL DEFAULT '0',
  PRIMARY KEY (report_id),
  UNIQUE INDEX uk_tenant_client_key (tenant_id, client_key),
  INDEX idx_sn_code (sn_code),
  INDEX idx_station_code (station_code),
  INDEX idx_step_record (step_record_id)
) COMMENT '工位终端报工记录(设备通道)';

-- 4. 关键件绑定操作日志(C5/C6 留痕)
CREATE TABLE IF NOT EXISTS mes_sn_bind_log (
  log_id               bigint       NOT NULL COMMENT '日志ID',
  tenant_id            varchar(20)  NULL DEFAULT '000000',
  action               varchar(16)  NOT NULL COMMENT '操作: BIND/UNBIND/REBIND',
  parent_sn_id         bigint       NULL COMMENT '父SN ID',
  parent_sn_code       varchar(64)  NULL COMMENT '父SN编码',
  component_sn_id      bigint       NULL COMMENT '组件SN ID',
  component_sn_code    varchar(64)  NULL COMMENT '组件SN编码',
  new_component_sn_code varchar(64) NULL COMMENT '换板新组件SN编码(REBIND)',
  reason               varchar(255) NULL COMMENT '操作原因(解绑/换板必填)',
  operator_code        varchar(32)  NULL COMMENT '操作员工号',
  create_dept          bigint       NULL,
  create_by            bigint       NULL,
  create_time          datetime     NULL DEFAULT CURRENT_TIMESTAMP,
  update_by            bigint       NULL,
  update_time          datetime     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag             char(1)      NULL DEFAULT '0',
  PRIMARY KEY (log_id),
  INDEX idx_parent (parent_sn_id),
  INDEX idx_component (component_sn_id)
) COMMENT '关键件绑定操作日志';
