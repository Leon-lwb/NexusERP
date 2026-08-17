-- ============================================================
-- MES 刷写能力全链路正式测试数据 (ry-mes 业务库)
-- 场景: 智能网关 GW-1000 产线 — 刷写主控固件 + 刷写MAC(多类型工序)
-- 幂等: INSERT IGNORE / ON DUPLICATE KEY UPDATE; 固定雪花ID可重复执行
-- 前置: P0/P2/P3 SQL 已执行(mes_firmware/mes_content_file/mes_resource_pool/mes_flash_type/pro_work_order_step 就绪)
-- 依赖: pro_sn SN202606200001(sn_id=1, mes_wo_id=1, material_id=1001) + pro_work_order_step step_id=1(工序10 CUT)
-- 日期: 2026-08-17
-- ============================================================

-- ==================== 1. 固件档案(GW-1000主控固件 v1.0.0, material_id=1001 与SN物料一致, RELEASED, 物料默认) ====================
INSERT IGNORE INTO `mes_firmware`(`firmware_id`, `tenant_id`, `firmware_code`, `firmware_name`, `material_id`, `material_code`, `material_name`, `version`, `build_no`, `oss_id`, `file_md5`, `file_size`, `release_note`, `status`, `is_default`, `published_by`, `publish_time`, `create_dept`, `create_by`, `create_time`, `del_flag`, `remark`) VALUES
(2091000000000006101, '000000', 'FW20260817001', 'GW-1000主控固件', 1001, 'IF20251018001', '电机Y132M-4 7.5KW', '1.0.0', 'CI-20260817', 2088871530275577857, '21c307a98a4cf86276b003b47340638d', 6752, 'GW-1000 首版主控固件(产线正式版)', 'RELEASED', '1', 1, NOW(), 103, 1, NOW(), '0', '正式测试数据');

-- ==================== 2. 配置文件档案(GW-1000出厂配置 v1.0, material_id=1001, RELEASED) ====================
INSERT IGNORE INTO `mes_content_file`(`content_file_id`, `tenant_id`, `content_code`, `content_name`, `content_type`, `material_id`, `material_code`, `material_name`, `version`, `oss_id`, `file_md5`, `file_size`, `release_note`, `status`, `is_default`, `published_by`, `publish_time`, `create_dept`, `create_by`, `create_time`, `del_flag`, `remark`) VALUES
(2091000000000006102, '000000', 'CF20260817001', 'GW-1000出厂配置', 'CONFIG_FILE', 1001, 'IF20251018001', '电机Y132M-4 7.5KW', '1.0', 2088871530275577857, '21c307a98a4cf86276b003b47340638d', 6752, '出厂默认配置(波特率/服务器地址等)', 'RELEASED', '1', 1, NOW(), 103, 1, NOW(), '0', '正式测试数据');

-- ==================== 3. MAC 资源池 + 10 个 MAC(1个已分配给SN1, 9个可用) ====================
INSERT IGNORE INTO `mes_resource_pool`(`pool_id`, `tenant_id`, `pool_code`, `pool_name`, `resource_type`, `source`, `alloc_strategy`, `total_count`, `used_count`, `status`, `create_dept`, `create_by`, `create_time`, `del_flag`, `remark`) VALUES
(2091000000000006103, '000000', 'PL20260817001', 'GW-1000产线MAC池', 'MAC', 'INTERNAL', 'FIFO', 10, 1, 'ENABLED', 103, 1, NOW(), '0', '正式测试数据');

INSERT IGNORE INTO `mes_resource_item`(`item_id`, `tenant_id`, `pool_id`, `resource_value`, `status`, `bound_sn_id`, `bound_time`, `create_dept`, `create_by`, `create_time`, `del_flag`) VALUES
(2091000000000006110, '000000', 2091000000000006103, '00:1A:2B:3C:4D:01', 'AVAILABLE', NULL, NULL, 103, 1, NOW(), '0'),
(2091000000000006111, '000000', 2091000000000006103, '00:1A:2B:3C:4D:02', 'AVAILABLE', NULL, NULL, 103, 1, NOW(), '0'),
(2091000000000006112, '000000', 2091000000000006103, '00:1A:2B:3C:4D:03', 'AVAILABLE', NULL, NULL, 103, 1, NOW(), '0'),
(2091000000000006113, '000000', 2091000000000006103, '00:1A:2B:3C:4D:04', 'AVAILABLE', NULL, NULL, 103, 1, NOW(), '0'),
(2091000000000006114, '000000', 2091000000000006103, '00:1A:2B:3C:4D:05', 'AVAILABLE', NULL, NULL, 103, 1, NOW(), '0'),
(2091000000000006115, '000000', 2091000000000006103, '00:1A:2B:3C:4D:06', 'AVAILABLE', NULL, NULL, 103, 1, NOW(), '0'),
(2091000000000006116, '000000', 2091000000000006103, '00:1A:2B:3C:4D:07', 'AVAILABLE', NULL, NULL, 103, 1, NOW(), '0'),
(2091000000000006117, '000000', 2091000000000006103, '00:1A:2B:3C:4D:08', 'AVAILABLE', NULL, NULL, 103, 1, NOW(), '0'),
(2091000000000006118, '000000', 2091000000000006103, '00:1A:2B:3C:4D:09', 'AVAILABLE', NULL, NULL, 103, 1, NOW(), '0'),
(2091000000000006119, '000000', 2091000000000006103, '00:1A:2B:3C:4D:0A', 'ALLOCATED', 1, NOW(), 103, 1, NOW(), '0');

-- ==================== 4. SN 资源池 + 10 个 SN资源(供"刷写SN"类型用, 全可用) ====================
INSERT IGNORE INTO `mes_resource_pool`(`pool_id`, `tenant_id`, `pool_code`, `pool_name`, `resource_type`, `source`, `alloc_strategy`, `total_count`, `used_count`, `status`, `create_dept`, `create_by`, `create_time`, `del_flag`, `remark`) VALUES
(2091000000000006120, '000000', 'PL20260817002', 'GW-1000产线SN池', 'SN', 'INTERNAL', 'FIFO', 10, 0, 'ENABLED', 103, 1, NOW(), '0', '正式测试数据');

INSERT IGNORE INTO `mes_resource_item`(`item_id`, `tenant_id`, `pool_id`, `resource_value`, `status`, `bound_sn_id`, `bound_time`, `create_dept`, `create_by`, `create_time`, `del_flag`) VALUES
(2091000000000006130, '000000', 2091000000000006120, 'SN-GW1000-0001', 'AVAILABLE', NULL, NULL, 103, 1, NOW(), '0'),
(2091000000000006131, '000000', 2091000000000006120, 'SN-GW1000-0002', 'AVAILABLE', NULL, NULL, 103, 1, NOW(), '0'),
(2091000000000006132, '000000', 2091000000000006120, 'SN-GW1000-0003', 'AVAILABLE', NULL, NULL, 103, 1, NOW(), '0'),
(2091000000000006133, '000000', 2091000000000006120, 'SN-GW1000-0004', 'AVAILABLE', NULL, NULL, 103, 1, NOW(), '0'),
(2091000000000006134, '000000', 2091000000000006120, 'SN-GW1000-0005', 'AVAILABLE', NULL, NULL, 103, 1, NOW(), '0'),
(2091000000000006135, '000000', 2091000000000006120, 'SN-GW1000-0006', 'AVAILABLE', NULL, NULL, 103, 1, NOW(), '0'),
(2091000000000006136, '000000', 2091000000000006120, 'SN-GW1000-0007', 'AVAILABLE', NULL, NULL, 103, 1, NOW(), '0'),
(2091000000000006137, '000000', 2091000000000006120, 'SN-GW1000-0008', 'AVAILABLE', NULL, NULL, 103, 1, NOW(), '0'),
(2091000000000006138, '000000', 2091000000000006120, 'SN-GW1000-0009', 'AVAILABLE', NULL, NULL, 103, 1, NOW(), '0'),
(2091000000000006139, '000000', 2091000000000006120, 'SN-GW1000-0010', 'AVAILABLE', NULL, NULL, 103, 1, NOW(), '0');

-- ==================== 5. 工单工序绑定(SN202606200001 工单WO-TEST-001 工序10 CUT 标为刷写工序, 绑固件+MAC多类型) ====================
UPDATE `pro_work_order_step` SET
  `step_type` = 'FLASH',
  `content_ref_type` = 'FIRMWARE',
  `content_ref_id` = 2091000000000006101,
  `step_flash_config` = '[{"flashTypeCode":"firmware_file","contentRefId":2091000000000006101},{"flashTypeCode":"mac"}]'
WHERE `step_id` = 1;

-- ==================== 6. 刷写记录(SN202606200001 已刷固件PASS + 已刷MAC PASS, 演示完整链路) ====================
INSERT IGNORE INTO `mes_flash_record`(`flash_record_id`, `tenant_id`, `flash_no`, `flash_type_id`, `flash_type_code`, `sn_id`, `sn_code`, `mes_wo_id`, `wo_no`, `card_id`, `process_id`, `process_code`, `process_name`, `content_ref_type`, `content_ref_id`, `content_version`, `content_payload`, `content_checksum`, `operator_id`, `operator_name`, `flash_time`, `result`, `retry_count`, `flash_mode`, `create_dept`, `create_by`, `create_time`, `del_flag`, `remark`) VALUES
-- 固件刷写 PASS
(2091000000000006140, '000000', 'FL20260817001', 2091000000000006001, 'firmware_file', 1, 'SN202606200001', 1, 'WO-TEST-001', 1, 1, 'CUT', '切割', 'FIRMWARE', 2091000000000006101, '1.0.0', NULL, '21c307a98a4cf86276b003b47340638d', 1, 'admin', NOW(), 'PASS', 0, 'MANUAL', 103, 1, NOW(), '0', '正式测试数据-固件刷写'),
-- MAC 刷写 PASS (绑定的MAC资源项 2091000000000006119)
(2091000000000006141, '000000', 'FL20260817002', 2091000000000006003, 'mac', 1, 'SN202606200001', 1, 'WO-TEST-001', 1, 1, 'CUT', '切割', 'MAC_POOL', 2091000000000006119, NULL, '{"mac":"00:1A:2B:3C:4D:0A"}', NULL, 1, 'admin', NOW(), 'PASS', 0, 'MANUAL', 103, 1, NOW(), '0', '正式测试数据-MAC刷写');

-- ==================== 校验 ====================
-- SELECT firmware_code,version,status,material_id FROM mes_firmware WHERE firmware_id=2091000000000006101;
-- SELECT content_code,version,status FROM mes_content_file WHERE content_file_id=2091000000000006102;
-- SELECT pool_code,resource_type,total_count,used_count FROM mes_resource_pool WHERE pool_id IN (2091000000000006103,2091000000000006120);
-- SELECT resource_value,status,bound_sn_id FROM mes_resource_item WHERE pool_id=2091000000000006103 ORDER BY item_id;
-- SELECT step_id,step_type,content_ref_type,content_ref_id,step_flash_config FROM pro_work_order_step WHERE step_id=1;
-- SELECT flash_no,flash_type_code,content_ref_type,content_ref_id,content_version,content_payload,result FROM mes_flash_record WHERE flash_record_id IN (2091000000000006140,2091000000000006141);
