-- ----------------------------
-- 增量脚本：补录 sch_worker.workshop_id 与 bd_workshop.dept_id
-- 日期：2026-07-01
-- 说明：
--   sch_worker / bd_workshop 新增了 workshop_id / dept_id 字段后，存量数据均为 NULL。
--   本脚本用于一次性补录。**映射关系需根据企业实际组织结构调整后再执行。**
--
--   当前数据现状（仅供参考）：
--     sys_dept 生产部门节点：129 加工车间 / 130 装配车间 / 131 包装车间
--     bd_workshop 现有车间：  1 注塑车间#1 (CJ007) / 2 冲压车间#1 (CJ008)
--     sys_user 生产人员：user_id 105-108 → dept 129；109-111 → 130；112-113 → 131
--     sch_worker 已建档：worker_id 12-21 对应上述 user_id；30001-30006 无 user_id（手填工人）
--
--   ⚠️ 注意：bd_workshop 只有 2 条车间数据，与 sys_dept 的 3 个车间部门无法一一对应。
--      执行前请先确认：每个 bd_workshop 应归属哪个 sys_dept 部门，以及每个工人归属哪个车间。
-- ----------------------------

-- ============ 1. 补录 bd_workshop.dept_id（车间 → 行政部门）============
-- 请根据实际组织结构调整下面的映射。以下为示例映射：
UPDATE `bd_workshop` SET `dept_id` = 129, `dept_name` = '加工车间' WHERE `workshop_id` = 1;
UPDATE `bd_workshop` SET `dept_id` = 130, `dept_name` = '装配车间' WHERE `workshop_id` = 2;
-- 如有更多车间，按上述格式继续补充

-- ============ 2. 补录 sch_worker.workshop_id（工人 → 车间）============
-- 方式 A：按 sys_user.dept_id 与 bd_workshop.dept_id 的归属关系自动回填
--         （依赖上面第 1 步已正确建立 bd_workshop.dept_id）
--         适用于"工人的部门 = 其所在车间的归属部门"的场景
UPDATE `sch_worker` w
  JOIN `sys_user` u ON w.`user_id` = u.`user_id`
  JOIN `bd_workshop` ws ON u.`dept_id` = ws.`dept_id`
SET w.`workshop_id` = ws.`workshop_id`,
    w.`workshop_name` = ws.`workshop_name`
WHERE w.`workshop_id` IS NULL
  AND w.`del_flag` = '0';

-- 方式 B（可选）：对无 user_id 的手填工人（如 30001-30006），需人工指定车间
-- 下方为示例，请按实际工种/所属车间调整后取消注释执行：
-- UPDATE `sch_worker` SET `workshop_id` = 1, `workshop_name` = '注塑车间#1' WHERE `worker_id` IN (30001, 30002, 30003);
-- UPDATE `sch_worker` SET `workshop_id` = 2, `workshop_name` = '冲压车间#1' WHERE `worker_id` IN (30004, 30005, 30006);
