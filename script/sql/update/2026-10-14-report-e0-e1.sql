/** 自研报表 E0/E1 元数据增量，执行前必须核对目标环境和已授权基线。 */
/** 仅在首期增量已完成后执行，兼容 MySQL 5.7，不重导初始化或修改业务表。 */
SET @report_e01_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rpt_export_job' AND column_name='execution_phase')=0, 'ALTER TABLE rpt_export_job ADD COLUMN execution_phase varchar(24) NOT NULL DEFAULT ''QUEUED''', 'SELECT 1');
PREPARE report_e01_stmt FROM @report_e01_ddl;
EXECUTE report_e01_stmt;
DEALLOCATE PREPARE report_e01_stmt;
SET @report_e01_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rpt_export_job' AND column_name='processed_rows')=0, 'ALTER TABLE rpt_export_job ADD COLUMN processed_rows bigint NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE report_e01_stmt FROM @report_e01_ddl;
EXECUTE report_e01_stmt;
DEALLOCATE PREPARE report_e01_stmt;
SET @report_e01_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rpt_export_job' AND column_name='total_rows')=0, 'ALTER TABLE rpt_export_job ADD COLUMN total_rows bigint DEFAULT NULL', 'SELECT 1');
PREPARE report_e01_stmt FROM @report_e01_ddl;
EXECUTE report_e01_stmt;
DEALLOCATE PREPARE report_e01_stmt;
SET @report_e01_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rpt_export_job' AND column_name='retry_round')=0, 'ALTER TABLE rpt_export_job ADD COLUMN retry_round int NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE report_e01_stmt FROM @report_e01_ddl;
EXECUTE report_e01_stmt;
DEALLOCATE PREPARE report_e01_stmt;
SET @report_e01_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rpt_export_job' AND column_name='last_heartbeat')=0, 'ALTER TABLE rpt_export_job ADD COLUMN last_heartbeat datetime DEFAULT NULL', 'SELECT 1');
PREPARE report_e01_stmt FROM @report_e01_ddl;
EXECUTE report_e01_stmt;
DEALLOCATE PREPARE report_e01_stmt;
