-- 删除旧表并重建wf_my_business表
-- 与WfMyBusiness.java实体类完全匹配

DROP TABLE IF EXISTS `wf_my_business`;

CREATE TABLE `wf_my_business` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `process_definition_key` varchar(64) DEFAULT NULL COMMENT '流程定义key 一个key会有多个版本的id',
  `process_definition_id` varchar(64) DEFAULT NULL COMMENT '流程定义id 一个流程定义唯一',
  `process_instance_id` varchar(64) DEFAULT NULL COMMENT '流程业务实例id 一个流程业务唯一',
  `title` varchar(255) DEFAULT NULL COMMENT '流程业务简要描述',
  `data_id` varchar(64) DEFAULT NULL COMMENT '业务表id 理论唯一',
  `service_impl_name` varchar(128) DEFAULT NULL COMMENT '业务类名 用来获取spring容器里的服务对象',
  `proposer` varchar(64) DEFAULT NULL COMMENT '申请人',
  `act_status` varchar(32) DEFAULT NULL COMMENT '流程状态说明 启动 撤回 驳回 审批中 审批通过 审批异常',
  `task_id` varchar(64) DEFAULT NULL COMMENT '当前的节点定义上的Id',
  `task_name` varchar(255) DEFAULT NULL COMMENT '当前的节点',
  `task_name_id` varchar(64) DEFAULT NULL COMMENT '当前的节点实例上的Id',
  `todo_users` varchar(1000) DEFAULT NULL COMMENT '当前的节点可以处理的用户名',
  `done_users` varchar(1000) DEFAULT NULL COMMENT '处理过的人',
  `priority` varchar(10) DEFAULT NULL COMMENT '当前任务节点的优先级',
  `route_name` varchar(255) DEFAULT NULL COMMENT '前端页面显示的路由地址',
  `deploy_id` varchar(64) DEFAULT NULL COMMENT '流程实例主键',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_data_id` (`data_id`),
  KEY `idx_process_instance_id` (`process_instance_id`),
  KEY `idx_service_impl_name` (`service_impl_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程业务扩展表';
