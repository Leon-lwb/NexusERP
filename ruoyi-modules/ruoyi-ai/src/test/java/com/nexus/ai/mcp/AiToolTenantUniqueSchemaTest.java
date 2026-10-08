package com.nexus.ai.mcp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** 从主安装SQL提取真实唯一列合同，再用独立H2验证租户内唯一而非全局占名。 */
class AiToolTenantUniqueSchemaTest {
    private Path repository() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.isRegularFile(path.resolve("script/sql/ry-vue.sql"))) path = path.getParent();
        assertNotNull(path);
        return path;
    }

    private String definition() throws Exception {
        String sql = Files.readString(repository().resolve("script/sql/ry-vue.sql"));
        int begin = sql.indexOf("CREATE TABLE `ai_tool`");
        assertTrue(begin >= 0);
        return sql.substring(begin, sql.indexOf(';', begin));
    }

    @Test
    @DisplayName("新装工具表的唯一性明确包含租户维度")
    void newInstallationHasTenantUniqueContract() throws Exception {
        assertTrue(Pattern.compile("UNIQUE INDEX `uk_tenant_tool_name`\\s*\\(\\s*`tenant_id`\\s*,\\s*`tool_name`\\s*\\)").matcher(definition()).find());
    }

    @Test
    @DisplayName("Fresh installation defaults tools to disabled without a global name constraint")
    void disabledDefaultAndTenantIdentityAreExplicit() throws Exception {
        String table = definition();
        assertTrue(Pattern.compile("`status`[^\\n]*DEFAULT '1'").matcher(table).find());
        assertTrue(Pattern.compile("`tenant_id`[^\\n]*DEFAULT '000000'").matcher(table).find());
        assertEquals(1, Pattern.compile("(?i)UNIQUE\\s+(?:INDEX|KEY)\\b").matcher(table).results().count(),
            "Only the tenant/name key may reserve tool names");
    }

    @ParameterizedTest
    @ValueSource(strings = {"000000", "001234", "factory_A"})
    @DisplayName("同名工具跨租户可各自持有，当前租户重复仍拒绝")
    void actualUniqueConstraintIsPerTenant(String otherTenant) throws Exception {
        var matcher = Pattern.compile("UNIQUE INDEX `[^`]+`\\s*\\(([^)]+)\\)").matcher(definition());
        assertTrue(matcher.find());
        String columns = matcher.group(1).replace("`", "");
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID());
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE ai_tool (id BIGINT PRIMARY KEY, tenant_id VARCHAR(20) NOT NULL, tool_name VARCHAR(128) NOT NULL, UNIQUE (" + columns + "))");
            statement.execute("INSERT INTO ai_tool VALUES (1, '0', 'erp_inventory')");
            assertDoesNotThrow(() -> statement.execute("INSERT INTO ai_tool VALUES (2, '" + otherTenant + "', 'erp_inventory')"), "历史0与当前文本租户不能互相占名");
            assertThrows(SQLException.class, () -> statement.execute("INSERT INTO ai_tool VALUES (3, '" + otherTenant + "', 'erp_inventory')"));
        }
    }
}
