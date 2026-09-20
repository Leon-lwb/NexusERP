-- 销售合同到期扫描任务登记（销售模块优化决策 Q9）
-- 背景（2026-09-19 加固轮）:
--   so_contract_status 字典里有 EXPIRED（已到期），但**全仓此前没有任何代码**会写入该状态 ——
--   合同一旦生效（EFFECTIVE）就永远停在那里：过期合同仍可被引用、仍计入「有效合同」。
--   本次补齐 SnailJob 执行器 soContractExpiryTask（SoContractExpiryTask，每天 01:30 扫描，
--   把 expiry_date < 今天 的 EFFECTIVE 合同置为 EXPIRED；到期日当天仍算有效），
--   本脚本负责在调度中心登记该任务。
-- 目标库: 系统库 (ry-vue)。
-- 执行方式: 幂等。sj_job 有唯一键 uk_sj_job_01(namespace_id, biz_id)、
--           sj_job_executor 用固定主键 id，均配合 INSERT IGNORE，可重复执行。
-- 注意: 应用若在运行，SnailJob 客户端拉取任务有缓存，建议在 SnailJob 控制台确认任务已出现，
--       或重启应用后生效。
-- 前置检查（应无输出，有输出说明已登记过，本脚本可跳过）:
--   SELECT id, biz_id, job_name FROM sj_job WHERE executor_info = 'soContractExpiryTask';

-- ============================================================
-- 1. 登记执行器（executor_info 必须与 @JobExecutor(name = "...") 完全一致）
-- ============================================================
INSERT IGNORE INTO `sj_job_executor` VALUES (200, 'dev', 'ruoyi_group', 'soContractExpiryTask', '1', '2026-09-19 20:00:00', '2026-09-19 20:00:00');

-- ============================================================
-- 2. 登记任务（每天 01:30；trigger_type=3 表示 CRON）
--    executor_timeout=90 秒：扫描是单条条件更新，远小于此值
-- ============================================================
INSERT IGNORE INTO `sj_job` VALUES (200, 'dev', 'biz_200', 'ruoyi_group', '销售合同到期扫描任务', '', 1, 1789405200000, 1, 1, 4, 1, 'soContractExpiryTask', 3, '0 30 1 * * ?', 1, 60, 3, 1, 1, 90, 0, '', 1, '{}', '', '', 0, '2026-09-19 20:00:00', '2026-09-19 20:00:00');

-- ============================================================
-- 3. 复核：确认任务与执行器已登记（期望各 1 行）
-- ============================================================
-- SELECT id, biz_id, job_name, executor_info, trigger_interval FROM sj_job WHERE executor_info = 'soContractExpiryTask';
-- SELECT id, group_name, executor_info FROM sj_job_executor WHERE executor_info = 'soContractExpiryTask';
