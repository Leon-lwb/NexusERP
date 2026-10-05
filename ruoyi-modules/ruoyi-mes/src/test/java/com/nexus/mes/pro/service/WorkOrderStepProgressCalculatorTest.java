package com.nexus.mes.pro.service;

import com.nexus.mes.pro.domain.ProFlowCard;
import com.nexus.mes.pro.domain.ProFlowCardStep;
import com.nexus.mes.pro.domain.ProWorkOrder;
import com.nexus.mes.pro.domain.ProWorkOrderStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工单步骤纯投影测试：反射入口保证实现尚不存在时仍能编译，并以断言失败验证红灯。
 */
class WorkOrderStepProgressCalculatorTest {

    private static final String CALCULATOR = "com.nexus.mes.pro.service.WorkOrderStepProgressCalculator";
    private static final String TENANT = "tenant-a";
    private static final Long WO_ID = 1L;

    @Test
    void exposesPublicStaticCalculatorAndProjectionRecord() {
        Method method = calculatorMethod();
        assertEquals(List.class, method.getReturnType());
        Class<?> projection = assertDoesNotThrow(() -> Class.forName(CALCULATOR + "$StepProjection"));
        assertTrue(projection.isRecord());
        assertTrue(Modifier.isPublic(projection.getModifiers()));
        assertEquals(List.of("stepId", "completedQty", "scrapQty", "stepStatus", "startTime", "endTime"),
            Arrays.stream(projection.getRecordComponents()).map(component -> component.getName()).toList());
        assertEquals(List.of(Long.class, BigDecimal.class, BigDecimal.class, String.class, Date.class, Date.class),
            Arrays.stream(projection.getRecordComponents()).map(component -> component.getType()).toList());
    }

    @Test
    void projectsTenPiecesPerStepInsteadOfTwentyFinishedPieces() {
        Fixture fixture = completeCard();
        fixture.wo().setCompletedQty(new BigDecimal("20"));
        fixture.woSteps().get(0).setCompletedQty(new BigDecimal("99"));
        List<?> result = calculate(fixture);
        assertEquals(2, result.size());
        assertStep(result.get(0), 11L, "10", "0", "COMPLETED");
        assertStep(result.get(1), 12L, "10", "0", "COMPLETED");
        assertEquals(new BigDecimal("20"), fixture.wo().getCompletedQty());
        assertEquals(new BigDecimal("99"), fixture.woSteps().get(0).getCompletedQty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREATED", "RELEASED", "IN_PROGRESS", "COMPLETED", "CLOSED"})
    void acceptsAllSupportedCardStatusesIncludingArchivedCards(String status) {
        Fixture fixture = completeCard();
        fixture.cards().get(0).setCardStatus(status);
        assertStep(calculate(fixture).get(0), 11L, "10", "0", "COMPLETED");
    }

    @Test
    void keepsPartiallyCoveredTerminalStepsInProgressWithoutEndTime() {
        Fixture fixture = completeCard();
        fixture.cards().get(0).setPlannedQty(new BigDecimal("4"));
        for (ProFlowCardStep step : fixture.cardSteps()) {
            step.setPlannedQty(new BigDecimal("4"));
            step.setGoodQty(new BigDecimal("4"));
            step.setActualEndTime(new Date(900L));
        }
        Object result = calculate(fixture).get(0);
        assertStep(result, 11L, "4", "0", "IN_PROGRESS");
        assertNull(value(result, "endTime"));
    }

    @Test
    void leavesUnstartedPartialCoveragePending() {
        Fixture fixture = completeCard();
        fixture.cards().get(0).setPlannedQty(new BigDecimal("4"));
        for (ProFlowCardStep step : fixture.cardSteps()) {
            step.setPlannedQty(new BigDecimal("4"));
            step.setStepStatus("PENDING");
            clearExecutionQuantities(step);
        }
        assertStep(calculate(fixture).get(0), 11L, "0", "0", "PENDING");
    }

    @Test
    void sumsMultipleCardsBySourceStepOnly() {
        Fixture fixture = splitCards();
        fixture.cardSteps().get(0).setGoodQty(new BigDecimal("3.0"));
        fixture.cardSteps().get(0).setScrapQty(new BigDecimal("1.0"));
        fixture.cardSteps().get(1).setGoodQty(new BigDecimal("2"));
        fixture.cardSteps().get(1).setScrapQty(new BigDecimal("1"));
        fixture.cardSteps().get(2).setGoodQty(new BigDecimal("5.00"));
        fixture.cardSteps().get(2).setScrapQty(new BigDecimal("1"));
        fixture.cardSteps().get(3).setGoodQty(new BigDecimal("4"));
        fixture.cardSteps().get(3).setScrapQty(new BigDecimal("1"));
        List<?> result = calculate(fixture);
        assertStep(result.get(0), 11L, "8", "2", "COMPLETED");
        assertStep(result.get(1), 12L, "6", "2", "COMPLETED");
    }

    @Test
    void usesSourceIdsRatherThanProcessOrSequenceAndPreservesInputOrder() {
        Fixture fixture = completeCard();
        fixture.woSteps().get(0).setSequenceNum(1);
        fixture.woSteps().get(1).setSequenceNum(1);
        fixture.woSteps().get(0).setProcessId(81L);
        fixture.woSteps().get(1).setProcessId(81L);
        fixture.cardSteps().get(1).setGoodQty(new BigDecimal("8"));
        fixture.cardSteps().get(1).setDefectQty(new BigDecimal("2"));
        List<?> result = calculate(fixture.wo(), List.of(fixture.woSteps().get(1), fixture.woSteps().get(0)),
            fixture.cards(), List.of(fixture.cardSteps().get(1), fixture.cardSteps().get(0)));
        assertStep(result.get(0), 12L, "8", "0", "COMPLETED");
        assertStep(result.get(1), 11L, "10", "0", "COMPLETED");
    }

    @Test
    void projectsFullyCoveredSkippedStepsWithoutInventingStartOrGoodQuantity() {
        Fixture fixture = splitCards();
        for (ProFlowCardStep step : fixture.cardSteps()) {
            step.setStepStatus("SKIPPED");
            clearExecutionQuantities(step);
            step.setActualEndTime(new Date(step.getStepRecordId()));
        }
        Object result = calculate(fixture).get(0);
        assertStep(result, 11L, "0", "0", "SKIPPED");
        assertNull(value(result, "startTime"));
        assertEquals(new Date(103L), value(result, "endTime"));
    }

    @Test
    void projectsMixedCompletedAndSkippedCardsAsCompleted() {
        Fixture fixture = splitCards();
        fixture.cardSteps().get(0).setStepStatus("SKIPPED");
        clearExecutionQuantities(fixture.cardSteps().get(0));
        assertStep(calculate(fixture).get(0), 11L, "6", "0", "COMPLETED");
    }

    @Test
    void keepsSkippedPartialCoverageInProgress() {
        Fixture fixture = completeCard();
        fixture.cards().get(0).setPlannedQty(new BigDecimal("4"));
        for (ProFlowCardStep step : fixture.cardSteps()) {
            step.setPlannedQty(new BigDecimal("4"));
            step.setStepStatus("SKIPPED");
            clearExecutionQuantities(step);
            step.setActualEndTime(new Date(900L));
        }
        Object result = calculate(fixture).get(0);
        assertStep(result, 11L, "0", "0", "IN_PROGRESS");
        assertNull(value(result, "endTime"));
    }

    @Test
    void keepsSkippedAndPendingCardsInProgressUntilAllClose() {
        Fixture fixture = splitCards();
        fixture.cardSteps().get(0).setStepStatus("SKIPPED");
        fixture.cardSteps().get(2).setStepStatus("PENDING");
        clearExecutionQuantities(fixture.cardSteps().get(0));
        clearExecutionQuantities(fixture.cardSteps().get(2));
        fixture.cardSteps().get(0).setActualEndTime(new Date(500L));
        Object result = calculate(fixture).get(0);
        assertStep(result, 11L, "0", "0", "IN_PROGRESS");
        assertNull(value(result, "endTime"));
    }

    @Test
    void neverCompletesWhileAnotherCardStepRemainsNonterminal() {
        Fixture fixture = splitCards();
        fixture.woSteps().get(0).setPlannedQty(new BigDecimal("4"));
        fixture.cardSteps().get(0).setActualEndTime(new Date(500L));
        fixture.cardSteps().get(2).setStepStatus("PENDING");
        clearExecutionQuantities(fixture.cardSteps().get(2));
        Object result = calculate(fixture).get(0);
        assertStep(result, 11L, "4", "0", "IN_PROGRESS");
        assertNull(value(result, "endTime"));
    }

    @Test
    void retainsDownstreamGoodQuantityAfterUpstreamScrap() {
        Fixture fixture = completeCard();
        fixture.cardSteps().get(0).setGoodQty(new BigDecimal("8"));
        fixture.cardSteps().get(0).setScrapQty(new BigDecimal("2"));
        fixture.cardSteps().get(1).setGoodQty(new BigDecimal("8"));
        List<?> result = calculate(fixture);
        assertStep(result.get(0), 11L, "8", "2", "COMPLETED");
        assertStep(result.get(1), 12L, "8", "0", "COMPLETED");
    }

    @ParameterizedTest
    @CsvSource({"PENDING,0,PENDING", "PENDING,2,IN_PROGRESS", "IN_PROGRESS,0,IN_PROGRESS"})
    void derivesProcessStatusFromActualExecution(String status, String good, String projectedStatus) {
        Fixture fixture = completeCard();
        fixture.cardSteps().get(0).setStepStatus(status);
        fixture.cardSteps().get(0).setGoodQty(new BigDecimal(good));
        assertStep(calculate(fixture).get(0), 11L, good, "0", projectedStatus);
    }

    @Test
    void treatsActualStartAsProgressWithoutInventingAnEnd() {
        Fixture fixture = completeCard();
        ProFlowCardStep step = fixture.cardSteps().get(0);
        step.setStepStatus("PENDING");
        step.setGoodQty(BigDecimal.ZERO);
        step.setActualStartTime(new Date(400L));
        Object result = calculate(fixture).get(0);
        assertStep(result, 11L, "0", "0", "IN_PROGRESS");
        assertEquals(new Date(400L), value(result, "startTime"));
        assertNull(value(result, "endTime"));
    }

    @Test
    void selectsEarliestActualStartAndLatestActualEndOnlyWhenClosed() {
        Fixture fixture = splitCards();
        fixture.cardSteps().get(0).setActualStartTime(new Date(500L));
        fixture.cardSteps().get(0).setActualEndTime(new Date(700L));
        fixture.cardSteps().get(2).setActualStartTime(new Date(300L));
        fixture.cardSteps().get(2).setActualEndTime(new Date(900L));
        fixture.cardSteps().get(1).setActualStartTime(new Date(100L));
        fixture.cardSteps().get(1).setActualEndTime(new Date(1500L));
        Object result = calculate(fixture).get(0);
        assertEquals(new Date(300L), value(result, "startTime"));
        assertEquals(new Date(900L), value(result, "endTime"));
    }

    @Test
    void neverFallsBackToWorkOrderOrProjectedDatesWhenActualDatesAreMissing() {
        Fixture fixture = completeCard();
        fixture.wo().setActualStartDate(new Date(100L));
        fixture.wo().setActualEndDate(new Date(900L));
        fixture.woSteps().get(0).setStartTime(new Date(100L));
        fixture.woSteps().get(0).setEndTime(new Date(900L));
        Object result = calculate(fixture).get(0);
        assertNull(value(result, "startTime"));
        assertNull(value(result, "endTime"));
    }

    @Test
    void defensivelyCopiesDateInputsAndAccessorResults() {
        Fixture fixture = completeCard();
        Date start = new Date(100L);
        Date end = new Date(900L);
        fixture.cardSteps().get(0).setActualStartTime(start);
        fixture.cardSteps().get(0).setActualEndTime(end);
        Object result = calculate(fixture).get(0);
        start.setTime(2000L);
        end.setTime(3000L);
        assertEquals(new Date(100L), value(result, "startTime"));
        assertEquals(new Date(900L), value(result, "endTime"));
        ((Date) value(result, "startTime")).setTime(4000L);
        ((Date) value(result, "endTime")).setTime(5000L);
        assertEquals(new Date(100L), value(result, "startTime"));
        assertEquals(new Date(900L), value(result, "endTime"));
    }

    @Test
    void defensivelyCopiesDatesInPublicRecordConstructor() {
        Class<?> projection = assertDoesNotThrow(() -> Class.forName(CALCULATOR + "$StepProjection"));
        Date start = new Date(100L);
        Date end = new Date(900L);
        Object result = assertDoesNotThrow(() -> projection
            .getConstructor(Long.class, BigDecimal.class, BigDecimal.class, String.class, Date.class, Date.class)
            .newInstance(11L, BigDecimal.ONE, BigDecimal.ZERO, "COMPLETED", start, end));
        start.setTime(2000L);
        end.setTime(3000L);
        assertEquals(new Date(100L), value(result, "startTime"));
        assertEquals(new Date(900L), value(result, "endTime"));
    }

    @Test
    void leavesAllPlanningAndTechnicalAttributesUntouched() {
        Fixture fixture = completeCard();
        ProWorkOrderStep step = fixture.woSteps().get(0);
        step.setStepType("FLASH");
        step.setContentRefType("FIRMWARE");
        step.setContentRefId(77L);
        step.setStepFlashConfig("配置快照");
        step.setFirmwareSource("PACKAGE");
        step.setProcessId(81L);
        step.setWorkcenterId(91L);
        step.setStartTime(new Date(100L));
        step.setEndTime(new Date(900L));
        String before = fixture.toString();
        List<?> first = calculate(fixture);
        assertEquals(before, fixture.toString());
        assertEquals(first, calculate(fixture));
    }

    @Test
    void allowsPendingWithoutCardsOnlyWhenNoExecutionHasOccurred() {
        Fixture fixture = completeCard();
        fixture.cards().clear();
        fixture.cardSteps().clear();
        fixture.woSteps().get(0).setStepStatus("READY");
        List<?> result = calculate(fixture);
        assertStep(result.get(0), 11L, "0", "0", "PENDING");
        assertStep(result.get(1), 12L, "0", "0", "PENDING");
        assertNull(value(result.get(0), "startTime"));
        assertNull(value(result.get(0), "endTime"));
    }

    @ParameterizedTest(name = "缺失来源：{0}")
    @MethodSource("missingSourceCases")
    void rejectsExecutedOrdersWithoutCards(String description, Consumer<Fixture> corrupt) {
        Fixture fixture = completeCard();
        fixture.cards().clear();
        fixture.cardSteps().clear();
        corrupt.accept(fixture);
        assertThrows(IllegalArgumentException.class, () -> calculate(fixture), description);
    }

    private static Stream<Arguments> missingSourceCases() {
        return Stream.of(
            Arguments.of("工单已完工", (Consumer<Fixture>) f -> f.wo().setWoStatus("COMPLETED")),
            Arguments.of("工单已归档", (Consumer<Fixture>) f -> f.wo().setWoStatus("CLOSED")),
            Arguments.of("工单进行中", (Consumer<Fixture>) f -> f.wo().setWoStatus("IN_PROGRESS")),
            Arguments.of("工单已有完成量", (Consumer<Fixture>) f -> f.wo().setCompletedQty(BigDecimal.ONE)),
            Arguments.of("工单已有报废量", (Consumer<Fixture>) f -> f.wo().setScrapQty(BigDecimal.ONE)),
            Arguments.of("工单已经开工", (Consumer<Fixture>) f -> f.wo().setActualStartDate(new Date(100L))),
            Arguments.of("工序已有完成量", (Consumer<Fixture>) f -> f.woSteps().get(0).setCompletedQty(BigDecimal.ONE)),
            Arguments.of("工序已有报废量", (Consumer<Fixture>) f -> f.woSteps().get(0).setScrapQty(BigDecimal.ONE)),
            Arguments.of("工序已完工", (Consumer<Fixture>) f -> f.woSteps().get(0).setStepStatus("COMPLETED")),
            Arguments.of("工序已跳过", (Consumer<Fixture>) f -> f.woSteps().get(0).setStepStatus("SKIPPED")),
            Arguments.of("工序已经开工", (Consumer<Fixture>) f -> f.woSteps().get(0).setStartTime(new Date(100L)))
        );
    }

    @ParameterizedTest(name = "非法来源：{0}")
    @MethodSource("invalidSourceCases")
    void rejectsInvalidReferencesAndUntrustedQuantities(String description, Consumer<Fixture> corrupt) {
        Fixture fixture = completeCard();
        corrupt.accept(fixture);
        assertThrows(IllegalArgumentException.class, () -> calculate(fixture), description);
    }

    private static Stream<Arguments> invalidSourceCases() {
        return Stream.of(
            Arguments.of("工单主键为空", (Consumer<Fixture>) f -> f.wo().setMesWoId(null)),
            Arguments.of("工单主键为零", (Consumer<Fixture>) f -> f.wo().setMesWoId(0L)),
            Arguments.of("工单租户为空", (Consumer<Fixture>) f -> f.wo().setTenantId(null)),
            Arguments.of("工单租户空白", (Consumer<Fixture>) f -> f.wo().setTenantId(" ")),
            Arguments.of("工单计划为空", (Consumer<Fixture>) f -> f.wo().setPlannedQty(null)),
            Arguments.of("工单计划为负", (Consumer<Fixture>) f -> f.wo().setPlannedQty(BigDecimal.ONE.negate())),
            Arguments.of("工单完成量为负", (Consumer<Fixture>) f -> f.wo().setCompletedQty(BigDecimal.ONE.negate())),
            Arguments.of("工序主键为负", (Consumer<Fixture>) f -> f.woSteps().get(0).setStepId(-11L)),
            Arguments.of("工序计划为零", (Consumer<Fixture>) f -> f.woSteps().get(0).setPlannedQty(BigDecimal.ZERO)),
            Arguments.of("工序计划为空", (Consumer<Fixture>) f -> f.woSteps().get(0).setPlannedQty(null)),
            Arguments.of("工序计划为负", (Consumer<Fixture>) f -> f.woSteps().get(0).setPlannedQty(BigDecimal.ONE.negate())),
            Arguments.of("工序完成量为负", (Consumer<Fixture>) f -> f.woSteps().get(0).setCompletedQty(BigDecimal.ONE.negate())),
            Arguments.of("工序跨租户", (Consumer<Fixture>) f -> f.woSteps().get(0).setTenantId("tenant-b")),
            Arguments.of("工序错父工单", (Consumer<Fixture>) f -> f.woSteps().get(0).setMesWoId(2L)),
            Arguments.of("工序主键重复", (Consumer<Fixture>) f -> f.woSteps().add(f.woSteps().get(0))),
            Arguments.of("流转卡主键为零", (Consumer<Fixture>) f -> f.cards().get(0).setCardId(0L)),
            Arguments.of("流转卡跨租户", (Consumer<Fixture>) f -> f.cards().get(0).setTenantId("tenant-b")),
            Arguments.of("流转卡错父工单", (Consumer<Fixture>) f -> f.cards().get(0).setMesWoId(2L)),
            Arguments.of("流转卡计划为空", (Consumer<Fixture>) f -> f.cards().get(0).setPlannedQty(null)),
            Arguments.of("流转卡计划为负", (Consumer<Fixture>) f -> f.cards().get(0).setPlannedQty(BigDecimal.ONE.negate())),
            Arguments.of("流转卡状态未知", (Consumer<Fixture>) f -> f.cards().get(0).setCardStatus("CANCELLED")),
            Arguments.of("流转卡状态为空", (Consumer<Fixture>) f -> f.cards().get(0).setCardStatus(null)),
            Arguments.of("流转卡主键重复", (Consumer<Fixture>) f -> f.cards().add(f.cards().get(0))),
            Arguments.of("建卡计划超工单", (Consumer<Fixture>) f -> f.cards().get(0).setPlannedQty(new BigDecimal("11"))),
            Arguments.of("卡工序主键为空", (Consumer<Fixture>) f -> f.cardSteps().get(0).setStepRecordId(null)),
            Arguments.of("卡工序主键为负", (Consumer<Fixture>) f -> f.cardSteps().get(0).setStepRecordId(-101L)),
            Arguments.of("卡工序主键重复", (Consumer<Fixture>) f -> f.cardSteps().get(1).setStepRecordId(101L)),
            Arguments.of("卡工序跨租户", (Consumer<Fixture>) f -> f.cardSteps().get(0).setTenantId("tenant-b")),
            Arguments.of("卡工序错父工单", (Consumer<Fixture>) f -> f.cardSteps().get(0).setMesWoId(2L)),
            Arguments.of("卡工序来源为空", (Consumer<Fixture>) f -> f.cardSteps().get(0).setWoStepId(null)),
            Arguments.of("卡工序来源为零", (Consumer<Fixture>) f -> f.cardSteps().get(0).setWoStepId(0L)),
            Arguments.of("卡工序来源未知", (Consumer<Fixture>) f -> f.cardSteps().get(0).setWoStepId(999L)),
            Arguments.of("卡工序父卡为空", (Consumer<Fixture>) f -> f.cardSteps().get(0).setCardId(null)),
            Arguments.of("卡工序父卡未知", (Consumer<Fixture>) f -> f.cardSteps().get(0).setCardId(999L)),
            Arguments.of("卡内来源重复映射", (Consumer<Fixture>) f -> f.cardSteps().get(1).setWoStepId(11L)),
            Arguments.of("流转卡缺少工序", (Consumer<Fixture>) f -> f.cardSteps().remove(1)),
            Arguments.of("无卡但存在卡工序", (Consumer<Fixture>) f -> f.cards().clear()),
            Arguments.of("卡工序计划为负", (Consumer<Fixture>) f -> f.cardSteps().get(0).setPlannedQty(BigDecimal.ONE.negate())),
            Arguments.of("卡工序良品为负", (Consumer<Fixture>) f -> f.cardSteps().get(0).setGoodQty(BigDecimal.ONE.negate())),
            Arguments.of("卡工序不良品为负", (Consumer<Fixture>) f -> f.cardSteps().get(0).setDefectQty(BigDecimal.ONE.negate())),
            Arguments.of("卡工序报废为负", (Consumer<Fixture>) f -> f.cardSteps().get(0).setScrapQty(BigDecimal.ONE.negate())),
            Arguments.of("卡工序执行总量超卡计划", (Consumer<Fixture>) f -> f.cardSteps().get(0).setDefectQty(BigDecimal.ONE)),
            Arguments.of("卡工序状态未知", (Consumer<Fixture>) f -> f.cardSteps().get(0).setStepStatus("READY")),
            Arguments.of("卡工序状态为空", (Consumer<Fixture>) f -> f.cardSteps().get(0).setStepStatus(null)),
            Arguments.of("工序列表含空元素", (Consumer<Fixture>) f -> f.woSteps().add(null)),
            Arguments.of("流转卡列表含空元素", (Consumer<Fixture>) f -> f.cards().add(null)),
            Arguments.of("卡工序列表含空元素", (Consumer<Fixture>) f -> f.cardSteps().add(null))
        );
    }

    @ParameterizedTest
    @CsvSource({"COMPLETED,goodQty", "COMPLETED,defectQty", "COMPLETED,scrapQty",
        "IN_PROGRESS,goodQty", "IN_PROGRESS,defectQty", "IN_PROGRESS,scrapQty"})
    void requiresCompleteExecutionQuantitiesForExecutingOrCompletedSteps(String status, String field) {
        Fixture fixture = completeCard();
        ProFlowCardStep step = fixture.cardSteps().get(0);
        step.setStepStatus(status);
        switch (field) {
            case "goodQty" -> step.setGoodQty(null);
            case "defectQty" -> step.setDefectQty(null);
            case "scrapQty" -> step.setScrapQty(null);
            default -> fail("未定义的测试字段");
        }
        assertThrows(IllegalArgumentException.class, () -> calculate(fixture));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "SKIPPED"})
    void rejectsNullQuantitiesWhenPendingOrSkippedStepsAlreadyExecuted(String status) {
        Fixture fixture = completeCard();
        ProFlowCardStep step = fixture.cardSteps().get(0);
        step.setStepStatus(status);
        clearExecutionQuantities(step);
        step.setActualStartTime(new Date(100L));
        assertThrows(IllegalArgumentException.class, () -> calculate(fixture));
        step.setActualStartTime(null);
        step.setGoodQty(BigDecimal.ONE);
        assertThrows(IllegalArgumentException.class, () -> calculate(fixture));
    }

    @Test
    void rejectsNullRootAndLists() {
        Fixture fixture = completeCard();
        assertThrows(IllegalArgumentException.class, () -> calculate(null, fixture.woSteps(), fixture.cards(), fixture.cardSteps()));
        assertThrows(IllegalArgumentException.class, () -> calculate(fixture.wo(), null, fixture.cards(), fixture.cardSteps()));
        assertThrows(IllegalArgumentException.class, () -> calculate(fixture.wo(), fixture.woSteps(), null, fixture.cardSteps()));
        assertThrows(IllegalArgumentException.class, () -> calculate(fixture.wo(), fixture.woSteps(), fixture.cards(), null));
    }

    private static Fixture completeCard() {
        ProWorkOrder wo = new ProWorkOrder();
        wo.setMesWoId(WO_ID);
        wo.setTenantId(TENANT);
        wo.setPlannedQty(new BigDecimal("10"));
        wo.setCompletedQty(BigDecimal.ZERO);
        wo.setScrapQty(BigDecimal.ZERO);
        wo.setWoStatus("RELEASED");
        ProFlowCard card = card(21L, "10");
        return new Fixture(wo, new ArrayList<>(List.of(woStep(11L), woStep(12L))),
            new ArrayList<>(List.of(card)),
            new ArrayList<>(List.of(cardStep(101L, card, 11L), cardStep(102L, card, 12L))));
    }

    private static Fixture splitCards() {
        Fixture fixture = completeCard();
        ProFlowCard first = card(21L, "4");
        ProFlowCard second = card(22L, "6");
        fixture.cards().clear();
        fixture.cards().addAll(List.of(first, second));
        fixture.cardSteps().clear();
        fixture.cardSteps().addAll(List.of(cardStep(101L, first, 11L), cardStep(102L, first, 12L),
            cardStep(103L, second, 11L), cardStep(104L, second, 12L)));
        return fixture;
    }

    private static ProWorkOrderStep woStep(Long id) {
        ProWorkOrderStep step = new ProWorkOrderStep();
        step.setStepId(id);
        step.setMesWoId(WO_ID);
        step.setTenantId(TENANT);
        step.setPlannedQty(new BigDecimal("10"));
        step.setCompletedQty(BigDecimal.ZERO);
        step.setScrapQty(BigDecimal.ZERO);
        step.setStepStatus("PENDING");
        return step;
    }

    private static ProFlowCard card(Long id, String planned) {
        ProFlowCard card = new ProFlowCard();
        card.setCardId(id);
        card.setMesWoId(WO_ID);
        card.setTenantId(TENANT);
        card.setPlannedQty(new BigDecimal(planned));
        card.setCardStatus("COMPLETED");
        return card;
    }

    private static ProFlowCardStep cardStep(Long id, ProFlowCard card, Long woStepId) {
        ProFlowCardStep step = new ProFlowCardStep();
        step.setStepRecordId(id);
        step.setCardId(card.getCardId());
        step.setMesWoId(WO_ID);
        step.setTenantId(TENANT);
        step.setWoStepId(woStepId);
        step.setPlannedQty(card.getPlannedQty());
        step.setGoodQty(card.getPlannedQty());
        step.setDefectQty(BigDecimal.ZERO);
        step.setScrapQty(BigDecimal.ZERO);
        step.setStepStatus("COMPLETED");
        return step;
    }

    private static void clearExecutionQuantities(ProFlowCardStep step) {
        step.setGoodQty(null);
        step.setDefectQty(null);
        step.setScrapQty(null);
    }

    private static Method calculatorMethod() {
        Class<?> type = assertDoesNotThrow(() -> Class.forName(CALCULATOR), "必须提供纯计算器，缺失应表现为断言红灯");
        Method method = assertDoesNotThrow(() -> type.getMethod("calculate", ProWorkOrder.class, List.class, List.class, List.class),
            "必须提供公开计算入口");
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));
        return method;
    }

    private static List<?> calculate(Fixture fixture) {
        return calculate(fixture.wo(), fixture.woSteps(), fixture.cards(), fixture.cardSteps());
    }

    private static List<?> calculate(ProWorkOrder wo, List<ProWorkOrderStep> woSteps,
                                     List<ProFlowCard> cards, List<ProFlowCardStep> cardSteps) {
        try {
            return assertInstanceOf(List.class, calculatorMethod().invoke(null, wo, woSteps, cards, cardSteps));
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            if (exception.getCause() instanceof Error cause) {
                throw cause;
            }
            throw new AssertionError("计算器不应抛出受检异常", exception.getCause());
        } catch (IllegalAccessException exception) {
            throw new AssertionError("计算器入口必须可公开访问", exception);
        }
    }

    private static Object value(Object projection, String accessor) {
        return assertDoesNotThrow(() -> projection.getClass().getMethod(accessor).invoke(projection), "投影必须公开只读属性");
    }

    private static void assertStep(Object projection, Long stepId, String good, String scrap, String status) {
        assertEquals(stepId, value(projection, "stepId"));
        assertEquals(0, new BigDecimal(good).compareTo(assertInstanceOf(BigDecimal.class, value(projection, "completedQty"))));
        assertEquals(0, new BigDecimal(scrap).compareTo(assertInstanceOf(BigDecimal.class, value(projection, "scrapQty"))));
        assertEquals(status, value(projection, "stepStatus"));
    }

    private record Fixture(ProWorkOrder wo, List<ProWorkOrderStep> woSteps,
                           List<ProFlowCard> cards, List<ProFlowCardStep> cardSteps) {
    }
}
