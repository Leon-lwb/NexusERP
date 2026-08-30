-- M2 批次2: 移库两段式(下架 → 上架) — 移库行增加已下架数量字段
-- 头状态机新增取值 TAKEN_OFF(已下架, 待上架), 无头表 DDL
ALTER TABLE wm_transfer_line
    ADD COLUMN takeoff_qty DECIMAL(18, 4) NULL COMMENT '已下架数量(两段式移库, 整行下架时=调拨数量)';
