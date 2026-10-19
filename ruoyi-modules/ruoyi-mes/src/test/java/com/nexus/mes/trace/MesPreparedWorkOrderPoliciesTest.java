package com.nexus.mes.trace;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.aop.*;
import com.baomidou.dynamic.datasource.processor.DsSpelExpressionProcessor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.trace.*;
import com.nexus.common.trace.port.WorkOrderTrackingPolicyPort;
import com.nexus.mes.integration.trace.MesWorkOrderTrackingPolicyReader;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;
import java.time.Instant;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 通过真实代理与独立制造库验证事务挂起，确保写入前先读取冻结策略。
 */
class MesPreparedWorkOrderPoliciesTest {
    MesTraceBusinessTest b;
    MesPreparedWorkOrderPolicies prepared;
    AtomicInteger reads;
    AtomicBoolean unknown;
    TrackingPolicy frozen = new TrackingPolicy(true, false, true, 7L, TrackingStage.STRICT);

    @BeforeEach
    void setup() {
        b = new MesTraceBusinessTest();
        b.setup();
        reads = new AtomicInteger();
        unknown = new AtomicBoolean();
        WorkOrderTrackingPolicyPort bank =
                (tenant, wo, material) -> {
                    assertFalse(
                            TransactionSynchronizationManager.isActualTransactionActive(),
                            "ERP bank reads must not hold a MES transaction");
                    reads.incrementAndGet();
                    if (unknown.get()) {
                        return WorkOrderTrackingPolicySnapshot.unknown(
                                tenant, wo, material, "legacy release evidence missing");
                    }
                    var time = Instant.parse("2026-10-18T00:00:00Z");
                    return new WorkOrderTrackingPolicySnapshot(
                            tenant,
                            wo,
                            material,
                            WorkOrderTrackingPolicySnapshot.Provenance.ERP_RELEASE,
                            frozen,
                            1,
                            "a".repeat(64),
                            "b".repeat(64),
                            "c".repeat(64),
                            "ERP_RELEASE:20",
                            time,
                            time,
                            null,
                            null);
                };
        var target = new MesWorkOrderTrackingPolicyReader(b.jdbc, b.context, bank);
        var factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvisor(
                new DynamicDataSourceAnnotationAdvisor(
                        new DynamicDataSourceAnnotationInterceptor(
                                true, new DsSpelExpressionProcessor()),
                        DS.class));
        var tx = new TransactionInterceptor();
        tx.setTransactionManager(b.tx.getTransactionManager());
        tx.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        factory.addAdvice(tx);
        prepared =
                new MesPreparedWorkOrderPolicies(
                        b.jdbc, b.context, (MesWorkOrderTrackingPolicyReader) factory.getProxy());
        ReflectionTestUtils.setField(prepared, "traceRelayEnabled", true);
    }

    @AfterEach
    void cleanup() {
        b.cleanup();
    }

    @Test
    void publicCardPreparationReadsActualWorkOrderAndReusesFrozenPolicyAfterWrites() {
        b.inTx(
                () -> {
                    prepared.prepareCard(11L);
                    assertSame(frozen, prepared.productPolicy(10L, 20L, 100L));
                    b.events.command("business", "TEST_COMMAND", java.util.Map.of("value", 1));
                    prepared.prepareCard(11L);
                    prepared.prepareWorkOrder(10L);
                    assertSame(frozen, prepared.productPolicy(10L, 20L, 100L));
                    return null;
                });
        assertEquals(1, reads.get());
    }

    @Test
    void unknownReleaseFailsBeforeAnyLocalCommandOrBusinessWrite() {
        unknown.set(true);
        assertThrows(
                ServiceException.class,
                () ->
                        b.inTx(
                                () -> {
                                    prepared.prepareCard(11L);
                                    b.events.command(
                                            "must-not-exist", "TEST_COMMAND", java.util.Map.of());
                                    return null;
                                }));
        assertEquals(
                0, b.jdbc.queryForObject("SELECT COUNT(*) FROM mes_trace_inbox", Integer.class));
    }

    @Test
    void policyCannotBeFetchedForTheFirstTimeAfterMesWritesStarted() {
        b.inTx(
                () -> {
                    MesPreparedWorkOrderPolicies.markWriteStarted();
                    assertThrows(ServiceException.class, () -> prepared.prepareCard(11L));
                    return null;
                });
        assertEquals(0, reads.get());
    }

    @Test
    void frozenPolicyLookupCannotDriftToAnotherErpWorkOrderOrProduct() {
        b.inTx(
                () -> {
                    prepared.prepareCard(11L);
                    assertThrows(
                            ServiceException.class, () -> prepared.productPolicy(10L, 21L, 100L));
                    assertThrows(
                            ServiceException.class, () -> prepared.productPolicy(10L, 20L, 101L));
                    return null;
                });
    }

    @Test
    void completedTransactionDoesNotLeakPolicyIntoTheNextOperation() {
        b.inTx(
                () -> {
                    prepared.prepareCard(11L);
                    return null;
                });
        b.inTx(
                () -> {
                    prepared.prepareCard(11L);
                    return null;
                });
        assertEquals(2, reads.get());
    }

    @Test
    void rolledBackOperationDoesNotLeaveAReusablePolicyFrame() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        b.inTx(
                                () -> {
                                    prepared.prepareCard(11L);
                                    throw new IllegalStateException();
                                }));
        b.inTx(
                () -> {
                    prepared.prepareCard(11L);
                    return null;
                });
        assertEquals(2, reads.get());
    }

    @Test
    void requiresNewTransactionHasItsOwnPreparedPolicyScope() {
        b.inTx(
                () -> {
                    prepared.prepareCard(11L);
                    var inner = new TransactionTemplate(b.tx.getTransactionManager());
                    inner.setPropagationBehavior(
                            org.springframework.transaction.TransactionDefinition
                                    .PROPAGATION_REQUIRES_NEW);
                    inner.execute(
                            status -> {
                                prepared.prepareCard(11L);
                                return null;
                            });
                    assertSame(frozen, prepared.productPolicy(10L, 20L, 100L));
                    return null;
                });
        assertEquals(2, reads.get());
    }

    @Test
    void legacyQuantityModeDoesNotRequireAReleaseSnapshot() {
        ReflectionTestUtils.setField(prepared, "traceRelayEnabled", false);
        unknown.set(true);
        b.inTx(
                () -> {
                    prepared.prepareCard(11L);
                    prepared.prepareWorkOrder(10L);
                    return null;
                });
        assertEquals(0, reads.get());
    }

    @Test
    void componentPreparationUsesRealConsumptionWorkOrder() {
        b.jdbc.update(
                "INSERT INTO"
                    + " lt_wo_consumption(consumption_id,tenant_id,wo_id,material_id,consumed_qty)"
                    + " VALUES(55,'000001',10,200,1)");
        assertSame(frozen, prepared.componentForConsumption("assign", 55L));
        assertEquals(1, reads.get());
    }

    @Test
    void correctionPreparationUsesRealSnWorkOrder() {
        b.sn(1, "COMPLETED");
        assertSame(frozen, prepared.componentForSn("correct", 1L, 200L));
        assertEquals(1, reads.get());
    }

    @Test
    void componentPolicyReadCannotSuspendAnAlreadyHeldMesWriteTransaction() {
        assertThrows(
                ServiceException.class,
                () ->
                        b.inTx(
                                () ->
                                        prepared.componentForWorkOrder(
                                                "MATERIAL_PICK", "pick", 10L, 200L)));
        assertEquals(0, reads.get());
    }

    @Test
    void durableAppliedComponentCommandDoesNotRequeryMissingLegacyPolicy() {
        b.inTx(
                () -> {
                    var command =
                            b.events.command("committed", "SN_CONSUMPTION", java.util.List.of(1));
                    b.events.finish(command, java.util.Map.of("entryId", 1));
                    return null;
                });
        unknown.set(true);
        assertNull(prepared.componentForConsumption("committed", 999L));
        assertEquals(0, reads.get());
    }

    @Test
    void actualManifestUsesFrozenProductBatchPolicyAfterCurrentMasterDrifts() {
        frozen = new TrackingPolicy(true, true, true, 7L, TrackingStage.STRICT);
        b.productMapping();
        b.sn(1, "COMPLETED");
        var wo = b.wo(1, 0);
        org.mockito.Mockito.when(b.policies.policy("000001", 100L))
                .thenReturn(new TrackingPolicy(false, false, true, 99L, TrackingStage.STRICT));
        org.mockito.Mockito.clearInvocations(b.policies);
        ReflectionTestUtils.setField(b.manifests, "preparedPolicies", prepared);
        var manifest = b.inTx(() -> b.manifests.freeze(wo));
        assertEquals(
                600L,
                b.manifests
                        .lines("000001", manifest.manifestId(), 1, null, 500)
                        .rows()
                        .get(0)
                        .batchId());
        assertEquals(1, reads.get());
        org.mockito.Mockito.verifyNoInteractions(b.policies);
    }

    @Test
    void unknownProductPolicyStopsActualFreezeBeforeAnyNewManifestOrCommand() {
        b.sn(1, "COMPLETED");
        var wo = b.wo(1, 0);
        unknown.set(true);
        ReflectionTestUtils.setField(b.manifests, "preparedPolicies", prepared);
        assertThrows(ServiceException.class, () -> b.inTx(() -> b.manifests.freeze(wo)));
        assertEquals(
                0,
                b.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM pro_completion_manifest", Integer.class));
        assertEquals(
                0, b.jdbc.queryForObject("SELECT COUNT(*) FROM mes_trace_outbox", Integer.class));
    }

    @Test
    void committedActualPickCanReplayWithoutInventingAMissingLegacyPolicy() {
        long allocation = b.allocate("source-map", b.source(1, 5, 0), 8, 5);
        var pick = b.pick("committed-pick", 8, allocation, 1, null);
        var original = b.inTx(() -> b.materials.pick(pick, b.componentPolicy));
        unknown.set(true);
        assertNull(prepared.componentForWorkOrder("MATERIAL_PICK", "committed-pick", 10L, 200L));
        assertEquals(original, b.inTx(() -> b.materials.pick(pick, null)));
        assertEquals(0, reads.get());
        assertEquals(
                1, b.jdbc.queryForObject("SELECT COUNT(*) FROM lt_wo_consumption", Integer.class));
    }
}
