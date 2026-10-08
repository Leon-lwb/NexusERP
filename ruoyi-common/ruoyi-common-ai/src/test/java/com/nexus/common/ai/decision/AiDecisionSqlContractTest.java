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
import java.util.function.Predicate;
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

    private static final List<String> MENU_COLUMNS = List.of("menu_id", "menu_name", "parent_id", "order_num",
        "path", "component", "query_param", "is_frame", "is_cache", "menu_type", "visible", "status", "perms",
        "icon", "create_dept", "create_by", "create_time", "update_by", "update_time", "remark");
    private static final Pattern MENU_INSERT = Pattern.compile(
        "(?m)^INSERT INTO `sys_menu`(?:\\s*\\(([^;()]*)\\))?\\s+VALUES\\s*");
    private static final Pattern MENU_ROW = Pattern.compile(
        "\\s*\\(((?:" + DecisionSqlTestSupport.SQL_LITERAL + "|[^'();])*)\\)\\s*([,;])");
    private static final Pattern MENU_VALUE = Pattern.compile(
        "\\s*(" + DecisionSqlTestSupport.SQL_LITERAL + "|NULL|-?\\d+)\\s*(?:,\\s*|$)");
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
    void menuContractsSupportLegacyAndDumpStylesAfterIdRemapping() {
        for (String source : List.of(menuFixture(false), menuFixture(true))) {
            assertMenuPermissions(source);
            assertMenuIds(source);
        }
    }

    @Test
    void wrongApprovalMenuParentMustBeRejected() {
        for (String source : menuSources()) {
            MenuRow approval = menuByPermission(source, "ai:decision:approve");
            String mutated = replaceMenuField(source, approval, "parent_id", "0");
            assertFalse(source.equals(mutated), "审批菜单父节点变异未生效");
            assertThrows(AssertionError.class, () -> assertMenuPermissions(mutated),
                "审批菜单挂错父节点不应通过 SQL 契约");
        }
    }

    @Test
    void trailingCommaInApprovalMenuMustBeRejected() {
        for (String source : menuSources()) {
            MenuRow approval = menuByPermission(source, "ai:decision:approve");
            String mutated = source.substring(0, approval.valuesEnd()) + "," + source.substring(approval.valuesEnd());
            assertFalse(source.equals(mutated));
            assertThrows(AssertionError.class, () -> assertMenuPermissions(mutated),
                "VALUES 末尾多余逗号不应通过 SQL 契约");
        }
    }

    @Test
    void missingAndDoubledTupleCommasInDumpMustBeRejected() {
        String source = menuFixture(true);
        MenuRow root = oneMenu(new ArrayList<>(menuRows(source).values()), "决策中心目录",
            row -> "decision".equals(row.path()));
        int comma = root.valuesEnd() + 1;
        assertEquals(',', source.charAt(comma), "fixture 必须有下一行用于逗号变异");
        for (String replacement : List.of("", ",,")) {
            String mutated = source.substring(0, comma) + replacement + source.substring(comma + 1);
            assertFalse(source.equals(mutated));
            assertThrows(AssertionError.class, () -> assertMenuPermissions(mutated));
        }
    }

    @Test
    void wrongMenuTypeMissingPermissionsAndDuplicateIdentitiesMustBeRejected() {
        for (String source : menuSources()) {
            MenuRow approval = menuByPermission(source, "ai:decision:approve");
            MenuRow execute = menuByPermission(source, "ai:decision:execute");
            for (String mutated : List.of(
                replaceMenuField(source, approval, "menu_type", "'C'"),
                replaceMenuField(source, approval, "perms", "NULL"),
                replaceMenuField(source, execute, "perms", "'ai:decision:approve'"),
                replaceMenuField(source, approval, "menu_id", execute.id()))) {
                assertFalse(source.equals(mutated), "菜单变异未生效");
                assertThrows(AssertionError.class, () -> assertMenuPermissions(mutated));
            }
        }
    }

    @Test
    void wrongRunVersionTypeMustBeRejected() {
        String run = tableDefinition("ai_decision_run");
        String changed = run.replaceFirst("(?m)^(\\s*`version`\\s+)int(?:\\(\\d+\\))? NOT NULL DEFAULT (?:0|'0')",
            "$1varchar(20) NULL DEFAULT NULL");
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
            .replaceAll("(?i)\\s+(?:CHARACTER SET utf8mb4\\s+)?COLLATE utf8mb4_[a-z0-9_]+", "")
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

    @Test
    void columnContractsAcceptOnlyEquivalentMysql57Syntax() {
        for (String metadata : List.of("", " COLLATE utf8mb4_unicode_ci",
            " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")) {
            assertColumnDefinition("  `tenant_id` varchar(20)" + metadata + " NOT NULL DEFAULT '000000',",
                "tenant_id", "varchar(20) NOT NULL DEFAULT '000000'");
        }
        for (String type : List.of("int", "int(11)", "int(4)")) {
            for (String value : List.of("0", "'0'")) {
                assertColumnDefinition("  `version` " + type + " NOT NULL DEFAULT " + value + ",",
                    "version", "int NOT NULL DEFAULT 0");
            }
            for (String suffix : List.of("NOT NULL DEFAULT 1", "NOT NULL DEFAULT '2'", "NULL DEFAULT 0",
                "NOT NULL DEFAULT NULL", "NOT NULL DEFAULT 0 DEFAULT 2")) {
                assertThrows(AssertionError.class, () -> assertColumnDefinition(
                    "  `version` " + type + " " + suffix + ",", "version", "int NOT NULL DEFAULT 0"));
            }
        }
        for (String wrongType : List.of("bigint(20)", "tinyint(4)", "varchar(20)")) {
            assertThrows(AssertionError.class, () -> assertColumnDefinition(
                "  `version` " + wrongType + " NOT NULL DEFAULT 0,", "version", "int NOT NULL DEFAULT 0"));
        }
        for (String metadata : List.of("", " COLLATE utf8mb4_unicode_ci",
            " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")) {
            for (String value : List.of("'000001'", "000000", "'0'", "NULL",
                "'000000 COLLATE utf8mb4_unicode_ci'", "'000000 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci'")) {
                assertThrows(AssertionError.class, () -> assertColumnDefinition(
                    "  `tenant_id` varchar(20)" + metadata + " NOT NULL DEFAULT " + value + ",",
                    "tenant_id", "varchar(20) NOT NULL DEFAULT '000000'"));
            }
        }
        assertThrows(AssertionError.class, () -> assertColumnDefinition(
            "  `version` int DEFAULT 0,\n  `version` int NOT NULL DEFAULT 0,", "version", "int NOT NULL DEFAULT 0"));
    }

    @Test
    void indexContractsRejectBadOrderUniquenessAndDuplicatesInBothStyles() {
        for (String kind : List.of("KEY", "INDEX")) {
            for (String suffix : List.of("", " USING BTREE")) {
                String ordered = "  " + kind + " `idx` (`run_id`,`sequence_no`)" + suffix + ",";
                assertIndex(ordered, false, "idx", "run_id", "sequence_no");
                String reordered = ordered.replace("`run_id`,`sequence_no`", "`sequence_no`,`run_id`");
                assertFalse(ordered.equals(reordered));
                assertThrows(AssertionError.class, () -> assertIndex(reordered, false, "idx", "run_id", "sequence_no"));
                assertThrows(AssertionError.class, () -> assertIndex("  UNIQUE " + ordered.strip(), false,
                    "idx", "run_id", "sequence_no"));
                assertThrows(AssertionError.class, () -> assertIndex(ordered, true, "idx", "run_id", "sequence_no"));
                assertThrows(AssertionError.class, () -> assertIndex(ordered + "\n" + ordered, false,
                    "idx", "run_id", "sequence_no"));
            }
        }
    }

    private static void assertMenuPermissions(String source) {
        Map<String, MenuRow> all = menuRows(source);
        List<MenuRow> rows = decisionMenus(all);
        assertEquals(10, rows.size(), "决策菜单业务标识必须完整且唯一");
        MenuRow ai = oneMenu(new ArrayList<>(all.values()), "AI 根目录",
            row -> "ai".equals(row.path()) && "0".equals(row.parentId()));
        MenuRow root = oneMenu(rows, "决策中心目录", row -> "decision".equals(row.path()));
        MenuRow board = oneMenu(rows, "交付风险看板", row -> "ai/decision/index".equals(row.component()));
        MenuRow detail = oneMenu(rows, "决策详情", row -> "ai/decision/detail".equals(row.component()));
        assertEquals("M", ai.type(), "AI 父目录类型错误");
        assertMenuRow(root, "工业决策中心", ai.id(), "M", "0", "decision", null, null);
        assertMenuRow(board, "交付风险看板", root.id(), "C", "0", "index", "ai/decision/index", "ai:decision:query");
        assertMenuRow(detail, "决策运行详情", root.id(), "C", "1", "detail", "ai/decision/detail", "ai:decision:query");
        for (String[] button : new String[][]{{"query", "决策查询"}, {"run", "发起决策"},
            {"approve", "审批决策"}, {"execute", "执行重试"}, {"export", "决策导出"},
            {"config", "决策配置"}, {"benchmark", "决策评测"}}) {
            String permission = "ai:decision:" + button[0];
            MenuRow row = oneMenu(rows, permission, candidate -> permission.equals(candidate.permission())
                && (!button[0].equals("query") || "".equals(candidate.component())));
            assertMenuRow(row, button[1], board.id(), "F", "0", "", "", permission);
        }
        Map<String, Long> permissions = rows.stream().filter(row -> row.permission() != null && !row.permission().isEmpty())
            .collect(java.util.stream.Collectors.groupingBy(MenuRow::permission, java.util.stream.Collectors.counting()));
        assertEquals(Map.of("ai:decision:query", 3L, "ai:decision:run", 1L, "ai:decision:approve", 1L,
            "ai:decision:execute", 1L, "ai:decision:export", 1L, "ai:decision:config", 1L,
            "ai:decision:benchmark", 1L), permissions, "决策权限必须完整且不得重复授权");
    }

    private static MenuRow oneMenu(List<MenuRow> rows, String identity, Predicate<MenuRow> selector) {
        List<MenuRow> matches = rows.stream().filter(selector).toList();
        assertEquals(1, matches.size(), "菜单业务标识必须唯一: " + identity);
        return matches.get(0);
    }

    private static void assertMenuRow(MenuRow row, String name, String parentId, String type,
                                      String visible, String path, String component, String permission) {
        assertAll("menu_id=" + row.id(),
            () -> assertEquals(name, row.name()),
            () -> assertEquals(parentId, row.parentId(), "父菜单错误"),
            () -> assertEquals(type, row.type(), "菜单类型错误"),
            () -> assertEquals(visible, row.visible()),
            () -> assertEquals(path, row.path()),
            () -> assertEquals(component, row.component()),
            () -> assertEquals(permission, row.permission()));
    }

    private static void assertMenuIds(String source) {
        // All IDs are checked globally; completeness is by business identity, not dump-assigned IDs.
        assertMenuPermissions(source);
    }

    private static List<MenuRow> decisionMenus(Map<String, MenuRow> rows) {
        return rows.values().stream().filter(row -> "decision".equals(row.path())
            || row.component() != null && row.component().startsWith("ai/decision/")
            || row.permission() != null && row.permission().startsWith("ai:decision:")).toList();
    }

    private static Map<String, MenuRow> menuRows(String source) {
        Matcher insert = MENU_INSERT.matcher(source);
        Map<String, MenuRow> rows = new LinkedHashMap<>();
        while (insert.find()) {
            List<String> columns = MENU_COLUMNS;
            if (insert.group(1) != null) {
                columns = java.util.Arrays.stream(insert.group(1).split(",", -1)).map(String::strip).map(column -> {
                    assertTrue(column.matches("`[a-z_]+`"), "sys_menu 列清单格式错误");
                    return column.substring(1, column.length() - 1);
                }).toList();
                assertEquals(MENU_COLUMNS.size(), columns.size(), "sys_menu 列数量错误");
                assertEquals(new HashSet<>(MENU_COLUMNS), new HashSet<>(columns), "sys_menu 列缺失或重复");
            }
            Matcher tuple = MENU_ROW.matcher(source);
            int offset = insert.end();
            do {
                tuple.region(offset, source.length());
                assertTrue(tuple.lookingAt(), "sys_menu VALUES 行或逗号格式错误，位置 " + offset);
                List<String> values = parseMenuValues(tuple.group(1));
                String id = menuId(menuValue(columns, values, "menu_id"));
                MenuRow row = new MenuRow(id, menuValue(columns, values, "menu_name"),
                    menuId(menuValue(columns, values, "parent_id")), menuValue(columns, values, "menu_type"),
                    menuValue(columns, values, "visible"), menuValue(columns, values, "path"),
                    menuValue(columns, values, "component"), menuValue(columns, values, "perms"),
                    columns, tuple.start(1), tuple.end(1));
                assertFalse(rows.containsKey(id), "sys_menu ID 重复: " + id);
                rows.put(id, row);
                offset = tuple.end();
            } while (tuple.group(2).equals(","));
        }
        assertFalse(rows.isEmpty(), "缺少 sys_menu INSERT");
        return rows;
    }

    private static List<String> parseMenuValues(String values) {
        assertFalse(values.stripTrailing().endsWith(","), "sys_menu VALUES 不允许尾随逗号");
        List<String> fields = new ArrayList<>();
        Matcher token = MENU_VALUE.matcher(values);
        int offset = 0;
        while (offset < values.length()) {
            token.region(offset, values.length());
            assertTrue(token.lookingAt(), "sys_menu VALUES 格式错误，位置 " + offset);
            fields.add(token.group(1));
            offset = token.end();
        }
        assertEquals(MENU_COLUMNS.size(), fields.size(), "sys_menu VALUES 字段数量异常");
        return fields;
    }

    private static String menuValue(List<String> columns, List<String> values, String column) {
        String value = values.get(columns.indexOf(column));
        return value.equals("NULL") ? null : value.startsWith("'")
            ? value.substring(1, value.length() - 1).replace("''", "'").replace("\\'", "'").replace("\\\\", "\\") : value;
    }

    private static String menuId(String value) {
        assertTrue(value != null && value.matches("\\d+"), "sys_menu ID 必须是非负整数");
        return new java.math.BigInteger(value).toString();
    }

    private static MenuRow menuByPermission(String source, String permission) {
        return oneMenu(new ArrayList<>(menuRows(source).values()), permission, row -> permission.equals(row.permission()));
    }

    private static String replaceMenuField(String source, MenuRow row, String column, String replacement) {
        List<String> values = parseMenuValues(source.substring(row.valuesStart(), row.valuesEnd()));
        values.set(row.columns().indexOf(column), replacement);
        return source.substring(0, row.valuesStart()) + String.join(", ", values) + source.substring(row.valuesEnd());
    }

    private static List<String> menuSources() {
        return List.of(sql, menuFixture(false), menuFixture(true));
    }

    private static String menuFixture(boolean dumpStyle) {
        Map<String, MenuRow> all = menuRows(sql);
        List<MenuRow> selected = new ArrayList<>();
        selected.add(oneMenu(new ArrayList<>(all.values()), "AI 根目录",
            row -> "ai".equals(row.path()) && "0".equals(row.parentId())));
        selected.addAll(decisionMenus(all));
        Map<String, String> ids = new LinkedHashMap<>();
        for (int i = 0; i < selected.size(); i++) {
            ids.put(selected.get(i).id(), Long.toString(900000000000000001L + i * 13));
        }
        List<String> columns = new ArrayList<>(MENU_COLUMNS);
        if (dumpStyle) {
            java.util.Collections.rotate(columns, 3); // Explicit dump columns need not be positional.
        }
        List<String> tuples = new ArrayList<>();
        for (MenuRow row : selected) {
            List<String> raw = parseMenuValues(sql.substring(row.valuesStart(), row.valuesEnd()));
            Map<String, String> fields = new LinkedHashMap<>();
            for (String column : MENU_COLUMNS) {
                fields.put(column, raw.get(row.columns().indexOf(column)));
            }
            fields.put("menu_id", dumpStyle ? "'" + ids.get(row.id()) + "'" : ids.get(row.id()));
            String parent = row.parentId().equals("0") ? "0" : ids.get(row.parentId());
            assertNotNull(parent, "菜单 fixture 缺少父节点");
            fields.put("parent_id", dumpStyle ? "'" + parent + "'" : parent);
            tuples.add("(" + String.join(", ", columns.stream().map(fields::get).toList()) + ")");
        }
        if (dumpStyle) {
            return "INSERT INTO `sys_menu` (" + String.join(", ", columns.stream().map(c -> "`" + c + "`").toList())
                + ") VALUES\n" + String.join(",\n", tuples) + ";\n";
        }
        return String.join("\n", tuples.stream().map(tuple -> "INSERT INTO `sys_menu` VALUES " + tuple + ";").toList());
    }

    private record MenuRow(String id, String name, String parentId, String type, String visible,
                           String path, String component, String permission, List<String> columns,
                           int valuesStart, int valuesEnd) { }

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
        assertEquals(expected, DecisionSqlTestSupport.columnDefinition(table, name), name + " 应为 " + expected);
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