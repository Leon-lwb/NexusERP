-- ============================================================================
-- NexusERP 定时任务调度修复增量脚本（2026-09-20）
-- 对应审计: doc/audit/2026-09-20-audit-mes-eq.md (MES-EQ-02)
--           doc/audit/2026-09-20-audit-iot.md    (IOT-TASK-02)
-- 修复报告: doc/audit/2026-09-20-fix-scheduler.md
-- ----------------------------------------------------------------------------
-- 用途
--   1) 补齐 MES-EQ-02：oeeAggregateTask / equipmentHealthTask 此前只在 sj_job_executor
--      注册了“执行器能力”，sj_job 中没有任务定义 → 即使打开 SnailJob 也永不触发，
--      OEE 看板与设备健康度只有人工点“手动聚合/手动评估”才有数据。
--   2) 修正 IOT-TASK-02：iotDeviceHeartbeatTask / iotStreamTrimTask 的 cron 被误写成
--      '0 0 2 * * ?'（每天 2:00），与设计不符（心跳 5 分钟、Stream 裁剪每小时）→
--      设备掉线最长 24h 仍显示 ONLINE；Redis Stream 一天才裁剪一次，先 OOM 再谈修剪。
--
-- 执行前提
--   * 目标库：ry-vue（系统库）；目标表：sj_job。
--   * 仅用于存量库升级。script/sql/ry-vue.sql 主脚本的等价合并由主代理串行执行
--     （见修复报告 §3「主脚本合并清单」），本脚本不与主脚本冲突、可独立执行。
--   * 需已存在 sj_group_config 中 namespace_id='dev' + group_name='ruoyi_group' 的分组
--     （主脚本种子已包含；生产命名空间若为 'prod' 见文末第 7 节说明）。
--   * 无需清 Redis 缓存；但 SnailJob 服务端持有任务本地缓存，执行后建议重启
--     ruoyi-snailjob-server，或在控制台任务列表点一次“刷新”。
--
-- 幂等性
--   * INSERT 使用 INSERT IGNORE，依赖唯一索引 uk_sj_job_01(namespace_id, biz_id)：
--     重复执行不会产生第二条任务定义，也不会改动已存在的行。
--   * UPDATE 带 trigger_interval <> 目标值 条件：重复执行不产生额外变更。
--   * 不显式指定主键 id（交由 AUTO_INCREMENT 分配），避免与存量库 id 冲突。
--   * 前置检查：本脚本不新增索引/约束，无需存量重复数据检查。
--
-- 字段取值依据（与 sj_job 既有行完全一致）
--   job_status=1(开启)、trigger_type=3(CRON，既有行同值)、task_type=1(集群)、
--   route_key=4、executor_type=1、block_strategy=1、executor_timeout=300、
--   max_retry_times=3、parallel_num=1、retry_interval=60、resident=0、
--   owner_id=1、labels='{}'、deleted=0。
--   bucket_index=116：SnailJob 时间轮分桶值，仅用于集群扫描负载分摊；
--   主脚本 19 条上游任务（biz_10~biz_28、mq-*）同为 116，故沿用该值
--   （控制台保存任务时服务端会重算，不影响触发）。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1) MES-EQ-02：补 oeeAggregateTask 任务定义（每天 01:00 聚合前一天 OEE）
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `sj_job`
(`namespace_id`, `biz_id`, `group_name`, `job_name`, `args_str`, `args_type`, `next_trigger_at`,
 `job_status`, `task_type`, `route_key`, `executor_type`, `executor_info`, `trigger_type`, `trigger_interval`,
 `block_strategy`, `executor_timeout`, `max_retry_times`, `parallel_num`, `retry_interval`, `bucket_index`,
 `resident`, `notify_ids`, `owner_id`, `labels`, `description`, `ext_attrs`, `deleted`)
VALUES
('dev', 'biz_201', 'ruoyi_group', '设备OEE日聚合任务', NULL, 1, UNIX_TIMESTAMP(NOW()) * 1000,
 1, 1, 4, 1, 'oeeAggregateTask', 3, '0 0 1 * * ?',
 1, 300, 3, 1, 60, 116,
 0, '', 1, '{}',
 'MES-EQ-02 补种: 每日01:00聚合前一天设备OEE写入 eq_oee_record(租户+设备+stat_date 唯一, 幂等可重跑)', '', 0);

-- ----------------------------------------------------------------------------
-- 2) MES-EQ-02：补 equipmentHealthTask 任务定义（每天 07:00 设备健康日评）
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `sj_job`
(`namespace_id`, `biz_id`, `group_name`, `job_name`, `args_str`, `args_type`, `next_trigger_at`,
 `job_status`, `task_type`, `route_key`, `executor_type`, `executor_info`, `trigger_type`, `trigger_interval`,
 `block_strategy`, `executor_timeout`, `max_retry_times`, `parallel_num`, `retry_interval`, `bucket_index`,
 `resident`, `notify_ids`, `owner_id`, `labels`, `description`, `ext_attrs`, `deleted`)
VALUES
('dev', 'biz_202', 'ruoyi_group', '设备健康度日评任务', NULL, 1, UNIX_TIMESTAMP(NOW()) * 1000,
 1, 1, 4, 1, 'equipmentHealthTask', 3, '0 0 7 * * ?',
 1, 300, 3, 1, 60, 116,
 0, '', 1, '{}',
 'MES-EQ-02 补种: 每日07:00评估全部关联IoT设备健康度, LOW级触发EQUIPMENT安灯', '', 0);

-- ----------------------------------------------------------------------------
-- 3) IOT-TASK-02：心跳检测 cron 修正为每 5 分钟（原 '0 0 2 * * ?'）
--    cron 依据: IotDeviceHeartbeatTask（TIMEOUT_MINUTES=15 与 5 分钟扫描节奏对齐）
-- ----------------------------------------------------------------------------
UPDATE `sj_job`
   SET `trigger_interval` = '0 */5 * * * ?',
       `update_dt`        = NOW()
 WHERE `deleted` = 0
   AND `executor_info` = 'iotDeviceHeartbeatTask'
   AND `trigger_interval` <> '0 */5 * * * ?';

-- ----------------------------------------------------------------------------
-- 4) IOT-TASK-02：Stream 裁剪 cron 修正为每小时（原 '0 0 2 * * ?'）
--    cron 依据: IotStreamTrimTask（XTRIM MAXLEN ~100000 防 Redis 内存无限增长）
-- ----------------------------------------------------------------------------
UPDATE `sj_job`
   SET `trigger_interval` = '0 0 * * * ?',
       `update_dt`        = NOW()
 WHERE `deleted` = 0
   AND `executor_info` = 'iotStreamTrimTask'
   AND `trigger_interval` <> '0 0 * * * ?';

-- ----------------------------------------------------------------------------
-- 5) 前置检查：确认执行器能力已注册（MES-EQ-02 的前提；主脚本种子已存在则无需插入）
-- ----------------------------------------------------------------------------
-- SELECT `executor_name`, `group_name`, `executor_type`
--   FROM `sj_job_executor`
--  WHERE `executor_name` IN ('oeeAggregateTask', 'equipmentHealthTask');

-- ----------------------------------------------------------------------------
-- 6) 执行后校验：应返回 4 行，且 cron 分别为
--    oeeAggregateTask='0 0 1 * * ?'、equipmentHealthTask='0 0 7 * * ?'、
--    iotDeviceHeartbeatTask='0 */5 * * * ?'、iotStreamTrimTask='0 0 * * * ?'
-- ----------------------------------------------------------------------------
-- SELECT `id`, `namespace_id`, `biz_id`, `executor_info`, `trigger_type`, `trigger_interval`, `job_status`
--   FROM `sj_job`
--  WHERE `deleted` = 0
--    AND `executor_info` IN ('oeeAggregateTask', 'equipmentHealthTask',
--                            'iotDeviceHeartbeatTask', 'iotStreamTrimTask')
--  ORDER BY `executor_info`;

-- ----------------------------------------------------------------------------
-- 7) 生产命名空间说明（默认不执行）
--    上述 INSERT 的 namespace_id 与主脚本既有 sj_job 行保持一致，均为 'dev'。
--    若生产 SnailJob 客户端配置的命名空间为 'prod'（sj_group_config 已有 prod 行），
--    需将第 1/2 节的 namespace_id 改为 'prod'（biz_id 亦需保持全局唯一，建议 'biz_201p'/'biz_202p'）
--    后再执行；第 3/4 节的 UPDATE 未按 namespace_id 过滤，两个命名空间都会一并修正。
-- ----------------------------------------------------------------------------
