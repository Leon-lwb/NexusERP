package com.nexus.erp.prd.schedule;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PrdLaborCapacity} 单元测试（ERP-PRD-01）
 * <p>核心断言：班次/排班折算的车间日人力工时正确、休息日（人力 0）把产能压到 0、
 * 日历产能与人力工时取小。修复前 APS 完全不读排班，这些断言无从谈起（引擎只会用 8h/日 默认值）。</p>
 *
 * @author Nexus
 */
@Tag("dev")
@Tag("local")
class PrdLaborCapacityTest {

    private static final LocalDate D1 = LocalDate.of(2026, 9, 21);
    private static final LocalDate D2 = LocalDate.of(2026, 9, 22);

    private PrdLaborCapacity.LaborRow row(long workshopId, LocalDate date, long workerId, String hours) {
        PrdLaborCapacity.LaborRow r = new PrdLaborCapacity.LaborRow();
        r.setWorkshopId(workshopId);
        r.setScheduledDate(date);
        r.setWorkerId(workerId);
        r.setHours(new BigDecimal(hours));
        return r;
    }

    @Test
    void aggregate_sumsHoursPerWorkshopAndFillsRestDaysWithZero() {
        // 车间 1：9-21 两名工人各 8h；9-22 无排班(全员休息) → 必须补 0 而不是"无数据"
        Map<Long, Map<LocalDate, BigDecimal>> labor = PrdLaborCapacity.aggregate(
            List.of(row(1L, D1, 101L, "8"), row(1L, D1, 102L, "8")), D1, D2);

        assertEquals(new BigDecimal("16"), labor.get(1L).get(D1));
        assertEquals(BigDecimal.ZERO, labor.get(1L).get(D2));
    }

    @Test
    void aggregate_noRows_returnsEmpty() {
        assertTrue(PrdLaborCapacity.aggregate(List.of(), D1, D2).isEmpty());
    }

    @Test
    void applyCap_takesMinOfCalendarAndLabor() {
        Map<LocalDate, Map<String, BigDecimal>> capacity = new HashMap<>();
        Map<String, BigDecimal> day1 = new HashMap<>();
        day1.put("WORKSTATION:11", new BigDecimal("24"));   // 日历(设备)24h
        day1.put("WORKSTATION:12", new BigDecimal("4"));    // 日历只有 4h
        capacity.put(D1, day1);

        Map<Long, Map<LocalDate, BigDecimal>> labor = new HashMap<>();
        Map<LocalDate, BigDecimal> shop = new HashMap<>();
        shop.put(D1, new BigDecimal("16"));                 // 人力 16h
        labor.put(1L, shop);

        Map<Long, List<Long>> stations = Map.of(1L, List.of(11L, 12L));
        PrdLaborCapacity.applyCap(capacity, labor, stations);

        // 24 与 16 取小 → 16；4 与 16 取小 → 4
        assertEquals(0, capacity.get(D1).get("WORKSTATION:11").compareTo(new BigDecimal("16")));
        assertEquals(0, capacity.get(D1).get("WORKSTATION:12").compareTo(new BigDecimal("4")));
        // 车间资源键也被收窄
        assertEquals(0, capacity.get(D1).get("WORKSHOP:1").compareTo(new BigDecimal("16")));
    }

    @Test
    void applyCap_zeroLaborForcesZeroCapacity() {
        Map<LocalDate, Map<String, BigDecimal>> capacity = new HashMap<>();
        capacity.put(D2, new HashMap<>(Map.of("WORKSTATION:11", new BigDecimal("8"))));

        Map<Long, Map<LocalDate, BigDecimal>> labor = new HashMap<>();
        labor.put(1L, new HashMap<>(Map.of(D2, BigDecimal.ZERO)));

        PrdLaborCapacity.applyCap(capacity, labor, Map.of(1L, List.of(11L)));

        // 休息日：人力 0 → 产能 0，引擎不会把工序排到这天
        assertEquals(0, capacity.get(D2).get("WORKSTATION:11").signum());
        assertEquals(0, capacity.get(D2).get("WORKSHOP:1").signum());
    }

    @Test
    void applyCap_laborOnlyDay_setsLaborHours() {
        Map<LocalDate, Map<String, BigDecimal>> capacity = new HashMap<>();
        Map<Long, Map<LocalDate, BigDecimal>> labor = new HashMap<>();
        labor.put(1L, new HashMap<>(Map.of(D1, new BigDecimal("16"))));

        PrdLaborCapacity.applyCap(capacity, labor, Map.of(1L, List.of(11L)));

        // 无产能日历记录的日期：以班次人力工时为准（16h = 两班次）
        assertEquals(0, capacity.get(D1).get("WORKSTATION:11").compareTo(new BigDecimal("16")));
    }

    @Test
    void leaveKey_isStable() {
        assertEquals("101|2026-09-21", PrdLaborCapacity.leaveKey(101L, D1));
    }
}
