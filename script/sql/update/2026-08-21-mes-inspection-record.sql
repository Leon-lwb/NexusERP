-- =====================================================================
-- 设备点检历史留痕 (M3 批次2 设备点检) — 增量脚本
-- 文档: doc/station/plans/2026-08-21-station-code-review-and-next-plan.md (阶段4 批次2)
-- 目标库: ry-mes
-- 日期: 2026-08-21
-- 说明: CREATE TABLE IF NOT EXISTS, 可重复执行
-- =====================================================================

USE `ry-mes`;

CREATE TABLE IF NOT EXISTS eq_inspection_record (
  record_id     bigint       NOT NULL COMMENT '记录ID',
  tenant_id     varchar(20)  NULL DEFAULT '000000' COMMENT '租户ID',
  equipment_id  bigint       NOT NULL COMMENT '设备ID',
  station_code  varchar(64)  NULL COMMENT '工位编码(工位终端提交时留痕)',
  inspector_id  bigint       NULL COMMENT '点检人ID',
  inspector_name varchar(64) NULL COMMENT '点检人姓名',
  operator_code varchar(64)  NULL COMMENT '点检人工号(设备通道免登录留痕)',
  inspect_time  datetime     NOT NULL COMMENT '点检时间',
  results       text         NULL COMMENT '逐项点检明细 JSON: [{item,standard,actual,pass}]',
  overall_pass  varchar(8)   NULL COMMENT '总判定: Y合格/N不合格',
  remark        varchar(500) NULL COMMENT '备注',
  create_dept   bigint       NULL,
  create_by     bigint       NULL,
  create_time   datetime     NULL DEFAULT CURRENT_TIMESTAMP,
  update_by     bigint       NULL,
  update_time   datetime     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag      char(1)      NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (record_id),
  INDEX idx_equipment_id (equipment_id),
  INDEX idx_inspect_time (inspect_time)
) COMMENT '设备点检记录(历史留痕, PC端与工位终端共用)';
