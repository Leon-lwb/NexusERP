package com.nexus.erp.ai.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Offline native-SQL contracts: no connection or database mutations. */
class ErpPurchaseStockLineageMapperSqlContractTest {
    private static final String PREFIX = "com.nexus.erp.";

    private static Class<?> verifier() {
        return assertDoesNotThrow(() -> Class.forName(PREFIX + "ai.adapter.ErpPurchaseStockLineageVerifier"),
            "Missing stock-lineage verifier: callers must not certify their own raw facts");
    }

    private static Class<?> mapper() {
        return assertDoesNotThrow(() -> Class.forName(PREFIX + "pu.mapper.ErpPurchaseStockLineageMapper"),
            "Missing current-read stock-lineage mapper");
    }

    @Test
    void onlyTenantAndMaterialAreAcceptedAndExistingPhysicalTransactionIsMandatory() throws Exception {
        Class<?> type = verifier();
        Method entry = type.getMethod("verifyLocked", String.class, Long.class);
        assertEquals("erp", type.getAnnotation(DS.class).value());
        assertEquals("erp", entry.getAnnotation(DS.class).value());
        assertEquals(Propagation.MANDATORY, entry.getAnnotation(Transactional.class).propagation());
        assertFalse(entry.getAnnotation(Transactional.class).readOnly());
        assertTrue(entry.getReturnType().isRecord());
        assertEquals(1, List.of(type.getMethods()).stream().filter(m -> m.getName().equals("verifyLocked")).count());
    }

    @Test
    void everyReadFlushesMapperLocalCachesAndDisablesSecondLevelCache() {
        for (Method method : mapper().getMethods()) {
            if (method.getAnnotation(Select.class) == null) continue;
            Options options = method.getAnnotation(Options.class);
            assertNotNull(options, method.getName());
            assertFalse(options.useCache(), method.getName());
            assertEquals(Options.FlushCachePolicy.TRUE, options.flushCache(), method.getName());
        }
    }

    private static String sql(String name) {
        Class<?> type = mapper();
        Configuration configuration = new Configuration();
        configuration.addMapper(type);
        return configuration.getMappedStatement(type.getName() + "." + name).getBoundSql(Map.of(
            "tenantId", "000000", "materialId", 140L, "stockIds", List.of(91L, 92L),
            "ids", List.of(501L, 502L))).getSql().replaceAll("\\s+", " ").trim().toUpperCase();
    }

    @Test
    void transactionReadCannotHideDeletedUnknownOrCrossMaterialStockReferences() {
        String query = sql("lockTransactions");
        assertTrue(query.contains("TENANT_ID = ? AND MATERIAL_ID = ?"), query);
        assertTrue(query.contains("OR MATERIAL_STOCK_ID IN"), query);
        assertTrue(query.endsWith("ORDER BY TX_ID ASC FOR UPDATE"), query);
        String where = query.substring(query.indexOf(" WHERE "));
        for (String forbidden : List.of("DEL_FLAG", "TX_TYPE", "TX_QUANTITY", "SUM(", "GROUP BY", "COALESCE(", "IFNULL(", "CAST(")) {
            assertFalse(where.contains(forbidden), forbidden + ": " + query);
        }
        assertTrue(query.contains("UNIX_TIMESTAMP(TX_DATE) * 1000"), query);
    }

    @Test
    void originalsAreLockedIndividuallyByStableIdsWithoutJoinOrBusinessStateFilters() {
        Map<String, String> idColumns = Map.of(
            "lockStocks", "STOCK_ID", "lockReceiveHeads", "RECEIVE_ID",
            "lockReceiveLines", "LINE_ID", "lockReceiveDetails", "DETAIL_ID",
            "lockIssueHeads", "ISSUE_ID", "lockIssueLines", "LINE_ID",
            "lockRequirementDetails", "DETAIL_ID", "lockRequirementHeads", "REQUIREMENT_ID",
            "lockWorkOrders", "WO_ID");
        for (var method : idColumns.entrySet()) {
            String query = sql(method.getKey());
            assertTrue(query.endsWith("ORDER BY " + method.getValue() + " ASC FOR UPDATE"), query);
            String where = query.substring(query.indexOf(" WHERE "));
            for (String forbidden : List.of(" JOIN ", "DEL_FLAG", "STATUS", "SUM(", "COALESCE(", "IFNULL(", "CAST(")) {
                assertFalse(where.contains(forbidden), forbidden + ": " + query);
            }
        }
    }

    @Test
    void schemaAndClockReadKeepNativeDecimalMetadataAndDatabaseEpoch() {
        String columns = sql("selectQuantityColumns");
        assertTrue(columns.contains("INFORMATION_SCHEMA.COLUMNS"));
        assertTrue(columns.contains("NUMERIC_PRECISION"));
        assertTrue(columns.contains("NUMERIC_SCALE"));
        assertFalse(columns.contains("CAST("));
        String clock = sql("selectDatabaseClock");
        assertTrue(clock.contains("UNIX_TIMESTAMP(NOW(6))"));
        assertTrue(clock.contains("@@SESSION.TIME_ZONE"));
    }
    @Test
    void pinsEveryOriginalDefinitionWithNoServerCacheBeforeCheckingActualEngines() {
        String pins = assertDoesNotThrow(() -> sql("pinTableDefinitions"), "Missing original-source definition pin");
        String engines = assertDoesNotThrow(() -> sql("selectTableEngines"), "Missing native source-engine metadata read");
        List<String> tables = List.of("BD_MATERIAL", "BD_UNIT_OF_MEASURE", "WM_MATERIAL_STOCK", "WM_WAREHOUSE",
            "WM_STORAGE_AREA", "WM_STORAGE_LOCATION", "WM_MATERIAL_STOCK_TX", "WM_MATERIAL_RECEIVE",
            "WM_MATERIAL_RECEIVE_LINE", "WM_MATERIAL_RECEIVE_DETAIL", "WM_PRODUCTION_ISSUE", "WM_PRODUCTION_ISSUE_LINE",
            "PRD_MATERIAL_REQUIREMENT", "PRD_MATERIAL_REQUIREMENT_DETAIL", "PRD_WORK_ORDER");
        assertTrue(pins.startsWith("SELECT SQL_NO_CACHE "), pins);
        assertEquals(1, pins.split("SQL_NO_CACHE", -1).length - 1);
        assertEquals(tables.size(), pins.split("WHERE 1=0", -1).length - 1);
        assertEquals(tables.size() - 1, pins.split("UNION ALL", -1).length - 1);
        assertFalse(pins.contains("?"), "Caller must never pick the source table set");
        for (String table : tables) {
            assertTrue(pins.contains(" FROM " + table + " WHERE 1=0"), table);
            assertTrue(engines.contains("'" + table + "'"), table);
        }
        assertTrue(engines.contains("ENGINE AS ENGINE"));
        assertTrue(engines.contains("INFORMATION_SCHEMA.TABLES"));
        assertTrue(engines.contains("TABLE_SCHEMA = DATABASE()"));
    }

}
