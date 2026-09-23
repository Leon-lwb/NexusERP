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
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Tag("local")
@Tag("dev")
@Tag("prod")
class AiDecisionSqlContractTest {

    private static final Pattern MENU_INSERT = Pattern.compile("(?m)^INSERT INTO `sys_menu` VALUES \\((\\d+),([^\\r\\n]*);$");
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
                    "result_json", "outcome_json", "summary_mode", "model_id", "model_name", "prompt_version",
                    "input_tokens", "output_tokens", "estimated_cost", "analysis_flow_instance_id",
                    "execution_flow_instance_id", "error_code", "error_message", "started_at", "analysis_finished_at",
                    "finished_at", "version");
                assertTrue(run.contains("KEY `idx_decision_tenant_scene_status` (`tenant_id`,`scene_code`,`status`)"));
                assertTrue(run.contains("KEY `idx_decision_biz` (`tenant_id`,`biz_type`,`biz_id`)"));
                assertTrue(run.contains("KEY `idx_decision_create_time` (`tenant_id`,`create_time`)"));
                assertTrue(run.contains("KEY `idx_decision_parent` (`parent_run_id`)"));
                assertTrue(run.contains("KEY `idx_decision_analysis_flow` (`analysis_flow_instance_id`)"));
                assertTrue(run.contains("KEY `idx_decision_execution_flow` (`execution_flow_instance_id`)"));
            },
            () -> {
                String evidence = tableDefinition("ai_decision_evidence");
                assertColumns(evidence, "id", "tenant_id", "run_id", "evidence_key", "source_domain", "source_type",
                    "source_id", "source_code", "source_name", "metric_code", "metric_name", "value_type",
                    "metric_value", "unit", "observed_at", "data_version", "confidence", "verifiable", "normative",
                    "derived_from_json", "display_path");
                assertTrue(evidence.contains("UNIQUE KEY `uk_decision_evidence` (`run_id`,`evidence_key`)"));
                assertTrue(evidence.contains("KEY `idx_decision_evidence_source` (`tenant_id`,`source_type`,`source_id`)"));
                assertTrue(evidence.contains("KEY `idx_decision_evidence_run` (`run_id`)"));
            },
            () -> {
                String action = tableDefinition("ai_decision_action");
                assertColumns(action, "id", "tenant_id", "run_id", "action_type", "title", "display_reason",
                    "target_biz_type", "target_biz_id", "target_biz_code", "payload_json", "risk_level",
                    "approval_required", "approval_status", "execution_status", "permission_code", "executor_code",
                    "sequence_no", "dependency_ids_json", "idempotency_key", "approved_by", "approved_at",
                    "approval_comment", "result_biz_type", "result_biz_id", "result_biz_code", "result_json",
                    "retry_count", "last_error_code", "last_error_message", "started_at", "finished_at", "version");
                assertTrue(action.contains("UNIQUE KEY `uk_decision_action_idempotency` (`tenant_id`,`idempotency_key`)"));
                assertTrue(action.contains("KEY `idx_decision_action_run` (`run_id`,`sequence_no`)"));
                assertTrue(action.contains("KEY `idx_decision_action_approval` (`tenant_id`,`approval_status`)"));
                assertTrue(action.contains("KEY `idx_decision_action_execution` (`tenant_id`,`execution_status`)"));
            });
    }

    @Test
    void decisionMenusAndPermissionsMustExist() {
        List<Executable> checks = new ArrayList<>();
        checks.add(() -> assertTrue(sql.contains("'工业决策中心'"), "缺少工业决策中心"));
        checks.add(() -> assertTrue(sql.contains("'ai/decision/index'"), "缺少决策看板路由"));
        checks.add(() -> assertTrue(sql.contains("'ai/decision/detail'"), "缺少决策详情路由"));
        for (String permission : new String[]{"query", "run", "approve", "execute", "export", "config", "benchmark"}) {
            checks.add(() -> assertTrue(sql.contains("'ai:decision:" + permission + "'"), "缺少权限 ai:decision:" + permission));
        }
        assertAll("decision menu permissions", checks);
    }

    @Test
    void decisionMenuIdsMustBeUniqueAndComplete() {
        Matcher matcher = MENU_INSERT.matcher(sql);
        Set<String> ids = new HashSet<>();
        Set<String> decisionIds = new HashSet<>();
        while (matcher.find()) {
            String id = matcher.group(1);
            assertTrue(ids.add(id), "sys_menu ID 重复: " + id);
            if (id.matches("185[0-9]")) {
                decisionIds.add(id);
            }
        }
        assertEquals(10, decisionIds.size(), "缺少决策菜单 ID 1850–1859");
        for (int id = 1850; id <= 1859; id++) {
            assertTrue(decisionIds.contains(Integer.toString(id)), "缺少决策菜单 ID " + id);
        }
    }

    private static String tableDefinition(String name) {
        Matcher matcher = Pattern.compile("(?s)CREATE TABLE `" + name + "`\\s*\\((.*?)\\) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4").matcher(sql);
        if (!matcher.find()) {
            return fail("缺少 CREATE TABLE `" + name + "` (InnoDB utf8mb4)");
        }
        String definition = matcher.group(1);
        assertColumns(definition, "create_dept", "create_by", "create_time", "update_by", "update_time",
            "del_flag", "attr1", "attr2", "attr3", "attr4");
        assertTrue(definition.contains("`tenant_id` varchar(20) NOT NULL DEFAULT '000000'"), name + " 租户类型/默认值错误");
        assertTrue(definition.contains("PRIMARY KEY (`id`)"), name + " 缺主键");
        return definition;
    }

    private static void assertColumns(String definition, String... columns) {
        assertAll("column coverage", java.util.Arrays.stream(columns).map(column -> (Executable) () ->
            assertTrue(Pattern.compile("(?m)^  `" + column + "`\\s+").matcher(definition).find(), "缺少列 `" + column + "`")));
    }
}