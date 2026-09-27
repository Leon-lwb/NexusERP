package com.nexus.ai.service.decision;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.ai.decision.dto.*;
import com.nexus.common.ai.decision.enums.*;
import com.nexus.common.ai.domain.entity.*;
import com.nexus.common.ai.mapper.*;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.mockito.MockedStatic;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import static com.nexus.ai.service.decision.DecisionTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class DecisionPersistenceServiceTest {
    private final AiDecisionRunMapper runs = mock(AiDecisionRunMapper.class);
    private final AiDecisionEvidenceMapper evidence = mock(AiDecisionEvidenceMapper.class);
    private final AiDecisionActionMapper actions = mock(AiDecisionActionMapper.class);
    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new com.nexus.common.json.config.JacksonConfig().registerJavaTimeModule())
        .registerModule(new com.nexus.common.web.config.JacksonConfig().bigNumberModule());
    private final DecisionPersistenceService store = new DecisionPersistenceService(
        runs, evidence, actions, new DecisionRunStateMachine(), new DecisionEvidenceService(), mapper);
    @BeforeAll static void initMyBatisMetadata() {
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(
            new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, AiDecisionRun.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, AiDecisionEvidence.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, AiDecisionAction.class);
    }

    private static final DecisionBusinessRef REF = new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 9007199254740993L, "WO-11");
    private static final DecisionActor ACTOR = new DecisionActor("TENANT-A", 7L, "tester", Set.of("ai:decision:run"));

    private AiDecisionRun current(DecisionRunStatus status, int version) {
        AiDecisionRun row = new AiDecisionRun();
        row.setId(99L); row.setTenantId("TENANT-A"); row.setSceneCode("DELIVERY_RISK");
        row.setBizType(REF.type().name()); row.setBizId(REF.id()); row.setBizCode(REF.code());
        row.setStatus(status.name()); row.setVersion(version);
        return row;
    }

    @Test void unlinkedExecutionFlowIdIsAuditedWithTenantStatusAndVersionCas() {
        withTenant(() -> {
            when(runs.selectOne(any())).thenReturn(current(DecisionRunStatus.EXECUTING, 3));
            when(runs.update(isNull(), any())).thenReturn(1);
            store.recordUnlinkedExecutionFlow(99L, 77L);
            var update = org.mockito.ArgumentCaptor.forClass(Wrapper.class);
            verify(runs).update(isNull(), update.capture());
            String bound = params(update.getValue()).values().toString();
            assertTrue(bound.contains("TENANT-A"));
            assertTrue(bound.contains("EXECUTING"));
            assertTrue(bound.contains("ORPHAN_EXECUTION_FLOW"));
            assertTrue(bound.contains("77"));
            assertTrue(bound.contains("3"));
            return null;
        });
    }

    @Test void compensationResultIsVersionedAndTenantScoped() {
        withTenant(() -> {
            when(runs.selectOne(any())).thenReturn(current(DecisionRunStatus.ROLLING_BACK, 5));
            var row = new AiDecisionAction();
            row.setId(501L); row.setRunId(99L); row.setTenantId("TENANT-A");
            row.setVersion(7); row.setApprovalStatus("APPROVED");
            row.setExecutionStatus("COMPENSATING");
            when(actions.selectOne(any())).thenReturn(row);
            when(actions.update(isNull(), any())).thenReturn(1);
            var result = new DecisionActionExecutionResult(true, false, "PRD_SCHEDULE_PLAN", 600L,
                "PLAN-600", Map.of("compensated", true), null, null);
            store.saveCompensationResult(501L, result);
            var update = org.mockito.ArgumentCaptor.forClass(Wrapper.class);
            verify(actions).update(isNull(), update.capture());
            assertTrue(params(update.getValue()).containsValue("TENANT-A"));
            assertTrue(params(update.getValue()).containsValue(7));
            assertTrue(params(update.getValue()).containsValue("COMPENSATED"));
            return null;
        });
    }

    @Test void actionResultJsonNeverPersistsUnsanitizedProviderSecrets() {
        withTenant(() -> {
            when(runs.selectOne(any())).thenReturn(current(DecisionRunStatus.EXECUTING, 2));
            AiDecisionAction action = new AiDecisionAction();
            action.setId(501L); action.setRunId(99L); action.setTenantId("TENANT-A");
            action.setVersion(3); action.setApprovalStatus("APPROVED");
            action.setExecutionStatus("EXECUTING");
            when(actions.selectOne(any())).thenReturn(action);
            when(actions.update(isNull(), any())).thenReturn(1);
            var result = new DecisionActionExecutionResult(false, false, null, null, null,
                Map.of(), "UPSTREAM", "Bearer secret-provider-token");
            store.saveActionResult(501L, result, 1);
            var update = org.mockito.ArgumentCaptor.forClass(Wrapper.class);
            verify(actions).update(isNull(), update.capture());
            assertFalse(params(update.getValue()).values().toString().contains("secret-provider-token"));
            return null;
        });
    }

    @Test void wildcardAdminApprovalScopeCanBeRestoredForExecution() {
        withTenant(() -> {
            AiDecisionRun run = current(DecisionRunStatus.APPROVED, 6);
            run.setApprovalContextJson("{\"userId\":7,\"userName\":\"admin\",\"permissions\":[\"*:*:*\"]}");
            when(runs.selectOne(any())).thenReturn(run);
            assertEquals(Set.of("*:*:*"), store.trustedExecutionActor(99L, "TENANT-A").permissions());
            return null;
        });
    }

    @Test void executionIdentityIsBoundToApproverAndCannotUseFlowInputUser() {
        withTenant(() -> {
            var run = current(DecisionRunStatus.WAITING_APPROVAL, 4);
            when(runs.selectOne(any())).thenReturn(run);
            when(runs.update(isNull(), any())).thenReturn(1);
            store.bindExecutionActor(99L, ACTOR);
            var update = org.mockito.ArgumentCaptor.forClass(Wrapper.class);
            verify(runs).update(isNull(), update.capture());
            assertTrue(params(update.getValue()).containsValue("TENANT-A"));
            assertTrue(params(update.getValue()).containsValue(4));
            assertTrue(params(update.getValue()).values().stream().filter(String.class::isInstance)
                .map(String.class::cast).anyMatch(json -> json.contains("\"userId\":7")));
            return null;
        });
    }

    @Test void terminalOutcomeUsesTenantStatusAndVersionCompareAndSet() {
        withTenant(() -> {
            AiDecisionRun row = current(DecisionRunStatus.SUCCEEDED, 4);
            when(runs.selectOne(any())).thenReturn(row);
            when(runs.update(isNull(), any())).thenReturn(1);
            var outcome = new DecisionOutcome(null, null, null, Map.of(), List.of(), List.of(),
                LocalDateTime.of(2026, 9, 26, 10, 0), "PROJECTED_ONLY");
            store.saveOutcome(99L, outcome);
            var update = org.mockito.ArgumentCaptor.forClass(Wrapper.class);
            verify(runs).update(isNull(), update.capture());
            var values = params(update.getValue()).values();
            assertTrue(values.contains("TENANT-A"));
            assertTrue(values.contains("SUCCEEDED"));
            assertTrue(values.contains(4));
            return null;
        });
    }

    @Test void approvedActionTransitionUsesTenantRunVersionAndExpectedStatus() {
        withTenant(() -> {
            when(runs.selectOne(any())).thenReturn(current(DecisionRunStatus.APPROVED, 2));
            var action = new AiDecisionAction();
            action.setId(501L); action.setRunId(99L); action.setTenantId("TENANT-A");
            action.setVersion(4); action.setApprovalStatus("APPROVED");
            action.setExecutionStatus("NOT_STARTED");
            when(actions.selectOne(any())).thenReturn(action);
            when(actions.update(isNull(), any())).thenReturn(1);
            store.transitionAction(501L, "APPROVED", "NOT_STARTED", "EXECUTING");
            var update = org.mockito.ArgumentCaptor.forClass(Wrapper.class);
            verify(actions).update(isNull(), update.capture());
            var values = params(update.getValue()).values();
            assertTrue(values.contains("TENANT-A"));
            assertTrue(values.contains(4));
            assertTrue(values.contains("NOT_STARTED"));
            assertTrue(values.contains("EXECUTING"));
            return null;
        });
    }

    @Test void trustedActorIsRebuiltFromImmutableRunScopeNotClientInput() {
        withTenant(() -> {
            AiDecisionRun row = current(DecisionRunStatus.RUNNING, 1);
            row.setCreateBy(7L);
            row.setScoreConfigJson("{\"horizonDays\":30,\"userName\":\"tester\",\"permissions\":[\"ai:decision:run\"]}");
            when(runs.selectOne(any())).thenReturn(row);
            DecisionActor trusted = store.trustedActor(99L, "TENANT-A");
            assertEquals(7L, trusted.userId());
            assertEquals(Set.of("ai:decision:run"), trusted.permissions());
            return null;
        });
    }

    @Test void noTenantContextIsRejectedBeforeAnyMapperAccess() {
        assertThrows(ServiceException.class, () -> store.transition(99L, DecisionRunStatus.CREATED, DecisionRunStatus.RUNNING));
        assertThrows(ServiceException.class, () -> store.requireRun(99L, "TENANT-A"));
        assertThrows(ServiceException.class, () -> store.findActiveRun(REF, " "));
        verifyNoInteractions(runs, evidence, actions);
    }

    @Test void activeLookupIncludesTenantAndExactBusinessRef() {
        withTenant(() -> {
            when(runs.selectOne(any())).thenReturn(current(DecisionRunStatus.RUNNING, 0));
            assertNotNull(store.findActiveRun(REF, "TENANT-A"));
            var query = org.mockito.ArgumentCaptor.forClass(Wrapper.class);
            verify(runs).selectOne(query.capture());
            assertTrue(params(query.getValue()).containsValue("TENANT-A"));
            assertTrue(params(query.getValue()).containsValue(REF.id()));
            assertEquals("TENANT-A", TenantHelper.getDynamic());
            return null;
        });
    }

    @Test void createdToRunningUsesAtomicStatusVersionAndTenantCas() {
        withTenant(() -> {
            when(runs.selectOne(any())).thenReturn(current(DecisionRunStatus.CREATED, 2));
            when(runs.update(isNull(), any())).thenReturn(1);
            store.transition(99L, DecisionRunStatus.CREATED, DecisionRunStatus.RUNNING);
            var update = org.mockito.ArgumentCaptor.forClass(Wrapper.class);
            verify(runs).update(isNull(), update.capture());
            var values = params(update.getValue()).values();
            assertTrue(values.contains("TENANT-A"));
            assertTrue(values.contains("CREATED"));
            assertTrue(values.contains(2));
            assertTrue(values.contains("RUNNING"));
            return null;
        });
    }

    @Test void staleConcurrentTransitionAndIllegalTransitionFailClosed() {
        withTenant(() -> {
            when(runs.selectOne(any())).thenReturn(current(DecisionRunStatus.CREATED, 0));
            when(runs.update(isNull(), any())).thenReturn(0);
            assertThrows(ServiceException.class, () -> store.transition(99L, DecisionRunStatus.CREATED, DecisionRunStatus.RUNNING));
            assertThrows(ServiceException.class, () -> store.transition(99L, DecisionRunStatus.CREATED, DecisionRunStatus.SUCCEEDED));
            verify(runs, times(1)).update(isNull(), any());
            return null;
        });
    }

    @Test void fastFlowCompletionStillAllowsFlowInstanceAttachment() {
        withTenant(() -> {
            when(runs.selectOne(any())).thenReturn(current(DecisionRunStatus.WAITING_APPROVAL, 3));
            when(runs.update(isNull(), any())).thenReturn(1);
            store.attachAnalysisFlow(99L, 199L);
            var update = org.mockito.ArgumentCaptor.forClass(Wrapper.class);
            verify(runs).update(isNull(), update.capture());
            assertTrue(params(update.getValue()).containsValue(199L));
            return null;
        });
    }

    @Test void snapshotJsonPreservesSnowflakeIdAsStringWithInjectedGlobalMapper() {
        withTenant(() -> {
            var row = current(DecisionRunStatus.RUNNING, 0);
            when(runs.selectOne(any())).thenReturn(row);
            when(runs.update(isNull(), any())).thenReturn(1);
            var snapshot = new DeliveryContextSnapshot(REF, List.of(), List.of(), Map.of(), List.of(),
                List.of(), List.of(), Map.of(), List.of(), Map.of(), List.of(), "sha-test",
                LocalDateTime.of(2026, 9, 26, 10, 0), List.of());
            store.saveSnapshot(99L, snapshot);
            var update = org.mockito.ArgumentCaptor.forClass(Wrapper.class);
            verify(runs).update(isNull(), update.capture());
            assertTrue(params(update.getValue()).values().stream()
                .filter(String.class::isInstance).map(String.class::cast)
                .anyMatch(text -> text.contains("\"id\":\"9007199254740993\"")));
            return null;
        });
    }

    @Test void failureMessageIsRedactedAndBoundedBeforePersistence() {
        withTenant(() -> {
            when(runs.selectOne(any())).thenReturn(current(DecisionRunStatus.RUNNING, 0));
            when(runs.update(isNull(), any())).thenReturn(1);
            store.fail(99L, "MODEL_ERROR", "Bearer eyJ123456789 and apiKey=my-secret jdbc:mysql://host/password " + "x".repeat(1500));
            var update = org.mockito.ArgumentCaptor.forClass(Wrapper.class);
            verify(runs).update(isNull(), update.capture());
            String joined = params(update.getValue()).values().toString();
            assertFalse(joined.contains("eyJ123456789"));
            assertFalse(joined.contains("my-secret"));
            assertFalse(joined.contains("jdbc:mysql://host"));
            return null;
        });
    }

    @Test void savedActionsAreBoundToTenantRunAndReturnAssignedIds() {
        withTenant(() -> {
            when(runs.selectOne(any())).thenReturn(current(DecisionRunStatus.RUNNING, 0));
            when(actions.insert(any(AiDecisionAction.class))).thenAnswer(call -> { ((AiDecisionAction) call.getArgument(0)).setId(501L); return 1; });
            var proposal = new DecisionActionProposal(null, DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT,
                "draft", "draft", REF, Map.of("runId", "99", "materialLines", List.of()),
                DecisionRiskLevel.HIGH, true, "pu:requisition:add", "TENANT-A:99:purchase", "purchaseRequisitionDraft", 1, List.of());
            var saved = store.saveActions(99L, List.of(proposal), ACTOR);
            assertEquals(501L, saved.get(0).getId());
            assertEquals("TENANT-A", saved.get(0).getTenantId());
            assertEquals("PROPOSED", saved.get(0).getApprovalStatus());
            assertEquals("NOT_STARTED", saved.get(0).getExecutionStatus());
            return null;
        });
    }

    @Test void completionRejectsNumericConclusionsWithoutProvenance() {
        withTenant(() -> {
            when(runs.selectOne(any())).thenReturn(current(DecisionRunStatus.RUNNING, 0));
            var result = new DecisionResult(99L, REF, 0, DecisionRiskLevel.LOW, "LOW", List.of(),
                null, List.of(), null, Map.of(), List.of(), List.of(), List.of());
            assertThrows(IllegalArgumentException.class, () -> store.completeAnalysis(99L, result, "TEMPLATE_FALLBACK"));
            verify(runs, never()).update(isNull(), any());
            return null;
        });
    }

    @Test void completionRefusesClaimedEvidenceThatWasNeverPersisted() {
        withTenant(() -> {
            var running = current(DecisionRunStatus.RUNNING, 2);
            running.setInputSnapshotHash("hash-1");
            running.setInputSnapshotJson("{}");
            when(runs.selectOne(any())).thenReturn(running);
            var root = new EvidenceItem(99L, "ERP:STOCK:101:stock_qty", "ERP", "STOCK", 101L,
                "101", null, "stock_qty", null, "NUMBER", "10", null,
                LocalDateTime.of(2026, 9, 26, 10, 0), "hash-1", null, true, false, List.of(), null);
            var score = new EvidenceItem(99L, "DERIVED:CALC:RISK:risk_score", "DERIVED", "CALC", null,
                "RISK", null, "risk_score", null, "NUMBER", "0", null,
                root.observedAt(), "hash-1", null, true, false, List.of(root.evidenceKey()), null);
            var result = new DecisionResult(99L, REF, 0, DecisionRiskLevel.LOW, "HIGH", List.of(),
                null, List.of(), null, Map.of(), List.of(root, score), List.of(), List.of());
            assertThrows(ServiceException.class, () -> store.completeAnalysis(99L, result, "TEMPLATE_FALLBACK"));
            verify(runs, never()).update(isNull(), any());
            return null;
        });
    }

    @Test void matchedPersistedEvidenceAllowsLowRiskCompletionWithStatusVersionCas() {
        withTenant(() -> {
            var running = current(DecisionRunStatus.RUNNING, 2);
            running.setInputSnapshotHash("hash-1");
            running.setInputSnapshotJson("{}");
            when(runs.selectOne(any())).thenReturn(running);
            var root = new EvidenceItem(99L, "ERP:STOCK:101:stock_qty", "ERP", "STOCK", 101L,
                "101", null, "stock_qty", null, "NUMBER", "10", null,
                LocalDateTime.of(2026, 9, 26, 10, 0), "hash-1", null, true, false, List.of(), null);
            var score = new EvidenceItem(99L, "DERIVED:CALC:RISK:risk_score", "DERIVED", "CALC", null,
                "RISK", null, "risk_score", null, "NUMBER", "0", null,
                root.observedAt(), "hash-1", null, true, false, List.of(root.evidenceKey()), null);
            AiDecisionEvidence persistedRoot = savedEvidence(root);
            AiDecisionEvidence persistedScore = savedEvidence(score);
            when(evidence.selectList(any())).thenReturn(List.of(persistedRoot, persistedScore));
            when(runs.update(isNull(), any())).thenReturn(1);
            var result = new DecisionResult(99L, REF, 0, DecisionRiskLevel.LOW, "HIGH", List.of(),
                null, List.of(), null, Map.of(), List.of(root, score), List.of(), List.of());
            store.completeAnalysis(99L, result, "TEMPLATE_FALLBACK");
            var update = org.mockito.ArgumentCaptor.forClass(Wrapper.class);
            verify(runs).update(isNull(), update.capture());
            assertTrue(params(update.getValue()).containsValue("RUNNING"));
            assertTrue(params(update.getValue()).containsValue("TENANT-A"));
            assertTrue(params(update.getValue()).containsValue(2));
            return null;
        });
    }

    private static AiDecisionEvidence savedEvidence(EvidenceItem item) {
        var row = new AiDecisionEvidence();
        row.setRunId(item.runId()); row.setEvidenceKey(item.evidenceKey());
        row.setSourceDomain(item.sourceDomain()); row.setSourceType(item.sourceType());
        row.setSourceId(item.sourceId()); row.setSourceCode(item.sourceCode());
        row.setMetricCode(item.metricCode());
        row.setMetricName(item.metricName() == null ? item.metricCode() : item.metricName());
        row.setValueType(item.valueType());
        row.setMetricValue(item.value()); row.setObservedAt(item.observedAt());
        row.setDataVersion(item.dataVersion()); row.setVerifiable("Y"); row.setNormative("N");
        row.setDerivedFromJson(item.derivedFromEvidenceKeys().isEmpty() ? "[]" :
            "[\"" + String.join("\",\"", item.derivedFromEvidenceKeys()) + "\"]");
        return row;
    }

    @Test void createRunStoresActorAndSceneCodeInExplicitTenantScope() {
        withTenant(() -> {
            var request = request(false); request.setBizType(REF.type()); request.setBizId(REF.id());
            when(runs.insert(any(AiDecisionRun.class))).thenAnswer(call -> { ((AiDecisionRun) call.getArgument(0)).setId(99L); return 1; });
            var created = store.createRun(request, ACTOR);
            assertEquals(99L, created.getId());
            assertEquals("TENANT-A", created.getTenantId());
            assertEquals("DELIVERY_RISK", created.getSceneCode());
            assertEquals("CREATED", created.getStatus());
            assertEquals(7L, created.getCreateBy());
            return null;
        });
    }

    @Test void unnamedErpFactIsPersistedWithRequiredMetricName() {
        withTenant(() -> {
            var run = current(DecisionRunStatus.RUNNING, 1); run.setInputSnapshotHash("hash-1");
            when(runs.selectOne(any())).thenReturn(run);
            when(evidence.selectList(any())).thenReturn(List.of());
            when(evidence.insert(any(AiDecisionEvidence.class))).thenReturn(1);
            var item = new EvidenceItem(99L, "ERP:ARRIVAL:11:remaining_qty", "ERP", "ARRIVAL", 11L,
                "11", null, "remaining_qty", null, "NUMBER", "1", null,
                LocalDateTime.of(2026, 9, 26, 10, 0), "hash-1", null, true, false, List.of(), null);
            store.replaceEvidence(99L, List.of(item), ACTOR);
            var inserted = org.mockito.ArgumentCaptor.forClass(AiDecisionEvidence.class);
            verify(evidence).insert(inserted.capture());
            assertEquals("remaining_qty", inserted.getValue().getMetricName());
            assertEquals("ERP:ARRIVAL:11:remaining_qty", inserted.getValue().getEvidenceKey());
            return null;
        });
    }

    @Test void databaseDatetimePrecisionAndDecimalScaleDoNotBreakEvidenceReplay() {
        withTenant(() -> {
            var run = current(DecisionRunStatus.RUNNING, 1); run.setInputSnapshotHash("hash-1");
            when(runs.selectOne(any())).thenReturn(run);
            AiDecisionEvidence prior = new AiDecisionEvidence();
            prior.setRunId(99L); prior.setTenantId("TENANT-A");
            prior.setEvidenceKey("ERP:STOCK:11:stock_qty");
            prior.setSourceDomain("ERP"); prior.setSourceType("STOCK"); prior.setSourceId(11L);
            prior.setSourceCode("11"); prior.setMetricCode("stock_qty"); prior.setMetricName("stock_qty");
            prior.setMetricValue("10");
            prior.setValueType("NUMBER"); prior.setVerifiable("Y"); prior.setNormative("N");
            prior.setDerivedFromJson("[]"); prior.setDataVersion("hash-1");
            prior.setObservedAt(LocalDateTime.of(2026, 9, 26, 10, 0, 0));
            prior.setConfidence(new BigDecimal("1.0000"));
            when(evidence.selectList(any())).thenReturn(List.of(prior));
            var item = new EvidenceItem(99L, prior.getEvidenceKey(), "ERP", "STOCK", 11L,
                "11", null, "stock_qty", null, "NUMBER", "10", null,
                prior.getObservedAt().plusNanos(123_000_000), "hash-1", BigDecimal.ONE,
                true, false, List.of(), null);
            assertDoesNotThrow(() -> store.replaceEvidence(99L, List.of(item), ACTOR));
            verify(evidence, never()).delete(any());
            verify(evidence, never()).insert(any(AiDecisionEvidence.class));
            return null;
        });
    }

    @Test void evidenceReplayDoesNotSoftDeleteUnderRunKeyUniqueConstraint() {
        withTenant(() -> {
            var run = current(DecisionRunStatus.RUNNING, 1); run.setInputSnapshotHash("sha-test");
            when(runs.selectOne(any())).thenReturn(run);
            AiDecisionEvidence existing = new AiDecisionEvidence();
            existing.setId(22L); existing.setTenantId("TENANT-A"); existing.setRunId(99L);
            existing.setEvidenceKey("ERP:ORDER:11:risk_score"); existing.setSourceDomain("ERP");
            existing.setSourceType("ORDER"); existing.setSourceId(11L);
            existing.setSourceCode("RISK"); existing.setMetricCode("risk_score");
            existing.setMetricName("risk_score"); existing.setMetricValue("40"); existing.setValueType("NUMBER");
            existing.setVerifiable("Y"); existing.setNormative("N");
            existing.setDerivedFromJson("[]");
            when(evidence.selectList(any())).thenReturn(List.of(existing));
            EvidenceItem item = new EvidenceItem(99L, existing.getEvidenceKey(), "ERP", "ORDER", 11L,
                "RISK", null, "risk_score", null, "NUMBER", "40", null, null, null,
                null, true, false, List.of(), null);
            store.replaceEvidence(99L, List.of(item), ACTOR);
            verify(evidence, never()).delete(any());
            verify(evidence, never()).insert(any(AiDecisionEvidence.class));
            EvidenceItem conflict = new EvidenceItem(99L, existing.getEvidenceKey(), "ERP", "ORDER", 11L,
                "RISK", null, "risk_score", null, "NUMBER", "41", null, null, null,
                null, true, false, List.of(), null);
            assertThrows(ServiceException.class, () -> store.replaceEvidence(99L, List.of(conflict), ACTOR));
            verify(evidence, never()).delete(any());
            return null;
        });
    }

    @Test void duplicateActionKeyMayReplayOnlyIdenticalStructuredPayload() {
        withTenant(() -> {
            when(runs.selectOne(any())).thenReturn(current(DecisionRunStatus.RUNNING, 0));
            var proposal = new DecisionActionProposal(null, DecisionActionType.SEND_MANAGER_NOTIFICATION,
                "notify", "notify", REF, Map.of("runId", "99", "targetUserId", "7"),
                DecisionRiskLevel.HIGH, true, "ai:decision:execute", "TENANT-A:99:notify",
                "managerNotification", 1, List.of());
            AiDecisionAction prior = new AiDecisionAction();
            prior.setId(501L); prior.setRunId(99L); prior.setTenantId("TENANT-A");
            prior.setIdempotencyKey(proposal.idempotencyKey()); prior.setActionType(proposal.actionType().name());
            try { prior.setPayloadJson(mapper.writeValueAsString(proposal.payload())); }
            catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new AssertionError(e); }
            prior.setTargetBizType(REF.type().name()); prior.setTargetBizId(REF.id()); prior.setTargetBizCode(REF.code());
            prior.setPermissionCode(proposal.permissionCode()); prior.setExecutorCode(proposal.executorCode());
            prior.setTitle(proposal.title()); prior.setDisplayReason(proposal.displayReason());
            prior.setRiskLevel(proposal.riskLevel().name()); prior.setApprovalRequired("Y");
            prior.setSequenceNo(1); prior.setDependencyIdsJson("[]");
            when(actions.selectList(any())).thenReturn(List.of(prior));
            assertEquals(501L, store.saveActions(99L, List.of(proposal), ACTOR).get(0).getId());
            verify(actions, never()).insert(any(AiDecisionAction.class));
            var changed = new DecisionActionProposal(null, proposal.actionType(), proposal.title(),
                proposal.displayReason(), REF, Map.of("runId", "99", "targetUserId", "8"),
                proposal.riskLevel(), true, proposal.permissionCode(), proposal.idempotencyKey(),
                proposal.executorCode(), 1, List.of());
            assertThrows(ServiceException.class, () -> store.saveActions(99L, List.of(changed), ACTOR));
            return null;
        });
    }

    private static Map<String, Object> params(Wrapper<?> wrapper) {
        var query = ((com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) wrapper);
        query.getSqlSegment(); // MyBatis-Plus 延迟绑定参数。
        return query.getParamNameValuePairs();
    }

    private <T> T withTenant(Supplier<T> action) {
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class);
             MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            spring.when(() -> SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(LoginHelper::isLogin).thenReturn(false);
            try { return TenantHelper.dynamic("TENANT-A", action); }
            finally { TenantHelper.clearDynamic(); }
        }
    }
}
