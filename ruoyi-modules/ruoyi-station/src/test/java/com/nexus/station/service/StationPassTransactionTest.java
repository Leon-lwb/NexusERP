package com.nexus.station.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.aop.DynamicDataSourceAnnotationAdvisor;
import com.baomidou.dynamic.datasource.aop.DynamicDataSourceAnnotationInterceptor;
import com.baomidou.dynamic.datasource.processor.DsSpelExpressionProcessor;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.common.trace.CodeCandidate;
import com.nexus.common.trace.CodeObjectType;
import com.nexus.common.trace.CodeResolution;
import com.nexus.common.trace.TraceHashes;
import com.nexus.common.trace.TrackingPolicy;
import com.nexus.common.trace.TrackingStage;
import com.nexus.common.trace.WorkOrderTrackingPolicySnapshot;
import com.nexus.common.trace.port.WorkOrderTrackingPolicyPort;
import com.nexus.mes.flash.domain.TestRecord;
import com.nexus.mes.flash.mapper.TestRecordMapper;
import com.nexus.mes.integration.trace.MesWorkOrderTrackingPolicyReader;
import com.nexus.mes.ncr.domain.NcrOrder;
import com.nexus.mes.ncr.mapper.NcrOrderMapper;
import com.nexus.mes.pro.domain.*;
import com.nexus.mes.pro.mapper.*;
import com.nexus.mes.pro.service.IProFlowCardService;
import com.nexus.mes.pro.service.IProSnService;
import com.nexus.mes.pro.service.MesProgressTransactionGuard;
import com.nexus.mes.pro.service.ProWorkOrderStepProgressService;
import com.nexus.mes.trace.MesPreparedWorkOrderPolicies;
import com.nexus.mes.trace.MesSerialReferenceService;
import com.nexus.mes.trace.MesTraceContext;
import com.nexus.station.domain.MesStationDevice;
import com.nexus.station.domain.MesStationReport;
import com.nexus.station.domain.dto.StationPassReportDto;
import com.nexus.station.mapper.*;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.LocalCacheScope;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.Date;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 真实 Spring 事务/动态数据源代理和生产 Mapper SQL 的私有 H2 回归。
 * 不连接业务库；H2 行锁证明不替代 MySQL5.7 的范围锁和硬件运行态验收。
 */
class StationPassTransactionTest {
    static final String STATION = "STN|JZ|L1|ASM|GENERAL";
    static final StationPassService.OperatorInfo OP =
            new StationPassService.OperatorInfo(105L, "EMP-1", "操作员");
    JdbcDataSource mes, master;
    JdbcTemplate db, masterDb, routedDb;
    DataSourceTransactionManager manager;
    StationPassService service;
    MesSerialReferenceService serialReferences;
    Map<String, CodeResolution> canonicalCodes;
    StationPassGuardMapper guard;
    MockedStatic<TenantHelper> tenant;
    AtomicBoolean failAfterIncrement;
    MesPreparedWorkOrderPolicies preparedPolicies;
    AtomicBoolean unknownRelease;
    AtomicInteger policyReads;
    StationSchWorkerMapper workers;
    IProFlowCardService flow;
    AtomicReference<Map<String, Object>> worker;

    @BeforeEach
    void setup() throws Exception {
        mes = database("mes");
        master = database("master");
        db = new JdbcTemplate(mes);
        masterDb = new JdbcTemplate(master);
        var routing =
                new AbstractRoutingDataSource() {
                    @Override
                    protected Object determineCurrentLookupKey() {
                        return DynamicDataSourceContextHolder.peek() == null
                                ? "master"
                                : DynamicDataSourceContextHolder.peek();
                    }
                };
        routing.setTargetDataSources(Map.of("mes", mes, "master", master));
        routing.setDefaultTargetDataSource(master);
        routing.setLenientFallback(false);
        routing.afterPropertiesSet();
        routedDb = new JdbcTemplate(routing);
        manager = new DataSourceTransactionManager(routing);
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setCacheEnabled(false);
        config.setLocalCacheScope(LocalCacheScope.STATEMENT);
        config.setEnvironment(
                new Environment("private-test", new SpringManagedTransactionFactory(), routing));
        for (Class<?> mapper :
                List.of(
                        ProSnMapper.class,
                        ProFlowCardMapper.class,
                        ProFlowCardStepMapper.class,
                        StationDeviceMapper.class,
                        StationReportMapper.class,
                        TestRecordMapper.class,
                        NcrOrderMapper.class,
                        StationPassGuardMapper.class)) {
            config.addMapper(mapper);
        }
        var sessionFactory = new MybatisSqlSessionFactoryBuilder().build(config);
        var sessions = new SqlSessionTemplate(sessionFactory);
        for (Class<?> entity :
                List.of(
                        ProSn.class,
                        ProFlowCard.class,
                        ProFlowCardStep.class,
                        MesStationDevice.class,
                        MesStationReport.class,
                        TestRecord.class,
                        NcrOrder.class)) {
            createTable(entity);
        }
        db.execute("ALTER TABLE mes_station_report ADD del_flag VARCHAR(1) DEFAULT '0'");
        db.execute("CREATE UNIQUE INDEX uk_client ON mes_station_report(tenant_id,client_key)");
        masterDb.execute("CREATE TABLE marker(id INT PRIMARY KEY)");
        db.execute(
                "CREATE TABLE pro_work_order(tenant_id VARCHAR(20),mes_wo_id BIGINT PRIMARY"
                    + " KEY,erp_wo_id BIGINT,material_id BIGINT,source_type VARCHAR(24),del_flag"
                    + " VARCHAR(1))");
        db.update("INSERT INTO pro_work_order VALUES('000000',20,200,77,'ERP','0')");
        db.execute(
                "CREATE TABLE pro_completion_manifest(manifest_id BIGINT PRIMARY KEY,revision"
                    + " INT,tenant_id VARCHAR(20),mes_wo_id BIGINT,content_hash VARCHAR(64),status"
                    + " VARCHAR(24),good_qty DECIMAL(18,4))");
        db.execute(
                "CREATE TABLE pro_completion_manifest_line(tenant_id VARCHAR(20),manifest_id"
                        + " BIGINT,revision INT,local_sn_id BIGINT,identity_id BIGINT,result"
                        + " VARCHAR(16))");
        db.execute(
                "CREATE TABLE mes_trace_outbox(event_id VARCHAR(64) PRIMARY KEY,tenant_id"
                        + " VARCHAR(20),source_module VARCHAR(16),aggregate_type"
                        + " VARCHAR(64),aggregate_id BIGINT,aggregate_version BIGINT,event_type"
                        + " VARCHAR(64),status VARCHAR(24),payload CLOB,content_hash"
                        + " VARCHAR(64),result_payload CLOB)");
        var steps = spy(sessions.getMapper(ProFlowCardStepMapper.class));
        failAfterIncrement = new AtomicBoolean();
        doAnswer(
                        i -> {
                            Object value = i.callRealMethod();
                            if (failAfterIncrement.get()) {
                                throw new ServiceException("模拟累计之后故障");
                            }
                            return value;
                        })
                .when(steps)
                .update(any(), any());
        // 内存数据库不支持对计数结果加行锁；先读取并锁定同一批实际行，再统计数量。
        doAnswer(
                        i -> {
                            Wrapper<?> query = i.getArgument(0);
                            assertTrue(query.getSqlSegment().contains("FOR UPDATE"));
                            return (long)
                                    routedDb.queryForList(
                                                    "SELECT step_record_id FROM pro_flow_card_step"
                                                            + " WHERE tenant_id=? AND card_id=10 AND"
                                                            + " del_flag='0' AND step_status NOT IN"
                                                            + " ('COMPLETED','SKIPPED') FOR UPDATE",
                                                    TenantHelper.getTenantId())
                                            .size();
                        })
                .when(steps)
                .selectCount(any());
        flow = mock(IProFlowCardService.class);
        doAnswer(
                        i -> {
                            new JdbcTemplate(routing)
                                    .update(
                                            "UPDATE pro_flow_card_step SET"
                                                    + " step_status='IN_PROGRESS' WHERE"
                                                    + " step_record_id=?",
                                            new Object[] {i.getArgument(1)});
                            return null;
                        })
                .when(flow)
                .startStep(any(), any(), any());
        var sns = mock(IProSnService.class);
        doAnswer(
                        i -> {
                            new JdbcTemplate(routing)
                                    .update(
                                            "UPDATE pro_sn SET sn_status='COMPLETED' WHERE sn_id=?",
                                            new Object[] {i.getArgument(0)});
                            return null;
                        })
                .when(sns)
                .completeSn(any());
        guard = sessions.getMapper(StationPassGuardMapper.class);
        workers = mock(StationSchWorkerMapper.class);
        worker = new AtomicReference<>(Map.of("user_id", 105L, "user_name", "操作员"));
        when(workers.selectByEmployeeId("EMP-1")).thenAnswer(i -> worker.get());
        canonicalCodes = new ConcurrentHashMap<>();
        registerCanonical("SN-1", 10001L, 1L);
        serialReferences =
                mesProxy(
                        new MesSerialReferenceService(
                                (tenantId, request, allowed) -> {
                                    assertEquals(TenantHelper.getTenantId(), tenantId);
                                    assertEquals(Set.of(CodeObjectType.SN), allowed);
                                    assertFalse(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive(),
                                            "ERP canonical resolution must run outside the held MES"
                                                    + " transaction");
                                    return canonicalCodes.getOrDefault(
                                            request.rawCode(),
                                            CodeResolution.missing(
                                                    "No approved identity or alias"));
                                },
                                sessions.getMapper(ProSnMapper.class),
                                new MesTraceContext(
                                        new MesProgressTransactionGuard(sessionFactory))));
        unknownRelease = new AtomicBoolean();
        policyReads = new AtomicInteger();
        WorkOrderTrackingPolicyPort bank =
                (policyTenant, erpWo, material) -> {
                    assertFalse(
                            TransactionSynchronizationManager.isActualTransactionActive(),
                            "ERP release policy must be read through the NOT_SUPPORTED proxy before"
                                    + " MES row locks");
                    assertEquals("000000", policyTenant);
                    assertEquals(200L, erpWo);
                    assertEquals(77L, material);
                    assertEquals(
                            0,
                            count(),
                            "New station policy must be prepared before its report insert");
                    assertEquals(
                            0,
                            good().signum(),
                            "New station policy must be prepared before quantity aggregation");
                    policyReads.incrementAndGet();
                    if (unknownRelease.get()) {
                        return WorkOrderTrackingPolicySnapshot.unknown(
                                policyTenant, erpWo, material, "release evidence unavailable");
                    }
                    var time = Instant.parse("2026-10-18T00:00:00Z");
                    return new WorkOrderTrackingPolicySnapshot(
                            policyTenant,
                            erpWo,
                            material,
                            WorkOrderTrackingPolicySnapshot.Provenance.ERP_RELEASE,
                            new TrackingPolicy(false, true, true, 7L, TrackingStage.STRICT),
                            1,
                            "a".repeat(64),
                            "b".repeat(64),
                            "c".repeat(64),
                            "ERP_RELEASE:200",
                            time,
                            time,
                            null,
                            null);
                };
        var policyReader =
                mesProxy(
                        new MesWorkOrderTrackingPolicyReader(
                                routedDb,
                                new MesTraceContext(
                                        new MesProgressTransactionGuard(sessionFactory)),
                                bank));
        preparedPolicies =
                new MesPreparedWorkOrderPolicies(
                        routedDb,
                        new MesTraceContext(new MesProgressTransactionGuard(sessionFactory)),
                        policyReader);
        var target =
                new StationPassService(
                        sessions.getMapper(StationReportMapper.class),
                        workers,
                        sessions.getMapper(StationDeviceMapper.class),
                        sessions.getMapper(ProSnMapper.class),
                        sessions.getMapper(ProFlowCardMapper.class),
                        steps,
                        sessions.getMapper(TestRecordMapper.class),
                        flow,
                        sns,
                        sessions.getMapper(NcrOrderMapper.class),
                        guard,
                        progressBoundary(),
                        serialReferences,
                        preparedPolicies);
        service = mesProxy(target);
        tenant = tenantScope("000000");
        db.update(
                "INSERT INTO"
                    + " pro_sn(sn_id,tenant_id,del_flag,sn_code,identity_id,material_id,card_id,mes_wo_id,sn_status,current_step_seq)"
                    + " VALUES(1,'000000','0','SN-1',10001,77,10,20,'IN_PRODUCTION',10)");
        db.update(
                "INSERT INTO pro_flow_card(card_id,tenant_id,del_flag,mes_wo_id,card_status)"
                        + " VALUES(10,'000000','0',20,'IN_PROGRESS')");
        db.update(
                "INSERT INTO"
                    + " pro_flow_card_step(step_record_id,tenant_id,del_flag,card_id,sequence_num,process_code,step_status,planned_qty,good_qty,defect_qty,scrap_qty,operator_id,mes_wo_id)"
                    + " VALUES(100,'000000','0',10,10,'ASM','IN_PROGRESS',100,0,0,0,105,20),(200,'000000','0',10,20,'PKG','PENDING',100,0,0,0,105,20)");
        db.update(
                "INSERT INTO"
                    + " mes_station_device(device_id,tenant_id,del_flag,station_code,process_code)"
                    + " VALUES(5,'000000','0',?,'ASM')",
                STATION);
    }

    @SuppressWarnings("unchecked")
    private <T> T mesProxy(T target) {
        var factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvisor(
                new DynamicDataSourceAnnotationAdvisor(
                        new DynamicDataSourceAnnotationInterceptor(
                                true, new DsSpelExpressionProcessor()),
                        DS.class));
        var tx = new TransactionInterceptor();
        tx.setTransactionManager(manager);
        tx.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        factory.addAdvice(tx);
        return (T) factory.getProxy();
    }

    private void registerCanonical(String code, Long identityId, Long localSnId) {
        canonicalCodes.put(
                code,
                CodeResolution.resolved(
                        new CodeCandidate(
                                CodeObjectType.SN,
                                identityId,
                                localSnId,
                                code,
                                77L,
                                "FG-77",
                                null,
                                null,
                                new TrackingPolicy(false, true, true, 1L, TrackingStage.STRICT))));
    }

    private void repairedLocalIdentityWithCanonicalTextCollision() {
        db.update("UPDATE pro_sn SET sn_code='LOCAL-OLD' WHERE sn_id=1");
        registerCanonical("ERP-FORMAL", 10001L, 1L);
        // 另一实物沿用相同本地文本，但并不拥有正式身份10001。
        db.update(
                "INSERT INTO"
                    + " pro_sn(sn_id,tenant_id,del_flag,sn_code,identity_id,material_id,card_id,mes_wo_id,sn_status,current_step_seq)"
                    + " VALUES(2,'000000','0','ERP-FORMAL',10002,77,10,20,'IN_PRODUCTION',10)");
    }

    private void testEvidence(long id, Long localSnId, String code, String result, String time) {
        db.update(
                "INSERT INTO"
                        + " mes_test_record(test_record_id,tenant_id,sn_id,sn_code,del_flag,result,test_time)"
                        + " VALUES(?,'000000',?,?,'0',?,?)",
                id,
                localSnId,
                code,
                result,
                Timestamp.valueOf(time));
    }

    /**
     * 原事务/幂等夹具继续验证报工事务，投影边界由独立服务测试覆盖。
     */
    private ProWorkOrderStepProgressService progressBoundary() {
        var progress = mock(ProWorkOrderStepProgressService.class);
        when(progress.lockForSn(anyString())).thenReturn(20L);
        return progress;
    }

    JdbcDataSource database(String name) {
        var ds = new JdbcDataSource();
        ds.setURL(
                "jdbc:h2:mem:"
                        + name
                        + UUID.randomUUID()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        return ds;
    }

    void createTable(Class<?> entity) {
        var info = TableInfoHelper.getTableInfo(entity);
        List<String> columns = new ArrayList<>();
        columns.add(info.getKeyColumn() + " BIGINT PRIMARY KEY");
        for (var f : info.getFieldList()) {
            Class<?> t = f.getPropertyType();
            String type =
                    t == Long.class
                            ? "BIGINT"
                            : t == Integer.class || t == Boolean.class
                                    ? "INTEGER"
                                    : t == BigDecimal.class
                                            ? "DECIMAL(18,4)"
                                            : Date.class.isAssignableFrom(t)
                                                    ? "TIMESTAMP"
                                                    : "VARCHAR(2048)";
            columns.add(f.getColumn() + " " + type);
        }
        db.execute("CREATE TABLE " + info.getTableName() + "(" + String.join(",", columns) + ")");
    }

    MockedStatic<TenantHelper> tenantScope(String value) {
        var scope = mockStatic(TenantHelper.class);
        scope.when(TenantHelper::getTenantId).thenReturn(value);
        scope.when(TenantHelper::isEnable).thenReturn(true);
        scope.when(
                        () ->
                                TenantHelper.dynamic(
                                        ArgumentMatchers.anyString(),
                                        ArgumentMatchers.any(Supplier.class)))
                .thenAnswer(i -> i.getArgument(1, Supplier.class).get());
        return scope;
    }

    @AfterEach
    void cleanup() {
        tenant.close();
        DynamicDataSourceContextHolder.clear();
        db.execute("DROP ALL OBJECTS");
        masterDb.execute("DROP ALL OBJECTS");
    }

    StationPassReportDto request(long ts) {
        var dto = new StationPassReportDto();
        dto.setSnCode("SN-1");
        dto.setStationCode(STATION);
        dto.setResult("PASS");
        dto.setClientTs(ts);
        return dto;
    }

    int count() {
        return db.queryForObject("SELECT COUNT(*) FROM mes_station_report", Integer.class);
    }

    BigDecimal good() {
        return db.queryForObject(
                "SELECT good_qty FROM pro_flow_card_step WHERE step_record_id=100",
                BigDecimal.class);
    }

    void unchanged() {
        assertEquals(0, count());
        assertEquals(0, good().signum());
        assertEquals(
                10,
                db.queryForObject(
                        "SELECT current_step_seq FROM pro_sn WHERE sn_id=1", Integer.class));
    }

    @Test
    void realProxyCommitsReportQuantityAndSnTogether() {
        assertEquals(
                "SERVER_COMMITTED", service.reportSingle(request(1000), OP).get("submissionState"));
        assertEquals(1, count());
        assertEquals(0, BigDecimal.ONE.compareTo(good()));
        assertEquals(
                20,
                db.queryForObject(
                        "SELECT current_step_seq FROM pro_sn WHERE sn_id=1", Integer.class));
    }

    @Test
    void traceEnabledStationReadsFrozenReleaseBeforeItsSourceWrites() {
        ReflectionTestUtils.setField(preparedPolicies, "traceRelayEnabled", true);
        assertEquals(
                "SERVER_COMMITTED", service.reportSingle(request(1000), OP).get("submissionState"));
        assertEquals(1, policyReads.get());
        assertEquals(1, count());
        assertEquals(0, BigDecimal.ONE.compareTo(good()));
    }

    @Test
    void unknownReleaseBlocksStationBeforeReportCounterAndSnWrites() {
        ReflectionTestUtils.setField(preparedPolicies, "traceRelayEnabled", true);
        unknownRelease.set(true);
        assertThrows(ServiceException.class, () -> service.reportSingle(request(1000), OP));
        assertEquals(1, policyReads.get());
        unchanged();
    }

    @Test
    void committedStationReplayKeepsOriginalEffectWhenReleaseLaterBecomesUnknown() {
        ReflectionTestUtils.setField(preparedPolicies, "traceRelayEnabled", true);
        service.reportSingle(request(1000), OP);
        unknownRelease.set(true);
        assertEquals(
                "SERVER_COMMITTED", service.reportSingle(request(1000), OP).get("submissionState"));
        assertEquals(1, policyReads.get());
        assertEquals(1, count());
        assertEquals(0, BigDecimal.ONE.compareTo(good()));
        assertEquals(
                20,
                db.queryForObject(
                        "SELECT current_step_seq FROM pro_sn WHERE sn_id=1", Integer.class));
    }

    @Test
    void failAfterSqlIncrementRollsBackReportCounterAndSn() {
        failAfterIncrement.set(true);
        assertThrows(ServiceException.class, () -> service.reportSingle(request(1000), OP));
        unchanged();
    }

    @Test
    void failReportAddsOnlyDefectAndNeverAdvancesSn() {
        var dto = request(1000);
        dto.setResult("FAIL");
        dto.setDefectCodes(List.of(" D2 ", "D1", "D2"));
        service.reportSingle(dto, OP);
        assertEquals(1, count());
        assertEquals(0, good().signum());
        assertEquals(
                "D1,D2",
                db.queryForObject("SELECT defect_codes FROM mes_station_report", String.class));
        assertEquals(
                10,
                db.queryForObject(
                        "SELECT current_step_seq FROM pro_sn WHERE sn_id=1", Integer.class));
    }

    @Test
    void committedReplaySucceedsEvenAfterCompletionButConflictDoesNot() {
        service.reportSingle(request(1000), OP);
        db.update("UPDATE pro_sn SET sn_status='COMPLETED'");
        service.reportSingle(request(1000), OP);
        var changed = request(1000);
        changed.setResult("FAIL");
        assertThrows(ServiceException.class, () -> service.reportSingle(changed, OP));
        assertEquals(1, count());
        assertEquals(0, BigDecimal.ONE.compareTo(good()));
    }

    @Test
    void independentlyCommittedMesTransactionDoesNotWriteOuterMaster() {
        new TransactionTemplate(manager)
                .execute(
                        status -> {
                            routedDb.update("INSERT INTO marker VALUES(1)");
                            service.reportSingle(request(1000), OP);
                            status.setRollbackOnly();
                            return null;
                        });
        assertEquals(1, count());
        assertEquals(0, masterDb.queryForObject("SELECT COUNT(*) FROM marker", Integer.class));
    }

    @Test
    void otherTenantCannotReadOrModifySameSnCode() {
        tenant.close();
        tenant = tenantScope("000001");
        assertNull(serialReferences.resolve("SN-1"));
        assertThrows(ServiceException.class, () -> service.reportSingle(request(1000), OP));
        unchanged();
    }

    @Test
    void canonicalIdentityMapsToActualLocalRowDespiteAnotherUnitsIdenticalLocalText() {
        repairedLocalIdentityWithCanonicalTextCollision();
        var validation = service.validate("ERP-FORMAL", STATION);
        var info = (Map<?, ?>) validation.get("snInfo");
        assertEquals(1L, info.get("localSnId"));
        assertEquals(10001L, info.get("identityId"));
        assertEquals("LOCAL-OLD", info.get("localSnCode"));
        assertEquals("ERP-FORMAL", info.get("canonicalSnCode"));
        var dto = request(1000);
        dto.setSnCode("ERP-FORMAL");
        service.reportSingle(dto, OP);
        service.reportSingle(dto, OP);
        assertEquals(1, count());
        assertEquals(
                "LOCAL-OLD",
                db.queryForObject("SELECT sn_code FROM mes_station_report", String.class));
        assertEquals(
                "SINGLE|ERP-FORMAL|" + STATION + "|1000",
                db.queryForObject("SELECT client_key FROM mes_station_report", String.class));
        assertEquals(
                20,
                db.queryForObject(
                        "SELECT current_step_seq FROM pro_sn WHERE sn_id=1", Integer.class));
        assertEquals(
                10,
                db.queryForObject(
                        "SELECT current_step_seq FROM pro_sn WHERE sn_id=2", Integer.class));
        assertEquals(
                "LOCAL-OLD",
                db.queryForObject("SELECT sn_code FROM pro_sn WHERE sn_id=1", String.class));
    }

    @Test
    void realIdentityMapperRejectsDuplicateMappingsAndCannotGuessByText() {
        db.update(
                "INSERT INTO pro_sn(sn_id,tenant_id,del_flag,sn_code,identity_id,material_id)"
                        + " VALUES(2,'000000','0','OTHER-TEXT',10001,77)");
        assertThrows(ServiceException.class, () -> service.reportSingle(request(1000), OP));
        unchanged();
    }

    @Test
    void canonicalLocalIdMustMatchTheRealIdentityRow() {
        registerCanonical("SN-1", 10001L, 2L);
        assertThrows(ServiceException.class, () -> service.reportSingle(request(1000), OP));
        unchanged();
    }

    @Test
    void sameTextWithoutVerifiedIdentityCannotAuthorizeWrite() {
        db.update("UPDATE pro_sn SET identity_id=NULL WHERE sn_id=1");
        assertThrows(ServiceException.class, () -> service.reportSingle(request(1000), OP));
        unchanged();
    }

    @Test
    void newerCodeOnlyPassForAnotherPhysicalUnitCannotHideMappedUnitsFail() {
        repairedLocalIdentityWithCanonicalTextCollision();
        testEvidence(1, 1L, "LOCAL-OLD", "FAIL", "2026-10-18 08:00:00");
        testEvidence(2, null, "ERP-FORMAL", "PASS", "2026-10-18 09:00:00");
        assertFalse((Boolean) service.validate("ERP-FORMAL", STATION).get("pass"));
        var dto = request(1000);
        dto.setSnCode("ERP-FORMAL");
        assertThrows(ServiceException.class, () -> service.reportSingle(dto, OP));
        unchanged();
    }

    @Test
    void lockedWriteCannotUseAnotherUnitsUnboundPassToOverrideIdentityFail() {
        repairedLocalIdentityWithCanonicalTextCollision();
        testEvidence(1, 1L, "LOCAL-OLD", "FAIL", "2026-10-18 08:00:00");
        testEvidence(2, null, "ERP-FORMAL", "PASS", "2026-10-18 09:00:00");
        var dto = request(1000);
        dto.setSnCode("ERP-FORMAL");
        var error = assertThrows(ServiceException.class, () -> service.reportSingle(dto, OP));
        assertTrue(error.getMessage().contains(StationPassService.CHECK_QUALITY_HOLD));
        unchanged();
    }

    @Test
    void lockedWriteKeepsUnboundOnlyPassAsAnEvidenceGap() {
        repairedLocalIdentityWithCanonicalTextCollision();
        testEvidence(1, null, "ERP-FORMAL", "PASS", "2026-10-18 09:00:00");
        var dto = request(1000);
        dto.setSnCode("ERP-FORMAL");
        var error = assertThrows(ServiceException.class, () -> service.reportSingle(dto, OP));
        assertTrue(error.getMessage().contains(StationPassService.CHECK_QUALITY_HOLD));
        unchanged();
    }

    @Test
    void qualityReadCannotUseAnotherTenantsNewerCodeOnlyPass() {
        testEvidence(1, 1L, "SN-1", "FAIL", "2026-10-18 08:00:00");
        db.update(
                "INSERT INTO"
                        + " mes_test_record(test_record_id,tenant_id,sn_code,del_flag,result,test_time)"
                        + " VALUES(2,'000001','SN-1','0','PASS',?)",
                Timestamp.valueOf("2026-10-18 09:00:00"));
        assertFalse((Boolean) service.validate("SN-1", STATION).get("pass"));
        assertThrows(ServiceException.class, () -> service.reportSingle(request(1000), OP));
        unchanged();
    }

    @Test
    void mismatchedCodesWithOnlyUnboundPassRemainAnEvidenceGap() {
        repairedLocalIdentityWithCanonicalTextCollision();
        testEvidence(1, null, "LOCAL-OLD", "PASS", "2026-10-18 08:00:00");
        testEvidence(2, null, "ERP-FORMAL", "PASS", "2026-10-18 09:00:00");
        var validation = service.validate("ERP-FORMAL", STATION);
        assertFalse((Boolean) validation.get("pass"));
        assertNotNull(validation.get("evidenceGaps"));
        var dto = request(1000);
        dto.setSnCode("ERP-FORMAL");
        assertThrows(ServiceException.class, () -> service.reportSingle(dto, OP));
        unchanged();
    }

    @Test
    void verifiedLocalIdPassIsNotOverriddenByAnotherUnitsCodeOnlyFail() {
        repairedLocalIdentityWithCanonicalTextCollision();
        testEvidence(1, 1L, "LOCAL-OLD", "PASS", "2026-10-18 08:00:00");
        testEvidence(2, null, "ERP-FORMAL", "FAIL", "2026-10-18 09:00:00");
        var validation = service.validate("ERP-FORMAL", STATION);
        assertTrue((Boolean) validation.get("pass"));
        assertNotNull(validation.get("evidenceGaps"));
        var dto = request(1000);
        dto.setSnCode("ERP-FORMAL");
        assertDoesNotThrow(() -> service.reportSingle(dto, OP));
        assertEquals(1, count());
    }

    private void frozenSubmission(
            JdbcTemplate target, long id, int revision, String manifestStatus, String eventStatus) {
        String payload =
                "{\"manifestId\":"
                        + id
                        + ",\"revision\":"
                        + revision
                        + ",\"manifestHash\":\""
                        + "a".repeat(64)
                        + "\",\"mesWoId\":20,\"completedQty\":1}";
        target.update(
                "INSERT INTO pro_completion_manifest VALUES(?,?,'000000',20,?,?,1)",
                id,
                revision,
                "a".repeat(64),
                manifestStatus);
        target.update(
                "INSERT INTO pro_completion_manifest_line VALUES('000000',?,?,1,10001,'GOOD')",
                id,
                revision);
        target.update(
                "INSERT INTO mes_trace_outbox"
                        + " VALUES(?,'000000','MES','COMPLETION_MANIFEST',?,1,'COMPLETION_MANIFEST_FROZEN',?,?,?,?)",
                "manifest-" + id,
                id,
                eventStatus,
                payload,
                TraceHashes.sha256(payload),
                "APPLIED".equals(eventStatus)
                        ? "{\"sourceEventId\":\"manifest-"
                                + id
                                + "\",\"receiptId\":900,\"postingGroupId\":901}"
                        : null);
        target.update(
                "UPDATE pro_sn SET sn_status='COMPLETED',handoff_status=? WHERE sn_id=1",
                "APPLIED".equals(manifestStatus)
                        ? "RECEIVED"
                        : "CANCELLED".equals(manifestStatus) ? "NONE" : "FROZEN");
    }

    @Test
    void reportResponseBeforeFreezeProvesOnlyServerCommit() {
        var response = service.reportSingle(request(1000), OP);
        assertEquals("SERVER_COMMITTED", response.get("submissionState"));
        assertEquals("NONE", response.get("handoffStatus"));
        assertFalse(response.containsKey("manifestRef"));
        assertEquals(1, count());
        assertEquals(0, BigDecimal.ONE.compareTo(good()));
    }

    @Test
    void finalFreezeIsVisibleInTheSameMesTransactionResponse() {
        db.update("DELETE FROM pro_flow_card_step WHERE step_record_id=200");
        db.update("UPDATE pro_flow_card_step SET planned_qty=1 WHERE step_record_id=100");
        doAnswer(
                        i -> {
                            frozenSubmission(routedDb, 700L, 1, "FROZEN", "NEW");
                            return null;
                        })
                .when(flow)
                .completeCard(10L);
        var response = service.reportSingle(request(1000), OP);
        assertEquals("CROSS_MODULE_PENDING", response.get("submissionState"));
        assertEquals("FROZEN", response.get("handoffStatus"));
        assertEquals(700L, ((Map<?, ?>) response.get("manifestRef")).get("manifestId"));
    }

    @Test
    void replayReturnsCurrentOriginalManifestDeliveryWithoutReportingAgain() {
        service.reportSingle(request(1000), OP);
        frozenSubmission(db, 700L, 1, "FROZEN", "NEW");
        var pending = service.reportSingle(request(1000), OP);
        assertEquals("CROSS_MODULE_PENDING", pending.get("submissionState"));
        assertEquals("FROZEN", pending.get("handoffStatus"));
        db.update(
                "UPDATE mes_trace_outbox SET status='APPLIED',result_payload=? WHERE"
                        + " aggregate_id=700",
                "{\"sourceEventId\":\"manifest-700\",\"receiptId\":900,\"postingGroupId\":901}");
        db.update("UPDATE pro_completion_manifest SET status='APPLIED' WHERE manifest_id=700");
        db.update("UPDATE pro_sn SET handoff_status='RECEIVED' WHERE sn_id=1");
        var confirmed = service.reportSingle(request(1000), OP);
        assertEquals("CONFIRMED", confirmed.get("submissionState"));
        assertEquals("RECEIVED", confirmed.get("handoffStatus"));
        assertEquals("APPLIED", ((Map<?, ?>) confirmed.get("manifestRef")).get("status"));
        db.update("UPDATE pro_sn SET handoff_status='SHIPPED',sn_status='SHIPPED' WHERE sn_id=1");
        var shipped = service.reportSingle(request(1000), OP);
        assertEquals("CONFIRMED", shipped.get("submissionState"));
        assertEquals("SHIPPED", shipped.get("handoffStatus"));
        assertEquals(1, count());
        assertEquals(0, BigDecimal.ONE.compareTo(good()));
    }

    @Test
    void manualReceivedFlagCannotTurnPendingOutboxIntoConfirmed() {
        service.reportSingle(request(1000), OP);
        frozenSubmission(db, 700L, 1, "FROZEN", "NEW");
        db.update("UPDATE pro_sn SET handoff_status='RECEIVED' WHERE sn_id=1");
        var replay = service.reportSingle(request(1000), OP);
        assertEquals("CROSS_MODULE_PENDING", replay.get("submissionState"));
        assertEquals("FROZEN", replay.get("handoffStatus"));
        assertEquals(1, count());
    }

    @Test
    void replayCannotJumpFromCancelledOriginalManifestToANewerRevision() {
        service.reportSingle(request(1000), OP);
        frozenSubmission(db, 700L, 1, "CANCELLED", "APPLIED");
        frozenSubmission(db, 701L, 2, "FROZEN", "NEW");
        var replay = service.reportSingle(request(1000), OP);
        assertEquals("SERVER_COMMITTED", replay.get("submissionState"));
        assertEquals("NONE", replay.get("handoffStatus"));
        var manifest = (Map<?, ?>) replay.get("manifestRef");
        assertEquals(700L, manifest.get("manifestId"));
        assertEquals(1, manifest.get("revision"));
        assertEquals("CANCELLED", manifest.get("status"));
        assertEquals(1, count());
        assertEquals(0, BigDecimal.ONE.compareTo(good()));
    }

    @Test
    void receiptMetadataRequiresDurableAppliedOwnerEventAndItsResult() {
        service.reportSingle(request(1000), OP);
        frozenSubmission(db, 700L, 1, "APPLIED", "NEW");
        assertThrows(ServiceException.class, () -> service.reportSingle(request(1000), OP));
        db.update(
                "UPDATE mes_trace_outbox SET status='APPLIED',result_payload=NULL WHERE"
                        + " aggregate_id=700");
        assertThrows(ServiceException.class, () -> service.reportSingle(request(1000), OP));
        assertEquals(1, count());
    }

    @Test
    void realQualitySourcesAndRegisteredBindingAreEnforced() {
        db.update(
                "INSERT INTO"
                    + " mes_test_record(test_record_id,tenant_id,sn_code,del_flag,result,test_time)"
                    + " VALUES(1,'000000','SN-1','0','FAIL',CURRENT_TIMESTAMP)");
        assertThrows(ServiceException.class, () -> service.reportSingle(request(1000), OP));
        unchanged();
        db.update("UPDATE mes_test_record SET result='PASS'");
        db.update(
                "INSERT INTO ncr_order(ncr_id,tenant_id,card_id,del_flag,status)"
                        + " VALUES(1,'000000',10,'0','OPEN')");
        assertThrows(ServiceException.class, () -> service.reportSingle(request(1000), OP));
        unchanged();
        db.update("UPDATE ncr_order SET status='CLOSED'");
        db.update("UPDATE mes_station_device SET process_code='WELD'");
        assertThrows(ServiceException.class, () -> service.reportSingle(request(1000), OP));
        unchanged();
        db.update("UPDATE mes_station_device SET process_code='ASM'");
        service.reportSingle(request(1000), OP);
        assertEquals(1, count());
    }

    @Test
    void sameSnDifferentStationsCannotCountSameStepTwice() {
        db.update(
                "INSERT INTO"
                    + " mes_station_device(device_id,tenant_id,del_flag,station_code,process_code)"
                    + " VALUES(6,'000000','0','ASM-B','ASM')");
        service.reportSingle(request(1000), OP);
        var dto = request(1001);
        dto.setStationCode("ASM-B");
        assertFalse(
                (Boolean) service.validate("SN-1", "ASM-B").get("pass"), "查询与写入必须一致拒绝同工序重复 PASS");
        assertThrows(ServiceException.class, () -> service.reportSingle(dto, OP));
        assertEquals(1, count());
    }

    @Test
    void stepFromDifferentWorkOrderIsRejectedWithoutAnyWrites() {
        db.update("UPDATE pro_flow_card_step SET mes_wo_id=99 WHERE step_record_id=100");
        assertThrows(ServiceException.class, () -> service.reportSingle(request(1000), OP));
        unchanged();
    }

    @Test
    void normalizedSixSegmentRequestAndCanonicalReplayHaveOneCommittedEffect() {
        var six = request(1000);
        six.setStationCode(STATION + "|IPC-1");
        service.reportSingle(six, OP);
        service.reportSingle(request(1000), OP);
        assertEquals(1, count());
        assertEquals(
                STATION,
                db.queryForObject("SELECT station_code FROM mes_station_report", String.class));
    }

    @Test
    void codeOnlyCommittedReplayDoesNotDependOnChangedWorkerAssociation() {
        var dto = request(1000);
        dto.setOperatorCode("EMP-1");
        service.reportSingle(
                dto, service.resolveOperator(dto.getOperatorId(), dto.getOperatorCode()));
        worker.set(Map.of("user_id", 106L, "user_name", "新关联"));
        assertDoesNotThrow(
                () ->
                        service.reportSingle(
                                dto,
                                service.resolveOperator(
                                        dto.getOperatorId(), dto.getOperatorCode())));
        assertEquals(1, count());
        assertEquals(0, BigDecimal.ONE.compareTo(good()));
        assertEquals(
                105L, db.queryForObject("SELECT operator_id FROM mes_station_report", Long.class));
    }

    @Test
    void codeOnlyCommittedReplayDoesNotDependOnDeletedWorkerAssociation() {
        var dto = request(1000);
        dto.setOperatorCode("EMP-1");
        service.reportSingle(
                dto, service.resolveOperator(dto.getOperatorId(), dto.getOperatorCode()));
        worker.set(null);
        assertDoesNotThrow(
                () ->
                        service.reportSingle(
                                dto,
                                service.resolveOperator(
                                        dto.getOperatorId(), dto.getOperatorCode())));
        assertEquals(1, count());
        assertEquals(0, BigDecimal.ONE.compareTo(good()));
    }

    @Test
    void changedRawOperatorInputCannotHideBehindSameEnrichedOperator() {
        var dto = request(1000);
        dto.setOperatorCode("EMP-1");
        service.reportSingle(
                dto, service.resolveOperator(dto.getOperatorId(), dto.getOperatorCode()));
        dto.setOperatorId(105L);
        assertThrows(
                ServiceException.class,
                () ->
                        service.reportSingle(
                                dto,
                                service.resolveOperator(
                                        dto.getOperatorId(), dto.getOperatorCode())));
        assertEquals(1, count());
        assertEquals(0, BigDecimal.ONE.compareTo(good()));
    }

    @Test
    void legacyNullFingerprintDoesNotGetGuessedFromCurrentAssociation() {
        var dto = request(1000);
        dto.setOperatorCode("EMP-1");
        service.reportSingle(dto, service.resolveOperator(null, "EMP-1"));
        db.update("UPDATE mes_station_report SET request_fingerprint=NULL");
        assertThrows(
                ServiceException.class,
                () -> service.reportSingle(dto, service.resolveOperator(null, "EMP-1")));
        assertEquals(1, count());
        assertEquals(0, BigDecimal.ONE.compareTo(good()));
    }

    @Test
    void installationKeepsFingerprintNullableAndReplayKeyTenantScoped() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("script/sql/ry-mes.sql"))) {
            root = root.getParent();
        }
        assertNotNull(root, "Installation schema must be present");
        var ddl =
                Pattern.compile(
                                "(?is)CREATE\\s+TABLE\\s+`mes_station_report`\\s*\\(.*?\\)\\s*ENGINE[^;]*;")
                        .matcher(Files.readString(root.resolve("script/sql/ry-mes.sql")));
        assertTrue(ddl.find(), "Missing installation DDL: mes_station_report");
        String table = ddl.group();
        assertReplayInstallationSchema(table);
        assertEquals(
                String.class,
                MesStationReport.class.getDeclaredField("requestFingerprint").getType());
        assertNull(new MesStationReport().getRequestFingerprint());
    }

    /**
     * 转储可省略显式 NULL 并使用 UNIQUE KEY，但不能替历史记录伪造请求指纹。
     */
    @Test
    void replaySchemaAcceptsEquivalentMysqlDumpSyntax() {
        for (String nullable : List.of("NULL DEFAULT NULL", "DEFAULT NULL")) {
            for (String unique : List.of("UNIQUE INDEX", "UNIQUE KEY")) {
                assertReplayInstallationSchema(
                        "  `request_fingerprint` varchar(64) COLLATE utf8mb4_unicode_ci "
                                + nullable
                                + ",\n"
                                + "  PRIMARY KEY (`report_id`) USING BTREE,\n"
                                + "  "
                                + unique
                                + " `uk_tenant_client_key` (`tenant_id`,`client_key`) USING BTREE");
            }
        }
    }

    /**
     * 保留非空、默认值、类型、租户列顺序及唯一性的变异负例。
     */
    @Test
    void replaySchemaRejectsVerifiedDefaultsAndUnscopedOrNonuniqueKeys() {
        String valid =
                "  `request_fingerprint` varchar(64) DEFAULT NULL,\n"
                        + "  PRIMARY KEY (`report_id`),\n"
                        + "  UNIQUE KEY `uk_tenant_client_key` (`tenant_id`,`client_key`)";
        for (String invalid :
                List.of(
                        valid.replace("DEFAULT NULL", "NOT NULL DEFAULT NULL"),
                        valid.replace("DEFAULT NULL", "DEFAULT 'trusted'"),
                        valid.replace("varchar(64)", "varchar(32)"),
                        valid.replace("UNIQUE KEY", "KEY"),
                        valid.replace("`tenant_id`,`client_key`", "`client_key`,`tenant_id`"),
                        valid.replace("`tenant_id`,`client_key`", "`client_key`"))) {
            assertThrows(AssertionError.class, () -> assertReplayInstallationSchema(invalid));
        }
    }

    /**
     * 只兼容语义等价的转储写法，不放宽旧指纹为 NULL 和永久租户重放键约束。
     */
    private static void assertReplayInstallationSchema(String table) {
        var column =
                Pattern.compile("(?im)^\\s*`request_fingerprint`\\s+([^\\r\\n]+)$").matcher(table);
        assertTrue(column.find(), "请求指纹列必须存在");
        String definition =
                column.group(1)
                        .replaceFirst("(?i)\\s+COMMENT\\s+.*$", "")
                        .replaceFirst(",\\s*$", "")
                        .replaceAll("(?i)\\s+(?:CHARACTER SET|COLLATE)\\s+\\w+", "")
                        .trim()
                        .replaceAll("\\s+", " ");
        assertTrue(
                definition.matches("(?i)varchar\\(64\\) (?:NULL )?DEFAULT NULL"),
                "旧指纹必须可空且无已验证默认值");
        assertTrue(table.contains("PRIMARY KEY (`report_id`)"));
        assertTrue(
                Pattern.compile(
                                "(?im)^\\s*UNIQUE\\s+(?:KEY|INDEX)\\s+`uk_tenant_client_key`\\s*"
                                    + "\\(`tenant_id`\\s*,\\s*`client_key`\\)(?:\\s+USING\\s+BTREE)?\\s*,?\\s*$")
                        .matcher(table)
                        .find());
    }

    @Test
    void deletedReplayCarrierIsNotReused() {
        service.reportSingle(request(1000), OP);
        db.update("UPDATE mes_station_report SET del_flag='1'");
        assertThrows(ServiceException.class, () -> service.reportSingle(request(1000), OP));
        assertEquals(1, count());
    }

    @Test
    void concurrentNewKeysCannotCountSameSnTwice() throws Exception {
        concurrent(false);
    }

    @Test
    void concurrentSameKeyIsSafeToRetryAfterDatabaseSerializationAbort() throws Exception {
        concurrent(true);
    }

    void concurrent(boolean sameKey) throws Exception {
        var start = new CountDownLatch(1);
        var ready = new CountDownLatch(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                long ts = sameKey ? 1000 : 1000 + i;
                futures.add(
                        pool.submit(
                                () -> {
                                    try (var scoped = tenantScope("000000")) {
                                        ready.countDown();
                                        assertTrue(start.await(5, TimeUnit.SECONDS));
                                        try {
                                            service.reportSingle(request(ts), OP);
                                            return true;
                                        } catch (ServiceException e) {
                                            if (sameKey) {
                                                throw e;
                                            }
                                            return false;
                                        } catch (TransientDataAccessException serializationAbort) {
                                            return false;
                                        } finally {
                                            DynamicDataSourceContextHolder.clear();
                                        }
                                    }
                                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            int successes = 0;
            for (var f : futures) {
                if (f.get(15, TimeUnit.SECONDS)) {
                    successes++;
                }
            }
            // H2 RR 可能对等待后已变化的行返回 40001；只接受真实回滚异常，不能忽略任意错误。
            assertTrue(successes >= 1);
            if (!sameKey) {
                assertEquals(1, successes);
            }
            if (sameKey) {
                assertEquals(
                        "SERVER_COMMITTED",
                        service.reportSingle(request(1000), OP).get("submissionState"));
            }
            assertEquals(1, count());
            assertEquals(0, BigDecimal.ONE.compareTo(good()));
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void qualityLocksReadFullRangesRatherThanOnlyLatestRow() throws Exception {
        for (String method : List.of("lockTests", "lockNcrs")) {
            var m =
                    Arrays.stream(StationPassGuardMapper.class.getMethods())
                            .filter(x -> method.equals(x.getName()))
                            .findFirst()
                            .orElseThrow();
            String sql =
                    String.join(" ", m.getAnnotation(Select.class).value())
                            .toUpperCase(Locale.ROOT);
            assertTrue(sql.contains("FOR UPDATE"));
            assertTrue(sql.contains("TENANT_ID="));
            assertFalse(sql.contains("LIMIT"));
        }
    }
}
