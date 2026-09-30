package com.nexus.common.ai.decision;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Tag("local")
@Tag("dev")
@Tag("prod")
class AiDecisionSqlContractTest {

    private static final Pattern MENU_INSERT = Pattern.compile("(?m)^INSERT INTO `sys_menu` VALUES \\((\\d+),([^\\r\\n]*)\\);$");
    private static final Pattern MENU_VALUE = Pattern.compile("\\s*('(?:''|[^'])*'|NULL|-?\\d+)\\s*(?:,\\s*|$)");
    private static String sql;

    @BeforeAll
    static void loadSql() throws IOException {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("script/sql/ry-vue.sql"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("未找到仓库根目录 script/sql/ry-vue.sql");
        }
        sql = Files.readString(dir.resolve("script/sql/ry-vue.sql"));
    }

    @Test
    void decisionTablesAndIndexesMustExist() {
        assertAll("industrial decision DDL",
            () -> {
                String run = tableDefinition("ai_decision_run");
                assertColumns(run, "id", "tenant_id", "scene_code", "biz_type", "biz_id", "biz_code",
                    "parent_run_id", "superseded_by_run_id", "trigger_type", "status", "risk_score", "risk_level",
                    "confidence_level", "recommended_scenario_code", "input_snapshot_json", "input_snapshot_hash",
                    "snapshot_time", "valid_until", "score_config_json", "scenario_result_json", "narrative_json",
                    "result_json", "outcome_json", "approval_context_json", "summary_mode", "model_id", "model_name", "prompt_version",
                    "input_tokens", "output_tokens", "estimated_cost", "analysis_flow_instance_id",
                    "execution_flow_instance_id", "error_code", "error_message", "started_at", "analysis_finished_at",
                    "finished_at", "version");
                assertColumnDefinition(run, "version", "int NOT NULL DEFAULT 0");
                assertIndex(run, false, "idx_decision_tenant_scene_status", "tenant_id", "scene_code", "status");
                assertIndex(run, false, "idx_decision_biz", "tenant_id", "biz_type", "biz_id");
                assertIndex(run, false, "idx_decision_create_time", "tenant_id", "create_time");
                assertIndex(run, false, "idx_decision_parent", "parent_run_id");
                assertIndex(run, false, "idx_decision_analysis_flow", "analysis_flow_instance_id");
                assertIndex(run, false, "idx_decision_execution_flow", "execution_flow_instance_id");
            },
            () -> {
                String evidence = tableDefinition("ai_decision_evidence");
                assertColumns(evidence, "id", "tenant_id", "run_id", "evidence_key", "source_domain", "source_type",
                    "source_id", "source_code", "source_name", "metric_code", "metric_name", "value_type",
                    "metric_value", "unit", "observed_at", "data_version", "confidence", "verifiable", "normative",
                    "derived_from_json", "display_path");
                assertIndex(evidence, true, "uk_decision_evidence", "run_id", "evidence_key");
                assertIndex(evidence, false, "idx_decision_evidence_source", "tenant_id", "source_type", "source_id");
                assertIndex(evidence, false, "idx_decision_evidence_run", "run_id");
            },
            () -> {
                String action = tableDefinition("ai_decision_action");
                assertColumns(action, "id", "tenant_id", "run_id", "action_type", "title", "display_reason",
                    "target_biz_type", "target_biz_id", "target_biz_code", "payload_json", "risk_level",
                    "approval_required", "approval_status", "execution_status", "permission_code", "executor_code",
                    "sequence_no", "dependency_ids_json", "idempotency_key", "approved_by", "approved_at",
                    "approval_comment", "result_biz_type", "result_biz_id", "result_biz_code", "result_json",
                    "retry_count", "last_error_code", "last_error_message", "started_at", "finished_at", "version");
                assertColumnDefinition(action, "version", "int NOT NULL DEFAULT 0");
                assertIndex(action, true, "uk_decision_action_idempotency", "tenant_id", "idempotency_key");
                assertIndex(action, false, "idx_decision_action_run", "run_id", "sequence_no");
                assertIndex(action, false, "idx_decision_action_approval", "tenant_id", "approval_status");
                assertIndex(action, false, "idx_decision_action_execution", "tenant_id", "execution_status");
            });
    }

    @Test
    void decisionMenusAndPermissionsMustExist() {
        assertMenuPermissions(sql);
    }

    @Test
    void decisionMenuIdsMustBeUniqueAndComplete() {
        assertMenuIds(sql);
    }

    @Test
    void wrongApprovalMenuParentMustBeRejected() {
        String mutated = sql.replace("VALUES (1855, '审批决策', 1851,", "VALUES (1855, '审批决策', 1700,");
        assertFalse(sql.equals(mutated), "测试输入未找到 1855 菜单行");
        assertThrows(AssertionError.class, () -> {
            assertMenuPermissions(mutated);
            assertMenuIds(mutated);
        }, "1855 挂错父菜单不应通过 SQL 契约");
    }

    @Test
    void trailingCommaInApprovalMenuMustBeRejected() {
        Matcher menu = Pattern.compile("(?m)^INSERT INTO `sys_menu` VALUES \\(1855,[^\\r\\n]*\\);$").matcher(sql);
        assertTrue(menu.find(), "缺少 1855 菜单行，无法构造尾逗号变体");
        String mutated = sql.substring(0, menu.end() - 2) + ",);" + sql.substring(menu.end());
        assertThrows(AssertionError.class, () -> assertMenuPermissions(mutated),
            "1855 VALUES 末尾多余逗号不应通过 SQL 契约");
    }

    @Test
    void wrongRunVersionTypeMustBeRejected() {
        String run = tableDefinition("ai_decision_run");
        String changed = run.replaceFirst("(?m)^  `version` int(?:\\(11\\))? NOT NULL DEFAULT 0",
            "  `version` varchar(20) NULL DEFAULT NULL");
        assertFalse(run.equals(changed), "测试输入未找到 ai_decision_run.version");
        String mutated = sql.replace(run, changed);
        assertThrows(AssertionError.class, () -> assertVersionColumns(mutated),
            "version 仍存在但类型和默认值错误时不应通过 SQL 契约");
    }

    @Test
    void wrongIndexOrderOrUniquenessMustBeRejected() {
        String action = tableDefinition("ai_decision_action");
        String reordered = action.replaceFirst("(`idx_decision_action_run`\\s*\\()\\s*`run_id`\\s*,\\s*`sequence_no`",
            "$1`sequence_no`, `run_id`");
        assertFalse(action.equals(reordered));
        assertThrows(AssertionError.class, () -> assertIndex(reordered, false, "idx_decision_action_run", "run_id", "sequence_no"));

        String nonUnique = action.replaceFirst("UNIQUE\\s+(?:KEY|INDEX)(\\s+`uk_decision_action_idempotency`)", "INDEX$1");
        assertFalse(action.equals(nonUnique));
        assertThrows(AssertionError.class, () -> assertIndex(nonUnique, true, "uk_decision_action_idempotency", "tenant_id", "idempotency_key"));
    }

    @Test
    void oldStyleDdlStillEnforcesColumnsAndIndexes() {
        String definition = tableDefinition("ai_decision_action")
            .replaceAll("(?i)\\s+CHARACTER SET utf8mb4\\s+COLLATE utf8mb4_[a-z0-9_]+", "")
            .replaceAll("(?i)\\bint\\(11\\)", "int")
            .replaceAll("(?m)^(\\s*)(UNIQUE )?INDEX (`[^`]+`)\\(", "$1$2KEY $3 (")
            .replaceAll("[ \t]*,[ \t]*(?=`)", ",");
        String oldStyle = "CREATE TABLE `ai_decision_action` (" + definition
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
        String parsed = tableDefinition(oldStyle, "ai_decision_action");
        assertColumnDefinition(parsed, "version", "int NOT NULL DEFAULT 0");
        assertIndex(parsed, true, "uk_decision_action_idempotency", "tenant_id", "idempotency_key");
        assertIndex(parsed, false, "idx_decision_action_run", "run_id", "sequence_no");
    }

    @Test
    void wrongTenantDefaultMustBeRejected() {
        String run = tableDefinition("ai_decision_run");
        String changed = run.replaceFirst("(`tenant_id`[^\\r\\n]*?DEFAULT )'000000'", "$1'000001'");
        assertFalse(run.equals(changed));
        assertThrows(AssertionError.class, () -> assertColumnDefinition(changed, "tenant_id", "varchar(20) NOT NULL DEFAULT '000000'"));
    }

    private static void assertMenuPermissions(String source) {
        Map<Integer, MenuRow> rows = decisionMenus(source);
        assertAll("decision menu mapping",
            () -> assertMenuRow(rows, 1850, "工业决策中心", 1700, "M", "0", "decision", null, null),
            () -> assertMenuRow(rows, 1851, "交付风险看板", 1850, "C", "0", "index", "ai/decision/index", "ai:decision:query"),
            () -> assertMenuRow(rows, 1852, "决策运行详情", 1850, "C", "1", "detail", "ai/decision/detail", "ai:decision:query"),
            () -> assertMenuRow(rows, 1853, "决策查询", 1851, "F", "0", "", "", "ai:decision:query"),
            () -> assertMenuRow(rows, 1854, "发起决策", 1851, "F", "0", "", "", "ai:decision:run"),
            () -> assertMenuRow(rows, 1855, "审批决策", 1851, "F", "0", "", "", "ai:decision:approve"),
            () -> assertMenuRow(rows, 1856, "执行重试", 1851, "F", "0", "", "", "ai:decision:execute"),
            () -> assertMenuRow(rows, 1857, "决策导出", 1851, "F", "0", "", "", "ai:decision:export"),
            () -> assertMenuRow(rows, 1858, "决策配置", 1851, "F", "0", "", "", "ai:decision:config"),
            () -> assertMenuRow(rows, 1859, "决策评测", 1851, "F", "0", "", "", "ai:decision:benchmark"));
    }

    private static void assertMenuRow(Map<Integer, MenuRow> rows, int id, String name, int parentId, String type,
                                      String visible, String path, String component, String permission) {
        MenuRow row = rows.get(id);
        assertNotNull(row, "缺少决策菜单 ID " + id);
        assertEquals(new MenuRow(name, parentId, type, visible, path, component, permission), row,
            "menu_id=" + id + " 的父级/类型/可见性/路由/权限映射错误");
    }

    private static void assertMenuIds(String source) {
        Map<Integer, MenuRow> rows = decisionMenus(source);
        assertEquals(10, rows.size(), "缺少决策菜单 ID 1850–1859");
        for (int id = 1850; id <= 1859; id++) {
            assertTrue(rows.containsKey(id), "缺少决策菜单 ID " + id);
        }
    }

    private static Map<Integer, MenuRow> decisionMenus(String source) {
        Matcher matcher = MENU_INSERT.matcher(source);
        Set<String> allIds = new HashSet<>();
        Map<Integer, MenuRow> rows = new LinkedHashMap<>();
        while (matcher.find()) {
            String id = matcher.group(1);
            assertTrue(allIds.add(id), "sys_menu ID 重复: " + id);
            if (id.matches("185[0-9]")) {
                int decisionId = Integer.parseInt(id);
                List<String> fields = parseMenuValues(matcher.group(2), decisionId);
                rows.put(decisionId, new MenuRow(fields.get(0), Integer.parseInt(fields.get(1)), fields.get(8),
                    fields.get(9), fields.get(3), fields.get(4), fields.get(11)));
            }
        }
        return rows;
    }

    private static List<String> parseMenuValues(String values, int id) {
        assertFalse(values.stripTrailing().endsWith(","), "sys_menu " + id + " VALUES 不允许尾随逗号");
        List<String> fields = new ArrayList<>();
        Matcher token = MENU_VALUE.matcher(values);
        int offset = 0;
        while (offset < values.length()) {
            token.region(offset, values.length());
            assertTrue(token.lookingAt(), "sys_menu " + id + " VALUES 格式错误，位置 " + offset);
            String value = token.group(1);
            fields.add(value.equals("NULL") ? null : value.startsWith("'")
                ? value.substring(1, value.length() - 1).replace("''", "'") : value);
            offset = token.end();
        }
        assertEquals(19, fields.size(), "sys_menu " + id + " 字段数量异常");
        return fields;
    }

    private record MenuRow(String name, int parentId, String type, String visible,
                           String path, String component, String permission) { }

    private static void assertVersionColumns(String source) {
        assertColumnDefinition(tableDefinition(source, "ai_decision_run"), "version", "int NOT NULL DEFAULT 0");
        assertColumnDefinition(tableDefinition(source, "ai_decision_action"), "version", "int NOT NULL DEFAULT 0");
    }

    private static String tableDefinition(String name) {
        return tableDefinition(sql, name);
    }

    private static String tableDefinition(String source, String name) {
        Matcher matcher = Pattern.compile("(?ms)^CREATE TABLE `" + Pattern.quote(name)
            + "`\\s*\\(((?:(?!^CREATE TABLE\\b).)*?)^\\)\\s*ENGINE\\s*=\\s*InnoDB\\b[^;\\r\\n]*\\b(?:DEFAULT\\s+CHARSET|CHARACTER\\s+SET)\\s*=\\s*utf8mb4\\b")
            .matcher(source);
        if (!matcher.find()) {
            return fail("缺少 CREATE TABLE `" + name + "` (InnoDB utf8mb4)");
        }
        String definition = matcher.group(1);
        assertColumns(definition, "create_dept", "create_by", "create_time", "update_by", "update_time",
            "del_flag", "attr1", "attr2", "attr3", "attr4");
        assertColumnDefinition(definition, "tenant_id", "varchar(20) NOT NULL DEFAULT '000000'");
        assertTrue(Pattern.compile("(?m)^\\s*PRIMARY KEY\\s*\\(\\s*`id`\\s*\\)(?:\\s+USING\\s+BTREE)?\\s*,?\\s*$")
            .matcher(definition).find(), name + " 缺主键");
        return definition;
    }

    private static void assertColumnDefinition(String table, String name, String expected) {
        Matcher column = Pattern.compile("(?m)^\\s*`" + Pattern.quote(name) + "`\\s+([^\\r\\n]+)$").matcher(table);
        assertTrue(column.find(), "缺少列 `" + name + "`");
        String normalized = column.group(1).replaceFirst(",\\s*$", "")
            .replaceAll("(?i)\\s+COMMENT\\s+'(?:''|[^'])*'$", "")
            .replaceAll("(?i)\\s+CHARACTER SET utf8mb4\\s+COLLATE utf8mb4_[a-z0-9_]+", "")
            .replaceAll("(?i)\\bint\\(11\\)", "int");
        assertEquals(expected, normalized, name + " 应为 " + expected);
    }

    private static void assertIndex(String table, boolean unique, String name, String... columns) {
        Matcher index = Pattern.compile("(?m)^\\s*(UNIQUE\\s+)?(?:KEY|INDEX)\\s+`" + Pattern.quote(name)
            + "`\\s*\\(([^)]*)\\)(?:\\s+USING\\s+BTREE)?\\s*,?\\s*$").matcher(table);
        assertTrue(index.find(), "缺少索引 `" + name + "`");
        assertEquals(unique, index.group(1) != null, name + " 唯一性错误");
        assertEquals(java.util.Arrays.stream(columns).map(column -> "`" + column + "`").toList(),
            java.util.Arrays.stream(index.group(2).split(",")).map(String::trim).toList(),
            name + " 索引列或顺序错误");
        assertFalse(index.find(), name + " 索引名称重复");
    }

    private static void assertColumns(String definition, String... columns) {
        assertAll("column coverage", java.util.Arrays.stream(columns).map(column -> (Executable) () ->
            assertTrue(Pattern.compile("(?m)^  `" + column + "`\\s+").matcher(definition).find(), "缺少列 `" + column + "`")));
    }
}