-- ====================================================================
-- PdM 预测性维护 - 设备健康评分 v1.0
-- 依赖: eq_equipment(已存在, iot_device_id 关联) / eq_repair(已存在) / iot_device_data(IoT库)
-- 库: ry-mes
-- 菜单: ry-vue 系统库 (设备管理目录下, 菜单ID 2981-2985 已确认空闲)
-- ====================================================================

SET NAMES utf8mb4;

-- ----------------------------
-- 1. 设备健康评分记录表
-- ----------------------------
DROP TABLE IF EXISTS `eq_health_record`;
CREATE TABLE `eq_health_record` (
  `record_id`         bigint(20)    NOT NULL                COMMENT '记录ID',
  `equipment_id`      bigint(20)    NOT NULL                COMMENT '设备ID',
  `equipment_code`    varchar(64)   NULL DEFAULT NULL       COMMENT '设备编码',
  `equipment_name`    varchar(128)  NULL DEFAULT NULL       COMMENT '设备名称',
  `iot_device_id`     bigint(20)    NULL DEFAULT NULL       COMMENT 'IoT设备ID',
  `score`             int(11)       NOT NULL                COMMENT '健康评分(0-100)',
  `grade`             varchar(16)   NOT NULL                COMMENT '等级: HIGH(>=80)/MEDIUM(>=60)/LOW(<60)',
  `anomaly_points`    int(11)       NULL DEFAULT 0          COMMENT '24h异常数据点数(z>3)',
  `repair_count_90d`  int(11)       NULL DEFAULT 0          COMMENT '90天内维修单数',
  `open_repair_count` int(11)       NULL DEFAULT 0          COMMENT '未闭环维修单数',
  `detail_json`       text          NULL                    COMMENT '明细JSON[{propertyCode,propertyName,mean,std,anomalyPoints}]',
  `prev_score`        int(11)       NULL DEFAULT NULL       COMMENT '前次评分(对比趋势)',
  `score_change`      int(11)       NULL DEFAULT NULL       COMMENT '评分变化(本次-前次)',
  `record_date`       date          NOT NULL                COMMENT '评分日期',
  `tenant_id`         varchar(20)   NULL DEFAULT '000000'   COMMENT '租户ID',
  `create_dept`       bigint(20)    NULL DEFAULT NULL       COMMENT '创建部门',
  `create_by`         bigint(20)    NULL DEFAULT NULL       COMMENT '创建人',
  `create_time`       datetime      NULL DEFAULT NULL       COMMENT '创建时间',
  `update_by`         bigint(20)    NULL DEFAULT NULL       COMMENT '更新人',
  `update_time`       datetime      NULL DEFAULT NULL       COMMENT '更新时间',
  `del_flag`          char(1)       NULL DEFAULT '0'        COMMENT '删除标志',
  PRIMARY KEY (`record_id`) USING BTREE,
  INDEX `idx_ehr_equipment`(`equipment_id`) USING BTREE,
  INDEX `idx_ehr_date`(`record_date`) USING BTREE,
  INDEX `idx_ehr_tenant`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '设备健康评分记录表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- 2. ry-vue 系统库: 菜单/按钮权限
--    MES(4000) -> 维护保养(4201) -> 设备健康(2981, order 6) -> 按钮(2982-2983)
-- ----------------------------
INSERT IGNORE INTO `sys_menu`(`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(2981, '设备健康', 4201, 6, 'health', 'mes/eq/health/index', NULL, 1, 0, 'C', '0', '0', 'mes:eq:health:list', 'monitor', 103, 1, NOW(), NULL, NULL, '预测性维护: EWMA异常检测+设备健康评分'),
(2982, '健康查询', 2981, 1, '', '', '', 1, 0, 'F', '0', '0', 'mes:eq:health:query', '#', 103, 1, NOW(), NULL, NULL, ''),
(2983, '重新评估', 2981, 2, '', '', '', 1, 0, 'F', '0', '0', 'mes:eq:health:evaluate', '#', 103, 1, NOW(), NULL, NULL, '手动触发单台设备健康评分');

-- ==================== 校验 ====================
-- SELECT equipment_code, score, grade, score_change, record_date FROM eq_health_record ORDER BY record_date DESC, score ASC LIMIT 10;
-- SELECT menu_id, menu_name, parent_id, perms FROM sys_menu WHERE perms LIKE 'mes:eq:health:%' ORDER BY menu_id;
