package com.nexus.erp.prd.schedule;

import com.nexus.erp.prd.schedule.ScheduleModel.ResourceLoad;
import com.nexus.erp.prd.schedule.ScheduleModel.ScheduleInput;
import com.nexus.erp.prd.schedule.ScheduleModel.ScheduleResult;
import com.nexus.erp.prd.schedule.ScheduleModel.ScheduledTask;
import com.nexus.erp.prd.schedule.ScheduleModel.StepInfo;
import com.nexus.erp.prd.schedule.ScheduleModel.Strategy;
import com.nexus.erp.prd.schedule.ScheduleModel.WoInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * APS 排程引擎单元测试（纯算法，不依赖 Spring 或数据库）
 *
 * @author nexus
 */
@Tag("dev")
class PrdScheduleEngineTest {

    private static final LocalDate D1 = LocalDate.of(2026, 9, 1);

    private WoInfo wo(Long id, Long materialId, BigDecimal qty, LocalDate due, Long priority) {
        WoInfo wo = new WoInfo();
        wo.setWoId(id);
        wo.setWoNo("WO-00" + id);
        wo.setMaterialId(materialId);
        wo.setMaterialCode("M" + materialId);
        wo.setMaterialName("物料" + materialId);
        wo.setRemainingQty(qty);
        wo.setPriority(priority);
        wo.setDueDate(due);
        wo.setWorkshopId(100L);
        wo.setWorkshopName("一车间");
        return wo;
    }

    private StepInfo step(int seq, Long workstationId, double standardHours, double setupHours) {
        StepInfo s = new StepInfo();
        s.setStepId((long) seq);
        s.setSeqNum(seq);
        s.setProcessCode("P" + seq);
        s.setProcessName("工序" + seq);
        s.setWorkstationId(workstationId);
        s.setWorkstationName(workstationId == null ? null : "工位" + workstationId);
        s.setStandardHours(BigDecimal.valueOf(standardHours));
        s.setSetupHours(BigDecimal.valueOf(setupHours));
        s.setIsOutsource(0);
        return s;
    }

    private ScheduleInput input(List<WoInfo> wos, Map<Long, List<StepInfo>> routings,
                                Map<LocalDate, Map<String, BigDecimal>> capacity, LocalDate start, int horizon, Strategy strategy) {
        ScheduleInput in = new ScheduleInput();
        in.setWorkOrders(wos);
        in.setRoutingProvider(routings::get);
        in.setCapacityByDate(capacity);
        in.setHolidays(Set.of());
        in.setStartDate(start);
        in.setHorizonDays(horizon);
        in.setStrategy(strategy);
        return in;
    }

    /**
     * EDD: 交期晚的工单在同资源上排后(无产能日历默认 8h/日, 首工单占满 D1)
     */
    @Test
    void testEddOrdering() {
        /** 交期 D1.plusDays(5) 的工单先排(占满 D1 8h), 交期 D1.plusDays(10) 的后排 */
        WoInfo early = wo(1L, 11L, BigDecimal.TEN, D1.plusDays(5), 5L);
        WoInfo late = wo(2L, 12L, BigDecimal.TEN, D1.plusDays(10), 5L);
        Map<Long, List<StepInfo>> routings = Map.of(
            11L, List.of(step(1, 200L, 0.8, 0)),
            12L, List.of(step(1, 200L, 1.0, 0))
        );
        ScheduleResult r = PrdScheduleEngine.schedule(input(List.of(late, early), routings, Map.of(), D1, 14, Strategy.EDD));

        assertEquals(2, r.getTasks().size());
        ScheduledTask t1 = r.getTasks().stream().filter(t -> t.getWo().getWoId() == 1L).findFirst().orElseThrow();
        ScheduledTask t2 = r.getTasks().stream().filter(t -> t.getWo().getWoId() == 2L).findFirst().orElseThrow();
        assertEquals(D1, t1.getStartDay());
        /** 工单1占满 D1 的 8h, 工单2只能从 D2 开始 */
        assertEquals(D1.plusDays(1), t2.getStartDay());
        assertFalse(t1.isDelay());
        assertFalse(t2.isDelay());
    }

    @Test
    void processHoursExcludesSetupButLoadAndTotalIncludeIt() {
        WoInfo order = wo(1L, 11L, BigDecimal.ONE, D1.plusDays(5), 1L);
        String resource = ScheduleModel.resourceKey("WORKSTATION", 200L);
        ScheduleResult result = PrdScheduleEngine.schedule(input(List.of(order),
            Map.of(11L, List.of(step(1, 200L, 0.07, 0.10))),
            Map.of(D1, Map.of(resource, new BigDecimal("0.17"))), D1, 1, Strategy.EDD));

        ScheduledTask task = result.getTasks().get(0);
        assertEquals(0, new BigDecimal("0.07").compareTo(task.getProcessHours()));
        assertEquals(0, new BigDecimal("0.10").compareTo(task.getSetupHours()));
        assertEquals(0, new BigDecimal("0.17").compareTo(result.getTotalHours()));
        assertEquals(0, new BigDecimal("0.17").compareTo(result.getLoads().get(0).getBookedHours()));
    }

    @Test
    void setupOnlyStepConsumesCapacityAndDelaysNextWorkOrder() {
        WoInfo first = wo(1L, 11L, BigDecimal.ONE, D1.plusDays(5), 1L);
        WoInfo second = wo(2L, 12L, BigDecimal.ONE, D1.plusDays(6), 1L);
        String resource = ScheduleModel.resourceKey("WORKSTATION", 200L);
        ScheduleResult result = PrdScheduleEngine.schedule(input(List.of(first, second),
            Map.of(11L, List.of(step(1, 200L, 0, 0.10)),
                12L, List.of(step(1, 200L, 0.10, 0))),
            Map.of(D1, Map.of(resource, new BigDecimal("0.10")),
                D1.plusDays(1), Map.of(resource, new BigDecimal("0.10"))),
            D1, 2, Strategy.EDD));

        assertEquals(0, BigDecimal.ZERO.compareTo(result.getTasks().get(0).getProcessHours()));
        assertEquals(D1.plusDays(1), result.getTasks().get(1).getStartDay());
        assertEquals(0, new BigDecimal("0.20").compareTo(result.getTotalHours()));
        assertEquals(0, new BigDecimal("0.20").compareTo(result.getLoads().get(0).getBookedHours()));
    }

    /**
     * 同资源不超产能且跨日拆分: 16h 任务在 8h/日下占 2 天, 负荷统计 booked=16
     */
    @Test
    void testCapacitySplitAcrossDays() {
        WoInfo wo = wo(1L, 11L, BigDecimal.TEN, D1.plusDays(30), 1L);
        Map<Long, List<StepInfo>> routings = Map.of(11L, List.of(step(1, 200L, 1.6, 0)));
        ScheduleResult r = PrdScheduleEngine.schedule(input(List.of(wo), routings, Map.of(), D1, 5, Strategy.EDD));

        assertEquals(1, r.getTasks().size());
        ScheduledTask t = r.getTasks().get(0);
        assertEquals(D1, t.getStartDay());
        assertEquals(D1.plusDays(1), t.getEndDay());
        assertEquals(2, t.getDurationDays());
        assertEquals(0, BigDecimal.valueOf(16).compareTo(t.getProcessHours()));
        assertEquals(1, r.getLoads().size());
        ResourceLoad load = r.getLoads().get(0);
        assertEquals(0, BigDecimal.valueOf(16).compareTo(load.getBookedHours()));
        /** 5 天 × 8h = 40h 可用, 16h 已占 = 40% */
        assertEquals(0, BigDecimal.valueOf(40).compareTo(load.getAvailableHours()));
        assertEquals(0, BigDecimal.valueOf(40.0).compareTo(load.getRate()));
    }

    /**
     * 产能日历生效: 日历剩余 4h 优先于默认 8h, 用尽后回到默认
     */
    @Test
    void testCalendarCapacityRespected() {
        WoInfo wo = wo(1L, 11L, BigDecimal.TEN, D1.plusDays(30), 1L);
        Map<Long, List<StepInfo>> routings = Map.of(11L, List.of(step(1, 200L, 1.0, 0)));
        /** D1 工位200 仅剩 4h → 10h 任务: D1 占 4h + D2 占 6h */
        Map<LocalDate, Map<String, BigDecimal>> capacity = Map.of(
            D1, Map.of(ScheduleModel.resourceKey("WORKSTATION", 200L), BigDecimal.valueOf(4))
        );
        ScheduleResult r = PrdScheduleEngine.schedule(input(List.of(wo), routings, capacity, D1, 5, Strategy.EDD));

        ScheduledTask t = r.getTasks().get(0);
        assertEquals(D1, t.getStartDay());
        assertEquals(D1.plusDays(1), t.getEndDay());
        assertEquals(0, BigDecimal.valueOf(4).compareTo(r.getLoads().get(0).getBookedHours().subtract(BigDecimal.valueOf(6))));
    }

    /**
     * 拖期: 交期 D1 但任务 16h 拖到 D2 完成 → 全部任务打拖期标记, 汇总拖期数 1
     */
    @Test
    void testDelayFlag() {
        WoInfo wo = wo(1L, 11L, BigDecimal.TEN, D1, 1L);
        Map<Long, List<StepInfo>> routings = Map.of(11L, List.of(step(1, 200L, 1.6, 0)));
        ScheduleResult r = PrdScheduleEngine.schedule(input(List.of(wo), routings, Map.of(), D1, 5, Strategy.EDD));

        assertEquals(1, r.getDelayedWoCount());
        assertTrue(r.getTasks().get(0).isDelay());
        assertEquals(D1.plusDays(1), r.getMakespan());
    }

    /**
     * 工序排序与同工单不重叠: 2 道工序在同一工位时第二道在前序完成后开始
     */
    @Test
    void testSequentialSteps() {
        WoInfo wo = wo(1L, 11L, BigDecimal.valueOf(10), D1.plusDays(30), 1L);
        Map<Long, List<StepInfo>> routings = Map.of(11L, List.of(
            step(1, 200L, 0.8, 0),
            step(2, 200L, 1.0, 0)
        ));
        ScheduleResult r = PrdScheduleEngine.schedule(input(List.of(wo), routings, Map.of(), D1, 10, Strategy.EDD));

        assertEquals(2, r.getTasks().size());
        ScheduledTask first = r.getTasks().get(0);
        ScheduledTask second = r.getTasks().get(1);
        assertEquals(1, first.getStep().getSeqNum());
        assertEquals(D1, first.getStartDay());
        /** 第一道占满 D1(8h), 第二道从 D2 开始 */
        assertEquals(D1.plusDays(1), second.getStartDay());
        assertEquals(2, second.getStep().getSeqNum());
    }

    /**
     * 委外工序不占资源产能, 按 等待+转移 时间折算日历天数
     */
    @Test
    void testOutsourceConsumesNoCapacity() {
        WoInfo wo = wo(1L, 11L, BigDecimal.TEN, D1.plusDays(30), 1L);
        StepInfo outsource = step(1, null, 0, 0);
        outsource.setIsOutsource(1);
        outsource.setWaitTime(BigDecimal.valueOf(48));
        outsource.setTransferTime(BigDecimal.valueOf(10));
        Map<Long, List<StepInfo>> routings = Map.of(11L, List.of(outsource));
        ScheduleResult r = PrdScheduleEngine.schedule(input(List.of(wo), routings, Map.of(), D1, 5, Strategy.EDD));

        ScheduledTask t = r.getTasks().get(0);
        assertEquals("OUTSOURCE", t.getResourceType());
        assertNull(t.getResourceId());
        /** (48+10)/24 向上取整 = 3 天 */
        assertEquals(D1.plusDays(2), t.getEndDay());
        assertEquals(3, t.getDurationDays());
        assertTrue(r.getLoads().isEmpty());
    }

    /**
     * 无工艺路线/数量非法的工单跳过并计数
     */
    @Test
    void testSkipWithoutRouting() {
        WoInfo noRouting = wo(1L, 11L, BigDecimal.TEN, D1.plusDays(5), 1L);
        WoInfo zeroQty = wo(2L, 12L, BigDecimal.ZERO, D1.plusDays(5), 1L);
        Map<Long, List<StepInfo>> routings = new HashMap<>();
        ScheduleResult r = PrdScheduleEngine.schedule(input(List.of(noRouting, zeroQty), routings, Map.of(), D1, 5, Strategy.EDD));

        assertTrue(r.getTasks().isEmpty());
        assertEquals(2, r.getSkipped().size());
        assertEquals(0, BigDecimal.ZERO.compareTo(r.getTotalHours()));
    }

    /**
     * PRIORITY 策略: 优先级小者先排(即便交期更晚)
     */
    @Test
    void testPriorityStrategy() {
        WoInfo urgent = wo(1L, 11L, BigDecimal.TEN, D1.plusDays(10), 1L);
        WoInfo normal = wo(2L, 12L, BigDecimal.TEN, D1.plusDays(5), 9L);
        Map<Long, List<StepInfo>> routings = Map.of(
            11L, List.of(step(1, 200L, 0.8, 0)),
            12L, List.of(step(1, 200L, 1.0, 0))
        );
        ScheduleResult r = PrdScheduleEngine.schedule(input(List.of(urgent, normal), routings, Map.of(), D1, 14, Strategy.PRIORITY));

        ScheduledTask first = r.getTasks().get(0);
        assertEquals(1L, first.getWo().getWoId());
        assertEquals(D1, first.getStartDay());
    }

    /**
     * 资源名称回退: 步骤名称/编码为空串时依次回退 编码 → "工作站+ID"; 车间名空串回退 "车间+ID"
     */
    @Test
    void testResourceNameBlankFallback() {
        /** 名称空串 + 编码空串 → 回退 "工作站{ID}" */
        StepInfo blankBoth = step(1, 300L, 1.0, 0);
        blankBoth.setWorkstationName("");
        blankBoth.setWorkstationCode("");
        /** 名称空串 + 编码有值 → 回退编码 */
        StepInfo blankName = step(1, 301L, 1.0, 0);
        blankName.setWorkstationName("");
        blankName.setWorkstationCode("WS-CODE");
        Map<Long, List<StepInfo>> routings = new HashMap<>();
        routings.put(11L, List.of(blankBoth, blankName));

        WoInfo wo = wo(1L, 11L, BigDecimal.TEN, D1.plusDays(30), 1L);
        wo.setWorkshopName("");
        ScheduleResult r = PrdScheduleEngine.schedule(input(List.of(wo), routings, Map.of(), D1, 14, Strategy.EDD));

        assertEquals(2, r.getTasks().size());
        assertEquals("工作站300", r.getTasks().get(0).getResourceName());
        assertEquals("WS-CODE", r.getTasks().get(1).getResourceName());
        /** 车间名空串的工单(步骤无工作站时)回退 "车间{ID}" — 通过 0 工时外委场景外验证: */
        StepInfo noStation = step(1, null, 0.5, 0);
        Map<Long, List<StepInfo>> routings2 = Map.of(12L, List.of(noStation));
        WoInfo wo2 = wo(2L, 12L, BigDecimal.TEN, D1.plusDays(30), 1L);
        wo2.setWorkshopName("");
        ScheduleResult r2 = PrdScheduleEngine.schedule(input(List.of(wo2), routings2, Map.of(), D1, 14, Strategy.EDD));
        assertEquals("车间100", r2.getTasks().get(0).getResourceName());
    }
    @Test
    void dailyLoadsPreserveExactDaysAndNeverBookAnInterveningHoliday() throws ReflectiveOperationException {
        WoInfo order = wo(61L, 901L, BigDecimal.ONE, D1.plusDays(8), 1L);
        String resource = ScheduleModel.resourceKey("WORKSTATION", 200L);
        ScheduleInput schedule = input(List.of(order),
            Map.of(901L, List.of(step(1, 200L, 2.75, 0.25))),
            Map.of(D1, Map.of(resource, BigDecimal.ONE),
                D1.plusDays(1), Map.of(resource, BigDecimal.valueOf(8)),
                D1.plusDays(2), Map.of(resource, BigDecimal.valueOf(2))),
            D1, 4, Strategy.EDD);
        schedule.setHolidays(Set.of(D1.plusDays(1)));
        ScheduledTask task = PrdScheduleEngine.schedule(schedule).getTasks().get(0);

        var getter = java.util.Arrays.stream(ScheduledTask.class.getMethods())
            .filter(method -> method.getName().equals("getDayLoads"))
            .findFirst().orElse(null);
        org.junit.jupiter.api.Assertions.assertNotNull(getter,
            "a saved schedule must expose the engine's per-day resource load facts");
        @SuppressWarnings("unchecked")
        Map<LocalDate, BigDecimal> observed = (Map<LocalDate, BigDecimal>) getter.invoke(task);
        assertEquals(Map.of(D1, BigDecimal.ONE, D1.plusDays(2), BigDecimal.valueOf(2)), observed);
        assertEquals(D1, task.getStartDay());
        assertEquals(D1.plusDays(2), task.getEndDay());
        assertEquals(0, task.getProcessHours().add(task.getSetupHours()).compareTo(
            observed.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)));
    }
    @Test
    void fourDigitRoutingSetupHasOneExplicitHundredthHourScheduleQuantization() {
        WoInfo order = wo(61L, 901L, BigDecimal.ONE, D1.plusDays(5), 1L);
        StepInfo route = step(1, 200L, 0.07, 0);
        route.setSetupHours(new BigDecimal("0.1234"));
        String resource = ScheduleModel.resourceKey("WORKSTATION", 200L);
        ScheduleResult result = PrdScheduleEngine.schedule(input(List.of(order),
            Map.of(901L, List.of(route)), Map.of(D1, Map.of(resource, new BigDecimal("0.20"))),
            D1, 1, Strategy.EDD));

        ScheduledTask task = result.getTasks().get(0);
        assertEquals(0, new BigDecimal("0.12").compareTo(task.getSetupHours()));
        assertEquals(0, new BigDecimal("0.07").compareTo(task.getProcessHours()));
        assertEquals(Map.of(D1, new BigDecimal("0.19")), task.getDayLoads());
        assertEquals(0, new BigDecimal("0.19").compareTo(result.getTotalHours()));
    }

    @Test
    void rejectsInternalStepWhoseRoundedProcessingAndSetupHoursAreBothZero() {
        WoInfo order = wo(63L, 903L, BigDecimal.ONE, D1.plusDays(5), 1L);
        for (StepInfo route : List.of(step(1, 200L, 0, 0), step(1, 200L, 0.004, 0))) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> PrdScheduleEngine.schedule(input(List.of(order), Map.of(903L, List.of(route)),
                    Map.of(), D1, 2, Strategy.EDD)));
            assertTrue(failure.getMessage().contains("工时"), failure.getMessage());
        }
    }

    @Test
    void neverBooksUnobservedDayBeyondLoadedCapacityAndHolidayHorizon() {
        WoInfo order = wo(62L, 902L, BigDecimal.ONE, D1.plusDays(5), 1L);
        String resource = ScheduleModel.resourceKey("WORKSTATION", 200L);
        ScheduleInput input = input(List.of(order), Map.of(902L, List.of(step(1, 200L, 1.0, 0))),
            Map.of(D1, Map.of(resource, BigDecimal.ZERO)), D1, 1, Strategy.EDD);

        assertThrows(IllegalArgumentException.class, () -> PrdScheduleEngine.schedule(input),
            "a day outside the assembled horizon has no trusted holiday or capacity facts");
    }
}
