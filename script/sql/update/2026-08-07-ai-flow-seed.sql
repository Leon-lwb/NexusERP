-- =============================================
-- NexusERP - AI 流程编排: 示例流程种子数据
-- 目标库: ry-vue
-- 创建时间: 2026-08-07
-- 所属: AI 流程编排系统 P5 打磨 (feature/ai-flow-orchestration)
-- 前提: 表结构已由 2026-08-06-ai-flow.sql 创建
-- 幂等: INSERT IGNORE (flow_key 唯一键防重)
-- 演示流程: 库存预警通知
--   运行入参: {"stock": 50, "threshold": 100, "owner": 1}
--   逻辑: CODE 计算库存水平 -> IF 判断 level=LOW -> LOW 分支发站内通知给 owner / OK 分支输出正常
--   纯本地节点(CODE/IF/NOTIFY), 不依赖 LLM/RAG 模型配置, 种子可立即运行
--   若需发通知, owner 填实际用户ID(如 1=admin)
-- 权威 schema 并入 script/sql/ry-vue.sql
-- =============================================

USE `ry-vue`;

-- 库存预警通知 (PUBLISHED, version=1, 手动触发)
INSERT IGNORE INTO `ai_flow`
(`id`, `tenant_id`, `flow_name`, `flow_key`, `description`, `status`, `version`, `config_json`, `trigger_type`, `cron_expr`, `max_retry`, `create_dept`, `create_by`, `create_time`, `del_flag`)
VALUES
(2090000000000000001, '000000', '库存预警通知', 'flow_demo_stock_alert', '示例流程: 计算库存水平, 低于阈值时向负责人发送站内通知。运行入参: {"stock":50,"threshold":100,"owner":1}', 'PUBLISHED', 1,
'{"nodes":[{"id":"node_1","type":"code","position":[60,200],"data":{"label":"库存水平计算","config":{"expressions":{"level":"input.stock > input.threshold ? ''OK'' : ''LOW''","msg":"''当前库存: '' + input.stock + '', 阈值: '' + input.threshold"}}}},{"id":"node_2","type":"if","position":[300,200],"data":{"label":"是否低库存","config":{"logic":"AND","conditions":[{"variable":"nodes.node_1.output.level","operator":"EQ","value":"LOW"}]}}},{"id":"node_3","type":"code","position":[540,80],"data":{"label":"库存正常","config":{"expressions":{"summary":"nodes[''node_1''][''output''][''msg''] + '' -> 库存正常''"}}}},{"id":"node_4","type":"notify","position":[540,320],"data":{"label":"低库存通知","config":{"userId":"{{input.owner}}","title":"库存预警","content":"{{nodes.node_1.output.msg}}，请及时补货","category":"system"}}}],"edges":[{"id":"edge_1","source":"node_1","target":"node_2","sourceHandle":"outputs/main/0","targetHandle":"inputs/main/0"},{"id":"edge_2","source":"node_2","target":"node_3","sourceHandle":"outputs/if/false","targetHandle":"inputs/main/0"},{"id":"edge_3","source":"node_2","target":"node_4","sourceHandle":"outputs/if/true","targetHandle":"inputs/main/0"}]}',
'MANUAL', NULL, 1, 103, 1, NOW(), '0');

-- 验证
-- SELECT id, flow_name, flow_key, status, version FROM `ai_flow` WHERE flow_key = 'flow_demo_stock_alert';
