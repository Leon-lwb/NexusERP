-- M2 批次2: 发料缺货逃生通道 — 发料行增加缺货原因字段
-- 缺货数量复用既有 short_qty(欠领数量), 行状态新增取值 SHORT_CLOSED(缺货关闭, 无DDL)
ALTER TABLE wm_production_issue_line
    ADD COLUMN shortage_reason VARCHAR(255) NULL COMMENT '缺货原因(缺货关闭时填写)';
