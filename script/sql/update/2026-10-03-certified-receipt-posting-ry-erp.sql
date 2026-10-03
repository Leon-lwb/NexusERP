-- 精确收货永久结果/ERP 待通知：只创建空表，不回填认证、不改旧浮点。
-- 仅经适用环境审查/备份后在 ERP 库执行；未执行不表示运行态已开放。
CREATE TABLE IF NOT EXISTS `wm_certified_receipt_posting` (
  `tenant_id` varchar(20) NOT NULL COMMENT '明确当前租户',
  `receive_id` bigint NOT NULL COMMENT '真实原入库单，永久幂等身份',
  `material_id` bigint NOT NULL,
  `actor_id` bigint NOT NULL,
  `source_fingerprint` char(64) NOT NULL COMMENT '冻结真实来源摘要，不是上传的核验布尔',
  `total_quantity` decimal(18,4) NOT NULL,
  `posted_at` datetime NOT NULL,
  `notification_status` varchar(16) NOT NULL COMMENT 'PENDING/ENQUEUED，接受不等于消费者完成',
  PRIMARY KEY (`tenant_id`,`receive_id`),
  KEY `idx_certified_receipt_material` (`tenant_id`,`material_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='原生精确收货过账结果与提交后通知恢复';
