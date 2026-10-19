package com.nexus.erp.wm.trace;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.aop.DynamicDataSourceAnnotationAdvisor;
import com.baomidou.dynamic.datasource.aop.DynamicDataSourceAnnotationInterceptor;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.common.trace.*;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/** 仅由专用容器runner启用的真实MySQL 5.7/Spring代理验证；不读取应用数据源配置。 */
@EnabledIfEnvironmentVariable(named="NEXUS_REGISTRY_MYSQL57_TOKEN",matches="[0-9a-f]{32}")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ErpSerialIdentityMysql57IT {
    static final String TENANT="990001";
    static String erpSchema,masterSchema,baseUrl;
    static AnnotationConfigApplicationContext first,second;
    static ErpSerialIdentityService a,b;
    static RecordingJdbcTemplate recordingA,recordingB;
    static JdbcTemplate admin;
    static final ObjectMapper JSON=new ObjectMapper();
    static final List<Map<String,Object>> CASES=new ArrayList<>();
    static final Map<String,Object> RUNTIME=new LinkedHashMap<>();
    static final AtomicLong EVENTS=new AtomicLong();

    /** 独立JUnit入口不加载应用bootstrap扩展；零测试或任何失败都返回非零。 */
    public static void main(String[] args)throws Exception {
        var request=LauncherDiscoveryRequestBuilder.request().selectors(selectClass(ErpSerialIdentityMysql57IT.class))
            .configurationParameter("junit.jupiter.extensions.autodetection.enabled","false").build();
        var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(request,listener);
        var summary=listener.getSummary();summary.printTo(new java.io.PrintWriter(System.out,true));
        summary.printFailuresTo(new java.io.PrintWriter(System.out,true));
        summary.getFailures().forEach(f->f.getException().printStackTrace(System.out));
        var evidence=new LinkedHashMap<String,Object>();evidence.put("testsFound",summary.getTestsFoundCount());
        evidence.put("testsSucceeded",summary.getTestsSucceededCount());evidence.put("testsFailed",summary.getTestsFailedCount());
        evidence.put("testsSkipped",summary.getTestsSkippedCount());evidence.put("cases",CASES);evidence.put("runtime",RUNTIME);
        String out=System.getenv("NEXUS_REGISTRY_MYSQL57_OUTPUT");
        if(out!=null)Files.writeString(Path.of(out,"java-evidence.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
        if(summary.getTestsStartedCount()==0 || summary.getTotalFailureCount()!=0)System.exit(1);
    }

    @BeforeAll static void startApplications() {
        String token=System.getenv("NEXUS_REGISTRY_MYSQL57_TOKEN"),port=System.getenv("NEXUS_REGISTRY_MYSQL57_PORT");
        need(token!=null && token.matches("[0-9a-f]{32}") && port!=null && port.matches("[0-9]{4,5}"),"Opt-in owned transport is required");
        erpSchema=System.getenv("NEXUS_REGISTRY_MYSQL57_ERP");masterSchema=System.getenv("NEXUS_REGISTRY_MYSQL57_MASTER");
        need(("mt57_registry_"+token.substring(0,16)+"_erp").equals(erpSchema)
            && ("mt57_registry_"+token.substring(0,16)+"_master").equals(masterSchema),"Only run-token-owned schemas are accepted");
        need(System.getenv("NEXUS_REGISTRY_MYSQL57_PASSWORD")!=null,"Synthetic probe credential is required");
        baseUrl="jdbc:mysql://127.0.0.1:"+port+"/";
        first=application();second=application();
        a=first.getBean(ErpSerialIdentityService.class);b=second.getBean(ErpSerialIdentityService.class);
        recordingA=first.getBean(RecordingJdbcTemplate.class);recordingB=second.getBean(RecordingJdbcTemplate.class);
        admin=new JdbcTemplate(first.getBean(DynamicRoutingDataSource.class).getDataSources().get("erp"));
        String version=admin.queryForObject("SELECT VERSION()",String.class);assertTrue(version.startsWith("5.7."));
        RUNTIME.put("mysqlVersion",version);RUNTIME.put("javaVersion",System.getProperty("java.version"));
        RUNTIME.put("springVersion",org.springframework.core.SpringVersion.getVersion());
        try(var connection=first.getBean(DynamicRoutingDataSource.class).getDataSources().get("erp").getConnection()) {
            RUNTIME.put("jdbcDriver",connection.getMetaData().getDriverVersion());
        }catch(SQLException failure){throw new IllegalStateException(failure);}
    }
    static AnnotationConfigApplicationContext application() {
        var app=new AnnotationConfigApplicationContext();
        app.getEnvironment().setActiveProfiles("registry-mysql57-opt-in");
        app.getEnvironment().getPropertySources().addFirst(new MapPropertySource("probe",Map.of("tenant.enable","true")));
        app.register(ProbeConfiguration.class);app.refresh();return app;
    }
    @AfterAll static void stopApplications(){if(second!=null)second.close();if(first!=null)first.close();}
    @BeforeEach void seedSyntheticData() {
        admin.execute("DROP TRIGGER IF EXISTS probe_atomic_failure");
        for(String table:List.of("wm_sn","wm_serial_reservation","wm_serial_sequence","bd_serial_number_rule","wm_batch","bd_material"))admin.execute("DELETE FROM "+table);
        admin.update("INSERT INTO bd_material(material_id,tenant_id,material_code,material_name,category_id,material_type,status,del_flag,is_serial_manage) VALUES(101,?,'SYNTH-M1','Synthetic M1',1,3,1,'0',1),(102,?,'SYNTH-M2','Synthetic M2',1,3,1,'0',1)",TENANT,TENANT);
        rule(1001,"JAVA",4);rule(1002,"JAVA",4);
        recordingA.clear();recordingB.clear();
    }
    @AfterEach void evidence(TestInfo info) {
        var entry=new LinkedHashMap<String,Object>();entry.put("test",info.getDisplayName());
        entry.put("instanceA",recordingA.evidence());entry.put("instanceB",recordingB.evidence());CASES.add(entry);
        recordingA.hook=null;recordingB.hook=null;
        admin.execute("DROP TRIGGER IF EXISTS probe_atomic_failure");
        assertNull(DynamicDataSourceContextHolder.peek());assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }
    static void rule(long id,String prefix,int width) {
        admin.update("INSERT INTO bd_serial_number_rule(rule_id,tenant_id,rule_code,rule_name,rule_type,prefix,date_format,seq_length,seq_cycle,check_digit_rule,status,del_flag,rule_version) VALUES(?,?,?,?,'PRODUCT_SN',?,'',?,'NONE','NONE','ACTIVE','0',1)",id,TENANT,"SYNTH-R"+id,"Synthetic rule "+id,prefix,width);
    }
    static <T>T tenant(Supplier<T> action){return TenantHelper.dynamic(TENANT,action);}
    static SerialReservation reserve(ErpSerialIdentityService service,String command,long material,int qty,long rule) {
        return tenant(()->service.reserve(TENANT,new SerialReservationCommand(command,material,qty,rule,"MES_FLOW_CARD",8001L)));
    }
    static List<String> codes(SerialReservation result){return result.identities().stream().map(SerialIdentity::snCode).toList();}
    static long count(String table){return admin.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}

    @Test @Order(0) void fixtureConfigurationDoesNotRegisterAnyDataSourceWithoutItsOptInProfile() {
        try(var inactive=new AnnotationConfigApplicationContext()) {
            inactive.register(ProbeConfiguration.class);inactive.refresh();
            assertTrue(inactive.getBeansOfType(DynamicRoutingDataSource.class).isEmpty());
            assertTrue(inactive.getBeansOfType(ErpSerialIdentityService.class).isEmpty());
        }
    }

    @Test @Order(1) void actualSpringAdvisorsRouteToThePhysicalErpPool() {
        assertTrue(AopUtils.isCglibProxy(a));assertTrue(AopUtils.isCglibProxy(b));assertNotSame(a,b);
        var advice=Arrays.stream(((Advised)a).getAdvisors()).map(v->v.getAdvice().getClass().getSimpleName()).toList();
        assertTrue(advice.contains("DynamicDataSourceAnnotationInterceptor"));assertTrue(advice.contains("TransactionInterceptor"));RUNTIME.put("actualRegistryAdvice",advice);
        assertNull(DynamicDataSourceContextHolder.peek());
        var result=reserve(a,"route",101,3,1001);
        assertEquals(List.of("JAVA0001","JAVA0002","JAVA0003"),codes(result));
        assertEquals(3L,admin.queryForObject("SELECT COUNT(*) FROM wm_sn WHERE status='REGISTERED' AND stock_id IS NULL",Long.class));
        assertEquals(erpSchema,recordingA.transactions.get(0).catalog);
        assertEquals("erp",recordingA.transactions.get(0).route);assertFalse(recordingA.transactions.get(0).autoCommit);
        assertEquals(0L,admin.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=? AND table_name='wm_sn'",Long.class,masterSchema));
    }

    @Test @Order(2) void sameCommandReplaysAcrossInstancesAndChangedPayloadIsRejected() {
        var original=reserve(a,"same-command",101,2,1001);
        assertEquals(original,reserve(b,"same-command",101,2,1001));
        assertThrows(ServiceException.class,()->reserve(b,"same-command",101,3,1001));
        assertEquals(1,count("wm_serial_reservation"));assertEquals(2,count("wm_sn"));
        assertEquals(2L,admin.queryForObject("SELECT current_value FROM wm_serial_sequence",Long.class));
    }

    @Test @Order(3) void actualMidBatchSqlFailureRollsBackTheWholeRangeAndReservation() {
        admin.execute("CREATE TRIGGER probe_atomic_failure BEFORE INSERT ON wm_sn FOR EACH ROW BEGIN IF NEW.registration_command='atomic-fail' AND RIGHT(NEW.sn_code,4)='0003' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic mid-batch rejection'; END IF; END");
        assertThrows(DataAccessException.class,()->reserve(a,"atomic-fail",101,3,1001));
        assertEquals(0,count("wm_sn"));assertEquals(0,count("wm_serial_reservation"));assertEquals(0,count("wm_serial_sequence"));
        assertEquals(1,recordingA.transactions.size());assertEquals(TransactionSynchronization.STATUS_ROLLED_BACK,recordingA.transactions.get(0).completion);
        admin.execute("DROP TRIGGER probe_atomic_failure");
        assertEquals(List.of("JAVA0001","JAVA0002","JAVA0003"),codes(reserve(a,"atomic-fail",101,3,1001)));
    }

    @Test @Order(4) void concurrentFirstInitializationAcrossInstancesAndRulesReservesDisjointRanges()throws Exception {
        var seen=new HashSet<String>();
        for(int round=0;round<3;round++) {
            admin.update("UPDATE bd_serial_number_rule SET prefix=?,rule_version=rule_version+1","ROUND"+round);
            int number=round;
            var results=together(()->reserve(a,"round-"+number+"-a",101,2,1001),()->reserve(b,"round-"+number+"-b",102,2,1002));
            for(var result:results){assertEquals(2,result.identities().size());for(var code:codes(result))assertTrue(seen.add(code));}
        }
        assertEquals(12,seen.size());assertEquals(12,count("wm_sn"));assertEquals(6,count("wm_serial_reservation"));
        assertEquals(3,count("wm_serial_sequence"));
    }

    @Test @Order(5) void differentOutputNamespacesSkipRealExistingCodesInsteadOfSticking()throws Exception {
        admin.update("UPDATE bd_serial_number_rule SET prefix='JAVA0',seq_length=3 WHERE rule_id=1002");
        assertEquals(List.of("JAVA0001"),codes(reserve(a,"namespace-a",101,1,1001)));
        assertEquals(List.of("JAVA0002"),codes(reserve(b,"namespace-b",102,1,1002)));
        var results=together(()->reserve(a,"namespace-a-next",101,2,1001),()->reserve(b,"namespace-b-next",102,2,1002));
        var all=new HashSet<>(List.of("JAVA0001","JAVA0002"));
        for(var result:results){assertEquals(2,result.identities().size());for(String code:codes(result))assertTrue(all.add(code));}
        assertEquals(results.get(0),reserve(b,"namespace-a-next",101,2,1001));
        assertEquals(results.get(1),reserve(a,"namespace-b-next",102,2,1002));
        assertEquals(6,all.size());assertEquals(6,count("wm_sn"));assertEquals(2,count("wm_serial_sequence"));
    }

    @Test @Order(6) void deletedHistoricalCodesAreNeverReissuedAndCollisionBurnsTheWholeWindow() {
        reserve(a,"history-first",101,1,1001);
        admin.update("INSERT INTO wm_sn(sn_id,tenant_id,sn_code,material_id,status,del_flag) VALUES(77,?,'JAVA0003',102,'OUT','2')",TENANT);
        assertEquals(List.of("JAVA0005","JAVA0006","JAVA0007"),codes(reserve(b,"after-history",101,3,1001)));
        assertEquals(1L,admin.queryForObject("SELECT COUNT(*) FROM wm_sn WHERE sn_id=77 AND material_id=102 AND del_flag='2'",Long.class));
        assertEquals(0L,admin.queryForObject("SELECT COUNT(*) FROM wm_sn WHERE sn_code IN ('JAVA0002','JAVA0004')",Long.class));
        assertEquals(7L,admin.queryForObject("SELECT current_value FROM wm_serial_sequence",Long.class));
    }

    @Test @Order(7) void editedPrefixSharesTheExistingOutputSequenceWithoutChangingAnOriginalReplay() {
        var original=reserve(a,"prefix-original",101,1,1001);
        admin.update("UPDATE bd_serial_number_rule SET prefix='OTHER' WHERE rule_id=1002");
        reserve(b,"prefix-other",102,2,1002);
        admin.update("UPDATE bd_serial_number_rule SET prefix='OTHER',rule_version=rule_version+1 WHERE rule_id=1001");
        assertEquals(List.of("OTHER0003","OTHER0004"),codes(reserve(a,"prefix-edited",101,2,1001)));
        assertEquals(original,reserve(b,"prefix-original",101,1,1001));
    }

    @Test @Order(8) void realInnoDb1213IsRolledBackBeforeANewSpringTransactionRetries()throws Exception {
        var gateLocked=new CountDownLatch(1);var registryGate=new CountDownLatch(1);var blockerWaiting=new CountDownLatch(1);
        var workers=Executors.newSingleThreadExecutor();var once=new AtomicBoolean();
        var blocker=workers.submit(()->{
            try(Connection connection=second.getBean(DynamicRoutingDataSource.class).getDataSources().get("erp").getConnection()) {
                connection.setAutoCommit(false);
                try(var statement=connection.createStatement()) {
                    statement.executeUpdate("UPDATE probe_deadlock_gate SET v=v+1 WHERE id>=3");
                    statement.executeQuery("SELECT id FROM probe_deadlock_gate WHERE id=1 FOR UPDATE").close();gateLocked.countDown();
                    await(registryGate);blockerWaiting.countDown();
                    statement.executeQuery("SELECT id FROM probe_deadlock_gate WHERE id=2 FOR UPDATE").close();connection.commit();
                }
            }catch(SQLException error){throw new IllegalStateException(error);}
        });
        recordingA.hook=(jdbc,sql,args)->{
            if(sql.contains("FROM wm_serial_sequence") && sql.endsWith("FOR UPDATE") && once.compareAndSet(false,true)) {
                await(gateLocked);jdbc.rawQuery("SELECT id FROM probe_deadlock_gate WHERE id=2 FOR UPDATE");registryGate.countDown();
                await(blockerWaiting);jdbc.rawQuery("SELECT id FROM probe_deadlock_gate WHERE id=1 FOR UPDATE");
            }
        };
        try {
            assertEquals(List.of("JAVA0001","JAVA0002"),codes(reserve(a,"real-deadlock-original",101,2,1001)));
            blocker.get(15,TimeUnit.SECONDS);
            assertTrue(recordingA.sqlErrors.contains(1213),"Real MySQL 1213 must be observed, not a synthetic Java exception");
            assertEquals(2,recordingA.transactions.size());var failed=recordingA.transactions.get(0);var retried=recordingA.transactions.get(1);
            assertEquals(TransactionSynchronization.STATUS_ROLLED_BACK,failed.completion);
            assertEquals(TransactionSynchronization.STATUS_COMMITTED,retried.completion);
            assertTrue(failed.completedEvent<retried.startedEvent);assertNotEquals(failed.id,retried.id);
            assertEquals(1,count("wm_serial_reservation"));assertEquals(1,count("wm_serial_sequence"));assertEquals(2,count("wm_sn"));
        }finally{recordingA.hook=null;registryGate.countDown();blockerWaiting.countDown();workers.shutdownNow();assertTrue(workers.awaitTermination(10,TimeUnit.SECONDS));}
    }

    @Test @Order(9) void physicalGuardRejectsAnErpMarkerOnAnActualMasterConnection() {
        var failure=assertThrows(ServiceException.class,()->tenant(()->{first.getBean(PhysicalProbe.class).wrongPhysicalConnection();return null;}));
        assertEquals("实际事务连接不属于ERP写库",failure.getMessage());
        assertEquals(0,count("wm_sn"));
    }
    @Test @Order(10) void physicalGuardRejectsReadOnlyAutocommitAndMissingTransactions() {
        var probe=first.getBean(PhysicalProbe.class);
        assertThrows(ServiceException.class,()->tenant(()->{probe.readOnly();return null;}));
        assertThrows(ServiceException.class,()->tenant(()->{probe.autocommit();return null;}));
        assertThrows(ServiceException.class,()->tenant(()->{probe.noTransaction();return null;}));
        assertEquals(0,count("wm_sn"));
    }
    @Test @Order(11) void actualSpringNeverPropagationRejectsAnOuterBusinessTransaction() {
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,()->tenant(()->{first.getBean(PhysicalProbe.class).outerReservation();return null;}));
        assertEquals(0,count("wm_serial_reservation"));assertEquals(0,count("wm_sn"));
    }
    @Test @Order(12) void actualTenantContextRejectsCrossTenantRegistrationBeforeWriting() {
        assertThrows(ServiceException.class,()->tenant(()->a.reserve("990002",new SerialReservationCommand("wrong-tenant",101L,1,1001L,"MES_FLOW_CARD",8001L))));
        assertEquals(0,count("wm_serial_reservation"));
    }

    @Test @Order(13) void invalidTenantSyntaxIsRejectedByTheActualContextBeforeAnyRegistryWrite() {
        var invalid=Arrays.asList(null,""," ","99001","990001A","tenant","990001\u0000","99 001","9".repeat(21));
        for(String value:invalid) {
            assertThrows(ServiceException.class,()->tenant(()->a.reserve(value,
                new SerialReservationCommand("invalid-tenant",101L,1,1001L,"MES_FLOW_CARD",8001L))));
            if(value!=null && !value.isBlank())assertThrows(ServiceException.class,
                ()->TenantHelper.dynamic(value,()->first.getBean(ErpTraceContext.class).tenant()));
        }
        assertEquals(0,count("wm_serial_reservation"));assertEquals(0,count("wm_sn"));
        assertTrue(recordingA.transactions.isEmpty());
    }

    static <T>List<T> together(Callable<T> one,Callable<T> two)throws Exception {
        var workers=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try {
            var a=workers.submit(()->{ready.countDown();await(start);return one.call();});
            var b=workers.submit(()->{ready.countDown();await(start);return two.call();});
            assertTrue(ready.await(5,TimeUnit.SECONDS));start.countDown();return List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
        }finally{start.countDown();workers.shutdownNow();assertTrue(workers.awaitTermination(10,TimeUnit.SECONDS));}
    }
    static void await(CountDownLatch latch){try{need(latch.await(15,TimeUnit.SECONDS),"Probe synchronization timed out");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}}
    static void need(boolean valid,String message){if(!valid)throw new IllegalStateException(message);}

    /** 两个独立Spring上下文分别拥有路由器、连接池与事务管理器，均不扫描应用配置。 */
    @Configuration(proxyBeanMethods=false)
    @Profile("registry-mysql57-opt-in")
    @EnableTransactionManagement(proxyTargetClass=true)
    @EnableAspectJAutoProxy(proxyTargetClass=true)
    static class ProbeConfiguration {
        @Bean SpringUtils springUtils(){return new SpringUtils();}
        @Bean DynamicRoutingDataSource dataSource() {
            var routing=new DynamicRoutingDataSource(List.of());routing.setPrimary("master");routing.setStrict(true);
            routing.addDataSource("erp",pool(erpSchema));routing.addDataSource("master",pool(masterSchema));return routing;
        }
        HikariDataSource pool(String schema) {
            var pool=new HikariDataSource();pool.setJdbcUrl(baseUrl+schema+"?useSSL=false&allowPublicKeyRetrieval=false&serverTimezone=Asia/Shanghai&connectTimeout=8000&socketTimeout=30000");
            pool.setUsername("registry_probe");pool.setPassword(System.getenv("NEXUS_REGISTRY_MYSQL57_PASSWORD"));
            pool.setMinimumIdle(0);pool.setMaximumPoolSize(6);pool.setConnectionTimeout(10000);pool.setPoolName("registry-probe-"+UUID.randomUUID());
            pool.setConnectionInitSql("SET SESSION innodb_lock_wait_timeout=6");return pool;
        }
        @Bean PlatformTransactionManager transactionManager(DynamicRoutingDataSource source){return new DataSourceTransactionManager(source);}
        @Bean RecordingJdbcTemplate jdbcTemplate(DynamicRoutingDataSource source){return new RecordingJdbcTemplate(source);}
        @Bean SqlSessionFactory sqlSessionFactory(DynamicRoutingDataSource source) {
            var config=new org.apache.ibatis.session.Configuration();config.setEnvironment(new Environment("probe",new JdbcTransactionFactory(),source));
            return new SqlSessionFactoryBuilder().build(config);
        }
        @Bean ErpTraceContext traceContext(SqlSessionFactory factory){return new ErpTraceContext(factory);}
        @Bean ErpSerialIdentityService registry(RecordingJdbcTemplate jdbc,ErpTraceContext context,PlatformTransactionManager manager){return new ErpSerialIdentityService(jdbc,JSON,context,manager);}
        @Bean PhysicalProbe physicalProbe(ErpTraceContext context,ErpSerialIdentityService registry,DynamicRoutingDataSource source){return new PhysicalProbe(context,registry,source);}
        @Bean Advisor dynamicDataSourceAdvisor() {
            var advisor=new DynamicDataSourceAnnotationAdvisor(new DynamicDataSourceAnnotationInterceptor(true,null),DS.class);advisor.setOrder(-1000);return advisor;
        }
    }

    /** 反例也通过真实Spring代理建立事务，之后由生产物理连接守卫拒绝。 */
    @DS("erp")
    static class PhysicalProbe {
        final ErpTraceContext context;final ErpSerialIdentityService registry;final DynamicRoutingDataSource source;
        PhysicalProbe(ErpTraceContext context,ErpSerialIdentityService registry,DynamicRoutingDataSource source){this.context=context;this.registry=registry;this.source=source;}
        @DS("master") @Transactional public void wrongPhysicalConnection(){DynamicDataSourceContextHolder.push("erp");try{context.requireWrite(TENANT);}finally{DynamicDataSourceContextHolder.poll();}}
        @Transactional(readOnly=true) public void readOnly(){context.requireWrite(TENANT);}
        @Transactional public void autocommit(){
            var connection=((ConnectionHolder)TransactionSynchronizationManager.getResource(source)).getConnection();
            try{connection.setAutoCommit(true);try{context.requireWrite(TENANT);}finally{connection.setAutoCommit(false);}}
            catch(SQLException error){throw new IllegalStateException(error);}
        }
        public void noTransaction(){context.requireWrite(TENANT);}
        @Transactional public void outerReservation(){registry.reserve(TENANT,new SerialReservationCommand("outer",101L,1,1001L,"MES_FLOW_CARD",8001L));}
    }
    interface QueryHook {void before(RecordingJdbcTemplate jdbc,String sql,Object[] args);}

    /** 仅记录真实事务/服务器连接，并在测试要求时安排真实InnoDB锁竞争，不伪造SQL异常。 */
    static class RecordingJdbcTemplate extends JdbcTemplate {
        final DynamicRoutingDataSource source;final List<TxSample> transactions=new CopyOnWriteArrayList<>();
        final List<Integer> sqlErrors=new CopyOnWriteArrayList<>();final Map<ConnectionHolder,TxSample> holders=Collections.synchronizedMap(new IdentityHashMap<>());
        volatile QueryHook hook;
        RecordingJdbcTemplate(DynamicRoutingDataSource source){super(source);this.source=source;}
        void clear(){transactions.clear();sqlErrors.clear();holders.clear();hook=null;}
        List<Map<String,Object>> rawQuery(String sql){return super.queryForList(sql);}
        @Override public List<Map<String,Object>> queryForList(String sql,Object...args)throws DataAccessException {
            capture();
            try{if(hook!=null)hook.before(this,sql,args);return super.queryForList(sql,args);}
            catch(RuntimeException failure){recordError(failure);throw failure;}
        }
        @Override public int update(String sql,Object...args)throws DataAccessException {
            capture();try{return super.update(sql,args);}catch(RuntimeException failure){recordError(failure);throw failure;}
        }
        void recordError(RuntimeException failure) {
            for(Throwable cause=failure;cause!=null;cause=cause.getCause())if(cause instanceof SQLException error){sqlErrors.add(error.getErrorCode());break;}
        }
        void capture() {
            Object value=TransactionSynchronizationManager.getResource(source);
            if(!(value instanceof ConnectionHolder holder) || holders.containsKey(holder))return;
            try {
                var connection=holder.getConnection();long server;
                try(var statement=connection.createStatement();var rows=statement.executeQuery("SELECT CONNECTION_ID()")){rows.next();server=rows.getLong(1);}
                var sample=new TxSample(EVENTS.incrementAndGet(),server,connection.getCatalog(),DynamicDataSourceContextHolder.peek(),connection.getAutoCommit());
                holders.put(holder,sample);transactions.add(sample);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){sample.completion=status;sample.completedEvent=EVENTS.incrementAndGet();}});
            }catch(SQLException error){throw new IllegalStateException(error);}
        }
        Map<String,Object> evidence(){return Map.of("transactions",transactions.stream().map(TxSample::evidence).toList(),"nativeSqlErrorCodes",List.copyOf(sqlErrors));}
    }
    static class TxSample {
        final long id,serverConnection,startedEvent;final String catalog,route;final boolean autoCommit;
        volatile int completion=-1;volatile long completedEvent;
        TxSample(long id,long server,String catalog,String route,boolean autoCommit){this.id=id;this.startedEvent=id;this.serverConnection=server;this.catalog=catalog;this.route=route;this.autoCommit=autoCommit;}
        Map<String,Object> evidence(){return Map.of("transactionId",id,"serverConnectionId",serverConnection,"catalog",catalog,"route",route,"autoCommit",autoCommit,"completion",completion,"startedEvent",startedEvent,"completedEvent",completedEvent);}
    }
}
