package com.nexus.mes.pro.service;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.pro.mapper.ProWorkOrderStepProgressMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

/** H2旧视图冲突模型验证全事务回滚及新事务固定数量重做；不能替代MySQL当前读和运行态验收。 */
class ProWorkOrderStepProgressConcurrencyTest {
    @Test
    void h2OldSnapshotConflictRollsBackBeforeFreshProjectionRetry() throws Exception {
        try (var pool=new HikariDataSource()) {
            pool.setJdbcUrl("jdbc:h2:mem:progress_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
            pool.setUsername("sa");pool.setMaximumPoolSize(4);
            var routing=new DynamicRoutingDataSource(java.util.List.of());routing.setPrimary("mes");routing.addDataSource("mes",pool);
            var db=new JdbcTemplate(routing);
            db.execute("CREATE TABLE pro_work_order(mes_wo_id BIGINT PRIMARY KEY,tenant_id VARCHAR(20),del_flag CHAR(1),planned_qty DECIMAL(18,4),completed_qty DECIMAL(18,4),scrap_qty DECIMAL(18,4),wo_status VARCHAR(32),material_id BIGINT)");
            db.execute("CREATE TABLE pro_work_order_step(step_id BIGINT PRIMARY KEY,mes_wo_id BIGINT,tenant_id VARCHAR(20),del_flag CHAR(1),sequence_num INT,planned_qty DECIMAL(18,4),completed_qty DECIMAL(18,4),scrap_qty DECIMAL(18,4),step_status VARCHAR(32),start_time TIMESTAMP,end_time TIMESTAMP,update_time TIMESTAMP)");
            db.execute("CREATE TABLE pro_flow_card(card_id BIGINT PRIMARY KEY,mes_wo_id BIGINT,tenant_id VARCHAR(20),del_flag CHAR(1),planned_qty DECIMAL(18,4),completed_qty DECIMAL(18,4),scrap_qty DECIMAL(18,4),card_status VARCHAR(32),material_id BIGINT)");
            db.execute("CREATE TABLE pro_flow_card_step(step_record_id BIGINT PRIMARY KEY,card_id BIGINT,mes_wo_id BIGINT,wo_step_id BIGINT,tenant_id VARCHAR(20),del_flag CHAR(1),sequence_num INT,planned_qty DECIMAL(18,4),good_qty DECIMAL(18,4),defect_qty DECIMAL(18,4),scrap_qty DECIMAL(18,4),step_status VARCHAR(32),actual_start_time TIMESTAMP,actual_end_time TIMESTAMP)");
            db.update("INSERT INTO pro_work_order VALUES(10,'000000','0',10,0,0,'IN_PROGRESS',200)");
            db.update("INSERT INTO pro_work_order_step VALUES(11,10,'000000','0',10,10,0,0,'PENDING',NULL,NULL,NULL)");
            db.update("INSERT INTO pro_flow_card VALUES(12,10,'000000','0',4,0,0,'IN_PROGRESS',200),(13,10,'000000','0',6,0,0,'IN_PROGRESS',200)");
            db.update("INSERT INTO pro_flow_card_step VALUES(21,12,10,11,'000000','0',10,4,0,0,0,'IN_PROGRESS',CURRENT_TIMESTAMP,NULL),(22,13,10,11,'000000','0',10,6,0,0,0,'IN_PROGRESS',CURRENT_TIMESTAMP,NULL)");
            var config=new Configuration();config.setMapUnderscoreToCamelCase(true);config.setEnvironment(new Environment("memory",new SpringManagedTransactionFactory(),routing));config.addMapper(ProWorkOrderStepProgressMapper.class);
            var factory=new SqlSessionFactoryBuilder().build(config);var sessions=new SqlSessionTemplate(factory);
            var service=new ProWorkOrderStepProgressService(sessions.getMapper(ProWorkOrderStepProgressMapper.class),new MesProgressTransactionGuard(factory));
            var manager=new DataSourceTransactionManager(routing);
            var firstLocked=new CountDownLatch(1);var secondStarted=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
            try {
                Future<?> first=executor.submit(()->write(service,db,manager,12L,21L,4,firstLocked,secondStarted,true));
                assertTrue(firstLocked.await(10,TimeUnit.SECONDS));
                Future<?> second=executor.submit(()->write(service,db,manager,13L,22L,6,firstLocked,secondStarted,false));
                first.get(20, TimeUnit.SECONDS);
                ExecutionException conflict = assertThrows(ExecutionException.class, () -> second.get(20, TimeUnit.SECONDS));
                // 本H2版本在已建立的RR旧视图上锁新版本时实际报冲突，不把它伪装成MySQL当前读。
                assertInstanceOf(org.springframework.dao.CannotAcquireLockException.class, conflict.getCause());
                assertEquals(0, db.queryForObject("SELECT good_qty FROM pro_flow_card_step WHERE step_record_id=22", java.math.BigDecimal.class).signum(), "失败写者的数量必须随原事务全部回滚");
                assertEquals(0, new java.math.BigDecimal("4").compareTo(db.queryForObject("SELECT completed_qty FROM pro_work_order_step WHERE step_id=11", java.math.BigDecimal.class)));
                finishSecondInFreshTransaction(service, db, manager);
                assertEquals(0,new java.math.BigDecimal("10").compareTo(db.queryForObject("SELECT completed_qty FROM pro_work_order_step WHERE step_id=11",java.math.BigDecimal.class)));
                assertEquals("COMPLETED",db.queryForObject("SELECT step_status FROM pro_work_order_step WHERE step_id=11",String.class));
                assertEquals(0,db.queryForObject("SELECT completed_qty FROM pro_work_order WHERE mes_wo_id=10",java.math.BigDecimal.class).signum(),"投影不承担工单数量入账");
            } finally {executor.shutdownNow();}
        }
    }
    private void write(ProWorkOrderStepProgressService service,JdbcTemplate db,DataSourceTransactionManager manager,Long card,Long step,int good,CountDownLatch firstLocked,CountDownLatch secondStarted,boolean first) {
        try (var tenant=mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::getTenantId).thenReturn("000000");DynamicDataSourceContextHolder.push("mes");
            var tx=new TransactionTemplate(manager);tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            tx.executeWithoutResult(status->{
                if (!first) {
                    // 在第一写者提交前建立真实旧视图，再通知它继续；不能只靠线程启动表示快照已建立。
                    assertEquals(0, db.queryForObject("SELECT good_qty FROM pro_flow_card_step WHERE step_record_id=21", java.math.BigDecimal.class).signum());
                    secondStarted.countDown();
                }
                service.lockForCard(card);
                if(first){firstLocked.countDown();try{if(!secondStarted.await(10,TimeUnit.SECONDS))throw new AssertionError("第二写者尚未建立旧视图");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
                db.update("UPDATE pro_flow_card_step SET good_qty=?,step_status='COMPLETED',actual_end_time=CURRENT_TIMESTAMP WHERE step_record_id=?",good,step);
                service.rebuildForCard(card);
            });
        } finally {DynamicDataSourceContextHolder.clear();}
    }
    /** 仅在本测试已观察到完整回滚后新开事务；不表示业务HTTP未知结果可以自动重发。 */
    private void finishSecondInFreshTransaction(ProWorkOrderStepProgressService service, JdbcTemplate db,
            DataSourceTransactionManager manager) {
        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::getTenantId).thenReturn("000000");
            DynamicDataSourceContextHolder.push("mes");
            var tx = new TransactionTemplate(manager);
            tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            tx.executeWithoutResult(status -> {
                service.lockForCard(13L);
                db.update("UPDATE pro_flow_card_step SET good_qty=6,step_status='COMPLETED',actual_end_time=CURRENT_TIMESTAMP WHERE step_record_id=22");
                service.rebuildForCard(13L);
            });
        } finally {
            DynamicDataSourceContextHolder.clear();
        }
    }

}
