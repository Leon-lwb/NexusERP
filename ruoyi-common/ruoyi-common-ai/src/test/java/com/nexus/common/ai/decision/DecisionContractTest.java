package com.nexus.common.ai.decision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nexus.common.ai.decision.dto.ApsScenarioCompareResult;
import com.nexus.common.ai.decision.dto.DecisionActionExecutionResult;
import com.nexus.common.ai.decision.dto.DecisionActionProposal;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.dto.DecisionApprovalRequest;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.dto.DecisionFreshnessResult;
import com.nexus.common.ai.decision.dto.DecisionKpiSet;
import com.nexus.common.ai.decision.dto.DecisionOutcome;
import com.nexus.common.ai.decision.dto.DecisionResult;
import com.nexus.common.ai.decision.dto.DecisionRunAcceptedVo;
import com.nexus.common.ai.decision.dto.DecisionScenario;
import com.nexus.common.ai.decision.dto.DecisionWorkflowStartCommand;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot;
import com.nexus.common.ai.decision.dto.DeliveryRiskRunRequest;
import com.nexus.common.ai.decision.dto.EvidenceItem;
import com.nexus.common.ai.decision.dto.RetrievalHit;
import com.nexus.common.ai.decision.dto.ShortageSimulationResult;
import com.nexus.common.ai.decision.enums.DecisionActionType;
import com.nexus.common.ai.decision.enums.DecisionApprovalStatus;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.ai.decision.enums.DecisionExecutionStatus;
import com.nexus.common.ai.decision.enums.DecisionRiskLevel;
import com.nexus.common.ai.decision.enums.DecisionRunStatus;
import com.nexus.common.ai.decision.enums.DecisionTriggerType;
import com.nexus.common.ai.decision.port.DecisionWorkflowPort;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import com.nexus.common.web.config.JacksonConfig;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("local")
@Tag("dev")
@Tag("prod")
class DecisionContractTest {

    @Test
    void riskLevelUsesStableCompetitionThresholds() {
        assertEquals(DecisionRiskLevel.LOW, DecisionRiskLevel.fromScore(0));
        assertEquals(DecisionRiskLevel.LOW, DecisionRiskLevel.fromScore(19.99));
        assertEquals(DecisionRiskLevel.MEDIUM, DecisionRiskLevel.fromScore(20));
        assertEquals(DecisionRiskLevel.MEDIUM, DecisionRiskLevel.fromScore(39.99));
        assertEquals(DecisionRiskLevel.HIGH, DecisionRiskLevel.fromScore(40));
        assertEquals(DecisionRiskLevel.HIGH, DecisionRiskLevel.fromScore(69.99));
        assertEquals(DecisionRiskLevel.CRITICAL, DecisionRiskLevel.fromScore(70));
        assertEquals(DecisionRiskLevel.CRITICAL, DecisionRiskLevel.fromScore(100));
    }

    @Test
    void invalidScoreCannotBeSilentlyClassifiedLow() {
        assertThrows(IllegalArgumentException.class, () -> DecisionRiskLevel.fromScore(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> DecisionRiskLevel.fromScore(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> DecisionRiskLevel.fromScore(Double.NEGATIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> DecisionRiskLevel.fromScore(-0.01));
        assertThrows(IllegalArgumentException.class, () -> DecisionRiskLevel.fromScore(101));
    }

    @Test
    void terminalRunStatusesAreExplicit() {
        EnumSet<DecisionRunStatus> terminal = EnumSet.of(
            DecisionRunStatus.SUCCEEDED, DecisionRunStatus.PARTIAL_SUCCESS,
            DecisionRunStatus.FAILED, DecisionRunStatus.SUPERSEDED,
            DecisionRunStatus.REJECTED, DecisionRunStatus.ROLLED_BACK,
            DecisionRunStatus.CANCELLED);
        for (DecisionRunStatus status : DecisionRunStatus.values()) {
            assertEquals(terminal.contains(status), status.isTerminal(), status.name());
        }
        assertEquals(14, DecisionRunStatus.values().length);
    }

    @Test
    void writeActionsAlwaysDeclarePermissionAndExecutor() {
        assertEquals("pu:requisition:add", DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT.permissionCode());
        assertEquals("purchaseRequisitionDraft", DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT.executorCode());
        assertEquals("prd:schedule:run", DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT.permissionCode());
        assertEquals("schedulePlanDraft", DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT.executorCode());
        assertEquals("ai:decision:execute", DecisionActionType.SEND_EXPEDITE_NOTICE.permissionCode());
        assertFalse(DecisionActionType.SEND_EXPEDITE_NOTICE.approvalRequired());
        assertTrue(DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT.approvalRequired());
        assertTrue(DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT.approvalRequired());
        assertFalse(DecisionActionType.SEND_MANAGER_NOTIFICATION.approvalRequired());
        assertEquals(EnumSet.of(DecisionActionType.SEND_EXPEDITE_NOTICE,
            DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT,
            DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT,
            DecisionActionType.SEND_MANAGER_NOTIFICATION), EnumSet.allOf(DecisionActionType.class));
        for (DecisionActionType type : DecisionActionType.values()) {
            assertFalse(type.executorCode().isBlank());
            assertFalse(type.permissionCode().isBlank());
        }
    }

    @Test
    void enumVocabularyStaysStable() {
        assertEquals(EnumSet.of(DecisionBusinessType.SALES_ORDER, DecisionBusinessType.WORK_ORDER,
            DecisionBusinessType.SCHEDULE_PLAN, DecisionBusinessType.SHORTAGE_HORIZON),
            EnumSet.allOf(DecisionBusinessType.class));
        assertEquals(EnumSet.of(DecisionTriggerType.MANUAL, DecisionTriggerType.CHAT,
            DecisionTriggerType.EVENT, DecisionTriggerType.SCHEDULE), EnumSet.allOf(DecisionTriggerType.class));
        assertEquals(EnumSet.of(DecisionApprovalStatus.PROPOSED, DecisionApprovalStatus.PENDING_APPROVAL,
            DecisionApprovalStatus.APPROVED, DecisionApprovalStatus.REJECTED, DecisionApprovalStatus.NOT_AUTHORIZED),
            EnumSet.allOf(DecisionApprovalStatus.class));
        assertEquals(EnumSet.of(DecisionExecutionStatus.NOT_STARTED, DecisionExecutionStatus.EXECUTING,
            DecisionExecutionStatus.SUCCEEDED, DecisionExecutionStatus.FAILED_PRECHECK, DecisionExecutionStatus.FAILED,
            DecisionExecutionStatus.SKIPPED, DecisionExecutionStatus.COMPENSATING, DecisionExecutionStatus.COMPENSATED,
            DecisionExecutionStatus.COMPENSATE_FAILED), EnumSet.allOf(DecisionExecutionStatus.class));
        assertEquals(DecisionBusinessType.SHORTAGE_HORIZON, DecisionBusinessType.valueOf("SHORTAGE_HORIZON"));
        assertEquals(DecisionTriggerType.CHAT, DecisionTriggerType.valueOf("CHAT"));
        assertEquals(DecisionApprovalStatus.NOT_AUTHORIZED, DecisionApprovalStatus.valueOf("NOT_AUTHORIZED"));
        assertEquals(DecisionExecutionStatus.COMPENSATE_FAILED,
            DecisionExecutionStatus.valueOf("COMPENSATE_FAILED"));
    }

    @Test
    void actorAndBusinessRefDeserializeWithExactTenantAndLongIds() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        DecisionActor actor = mapper.readValue(
            "{\"tenantId\":\"000023\",\"userId\":9007199254740993,\"userName\":\"tester\",\"permissions\":[\"prd:shortage:query\"]}",
            DecisionActor.class);
        assertEquals("000023", actor.tenantId());
        assertEquals(9007199254740993L, actor.userId());
        assertEquals(Set.of("prd:shortage:query"), actor.permissions());
        DecisionBusinessRef ref = mapper.readValue(
            "{\"type\":\"WORK_ORDER\",\"id\":9007199254740993,\"code\":\"WO-1\"}",
            DecisionBusinessRef.class);
        assertEquals(DecisionBusinessType.WORK_ORDER, ref.type());
        assertEquals(9007199254740993L, ref.id());
        assertEquals(Long.class, DecisionRunAcceptedVo.class.getRecordComponents()[0].getType());
        assertEquals(DecisionRunStatus.CREATED,
            new DecisionRunAcceptedVo(5L, DecisionRunStatus.CREATED, "DELIVERY_RISK", LocalDateTime.now()).status());
    }

    @Test
    void requestDeserializesDefaultsAndAllowsOnlyHorizonWithoutId() throws Exception {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        ObjectMapper mapper = new ObjectMapper();
        DeliveryRiskRunRequest horizon = mapper.readValue("{\"bizType\":\"SHORTAGE_HORIZON\"}", DeliveryRiskRunRequest.class);
        assertEquals(30, horizon.getHorizonDays());
        assertEquals(List.of("BASELINE_CURRENT", "APS_EDD", "APS_CR", "APS_CR_EXPEDITE"), horizon.getScenarioCodes());
        assertEquals(DecisionTriggerType.MANUAL, horizon.getTriggerType());
        assertFalse(horizon.getForceRestart());
        assertTrue(validator.validate(horizon).isEmpty());

        for (DecisionBusinessType type : List.of(DecisionBusinessType.SALES_ORDER,
            DecisionBusinessType.WORK_ORDER, DecisionBusinessType.SCHEDULE_PLAN)) {
            DeliveryRiskRunRequest missing = mapper.readValue("{\"bizType\":\"" + type + "\"}", DeliveryRiskRunRequest.class);
            assertFalse(validator.validate(missing).isEmpty(), type.name() + " requires bizId");
            DeliveryRiskRunRequest supplied = mapper.readValue("{\"bizType\":\"" + type
                + "\",\"bizId\":42,\"horizonDays\":1}", DeliveryRiskRunRequest.class);
            assertTrue(validator.validate(supplied).isEmpty(), type.name());
        }
        DeliveryRiskRunRequest invalidHorizon = mapper.readValue(
            "{\"bizType\":\"SHORTAGE_HORIZON\",\"bizId\":42}", DeliveryRiskRunRequest.class);
        assertFalse(validator.validate(invalidHorizon).isEmpty());
        DeliveryRiskRunRequest missingType = mapper.readValue("{}", DeliveryRiskRunRequest.class);
        assertFalse(validator.validate(missingType).isEmpty());
    }

    @Test
    void recordScopedBusinessIdsMustBePositive() throws Exception {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        ObjectMapper mapper = new ObjectMapper();
        for (DecisionBusinessType type : List.of(DecisionBusinessType.SALES_ORDER,
            DecisionBusinessType.WORK_ORDER, DecisionBusinessType.SCHEDULE_PLAN)) {
            for (long id : List.of(0L, -1L)) {
                DeliveryRiskRunRequest request = mapper.readValue(
                    "{\"bizType\":\"" + type + "\",\"bizId\":" + id + "}", DeliveryRiskRunRequest.class);
                assertTrue(validator.validate(request).stream().anyMatch(v -> "bizId".equals(v.getPropertyPath().toString())),
                    type + " rejects " + id);
            }
        }
        DeliveryRiskRunRequest horizon = mapper.readValue(
            "{\"bizType\":\"SHORTAGE_HORIZON\",\"bizId\":null}", DeliveryRiskRunRequest.class);
        assertTrue(validator.validate(horizon).isEmpty());
    }

    @Test
    void horizonDaysMustRemainInConfiguredRange() throws Exception {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        ObjectMapper mapper = new ObjectMapper();
        for (int days : List.of(0, 91)) {
            DeliveryRiskRunRequest invalid = mapper.readValue(
                "{\"bizType\":\"SHORTAGE_HORIZON\",\"horizonDays\":" + days + "}", DeliveryRiskRunRequest.class);
            assertFalse(validator.validate(invalid).isEmpty());
        }
        assertNotNull(mapper.readValue("{\"bizType\":\"WORK_ORDER\",\"bizId\":9,\"forceRestart\":true}",
            DeliveryRiskRunRequest.class));
    }

    @Test
    void resultKeepsTypedScenariosEvidenceActionsAndKpis() throws Exception {
        DecisionKpiSet kpis = new DecisionKpiSet(2, 1, 3, 3, "2026-09-24", 1,
            BigDecimal.valueOf(5), "2026-09-25", BigDecimal.ONE, 0, 1, BigDecimal.ZERO);
        EvidenceItem evidence = new EvidenceItem(9L, "ERP:ORDER:7:promise_date", "ERP", "ORDER", 7L,
            "SO-7", "订单", "promise_date", "承诺交期", "DATE", "2026-09-25", null,
            LocalDateTime.of(2026, 9, 23, 9, 0), "v1", BigDecimal.ONE,
            true, false, List.of(), "订单/交期");
        DecisionScenario scenario = new DecisionScenario("APS_EDD", "按交期", "EDD", true,
            List.of(), kpis, BigDecimal.TEN, 1, Map.of(), List.of(evidence.evidenceKey()), List.of(),
            "sha256-input", List.of(7L), List.of("workstation:1"));
        DecisionActionProposal action = new DecisionActionProposal(8L,
            DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT, "补货", "缺料",
            new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 7L, "WO-7"), Map.of(),
            DecisionRiskLevel.HIGH, true, "pu:requisition:add", "tenant-000023-run-9-action-8",
            "purchaseRequisitionDraft", 1, List.of());
        DecisionResult result = new DecisionResult(9L, action.target(), 65,
            DecisionRiskLevel.HIGH, "HIGH", List.of("SHORTAGE"), kpis, List.of(scenario),
            scenario.scenarioCode(), Map.of("summary", "延期"), List.of(evidence), List.of(action), List.of());
        assertEquals(9L, result.runId());
        assertEquals(BigDecimal.valueOf(5), result.baselineKpis().totalShortageGap());
        assertEquals(evidence.evidenceKey(), result.evidence().get(0).evidenceKey());
        assertEquals(action.actionType(), result.actions().get(0).actionType());
        assertEquals(DecisionRiskLevel.class, DecisionActionProposal.class.getRecordComponents()[6].getType());
        assertTrue(Serializable.class.isAssignableFrom(DecisionResult.class));
        assertTrue(Serializable.class.isAssignableFrom(DecisionKpiSet.class));
        assertTrue(Serializable.class.isAssignableFrom(DecisionScenario.class));
        assertTrue(Serializable.class.isAssignableFrom(EvidenceItem.class));
    }

    @Test
    void scenarioRoundTripPreservesInputHashDelaysAndBottlenecks() throws Exception {
        DecisionScenario scenario = new DecisionScenario("APS_EDD", "交期优先", "EDD", true,
            List.of(), null, BigDecimal.ONE, 1, Map.of("horizonDays", 30), List.of("ERP:ORDER:7:due"),
            List.of(Map.of("workOrderId", 9L)), "sha256-input",
            List.of(9007199254740993L), List.of("workstation:104"));
        ObjectMapper mapper = new ObjectMapper();
        DecisionScenario parsed = mapper.readValue(mapper.writeValueAsString(scenario), DecisionScenario.class);
        assertEquals("sha256-input", parsed.inputHash());
        assertEquals(List.of(9007199254740993L), parsed.delayedOrders());
        assertEquals(List.of("workstation:104"), parsed.bottleneckResources());
        assertEquals(List.of("ERP:ORDER:7:due"), parsed.evidenceKeys());
    }

    @Test
    void actionIdempotencyAndEvidenceRunIdSurviveJsonRoundTrip() throws Exception {
        Long runId = 9007199254740993L;
        EvidenceItem evidence = new EvidenceItem(runId, "ERP:ORDER:7:promise_date", "ERP", "ORDER", 7L,
            "SO-7", "订单", "promise_date", "承诺交期", "DATE", "2026-09-28", null,
            LocalDateTime.of(2026, 9, 23, 9, 0), "v1", BigDecimal.ONE,
            true, false, List.of(), "订单/交期");
        DecisionActionProposal action = new DecisionActionProposal(8L,
            DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT, "补货", "缺料",
            new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 7L, "WO-7"), Map.of(),
            DecisionRiskLevel.HIGH, true, "pu:requisition:add", "tenant-000023-run-9-action-8",
            "purchaseRequisitionDraft", 1, List.of());
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        EvidenceItem parsedEvidence = mapper.readValue(mapper.writeValueAsString(evidence), EvidenceItem.class);
        DecisionActionProposal parsedAction = mapper.readValue(mapper.writeValueAsString(action),
            DecisionActionProposal.class);
        assertEquals(runId, parsedEvidence.runId());
        assertEquals("ERP:ORDER:7:promise_date", parsedEvidence.evidenceKey());
        assertEquals("tenant-000023-run-9-action-8", parsedAction.idempotencyKey());
        assertEquals("purchaseRequisitionDraft", parsedAction.executorCode());
    }

    @Test
    void dynamicActionPayloadRejectsUnsafeOrAmbiguousNumericIdentifiers() {
        long snowflake = 9007199254740993L;
        assertThrows(IllegalArgumentException.class, () -> proposal(snowflake, Map.of("workOrderId", snowflake)));
        assertThrows(IllegalArgumentException.class, () -> proposal(snowflake,
            Map.of("lines", List.of(Map.of("ambiguousNumber", snowflake)))));
        assertThrows(IllegalArgumentException.class, () -> proposal(snowflake, Map.of("workOrderId", 7)));
        assertThrows(IllegalArgumentException.class, () -> proposal(snowflake, Map.of("quantity", 2L)));
        assertThrows(IllegalArgumentException.class, () -> proposal(snowflake,
            Map.of("dependentIds", List.of("7", 8))));
    }

    @Test
    void dynamicActionPayloadPreservesStringIdsAndNumericFactsWithProductionSerializer() throws Exception {
        long snowflake = 9007199254740993L;
        String id = Long.toString(snowflake);
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("workOrderId", id);
        line.put("quantity", 12);
        line.put("amount", new BigDecimal("125.50"));
        List<Object> lines = new ArrayList<>(List.of(line));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", "000023");
        payload.put("runIds", new ArrayList<>(List.of(id)));
        payload.put("lines", lines);
        payload.put("reportCount", 42);
        DecisionActionProposal action = proposal(snowflake, payload);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JacksonConfig().bigNumberModule());
        assertTrue(mapper.readTree(mapper.writeValueAsString(Map.of("workOrderId", snowflake)))
            .path("workOrderId").isTextual(), "实际 BigNumberSerializer 会将动态 Map 的大 Long 转为 String");
        String json = mapper.writeValueAsString(action);
        JsonNode root = mapper.readTree(json);
        assertTrue(root.get("actionId").isTextual());
        assertEquals(id, root.get("actionId").textValue());
        assertTrue(root.path("target").path("id").isTextual());
        assertTrue(root.path("payload").path("lines").get(0).path("workOrderId").isTextual());
        assertTrue(root.path("payload").path("lines").get(0).path("quantity").isNumber());
        assertTrue(root.path("payload").path("lines").get(0).path("amount").isNumber());
        assertTrue(root.path("payload").path("reportCount").isNumber());

        line.put("workOrderId", "tampered");
        lines.clear();
        payload.clear();
        assertEquals(json, mapper.writeValueAsString(action));
        DecisionActionProposal roundTrip = mapper.readValue(json, DecisionActionProposal.class);
        assertEquals(snowflake, roundTrip.actionId());
        assertEquals(snowflake, roundTrip.target().id());
        Map<?, ?> lineAfter = (Map<?, ?>) ((List<?>) roundTrip.payload().get("lines")).get(0);
        assertEquals(id, lineAfter.get("workOrderId"));
        assertTrue(lineAfter.get("quantity") instanceof Number);
        assertTrue(lineAfter.get("amount") instanceof Number);
        assertEquals(List.of(id), roundTrip.payload().get("runIds"));
        assertThrows(UnsupportedOperationException.class, () -> roundTrip.payload().put("id", "7"));
        assertThrows(UnsupportedOperationException.class, () -> lineAfter.clear());
        assertThrows(UnsupportedOperationException.class, () -> ((List<?>) roundTrip.payload().get("lines")).clear());
        String invalidIdJson = json.replace("\"workOrderId\":\"" + id + "\"",
            "\"workOrderId\":" + id);
        assertThrows(com.fasterxml.jackson.databind.JsonMappingException.class,
            () -> mapper.readValue(invalidIdJson, DecisionActionProposal.class));
    }

    @Test
    void approvalBoundsCommentAndOutcomePreservesTypedIds() throws Exception {
        DecisionApprovalRequest request = new ObjectMapper().readValue(
            "{\"runVersion\":2,\"approvedActionIds\":[9007199254740993],"
                + "\"rejectedActionIds\":[],\"comment\":\"同意\",\"expectedSnapshotHash\":\"sha256\"}",
            DecisionApprovalRequest.class);
        assertEquals(2, request.getRunVersion());
        assertEquals(List.of(9007199254740993L), request.getApprovedActionIds());
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        assertTrue(validator.validate(request).isEmpty());
        request.setComment("x".repeat(501));
        assertFalse(validator.validate(request).isEmpty());
        DecisionOutcome outcome = new DecisionOutcome(null, null, null, Map.of("delayDays", 3),
            List.of(9007199254740993L), List.of(12L), LocalDateTime.of(2026, 9, 23, 9, 0), "HIGH");
        assertEquals(9007199254740993L, outcome.effectiveActionIds().get(0));
        assertEquals(Long.class, DecisionOutcome.class.getRecordComponents()[4].getGenericType()
            .getTypeName().contains("java.lang.Long") ? Long.class : Object.class);
    }

    @Test
    void snapshotAndShortageResultRetainTypedFactsAndDates() throws Exception {
        DeliveryContextSnapshot snapshot = new DeliveryContextSnapshot(
            new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 11L, "WO-11"),
            List.of(new DeliveryContextSnapshot.OrderFact(1L, "SO-1", LocalDate.of(2026, 9, 28), "OPEN")),
            List.of(new DeliveryContextSnapshot.WorkOrderFact(11L, "WO-11", 2L,
                BigDecimal.TEN, LocalDate.of(2026, 9, 28), 1L, 3L)),
            Map.of(), List.of(new DeliveryContextSnapshot.RequirementFact(2L, "MAT-2", "钢", LocalDate.of(2026, 9, 25),
                BigDecimal.TEN, 11L, "WO-11")),
            List.of(new DeliveryContextSnapshot.StockFact(2L, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO)),
            List.of(new DeliveryContextSnapshot.ArrivalFact(2L, 5L, LocalDate.of(2026, 9, 26), BigDecimal.ONE, 6L)),
            Map.of(LocalDate.of(2026, 9, 25), Map.of("A", BigDecimal.TEN)), List.of(), Map.of(),
            List.of(new DeliveryContextSnapshot.SupplierMetricFact(6L, BigDecimal.ONE, 3,
                LocalDateTime.of(2026, 9, 23, 9, 0))),
            "sha256", LocalDateTime.of(2026, 9, 23, 9, 0), List.of());
        assertEquals(2L, snapshot.requirements().get(0).materialId());
        assertEquals(BigDecimal.TEN, snapshot.capacityByDate().get(LocalDate.of(2026, 9, 25)).get("A"));
        assertEquals("sha256", snapshot.snapshotHash());

        ShortageSimulationResult.Item item = new ShortageSimulationResult.Item(
            2L, "MAT-2", "钢", LocalDate.of(2026, 9, 25), BigDecimal.valueOf(9), BigDecimal.ONE,
            List.of(11L), List.of(1L), "EXPEDITE", List.of("ERP:STOCK:2:available_qty"));
        ShortageSimulationResult simulation = new ShortageSimulationResult(true, 1,
            LocalDate.of(2026, 9, 25), BigDecimal.valueOf(9), List.of(item), null,
            List.of("ERP:STOCK:2:available_qty"), List.of());
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        ShortageSimulationResult parsed = mapper.readValue(mapper.writeValueAsString(simulation), ShortageSimulationResult.class);
        assertEquals(LocalDate.of(2026, 9, 25), parsed.items().get(0).firstShortageDate());
        assertEquals(List.of(11L), parsed.items().get(0).affectedWorkOrderIds());
        assertEquals(BigDecimal.valueOf(9), parsed.items().get(0).maxGap());
    }

    @Test
    void snapshotMakesDeepDefensiveCopiesOfEveryHashRelevantCollection() throws Exception {
        List<DeliveryContextSnapshot.OrderFact> orders = new ArrayList<>(List.of(
            new DeliveryContextSnapshot.OrderFact(1L, "SO-1", LocalDate.of(2026, 9, 28), "OPEN")));
        List<DeliveryContextSnapshot.WorkOrderFact> workOrders = new ArrayList<>();
        List<DeliveryContextSnapshot.RequirementFact> requirements = new ArrayList<>();
        List<DeliveryContextSnapshot.StockFact> stocks = new ArrayList<>();
        List<DeliveryContextSnapshot.ArrivalFact> arrivals = new ArrayList<>();
        List<LocalDate> holidays = new ArrayList<>();
        List<DeliveryContextSnapshot.SupplierMetricFact> suppliers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<DeliveryContextSnapshot.RoutingStepFact> steps = new ArrayList<>(List.of(
            new DeliveryContextSnapshot.RoutingStepFact(3L, 4L, 1, 5L, 6L, BigDecimal.ONE)));
        Map<Long, List<DeliveryContextSnapshot.RoutingStepFact>> routes = new LinkedHashMap<>();
        routes.put(2L, steps);
        Map<String, BigDecimal> dayCapacity = new LinkedHashMap<>();
        dayCapacity.put("WS-5", BigDecimal.TEN);
        Map<LocalDate, Map<String, BigDecimal>> capacities = new LinkedHashMap<>();
        capacities.put(LocalDate.of(2026, 9, 25), dayCapacity);
        List<String> slots = new ArrayList<>(List.of("morning"));
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("slots", slots);
        List<Object> tasks = new ArrayList<>(List.of(task));
        Map<String, Object> schedule = new LinkedHashMap<>();
        schedule.put("tasks", tasks);

        DeliveryContextSnapshot snapshot = new DeliveryContextSnapshot(
            new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 11L, "WO-11"),
            orders, workOrders, routes, requirements, stocks, arrivals, capacities,
            holidays, schedule, suppliers, "hash-v1", LocalDateTime.of(2026, 9, 23, 9, 0), warnings);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String originalJson = mapper.writeValueAsString(snapshot);

        orders.clear();
        workOrders.add(null);
        requirements.add(null);
        stocks.add(null);
        arrivals.add(null);
        holidays.add(LocalDate.of(2026, 9, 26));
        suppliers.add(null);
        warnings.add("changed");
        steps.clear();
        routes.clear();
        dayCapacity.clear();
        capacities.clear();
        slots.clear();
        task.clear();
        tasks.clear();
        schedule.clear();

        assertEquals(originalJson, mapper.writeValueAsString(snapshot));
        assertEquals(1, snapshot.routingByMaterial().get(2L).size());
        assertEquals(BigDecimal.TEN, snapshot.capacityByDate().get(LocalDate.of(2026, 9, 25)).get("WS-5"));
        List<?> snapTasks = (List<?>) snapshot.currentSchedule().get("tasks");
        Map<?, ?> snapTask = (Map<?, ?>) snapTasks.get(0);
        List<?> snapSlots = (List<?>) snapTask.get("slots");
        assertEquals(List.of("morning"), snapSlots);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.salesOrders().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.routingByMaterial().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.routingByMaterial().get(2L).clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.capacityByDate().clear());
        assertThrows(UnsupportedOperationException.class, snapTask::clear);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.capacityByDate()
            .get(LocalDate.of(2026, 9, 25)).clear());
        assertThrows(UnsupportedOperationException.class, snapSlots::clear);
        DeliveryContextSnapshot parsed = mapper.readValue(originalJson, DeliveryContextSnapshot.class);
        assertEquals(originalJson, mapper.writeValueAsString(parsed));
    }

    @Test
    void snapshotDeserializesLegacyNullCollectionsWithoutChangingTheirMeaning() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        DeliveryContextSnapshot old = mapper.readValue("{\"snapshotHash\":\"legacy\"}",
            DeliveryContextSnapshot.class);
        assertEquals("legacy", old.snapshotHash());
        assertNull(old.salesOrders());
        assertNull(old.routingByMaterial());
        assertNull(old.capacityByDate());
        assertNull(old.currentSchedule());
        DeliveryContextSnapshot restored = mapper.readValue(mapper.writeValueAsString(old),
            DeliveryContextSnapshot.class);
        assertNull(restored.warnings());
        assertEquals("legacy", restored.snapshotHash());
    }

    @Test
    void snapshotRoutingStepsRoundTripWithMaterialIdsAndWorkstationFacts() throws Exception {
        long materialId = 9007199254740993L;
        Map<Long, List<DeliveryContextSnapshot.RoutingStepFact>> routes = Map.of(materialId,
            List.of(new DeliveryContextSnapshot.RoutingStepFact(101L, 102L, 3, 103L, 104L,
                new BigDecimal("1.75"))));
        DeliveryContextSnapshot snapshot = new DeliveryContextSnapshot(
            new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 1L, "WO-1"),
            List.of(), List.of(), routes, List.of(), List.of(), List.of(), Map.of(), List.of(),
            Map.of(), List.of(), "hash", LocalDateTime.of(2026, 9, 23, 9, 0), List.of());
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        DeliveryContextSnapshot parsed = mapper.readValue(mapper.writeValueAsString(snapshot),
            DeliveryContextSnapshot.class);
        assertEquals(routes, parsed.routingByMaterial());
        DeliveryContextSnapshot.RoutingStepFact step = parsed.routingByMaterial().get(materialId).get(0);
        assertEquals(101L, step.routingId());
        assertEquals(102L, step.stepId());
        assertEquals(3, step.seqNum());
        assertEquals(103L, step.workstationId());
        assertEquals(104L, step.workshopId());
        assertEquals(new BigDecimal("1.75"), step.standardHours());
    }

    @Test
    void shortageSimulationCarriesRiskSummaryKpisAndEvidenceReferences() throws Exception {
        DecisionKpiSet kpis = new DecisionKpiSet(1, 1, 2, 2, "2026-09-28", 1,
            new BigDecimal("9.25"), "2026-09-25", BigDecimal.ONE, 0, 0, BigDecimal.ZERO);
        ShortageSimulationResult.Item item = new ShortageSimulationResult.Item(
            7L, "MAT-7", "钢", LocalDate.of(2026, 9, 25), new BigDecimal("9.25"), BigDecimal.ONE,
            List.of(11L), List.of(12L), "EXPEDITE", List.of("ERP:STOCK:7:available_qty"));
        ShortageSimulationResult simulation = new ShortageSimulationResult(true, 1,
            LocalDate.of(2026, 9, 25), new BigDecimal("9.25"), List.of(item), kpis,
            List.of("ERP:STOCK:7:available_qty"), List.of("supplier pending"));
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        ShortageSimulationResult parsed = mapper.readValue(mapper.writeValueAsString(simulation),
            ShortageSimulationResult.class);
        assertTrue(parsed.hasRisk());
        assertEquals(1, parsed.riskMaterialCount());
        assertEquals(LocalDate.of(2026, 9, 25), parsed.earliestShortageDate());
        assertEquals(new BigDecimal("9.25"), parsed.totalGap());
        assertEquals(1, parsed.kpis().shortageMaterialCount());
        assertEquals(List.of("ERP:STOCK:7:available_qty"), parsed.evidenceRefs());
        assertEquals("java.util.List<java.lang.String>", ShortageSimulationResult.class
            .getRecordComponents()[6].getGenericType().getTypeName());
    }

    @Test
    void simulationAndExecutionDtosPreserveTypedFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        ApsScenarioCompareResult compared = mapper.readValue(
            "{\"scenarios\":[],\"warnings\":[\"NO_CAPACITY\"]}", ApsScenarioCompareResult.class);
        assertEquals(List.of("NO_CAPACITY"), compared.warnings());
        DecisionFreshnessResult freshness = mapper.readValue(
            "{\"fresh\":false,\"currentHash\":\"new\",\"changedFields\":[\"arrivals\"]}",
            DecisionFreshnessResult.class);
        assertEquals(List.of("arrivals"), freshness.changedFields());
        DecisionActionExecutionResult execution = mapper.readValue(
            "{\"success\":true,\"retryable\":false,\"resultBizType\":\"PURCHASE_REQUISITION\","
                + "\"resultBizId\":9007199254740993,\"resultBizCode\":\"PR-9\",\"result\":{},"
                + "\"errorCode\":null,\"errorMessage\":null}", DecisionActionExecutionResult.class);
        assertEquals(9007199254740993L, execution.resultBizId());
        RetrievalHit hit = mapper.readValue(
            "{\"knowledgeId\":1,\"documentId\":2,\"chunkId\":3,\"documentName\":\"doc\","
                + "\"content\":\"fact\",\"vectorScore\":0.9,\"keywordRank\":2,\"rerankScore\":0.8,"
                + "\"graphPath\":\"a/b\",\"version\":\"v1\",\"effectiveDate\":\"2026-09-23\","
                + "\"sourceType\":\"KNOWLEDGE\"}", RetrievalHit.class);
        assertEquals("fact", hit.content());
        assertEquals(LocalDate.of(2026, 9, 23), hit.effectiveDate());
        assertEquals(Long.class, RetrievalHit.class.getRecordComponents()[0].getType());
        assertEquals(String.class, DecisionWorkflowStartCommand.class.getRecordComponents()[2].getType());
    }

    @Test
    void workflowCommandAndPortsKeepAllExactEntryPoints() throws Exception {
        DecisionWorkflowStartCommand command = new ObjectMapper().readValue(
            "{\"runId\":9007199254740993,\"flowKey\":\"DELIVERY_RISK\",\"tenantId\":\"000023\","
                + "\"userId\":9,\"userName\":\"tester\",\"input\":{}}", DecisionWorkflowStartCommand.class);
        assertEquals("000023", command.tenantId());
        assertEquals(9007199254740993L, command.runId());
        assertEquals(Long.class, DecisionWorkflowPort.class
            .getMethod("startAnalysis", DecisionWorkflowStartCommand.class).getReturnType());
        assertEquals(Long.class, DecisionWorkflowPort.class
            .getMethod("startExecution", DecisionWorkflowStartCommand.class).getReturnType());
        assertEquals(boolean.class, DecisionWorkflowPort.class
            .getMethod("stop", Long.class, DecisionActor.class).getReturnType());
        assertEquals(Set.of("startAnalysis", "startExecution", "stop"), Arrays.stream(
            DecisionWorkflowPort.class.getDeclaredMethods()).map(method -> method.getName()).collect(
                Collectors.toSet()));
        assertEquals(boolean.class, IndustrialDecisionDataPort.class
            .getMethod("canAccess", DecisionBusinessRef.class, DecisionActor.class).getReturnType());
        assertEquals(DeliveryContextSnapshot.class, IndustrialDecisionDataPort.class
            .getMethod("loadDeliveryContext", DecisionBusinessRef.class, int.class, DecisionActor.class).getReturnType());
        assertEquals(ShortageSimulationResult.class, IndustrialDecisionDataPort.class
            .getMethod("simulateShortage", DeliveryContextSnapshot.class, int.class, Map.class,
                DecisionActor.class).getReturnType());
        assertEquals("java.util.Map<java.lang.String, java.lang.Object>", IndustrialDecisionDataPort.class
            .getMethod("simulateShortage", DeliveryContextSnapshot.class, int.class, Map.class,
                DecisionActor.class).getGenericParameterTypes()[2].getTypeName());
        assertEquals(ApsScenarioCompareResult.class, IndustrialDecisionDataPort.class
            .getMethod("simulateSchedules", DeliveryContextSnapshot.class, List.class, int.class,
                DecisionActor.class).getReturnType());
        assertEquals("java.util.List<java.lang.String>", IndustrialDecisionDataPort.class
            .getMethod("simulateSchedules", DeliveryContextSnapshot.class, List.class, int.class,
                DecisionActor.class).getGenericParameterTypes()[1].getTypeName());
        assertEquals(DecisionFreshnessResult.class, IndustrialDecisionDataPort.class
            .getMethod("checkFreshness", DecisionBusinessRef.class, String.class, DecisionActor.class).getReturnType());
        for (String operation : List.of("executeBusinessAction", "verifyBusinessAction", "compensateBusinessAction")) {
            Class<?>[] signature = operation.equals("executeBusinessAction")
                ? new Class<?>[]{DecisionActionProposal.class, DecisionActor.class}
                : new Class<?>[]{DecisionActionProposal.class, DecisionActionExecutionResult.class, DecisionActor.class};
            assertEquals(DecisionActionExecutionResult.class,
                IndustrialDecisionDataPort.class.getMethod(operation, signature).getReturnType(), operation);
        }
    }

    private static DecisionActionProposal proposal(long actionId, Map<String, Object> payload) {
        return new DecisionActionProposal(actionId, DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT,
            "draft", "shortage", new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, actionId, "WO-42"),
            payload, DecisionRiskLevel.HIGH, true, "pu:requisition:add", "action-idempotency-key",
            "purchaseRequisitionDraft", 1, List.of());
    }

    @Test
    void everyDtoHasExplicitSerializableTransportType() {
        for (Class<?> dto : List.of(DecisionActor.class, DecisionBusinessRef.class, DeliveryRiskRunRequest.class,
            DecisionRunAcceptedVo.class, DeliveryContextSnapshot.class, DecisionKpiSet.class,
            DecisionScenario.class, EvidenceItem.class, ShortageSimulationResult.class,
            ApsScenarioCompareResult.class, DecisionResult.class, DecisionActionProposal.class,
            DecisionApprovalRequest.class, DecisionOutcome.class, DecisionFreshnessResult.class,
            DecisionWorkflowStartCommand.class, DecisionActionExecutionResult.class, RetrievalHit.class,
            DeliveryContextSnapshot.OrderFact.class, DeliveryContextSnapshot.WorkOrderFact.class,
            DeliveryContextSnapshot.RoutingStepFact.class, DeliveryContextSnapshot.RequirementFact.class, DeliveryContextSnapshot.StockFact.class,
            DeliveryContextSnapshot.ArrivalFact.class, DeliveryContextSnapshot.SupplierMetricFact.class,
            ShortageSimulationResult.Item.class)) {
            assertTrue(Serializable.class.isAssignableFrom(dto), dto.getName());
            if (dto.isRecord()) {
                for (var component : dto.getRecordComponents()) {
                    assertFalse(Object.class.equals(component.getType()), dto.getName() + "." + component.getName());
                }
            }
        }
    }
}
