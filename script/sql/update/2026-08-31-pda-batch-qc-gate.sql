-- M3 批次5: 发料批次质检门禁 — 批次 IQC 状态回写字段 + 门禁开关配置
-- 1) wm_batch.qc_status: IQC 检验完成消息按收货行批次号回写(QUALIFIED/UNQUALIFIED);
--    NULL=无质检记录, 发料门禁放行。目标库: erp 库 (wm_* 表所在库, 即 ry-erp)。
-- 2) sys_config 开关 wm.issue.batch.qcGate: true=拣货批次 IQC 不合格禁止发料, false/缺省=关闭。
--    目标库: 系统库 (sys_config 所在库, 即 ry-vue)。

-- ========== ry-erp ==========
ALTER TABLE wm_batch ADD COLUMN qc_status VARCHAR(20) NULL COMMENT 'IQC质检状态(QUALIFIED/UNQUALIFIED), 由IQC完成消息回写; NULL=无质检记录';

-- ========== ry-vue ==========
-- config_id 2091000000000005001 (自增序号避开既有种子)
INSERT INTO sys_config (config_id, tenant_id, config_name, config_key, config_value, config_type, remark, create_time)
VALUES (2091000000000005001, '000000', 'PDA发料批次质检门禁开关', 'wm.issue.batch.qcGate', 'false', 'N',
        'true=发料拣货批次IQC不合格时禁止发料(M3批次5); false/缺省=门禁关闭');
