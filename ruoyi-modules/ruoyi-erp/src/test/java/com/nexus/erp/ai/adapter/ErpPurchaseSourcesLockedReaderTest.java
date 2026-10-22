package com.nexus.erp.ai.adapter;

import static com.nexus.erp.pu.service.IPuPurchaseRequisitionService.STATUS_CLOSED;
import static com.nexus.erp.pu.service.IPuPurchaseRequisitionService.STATUS_DRAFT;
import static com.nexus.erp.pu.service.IPuPurchaseRequisitionService.STATUS_ORDERED;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.aop.DynamicDataSourceAnnotationAdvisor;
import com.baomidou.dynamic.datasource.aop.DynamicDataSourceAnnotationInterceptor;
import com.baomidou.dynamic.datasource.processor.DsSpelExpressionProcessor;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.domain.enums.PurchaseOrderLineReceiveStatus;
import com.nexus.erp.pu.domain.enums.PurchaseOrderStatusEnum;
import com.nexus.erp.pu.mapper.ErpPurchaseAllocationLineageMapper;
import com.nexus.erp.pu.mapper.ErpPurchaseCatalogSourceMapper;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper;
import com.nexus.erp.pu.mapper.PuRequirementMaterialGuardMapper;
import com.nexus.erp.pu.service.PuRequirementErpTransactionExecutor;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;
import com.zaxxer.hikari.HikariDataSource;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.sql.DataSource;

/**
 * 计划 B 的专属离线当前读合同；保留原有 203/445 回归用例，仅扩展获批结构断言。
 * 使用真实资格校验、Spring 事务和代理、MyBatis 映射及字段处理器，不模拟 verified。
 * 只有物理池的借连接及 JDBC 结果集是合成夹具；未联网、未写真实数据库。
 * 正例的路由别名由测试在真实物理 ERP 事务内建立，生产 reader 不得补绑。
 * SQL 断言覆盖最终 BoundSql 与实际执行参数，不代表 MySQL 现场锁并发已验收。
 */
@Tag("dev")
@Execution(ExecutionMode.SAME_THREAD)
class ErpPurchaseSourcesLockedReaderTest {
    private static final String TENANT = "000001";
    private static final long MATERIAL = 101L;
    private static final long VENDOR = 102L;
    private static final long BASE = 30L;
    private static final long ORDER_UNIT = 10L;
    private static final long MOQ_UNIT = 20L;
    private static final long ROOT = 5L;
    private static final Instant NOW = Instant.parse("2026-10-02T08:00:00.123456Z");
    private static final BigDecimal VERIFIED_MILLIS = new BigDecimal("1790755200000");
    private static final String SYNTHETIC_SECRET = "LOCKED_SYNTH_SQL_PASSWORD_BEARER";
    private static final Pattern FIRST_TABLE = Pattern.compile("(?i)\\bFROM\\s+(\\w+)");

    enum Kind {
        CATALOG(ErpPurchaseCatalogSourceReader.class, ErpPurchaseCatalogSourceMapper.class),
        LINEAGE(ErpPurchaseAllocationLineageReader.class, ErpPurchaseAllocationLineageMapper.class);
        final Class<?> reader;
        final Class<?> mapper;
        Kind(Class<?> reader, Class<?> mapper) { this.reader = reader; this.mapper = mapper; }
        Method entry(String name) throws NoSuchMethodException {
            return this == CATALOG ? reader.getMethod(name, String.class, Long.class, Long.class)
                : reader.getMethod(name, String.class, Long.class);
        }
        String failure() { return this == CATALOG ? "采购目录来源读取失败" : "采购占用归属来源读取失败"; }
    }

    @AfterEach
    void noThreadOrTransactionResourcesLeak() {
        assertAll(() -> assertNull(DynamicDataSourceContextHolder.peek()),
            () -> assertFalse(TransactionSynchronizationManager.isActualTransactionActive()),
            () -> assertFalse(TransactionSynchronizationManager.isSynchronizationActive()),
            () -> assertNull(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()),
            () -> assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty()));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void explicitSpringConstructorAndMandatoryEntryPreserveOldRead(Kind kind) throws Exception {
        Transactional read = kind.entry("read").getAnnotation(Transactional.class);
        assertAll(() -> assertTrue(read.readOnly()),
            () -> assertEquals(Propagation.REQUIRES_NEW, read.propagation()),
            () -> assertEquals(Isolation.REPEATABLE_READ, read.isolation()));
        Transactional locked = kind.entry("readLocked").getAnnotation(Transactional.class);
        assertNotNull(locked, "锁定入口必须有真实事务切面定义");
        assertAll(() -> assertFalse(locked.readOnly()),
            () -> assertEquals(Propagation.MANDATORY, locked.propagation()),
            () -> assertEquals(Isolation.DEFAULT, locked.isolation()),
            () -> assertEquals("erp", kind.entry("readLocked").getAnnotation(DS.class).value()));
        assertNotNull(kind.reader.getConstructor(kind.mapper, PuRequirementErpTransactionQualification.class)
            .getAnnotation(Autowired.class), "Spring 必须明确选择含资格校验的新构造");
        assertNotNull(kind.reader.getConstructor(kind.mapper), "保留既有只读单测构造");
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void actualProxyDoesNotCreateTransactionWhenMissing(Kind kind) throws Exception {
        Fixture f = new Fixture();
        try (MockedStatic<TenantHelper> ignored = tenant()) {
            assertThrows(IllegalTransactionStateException.class, () -> f.call(kind, f.proxy(kind), TENANT));
        }
        assertTrue(f.executions.isEmpty());
        verifyNoInteractions(f.erp, f.master);
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void oldConstructorCannotGrantLockedReadEvenInValidErpTransaction(Kind kind) throws Exception {
        Fixture f = new Fixture();
        Object old = kind.reader.getConstructor(kind.mapper).newInstance(f.mapper(kind));
        ServiceException failure = f.inErp(false, () -> assertThrows(ServiceException.class,
            () -> f.call(kind, f.proxy(old), TENANT)));
        assertTrue(failure.getMessage().contains("事务资格"));
        assertTrue(f.executions.isEmpty());
    }

    @ParameterizedTest(name = "{0}/{1}")
    @MethodSource("invalidTransactions")
    void realQualificationRejectsBeforeAnySourceSql(Kind kind, String invalid) throws Exception {
        Fixture f = new Fixture();
        Supplier<ServiceException> check = () -> assertThrows(ServiceException.class,
            () -> f.call(kind, f.target(kind), TENANT));
        ServiceException failure;
        switch (invalid) {
            case "none" -> {
                try (MockedStatic<TenantHelper> ignored = tenant()) {
                    failure = assertThrows(ServiceException.class, () -> f.call(kind, f.target(kind), TENANT));
                }
            }
            case "master" -> failure = f.inTransaction(f.master, false, false, check);
            case "router-only" -> {
                DynamicDataSourceContextHolder.push("erp");
                try { failure = f.inTransaction(f.routing, false, false, check); }
                finally { DynamicDataSourceContextHolder.poll(); }
            }
            case "read-only" -> failure = f.inErp(true, check);
            default -> failure = f.inErp(false, () -> {
                Object extra = new Object();
                switch (invalid) {
                    case "jdbc-autocommit" -> f.autoCommit.set(true);
                    case "jdbc-read-only" -> f.readOnly.set(true);
                    case "jdbc-closed" -> f.closed.set(true);
                    case "shared-physical" -> f.routing.getDataSources().put("master", f.erp);
                    case "other-resource" -> TransactionSynchronizationManager.bindResource(extra, new Object());
                    case "wrong-holder" -> {
                        TransactionSynchronizationManager.unbindResource(f.routing);
                        ConnectionHolder other = new ConnectionHolder(f.masterConnection);
                        other.setSynchronizedWithTransaction(true);
                        TransactionSynchronizationManager.bindResource(f.routing, other);
                    }
                    default -> throw new AssertionError("未知反例");
                }
                try { return check.get(); }
                finally { TransactionSynchronizationManager.unbindResourceIfPossible(extra); }
            });
        }
        assertTrue(failure.getMessage().contains("物料竞争池"), "必须来自真实资格校验而不是 stub 或 mock");
        assertEquals(1, f.qualification.participatingChecks, "reader 必须走参与式入口，不直接绕回旧 strict");
        assertTrue(f.executions.isEmpty(), "失败关闭不得运行来源 SQL");
        verify(f.erpConnection, never()).prepareStatement(anyString());
        verify(f.masterConnection, never()).prepareStatement(anyString());
    }

    static Stream<Arguments> invalidTransactions() {
        return Arrays.stream(Kind.values()).flatMap(kind -> Stream.of("none", "master", "router-only", "read-only",
            "jdbc-autocommit", "jdbc-read-only", "jdbc-closed", "shared-physical", "other-resource", "wrong-holder")
            .map(invalid -> Arguments.of(kind, invalid)));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void tenantIdentityIsCheckedBeforeQualificationAndSql(Kind kind) throws Exception {
        Fixture f = new Fixture();
        ServiceException failure = f.inErp(false, () -> assertThrows(ServiceException.class,
            () -> f.call(kind, f.proxy(kind), "000002")));
        assertTrue(failure.getMessage().contains("当前租户"));
        assertTrue(f.executions.isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void realMapperLocksOnTheExistingErpConnectionWithoutNewTransaction(Kind kind) throws Exception {
        Fixture f = new Fixture();
        Object result = f.inErp(false, () -> {
            Object holder = TransactionSynchronizationManager.getResource(f.erp);
            String name = TransactionSynchronizationManager.getCurrentTransactionName();
            Object snapshot = f.call(kind, f.proxy(kind), TENANT);
            assertEquals(1, f.qualification.participatingChecks, "新入口仍执行真实资格校验，不制造 verified");
            assertSame(holder, TransactionSynchronizationManager.getResource(f.erp));
            assertSame(holder, TransactionSynchronizationManager.getResource(f.routing));
            assertEquals(name, TransactionSynchronizationManager.getCurrentTransactionName());
            assertNotNull(TransactionSynchronizationManager.getResource(f.factory), "真实 MyBatis 会话参与同事务");
            return snapshot;
        });
        assertNotNull(result);
        assertTrue(f.executions.size() >= 6);
        for (Executed query : f.executions) {
            assertSame(f.erpConnection, query.connection());
            assertTrue(query.sql().endsWith("FOR UPDATE"), query.sql());
        }
        assertTrue(f.executions.get(0).sql().contains("UNIX_TIMESTAMP(CURRENT_TIMESTAMP(6))"));
        assertTrue(f.executions.get(0).sql().contains("@@session.time_zone"));
        assertEquals(NOW, kind == Kind.CATALOG ? ((ErpPurchaseCatalogSourceReader.CatalogSnapshot) result).snapshotAt()
            : ((ErpPurchaseAllocationLineageReader.LineageSnapshot) result).snapshotAt());
        verify(f.erp, times(1)).getConnection();
        verify(f.master, never()).getConnection();
        verify(f.erpConnection, never()).commit();
        verify(f.erpConnection, times(1)).rollback();
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void existingMyBatisSessionCannotBeRequalifiedByAFlagOrRoute(Kind kind) throws Exception {
        Fixture f = new Fixture();
        f.inErp(false, () -> {
            f.template.getMapper(ErpPurchaseCatalogSourceMapper.class).selectDatabaseClock();
            int before = f.executions.size();
            ServiceException failure = assertThrows(ServiceException.class, () -> f.call(kind, f.proxy(kind), TENANT));
            assertTrue(failure.getMessage().contains("既有 MyBatis 会话"));
            assertEquals(1, f.qualification.participatingChecks, "外部预绑定经新参与式入口仍必须拒绝");
            assertEquals(before, f.executions.size(), "资格拒绝不得补读或补绑会话");
            return null;
        });
    }

    @Test
    void ownedNativeRepeatableReadCanRunBothReadersAfterAnActualMyBatisQuery() throws Exception {
        Fixture f = new Fixture();
        PuRequirementErpTransactionExecutor executor = new PuRequirementErpTransactionExecutor(f.factory);
        AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
        try (MockedStatic<TenantHelper> ignored = tenant()) {
            try {
            executor.executeRepeatableRead(TENANT, () -> {
                try {
                assertEquals(Connection.TRANSACTION_REPEATABLE_READ, f.nativeIsolation.get(), "原生 JDBC 必须已经切到 RR");
                f.template.getMapper(ErpPurchaseCatalogSourceMapper.class).selectDatabaseClock();
                Object session = TransactionSynchronizationManager.getResource(f.factory);
                assertNotNull(session, "实际 Mapper 首次调用已经绑定会话");
                assertNotNull(f.call(Kind.CATALOG, f.proxy(Kind.CATALOG), TENANT));
                assertEquals(1, f.qualification.participatingChecks);
                assertSame(session, TransactionSynchronizationManager.getResource(f.factory));
                assertNotNull(f.call(Kind.LINEAGE, f.proxy(Kind.LINEAGE), TENANT));
                assertEquals(2, f.qualification.participatingChecks);
                assertSame(session, TransactionSynchronizationManager.getResource(f.factory));
                for (Executed query : f.executions) assertSame(f.erpConnection, query.connection());
                return null;
                } catch (RuntimeException | Error failure) {
                    callbackFailure.set(failure);
                    throw failure;
                }
            });
            } catch (ServiceException failure) {
                if (callbackFailure.get() != null) throw new AssertionError("真实连续回调失败", callbackFailure.get());
                throw new AssertionError("专属事务收尾失败，参与式次数=" + f.qualification.participatingChecks
                    + "，实际语句=" + f.executions.size(), failure);
            }
        }
        verify(f.erp, times(1)).getConnection();
        verify(f.master, never()).getConnection();
        verify(f.erpConnection, times(1)).commit();
        verify(f.erpConnection, never()).rollback();
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, f.nativeIsolation.get(), "事务结束恢复进入前的原生隔离级别");
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void legacyOwnedEntryCannotUpgradeAPreboundSessionIntoTheNativeRrSourceChain(Kind kind) throws Exception {
        Fixture f = new Fixture();
        PuRequirementErpTransactionExecutor executor = new PuRequirementErpTransactionExecutor(f.factory);
        AtomicReference<ServiceException> qualificationFailure = new AtomicReference<>();
        try (MockedStatic<TenantHelper> ignored = tenant()) {
            ServiceException failure = assertThrows(ServiceException.class, () -> executor.execute(TENANT, () -> {
                f.template.getMapper(ErpPurchaseCatalogSourceMapper.class).selectDatabaseClock();
                ServiceException rejected = assertThrows(ServiceException.class, () -> f.call(kind, f.proxy(kind), TENANT));
                qualificationFailure.set(rejected);
                throw rejected;
            }));
            assertNotNull(qualificationFailure.get(), "必须捕获 reader 内真实资格校验的拒绝");
            assertTrue(qualificationFailure.get().getMessage().contains("既有 MyBatis 会话"));
            assertTrue(failure.getMessage().contains("ERP 专属物理事务未成功完成"), "外层执行器保持固定脱敏诊断");
        }
        assertEquals(1, f.qualification.participatingChecks);
        assertEquals(1, f.executions.size(), "旧 execute 无权把已绑定会话提升为完整 RR 连续来源认证");
        verify(f.erpConnection, never()).commit();
        verify(f.erpConnection, times(1)).rollback();
    }

    @ParameterizedTest(name = "固定当前读 SQL：{0}/{1}")
    @MethodSource("lockMethods")
    void lockedBoundSqlPreservesAllSourcePredicatesAndResultHandlers(Kind kind, String select) {
        Configuration config = new Configuration();
        config.addMapper(kind.mapper);
        Map<String, Object> parameters = Map.of("tenantId", TENANT, "materialId", MATERIAL, "vendorId", VENDOR,
            "unitIds", List.of(10L, 20L, 30L), "headIds", List.of(501L, 601L), "detailIds", List.of(401L, 402L));
        String lock = "lock" + select.substring("select".length());
        assertTrue(config.hasStatement(kind.mapper.getName() + "." + lock), "必须注册真实固定 SQL，不能仅有 mock/default 方法");
        MappedStatement original = config.getMappedStatement(kind.mapper.getName() + "." + select);
        MappedStatement current = config.getMappedStatement(kind.mapper.getName() + "." + lock);
        BoundSql read = original.getBoundSql(parameters);
        BoundSql locked = current.getBoundSql(parameters);
        String readSql = normalized(read.getSql());
        String lockedSql = normalized(locked.getSql());
        assertFalse(readSql.contains("FOR UPDATE"), "原只读入口不得带锁");
        assertTrue(lockedSql.endsWith("FOR UPDATE"));
        assertEquals(withoutLockOrdering(readSql), withoutLockOrdering(lockedSql),
            "只允许加入当前读与确定主键排序，不能缩减来源/NULL/日期/租户过滤");
        assertEquals(read.getParameterMappings().stream().map(p -> p.getProperty()).toList(),
            locked.getParameterMappings().stream().map(p -> p.getProperty()).toList());
        assertEquals(original.getResultMaps().get(0).getResultMappings().stream().map(ErpPurchaseSourcesLockedReaderTest::mapping).toList(),
            current.getResultMaps().get(0).getResultMappings().stream().map(ErpPurchaseSourcesLockedReaderTest::mapping).toList(),
            "锁定查询必须沿用字段专用 NULL/二值/日期解码器");
        long selects = Pattern.compile("\\bSELECT\\b").matcher(lockedSql).results().count();
        long locks = Pattern.compile("\\bFOR UPDATE\\b").matcher(lockedSql).results().count();
        assertEquals(selects, locks, "嵌套来源子查询也必须是真实当前读");
        assertFalse(current.isUseCache(), "禁止二级缓存代替数据库锁定查询");
        assertTrue(current.isFlushCacheRequired(), "禁止同事务一级缓存代替真实当前读");
    }

    static Stream<Arguments> lockMethods() {
        return Stream.concat(Stream.of("selectDatabaseClock", "selectMaterial", "selectVendor", "selectRelations", "selectUnits")
            .map(method -> Arguments.of(Kind.CATALOG, method)), Stream.of("selectDatabaseClock", "selectMaterial",
                "selectAllocations", "selectRequisitionLines", "selectOrderLines", "selectRequisitionHeads",
                "selectOrderHeads", "selectRequirementDetails", "selectRequirementHeads")
            .map(method -> Arguments.of(Kind.LINEAGE, method)));
    }

    private static List<Object> mapping(ResultMapping value) {
        return Arrays.asList(value.getColumn(), value.getProperty(), value.getJavaType(), value.getTypeHandler().getClass());
    }

    private static String normalized(String sql) { return sql.replaceAll("\\s+", " ").trim(); }

    private static String withoutLockOrdering(String sql) {
        return normalized(sql.replaceAll("ORDER BY [a-z_]+ ASC", "").replace("FOR UPDATE", ""));
    }

    @Test
    void catalogLocksSortedUnitIdsAndKeepsReferencedRootReadBounded() throws Exception {
        Fixture f = new Fixture();
        ErpPurchaseCatalogSourceReader.CatalogSnapshot snapshot = (ErpPurchaseCatalogSourceReader.CatalogSnapshot)
            f.inErp(false, () -> f.call(Kind.CATALOG, f.proxy(Kind.CATALOG), TENANT));
        List<Executed> units = f.executions.stream().filter(query -> query.table().equals("bd_unit_of_measure")).toList();
        assertEquals(2, units.size());
        assertEquals(List.of(TENANT, ORDER_UNIT, MOQ_UNIT, BASE), units.get(0).parameters());
        assertEquals(List.of(TENANT, ROOT), units.get(1).parameters());
        assertTrue(units.stream().allMatch(query -> query.sql().contains("ORDER BY uom_id ASC FOR UPDATE")));
        assertTrue(f.executions.stream().filter(query -> query.table().equals("bd_material_vendor"))
            .allMatch(query -> query.sql().contains("ORDER BY relation_id ASC FOR UPDATE")));
        assertEquals(7, snapshot.sourceVersions().size());
        assertTrue(snapshot.sourceVersions().get("material:" + MATERIAL).matches("[0-9a-f]{64}"));
        assertTrue(snapshot.sourceVersions().get("vendor:" + VENDOR).matches("[0-9a-f]{64}"));
        assertEquals("30", snapshot.baseUnit().baseUnitId());
    }

    @Test
    void lineageLocksReferencedIdsInAscendingOrderWithoutWindowOrLegacySourceInference() throws Exception {
        Fixture f = new Fixture();
        Map<String, Object> ledger = new LinkedHashMap<>(f.ledger);
        ledger.put("allocationId", 200L);
        ledger.put("allocationKey", "offline-allocation-2");
        ledger.put("requisitionLineId", 300L);
        ledger.put("detailId", 400L);
        f.add("pu_requirement_allocation_ledger", ledger);
        Map<String, Object> requisitionLine = new LinkedHashMap<>(f.requisitionLine);
        requisitionLine.put("lineId", 300L);
        requisitionLine.put("requisitionId", 600L);
        f.add("pu_purchase_requisition_line", requisitionLine);
        Map<String, Object> requisitionHead = new LinkedHashMap<>(f.requisitionHead);
        requisitionHead.put("requisitionId", 600L);
        f.add("pu_purchase_requisition", requisitionHead);
        Map<String, Object> detail = new LinkedHashMap<>(f.detail);
        detail.put("detailId", 400L);
        detail.put("requirementId", 500L);
        f.add("prd_material_requirement_detail", detail);
        Map<String, Object> requirementHead = new LinkedHashMap<>(f.requirementHead);
        requirementHead.put("requirementId", 500L);
        f.add("prd_material_requirement", requirementHead);
        ErpPurchaseAllocationLineageReader.LineageSnapshot snapshot = (ErpPurchaseAllocationLineageReader.LineageSnapshot)
            f.inErp(false, () -> f.call(Kind.LINEAGE, f.proxy(Kind.LINEAGE), TENANT));
        assertEquals(List.of(200L, 201L), snapshot.allocations().stream().map(binding -> binding.ledger().allocationId()).toList());
        for (Executed query : f.executions) {
            switch (query.table()) {
                case "pu_purchase_requisition" -> assertEquals(List.of(TENANT, 600L, 601L), query.parameters());
                case "prd_material_requirement_detail" -> assertEquals(List.of(TENANT, 400L, 401L), query.parameters());
                case "prd_material_requirement" -> assertEquals(List.of(TENANT, 500L, 501L), query.parameters());
                default -> { }
            }
        }
    }

    @ParameterizedTest(name = "数据库业务时钟拒绝：{0}/{1}")
    @MethodSource("badClocks")
    void lockedReadsNeverReplaceDatabaseClockOrTimezone(Kind kind, Object epoch, String zone) throws Exception {
        Fixture f = new Fixture();
        f.clock.put("epoch_seconds", epoch);
        f.clock.put("database_time_zone", zone);
        ServiceException failure = f.inErp(false, () -> assertThrows(ServiceException.class,
            () -> f.call(kind, f.proxy(kind), TENANT)));
        assertTrue(failure.getMessage().contains("时钟") || failure.getMessage().contains("时区")
            || failure.getMessage().contains("时间戳") || failure.getMessage().contains("存储合同"));
        assertEquals(1, f.executions.size(), "时钟未知时不得继续锁定任何业务来源");
    }

    static Stream<Arguments> badClocks() {
        return Arrays.stream(Kind.values()).flatMap(kind -> Stream.of(
            Arguments.of(kind, null, "+08:00"), Arguments.of(kind, BigDecimal.ZERO, "+08:00"),
            Arguments.of(kind, new BigDecimal("1790928000.1234567"), "+08:00"),
            Arguments.of(kind, new BigDecimal("1E30"), "+08:00"),
            Arguments.of(kind, new BigDecimal("1790928000"), null),
            Arguments.of(kind, new BigDecimal("1790928000"), "SYSTEM"),
            Arguments.of(kind, new BigDecimal("1790928000"), "UTC"),
            Arguments.of(kind, new BigDecimal("1790928000"), "+00:00")));
    }

    @Test
    void lockedCatalogCannotCertifyTermsThatExpiredWhileAcquiringUnitLocks() throws Exception {
        Fixture f = new Fixture();
        f.terms.put("termsExpiresAtEpochMillis", BigDecimal.valueOf(NOW.plusSeconds(1).toEpochMilli()));
        f.afterTableRead = table -> {
            if (table.equals("bd_unit_of_measure")) {
                f.clock.put("epoch_seconds", BigDecimal.valueOf(NOW.plusSeconds(2).getEpochSecond())
                    .add(BigDecimal.valueOf(NOW.getNano(), 9)));
            }
        };
        ServiceException failure = f.inErp(false, () -> assertThrows(ServiceException.class,
            () -> f.call(Kind.CATALOG, f.proxy(Kind.CATALOG), TENANT)));
        assertTrue(failure.getMessage().contains("过期"), "等待真实来源行锁后必须重新核对数据库业务时钟及条款期限");
        assertEquals(2, f.executions.stream().filter(query -> query.table().equals("clock")).count());
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void lockedSnapshotsRecheckDatabaseSessionZoneAfterTheirLastSourceLock(Kind kind) throws Exception {
        Fixture f = new Fixture();
        f.afterTableRead = table -> {
            if (table.equals("bd_unit_of_measure") || table.equals("prd_material_requirement")) {
                f.clock.put("database_time_zone", "SYSTEM");
            }
        };
        ServiceException failure = f.inErp(false, () -> assertThrows(ServiceException.class,
            () -> f.call(kind, f.proxy(kind), TENANT)));
        assertTrue(failure.getMessage().contains("时区") || failure.getMessage().contains("存储合同"));
        assertEquals(2, f.executions.stream().filter(query -> query.table().equals("clock")).count());
    }

    @ParameterizedTest(name = "目录原门禁不放松：{0}")
    @MethodSource("invalidCatalog")
    void currentWriteDoesNotUpgradeCatalogNullStatusUnitsOrTerms(String name, Consumer<Fixture> corrupt,
                                                               String reason) throws Exception {
        Fixture f = new Fixture();
        corrupt.accept(f);
        ServiceException failure = f.inErp(false, () -> assertThrows(ServiceException.class,
            () -> f.call(Kind.CATALOG, f.proxy(Kind.CATALOG), TENANT)));
        assertTrue(failure.getMessage().contains(reason), name + "：" + failure.getMessage());
    }

    static Stream<Arguments> invalidCatalog() {
        return Stream.of(
            bad("物料未知状态", f -> f.material.put("status", null), "物料主数据无效"),
            bad("供应商跨租户", f -> f.vendor.put("tenantId", "000002"), "供应商主数据无效"),
            bad("关系重复", f -> f.rows.get("bd_material_vendor").add(new LinkedHashMap<>(f.terms)), "供应关系缺失或重复"),
            bad("条款未知版本", f -> f.terms.put("termsVersion", null), "新供应条款核验字段不完整"),
            bad("旧MOQ不能补填", f -> f.terms.put("verifiedMoqQty", null), "新供应条款核验字段不完整"),
            bad("拆零未知", f -> f.terms.put("allowSplit", null), "新供应条款核验字段不完整"),
            bad("未知拆零原值", f -> f.terms.put("allowSplit", new BigDecimal("2")), "采购目录来源读取失败"),
            bad("过期条款", f -> f.terms.put("termsExpiresAtEpochMillis", VERIFIED_MILLIS), "新供应条款未生效、已过期或核验时间在未来"),
            bad("日期未知", f -> f.terms.put("termsVerifiedAtEpochMillis", null), "新供应条款核验字段不完整"),
            bad("单位未知版本", f -> f.unit(BASE).put("conversionVersion", null), "单位业务核验字段、精度或换算率无效"),
            bad("单位未知精度", f -> f.unit(ORDER_UNIT).put("quantityScale", null), "单位业务核验字段、精度或换算率无效"),
            bad("单位维度冲突", f -> f.unit(ORDER_UNIT).put("category", "mass"), "单位维度或换算版本不一致"),
            bad("单位版本冲突", f -> f.unit(ORDER_UNIT).put("conversionVersion", "v-other"), "单位维度或换算版本不一致"),
            bad("非法根链", f -> f.unit(ROOT).put("conversionRate", new BigDecimal("2")), "主单位必须有明确自映射及核验后的恒等换算"),
            bad("不能精确相除", f -> f.unit(BASE).put("conversionRate", new BigDecimal("3")), "不能精确相除"),
            bad("MOQ零", f -> f.terms.put("verifiedMoqQty", BigDecimal.ZERO), "供应条款数量非正"),
            bad("包装未知", f -> f.terms.put("unitsPerPack", null), "新供应条款核验字段不完整"));
    }

    @ParameterizedTest(name = "归属局部拒绝合同：{0}")
    @MethodSource("invalidLineage")
    void currentWriteDoesNotExpandLineageFulfilmentOrSourceSupport(String name, Consumer<Fixture> corrupt,
                                                                 String reason) throws Exception {
        Fixture f = new Fixture();
        corrupt.accept(f);
        ServiceException failure = f.inErp(false, () -> assertThrows(ServiceException.class,
            () -> f.call(Kind.LINEAGE, f.proxy(Kind.LINEAGE), TENANT)));
        assertTrue(failure.getMessage().contains(reason), name + "：" + failure.getMessage());
    }

    static Stream<Arguments> invalidLineage() {
        return Stream.of(
            bad("旧source_id不得推断", f -> f.rows.get("pu_requirement_allocation_ledger").clear(), "开放申请行存在未绑定来源"),
            bad("账本单位未知", f -> f.ledger.put("baseUomId", null), "账本身份、来源键或版本未核验"),
            bad("账本跨租户", f -> f.ledger.put("tenantId", "000002"), "账本身份、来源键或版本未核验"),
            bad("账本四量未知", f -> f.ledger.put("occupiedBaseQty", null), "账本四量不符合"),
            bad("账本履约不升级", f -> {
                f.ledger.put("fulfilledBaseQty", BigDecimal.ONE); f.ledger.put("occupiedBaseQty", new BigDecimal("9"));
            }, "账本履约事实未认证"),
            bad("未知来源类型", f -> f.ledger.put("sourceType", "OLD_SOURCE"), "账本身份、来源键或版本未核验"),
            bad("未知申请版本", f -> f.requisitionHead.put("version", null), "申请头状态或版本未核验"),
            bad("缺失MRP链", f -> f.rows.get("prd_material_requirement_detail").clear(), "引用实体缺失"),
            bad("未知MRP删除", f -> f.detail.put("delFlag", null), "材料来源跨租户"),
            bad("部分收货仍拒绝", f -> { f.commitOrder(); f.orderLine.put("receivedQuantity", BigDecimal.ONE); }, "下游履约事实未认证"),
            bad("收货NULL仍拒绝", f -> { f.commitOrder(); f.orderLine.put("receivedQuantity", null); }, "下游履约事实未认证"),
            bad("退货仍拒绝", f -> { f.commitOrder(); f.orderLine.put("returnedQuantity", BigDecimal.ONE); }, "下游履约事实未认证"),
            bad("下游开票仍拒绝", f -> { f.commitOrder(); f.orderLine.put("invoicedQty", BigDecimal.ONE); }, "下游履约事实未认证"),
            bad("订单关闭仍拒绝", f -> { f.commitOrder(); f.orderHead.put("status", PurchaseOrderStatusEnum.CLOSED.getCode()); }, "下游生命周期未认证"));
    }

    private static Arguments bad(String name, Consumer<Fixture> corrupt, String reason) {
        return Arguments.of(name, corrupt, reason);
    }

    @Test
    void lockedLineageRetainsUnknownHistoricalUnitsWithoutManufacturingTrustedUnits() throws Exception {
        Fixture f = new Fixture();
        f.commitOrder();
        f.requisitionHead.put("status", STATUS_CLOSED);
        ErpPurchaseAllocationLineageReader.LineageSnapshot snapshot = (ErpPurchaseAllocationLineageReader.LineageSnapshot)
            f.inErp(false, () -> f.call(Kind.LINEAGE, f.proxy(Kind.LINEAGE), TENANT));
        assertNull(snapshot.allocations().get(0).requisitionLine().unit());
        assertNull(snapshot.allocations().get(0).orderLine().unit());
        assertNull(snapshot.allocations().get(0).requirementDetail().unit());
        assertEquals(new BigDecimal("10"), snapshot.allocations().get(0).ledger().occupiedBaseQty());
        assertNotEquals(MATERIAL, snapshot.allocations().get(0).requirementHead().materialId(), "成品头物料不能强制等于子项");
        assertThrows(UnsupportedOperationException.class, () -> snapshot.allocations().clear());
    }

    @ParameterizedTest(name = "查询故障必须固定脱敏：{0}/{1}/ServiceException={2}")
    @MethodSource("queryFailures")
    void actualMapperFailuresCannotExposeQueryDetails(Kind kind, String table, boolean businessException) throws Exception {
        Fixture f = new Fixture();
        f.failTable = table;
        RuntimeException cause = businessException ? new ServiceException(SYNTHETIC_SECRET)
            : new IllegalStateException(SYNTHETIC_SECRET, new SQLException(SYNTHETIC_SECRET));
        cause.addSuppressed(new IllegalArgumentException(SYNTHETIC_SECRET));
        f.queryFailure = cause;
        ServiceException failure = f.inErp(false, () -> assertThrows(ServiceException.class,
            () -> f.call(kind, f.proxy(kind), TENANT)));
        assertEquals(kind.failure(), failure.getMessage());
        assertNull(failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
        StringWriter trace = new StringWriter();
        failure.printStackTrace(new PrintWriter(trace));
        assertFalse(trace.toString().contains(SYNTHETIC_SECRET));
        assertNull(failure.getDetailMessage());
    }

    static Stream<Arguments> queryFailures() {
        return Arrays.stream(Kind.values()).flatMap(kind -> tables(kind).stream().flatMap(table -> Stream.of(
            Arguments.of(kind, table, false), Arguments.of(kind, table, true))));
    }

    private static List<String> tables(Kind kind) {
        return kind == Kind.CATALOG ? List.of("clock", "bd_material", "bd_vendor", "bd_material_vendor", "bd_unit_of_measure")
            : List.of("clock", "bd_material", "pu_requirement_allocation_ledger", "pu_purchase_requisition_line",
                "pu_purchase_order_line", "pu_purchase_requisition", "prd_material_requirement_detail", "prd_material_requirement");
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void fatalErrorsAreNotRepackagedAsTrustedBusinessDiagnostics(Kind kind) throws Exception {
        Fixture f = new Fixture();
        Error fatal = new AssertionError(SYNTHETIC_SECRET);
        f.failTable = "clock";
        f.queryFailure = fatal;
        Throwable actual = f.inErp(false, () -> assertThrows(Throwable.class, () -> f.call(kind, f.proxy(kind), TENANT)));
        assertSame(fatal, actual);
        verify(f.erpConnection, times(1)).rollback();
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void springReallySelectsQualificationConstructor(Kind kind) throws Exception {
        Fixture f = new Fixture();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            registerMapper(context, kind.mapper, f.mapper(kind));
            context.registerBean(PuRequirementErpTransactionQualification.class, () -> f.qualification);
            context.register(kind.reader);
            context.refresh();
            assertNotNull(f.inErp(false, () -> f.call(kind, f.proxy(context.getBean(kind.reader)), TENANT)));
        }
    }

    private static <T> void registerMapper(AnnotationConfigApplicationContext context, Class<T> type, Object mapper) {
        context.registerBean(type, () -> type.cast(mapper));
    }

    private static MockedStatic<TenantHelper> tenant() {
        MockedStatic<TenantHelper> result = mockStatic(TenantHelper.class);
        result.when(TenantHelper::isEnable).thenReturn(true);
        result.when(TenantHelper::getTenantId).thenReturn(TENANT);
        return result;
    }

    private record Executed(String sql, String table, List<Object> parameters, Connection connection) { }

    /** 只合成 JDBC 与来源行；Spring 资格、事务切面和 MyBatis 执行器均为真实实现。 */
    private static final class Fixture {
        final HikariDataSource erp = mock(HikariDataSource.class);
        final HikariDataSource master = mock(HikariDataSource.class);
        final Connection erpConnection = mock(Connection.class);
        final Connection masterConnection = mock(Connection.class);
        final AtomicBoolean autoCommit = new AtomicBoolean(true);
        final AtomicBoolean readOnly = new AtomicBoolean(false);
        final AtomicBoolean closed = new AtomicBoolean(false);
        final AtomicInteger nativeIsolation = new AtomicInteger(Connection.TRANSACTION_READ_COMMITTED);
        final DynamicRoutingDataSource routing = new DynamicRoutingDataSource(List.of());
        final Configuration configuration;
        final SqlSessionFactory factory;
        final SqlSessionTemplate template;
        final AuditedQualification qualification;
        final List<Executed> executions = new ArrayList<>();
        final Map<String, List<Map<String, Object>>> rows = new LinkedHashMap<>();
        final Map<String, Object> clock = row("epoch_seconds", BigDecimal.valueOf(NOW.getEpochSecond())
            .add(BigDecimal.valueOf(NOW.getNano(), 9)), "database_time_zone", "+08:00");
        final Map<String, Object> material = row("tenantId", TENANT, "materialId", MATERIAL,
            "baseUomId", BASE, "status", 1L, "delFlag", "0");
        final Map<String, Object> vendor = row("tenantId", TENANT, "vendorId", VENDOR, "status", "ACTIVE", "delFlag", "0");
        final Map<String, Object> terms = row("tenantId", TENANT, "relationId", 103L, "materialId", MATERIAL,
            "vendorId", VENDOR, "orderUomId", ORDER_UNIT, "moqUomId", MOQ_UNIT,
            "verifiedMoqQty", new BigDecimal("2"), "orderMultiple", BigDecimal.ONE, "allowSplit", BigDecimal.ONE,
            "unitsPerPack", BigDecimal.ONE, "termsVersion", "terms-v2", "termsEffectiveAtEpochMillis", VERIFIED_MILLIS,
            "termsExpiresAtEpochMillis", BigDecimal.valueOf(NOW.plusSeconds(86400).toEpochMilli()),
            "termsVerifiedAtEpochMillis", VERIFIED_MILLIS, "status", 1L, "delFlag", "0");
        final Map<String, Object> ledger = row("allocationId", 201L, "tenantId", TENANT, "materialId", MATERIAL,
            "sourceType", "MRP_DETAIL", "detailId", 401L, "requisitionLineId", 301L, "allocationNo", 1,
            "orderLineId", null, "baseUomId", BASE, "coveredDemandBaseQty", new BigDecimal("10"),
            "occupiedBaseQty", new BigDecimal("10"), "fulfilledBaseQty", BigDecimal.ZERO, "releasedBaseQty", BigDecimal.ZERO,
            "allocationStatus", "REQUEST_INTENT", "allocationKey", "offline-allocation", "decisionIdempotencyKey", "offline-decision",
            "version", 0, "createTimeEpochMillis", VERIFIED_MILLIS, "transferTimeEpochMillis", null,
            "releasedAtEpochMillis", null, "releaseReason", null, "updateTimeEpochMillis", VERIFIED_MILLIS);
        final Map<String, Object> requisitionLine = row("lineId", 301L, "tenantId", TENANT, "requisitionId", 601L,
            "materialId", MATERIAL, "unit", null, "applyQuantity", new BigDecimal("10"), "approvedQuantity", null,
            "shortageQuantity", new BigDecimal("10"), "requiredDateEpochMillis", VERIFIED_MILLIS, "sourceType", "MRP",
            "sourceId", 501L, "attr1", null, "attr2", null, "attr3", null, "attr4", null, "delFlag", "0",
            "createTimeEpochMillis", VERIFIED_MILLIS, "updateTimeEpochMillis", VERIFIED_MILLIS);
        final Map<String, Object> requisitionHead = row("requisitionId", 601L, "tenantId", TENANT, "requisitionNo", "offline-req",
            "sourceType", "MRP", "status", STATUS_DRAFT, "requisitionDateEpochMillis", VERIFIED_MILLIS,
            "requiredDateEpochMillis", VERIFIED_MILLIS, "decisionRunId", null, "decisionActionId", null,
            "decisionIdempotencyKey", "offline-decision", "selectFlowId", null, "version", 0, "delFlag", "0",
            "createTimeEpochMillis", VERIFIED_MILLIS, "updateTimeEpochMillis", VERIFIED_MILLIS);
        final Map<String, Object> detail = row("detailId", 401L, "tenantId", TENANT, "requirementId", 501L,
            "materialId", MATERIAL, "unit", null, "sourceType", "MRP", "requiredQuantity", new BigDecimal("10"),
            "issuedQuantity", BigDecimal.ZERO, "stockQuantity", null, "shortageQuantity", null,
            "requiredDateEpochMillis", VERIFIED_MILLIS, "status", 1L, "delFlag", "0", "backflushFlag", null,
            "actualConsumedQty", null, "createTimeEpochMillis", VERIFIED_MILLIS, "updateTimeEpochMillis", VERIFIED_MILLIS);
        final Map<String, Object> requirementHead = row("requirementId", 501L, "tenantId", TENANT,
            "requirementNo", "offline-mrp", "parentRequirementId", null, "planId", null, "planNo", null, "bomId", null,
            "materialId", 999L, "requirementDateEpochMillis", VERIFIED_MILLIS, "sourceType", "MRP", "status", "CONFIRMED",
            "confirmedTimeEpochMillis", VERIFIED_MILLIS, "issuedTimeEpochMillis", null, "closedTimeEpochMillis", null,
            "delFlag", "0", "createTimeEpochMillis", VERIFIED_MILLIS, "updateTimeEpochMillis", VERIFIED_MILLIS);
        final Map<String, Object> orderLine = row("lineId", 701L, "tenantId", TENANT, "orderId", 801L,
            "requisitionLineId", 301L, "materialId", MATERIAL, "unit", null, "orderQuantity", new BigDecimal("10"),
            "receivedQuantity", BigDecimal.ZERO, "returnedQuantity", BigDecimal.ZERO, "receiveStatus", PurchaseOrderLineReceiveStatus.PENDING.getCode(),
            "invoicedQty", BigDecimal.ZERO, "delFlag", "0", "createTimeEpochMillis", VERIFIED_MILLIS, "updateTimeEpochMillis", VERIFIED_MILLIS);
        final Map<String, Object> orderHead = row("orderId", 801L, "tenantId", TENANT, "orderNo", "offline-order",
            "requisitionId", 601L, "vendorId", VENDOR, "status", PurchaseOrderStatusEnum.DRAFT.getCode(),
            "orderDateEpochMillis", VERIFIED_MILLIS, "expectedArrivalDateEpochMillis", VERIFIED_MILLIS,
            "delFlag", "0", "createTimeEpochMillis", VERIFIED_MILLIS, "updateTimeEpochMillis", VERIFIED_MILLIS);
        String failTable;
        Throwable queryFailure;
        Consumer<String> afterTableRead = ignored -> { };

        Fixture() throws SQLException {
            when(erp.getConnection()).thenReturn(erpConnection);
            when(master.getConnection()).thenReturn(masterConnection);
            jdbcState(erpConnection, autoCommit, readOnly, closed, nativeIsolation);
            jdbcState(masterConnection, new AtomicBoolean(true), new AtomicBoolean(false), new AtomicBoolean(false),
                new AtomicInteger(Connection.TRANSACTION_READ_COMMITTED));
            when(erpConnection.prepareStatement(anyString())).thenAnswer(invocation -> statement(invocation.getArgument(0)));
            routing.setPrimary("master");
            routing.setStrict(true);
            routing.addDataSource("master", master);
            routing.addDataSource("erp", erp);
            configuration = new Configuration(new Environment("offline-locked", new SpringManagedTransactionFactory(), routing));
            configuration.addMapper(PuRequirementCompetitionPoolMapper.class);
            configuration.addMapper(PuRequirementMaterialGuardMapper.class);
            configuration.addMapper(ErpPurchaseCatalogSourceMapper.class);
            configuration.addMapper(ErpPurchaseAllocationLineageMapper.class);
            factory = new DefaultSqlSessionFactory(configuration);
            template = new SqlSessionTemplate(factory);
            qualification = new AuditedQualification(factory);
            add("clock", clock);
            add("bd_material", material);
            add("bd_vendor", vendor);
            add("bd_material_vendor", terms);
            for (long id : List.of(BASE, ORDER_UNIT, MOQ_UNIT, ROOT)) {
                add("bd_unit_of_measure", row("tenantId", TENANT, "uomId", id, "category", "count",
                    "isBase", id == ROOT ? 1L : 0L, "primaryId", ROOT,
                    "conversionRate", id == ORDER_UNIT ? new BigDecimal("10") : BigDecimal.ONE,
                    "quantityScale", 4, "conversionVersion", "conversion-v3", "conversionVerifiedAtEpochMillis", VERIFIED_MILLIS,
                    "status", 1L, "delFlag", "0"));
            }
            add("pu_requirement_allocation_ledger", ledger);
            add("pu_purchase_requisition_line", requisitionLine);
            add("pu_purchase_requisition", requisitionHead);
            add("prd_material_requirement_detail", detail);
            add("prd_material_requirement", requirementHead);
            rows.put("pu_purchase_order_line", new ArrayList<>());
            rows.put("pu_purchase_order", new ArrayList<>());
        }

        void commitOrder() {
            ledger.put("allocationStatus", "ORDER_COMMITTED_NOT_TIMELY_SUPPLY");
            ledger.put("orderLineId", 701L);
            ledger.put("transferTimeEpochMillis", VERIFIED_MILLIS);
            requisitionHead.put("status", STATUS_ORDERED);
            add("pu_purchase_order_line", orderLine);
            add("pu_purchase_order", orderHead);
        }

        Map<String, Object> unit(long id) {
            return rows.get("bd_unit_of_measure").stream().filter(unit -> Long.valueOf(id).equals(unit.get("uomId"))).findFirst().orElseThrow();
        }

        void add(String table, Map<String, Object> value) { rows.computeIfAbsent(table, ignored -> new ArrayList<>()).add(value); }

        Object mapper(Kind kind) { return template.getMapper(kind.mapper); }

        Object target(Kind kind) {
            return kind == Kind.CATALOG ? new ErpPurchaseCatalogSourceReader((ErpPurchaseCatalogSourceMapper) mapper(kind), qualification)
                : new ErpPurchaseAllocationLineageReader((ErpPurchaseAllocationLineageMapper) mapper(kind), qualification);
        }

        Object proxy(Kind kind) { return proxy(target(kind)); }

        Object proxy(Object target) {
            TransactionInterceptor transaction = new TransactionInterceptor();
            transaction.setTransactionManager(new DataSourceTransactionManager(erp));
            transaction.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
            ProxyFactory proxy = new ProxyFactory(target);
            proxy.setProxyTargetClass(true);
            proxy.addAdvisor(new DynamicDataSourceAnnotationAdvisor(
                new DynamicDataSourceAnnotationInterceptor(true, new DsSpelExpressionProcessor()), DS.class));
            proxy.addAdvice(transaction);
            return proxy.getProxy();
        }

        Object call(Kind kind, Object reader, String tenantId) {
            return kind == Kind.CATALOG ? ((ErpPurchaseCatalogSourceReader) reader).readLocked(tenantId, MATERIAL, VENDOR)
                : ((ErpPurchaseAllocationLineageReader) reader).readLocked(tenantId, MATERIAL);
        }

        <T> T inErp(boolean readOnly, Supplier<T> work) { return inTransaction(erp, readOnly, true, work); }

        <T> T inTransaction(DataSource physical, boolean readOnly, boolean alias, Supplier<T> work) {
            TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(physical));
            transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            transaction.setReadOnly(readOnly);
            transaction.setName("离线物理事务");
            try (MockedStatic<TenantHelper> ignored = tenant()) {
                return transaction.execute(status -> {
                    if (alias) {
                        Object holder = TransactionSynchronizationManager.getResource(physical);
                        assertInstanceOf(ConnectionHolder.class, holder, "持有者由真实 Spring 事务创建");
                        TransactionSynchronizationManager.bindResource(routing, holder);
                    }
                    try { return work.get(); }
                    finally { status.setRollbackOnly(); }
                });
            } finally {
                if (alias) TransactionSynchronizationManager.unbindResourceIfPossible(routing);
            }
        }

        private PreparedStatement statement(String rawSql) throws SQLException {
            PreparedStatement statement = mock(PreparedStatement.class);
            Map<Integer, Object> parameters = new LinkedHashMap<>();
            doAnswer(invocation -> { parameters.put(invocation.getArgument(0), invocation.getArgument(1)); return null; })
                .when(statement).setString(anyInt(), anyString());
            doAnswer(invocation -> { parameters.put(invocation.getArgument(0), invocation.getArgument(1)); return null; })
                .when(statement).setLong(anyInt(), anyLong());
            doAnswer(invocation -> { parameters.put(invocation.getArgument(0), invocation.getArgument(1)); return null; })
                .when(statement).setInt(anyInt(), anyInt());
            AtomicReference<ResultSet> result = new AtomicReference<>();
            when(statement.getConnection()).thenReturn(erpConnection);
            when(statement.getUpdateCount()).thenReturn(-1);
            when(statement.execute()).thenAnswer(invocation -> {
                String sql = normalized(rawSql);
                Matcher matcher = FIRST_TABLE.matcher(sql);
                String table = matcher.find() ? matcher.group(1).toLowerCase(Locale.ROOT) : "clock";
                List<Object> bound = parameters.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).toList();
                executions.add(new Executed(sql, table, bound, erpConnection));
                assertSame(TransactionSynchronizationManager.getResource(erp), TransactionSynchronizationManager.getResource(routing));
                assertFalse(autoCommit.get(), "JDBC 查询必须处于实际事务中");
                if (sql.endsWith("FOR UPDATE")) {
                    assertTrue(qualification.participatingChecks > 0, "任何实际来源查询前必须已调用真实参与式资格校验");
                    assertEquals("erp", DynamicDataSourceContextHolder.peek(), "必须经过真实数据源代理");
                    assertFalse(TransactionAspectSupport.currentTransactionStatus().isNewTransaction(), "MANDATORY 只能加入上层事务");
                }
                if (table.equals(failTable)) throw queryFailure;
                List<Map<String, Object>> selected = rows.get(table);
                if (table.equals("bd_unit_of_measure")) {
                    selected = selected.stream().filter(unit -> bound.contains(unit.get("uomId"))).toList();
                }
                afterTableRead.accept(table);
                result.set(resultSet(selected));
                return true;
            });
            when(statement.getResultSet()).thenAnswer(ignored -> result.get());
            return statement;
        }
    }

    /** 仅记录 reader 调用的新入口；无模拟返回值，始终委托生产资格校验，不赋予资格。 */
    private static final class AuditedQualification extends PuRequirementErpTransactionQualification {
        int participatingChecks;

        AuditedQualification(SqlSessionFactory factory) {
            super(factory);
        }

        @Override
        public void requireParticipatingWritableErpTransaction() {
            participatingChecks++;
            super.requireParticipatingWritableErpTransaction();
        }
    }

    private static void jdbcState(Connection connection, AtomicBoolean autoCommit, AtomicBoolean readOnly,
                                  AtomicBoolean closed, AtomicInteger isolation) throws SQLException {
        when(connection.getAutoCommit()).thenAnswer(ignored -> autoCommit.get());
        when(connection.isReadOnly()).thenAnswer(ignored -> readOnly.get());
        when(connection.isClosed()).thenAnswer(ignored -> closed.get());
        // 真实 Spring 收尾会关闭池连接句柄；模拟其状态变化，不绕过生产执行器的关闭核验。
        doAnswer(ignored -> { closed.set(true); return null; }).when(connection).close();
        when(connection.getTransactionIsolation()).thenAnswer(ignored -> isolation.get());
        doAnswer(invocation -> { isolation.set(invocation.getArgument(0)); return null; })
            .when(connection).setTransactionIsolation(anyInt());
        doAnswer(invocation -> { autoCommit.set(invocation.getArgument(0)); return null; })
            .when(connection).setAutoCommit(anyBoolean());
        doAnswer(invocation -> { readOnly.set(invocation.getArgument(0)); return null; })
            .when(connection).setReadOnly(anyBoolean());
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) row.put((String) values[i], values[i + 1]);
        return row;
    }

    /** 合成结果集不替 reader 决策，未知值仍为 SQL NULL，经生产映射处理后再校验。 */
    private static ResultSet resultSet(List<Map<String, Object>> source) throws SQLException {
        List<Map<String, Object>> rows = source.stream().map(LinkedHashMap::new)
            .map(value -> (Map<String, Object>) value).toList();
        List<String> columns = rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet());
        AtomicInteger cursor = new AtomicInteger(-1);
        AtomicBoolean wasNull = new AtomicBoolean();
        ResultSet result = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(result.getMetaData()).thenReturn(metadata);
        when(result.next()).thenAnswer(ignored -> cursor.incrementAndGet() < rows.size());
        when(result.wasNull()).thenAnswer(ignored -> wasNull.get());
        when(metadata.getColumnCount()).thenReturn(columns.size());
        when(metadata.getColumnLabel(anyInt())).thenAnswer(invocation -> columns.get((int) invocation.getArgument(0) - 1));
        when(metadata.getColumnName(anyInt())).thenAnswer(invocation -> columns.get((int) invocation.getArgument(0) - 1));
        when(metadata.getColumnType(anyInt())).thenAnswer(invocation -> {
            Object value = rows.get(0).get(columns.get((int) invocation.getArgument(0) - 1));
            return value instanceof Number ? Types.DECIMAL : Types.VARCHAR;
        });
        when(metadata.getColumnClassName(anyInt())).thenAnswer(invocation -> {
            Object value = rows.get(0).get(columns.get((int) invocation.getArgument(0) - 1));
            return value == null ? String.class.getName() : value.getClass().getName();
        });
        Answer<Object> raw =
                invocation -> {
                    Object argument = invocation.getArgument(0);
                    String column =
                            argument instanceof Integer index
                                    ? columns.get(index - 1)
                                    : argument.toString();
                    Object value = rows.get(cursor.get()).get(column);
                    wasNull.set(value == null);
                    return value;
                };
        Answer<Object> string =
                invocation -> {
                    Object value = raw.answer(invocation);
                    return value == null ? null : value.toString();
                };
        Answer<Object> decimal =
                invocation -> {
                    Object value = raw.answer(invocation);
                    return value == null ? null : new BigDecimal(value.toString());
                };
        Answer<Object> longValue =
                invocation -> {
                    Object value = raw.answer(invocation);
                    return value == null ? 0L : ((Number) value).longValue();
                };
        Answer<Object> integer =
                invocation -> {
                    Object value = raw.answer(invocation);
                    return value == null ? 0 : ((Number) value).intValue();
                };
        when(result.getObject(anyString())).thenAnswer(raw);
        when(result.getObject(anyInt())).thenAnswer(raw);
        when(result.getString(anyString())).thenAnswer(string);
        when(result.getString(anyInt())).thenAnswer(string);
        when(result.getBigDecimal(anyString())).thenAnswer(decimal);
        when(result.getBigDecimal(anyInt())).thenAnswer(decimal);
        when(result.getLong(anyString())).thenAnswer(longValue);
        when(result.getLong(anyInt())).thenAnswer(longValue);
        when(result.getInt(anyString())).thenAnswer(integer);
        when(result.getInt(anyInt())).thenAnswer(integer);
        return result;
    }
}
