-- =============================================
-- NexusERP - MES模块 Week 20 Flowable审批流配置SQL
-- 数据库: ry-vue
-- 说明: 注册MES工单审批流到 wf_custom_form
-- 注意: deploy_id 和 flow_name 在BPMN部署后由系统自动填充
-- 创建时间: 2026-06-19
-- =============================================

USE `ry-vue`;

-- -----------------------------------------------------------
-- 注册MES工单审批流到 wf_custom_form
-- business_service 必须与 @Service("proWorkOrderService") bean名称一致
-- route_name 指向前端详情页路径(views/相对路径)
-- -----------------------------------------------------------
INSERT INTO wf_custom_form (id, business_name, business_service, flow_name, deploy_id,
    route_name, component, create_by, create_time, tenant_id)
VALUES (
    4050,
    'MES工单审批',
    'proWorkOrderService',
    '',
    '',
    'mes/pro/workorder/detail',
    '',
    1, NOW(), '000000'
) ON DUPLICATE KEY UPDATE
    business_name = VALUES(business_name),
    business_service = VALUES(business_service),
    route_name = VALUES(route_name);

-- -----------------------------------------------------------
-- BPMN流程配置指引（需在运行系统中通过Flowable Modeler UI操作）
-- -----------------------------------------------------------
-- 1. 访问流程模型管理页面: /workflow/model
-- 2. 新建流程模型:
--    - 名称: MES工单审批流程
--    - 分类: 自定义业务(ZDYYW)
--    - Key: mesWorkOrderApproval
-- 3. 设计审批节点:
--    - StartEvent: formKey 设为 "key_4050" (对应上方插入的wf_custom_form.id)
--    - UserTask1: 车间主任审批 (候选人: 角色车间主任)
--    - UserTask2: 生产主管审批 (候选人: 角色生产主管)
--    - EndEvent
-- 4. 点击"部署"按钮:
--    - 系统自动生成 BPMN XML 并部署到 Flowable
--    - deploy_id 自动填充到 wf_custom_form 表
--    - flow_name 自动填充
-- 5. 验证:
--    GET /workflow/process/getProcesss?serviceName=proWorkOrderService
--    应返回新流程记录
-- 6. MES工单详情页审批流下拉框将自动显示该流程
