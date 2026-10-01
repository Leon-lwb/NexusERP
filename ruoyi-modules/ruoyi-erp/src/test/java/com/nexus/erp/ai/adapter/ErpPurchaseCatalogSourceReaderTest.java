package com.nexus.erp.ai.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.aop.DynamicDataSourceAnnotationAdvisor;
import com.baomidou.dynamic.datasource.aop.DynamicDataSourceAnnotationInterceptor;
import com.baomidou.dynamic.datasource.processor.DsSpelExpressionProcessor;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.ai.adapter.ErpPurchaseQuantityPolicy.Demand;
import com.nexus.erp.ai.adapter.ErpPurchaseQuantityPolicy.Input;
import com.nexus.erp.ai.adapter.ErpPurchaseQuantityPolicy.Material;
import com.nexus.erp.ai.adapter.ErpPurchaseQuantityPolicy.RejectionReason;
import com.nexus.erp.ai.adapter.ErpPurchaseQuantityPolicy.SupplierTerms;
import com.nexus.erp.ai.adapter.ErpPurchaseQuantityPolicy.TrustedUnit;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdMaterialVendor;
import com.nexus.erp.bd.domain.BdUnitOfMeasure;
import com.nexus.erp.bd.domain.BdVendor;
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
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.BooleanTypeHandler;
import org.apache.ibatis.type.TypeHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.PrintWriter;
import java.io.StringWriter;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
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
 * P1 只读采购目录契约测试；不连接数据库、启动服务或证明采购执行资格。
 * 实体和数据库时钟均由离线 Mapper 夹具提供，不能冒充当前数据库核验结果。
 * 字段解码另经真实 MyBatis 注册映射处理合成 ResultSet，不执行 SQL 或连接数据库。
 * 新目标合同限定 P1 DATETIME 为 Asia/Shanghai 业务墙上时间，读取会话仅支持明确
 * +08:00；可解析时区不证明存储口径一致，NULL 不回填，也不表示业务资料已确认。
 * 通过真实 Spring 代理运行数据源切面和只读新事务；无连接事务探针仅验证代理
 * 生命周期和隔离级别定义，不证明实际 JDBC 连接归属或数据库隔离配置，
 * 执行前仍需同 ERP 统一写事务重读。
 * 新 reader、Mapper 及其时钟投影仅在测试方法内反射解析，使缺失实现成为普通
 * 断言失败，而不是 javac 缺类、跳过用例或静态初始化错误。
 */
@Tag("dev")
@Execution(ExecutionMode.SAME_THREAD)
class ErpPurchaseCatalogSourceReaderTest {
    private static final String READER = "com.nexus.erp.ai.adapter.ErpPurchaseCatalogSourceReader";
    private static final String MAPPER = "com.nexus.erp.pu.mapper.ErpPurchaseCatalogSourceMapper";
    private static final String TENANT = "000001";
    private static final String OTHER_TENANT = "000002";
    private static final long MATERIAL_ID = 2105159630382362626L;
    private static final long VENDOR_ID = 2105159630382362627L;
    private static final long RELATION_ID = 2105159630382362628L;
    private static final long BASE_ID = 2105159630382362629L;
    private static final long ORDER_ID = 2105159630382362630L;
    private static final long ROOT_ID = 2105159630382362631L;
    private static final String TERMS_VERSION = "terms-v2";
    private static final String CONVERSION_VERSION = "conversion-v3";
    private static final String DATABASE_TIME_ZONE = "+08:00";
    private static final Instant SNAPSHOT = Instant.parse("2026-09-30T08:00:00.123456Z");
    private static final Instant VERIFIED = Instant.parse("2026-09-29T08:00:00Z");
    private static final Instant EFFECTIVE = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant EXPIRES = Instant.parse("2026-10-31T23:59:59Z");

    /** 全部为合成敏感标记，不含真实 SQL、连接、口令或令牌，不发起 JDBC 操作。 */
    private static final List<String> SYNTHETIC_SENSITIVE_MARKERS = List.of(
        "CATALOG_SYNTH_SQL", "CATALOG_SYNTH_CONNECTION", "CATALOG_SYNTH_PASSWORD", "CATALOG_SYNTH_BEARER");
    private static final List<String> MAPPER_QUERY_ORDER = List.of(
        "selectDatabaseClock", "selectMaterial", "selectVendor", "selectRelations", "selectUnits");
    private static final String MAPPER_FAILURE_DIAGNOSTIC = "采购目录来源读取失败";

    /** 与实际 Mapper 投影核对；不把元数据或合成结果集当作业务数据库事实。 */
    private static final List<DateColumn> DATE_COLUMNS = List.of(
        new DateColumn("selectRelations", "termsEffectiveAt", "termsEffectiveAtEpochMillis"),
        new DateColumn("selectRelations", "termsExpiresAt", "termsExpiresAtEpochMillis"),
        new DateColumn("selectRelations", "termsVerifiedAt", "termsVerifiedAtEpochMillis"),
        new DateColumn("selectUnits", "conversionVerifiedAt", "conversionVerifiedAtEpochMillis"));
    private static final BigDecimal EXACT_DATE_MILLIS = new BigDecimal("1790755200123");

    @AfterEach
    void noThreadContextMayLeak() {
        assertAll(
            () -> assertNull(DynamicDataSourceContextHolder.peek(), "不得泄漏数据源路由"),
            () -> assertFalse(TransactionSynchronizationManager.isActualTransactionActive()),
            () -> assertFalse(TransactionSynchronizationManager.isSynchronizationActive()),
            () -> assertNull(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()),
            () -> assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty())
        );
    }

    @Test
    void approvedFieldsAlreadyExistButLegacyFieldsDoNotBecomeProof() {
        for (String field : List.of("orderUomId", "verifiedMoqQty", "moqUomId", "orderMultiple",
            "allowSplit", "unitsPerPack", "termsVersion", "termsEffectiveAt", "termsExpiresAt", "termsVerifiedAt")) {
            assertDoesNotThrow(() -> BdMaterialVendor.class.getDeclaredField(field), field);
        }
        for (String field : List.of("quantityScale", "conversionVersion", "conversionVerifiedAt")) {
            assertDoesNotThrow(() -> BdUnitOfMeasure.class.getDeclaredField(field), field);
        }
        BdMaterialVendor old = new BdMaterialVendor();
        old.setMinOrderQty(bd("510"));
        assertNull(old.getVerifiedMoqQty());
        assertNull(old.getTermsVerifiedAt());
        assertNull(new BdUnitOfMeasure().getQuantityScale());
    }

    @Test
    void internalIdentityOnlyEntryRequiresErpReadOnlyNewTransaction() {
        Class<?> reader = requiredClass(READER);
        assertNotNull(reader.getAnnotation(Service.class));
        assertNull(reader.getAnnotation(RestController.class));
        assertNull(reader.getAnnotation(RequestMapping.class));
        assertEquals("erp", notNull(reader.getAnnotation(DS.class)).value());
        assertEquals(List.of("read"), Arrays.stream(reader.getDeclaredMethods())
            .filter(method -> Modifier.isPublic(method.getModifiers()) && !method.isSynthetic())
            .map(Method::getName).sorted().toList(), "禁止外部事实、verified 或快照时间的旁路入口");
        assertDoesNotThrow(() -> reader.getConstructor(requiredClass(MAPPER)));
        assertEquals(Set.of(requiredClass(MAPPER)), Arrays.stream(reader.getDeclaredFields())
            .filter(field -> !Modifier.isStatic(field.getModifiers())).map(field -> field.getType()).collect(Collectors.toSet()),
            "不可注入应用时钟、AI、Flow 或写入器");
        Transactional transaction = notNull(entry().getAnnotation(Transactional.class));
        assertTrue(transaction.readOnly());
        assertEquals(Propagation.REQUIRES_NEW, transaction.propagation());
        assertEquals(Isolation.REPEATABLE_READ, transaction.isolation(), "不得依赖服务器默认隔离级别");
        Class<?> snapshot = entry().getReturnType();
        assertEquals("CatalogSnapshot", snapshot.getSimpleName());
        assertTrue(snapshot.isRecord());
        assertEquals(Set.of("tenantId", "vendorId", "relationId", "snapshotAt", "databaseTimeZone",
            "material", "terms", "baseUnit", "orderUnit", "moqUnit", "sourceVersions"),
            Arrays.stream(snapshot.getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toSet()),
            "目录不得声明授权、需求完整、全局额度或可执行资格");
    }

    @Test
    void dedicatedMapperHasOnlyFixedTenantScopedSelectsAndExplicitDatabaseClock() {
        Class<?> mapper = requiredClass(MAPPER);
        assertTrue(mapper.isInterface());
        assertEquals(0, mapper.getInterfaces().length, "不得继承包含写方法的 BaseMapper");
        assertEquals(Set.of("selectDatabaseClock", "selectMaterial", "selectVendor", "selectRelations", "selectUnits"),
            Arrays.stream(mapper.getDeclaredMethods()).map(Method::getName).collect(Collectors.toSet()));
        for (Method method : mapper.getDeclaredMethods()) {
            assertNull(method.getAnnotation(Insert.class));
            assertNull(method.getAnnotation(Update.class));
            assertNull(method.getAnnotation(Delete.class));
            String sql = sql(method);
            assertTrue(sql.matches("(?s).*\\bselect\\b.*"), method.getName());
            assertFalse(sql.contains("${"), "禁止 SQL 文本插值");
            assertFalse(sql.matches("(?s).*\\b(insert|update|delete|replace|alter|drop|truncate|call)\\b.*"));
            assertFalse(sql.contains("for update"));
            if (!method.getName().equals("selectDatabaseClock")) {
                assertTrue(sql.matches("(?s).*tenant_id\\s*=\\s*#\\{tenantid}.*"), method.getName());
                assertTrue(sql.matches("(?s).*del_flag\\s*=\\s*'0'.*"), method.getName());
                assertEquals("tenantId", notNull(method.getParameters()[0].getAnnotation(Param.class)).value());
            }
        }
        assertEquals(BdMaterial.class, query("selectMaterial", String.class, Long.class).getReturnType());
        assertEquals(BdVendor.class, query("selectVendor", String.class, Long.class).getReturnType());
        assertEquals("java.util.List<com.nexus.erp.bd.domain.BdMaterialVendor>",
            query("selectRelations", String.class, Long.class, Long.class).getGenericReturnType().getTypeName());
        assertEquals("java.util.List<com.nexus.erp.bd.domain.BdUnitOfMeasure>",
            query("selectUnits", String.class, List.class).getGenericReturnType().getTypeName());
        Class<?> clockType = requiredClass(MAPPER + "$DatabaseClock");
        assertTrue(clockType.isRecord());
        assertDoesNotThrow(() -> clockType.getConstructor(BigDecimal.class, String.class));
        assertEquals(clockType, query("selectDatabaseClock").getReturnType());
        String clock = sql(query("selectDatabaseClock"));
        assertTrue(clock.contains("unix_timestamp("), "数据库时间戳不得依赖 JVM 时区");
        assertTrue(clock.matches("(?s).*unix_timestamp\\(\\s*(current_timestamp\\(6\\)|now\\(6\\))\\s*\\).*"),
            "不能从固定日期或旧关系日期伪造数据库业务时钟");
        assertTrue(clock.contains("epoch_seconds"));
        assertTrue(clock.contains("@@session.time_zone"));
        assertTrue(clock.contains("database_time_zone"));
        assertFalse(clock.contains("#{"), "不接收调用方时间或时区");
        String relations = sql(query("selectRelations", String.class, Long.class, Long.class));
        assertTrue(relations.contains("bd_material_vendor"));
        assertTrue(relations.matches("(?s).*material_id\\s*=\\s*#\\{materialid}.*"));
        assertTrue(relations.matches("(?s).*vendor_id\\s*=\\s*#\\{vendorid}.*"));
        assertFalse(relations.matches("(?s).*\\blimit\\b.*"), "不能以 LIMIT 1 掩盖重复关系");
        for (String column : List.of("order_uom_id", "verified_moq_qty", "moq_uom_id", "order_multiple",
            "allow_split", "units_per_pack", "terms_version", "terms_effective_at", "terms_expires_at",
            "terms_verified_at", "relation_id", "status", "del_flag")) {
            assertTrue(relations.contains(column), column);
        }
        String units = sql(query("selectUnits", String.class, List.class));
        for (String column : List.of("bd_unit_of_measure", "uom_id", "primary_id", "is_base", "category",
            "conversion_rate", "quantity_scale", "conversion_version", "conversion_verified_at", "status", "del_flag")) {
            assertTrue(units.contains(column), column);
        }
        assertTrue(units.contains("<foreach"), "单位集合只能绑定参数，不可拼接 ID");
        assertTrue(sql(query("selectVendor", String.class, Long.class)).contains("bd_vendor"));
        assertTrue(sql(query("selectMaterial", String.class, Long.class)).contains("bd_material"));
    }

    @ParameterizedTest(name = "预拒绝且零查询：{0}")
    @MethodSource("invalidRequests")
    void tenantAndIdentityFailuresAreRejectedBeforeEveryMapperQuery(String name, boolean enabled,
        String current, String requested, Long materialId, Long vendorId) {
        Fixture fixture = new Fixture();
        try (MockedStatic<TenantHelper> ignored = tenant(enabled, current)) {
            ServiceException rejection = assertThrows(ServiceException.class,
                () -> fixture.read(requested, materialId, vendorId), name);
            assertTrue(rejection.getMessage().startsWith("采购目录来源未核验："),
                "查询外受控身份/租户拒绝必须保留，不能改成 Mapper 故障");
        }
        verifyNoInteractions(fixture.mapper);
        assertTrue(fixture.queries.isEmpty());
    }

    private static Stream<Arguments> invalidRequests() {
        return Stream.of(
            Arguments.of("租户功能关闭", false, TENANT, TENANT, MATERIAL_ID, VENDOR_ID),
            Arguments.of("当前租户缺失", true, null, TENANT, MATERIAL_ID, VENDOR_ID),
            Arguments.of("当前租户空白", true, " ", TENANT, MATERIAL_ID, VENDOR_ID),
            Arguments.of("请求租户缺失", true, TENANT, null, MATERIAL_ID, VENDOR_ID),
            Arguments.of("请求租户空白", true, TENANT, " ", MATERIAL_ID, VENDOR_ID),
            Arguments.of("请求租户不一致", true, TENANT, OTHER_TENANT, MATERIAL_ID, VENDOR_ID),
            Arguments.of("物料缺失", true, TENANT, TENANT, null, VENDOR_ID),
            Arguments.of("物料零值", true, TENANT, TENANT, 0L, VENDOR_ID),
            Arguments.of("物料负值", true, TENANT, TENANT, -1L, VENDOR_ID),
            Arguments.of("供应商缺失", true, TENANT, TENANT, MATERIAL_ID, null),
            Arguments.of("供应商零值", true, TENANT, TENANT, MATERIAL_ID, 0L),
            Arguments.of("供应商负值", true, TENANT, TENANT, MATERIAL_ID, -1L));
    }

    @Test
    void legacy510AndLegacyDatesCannotCertifyNullNewTerms() {
        Fixture fixture = new Fixture();
        for (String property : List.of("orderUomId", "verifiedMoqQty", "moqUomId", "orderMultiple",
            "allowSplit", "unitsPerPack", "termsVersion", "termsEffectiveAt", "termsExpiresAt", "termsVerifiedAt")) {
            setNull(fixture.relation, property);
        }
        denied(fixture);
        qty("510", fixture.relation.getMinOrderQty());
        assertNull(fixture.relation.getVerifiedMoqQty());
    }

    @ParameterizedTest(name = "条款字段未知：{0}")
    @ValueSource(strings = {"orderUomId", "verifiedMoqQty", "moqUomId", "orderMultiple", "allowSplit",
        "unitsPerPack", "termsVersion", "termsEffectiveAt", "termsExpiresAt", "termsVerifiedAt"})
    void eachUnknownTermIsRejectedWithoutLegacyFallback(String property) {
        Fixture fixture = new Fixture();
        setNull(fixture.relation, property);
        denied(fixture);
    }

    @Test
    void databaseSnapshotRatherThanApplicationClockControlsInclusiveTermsWindow() {
        Fixture fixture = new Fixture();
        Instant boundary = Instant.parse("2026-09-30T08:00:00Z");
        fixture.epochSeconds = epoch(boundary);
        fixture.relation.setTermsEffectiveAt(Date.from(boundary));
        fixture.relation.setTermsExpiresAt(Date.from(boundary));
        fixture.relation.setTermsVerifiedAt(Date.from(boundary));
        Object snapshot = readValid(fixture);
        assertEquals(boundary, property(snapshot, "snapshotAt"));
        assertEquals(DATABASE_TIME_ZONE, property(snapshot, "databaseTimeZone"));
        assertEquals("selectDatabaseClock", fixture.queries.get(0));
    }

    @ParameterizedTest(name = "数据库时区未知：{0}")
    @ValueSource(strings = {"", " ", "SYSTEM", "unknown/zone", "+99:00"})
    void unresolvableDatabaseTimeZoneRejectsBeforeCatalogQueries(String zone) {
        Fixture fixture = new Fixture();
        fixture.databaseTimeZone = zone;
        denied(fixture);
        assertEquals(List.of("selectDatabaseClock"), fixture.queries);
    }

    /** 可解析会话时区不能自证存储 DATETIME 的业务语义，必须先于目录查询拒绝。 */
    @ParameterizedTest(name = "可解析但不符合 Shanghai 存储合同的 session：{0}")
    @ValueSource(strings = {"UTC", "+00:00", "-05:00", "+07:00", "+09:00", "+08:01"})
    void parseableSessionZoneCannotCertifyShanghaiWallDatetimeBeforeCatalogRead(String sessionZone) {
        assertDoesNotThrow(() -> ZoneId.of(sessionZone), "本例不是不可解析时区造成的拒绝");
        Fixture fixture = new Fixture();
        fixture.databaseTimeZone = sessionZone;
        denied(fixture);
        assertEquals(List.of("selectDatabaseClock"), fixture.queries,
            "只能读取数据库时钟，不得查询物料、供应商、条款或单位后再判存储口径");
    }

    /**
     * 纯合成反例：2026-10-01 12:00 Shanghai 墙上到期是真实时间 04Z，
     * 06Z 快照已过期；误按 UTC 投影为 12Z 会伪装有效。不声称现库曾出现此误判。
     */
    @ParameterizedTest(name = "UTC错误投影不能复活Shanghai已过期条款：{0}")
    @ValueSource(strings = {"UTC", "+00:00"})
    void utcProjectionMustNotReviveExpiredShanghaiWallTerms(String sessionZone) {
        Instant snapshot = Instant.parse("2026-10-01T06:00:00Z");
        LocalDateTime wallExpiry = LocalDateTime.of(2026, 10, 1, 12, 0);
        Instant shanghaiExpiry = wallExpiry.atZone(ZoneId.of("Asia/Shanghai")).toInstant();
        Instant wrongUtcProjection = wallExpiry.toInstant(ZoneOffset.UTC);
        assertEquals(Instant.parse("2026-10-01T04:00:00Z"), shanghaiExpiry);
        assertEquals(Instant.parse("2026-10-01T12:00:00Z"), wrongUtcProjection);
        assertTrue(shanghaiExpiry.isBefore(snapshot));
        assertTrue(wrongUtcProjection.isAfter(snapshot));
        Fixture fixture = new Fixture();
        fixture.databaseTimeZone = sessionZone;
        fixture.epochSeconds = epoch(snapshot);
        fixture.relation.setTermsExpiresAt(Date.from(wrongUtcProjection));
        denied(fixture);
        assertEquals(List.of("selectDatabaseClock"), fixture.queries,
            "即使错误投影的日期看起来有效，也必须零目录查询前置拒绝");
    }

    /** +08:00 明确会话只允许正常核验来源，不能把实际过期解释为未知时区或有效。 */
    @Test
    void explicitShanghaiSessionPreservesExpiredWallTermRejection() {
        Instant snapshot = Instant.parse("2026-10-01T06:00:00Z");
        Instant shanghaiExpiry = LocalDateTime.of(2026, 10, 1, 12, 0)
            .atZone(ZoneId.of("Asia/Shanghai")).toInstant();
        Fixture fixture = new Fixture();
        fixture.databaseTimeZone = "+08:00";
        fixture.epochSeconds = epoch(snapshot);
        fixture.relation.setTermsExpiresAt(Date.from(shanghaiExpiry));
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            ServiceException rejection = assertThrows(ServiceException.class,
                () -> fixture.read(TENANT, MATERIAL_ID, VENDOR_ID));
            assertTrue(rejection.getMessage().contains("已过期"));
        }
        assertEquals(List.of("selectDatabaseClock", "selectMaterial", "selectVendor", "selectRelations"), fixture.queries);
    }

    @Test
    void nullDatabaseClockOrEpochOrTimeZoneIsNotReplacedByJvmTime() {
        for (int missing = 0; missing < 3; missing++) {
            Fixture fixture = new Fixture();
            if (missing == 0) fixture.missingClock = true;
            if (missing == 1) fixture.epochSeconds = null;
            if (missing == 2) fixture.databaseTimeZone = null;
            denied(fixture);
            assertEquals(List.of("selectDatabaseClock"), fixture.queries);
        }
    }

    @ParameterizedTest(name = "未知/失效目录行：{0}")
    @MethodSource("invalidCatalogRows")
    void scopedActiveNotDeletedIdentitiesAreCheckedAgainOnMutableMapperRows(String name, Consumer<Fixture> corrupt) {
        Fixture fixture = new Fixture();
        corrupt.accept(fixture);
        denied(fixture);
    }

    private static Stream<Arguments> invalidCatalogRows() {
        return Stream.of(
            bad("物料跨租户", f -> f.material.setTenantId(OTHER_TENANT)),
            bad("物料身份不符", f -> f.material.setMaterialId(MATERIAL_ID + 10)),
            bad("物料停用", f -> f.material.setStatus(0L)),
            bad("物料状态未知", f -> f.material.setStatus(null)),
            bad("物料已删除", f -> f.material.setDelFlag("1")),
            bad("物料删除状态未知", f -> f.material.setDelFlag(null)),
            bad("物料基本单位缺失", f -> f.material.setBaseUomId(null)),
            bad("供应商跨租户", f -> f.vendor.setTenantId(OTHER_TENANT)),
            bad("供应商身份不符", f -> f.vendor.setVendorId(VENDOR_ID + 10)),
            bad("供应商已冻结", f -> f.vendor.setStatus("FROZEN")),
            bad("供应商状态未知", f -> f.vendor.setStatus(null)),
            bad("供应商已删除", f -> f.vendor.setDelFlag("1")),
            bad("供应商删除状态未知", f -> f.vendor.setDelFlag(null)),
            bad("关系跨租户", f -> f.relation.setTenantId(OTHER_TENANT)),
            bad("关系物料不符", f -> f.relation.setMaterialId(MATERIAL_ID + 10)),
            bad("关系供应商不符", f -> f.relation.setVendorId(VENDOR_ID + 10)),
            bad("关系身份缺失", f -> f.relation.setRelationId(null)),
            bad("关系停用", f -> f.relation.setStatus(0L)),
            bad("关系状态未知", f -> f.relation.setStatus(null)),
            bad("关系已删除", f -> f.relation.setDelFlag("1")),
            bad("关系删除状态未知", f -> f.relation.setDelFlag(null)),
            bad("单位跨租户", f -> f.units.get(0).setTenantId(OTHER_TENANT)),
            bad("单位身份不符", f -> f.units.get(0).setUomId(BASE_ID + 20)),
            bad("单位停用", f -> f.units.get(0).setStatus(0L)),
            bad("单位状态未知", f -> f.units.get(0).setStatus(null)),
            bad("单位已删除", f -> f.units.get(0).setDelFlag("1")),
            bad("单位删除状态未知", f -> f.units.get(0).setDelFlag(null)),
            bad("同单位核验时间在未来", f -> f.units.get(0).setConversionVerifiedAt(Date.from(SNAPSHOT.plusSeconds(1)))),
            bad("旧日期有效但新条款未生效", f -> f.relation.setTermsEffectiveAt(Date.from(SNAPSHOT.plusSeconds(1)))),
            bad("旧日期有效但新条款过期", f -> f.relation.setTermsExpiresAt(Date.from(SNAPSHOT.minusSeconds(1)))),
            bad("条款核验时间在未来", f -> f.relation.setTermsVerifiedAt(Date.from(SNAPSHOT.plusSeconds(1)))),
            bad("条款起止倒置", f -> f.relation.setTermsEffectiveAt(Date.from(EXPIRES.plusSeconds(1)))),
            bad("空条款版本", f -> f.relation.setTermsVersion(" ")),
            bad("MOQ非正数", f -> f.relation.setVerifiedMoqQty(BigDecimal.ZERO)),
            bad("订购倍数非正数", f -> f.relation.setOrderMultiple(bd("-1"))),
            bad("包装非正数", f -> f.relation.setUnitsPerPack(BigDecimal.ZERO)),
            bad("MOQ超DECIMAL18_4", f -> f.relation.setVerifiedMoqQty(bd("100000000000000"))),
            bad("包装超数据库精度", f -> f.relation.setUnitsPerPack(bd("1.00001"))),
            bad("个数包装不可舍入", f -> f.relation.setUnitsPerPack(bd("5.4292"))),
            bad("MOQ单位不存在", f -> f.relation.setMoqUomId(ROOT_ID)));
    }

    @Test
    void missingRowsAndDuplicateRelationsOrUnitsAreRejected() {
        for (int missing = 0; missing < 6; missing++) {
            Fixture fixture = new Fixture();
            if (missing == 0) fixture.material = null;
            if (missing == 1) fixture.vendor = null;
            if (missing == 2) fixture.relations.clear();
            if (missing == 3) fixture.relations.add(fixture.relation);
            if (missing == 4) fixture.units.clear();
            if (missing == 5) fixture.units.add(fixture.units.get(0));
            denied(fixture);
        }
    }

    @ParameterizedTest(name = "单位核验字段未知：{0}")
    @ValueSource(strings = {"quantityScale", "conversionVersion", "conversionVerifiedAt", "category",
        "primaryId", "isBase", "conversionRate"})
    void identityRateOneAndMatchingNamesDoNotReplaceUnitVerification(String property) {
        Fixture fixture = new Fixture();
        setNull(fixture.units.get(0), property);
        denied(fixture);
    }

    @ParameterizedTest(name = "非法单位/共同根：{0}")
    @MethodSource("invalidUnits")
    void dimensionsRootsVersionsScalesAndExactRelativeRatesMustBeVerified(String name, Consumer<Fixture> corrupt) {
        Fixture fixture = gramsToKilograms();
        corrupt.accept(fixture);
        denied(fixture);
    }

    private static Stream<Arguments> invalidUnits() {
        return Stream.of(
            bad("同名单位维度不同", f -> { f.units.get(1).setUomName("克"); f.units.get(1).setCategory("COUNT"); }),
            bad("主单位维度未知", f -> f.units.get(0).setCategory(" ")),
            bad("订购单位未来核验", f -> f.units.get(1).setConversionVerifiedAt(Date.from(SNAPSHOT.plusSeconds(1)))),
            bad("换算版本不符", f -> f.units.get(1).setConversionVersion("other-version")),
            bad("共同根核验字段缺失", f -> f.units.get(0).setConversionVerifiedAt(null)),
            bad("共同根转换率不是1", f -> f.units.get(0).setConversionRate(bd("3"))),
            bad("订购单位跨租户", f -> f.units.get(1).setTenantId(OTHER_TENANT)),
            bad("订购单位状态未知", f -> f.units.get(1).setStatus(null)),
            bad("订购单位停用", f -> f.units.get(1).setStatus(0L)),
            bad("订购单位已删除", f -> f.units.get(1).setDelFlag("1")),
            bad("订购单位精度负值", f -> f.units.get(1).setQuantityScale(-1)),
            bad("订购单位精度超过4", f -> f.units.get(1).setQuantityScale(5)),
            bad("换算率为零", f -> f.units.get(1).setConversionRate(BigDecimal.ZERO)),
            bad("换算率为负", f -> f.units.get(1).setConversionRate(bd("-1000"))),
            bad("换算率超过6位", f -> f.units.get(1).setConversionRate(bd("1000.0000001"))),
            bad("换算率超DECIMAL18_6", f -> f.units.get(1).setConversionRate(bd("1000000000000"))),
            bad("必要祖先缺失", f -> f.units.get(1).setPrimaryId(ROOT_ID)),
            bad("循环祖先", f -> { f.units.get(0).setIsBase(0L); f.units.get(0).setPrimaryId(ORDER_ID); }),
            bad("同维度但共同主单位不符", f -> {
                f.units.add(unit(ROOT_ID, "MASS", ROOT_ID, 1L, "1", 0));
                f.units.get(1).setPrimaryId(ROOT_ID);
            }),
            bad("未知多级链", f -> {
                BdUnitOfMeasure ancestor = unit(ROOT_ID, "MASS", BASE_ID, 0L, "10", 4);
                ancestor.setConversionVersion(null);
                f.units.add(ancestor);
                f.units.get(1).setPrimaryId(ROOT_ID);
            }),
            bad("相对比例1除3不得舍入", f -> {
                f.material.setBaseUomId(ORDER_ID);
                f.relation.setOrderUomId(BASE_ID);
                f.relation.setMoqUomId(ORDER_ID);
                f.relation.setOrderMultiple(bd("1"));
                f.units.get(1).setConversionRate(bd("3"));
            }),
            bad("相对比例精确但超6位仍拒绝", f -> {
                f.units.get(1).setConversionRate(bd("10000000"));
                f.material.setBaseUomId(ORDER_ID);
                f.relation.setOrderUomId(BASE_ID);
                f.relation.setMoqUomId(ORDER_ID);
                f.relation.setOrderMultiple(bd("1"));
            }));
    }

    @Test
    void sameVerifiedUnitProducesIdentityWithoutInheritingLegacyMoq510() {
        Object snapshot = readValid(new Fixture());
        Material material = part(snapshot, "material", Material.class);
        SupplierTerms terms = part(snapshot, "terms", SupplierTerms.class);
        TrustedUnit base = part(snapshot, "baseUnit", TrustedUnit.class);
        assertEquals(SNAPSHOT, property(snapshot, "snapshotAt"), "数据库微秒时间戳必须精确保留");
        assertEquals(TENANT, property(snapshot, "tenantId"));
        assertEquals(TENANT, material.tenantId());
        assertEquals(Long.toString(MATERIAL_ID), material.materialId());
        assertEquals(Long.toString(BASE_ID), material.baseUomId());
        assertTrue(material.verified());
        assertEquals(Long.toString(VENDOR_ID), property(snapshot, "vendorId"));
        assertEquals(Long.toString(RELATION_ID), property(snapshot, "relationId"));
        assertEquals(Long.toString(RELATION_ID), terms.termsId());
        qty("1", terms.verifiedMoqQty());
        assertTrue(terms.verified());
        assertEquals(base, part(snapshot, "orderUnit", TrustedUnit.class));
        assertEquals(base, part(snapshot, "moqUnit", TrustedUnit.class));
        assertTrue(base.verified());
        qty("1", base.basePerUnit());
        assertEquals(Long.toString(BASE_ID), base.baseUnitId());
        assertEquals(TERMS_VERSION, versions(snapshot).get("terms:" + RELATION_ID));
        assertEquals(CONVERSION_VERSION, versions(snapshot).get("unit:" + BASE_ID));
    }

    @Test
    void confirmedGramsToKilogramsAreExactRelativeToMaterialBaseNotGuessedFromName() {
        Object snapshot = readValid(gramsToKilograms());
        TrustedUnit base = part(snapshot, "baseUnit", TrustedUnit.class);
        TrustedUnit order = part(snapshot, "orderUnit", TrustedUnit.class);
        assertEquals("MASS", base.dimensionId());
        assertEquals(base.dimensionId(), order.dimensionId());
        assertEquals(Long.toString(BASE_ID), order.baseUnitId());
        assertEquals(CONVERSION_VERSION, order.conversionVersion());
        qty("1000", order.basePerUnit());
        var evaluation = new ErpPurchaseQuantityPolicy().evaluate(new Input(
            demand(snapshot, "1500"), part(snapshot, "material", Material.class), part(snapshot, "terms", SupplierTerms.class),
            base, order, part(snapshot, "moqUnit", TrustedUnit.class), null, false));
        assertTrue(evaluation.allowed(), "仅纯数量合同互操作，不授予需求认证或执行资格");
        qty("1.5", evaluation.requestedOrderQty());
        qty("1500", evaluation.orderedBaseQty());
    }

    @Test
    void materialBaseKilogramsRequiresRelativeGrams0001NotRootRateOne() {
        Fixture fixture = gramsToKilograms();
        fixture.material.setBaseUomId(ORDER_ID);
        fixture.relation.setOrderUomId(BASE_ID);
        fixture.relation.setMoqUomId(BASE_ID);
        fixture.relation.setOrderMultiple(bd("500"));
        fixture.relation.setUnitsPerPack(bd("0.5"));
        Object snapshot = readValid(fixture);
        TrustedUnit base = part(snapshot, "baseUnit", TrustedUnit.class);
        TrustedUnit order = part(snapshot, "orderUnit", TrustedUnit.class);
        qty("1", base.basePerUnit());
        qty("0.001", order.basePerUnit());
        assertEquals(Long.toString(ORDER_ID), order.baseUnitId());
        assertEquals(CONVERSION_VERSION, versions(snapshot).get("unit:" + BASE_ID), "共同根版本也必须冻结");
        var evaluation = new ErpPurchaseQuantityPolicy().evaluate(new Input(
            demand(snapshot, "1.5"), part(snapshot, "material", Material.class), part(snapshot, "terms", SupplierTerms.class),
            base, order, part(snapshot, "moqUnit", TrustedUnit.class), null, false));
        assertTrue(evaluation.allowed());
        qty("1500", evaluation.requestedOrderQty());
    }

    @Test
    void quantityPolicyStillRejects54292PiecesWithoutRoundingToSix() {
        Object snapshot = readValid(new Fixture());
        var evaluation = new ErpPurchaseQuantityPolicy().evaluate(new Input(
            demand(snapshot, "5.4292"), part(snapshot, "material", Material.class), part(snapshot, "terms", SupplierTerms.class),
            part(snapshot, "baseUnit", TrustedUnit.class), part(snapshot, "orderUnit", TrustedUnit.class),
            part(snapshot, "moqUnit", TrustedUnit.class), null, false));
        assertFalse(evaluation.allowed());
        assertEquals(RejectionReason.INVALID_PRECISION, evaluation.reason());
        assertNull(evaluation.requestedOrderQty());
        assertNull(evaluation.orderedBaseQty());
    }

    @Test
    void frozenPartsDatesAndVersionsDoNotAliasMutableMapperEntities() {
        Fixture fixture = gramsToKilograms();
        Object snapshot = readValid(fixture);
        Material material = part(snapshot, "material", Material.class);
        SupplierTerms terms = part(snapshot, "terms", SupplierTerms.class);
        TrustedUnit base = part(snapshot, "baseUnit", TrustedUnit.class);
        TrustedUnit order = part(snapshot, "orderUnit", TrustedUnit.class);
        fixture.material.setTenantId(OTHER_TENANT);
        fixture.material.setBaseUomId(ROOT_ID);
        fixture.vendor.setVendorId(1L);
        fixture.relation.setVerifiedMoqQty(bd("510"));
        fixture.relation.setTermsVersion("mutated");
        fixture.relation.getTermsEffectiveAt().setTime(0);
        fixture.relation.getTermsExpiresAt().setTime(0);
        fixture.relation.getTermsVerifiedAt().setTime(0);
        fixture.units.get(0).getConversionVerifiedAt().setTime(0);
        fixture.units.get(1).setConversionRate(bd("6"));
        fixture.units.get(1).setConversionVersion("mutated");
        fixture.units.clear();
        fixture.relations.clear();
        assertEquals(TENANT, material.tenantId());
        assertEquals(Long.toString(BASE_ID), material.baseUomId());
        qty("500", terms.verifiedMoqQty());
        assertEquals(EFFECTIVE, terms.effectiveAt());
        assertEquals(EXPIRES, terms.expiresAt());
        assertEquals(VERIFIED, terms.verifiedAt());
        assertEquals(VERIFIED, base.verifiedAt());
        qty("1000", order.basePerUnit());
        assertEquals(TERMS_VERSION, terms.termsVersion());
        assertEquals(CONVERSION_VERSION, order.conversionVersion());
        assertEquals(TERMS_VERSION, versions(snapshot).get("terms:" + RELATION_ID));
        assertThrows(UnsupportedOperationException.class, () -> versions(snapshot).put("forged", "verified"));
        for (RecordComponent component : snapshot.getClass().getRecordComponents()) {
            assertFalse(Date.class.isAssignableFrom(component.getType()));
            assertFalse(component.getType().getName().startsWith("com.nexus.erp.bd.domain."));
        }
    }

    @Test
    void actualProxyRoutesBeforeReadOnlyNewTransactionAndRestoresSuspendedCaller() {
        Fixture fixture = new Fixture();
        DynamicDataSourceContextHolder.push("master");
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            new TransactionTemplate(fixture.transactions).execute(status -> {
                int outer = fixture.transactions.currentId();
                assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                fixture.read(TENANT, MATERIAL_ID, VENDOR_ID);
                assertEquals(outer, fixture.transactions.currentId());
                assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                assertEquals("master", DynamicDataSourceContextHolder.peek());
                assertNull(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel(),
                    "恢复调用方原隔离级别，不能泄漏目录 REPEATABLE_READ 上下文");
                fixture.read(TENANT, MATERIAL_ID, VENDOR_ID);
                assertEquals(outer, fixture.transactions.currentId());
                return null;
            });
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
        assertEquals(3, fixture.transactions.begins);
        assertEquals(List.of("master", "erp", "erp"), fixture.transactions.routes);
        assertEquals(3, fixture.transactions.commits);
        assertEquals(0, fixture.transactions.rollbacks);
        assertTrue(fixture.queryTransactions.stream().allMatch(id -> id != 1));
        assertEquals(2, Set.copyOf(fixture.queryTransactions).size());
    }

    /** Mapper 返回的异常不因 ServiceException 类名而成为 reader 的受控业务校验。 */
    @Test
    void mapperServiceExceptionIsReplacedWithoutFactsAndProxyContextIsCleaned() {
        Fixture fixture = new Fixture();
        ServiceException failure = new ServiceException("离线查询失败");
        fixture.queryFailure = failure;
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            ServiceException diagnosis = assertThrows(ServiceException.class,
                () -> fixture.read(TENANT, MATERIAL_ID, VENDOR_ID));
            safeMapperDiagnosis(failure, diagnosis);
        }
        assertEquals("离线查询失败", failure.getMessage(), "不能靠改写原异常原地脱敏");
        assertEquals(0, fixture.transactions.commits);
        assertEquals(1, fixture.transactions.rollbacks);
    }

    /**
     * 保护内部调用方及 ServiceException 路径，不预设全局 Runtime 处理器原文回显。
     * 五个查询点均通过真实服务代理触发，异常矩阵不包含任何真实敏感资料。
     */
    @ParameterizedTest(name = "Mapper 故障固定脱敏：{0}/{1}")
    @MethodSource("mapperRuntimeFailures")
    void mapperRuntimeFaultCannotExposeSyntheticSecretsInAnyThrowableView(String query, String kind) {
        Fixture fixture = new Fixture();
        RuntimeException original = sensitiveMapperFailure(kind);
        for (String marker : SYNTHETIC_SENSITIVE_MARKERS) {
            assertTrue(stackOutput(original).contains(marker), "故障夹具必须实际携带合成标记");
        }
        fixture.failureQuery = query;
        fixture.queryFailure = original;
        ServiceException diagnosis;
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            diagnosis = assertThrows(ServiceException.class, () -> fixture.read(TENANT, MATERIAL_ID, VENDOR_ID));
        }
        safeMapperDiagnosis(original, diagnosis);
        failedExactlyAt(fixture, query);
    }

    private static Stream<Arguments> mapperRuntimeFailures() {
        return MAPPER_QUERY_ORDER.stream().flatMap(query -> Stream.of(
            "runtime", "mybatis", "connection", "grammar", "uncategorized",
            "service-message", "service-detail", "service-cause")
            .map(kind -> Arguments.of(query, kind)));
    }

    /** 即使未含敏感标记，Mapper 的 ServiceException 也不能冒充查询外受控业务拒绝。 */
    @ParameterizedTest(name = "Mapper ServiceException 不按类型透传：{0}")
    @MethodSource("mapperQueries")
    void mapperServiceExceptionCannotCertifyReaderControlledBusinessRejection(String query) {
        Fixture fixture = new Fixture();
        ServiceException original = new ServiceException("既有目录业务拒绝", 409).setDetailMessage("原有业务诊断");
        fixture.failureQuery = query;
        fixture.queryFailure = original;
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            ServiceException diagnosis = assertThrows(ServiceException.class,
                () -> fixture.read(TENANT, MATERIAL_ID, VENDOR_ID));
            safeMapperDiagnosis(original, diagnosis);
        }
        assertEquals("既有目录业务拒绝", original.getMessage());
        assertEquals("原有业务诊断", original.getDetailMessage());
        assertEquals(Integer.valueOf(409), original.getCode());
        failedExactlyAt(fixture, query);
    }

    /** 所有 Mapper Runtime 使用新的固定诊断，不能沿用原异常、内部明细或异常链。 */
    private static void safeMapperDiagnosis(RuntimeException original, ServiceException diagnosis) {
        assertAll(
            () -> assertEquals(MAPPER_FAILURE_DIAGNOSTIC, diagnosis.getMessage()),
            () -> assertNotSame(original, diagnosis),
            () -> assertNull(diagnosis.getCause(), "不能保留 JDBC/Mapper 原异常为 cause"),
            () -> assertEquals(0, diagnosis.getSuppressed().length, "不能保留原始 suppressed 异常"),
            () -> assertNull(diagnosis.getDetailMessage(), "内部诊断字段也不得携带原文"),
            () -> noSyntheticSecrets(diagnosis.getMessage()),
            () -> noSyntheticSecrets(diagnosis.toString()),
            () -> noSyntheticSecrets(String.valueOf(diagnosis.getCause())),
            () -> noSyntheticSecrets(Arrays.toString(diagnosis.getSuppressed())),
            () -> noSyntheticSecrets(stackOutput(diagnosis))
        );
    }

    private static Stream<String> mapperQueries() {
        return MAPPER_QUERY_ORDER.stream();
    }

    /** 只构造轻量合成 VMError 和 ThreadDeath，不分配巨量内存，不调用线程停止。 */
    @SuppressWarnings("removal")
    @ParameterizedTest(name = "致命 Error 必须原样逃逸：{0}/VMError={1}")
    @MethodSource("fatalMapperFailures")
    void virtualMachineErrorAndThreadDeathMustNotBeInterceptedOrSwallowed(String query, boolean virtualMachine) {
        Fixture fixture = new Fixture();
        Error fatal = virtualMachine ? new SyntheticVirtualMachineError() : new ThreadDeath();
        fixture.failureQuery = query;
        fixture.fatalFailure = fatal;
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            assertSame(fatal, assertThrows(Error.class, () -> fixture.read(TENANT, MATERIAL_ID, VENDOR_ID)));
        }
        failedExactlyAt(fixture, query);
    }

    private static Stream<Arguments> fatalMapperFailures() {
        return MAPPER_QUERY_ORDER.stream().flatMap(query -> Stream.of(
            Arguments.of(query, true), Arguments.of(query, false)));
    }

    /** 故障只发生在指定查询点，不可吞错后继续查询、返回目录或重试读取。 */
    private static void failedExactlyAt(Fixture fixture, String query) {
        assertEquals(MAPPER_QUERY_ORDER.subList(0, MAPPER_QUERY_ORDER.indexOf(query) + 1), fixture.queries);
        assertEquals(0, fixture.transactions.commits);
        assertEquals(1, fixture.transactions.rollbacks);
    }

    private static RuntimeException sensitiveMapperFailure(String kind) {
        String sql = "SELECT CATALOG_SYNTH_SQL FROM fixture_catalog";
        String transport = "connection=CATALOG_SYNTH_CONNECTION password=CATALOG_SYNTH_PASSWORD bearer=CATALOG_SYNTH_BEARER";
        String payload = sql + "; " + transport;
        SQLException jdbc = new SQLException(payload, "HY000", 0);
        jdbc.addSuppressed(new IllegalStateException("合成 JDBC suppressed：" + payload));
        RuntimeException failure = switch (kind) {
            case "runtime" -> new RuntimeException(payload, jdbc);
            case "mybatis" -> new PersistenceException(payload, jdbc);
            case "connection" -> new DataAccessResourceFailureException(payload, jdbc);
            case "grammar" -> new BadSqlGrammarException("合成目录查询：" + transport, sql, jdbc);
            case "uncategorized" -> new UncategorizedSQLException("合成目录查询：" + transport, sql, jdbc);
            case "service-message" -> new ServiceException(payload);
            case "service-detail" -> new ServiceException("合成 Mapper 故障").setDetailMessage(payload);
            case "service-cause" -> {
                ServiceException service = new ServiceException("合成 Mapper 故障");
                service.initCause(jdbc);
                yield service;
            }
            default -> throw new IllegalArgumentException("未知离线异常种类");
        };
        failure.addSuppressed(new IllegalStateException("合成 Mapper suppressed：" + payload));
        failure.setStackTrace(new StackTraceElement[]{new StackTraceElement(
            "synthetic.CATALOG_SYNTH_SQL", "CATALOG_SYNTH_CONNECTION", "CATALOG_SYNTH_PASSWORD_CATALOG_SYNTH_BEARER.java", 1)});
        return failure;
    }

    private static void noSyntheticSecrets(String output) {
        for (String marker : SYNTHETIC_SENSITIVE_MARKERS) {
            assertFalse(String.valueOf(output).contains(marker), "异常输出不得包含合成敏感标记：" + marker);
        }
    }

    private static String stackOutput(Throwable failure) {
        StringWriter output = new StringWriter();
        try (PrintWriter writer = new PrintWriter(output)) {
            failure.printStackTrace(writer);
        }
        return output.toString();
    }

    /** 合成 VMError 仅用于验证传播，不触发真实内存耗尽或虚拟机故障。 */
    private static final class SyntheticVirtualMachineError extends VirtualMachineError {
        private static final long serialVersionUID = 1L;
    }

    /** 原生 MyBatis 解析真实 Mapper 注解，不用测试中硬编码的新 handler 替代产品映射。 */
    @Test
    void registeredResultMapsScopeBinaryAndDateHandlersWithoutChangingGlobalBoolean() {
        Configuration configuration = mappedConfiguration();
        assertInstanceOf(BooleanTypeHandler.class, configuration.getTypeHandlerRegistry().getTypeHandler(Boolean.class));
        MappedStatement statement = configuration.getMappedStatement(MAPPER + ".selectRelations");
        ResultMap resultMap = statement.getResultMaps().get(0);
        assertEquals(BdMaterialVendor.class, resultMap.getType());
        ResultMapping split = resultMap.getResultMappings().stream()
            .filter(mapping -> mapping.getProperty().equals("allowSplit")).findFirst().orElse(null);
        assertNotNull(split, "本 Mapper 必须显式读取原始二值，不能依赖默认 Boolean 自动映射折叠");
        assertEquals("allowSplit", split.getColumn());
        assertEquals(Boolean.class, split.getJavaType());
        assertFalse(split.getTypeHandler() instanceof BooleanTypeHandler,
            "严格二值解码必须限定此 Mapper，不得沿用 getBoolean 非零折叠");
        for (DateColumn column : DATE_COLUMNS) registeredDateMapping(configuration, column);
        assertInstanceOf(BooleanTypeHandler.class, configuration.getTypeHandlerRegistry().getTypeHandler(Boolean.class));
    }

    @ParameterizedTest(name = "实际结果映射合法 allow_split：{0}")
    @MethodSource("validBinaryValues")
    void actualResultMapReadsBinaryZeroOneAndNullCorrectly(String rawText, Boolean expected) throws SQLException {
        BigDecimal raw = rawText == null ? null : new BigDecimal(rawText);
        Object actual = mappedValue(mappedConfiguration(), "selectRelations", "allowSplit", "allowSplit", raw, Types.TINYINT);
        assertEquals(expected, actual);
    }

    private static Stream<Arguments> validBinaryValues() {
        return Stream.of(Arguments.of("0", Boolean.FALSE), Arguments.of("1", Boolean.TRUE),
            Arguments.of(null, (Object) null));
    }

    @ParameterizedTest(name = "实际结果映射拒绝非法 allow_split：{0}")
    @ValueSource(strings = {"2", "-1", "3", "-2", "127", "-128"})
    void actualResultMapNeverFoldsNonBinaryRawValuesIntoTrustedBoolean(String rawText) throws SQLException {
        invalidMappedValueMustClose(mappedConfiguration(), "selectRelations", "allowSplit", "allowSplit",
            new BigDecimal(rawText), Types.TINYINT);
    }

    @ParameterizedTest(name = "真实日期 handler 的 NULL/精确毫秒：{0}")
    @MethodSource("dateColumns")
    void registeredDateHandlersPreserveNullAndExactMillis(DateColumn column) throws SQLException {
        Configuration configuration = mappedConfiguration();
        registeredDateMapping(configuration, column);
        assertNull(mappedValue(configuration, column.method(), column.property(), column.alias(), null, Types.DECIMAL));
        Date actual = (Date) mappedValue(configuration, column.method(), column.property(), column.alias(),
            EXACT_DATE_MILLIS, Types.DECIMAL);
        assertNotNull(actual);
        assertEquals(EXACT_DATE_MILLIS.longValueExact(), actual.getTime());
        Date trailingZeros = (Date) mappedValue(configuration, column.method(), column.property(), column.alias(),
            new BigDecimal("1790755200123.000"), Types.DECIMAL);
        assertNotNull(trailingZeros);
        assertEquals(EXACT_DATE_MILLIS.longValueExact(), trailingZeros.getTime());
    }

    private static Stream<DateColumn> dateColumns() {
        return DATE_COLUMNS.stream();
    }

    @ParameterizedTest(name = "真实日期 handler 拒绝不精确/超 long：{0}/{1}")
    @MethodSource("invalidDateMillis")
    void registeredDateHandlersDoNotTruncateFractionOrWrapOutOfLongRange(DateColumn column, String rawText)
        throws SQLException {
        Configuration configuration = mappedConfiguration();
        registeredDateMapping(configuration, column);
        invalidMappedValueMustClose(configuration, column.method(), column.property(), column.alias(),
            new BigDecimal(rawText), Types.DECIMAL);
    }

    private static Stream<Arguments> invalidDateMillis() {
        return DATE_COLUMNS.stream().flatMap(column -> Stream.of(
            "1790755200123.5", "9223372036854775808", "-9223372036854775809")
            .map(raw -> Arguments.of(column, raw)));
    }

    /** 仅修改隔离测试 JVM 的默认时区，持资源锁并 finally 恢复，不触及用户服务。 */
    @ResourceLock(Resources.TIME_ZONE)
    @ParameterizedTest(name = "真实日期结果映射不依赖 JVM 时区：{0}")
    @ValueSource(strings = {"UTC", "Asia/Shanghai", "America/Los_Angeles"})
    void registeredEpochDateHandlersAreIndependentOfJvmDefaultTimeZone(String zone) throws SQLException {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            Configuration configuration = mappedConfiguration();
            for (DateColumn column : DATE_COLUMNS) {
                registeredDateMapping(configuration, column);
                Date actual = (Date) mappedValue(configuration, column.method(), column.property(), column.alias(),
                    EXACT_DATE_MILLIS, Types.DECIMAL);
                assertNotNull(actual);
                assertEquals(EXACT_DATE_MILLIS.longValueExact(), actual.getTime(), column.property());
            }
        } finally {
            TimeZone.setDefault(original);
        }
        assertEquals(original, TimeZone.getDefault());
    }

    private static Configuration mappedConfiguration() {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        TypeHandler<Boolean> originalBoolean = configuration.getTypeHandlerRegistry().getTypeHandler(Boolean.class);
        configuration.addMapper(requiredClass(MAPPER));
        assertSame(originalBoolean, configuration.getTypeHandlerRegistry().getTypeHandler(Boolean.class),
            "不允许全局替换 Boolean 解码器");
        return configuration;
    }

    private static ResultMapping registeredDateMapping(Configuration configuration, DateColumn column) {
        MappedStatement statement = configuration.getMappedStatement(MAPPER + "." + column.method());
        ResultMapping mapping = statement.getResultMaps().get(0).getResultMappings().stream()
            .filter(candidate -> candidate.getProperty().equals(column.property())).findFirst().orElse(null);
        assertNotNull(mapping, column.property());
        assertEquals(column.alias(), mapping.getColumn());
        assertEquals(Date.class, mapping.getJavaType());
        assertNotNull(mapping.getTypeHandler());
        assertTrue(boundSql(statement).getSql().toLowerCase(Locale.ROOT)
            .contains("as " + column.alias().toLowerCase(Locale.ROOT)), "必须使用实际固定 SQL 的日期列别名");
        return mapping;
    }

    /** 接受明确 SQL 拒绝或 NULL；不将任意编程异常、AssertionError 或致命 Error 当成拒绝证明。 */
    private static void invalidMappedValueMustClose(Configuration configuration, String method, String property,
                                                   String alias, BigDecimal raw, int jdbcType) throws SQLException {
        try {
            assertNull(mappedValue(configuration, method, property, alias, raw, jdbcType),
                "非法数据库原值不能折叠、截断或回绕为已知字段");
        } catch (SQLException expected) {
            /** 解码器明确拒绝原值；不制造默认事实。 */
        } catch (ResultMapException expected) {
            assertInstanceOf(SQLException.class, expected.getCause(), "非 SQL 编程错误不能充当安全拒绝");
        }
    }

    private static BoundSql boundSql(MappedStatement statement) {
        return statement.getBoundSql(Map.of("tenantId", TENANT, "materialId", MATERIAL_ID,
            "vendorId", VENDOR_ID, "unitIds", List.of(BASE_ID)));
    }

    /** 原生 DefaultResultSetHandler 使用已注册 ResultMap 填充真实可变实体，无 SQL 执行。 */
    private static Object mappedValue(Configuration configuration, String method, String property, String alias,
                                      BigDecimal raw, int jdbcType) throws SQLException {
        MappedStatement statement = configuration.getMappedStatement(MAPPER + "." + method);
        Class<?> rowType = statement.getResultMaps().get(0).getType();
        String idAlias = rowType == BdMaterialVendor.class ? "relationId" : "uomId";
        long rowId = rowType == BdMaterialVendor.class ? RELATION_ID : BASE_ID;
        ResultSet result = syntheticNumericRow(idAlias, rowId, alias, raw, jdbcType);
        Statement jdbcStatement = mock(Statement.class);
        when(jdbcStatement.getResultSet()).thenReturn(result);
        when(jdbcStatement.getUpdateCount()).thenReturn(-1);
        Connection connection = mock(Connection.class);
        DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(jdbcStatement.getConnection()).thenReturn(connection);
        BoundSql sql = boundSql(statement);
        ParameterHandler parameters = configuration.newParameterHandler(statement, sql.getParameterObject(), sql);
        DefaultResultSetHandler reader = new DefaultResultSetHandler(mock(Executor.class), statement,
            parameters, null, sql, RowBounds.DEFAULT);
        List<Object> rows = reader.handleResultSets(jdbcStatement);
        assertEquals(1, rows.size(), "保留身份列，NULL 字段不能变成无结果而假通过");
        Object row = rows.get(0);
        assertInstanceOf(rowType, row);
        String getter = "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
        return invoke(assertDoesNotThrow(() -> rowType.getMethod(getter)), row);
    }

    /** 刻意模拟 getBoolean 非零折叠以暴露风险；这不是当前数据库或真实驱动验证。 */
    private static ResultSet syntheticNumericRow(String idAlias, long id, String alias, BigDecimal raw, int jdbcType)
        throws SQLException {
        List<String> columns = List.of(idAlias, alias);
        Map<String, Number> values = new LinkedHashMap<>();
        values.put(idAlias, id);
        values.put(alias, raw);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(metadata.getColumnCount()).thenReturn(2);
        for (int index = 1; index <= columns.size(); index++) {
            String column = columns.get(index - 1);
            when(metadata.getColumnLabel(index)).thenReturn(column);
            when(metadata.getColumnName(index)).thenReturn(column);
            when(metadata.getColumnType(index)).thenReturn(index == 1 ? Types.BIGINT : jdbcType);
            when(metadata.getColumnClassName(index)).thenReturn(index == 1 ? Long.class.getName() : BigDecimal.class.getName());
        }
        AtomicInteger cursor = new AtomicInteger();
        AtomicBoolean wasNull = new AtomicBoolean();
        AtomicBoolean closed = new AtomicBoolean();
        return mock(ResultSet.class, invocation -> {
            String name = invocation.getMethod().getName();
            if (name.equals("getMetaData")) return metadata;
            if (name.equals("next")) return cursor.getAndIncrement() == 0;
            if (name.equals("getType")) return ResultSet.TYPE_FORWARD_ONLY;
            if (name.equals("wasNull")) return wasNull.get();
            if (name.equals("isClosed")) return closed.get();
            if (name.equals("close")) { closed.set(true); return null; }
            if (name.startsWith("get") && invocation.getArguments().length > 0
                && (invocation.getArgument(0) instanceof String || invocation.getArgument(0) instanceof Integer)) {
                Object key = invocation.getArgument(0);
                String column = key instanceof Integer index ? columns.get(index - 1) : (String) key;
                assertTrue(values.containsKey(column), "不得读取 SQL 投影之外的列");
                Number value = values.get(column);
                wasNull.set(value == null);
                BigDecimal decimal = value == null ? null : value instanceof BigDecimal number ? number
                    : BigDecimal.valueOf(value.longValue());
                return switch (name) {
                    case "getBoolean" -> decimal != null && decimal.signum() != 0;
                    case "getBigDecimal" -> decimal;
                    case "getObject" -> value;
                    case "getString" -> decimal == null ? null : decimal.toPlainString();
                    case "getLong" -> value == null ? 0L : value.longValue();
                    case "getInt" -> value == null ? 0 : value.intValue();
                    case "getShort" -> value == null ? (short) 0 : value.shortValue();
                    case "getByte" -> value == null ? (byte) 0 : value.byteValue();
                    default -> Answers.RETURNS_DEFAULTS.answer(invocation);
                };
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
    }

    private record DateColumn(String method, String property, String alias) { }

    private static Arguments bad(String name, Consumer<Fixture> mutation) {
        return Arguments.of(name, mutation);
    }

    private static Fixture gramsToKilograms() {
        Fixture fixture = new Fixture();
        fixture.units.clear();
        fixture.units.add(unit(BASE_ID, "MASS", BASE_ID, 1L, "1", 0));
        fixture.units.add(unit(ORDER_ID, "MASS", BASE_ID, 0L, "1000", 4));
        fixture.relation.setOrderUomId(ORDER_ID);
        fixture.relation.setVerifiedMoqQty(bd("500"));
        fixture.relation.setOrderMultiple(bd("0.5"));
        fixture.relation.setUnitsPerPack(bd("500"));
        return fixture;
    }

    private static BdUnitOfMeasure unit(long id, String dimension, long root, long isBase, String rate, int scale) {
        BdUnitOfMeasure unit = new BdUnitOfMeasure();
        unit.setTenantId(TENANT);
        unit.setUomId(id);
        unit.setUomCode("fixture-" + id);
        unit.setUomName("离线单位，不作为换算依据");
        unit.setCategory(dimension);
        unit.setIsBase(isBase);
        unit.setPrimaryId(root);
        unit.setConversionRate(bd(rate));
        unit.setQuantityScale(scale);
        unit.setConversionVersion(CONVERSION_VERSION);
        unit.setConversionVerifiedAt(Date.from(VERIFIED));
        unit.setStatus(1L);
        unit.setDelFlag("0");
        return unit;
    }

    private static Object readValid(Fixture fixture) {
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            return notNull(fixture.read(TENANT, MATERIAL_ID, VENDOR_ID));
        }
    }

    /** 查询返回之后的来源、时区、条款和单位校验仍保留 reader 受控拒绝诊断。 */
    private static void denied(Fixture fixture) {
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            ServiceException rejection = assertThrows(ServiceException.class,
                () -> fixture.read(TENANT, MATERIAL_ID, VENDOR_ID));
            assertTrue(rejection.getMessage().startsWith("采购目录来源未核验："),
                "查询外受控业务校验不能被替换成 Mapper 故障诊断");
        }
    }

    /** 合成需求仅用于纯数量合同互操作，不是 reader 已核验的需求明细。 */
    private static Demand demand(Object snapshot, String quantity) {
        Material material = part(snapshot, "material", Material.class);
        return new Demand(TENANT, Long.toString(MATERIAL_ID), material.baseUomId(), bd(quantity),
            LocalDate.of(2026, 10, 2), (Instant) property(snapshot, "snapshotAt"), true);
    }

    private static MockedStatic<TenantHelper> tenant(boolean enabled, String current) {
        MockedStatic<TenantHelper> tenant = mockStatic(TenantHelper.class);
        tenant.when(TenantHelper::isEnable).thenReturn(enabled);
        tenant.when(TenantHelper::getTenantId).thenReturn(current);
        return tenant;
    }

    private static Class<?> requiredClass(String name) {
        return assertDoesNotThrow(() -> Class.forName(name), "缺少已批准的只读目录实现：" + name);
    }

    private static Method entry() {
        return assertDoesNotThrow(() -> requiredClass(READER).getMethod("read", String.class, Long.class, Long.class));
    }

    private static Method query(String name, Class<?>... arguments) {
        return assertDoesNotThrow(() -> requiredClass(MAPPER).getMethod(name, arguments));
    }

    private static String sql(Method method) {
        return String.join(" ", notNull(method.getAnnotation(Select.class)).value())
            .replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static Object property(Object bean, String name) {
        assertNotNull(bean, name);
        return invoke(assertDoesNotThrow(() -> bean.getClass().getMethod(name)), bean);
    }

    private static <T> T part(Object snapshot, String name, Class<T> type) {
        Object value = property(snapshot, name);
        assertInstanceOf(type, value);
        return type.cast(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> versions(Object snapshot) {
        Object value = property(snapshot, "sourceVersions");
        assertInstanceOf(Map.class, value);
        return (Map<String, String>) value;
    }

    private static void setNull(Object bean, String property) {
        String setter = "set" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
        Method method = Arrays.stream(bean.getClass().getMethods())
            .filter(candidate -> candidate.getName().equals(setter) && candidate.getParameterCount() == 1)
            .findFirst().orElseThrow(() -> new AssertionError("缺少现有实体方法：" + setter));
        invoke(method, bean, new Object[]{null});
    }

    private static Object invoke(Method method, Object target, Object... arguments) {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw new AssertionError("目录契约不应抛出受检异常", failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("目录契约反射调用失败", failure);
        }
    }

    private static <T> T notNull(T value) {
        assertNotNull(value);
        return value;
    }

    private static BigDecimal epoch(Instant value) {
        return BigDecimal.valueOf(value.getEpochSecond()).add(BigDecimal.valueOf(value.getNano(), 9));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static void qty(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, bd(expected).compareTo(actual));
    }

    /** 模拟固定 SELECT 返回可变现有实体；不实现 reader 的核验或换算算法。 */
    private static final class Fixture {
        private BdMaterial material = new BdMaterial();
        private BdVendor vendor = new BdVendor();
        private final BdMaterialVendor relation = new BdMaterialVendor();
        private final List<BdMaterialVendor> relations = new ArrayList<>();
        private final List<BdUnitOfMeasure> units = new ArrayList<>();
        private BigDecimal epochSeconds = epoch(SNAPSHOT);
        private String databaseTimeZone = DATABASE_TIME_ZONE;
        private boolean missingClock;
        private RuntimeException queryFailure;
        private String failureQuery;
        private Error fatalFailure;
        private final List<String> queries = new ArrayList<>();
        private final List<Integer> queryTransactions = new ArrayList<>();
        private final ProbeTransactionManager transactions = new ProbeTransactionManager();
        private final Object mapper;
        private final Object reader;

        private Fixture() {
            material.setTenantId(TENANT);
            material.setMaterialId(MATERIAL_ID);
            material.setBaseUomId(BASE_ID);
            material.setStatus(1L);
            material.setDelFlag("0");
            vendor.setTenantId(TENANT);
            vendor.setVendorId(VENDOR_ID);
            vendor.setStatus("ACTIVE");
            vendor.setDelFlag("0");
            relation.setTenantId(TENANT);
            relation.setRelationId(RELATION_ID);
            relation.setMaterialId(MATERIAL_ID);
            relation.setVendorId(VENDOR_ID);
            relation.setMinOrderQty(bd("510"));
            relation.setEffectiveDate(Date.from(EFFECTIVE));
            relation.setExpiryDate(Date.from(EXPIRES));
            relation.setOrderUomId(BASE_ID);
            relation.setMoqUomId(BASE_ID);
            relation.setVerifiedMoqQty(bd("1"));
            relation.setOrderMultiple(bd("1"));
            relation.setAllowSplit(true);
            relation.setUnitsPerPack(bd("1"));
            relation.setTermsVersion(TERMS_VERSION);
            relation.setTermsEffectiveAt(Date.from(EFFECTIVE));
            relation.setTermsExpiresAt(Date.from(EXPIRES));
            relation.setTermsVerifiedAt(Date.from(VERIFIED));
            relation.setStatus(1L);
            relation.setDelFlag("0");
            relations.add(relation);
            units.add(unit(BASE_ID, "COUNT", BASE_ID, 1L, "1", 0));
            Class<?> readerType = requiredClass(READER);
            Class<?> mapperType = requiredClass(MAPPER);
            mapper = mock(mapperType, invocation -> {
                if (invocation.getMethod().getDeclaringClass() == Object.class) {
                    return Answers.RETURNS_DEFAULTS.answer(invocation);
                }
                String name = invocation.getMethod().getName();
                queries.add(name);
                queryTransactions.add(transactions.currentId());
                assertEquals("erp", DynamicDataSourceContextHolder.peek(), "须经过实际数据源代理后查询");
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                assertEquals(Integer.valueOf(TransactionDefinition.ISOLATION_REPEATABLE_READ),
                    TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
                if (failureQuery == null || failureQuery.equals(name)) {
                    if (queryFailure != null) throw queryFailure;
                    if (fatalFailure != null) throw fatalFailure;
                }
                if (name.equals("selectDatabaseClock")) {
                    if (missingClock) return null;
                    return assertDoesNotThrow(() -> requiredClass(MAPPER + "$DatabaseClock")
                        .getConstructor(BigDecimal.class, String.class).newInstance(epochSeconds, databaseTimeZone));
                }
                assertEquals(TENANT, invocation.getArgument(0));
                return switch (name) {
                    case "selectMaterial" -> {
                        assertEquals(Long.valueOf(MATERIAL_ID), invocation.<Long>getArgument(1));
                        yield material;
                    }
                    case "selectVendor" -> {
                        assertEquals(Long.valueOf(VENDOR_ID), invocation.<Long>getArgument(1));
                        yield vendor;
                    }
                    case "selectRelations" -> {
                        assertEquals(Long.valueOf(MATERIAL_ID), invocation.<Long>getArgument(1));
                        assertEquals(Long.valueOf(VENDOR_ID), invocation.<Long>getArgument(2));
                        yield relations;
                    }
                    case "selectUnits" -> {
                        assertInstanceOf(List.class, invocation.getArgument(1));
                        List<?> ids = invocation.getArgument(1);
                        assertFalse(ids.isEmpty());
                        assertTrue(ids.stream().allMatch(Long.class::isInstance), "64 位 ID 不得改为 double 或窄整数");
                        yield units.stream().filter(unit -> ids.contains(unit.getUomId()))
                            .collect(Collectors.toCollection(ArrayList::new));
                    }
                    default -> throw new AssertionError("非约定查询：" + name);
                };
            });
            Object target = assertDoesNotThrow(() -> readerType.getConstructor(mapperType).newInstance(mapper));
            TransactionInterceptor transaction = new TransactionInterceptor();
            transaction.setTransactionManager(transactions);
            transaction.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
            ProxyFactory proxy = new ProxyFactory(target);
            proxy.setProxyTargetClass(true);
            proxy.addAdvisor(new DynamicDataSourceAnnotationAdvisor(
                new DynamicDataSourceAnnotationInterceptor(true, new DsSpelExpressionProcessor()), DS.class));
            proxy.addAdvice(transaction);
            reader = proxy.getProxy();
        }

        private Object read(String tenant, Long materialId, Long vendorId) {
            return invoke(entry(), reader, tenant, materialId, vendorId);
        }
    }

    /** 无连接、无 SQL 的事务生命周期探针，仅核验实际代理的传播和上下文恢复。 */
    private static final class ProbeTransactionManager extends AbstractPlatformTransactionManager {
        private final ThreadLocal<Integer> current = new ThreadLocal<>();
        private final List<String> routes = new ArrayList<>();
        private int begins;
        private int commits;
        private int rollbacks;

        private int currentId() {
            assertNotNull(current.get(), "查询必须处于由代理开启的事务中");
            return current.get();
        }

        @Override
        protected Object doGetTransaction() {
            return new ProbeTransaction(current.get());
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return ((ProbeTransaction) transaction).id != null;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            if ("erp".equals(DynamicDataSourceContextHolder.peek())) {
                assertTrue(definition.isReadOnly());
                assertEquals(TransactionDefinition.ISOLATION_REPEATABLE_READ, definition.getIsolationLevel());
            }
            begins++;
            routes.add(DynamicDataSourceContextHolder.peek());
            ((ProbeTransaction) transaction).id = begins;
            current.set(begins);
        }

        @Override
        protected Object doSuspend(Object transaction) {
            Integer suspended = ((ProbeTransaction) transaction).id;
            ((ProbeTransaction) transaction).id = null;
            current.remove();
            return suspended;
        }

        @Override
        protected void doResume(Object transaction, Object suspendedResources) {
            current.set((Integer) suspendedResources);
            if (transaction != null) ((ProbeTransaction) transaction).id = (Integer) suspendedResources;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            current.remove();
        }
    }

    private static final class ProbeTransaction {
        private Integer id;

        private ProbeTransaction(Integer id) {
            this.id = id;
        }
    }
}
