-- 考勤打卡位置留痕 — sch_attendance 增加上下班打卡经纬度/地址字段
-- 背景: App 打卡接口前端已传 latitude/longitude/address/accuracy, 后端此前接收后直接丢弃,
--       无法在考勤记录上回溯"在哪里打的卡"。本次补齐持久化能力(上下班分开存, 支持外勤异地打卡)。
-- 目标库: erp 库 (sch_* 表所在库, 即 ry-erp)。
-- 注意: 必须先执行本脚本再部署对应后端版本 —— 实体新增字段后 INSERT 会带上新列, 缺列将导致打卡失败。

ALTER TABLE sch_attendance ADD COLUMN clock_in_latitude DECIMAL(10,7) NULL COMMENT '上班打卡纬度';
ALTER TABLE sch_attendance ADD COLUMN clock_in_longitude DECIMAL(10,7) NULL COMMENT '上班打卡经度';
ALTER TABLE sch_attendance ADD COLUMN clock_in_address VARCHAR(255) NULL COMMENT '上班打卡地址/定位描述';
ALTER TABLE sch_attendance ADD COLUMN clock_out_latitude DECIMAL(10,7) NULL COMMENT '下班打卡纬度';
ALTER TABLE sch_attendance ADD COLUMN clock_out_longitude DECIMAL(10,7) NULL COMMENT '下班打卡经度';
ALTER TABLE sch_attendance ADD COLUMN clock_out_address VARCHAR(255) NULL COMMENT '下班打卡地址/定位描述';
