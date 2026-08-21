-- =====================================================================
-- SN 标签打印 (M1 标签打印) — 增量脚本
-- 文档: doc/station/plans/2026-08-21-station-code-review-and-next-plan.md (阶段4)
-- 目标库: ry-mes (表) + ry-vue (菜单)
-- 日期: 2026-08-21
-- 说明: CREATE TABLE IF NOT EXISTS / INSERT IGNORE, 可重复执行
--   菜单段位: 4231-4236 (避让 4230 工位终端 / 4261-4275 SOP)
-- =====================================================================

-- 1. 标签打印模板(字段映射式, 服务端渲染 TSPL/ZPL, TCP 9100 直发网络打印机)
USE `ry-mes`;

CREATE TABLE IF NOT EXISTS mes_label_template (
  template_id   bigint       NOT NULL COMMENT '模板ID',
  tenant_id     varchar(20)  NULL DEFAULT '000000' COMMENT '租户ID',
  template_code varchar(64)  NOT NULL COMMENT '模板编码',
  template_name varchar(128) NOT NULL COMMENT '模板名称',
  label_type    varchar(20)  NULL DEFAULT 'SN' COMMENT '标签类型: SN 标签(流转卡标签后续扩展)',
  label_lang    varchar(10)  NOT NULL DEFAULT 'TSPL' COMMENT '打印机语言: TSPL/ZPL',
  width_mm      int          NULL DEFAULT 60 COMMENT '标签宽度(mm)',
  height_mm     int          NULL DEFAULT 40 COMMENT '标签高度(mm)',
  printer_host  varchar(64)  NULL COMMENT '网络打印机地址(TCP RAW 9100)',
  printer_port  int          NULL DEFAULT 9100 COMMENT '网络打印机端口',
  content       text         NULL COMMENT '字段映射布局 JSON: {items:[{type,field,x,y,size,height}]}',
  enabled       char(1)      NULL DEFAULT 'Y' COMMENT '是否启用(Y/N): SN 造码打印仅消费启用模板',
  remark        varchar(500) NULL COMMENT '备注',
  create_dept   bigint       NULL,
  create_by     bigint       NULL,
  create_time   datetime     NULL DEFAULT CURRENT_TIMESTAMP,
  update_by     bigint       NULL,
  update_time   datetime     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag      char(1)      NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (template_id),
  UNIQUE INDEX uk_template_code (template_code),
  INDEX idx_label_type (label_type)
) COMMENT '标签打印模板';

-- 2. 菜单(生产执行 → 标签模板)
USE `ry-vue`;

INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(4231, '标签模板', 4001, 23, 'label-template', 'mes/label/template/index', '', 1, 0, 'C', '0', '0', 'mes:label:template:list', 'edit', 103, 1, NOW(), NULL, NULL, '标签打印模板(字段映射式, 服务端渲染 TSPL/ZPL, TCP 9100 直发)'),
(4232, '标签模板查询', 4231, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:label:template:query', '#', 103, 1, NOW(), NULL, NULL, ''),
(4233, '标签模板新增', 4231, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:label:template:add', '#', 103, 1, NOW(), NULL, NULL, ''),
(4234, '标签模板修改', 4231, 3, '', '', '', 1, 0, 'F', '0', '0', 'mes:label:template:edit', '#', 103, 1, NOW(), NULL, NULL, ''),
(4235, '标签模板删除', 4231, 4, '', '', '', 1, 0, 'F', '0', '0', 'mes:label:template:remove', '#', 103, 1, NOW(), NULL, NULL, ''),
(4236, '标签测试打印', 4231, 5, '', '', '', 1, 0, 'F', '0', '0', 'mes:label:template:print', '#', 103, 1, NOW(), NULL, NULL, '');
