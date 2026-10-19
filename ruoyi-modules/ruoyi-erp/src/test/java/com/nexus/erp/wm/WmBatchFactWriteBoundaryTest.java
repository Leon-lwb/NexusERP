package com.nexus.erp.wm;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.*;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.wm.controller.WmBatchController;
import com.nexus.erp.wm.domain.*;
import com.nexus.erp.wm.domain.bo.WmBatchBo;
import com.nexus.erp.wm.mapper.*;
import com.nexus.erp.wm.service.WmBatchWriteGuard;
import com.nexus.erp.wm.service.impl.DailyCodeSequenceProvider;
import com.nexus.erp.wm.service.impl.WmBatchServiceImpl;
import com.nexus.erp.wm.trace.ErpTraceContext;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.mapping.Environment;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.MockedStatic;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real isolated H2, MyBatis batch SQL, Spring ERP transaction proxy and original controller URLs. */
@Isolated("Restores MyBatis static metadata/identifier state; TenantHelper mocks are always closed")
class WmBatchFactWriteBoundaryTest {
    @ParameterizedTest @CsvSource({"receivedQty,1", "receivedQty,-1", "issuedQty,1", "issuedQty,-1"})
    void bothRegistrationMethodsRejectInventedCumulativeFacts(String field, String value) throws Exception {
        try(var f=new Fixture()) {
            var bo=f.registration();new BeanWrapperImpl(bo).setPropertyValue(field,new BigDecimal(value));
            assertThrows(ServiceException.class,()->f.service.insertByBo(bo));
            assertThrows(ServiceException.class,()->f.service.createBatch(bo));
            assertEquals(1,f.count());
        }
    }

    @ParameterizedTest @MethodSource("immutableFields")
    void registeredIdentitySourceAndCountersAreImmutableThroughBo(String field,Object forged) throws Exception {
        try(var f=new Fixture()) {
            Map<String,Object> before=f.row();var bo=f.edit();new BeanWrapperImpl(bo).setPropertyValue(field,forged);
            assertThrows(ServiceException.class,()->f.service.updateByBo(bo),field);
            assertEquals(before,f.row());
        }
    }
    static Stream<Arguments> immutableFields() {
        return Stream.of(Arguments.of("batchCode","rewritten"),Arguments.of("materialId",101L),
            Arguments.of("materialCode","other"),Arguments.of("materialName","other"),Arguments.of("materialSpec","other"),
            Arguments.of("vendorBatchCode","other"),Arguments.of("vendorId",8L),Arguments.of("vendorCode","other"),Arguments.of("vendorName","other"),
            Arguments.of("warehouseId",31L),Arguments.of("warehouseCode","other"),Arguments.of("warehouseName","other"),
            Arguments.of("productionDate",new Date(0)),Arguments.of("sourceDocType","MISC_RECEIVE"),
            Arguments.of("sourceDocId",999L),Arguments.of("sourceDocNo","other"),Arguments.of("ruleId",19L),
            Arguments.of("quantity",new BigDecimal("200")),Arguments.of("receivedQty",new BigDecimal("49")),
            Arguments.of("issuedQty",new BigDecimal("9")),Arguments.of("attr1","other"),Arguments.of("attr2","other"),
            Arguments.of("attr3",3L),Arguments.of("attr4",4L),Arguments.of("delFlag","1"));
    }

    @Test void onlyMetadataChangesWhileOriginalFactsRemainReadable() throws Exception {
        try(var f=new Fixture()) {
            var bo=f.edit();bo.setStatus("LOCKED");bo.setExpiryDate(new Date(2000000000000L));bo.setRemark("reviewed");
            bo.setQuantity(new BigDecimal("100.0000"));bo.setReceivedQty(new BigDecimal("50.00"));bo.setIssuedQty(new BigDecimal("10.0"));
            assertEquals(1,f.service.updateByBo(bo));assertEquals("LOCKED",f.row().get("STATUS"));
            assertEquals("reviewed",f.row().get("REMARK"));f.assertFacts("50","10");
        }
    }

    @Test void staleCumulativeEchoCannotOverwriteNewSourceEffects() throws Exception {
        try(var f=new Fixture()) {
            var stale=f.edit();stale.setReceivedQty(new BigDecimal("50"));stale.setRemark("old screen");
            assertEquals(1,f.mapper.increaseProductionReceivedQty(1L,100L,Fixture.TENANT,7L,new BigDecimal("5")));
            assertThrows(ServiceException.class,()->f.service.updateByBo(stale));f.assertFacts("55","10");
        }
    }

    @Test void registrationStartsAtZeroAndCannotTakeAnExistingIdentityOrHideIt() throws Exception {
        try(var f=new Fixture()) {
            var bo=f.registration();bo.setBatchId(1L);
            assertThrows(ServiceException.class,()->f.service.createBatch(bo));
            bo.setBatchId(null);bo.setDelFlag("1");assertThrows(ServiceException.class,()->f.service.insertByBo(bo));
            bo.setDelFlag(null);bo.setQuantity(new BigDecimal("-1"));assertThrows(ServiceException.class,()->f.service.insertByBo(bo));
            bo.setQuantity(new BigDecimal("12"));bo.setBatchCode("B-NEW");assertEquals(1,f.service.insertByBo(bo));
            var inserted=f.jdbc.queryForMap("SELECT * FROM wm_batch WHERE batch_code='B-NEW'");
            assertEquals(Fixture.TENANT,inserted.get("TENANT_ID"));assertEquals("0",inserted.get("DEL_FLAG"));
            assertEquals(0,((BigDecimal)inserted.get("RECEIVED_QTY")).signum());assertEquals(0,((BigDecimal)inserted.get("ISSUED_QTY")).signum());
            assertNull(inserted.get("QC_STATUS"));
        }
    }

    @ParameterizedTest @CsvSource({"1,true", "-1,true", "1,false", "0,false"})
    void sourceFreeQuantityEntryIsAlwaysClosed(String qty,boolean receive) throws Exception {
        try(var f=new Fixture()) {
            assertThrows(ServiceException.class,()->f.service.updateQuantity(1L,new BigDecimal(qty),receive));f.assertFacts("50","10");
        }
    }

    @Test void registeredIdentityCannotBeDeletedEvenWithoutCurrentStock() throws Exception {
        try(var f=new Fixture()) {
            assertThrows(ServiceException.class,()->f.service.deleteByIds(new Long[]{1L}));
            assertEquals(1,f.count());assertEquals("0",f.row().get("DEL_FLAG"));f.assertFacts("50","10");
        }
    }

    @ParameterizedTest @ValueSource(strings={"QUALIFIED","IN_STOCK","DELETED","", "closed"})
    void statusEntryAcceptsOnlyMetadataStatusVocabulary(String invalid) throws Exception {
        try(var f=new Fixture()) {
            assertThrows(ServiceException.class,()->f.service.changeStatus(1L,invalid));f.assertFacts("50","10");
        }
    }

    @Test void inactiveThroughTheActualPcStatusUrlPreservesFactsAndSurvivesExpiryCheck() throws Exception {
        try(var f=new Fixture()) {
            f.mvc.perform(put("/erp/wm/batch/changeStatus").param("batchId","1").param("status","INACTIVE")).andExpect(status().isOk());
            assertEquals("INACTIVE",f.row().get("STATUS"));assertEquals("0",f.row().get("DEL_FLAG"));f.assertFacts("50","10");
            f.jdbc.update("UPDATE wm_batch SET expiry_date=? WHERE batch_id=1",new Date(0));
            f.service.checkExpiry();assertEquals("INACTIVE",f.row().get("STATUS"));assertEquals(1,f.count());f.assertFacts("50","10");
        }
    }

    @Test void inactiveIsAcceptedByBoMetadataAndBothGuardedRegistrationEntries() throws Exception {
        try(var f=new Fixture()) {
            var edit=f.edit();edit.setStatus("INACTIVE");assertEquals(1,f.service.updateByBo(edit));
            assertEquals("INACTIVE",f.row().get("STATUS"));f.assertFacts("50","10");
            var bo=f.registration();bo.setStatus("INACTIVE");assertEquals(1,f.service.insertByBo(bo));
            var auto=f.registration();auto.setStatus("INACTIVE");var created=f.service.createBatch(auto);
            assertEquals("INACTIVE",created.getStatus());assertEquals(3,f.count());
            assertEquals(2,f.jdbc.queryForObject("SELECT COUNT(*) FROM wm_batch WHERE batch_id<>1 AND status='INACTIVE' AND received_qty=0 AND issued_qty=0 AND qc_status IS NULL AND del_flag='0'",Integer.class));
        }
    }

    @Test void statusAndExpiryJobsDoNotWriteCumulativeOrQcSnapshots() throws Exception {
        try(var f=new Fixture()) {
            f.service.changeStatus(1L,"LOCKED");assertEquals("LOCKED",f.row().get("STATUS"));f.assertFacts("50","10");
            f.service.changeStatus(1L,"ACTIVE");f.jdbc.update("UPDATE wm_batch SET expiry_date=? WHERE batch_id=1",new Date(0));
            f.service.checkExpiry();assertEquals("EXPIRED",f.row().get("STATUS"));f.assertFacts("50","10");
            f.mvc.perform(post("/erp/wm/batch/checkExpiry")).andExpect(status().isOk());f.assertFacts("50","10");
        }
    }

    @Test void existingSourceAtomicAccumulatorsRemainTenantBoundAndNeverTouchMetadata() throws Exception {
        try(var f=new Fixture()) {
            assertEquals(0,f.mapper.increaseIssuedQty(1L,100L,"000002",BigDecimal.ONE));
            assertEquals(0,f.mapper.increaseIssuedQty(1L,101L,Fixture.TENANT,BigDecimal.ONE));
            assertEquals(0,f.mapper.increaseIssuedQty(1L,100L,Fixture.TENANT,new BigDecimal("41")));
            assertEquals(1,f.mapper.increaseIssuedQty(1L,100L,Fixture.TENANT,BigDecimal.ONE));
            assertEquals(0,f.mapper.increaseProductionReceivedQty(1L,100L,Fixture.TENANT,8L,BigDecimal.ONE));
            assertEquals(1,f.mapper.increaseProductionReceivedQty(1L,100L,Fixture.TENANT,7L,new BigDecimal("2")));
            f.service.changeStatus(1L,"CLOSED");f.assertFacts("52","11");assertEquals("CLOSED",f.row().get("STATUS"));
        }
    }

    @Test void oldQuantityAndCreateUrlsEnforceTheRealServiceBoundary() throws Exception {
        try(var f=new Fixture()) {
            f.mvc.perform(put("/erp/wm/batch/updateQuantity").param("batchId","1").param("deltaQty","5").param("isReceive","true")).andExpect(status().isBadRequest());
            f.mvc.perform(post("/erp/wm/batch/createBatch").contentType("application/json").content("{\"materialId\":100,\"receivedQty\":5}")).andExpect(status().isBadRequest());
            f.mvc.perform(post("/erp/wm/batch").contentType("application/json").content("{\"batchCode\":\"B-FAKE\",\"materialId\":100,\"issuedQty\":1}")).andExpect(status().isBadRequest());
            f.mvc.perform(delete("/erp/wm/batch/1")).andExpect(status().isBadRequest());
            assertEquals(1,f.count());f.assertFacts("50","10");
        }
    }

    @Test void oldEditAndStatusUrlsOnlyAffectMetadataAndCannotSetQcFacts() throws Exception {
        try(var f=new Fixture()) {
            f.mvc.perform(put("/erp/wm/batch").contentType("application/json").content("{\"batchId\":1,\"batchCode\":\"B1\",\"materialId\":100,\"receivedQty\":999}")).andExpect(status().isBadRequest());
            f.mvc.perform(put("/erp/wm/batch").contentType("application/json").content("{\"batchId\":1,\"batchCode\":\"B1\",\"materialId\":100,\"status\":\"LOCKED\",\"qcStatus\":\"UNQUALIFIED\",\"remark\":\"metadata\"}")).andExpect(status().isOk());
            f.mvc.perform(put("/erp/wm/batch/changeStatus").param("batchId","1").param("status","CLOSED")).andExpect(status().isOk());
            assertEquals("CLOSED",f.row().get("STATUS"));f.assertFacts("50","10");
        }
    }

    @Test void metadataReadsAndWritesCannotCrossTenantsAndExpiryOnlyTouchesTheCurrentTenant() throws Exception {
        try(var f=new Fixture()) {
            f.tenants.when(TenantHelper::getTenantId).thenReturn("000002");
            assertThrows(ServiceException.class,()->f.service.updateByBo(f.edit()));
            assertThrows(ServiceException.class,()->f.service.changeStatus(1L,"CLOSED"));
            f.jdbc.update("UPDATE wm_batch SET expiry_date=? WHERE batch_id=1",new Date(0));
            f.service.checkExpiry();assertEquals("ACTIVE",f.row().get("STATUS"));f.assertFacts("50","10");
        }
    }

    @Test void rawOrWrongRouteAndReadOnlyTransactionsCannotRegisterOrEditBatchFacts() throws Exception {
        try(var f=new Fixture()) {
            assertThrows(ServiceException.class,()->f.target.updateByBo(f.edit()));
            assertThrows(ServiceException.class,()->f.target.createBatch(f.registration()));
            DynamicDataSourceContextHolder.push("mes");
            try {assertThrows(ServiceException.class,()->f.service.changeStatus(1L,"CLOSED"));}
            finally {DynamicDataSourceContextHolder.poll();}
            var readOnly=new TransactionTemplate(f.tx.getTransactionManager());readOnly.setReadOnly(true);
            assertThrows(ServiceException.class,()->readOnly.execute(status->{f.service.changeStatus(1L,"CLOSED");return null;}));
            assertEquals(1,f.count());assertEquals("ACTIVE",f.row().get("STATUS"));f.assertFacts("50","10");
        }
    }

    @Test void outerSourceFailureRollsBackTheMetadataAndLegitimateAtomicCounterTogether() throws Exception {
        try(var f=new Fixture()) {
            Map<String,Object> original=f.row();
            assertThrows(ServiceException.class,()->f.tx.execute(status->{
                f.service.changeStatus(1L,"LOCKED");
                assertEquals(1,f.mapper.increaseProductionReceivedQty(1L,100L,Fixture.TENANT,7L,BigDecimal.ONE));
                throw new ServiceException("source proof failed");
            }));
            assertEquals(original,f.row());
        }
    }

    @Test void metadataWaitsForTheSourceRowLockAndPreservesItsCommittedCounterAndQcFacts() throws Exception {
        try(var f=new Fixture()) {
            var executor=Executors.newFixedThreadPool(2);
            var sourceLocked=new CountDownLatch(1);var allowCommit=new CountDownLatch(1);var metadataStarted=new CountDownLatch(1);
            try {
                Future<?> source=executor.submit(()->underTenant(()->f.tx.execute(status->{
                    f.jdbc.update("UPDATE wm_batch SET received_qty=received_qty+5,qc_status='UNQUALIFIED' WHERE batch_id=1");
                    sourceLocked.countDown();await(allowCommit);return null;
                })));
                assertTrue(sourceLocked.await(5,TimeUnit.SECONDS));
                Future<?> metadata=executor.submit(()->underTenant(()->{metadataStarted.countDown();f.service.changeStatus(1L,"LOCKED");}));
                assertTrue(metadataStarted.await(5,TimeUnit.SECONDS));
                assertThrows(TimeoutException.class,()->metadata.get(200,TimeUnit.MILLISECONDS));
                allowCommit.countDown();source.get(5,TimeUnit.SECONDS);metadata.get(5,TimeUnit.SECONDS);
                assertEquals(new BigDecimal("55.0000"),f.row().get("RECEIVED_QTY"));
                assertEquals(new BigDecimal("10.0000"),f.row().get("ISSUED_QTY"));
                assertEquals("UNQUALIFIED",f.row().get("QC_STATUS"));assertEquals("LOCKED",f.row().get("STATUS"));
            } finally {
                allowCommit.countDown();executor.shutdownNow();
                assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS),"The test worker must stop before its private database is closed");
            }
        }
    }

    @Test void legalSourceBatchCreationAndIqcWritebackStillWorkWithoutCreditingInventoryThroughCrud() throws Exception {
        try(var f=new Fixture()) {
            var result=f.service.createBatchByMaterial(100L,"PRD_WORK_ORDER",7L,"WO7",30L,"supplier-lot");
            var registered=f.jdbc.queryForMap("SELECT * FROM wm_batch WHERE batch_id=?",result.getBatchId());
            assertEquals("PRD_WORK_ORDER",registered.get("SOURCE_DOC_TYPE"));assertEquals(7L,registered.get("SOURCE_DOC_ID"));
            assertEquals(30L,registered.get("WAREHOUSE_ID"));assertEquals(BigDecimal.ZERO.setScale(4),registered.get("RECEIVED_QTY"));
            var line=new WmMaterialReceiveLine();line.setBatchCode("B1");
            when(f.receiveLines.selectList(any())).thenReturn(List.of(line));
            assertEquals(1,f.service.markQcStatusByReceive(71L,100L,"UNQUALIFIED"));
            assertEquals("UNQUALIFIED",f.row().get("QC_STATUS"));assertEquals(new BigDecimal("50.0000"),f.row().get("RECEIVED_QTY"));
        }
    }

    static void underTenant(Runnable action) {
        try(var tenants=mockStatic(TenantHelper.class)) {
            tenants.when(TenantHelper::isEnable).thenReturn(true);tenants.when(TenantHelper::getTenantId).thenReturn(Fixture.TENANT);
            DynamicDataSourceContextHolder.push("erp");try {action.run();} finally {DynamicDataSourceContextHolder.poll();}
        }
    }
    static void await(CountDownLatch latch) {try {if(!latch.await(5,TimeUnit.SECONDS))throw new IllegalStateException("latch timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}

    @RestControllerAdvice static class Errors {
        @ExceptionHandler(ServiceException.class) ResponseEntity<String> reject(ServiceException error) {return ResponseEntity.badRequest().body(error.getMessage());}
    }

    static final class Fixture implements AutoCloseable {
        static final String TENANT="000001";
        final CacheState caches=new CacheState();final HikariDataSource pool=new HikariDataSource();
        MockedStatic<TenantHelper> tenants;JdbcTemplate jdbc;WmBatchMapper mapper;
        final WmMaterialReceiveLineMapper receiveLines=mock(WmMaterialReceiveLineMapper.class);
        WmBatchServiceImpl target,service;ErpTraceContext erp;TransactionTemplate tx;MockMvc mvc;
        Fixture() throws Exception {
            try {
                tenants=mockStatic(TenantHelper.class);tenants.when(TenantHelper::isEnable).thenReturn(true);tenants.when(TenantHelper::getTenantId).thenReturn(TENANT);
                DynamicDataSourceContextHolder.push("erp");
                pool.setJdbcUrl("jdbc:h2:mem:batchfacts"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");pool.setUsername("sa");pool.setPassword("");pool.setMaximumPoolSize(4);
                var routing=mock(DynamicRoutingDataSource.class);when(routing.getDataSources()).thenReturn(Map.of("erp",pool));when(routing.getConnection()).thenAnswer(call->pool.getConnection());
                jdbc=new JdbcTemplate(routing);var cfg=new MybatisConfiguration();cfg.setLogImpl(NoLoggingImpl.class);cfg.setMapUnderscoreToCamelCase(true);
                cfg.setEnvironment(new Environment("isolated ERP batch facts",new SpringManagedTransactionFactory(),routing));
                var global=new GlobalConfig();global.setDbConfig(new GlobalConfig.DbConfig().setLogicDeleteValue("1").setLogicNotDeleteValue("0"));global.setSqlInjector(new DefaultSqlInjector());GlobalConfigUtils.setGlobalConfig(cfg,global);
                cfg.addMapper(WmBatchMapper.class);var info=TableInfoHelper.getTableInfo(WmBatch.class);var columns=new ArrayList<String>();columns.add("batch_id BIGINT PRIMARY KEY");
                info.getFieldList().forEach(field->{Class<?> type=field.getPropertyType();String sqlType=type==String.class?"VARCHAR(512)":Date.class.isAssignableFrom(type)?"TIMESTAMP":type==BigDecimal.class?"DECIMAL(18,4)":"BIGINT";columns.add(field.getColumn()+" "+sqlType);});
                jdbc.execute("CREATE TABLE wm_batch("+String.join(",",columns)+")");
                var factory=new MybatisSqlSessionFactoryBuilder().build(cfg);mapper=new SqlSessionTemplate(factory).getMapper(WmBatchMapper.class);erp=new ErpTraceContext(factory);
                var manager=new DataSourceTransactionManager(routing);tx=new TransactionTemplate(manager);
                var sequence=mock(DailyCodeSequenceProvider.class);var count=new AtomicInteger(6);when(sequence.next(anyString(),any())).thenAnswer(call->(long)count.getAndIncrement());
                target=new WmBatchServiceImpl(mapper,sequence,receiveLines,new WmBatchWriteGuard(mapper,erp));
                var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));service=(WmBatchServiceImpl)proxy.getProxy();
                var json=new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                mvc=MockMvcBuilders.standaloneSetup(new WmBatchController(service)).setControllerAdvice(new Errors()).setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
                jdbc.update("INSERT INTO wm_batch(batch_id,tenant_id,batch_code,material_id,material_code,material_name,material_spec,vendor_id,vendor_code,vendor_name,vendor_batch_code,warehouse_id,warehouse_code,warehouse_name,production_date,quantity,received_qty,issued_qty,qc_status,status,source_doc_type,source_doc_id,source_doc_no,rule_id,attr1,attr2,attr3,attr4,del_flag) VALUES(1,?,'B1',100,'M100','material','spec',5,'V5','vendor','vendor-lot',30,'W30','warehouse',TIMESTAMP '2026-10-01 00:00:00',100,50,10,'QUALIFIED','ACTIVE','PRD_WORK_ORDER',7,'WO7',18,'a','b',1,2,'0')",TENANT);
            } catch(Exception|Error failure) {close();throw failure;}
        }
        WmBatchBo registration(){var bo=new WmBatchBo();bo.setMaterialId(100L);bo.setBatchCode("B-NEW");return bo;}
        WmBatchBo edit(){var bo=new WmBatchBo();bo.setBatchId(1L);bo.setBatchCode("B1");bo.setMaterialId(100L);return bo;}
        int count(){return jdbc.queryForObject("SELECT COUNT(*) FROM wm_batch",Integer.class);}
        Map<String,Object> row(){return jdbc.queryForMap("SELECT * FROM wm_batch WHERE batch_id=1");}
        void assertFacts(String received,String issued){var row=row();assertEquals(0,new BigDecimal(received).compareTo((BigDecimal)row.get("RECEIVED_QTY")));assertEquals(0,new BigDecimal(issued).compareTo((BigDecimal)row.get("ISSUED_QTY")));assertEquals("QUALIFIED",row.get("QC_STATUS"));assertEquals("B1",row.get("BATCH_CODE"));assertEquals(100L,row.get("MATERIAL_ID"));assertEquals("PRD_WORK_ORDER",row.get("SOURCE_DOC_TYPE"));assertEquals(7L,row.get("SOURCE_DOC_ID"));}
        public void close(){try{pool.close();}finally{if(tenants!=null){tenants.close();tenants=null;}DynamicDataSourceContextHolder.clear();caches.close();}}
    }

    /** Restore all touched 3.5.16 caches/ID generator, including partially failed fixture construction. */
    static final class CacheState implements AutoCloseable {
        final List<Map<Object,Object>> live=new ArrayList<>(),saved=new ArrayList<>();final IdentifierGenerator generator;
        CacheState(){capture(TableInfoHelper.class,"TABLE_INFO_CACHE");capture(TableInfoHelper.class,"TABLE_NAME_INFO_CACHE");capture(LambdaUtils.class,"COLUMN_CACHE_MAP");capture(GlobalConfigUtils.class,"GLOBAL_CONFIG");generator=(IdentifierGenerator)value(IdWorker.class,"IDENTIFIER_GENERATOR");}
        @SuppressWarnings("unchecked") void capture(Class<?> type,String name){var map=(Map<Object,Object>)value(type,name);live.add(map);saved.add(new HashMap<>(map));}
        static Object value(Class<?> type,String name){try{Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(null);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
        public void close(){for(int i=0;i<live.size();i++){live.get(i).clear();live.get(i).putAll(saved.get(i));}IdWorker.setIdentifierGenerator(generator);}
    }
}
