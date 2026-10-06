package com.nexus.ai.service.decision;

import cn.hutool.extra.spring.SpringUtil;
import com.nexus.common.ai.decision.dto.*;
import com.nexus.common.ai.decision.enums.*;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import com.nexus.common.ai.decision.util.DecisionPurchaseContractCodec;
import com.nexus.common.ai.domain.entity.AiDecisionRun;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DecisionPurchaseProposalServiceTest {
    private final DecisionPersistenceService store = mock(DecisionPersistenceService.class);
    private final IndustrialDecisionDataPort port = mock(IndustrialDecisionDataPort.class);
    private final DecisionActionProposalService proposals = new DecisionActionProposalService();
    private static final DecisionBusinessRef REF = new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 11L, "WO-11");
    private static final DecisionActor ACTOR = new DecisionActor("000001", 7L, "分析员", Set.of("ai:decision:run", "prd:work:query"));

    @Test void freezeForRunUsesStoredActorSnapshotAndWindow() {
        withTenant(() -> {
            var snapshot = ready(); var contract = contract(100L, 200L);
            when(port.freezePurchaseContracts(snapshot, 30, ACTOR)).thenReturn(List.of(contract));
            assertEquals(List.of(contract), freeze(99L, ACTOR), "冻结入口应只从持久化范围取得合同");
            verify(port).freezePurchaseContracts(snapshot, 30, ACTOR);
            var forged = new DecisionActor("000001", 99L, "伪造用户", Set.of("*:*:*"));
            assertThrows(ServiceException.class, () -> freeze(99L, forged), "外部或流程身份不能替换创建人");
            verify(port, times(1)).freezePurchaseContracts(any(), anyInt(), any());
            return null;
        });
    }

    @Test void contractOutsideFrozenRequirementsOrWindowCannotBeProposed() {
        withTenant(() -> {
            var snapshot = ready();
            when(port.freezePurchaseContracts(snapshot, 30, ACTOR)).thenReturn(List.of(contract(101L, 200L)));
            assertThrows(ServiceException.class, () -> propose(99L, input(REF, Set.of("MATERIAL_SHORTAGE"))),
                "合同物料和逐来源标识必须属于冻结工单快照");
            var source = contract(100L, 200L);
            var extended = new DecisionPurchaseContractV2(source.materialId(), source.vendorId(), source.windowEnd().plusDays(1),
                source.materialVersion(), source.vendorVersion(), source.supplierTermsId(), source.supplierTermsVersion(),
                source.baseUomId(), source.baseUomVersion(), source.orderUomId(), source.orderUomVersion(), source.conversionVersion(),
                source.requirementDetailIds(), source.frozenLines(), source.expectedSourceFingerprint());
            when(port.freezePurchaseContracts(snapshot, 30, ACTOR)).thenReturn(List.of(extended));
            assertThrows(ServiceException.class, () -> propose(99L, input(REF, Set.of("MATERIAL_SHORTAGE"))),
                "不能以端口返回值延长持久化分析窗口");
            return null;
        });
    }

    @Test void domainFreezeFailureKeepsSafeChineseReasonWithoutCredentials() {
        withTenant(() -> {
            var snapshot = ready();
            when(port.freezePurchaseContracts(snapshot, 30, ACTOR))
                .thenThrow(new ServiceException("供应商来源不唯一，Bearer SECRET-FOR-TEST"));
            var error = assertThrows(ServiceException.class, () -> propose(99L, input(REF, Set.of("MATERIAL_SHORTAGE"))),
                "来源认证失败必须关闭采购并提供安全业务原因");
            assertTrue(error.getMessage().contains("供应商来源不唯一"), "保留可行动的中文拒绝原因");
            assertFalse(error.getMessage().contains("SECRET-FOR-TEST"), "任何凭据文本都不得进入公开结果");
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private List<DecisionPurchaseContractV2> freeze(Long runId, DecisionActor actor) {
        try {
            var type = Class.forName("com.nexus.ai.service.decision.DecisionPurchaseProposalService");
            var service = type.getConstructor(DecisionPersistenceService.class, IndustrialDecisionDataPort.class,
                DecisionActionProposalService.class).newInstance(store, port, proposals);
            return (List<DecisionPurchaseContractV2>) type.getMethod("freezeForRun", Long.class, DecisionActor.class)
                .invoke(service, runId, actor);
        } catch (java.lang.reflect.InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException cause) throw cause;
            throw new AssertionError("冻结入口出现非预期异常", error.getCause());
        } catch (ReflectiveOperationException error) { throw new AssertionError("缺少约定的冻结运行接口", error); }
    }

    @Test void newAnalysisUsesFrozenPurchasePortRatherThanGap() {
        withTenant(() -> {
            var snapshot = ready();
            var frozen = contract(100L, 200L);
            when(port.freezePurchaseContracts(snapshot, 30, ACTOR)).thenReturn(List.of(frozen));
            var input = input(REF, Set.of("MATERIAL_SHORTAGE"));
            var result = propose(99L, input);
            verify(port).freezePurchaseContracts(snapshot, 30, ACTOR);
            var purchase = result.stream().filter(a -> a.actionType() == DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT)
                .findFirst().orElseThrow();
            assertEquals(DecisionPurchaseContractCodec.toPayload(99L, frozen), purchase.payload(),
                "采购数量、供应商及单位必须原样来自冻结端口，不能取伪造的旧缺口或流程变量");
            assertEquals("200", purchase.payload().get("vendorId"), "供应商必须来自真实冻结合同");
            assertEquals("3", ((Map<?, ?>) ((List<?>) purchase.payload().get("frozenLines")).get(0)).get("requestedOrderQty"),
                "模型缺口不是采购额度");
            assertFalse(purchase.payload().containsKey("verified"), "不得默认来源已核验旗号");
            verify(store).trustedActor(99L, "000001");
            verify(store).requireHorizonDays(any());
            return null;
        });
    }

    @Test void emptyPositiveAllocationNeverInventsPurchaseButKeepsScheduling() {
        withTenant(() -> {
            var snapshot = ready();
            when(port.freezePurchaseContracts(snapshot, 30, ACTOR)).thenReturn(List.of());
            var result = propose(99L, input(REF, Set.of("MATERIAL_SHORTAGE", "SCHEDULE_DELAY")));
            assertFalse(result.stream().anyMatch(a -> a.actionType() == DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT),
                "没有正额度合同时不得用模型缺口补采购");
            var schedule = result.stream().filter(a -> a.actionType() == DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT)
                .findFirst().orElseThrow();
            assertEquals(30, schedule.payload().get("horizonDays"), "排程时段仍来自持久化运行");
            assertEquals(List.of("11"), schedule.payload().get("workOrderIds"), "不能使用流程伪造工单范围");
            assertEquals("2026-10-01", schedule.payload().get("startDate"), "排程日期保留冻结快照边界");
            return null;
        });
    }

    @Test void missingFrozenPortFailsClosedAndLegacyRunIsNeverUpgraded() {
        withTenant(() -> {
            var snapshot = ready();
            when(port.freezePurchaseContracts(snapshot, 30, ACTOR)).thenThrow(new UnsupportedOperationException("冻结端口未实现"));
            assertThrows(ServiceException.class, () -> propose(99L, input(REF, Set.of("MATERIAL_SHORTAGE"))),
                "未实现冻结端口必须安全失败而不能回退旧缺口");
            var legacy = running(); legacy.setDecisionContractVersion(null);
            when(store.requireRun(99L, "000001")).thenReturn(legacy);
            assertThrows(ServiceException.class, () -> propose(99L, input(REF, Set.of("MATERIAL_SHORTAGE"))),
                "旧空版本运行不得进入新版采购路径");
            assertNull(legacy.getDecisionContractVersion(), "旧版本永不回填");
            verify(store, never()).saveActions(anyLong(), any(), any());
            return null;
        });
    }

    @Test void forgedTargetOrMismatchedPersistedSnapshotCannotFreezeContracts() {
        withTenant(() -> {
            ready();
            var fake = new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 12L, "WO-12");
            assertThrows(ServiceException.class, () -> propose(99L, input(fake, Set.of("MATERIAL_SHORTAGE"))),
                "流程目标不能覆盖已持久化业务根");
            var row = running(); row.setInputSnapshotHash("other");
            when(store.requireRun(99L, "000001")).thenReturn(row);
            assertThrows(ServiceException.class, () -> propose(99L, input(REF, Set.of("MATERIAL_SHORTAGE"))),
                "存储快照指纹不一致时不得冻结采购");
            verifyNoInteractions(port);
            return null;
        });
    }

    @Test void nonWorkOrderRootKeepsNonPurchaseActionsWithoutCallingPurchasePort() {
        withTenant(() -> {
            var ref = new DecisionBusinessRef(DecisionBusinessType.SCHEDULE_PLAN, 12L, "APS-12");
            var row = running(); row.setBizType(ref.type().name()); row.setBizId(ref.id()); row.setBizCode(ref.code());
            when(store.requireRun(99L, "000001")).thenReturn(row);
            when(store.requireSnapshot(99L, "000001")).thenReturn(workSnapshot(ref));
            when(store.requireHorizonDays(row)).thenReturn(30);
            when(store.trustedActor(99L, "000001")).thenReturn(ACTOR);
            var result = propose(99L, input(ref, Set.of("MATERIAL_SHORTAGE", "SCHEDULE_DELAY")));
            assertTrue(result.stream().anyMatch(a -> a.actionType() == DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT),
                "首包以外业务根仍保留排程行为");
            assertFalse(result.stream().anyMatch(a -> a.actionType() == DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT),
                "非工单业务根不能生成采购");
            verifyNoInteractions(port);
            return null;
        });
    }

    private DeliveryContextSnapshot ready() {
        var row = running(); var snapshot = workSnapshot(REF);
        when(store.requireRun(99L, "000001")).thenReturn(row);
        when(store.requireSnapshot(99L, "000001")).thenReturn(snapshot);
        when(store.requireHorizonDays(row)).thenReturn(30);
        when(store.trustedActor(99L, "000001")).thenReturn(ACTOR);
        return snapshot;
    }

    private AiDecisionRun running() {
        var run = new AiDecisionRun(); run.setId(99L); run.setTenantId("000001");
        run.setStatus("RUNNING"); run.setVersion(0); run.setDecisionContractVersion(2);
        run.setBizType(REF.type().name()); run.setBizId(REF.id()); run.setBizCode(REF.code());
        run.setInputSnapshotHash("hash-99"); return run;
    }

    private DecisionActionProposalService.ProposalInput input(DecisionBusinessRef ref, Set<String> causes) {
        return new DecisionActionProposalService.ProposalInput(99L, "000001", ref, DecisionRiskLevel.HIGH,
            causes, "APS_CR", false, List.of(Map.of("materialId", "999", "gap", "99999", "vendorId", "999", "unit", "伪造")),
            7L, List.of("999"), LocalDate.of(2099, 1, 1), 90, 999L);
    }

    static DecisionPurchaseContractV2 contract(Long material, Long vendor) {
        return new DecisionPurchaseContractV2(material, vendor, LocalDate.of(2026, 10, 30), "material-v1", "vendor-v1",
            300L, "terms-v1", 1L, "uom-v1", 1L, "uom-v1", "uom-v1", List.of(material + 1000L),
            List.of(new DecisionPurchaseContractV2.Line(material + 1000L, 1L, new BigDecimal("3"), 1L,
                new BigDecimal("3"), LocalDate.of(2026, 10, 3))), "a".repeat(64));
    }

    static DeliveryContextSnapshot workSnapshot(DecisionBusinessRef ref) {
        return new DeliveryContextSnapshot(ref, List.of(),
            List.of(new DeliveryContextSnapshot.WorkOrderFact(11L, "WO-11", 100L, BigDecimal.TEN,
                LocalDate.of(2026, 10, 3), 1L, 9L)), Map.of(),
            List.of(new DeliveryContextSnapshot.RequirementFact(1100L, 100L, "MAT-100", "物料",
                LocalDate.of(2026, 10, 3), BigDecimal.TEN, 11L, "WO-11", 1L)), List.of(), List.of(), Map.of(),
            List.of(), Map.of(), List.of(), "hash-99", LocalDateTime.of(2026, 10, 1, 10, 0), List.of());
    }

    @SuppressWarnings("unchecked")
    private List<DecisionActionProposal> propose(Long runId, DecisionActionProposalService.ProposalInput input) {
        try {
            var type = Class.forName("com.nexus.ai.service.decision.DecisionPurchaseProposalService");
            var service = type.getConstructor(DecisionPersistenceService.class, IndustrialDecisionDataPort.class,
                DecisionActionProposalService.class).newInstance(store, port, proposals);
            return (List<DecisionActionProposal>) type.getMethod("propose", Long.class,
                DecisionActionProposalService.ProposalInput.class).invoke(service, runId, input);
        } catch (java.lang.reflect.InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException cause) throw cause;
            throw new AssertionError("采购提案服务出现非预期异常", error.getCause());
        } catch (ReflectiveOperationException error) { throw new AssertionError("缺少服务端采购提案接线接口", error); }
    }

    private <T> T withTenant(Supplier<T> action) {
        try (var spring = mockStatic(SpringUtil.class); var login = mockStatic(LoginHelper.class)) {
            spring.when(() -> SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(LoginHelper::isLogin).thenReturn(false);
            try { return TenantHelper.dynamic("000001", action); }
            finally { TenantHelper.clearDynamic(); }
        }
    }
}
