-- 2026-10-02：逐来源占用事件持久因果序号（ERP库，MySQL 5.7 / InnoDB）。
-- 仅适用于已有 pu_requirement_allocation_ledger/event 的 P1 基线，且尚无 event_sequence 列/索引。
-- 部署前停止相关业务写入口、审查实际表定义、备份并取得明确写授权；Agent 禁止自动执行。
-- 本迁移是单次结构变更，不可盲目重放；若列或同名索引已存在，必须单独核对后决定差异迁移。
-- 旧事件保留 NULL：绝不依据 occurred_at、雪花 event_id 或当前账本 version 猜测/回填历史因果。
-- 新 CREATE 必须显式写 0；每次 transition/补偿写入 ledger CAS 后的新版本。
-- NULL 旧事件不能用于恢复/新业务状态转移；新服务明确拒绝并保留原始审计证据。
-- DDL 本身不是部署验收；需以实际 InnoDB/完整唯一键门禁、非零测试与授权现场核验确认。

ALTER TABLE `pu_requirement_allocation_event`
  ADD COLUMN `event_sequence` int(11) NULL DEFAULT NULL
    COMMENT '占用内因果序号：CREATE=0，变更=账本CAS新版本；NULL旧事件未证明，禁止自动回填' AFTER `allocation_id`,
  ADD UNIQUE INDEX `uk_prae_tenant_allocation_sequence` (`tenant_id`, `allocation_id`, `event_sequence`);

-- MySQL 唯一索引允许旧 NULL 保留；服务仍必须拒绝任何 NULL、不连续或与账本版本不一致的序列。
-- 不删除事件/命令，不改写业务状态或数量。数据库变更登记由主线程追加本迁移条目。
