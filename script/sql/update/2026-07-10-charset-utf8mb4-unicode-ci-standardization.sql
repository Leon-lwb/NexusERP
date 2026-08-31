-- =====================================================================
-- Nexus-ERP 四库字符集与排序规则统一迁移脚本
-- 日期：2026-07-10
-- 关联规范：doc/system/specs/charset-encoding-standard.md
--
-- 目标：
--   将 ry-vue / ry-erp / ry-mes / ry-iot 四个库统一到
--   字符集 utf8mb4 + 排序规则 utf8mb4_unicode_ci
--   （与 application-dev.yml 中 connectionCollation=utf8mb4_unicode_ci 对齐）
--
-- 范围：
--   1) 四个库的默认字符集 (ALTER DATABASE)
--   2) 所有业务表/系统表的表级与列级字符集 (CONVERT TO)
--      —— 包括 ry-vue 中仍是 3 字节 utf8 的 sys_* / gen_* / im_* / jimu_* /
--         onl_* / test_* / tmp_* 等表，全部升级到 utf8mb4。
--
-- 强制例外（不得转换，保留原 utf8 / utf8_bin）：
--   - act_*  表：Flowable BPMN 引擎表，引擎 DDL 硬编码 utf8/utf8_bin
--   - flw_*  表：Flowable 7 事件/批处理引擎表，同上
--   转换它们会破坏流程引擎，脚本已通过 NOT LIKE 'act\_%' / 'flw\_%' 排除。
--
-- 安全性核查（已基于 script/sql 导出文件完成）：
--   ① act_/flw_ 之外无列使用 _bin 排序规则 -> CONVERT TO 不会抹掉任何
--      刻意的区分大小写列。
--   ② 待转换表中不存在「被索引且长度 >= 769」的 varchar 列 -> utf8(3字节)
--      升 utf8mb4(4字节) 后索引长度 4*N <= 3072 字节，不会触发
--      "Specified key was too long" 报错。
--      （act_ 的 varchar(4000)、ai_* 的 varchar(1024) 均不在索引中或已为 utf8mb4）
--   ③ sys_oper_log 等 varchar(4000) 列未建索引，行格式 Dynamic，转换安全。
--
-- 数据安全：
--   - utf8 -> utf8mb4 是超集升级，无数据丢失。
--   - utf8mb4_general_ci -> utf8mb4_unicode_ci 仅改排序规则，无数据丢失。
--   - CONVERT TO 会重建表与索引，期间表加锁。大表(如 sys_oper_log)耗时较长，
--     建议在低峰期执行。执行前请务必对四个库做完整备份(mysqldump)。
--
-- 执行方式（Navicat / mysql CLI 均可）：
--   1) 先执行【第 0 部分】预检，确认待转换表清单符合预期（应排除 act_*/flw_*）。
--   2) 依次执行【第 1 部分】~【第 3 部分】。
--   3) 执行【第 4 部分】核验，确认除 act_*/flw_* 外全部为 utf8mb4_unicode_ci。
--   4) 查看 _charset_migration_log 日志表，若有 FAIL 行，对其单独执行
--      ALTER 语句排查具体错误。
--
-- 回滚：
--   本脚本不可逆回滚（字符集升级后不建议降回 utf8）。回滚依赖执行前的备份。
-- =====================================================================


-- =====================================================================
-- 第 0 部分：执行前预检（只读，可反复执行）
-- =====================================================================

-- 0.1 四库表级字符集/排序规则分布统计（执行前快照）
SELECT
    TABLE_SCHEMA                                                      AS `库`,
    TABLE_COLLATION                                                   AS `表排序规则`,
    COUNT(*)                                                          AS `表数量`
FROM information_schema.TABLES
WHERE TABLE_SCHEMA IN ('ry-vue', 'ry-erp', 'ry-mes', 'ry-iot')
  AND TABLE_TYPE = 'BASE TABLE'
GROUP BY TABLE_SCHEMA, TABLE_COLLATION
ORDER BY TABLE_SCHEMA, TABLE_COLLATION;

-- 0.2 列出所有「不符合标准」的表（即将被转换的表，应排除 act_*/flw_*）
--     含两类：① 表级排序规则不符；② 表级已达标但存在列级显式覆盖。
--     逐表核对：确认 act_*/flw_* 不在结果中，其余表都会被转换。
SELECT
    t.TABLE_SCHEMA     AS `库`,
    t.TABLE_NAME       AS `表名`,
    t.TABLE_COLLATION  AS `表排序规则`,
    (SELECT COUNT(*) FROM information_schema.COLUMNS c
      WHERE c.TABLE_SCHEMA = t.TABLE_SCHEMA AND c.TABLE_NAME = t.TABLE_NAME
        AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci'
    )                  AS `非标列数`
FROM information_schema.TABLES t
WHERE t.TABLE_SCHEMA IN ('ry-vue', 'ry-erp', 'ry-mes', 'ry-iot')
  AND t.TABLE_TYPE = 'BASE TABLE'
  AND t.TABLE_NAME NOT LIKE 'act\_%'
  AND t.TABLE_NAME NOT LIKE 'flw\_%'
  AND (
        t.TABLE_COLLATION IS NULL
        OR t.TABLE_COLLATION <> 'utf8mb4_unicode_ci'
        OR EXISTS (
            SELECT 1 FROM information_schema.COLUMNS c
            WHERE c.TABLE_SCHEMA = t.TABLE_SCHEMA AND c.TABLE_NAME = t.TABLE_NAME
              AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci'
        )
      )
ORDER BY t.TABLE_SCHEMA, t.TABLE_NAME;


-- =====================================================================
-- 第 1 部分：修改四个库的默认字符集与排序规则
--   新建表时若未显式指定，将继承库默认值 -> utf8mb4 / utf8mb4_unicode_ci
-- =====================================================================

ALTER DATABASE `ry-vue` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER DATABASE `ry-erp` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER DATABASE `ry-mes` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER DATABASE `ry-iot` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


-- =====================================================================
-- 第 2 部分：动态转换表字符集（排除 act_*/flw_*）
--   通过存储过程遍历 information_schema，对每个不符合标准的表执行
--   ALTER TABLE ... CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
--   CONVERT TO 会同时转换表级默认与所有字符列的字符集/排序规则。
--   单表失败不中断整体流程，结果写入日志表 _charset_migration_log。
-- =====================================================================

-- 2.1 迁移日志表（放 ry-vue 库，记录四库转换结果）
DROP TABLE IF EXISTS `ry-vue`.`_charset_migration_log`;
CREATE TABLE `ry-vue`.`_charset_migration_log` (
    `id`           bigint NOT NULL AUTO_INCREMENT,
    `schema_name`  varchar(64)  NOT NULL COMMENT '库名',
    `table_name`   varchar(128) NOT NULL COMMENT '表名',
    `status`       varchar(8)   NOT NULL COMMENT 'OK/FAIL',
    `message`      varchar(1000) NULL DEFAULT NULL COMMENT '失败时的错误信息',
    `oper_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB COMMENT = '字符集迁移日志(临时,迁移完成后可删)';

-- 2.2 转换存储过程
DROP PROCEDURE IF EXISTS `ry-vue`.`p_convert_charset`;
DELIMITER $$
CREATE PROCEDURE `ry-vue`.`p_convert_charset`(IN p_db VARCHAR(64))
BEGIN
    DECLARE v_done INT DEFAULT 0;
    DECLARE v_tbl  VARCHAR(128);
    DECLARE v_skip INT;
    DECLARE v_err  VARCHAR(1000);
    DECLARE cur CURSOR FOR
        SELECT t.TABLE_NAME
        FROM information_schema.TABLES t
        WHERE t.TABLE_SCHEMA = p_db
          AND t.TABLE_TYPE = 'BASE TABLE'
          AND t.TABLE_NAME NOT LIKE 'act\_%'
          AND t.TABLE_NAME NOT LIKE 'flw\_%'
          AND (
                -- 表级排序规则不符
                t.TABLE_COLLATION IS NULL
                OR t.TABLE_COLLATION <> 'utf8mb4_unicode_ci'
                -- 或表级已达标但存在列级显式覆盖(如建表时列上单独声明了 general_ci)
                OR EXISTS (
                    SELECT 1 FROM information_schema.COLUMNS c
                    WHERE c.TABLE_SCHEMA = t.TABLE_SCHEMA
                      AND c.TABLE_NAME   = t.TABLE_NAME
                      AND c.COLLATION_NAME IS NOT NULL
                      AND c.COLLATION_NAME <> 'utf8mb4_unicode_ci'
                )
              );
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

    OPEN cur;
    r: LOOP
        FETCH cur INTO v_tbl;
        IF v_done = 1 THEN
            LEAVE r;
        END IF;
        SET v_skip = 0;
        SET v_err  = NULL;
        -- 内层块带独立异常处理器：单表失败仅置位 v_skip 并记录错误，不中断循环
        BEGIN
            DECLARE CONTINUE HANDLER FOR SQLEXCEPTION
            BEGIN
                GET DIAGNOSTICS CONDITION 1 @msg = MESSAGE_TEXT;
                SET v_skip = 1;
                SET v_err  = @msg;
            END;
            SET @sql = CONCAT(
                'ALTER TABLE `', p_db, '`.`', v_tbl,
                '` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci'
            );
            PREPARE s FROM @sql;
            EXECUTE s;
            DEALLOCATE PREPARE s;
        END;
        INSERT INTO `ry-vue`.`_charset_migration_log`(schema_name, table_name, status, message)
        VALUES (p_db, v_tbl, IF(v_skip = 1, 'FAIL', 'OK'), v_err);
    END LOOP;
    CLOSE cur;
END$$
DELIMITER ;

-- 2.3 对四个库依次执行转换
CALL `ry-vue`.`p_convert_charset`('ry-vue');
CALL `ry-vue`.`p_convert_charset`('ry-erp');
CALL `ry-vue`.`p_convert_charset`('ry-mes');
CALL `ry-vue`.`p_convert_charset`('ry-iot');

-- 2.4 清理存储过程
DROP PROCEDURE IF EXISTS `ry-vue`.`p_convert_charset`;


-- =====================================================================
-- 第 3 部分：迁移结果日志
-- =====================================================================

SELECT
    schema_name  AS `库`,
    status       AS `结果`,
    COUNT(*)     AS `表数量`
FROM `ry-vue`.`_charset_migration_log`
GROUP BY schema_name, status
ORDER BY schema_name, status;

-- 若上一步出现 FAIL，查看具体表与错误信息：
-- SELECT * FROM `ry-vue`.`_charset_migration_log` WHERE status = 'FAIL';


-- =====================================================================
-- 第 4 部分：执行后核验（只读，可反复执行）
-- =====================================================================

-- 4.1 核验：除 act_*/flw_* 外，所有表应为 utf8mb4_unicode_ci
SELECT
    TABLE_SCHEMA     AS `库`,
    TABLE_NAME       AS `表名`,
    TABLE_COLLATION  AS `表排序规则`,
    CASE
        WHEN TABLE_NAME LIKE 'act\_%' OR TABLE_NAME LIKE 'flw\_%'
            THEN '例外(Flowable,保留utf8_bin)'
        WHEN TABLE_COLLATION = 'utf8mb4_unicode_ci'
            THEN '符合标准'
        ELSE '★仍不符合★'
    END               AS `状态`
FROM information_schema.TABLES
WHERE TABLE_SCHEMA IN ('ry-vue', 'ry-erp', 'ry-mes', 'ry-iot')
  AND TABLE_TYPE = 'BASE TABLE'
ORDER BY `状态`, TABLE_SCHEMA, TABLE_NAME;

-- 4.2 核验：是否仍有字符列排序规则不为 utf8mb4_unicode_ci（排除 act_*/flw_*）
--     正常情况下除 Flowable 表外应为 0 行。
SELECT
    TABLE_SCHEMA     AS `库`,
    TABLE_NAME       AS `表名`,
    COLUMN_NAME      AS `列名`,
    COLLATION_NAME   AS `列排序规则`
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA IN ('ry-vue', 'ry-erp', 'ry-mes', 'ry-iot')
  AND TABLE_NAME NOT LIKE 'act\_%'
  AND TABLE_NAME NOT LIKE 'flw\_%'
  AND COLLATION_NAME IS NOT NULL
  AND COLLATION_NAME <> 'utf8mb4_unicode_ci'
ORDER BY TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME;

-- 4.3 核验：四个库默认字符集
SELECT
    SCHEMA_NAME      AS `库`,
    DEFAULT_CHARACTER_SET_NAME  AS `默认字符集`,
    DEFAULT_COLLATION_NAME      AS `默认排序规则`
FROM information_schema.SCHEMATA
WHERE SCHEMA_NAME IN ('ry-vue', 'ry-erp', 'ry-mes', 'ry-iot')
ORDER BY SCHEMA_NAME;

-- 4.4 核验通过后，清理日志表（确认无误再执行）
-- DROP TABLE IF EXISTS `ry-vue`.`_charset_migration_log`;
