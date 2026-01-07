-- ============================================
-- 考勤系统数据库升级脚本
-- 添加钉钉风格打卡功能所需字段和表
-- ============================================

-- 1. 为sch_attendance表添加新字段
ALTER TABLE `sch_attendance` 
ADD COLUMN `location_lat` DECIMAL(10, 8) NULL COMMENT '打卡位置纬度' AFTER `device_name`,
ADD COLUMN `location_lng` DECIMAL(11, 8) NULL COMMENT '打卡位置经度' AFTER `location_lat`,
ADD COLUMN `location_address` VARCHAR(500) NULL COMMENT '打卡位置地址' AFTER `location_lng`,
ADD COLUMN `punch_type` VARCHAR(64) DEFAULT 'normal' COMMENT '打卡类型: normal正常打卡, outdoor外勤打卡, supplement补卡, remote远程打卡' AFTER `location_address`,
ADD COLUMN `photo_url` VARCHAR(500) NULL COMMENT '外勤打卡照片URL' AFTER `punch_type`,
ADD COLUMN `wifi_ssid` VARCHAR(255) NULL COMMENT 'Wi-Fi网络SSID（用于Wi-Fi打卡）' AFTER `photo_url`,
ADD COLUMN `wifi_bssid` VARCHAR(255) NULL COMMENT 'Wi-Fi网络BSSID' AFTER `wifi_ssid`,
ADD COLUMN `bluetooth_device` VARCHAR(255) NULL COMMENT '蓝牙设备标识' AFTER `wifi_bssid`,
ADD COLUMN `accuracy` DECIMAL(5,2) NULL COMMENT '定位精度（米）' AFTER `bluetooth_device`,
ADD COLUMN `is_valid_location` TINYINT(1) DEFAULT 1 COMMENT '是否有效定位（在打卡范围内）' AFTER `accuracy`,
ADD COLUMN `punch_status` VARCHAR(64) DEFAULT 'success' COMMENT '打卡状态: success成功, failed失败, pending待审核' AFTER `is_valid_location`;

-- 添加新字段的索引
ALTER TABLE `sch_attendance` 
ADD INDEX `idx_punch_type` (`punch_type`),
ADD INDEX `idx_punch_status` (`punch_status`),
ADD INDEX `idx_record_date_type` (`record_date`, `punch_type`);

-- 2. 创建打卡规则表
CREATE TABLE IF NOT EXISTS `sch_punch_rule` (
  `rule_id` bigint NOT NULL AUTO_INCREMENT COMMENT '规则ID',
  `rule_name` varchar(255) NOT NULL COMMENT '规则名称',
  `shift_id` bigint DEFAULT NULL COMMENT '适用班次ID（NULL表示所有班次）',
  `shift_name` varchar(255) DEFAULT NULL COMMENT '班次名称',
  `work_start_time` time NOT NULL COMMENT '上班时间',
  `work_end_time` time NOT NULL COMMENT '下班时间',
  `allow_clock_in_start` time NOT NULL COMMENT '允许上班打卡开始时间',
  `allow_clock_in_end` time NOT NULL COMMENT '允许上班打卡结束时间',
  `allow_clock_out_start` time NOT NULL COMMENT '允许下班打卡开始时间',
  `allow_clock_out_end` time NOT NULL COMMENT '允许下班打卡结束时间',
  `location_lat` DECIMAL(10, 8) NULL COMMENT '打卡中心点纬度',
  `location_lng` DECIMAL(11, 8) NULL COMMENT '打卡中心点经度',
  `location_radius` int DEFAULT 100 COMMENT '打卡半径（米）',
  `location_address` VARCHAR(500) NULL COMMENT '打卡地点描述',
  `allow_wifi_ssids` TEXT NULL COMMENT '允许的Wi-Fi网络SSID列表（JSON数组）',
  `allow_bluetooth_devices` TEXT NULL COMMENT '允许的蓝牙设备列表（JSON数组）',
  `allow_outdoor_punch` TINYINT(1) DEFAULT 0 COMMENT '是否允许外勤打卡',
  `outdoor_require_photo` TINYINT(1) DEFAULT 1 COMMENT '外勤打卡是否要求拍照',
  `allow_supplement` TINYINT(1) DEFAULT 1 COMMENT '是否允许补卡',
  `supplement_deadline` int DEFAULT 3 COMMENT '补卡申请截止天数',
  `late_threshold` int DEFAULT 5 COMMENT '迟到阈值（分钟）',
  `early_leave_threshold` int DEFAULT 5 COMMENT '早退阈值（分钟）',
  `absent_threshold` int DEFAULT 60 COMMENT '旷工阈值（分钟）',
  `is_enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`rule_id`),
  KEY `idx_shift_id` (`shift_id`),
  KEY `idx_is_enabled` (`is_enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='打卡规则表';

-- 3. 创建补卡申请记录表
CREATE TABLE IF NOT EXISTS `sch_supplement_apply` (
  `apply_id` bigint NOT NULL AUTO_INCREMENT COMMENT '申请ID',
  `worker_id` bigint NOT NULL COMMENT '人员ID',
  `worker_name` varchar(255) DEFAULT NULL COMMENT '人员姓名',
  `apply_date` date NOT NULL COMMENT '申请补卡日期',
  `apply_type` varchar(64) NOT NULL COMMENT '申请类型: clock_in上班补卡, clock_out下班补卡, both上下班补卡',
  `reason` varchar(500) NOT NULL COMMENT '补卡原因',
  `proof_photos` TEXT NULL COMMENT '证明材料照片URL列表（JSON数组）',
  `apply_status` varchar(64) DEFAULT 'pending' COMMENT '申请状态: pending待审核, approved通过, rejected驳回',
  `review_by` varchar(64) DEFAULT NULL COMMENT '审核人',
  `review_time` datetime DEFAULT NULL COMMENT '审核时间',
  `review_remark` varchar(500) DEFAULT NULL COMMENT '审核备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`apply_id`),
  KEY `idx_worker_id` (`worker_id`),
  KEY `idx_apply_date` (`apply_date`),
  KEY `idx_apply_status` (`apply_status`),
  KEY `idx_worker_date` (`worker_id`, `apply_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='补卡申请记录表';

-- 4. 创建外勤打卡记录表（如果需要单独记录）
CREATE TABLE IF NOT EXISTS `sch_outdoor_punch` (
  `outdoor_id` bigint NOT NULL AUTO_INCREMENT COMMENT '外勤打卡ID',
  `attendance_id` bigint NOT NULL COMMENT '关联考勤记录ID',
  `worker_id` bigint NOT NULL COMMENT '人员ID',
  `punch_time` datetime NOT NULL COMMENT '打卡时间',
  `punch_type` varchar(64) NOT NULL COMMENT '打卡类型: clock_in上班, clock_out下班',
  `location_lat` DECIMAL(10, 8) NOT NULL COMMENT '打卡纬度',
  `location_lng` DECIMAL(11, 8) NOT NULL COMMENT '打卡经度',
  `location_address` VARCHAR(500) NOT NULL COMMENT '打卡地址',
  `photo_url` VARCHAR(500) NULL COMMENT '现场照片URL',
  `customer_name` VARCHAR(255) NULL COMMENT '客户名称（如有）',
  `business_reason` VARCHAR(500) NULL COMMENT '外勤事由',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`outdoor_id`),
  KEY `idx_attendance_id` (`attendance_id`),
  KEY `idx_worker_id` (`worker_id`),
  KEY `idx_punch_time` (`punch_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='外勤打卡记录表';

-- 5. 插入默认打卡规则示例数据
INSERT INTO `sch_punch_rule` (
  `rule_name`, `shift_id`, `shift_name`, 
  `work_start_time`, `work_end_time`,
  `allow_clock_in_start`, `allow_clock_in_end`,
  `allow_clock_out_start`, `allow_clock_out_end`,
  `location_lat`, `location_lng`, `location_radius`, `location_address`,
  `allow_outdoor_punch`, `outdoor_require_photo`,
  `allow_supplement`, `supplement_deadline`,
  `late_threshold`, `early_leave_threshold`, `absent_threshold`,
  `remark`
) VALUES 
(
  '默认打卡规则', NULL, '所有班次',
  '09:00:00', '18:00:00',
  '08:00:00', '10:00:00',
  '17:00:00', '20:00:00',
  31.230416, 121.473701, 500, '上海市黄浦区',
  1, 1,
  1, 3,
  5, 5, 60,
  '系统默认打卡规则，适用于所有班次'
),
(
  '早班打卡规则', 1, '早班',
  '08:00:00', '17:00:00',
  '07:00:00', '09:00:00',
  '16:00:00', '19:00:00',
  31.230416, 121.473701, 500, '上海市黄浦区',
  1, 1,
  1, 3,
  5, 5, 60,
  '早班专用打卡规则'
);

-- 6. 更新现有数据的默认值（可选）
-- 将现有考勤记录的punch_type设置为'normal'
UPDATE `sch_attendance` SET `punch_type` = 'normal' WHERE `punch_type` IS NULL;

-- 7. 添加注释说明
COMMENT ON TABLE `sch_attendance` IS '考勤记录表（已增强支持钉钉风格打卡）';