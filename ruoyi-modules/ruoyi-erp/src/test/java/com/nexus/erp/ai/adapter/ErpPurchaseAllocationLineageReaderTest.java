package com.nexus.erp.ai.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.aop.DynamicDataSourceAnnotationAdvisor;
import com.baomidou.dynamic.datasource.aop.DynamicDataSourceAnnotationInterceptor;
import com.baomidou.dynamic.datasource.processor.DsSpelExpressionProcessor;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.pu.domain.PuRequirementAllocationLedger;
import com.nexus.erp.pu.mapper.ErpPurchaseCatalogSourceMapper;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.RawOrderHead;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.RawOrderLine;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.RawRequirementDetail;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.RawRequirementHead;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.RawRequisitionHead;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.RawRequisitionLine;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.result.ResultMapException;
import org.apache.ibatis.executor.resultset.DefaultResultSetHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.TypeHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 有界采购占用来源归属合同；仅认证实际主键引用，不认证库存、额度、量纲或准时供应。
 * 新类只在测试方法内反射解析，缺实现必须是普通断言失败而不是编译缺类或跳过。
 * 事务边界使用真实 Spring/动态数据源代理与离线合成 JDBC 连接，字段合同使用模拟 ResultSet。
 * 来源可见性反例在 UUID 私有 H2 内存库执行生产 SQL、参数处理器与真实 MyBatis ResultMap；
 * 十进制秒类型适配仅限该私有方言，不连接业务库、启动服务或回填真实业务事实。
 * 日期映射沿明确 +08:00 墙上 DATETIME 生产合同；不修改 JVM、用户或真实业务库时区，
 * 不注册新的全局日期转换器。
 */
@Tag("dev")
@Execution(ExecutionMode.SAME_THREAD)
class ErpPurchaseAllocationLineageReaderTest {
    private static final String READER = "com.nexus.erp.ai.adapter.ErpPurchaseAllocationLineageReader";
    private static final String MAPPER = "com.nexus.erp.pu.mapper.ErpPurchaseAllocationLineageMapper";
    private static final String TENANT = "000001";
    private static final String OTHER_TENANT = "000002";
    private static final long MATERIAL_ID = 2105159630382362701L;
    private static final long BASE_ID = 2105159630382362702L;
    private static final long ALLOCATION_ID = 2105159630382362703L;
    private static final long DETAIL_ID = 2105159630382362704L;
    private static final long REQUIREMENT_ID = 2105159630382362705L;
    private static final long REQUISITION_LINE_ID = 2105159630382362706L;
    private static final long REQUISITION_ID = 2105159630382362707L;
    private static final long ORDER_LINE_ID = 2105159630382362708L;
    private static final long ORDER_ID = 2105159630382362709L;
    private static final String DECISION_KEY = "synthetic-lineage-decision-key";
    private static final String REQUEST_INTENT = "REQUEST_INTENT";
    private static final String ORDER_COMMITTED = "ORDER_COMMITTED_NOT_TIMELY_SUPPLY";
    private static final String RELEASED = "RELEASED";
    private static final Instant SNAPSHOT_AT = Instant.parse("2026-10-01T08:00:00.123456Z");
    private static final Instant CREATED_AT = Instant.parse("2026-09-29T08:00:00.123Z");
    private static final Instant CHANGED_AT = Instant.parse("2026-09-30T08:00:00.456Z");
    private static final Instant OUTSIDE_WINDOW = Instant.parse("2027-01-01T08:00:00Z");
    private static final String MAPPER_FAILURE = "采购占用归属来源读取失败";
    private static final List<String> QUERY_NAMES = List.of("selectDatabaseClock", "selectMaterial",
        "selectAllocations", "selectRequisitionLines", "selectOrderLines", "selectRequisitionHeads",
        "selectOrderHeads", "selectRequirementDetails", "selectRequirementHeads");
    private static final List<String> PART_NAMES = List.of("ledger", "requirementDetail", "requirementHead",
        "requisitionLine", "requisitionHead", "orderLine", "orderHead");
    private static final List<String> SENSITIVE_MARKERS = List.of("LINEAGE_SYNTH_SQL",
        "LINEAGE_SYNTH_CONNECTION", "LINEAGE_SYNTH_PASSWORD", "LINEAGE_SYNTH_BEARER");
    private static final List<RowContract> ROWS = List.of(
        new RowContract("selectMaterial", BdMaterial.class, "materialId"),
        new RowContract("selectAllocations", PuRequirementAllocationLedger.class, "allocationId"),
        new RowContract("selectRequisitionLines", RawRequisitionLine.class, "lineId"),
        new RowContract("selectOrderLines", RawOrderLine.class, "lineId"),
        new RowContract("selectRequisitionHeads", RawRequisitionHead.class, "requisitionId"),
        new RowContract("selectOrderHeads", RawOrderHead.class, "orderId"),
        new RowContract("selectRequirementDetails", RawRequirementDetail.class, "detailId"),
        new RowContract("selectRequirementHeads", RawRequirementHead.class, "requirementId"));

    @AfterEach
    void threadAndConnectionContextMustNotLeak() {
        assertAll(
            () -> assertNull(DynamicDataSourceContextHolder.peek()),
            () -> assertFalse(TransactionSynchronizationManager.isActualTransactionActive()),
            () -> assertFalse(TransactionSynchronizationManager.isSynchronizationActive()),
            () -> assertNull(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()),
            () -> assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty()));
    }

    @Test
    void readerIsInternalIdentityOnlyReadOnlyErpNewRepeatableReadService() {
        Class<?> reader = requiredClass(READER);
        Class<?> mapper = requiredClass(MAPPER);
        assertNotNull(reader.getAnnotation(Service.class));
        assertNull(reader.getAnnotation(RestController.class));
        assertNull(reader.getAnnotation(RequestMapping.class));
        DS ds = reader.getAnnotation(DS.class);
        if (ds == null) ds = entry().getAnnotation(DS.class);
        assertNotNull(ds);
        assertEquals("erp", ds.value());
        assertEquals(List.of("read"), Arrays.stream(reader.getDeclaredMethods())
            .filter(method -> Modifier.isPublic(method.getModifiers()) && !method.isSynthetic())
            .map(Method::getName).sorted().toList(), "不得接受外部事实、now、verified 或授权旁路");
        assertEquals(1, reader.getConstructors().length);
        assertDoesNotThrow(() -> reader.getConstructor(mapper));
        assertEquals(Set.of(mapper), Arrays.stream(reader.getDeclaredFields())
            .filter(field -> !Modifier.isStatic(field.getModifiers())).map(Field::getType).collect(Collectors.toSet()),
            "不能注入写入口、应用时钟、供应条款认证或 allocator");
        Transactional transaction = entry().getAnnotation(Transactional.class);
        assertNotNull(transaction);
        assertTrue(transaction.readOnly());
        assertEquals(Propagation.REQUIRES_NEW, transaction.propagation());
        assertEquals(Isolation.REPEATABLE_READ, transaction.isolation());
        assertEquals("LineageSnapshot", entry().getReturnType().getSimpleName());
        assertTrue(entry().getReturnType().isRecord());
    }

    @Test
    void constructorDoesNotQueryOrCreateQualifiedFacts() {
        Class<?> mapperType = requiredClass(MAPPER);
        Object mapper = mock(mapperType);
        assertDoesNotThrow(() -> requiredClass(READER).getConstructor(mapperType).newInstance(mapper));
        verifyNoInteractions(mapper);
    }

    @Test
    void mapperIsFixedSelectOnlyAndDoesNotHideClosedUnknownOrReferencedDeletedFacts() {
        Class<?> mapper = requiredClass(MAPPER);
        assertTrue(mapper.isInterface());
        assertEquals(0, mapper.getInterfaces().length, "禁止继承含写入方法的 BaseMapper");
        assertEquals(Set.copyOf(QUERY_NAMES), Arrays.stream(mapper.getDeclaredMethods())
            .map(Method::getName).collect(Collectors.toSet()));
        for (Method method : mapper.getDeclaredMethods()) {
            assertNull(method.getAnnotation(Insert.class));
            assertNull(method.getAnnotation(Update.class));
            assertNull(method.getAnnotation(Delete.class));
            String sql = sql(method);
            assertFalse(sql.contains("${"));
            assertTrue(sql.matches("(?s).*\\bselect\\b.*"));
            assertFalse(sql.matches("(?s).*\\b(insert|update|delete|replace|alter|drop|truncate|call|join|limit)\\b.*"));
            assertFalse(sql.contains("for update"));
            assertFalse(sql.contains("coalesce(") || sql.contains("ifnull(") || sql.contains("sum("));
            if (method.getName().equals("selectDatabaseClock")) {
                assertEquals(0, method.getParameterCount());
                assertTrue(sql.contains("unix_timestamp(current_timestamp(6))"));
                assertTrue(sql.contains("@@session.time_zone"));
                continue;
            }
            assertEquals("tenantId", method.getParameters()[0].getAnnotation(Param.class).value());
            assertTrue(sql.matches("(?s).*tenant_id\\s*=\\s*#\\{tenantid}.*"));
            String where = sql.substring(sql.indexOf("where"));
            assertFalse(where.matches("(?s).*\\b(status|allocation_status|receive_status|required_date)\\s*(=|in|<|>|between).*"),
                "不能隐藏关闭、未知状态或窗口外引用：" + method.getName());
            if (Set.of("selectRequisitionLines", "selectOrderLines").contains(method.getName())) {
                assertTrue(where.contains("del_flag is null"));
                assertTrue(where.matches("(?s).*del_flag\\s+not\\s+in\\s*\\(\\s*'0'\\s*,\\s*'1'\\s*\\).*"));
                assertTrue(where.matches("(?s).*material_id\\s*=\\s*#\\{materialid}.*"));
            } else if (!method.getName().equals("selectMaterial")) {
                assertFalse(where.contains("del_flag"), "被引用头/明细及完整账本不能过滤删除事实");
            }
            if (method.getParameterTypes()[1] == List.class) {
                assertTrue(sql.contains("<foreach"));
                assertFalse(where.contains("window"));
            }
        }
        assertDoesNotThrow(() -> query("selectRequirementDetails", String.class, List.class));
        assertEquals(List.of(String.class, Long.class), Arrays.asList(entry().getParameterTypes()));
    }

    /** 来源账本按真实引用扩读，不能以自身物料预过滤隐藏重复订单或跨物料需求/申请关联。 */
    @ParameterizedTest(name = "真实SQL账本关联不可遗漏：{0}")
    @ValueSource(strings = {"重复订单", "关联申请", "关联明细", "无种子申请"})
    void actualLedgerSelectionRetainsConflictingLinkedMaterial(String relation) throws SQLException {
        Fixture fixture = new Fixture();
        List<PuRequirementAllocationLedger> selected = selectActualAllocationRows(fixture.ledger, relation);
        assertEquals(relation.equals("无种子申请") ? 1 : 2, selected.size(), "相关错物料账本必须读出，不能遗漏后宣称唯一承接");
        fixture.allocations.clear();
        fixture.allocations.addAll(selected);
        denied(fixture);
    }

    /** 关联账本读取仍隔离其他租户及与本物料没有引用关系的账本。 */
    @Test
    void actualLedgerSelectionExcludesOtherTenantAndUnrelatedMaterial() throws SQLException {
        Fixture fixture = new Fixture();
        List<PuRequirementAllocationLedger> selected = selectActualAllocationRows(fixture.ledger, "无关隔离");
        assertEquals(List.of(fixture.ledger.getAllocationId()), selected.stream().map(PuRequirementAllocationLedger::getAllocationId).toList());
        fixture.allocations.clear();
        fixture.allocations.addAll(selected);
        assertEquals(1, allocations(readValid(fixture)).size());
    }

    /** H2仅作自动销毁的隔离SQL反例，不持久化归属；生产语句/参数器/ResultMap完整执行。 */
    private static List<PuRequirementAllocationLedger> selectActualAllocationRows(PuRequirementAllocationLedger original, String relation)
        throws SQLException {
        try (Connection connection = java.sql.DriverManager.getConnection("jdbc:h2:mem:lineage_ledger_"
            + java.util.UUID.randomUUID() + ";MODE=MySQL", "sa", ""); Statement ddl = connection.createStatement()) {
            /** H2原生秒值是INT，乘毫秒会溢出；只在本私有方言替换为十进制秒，不改生产SQL/全局配置。 */
            ddl.execute("SET BUILTIN_ALIAS_OVERRIDE TRUE");
            ddl.execute("CREATE ALIAS UNIX_TIMESTAMP FOR \"com.nexus.erp.ai.adapter.ErpPurchaseAllocationLineageReaderTest$MemoryEpochFunctions.seconds\"");
            ddl.execute("CREATE TABLE pu_requirement_allocation_ledger (allocation_id BIGINT PRIMARY KEY, tenant_id VARCHAR(6), "
                + "material_id BIGINT NOT NULL, source_type VARCHAR(20), detail_id BIGINT, requisition_line_id BIGINT, allocation_no INT, "
                + "order_line_id BIGINT, base_uom_id BIGINT, covered_demand_base_qty DECIMAL(18,4), occupied_base_qty DECIMAL(18,4), "
                + "fulfilled_base_qty DECIMAL(18,4), released_base_qty DECIMAL(18,4), allocation_status VARCHAR(50), "
                + "allocation_key VARCHAR(128), decision_idempotency_key VARCHAR(128), version INT, create_time TIMESTAMP, "
                + "transfer_time TIMESTAMP, released_at TIMESTAMP, release_reason VARCHAR(200), update_time TIMESTAMP)");
            for (String table : List.of("pu_purchase_requisition_line", "pu_purchase_order_line", "prd_material_requirement_detail")) {
                String key = table.equals("prd_material_requirement_detail") ? "detail_id" : "line_id";
                ddl.execute("CREATE TABLE " + table + " (" + key + " BIGINT PRIMARY KEY, tenant_id VARCHAR(6), material_id BIGINT, del_flag VARCHAR(1))");
                try (var insert = connection.prepareStatement("INSERT INTO " + table + " VALUES (?, ?, ?, '0')")) {
                    insert.setLong(1, table.equals("pu_purchase_requisition_line") ? REQUISITION_LINE_ID
                        : table.equals("pu_purchase_order_line") ? ORDER_LINE_ID : DETAIL_ID);
                    insert.setString(2, TENANT);
                    insert.setLong(3, MATERIAL_ID);
                    assertEquals(1, insert.executeUpdate());
                }
            }
            if (!relation.equals("无种子申请")) insertMemoryAllocation(connection, original);
            PuRequirementAllocationLedger conflict = new PuRequirementAllocationLedger();
            copyBean(original, conflict);
            conflict.setTenantId(TENANT);
            conflict.setCreateTime(original.getCreateTime());
            conflict.setUpdateTime(original.getUpdateTime());
            conflict.setAllocationId(original.getAllocationId() + 100L);
            conflict.setAllocationKey(original.getAllocationKey() + "-第二归属");
            conflict.setAllocationNo(2);
            conflict.setMaterialId(MATERIAL_ID + 1L);
            if (!relation.equals("重复订单")) conflict.setOrderLineId(null);
            if (relation.equals("关联明细") || relation.equals("无关隔离")) conflict.setRequisitionLineId(REQUISITION_LINE_ID + 100L);
            if (!relation.equals("重复订单") && !relation.equals("关联明细")) conflict.setDetailId(DETAIL_ID + 100L);
            insertMemoryAllocation(connection, conflict);
            conflict.setAllocationId(original.getAllocationId() + 101L);
            conflict.setAllocationKey(original.getAllocationKey() + "-其他租户");
            conflict.setTenantId(OTHER_TENANT);
            conflict.setMaterialId(MATERIAL_ID);
            conflict.setRequisitionLineId(REQUISITION_LINE_ID);
            conflict.setDetailId(DETAIL_ID);
            conflict.setOrderLineId(ORDER_LINE_ID);
            insertMemoryAllocation(connection, conflict);
            Configuration configuration = mappedConfiguration();
            MappedStatement statement = configuration.getMappedStatement(MAPPER + ".selectAllocations");
            BoundSql sql = boundSql(statement);
            ParameterHandler parameters = configuration.newParameterHandler(statement, sql.getParameterObject(), sql);
            try (var jdbc = connection.prepareStatement(sql.getSql())) {
                parameters.setParameters(jdbc);
                assertTrue(jdbc.execute());
                DefaultResultSetHandler handler = new DefaultResultSetHandler(mock(Executor.class), statement,
                    parameters, null, sql, RowBounds.DEFAULT);
                return handler.handleResultSets(jdbc).stream().map(PuRequirementAllocationLedger.class::cast).toList();
            }
        }
    }

    /** 仅供私有H2方言返回与既定生产投影一致的十进制秒，不参与任何业务时钟认证。 */
    public static final class MemoryEpochFunctions {
        /** 空值不补全，毫秒按精确十进制比例返回，避免H2内置INT秒的乘法溢出。 */
        public static BigDecimal seconds(java.sql.Timestamp value) {
            return value == null ? null : BigDecimal.valueOf(value.getTime(), 3);
        }
    }

    /** 只向私有内存表复制原始账本字段，Date显式转换，真实业务库没有任何回填。 */
    private static void insertMemoryAllocation(Connection connection, PuRequirementAllocationLedger row) throws SQLException {
        List<String> fields = List.of("allocationId", "tenantId", "materialId", "sourceType", "detailId", "requisitionLineId",
            "allocationNo", "orderLineId", "baseUomId", "coveredDemandBaseQty", "occupiedBaseQty", "fulfilledBaseQty",
            "releasedBaseQty", "allocationStatus", "allocationKey", "decisionIdempotencyKey", "version", "createTime",
            "transferTime", "releasedAt", "releaseReason", "updateTime");
        String columns = fields.stream().map(field -> field.replaceAll("([A-Z])", "_$1").toLowerCase(Locale.ROOT)).collect(Collectors.joining(","));
        try (var insert = connection.prepareStatement("INSERT INTO pu_requirement_allocation_ledger (" + columns
            + ") VALUES (" + String.join(",", java.util.Collections.nCopies(fields.size(), "?")) + ")")) {
            for (int i = 0; i < fields.size(); i++) {
                Object value = beanProperty(row, fields.get(i));
                insert.setObject(i + 1, value instanceof Date date ? new java.sql.Timestamp(date.getTime()) : value);
            }
            assertEquals(1, insert.executeUpdate());
        }
    }

    /** 实际 Mapper SQL 在隔离内存表执行，错误或空物料的同申请承接不能被物料预过滤隐藏。 */
    @ParameterizedTest(name = "真实SQL关联承接不可遗漏：{0}")
    @MethodSource("conflictingOrderMaterials")
    void actualOrderSelectionRetainsConflictingRequisitionInheritance(String label, Long conflictingMaterial, boolean committed)
        throws SQLException {
        Fixture fixture = committed ? new Fixture() : Fixture.intent();
        List<RawOrderLine> selected = selectActualOrderRows(conflictingMaterial, committed, false);
        assertEquals(committed ? 2 : 1, selected.size(), "关联申请行的错误/NULL物料订单行必须读出后拒绝");
        fixture.orderLines.clear();
        fixture.orderLines.addAll(selected);
        denied(fixture);
    }

    private static Stream<Arguments> conflictingOrderMaterials() {
        return Stream.of(
            Arguments.of("已有承接另挂错误物料", MATERIAL_ID + 1L, true),
            Arguments.of("已有承接另挂未知物料", null, true),
            Arguments.of("意图已有错误物料承接", MATERIAL_ID + 1L, false),
            Arguments.of("意图已有未知物料承接", null, false));
    }

    /** 扩大真实引用读取不能跨租户或纳入没有相关申请/账本的其他物料行。 */
    @Test
    void actualOrderSelectionDoesNotBorrowOtherTenantOrUnrelatedMaterial() throws SQLException {
        List<RawOrderLine> selected = selectActualOrderRows(MATERIAL_ID + 1L, true, true);
        assertEquals(List.of(ORDER_LINE_ID), selected.stream().map(RawOrderLine::getLineId).toList());
    }

    /** 只在自动销毁的内存库建合成表；使用生产SQL、参数处理器及ResultMap，不访问本机业务库。 */
    private static List<RawOrderLine> selectActualOrderRows(Long conflictingMaterial, boolean committed, boolean unrelated)
        throws SQLException {
        try (Connection connection = java.sql.DriverManager.getConnection("jdbc:h2:mem:lineage_conflict_"
            + java.util.UUID.randomUUID() + ";MODE=MySQL", "sa", ""); Statement ddl = connection.createStatement()) {
            ddl.execute("CREATE TABLE pu_purchase_order_line (line_id BIGINT PRIMARY KEY, tenant_id VARCHAR(6), "
                + "order_id BIGINT, requisition_line_id BIGINT, material_id BIGINT, unit VARCHAR(20), "
                + "order_quantity DECIMAL(18,4), received_quantity DECIMAL(18,4), returned_quantity DECIMAL(18,4), "
                + "invoiced_qty DECIMAL(18,4), receive_status VARCHAR(20), del_flag VARCHAR(1), "
                + "create_time TIMESTAMP, update_time TIMESTAMP)");
            ddl.execute("CREATE TABLE pu_purchase_requisition_line (line_id BIGINT PRIMARY KEY, tenant_id VARCHAR(6), "
                + "material_id BIGINT, del_flag VARCHAR(1))");
            ddl.execute("CREATE TABLE pu_requirement_allocation_ledger (allocation_id BIGINT PRIMARY KEY, tenant_id VARCHAR(6), "
                + "material_id BIGINT, requisition_line_id BIGINT, order_line_id BIGINT)");
            try (var insert = connection.prepareStatement("INSERT INTO pu_purchase_requisition_line VALUES (?, ?, ?, '0')")) {
                insert.setLong(1, REQUISITION_LINE_ID);
                insert.setString(2, TENANT);
                insert.setLong(3, MATERIAL_ID);
                assertEquals(1, insert.executeUpdate());
            }
            if (committed) insertMemoryOrder(connection, ORDER_LINE_ID, TENANT, REQUISITION_LINE_ID, MATERIAL_ID);
            insertMemoryOrder(connection, ORDER_LINE_ID + 100L, TENANT,
                unrelated ? REQUISITION_LINE_ID + 100L : REQUISITION_LINE_ID, conflictingMaterial);
            insertMemoryOrder(connection, ORDER_LINE_ID + 101L, OTHER_TENANT, REQUISITION_LINE_ID, MATERIAL_ID);
            Configuration configuration = mappedConfiguration();
            MappedStatement statement = configuration.getMappedStatement(MAPPER + ".selectOrderLines");
            BoundSql sql = boundSql(statement);
            ParameterHandler parameters = configuration.newParameterHandler(statement, sql.getParameterObject(), sql);
            try (var jdbc = connection.prepareStatement(sql.getSql())) {
                parameters.setParameters(jdbc);
                assertTrue(jdbc.execute());
                DefaultResultSetHandler handler = new DefaultResultSetHandler(mock(Executor.class), statement,
                    parameters, null, sql, RowBounds.DEFAULT);
                return handler.handleResultSets(jdbc).stream().map(RawOrderLine.class::cast).toList();
            }
        }
    }

    /** 内存订单行保持未收货原始状态，仅改变待复现的物料及申请引用。 */
    private static void insertMemoryOrder(Connection connection, long lineId, String tenantId, long requisitionLineId,
        Long materialId) throws SQLException {
        try (var insert = connection.prepareStatement("INSERT INTO pu_purchase_order_line "
            + "(line_id, tenant_id, order_id, requisition_line_id, material_id, unit, order_quantity, "
            + "received_quantity, returned_quantity, invoiced_qty, receive_status, del_flag) "
            + "VALUES (?, ?, ?, ?, ?, '个', 510, 0, 0, 0, 'PENDING', '0')")) {
            insert.setLong(1, lineId);
            insert.setString(2, tenantId);
            insert.setLong(3, ORDER_ID);
            insert.setLong(4, requisitionLineId);
            if (materialId == null) insert.setNull(5, Types.BIGINT); else insert.setLong(5, materialId);
            assertEquals(1, insert.executeUpdate());
        }
    }

    @ParameterizedTest(name = "身份预拒绝，来源零查询：{0}")
    @MethodSource("invalidRequests")
    void invalidTenantOrMaterialIsRejectedBeforeEverySourceQuery(String label, boolean enabled,
        String current, String requested, Long material) {
        Fixture fixture = new Fixture();
        try (MockedStatic<TenantHelper> ignored = tenant(enabled, current)) {
            assertThrows(ServiceException.class, () -> fixture.read(requested, material), label);
        }
        verifyNoInteractions(fixture.mapper);
        assertTrue(fixture.queries.isEmpty());
    }

    private static Stream<Arguments> invalidRequests() {
        return Stream.of(
            Arguments.of("租户未启用", false, TENANT, TENANT, MATERIAL_ID),
            Arguments.of("当前租户缺失", true, null, TENANT, MATERIAL_ID),
            Arguments.of("当前租户空白", true, " ", TENANT, MATERIAL_ID),
            Arguments.of("请求租户缺失", true, TENANT, null, MATERIAL_ID),
            Arguments.of("请求租户空白", true, TENANT, " ", MATERIAL_ID),
            Arguments.of("请求租户跨租户", true, TENANT, OTHER_TENANT, MATERIAL_ID),
            Arguments.of("同值非法租户", true, "tenant-text", "tenant-text", MATERIAL_ID),
            Arguments.of("租户含空白", true, " 000001", " 000001", MATERIAL_ID),
            Arguments.of("物料缺失", true, TENANT, TENANT, null),
            Arguments.of("物料零", true, TENANT, TENANT, 0L),
            Arguments.of("物料负", true, TENANT, TENANT, -1L));
    }

    @ParameterizedTest(name = "非代理/无效上下文拒绝：{0}")
    @ValueSource(strings = {"none", "metadata-only", "master", "writable", "read-committed", "no-isolation", "erp-rr-template"})
    void directCallCannotBeQualifiedByManuallySuppliedTransactionMetadata(String context) {
        Fixture fixture = new Fixture();
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            if (context.equals("none")) {
                assertThrows(ServiceException.class, fixture::directRead);
            } else if (context.equals("metadata-only")) {
                DynamicDataSourceContextHolder.push("erp");
                TransactionSynchronizationManager.setActualTransactionActive(true);
                TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
                TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
                try {
                    assertThrows(ServiceException.class, fixture::directRead, "ThreadLocal 声明不能伪造服务代理");
                } finally {
                    TransactionSynchronizationManager.clear();
                    DynamicDataSourceContextHolder.poll();
                }
            } else {
                DynamicDataSourceContextHolder.push(context.equals("master") ? "master" : "erp");
                TransactionTemplate transaction = new TransactionTemplate(fixture.transactions);
                transaction.setReadOnly(!context.equals("writable"));
                transaction.setIsolationLevel(context.equals("read-committed") ? TransactionDefinition.ISOLATION_READ_COMMITTED
                    : context.equals("no-isolation") ? TransactionDefinition.ISOLATION_DEFAULT
                    : TransactionDefinition.ISOLATION_REPEATABLE_READ);
                try {
                    transaction.execute(status -> {
                        assertThrows(ServiceException.class, fixture::directRead,
                            "独立只读 RR 模板也不能替代实际 reader 事务/DS 代理");
                        return null;
                    });
                } finally {
                    DynamicDataSourceContextHolder.poll();
                }
            }
        }
        verifyNoInteractions(fixture.mapper);
        assertTrue(fixture.queries.isEmpty());
    }
    @Test
    void actualProxyUsesErpConnectionReadOnlyRepeatableReadAndRestoresMasterCaller() {
        Fixture fixture = new Fixture();
        assertTrue(AopUtils.isAopProxy(fixture.reader));
        DynamicDataSourceContextHolder.push("master");
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            new TransactionTemplate(fixture.transactions).execute(status -> {
                Connection outer = ((ConnectionHolder) TransactionSynchronizationManager.getResource(fixture.routing)).getConnection();
                fixture.read(TENANT, MATERIAL_ID);
                assertSame(outer, ((ConnectionHolder) TransactionSynchronizationManager.getResource(fixture.routing)).getConnection());
                assertEquals("master", DynamicDataSourceContextHolder.peek());
                assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                assertNull(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
                fixture.read(TENANT, MATERIAL_ID);
                assertSame(outer, ((ConnectionHolder) TransactionSynchronizationManager.getResource(fixture.routing)).getConnection());
                return null;
            });
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
        assertEquals(List.of("master", "erp", "erp"), fixture.connections.stream().map(connection -> connection.route).toList());
        assertEquals(3, fixture.connections.stream().mapToInt(connection -> connection.commits).sum());
        assertEquals(0, fixture.connections.stream().mapToInt(connection -> connection.rollbacks).sum());
        assertEquals(2, fixture.queryConnections.stream().distinct().count());
        assertTrue(fixture.connections.stream().allMatch(connection -> connection.closed));
    }

    @ParameterizedTest(name = "缺少真实切面拒绝：{0}")
    @ValueSource(strings = {"ds-only", "transaction-only"})
    void missingDataSourceOrTransactionAdviceRejectsBeforeSourceSql(String advice) {
        Fixture fixture = new Fixture();
        Object incomplete = fixture.proxy(advice.equals("ds-only"), advice.equals("transaction-only"));
        DynamicDataSourceContextHolder.push("master");
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            assertThrows(ServiceException.class, () -> invoke(entry(), incomplete, TENANT, MATERIAL_ID));
            assertEquals("master", DynamicDataSourceContextHolder.peek());
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
        verifyNoInteractions(fixture.mapper);
    }

    @Test
    void emptySourcesAreOnlyAnEmptyBindingSnapshotNotZeroInventoryOrRequestableQuota() {
        Fixture fixture = Fixture.empty();
        Object snapshot = readValid(fixture);
        assertTrue(allocations(snapshot).isEmpty());
        assertTrue(versions(snapshot).isEmpty());
        assertEquals(TENANT, property(snapshot, "tenantId"));
        assertEquals(MATERIAL_ID, property(snapshot, "materialId"));
        assertEquals(BASE_ID, property(snapshot, "baseUomId"));
        assertEquals(SNAPSHOT_AT, property(snapshot, "snapshotAt"));
        assertEquals("+08:00", property(snapshot, "databaseTimeZone"));
        assertBindingOnly(snapshot);
        assertFalse(fixture.queries.contains("selectRequirementDetails"));
        assertFalse(fixture.queries.contains("selectRequirementHeads"));
        assertFalse(fixture.queries.contains("selectRequisitionHeads"));
        assertFalse(fixture.queries.contains("selectOrderHeads"));
    }

    @Test
    void completeOrderInheritanceCopiesEveryRawFactWithoutClaimingTimelySupply() {
        Fixture fixture = new Fixture();
        Object snapshot = readValid(fixture);
        assertEquals(1, allocations(snapshot).size());
        Object binding = allocations(snapshot).get(0);
        for (String part : PART_NAMES) assertFrozenBean(fixture.bean(part), property(binding, part));
        assertEquals(ORDER_COMMITTED, property(property(binding, "ledger"), "allocationStatus"));
        assertEquals("7", versions(snapshot).get("allocation:" + ALLOCATION_ID));
        assertEquals("9", versions(snapshot).get("requisition:" + REQUISITION_ID));
        assertEquals(Set.copyOf(QUERY_NAMES), Set.copyOf(fixture.queries));
        assertEquals(QUERY_NAMES.size(), fixture.queries.size());
        assertEquals(List.of("selectDatabaseClock", "selectMaterial"), fixture.queries.subList(0, 2));
        assertBindingOnly(snapshot);
    }

    @ParameterizedTest(name = "申请意图真实状态：{0}")
    @ValueSource(strings = {"DRAFT", "SUBMITTED", "APPROVING", "APPROVED", "REJECTED"})
    void actualOpenRequisitionConstantsAreBoundAsIntentNotInventory(String state) {
        assertTrue(actualRequisitionStates().contains(state));
        Fixture fixture = Fixture.intent();
        fixture.requisitionHead.setStatus(state);
        Object snapshot = readValid(fixture);
        Object binding = allocations(snapshot).get(0);
        assertNull(property(binding, "orderLine"));
        assertNull(property(binding, "orderHead"));
        assertEquals(REQUEST_INTENT, property(property(binding, "ledger"), "allocationStatus"));
        assertDecimal("5.4292", property(property(binding, "ledger"), "occupiedBaseQty"));
        assertBindingOnly(snapshot);
    }

    @ParameterizedTest(name = "未履约订单仅承接占用：{0}")
    @ValueSource(strings = {"DRAFT", "SUBMITTED", "APPROVING", "APPROVED", "REJECTED"})
    void preReceiptOrderStatesRemainNonTimelyCommittedFacts(String state) {
        assertDoesNotThrow(() -> com.nexus.erp.pu.domain.enums.PurchaseOrderStatusEnum.fromCode(state));
        Fixture fixture = new Fixture();
        fixture.orderHead.setStatus(state);
        fixture.orderHead.setExpectedArrivalDate(Date.from(CREATED_AT));
        Object snapshot = readValid(fixture);
        assertEquals(ORDER_COMMITTED, property(property(allocations(snapshot).get(0), "ledger"), "allocationStatus"));
        assertBindingOnly(snapshot);
    }

    @ParameterizedTest(name = "完整未转单释放事实：{0}")
    @ValueSource(strings = {"CLOSED", "CANCELLED"})
    void fullIntentReleaseRequiresActualReleaseFactsAndNeverReopensQuota(String state) {
        Fixture fixture = Fixture.released();
        fixture.requisitionHead.setStatus(state);
        Object snapshot = readValid(fixture);
        Object ledger = property(allocations(snapshot).get(0), "ledger");
        assertEquals(RELEASED, property(ledger, "allocationStatus"));
        assertDecimal("0", property(ledger, "occupiedBaseQty"));
        assertDecimal("5.4292", property(ledger, "releasedBaseQty"));
        assertEquals(CHANGED_AT, property(ledger, "releasedAt"));
        assertEquals("合成未转单申请撤销", property(ledger, "releaseReason"));
        assertBindingOnly(snapshot);
    }

    @Test
    void closingRequisitionDoesNotReleaseAnExistingOrderInheritanceOrCountItTwice() {
        Fixture fixture = new Fixture();
        fixture.requisitionHead.setStatus("CLOSED");
        Object snapshot = readValid(fixture);
        assertEquals(1, allocations(snapshot).size());
        Object ledger = property(allocations(snapshot).get(0), "ledger");
        assertEquals(ORDER_COMMITTED, property(ledger, "allocationStatus"));
        assertDecimal("5.4292", property(ledger, "occupiedBaseQty"));
        assertDecimal("0", property(ledger, "releasedBaseQty"));
        assertNull(property(ledger, "releasedAt"));
    }

    @Test
    void referencedMrpOutsideWindowAndFinishedProductHeadAreReadByTheirRealIds() {
        Fixture fixture = new Fixture();
        fixture.detail.setRequiredDate(Date.from(OUTSIDE_WINDOW));
        fixture.requirementHead.setMaterialId(MATERIAL_ID + 900L);
        Object snapshot = readValid(fixture);
        Object binding = allocations(snapshot).get(0);
        assertEquals(OUTSIDE_WINDOW, property(property(binding, "requirementDetail"), "requiredDate"));
        assertEquals(MATERIAL_ID + 900L, property(property(binding, "requirementHead"), "materialId"),
            "MRP 头是成品，不能混同子项材料身份");
        assertEquals(List.of(DETAIL_ID), fixture.idArguments.get("selectRequirementDetails"));
        assertEquals(List.of(REQUIREMENT_ID), fixture.idArguments.get("selectRequirementHeads"));
        assertFalse(sql(query("selectRequirementDetails", String.class, List.class)).contains("window"));
    }

    @Test
    void legacySourceIdAndRawUnitsOrQuantitiesCannotOverrideExplicitLedgerReferences() {
        Fixture fixture = new Fixture();
        fixture.requisitionLine.setSourceId(42L);
        fixture.requisitionLine.setSourceType("AI_DECISION");
        fixture.requisitionLine.setUnit("旧箱单位，尚未认证");
        fixture.requisitionLine.setApplyQuantity(bd("510"));
        fixture.requisitionLine.setApprovedQuantity(bd("508"));
        fixture.orderLine.setUnit("历史采购单位，尚未换算");
        fixture.orderLine.setOrderQuantity(bd("123.4567"));
        fixture.detail.setUnit("旧MRP文本单位");
        Object snapshot = readValid(fixture);
        Object binding = allocations(snapshot).get(0);
        assertEquals(DETAIL_ID, property(property(binding, "ledger"), "detailId"));
        assertEquals(42L, property(property(binding, "requisitionLine"), "sourceId"));
        assertDecimal("510", property(property(binding, "requisitionLine"), "applyQuantity"));
        assertDecimal("123.4567", property(property(binding, "orderLine"), "orderQuantity"));
        assertDecimal("5.4292", property(property(binding, "ledger"), "coveredDemandBaseQty"));
        assertEquals("旧MRP文本单位", property(property(binding, "requirementDetail"), "unit"));
        assertBindingOnly(snapshot);
    }

    @Test
    void unknownRawUnitAndApplicationQuantityStayUnknownNotAllocationEligible() {
        Fixture fixture = Fixture.intent();
        fixture.requisitionLine.setUnit(null);
        fixture.requisitionLine.setApplyQuantity(null);
        fixture.requisitionLine.setApprovedQuantity(null);
        fixture.requisitionLine.setShortageQuantity(null);
        fixture.detail.setUnit(null);
        fixture.detail.setStockQuantity(null);
        fixture.detail.setShortageQuantity(null);
        Object snapshot = readValid(fixture);
        Object binding = allocations(snapshot).get(0);
        assertNull(property(property(binding, "requisitionLine"), "unit"));
        assertNull(property(property(binding, "requisitionLine"), "applyQuantity"));
        assertNull(property(property(binding, "requirementDetail"), "stockQuantity"));
        assertBindingOnly(snapshot);
    }

    @ParameterizedTest(name = "旧申请510未绑定不能猜来源：{0}")
    @ValueSource(strings = {"DRAFT", "SUBMITTED", "APPROVING", "APPROVED", "ORDERED", "REJECTED"})
    void emptyLedgerWithOpenLegacy510RequisitionRejectsEntireMaterial(String state) {
        Fixture fixture = Fixture.intent();
        fixture.allocations.clear();
        fixture.requisitionHead.setStatus(state);
        fixture.requisitionLine.setApplyQuantity(bd("510"));
        fixture.requisitionLine.setSourceType("MRP_DETAIL");
        fixture.requisitionLine.setSourceId(DETAIL_ID);
        denied(fixture);
    }

    @ParameterizedTest(name = "无申请账本绑定的开放订单拒绝：{0}")
    @ValueSource(strings = {"DRAFT", "SUBMITTED", "APPROVING", "APPROVED", "REJECTED"})
    void emptyLedgerWithUnboundOpenOrderRejectsEntireMaterial(String state) {
        Fixture fixture = new Fixture();
        fixture.allocations.clear();
        fixture.orderHead.setStatus(state);
        fixture.requisitionLines.clear();
        fixture.requisitionHeads.clear();
        denied(fixture);
    }

    @ParameterizedTest(name = "额外开放来源使整物料拒绝：{0}")
    @ValueSource(strings = {"requisition", "order"})
    void anotherOpenSameMaterialLineIsNotCoveredBySomeoneElsesBinding(String kind) {
        Fixture fixture = new Fixture();
        if (kind.equals("requisition")) {
            RawRequisitionLine extra = new RawRequisitionLine();
            copyBean(fixture.requisitionLine, extra);
            extra.setLineId(REQUISITION_LINE_ID + 100L);
            fixture.requisitionLines.add(extra);
        } else {
            RawOrderLine extra = new RawOrderLine();
            copyBean(fixture.orderLine, extra);
            extra.setLineId(ORDER_LINE_ID + 100L);
            fixture.orderLines.add(extra);
        }
        denied(fixture);
    }

    @ParameterizedTest(name = "无账本也不能吞掉未知来源状态：{0}")
    @ValueSource(strings = {"requisition", "order"})
    void emptyLedgerDoesNotTurnUnknownLegacyStateIntoClosedOrZero(String source) {
        Fixture fixture = new Fixture();
        fixture.allocations.clear();
        if (source.equals("requisition")) {
            fixture.orderLines.clear();
            fixture.orderHeads.clear();
            fixture.requisitionHead.setStatus("OPEN");
        } else {
            fixture.requisitionHead.setStatus("CLOSED");
            fixture.orderHead.setStatus("UNKNOWN");
        }
        denied(fixture);
    }
    @ParameterizedTest(name = "数据库时钟/时区不能由外部替代：{0}")
    @MethodSource("invalidClocks")
    void unknownClockOrNonPlusEightSessionRejectsBeforeMaterialSql(String label, BigDecimal seconds,
        String zone, boolean missing) {
        Fixture fixture = new Fixture();
        fixture.epochSeconds = seconds;
        fixture.databaseTimeZone = zone;
        fixture.missingClock = missing;
        denied(fixture);
        assertEquals(List.of("selectDatabaseClock"), fixture.queries);
    }

    private static Stream<Arguments> invalidClocks() {
        return Stream.of(
            Arguments.of("缺时钟", epoch(SNAPSHOT_AT), "+08:00", true),
            Arguments.of("秒数未知", null, "+08:00", false),
            Arguments.of("零时钟", BigDecimal.ZERO, "+08:00", false),
            Arguments.of("负时钟", bd("-1"), "+08:00", false),
            Arguments.of("亚纳秒", bd("1790832000.1234567891"), "+08:00", false),
            Arguments.of("溢出", bd("1E30"), "+08:00", false),
            Arguments.of("缺时区", epoch(SNAPSHOT_AT), null, false),
            Arguments.of("空时区", epoch(SNAPSHOT_AT), "", false),
            Arguments.of("SYSTEM未核", epoch(SNAPSHOT_AT), "SYSTEM", false),
            Arguments.of("UTC不符存储合同", epoch(SNAPSHOT_AT), "+00:00", false),
            Arguments.of("偏移不符", epoch(SNAPSHOT_AT), "+09:00", false),
            Arguments.of("名称未核", epoch(SNAPSHOT_AT), "Asia/Shanghai", false));
    }
    @ParameterizedTest(name = "真实身份/父子/状态归属拒绝：{0}")
    @MethodSource("invalidBindings")
    void brokenBindingRejectsEntireMaterial(String label, Consumer<Fixture> mutation) {
        Fixture fixture = new Fixture();
        mutation.accept(fixture);
        denied(fixture);
    }

    /** 材料身份只检查子项实体；MRP 头的成品身份有独立正例，不纳入此拒绝矩阵。 */
    private static Stream<Arguments> invalidBindings() {
        List<Arguments> cases = new ArrayList<>();
        Map<String, String> ids = new LinkedHashMap<>();
        ids.put("ledger", "allocationId");
        ids.put("requirementDetail", "detailId");
        ids.put("requirementHead", "requirementId");
        ids.put("requisitionLine", "lineId");
        ids.put("requisitionHead", "requisitionId");
        ids.put("orderLine", "lineId");
        ids.put("orderHead", "orderId");
        ids.forEach((part, id) -> {
            cases.add(bad(part + "租户缺失", f -> set(f.bean(part), "tenantId", null)));
            cases.add(bad(part + "跨租户", f -> set(f.bean(part), "tenantId", OTHER_TENANT)));
            for (Long invalid : Arrays.asList(null, 0L, -1L)) {
                cases.add(bad(part + "非法主键" + invalid, f -> set(f.bean(part), id, invalid)));
            }
            if (!part.equals("ledger")) {
                for (String invalid : Arrays.asList(null, "1", "?")) {
                    cases.add(bad(part + "删除或未知删除" + invalid, f -> set(f.bean(part), "delFlag", invalid)));
                }
            }
        });
        for (String part : List.of("ledger", "requirementDetail", "requisitionLine", "orderLine")) {
            for (Long invalid : Arrays.asList(null, 0L, MATERIAL_ID + 1L)) {
                cases.add(bad(part + "缺失或跨材料" + invalid, f -> set(f.bean(part), "materialId", invalid)));
            }
        }
        cases.add(bad("物料不存在", f -> f.material = null));
        cases.add(bad("物料身份被替换", f -> f.material.setMaterialId(MATERIAL_ID + 1L)));
        cases.add(bad("物料租户缺失", f -> f.material.setTenantId(null)));
        cases.add(bad("物料跨租户", f -> f.material.setTenantId(OTHER_TENANT)));
        for (String flag : Arrays.asList(null, "1", "?")) {
            cases.add(bad("物料删除或未知" + flag, f -> f.material.setDelFlag(flag)));
        }
        for (Long status : Arrays.asList(null, 0L, 2L)) {
            cases.add(bad("物料状态未知或禁用" + status, f -> f.material.setStatus(status)));
        }
        for (Long id : Arrays.asList(null, 0L, BASE_ID + 1L)) {
            cases.add(bad("真实物料基本单位不符" + id, f -> f.material.setBaseUomId(id)));
            cases.add(bad("账本基本单位不符" + id, f -> f.ledger.setBaseUomId(id)));
        }
        for (Integer number : Arrays.asList(null, 0, -1)) {
            cases.add(bad("allocationNo必须显式正整数" + number, f -> f.ledger.setAllocationNo(number)));
        }
        for (Integer version : Arrays.asList(null, -1)) {
            cases.add(bad("账本版本未知或负" + version, f -> f.ledger.setVersion(version)));
            cases.add(bad("申请头版本未知或负" + version, f -> f.requisitionHead.setVersion(version)));
        }
        for (String key : Arrays.asList(null, "", " ", " padded ", "x".repeat(129))) {
            cases.add(bad("allocationKey非法" + key, f -> f.ledger.setAllocationKey(key)));
            cases.add(bad("decisionIdempotencyKey非法" + key, f -> f.ledger.setDecisionIdempotencyKey(key)));
        }
        for (String type : Arrays.asList(null, "", "AI_DECISION", "MRP")) {
            cases.add(bad("账本不是显式MRP_DETAIL" + type, f -> f.ledger.setSourceType(type)));
        }
        cases.add(bad("账本detail未真实返回", f -> f.details.clear()));
        cases.add(bad("账本申请行未真实返回", f -> f.requisitionLines.clear()));
        cases.add(bad("账本订单行未真实返回", f -> f.orderLines.clear()));
        cases.add(bad("缺MRP头", f -> f.requirementHeads.clear()));
        cases.add(bad("缺申请头", f -> f.requisitionHeads.clear()));
        cases.add(bad("缺订单头", f -> f.orderHeads.clear()));
        cases.add(bad("MRP明细挂错头", f -> f.detail.setRequirementId(REQUIREMENT_ID + 1L)));
        cases.add(bad("申请行挂错头", f -> f.requisitionLine.setRequisitionId(REQUISITION_ID + 1L)));
        cases.add(bad("订单行挂错头", f -> f.orderLine.setOrderId(ORDER_ID + 1L)));
        cases.add(bad("订单行回指另一申请行", f -> f.orderLine.setRequisitionLineId(REQUISITION_LINE_ID + 1L)));
        cases.add(bad("订单头回指另一申请头", f -> f.orderHead.setRequisitionId(REQUISITION_ID + 1L)));
        cases.add(bad("账本引用另一detail", f -> f.ledger.setDetailId(DETAIL_ID + 1L)));
        cases.add(bad("账本引用另一申请行", f -> f.ledger.setRequisitionLineId(REQUISITION_LINE_ID + 1L)));
        cases.add(bad("账本引用另一订单行", f -> f.ledger.setOrderLineId(ORDER_LINE_ID + 1L)));
        for (String part : List.of("requirementDetail", "requisitionLine", "orderLine")) {
            String parent = part.equals("requirementDetail") ? "requirementId"
                : part.equals("requisitionLine") ? "requisitionId" : "orderId";
            cases.add(bad(part + "父键未知", f -> set(f.bean(part), parent, null)));
            cases.add(bad(part + "父键零", f -> set(f.bean(part), parent, 0L)));
        }
        cases.add(bad("订单行来源申请键未知", f -> f.orderLine.setRequisitionLineId(null)));
        cases.add(bad("订单头来源申请键未知", f -> f.orderHead.setRequisitionId(null)));
        cases.add(bad("承接账本订单ID未知", f -> f.ledger.setOrderLineId(null)));
        cases.add(bad("承接账本订单ID零", f -> f.ledger.setOrderLineId(0L)));
        cases.add(bad("账本创建时间未知", f -> f.ledger.setCreateTime(null)));
        cases.add(bad("承接时间未知", f -> f.ledger.setTransferTime(null)));
        return cases.stream();
    }

    @ParameterizedTest(name = "未知实际状态拒绝：{0}/{1}")
    @MethodSource("unknownStates")
    void unknownOrUnsupportedEntityStateCannotBeHiddenByMapper(String part, Object state) {
        Fixture fixture = new Fixture();
        set(fixture.bean(part), part.equals("ledger") ? "allocationStatus"
            : part.equals("orderLine") ? "receiveStatus" : "status", state);
        denied(fixture);
    }

    private static Stream<Arguments> unknownStates() {
        return Stream.concat(
            Stream.of("requirementHead", "requisitionHead", "orderHead", "ledger", "orderLine")
                .flatMap(part -> Stream.of(Arguments.of(part, null), Arguments.of(part, ""), Arguments.of(part, "UNKNOWN"))),
            Stream.of(
                Arguments.of("requirementDetail", null), Arguments.of("requirementDetail", 0L),
                Arguments.of("requirementDetail", 2L), Arguments.of("requisitionHead", "PARTIALLY_RECEIVED"),
                Arguments.of("orderHead", "ORDERED"), Arguments.of("ledger", "ORDER_TIMELY_SUPPLY"),
                Arguments.of("ledger", "FULFILLED")));
    }

    @ParameterizedTest(name = "四量DECIMAL原值拒绝：{0}/{1}")
    @MethodSource("invalidLedgerQuantities")
    void everyLedgerQuantityMustFitDecimal184ExactlyAndRemainNonnegative(String field, BigDecimal value) {
        Fixture fixture = field.equals("releasedBaseQty") ? Fixture.released() : new Fixture();
        set(fixture.ledger, field, value);
        if (field.equals("coveredDemandBaseQty") || field.equals("occupiedBaseQty")) {
            fixture.ledger.setCoveredDemandBaseQty(value);
            fixture.ledger.setOccupiedBaseQty(value);
        } else if (field.equals("releasedBaseQty")) {
            fixture.ledger.setCoveredDemandBaseQty(value);
        }
        denied(fixture);
    }

    private static Stream<Arguments> invalidLedgerQuantities() {
        return Stream.of("coveredDemandBaseQty", "occupiedBaseQty", "fulfilledBaseQty", "releasedBaseQty")
            .flatMap(field -> Stream.of(Arguments.of(field, null), Arguments.of(field, bd("-0.0001")),
                Arguments.of(field, bd("0.00001")), Arguments.of(field, bd("100000000000000.0000"))));
    }

    @ParameterizedTest(name = "覆盖量为正且守恒：{0}")
    @ValueSource(strings = {"zero-cover", "unequal-balance", "fulfilled-but-conserved", "partial-release-but-conserved"})
    void conservationAloneCannotCertifyUnimplementedFulfillmentOrRelease(String failure) {
        Fixture fixture = new Fixture();
        if (failure.equals("zero-cover")) {
            fixture.ledger.setCoveredDemandBaseQty(BigDecimal.ZERO);
            fixture.ledger.setOccupiedBaseQty(BigDecimal.ZERO);
        } else if (failure.equals("unequal-balance")) {
            fixture.ledger.setOccupiedBaseQty(bd("5.4291"));
        } else if (failure.equals("fulfilled-but-conserved")) {
            fixture.ledger.setOccupiedBaseQty(bd("4.4292"));
            fixture.ledger.setFulfilledBaseQty(bd("1"));
        } else {
            fixture.ledger.setOccupiedBaseQty(bd("4.4292"));
            fixture.ledger.setReleasedBaseQty(bd("1"));
        }
        denied(fixture);
    }

    @Test
    void decimalTrailingZeroScaleAndExplicitZeroVersionDoNotLoseExactFacts() {
        Fixture fixture = new Fixture();
        fixture.ledger.setCoveredDemandBaseQty(bd("5.429200"));
        fixture.ledger.setOccupiedBaseQty(bd("5.429200"));
        fixture.ledger.setVersion(0);
        Object snapshot = readValid(fixture);
        assertDecimal("5.4292", property(property(allocations(snapshot).get(0), "ledger"), "coveredDemandBaseQty"));
        assertEquals("0", versions(snapshot).get("allocation:" + ALLOCATION_ID));
    }

    @ParameterizedTest(name = "未认证下游事实拒绝：{0}")
    @MethodSource("unsupportedDownstream")
    void partialReceiptReturnFulfillmentOrTerminalOrderMustFailClosed(String label, Consumer<Fixture> mutation) {
        Fixture fixture = new Fixture();
        mutation.accept(fixture);
        denied(fixture);
    }

    private static Stream<Arguments> unsupportedDownstream() {
        List<Arguments> cases = new ArrayList<>();
        for (String state : List.of("PARTIALLY_RECEIVED", "RECEIVED", "CLOSED", "CANCELLED")) {
            cases.add(bad("订单状态" + state, f -> f.orderHead.setStatus(state)));
        }
        for (String state : List.of("PARTIAL_RECEIVED", "FULL_RECEIVED", "OVER_RECEIVED", "SHORT_RECEIVED")) {
            cases.add(bad("订单行收货状态" + state, f -> f.orderLine.setReceiveStatus(state)));
        }
        for (String field : List.of("receivedQuantity", "returnedQuantity", "invoicedQty")) {
            for (BigDecimal value : Arrays.asList(null, bd("0.0001"), bd("-0.0001"))) {
                cases.add(bad("下游原量" + field + "=" + value, f -> set(f.orderLine, field, value)));
            }
        }
        cases.add(bad("已承接账本残留释放时间", f -> f.ledger.setReleasedAt(Date.from(CHANGED_AT))));
        cases.add(bad("已承接账本残留释放原因", f -> f.ledger.setReleaseReason("合成矛盾释放事实")));
        cases.add(bad("申请已作废却仍承接", f -> f.requisitionHead.setStatus("CANCELLED")));
        cases.add(bad("申请未转单却宣称已承接", f -> f.requisitionHead.setStatus("DRAFT")));
        cases.add(bad("订单承接账本不能随申请关闭被释放", f -> {
            f.requisitionHead.setStatus("CLOSED");
            f.ledger.setAllocationStatus(RELEASED);
            f.ledger.setOccupiedBaseQty(BigDecimal.ZERO);
            f.ledger.setReleasedBaseQty(f.ledger.getCoveredDemandBaseQty());
            f.ledger.setReleasedAt(Date.from(CHANGED_AT));
            f.ledger.setReleaseReason("合成申请关闭，不能证明订单释放");
        }));
        return cases.stream();
    }

    @ParameterizedTest(name = "意图不得暗含承接/释放：{0}")
    @ValueSource(strings = {"order-id", "transfer-time", "released-at", "release-reason", "ordered-request", "closed-unreleased", "cancelled-unreleased"})
    void requestIntentCannotCarryOrderOrReleaseEvidence(String failure) {
        Fixture fixture = Fixture.intent();
        switch (failure) {
            case "order-id" -> fixture.ledger.setOrderLineId(ORDER_LINE_ID);
            case "transfer-time" -> fixture.ledger.setTransferTime(Date.from(CHANGED_AT));
            case "released-at" -> fixture.ledger.setReleasedAt(Date.from(CHANGED_AT));
            case "release-reason" -> fixture.ledger.setReleaseReason("合成释放事实");
            case "ordered-request" -> fixture.requisitionHead.setStatus("ORDERED");
            case "closed-unreleased" -> fixture.requisitionHead.setStatus("CLOSED");
            case "cancelled-unreleased" -> fixture.requisitionHead.setStatus("CANCELLED");
            default -> throw new AssertionError("未知意图反例");
        }
        denied(fixture);
    }

    @ParameterizedTest(name = "完整释放缺事实或矛盾：{0}")
    @ValueSource(strings = {"missing-time", "missing-reason", "blank-reason", "partial-release", "occupied", "fulfilled", "order-id", "transfer-time", "still-open"})
    void releasedIntentCannotBeInventedFromClosedHeaderOrPartialBalances(String failure) {
        Fixture fixture = Fixture.released();
        switch (failure) {
            case "missing-time" -> fixture.ledger.setReleasedAt(null);
            case "missing-reason" -> fixture.ledger.setReleaseReason(null);
            case "blank-reason" -> fixture.ledger.setReleaseReason(" ");
            case "partial-release" -> fixture.ledger.setReleasedBaseQty(bd("4.4292"));
            case "occupied" -> fixture.ledger.setOccupiedBaseQty(bd("0.0001"));
            case "fulfilled" -> fixture.ledger.setFulfilledBaseQty(bd("0.0001"));
            case "order-id" -> fixture.ledger.setOrderLineId(ORDER_LINE_ID);
            case "transfer-time" -> fixture.ledger.setTransferTime(Date.from(CHANGED_AT));
            case "still-open" -> fixture.requisitionHead.setStatus("DRAFT");
            default -> throw new AssertionError("未知释放反例");
        }
        denied(fixture);
    }

    @ParameterizedTest(name = "原始明细主键重复整池拒绝：{0}")
    @ValueSource(strings = {"ledger", "requirementDetail", "requirementHead", "requisitionLine", "requisitionHead", "orderLine", "orderHead"})
    void duplicatePrimaryKeysCannotBeSilentlyCollapsed(String part) {
        Fixture fixture = new Fixture();
        fixture.rows(part).add(fixture.bean(part));
        denied(fixture);
    }

    @ParameterizedTest(name = "重复承接/来源键冲突拒绝：{0}")
    @ValueSource(strings = {"allocation-key", "source-tuple", "same-order-line", "intent-and-order-double-count"})
    void overlappingLedgerRowsCannotCountTheSameInheritanceTwice(String collision) {
        Fixture fixture = new Fixture();
        PuRequirementAllocationLedger extra = new PuRequirementAllocationLedger();
        copyBean(fixture.ledger, extra);
        extra.setAllocationId(ALLOCATION_ID + 100L);
        extra.setAllocationKey("synthetic-extra-allocation-key");
        extra.setAllocationNo(2);
        if (collision.equals("allocation-key")) extra.setAllocationKey(fixture.ledger.getAllocationKey());
        if (collision.equals("source-tuple")) extra.setAllocationNo(fixture.ledger.getAllocationNo());
        if (collision.equals("intent-and-order-double-count")) {
            extra.setOrderLineId(null);
            extra.setTransferTime(null);
            extra.setAllocationStatus(REQUEST_INTENT);
        }
        fixture.allocations.add(extra);
        denied(fixture);
    }

    @Test
    void distinctRequisitionLineBindingsCanShareOneActualMrpDetailWithoutDuplicatingDetailRows() {
        Fixture fixture = Fixture.intent();
        PuRequirementAllocationLedger extra = new PuRequirementAllocationLedger();
        copyBean(fixture.ledger, extra);
        extra.setAllocationId(ALLOCATION_ID + 100L);
        extra.setAllocationKey("synthetic-second-intent-key");
        extra.setRequisitionLineId(REQUISITION_LINE_ID + 100L);
        RawRequisitionLine secondLine = new RawRequisitionLine();
        copyBean(fixture.requisitionLine, secondLine);
        secondLine.setLineId(REQUISITION_LINE_ID + 100L);
        fixture.allocations.add(extra);
        fixture.requisitionLines.add(secondLine);
        Object snapshot = readValid(fixture);
        assertEquals(2, allocations(snapshot).size());
        assertEquals(List.of(DETAIL_ID), fixture.idArguments.get("selectRequirementDetails"));
        assertEquals(List.of(REQUISITION_ID), fixture.idArguments.get("selectRequisitionHeads"));
        assertBindingOnly(snapshot);
    }

    @ParameterizedTest(name = "查询NULL结果不是空来源：{0}")
    @MethodSource("mapperQueries")
    void nullQueryResultMustNotBecomeAnEmptyOrKnownPool(String query) {
        Fixture fixture = new Fixture();
        fixture.queryOverrides.put(query, null);
        denied(fixture);
    }

    @ParameterizedTest(name = "来源列表NULL行拒绝：{0}")
    @ValueSource(strings = {"ledger", "requirementDetail", "requirementHead", "requisitionLine", "requisitionHead", "orderLine", "orderHead"})
    void nullEntityInAnySourceCollectionRejectsEntireMaterial(String part) {
        Fixture fixture = new Fixture();
        fixture.rows(part).set(0, null);
        denied(fixture);
    }
    @Test
    void rawEntityDateAndCollectionAliasesCannotMutateFrozenSnapshot() {
        Fixture fixture = new Fixture();
        Object snapshot = readValid(fixture);
        Object binding = allocations(snapshot).get(0);
        Map<String, Map<String, Object>> before = new LinkedHashMap<>();
        for (String part : PART_NAMES) {
            Object frozen = property(binding, part);
            assertBindingOnly(frozen);
            Map<String, Object> values = new LinkedHashMap<>();
            for (Field field : rawFields(fixture.bean(part).getClass())) {
                values.put(field.getName(), property(frozen, field.getName()));
                Object raw = beanProperty(fixture.bean(part), field.getName());
                if (raw instanceof Date date) date.setTime(1L);
            }
            before.put(part, values);
        }
        fixture.ledger.setAllocationId(1L);
        fixture.ledger.setOccupiedBaseQty(bd("999"));
        fixture.requisitionLine.setUnit("合成别名污染");
        fixture.orderLine.setOrderQuantity(bd("999"));
        for (String part : PART_NAMES) fixture.rows(part).clear();
        assertEquals(1, allocations(snapshot).size());
        for (String part : PART_NAMES) {
            before.get(part).forEach((name, value) -> assertEquals(value, property(property(binding, part), name), part + "." + name));
        }
        assertEquals("7", versions(snapshot).get("allocation:" + ALLOCATION_ID));
        assertThrows(UnsupportedOperationException.class, () -> allocations(snapshot).clear());
        assertThrows(UnsupportedOperationException.class, () -> allocations(snapshot).add(binding));
        assertThrows(UnsupportedOperationException.class, () -> versions(snapshot).put("forged", "1"));
        assertThrows(UnsupportedOperationException.class, () -> versions(snapshot).entrySet().iterator().next().setValue("forged"));
    }

    @Test
    void eachQueryResultIsFrozenBeforeLaterMapperCallsCanChangeEarlierBeans() {
        Fixture fixture = new Fixture();
        fixture.beforeQueries.put("selectRequisitionLines", () -> {
            assertTrue(fixture.queries.contains("selectAllocations"));
            fixture.ledger.getCreateTime().setTime(1L);
            fixture.ledger.setCoveredDemandBaseQty(bd("999"));
            fixture.ledger.setOccupiedBaseQty(bd("999"));
            fixture.ledger.setVersion(99);
        });
        fixture.beforeQueries.put("selectRequisitionHeads", () -> {
            fixture.requisitionLine.getRequiredDate().setTime(1L);
            fixture.requisitionLine.setApplyQuantity(bd("999"));
        });
        Object snapshot = readValid(fixture);
        Object binding = allocations(snapshot).get(0);
        assertEquals(CREATED_AT, property(property(binding, "ledger"), "createTime"));
        assertDecimal("5.4292", property(property(binding, "ledger"), "coveredDemandBaseQty"));
        assertEquals("7", versions(snapshot).get("allocation:" + ALLOCATION_ID));
        assertEquals(OUTSIDE_WINDOW, property(property(binding, "requisitionLine"), "requiredDate"));
        assertDecimal("510", property(property(binding, "requisitionLine"), "applyQuantity"));
    }

    @ParameterizedTest(name = "Mapper Runtime脱敏：{0}/{1}")
    @MethodSource("mapperRuntimeFailures")
    void everyQueryRuntimeIncludingServiceMessageDetailAndCauseHasNewFixedDiagnosis(String query, String kind) {
        Fixture fixture = new Fixture();
        RuntimeException original = sensitiveFailure(kind);
        for (String marker : SENSITIVE_MARKERS) assertTrue(stackOutput(original).contains(marker));
        fixture.failureQuery = query;
        fixture.queryFailure = original;
        ServiceException diagnosis;
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            diagnosis = assertThrows(ServiceException.class, () -> fixture.read(TENANT, MATERIAL_ID));
        }
        assertNotSame(original, diagnosis);
        assertEquals(MAPPER_FAILURE, diagnosis.getMessage());
        assertNull(diagnosis.getDetailMessage());
        assertNull(diagnosis.getCause());
        assertEquals(0, diagnosis.getSuppressed().length);
        String rendered = diagnosis.getMessage() + diagnosis.getDetailMessage() + diagnosis
            + diagnosis.getCause() + Arrays.toString(diagnosis.getSuppressed()) + stackOutput(diagnosis);
        for (String marker : SENSITIVE_MARKERS) assertFalse(rendered.contains(marker));
        assertTrue(stackOutput(original).contains("LINEAGE_SYNTH_PASSWORD"), "原异常不能被原地篡改");
        failedAt(fixture, query);
    }

    private static Stream<Arguments> mapperRuntimeFailures() {
        return mapperQueries().flatMap(query -> Stream.of("runtime", "mybatis", "connection", "grammar",
            "uncategorized", "service-message", "service-detail", "service-cause")
            .map(kind -> Arguments.of(query, kind)));
    }

    /** 轻量合成 Error；不分配巨量内存，不停止真实线程，核对逃逸对象身份。 */
    @SuppressWarnings("removal")
    @ParameterizedTest(name = "致命Error原样逃逸：{0}/{1}")
    @MethodSource("fatalFailures")
    void virtualMachineErrorAndThreadDeathAreNeverSanitizedWrappedOrSwallowed(String query, boolean vm) {
        Fixture fixture = new Fixture();
        Error original = vm ? new SyntheticVirtualMachineError() : new ThreadDeath();
        fixture.failureQuery = query;
        fixture.fatalFailure = original;
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            assertSame(original, assertThrows(Error.class, () -> fixture.read(TENANT, MATERIAL_ID)));
        }
        failedAt(fixture, query);
    }

    private static Stream<Arguments> fatalFailures() {
        return mapperQueries().flatMap(query -> Stream.of(Arguments.of(query, true), Arguments.of(query, false)));
    }

    private static Stream<String> mapperQueries() {
        return QUERY_NAMES.stream();
    }

    @ParameterizedTest(name = "真实MyBatis投影合同：{0}")
    @MethodSource("rowContracts")
    void realRegisteredResultMapPreservesRawFieldsIdsQuantitiesAndEveryDate(RowContract row) throws SQLException {
        Configuration configuration = mappedConfiguration();
        MappedStatement statement = configuration.getMappedStatement(MAPPER + "." + row.method());
        assertEquals(row.type(), statement.getResultMaps().get(0).getType());
        Map<String, Object> values = sampleValues(row);
        Object actual = mappedRow(configuration, row.method(), projectedValues(values));
        assertInstanceOf(row.type(), actual);
        values.forEach((field, value) -> assertEquals(value, beanProperty(actual, field), row.method() + "." + field));
        assertTrue(boundSql(statement).getSql().toLowerCase(Locale.ROOT).contains(table(row.method())));
        assertFalse(configuration.isMapUnderscoreToCamelCase(), "合同不依赖全局下划线自动映射");
    }

    private static Stream<RowContract> rowContracts() {
        return ROWS.stream();
    }

    @Test
    void databaseClockRecordIsMappedFromDatabaseDecimalSecondsAndRawSessionZone() throws SQLException {
        Configuration configuration = mappedConfiguration();
        MappedStatement statement = configuration.getMappedStatement(MAPPER + ".selectDatabaseClock");
        Object clock = mappedRow(configuration, "selectDatabaseClock",
            new LinkedHashMap<>(Map.of("epoch_seconds", epoch(SNAPSHOT_AT), "database_time_zone", "+08:00")));
        assertEquals(statement.getResultMaps().get(0).getType(), clock.getClass());
        assertTrue(clock.getClass().isRecord());
        assertEquals(epoch(SNAPSHOT_AT), property(clock, "epochSeconds"));
        assertEquals("+08:00", property(clock, "databaseTimeZone"));
    }

    @ParameterizedTest(name = "Date专用EpochMillis映射，NULL和无效值不回填：{0}")
    @MethodSource("dateColumns")
    void everyWallDatetimeUsesExistingScopedHandlerAndNeverJvmTimestampConversion(DateColumn column) throws SQLException {
        Configuration configuration = mappedConfiguration();
        MappedStatement statement = configuration.getMappedStatement(MAPPER + "." + column.row().method());
        ResultMapping mapping = statement.getResultMaps().get(0).getResultMappings().stream()
            .filter(candidate -> column.property().equals(candidate.getProperty())).findFirst().orElse(null);
        assertNotNull(mapping, "必须显式映射日期：" + column);
        assertEquals(column.alias(), mapping.getColumn());
        assertEquals(Date.class, mapping.getJavaType());
        assertEquals(ErpPurchaseCatalogSourceMapper.EpochMillisDateTypeHandler.class, mapping.getTypeHandler().getClass(),
            "复用已存在的 scoped handler，不注册全局 Converter");
        String sql = boundSql(statement).getSql().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        assertTrue(sql.contains("unix_timestamp(" + snake(column.property()) + ")"), column.toString());
        assertTrue(sql.matches("(?s).*unix_timestamp\\(" + snake(column.property())
            + "\\)\\s*\\*\\s*1000\\s+as\\s+" + column.alias().toLowerCase(Locale.ROOT) + ".*"));
        Map<String, Object> values = projectedValues(sampleValues(column.row()));
        values.put(column.alias(), null);
        assertNull(beanProperty(mappedRow(configuration, column.row().method(), values), column.property()));
        BigDecimal millis = bd("1790755200123.000");
        values.put(column.alias(), millis);
        assertEquals(new Date(millis.longValueExact()),
            beanProperty(mappedRow(configuration, column.row().method(), values), column.property()));
        for (BigDecimal invalid : List.of(BigDecimal.ZERO, bd("-1"), bd("1790755200123.1"), bd("1E30"))) {
            values.put(column.alias(), invalid);
            try {
                assertNull(beanProperty(mappedRow(configuration, column.row().method(), values), column.property()),
                    "无效日期不能被截断或回绕：" + column);
            } catch (SQLException expected) {
                /** 允许解码器明确拒绝；不能拿编程错误或致命 Error 充当合同满足。 */
            } catch (ResultMapException expected) {
                assertInstanceOf(SQLException.class, expected.getCause());
            }
        }
    }

    private static Stream<DateColumn> dateColumns() {
        return ROWS.stream().filter(row -> row.type() != BdMaterial.class)
            .flatMap(row -> rawFields(row.type()).stream().filter(field -> field.getType() == Date.class)
                .map(field -> new DateColumn(row, field.getName(), field.getName() + "EpochMillis")));
    }

    @ParameterizedTest(name = "实际NULL版本/四量映射不变零：{0}")
    @ValueSource(strings = {"selectAllocations", "selectRequisitionHeads"})
    void realResultMapRetainsUnknownVersionsAndLedgerBalances(String query) throws SQLException {
        RowContract row = ROWS.stream().filter(candidate -> candidate.method().equals(query)).findFirst().orElseThrow();
        Map<String, Object> values = sampleValues(row);
        values.put("version", null);
        if (query.equals("selectAllocations")) {
            for (String field : List.of("coveredDemandBaseQty", "occupiedBaseQty", "fulfilledBaseQty", "releasedBaseQty")) {
                values.put(field, null);
            }
        }
        Object mapped = mappedRow(mappedConfiguration(), query, projectedValues(values));
        assertNull(beanProperty(mapped, "version"));
        if (query.equals("selectAllocations")) {
            for (String field : List.of("coveredDemandBaseQty", "occupiedBaseQty", "fulfilledBaseQty", "releasedBaseQty")) {
                assertNull(beanProperty(mapped, field));
            }
        }
        assertEquals(values.get(row.id()), beanProperty(mapped, row.id()), "NULL不是无结果行");
    }

    /** 仅登记 Mapper；必须维持原有全局 Date 解码器身份，不改任何全局配置。 */
    private static Configuration mappedConfiguration() {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(false);
        TypeHandler<Date> before = configuration.getTypeHandlerRegistry().getTypeHandler(Date.class);
        configuration.addMapper(requiredClass(MAPPER));
        assertSame(before, configuration.getTypeHandlerRegistry().getTypeHandler(Date.class));
        return configuration;
    }

    private static BoundSql boundSql(MappedStatement statement) {
        return statement.getBoundSql(Map.of("tenantId", TENANT, "materialId", MATERIAL_ID,
            "headIds", List.of(REQUIREMENT_ID), "detailIds", List.of(DETAIL_ID)));
    }

    /** 真实 ResultSet 处理器处理合成一行；不调用 JDBC execute 或取得真实连接。 */
    private static Object mappedRow(Configuration configuration, String query, Map<String, Object> columns) throws SQLException {
        MappedStatement statement = configuration.getMappedStatement(MAPPER + "." + query);
        ResultSet result = syntheticRow(columns);
        Statement jdbc = mock(Statement.class);
        when(jdbc.getResultSet()).thenReturn(result);
        when(jdbc.getUpdateCount()).thenReturn(-1);
        Connection connection = mock(Connection.class);
        when(connection.getMetaData()).thenReturn(mock(DatabaseMetaData.class));
        when(jdbc.getConnection()).thenReturn(connection);
        BoundSql sql = boundSql(statement);
        ParameterHandler parameters = configuration.newParameterHandler(statement, sql.getParameterObject(), sql);
        DefaultResultSetHandler handler = new DefaultResultSetHandler(mock(Executor.class), statement,
            parameters, null, sql, RowBounds.DEFAULT);
        List<Object> rows = handler.handleResultSets(jdbc);
        assertEquals(1, rows.size(), "保留真实身份列，不能通过丢行伪装NULL映射通过");
        return rows.get(0);
    }

    /** 包含真实字段名与原始数字；DATETIME不得经getTimestamp悄悄借用JVM时区。 */
    private static ResultSet syntheticRow(Map<String, Object> values) throws SQLException {
        List<String> columns = List.copyOf(values.keySet());
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(metadata.getColumnCount()).thenReturn(columns.size());
        for (int index = 1; index <= columns.size(); index++) {
            String name = columns.get(index - 1);
            Object value = values.get(name);
            int type = name.endsWith("EpochMillis") || value instanceof BigDecimal ? Types.DECIMAL
                : value instanceof Long ? Types.BIGINT : value instanceof Integer ? Types.INTEGER : Types.VARCHAR;
            when(metadata.getColumnLabel(index)).thenReturn(name);
            when(metadata.getColumnName(index)).thenReturn(name);
            when(metadata.getColumnType(index)).thenReturn(type);
            when(metadata.getColumnClassName(index)).thenReturn(value == null ? Object.class.getName() : value.getClass().getName());
        }
        AtomicInteger cursor = new AtomicInteger();
        AtomicBoolean wasNull = new AtomicBoolean();
        AtomicBoolean closed = new AtomicBoolean();
        return mock(ResultSet.class, invocation -> {
            String method = invocation.getMethod().getName();
            if (method.equals("getMetaData")) return metadata;
            if (method.equals("next")) return cursor.getAndIncrement() == 0;
            if (method.equals("getType")) return ResultSet.TYPE_FORWARD_ONLY;
            if (method.equals("wasNull")) return wasNull.get();
            if (method.equals("isClosed")) return closed.get();
            if (method.equals("close")) { closed.set(true); return null; }
            if (method.startsWith("get") && invocation.getArguments().length > 0
                && (invocation.getArgument(0) instanceof String || invocation.getArgument(0) instanceof Integer)) {
                Object key = invocation.getArgument(0);
                String column = key instanceof Integer index ? columns.get(index - 1) : (String) key;
                assertTrue(values.containsKey(column), "不得读取固定SELECT投影以外的字段：" + column);
                Object value = values.get(column);
                wasNull.set(value == null);
                return switch (method) {
                    case "getObject" -> value;
                    case "getString" -> value == null ? null : value.toString();
                    case "getBigDecimal" -> value == null ? null : value instanceof BigDecimal number
                        ? number : new BigDecimal(value.toString());
                    case "getLong" -> value == null ? 0L : ((Number) value).longValue();
                    case "getInt" -> value == null ? 0 : ((Number) value).intValue();
                    case "getShort" -> value == null ? (short) 0 : ((Number) value).shortValue();
                    case "getByte" -> value == null ? (byte) 0 : ((Number) value).byteValue();
                    case "getTimestamp", "getDate" -> throw new AssertionError("日期必须从数据库EpochMillis列读取");
                    default -> Answers.RETURNS_DEFAULTS.answer(invocation);
                };
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
    }
    private static Map<String, Object> sampleValues(RowContract row) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (row.type() == BdMaterial.class) {
            values.put("tenantId", TENANT);
            values.put("materialId", MATERIAL_ID);
            values.put("baseUomId", BASE_ID);
            values.put("status", 1L);
            values.put("delFlag", "0");
            return values;
        }
        for (Field field : rawFields(row.type())) {
            Class<?> type = field.getType();
            Object value = type == Long.class ? MATERIAL_ID : type == Integer.class ? 7
                : type == BigDecimal.class ? bd("1.2345") : type == Date.class ? Date.from(CREATED_AT)
                : type == String.class ? "合成字段-" + field.getName() : null;
            assertNotNull(value, "新类型需明确映射合同：" + field);
            values.put(field.getName(), value);
        }
        return values;
    }

    private static Map<String, Object> projectedValues(Map<String, Object> fields) {
        Map<String, Object> projected = new LinkedHashMap<>();
        fields.forEach((name, value) -> projected.put(value instanceof Date ? name + "EpochMillis" : name,
            value instanceof Date date ? BigDecimal.valueOf(date.getTime()) : value));
        return projected;
    }

    private static String table(String query) {
        return switch (query) {
            case "selectMaterial" -> "bd_material";
            case "selectAllocations" -> "pu_requirement_allocation_ledger";
            case "selectRequisitionLines" -> "pu_purchase_requisition_line";
            case "selectRequisitionHeads" -> "pu_purchase_requisition";
            case "selectOrderLines" -> "pu_purchase_order_line";
            case "selectOrderHeads" -> "pu_purchase_order";
            case "selectRequirementDetails" -> "prd_material_requirement_detail";
            case "selectRequirementHeads" -> "prd_material_requirement";
            default -> throw new AssertionError("未知固定来源表");
        };
    }

    private static String snake(String field) {
        return field.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    private static void failedAt(Fixture fixture, String query) {
        assertEquals(query, fixture.queries.get(fixture.queries.size() - 1), "不能吞错后继续查询");
        assertEquals(1, fixture.queries.stream().filter(query::equals).count(), "不能隐式重试");
        assertEquals(0, fixture.connections.stream().mapToInt(connection -> connection.commits).sum());
        assertEquals(1, fixture.connections.stream().mapToInt(connection -> connection.rollbacks).sum());
        assertTrue(fixture.connections.stream().allMatch(connection -> connection.closed));
    }

    private static RuntimeException sensitiveFailure(String kind) {
        String sql = "SELECT LINEAGE_SYNTH_SQL FROM synthetic_lineage";
        String payload = sql + "; connection=LINEAGE_SYNTH_CONNECTION password=LINEAGE_SYNTH_PASSWORD bearer=LINEAGE_SYNTH_BEARER";
        SQLException jdbc = new SQLException(payload, "HY000", 0);
        jdbc.addSuppressed(new IllegalStateException(payload));
        RuntimeException failure = switch (kind) {
            case "runtime" -> new RuntimeException(payload, jdbc);
            case "mybatis" -> new PersistenceException(payload, jdbc);
            case "connection" -> new DataAccessResourceFailureException(payload, jdbc);
            case "grammar" -> new BadSqlGrammarException(payload, sql, jdbc);
            case "uncategorized" -> new UncategorizedSQLException(payload, sql, jdbc);
            case "service-message" -> new ServiceException(payload);
            case "service-detail" -> new ServiceException("合成查询故障").setDetailMessage(payload);
            case "service-cause" -> {
                ServiceException service = new ServiceException("合成查询故障");
                service.initCause(jdbc);
                yield service;
            }
            default -> throw new AssertionError("未知合成异常种类");
        };
        failure.addSuppressed(new IllegalStateException(payload));
        failure.setStackTrace(new StackTraceElement[]{new StackTraceElement("synthetic.LINEAGE_SYNTH_SQL",
            "LINEAGE_SYNTH_CONNECTION", "LINEAGE_SYNTH_PASSWORD_LINEAGE_SYNTH_BEARER.java", 1)});
        return failure;
    }

    private static String stackOutput(Throwable failure) {
        StringWriter output = new StringWriter();
        failure.printStackTrace(new PrintWriter(output));
        return output.toString();
    }

    /** 查询外的合同拒绝不应被误替换成 Mapper 故障；明确检查代理回滚而非仅检查异常。 */
    private static void denied(Fixture fixture) {
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            ServiceException rejection = assertThrows(ServiceException.class, () -> fixture.read(TENANT, MATERIAL_ID));
            assertNotEquals(MAPPER_FAILURE, rejection.getMessage(), "来源校验失败不是 Mapper Runtime 故障");
        }
        assertEquals(0, fixture.connections.stream().mapToInt(connection -> connection.commits).sum());
        assertEquals(1, fixture.connections.stream().mapToInt(connection -> connection.rollbacks).sum());
    }

    private static Object readValid(Fixture fixture) {
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            return assertDoesNotThrow(() -> fixture.read(TENANT, MATERIAL_ID));
        }
    }

    private static MockedStatic<TenantHelper> tenant(boolean enabled, String current) {
        MockedStatic<TenantHelper> helper = mockStatic(TenantHelper.class);
        helper.when(TenantHelper::isEnable).thenReturn(enabled);
        helper.when(TenantHelper::getTenantId).thenReturn(current);
        return helper;
    }

    /** 采购申请无独立枚举，直接核对服务接口真实状态常量，不借用采购订单集合。 */
    private static Set<String> actualRequisitionStates() {
        Class<?> type = com.nexus.erp.pu.service.IPuPurchaseRequisitionService.class;
        Set<String> states = Arrays.stream(type.getFields()).filter(field -> field.getName().startsWith("STATUS_"))
            .map(field -> assertDoesNotThrow(() -> (String) field.get(null))).collect(Collectors.toSet());
        assertEquals(Set.of("DRAFT", "SUBMITTED", "APPROVING", "APPROVED", "ORDERED", "CLOSED", "CANCELLED", "REJECTED"), states);
        return states;
    }

    private static void assertFrozenBean(Object raw, Object frozen) {
        assertNotNull(raw);
        assertNotNull(frozen);
        assertBindingOnly(frozen);
        for (Field field : rawFields(raw.getClass())) {
            Object expected = beanProperty(raw, field.getName());
            if (expected instanceof Date date) expected = date.toInstant();
            assertEquals(expected, property(frozen, field.getName()), field.getName());
        }
    }

    /** 只准冻结的原始事实；不准出现库存完整、可执行或 allocator 的可信布尔资格。 */
    private static void assertBindingOnly(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Instant) return;
        if (value instanceof Collection<?> collection) {
            collection.forEach(ErpPurchaseAllocationLineageReaderTest::assertBindingOnly);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> { assertBindingOnly(key); assertBindingOnly(item); });
            return;
        }
        assertTrue(value.getClass().isRecord(), "不能泄漏可变 Raw DTO、Date 或领域实体：" + value.getClass());
        assertFalse(value.getClass().getName().startsWith(ErpPurchaseDemandAllocator.class.getName()));
        Set<String> forbidden = Set.of("verified", "allocationEligible", "globalPoolComplete", "inventoryComplete",
            "executable", "requestable", "requestableBaseQty", "netGapBase", "trusted");
        for (RecordComponent component : value.getClass().getRecordComponents()) {
            assertFalse(forbidden.contains(component.getName()), component.getName());
            assertFalse(component.getType() == boolean.class || component.getType() == Boolean.class,
                "本片段不颁发任何布尔可信旗号");
            assertFalse(Date.class.isAssignableFrom(component.getType()));
            assertBindingOnly(property(value, component.getName()));
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> allocations(Object snapshot) {
        return (List<Object>) assertInstanceOf(List.class, property(snapshot, "allocations"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> versions(Object snapshot) {
        return (Map<String, String>) assertInstanceOf(Map.class, property(snapshot, "sourceVersions"));
    }

    private static List<Field> rawFields(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).filter(field -> !Modifier.isStatic(field.getModifiers())
            && !field.isSynthetic()).toList();
    }

    private static Object beanProperty(Object bean, String name) {
        String getter = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        return invoke(assertDoesNotThrow(() -> bean.getClass().getMethod(getter)), bean);
    }

    private static Object property(Object bean, String name) {
        assertNotNull(bean, name);
        return invoke(assertDoesNotThrow(() -> bean.getClass().getMethod(name)), bean);
    }

    private static void set(Object bean, String field, Object value) {
        String setter = "set" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
        Method method = Arrays.stream(bean.getClass().getMethods())
            .filter(candidate -> candidate.getName().equals(setter) && candidate.getParameterCount() == 1)
            .findFirst().orElseThrow(() -> new AssertionError("缺真实字段Setter：" + setter));
        invoke(method, bean, new Object[]{value});
    }

    private static void copyBean(Object source, Object target) {
        for (Field field : rawFields(source.getClass())) {
            Object value = beanProperty(source, field.getName());
            set(target, field.getName(), value instanceof Date date ? new Date(date.getTime()) : value);
        }
    }

    private static Class<?> requiredClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException missing) {
            return fail("尚缺本轮来源实现（有效RED应为此断言，不是javac缺类）：" + name, missing);
        }
    }

    private static Method entry() {
        return assertDoesNotThrow(() -> requiredClass(READER).getMethod("read", String.class, Long.class));
    }

    private static Method query(String name, Class<?>... parameters) {
        return assertDoesNotThrow(() -> requiredClass(MAPPER).getMethod(name, parameters));
    }

    private static String sql(Method method) {
        Select select = method.getAnnotation(Select.class);
        assertNotNull(select, method.getName());
        return String.join(" ", select.value()).replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static Object invoke(Method method, Object target, Object... arguments) {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw new AssertionError("归属读取不应抛受检异常", failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("归属合同反射调用失败", failure);
        }
    }

    private static Arguments bad(String label, Consumer<Fixture> mutation) {
        return Arguments.of(label, mutation);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static BigDecimal epoch(Instant value) {
        return BigDecimal.valueOf(value.getEpochSecond()).add(BigDecimal.valueOf(value.getNano(), 9));
    }

    private static void assertDecimal(String expected, Object actual) {
        assertEquals(0, bd(expected).compareTo(assertInstanceOf(BigDecimal.class, actual)));
    }

    private record RowContract(String method, Class<?> type, String id) { }
    private record DateColumn(RowContract row, String property, String alias) { }

    private static final class SyntheticVirtualMachineError extends VirtualMachineError {
        private static final long serialVersionUID = 1L;
    }

    /** 现有 Raw/账本实体提供未解释事实；核验逻辑只能由待实现 reader 执行。 */
    private static final class Fixture {
        private BdMaterial material = new BdMaterial();
        private final PuRequirementAllocationLedger ledger = new PuRequirementAllocationLedger();
        private final RawRequirementDetail detail = new RawRequirementDetail();
        private final RawRequirementHead requirementHead = new RawRequirementHead();
        private final RawRequisitionLine requisitionLine = new RawRequisitionLine();
        private final RawRequisitionHead requisitionHead = new RawRequisitionHead();
        private final RawOrderLine orderLine = new RawOrderLine();
        private final RawOrderHead orderHead = new RawOrderHead();
        private final List<PuRequirementAllocationLedger> allocations = new ArrayList<>();
        private final List<RawRequirementDetail> details = new ArrayList<>();
        private final List<RawRequirementHead> requirementHeads = new ArrayList<>();
        private final List<RawRequisitionLine> requisitionLines = new ArrayList<>();
        private final List<RawRequisitionHead> requisitionHeads = new ArrayList<>();
        private final List<RawOrderLine> orderLines = new ArrayList<>();
        private final List<RawOrderHead> orderHeads = new ArrayList<>();
        private BigDecimal epochSeconds = epoch(SNAPSHOT_AT);
        private String databaseTimeZone = "+08:00";
        private boolean missingClock;
        private String failureQuery;
        private RuntimeException queryFailure;
        private Error fatalFailure;
        private final Map<String, Object> queryOverrides = new LinkedHashMap<>();
        private final Map<String, Runnable> beforeQueries = new LinkedHashMap<>();
        private final List<String> queries = new ArrayList<>();
        private final Map<String, List<Long>> idArguments = new LinkedHashMap<>();
        private final List<JdbcProbe> connections = new ArrayList<>();
        private final List<Connection> queryConnections = new ArrayList<>();
        private final AbstractRoutingDataSource routing;
        private final DataSourceTransactionManager transactions;
        private final Object mapper;
        private final Object target;
        private final Object reader;

        private Fixture() {
            material.setTenantId(TENANT);
            material.setMaterialId(MATERIAL_ID);
            material.setBaseUomId(BASE_ID);
            material.setStatus(1L);
            material.setDelFlag("0");
            ledger.setAllocationId(ALLOCATION_ID);
            ledger.setTenantId(TENANT);
            ledger.setMaterialId(MATERIAL_ID);
            ledger.setSourceType("MRP_DETAIL");
            ledger.setDetailId(DETAIL_ID);
            ledger.setRequisitionLineId(REQUISITION_LINE_ID);
            ledger.setAllocationNo(1);
            ledger.setOrderLineId(ORDER_LINE_ID);
            ledger.setBaseUomId(BASE_ID);
            ledger.setCoveredDemandBaseQty(bd("5.4292"));
            ledger.setOccupiedBaseQty(bd("5.4292"));
            ledger.setFulfilledBaseQty(bd("0.0000"));
            ledger.setReleasedBaseQty(bd("0.0000"));
            ledger.setAllocationStatus(ORDER_COMMITTED);
            ledger.setAllocationKey("synthetic-allocation-key");
            ledger.setDecisionIdempotencyKey(DECISION_KEY);
            ledger.setVersion(7);
            ledger.setCreateTime(Date.from(CREATED_AT));
            ledger.setTransferTime(Date.from(CHANGED_AT));
            ledger.setUpdateTime(Date.from(CHANGED_AT));
            detail.setDetailId(DETAIL_ID);
            detail.setTenantId(TENANT);
            detail.setRequirementId(REQUIREMENT_ID);
            detail.setMaterialId(MATERIAL_ID);
            detail.setUnit(Long.toString(BASE_ID));
            detail.setSourceType("MRP");
            detail.setRequiredQuantity(bd("5.4292"));
            detail.setIssuedQuantity(bd("0"));
            detail.setStockQuantity(bd("0"));
            detail.setShortageQuantity(bd("5.4292"));
            detail.setRequiredDate(Date.from(OUTSIDE_WINDOW));
            detail.setStatus(1L);
            detail.setDelFlag("0");
            detail.setBackflushFlag(0);
            detail.setActualConsumedQty(bd("0"));
            requirementHead.setRequirementId(REQUIREMENT_ID);
            requirementHead.setTenantId(TENANT);
            requirementHead.setRequirementNo("MRP-SYNTHETIC");
            requirementHead.setMaterialId(MATERIAL_ID + 900L);
            requirementHead.setStatus("CONFIRMED");
            requirementHead.setDelFlag("0");
            requirementHead.setRequirementDate(Date.from(CREATED_AT));
            requirementHead.setConfirmedTime(Date.from(CHANGED_AT));
            requisitionLine.setLineId(REQUISITION_LINE_ID);
            requisitionLine.setTenantId(TENANT);
            requisitionLine.setRequisitionId(REQUISITION_ID);
            requisitionLine.setMaterialId(MATERIAL_ID);
            requisitionLine.setUnit("历史个单位");
            requisitionLine.setApplyQuantity(bd("510"));
            requisitionLine.setApprovedQuantity(null);
            requisitionLine.setShortageQuantity(bd("5.4292"));
            requisitionLine.setRequiredDate(Date.from(OUTSIDE_WINDOW));
            requisitionLine.setSourceType("AI_DECISION");
            requisitionLine.setSourceId(42L);
            requisitionLine.setDelFlag("0");
            requisitionHead.setRequisitionId(REQUISITION_ID);
            requisitionHead.setTenantId(TENANT);
            requisitionHead.setRequisitionNo("REQ-SYNTHETIC");
            requisitionHead.setSourceType("AI_DECISION");
            requisitionHead.setStatus("ORDERED");
            requisitionHead.setRequisitionDate(Date.from(CREATED_AT));
            requisitionHead.setRequiredDate(Date.from(OUTSIDE_WINDOW));
            requisitionHead.setDecisionIdempotencyKey(DECISION_KEY);
            requisitionHead.setVersion(9);
            requisitionHead.setDelFlag("0");
            orderLine.setLineId(ORDER_LINE_ID);
            orderLine.setTenantId(TENANT);
            orderLine.setOrderId(ORDER_ID);
            orderLine.setRequisitionLineId(REQUISITION_LINE_ID);
            orderLine.setMaterialId(MATERIAL_ID);
            orderLine.setUnit("历史个单位");
            orderLine.setOrderQuantity(bd("510"));
            orderLine.setReceivedQuantity(bd("0.0000"));
            orderLine.setReturnedQuantity(bd("0.0000"));
            orderLine.setInvoicedQty(bd("0.0000"));
            orderLine.setReceiveStatus("PENDING");
            orderLine.setDelFlag("0");
            orderHead.setOrderId(ORDER_ID);
            orderHead.setTenantId(TENANT);
            orderHead.setOrderNo("PO-SYNTHETIC");
            orderHead.setRequisitionId(REQUISITION_ID);
            orderHead.setVendorId(MATERIAL_ID + 901L);
            orderHead.setStatus("DRAFT");
            orderHead.setOrderDate(Date.from(CHANGED_AT));
            orderHead.setExpectedArrivalDate(null);
            orderHead.setDelFlag("0");
            allocations.add(ledger);
            details.add(detail);
            requirementHeads.add(requirementHead);
            requisitionLines.add(requisitionLine);
            requisitionHeads.add(requisitionHead);
            orderLines.add(orderLine);
            orderHeads.add(orderHead);
            for (String part : PART_NAMES) {
                if (!part.equals("ledger")) {
                    set(bean(part), "createTime", Date.from(CREATED_AT));
                    set(bean(part), "updateTime", Date.from(CHANGED_AT));
                }
            }
            routing = new AbstractRoutingDataSource() {
                @Override
                protected Object determineCurrentLookupKey() {
                    return DynamicDataSourceContextHolder.peek();
                }
            };
            DataSource master = offlineDataSource("master");
            DataSource erp = offlineDataSource("erp");
            routing.setTargetDataSources(Map.of("master", master, "erp", erp));
            routing.setDefaultTargetDataSource(master);
            routing.setLenientFallback(false);
            routing.afterPropertiesSet();
            transactions = new DataSourceTransactionManager(routing);
            Class<?> mapperType = requiredClass(MAPPER);
            mapper = mock(mapperType, invocation -> {
                if (invocation.getMethod().getDeclaringClass() == Object.class) return Answers.RETURNS_DEFAULTS.answer(invocation);
                String name = invocation.getMethod().getName();
                assertTrue(QUERY_NAMES.contains(name), "不得读库存、供应条款或写入任何来源");
                assertEquals("erp", DynamicDataSourceContextHolder.peek());
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                assertEquals(Integer.valueOf(TransactionDefinition.ISOLATION_REPEATABLE_READ),
                    TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
                ConnectionHolder holder = assertInstanceOf(ConnectionHolder.class,
                    TransactionSynchronizationManager.getResource(routing));
                Connection connection = holder.getConnection();
                JdbcProbe probe = connections.stream().filter(candidate -> candidate.connection == connection).findFirst().orElseThrow();
                assertEquals("erp", probe.route);
                assertTrue(probe.readOnly);
                assertEquals(Connection.TRANSACTION_REPEATABLE_READ, probe.isolation);
                assertFalse(probe.autoCommit);
                queryConnections.add(connection);
                queries.add(name);
                Runnable before = beforeQueries.get(name);
                if (before != null) before.run();
                if (name.equals(failureQuery)) {
                    if (queryFailure != null) throw queryFailure;
                    if (fatalFailure != null) throw fatalFailure;
                }
                if (queryOverrides.containsKey(name)) return queryOverrides.get(name);
                if (name.equals("selectDatabaseClock")) {
                    if (missingClock) return null;
                    return assertDoesNotThrow(() -> invocation.getMethod().getReturnType()
                        .getConstructor(BigDecimal.class, String.class).newInstance(epochSeconds, databaseTimeZone));
                }
                assertEquals(TENANT, invocation.getArgument(0));
                Object scope = invocation.getArgument(1);
                if (scope instanceof List<?> ids) {
                    assertFalse(ids.isEmpty(), "无引用必须跳过补读，不能发送空IN");
                    assertTrue(ids.stream().allMatch(id -> id instanceof Long number && number > 0));
                    assertEquals(ids.stream().distinct().sorted().toList(), ids, "补读ID需去重并排序");
                    idArguments.put(name, ids.stream().map(Long.class::cast).toList());
                } else {
                    assertEquals(Long.valueOf(MATERIAL_ID), scope);
                }
                return switch (name) {
                    case "selectMaterial" -> material;
                    case "selectAllocations" -> allocations;
                    case "selectRequisitionLines" -> requisitionLines;
                    case "selectOrderLines" -> orderLines;
                    case "selectRequisitionHeads" -> requisitionHeads;
                    case "selectOrderHeads" -> orderHeads;
                    case "selectRequirementDetails" -> details;
                    case "selectRequirementHeads" -> requirementHeads;
                    default -> throw new AssertionError("未知固定查询");
                };
            });
            target = assertDoesNotThrow(() -> requiredClass(READER).getConstructor(mapperType).newInstance(mapper));
            verifyNoInteractions(mapper);
            reader = proxy(true, true);
        }

        private Object proxy(boolean dataSourceAdvice, boolean transactionAdvice) {
            ProxyFactory proxy = new ProxyFactory(target);
            proxy.setProxyTargetClass(true);
            if (dataSourceAdvice) {
                proxy.addAdvisor(new DynamicDataSourceAnnotationAdvisor(
                    new DynamicDataSourceAnnotationInterceptor(true, new DsSpelExpressionProcessor()), DS.class));
            }
            if (transactionAdvice) {
                TransactionInterceptor transaction = new TransactionInterceptor();
                transaction.setTransactionManager(transactions);
                transaction.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
                proxy.addAdvice(transaction);
            }
            return proxy.getProxy();
        }

        private DataSource offlineDataSource(String route) {
            return mock(DataSource.class, invocation -> {
                if (invocation.getMethod().getName().equals("getConnection")) {
                    assertEquals(route, DynamicDataSourceContextHolder.peek() == null ? "master" : DynamicDataSourceContextHolder.peek());
                    JdbcProbe probe = new JdbcProbe(route);
                    connections.add(probe);
                    return probe.connection;
                }
                return Answers.RETURNS_DEFAULTS.answer(invocation);
            });
        }

        private Object read(String tenant, Long material) {
            return invoke(entry(), reader, tenant, material);
        }

        private Object directRead() {
            return invoke(entry(), target, TENANT, MATERIAL_ID);
        }

        private Object bean(String part) {
            return switch (part) {
                case "ledger" -> ledger;
                case "requirementDetail" -> detail;
                case "requirementHead" -> requirementHead;
                case "requisitionLine" -> requisitionLine;
                case "requisitionHead" -> requisitionHead;
                case "orderLine" -> orderLine;
                case "orderHead" -> orderHead;
                default -> throw new AssertionError("未知绑定部分");
            };
        }

        @SuppressWarnings("unchecked")
        private List<Object> rows(String part) {
            List<?> result = switch (part) {
                case "ledger" -> allocations;
                case "requirementDetail" -> details;
                case "requirementHead" -> requirementHeads;
                case "requisitionLine" -> requisitionLines;
                case "requisitionHead" -> requisitionHeads;
                case "orderLine" -> orderLines;
                case "orderHead" -> orderHeads;
                default -> throw new AssertionError("未知来源集合");
            };
            return (List<Object>) result;
        }

        private static Fixture intent() {
            Fixture fixture = new Fixture();
            fixture.orderLines.clear();
            fixture.orderHeads.clear();
            fixture.ledger.setOrderLineId(null);
            fixture.ledger.setTransferTime(null);
            fixture.ledger.setAllocationStatus(REQUEST_INTENT);
            fixture.requisitionHead.setStatus("DRAFT");
            return fixture;
        }

        private static Fixture released() {
            Fixture fixture = intent();
            fixture.requisitionHead.setStatus("CLOSED");
            fixture.ledger.setAllocationStatus(RELEASED);
            fixture.ledger.setOccupiedBaseQty(bd("0.0000"));
            fixture.ledger.setReleasedBaseQty(fixture.ledger.getCoveredDemandBaseQty());
            fixture.ledger.setReleasedAt(Date.from(CHANGED_AT));
            fixture.ledger.setReleaseReason("合成未转单申请撤销");
            return fixture;
        }

        private static Fixture empty() {
            Fixture fixture = new Fixture();
            for (String part : PART_NAMES) fixture.rows(part).clear();
            return fixture;
        }
    }

    /** JDBC 离线事务探针；真实管理器设置隔离/只读、暂停恢复与回滚，但无数据库连接。 */
    private static final class JdbcProbe {
        private final String route;
        private final Connection connection;
        private boolean autoCommit = true;
        private boolean readOnly;
        private int isolation = Connection.TRANSACTION_READ_COMMITTED;
        private boolean closed;
        private int commits;
        private int rollbacks;

        private JdbcProbe(String route) {
            this.route = route;
            connection = mock(Connection.class, invocation -> {
                return switch (invocation.getMethod().getName()) {
                    case "getAutoCommit" -> autoCommit;
                    case "setAutoCommit" -> { autoCommit = invocation.getArgument(0); yield null; }
                    case "isReadOnly" -> readOnly;
                    case "setReadOnly" -> { readOnly = invocation.getArgument(0); yield null; }
                    case "getTransactionIsolation" -> isolation;
                    case "setTransactionIsolation" -> { isolation = invocation.getArgument(0); yield null; }
                    case "isClosed" -> closed;
                    case "close" -> { closed = true; yield null; }
                    case "commit" -> { commits++; yield null; }
                    case "rollback" -> { rollbacks++; yield null; }
                    case "createStatement", "prepareStatement", "prepareCall" -> throw new AssertionError("事务探针不得执行SQL");
                    default -> Answers.RETURNS_DEFAULTS.answer(invocation);
                };
            });
        }
    }
}
