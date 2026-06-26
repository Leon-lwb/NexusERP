-- =============================================
-- NexusERP - SnailJob 任务注册: IoT 自动采集归档
-- 目标库: ry-vue (SnailJob 调度库，与系统主库同实例)
-- 创建时间: 2026-06-26
-- 说明: 为 Wave1 Task 3.2「IoT 自动采集归档」注册定时任务
--       - executor_info = 'iotDataArchiveTask' 对应 @JobExecutor(name = "iotDataArchiveTask")
--       - cron: 0 */5 * * * ?  每 5 分钟执行一次
--       - 拉取时间窗口 6 分钟，略大于周期以避免漏拉
--       - block_strategy=1(丢弃) 避免任务重叠
-- 幂等设计: 使用 job_name + executor_info + namespace_id + group_name 检查存在性
-- =============================================

USE `ry-vue`;

-- ============================================================
-- 1. 注册 iotDataArchiveTask 任务（幂等：仅在不存在时插入）
-- ============================================================
INSERT INTO `sj_job` (
    `namespace_id`, `group_name`, `job_name`, `args_str`, `args_type`,
    `next_trigger_at`, `job_status`, `task_type`, `route_key`,
    `executor_type`, `executor_info`, `trigger_type`, `trigger_interval`,
    `block_strategy`, `executor_timeout`, `max_retry_times`,
    `parallel_num`, `retry_interval`, `bucket_index`, `resident`,
    `notify_ids`, `owner_id`, `labels`, `description`, `ext_attrs`,
    `deleted`, `create_dt`, `update_dt`
)
SELECT
    'dev', 'ruoyi_group', 'IoT数据自动归档任务', NULL, 1,
    -- 下次触发时间: 当前时间 + 5 分钟（毫秒时间戳）
    UNIX_TIMESTAMP(DATE_ADD(NOW(), INTERVAL 5 MINUTE)) * 1000,
    1, 1, 4,
    1, 'iotDataArchiveTask', 3, '0 */5 * * * ?',
    1, 300, 3,
    1, 60, 121, 0,
    '', 1, '{}', 'IoT自动采集数据归档到 dc_data_record，每5分钟执行一次（拉取6分钟窗口，uk_point_collect_time 去重）', '',
    0, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `sj_job`
    WHERE `namespace_id` = 'dev'
      AND `group_name` = 'ruoyi_group'
      AND `executor_info` = 'iotDataArchiveTask'
      AND `deleted` = 0
);

-- ============================================================
-- 验证
-- ============================================================
-- SELECT id, namespace_id, group_name, job_name, executor_info, trigger_type, trigger_interval, job_status
--   FROM `sj_job` WHERE `executor_info` = 'iotDataArchiveTask';
-- 期望: 1 行，job_status=1, trigger_type=3, trigger_interval='0 */5 * * * ?'
