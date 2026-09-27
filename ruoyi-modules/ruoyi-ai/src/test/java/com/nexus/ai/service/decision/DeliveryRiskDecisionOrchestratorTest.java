package com.nexus.ai.service.decision;

import cn.hutool.extra.spring.SpringUtil;
import com.nexus.ai.config.AiProperties;
import com.nexus.common.ai.decision.dto.*;
import com.nexus.common.ai.decision.enums.*;
import com.nexus.common.ai.decision.port.*;
import com.nexus.common.ai.domain.entity.AiDecisionRun;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import static com.nexus.ai.service.decision.DecisionTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class DeliveryRiskDecisionOrchestratorTest {
    private final AiProperties config = new AiProperties();
    private final IndustrialDecisionDataPort data = mock(IndustrialDecisionDataPort.class);
    private final DecisionWorkflowPort workflow = mock(DecisionWorkflowPort.class);
    private final DecisionPersistenceService store = mock(DecisionPersistenceService.class);
    private final RedissonClient redisson = mock(RedissonClient.class);
    private final RLock lock = mock(RLock.class);
    private final DeliveryRiskDecisionOrchestrator orchestrator =
        new DeliveryRiskDecisionOrchestrator(config, data, workflow, store, redisson);

    @Test void acceptedNewRunPublishesStartedStatusBeforeFlowExecution() {
        withTenant(() -> {
            var sse = mock(com.nexus.ai.service.sse.AiDecisionSseMessageHelper.class);
            var withEvents = new DeliveryRiskDecisionOrchestrator(config, data, workflow, store, redisson, sse);
            ready();
            when(store.createRun(any(), eq(actor()))).thenReturn(run(99L, DecisionRunStatus.CREATED));
            when(store.requireRun(99L, "000000")).thenReturn(run(99L, DecisionRunStatus.RUNNING));
            when(workflow.startAnalysis(any())).thenReturn(199L);
            withEvents.start(request(false), actor());
            InOrder ordered = inOrder(store, sse, workflow);
            ordered.verify(store).transition(99L, DecisionRunStatus.CREATED, DecisionRunStatus.RUNNING);
            ordered.verify(sse).sendStarted(1L, 99L);
            ordered.verify(workflow).startAnalysis(any());
            return null;
        });
    }

    @Test void executionOnlyActorCannotStartANewAnalysisThroughTheService() {
        withTenant(() -> {
            var executor = new DecisionActor("000000", 1L, "approver",
                java.util.Set.of("ai:decision:execute", "prd:work:query"));
            assertThrows(ServiceException.class, () -> orchestrator.start(request(false), executor));
            verifyNoInteractions(data, store, workflow, redisson);
            return null;
        });
    }

    @Test void activeRunIsReusedWithoutStartingFlowOrClaimingAnotherLock() {
        withTenant(() -> {
            when(data.canAccess(any(), any())).thenReturn(true);
            when(store.findActiveRun(any(), eq("000000"))).thenReturn(run(88L, DecisionRunStatus.RUNNING));
            var accepted = orchestrator.start(request(false), actor());
            assertEquals(88L, accepted.runId());
            assertEquals(DecisionRunStatus.RUNNING, accepted.status());
            verifyNoInteractions(workflow, redisson);
            return null;
        });
    }

    @Test void newRunIsRunningBeforeValidatedFlowStartsAndCarriesOnlyTrustedId() {
        withTenant(() -> {
            ready();
            when(store.findActiveRun(any(), eq("000000"))).thenReturn(null);
            when(store.createRun(any(), eq(actor()))).thenReturn(run(99L, DecisionRunStatus.CREATED));
            when(workflow.startAnalysis(any())).thenReturn(199L);
            var accepted = orchestrator.start(request(false), actor());
            assertEquals(99L, accepted.runId());
            assertEquals(DecisionRunStatus.RUNNING, accepted.status());
            var command = ArgumentCaptor.forClass(DecisionWorkflowStartCommand.class);
            InOrder ordered = inOrder(store, workflow);
            ordered.verify(store).createRun(any(), eq(actor()));
            ordered.verify(workflow).validateAnalysis(command.capture());
            ordered.verify(store).transition(99L, DecisionRunStatus.CREATED, DecisionRunStatus.RUNNING);
            ordered.verify(workflow).startAnalysis(any());
            ordered.verify(store).attachAnalysisFlow(99L, 199L);
            assertEquals(Map.of("runId", "99"), command.getValue().input());
            assertEquals("000000", command.getValue().tenantId());
            assertEquals(1L, command.getValue().userId());
            verify(lock).unlock();
            return null;
        });
    }

    @Test void graphValidationFailureMarksCreatedRunFailedWithoutLaunching() {
        withTenant(() -> {
            ready();
            when(store.createRun(any(), eq(actor()))).thenReturn(run(99L, DecisionRunStatus.CREATED));
            doThrow(new ServiceException("empty DAG")).when(workflow).validateAnalysis(any());
            assertThrows(ServiceException.class, () -> orchestrator.start(request(false), actor()));
            verify(store).fail(eq(99L), eq("FLOW_START_FAILED"), anyString());
            verify(workflow, never()).startAnalysis(any());
            return null;
        });
    }

    @Test void flowFailureSanitizesExceptionAndMarksRunFailed() {
        withTenant(() -> {
            ready();
            when(store.createRun(any(), eq(actor()))).thenReturn(run(99L, DecisionRunStatus.CREATED));
            when(workflow.startAnalysis(any())).thenThrow(new IllegalStateException("Bearer eyJSecretToken"));
            var failure = assertThrows(ServiceException.class, () -> orchestrator.start(request(false), actor()));
            assertFalse(failure.getMessage().contains("eyJSecretToken"));
            verify(store).fail(eq(99L), eq("FLOW_START_FAILED"), anyString());
            return null;
        });
    }

    @Test void sameBusinessIsRecheckedUnderDistributedLockBeforeInsert() {
        withTenant(() -> {
            ready();
            when(store.findActiveRun(any(), eq("000000"))).thenReturn(null,
                run(88L, DecisionRunStatus.RUNNING));
            var accepted = orchestrator.start(request(false), actor());
            assertEquals(88L, accepted.runId());
            verify(store, never()).createRun(any(), any());
            verify(lock).unlock();
            return null;
        });
    }

    @Test void crossTenantAndInvalidHorizonNeverReachPersistenceOrLock() {
        withTenant(() -> {
            when(data.canAccess(any(), any())).thenReturn(false);
            assertThrows(ServiceException.class, () -> orchestrator.start(request(false), actor()));
            var invalid = request(false); invalid.setHorizonDays(91);
            assertThrows(ServiceException.class, () -> orchestrator.start(invalid, actor()));
            verifyNoInteractions(store, workflow, redisson);
            return null;
        });
    }

    @Test void forceRestartSupersedesWaitingRunBeforeReplacingIt() {
        withTenant(() -> {
            ready();
            when(store.findActiveRun(any(), eq("000000"))).thenReturn(run(88L, DecisionRunStatus.WAITING_APPROVAL));
            when(store.createRun(any(), eq(actor()))).thenReturn(run(99L, DecisionRunStatus.CREATED));
            when(workflow.startAnalysis(any())).thenReturn(199L);
            var accepted = orchestrator.start(request(true), actor());
            assertEquals(99L, accepted.runId());
            InOrder ordered = inOrder(store);
            ordered.verify(store).transition(88L, DecisionRunStatus.WAITING_APPROVAL, DecisionRunStatus.STALE);
            ordered.verify(store).transition(88L, DecisionRunStatus.STALE, DecisionRunStatus.SUPERSEDED);
            ordered.verify(store).createRun(any(), eq(actor()));
            ordered.verify(store).linkReplacement(88L, 99L);
            return null;
        });
    }

    @Test void fastAnalysisMayFinishBeforeStartReturnsWithoutResettingRunStatus() {
        withTenant(() -> {
            ready();
            when(store.createRun(any(), eq(actor()))).thenReturn(run(99L, DecisionRunStatus.CREATED));
            when(workflow.startAnalysis(any())).thenReturn(199L);
            when(store.requireRun(99L, "000000")).thenReturn(run(99L, DecisionRunStatus.WAITING_APPROVAL));
            var accepted = orchestrator.start(request(false), actor());
            assertEquals(DecisionRunStatus.WAITING_APPROVAL, accepted.status());
            verify(store, times(1)).transition(99L, DecisionRunStatus.CREATED, DecisionRunStatus.RUNNING);
            return null;
        });
    }

    @Test void missingWorkflowBeanFailsClosedWithoutInsertingRun() {
        withTenant(() -> {
            var noFlow = new DeliveryRiskDecisionOrchestrator(config, data, null, store, redisson);
            when(data.canAccess(any(), any())).thenReturn(true);
            assertThrows(ServiceException.class, () -> noFlow.start(request(false), actor()));
            verify(store, never()).createRun(any(), any());
            return null;
        });
    }

    @Test void concurrentStartsForSameBusinessDispatchOnlyOneFlow() throws Exception {
        var firstReads = new CountDownLatch(2);
        var active = new AtomicReference<AiDecisionRun>();
        var realLock = new ReentrantLock();
        when(data.canAccess(any(), any())).thenReturn(true);
        when(redisson.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenAnswer(call ->
            realLock.tryLock(5, TimeUnit.SECONDS));
        when(lock.isHeldByCurrentThread()).thenAnswer(call -> realLock.isHeldByCurrentThread());
        doAnswer(call -> { realLock.unlock(); return null; }).when(lock).unlock();
        when(store.findActiveRun(any(), eq("000000"))).thenAnswer(call -> {
            AiDecisionRun observed = active.get();
            if (firstReads.getCount() > 0) {
                firstReads.countDown();
                assertTrue(firstReads.await(5, TimeUnit.SECONDS));
            }
            return observed;
        });
        when(store.createRun(any(), any())).thenAnswer(call -> {
            AiDecisionRun created = run(99L, DecisionRunStatus.CREATED);
            active.set(created);
            return created;
        });
        when(store.requireRun(99L, "000000")).thenReturn(run(99L, DecisionRunStatus.RUNNING));
        when(workflow.startAnalysis(any())).thenReturn(199L);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<DecisionRunAcceptedVo> first = pool.submit(() -> withTenant(() -> orchestrator.start(request(false), actor())));
            Future<DecisionRunAcceptedVo> second = pool.submit(() -> withTenant(() -> orchestrator.start(request(false), actor())));
            assertEquals(99L, first.get(10, TimeUnit.SECONDS).runId());
            assertEquals(99L, second.get(10, TimeUnit.SECONDS).runId());
            verify(store, times(1)).createRun(any(), any());
            verify(workflow, times(1)).startAnalysis(any());
            verify(lock, times(2)).tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }

    @Test void failedStopRecordsOrphanFlowForAuditInsteadOfClaimingCleanFailure() {
        withTenant(() -> {
            ready();
            when(store.createRun(any(), eq(actor()))).thenReturn(run(99L, DecisionRunStatus.CREATED));
            when(workflow.startAnalysis(any())).thenReturn(199L);
            when(workflow.stop(199L, actor())).thenReturn(false);
            doThrow(new ServiceException("cannot link")).when(store).attachAnalysisFlow(99L, 199L);
            assertThrows(ServiceException.class, () -> orchestrator.start(request(false), actor()));
            verify(store).fail(eq(99L), eq("ORPHAN_FLOW"), contains("199"));
            return null;
        });
    }

    @Test void fastCompletionWithUnlinkedFlowBecomesStaleNotApprovable() {
        withTenant(() -> {
            ready();
            when(store.createRun(any(), eq(actor()))).thenReturn(run(99L, DecisionRunStatus.CREATED));
            when(workflow.startAnalysis(any())).thenReturn(199L);
            when(store.requireRun(99L, "000000")).thenReturn(run(99L, DecisionRunStatus.WAITING_APPROVAL));
            doThrow(new ServiceException("cannot link")).when(store).attachAnalysisFlow(99L, 199L);
            assertThrows(ServiceException.class, () -> orchestrator.start(request(false), actor()));
            verify(store).transition(99L, DecisionRunStatus.WAITING_APPROVAL, DecisionRunStatus.STALE);
            return null;
        });
    }

    @Test void redisFailureBeforeCreateIsSanitizedAndDoesNotLeakLockDetails() {
        withTenant(() -> {
            when(data.canAccess(any(), any())).thenReturn(true);
            when(redisson.getLock(anyString())).thenReturn(lock);
            try { when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenThrow(new IllegalStateException("Bearer secret-redis")); }
            catch (InterruptedException impossible) { throw new AssertionError(impossible); }
            var failure = assertThrows(ServiceException.class, () -> orchestrator.start(request(false), actor()));
            assertFalse(failure.getMessage().contains("secret-redis"));
            verify(store, never()).createRun(any(), any());
            return null;
        });
    }

    @Test void failedFlowAttachmentStopsOrphanAndMarksRunFailed() {
        withTenant(() -> {
            ready();
            when(store.createRun(any(), eq(actor()))).thenReturn(run(99L, DecisionRunStatus.CREATED));
            when(workflow.startAnalysis(any())).thenReturn(199L);
            when(workflow.stop(199L, actor())).thenReturn(true);
            doThrow(new ServiceException("cannot link")).when(store).attachAnalysisFlow(99L, 199L);
            assertThrows(ServiceException.class, () -> orchestrator.start(request(false), actor()));
            InOrder ordered = inOrder(workflow, store);
            ordered.verify(workflow).startAnalysis(any());
            ordered.verify(store).attachAnalysisFlow(99L, 199L);
            ordered.verify(workflow).stop(199L, actor());
            ordered.verify(store).fail(eq(99L), eq("FLOW_START_FAILED"), anyString());
            return null;
        });
    }

    @Test void completionRejectsSpoofedErpFactEvenWhenMarkedVerifiable() {
        withTenant(() -> {
            DeliveryContextSnapshot snapshot = stockSnapshot("hash-1");
            AiDecisionRun running = run(99L, DecisionRunStatus.RUNNING);
            running.setInputSnapshotHash("hash-1");
            running.setScoreConfigJson("{\"horizonDays\":30}");
            when(store.requireRun(99L, "000000")).thenReturn(running);
            when(store.requireSnapshot(99L, "000000")).thenReturn(snapshot);
            when(store.requireHorizonDays(running)).thenReturn(30);
            when(data.canAccess(any(), any())).thenReturn(true);
            when(data.checkFreshness(any(), eq("hash-1"), eq(actor())))
                .thenReturn(new DecisionFreshnessResult(true, "hash-1", List.of()));
            when(data.loadDeliveryContext(any(), eq(30), eq(actor()))).thenReturn(snapshot);
            EvidenceItem fake = stockEvidence("99", "hash-1");
            var result = resultWithStockEvidence(fake);
            assertThrows(ServiceException.class, () -> orchestrator.completeAnalysis(99L, result, "TEMPLATE_FALLBACK", actor()));
            verify(store, never()).completeAnalysis(anyLong(), any(), anyString());
            return null;
        });
    }

    @Test void completionAcceptsAdminWildcardAfterIndependentPortReread() {
        withTenant(() -> {
            DecisionActor superAdmin = new DecisionActor("000000", 1L, "admin", Set.of("*:*:*"));
            DeliveryContextSnapshot snapshot = stockSnapshot("hash-1");
            AiDecisionRun running = run(99L, DecisionRunStatus.RUNNING);
            running.setInputSnapshotHash("hash-1");
            running.setScoreConfigJson("{\"horizonDays\":30}");
            when(store.requireRun(99L, "000000")).thenReturn(running);
            when(store.requireSnapshot(99L, "000000")).thenReturn(snapshot);
            when(store.requireHorizonDays(running)).thenReturn(30);
            when(data.canAccess(any(), eq(superAdmin))).thenReturn(true);
            when(data.checkFreshness(any(), eq("hash-1"), eq(superAdmin)))
                .thenReturn(new DecisionFreshnessResult(true, "hash-1", List.of()));
            when(data.loadDeliveryContext(any(), eq(30), eq(superAdmin))).thenReturn(snapshot);
            var result = resultWithStockEvidence(stockEvidence("10", "hash-1"));
            orchestrator.completeAnalysis(99L, result, "TEMPLATE_FALLBACK", superAdmin);
            verify(store).completeAnalysis(99L, result, "TEMPLATE_FALLBACK");
            return null;
        });
    }

    @Test void completionRejectsActorWithoutRunPermission() {
        DecisionActor queryOnly = new DecisionActor("000000", 1L, "admin", Set.of("ai:decision:query"));
        assertThrows(ServiceException.class, () ->
            orchestrator.completeAnalysis(99L, null, "TEMPLATE_FALLBACK", queryOnly));
        verifyNoInteractions(store, data);
    }
    @Test void completionAcceptsOnlySnapshotBoundFactsAfterIndependentPortReread() {
        withTenant(() -> {
            DeliveryContextSnapshot snapshot = stockSnapshot("hash-1");
            AiDecisionRun running = run(99L, DecisionRunStatus.RUNNING);
            running.setInputSnapshotHash("hash-1");
            running.setScoreConfigJson("{\"horizonDays\":30}");
            when(store.requireRun(99L, "000000")).thenReturn(running);
            when(store.requireSnapshot(99L, "000000")).thenReturn(snapshot);
            when(store.requireHorizonDays(running)).thenReturn(30);
            when(data.canAccess(any(), any())).thenReturn(true);
            when(data.checkFreshness(any(), eq("hash-1"), eq(actor())))
                .thenReturn(new DecisionFreshnessResult(true, "hash-1", List.of()));
            when(data.loadDeliveryContext(any(), eq(30), eq(actor()))).thenReturn(snapshot);
            var result = resultWithStockEvidence(stockEvidence("10", "hash-1"));
            orchestrator.completeAnalysis(99L, result, "TEMPLATE_FALLBACK", actor());
            verify(data).loadDeliveryContext(any(), eq(30), eq(actor()));
            verify(store).completeAnalysis(99L, result, "TEMPLATE_FALLBACK");
            return null;
        });
    }

    @Test void freshRereadWithLaterSnapshotAtDoesNotInvalidatePersistedObservation() {
        withTenant(() -> {
            DeliveryContextSnapshot stored = stockSnapshot("hash-1");
            DeliveryContextSnapshot reread = new DeliveryContextSnapshot(stored.rootBusinessRef(),
                stored.salesOrders(), stored.workOrders(), stored.routingByMaterial(), stored.requirements(),
                stored.stocks(), stored.arrivals(), stored.capacityByDate(), stored.holidays(),
                stored.currentSchedule(), stored.supplierMetrics(), "hash-1",
                stored.snapshotAt().plusSeconds(1), stored.warnings());
            AiDecisionRun running = run(99L, DecisionRunStatus.RUNNING);
            running.setInputSnapshotHash("hash-1");
            when(store.requireRun(99L, "000000")).thenReturn(running);
            when(store.requireSnapshot(99L, "000000")).thenReturn(stored);
            when(store.requireHorizonDays(running)).thenReturn(30);
            when(data.canAccess(any(), any())).thenReturn(true);
            when(data.checkFreshness(any(), eq("hash-1"), eq(actor())))
                .thenReturn(new DecisionFreshnessResult(true, "hash-1", List.of()));
            when(data.loadDeliveryContext(any(), eq(30), eq(actor()))).thenReturn(reread);
            var result = resultWithStockEvidence(stockEvidence("10", "hash-1"));
            orchestrator.completeAnalysis(99L, result, "TEMPLATE_FALLBACK", actor());
            verify(store).completeAnalysis(99L, result, "TEMPLATE_FALLBACK");
            return null;
        });
    }

    @Test void rereadWithSameHashButChangedNumericFactIsRejected() {
        withTenant(() -> {
            DeliveryContextSnapshot stored = stockSnapshot("hash-1");
            DeliveryContextSnapshot reread = new DeliveryContextSnapshot(stored.rootBusinessRef(),
                stored.salesOrders(), stored.workOrders(), stored.routingByMaterial(), stored.requirements(),
                List.of(new DeliveryContextSnapshot.StockFact(101L, java.math.BigDecimal.valueOf(99),
                    java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO)), stored.arrivals(),
                stored.capacityByDate(), stored.holidays(), stored.currentSchedule(),
                stored.supplierMetrics(), "hash-1", stored.snapshotAt().plusSeconds(1), stored.warnings());
            AiDecisionRun running = run(99L, DecisionRunStatus.RUNNING);
            running.setInputSnapshotHash("hash-1");
            when(store.requireRun(99L, "000000")).thenReturn(running);
            when(store.requireSnapshot(99L, "000000")).thenReturn(stored);
            when(store.requireHorizonDays(running)).thenReturn(30);
            when(data.canAccess(any(), any())).thenReturn(true);
            when(data.checkFreshness(any(), eq("hash-1"), eq(actor())))
                .thenReturn(new DecisionFreshnessResult(true, "hash-1", List.of()));
            when(data.loadDeliveryContext(any(), eq(30), eq(actor()))).thenReturn(reread);
            var result = resultWithStockEvidence(stockEvidence("10", "hash-1"));
            assertThrows(ServiceException.class, () -> orchestrator.completeAnalysis(99L, result, "TEMPLATE_FALLBACK", actor()));
            verify(store, never()).completeAnalysis(anyLong(), any(), anyString());
            return null;
        });
    }

    @Test void completionRejectsPortFactChangesEvenIfOldSnapshotClaimsVerifiable() {
        withTenant(() -> {
            DeliveryContextSnapshot snapshot = stockSnapshot("hash-1");
            AiDecisionRun running = run(99L, DecisionRunStatus.RUNNING);
            running.setInputSnapshotHash("hash-1");
            running.setScoreConfigJson("{\"horizonDays\":30}");
            when(store.requireRun(99L, "000000")).thenReturn(running);
            when(store.requireSnapshot(99L, "000000")).thenReturn(snapshot);
            when(store.requireHorizonDays(running)).thenReturn(30);
            when(data.canAccess(any(), any())).thenReturn(true);
            when(data.checkFreshness(any(), eq("hash-1"), eq(actor())))
                .thenReturn(new DecisionFreshnessResult(true, "hash-1", List.of()));
            when(data.loadDeliveryContext(any(), eq(30), eq(actor()))).thenReturn(stockSnapshot("hash-2"));
            var result = resultWithStockEvidence(stockEvidence("10", "hash-1"));
            assertThrows(ServiceException.class, () -> orchestrator.completeAnalysis(99L, result, "TEMPLATE_FALLBACK", actor()));
            verify(store, never()).completeAnalysis(anyLong(), any(), anyString());
            return null;
        });
    }

    @Test void supplierEvidencePreservesActualObservationTimeRatherThanSnapshotTime() {
        var at = java.time.LocalDateTime.of(2026, 9, 25, 11, 0);
        var snapshot = new DeliveryContextSnapshot(new DecisionBusinessRef(DecisionBusinessType.SCHEDULE_PLAN, 9001L, null),
            List.of(), List.of(), Map.of(), List.of(), List.of(), List.of(), Map.of(),
            List.of(), Map.of(), List.of(new DeliveryContextSnapshot.SupplierMetricFact(5L,
                java.math.BigDecimal.valueOf(90), 10, at)), "hash-1",
            java.time.LocalDateTime.of(2026, 9, 26, 10, 0), List.of());
        var supplier = new DecisionSourceFactVerifier().numericFacts(99L, snapshot).stream()
            .filter(e -> "on_time_rate".equals(e.metricCode())).findFirst().orElseThrow();
        assertEquals(at, supplier.observedAt());
    }

    @Test void supplierWithoutObservationTimestampIsExcludedInsteadOfBreakingSnapshotFacts() {
        var snapshot = new DeliveryContextSnapshot(new DecisionBusinessRef(DecisionBusinessType.SCHEDULE_PLAN, 9001L, null),
            List.of(), List.of(), Map.of(), List.of(), List.of(), List.of(), Map.of(),
            List.of(), Map.of(), List.of(new DeliveryContextSnapshot.SupplierMetricFact(5L,
                java.math.BigDecimal.valueOf(90), 10, null)), "hash-1",
            java.time.LocalDateTime.of(2026, 9, 26, 10, 0), List.of());
        assertTrue(new DecisionSourceFactVerifier().numericFacts(99L, snapshot).isEmpty());
    }

    @Test void unlockFailureCannotTurnSuccessfullyStartedRunIntoFalseError() {
        withTenant(() -> {
            ready();
            when(store.createRun(any(), eq(actor()))).thenReturn(run(99L, DecisionRunStatus.CREATED));
            when(store.requireRun(99L, "000000")).thenReturn(run(99L, DecisionRunStatus.WAITING_APPROVAL));
            when(workflow.startAnalysis(any())).thenReturn(199L);
            doThrow(new IllegalStateException("Bearer secret-unlock")).when(lock).unlock();
            var accepted = orchestrator.start(request(false), actor());
            assertEquals(DecisionRunStatus.WAITING_APPROVAL, accepted.status());
            verify(store, never()).fail(anyLong(), anyString(), anyString());
            return null;
        });
    }

    private static DeliveryContextSnapshot stockSnapshot(String hash) {
        return new DeliveryContextSnapshot(new DecisionBusinessRef(DecisionBusinessType.SCHEDULE_PLAN, 9001L, null),
            List.of(), List.of(), Map.of(), List.of(),
            List.of(new DeliveryContextSnapshot.StockFact(101L, java.math.BigDecimal.TEN,
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO)),
            List.of(), Map.of(), List.of(), Map.of(), List.of(), hash,
            java.time.LocalDateTime.of(2026, 9, 26, 10, 0), List.of());
    }

    private static EvidenceItem stockEvidence(String amount, String hash) {
        return new EvidenceItem(99L, "ERP:STOCK:101:stock_qty", "ERP", "STOCK", 101L,
            "101", null, "stock_qty", null, "NUMBER", amount, null,
            java.time.LocalDateTime.of(2026, 9, 26, 10, 0), hash, null, true, false, List.of(), null);
    }

    private static DecisionResult resultWithStockEvidence(EvidenceItem stock) {
        EvidenceItem score = new EvidenceItem(99L, "DERIVED:CALC:RISK:risk_score", "DERIVED", "CALC",
            null, "RISK", null, "risk_score", null, "NUMBER", "0", null,
            stock.observedAt(), stock.dataVersion(), null, true, false,
            List.of(stock.evidenceKey()), null);
        return new DecisionResult(99L, new DecisionBusinessRef(DecisionBusinessType.SCHEDULE_PLAN, 9001L, null),
            0, DecisionRiskLevel.LOW, "HIGH", List.of(), null, List.of(), null,
            Map.of(), List.of(stock, score), List.of(), List.of());
    }

    private void ready() {
        when(data.canAccess(any(), any())).thenReturn(true);
        when(redisson.getLock(anyString())).thenReturn(lock);
        try { when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true); }
        catch (InterruptedException impossible) { throw new AssertionError(impossible); }
        when(lock.isHeldByCurrentThread()).thenReturn(true);
    }

    private <T> T withTenant(Supplier<T> action) {
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class);
             MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            spring.when(() -> SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(LoginHelper::isLogin).thenReturn(false);
            try { return action.get(); }
            finally { TenantHelper.clearDynamic(); }
        }
    }
}
