-- =============================================
-- NexusERP - SnailJob 任务注册: 设备保养到期提醒
-- 目标库: ry-vue (SnailJob 调度库)
-- 创建时间: 2026-06-26
-- 说明: Wave2 MES 运行时补齐 - 注册 maintenanceDueTask
--       - executor_info = 'maintenanceDueTask' 对应 @JobExecutor(name = "maintenanceDueTask")
--       - cron: 0 0 8 * * ?  每天 8:00 执行
--       - block_strategy=1(丢弃) 避免重叠
-- 幂等: WHERE NOT EXISTS
-- =============================================

USE `ry-vue`;

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
    'dev', 'ruoyi_group', '设备保养到期提醒任务', NULL, 1,
    UNIX_TIMESTAMP(DATE_ADD(NOW(), INTERVAL 1 HOUR)) * 1000,
    1, 1, 4,
    1, 'maintenanceDueTask', 3, '0 0 8 * * ?',
    1, 300, 3,
    1, 60, 122, 0,
    '', 1, '{}', '扫描 eq_maintenance_plan 中 next_date<=today 的到期计划, 触发 SSE 推送+EQUIPMENT 安灯(每计划24h限频)', '',
    0, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `sj_job`
    WHERE `namespace_id` = 'dev'
      AND `group_name` = 'ruoyi_group'
      AND `executor_info` = 'maintenanceDueTask'
      AND `deleted` = 0
);

-- 同步注册执行器(SnailJob 客户端启动时也会自动同步, 此处幂等兜底)
INSERT INTO `sj_job_executor` (
    `namespace_id`, `group_name`, `executor_info`, `executor_type`,
    `create_dt`, `update_dt`
)
SELECT
    'dev', 'ruoyi_group', 'maintenanceDueTask', '1',
    NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `sj_job_executor`
    WHERE `namespace_id` = 'dev'
      AND `group_name` = 'ruoyi_group'
      AND `executor_info` = 'maintenanceDueTask'
);

-- 验证
-- SELECT id, job_name, executor_info, trigger_interval FROM `sj_job` WHERE `executor_info` = 'maintenanceDueTask';
-- SELECT id, executor_info FROM `sj_job_executor` WHERE `executor_info` = 'maintenanceDueTask';
