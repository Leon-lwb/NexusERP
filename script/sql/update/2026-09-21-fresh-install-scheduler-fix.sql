-- ============================================================================
-- 2026-09-21 审查修复批次：补种 wmBatchExpiryTask 的 SnailJob 任务定义
-- ----------------------------------------------------------------------------
-- 用途   : 批次效期过期标记任务（ERP-WM-06，WmBatchExpiryTask）此前**只有 @Scheduled
--          本地兜底、没有 sj_job / sj_job_executor 定义**。而兜底由
--          SchedulerFallbackUtils.runIfFallbackEnabled 守卫：snail-job.enabled=true 时
--          主动让位（防双跑），SnailJob 侧又没有这个任务 → **两边都不跑**，
--          ACTIVE 且已过期的批次永远不会被置为 EXPIRED（EXPIRED 退回"准死状态"）。
--          该任务是全仓唯一「既有兜底又无 sj_job 种子」的任务。
-- 影响库 : ry-vue
-- 前提   : 无（不依赖其它增量脚本）
-- 幂等   : 是（INSERT IGNORE + 固定主键；uk_sj_job_01(namespace_id, biz_id) 兜底）
-- 缓存   : 无需清 Redis（sj_job 由 SnailJob 服务端读取，不属应用侧 Redis 缓存）
-- 备注   : 主脚本 script/sql/ry-vue.sql 已同步含这两行，全新安装无需执行本脚本。
-- ============================================================================

-- 前置检查（可选，应先返回 0 行）：
-- SELECT id, biz_id, executor_info, trigger_interval, deleted FROM sj_job WHERE biz_id = 'biz_206';

INSERT IGNORE INTO `sj_job`
(`id`, `namespace_id`, `biz_id`, `group_name`, `job_name`, `args_str`, `args_type`, `next_trigger_at`,
 `job_status`, `task_type`, `route_key`, `executor_type`, `executor_info`, `trigger_type`, `trigger_interval`,
 `block_strategy`, `executor_timeout`, `max_retry_times`, `parallel_num`, `retry_interval`, `bucket_index`,
 `resident`, `notify_ids`, `owner_id`, `labels`, `description`, `ext_attrs`, `deleted`, `create_dt`, `update_dt`)
VALUES
(206, 'dev', 'biz_206', 'ruoyi_group', '批次效期过期标记任务', '', 1, UNIX_TIMESTAMP(NOW()) * 1000,
 1, 1, 4, 1, 'wmBatchExpiryTask', 3, '0 30 1 * * ?',
 1, 300, 3, 1, 60, 122,
 0, '', 1, '{}',
 'ERP-WM-06 补种: 每日01:30把 ACTIVE 且 expiry_date < now 的批次置 EXPIRED(幂等条件更新, 无需推进游标)', '', 0,
 NOW(), NOW());

INSERT IGNORE INTO `sj_job_executor` VALUES (47, 'dev', 'ruoyi_group', 'wmBatchExpiryTask', '1', NOW(), NOW());

-- 回滚：
-- DELETE FROM `sj_job` WHERE `biz_id` = 'biz_206';
-- DELETE FROM `sj_job_executor` WHERE `id` = 47;
