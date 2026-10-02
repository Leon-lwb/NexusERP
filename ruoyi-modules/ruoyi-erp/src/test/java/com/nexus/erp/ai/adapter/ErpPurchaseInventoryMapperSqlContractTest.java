package com.nexus.erp.ai.adapter;

import com.nexus.erp.bd.domain.BdUnitOfMeasure;
import com.nexus.erp.pu.mapper.ErpPurchaseInventorySourceMapper;
import com.nexus.erp.pu.mapper.ErpPurchaseInventorySourceMapper.DatabaseClock;
import com.nexus.erp.pu.mapper.ErpPurchaseInventorySourceMapper.QuantityColumn;
import com.nexus.erp.wm.domain.WmMaterialStock;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.resultset.DefaultResultSetHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.BooleanTypeHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 使用真实 MyBatis 注解解析、动态参数绑定及结果处理器验证 SQL 合同。
 * 结果集为离线 JDBC 夹具，不执行真实数据库、不宣称现场锁顺序或并发已验收。
 */
@Tag("dev")
class ErpPurchaseInventoryMapperSqlContractTest {
    private static final String PREFIX = ErpPurchaseInventorySourceMapper.class.getName() + ".";

    @ParameterizedTest
    @MethodSource("lockQueries")
    void locksWithoutJoinsAggregationBusinessFiltersOrUnknownDeleteLoss(String method, String table, String id) {
        Configuration configuration = configuration();
        BoundSql bound = statement(configuration, method).getBoundSql(parameters());
        String sql = normalized(bound);
        assertTrue(sql.contains("from " + table), sql);
        assertTrue(sql.endsWith("order by " + id + " for update"), sql);
        String where = sql.substring(sql.indexOf("where"));
        assertTrue(where.contains("tenant_id = ?"), sql);
        assertTrue(where.contains("(del_flag is null or del_flag <> '1')"), sql);
        for (String forbidden : List.of(" join ", " sum(", " coalesce(", " ifnull(", " cast(", " limit ", " group by ",
            "status =", "enable_flag =", "frozen_flag =", "location_flag =", "stock_quantity >", "reserved_quantity >",
            "expire_date >", "del_flag = '0'")) {
            assertFalse(sql.contains(forbidden), forbidden + ": " + sql);
        }
        assertTrue(bound.getParameterMappings().stream().anyMatch(p -> p.getProperty().equals("tenantId")));
        assertFalse(sql.contains("inventory-test"));
    }

    private static Stream<Arguments> lockQueries() {
        return Stream.of(Arguments.of("lockMaterial", "bd_material", "material_id"),
            Arguments.of("lockUnit", "bd_unit_of_measure", "uom_id"),
            Arguments.of("lockStocks", "wm_material_stock", "stock_id"),
            Arguments.of("lockWarehouses", "wm_warehouse", "warehouse_id"),
            Arguments.of("lockAreas", "wm_storage_area", "area_id"),
            Arguments.of("lockLocations", "wm_storage_location", "location_id"));
    }

    @Test
    void clockAndActualSchemaAreReadFromCurrentConnectionWithoutChangingConfiguration() {
        Configuration configuration = configuration();
        String clock = normalized(statement(configuration, "selectDatabaseClock").getBoundSql(null));
        assertTrue(clock.contains("unix_timestamp(now(6)) as epochseconds"));
        assertTrue(clock.contains("@@session.time_zone as databasetimezone"));
        String columns = normalized(statement(configuration, "selectQuantityColumns").getBoundSql(null));
        assertTrue(columns.contains("from information_schema.columns"));
        assertTrue(columns.contains("table_schema = database()"));
        assertTrue(columns.contains("numeric_precision as numericprecision"));
        assertTrue(columns.contains("numeric_scale as numericscale"));
        assertFalse(columns.contains("data_type ="), "不预先隐藏 DOUBLE 或未知类型");
        for (String sql : List.of(clock, columns)) {
            assertFalse(sql.contains("update ")); assertFalse(sql.contains("insert ")); assertFalse(sql.contains("set "));
        }
    }

    @Test
    void schemaQueryCannotReceiveBusinessTenantColumnAndMapperIsDiscoverable() throws NoSuchMethodException {
        assertNotNull(ErpPurchaseInventorySourceMapper.class.getAnnotation(org.apache.ibatis.annotations.Mapper.class));
        var ignore = ErpPurchaseInventorySourceMapper.class.getMethod("selectQuantityColumns")
            .getAnnotation(com.baomidou.mybatisplus.annotation.InterceptorIgnore.class);
        assertNotNull(ignore, "实际元数据表没有 tenant_id，必须仅对此无业务行查询忽略租户插件");
        assertEquals("true", ignore.tenantLine());
    }

    @Test
    void realResultHandlerPreservesDecimalNullAndUnknownNumericFlags() throws SQLException {
        Configuration configuration = configuration();
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("stockId", 91L); values.put("stockQuantity", new BigDecimal("12345678901234.5678"));
        values.put("reservedQuantity", null); values.put("baseUomId", "027");
        values.put("frozenFlag", 2L); values.put("version", -1);
        WmMaterialStock stock = (WmMaterialStock) mapped(configuration, "lockStocks", values);
        assertEquals(new BigDecimal("12345678901234.5678"), stock.getStockQuantity());
        assertNull(stock.getReservedQuantity()); assertEquals("027", stock.getBaseUomId());
        assertEquals(2L, stock.getFrozenFlag()); assertEquals(-1, stock.getVersion());
        assertInstanceOf(BooleanTypeHandler.class, configuration.getTypeHandlerRegistry().getTypeHandler(Boolean.class));
    }

    /** 实际实体无参构造与真实产品映射同时核验，覆盖默认不调用 NULL setter 的行为。 */
    @ParameterizedTest(name = "库存实际 SQL NULL，callSettersOnNulls={0}")
    @ValueSource(booleans = {false, true})
    void actualStockSqlNullNeverBecomesInitializedZeroVersionOrQuantity(boolean callSettersOnNulls)
        throws SQLException {
        Configuration configuration = nullMappingConfiguration(callSettersOnNulls);
        WmMaterialStock initial = new WmMaterialStock();
        assertNull(initial.getVersion(), "实际无参构造不能预置可信版本 0");
        assertNull(initial.getStockQuantity()); assertNull(initial.getReservedQuantity());
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("stockId", 91L);
        for (String column : List.of("tenantId", "materialId", "baseUomId", "warehouseId", "areaId", "locationId",
            "stockQuantity", "reservedQuantity", "frozenFlag", "version", "delFlag",
            "productionDateEpochMillis", "recptDateEpochMillis", "expireDateEpochMillis")) values.put(column, null);
        WmMaterialStock actual = (WmMaterialStock) mapped(configuration, "lockStocks", values);
        assertEquals(91L, actual.getStockId());
        assertAll(
            () -> assertNull(actual.getTenantId()), () -> assertNull(actual.getMaterialId()),
            () -> assertNull(actual.getBaseUomId()), () -> assertNull(actual.getWarehouseId()),
            () -> assertNull(actual.getAreaId()), () -> assertNull(actual.getLocationId()),
            () -> assertNull(actual.getStockQuantity()), () -> assertNull(actual.getReservedQuantity()),
            () -> assertNull(actual.getFrozenFlag()), () -> assertNull(actual.getVersion()),
            () -> assertNull(actual.getDelFlag()), () -> assertNull(actual.getProductionDate()),
            () -> assertNull(actual.getRecptDate()), () -> assertNull(actual.getExpireDate()));
    }

    @ParameterizedTest(name = "物料实际 SQL NULL，callSettersOnNulls={0}")
    @ValueSource(booleans = {false, true})
    void actualMaterialSqlNullCannotBecomeKnownBaseUnitSafetyOrStatus(boolean callSettersOnNulls)
        throws SQLException {
        Configuration configuration = nullMappingConfiguration(callSettersOnNulls);
        var initial = new com.nexus.erp.bd.domain.BdMaterial();
        assertNull(initial.getBaseUomId()); assertNull(initial.getSafetyStock()); assertNull(initial.getStatus());
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("materialId", 139L);
        for (String column : List.of("tenantId", "baseUomId", "safetyStock", "status", "delFlag")) values.put(column, null);
        var actual = (com.nexus.erp.bd.domain.BdMaterial) mapped(configuration, "lockMaterial", values);
        assertEquals(139L, actual.getMaterialId());
        assertAll(() -> assertNull(actual.getTenantId()), () -> assertNull(actual.getBaseUomId()),
            () -> assertNull(actual.getSafetyStock()), () -> assertNull(actual.getStatus()),
            () -> assertNull(actual.getDelFlag()));
    }

    @ParameterizedTest(name = "单位实际 SQL NULL，callSettersOnNulls={0}")
    @ValueSource(booleans = {false, true})
    void actualUnitSqlNullCannotBecomeKnownPrecisionRateVersionOrVerificationDate(boolean callSettersOnNulls)
        throws SQLException {
        Configuration configuration = nullMappingConfiguration(callSettersOnNulls);
        BdUnitOfMeasure initial = new BdUnitOfMeasure();
        assertNull(initial.getQuantityScale()); assertNull(initial.getConversionRate());
        assertNull(initial.getConversionVersion()); assertNull(initial.getConversionVerifiedAt());
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("uomId", 27L);
        for (String column : List.of("tenantId", "category", "isBase", "primaryId", "conversionRate", "quantityScale",
            "conversionVersion", "conversionVerifiedAtEpochMillis", "status", "delFlag")) values.put(column, null);
        BdUnitOfMeasure actual = (BdUnitOfMeasure) mapped(configuration, "lockUnit", values);
        assertEquals(27L, actual.getUomId());
        assertAll(() -> assertNull(actual.getTenantId()), () -> assertNull(actual.getCategory()),
            () -> assertNull(actual.getIsBase()), () -> assertNull(actual.getPrimaryId()),
            () -> assertNull(actual.getConversionRate()), () -> assertNull(actual.getQuantityScale()),
            () -> assertNull(actual.getConversionVersion()), () -> assertNull(actual.getConversionVerifiedAt()),
            () -> assertNull(actual.getStatus()), () -> assertNull(actual.getDelFlag()));
    }

    /** 正向对照：SQL 明确零必须保留为零，不能用一律返回 NULL 的映射伪造反例通过。 */
    @ParameterizedTest(name = "实际 SQL 零与 NULL 区分，callSettersOnNulls={0}")
    @ValueSource(booleans = {false, true})
    void actualExplicitSqlZeroIsRetainedRatherThanBlanketReplacedWithNull(boolean callSettersOnNulls)
        throws SQLException {
        Configuration configuration = nullMappingConfiguration(callSettersOnNulls);
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("stockId", 91L); values.put("version", 0); values.put("frozenFlag", 0L);
        values.put("stockQuantity", new BigDecimal("0.0000")); values.put("reservedQuantity", new BigDecimal("0.0000"));
        WmMaterialStock stock = (WmMaterialStock) mapped(configuration, "lockStocks", values);
        assertEquals(0, stock.getVersion()); assertEquals(0L, stock.getFrozenFlag());
        assertEquals(new BigDecimal("0.0000"), stock.getStockQuantity());
        assertEquals(new BigDecimal("0.0000"), stock.getReservedQuantity());
        values.clear(); values.put("uomId", 27L); values.put("quantityScale", 0);
        BdUnitOfMeasure unit = (BdUnitOfMeasure) mapped(configuration, "lockUnit", values);
        assertEquals(0, unit.getQuantityScale());
        values.clear(); values.put("materialId", 139L); values.put("safetyStock", new BigDecimal("0.0000"));
        var material = (com.nexus.erp.bd.domain.BdMaterial) mapped(configuration, "lockMaterial", values);
        assertEquals(new BigDecimal("0.0000"), material.getSafetyStock());
    }

    /** 使用项目实际的 MyBatis-Plus 配置类；不要求通过修改全局配置保留未知值。 */
    private static Configuration nullMappingConfiguration(boolean callSettersOnNulls) {
        Configuration configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        assertFalse(configuration.isCallSettersOnNulls(), "实际默认开关为 false");
        configuration.setCallSettersOnNulls(callSettersOnNulls);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(ErpPurchaseInventorySourceMapper.class);
        return configuration;
    }

    @Test
    void realResultHandlerReadsDatabaseClockAndUnfilteredColumnPrecisionRecords() throws SQLException {
        Configuration configuration = configuration();
        LinkedHashMap<String, Object> clockValues = new LinkedHashMap<>();
        clockValues.put("epochSeconds", new BigDecimal("1790906584.123456"));
        clockValues.put("databaseTimeZone", "+08:00");
        DatabaseClock clock = (DatabaseClock) mapped(configuration, "selectDatabaseClock", clockValues);
        assertEquals(new BigDecimal("1790906584.123456"), clock.epochSeconds());
        assertEquals("+08:00", clock.databaseTimeZone());
        LinkedHashMap<String, Object> columns = new LinkedHashMap<>();
        columns.put("tableName", "wm_material_stock"); columns.put("columnName", "stock_quantity");
        columns.put("dataType", "double"); columns.put("numericPrecision", 12); columns.put("numericScale", 2);
        QuantityColumn column = (QuantityColumn) mapped(configuration, "selectQuantityColumns", columns);
        assertEquals("double", column.dataType()); assertEquals(12, column.numericPrecision());
        assertEquals(2, column.numericScale());
    }

    @ParameterizedTest
    @MethodSource("dateColumns")
    void registeredDateMappingPreservesNullAndExactEpochMillis(String method, String property, String alias)
        throws ReflectiveOperationException, SQLException {
        Configuration configuration = configuration();
        var mapping = statement(configuration, method).getResultMaps().get(0).getResultMappings().stream()
            .filter(m -> property.equals(m.getProperty())).findFirst().orElseThrow();
        assertEquals(alias, mapping.getColumn());
        assertEquals(ErpPurchaseInventorySourceMapper.EpochMillisDateTypeHandler.class, mapping.getTypeHandler().getClass());
        var getter = statement(configuration, method).getResultMaps().get(0).getType()
            .getMethod("get" + Character.toUpperCase(property.charAt(0)) + property.substring(1));
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put(method.equals("lockUnit") ? "uomId" : "stockId", 91L); values.put(alias, null);
        assertNull(getter.invoke(mapped(configuration, method, values)));
        values.put(alias, new BigDecimal("1790900000123.000"));
        assertEquals(new Date(1790900000123L), getter.invoke(mapped(configuration, method, values)));
    }

    private static Stream<Arguments> dateColumns() {
        return Stream.of(Arguments.of("lockUnit", "conversionVerifiedAt", "conversionVerifiedAtEpochMillis"),
            Arguments.of("lockStocks", "productionDate", "productionDateEpochMillis"),
            Arguments.of("lockStocks", "recptDate", "recptDateEpochMillis"),
            Arguments.of("lockStocks", "expireDate", "expireDateEpochMillis"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1790900000123.5", "9223372036854775808"})
    void invalidDateDoesNotBecomeKnownZeroOrTruncatedDate(String raw) throws SQLException {
        Configuration configuration = configuration();
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("stockId", 91L); values.put("expireDateEpochMillis", new BigDecimal(raw));
        var failure = assertThrows(org.apache.ibatis.executor.result.ResultMapException.class,
            () -> mapped(configuration, "lockStocks", values));
        assertInstanceOf(SQLException.class, failure.getCause());
    }

    /** 真实 SqlSession 与执行器的同一 SESSION 缓存，不以两次 Mockito Mapper 回答替代 JDBC 查询。 */
    @Test
    void sameSessionClockRefreshMustExecuteJdbcTwiceAndReturnLaterTime() throws SQLException {
        JdbcSourceFixture f = new JdbcSourceFixture();
        try (var session = f.rawSession()) {
            assertEquals(org.apache.ibatis.session.LocalCacheScope.SESSION, session.getConfiguration().getLocalCacheScope());
            var mapper = session.getMapper(ErpPurchaseInventorySourceMapper.class);
            DatabaseClock first = mapper.selectDatabaseClock();
            f.databaseNow = JdbcSourceFixture.NOW.plusSeconds(2);
            DatabaseClock second = mapper.selectDatabaseClock();
            assertEquals(2, f.count("selectDatabaseClock"), "相同 SESSION 的后锁时钟必须真实执行第二次 JDBC 查询");
            assertNotEquals(first.epochSeconds(), second.epochSeconds());
            assertEquals(JdbcSourceFixture.epoch(f.databaseNow), second.epochSeconds());
        }
    }

    /** 框架反例对照：只关闭二级缓存，仍然不能绕过 SESSION 一级缓存。 */
    @Test
    void useCacheFalseWithoutFlushStillReusesSessionClockAndIsNotSufficientFix() throws SQLException {
        JdbcSourceFixture f = new JdbcSourceFixture();
        MappedStatement original = statement(f.configuration, "selectDatabaseClock");
        String id = PREFIX + "clockWithoutFlushControl";
        f.configuration.addMappedStatement(new MappedStatement.Builder(f.configuration, id, original.getSqlSource(),
            org.apache.ibatis.mapping.SqlCommandType.SELECT).resultMaps(original.getResultMaps())
            .useCache(false).flushCacheRequired(false).build());
        try (var session = f.rawSession()) {
            DatabaseClock first = session.selectOne(id);
            f.databaseNow = JdbcSourceFixture.NOW.plusSeconds(2);
            DatabaseClock second = session.selectOne(id);
            assertEquals(1, f.count("selectDatabaseClock"));
            assertEquals(first.epochSeconds(), second.epochSeconds(), "该控制语句应稳定复现一级缓存旧时钟");
        }
    }

    @ParameterizedTest(name = "实际 SESSION 重复当前读必须再次执行 JDBC：{0}")
    @ValueSource(strings = {"selectDatabaseClock", "selectQuantityColumns", "lockMaterial", "lockUnit", "lockStocks",
        "lockWarehouses", "lockAreas", "lockLocations"})
    void everyCurrentReadInSameSessionExecutesJdbcAgainInsteadOfCachedFacts(String method) throws SQLException {
        JdbcSourceFixture f = new JdbcSourceFixture();
        try (var session = f.rawSession()) {
            var mapper = session.getMapper(ErpPurchaseInventorySourceMapper.class);
            f.invoke(mapper, method);
            f.invoke(mapper, method);
            assertEquals(2, f.count(method), "当前读与行锁不能由 SESSION 缓存代答：" + method);
            MappedStatement mapped = statement(f.configuration, method);
            assertTrue(mapped.isFlushCacheRequired(), "需在查询前清理一级缓存");
            assertFalse(mapped.isUseCache(), "不将当前读交给二级缓存");
            assertEquals(org.apache.ibatis.session.LocalCacheScope.SESSION, f.configuration.getLocalCacheScope(),
                "产品修复不得改全局缓存范围");
        }
    }

    /** 真实专属 RR 入口、SqlSessionTemplate 与 JDBC 行；锁等待使库存过期时必须拒绝。 */
    @Test
    void expiryDuringStockLockIsRejectedUsingFreshClockOnSameOwnedSession() throws SQLException {
        JdbcSourceFixture f = new JdbcSourceFixture();
        f.advanceDuringStockLock = true;
        f.expiresAt = JdbcSourceFixture.NOW.plusSeconds(1);
        AtomicReference<com.nexus.common.core.exception.ServiceException> original = new AtomicReference<>();
        assertThrows(com.nexus.common.core.exception.ServiceException.class, () -> f.inOwnedTransaction(() -> {
            try {
                return f.verifier().verifyLocked(JdbcSourceFixture.TENANT, JdbcSourceFixture.MATERIAL);
            } catch (com.nexus.common.core.exception.ServiceException rejected) {
                original.set(rejected);
                throw rejected;
            }
        }));
        assertNotNull(original.get(), "必须由真实库存认证器拒绝，不把入口/基础设施错误当成过期证明；回调进入="
            + f.callbackEntered + "，内部失败=" + f.callbackFailure);
        assertTrue(original.get().getMessage().contains("过期"), original.get().getMessage());
        assertEquals(2, f.count("selectDatabaseClock"), "后锁时钟必须到实际 JDBC，而非缓存");
        assertEquals(1, f.observedSessions.size(), "两次时钟与所有来源在同一个真实 SqlSession");
        verify(f.connection).rollback();
        verify(f.connection, never()).commit();
    }

    /** 连续两次认证仍在同一专属会话中，变化的库存/版本必须重新读取而不是旧对象。 */
    @Test
    void secondVerificationOnSameOwnedSessionReadsChangedStockVersionAndQuantity() throws SQLException {
        JdbcSourceFixture f = new JdbcSourceFixture();
        var results = f.inOwnedTransaction(() -> {
            var reader = f.verifier();
            var first = reader.verifyLocked(JdbcSourceFixture.TENANT, JdbcSourceFixture.MATERIAL);
            f.stockQuantity = new BigDecimal("99.0000");
            f.stockVersion = 3;
            f.databaseNow = JdbcSourceFixture.NOW.plusSeconds(2);
            var second = reader.verifyLocked(JdbcSourceFixture.TENANT, JdbcSourceFixture.MATERIAL);
            return List.of(first, second);
        });
        assertEquals(new BigDecimal("12.5000"), results.get(0).quantity());
        assertEquals(new BigDecimal("99.0000"), results.get(1).quantity());
        assertTrue(results.get(1).sourceVersions().get("wm_material_stock:91").contains("version=3"));
        assertEquals(JdbcSourceFixture.NOW.plusSeconds(2), results.get(1).snapshotAt());
        assertEquals(4, f.count("selectDatabaseClock"));
        for (String method : List.of("selectQuantityColumns", "lockMaterial", "lockUnit", "lockStocks",
            "lockWarehouses", "lockAreas", "lockLocations")) assertEquals(2, f.count(method), method);
        assertEquals(1, f.observedSessions.size());
    }

    /** 只模拟驱动结果，不模拟 Mapper、SqlSession、执行器、事务资格或专属入口。 */
    private static final class JdbcSourceFixture {
        static final String TENANT = "jdbc-cache-tenant";
        static final Long MATERIAL = 139L;
        static final java.time.Instant NOW = java.time.Instant.parse("2026-10-02T02:03:04.123456Z");
        final Connection connection;
        final com.baomidou.dynamic.datasource.DynamicRoutingDataSource routing;
        final Configuration configuration;
        final org.apache.ibatis.session.SqlSessionFactory factory;
        final Map<String, Integer> jdbcCounts = new LinkedHashMap<>();
        final java.util.Set<org.apache.ibatis.session.SqlSession> observedSessions = java.util.Collections
            .newSetFromMap(new java.util.IdentityHashMap<>());
        java.time.Instant databaseNow = NOW;
        java.time.Instant expiresAt = NOW.plusSeconds(3600);
        boolean advanceDuringStockLock;
        boolean callbackEntered;
        Throwable callbackFailure;
        BigDecimal stockQuantity = new BigDecimal("12.5000");
        int stockVersion = 2;

        JdbcSourceFixture() throws SQLException {
            connection = mock(Connection.class);
            AtomicBoolean autoCommit = new AtomicBoolean(true);
            AtomicBoolean readOnly = new AtomicBoolean(false);
            AtomicInteger isolation = new AtomicInteger(Connection.TRANSACTION_READ_COMMITTED);
            when(connection.getAutoCommit()).thenAnswer(i -> autoCommit.get());
            when(connection.isReadOnly()).thenAnswer(i -> readOnly.get());
            when(connection.getTransactionIsolation()).thenAnswer(i -> isolation.get());
            when(connection.isClosed()).thenReturn(false);
            doAnswer(i -> { autoCommit.set(i.getArgument(0)); return null; }).when(connection).setAutoCommit(anyBoolean());
            doAnswer(i -> { readOnly.set(i.getArgument(0)); return null; }).when(connection).setReadOnly(anyBoolean());
            doAnswer(i -> { isolation.set(i.getArgument(0)); return null; }).when(connection).setTransactionIsolation(anyInt());
            when(connection.getMetaData()).thenReturn(mock(DatabaseMetaData.class));
            var erp = mock(com.zaxxer.hikari.HikariDataSource.class);
            var master = mock(com.zaxxer.hikari.HikariDataSource.class);
            when(erp.getConnection()).thenReturn(connection);
            routing = new com.baomidou.dynamic.datasource.DynamicRoutingDataSource(List.of());
            routing.setPrimary("master"); routing.setStrict(true);
            routing.addDataSource("master", master); routing.addDataSource("erp", erp);
            configuration = new Configuration(new org.apache.ibatis.mapping.Environment("离线真实执行器",
                new org.mybatis.spring.transaction.SpringManagedTransactionFactory(), routing));
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(ErpPurchaseInventorySourceMapper.class);
            configuration.addMapper(com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.class);
            configuration.addMapper(com.nexus.erp.pu.mapper.PuRequirementMaterialGuardMapper.class);
            factory = new org.apache.ibatis.session.defaults.DefaultSqlSessionFactory(configuration);
            when(connection.prepareStatement(anyString())).thenAnswer(i -> prepared(i.getArgument(0)));
        }

        org.apache.ibatis.session.SqlSession rawSession() {
            configuration.setEnvironment(new org.apache.ibatis.mapping.Environment("离线会话缓存对照",
                new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(), routing));
            return factory.openSession(org.apache.ibatis.session.ExecutorType.SIMPLE, connection);
        }

        ErpPurchaseInventorySourceVerifier verifier() {
            var template = new org.mybatis.spring.SqlSessionTemplate(factory);
            return new ErpPurchaseInventorySourceVerifier(template.getMapper(ErpPurchaseInventorySourceMapper.class),
                new com.nexus.erp.pu.service.PuRequirementErpTransactionQualification(factory));
        }

        <T> T inOwnedTransaction(Supplier<T> callback) throws SQLException {
            // 只读夹具显式回滚；结果仅用于事务外断言，绝不将回滚后的快照当采购授权。
            RuntimeException rollbackMarker = new RuntimeException("离线来源夹具完成后主动回滚");
            AtomicReference<T> observed = new AtomicReference<>();
            try (var tenant = mockStatic(com.nexus.common.tenant.helper.TenantHelper.class)) {
                tenant.when(com.nexus.common.tenant.helper.TenantHelper::isEnable).thenReturn(true);
                tenant.when(com.nexus.common.tenant.helper.TenantHelper::getTenantId).thenReturn(TENANT);
                try {
                    new com.nexus.erp.pu.service.PuRequirementErpTransactionExecutor(factory)
                        .executeRepeatableRead(TENANT, () -> {
                            callbackEntered = true;
                            try {
                                T value = callback.get();
                                observed.set(value);
                                throw rollbackMarker;
                            } catch (RuntimeException | Error failure) {
                                callbackFailure = failure;
                                throw failure;
                            }
                        });
                    throw new AssertionError("夹具主动回滚必须由真实专属入口拒绝提交");
                } catch (com.nexus.common.core.exception.ServiceException rejected) {
                    if (callbackFailure != rollbackMarker) throw rejected;
                    verify(connection).rollback();
                    verify(connection, never()).commit();
                    return observed.get();
                }
            } finally {
                assertTrue(org.springframework.transaction.support.TransactionSynchronizationManager.getResourceMap().isEmpty());
                assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
                assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive());
                assertNull(com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder.peek());
            }
        }

        int count(String method) { return jdbcCounts.getOrDefault(method, 0); }

        Object invoke(ErpPurchaseInventorySourceMapper mapper, String method) {
            return switch (method) {
                case "selectDatabaseClock" -> mapper.selectDatabaseClock();
                case "selectQuantityColumns" -> mapper.selectQuantityColumns();
                case "lockMaterial" -> mapper.lockMaterial(TENANT, MATERIAL);
                case "lockUnit" -> mapper.lockUnit(TENANT, 27L);
                case "lockStocks" -> mapper.lockStocks(TENANT, MATERIAL);
                case "lockWarehouses" -> mapper.lockWarehouses(TENANT, List.of(8L));
                case "lockAreas" -> mapper.lockAreas(TENANT, List.of(18L));
                case "lockLocations" -> mapper.lockLocations(TENANT, List.of(28L));
                default -> throw new IllegalArgumentException("未知离线查询");
            };
        }

        private PreparedStatement prepared(String sql) throws SQLException {
            String normalized = sql.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
            String method = normalized.contains("@@session.time_zone") ? "selectDatabaseClock"
                : normalized.contains("information_schema.columns") ? "selectQuantityColumns"
                : normalized.contains("from bd_material ") ? "lockMaterial"
                : normalized.contains("from bd_unit_of_measure ") ? "lockUnit"
                : normalized.contains("from wm_material_stock ") ? "lockStocks"
                : normalized.contains("from wm_warehouse ") ? "lockWarehouses"
                : normalized.contains("from wm_storage_area ") ? "lockAreas"
                : normalized.contains("from wm_storage_location ") ? "lockLocations" : null;
            if (method == null) throw new SQLException("离线驱动拒绝非固定来源查询");
            PreparedStatement statement = mock(PreparedStatement.class);
            AtomicReference<ResultSet> result = new AtomicReference<>();
            when(statement.getConnection()).thenReturn(connection);
            when(statement.getUpdateCount()).thenReturn(-1);
            when(statement.getResultSet()).thenAnswer(i -> result.get());
            when(statement.execute()).thenAnswer(i -> {
                jdbcCounts.merge(method, 1, Integer::sum);
                Object held = org.springframework.transaction.support.TransactionSynchronizationManager.getResource(factory);
                if (held instanceof org.mybatis.spring.SqlSessionHolder holder) observedSessions.add(holder.getSqlSession());
                if (method.equals("lockStocks") && advanceDuringStockLock) databaseNow = NOW.plusSeconds(2);
                result.set(rows(rowsFor(method)));
                return true;
            });
            return statement;
        }

        private List<LinkedHashMap<String, Object>> rowsFor(String method) {
            return switch (method) {
                case "selectDatabaseClock" -> List.of(row("epochSeconds", epoch(databaseNow), "databaseTimeZone", "+08:00"));
                case "selectQuantityColumns" -> List.of(
                    row("tableName", "bd_material", "columnName", "safety_stock", "dataType", "decimal", "numericPrecision", 18, "numericScale", 4),
                    row("tableName", "wm_material_stock", "columnName", "stock_quantity", "dataType", "decimal", "numericPrecision", 18, "numericScale", 4),
                    row("tableName", "wm_material_stock", "columnName", "reserved_quantity", "dataType", "decimal", "numericPrecision", 18, "numericScale", 4));
                case "lockMaterial" -> List.of(row("tenantId", TENANT, "materialId", MATERIAL, "baseUomId", 27L,
                    "safetyStock", new BigDecimal("3.0000"), "status", 1L, "delFlag", "0"));
                case "lockUnit" -> List.of(row("tenantId", TENANT, "uomId", 27L, "category", "QUANTITY", "isBase", 1L,
                    "primaryId", null, "conversionRate", BigDecimal.ONE, "quantityScale", 4, "conversionVersion", "真实驱动夹具单位版本",
                    "conversionVerifiedAtEpochMillis", millis(NOW.minusSeconds(60)), "status", 1L, "delFlag", "0"));
                case "lockStocks" -> List.of(row("tenantId", TENANT, "stockId", 91L, "materialId", MATERIAL, "baseUomId", "27",
                    "warehouseId", 8L, "areaId", 18L, "locationId", 28L, "stockQuantity", stockQuantity,
                    "reservedQuantity", new BigDecimal("2.0000"), "productionDateEpochMillis", millis(NOW.minusSeconds(60)),
                    "recptDateEpochMillis", millis(NOW.minusSeconds(30)), "expireDateEpochMillis", millis(expiresAt),
                    "frozenFlag", 0L, "version", stockVersion, "delFlag", "0"));
                case "lockWarehouses" -> List.of(row("tenantId", TENANT, "warehouseId", 8L, "status", 1L, "delFlag", "0"));
                case "lockAreas" -> List.of(row("tenantId", TENANT, "areaId", 18L, "warehouseId", 8L,
                    "locationFlag", 1L, "enableFlag", 1L, "frozenFlag", 0L, "delFlag", "0"));
                case "lockLocations" -> List.of(row("tenantId", TENANT, "locationId", 28L, "warehouseId", 8L,
                    "areaId", 18L, "enableFlag", 1L, "frozenFlag", 0L, "delFlag", "0"));
                default -> throw new IllegalArgumentException("未知离线结果");
            };
        }

        static BigDecimal epoch(java.time.Instant value) {
            return BigDecimal.valueOf(value.getEpochSecond()).add(BigDecimal.valueOf(value.getNano(), 9));
        }

        private static BigDecimal millis(java.time.Instant value) { return BigDecimal.valueOf(value.toEpochMilli()); }

        private static LinkedHashMap<String, Object> row(Object... cells) {
            var result = new LinkedHashMap<String, Object>();
            for (int i = 0; i < cells.length; i += 2) result.put((String) cells[i], cells[i + 1]);
            return result;
        }

        private static ResultSet rows(List<LinkedHashMap<String, Object>> data) throws SQLException {
            List<String> columns = new ArrayList<>(data.get(0).keySet());
            ResultSetMetaData metadata = mock(ResultSetMetaData.class);
            when(metadata.getColumnCount()).thenReturn(columns.size());
            when(metadata.getColumnLabel(anyInt())).thenAnswer(i -> columns.get((int) i.getArgument(0) - 1));
            when(metadata.getColumnName(anyInt())).thenAnswer(i -> columns.get((int) i.getArgument(0) - 1));
            when(metadata.getColumnType(anyInt())).thenAnswer(i -> {
                Object value = data.get(0).get(columns.get((int) i.getArgument(0) - 1));
                return value instanceof String ? Types.VARCHAR : value instanceof Long ? Types.BIGINT
                    : value instanceof Integer ? Types.INTEGER : Types.DECIMAL;
            });
            when(metadata.getColumnClassName(anyInt())).thenAnswer(i -> {
                Object value = data.get(0).get(columns.get((int) i.getArgument(0) - 1));
                return value == null ? BigDecimal.class.getName() : value.getClass().getName();
            });
            ResultSet result = mock(ResultSet.class);
            AtomicInteger index = new AtomicInteger(-1);
            AtomicBoolean wasNull = new AtomicBoolean();
            when(result.getMetaData()).thenReturn(metadata);
            when(result.next()).thenAnswer(i -> index.incrementAndGet() < data.size());
            org.mockito.stubbing.Answer<Object> read = i -> {
                Object key = i.getArgument(0);
                String column = key instanceof Integer ? columns.get((Integer) key - 1) : key.toString();
                Object value = data.get(index.get()).get(column); wasNull.set(value == null);
                return switch (i.getMethod().getName()) {
                    case "getString" -> value == null ? null : value.toString();
                    case "getLong" -> value == null ? 0L : ((Number) value).longValue();
                    case "getInt" -> value == null ? 0 : ((Number) value).intValue();
                    case "getBigDecimal" -> value == null ? null : value instanceof BigDecimal ? value : new BigDecimal(value.toString());
                    default -> value;
                };
            };
            when(result.getString(anyString())).thenAnswer(read); when(result.getString(anyInt())).thenAnswer(read);
            when(result.getLong(anyString())).thenAnswer(read); when(result.getLong(anyInt())).thenAnswer(read);
            when(result.getInt(anyString())).thenAnswer(read); when(result.getInt(anyInt())).thenAnswer(read);
            when(result.getBigDecimal(anyString())).thenAnswer(read); when(result.getBigDecimal(anyInt())).thenAnswer(read);
            when(result.wasNull()).thenAnswer(i -> wasNull.get());
            return result;
        }
    }

    private static Configuration configuration() {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(ErpPurchaseInventorySourceMapper.class);
        return configuration;
    }

    private static Map<String, Object> parameters() {
        return Map.of("tenantId", "inventory-test", "materialId", 139L, "unitId", 27L, "ids", List.of(7L, 8L));
    }

    private static MappedStatement statement(Configuration configuration, String method) {
        return configuration.getMappedStatement(PREFIX + method);
    }

    private static String normalized(BoundSql sql) {
        return sql.getSql().replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    /** 原生结果处理器读取真实产品映射；不以测试专用日期/数量解码器替换产品行为。 */
    private static Object mapped(Configuration configuration, String method, LinkedHashMap<String, Object> values)
        throws SQLException {
        List<String> columns = new ArrayList<>(values.keySet());
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(metadata.getColumnCount()).thenReturn(columns.size());
        when(metadata.getColumnLabel(anyInt())).thenAnswer(i -> columns.get((int) i.getArgument(0) - 1));
        when(metadata.getColumnName(anyInt())).thenAnswer(i -> columns.get((int) i.getArgument(0) - 1));
        when(metadata.getColumnType(anyInt())).thenAnswer(i -> {
            Object value = values.get(columns.get((int) i.getArgument(0) - 1));
            return value instanceof String ? Types.VARCHAR : value instanceof Long ? Types.BIGINT
                : value instanceof Integer ? Types.INTEGER : Types.DECIMAL;
        });
        when(metadata.getColumnClassName(anyInt())).thenAnswer(i -> {
            Object value = values.get(columns.get((int) i.getArgument(0) - 1));
            return value == null ? BigDecimal.class.getName() : value.getClass().getName();
        });
        ResultSet result = mock(ResultSet.class);
        when(result.getMetaData()).thenReturn(metadata);
        when(result.next()).thenReturn(true, false);
        AtomicBoolean wasNull = new AtomicBoolean();
        org.mockito.stubbing.Answer<Object> read = i -> {
            Object key = i.getArgument(0);
            String column = key instanceof Integer ? columns.get((Integer) key - 1) : key.toString();
            Object value = values.get(column); wasNull.set(value == null);
            return switch (i.getMethod().getName()) {
                case "getString" -> value == null ? null : value.toString();
                case "getLong" -> value == null ? 0L : ((Number) value).longValue();
                case "getInt" -> value == null ? 0 : ((Number) value).intValue();
                case "getBigDecimal" -> value == null ? null : value instanceof BigDecimal ? value : new BigDecimal(value.toString());
                default -> value;
            };
        };
        when(result.getLong(anyString())).thenAnswer(read); when(result.getLong(anyInt())).thenAnswer(read);
        when(result.getInt(anyString())).thenAnswer(read); when(result.getInt(anyInt())).thenAnswer(read);
        when(result.getString(anyString())).thenAnswer(read); when(result.getString(anyInt())).thenAnswer(read);
        when(result.getBigDecimal(anyString())).thenAnswer(read); when(result.getBigDecimal(anyInt())).thenAnswer(read);
        when(result.wasNull()).thenAnswer(i -> wasNull.get());
        Statement jdbc = mock(Statement.class); when(jdbc.getResultSet()).thenReturn(result);
        when(jdbc.getUpdateCount()).thenReturn(-1);
        Connection connection = mock(Connection.class); when(jdbc.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(mock(DatabaseMetaData.class));
        MappedStatement statement = statement(configuration, method); BoundSql sql = statement.getBoundSql(parameters());
        ParameterHandler parameterHandler = configuration.newParameterHandler(statement, sql.getParameterObject(), sql);
        DefaultResultSetHandler handler = new DefaultResultSetHandler(mock(Executor.class), statement,
            parameterHandler, null, sql, RowBounds.DEFAULT);
        List<Object> rows = handler.handleResultSets(jdbc);
        assertEquals(1, rows.size(), "NULL 字段保留身份行，不能用零行结果假通过");
        return rows.get(0);
    }
}
