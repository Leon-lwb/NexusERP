package com.nexus.report.extension;

import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.*;
import java.util.regex.Pattern;

/** 在隔离H2夹具中安装当前扩展增量，不触碰业务数据库。 */
public final class ReportExtensionSchema {
    /**
     * 执行完整的扩展DDL，保证测试与实际增量字段一致。
     *
     * @param jdbc 当前契约的jdbc配置或状态
     */
    public static void apply(JdbcTemplate jdbc) {
        try {
            var sql =
                    Files.readString(
                            Path.of("../../script/sql/update/2026-10-15-report-e2-e7.sql"));
            var tables =
                    Pattern.compile(
                                    "CREATE TABLE IF NOT EXISTS \u0060rpt_[^\u0060]+\u0060"
                                            + " \\([\\s\\S]*?\\) ENGINE[^;]*;")
                            .matcher(sql);
            while (tables.find())
                jdbc.execute(
                        tables.group()
                                .replaceAll("\\) ENGINE[^;]*;", ");")
                                .replaceAll("(?m)^  KEY [^\n]+\n", "")
                                .replaceAll(",\n\\)", "\n)"));
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }
}
