-- ----------------------------
-- Phase 1 增量脚本：dept_category 字典 + 部门类别数据补录
-- 日期：2026-07-02
-- 说明：
--   1. 新建 dept_category 字典（部门类别），用于区分部门类型，支撑部门选择器按类别过滤
--   2. 给 35 条现有 sys_dept 数据补录 dept_category
--   3. 配合 P2-8：sys_dept 129/130/131 车间节点标记 PRODUCTION（重分类不删除）
--   后端 SysDept 实体/Service/前端 dept 页面对 dept_category 的 CRUD 已就绪，无需改后端
-- ----------------------------

-- ============ 1. 新建 dept_category 字典类型 ============
INSERT INTO `sys_dict_type` VALUES (9100, '000000', '部门类别', 'dept_category', 103, 1, '2026-07-02 10:00:00', NULL, NULL, '部门类别，用于区分生产/销售/采购/仓储/财务/管理/人力等部门类型');

-- ============ 2. 新建 dept_category 字典数据 ============
INSERT INTO `sys_dict_data` VALUES (9101, '000000', 1, '生产', 'PRODUCTION', 'dept_category', NULL, 'primary', 'N', 103, 1, '2026-07-02 10:00:00', NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (9102, '000000', 2, '销售', 'SALES', 'dept_category', NULL, 'success', 'N', 103, 1, '2026-07-02 10:00:00', NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (9103, '000000', 3, '采购', 'PURCHASE', 'dept_category', NULL, 'warning', 'N', 103, 1, '2026-07-02 10:00:00', NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (9104, '000000', 4, '仓储', 'WAREHOUSE', 'dept_category', NULL, 'info', 'N', 103, 1, '2026-07-02 10:00:00', NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (9105, '000000', 5, '财务', 'FINANCE', 'dept_category', NULL, 'danger', 'N', 103, 1, '2026-07-02 10:00:00', NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (9106, '000000', 6, '管理', 'MANAGEMENT', 'dept_category', NULL, 'default', 'N', 103, 1, '2026-07-02 10:00:00', NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (9107, '000000', 7, '人力', 'HR', 'dept_category', NULL, 'default', 'N', 103, 1, '2026-07-02 10:00:00', NULL, NULL, NULL);

-- ============ 3. 补录 sys_dept.dept_category（35 条）============
-- 管理类（集团/公司/总经办/研发/测试/运维/IT）
UPDATE `sys_dept` SET `dept_category` = 'MANAGEMENT' WHERE `dept_id` IN (100, 101, 102, 103, 105, 107, 112, 113, 122, 123, 124, 125, 2054472456746233857, 2062813928180600833);
-- 销售类（市场/销售部门及其子组）
UPDATE `sys_dept` SET `dept_category` = 'SALES' WHERE `dept_id` IN (104, 108, 115, 117, 118, 119, 120, 121);
-- 财务类
UPDATE `sys_dept` SET `dept_category` = 'FINANCE' WHERE `dept_id` IN (106, 109);
-- 采购类（110 已删除，仅 114）
UPDATE `sys_dept` SET `dept_category` = 'PURCHASE' WHERE `dept_id` IN (110, 114);
-- 人力类
UPDATE `sys_dept` SET `dept_category` = 'HR' WHERE `dept_id` = 116;
-- 生产类（生产中心及车间、生产计划部、工艺/产品设计组）
UPDATE `sys_dept` SET `dept_category` = 'PRODUCTION' WHERE `dept_id` IN (111, 126, 127, 128, 129, 130, 131);
-- 仓储类
UPDATE `sys_dept` SET `dept_category` = 'WAREHOUSE' WHERE `dept_id` = 132;
