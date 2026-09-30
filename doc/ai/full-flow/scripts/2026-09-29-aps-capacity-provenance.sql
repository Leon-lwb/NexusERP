/** APS 产能按日溯源：仅对已备份并确认基线的本地 TCP 3306 / ry-erp 执行一次。 */
/** 执行前主 agent 须审查目标实例是 MySQL 5.7.20、核对 prd_schedule_plan 已存在但 */
/** 不含 capacity_calc_version，且两张新表均不存在；确认备份可恢复后才可执行。 */
/** 禁止在已有库导入 script/sql/ry-erp.sql；本脚本不是幂等脚本，拒绝直接重跑。 */
/** MySQL DDL 非事务原子；中途失败须保留现场和备份，核对状态后制定恢复方案。 */
/** 历史 COMPLETED 行不回填：新增列的默认值 0 标识旧计算口径，新生成方案由业务层显式写 2。 */

ALTER TABLE `ry-erp`.`prd_schedule_plan` ADD COLUMN `capacity_calc_version` tinyint(4) NOT NULL DEFAULT 0 COMMENT '产能计算版本: 0-旧口径, 2-按日产能预约' AFTER `version`;
CREATE TABLE `ry-erp`.`prd_schedule_task_day_load`  (
  `load_id` bigint(20) NOT NULL COMMENT '雪花ID，工序按日负荷记录',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '租户ID',
  `plan_id` bigint(20) NOT NULL COMMENT '排程方案ID',
  `task_id` bigint(20) NOT NULL COMMENT '排程工序任务ID',
  `resource_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源类型: WORKSTATION/WORKSHOP/OUTSOURCE',
  `resource_id` bigint(20) NOT NULL COMMENT '资源ID',
  `load_date` date NOT NULL COMMENT '负荷日期',
  `planned_hours` decimal(12, 2) NOT NULL COMMENT '当日计划占用工时',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`load_id`) USING BTREE,
  UNIQUE INDEX `uk_psdl_tenant_plan_task_date`(`tenant_id`, `plan_id`, `task_id`, `load_date`) USING BTREE,
  INDEX `idx_psdl_tenant_plan`(`tenant_id`, `plan_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'APS工序按日负荷溯源表' ROW_FORMAT = DYNAMIC;

CREATE TABLE `ry-erp`.`prd_schedule_capacity_booking`  (
  `booking_id` bigint(20) NOT NULL COMMENT '雪花ID，产能预约记录',
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '租户ID',
  `plan_id` bigint(20) NOT NULL COMMENT '排程方案ID',
  `task_id` bigint(20) NOT NULL COMMENT '排程工序任务ID',
  `calendar_id` bigint(20) NOT NULL COMMENT '产能日历ID',
  `load_date` date NOT NULL COMMENT '预约日期（日历日期快照）',
  `booked_hours` decimal(12, 2) NOT NULL COMMENT '预约工时',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`booking_id`) USING BTREE,
  UNIQUE INDEX `uk_pscb_tenant_plan_task_calendar`(`tenant_id`, `plan_id`, `task_id`, `calendar_id`) USING BTREE,
  INDEX `idx_pscb_tenant_plan`(`tenant_id`, `plan_id`) USING BTREE,
  INDEX `idx_pscb_tenant_calendar`(`tenant_id`, `calendar_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'APS产能日历预约溯源表' ROW_FORMAT = DYNAMIC;
