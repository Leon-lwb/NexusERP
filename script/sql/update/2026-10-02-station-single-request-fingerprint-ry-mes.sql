-- 目标：MES 数据源（ry-mes），不是系统库或 ERP 库。
-- 一次性存量结构迁移：执行前人工核对数据库、备份及该列尚不存在。
-- 本脚本不自动执行，不重放，不修改旧业务行、数量、状态或历史请求资格。
-- 新后端部署前必须先完成此结构迁移；旧记录保持 NULL，不能由当前员工关联猜测原请求。
ALTER TABLE mes_station_report
    ADD COLUMN request_fingerprint varchar(64) NULL DEFAULT NULL
    COMMENT '单件规范化原始请求SHA256指纹;旧记录不得猜测回填';
