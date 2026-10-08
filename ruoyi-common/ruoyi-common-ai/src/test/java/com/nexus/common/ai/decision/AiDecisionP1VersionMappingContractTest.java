package com.nexus.common.ai.decision;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.ai.domain.entity.AiDecisionAction;
import com.nexus.common.ai.domain.entity.AiDecisionRun;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 仅基于源码的合同检查：历史记录保持 NULL；不连接数据库，也不执行迁移。 */
@Tag("dev")
class AiDecisionP1VersionMappingContractTest {

    @Test
    void decisionContractVersionIsAnExplicitNullableRunColumn() throws Exception {
        assertColumn(AiDecisionRun.class, "ai_decision_run", "decisionContractVersion",
            "decision_contract_version");
    }

    @Test
    void purchasePayloadVersionIsAnExplicitNullableActionColumn() throws Exception {
        assertColumn(AiDecisionAction.class, "ai_decision_action", "purchasePayloadVersion",
            "purchase_payload_version");
    }

    private static void assertColumn(Class<?> entity, String table, String fieldName, String column)
        throws Exception {
        assertEquals(table, entity.getAnnotation(TableName.class).value());
        String sql = tableDefinition(table);
        Matcher ddl = Pattern.compile("(?im)^\\s*`" + column
            + "`\\s+tinyint\\(4\\)\\s+NULL\\s+DEFAULT\\s+NULL").matcher(sql);
        assertTrue(ddl.find(), "P1 column must be nullable, without historical default: " + column);
        Field field = entity.getDeclaredField(fieldName);
        assertEquals(Integer.class, field.getType());
        assertNotNull(field.getAnnotation(TableField.class));
        assertEquals(column, field.getAnnotation(TableField.class).value());
        field.setAccessible(true);
        assertNull(field.get(entity.getDeclaredConstructor().newInstance()),
            "Absent persisted version denotes a legacy record, not v2");
        assertTrue(sql.contains("PRIMARY KEY (`id`)"));
        assertTrue(Pattern.compile("(?i)ENGINE\\s*=\\s*InnoDB").matcher(sql).find());
    }

    @Test
    void documentationCommentsDoNotCountAsExecutableStatements() {
        String sql = "/** 禁止执行 UPDATE、INSERT、DELETE、DROP。 */\n"
            + "-- 仅允许结构变更\nALTER TABLE `sample` ADD COLUMN `version` tinyint(4) NULL DEFAULT NULL;";
        assertEquals("ALTER TABLE `sample` ADD COLUMN `version` tinyint(4) NULL DEFAULT NULL;",
            sqlWithoutDocumentation(sql).strip());
    }

    @Test
    void executableStatementsRemainVisibleAfterDocumentationRemoval() {
        for (String operation : new String[]{"UPDATE", "INSERT", "DELETE", "DROP"}) {
            String sql = "/** 这段说明不执行。 */\n" + operation + " `sample`;";
            assertTrue(Pattern.compile("(?i)\\b(?:UPDATE|INSERT|DELETE|DROP)\\b")
                .matcher(sqlWithoutDocumentation(sql)).find());
        }
    }

    @Test
    void mysqlExecutableCommentsAndOptimizerHintsAreNotDocumentation() {
        String sql = "/*!50700 UPDATE `sample` SET `version` = 2 */;\n/*+ 提示 */ SELECT 1;";
        assertEquals(sql, sqlWithoutDocumentation(sql));
    }

    /** 仅用于固定 SQL 样本的静态检查；不作为通用 SQL 解析器或执行权限判定。 */
    private static String sqlWithoutDocumentation(String sql) {
        return sql.replaceAll("(?s)/\\*(?![!+]).*?\\*/", "")
            .replaceAll("(?m)^\\s*--[^\\r\\n]*", "");
    }

    private static String tableDefinition(String table) throws Exception {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path installation = dir.resolve("script/sql/ry-vue.sql");
            if (Files.isRegularFile(installation)) {
                Matcher ddl = Pattern.compile("(?is)CREATE\\s+TABLE\\s+`" + Pattern.quote(table)
                    + "`\\s*\\(.*?\\)\\s*ENGINE[^;]*;").matcher(Files.readString(installation));
                assertTrue(ddl.find(), "Missing installation DDL: " + table);
                return ddl.group();
            }
        }
        throw new IllegalStateException("Installation schema not found");
    }
}
