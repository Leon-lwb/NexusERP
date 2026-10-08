package com.nexus.station.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.aop.DynamicDataSourceAnnotationAdvisor;
import com.baomidou.dynamic.datasource.aop.DynamicDataSourceAnnotationInterceptor;
import com.baomidou.dynamic.datasource.processor.DsSpelExpressionProcessor;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.flash.domain.TestRecord;
import com.nexus.mes.flash.mapper.TestRecordMapper;
import com.nexus.mes.ncr.domain.NcrOrder;
import com.nexus.mes.ncr.mapper.NcrOrderMapper;
import com.nexus.mes.pro.domain.*;
import com.nexus.mes.pro.mapper.*;
import com.nexus.mes.pro.service.IProFlowCardService;
import com.nexus.mes.pro.service.ProWorkOrderStepProgressService;
import com.nexus.mes.pro.service.IProSnService;
import com.nexus.station.domain.MesStationDevice;
import com.nexus.station.domain.MesStationReport;
import com.nexus.station.domain.dto.StationPassReportDto;
import com.nexus.station.mapper.*;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.LocalCacheScope;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 真实 Spring 事务/动态数据源代理和生产 Mapper SQL 的私有 H2 回归。
 * 不连接业务库；H2 行锁证明不替代 MySQL5.7 的范围锁和硬件运行态验收。
 */
class StationPassTransactionTest {
    static final String STATION="STN|JZ|L1|ASM|GENERAL";
    static final StationPassService.OperatorInfo OP=new StationPassService.OperatorInfo(105L,"EMP-1","操作员");
    JdbcDataSource mes, master;
    JdbcTemplate db, masterDb, routedDb;
    DataSourceTransactionManager manager;
    StationPassService service;
    StationPassGuardMapper guard;
    MockedStatic<TenantHelper> tenant;
    AtomicBoolean failAfterIncrement;
    StationSchWorkerMapper workers;
    java.util.concurrent.atomic.AtomicReference<Map<String,Object>> worker;

    @BeforeEach void setup() throws Exception {
        mes=database("mes"); master=database("master"); db=new JdbcTemplate(mes); masterDb=new JdbcTemplate(master);
        var routing=new AbstractRoutingDataSource() {
            @Override protected Object determineCurrentLookupKey() {
                return DynamicDataSourceContextHolder.peek()==null?"master":DynamicDataSourceContextHolder.peek();
            }
        };
        routing.setTargetDataSources(Map.of("mes",mes,"master",master)); routing.setDefaultTargetDataSource(master);
        routing.setLenientFallback(false); routing.afterPropertiesSet(); routedDb=new JdbcTemplate(routing); manager=new DataSourceTransactionManager(routing);
        var config=new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true); config.setCacheEnabled(false);
        config.setLocalCacheScope(LocalCacheScope.STATEMENT);
        config.setEnvironment(new Environment("private-test",new SpringManagedTransactionFactory(),routing));
        for(Class<?> mapper:List.of(ProSnMapper.class,ProFlowCardMapper.class,ProFlowCardStepMapper.class,
            StationDeviceMapper.class,StationReportMapper.class,TestRecordMapper.class,NcrOrderMapper.class,StationPassGuardMapper.class)) config.addMapper(mapper);
        var sessions=new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(config));
        for(Class<?> entity:List.of(ProSn.class,ProFlowCard.class,ProFlowCardStep.class,MesStationDevice.class,
            MesStationReport.class,TestRecord.class,NcrOrder.class)) createTable(entity);
        db.execute("ALTER TABLE mes_station_report ADD del_flag VARCHAR(1) DEFAULT '0'");
        db.execute("CREATE UNIQUE INDEX uk_client ON mes_station_report(tenant_id,client_key)");
        masterDb.execute("CREATE TABLE marker(id INT PRIMARY KEY)");
        var steps=spy(sessions.getMapper(ProFlowCardStepMapper.class)); failAfterIncrement=new AtomicBoolean();
        doAnswer(i->{
            Object value=i.callRealMethod();
            if(failAfterIncrement.get()) throw new ServiceException("模拟累计之后故障");
            return value;
        }).when(steps).update(any(),any());
        var flow=mock(IProFlowCardService.class);
        doAnswer(i->{new JdbcTemplate(routing).update("UPDATE pro_flow_card_step SET step_status='IN_PROGRESS' WHERE step_record_id=?",new Object[]{i.getArgument(1)});return null;})
            .when(flow).startStep(any(),any(),any());
        var sns=mock(IProSnService.class);
        doAnswer(i->{new JdbcTemplate(routing).update("UPDATE pro_sn SET sn_status='COMPLETED' WHERE sn_id=?",new Object[]{i.getArgument(0)});return null;})
            .when(sns).completeSn(any());
        guard=sessions.getMapper(StationPassGuardMapper.class);
        workers=mock(StationSchWorkerMapper.class); worker=new java.util.concurrent.atomic.AtomicReference<>(Map.of("user_id",105L,"user_name","操作员"));
        when(workers.selectByEmployeeId("EMP-1")).thenAnswer(i->worker.get());
        var target=new StationPassService(sessions.getMapper(StationReportMapper.class),workers,
            sessions.getMapper(StationDeviceMapper.class),sessions.getMapper(ProSnMapper.class),sessions.getMapper(ProFlowCardMapper.class),
            steps,sessions.getMapper(TestRecordMapper.class),flow,sns,sessions.getMapper(NcrOrderMapper.class),guard, progressBoundary());
        var factory=new ProxyFactory(target); factory.setProxyTargetClass(true);
        factory.addAdvisor(new DynamicDataSourceAnnotationAdvisor(new DynamicDataSourceAnnotationInterceptor(true,new DsSpelExpressionProcessor()),DS.class));
        var tx=new TransactionInterceptor(); tx.setTransactionManager(manager); tx.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        factory.addAdvice(tx); service=(StationPassService)factory.getProxy();
        tenant=tenantScope("000000");
        db.update("INSERT INTO pro_sn(sn_id,tenant_id,del_flag,sn_code,card_id,mes_wo_id,sn_status,current_step_seq) VALUES(1,'000000','0','SN-1',10,20,'IN_PRODUCTION',10)");
        db.update("INSERT INTO pro_flow_card(card_id,tenant_id,del_flag,mes_wo_id,card_status) VALUES(10,'000000','0',20,'IN_PROGRESS')");
        db.update("INSERT INTO pro_flow_card_step(step_record_id,tenant_id,del_flag,card_id,sequence_num,process_code,step_status,planned_qty,good_qty,defect_qty,scrap_qty,operator_id,mes_wo_id) VALUES(100,'000000','0',10,10,'ASM','IN_PROGRESS',100,0,0,0,105,20),(200,'000000','0',10,20,'PKG','PENDING',100,0,0,0,105,20)");
        db.update("INSERT INTO mes_station_device(device_id,tenant_id,del_flag,station_code,process_code) VALUES(5,'000000','0',?,'ASM')",STATION);
    }
    /** 原事务/幂等夹具继续验证报工事务，投影边界由独立服务测试覆盖。 */
    private ProWorkOrderStepProgressService progressBoundary() {
        var progress=mock(ProWorkOrderStepProgressService.class);
        when(progress.lockForSn(anyString())).thenReturn(20L);
        return progress;
    }

    JdbcDataSource database(String name) {
        var ds=new JdbcDataSource(); ds.setURL("jdbc:h2:mem:"+name+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000"); return ds;
    }
    void createTable(Class<?> entity) {
        var info=TableInfoHelper.getTableInfo(entity); List<String> columns=new ArrayList<>();
        columns.add(info.getKeyColumn()+" BIGINT PRIMARY KEY");
        for(var f:info.getFieldList()) {
            Class<?> t=f.getPropertyType();
            String type=t==Long.class?"BIGINT":t==Integer.class||t==Boolean.class?"INTEGER":t==BigDecimal.class?"DECIMAL(18,4)":
                java.util.Date.class.isAssignableFrom(t)?"TIMESTAMP":"VARCHAR(2048)";
            columns.add(f.getColumn()+" "+type);
        }
        db.execute("CREATE TABLE "+info.getTableName()+"("+String.join(",",columns)+")");
    }
    MockedStatic<TenantHelper> tenantScope(String value) {
        var scope=mockStatic(TenantHelper.class); scope.when(TenantHelper::getTenantId).thenReturn(value);
        scope.when(TenantHelper::isEnable).thenReturn(true);
        scope.when(()->TenantHelper.dynamic(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(java.util.function.Supplier.class)))
            .thenAnswer(i->i.getArgument(1,java.util.function.Supplier.class).get()); return scope;
    }
    @AfterEach void cleanup() {
        tenant.close(); DynamicDataSourceContextHolder.clear();
        db.execute("DROP ALL OBJECTS"); masterDb.execute("DROP ALL OBJECTS");
    }
    StationPassReportDto request(long ts) {
        var dto=new StationPassReportDto(); dto.setSnCode("SN-1"); dto.setStationCode(STATION); dto.setResult("PASS"); dto.setClientTs(ts); return dto;
    }
    int count() { return db.queryForObject("SELECT COUNT(*) FROM mes_station_report",Integer.class); }
    BigDecimal good() { return db.queryForObject("SELECT good_qty FROM pro_flow_card_step WHERE step_record_id=100",BigDecimal.class); }
    void unchanged() { assertEquals(0,count()); assertEquals(0,good().signum()); assertEquals(10,db.queryForObject("SELECT current_step_seq FROM pro_sn WHERE sn_id=1",Integer.class)); }

    @Test void realProxyCommitsReportQuantityAndSnTogether() {
        assertTrue(service.reportSingle(request(1000),OP).isEmpty()); assertEquals(1,count()); assertEquals(0,BigDecimal.ONE.compareTo(good()));
        assertEquals(20,db.queryForObject("SELECT current_step_seq FROM pro_sn WHERE sn_id=1",Integer.class));
    }
    @Test void failAfterSqlIncrementRollsBackReportCounterAndSn() {
        failAfterIncrement.set(true); assertThrows(ServiceException.class,()->service.reportSingle(request(1000),OP)); unchanged();
    }
    @Test void failReportAddsOnlyDefectAndNeverAdvancesSn() {
        var dto=request(1000); dto.setResult("FAIL"); dto.setDefectCodes(List.of(" D2 ","D1","D2"));
        service.reportSingle(dto,OP); assertEquals(1,count()); assertEquals(0,good().signum());
        assertEquals("D1,D2",db.queryForObject("SELECT defect_codes FROM mes_station_report",String.class));
        assertEquals(10,db.queryForObject("SELECT current_step_seq FROM pro_sn WHERE sn_id=1",Integer.class));
    }
    @Test void committedReplaySucceedsEvenAfterCompletionButConflictDoesNot() {
        service.reportSingle(request(1000),OP); db.update("UPDATE pro_sn SET sn_status='COMPLETED'");
        service.reportSingle(request(1000),OP); var changed=request(1000); changed.setResult("FAIL");
        assertThrows(ServiceException.class,()->service.reportSingle(changed,OP)); assertEquals(1,count()); assertEquals(0,BigDecimal.ONE.compareTo(good()));
    }
    @Test void independentlyCommittedMesTransactionDoesNotWriteOuterMaster() {
        new TransactionTemplate(manager).execute(status->{
            routedDb.update("INSERT INTO marker VALUES(1)"); service.reportSingle(request(1000),OP); status.setRollbackOnly(); return null;
        });
        assertEquals(1,count()); assertEquals(0,masterDb.queryForObject("SELECT COUNT(*) FROM marker",Integer.class));
    }
    @Test void otherTenantCannotReadOrModifySameSnCode() {
        tenant.close(); tenant=tenantScope("OTHER"); assertThrows(ServiceException.class,()->service.reportSingle(request(1000),OP)); unchanged();
    }
    @Test void realQualitySourcesAndRegisteredBindingAreEnforced() {
        db.update("INSERT INTO mes_test_record(test_record_id,tenant_id,sn_code,del_flag,result,test_time) VALUES(1,'000000','SN-1','0','FAIL',CURRENT_TIMESTAMP)");
        assertThrows(ServiceException.class,()->service.reportSingle(request(1000),OP)); unchanged();
        db.update("UPDATE mes_test_record SET result='PASS'");
        db.update("INSERT INTO ncr_order(ncr_id,tenant_id,card_id,del_flag,status) VALUES(1,'000000',10,'0','OPEN')");
        assertThrows(ServiceException.class,()->service.reportSingle(request(1000),OP)); unchanged();
        db.update("UPDATE ncr_order SET status='CLOSED'");
        db.update("UPDATE mes_station_device SET process_code='WELD'");
        assertThrows(ServiceException.class,()->service.reportSingle(request(1000),OP)); unchanged();
        db.update("UPDATE mes_station_device SET process_code='ASM'"); service.reportSingle(request(1000),OP); assertEquals(1,count());
    }
    @Test void sameSnDifferentStationsCannotCountSameStepTwice() {
        db.update("INSERT INTO mes_station_device(device_id,tenant_id,del_flag,station_code,process_code) VALUES(6,'000000','0','ASM-B','ASM')");
        service.reportSingle(request(1000),OP); var dto=request(1001); dto.setStationCode("ASM-B");
        assertFalse((Boolean) service.validate("SN-1","ASM-B").get("pass"), "查询与写入必须一致拒绝同工序重复 PASS");
        assertThrows(ServiceException.class,()->service.reportSingle(dto,OP)); assertEquals(1,count());
    }
    @Test void stepFromDifferentWorkOrderIsRejectedWithoutAnyWrites() {
        db.update("UPDATE pro_flow_card_step SET mes_wo_id=99 WHERE step_record_id=100");
        assertThrows(ServiceException.class,()->service.reportSingle(request(1000),OP)); unchanged();
    }
    @Test void normalizedSixSegmentRequestAndCanonicalReplayHaveOneCommittedEffect() {
        var six=request(1000); six.setStationCode(STATION+"|IPC-1");
        service.reportSingle(six,OP); service.reportSingle(request(1000),OP); assertEquals(1,count());
        assertEquals(STATION,db.queryForObject("SELECT station_code FROM mes_station_report",String.class));
    }
    @Test void codeOnlyCommittedReplayDoesNotDependOnChangedWorkerAssociation() {
        var dto=request(1000); dto.setOperatorCode("EMP-1");
        service.reportSingle(dto,service.resolveOperator(dto.getOperatorId(),dto.getOperatorCode()));
        worker.set(Map.of("user_id",106L,"user_name","新关联"));
        assertDoesNotThrow(()->service.reportSingle(dto,service.resolveOperator(dto.getOperatorId(),dto.getOperatorCode())));
        assertEquals(1,count()); assertEquals(0,BigDecimal.ONE.compareTo(good()));
        assertEquals(105L,db.queryForObject("SELECT operator_id FROM mes_station_report",Long.class));
    }
    @Test void codeOnlyCommittedReplayDoesNotDependOnDeletedWorkerAssociation() {
        var dto=request(1000); dto.setOperatorCode("EMP-1");
        service.reportSingle(dto,service.resolveOperator(dto.getOperatorId(),dto.getOperatorCode())); worker.set(null);
        assertDoesNotThrow(()->service.reportSingle(dto,service.resolveOperator(dto.getOperatorId(),dto.getOperatorCode())));
        assertEquals(1,count()); assertEquals(0,BigDecimal.ONE.compareTo(good()));
    }
    @Test void changedRawOperatorInputCannotHideBehindSameEnrichedOperator() {
        var dto=request(1000); dto.setOperatorCode("EMP-1");
        service.reportSingle(dto,service.resolveOperator(dto.getOperatorId(),dto.getOperatorCode())); dto.setOperatorId(105L);
        assertThrows(ServiceException.class,()->service.reportSingle(dto,service.resolveOperator(dto.getOperatorId(),dto.getOperatorCode())));
        assertEquals(1,count()); assertEquals(0,BigDecimal.ONE.compareTo(good()));
    }
    @Test void legacyNullFingerprintDoesNotGetGuessedFromCurrentAssociation() {
        var dto=request(1000); dto.setOperatorCode("EMP-1");
        service.reportSingle(dto,service.resolveOperator(null,"EMP-1")); db.update("UPDATE mes_station_report SET request_fingerprint=NULL");
        assertThrows(ServiceException.class,()->service.reportSingle(dto,service.resolveOperator(null,"EMP-1")));
        assertEquals(1,count()); assertEquals(0,BigDecimal.ONE.compareTo(good()));
    }
    @Test void installationKeepsFingerprintNullableAndReplayKeyTenantScoped() throws Exception {
        Path root=Path.of("").toAbsolutePath();
        while(root!=null&&!Files.isRegularFile(root.resolve("script/sql/ry-mes.sql")))root=root.getParent();
        assertNotNull(root,"Installation schema must be present");
        var ddl=Pattern.compile("(?is)CREATE\\s+TABLE\\s+`mes_station_report`\\s*\\(.*?\\)\\s*ENGINE[^;]*;")
            .matcher(Files.readString(root.resolve("script/sql/ry-mes.sql")));
        assertTrue(ddl.find(),"Missing installation DDL: mes_station_report");
        String table=ddl.group();
        assertTrue(Pattern.compile("(?im)^\\s*`request_fingerprint`\\s+varchar\\(64\\)"
            +"(?:\\s+CHARACTER SET\\s+\\w+)?(?:\\s+COLLATE\\s+\\w+)?\\s+NULL\\s+DEFAULT\\s+NULL").matcher(table).find());
        assertEquals(String.class,MesStationReport.class.getDeclaredField("requestFingerprint").getType());
        assertNull(new MesStationReport().getRequestFingerprint());
        assertTrue(table.contains("PRIMARY KEY (`report_id`)"));
        assertTrue(table.contains("UNIQUE INDEX `uk_tenant_client_key`(`tenant_id`, `client_key`)"));
    }
    @Test void deletedReplayCarrierIsNotReused() {
        service.reportSingle(request(1000),OP); db.update("UPDATE mes_station_report SET del_flag='1'");
        assertThrows(ServiceException.class,()->service.reportSingle(request(1000),OP)); assertEquals(1,count());
    }
    @Test void concurrentNewKeysCannotCountSameSnTwice() throws Exception { concurrent(false); }
    @Test void concurrentSameKeyIsSafeToRetryAfterDatabaseSerializationAbort() throws Exception { concurrent(true); }
    void concurrent(boolean sameKey) throws Exception {
        var start=new CountDownLatch(1); var ready=new CountDownLatch(2); var pool=Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> futures=new ArrayList<>();
            for(int i=0;i<2;i++) { long ts=sameKey?1000:1000+i; futures.add(pool.submit(()->{
                try(var scoped=tenantScope("000000")) {
                    ready.countDown(); assertTrue(start.await(5,TimeUnit.SECONDS));
                    try { service.reportSingle(request(ts),OP); return true; }
                    catch(ServiceException e) { if(sameKey)throw e; return false; }
                    catch(org.springframework.dao.TransientDataAccessException serializationAbort) { return false; }
                    finally { DynamicDataSourceContextHolder.clear(); }
                }
            })); }
            assertTrue(ready.await(5,TimeUnit.SECONDS)); start.countDown(); int successes=0;
            for(var f:futures) if(f.get(15,TimeUnit.SECONDS))successes++;
            // H2 RR 可能对等待后已变化的行返回 40001；只接受真实回滚异常，不能忽略任意错误。
            assertTrue(successes >= 1); if(!sameKey) assertEquals(1,successes);
            if(sameKey) assertTrue(service.reportSingle(request(1000),OP).isEmpty());
            assertEquals(1,count()); assertEquals(0,BigDecimal.ONE.compareTo(good()));
        } finally { start.countDown(); pool.shutdownNow(); assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS)); }
    }
    @Test void qualityLocksReadFullRangesRatherThanOnlyLatestRow() throws Exception {
        for(String method:List.of("lockTests","lockNcrs")) {
            var m=Arrays.stream(StationPassGuardMapper.class.getMethods()).filter(x->method.equals(x.getName())).findFirst().orElseThrow();
            String sql=String.join(" ",m.getAnnotation(Select.class).value()).toUpperCase(Locale.ROOT);
            assertTrue(sql.contains("FOR UPDATE")); assertTrue(sql.contains("TENANT_ID=")); assertFalse(sql.contains("LIMIT"));
        }
    }
}
