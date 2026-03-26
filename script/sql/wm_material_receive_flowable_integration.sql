-- 物料入库单审批集成Flowable数据库脚本
-- 版本：V1.0
-- 描述：新增流程相关字段和索引

-- 1. 新增流程选择字段到物料入库单表
ALTER TABLE wm_material_receive 
ADD COLUMN select_flow_id bigint(20) NULL COMMENT '已选审批流程模板（wf_custom_form.id）' 
AFTER status;

-- 2. 新增复合索引优化查询性能
CREATE INDEX idx_receive_status ON wm_material_receive(status);
CREATE INDEX idx_receive_vendor ON wm_material_receive(vendor_id);

-- 3. 检查并添加wf_my_business表缺失的字段
-- 注意：请根据实际数据库情况选择性执行以下ALTER语句

-- 添加process_definition_key字段（如果不存在）
-- ALTER TABLE wf_my_business ADD COLUMN process_definition_key varchar(64) COMMENT '流程定义key' AFTER id;

-- 添加proposer字段（如果不存在）
-- ALTER TABLE wf_my_business ADD COLUMN proposer varchar(64) COMMENT '申请人' AFTER service_impl_name;

-- 添加act_status字段（如果不存在）
-- ALTER TABLE wf_my_business ADD COLUMN act_status varchar(32) COMMENT '流程状态' AFTER proposer;

-- 添加task_name_id字段（如果不存在）
-- ALTER TABLE wf_my_business ADD COLUMN task_name_id varchar(64) COMMENT '当前节点实例ID' AFTER task_name;

-- 添加priority字段（如果不存在）
-- ALTER TABLE wf_my_business ADD COLUMN priority varchar(10) COMMENT '优先级' AFTER done_users;

-- 添加deploy_id字段（如果不存在）
-- ALTER TABLE wf_my_business ADD COLUMN deploy_id varchar(64) COMMENT '部署ID' AFTER route_name;

-- 4. 新增流程关联配置到wf_custom_form表
INSERT INTO wf_custom_form (id, business_name, business_service, flow_name, deploy_id, route_name, component, create_time, update_time) VALUES
(1001, '物料入库审批', 'wmMaterialReceiveService', '物料入库审批流程', NULL, '/core/wm/receive/form', 'WmMaterialReceiveApprove', NOW(), NULL);

-- 5. 回滚脚本（需要时执行）
-- ALTER TABLE wm_material_receive DROP COLUMN select_flow_id;
-- DROP INDEX idx_receive_status ON wm_material_receive;
-- DROP INDEX idx_receive_vendor ON wm_material_receive;
-- DELETE FROM wf_custom_form WHERE business_service = 'wmMaterialReceiveService';
