-- =====================================================================
-- 工位物料上料台账 (M3 批次4 上料防错) — 增量脚本
-- 文档: doc/station/plans/2026-08-21-station-code-review-and-next-plan.md (阶段4 批次4)
-- 目标库: ry-mes
-- 日期: 2026-08-21
-- 说明: CREATE TABLE IF NOT EXISTS, 可重复执行
-- =====================================================================

USE `ry-mes`;

CREATE TABLE IF NOT EXISTS mes_material_load (
  load_id         bigint       NOT NULL COMMENT '台账ID',
  tenant_id       varchar(20)  NULL DEFAULT '000000' COMMENT '租户ID',
  mes_wo_id       bigint       NULL COMMENT '工单ID(pro_work_order.mes_wo_id)',
  wo_no           varchar(64)  NULL COMMENT '工单编号',
  station_code    varchar(64)  NOT NULL COMMENT '工位编码',
  process_code    varchar(64)  NULL COMMENT '工序编码',
  workstation_code varchar(64) NULL COMMENT '工作中心编码(MBOM快照冗余)',
  material_id     bigint       NOT NULL COMMENT '物料ID',
  material_code   varchar(64)  NULL COMMENT '物料编码',
  material_name   varchar(128) NULL COMMENT '物料名称',
  batch_id        bigint       NULL COMMENT '批次ID(wm_batch.batch_id)',
  batch_code      varchar(64)  NULL COMMENT '批次编码(wm_batch.batch_code)',
  feeder_slot     varchar(32)  NULL COMMENT '料站号(可空, 贴片机 feeder 位号等)',
  is_alternative  char(1)      NULL DEFAULT 'N' COMMENT '是否替代料匹配: Y=bd_bom_alternative替代料, N=BOM主料',
  operator_code   varchar(64)  NULL COMMENT '上料人工号',
  operator_name   varchar(64)  NULL COMMENT '上料人姓名',
  load_time       datetime     NOT NULL COMMENT '上料时间',
  status          varchar(16)  NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE=在机, REPLACED=已换料',
  remark          varchar(500) NULL COMMENT '备注',
  create_dept     bigint       NULL,
  create_by       bigint       NULL,
  create_time     datetime     NULL DEFAULT CURRENT_TIMESTAMP,
  update_by       bigint       NULL,
  update_time     datetime     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag        char(1)      NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (load_id),
  INDEX idx_mes_wo_id (mes_wo_id),
  INDEX idx_station_code (station_code),
  INDEX idx_material (material_id),
  INDEX idx_load_time (load_time)
) COMMENT '工位物料上料台账(上料防错 C19 写入, 换料置 REPLACED)';
