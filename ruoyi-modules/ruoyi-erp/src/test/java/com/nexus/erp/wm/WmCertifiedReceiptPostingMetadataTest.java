package com.nexus.erp.wm;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.nexus.common.tenant.handle.PlusTenantLineHandler;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.common.tenant.properties.TenantProperties;
import com.nexus.erp.wm.mapper.WmCertifiedReceiptPostingMapper;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 完整固定 SQL 与真实 MyBatis-Plus 租户/数据权限插件链的离线回归。
 * 只固定线程租户与 Executor 结果接收端，不模拟 SQL、解析器或拦截器；不连接数据库，
 * 不代表真实 MySQL/InnoDB、事务或正式收货验收。
 */
class WmCertifiedReceiptPostingMetadataTest {

    private static final Set<String> METADATA_METHODS = Set.of(
        "tableDefinitions", "quantityDefinitions", "uniqueDefinitions");
    private static final String MAPPER = WmCertifiedReceiptPostingMapper.class.getName();
    private MockedStatic<TenantHelper> tenantContext;
    private MybatisConfiguration configuration;

    @BeforeEach
    void setup() {
        tenantContext = mockStatic(TenantHelper.class);
        tenantContext.when(TenantHelper::getTenantId).thenReturn("000000");
        tenantContext.when(TenantHelper::isEnable).thenReturn(true);
        configuration = new MybatisConfiguration();
        configuration.addMapper(WmCertifiedReceiptPostingMapper.class);
    }

    @AfterEach
    void close() {
        tenantContext.close();
    }

    static Stream<Arguments> metadataQueries() {
        return Stream.of(
            Arguments.of("tableDefinitions", "information_schema.TABLES", List.of("TABLE_NAME", "ENGINE")),
            Arguments.of("quantityDefinitions", "information_schema.COLUMNS",
                List.of("TABLE_NAME", "COLUMN_NAME", "DATA_TYPE", "NUMERIC_PRECISION", "NUMERIC_SCALE")),
            Arguments.of("uniqueDefinitions", "information_schema.STATISTICS",
                List.of("INDEX_NAME", "SEQ_IN_INDEX", "COLUMN_NAME", "SUB_PART"))
        );
    }

    @ParameterizedTest
    @MethodSource("metadataQueries")
    void completeAnnotationSqlParsesWithNativeMetadataProjection(String methodName, String table,
                                                               List<String> projection) throws Exception {
        Method method = WmCertifiedReceiptPostingMapper.class.getMethod(methodName);
        String sql = String.join(" ", method.getAnnotation(Select.class).value());
        PlainSelect parsed = assertInstanceOf(PlainSelect.class,
            assertDoesNotThrow(() -> CCJSqlParserUtil.parse(sql), "完整原注解 SQL 必须可解析，不修补测试输入"));
        assertEquals(Set.of(table), new TablesNamesFinder<Void>().getTables((Statement) parsed));
        assertEquals(projection, parsed.getSelectItems().stream()
            .map(item -> item.getExpression().toString()).toList());
        assertNotNull(parsed.getWhere());
        String where = parsed.getWhere().toString().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        assertTrue(where.contains("TABLE_SCHEMA=DATABASE()"), "必须保留当前实际库的固定范围");
        assertFalse(where.contains("TENANT_ID"), "信息模式没有业务租户列");
        if ("uniqueDefinitions".equals(methodName)) {
            assertTrue(where.contains("NON_UNIQUE=0"));
            assertTrue(where.contains("TABLE_NAME='WM_CERTIFIED_RECEIPT_POSTING'"));
            assertEquals(List.of("INDEX_NAME", "SEQ_IN_INDEX"), parsed.getOrderByElements().stream()
                .map(item -> item.getExpression().toString()).toList());
        }
    }

    @ParameterizedTest
    @MethodSource("metadataQueries")
    void metadataSqlStaysIdenticalThroughRealPluginChainForDifferentTenants(String methodName, String table,
                                                                         List<String> projection) throws Exception {
        MappedStatement statement = configuration.getMappedStatement(MAPPER + "." + methodName);
        String original = statement.getBoundSql(Map.of()).getSql();
        for (String tenant : List.of("000000", "654321")) {
            tenantContext.when(TenantHelper::getTenantId).thenReturn(tenant);
            AtomicInteger dataPermissionCalls = new AtomicInteger();
            BoundSql bound = statement.getBoundSql(Map.of());
            assertTrue(bound.getParameterMappings().isEmpty(), "元数据 SQL 不接受上传租户/表名/片段");
            String actual = assertDoesNotThrow(() -> throughPluginChain(statement, Map.of(), bound, dataPermissionCalls));
            assertEquals(original, actual, "真实插件链不得向 " + table + " 加业务列或改写固定范围");
            assertEquals(0, dataPermissionCalls.get(), "元数据查询不能调用业务数据权限 handler");
            assertDoesNotThrow(() -> CCJSqlParserUtil.parse(actual), "插件链出口仍须是完整可解析 SQL");
        }
    }

    @Test
    void businessQueryStillRunsTenantAndDataPermissionInterceptors() throws Exception {
        MappedStatement statement = configuration.getMappedStatement(MAPPER + ".discoverLineIdentity");
        Map<String, Object> parameters = Map.of("tenant", "000000", "id", 701L);
        AtomicInteger dataPermissionCalls = new AtomicInteger();
        String sql = throughPluginChain(statement, parameters, statement.getBoundSql(parameters), dataPermissionCalls);
        assertTrue(sql.contains("tenant_id = '000000'"), "普通查询仍须注入真实线程租户，不能只有上传参数");
        assertTrue(sql.contains("create_dept = 123"), "普通查询不能因元数据修复而屏蔽数据权限");
        assertTrue(dataPermissionCalls.get() > 0);
        assertDoesNotThrow(() -> CCJSqlParserUtil.parse(sql));
    }

    @Test
    void onlyTheThreeMetadataMethodsOptOutOfBusinessInterceptors() {
        assertNull(WmCertifiedReceiptPostingMapper.class.getAnnotation(InterceptorIgnore.class),
            "禁止在整个 mapper 上关闭业务隔离");
        Set<String> ignored = Arrays.stream(WmCertifiedReceiptPostingMapper.class.getMethods())
            .filter(method -> method.isAnnotationPresent(InterceptorIgnore.class))
            .peek(method -> {
                InterceptorIgnore ignore = method.getAnnotation(InterceptorIgnore.class);
                assertEquals("true", ignore.tenantLine());
                assertEquals("true", ignore.dataPermission());
            })
            .map(Method::getName).collect(Collectors.toSet());
        assertEquals(METADATA_METHODS, ignored, "仅三个 information_schema 查询允许局部忽略");
    }

    @Test
    void permanentPostingTimeHasExplicitSecondPrecisionIndependentOfJdbcStringRendering() {
        MappedStatement statement = configuration.getMappedStatement(MAPPER + ".lockPosting");
        String sql = statement.getBoundSql(Map.of("tenant", "000000", "id", 101L)).getSql();
        PlainSelect parsed = assertInstanceOf(PlainSelect.class,
            assertDoesNotThrow(() -> CCJSqlParserUtil.parse(sql)));
        assertEquals("wm_certified_receipt_posting", assertInstanceOf(Table.class, parsed.getFromItem()).getName());
        List<String> timestamps = parsed.getSelectItems().stream()
            .filter(item -> item.getAlias() != null && "postedAt".equals(item.getAlias().getName()))
            .map(item -> item.getExpression().toString()).toList();
        assertEquals(List.of("DATE_FORMAT(posted_at, '%Y-%m-%d %H:%i:%s')"), timestamps,
            "永久结果与原 tx 时点必须使用相同显式格式，不能依赖 JDBC 日期字符串小数秒渲染");
        assertTrue(sql.stripTrailing().endsWith("FOR UPDATE"));
    }

    @Test
    void receiptTransactionReadUsesOriginalTxDimensionsAndPhysicalPostingTime() {
        MappedStatement statement = configuration.getMappedStatement(MAPPER + ".lockReceiptTransactions");
        String sql = statement.getBoundSql(Map.of("tenant", "000000", "id", 101L)).getSql();
        PlainSelect parsed = assertInstanceOf(PlainSelect.class,
            assertDoesNotThrow(() -> CCJSqlParserUtil.parse(sql)));
        assertEquals("wm_material_stock_tx", assertInstanceOf(Table.class, parsed.getFromItem()).getName());
        assertNull(parsed.getJoins(), "恢复维度必须读原 tx，不能 JOIN 今天的库存维度");
        List<String> nativeColumns = parsed.getSelectItems().stream().map(item -> item.getExpression())
            .filter(Column.class::isInstance).map(Column.class::cast).map(Column::getColumnName).toList();
        assertTrue(nativeColumns.containsAll(List.of("material_stock_id", "warehouse_id", "area_id", "location_id", "batch_id")),
            "必须读实际 material_stock_id 及原 tx 自身仓库/库区/储位/批次");
        List<String> timestamps = parsed.getSelectItems().stream()
            .filter(item -> item.getAlias() != null && "postedAt".equals(item.getAlias().getName()))
            .map(item -> item.getExpression().toString()).toList();
        assertEquals(List.of("DATE_FORMAT(tx_date, '%Y-%m-%d %H:%i:%s')"), timestamps);
        assertTrue(sql.stripTrailing().endsWith("FOR UPDATE"));
    }

    @Test
    void postedTxRecordRetainsExistingFieldsAndAppendsOriginalPhysicalFacts() {
        List<String> components = Arrays.stream(WmCertifiedReceiptPostingMapper.PostedTx.class.getRecordComponents())
            .map(component -> component.getName()).toList();
        assertEquals(List.of("txId", "stockId", "lineId", "baseUomId", "quantity", "beforeQuantity", "afterQuantity",
            "unitCost", "txAmount", "warehouseId", "areaId", "locationId", "batchId", "postedAt"), components);
        List<Class<?>> types = Arrays.stream(WmCertifiedReceiptPostingMapper.PostedTx.class.getRecordComponents())
            .skip(9).<Class<?>>map(component -> component.getType()).toList();
        assertEquals(List.of(Long.class, Long.class, Long.class, Long.class, String.class), types);
    }

    @Test
    void allOriginalReceiptTransactionIdsIncludeDeletedAndUnexpectedTypes() {
        Method method = assertDoesNotThrow(() -> WmCertifiedReceiptPostingMapper.class.getMethod(
            "lockAllReceiptTransactionIds", String.class, Long.class), "需要完整原单据流水当前读接口");
        Options options = method.getAnnotation(Options.class);
        assertNotNull(options);
        assertFalse(options.useCache());
        assertEquals(Options.FlushCachePolicy.TRUE, options.flushCache());
        MappedStatement statement = configuration.getMappedStatement(MAPPER + ".lockAllReceiptTransactionIds");
        BoundSql bound = statement.getBoundSql(Map.of("tenant", "000000", "id", 101L));
        String sql = bound.getSql();
        PlainSelect parsed = assertInstanceOf(PlainSelect.class,
            assertDoesNotThrow(() -> CCJSqlParserUtil.parse(sql)));
        assertEquals("wm_material_stock_tx", assertInstanceOf(Table.class, parsed.getFromItem()).getName());
        assertNull(parsed.getJoins());
        assertEquals(List.of("tx_id"), parsed.getSelectItems().stream()
            .map(item -> item.getExpression().toString()).toList());
        assertEquals(List.of("tenant", "id"), bound.getParameterMappings().stream()
            .map(mapping -> mapping.getProperty()).toList());
        String where = parsed.getWhere().toString().toLowerCase(Locale.ROOT);
        assertTrue(where.contains("tenant_id = ?"));
        assertTrue(where.contains("source_doc_type = 'wm_material_receive'"));
        assertTrue(where.contains("source_doc_id = ?"));
        assertFalse(where.contains("del_flag"), "删除行也须进入原单据流水全集");
        assertFalse(where.contains("tx_type"), "异类型流水也须进入原单据流水全集");
        assertFalse(where.contains("tx_flag"), "不得只取正向有效流水，掩盖额外写入");
        assertEquals(List.of("tx_id"), parsed.getOrderByElements().stream()
            .map(item -> item.getExpression().toString()).toList());
        assertTrue(sql.stripTrailing().endsWith("FOR UPDATE"));
    }

    private String throughPluginChain(MappedStatement statement, Map<String, ?> parameters, BoundSql bound,
                                      AtomicInteger dataPermissionCalls) throws Exception {
        TenantProperties properties = new TenantProperties();
        properties.setEnable(true);
        properties.setExcludes(List.of());
        MybatisPlusInterceptor chain = new MybatisPlusInterceptor();
        chain.addInnerInterceptor(new TenantLineInnerInterceptor(new PlusTenantLineHandler(properties)));
        chain.addInnerInterceptor(new DataPermissionInterceptor(new MultiDataPermissionHandler() {
            @Override
            public Expression getSqlSegment(Table table, Expression where, String statementId) {
                dataPermissionCalls.incrementAndGet();
                EqualsTo predicate = new EqualsTo();
                predicate.setLeftExpression(new Column("create_dept"));
                predicate.setRightExpression(new LongValue(123));
                return predicate;
            }
        }));
        // 原生拦截链接收真实的 MappedStatement/BoundSql，并通过 Executor 代理执行。
        // 仅隔离最终执行端，因此不使用数据库连接、会话、数据库或外部服务。
        Executor sink = mock(Executor.class);
        when(sink.createCacheKey(any(MappedStatement.class), any(), any(RowBounds.class), any(BoundSql.class)))
            .thenReturn(new CacheKey());
        doAnswer(call -> List.of(((BoundSql) call.getArgument(5)).getSql())).when(sink)
            .query(any(MappedStatement.class), any(), any(RowBounds.class), isNull(), any(CacheKey.class), any(BoundSql.class));
        Executor intercepted = (Executor) chain.plugin(sink);
        List<String> result = intercepted.query(statement, parameters, RowBounds.DEFAULT, null, new CacheKey(), bound);
        assertEquals(1, result.size());
        return result.get(0);
    }
}
