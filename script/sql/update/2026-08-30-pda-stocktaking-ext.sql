-- M2 批次2: 盘点扩展(明盲盘) — 盘点单增加盘点模式字段
-- 临时盘点/动碰盘为 PDA 侧编排能力, 无新增表
ALTER TABLE wm_stocktaking_plan
    ADD COLUMN count_mode VARCHAR(10) DEFAULT 'OPEN' COMMENT '盘点模式 OPEN明盘/BLIND盲盘';
