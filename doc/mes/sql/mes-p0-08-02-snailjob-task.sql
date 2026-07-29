-- =============================================
-- NexusERP - MES P0-8 SnailJob 任务注册: equipmentStatusSyncTask
-- 目标库: ry-vue (sj_job)
-- 创建时间: 2026-07-29
-- 说明: F8-5 设备状态联动任务注册到 SnailJob 调度表
--   iotDataArchiveTask(id=7, 0 */5 * * * ?)已存在, 无需新增
--   本脚本仅注册 equipmentStatusSyncTask, 每 60s 一次(避开 :00)
-- 幂等: NOT EXISTS + 动态 ID(MAX+1), 可重复执行
-- 镜像源: id=8 maintenanceDueTask 的全部配置列
-- =============================================
USE `ry-vue`;

INSERT INTO `sj_job` (
    `id`, `namespace_id`, `group_name`, `job_name`, `args_str`, `args_type`, `next_trigger_at`,
    `job_status`, `task_type`, `route_key`, `executor_type`, `executor_info`, `trigger_type`,
    `trigger_interval`, `block_strategy`, `executor_timeout`, `max_retry_times`, `parallel_num`,
    `retry_interval`, `bucket_index`, `resident`, `notify_ids`, `owner_id`, `labels`, `description`,
    `ext_attrs`, `deleted`, `create_dt`, `update_dt`
)
SELECT
    (SELECT IFNULL(MAX(id), 0) + 1 FROM `sj_job`),
    'dev', 'ruoyi_group', '设备状态联动任务', NULL, 1, UNIX_TIMESTAMP() * 1000 + 60000,
    1, 1, 4, 1, 'equipmentStatusSyncTask', 3,
    '7 */1 * * * ?', 1, 300, 3, 1,
    60, 122, 0, '', 1, '{}',
    'F8-5: 每60s扫描 iot_device_id 非空且 status IN(NORMAL,OFFLINE) 设备, 调 getOnlineStatus 回写 eq_equipment.status; CAS WHERE status IN(NORMAL,OFFLINE) 保护 FAULT/MAINTENANCE/SCRAPPED 不覆盖',
    '', 0, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM `sj_job` WHERE `executor_info` = 'equipmentStatusSyncTask'
);

-- 验证
-- SELECT id, job_name, executor_info, trigger_interval, job_status FROM sj_job WHERE executor_info IN ('equipmentStatusSyncTask', 'iotDataArchiveTask');
