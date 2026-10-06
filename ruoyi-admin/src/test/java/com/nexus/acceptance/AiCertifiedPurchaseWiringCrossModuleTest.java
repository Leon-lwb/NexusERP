package com.nexus.acceptance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.service.decision.DecisionActionProposalService;
import com.nexus.ai.service.decision.DecisionPurchaseContractGuard;
import com.nexus.common.ai.decision.dto.DecisionActionProposal;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.dto.DecisionPurchaseContractV2;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.ai.decision.enums.DecisionRiskLevel;
import com.nexus.common.ai.decision.util.DecisionPurchaseContractCodec;
import com.nexus.common.ai.domain.entity.AiDecisionAction;
import com.nexus.common.ai.domain.entity.AiDecisionRun;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.ai.adapter.ErpCertifiedPurchaseDecisionAdapter;
import com.nexus.erp.pu.domain.bo.PuCertifiedPurchaseCommand;
import com.nexus.erp.pu.domain.vo.PuCertifiedPurchaseResult;
import com.nexus.erp.pu.service.PuCertifiedPurchaseWriteService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 公共合同、真实 AI 提案/结构门禁与真实 ERP 适配器的跨模块接线回归。
 * 仅统一写入口存储和租户上下文使用替身；不代表完整分析、审批、物理事务或运行实例已验收。
 */
class AiCertifiedPurchaseWiringCrossModuleTest {
    private static final Long RUN = 801L;
    private static final Long ROOT = 901L;
    private static final Long ACTION = 1001L;
    private static final LocalDate DATE = LocalDate.of(2026, 10, 13);
    private static final DecisionActor ACTOR = new DecisionActor("000007", 7L, "采购负责人",
        Set.of("ai:decision:execute", "pu:requisition:add"));
    private static final DecisionBusinessRef TARGET = new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER,
        ROOT, "SCGD202610060010");

    @Test
    void differentMaterialContractsCrossModulesKeepSeparateStableKeys() throws Exception {
        var actions = new DecisionActionProposalService().propose(input(),
            List.of(contract(101L, 401L, "6"), contract(102L, 402L, "12")));
        assertEquals(2, actions.size(), "两个物料必须保留两个独立冻结采购动作");
        assertNotEquals(actions.get(0).idempotencyKey(), actions.get(1).idempotencyKey(), "不同物料不能共享永久请求键");
        for (int index = 0; index < actions.size(); index++) {
            var proposed = actions.get(index);
            assertEquals(79, proposed.idempotencyKey().length(), "新键应完整编码身份且满足既有一百二十八字符上限");
            var persisted = persisted(proposed, ACTION + index);
            assertDoesNotThrow(() -> DecisionPurchaseContractGuard.requireExecutable(run(2), persisted),
                "真实提案经持久化结构映射后必须能通过新合同门禁");
            assertEquals(proposed.payload(), new ObjectMapper().readValue(persisted.getPayloadJson(), Map.class),
                "字符串标识与数值不能在模块间往返漂移");
        }
    }

    @Test
    void proposalContractReachesExistingWriterWithRootAndFullFingerprint() throws Exception {
        var action = action();
        var writer = mock(PuCertifiedPurchaseWriteService.class);
        when(writer.createForDecision(any(), eq(ACTOR), eq(RUN), eq(ACTION), anyList(), any()))
            .thenReturn(receipt(false));
        var adapter = new ErpCertifiedPurchaseDecisionAdapter(writer);
        try (MockedStatic<TenantHelper> tenant = tenant()) {
            assertDoesNotThrow(() -> DecisionPurchaseContractGuard.requireExecutable(run(2), persisted(action, ACTION)),
                "执行入口之前必须先核已持久化新版结构");
            var result = adapter.execute(action, ACTOR);
            assertTrue(result.success(), "已有统一 writer 的真实返回应成为结构化执行结果");
            assertEquals(501L, result.resultBizId(), "只能返回 writer 实际提交的业务引用");
            var command = ArgumentCaptor.forClass(PuCertifiedPurchaseCommand.class);
            var frozen = ArgumentCaptor.forClass(PuCertifiedPurchaseWriteService.FrozenContract.class);
            verify(writer).createForDecision(command.capture(), eq(ACTOR), eq(RUN), eq(ACTION), anyList(), frozen.capture());
            assertEquals(action.idempotencyKey(), command.getValue().requestKey(), "请求键必须与唯一公共助手一致");
            assertEquals(ROOT, frozen.getValue().rootWorkOrderId(), "实际写事务必须收到被冻结的工单根");
            assertEquals(DecisionPurchaseContractCodec.payloadFingerprint(action, ACTOR), frozen.getValue().payloadFingerprint(),
                "完整摘要必须绑定执行人、动作和冻结载荷");
            assertEquals(9, frozen.getValue().metadata().size(), "九项计量及供应合同元数据不得缺失");
            verify(writer, never()).recoverForDecision(any(), any(), any(), any(), anyList(), any());
        }
    }

    @Test
    void committedReceiptRecoversAndVerifiesWithoutCreatingAnotherDocument() {
        var action = action();
        var writer = mock(PuCertifiedPurchaseWriteService.class);
        when(writer.recoverForDecision(any(), eq(ACTOR), eq(RUN), eq(ACTION), anyList(), any()))
            .thenReturn(receipt(true));
        var adapter = new ErpCertifiedPurchaseDecisionAdapter(writer);
        try (MockedStatic<TenantHelper> tenant = tenant()) {
            var recovered = adapter.recover(action, ACTOR);
            assertNotNull(recovered, "明确已提交回执不得丢失");
            assertTrue(recovered.success(), "恢复必须返回已有草稿而非创建新单");
            assertEquals(Boolean.TRUE, recovered.result().get("replayed"), "恢复观察字段应准确标为重放");
            assertTrue(adapter.verify(action, recovered, ACTOR).success(), "真实恢复结果必须能通过现有信封复核");
            verify(writer, times(2)).recoverForDecision(any(), eq(ACTOR), eq(RUN), eq(ACTION), anyList(), any());
            verify(writer, never()).createForDecision(any(), any(), any(), any(), anyList(), any());
        }
    }

    @Test
    void definiteRecoveryMissCannotBecomeCreation() {
        var writer = mock(PuCertifiedPurchaseWriteService.class);
        var adapter = new ErpCertifiedPurchaseDecisionAdapter(writer);
        try (MockedStatic<TenantHelper> tenant = tenant()) {
            assertNull(adapter.recover(action(), ACTOR), "确定缺永久提交只能表示未找到，不能转到新建");
            verify(writer).recoverForDecision(any(), eq(ACTOR), eq(RUN), eq(ACTION), anyList(), any());
            verify(writer, never()).createForDecision(any(), any(), any(), any(), anyList(), any());
        }
    }

    @Test
    void transitionedDocumentKeepsOriginalReferenceAndCannotClaimNewDraft() {
        var writer = mock(PuCertifiedPurchaseWriteService.class);
        var draft = receipt(true);
        when(writer.recoverForDecision(any(), any(), any(), any(), anyList(), any()))
            .thenReturn(new PuCertifiedPurchaseResult(draft.requisitionId(), draft.requisitionNo(), "ORDERED", 1,
                true, draft.sourceFingerprint(), draft.lines()));
        try (MockedStatic<TenantHelper> tenant = tenant()) {
            var recovered = new ErpCertifiedPurchaseDecisionAdapter(writer).recover(action(), ACTOR);
            assertNotNull(recovered, "状态变更不能抹掉真实历史单据引用");
            assertFalse(recovered.success(), "已转单不能冒充当前可恢复草稿");
            assertEquals(501L, recovered.resultBizId(), "冲突仍须保留原业务引用");
            verify(writer, never()).createForDecision(any(), any(), any(), any(), anyList(), any());
        }
    }

    @Test
    void completePayloadCannotUpgradeLegacyRunOrAction() throws Exception {
        var row = persisted(action(), ACTION);
        assertThrows(ServiceException.class, () -> DecisionPurchaseContractGuard.requireExecutable(run(null), row),
            "完整载荷不能给旧运行补新版结论");
        row.setPurchasePayloadVersion(null);
        assertThrows(ServiceException.class, () -> DecisionPurchaseContractGuard.requireExecutable(run(2), row),
            "新版运行不能认领缺动作版本的旧采购");
    }

    private DecisionActionProposal action() {
        var proposed = new DecisionActionProposalService().propose(input(), List.of(contract(101L, 401L, "6"))).get(0);
        return new DecisionActionProposal(ACTION, proposed.actionType(), proposed.title(), proposed.displayReason(),
            proposed.target(), proposed.payload(), proposed.riskLevel(), proposed.approvalRequired(), proposed.permissionCode(),
            proposed.idempotencyKey(), proposed.executorCode(), proposed.sequence(), proposed.dependencyIds());
    }

    private DecisionActionProposalService.ProposalInput input() {
        return new DecisionActionProposalService.ProposalInput(RUN, ACTOR.tenantId(), TARGET, DecisionRiskLevel.MEDIUM,
            Set.of("MATERIAL_SHORTAGE"), null, false, List.of(), null);
    }

    private DecisionPurchaseContractV2 contract(Long material, Long detail, String quantity) {
        return new DecisionPurchaseContractV2(material, 201L, LocalDate.of(2026, 11, 4), "M-1", "V-1", 301L,
            "T-1", 26L, "Q-1", 26L, "Q-1", "Q-1", List.of(detail), List.of(new DecisionPurchaseContractV2.Line(
                detail, 26L, new BigDecimal(quantity), 26L, new BigDecimal(quantity), DATE)), "a".repeat(64));
    }

    private PuCertifiedPurchaseResult receipt(boolean replayed) {
        return new PuCertifiedPurchaseResult(501L, "CGSQ202610060010", "DRAFT", 0, replayed, "a".repeat(64),
            List.of(new PuCertifiedPurchaseResult.Line(601L, 401L, 101L, 26L, new BigDecimal("6.0000"), DATE)));
    }

    private AiDecisionRun run(Integer version) {
        var run = new AiDecisionRun();
        run.setId(RUN); run.setTenantId(ACTOR.tenantId()); run.setDecisionContractVersion(version);
        run.setBizType(TARGET.type().name()); run.setBizId(TARGET.id()); run.setBizCode(TARGET.code());
        return run;
    }

    private AiDecisionAction persisted(DecisionActionProposal proposed, Long id) throws Exception {
        var row = new AiDecisionAction();
        row.setId(id); row.setRunId(RUN); row.setTenantId(ACTOR.tenantId()); row.setPurchasePayloadVersion(2);
        row.setActionType(proposed.actionType().name()); row.setExecutorCode(proposed.executorCode());
        row.setPermissionCode(proposed.permissionCode()); row.setApprovalRequired("Y");
        row.setTargetBizType(TARGET.type().name()); row.setTargetBizId(TARGET.id()); row.setTargetBizCode(TARGET.code());
        row.setPayloadJson(new ObjectMapper().writeValueAsString(proposed.payload()));
        row.setIdempotencyKey(proposed.idempotencyKey());
        return row;
    }

    private MockedStatic<TenantHelper> tenant() {
        var tenant = mockStatic(TenantHelper.class);
        tenant.when(TenantHelper::isEnable).thenReturn(true);
        tenant.when(TenantHelper::getTenantId).thenReturn(ACTOR.tenantId());
        return tenant;
    }
}
