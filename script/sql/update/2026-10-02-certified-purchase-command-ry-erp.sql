-- 2026-10-02：可信采购永久请求与跨运行恢复；本脚本不写业务事实、不升级旧申请。
-- 仅适用于已审查P1账本基线的ERP库。执行前备份、核对目标及源码；禁止自动执行。
CREATE TABLE IF NOT EXISTS `pu_certified_purchase_command` (
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `request_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '原始请求永久占用，不能删键重建',
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '身份及排序来源请求摘要',
  `actor_id` bigint(20) NOT NULL,
  `entry_type` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `material_id` bigint(20) NOT NULL,
  `vendor_id` bigint(20) NOT NULL,
  `window_end` date NOT NULL,
  `requisition_id` bigint(20) NOT NULL,
  `requisition_no` varchar(64) NOT NULL,
  `source_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `result_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '逐来源已提交申请行不可变摘要',
  `catalog_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '批准及转单前核对完整冻结目录，不含读取时钟',
  `command_status` varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'COMMITTED',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`tenant_id`,`request_key`),
  UNIQUE KEY `uk_certified_purchase_requisition` (`tenant_id`,`requisition_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='可信采购永久命令与ERP提交恢复凭据';
