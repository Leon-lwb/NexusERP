package com.nexus.erp.prd;

import com.nexus.erp.support.ErpDdlTestSupport;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 静态 DDL 契约测试；不连接、不读取、也不写入数据库。 */
class ApsProvenanceSqlContractTest {

    private static final Path ROOT = repositoryRoot();
    private static final Path INITIAL = ROOT.resolve("script/sql/ry-erp.sql");
    private static final Path MIGRATION = ROOT.resolve(
        "doc/ai/full-flow/scripts/2026-09-29-aps-capacity-provenance.sql");

    @Test
    void newPlansHaveVersionTwoAvailableButLegacyRowsDefaultToZero() throws IOException {
        String plan = tableDefinition(Files.readString(INITIAL, StandardCharsets.UTF_8), "prd_schedule_plan");
        assertColumn(plan, "capacity_calc_version", "tinyint NOT NULL DEFAULT 0");
    }

    @Test
    void dailyTaskLoadIsUniquePerTenantPlanTaskAndDate() throws IOException {
        for (Path file : new Path[] { INITIAL, MIGRATION }) {
            String source = file.equals(MIGRATION) ? migrationSql() : Files.readString(file, StandardCharsets.UTF_8);
            String table = tableDefinition(source, "prd_schedule_task_day_load");
            assertColumn(table, "load_id", "bigint NOT NULL");
            assertColumn(table, "tenant_id", "varchar(20) NOT NULL");
            assertColumn(table, "plan_id", "bigint NOT NULL");
            assertColumn(table, "task_id", "bigint NOT NULL");
            assertColumn(table, "resource_type", "varchar(16) NOT NULL");
            assertColumn(table, "resource_id", "bigint NOT NULL");
            assertColumn(table, "load_date", "date NOT NULL");
            assertColumn(table, "planned_hours", "decimal(12,2) NOT NULL");
            assertPrimaryKey(table, "load_id");
            assertIndex(table, true, "uk_psdl_tenant_plan_task_date", "tenant_id", "plan_id", "task_id", "load_date");
            assertUnicodeCollation(table);
        }
    }

    @Test
    void bookingIsUniquePerTenantPlanTaskAndCalendarAndIndexedForReservations() throws IOException {
        for (Path file : new Path[] { INITIAL, MIGRATION }) {
            String source = file.equals(MIGRATION) ? migrationSql() : Files.readString(file, StandardCharsets.UTF_8);
            String table = tableDefinition(source, "prd_schedule_capacity_booking");
            assertColumn(table, "booking_id", "bigint NOT NULL");
            assertColumn(table, "tenant_id", "varchar(20) NOT NULL");
            assertColumn(table, "plan_id", "bigint NOT NULL");
            assertColumn(table, "task_id", "bigint NOT NULL");
            assertColumn(table, "calendar_id", "bigint NOT NULL");
            assertColumn(table, "load_date", "date NOT NULL");
            assertColumn(table, "booked_hours", "decimal(12,2) NOT NULL");
            assertPrimaryKey(table, "booking_id");
            assertIndex(table, true, "uk_pscb_tenant_plan_task_calendar", "tenant_id", "plan_id", "task_id", "calendar_id");
            assertIndex(table, false, "idx_pscb_tenant_plan", "tenant_id", "plan_id");
            assertIndex(table, false, "idx_pscb_tenant_calendar", "tenant_id", "calendar_id");
            assertUnicodeCollation(table);
        }
    }

    @Test
    void localOneShotMigrationContainsOnlyThreeErpDdlStatements() throws IOException {
        String sql = migrationSql();
        String uncommented = sql.replaceAll("(?s)/\\*.*?\\*/", "")
            .replaceAll("(?m)^\\s*--[^\\r\\n]*", "");
        String[] statements = Arrays.stream(uncommented.split(";"))
            .map(String::trim).filter(statement -> !statement.isEmpty()).toArray(String[]::new);
        assertEquals(3, statements.length, "Only one ALTER and two CREATE TABLE statements are allowed");
        assertTrue(statements[0].matches("(?is)^ALTER\\s+TABLE\\s+`ry-erp`\\.`prd_schedule_plan`\\s+ADD\\s+COLUMN\\s+`capacity_calc_version`\\s+tinyint(?:\\(\\d+\\))?\\s+NOT\\s+NULL\\s+DEFAULT\\s+0(?:\\s+COMMENT\\s+'[^']*')?(?:\\s+AFTER\\s+`[^`]+`)?$"));
        assertTrue(statements[1].matches("(?is)^CREATE\\s+TABLE\\s+`ry-erp`\\.`prd_schedule_task_day_load`\\s*\\(.*"));
        assertTrue(statements[2].matches("(?is)^CREATE\\s+TABLE\\s+`ry-erp`\\.`prd_schedule_capacity_booking`\\s*\\(.*"));
        assertFalse(Pattern.compile("(?i)\\bIF\\s+NOT\\s+EXISTS\\b|\\bDROP\\b|\\bTRUNCATE\\b|\\bUPDATE\\b|\\bINSERT\\b|\\bDELETE\\b")
            .matcher(uncommented).find(), "No idempotent skip, destructive DDL or data mutations in one-shot migration");
    }

    private static String migrationSql() throws IOException {
        assertTrue(Files.isRegularFile(MIGRATION), "Missing reviewed one-shot migration: " + MIGRATION);
        return Files.readString(MIGRATION, StandardCharsets.UTF_8);
    }

    private static String tableDefinition(String source, String table) {
        Pattern pattern = Pattern.compile("(?is)\\bCREATE\\s+TABLE\\s+(?:`ry-erp`\\.)?`"
            + Pattern.quote(table) + "`\\s*\\((.*?)\\)\\s*(ENGINE\\s*=\\s*InnoDB[^;]*);");
        Matcher match = pattern.matcher(source);
        assertTrue(match.find(), "Missing CREATE TABLE " + table);
        String definition = match.group(1) + ") " + match.group(2);
        assertFalse(match.find(), "Multiple CREATE TABLE definitions for " + table);
        return definition;
    }

    private static void assertColumn(String table, String column, String definition) {
        ErpDdlTestSupport.assertColumn(table, column, definition);
        if (column.equals("tenant_id") || column.equals("resource_type")) {
            ErpDdlTestSupport.assertColumnCollation(table, column, "utf8mb4_unicode_ci");
        }
    }

    private static void assertPrimaryKey(String table, String column) {
        ErpDdlTestSupport.assertPrimaryKey(table, column);
    }

    private static void assertIndex(String table, boolean unique, String name, String... columns) {
        ErpDdlTestSupport.assertIndex(table, unique, name, columns);
    }

    private static void assertUnicodeCollation(String table) {
        assertTrue(Pattern.compile("(?i)ENGINE\\s*=\\s*InnoDB\\s+(?:DEFAULT\\s+CHARSET|CHARACTER\\s+SET)\\s*=\\s*utf8mb4\\s+COLLATE\\s*=\\s*utf8mb4_unicode_ci")
            .matcher(table).find(), "Table must explicitly use utf8mb4_unicode_ci");
    }

    private static Path repositoryRoot() {
        for (Path directory = Path.of("").toAbsolutePath(); directory != null; directory = directory.getParent()) {
            if (Files.isRegularFile(directory.resolve("script/sql/ry-erp.sql"))) {
                return directory;
            }
        }
        throw new IllegalStateException("Cannot find repository root with script/sql/ry-erp.sql");
    }
}
