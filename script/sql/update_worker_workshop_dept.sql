-- ----------------------------
-- 增量脚本：工人加车间归属、车间加部门归属
-- 日期：2026-07-01
-- 说明：
--   1. sch_worker 增加 workshop_id / workshop_name，工人直接归属车间（不再仅靠班组反推）
--   2. bd_workshop 增加 dept_id / dept_name，车间归属行政部门，打通 sys_dept 体系
--   3. 配合"选用户建档"方案：放弃定时同步，工人档案改为人工新建并选择车间
-- ----------------------------

-- 生产工人档案表：增加车间归属
ALTER TABLE `sch_worker`
  ADD COLUMN `workshop_id` bigint(20) NULL DEFAULT NULL COMMENT '所属车间ID（关联bd_workshop）' AFTER `user_id`,
  ADD COLUMN `workshop_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '所属车间名称' AFTER `workshop_id`;
ALTER TABLE `sch_worker` ADD INDEX `idx_workshop_id`(`workshop_id`) USING BTREE;

-- 车间表：增加部门归属
ALTER TABLE `bd_workshop`
  ADD COLUMN `dept_id` bigint(20) NULL DEFAULT NULL COMMENT '所属部门ID（关联sys_dept，打通行政组织体系）' AFTER `manager_name`,
  ADD COLUMN `dept_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '所属部门名称' AFTER `dept_id`;
ALTER TABLE `bd_workshop` ADD INDEX `idx_dept_id`(`dept_id`) USING BTREE;
