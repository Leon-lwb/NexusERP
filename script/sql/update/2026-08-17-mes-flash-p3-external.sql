-- ============================================================
-- MES 刷写能力抽象 P3: external_rest 刷写类型种子(Provider 已实现)
-- 库: ry-vue 系统库(mes_flash_type 表)
-- 日期: 2026-08-17
-- 前置: P0(2026-08-17-mes-flash-abstraction.sql) + P2(2026-08-17-mes-flash-p2-resource.sql) 已执行
-- ============================================================

-- external_rest 刷写类型(ENABLED; source_config 配 url+token, 见 ExternalRestFlashProvider)
INSERT IGNORE INTO `mes_flash_type`(`flash_type_id`, `tenant_id`, `type_code`, `type_name`, `source_type`, `source_provider`, `verify_strategy`, `gate_enabled`, `status`, `sort_order`, `create_dept`, `create_by`, `create_time`, `remark`) VALUES
(2091000000000006005, '000000', 'external_rest', '外部REST刷写', 'EXTERNAL', 'externalRestFlashProvider', 'CUSTOM', '0', 'ENABLED', 5, 103, 1, NOW(), '外部系统REST获取内容, SSRF防护禁内网; source_config配 {"url":"http://...","token":"..."}');

-- 校验
-- SELECT type_code, type_name, source_type, source_provider, status FROM mes_flash_type ORDER BY sort_order;
