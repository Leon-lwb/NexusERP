package com.nexus.ai.service.query;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexus.ai.config.AiProperties;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** 真正执行净化后的SQL；不能只因字符串含tenant_id就认定隔离成功。 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class SqlTenantIsolationResultTest {
    @ParameterizedTest(name = "隔离两租户真实数据：{0}")
    @ValueSource(strings = {
        "SELECT s.id FROM wm_material_stock s WHERE s.stock_quantity > 0 OR 1=1",
        "SELECT m.material_id AS id FROM wm_material_stock s CROSS JOIN bd_material m WHERE 'm.tenant_id' <> ''",
        "SELECT m.material_id AS id FROM wm_material_stock s, bd_material m WHERE 'm.tenant_id' <> ''",
        "SELECT s.id FROM wm_material_stock s WHERE EXISTS (SELECT 1 FROM bd_material m WHERE m.material_id = 2)",
        "SELECT m.material_id AS id FROM wm_material_stock s JOIN bd_material m ON m.material_id=s.material_id OR 1=1",
        "SELECT x.material_id AS id FROM wm_material_stock s INNER JOIN bd_material m ON m.material_id=s.material_id CROSS JOIN bd_material x WHERE s.stock_quantity > 0 OR x.material_id = 1",
        "SELECT x.material_id AS id FROM wm_material_stock s INNER JOIN bd_material m ON m.material_id=s.material_id CROSS JOIN bd_material x WHERE m.material_id = 1 OR 1=1"
    })
    void actualRowsNeverExposeOtherTenant(String original) throws Exception {
        withDatabase(jdbc -> {
            var validator = new SqlSafetyValidator(mock(ErpQueryService.class), new AiProperties());
            String safe = validator.validateAndSanitize(original, 20, "000000");
            List<Long> rows = jdbc.queryForList(safe, Long.class);
            assertFalse(rows.contains(2L), safe);
            if (original.contains("EXISTS")) assertTrue(rows.isEmpty(), safe);
            else assertFalse(rows.isEmpty(), safe);
        });
    }

    @Test
    void scalarSubqueryDoesNotCountOtherTenant() throws Exception {
        withDatabase(jdbc -> {
            var validator = new SqlSafetyValidator(mock(ErpQueryService.class), new AiProperties());
            String safe = validator.validateAndSanitize(
                "SELECT (SELECT COUNT(*) FROM bd_material m) AS qty FROM wm_material_stock s", 20, "000000");
            assertEquals(List.of(1L), jdbc.queryForList(safe, Long.class), safe);
        });
    }

    @Test
    void leftJoinPreservesMainRowsWithoutMatchingMaterial() throws Exception {
        withDatabase(jdbc -> {
            jdbc.update("UPDATE wm_material_stock SET material_id=99 WHERE id=1");
            var validator = new SqlSafetyValidator(mock(ErpQueryService.class), new AiProperties());
            String safe = validator.validateAndSanitize(
                "SELECT s.id FROM wm_material_stock s LEFT JOIN bd_material m ON s.material_id=m.material_id", 20, "000000");
            assertEquals(List.of(1L), jdbc.queryForList(safe, Long.class), safe);
        });
    }

    private void withDatabase(Consumer<JdbcTemplate> assertion) throws Exception {
        var datasource = new DriverManagerDataSource(
            "jdbc:h2:mem:sql_isolation_" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        try (var keepAlive = datasource.getConnection()) {
            var jdbc = new JdbcTemplate(datasource);
            jdbc.execute("CREATE TABLE wm_material_stock(id BIGINT,tenant_id VARCHAR(20),material_id BIGINT,stock_quantity INT)");
            jdbc.execute("CREATE TABLE bd_material(material_id BIGINT,tenant_id VARCHAR(20))");
            jdbc.update("INSERT INTO wm_material_stock VALUES (1,'000000',1,1),(2,'000001',2,1)");
            jdbc.update("INSERT INTO bd_material VALUES (1,'000000'),(2,'000001')");
            assertion.accept(jdbc);
            jdbc.execute("DROP ALL OBJECTS");
        }
    }
}
