-- =============================================
-- NexusERP - MES模块 Week 25 质量管理菜单权限SQL
-- 目标库: ry-vue (sys_menu)
-- 说明: IQC来料检验 + IPQC过程检验 + OQC出货检验 菜单 + 按钮权限
-- 父菜单: 4002(质量管理, Week17已建)
-- 菜单ID段: 4090-4101 (Week24用到4086, 接续)
-- 创建时间: 2026-06-23
-- =============================================
USE `ry-vue`;

-- IQC来料检验 (parent=4002 质量管理)
INSERT INTO sys_menu VALUES
(4090, 'IQC来料检验', 4002, 1, 'qc/iqc', 'mes/qc/iqc/index', '', 1, 0, 'C', '0', '0', 'mes:qc:iqc:list', 'check', 103, 1, NOW(), NULL, NULL, 'MES来料检验');
INSERT INTO sys_menu VALUES
(4091, 'IQC查询', 4090, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:qc:iqc:query', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO sys_menu VALUES
(4092, 'IQC新增', 4090, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:qc:iqc:add', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO sys_menu VALUES
(4093, 'IQC检验', 4090, 3, '', '', '', 1, 0, 'F', '0', '0', 'mes:qc:iqc:inspect', '#', 103, 1, NOW(), NULL, NULL, '');

-- IPQC过程检验
INSERT INTO sys_menu VALUES
(4094, 'IPQC过程检验', 4002, 2, 'qc/ipqc', 'mes/qc/ipqc/index', '', 1, 0, 'C', '0', '0', 'mes:qc:ipqc:list', 'check', 103, 1, NOW(), NULL, NULL, 'MES过程检验');
INSERT INTO sys_menu VALUES
(4095, 'IPQC查询', 4094, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:qc:ipqc:query', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO sys_menu VALUES
(4096, 'IPQC新增', 4094, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:qc:ipqc:add', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO sys_menu VALUES
(4097, 'IPQC检验', 4094, 3, '', '', '', 1, 0, 'F', '0', '0', 'mes:qc:ipqc:inspect', '#', 103, 1, NOW(), NULL, NULL, '');

-- OQC出货检验
INSERT INTO sys_menu VALUES
(4098, 'OQC出货检验', 4002, 3, 'qc/oqc', 'mes/qc/oqc/index', '', 1, 0, 'C', '0', '0', 'mes:qc:oqc:list', 'check', 103, 1, NOW(), NULL, NULL, 'MES出货检验');
INSERT INTO sys_menu VALUES
(4099, 'OQC查询', 4098, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:qc:oqc:query', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO sys_menu VALUES
(4100, 'OQC新增', 4098, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:qc:oqc:add', '#', 103, 1, NOW(), NULL, NULL, '');
INSERT INTO sys_menu VALUES
(4101, 'OQC检验', 4098, 3, '', '', '', 1, 0, 'F', '0', '0', 'mes:qc:oqc:inspect', '#', 103, 1, NOW(), NULL, NULL, '');

-- 验证(执行后人工核对):
-- SELECT menu_id, menu_name, perms FROM sys_menu WHERE menu_id BETWEEN 4090 AND 4101 ORDER BY menu_id;
-- 预期: 3个C菜单(4090/4094/4098) + 9个F按钮权限(4091~4093, 4095~4097, 4099~4101)
-- 执行后为相关角色勾选 4090-4101 权限
