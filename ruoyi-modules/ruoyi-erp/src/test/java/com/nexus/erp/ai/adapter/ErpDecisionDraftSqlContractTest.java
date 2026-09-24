package com.nexus.erp.ai.adapter;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 主初始化脚本中的 ERP 决策草稿结构契约测试；不连接或修改真实数据库。 */
@Tag("dev")
class ErpDecisionDraftSqlContractTest {

    @Test
    void purchaseRequisitionContainsStructuredIdempotencyVersionAndIndexes() throws IOException {
        String table = tableSql("pu_purchase_requisition");

        assertColumn(table, "decision_run_id", "bigint");
        assertColumn(table, "decision_action_id", "bigint");
        assertColumn(table, "decision_idempotency_key", "varchar\\(128\\)");
        assertColumn(table, "version", "int(?:\\(11\\))?\\s+not\\s+null\\s+default\\s+0");
        assertContains(table, "unique index `uk_pur_decision_idem`(`tenant_id`, `decision_idempotency_key`)");
        assertContains(table, "index `idx_pur_decision_run`(`tenant_id`, `decision_run_id`)");
    }

    @Test
    void schedulePlanContainsStructuredScopeVersionAndIndexes() throws IOException {
        String table = tableSql("prd_schedule_plan");

        assertColumn(table, "decision_run_id", "bigint");
        assertColumn(table, "decision_action_id", "bigint");
        assertColumn(table, "decision_idempotency_key", "varchar\\(128\\)");
        assertColumn(table, "decision_scenario_code", "varchar\\(64\\)");
        assertColumn(table, "decision_work_order_ids_json", "varchar\\(4000\\)");
        assertColumn(table, "version", "int(?:\\(11\\))?\\s+not\\s+null\\s+default\\s+0");
        assertContains(table, "unique index `uk_psp_decision_idem`(`tenant_id`, `decision_idempotency_key`)");
        assertContains(table, "index `idx_psp_decision_run`(`tenant_id`, `decision_run_id`)");
    }

    private String tableSql(String tableName) throws IOException {
        String sql = Files.readString(repoRoot().resolve("script/sql/ry-erp.sql"));
        Pattern pattern = Pattern.compile(
            "(?is)CREATE\\s+TABLE\\s+`" + Pattern.quote(tableName) + "`\\s*\\((.*?)\\)\\s*ENGINE");
        Matcher matcher = pattern.matcher(sql);
        assertTrue(matcher.find(), "未找到表定义: " + tableName);
        return matcher.group(1).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private Path repoRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("script/sql/ry-erp.sql"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("无法定位仓库根目录");
        }
        return current;
    }

    private void assertColumn(String table, String column, String typePattern) {
        assertTrue(Pattern.compile("`" + Pattern.quote(column) + "`\\s+" + typePattern,
            Pattern.CASE_INSENSITIVE).matcher(table).find(),
            () -> "缺少或定义错误的列 " + column + ": " + table);
    }

    private void assertContains(String table, String expected) {
        assertTrue(table.contains(expected), () -> "表定义缺少: " + expected + "；实际=" + table);
    }
}
