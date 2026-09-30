/** P1 工业决策合同版本：仅针对确认基线且已备份的 ry-vue 存量库执行一次。 */
/** 手工先停新版采购写入，核对实例/备份及预检结果；不可重放，也不能导入含 DROP 的主初始化脚本。 */
/** MySQL 5.7 DDL 隐式提交；任一步失败须保留现场，按实际对象状态制定恢复方案，不可直接重跑。 */
/** 历史 run/action 的新列必须保持 NULL（旧合同），不得以默认值 2、载荷 JSON 或客户端参数升级旧记录。 */
/** 只读预检（单独执行并由 DBA 审核结果，不属于本迁移的可执行部分）： */
/** 只读核验示例（仅供人工逐条审阅，不属于执行批次）：SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'ry-vue' AND TABLE_NAME IN ('ai_decision_run','ai_decision_action'); */
/** 只读核验示例（仅供人工逐条审阅，不属于执行批次）：SELECT TABLE_NAME,COLUMN_NAME,IS_NULLABLE,COLUMN_DEFAULT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'ry-vue' AND TABLE_NAME IN ('ai_decision_run','ai_decision_action') AND COLUMN_NAME IN ('decision_contract_version','purchase_payload_version'); */
/** 只读核验示例（仅供人工逐条审阅，不属于执行批次）：SELECT TABLE_NAME,INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = 'ry-vue' AND TABLE_NAME IN ('ai_decision_run','ai_decision_action'); */
/** 只读核验示例（仅供人工逐条审阅，不属于执行批次）：SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'ry-vue' AND ((TABLE_NAME = 'ai_decision_run' AND COLUMN_NAME IN ('status','tenant_id','input_snapshot_json')) OR (TABLE_NAME = 'ai_decision_action' AND COLUMN_NAME IN ('payload_json','tenant_id','run_id'))); */
/** 只读核验示例（仅供人工逐条审阅，不属于执行批次）：SELECT COUNT(*) FROM `ry-vue`.`ai_decision_run`; SELECT COUNT(*) FROM `ry-vue`.`ai_decision_action`; */
/** 预期两张原表存在、新增列均不存在；历史行数和状态另行只读记录，执行后新列应仍为 NULL。 */

USE `ry-vue`;

ALTER TABLE `ai_decision_run`
  ADD COLUMN `decision_contract_version` tinyint(4) NULL DEFAULT NULL COMMENT '决策合同版本；NULL为旧合同，只有可信新版路径显式写2' AFTER `status`;

ALTER TABLE `ai_decision_action`
  ADD COLUMN `purchase_payload_version` tinyint(4) NULL DEFAULT NULL COMMENT '采购动作载荷合同版本；NULL为旧合同，必须与run和冻结载荷校验' AFTER `payload_json`;