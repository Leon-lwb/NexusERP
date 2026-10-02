package com.nexus.erp.ai.adapter;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.ai.decision.dto.*;
import com.nexus.common.ai.decision.enums.DecisionActionType;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.ai.decision.enums.DecisionRiskLevel;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class ErpIndustrialDecisionDataAdapterTest {
    private static final DecisionBusinessRef REF = new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 11L, "WO-11");
    private static final Set<String> PERMISSIONS = Set.of(
        "ai:decision:run", "ai:decision:execute", "prd:work:query", "pu:requisition:add", "prd:schedule:run");
    private static final DecisionActor ACTOR = new DecisionActor("TENANT-B", 7L, "tester", PERMISSIONS);
    private static final DeliveryContextSnapshot SNAPSHOT = new DeliveryContextSnapshot(
        REF, List.of(), List.of(), Map.of(), List.of(), List.of(), List.of(), Map.of(),
        List.of(), Map.of(), List.of(), "old-hash", LocalDateTime.of(2026, 9, 24, 10, 0), List.of());

    @Mock private DeliveryContextAssembler assembler;
    @Mock private ErpDecisionShortageSimulation shortage;
    @Mock private ErpDecisionApsSimulation aps;
    @Mock private ErpDecisionFreshnessChecker freshness;
    @Mock private ErpDecisionActionExecutor executor;
    @Mock private ErpCertifiedPurchaseDecisionAdapter purchase;
    @InjectMocks private ErpIndustrialDecisionDataAdapter adapter;

    @Test
    void facadeExposesPortOnErpDatasource() {
        assertInstanceOf(IndustrialDecisionDataPort.class, adapter);
        assertEquals("erp", ErpIndustrialDecisionDataAdapter.class.getAnnotation(DS.class).value());
    }

    @Test
    void invalidActorOrBusinessPermissionFailsClosedBeforeAnyDelegate() {
        assertFalse(adapter.canAccess(REF, null));
        assertFalse(adapter.canAccess(REF, new DecisionActor("  ", 7L, "tester", PERMISSIONS)));
        assertFalse(adapter.canAccess(REF, new DecisionActor("TENANT-B", 7L, "tester", Set.of("prd:work:query"))));
        assertFalse(adapter.canAccess(REF, new DecisionActor("TENANT-B", 7L, "tester", Set.of("ai:decision:run"))));
        assertFalse(adapter.canAccess(new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 0L, null), ACTOR));
        assertThrows(ServiceException.class, () -> adapter.loadDeliveryContext(REF, 7, null));
        assertThrows(ServiceException.class, () -> adapter.checkFreshness(REF, "old-hash",
            new DecisionActor("", 7L, "tester", PERMISSIONS)));
        assertThrows(ServiceException.class, () -> adapter.simulateShortage(SNAPSHOT, 7, Map.of(), null));
        assertThrows(ServiceException.class, () -> adapter.simulateSchedules(SNAPSHOT, List.of("APS_EDD"), 7, null));
        assertThrows(ServiceException.class, () -> adapter.executeBusinessAction(purchaseAction(), null));
        assertThrows(ServiceException.class, () -> adapter.verifyBusinessAction(purchaseAction(), success(), null));
        assertThrows(ServiceException.class, () -> adapter.compensateBusinessAction(purchaseAction(), success(), null));
        verifyNoInteractions(assembler, shortage, aps, freshness, executor);
    }

    @Test
    void queryOnlyViewerNeedsBothDecisionAndBusinessReadPermission() {
        withTenant(() -> {
            DecisionActor viewer = actor(Set.of("ai:decision:query", "prd:work:query"));
            when(assembler.exists(REF, viewer)).thenReturn(true);
            assertTrue(adapter.canReadDecision(REF, viewer));
            assertFalse(adapter.canReadDecision(REF, actor(Set.of("ai:decision:query"))));
            assertFalse(adapter.canReadDecision(REF, actor(Set.of("prd:work:query"))));
            verify(assembler, times(1)).exists(REF, viewer);
        });
    }

    @Test
    void approverWithExecutionAndBusinessReadMayCheckFreshnessAndCreateDraftWithoutAnalysisRight() {
        withTenant(() -> {
            DecisionActor approver = actor(Set.of("ai:decision:execute", "prd:work:query", "pu:requisition:add"));
            when(assembler.exists(REF, approver)).thenReturn(true);
            var current = new DecisionFreshnessResult(true, "old-hash", List.of());
            when(freshness.check(REF, "old-hash", approver)).thenReturn(current);
            when(purchase.execute(purchaseAction(), approver)).thenReturn(success());
            assertTrue(adapter.canAccess(REF, approver));
            assertSame(current, adapter.checkFreshness(REF, "old-hash", approver));
            assertEquals(success(), adapter.executeBusinessAction(purchaseAction(), approver));
            assertFalse(adapter.canAccess(REF, actor(Set.of("ai:decision:execute"))));
        });
    }

    @Test
    void canAccessUsesBusinessPermissionMatrixAndExactRootExistence() {
        var cases = Map.of(
            DecisionBusinessType.SALES_ORDER, "so:orderheader:query",
            DecisionBusinessType.WORK_ORDER, "prd:work:query",
            DecisionBusinessType.SCHEDULE_PLAN, "prd:schedule:query",
            DecisionBusinessType.SHORTAGE_HORIZON, "prd:shortage:query");
        withTenant(() -> {
            for (var entry : cases.entrySet()) {
                DecisionBusinessRef ref = new DecisionBusinessRef(entry.getKey(),
                    entry.getKey() == DecisionBusinessType.SHORTAGE_HORIZON ? null : 11L, null);
                DecisionActor permitted = actor(Set.of("ai:decision:run", entry.getValue()));
                assertFalse(adapter.canAccess(ref, ACTOR_WITH_NO_READ));
                when(assembler.exists(ref, permitted)).thenAnswer(call -> {
                    assertEquals("TENANT-B", TenantHelper.getDynamic());
                    return true;
                });
                assertTrue(adapter.canAccess(ref, permitted));
                verify(assembler).exists(ref, permitted);
            }
            assertNull(TenantHelper.getDynamic());
        });
    }

    private static final DecisionActor ACTOR_WITH_NO_READ = actor(Set.of("ai:decision:run"));

    @Test
    void allReadMethodsRunWithinTenantAndRestoreOuterTenantEvenOnFailure() {
        withTenant(() -> {
            TenantHelper.setDynamic("TENANT-A");
            when(assembler.exists(REF, ACTOR)).thenAnswer(call -> {
                assertEquals("TENANT-B", TenantHelper.getDynamic());
                return true;
            });
            when(assembler.assemble(REF, 14, ACTOR)).thenAnswer(call -> {
                assertEquals("TENANT-B", TenantHelper.getDynamic());
                return SNAPSHOT;
            });
            when(freshness.check(REF, "old-hash", ACTOR)).thenAnswer(call -> {
                assertEquals("TENANT-B", TenantHelper.getDynamic());
                return new DecisionFreshnessResult(true, "old-hash", List.of());
            });
            assertTrue(adapter.canAccess(REF, ACTOR));
            assertSame(SNAPSHOT, adapter.loadDeliveryContext(REF, 14, ACTOR));
            assertTrue(adapter.checkFreshness(REF, "old-hash", ACTOR).fresh());
            assertEquals("TENANT-A", TenantHelper.getDynamic());

            when(assembler.assemble(REF, 17, ACTOR)).thenAnswer(call -> {
                assertEquals("TENANT-B", TenantHelper.getDynamic());
                throw new IllegalStateException("erp down");
            });
            assertThrows(IllegalStateException.class, () -> adapter.loadDeliveryContext(REF, 17, ACTOR));
            assertEquals("TENANT-A", TenantHelper.getDynamic());
        });
    }

    @Test
    void simulationsRefuseStaleOrUnauthorizedSnapshotWithoutCallingEngine() {
        withTenant(() -> {
            when(assembler.exists(REF, ACTOR)).thenReturn(true);
            when(freshness.check(REF, "old-hash", ACTOR))
                .thenReturn(new DecisionFreshnessResult(false, "new-hash", List.of("criticalSnapshotHash")));
            assertThrows(ServiceException.class, () -> adapter.simulateShortage(SNAPSHOT, 7, Map.of(), ACTOR));
            assertThrows(ServiceException.class, () -> adapter.simulateSchedules(SNAPSHOT, List.of("APS_EDD"), 7, ACTOR));
            verifyNoInteractions(shortage, aps);

            DecisionActor unauthorized = actor(Set.of("ai:decision:run"));
            assertThrows(ServiceException.class, () -> adapter.simulateShortage(SNAPSHOT, 7, Map.of(), unauthorized));
            assertThrows(ServiceException.class, () -> adapter.simulateSchedules(SNAPSHOT, List.of(), 7, unauthorized));
            verifyNoMoreInteractions(freshness);
        });
    }

    @Test
    void freshSimulationDelegatesOnlyAfterCheckingRootAndHashInCallerTenant() {
        withTenant(() -> {
            when(assembler.exists(REF, ACTOR)).thenAnswer(call -> {
                assertEquals("TENANT-B", TenantHelper.getDynamic());
                return true;
            });
            when(freshness.check(REF, "old-hash", ACTOR)).thenAnswer(call -> {
                assertEquals("TENANT-B", TenantHelper.getDynamic());
                return new DecisionFreshnessResult(true, "old-hash", List.of());
            });
            Map<String, Object> overrides = Map.of("purchaseArrivalOverrides", Map.of("101", "2026-09-25"));
            ShortageSimulationResult shortageResult = mock(ShortageSimulationResult.class);
            ApsScenarioCompareResult apsResult = mock(ApsScenarioCompareResult.class);
            when(shortage.simulate(SNAPSHOT, 7, overrides)).thenAnswer(call -> {
                assertEquals("TENANT-B", TenantHelper.getDynamic());
                return shortageResult;
            });
            when(aps.compare(SNAPSHOT, List.of("APS_CR"), 14)).thenAnswer(call -> {
                assertEquals("TENANT-B", TenantHelper.getDynamic());
                return apsResult;
            });
            assertSame(shortageResult, adapter.simulateShortage(SNAPSHOT, 7, overrides, ACTOR));
            assertSame(apsResult, adapter.simulateSchedules(SNAPSHOT, List.of("APS_CR"), 14, ACTOR));
            assertNull(TenantHelper.getDynamic());
        });
    }

    @Test
    void businessActionsRequireRootAccessAndTrustedActionPermissionBeforeDelegate() {
        DecisionActionProposal action = purchaseAction();
        withTenant(() -> {
            DecisionActor noPurchasePermission = actor(Set.of("ai:decision:run", "prd:work:query"));
            assertThrows(ServiceException.class, () -> adapter.executeBusinessAction(action, noPurchasePermission));
            assertThrows(ServiceException.class, () -> adapter.verifyBusinessAction(action, success(), noPurchasePermission));
            assertThrows(ServiceException.class, () -> adapter.compensateBusinessAction(action, success(), noPurchasePermission));
            verifyNoInteractions(assembler, executor, purchase);

            when(assembler.exists(REF, ACTOR)).thenReturn(false);
            assertThrows(ServiceException.class, () -> adapter.executeBusinessAction(action, ACTOR));
            verifyNoInteractions(executor, purchase);
        });
    }

    @Test
    void purchaseOperationsDelegateOnlyToCertifiedAdapterWithinTenant() {
        DecisionActionProposal action = purchaseAction();
        DecisionActionExecutionResult result = success();
        withTenant(() -> {
            TenantHelper.setDynamic("TENANT-A");
            when(assembler.exists(REF, ACTOR)).thenReturn(true);
            when(purchase.execute(action, ACTOR)).thenAnswer(call -> {
                assertEquals("TENANT-B", TenantHelper.getDynamic());
                return result;
            });
            when(purchase.verify(action, result, ACTOR)).thenAnswer(call -> {
                assertEquals("TENANT-B", TenantHelper.getDynamic());
                return result;
            });
            when(purchase.compensate(action, result, ACTOR)).thenAnswer(call -> {
                assertEquals("TENANT-B", TenantHelper.getDynamic());
                throw new IllegalStateException("rollback");
            });
            assertSame(result, adapter.executeBusinessAction(action, ACTOR));
            assertSame(result, adapter.verifyBusinessAction(action, result, ACTOR));
            assertThrows(IllegalStateException.class, () -> adapter.compensateBusinessAction(action, result, ACTOR));
            assertEquals("TENANT-A", TenantHelper.getDynamic());
            verifyNoInteractions(executor);
        });
    }

    @Test
    void scheduleOperationsStillUseOldExecutorAndNeverCertifiedPurchaseAdapter() {
        var action = new DecisionActionProposal(102L, DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT,
            "schedule", "reason", REF, Map.of("runId", "10"), DecisionRiskLevel.HIGH, true,
            "prd:schedule:run", "schedule-102", "schedulePlanDraft", 1, List.of());
        var result = new DecisionActionExecutionResult(true, false, "PRD_SCHEDULE_PLAN", 100L,
            "APS-100", Map.of("runId", "10"), null, null);
        withTenant(() -> {
            when(assembler.exists(REF, ACTOR)).thenReturn(true);
            when(executor.execute(action, ACTOR)).thenAnswer(call -> {
                assertEquals("TENANT-B", TenantHelper.getDynamic()); return result;
            });
            when(executor.verify(action, result, ACTOR)).thenReturn(result);
            when(executor.compensate(action, result, ACTOR)).thenReturn(result);
            assertSame(result, adapter.executeBusinessAction(action, ACTOR));
            assertSame(result, adapter.verifyBusinessAction(action, result, ACTOR));
            assertSame(result, adapter.compensateBusinessAction(action, result, ACTOR));
            assertNull(TenantHelper.getDynamic());
            verifyNoInteractions(purchase);
        });
    }

    @Test
    void realCertifiedAdapterKeepsOldPayloadRejectedAfterTargetTenantAndPermissionsCheck() {
        var writer = mock(com.nexus.erp.pu.service.PuCertifiedPurchaseWriteService.class);
        var realPurchase = new ErpCertifiedPurchaseDecisionAdapter(writer);
        var boundary = new ErpIndustrialDecisionDataAdapter(assembler, shortage, aps, freshness, executor, realPurchase);
        var actor = new DecisionActor("000001", 7L, "tester", PERMISSIONS);
        withTenant(() -> {
            TenantHelper.setDynamic("000002");
            when(assembler.exists(REF, actor)).thenAnswer(call -> {
                assertEquals("000001", TenantHelper.getDynamic()); return true;
            });
            var old = purchaseAction();
            assertThrows(ServiceException.class, () -> boundary.executeBusinessAction(old, actor));
            assertThrows(ServiceException.class, () -> boundary.verifyBusinessAction(old, success(), actor));
            assertThrows(ServiceException.class, () -> boundary.compensateBusinessAction(old, success(), actor));
            assertEquals("000002", TenantHelper.getDynamic());
            verifyNoInteractions(executor, writer);
        });
    }

    @Test
    void facadeDoesNotIntroduceOuterTransactionAroundPurchaseWriter() {
        assertNull(ErpIndustrialDecisionDataAdapter.class.getAnnotation(org.springframework.transaction.annotation.Transactional.class));
        for (var method : ErpIndustrialDecisionDataAdapter.class.getDeclaredMethods()) {
            assertNull(method.getAnnotation(org.springframework.transaction.annotation.Transactional.class));
        }
    }

    private static DecisionActor actor(Set<String> permissions) {
        return new DecisionActor("TENANT-B", 7L, "tester", permissions);
    }

    private static DecisionActionProposal purchaseAction() {
        return new DecisionActionProposal(101L, DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT,
            "draft", "reason", REF, Map.of("runId", "10"), DecisionRiskLevel.HIGH, true,
            "pu:requisition:add", "purchase-101", "purchaseRequisitionDraft", 1, List.of());
    }

    private static DecisionActionExecutionResult success() {
        return new DecisionActionExecutionResult(true, false, "PU_PURCHASE_REQUISITION", 99L,
            "PR-99", Map.of("runId", "10"), null, null);
    }

    private static void withTenant(Runnable assertions) {
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class);
             MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            spring.when(() -> SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(LoginHelper::isLogin).thenReturn(false);
            try {
                assertions.run();
            } finally {
                TenantHelper.clearDynamic();
            }
        }
    }
}
