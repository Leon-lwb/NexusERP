-- ====================================================================
-- APS 智能排产 (Advanced Planning & Scheduling) v1.0
-- 依赖: prd_work_order / bd_routing / bd_routing_step / prd_capacity_calendar / sch_holiday (均已存在)
-- 库: ry-erp
-- 菜单: ry-vue 库 (生产执行目录 2968 下, 菜单ID 2974-2980 已确认空闲)
-- ====================================================================

SET NAMES utf8mb4;

-- ----------------------------
-- 1. APS 排程方案表
-- ----------------------------
DROP TABLE IF EXISTS `prd_schedule_plan`;
CREATE TABLE `prd_schedule_plan` (
  `plan_id`           bigint(20)    NOT NULL                COMMENT '排程方案ID',
  `plan_no`           varchar(64)   NOT NULL                COMMENT '方案编号(APS+yyyyMMdd+4位流水)',
  `plan_name`         varchar(128)  NULL DEFAULT NULL       COMMENT '方案名称',
  `workshop_id`       bigint(20)    NULL DEFAULT NULL       COMMENT '车间ID(空=全部车间)',
  `workshop_name`     varchar(64)   NULL DEFAULT NULL       COMMENT '车间名称',
  `start_date`        date          NOT NULL                COMMENT '排产起始日',
  `horizon_days`      int(11)       NOT NULL DEFAULT 14     COMMENT '排产跨度(天)',
  `strategy`          varchar(16)   NOT NULL DEFAULT 'EDD'  COMMENT '排序策略: EDD-最早交期, CR-临界比, PRIORITY-优先级',
  `wo_count`          int(11)       NULL DEFAULT 0          COMMENT '参与排产工单数',
  `task_count`        int(11)       NULL DEFAULT 0          COMMENT '排产工序任务数',
  `skipped_count`     int(11)       NULL DEFAULT 0          COMMENT '跳过工单数(无工艺路线等)',
  `total_hours`       decimal(12,2) NULL DEFAULT 0          COMMENT '总负荷工时(小时)',
  `delayed_count`     int(11)       NULL DEFAULT 0          COMMENT '拖期工单数',
  `makespan_date`     date          NULL DEFAULT NULL       COMMENT '总完工日期',
  `utilization_json`  text          NULL                    COMMENT '资源利用率快照JSON[{resourceName,bookedHours,availableHours,rate}]',
  `status`            varchar(16)   NOT NULL DEFAULT 'COMPLETED' COMMENT '状态: COMPLETED-已生成, PUBLISHED-已发布',
  `tenant_id`         varchar(20)   NULL DEFAULT '000000'   COMMENT '租户ID',
  `create_dept`       bigint(20)    NULL DEFAULT NULL       COMMENT '创建部门',
  `create_by`         bigint(20)    NULL DEFAULT NULL       COMMENT '创建人',
  `create_time`       datetime      NULL DEFAULT NULL       COMMENT '创建时间',
  `update_by`         bigint(20)    NULL DEFAULT NULL       COMMENT '更新人',
  `update_time`       datetime      NULL DEFAULT NULL       COMMENT '更新时间',
  `remark`            varchar(500)  NULL DEFAULT NULL       COMMENT '备注',
  `del_flag`          char(1)       NULL DEFAULT '0'        COMMENT '删除标志',
  PRIMARY KEY (`plan_id`) USING BTREE,
  INDEX `idx_psp_tenant`(`tenant_id`) USING BTREE,
  INDEX `idx_psp_no`(`plan_no`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'APS排程方案表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- 2. APS 排程工序任务表
-- ----------------------------
DROP TABLE IF EXISTS `prd_schedule_task`;
CREATE TABLE `prd_schedule_task` (
  `task_id`           bigint(20)    NOT NULL                COMMENT '任务ID',
  `plan_id`           bigint(20)    NOT NULL                COMMENT '排程方案ID',
  `wo_id`             bigint(20)    NOT NULL                COMMENT '生产工单ID',
  `wo_no`             varchar(64)   NOT NULL                COMMENT '工单编号',
  `material_code`     varchar(64)   NULL DEFAULT NULL       COMMENT '产品编码',
  `material_name`     varchar(128)  NULL DEFAULT NULL       COMMENT '产品名称',
  `seq_num`           int(11)       NULL DEFAULT NULL       COMMENT '工序序号(工艺路线步骤序号)',
  `step_id`           bigint(20)    NULL DEFAULT NULL       COMMENT '工艺路线步骤ID',
  `process_code`      varchar(64)   NULL DEFAULT NULL       COMMENT '工序编码',
  `process_name`      varchar(128)  NULL DEFAULT NULL       COMMENT '工序名称',
  `resource_type`     varchar(16)   NULL DEFAULT NULL       COMMENT '资源类型: WORKSTATION-工作站, WORKSHOP-车间, OUTSOURCE-委外',
  `resource_id`       bigint(20)    NULL DEFAULT NULL       COMMENT '资源ID(工作站/车间)',
  `resource_name`     varchar(128)  NULL DEFAULT NULL       COMMENT '资源名称',
  `setup_hours`       decimal(10,2) NULL DEFAULT 0          COMMENT '准备工时(小时)',
  `process_hours`     decimal(10,2) NULL DEFAULT 0          COMMENT '加工工时(小时)=标准工时×剩余数量',
  `start_time`        datetime      NULL DEFAULT NULL       COMMENT '开始时间(起始日00:00)',
  `end_time`          datetime      NULL DEFAULT NULL       COMMENT '结束时间(末日23:59:59)',
  `duration_days`     int(11)       NULL DEFAULT 1          COMMENT '占用天数',
  `delay_flag`        tinyint(4)    NULL DEFAULT 0          COMMENT '拖期标记(所属工单拖期时置1)',
  `tenant_id`         varchar(20)   NULL DEFAULT '000000'   COMMENT '租户ID',
  `create_dept`       bigint(20)    NULL DEFAULT NULL       COMMENT '创建部门',
  `create_by`         bigint(20)    NULL DEFAULT NULL       COMMENT '创建人',
  `create_time`       datetime      NULL DEFAULT NULL       COMMENT '创建时间',
  `update_by`         bigint(20)    NULL DEFAULT NULL       COMMENT '更新人',
  `update_time`       datetime      NULL DEFAULT NULL       COMMENT '更新时间',
  `del_flag`          char(1)       NULL DEFAULT '0'        COMMENT '删除标志',
  PRIMARY KEY (`task_id`) USING BTREE,
  INDEX `idx_pst_plan`(`plan_id`) USING BTREE,
  INDEX `idx_pst_wo`(`wo_id`) USING BTREE,
  INDEX `idx_pst_tenant`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'APS排程工序任务表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- 3. ry-vue 系统库: 菜单/按钮权限
--    生产执行(2968) -> 智能排产(2974) -> 按钮(2975-2980)
-- ----------------------------
INSERT IGNORE INTO `sys_menu`(`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(2974, '智能排产', 2968, 6, 'schedule', 'erp/prd/schedule/index', NULL, 1, 0, 'C', '0', '0', 'prd:schedule:list', 'tree-table', 103, 1, NOW(), NULL, NULL, 'APS智能排产(工单排程/甘特图/拖期分析)'),
(2975, '排产查询', 2974, 1, '', '', '', 1, 0, 'F', '0', '0', 'prd:schedule:query',   '#', 103, 1, NOW(), NULL, NULL, ''),
(2976, '执行排产', 2974, 2, '', '', '', 1, 0, 'F', '0', '0', 'prd:schedule:run',     '#', 103, 1, NOW(), NULL, NULL, '按策略运行APS排程引擎'),
(2977, '发布排程', 2974, 3, '', '', '', 1, 0, 'F', '0', '0', 'prd:schedule:publish', '#', 103, 1, NOW(), NULL, NULL, '回写工单计划开始/结束日期'),
(2978, '排产删除', 2974, 4, '', '', '', 1, 0, 'F', '0', '0', 'prd:schedule:remove',  '#', 103, 1, NOW(), NULL, NULL, '');

-- ==================== 校验 ====================
-- SELECT plan_id, plan_no, status, wo_count, delayed_count FROM prd_schedule_plan ORDER BY create_time DESC LIMIT 5;
-- SELECT menu_id, menu_name, parent_id, perms FROM sys_menu WHERE perms LIKE 'prd:schedule:%' ORDER BY menu_id;
