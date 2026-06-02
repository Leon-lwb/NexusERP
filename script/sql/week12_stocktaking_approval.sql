-- ----------------------------
-- R-055 盘点差异审批流
-- ----------------------------

SET NAMES utf8mb4;

-- 1. 盘点计划表增加审批相关字段
ALTER TABLE wm_stock_taking_plan ADD COLUMN act_process_id varchar(64) DEFAULT NULL COMMENT '流程实例ID';
ALTER TABLE wm_stock_taking_plan ADD COLUMN select_flow_id bigint(20) DEFAULT NULL COMMENT '审批流程ID';

-- 2. 盘点状态字典数据补充（审批中、已完成、已驳回）
-- 使用安全大ID段 500xxx 避免主键冲突
INSERT INTO `sys_dict_data` VALUES (5000301, '000000', 4, '审批中', 'APPROVING', 'wm_stocktaking_status', '', 'warning', 'N', 103, 1, NOW(), NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (5000302, '000000', 5, '已完成', 'COMPLETED', 'wm_stocktaking_status', '', 'success', 'N', 103, 1, NOW(), NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (5000303, '000000', 6, '已驳回', 'REJECTED', 'wm_stocktaking_status', '', 'danger', 'N', 103, 1, NOW(), NULL, NULL, NULL);
