package com.nexus.erp.prd.schedule;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * APS 人力可用产能折算（纯算法，无 Spring/DB 依赖，便于单元测试）
 * <p>
 * <b>背景（ERP-PRD-01）</b>：APS 排产原先只读 {@code prd_capacity_calendar}（设备/工作站产能）与
 * {@code sch_holiday}（假期），完全不读 {@code sch_shift}/{@code sch_team}/{@code sch_employee_schedule}，
 * 于是"当天班组休息 / 全员请假"仍会被排上工序，排产结果不可执行，排产↔排班↔考勤链路在人力维度断裂。
 * <p>
 * <b>口径</b>（与排产报告、类注释保持一致）：
 * <ol>
 *   <li>人力的可用工时来源是<b>排班明细</b> {@code sch_employee_schedule}：
 *       仅统计 {@code isWorkingDay=1} 且 {@code scheduleStatus != rest} 的排班行；</li>
 *   <li>单条排班工时优先取班次定义 {@code sch_shift.work_hours}，缺失时按
 *       {@code 排班结束时间 - 排班开始时间} 折算，再缺失按 8h/人·日兜底；</li>
 *   <li><b>排除请假</b>：{@code sch_leave_apply.apply_status='approved'} 且日期落在请假区间内的
 *       (工人, 日期) 组合不计入人力；<b>未排班人员</b>天然不计入（无排班行即无人力）；</li>
 *   <li>按排班所属<b>班组→车间</b>（{@code sch_team.workshop_id}）汇总为
 *       「车间 × 日期 → 可用人力工时」；</li>
 *   <li>该人力工时作为该车间内资源（{@code WORKSHOP:{id}} 与车间下各 {@code WORKSTATION:{id}}）的
 *       日产能<b>上限</b>：日历产能与人力工时取较小者；日历无该日记录时以人力工时为准
 *       （即"按班次时长算可用产能"）；</li>
 *   <li>某车间在本跨度内<b>完全没有排班数据</b>时不做任何收窄（保持原有日历/默认 8h 行为），
 *       避免未启用排班模块的环境排产全部归零；某车间有排班数据时，其休息日（人力 0）产能即为 0。</li>
 * </ol>
 * <p>
 * <b>已知简化</b>：不做"工作站级人力分配"（不把具体工人绑到具体工作站），只把车间总人力工时
 * 作为车间内各资源的产能上限；如需工作站级人力约束，应在 {@code bd_workstation.operator_count}
 * 与排班之间建立显式分配关系后再细化。
 *
 * @author nexus
 */
public final class PrdLaborCapacity {

    /**
     * 无班次工时且无法从排班时间折算时的默认单人日工时(小时)
     */
    public static final BigDecimal DEFAULT_HOURS_PER_WORKER_DAY = BigDecimal.valueOf(8);

    private PrdLaborCapacity() {
    }

    /**
     * 一条已确认可出勤的排班人力记录（请假/休息行在装配阶段已被剔除）
     */
    @Data
    public static class LaborRow {

        /**
         * 所属车间ID（由班组映射得到；为 null 的行不参与汇总）
         */
        private Long workshopId;

        /**
         * 排班日期（跨天班次取排班起始日，与 sch_employee_schedule.scheduled_date 一致）
         */
        private LocalDate scheduledDate;

        /**
         * 工人ID（仅用于去重/排查，汇总时不区分到人）
         */
        private Long workerId;

        /**
         * 该排班当日的可用工时(小时)
         */
        private BigDecimal hours;
    }

    /**
     * 按「车间 × 日期」汇总可用人力工时。
     * <p>对出现过的车间，返回其在 {@code [start, end]} 内<b>每一天</b>的工时（无排班的日期为 0），
     * 这样"某车间本周有排班但周三是全员休息"会被正确算成 0 而不是"无数据、沿用默认产能"。
     *
     * @param rows  可出勤的排班人力记录（已排除请假与休息）
     * @param start 跨度起始日（含）
     * @param end   跨度结束日（含）
     * @return 车间ID → (日期 → 可用人力工时)
     */
    public static Map<Long, Map<LocalDate, BigDecimal>> aggregate(List<LaborRow> rows,
                                                                  LocalDate start, LocalDate end) {
        Map<Long, Map<LocalDate, BigDecimal>> result = new HashMap<>();
        if (rows == null || rows.isEmpty() || start == null || end == null || end.isBefore(start)) {
            return result;
        }
        Set<Long> workshops = new HashSet<>();
        Map<Long, Map<LocalDate, BigDecimal>> sums = new HashMap<>();
        for (LaborRow row : rows) {
            if (row == null || row.getWorkshopId() == null || row.getScheduledDate() == null) {
                continue;
            }
            if (row.getScheduledDate().isBefore(start) || row.getScheduledDate().isAfter(end)) {
                continue;
            }
            workshops.add(row.getWorkshopId());
            BigDecimal hours = row.getHours() == null ? BigDecimal.ZERO : row.getHours();
            sums.computeIfAbsent(row.getWorkshopId(), k -> new HashMap<>())
                .merge(row.getScheduledDate(), hours, BigDecimal::add);
        }
        // 出现过的车间：跨度内每一天都要有值(缺失=0)，否则休息日会退化成"无数据→默认产能"
        for (Long workshopId : workshops) {
            Map<LocalDate, BigDecimal> byDate = sums.get(workshopId);
            Map<LocalDate, BigDecimal> filled = new HashMap<>();
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                filled.put(d, byDate.getOrDefault(d, BigDecimal.ZERO));
            }
            result.put(workshopId, filled);
        }
        return result;
    }

    /**
     * 把人力工时作为车间内资源日产能的上限写入排产产能视图（原地修改）。
     * <p>规则：{@code 最终产能 = 日历产能 == null ? 人力工时 : min(日历产能, 人力工时)}。
     * 只覆盖"人力数据里出现过的车间 × 日期"，其余资源键保持不变。
     *
     * @param capacityByDate      日期 → (资源键 → 小时)，会被原地收窄
     * @param laborByWorkshop     车间ID → (日期 → 可用人力工时)
     * @param workstationsByShop  车间ID → 该车间的工作站ID列表（用于把上限传导到工作站资源键）
     */
    public static void applyCap(Map<LocalDate, Map<String, BigDecimal>> capacityByDate,
                                Map<Long, Map<LocalDate, BigDecimal>> laborByWorkshop,
                                Map<Long, List<Long>> workstationsByShop) {
        if (capacityByDate == null || laborByWorkshop == null || laborByWorkshop.isEmpty()) {
            return;
        }
        for (Map.Entry<Long, Map<LocalDate, BigDecimal>> shopEntry : laborByWorkshop.entrySet()) {
            Long workshopId = shopEntry.getKey();
            List<String> keys = new ArrayList<>();
            keys.add(ScheduleModel.resourceKey("WORKSHOP", workshopId));
            List<Long> workstations = workstationsByShop == null ? null : workstationsByShop.get(workshopId);
            if (workstations != null) {
                for (Long workstationId : workstations) {
                    if (workstationId != null) {
                        keys.add(ScheduleModel.resourceKey("WORKSTATION", workstationId));
                    }
                }
            }
            for (Map.Entry<LocalDate, BigDecimal> dayEntry : shopEntry.getValue().entrySet()) {
                LocalDate day = dayEntry.getKey();
                BigDecimal labor = dayEntry.getValue() == null ? BigDecimal.ZERO : dayEntry.getValue();
                Map<String, BigDecimal> dayMap = capacityByDate.computeIfAbsent(day, k -> new HashMap<>());
                for (String key : keys) {
                    BigDecimal calendar = dayMap.get(key);
                    dayMap.put(key, calendar == null ? labor : calendar.min(labor));
                }
            }
        }
    }

    /**
     * 请假排除键：工人ID + 日期，用于装配阶段过滤排班行
     *
     * @param workerId 工人ID
     * @param date     日期
     * @return 键
     */
    public static String leaveKey(Long workerId, LocalDate date) {
        return workerId + "|" + date;
    }
}
