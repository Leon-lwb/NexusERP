-- ----------------------------------------------------------------------------
-- App 考勤收尾：补卡申请 / 外勤打卡 / 请假申请
-- 日期: 2026-08-24
-- 说明: 配套 AppSchAttendanceController 真实实现（替换原 Mock 返回）。
--       依赖 sch_punch_rule 已有的 allow_supplement / supplement_deadline /
--       allow_outdoor_punch / outdoor_require_photo 规则字段。
-- 目标库: ry-erp（业务表）/ ry-vue（字典 + 菜单）
-- 幂等: 表结构 IF NOT EXISTS；字典/菜单 INSERT IGNORE（固定 ID 段，可重复执行）
-- ID 段位: 字典 6011xx（避让 6000xx-6009xx 已占用段）；菜单 6201（避让 6100-6190）
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1. 补卡申请表（ry-erp）
-- ----------------------------
USE `ry-erp`;

CREATE TABLE IF NOT EXISTS `sch_supplement_apply` (
  `apply_id`          bigint(20)    NOT NULL                COMMENT '补卡申请ID',
  `tenant_id`         varchar(20)   NULL DEFAULT '000000'   COMMENT '租户ID',
  `apply_no`          varchar(32)   NULL DEFAULT NULL       COMMENT '申请单号',
  `worker_id`         bigint(20)    NOT NULL                COMMENT '申请人(工人)ID',
  `worker_name`       varchar(64)   NULL DEFAULT NULL       COMMENT '申请人姓名',
  `apply_date`        datetime      NOT NULL                COMMENT '补卡日期',
  `apply_type`        varchar(20)   NOT NULL                COMMENT '补卡类型: clock_in上班缺卡 / clock_out下班缺卡 / full_day全天缺卡',
  `reason`            varchar(500)  NOT NULL                COMMENT '补卡原因',
  `apply_status`      varchar(20)   NOT NULL DEFAULT 'pending' COMMENT '申请状态: pending待审批 / approved已通过 / rejected已驳回 / cancelled已取消',
  `approve_user_id`   bigint(20)    NULL DEFAULT NULL       COMMENT '审批人用户ID',
  `approve_user_name` varchar(64)   NULL DEFAULT NULL       COMMENT '审批人姓名',
  `approve_time`      datetime      NULL DEFAULT NULL       COMMENT '审批时间',
  `approve_remark`    varchar(500)  NULL DEFAULT NULL       COMMENT '审批备注',
  `create_dept`       bigint(20)    NULL DEFAULT NULL       COMMENT '创建部门',
  `create_by`         bigint(20)    NULL DEFAULT NULL       COMMENT '创建者',
  `create_time`       datetime      NULL DEFAULT NULL       COMMENT '创建时间',
  `update_by`         bigint(20)    NULL DEFAULT NULL       COMMENT '更新者',
  `update_time`       datetime      NULL DEFAULT NULL       COMMENT '更新时间',
  `del_flag`          char(1)       NULL DEFAULT '0'        COMMENT '删除标志（0存在 1删除）',
  `remark`            varchar(500)  NULL DEFAULT NULL       COMMENT '备注',
  PRIMARY KEY (`apply_id`) USING BTREE,
  KEY `idx_worker_status` (`worker_id`, `apply_status`) USING BTREE,
  KEY `idx_worker_date` (`worker_id`, `apply_date`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '考勤补卡申请表';

-- ----------------------------
-- 2. 外勤打卡表（ry-erp）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sch_outdoor_punch` (
  `punch_id`      bigint(20)    NOT NULL                COMMENT '外勤打卡ID',
  `tenant_id`     varchar(20)   NULL DEFAULT '000000'   COMMENT '租户ID',
  `worker_id`     bigint(20)    NOT NULL                COMMENT '工人ID',
  `worker_name`   varchar(64)   NULL DEFAULT NULL       COMMENT '工人姓名',
  `punch_time`    datetime      NOT NULL                COMMENT '外勤打卡时间',
  `latitude`      decimal(10, 6) NULL DEFAULT NULL      COMMENT '纬度',
  `longitude`     decimal(10, 6) NULL DEFAULT NULL      COMMENT '经度',
  `address`       varchar(255)  NULL DEFAULT NULL       COMMENT '打卡定位地址',
  `customer_name` varchar(128)  NULL DEFAULT NULL       COMMENT '拜访客户名称(选填)',
  `reason`        varchar(500)  NULL DEFAULT NULL       COMMENT '外勤事由',
  `photo_oss_id`  bigint(20)    NULL DEFAULT NULL       COMMENT '现场照片ossId(MinIO存证)',
  `create_dept`   bigint(20)    NULL DEFAULT NULL       COMMENT '创建部门',
  `create_by`     bigint(20)    NULL DEFAULT NULL       COMMENT '创建者',
  `create_time`   datetime      NULL DEFAULT NULL       COMMENT '创建时间',
  `update_by`     bigint(20)    NULL DEFAULT NULL       COMMENT '更新者',
  `update_time`   datetime      NULL DEFAULT NULL       COMMENT '更新时间',
  `del_flag`      char(1)       NULL DEFAULT '0'        COMMENT '删除标志（0存在 1删除）',
  `remark`        varchar(500)  NULL DEFAULT NULL       COMMENT '备注',
  PRIMARY KEY (`punch_id`) USING BTREE,
  KEY `idx_worker_time` (`worker_id`, `punch_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '外勤打卡记录表';

-- ----------------------------
-- 3. 请假申请表（ry-erp）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sch_leave_apply` (
  `leave_id`          bigint(20)    NOT NULL                COMMENT '请假申请ID',
  `tenant_id`         varchar(20)   NULL DEFAULT '000000'   COMMENT '租户ID',
  `leave_no`          varchar(32)   NULL DEFAULT NULL       COMMENT '申请单号',
  `worker_id`         bigint(20)    NOT NULL                COMMENT '申请人(工人)ID',
  `worker_name`       varchar(64)   NULL DEFAULT NULL       COMMENT '申请人姓名',
  `leave_type`        varchar(20)   NOT NULL                COMMENT '请假类型(字典 sch_leave_type)',
  `start_date`        datetime      NOT NULL                COMMENT '请假开始日期',
  `end_date`          datetime      NOT NULL                COMMENT '请假结束日期',
  `duration_days`     decimal(5, 1) NOT NULL                COMMENT '请假时长(天)',
  `reason`            varchar(500)  NOT NULL                COMMENT '请假事由',
  `apply_status`      varchar(20)   NOT NULL DEFAULT 'pending' COMMENT '申请状态: pending待审批 / approved已通过 / rejected已驳回 / cancelled已取消',
  `approve_user_id`   bigint(20)    NULL DEFAULT NULL       COMMENT '审批人用户ID',
  `approve_user_name` varchar(64)   NULL DEFAULT NULL       COMMENT '审批人姓名',
  `approve_time`      datetime      NULL DEFAULT NULL       COMMENT '审批时间',
  `approve_remark`    varchar(500)  NULL DEFAULT NULL       COMMENT '审批备注',
  `create_dept`       bigint(20)    NULL DEFAULT NULL       COMMENT '创建部门',
  `create_by`         bigint(20)    NULL DEFAULT NULL       COMMENT '创建者',
  `create_time`       datetime      NULL DEFAULT NULL       COMMENT '创建时间',
  `update_by`         bigint(20)    NULL DEFAULT NULL       COMMENT '更新者',
  `update_time`       datetime      NULL DEFAULT NULL       COMMENT '更新时间',
  `del_flag`          char(1)       NULL DEFAULT '0'        COMMENT '删除标志（0存在 1删除）',
  `remark`            varchar(500)  NULL DEFAULT NULL       COMMENT '备注',
  PRIMARY KEY (`leave_id`) USING BTREE,
  KEY `idx_worker_status` (`worker_id`, `apply_status`) USING BTREE,
  KEY `idx_worker_date` (`worker_id`, `start_date`, `end_date`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '考勤请假申请表';

-- ----------------------------
-- 4. 请假类型字典（ry-vue，段位 6011xx）
-- ----------------------------
USE `ry-vue`;

INSERT IGNORE INTO `sys_dict_type` (`dict_id`, `tenant_id`, `dict_name`, `dict_type`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(601101, '000000', '请假类型', 'sch_leave_type', 103, 1, NOW(), NULL, NULL, 'App考勤请假申请类型');

INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(601111, '000000', 1, '事假', 'personal',   'sch_leave_type', NULL, 'default', 'Y', 103, 1, NOW(), NULL, NULL, NULL),
(601112, '000000', 2, '病假', 'sick',       'sch_leave_type', NULL, 'warning', 'N', 103, 1, NOW(), NULL, NULL, NULL),
(601113, '000000', 3, '年假', 'annual',     'sch_leave_type', NULL, 'success', 'N', 103, 1, NOW(), NULL, NULL, NULL),
(601114, '000000', 4, '调休', 'compensate', 'sch_leave_type', NULL, 'primary', 'N', 103, 1, NOW(), NULL, NULL, NULL),
(601115, '000000', 5, '婚假', 'marriage',   'sch_leave_type', NULL, 'success', 'N', 103, 1, NOW(), NULL, NULL, NULL),
(601116, '000000', 6, '产假', 'maternity',  'sch_leave_type', NULL, 'primary', 'N', 103, 1, NOW(), NULL, NULL, NULL),
(601117, '000000', 7, '其他', 'other',      'sch_leave_type', NULL, 'info',    'N', 103, 1, NOW(), NULL, NULL, NULL);

-- ----------------------------
-- 5. 审批权限按钮（ry-vue，挂在「排班考勤」目录 2971 下）
--    App 端审批接口校验权限标识 sch:appApply:approve；超管天然具备，
--    其他考勤管理员角色需在 Web 端「角色管理」勾选该按钮授权。
-- ----------------------------
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query_param`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(6201, 'App考勤申请审批', 2971, 99, '', '', NULL, 1, 0, 'F', '0', '0', 'sch:appApply:approve', '#', 103, 1, NOW(), NULL, NULL, 'App端补卡/请假审批权限(按钮)');
