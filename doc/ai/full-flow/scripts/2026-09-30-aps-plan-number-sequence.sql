/** APS 编号单次升级：仅用于预检和备份通过的本机 ry-erp 存量库；不可重放。 */
/** 必须先确认本表不存在、plan_no 仅有 APS+yyyyMMdd+4 位流水、无重复 (tenant_id,plan_no) */
/** 且 tenant_id 非空；MySQL 5.7 DDL 隐式提交，任何中途错误均须停下并保留现场。 */
/** 从含软删除方案的全部历史编号按排程起始日种子，禁止因软删除复用流水。 */
/** 只添加键/序列表，不更改既有排程、任务、工时和审批事实。 */

CREATE TABLE `ry-erp`.`prd_schedule_no_sequence` (
  `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '租户ID',
  `plan_date` date NOT NULL COMMENT '排程起始日（编号日期段）',
  `last_seq` int(11) NOT NULL DEFAULT 0 COMMENT '该租户该日起始日已保留的最大流水号',
  PRIMARY KEY (`tenant_id`, `plan_date`) USING BTREE
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='APS方案编号保留序列（含软删除）' ROW_FORMAT=DYNAMIC;

INSERT INTO `ry-erp`.`prd_schedule_no_sequence` (`tenant_id`, `plan_date`, `last_seq`)
SELECT tenant_id, STR_TO_DATE(SUBSTRING(plan_no, 4, 8), '%Y%m%d'),
       MAX(CAST(SUBSTRING(plan_no, 12, 4) AS UNSIGNED))
FROM `ry-erp`.`prd_schedule_plan`
WHERE plan_no REGEXP '^APS[0-9]{12}$'
GROUP BY tenant_id, STR_TO_DATE(SUBSTRING(plan_no, 4, 8), '%Y%m%d');

ALTER TABLE `ry-erp`.`prd_schedule_plan`
  MODIFY COLUMN `tenant_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '000000' COMMENT '租户ID',
  ADD UNIQUE INDEX `uk_psp_tenant_plan_no` (`tenant_id`, `plan_no`) USING BTREE;
