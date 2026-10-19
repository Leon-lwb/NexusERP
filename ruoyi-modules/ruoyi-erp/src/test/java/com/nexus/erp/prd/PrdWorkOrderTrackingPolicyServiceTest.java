package com.nexus.erp.prd;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.trace.*;
import com.nexus.common.trace.port.WorkOrderTrackingPolicyPort;
import com.nexus.erp.bd.domain.vo.IndentedBomLineVo;
import com.nexus.erp.bd.service.IBdBomService;
import com.nexus.erp.prd.service.PrdWorkOrderTrackingPolicyService;
import com.nexus.erp.wm.trace.ErpTraceContext;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 使用独立本地连接和实际库存事务，不依赖制造模块、管理端或正在运行的业务库。
 */
class PrdWorkOrderTrackingPolicyServiceTest {
    @Test
    void releaseFreezesRootBomAndItsOwnRequirementIncludingBackflush() throws Exception {
        try (var f = new Fixture()) {
            String hash = f.freeze();
            assertEquals(
                    List.of(10L, 20L, 30L),
                    f.jdbc.queryForList(
                            "SELECT material_id FROM wm_work_order_tracking_policy ORDER BY"
                                    + " material_id",
                            Long.class));
            var root = f.service.find(Fixture.TENANT, 1L, 10L);
            var part = f.service.find(Fixture.TENANT, 1L, 20L);
            var backflush = f.service.find(Fixture.TENANT, 1L, 30L);
            assertTrue(root.originalRelease());
            assertEquals(hash, root.snapshotHash());
            assertEquals(hash, part.snapshotHash());
            assertEquals(
                    new TrackingPolicy(true, true, false, 5L, TrackingStage.STRICT), part.policy());
            assertEquals(8L, backflush.policy().version());
            assertTrue(backflush.policy().allowMixedLot());
            assertFalse(
                    f.service.find(Fixture.TENANT, 1L, 90L).known(),
                    "another requirement on the same plan is not a WO component");
            assertEquals(root.frozenAt(), root.effectiveFrom());
        }
    }

    @Test
    void changingMasterFlagsVersionsAndStageCannotRetroactivelyChangeFrozenPolicy()
            throws Exception {
        try (var f = new Fixture()) {
            f.freeze();
            var before = f.service.find(Fixture.TENANT, 1L, 20L);
            var auditRoot = f.service.find(Fixture.TENANT, 1L, 10L);
            f.jdbc.update(
                    "UPDATE bd_material SET"
                        + " is_serial_manage=0,allow_mixed_lot=1,tracking_stage='AUDIT',tracking_version=99");
            var after = f.service.find(Fixture.TENANT, 1L, 20L);
            assertEquals(before, after);
            f.jdbc.update("UPDATE bd_material SET tracking_stage='STRICT',tracking_version=100");
            assertEquals(auditRoot, f.service.find(Fixture.TENANT, 1L, 10L));
            f.jdbc.execute("DROP TABLE bd_material");
            assertEquals(before, f.service.find(Fixture.TENANT, 1L, 20L));
        }
    }

    @Test
    void unknownLegacyOrderNeverSynthesizesCurrentPolicyOrWritesEvidence() throws Exception {
        try (var f = new Fixture()) {
            f.jdbc.update("UPDATE prd_work_order SET wo_status='RELEASED'");
            f.jdbc.execute("DROP TABLE bd_material");
            WorkOrderTrackingPolicyPort port = f.service;
            var result = port.find(Fixture.TENANT, 1L, 10L);
            assertFalse(result.known());
            assertEquals(WorkOrderTrackingPolicySnapshot.Provenance.UNKNOWN, result.provenance());
            assertNull(result.policy());
            assertNull(result.policyHash());
            assertNotNull(result.unknownReason());
            assertEquals(0, f.count());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"RELEASED", "IN_PROGRESS", "COMPLETED", "CANCELLED"})
    void anAlreadyReleasedLegacyOrderCannotAcquireInventedReleaseEvidence(String status)
            throws Exception {
        try (var f = new Fixture()) {
            f.jdbc.update("UPDATE prd_work_order SET wo_status=?", status);
            assertThrows(RuntimeException.class, f::freeze);
            assertEquals(0, f.count());
        }
    }

    @Test
    void snapshotAndReleaseStateRollBackTogetherWhenTheSourceOperationFails() throws Exception {
        try (var f = new Fixture()) {
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            f.tx.execute(
                                    s -> {
                                        f.service.freezeForRelease(1L);
                                        f.jdbc.update(
                                                "UPDATE prd_work_order SET wo_status='RELEASED'");
                                        throw new IllegalStateException("source failure");
                                    }));
            assertEquals(0, f.count());
            assertEquals(
                    "CREATED",
                    f.jdbc.queryForObject("SELECT wo_status FROM prd_work_order", String.class));
        }
    }

    @Test
    void writerCannotRunWithoutTheActualLocalErpWriteTransaction() throws Exception {
        try (var f = new Fixture()) {
            assertThrows(RuntimeException.class, () -> f.service.freezeForRelease(1L));
            assertEquals(0, f.count());
            assertThrows(RuntimeException.class, () -> f.target.freezeForRelease(1L));
            assertEquals(0, f.count());
            DynamicDataSourceContextHolder.clear();
            DynamicDataSourceContextHolder.push("mes");
            assertThrows(RuntimeException.class, f::freeze);
            assertEquals(0, f.count());
        }
    }

    @Test
    void readerAndWriterRejectAnotherTenantBeforeLookingUpItsWorkOrder() throws Exception {
        try (var f = new Fixture()) {
            f.freeze();
            assertThrows(RuntimeException.class, () -> f.service.find("000002", 1L, 10L));
            assertThrows(
                    RuntimeException.class,
                    () -> f.service.find("000001" + Character.toString(0), 1L, 10L));
            f.jdbc.update("UPDATE prd_work_order SET tenant_id='000002'");
            assertThrows(RuntimeException.class, f::freeze);
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "UPDATE wm_work_order_tracking_policy SET policy_version=99 WHERE material_id=20",
                "UPDATE wm_work_order_tracking_policy SET policy_json='{}' WHERE material_id=20",
                "UPDATE wm_work_order_tracking_policy SET source_json='{}' WHERE material_id=10",
                "UPDATE wm_work_order_tracking_policy SET snapshot_hash=REPEAT('b',64) WHERE"
                        + " material_id=30",
                "DELETE FROM wm_work_order_tracking_policy WHERE material_id=20",
                "UPDATE wm_work_order_tracking_policy SET provenance='SOURCE_POLICY_ADOPTION' WHERE"
                        + " material_id=10"
            })
    void incompleteOrTamperedProofCannotBeReturnedAsAValidFrozenPolicy(String sql)
            throws Exception {
        try (var f = new Fixture()) {
            f.freeze();
            f.jdbc.update(sql);
            assertThrows(RuntimeException.class, () -> f.service.find(Fixture.TENANT, 1L, 10L));
        }
    }

    @Test
    void aForeignSourceRequirementOrBomCannotExpandTheSnapshotScope() throws Exception {
        try (var f = new Fixture()) {
            f.jdbc.update(
                    "UPDATE prd_material_requirement SET tenant_id='000002' WHERE"
                            + " requirement_id=200");
            assertThrows(RuntimeException.class, f::freeze);
            assertEquals(0, f.count());
        }
        try (var f = new Fixture()) {
            f.jdbc.update("UPDATE bd_bom SET parent_material_id=99");
            assertThrows(RuntimeException.class, f::freeze);
            assertEquals(0, f.count());
        }
    }

    @Test
    void failedSecondPolicyReadLeavesNoPartialFrozenMaterialSet() throws Exception {
        try (var f = new Fixture()) {
            f.jdbc.update("DELETE FROM bd_material WHERE material_id=30");
            assertThrows(RuntimeException.class, f::freeze);
            assertEquals(0, f.count());
        }
    }

    @Test
    void sameReleaseAttemptReusesItsEvidenceWithoutOverwritingAnyPolicy() throws Exception {
        try (var f = new Fixture()) {
            String hash = f.freeze();
            f.jdbc.update("UPDATE bd_material SET tracking_version=99,allow_mixed_lot=1");
            assertEquals(hash, f.freeze());
            assertEquals(3, f.count());
            assertEquals(5L, f.service.find(Fixture.TENANT, 1L, 20L).policy().version());
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "UPDATE bd_material SET tracking_version=NULL WHERE material_id=20",
                "UPDATE bd_material SET tracking_stage=NULL WHERE material_id=20",
                "UPDATE bd_material SET allow_mixed_lot=NULL WHERE material_id=20",
                "UPDATE bd_material SET is_serial_manage=NULL WHERE material_id=20",
                "UPDATE bd_material SET is_batch_manage=NULL WHERE material_id=20",
                "UPDATE bd_material SET tracking_version=0 WHERE material_id=20",
                "UPDATE bd_material SET allow_mixed_lot=2 WHERE material_id=20",
                "ALTER TABLE bd_material DROP COLUMN tracking_version"
            })
    void enabledReleaseRequiresExplicitCompletePolicyMetadataRatherThanAuditDefaults(String sql)
            throws Exception {
        try (var f = new Fixture()) {
            f.jdbc.execute(sql);
            assertThrows(RuntimeException.class, f::freeze);
            assertEquals(0, f.count());
        }
    }

    @Test
    void absentSchemaIsAnErrorAndCannotBeSilentlyReportedAsLegacyUnknown() throws Exception {
        try (var f = new Fixture()) {
            f.jdbc.execute("DROP TABLE wm_work_order_tracking_policy");
            assertThrows(RuntimeException.class, () -> f.service.find(Fixture.TENANT, 1L, 10L));
        }
    }

    static final class Fixture implements AutoCloseable {
        static final String TENANT = "000001";
        final HikariDataSource pool = new HikariDataSource();
        final JdbcTemplate jdbc;
        final TransactionTemplate tx;
        final IBdBomService bom = mock(IBdBomService.class);
        final PrdWorkOrderTrackingPolicyService target, service;

        Fixture() throws Exception {
            try {
                pool.setJdbcUrl(
                        "jdbc:h2:mem:wopolicy"
                                + UUID.randomUUID()
                                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
                pool.setUsername("sa");
                pool.setPassword("");
                pool.setMaximumPoolSize(3);
                var routing = mock(DynamicRoutingDataSource.class);
                when(routing.getDataSources()).thenReturn(Map.of("erp", pool));
                when(routing.getConnection()).thenAnswer(a -> pool.getConnection());
                jdbc = new JdbcTemplate(routing);
                var manager = new DataSourceTransactionManager(routing);
                tx = new TransactionTemplate(manager);
                var cfg = new Configuration();
                cfg.setEnvironment(new Environment("test", new JdbcTransactionFactory(), routing));
                var sf = mock(SqlSessionFactory.class);
                when(sf.getConfiguration()).thenReturn(cfg);
                var context = spy(new ErpTraceContext(sf));
                doReturn(TENANT).when(context).tenant();
                DynamicDataSourceContextHolder.push("erp");
                target =
                        new PrdWorkOrderTrackingPolicyService(
                                jdbc, context, new ObjectMapper(), bom);
                var factory = new ProxyFactory(target);
                factory.setProxyTargetClass(true);
                factory.addAdvice(
                        new TransactionInterceptor(
                                manager, new AnnotationTransactionAttributeSource()));
                service = (PrdWorkOrderTrackingPolicyService) factory.getProxy();
                schema();
                var component = new IndentedBomLineVo();
                component.setBomId(100L);
                component.setMaterialId(20L);
                component.setCumulativeQuantity(BigDecimal.ONE);
                component.setLevel(1);
                when(bom.explodeBom(100L)).thenReturn(List.of(component));
            } catch (Exception | Error error) {
                close();
                throw error;
            }
        }

        String freeze() {
            return tx.execute(s -> service.freezeForRelease(1L));
        }

        int count() {
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM wm_work_order_tracking_policy", Integer.class);
        }

        void schema() {
            jdbc.execute(
                    "CREATE TABLE prd_work_order(wo_id BIGINT PRIMARY KEY,tenant_id"
                            + " VARCHAR(20),material_id BIGINT,bom_id BIGINT,bom_version"
                            + " VARCHAR(64),source_type VARCHAR(64),source_id BIGINT,plan_id"
                            + " BIGINT,wo_status VARCHAR(32),backflush_flag INT,del_flag CHAR(1))");
            jdbc.execute(
                    "CREATE TABLE bd_bom(bom_id BIGINT PRIMARY KEY,tenant_id"
                        + " VARCHAR(20),parent_material_id BIGINT,bom_version VARCHAR(64),status"
                        + " BIGINT,del_flag CHAR(1))");
            jdbc.execute(
                    "CREATE TABLE prd_material_requirement(requirement_id BIGINT PRIMARY"
                            + " KEY,tenant_id VARCHAR(20),plan_id BIGINT,bom_id BIGINT,del_flag"
                            + " CHAR(1))");
            jdbc.execute(
                    "CREATE TABLE prd_material_requirement_detail(detail_id BIGINT PRIMARY"
                            + " KEY,tenant_id VARCHAR(20),requirement_id BIGINT,material_id"
                            + " BIGINT,required_quantity DECIMAL(18,4),backflush_flag INT,del_flag"
                            + " CHAR(1))");
            jdbc.execute(
                    "CREATE TABLE bd_material(material_id BIGINT PRIMARY KEY,tenant_id"
                            + " VARCHAR(20),is_batch_manage BIGINT,is_serial_manage"
                            + " BIGINT,allow_mixed_lot INT,tracking_version BIGINT,tracking_stage"
                            + " VARCHAR(16),status BIGINT,del_flag CHAR(1))");
            jdbc.execute(
                    "CREATE TABLE wm_work_order_tracking_policy(snapshot_id BIGINT PRIMARY"
                        + " KEY,tenant_id VARCHAR(20) NOT NULL,erp_wo_id BIGINT NOT"
                        + " NULL,material_id BIGINT NOT NULL,snapshot_version INT NOT"
                        + " NULL,policy_version BIGINT NOT NULL,policy_json CLOB NOT"
                        + " NULL,policy_hash VARCHAR(64) NOT NULL,source_json CLOB NOT"
                        + " NULL,source_hash VARCHAR(64) NOT NULL,snapshot_hash VARCHAR(64) NOT"
                        + " NULL,provenance VARCHAR(32) NOT NULL,evidence_ref VARCHAR(255) NOT"
                        + " NULL,frozen_at TIMESTAMP(3) NOT NULL,effective_from TIMESTAMP(3) NOT"
                        + " NULL,actor_id BIGINT,UNIQUE(tenant_id,erp_wo_id,material_id))");
            jdbc.update(
                    "INSERT INTO prd_work_order"
                        + " VALUES(1,?,10,100,'B1','MATERIAL_REQUIREMENT',200,500,'CREATED',0,'0')",
                    TENANT);
            jdbc.update("INSERT INTO bd_bom VALUES(100,?,10,'B1',1,'0')", TENANT);
            jdbc.update(
                    "INSERT INTO prd_material_requirement"
                            + " VALUES(200,?,500,100,'0'),(299,?,500,100,'0')",
                    TENANT,
                    TENANT);
            jdbc.update(
                    "INSERT INTO prd_material_requirement_detail"
                        + " VALUES(201,?,200,30,2,1,'0'),(202,?,200,20,1,0,'0'),(299,?,299,90,1,0,'0')",
                    TENANT,
                    TENANT,
                    TENANT);
            jdbc.update(
                    "INSERT INTO bd_material"
                        + " VALUES(10,?,1,1,1,3,'AUDIT',1,'0'),(20,?,1,1,0,5,'STRICT',1,'0'),(30,?,1,0,1,8,'STRICT',1,'0')",
                    TENANT,
                    TENANT,
                    TENANT);
        }

        public void close() {
            DynamicDataSourceContextHolder.clear();
            pool.close();
        }
    }
}
