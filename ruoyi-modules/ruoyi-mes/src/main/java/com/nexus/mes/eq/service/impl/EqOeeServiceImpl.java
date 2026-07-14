package com.nexus.mes.eq.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.common.mapper.MesSysTenantMapper;
import com.nexus.mes.eq.domain.EqEquipment;
import com.nexus.mes.eq.domain.EqOeeRecord;
import com.nexus.mes.eq.domain.EqRepair;
import com.nexus.mes.eq.domain.bo.EqOeeQueryBo;
import com.nexus.mes.eq.domain.vo.EqOeeDistributionVo;
import com.nexus.mes.eq.domain.vo.EqOeeOverviewVo;
import com.nexus.mes.eq.domain.vo.EqOeeRecordVo;
import com.nexus.mes.eq.domain.vo.EqOeeTopVo;
import com.nexus.mes.eq.domain.vo.EqOeeTrendVo;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.eq.mapper.EqOeeRecordMapper;
import com.nexus.mes.eq.mapper.EqRepairMapper;
import com.nexus.mes.eq.service.IEqOeeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * MES设备 OEE & 稼动率看板 Service 业务层实现
 * <p>
 * 数据源: ry-mes 库(eq_oee_record / eq_equipment / eq_repair)。
 * 类级 @DS("mes"): 所有查询走 mes 库; aggregate 内查租户列表时显式 push("master")。
 * 首版 OEE = 稼动率(availability), 表现率/质量率占位 1.0。
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("eqOeeService")
public class EqOeeServiceImpl implements IEqOeeService {

    private static final BigDecimal SIXTY = new BigDecimal("60");
    private static final BigDecimal DEFAULT_PERF = BigDecimal.ONE;
    private static final int RECENT_DAYS = 7;
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String DEFAULT_TENANT_ID = "000000";

    private final EqOeeRecordMapper baseMapper;
    private final EqEquipmentMapper equipmentMapper;
    private final EqRepairMapper repairMapper;
    private final MesSysTenantMapper mesSysTenantMapper;

    // ==================== 看板查询 ====================

    @Override
    public EqOeeOverviewVo overview(EqOeeQueryBo bo) {
        List<EqOeeRecordVo> list = queryRecords(bo);
        EqOeeOverviewVo vo = new EqOeeOverviewVo();
        BigDecimal totalPlanned = sum(list, EqOeeRecordVo::getPlannedMinutes);
        BigDecimal totalRun = sum(list, EqOeeRecordVo::getRunMinutes);
        BigDecimal totalDown = sum(list, EqOeeRecordVo::getDowntimeMinutes);
        int totalFault = list.stream().mapToInt(r -> r.getFaultCount() == null ? 0 : r.getFaultCount()).sum();
        long eqCount = list.stream().map(EqOeeRecordVo::getEquipmentId).filter(java.util.Objects::nonNull).distinct().count();
        BigDecimal avgAvail = totalPlanned.compareTo(BigDecimal.ZERO) > 0
            ? totalRun.divide(totalPlanned, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        vo.setAvgAvailability(avgAvail);
        vo.setAvgOee(avgAvail); // 首版 OEE = 稼动率
        vo.setTotalDowntimeMinutes(totalDown);
        vo.setTotalFaultCount(totalFault);
        vo.setEquipmentCount((int) eqCount);
        vo.setTotalPlannedMinutes(totalPlanned);
        vo.setTotalRunMinutes(totalRun);
        return vo;
    }

    @Override
    public List<EqOeeTrendVo> trend(EqOeeQueryBo bo) {
        List<EqOeeRecordVo> list = queryRecords(bo);
        // 按 stat_date 分组(TreeMap 升序, 便于趋势图展示)
        Map<Date, List<EqOeeRecordVo>> grouped = list.stream()
            .collect(Collectors.groupingBy(EqOeeRecordVo::getStatDate, TreeMap::new, Collectors.toList()));
        List<EqOeeTrendVo> result = new ArrayList<>(grouped.size());
        for (Map.Entry<Date, List<EqOeeRecordVo>> e : grouped.entrySet()) {
            List<EqOeeRecordVo> dayList = e.getValue();
            BigDecimal planned = sum(dayList, EqOeeRecordVo::getPlannedMinutes);
            BigDecimal run = sum(dayList, EqOeeRecordVo::getRunMinutes);
            BigDecimal down = sum(dayList, EqOeeRecordVo::getDowntimeMinutes);
            int fault = dayList.stream().mapToInt(r -> r.getFaultCount() == null ? 0 : r.getFaultCount()).sum();
            BigDecimal avail = planned.compareTo(BigDecimal.ZERO) > 0
                ? run.divide(planned, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            EqOeeTrendVo t = new EqOeeTrendVo();
            t.setStatDate(e.getKey());
            t.setAvailability(avail);
            t.setOee(avail);
            t.setDowntimeMinutes(down);
            t.setFaultCount(fault);
            result.add(t);
        }
        return result;
    }

    @Override
    public EqOeeDistributionVo distribution(EqOeeQueryBo bo) {
        List<EqOeeRecordVo> list = queryRecords(bo);
        BigDecimal run = sum(list, EqOeeRecordVo::getRunMinutes);
        BigDecimal down = sum(list, EqOeeRecordVo::getDowntimeMinutes);
        BigDecimal maint = sum(list, EqOeeRecordVo::getMaintenanceMinutes);
        BigDecimal total = run.add(down).add(maint);
        EqOeeDistributionVo vo = new EqOeeDistributionVo();
        vo.setRunMinutes(run);
        vo.setDowntimeMinutes(down);
        vo.setMaintenanceMinutes(maint);
        if (total.compareTo(BigDecimal.ZERO) > 0) {
            vo.setRunRatio(run.divide(total, 4, RoundingMode.HALF_UP));
            vo.setDowntimeRatio(down.divide(total, 4, RoundingMode.HALF_UP));
            vo.setMaintenanceRatio(maint.divide(total, 4, RoundingMode.HALF_UP));
        } else {
            vo.setRunRatio(BigDecimal.ZERO);
            vo.setDowntimeRatio(BigDecimal.ZERO);
            vo.setMaintenanceRatio(BigDecimal.ZERO);
        }
        return vo;
    }

    @Override
    public List<EqOeeTopVo> topDowntime(EqOeeQueryBo bo) {
        List<EqOeeRecordVo> list = queryRecords(bo);
        Map<Long, List<EqOeeRecordVo>> grouped = list.stream()
            .filter(r -> r.getEquipmentId() != null)
            .collect(Collectors.groupingBy(EqOeeRecordVo::getEquipmentId));
        List<EqOeeTopVo> tops = new ArrayList<>(grouped.size());
        for (Map.Entry<Long, List<EqOeeRecordVo>> e : grouped.entrySet()) {
            List<EqOeeRecordVo> el = e.getValue();
            EqOeeRecordVo first = el.get(0);
            EqOeeTopVo t = new EqOeeTopVo();
            t.setEquipmentId(e.getKey());
            t.setEquipmentCode(first.getEquipmentCode());
            t.setEquipmentName(first.getEquipmentName());
            t.setDowntimeMinutes(sum(el, EqOeeRecordVo::getDowntimeMinutes));
            t.setFaultCount(el.stream().mapToInt(r -> r.getFaultCount() == null ? 0 : r.getFaultCount()).sum());
            BigDecimal planned = sum(el, EqOeeRecordVo::getPlannedMinutes);
            BigDecimal run = sum(el, EqOeeRecordVo::getRunMinutes);
            t.setAvgAvailability(planned.compareTo(BigDecimal.ZERO) > 0
                ? run.divide(planned, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            tops.add(t);
        }
        tops.sort(Comparator.comparing(EqOeeTopVo::getDowntimeMinutes, Comparator.nullsFirst(Comparator.reverseOrder())));
        return tops.stream().limit(10).collect(Collectors.toList());
    }

    @Override
    public TableDataInfo<EqOeeRecordVo> selectPageList(EqOeeQueryBo bo, PageQuery pageQuery) {
        Date[] range = resolveRange(bo);
        Page<EqOeeRecordVo> page = baseMapper.selectVoPage(pageQuery.build(), buildListWrapper(bo, range));
        return TableDataInfo.build(page);
    }

    @Override
    public List<EqOeeRecordVo> selectList(EqOeeQueryBo bo) {
        Date[] range = resolveRange(bo);
        return baseMapper.selectVoList(buildListWrapper(bo, range));
    }

    // ==================== 聚合任务 ====================

    @Override
    public int aggregate(Date date) {
        if (date == null) {
            date = toDate(LocalDate.now().minusDays(1)); // 默认聚合昨天
        }
        final Date statDate = toDate(toLocalDate(date));
        List<String> tenantIds = queryTenantIds();
        int total = 0;
        for (String tenantId : tenantIds) {
            try {
                total += TenantHelper.dynamic(tenantId, () -> aggregateForCurrentTenant(statDate));
            } catch (Exception e) {
                log.warn("[OEE聚合] 租户 {} 聚合失败: {}", tenantId, e.getMessage(), e);
            }
        }
        log.info("[OEE聚合] 日期 {} 遍历 {} 个租户, 处理 {} 条记录", statDate, tenantIds.size(), total);
        return total;
    }

    /**
     * 聚合当前租户(由 TenantHelper.dynamic 注入)下所有设备的 OEE。
     * 调用方已在 mes 数据源上下文(类级 @DS)。
     */
    private Integer aggregateForCurrentTenant(Date statDate) {
        List<EqEquipment> equipments = equipmentMapper.selectList(Wrappers.<EqEquipment>lambdaQuery());
        if (equipments == null || equipments.isEmpty()) {
            return 0;
        }
        Date[] dayRange = dayRange(statDate);
        int count = 0;
        for (EqEquipment eq : equipments) {
            try {
                count += aggregateOneEquipment(eq, statDate, dayRange);
            } catch (Exception e) {
                log.warn("[OEE聚合] 设备 {} 聚合失败: {}", eq.getEquipmentId(), e.getMessage(), e);
            }
        }
        return count;
    }

    private int aggregateOneEquipment(EqEquipment eq, Date statDate, Date[] dayRange) {
        // 当日已完成维修的 downtime_hours 求和(按 repair_end 归集)
        List<EqRepair> completedRepairs = repairMapper.selectList(Wrappers.<EqRepair>lambdaQuery()
            .eq(EqRepair::getEquipmentId, eq.getEquipmentId())
            .eq(EqRepair::getStatus, STATUS_COMPLETED)
            .between(EqRepair::getRepairEnd, dayRange[0], dayRange[1]));
        BigDecimal downtimeHours = BigDecimal.ZERO;
        for (EqRepair r : completedRepairs) {
            if (r.getDowntimeHours() != null) {
                downtimeHours = downtimeHours.add(r.getDowntimeHours());
            }
        }
        BigDecimal downtimeMinutes = downtimeHours.multiply(SIXTY).setScale(2, RoundingMode.HALF_UP);

        // 当日故障次数(报修数, 按 create_time 归集, 不限状态)
        Long faultCount = repairMapper.selectCount(Wrappers.<EqRepair>lambdaQuery()
            .eq(EqRepair::getEquipmentId, eq.getEquipmentId())
            .between(EqRepair::getCreateTime, dayRange[0], dayRange[1]));

        BigDecimal planned = DEFAULT_PLANNED_MINUTES;
        BigDecimal maintenance = BigDecimal.ZERO;
        BigDecimal run = planned.subtract(downtimeMinutes).max(BigDecimal.ZERO);
        BigDecimal availability = planned.compareTo(BigDecimal.ZERO) > 0
            ? run.divide(planned, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal oee = availability.multiply(DEFAULT_PERF).multiply(DEFAULT_PERF).setScale(4, RoundingMode.HALF_UP);
        int fault = faultCount == null ? 0 : faultCount.intValue();

        // upsert by (equipment_id, stat_date) - tenant_id 由多租户插件自动加条件/填充
        EqOeeRecord existing = baseMapper.selectOne(Wrappers.<EqOeeRecord>lambdaQuery()
            .eq(EqOeeRecord::getEquipmentId, eq.getEquipmentId())
            .eq(EqOeeRecord::getStatDate, statDate)
            .last("LIMIT 1"));
        if (existing != null) {
            baseMapper.update(null, Wrappers.<EqOeeRecord>lambdaUpdate()
                .set(EqOeeRecord::getEquipmentCode, eq.getEquipmentCode())
                .set(EqOeeRecord::getEquipmentName, eq.getEquipmentName())
                .set(EqOeeRecord::getEquipmentType, eq.getEquipmentType())
                .set(EqOeeRecord::getWorkcenterId, eq.getWorkcenterId())
                .set(EqOeeRecord::getPlannedMinutes, planned)
                .set(EqOeeRecord::getDowntimeMinutes, downtimeMinutes)
                .set(EqOeeRecord::getMaintenanceMinutes, maintenance)
                .set(EqOeeRecord::getRunMinutes, run)
                .set(EqOeeRecord::getFaultCount, fault)
                .set(EqOeeRecord::getAvailability, availability)
                .set(EqOeeRecord::getPerformanceRate, DEFAULT_PERF)
                .set(EqOeeRecord::getQualityRate, DEFAULT_PERF)
                .set(EqOeeRecord::getOee, oee)
                .eq(EqOeeRecord::getRecordId, existing.getRecordId()));
            return 1;
        }
        EqOeeRecord rec = new EqOeeRecord();
        rec.setEquipmentId(eq.getEquipmentId());
        rec.setEquipmentCode(eq.getEquipmentCode());
        rec.setEquipmentName(eq.getEquipmentName());
        rec.setEquipmentType(eq.getEquipmentType());
        rec.setWorkcenterId(eq.getWorkcenterId());
        rec.setStatDate(statDate);
        rec.setPlannedMinutes(planned);
        rec.setDowntimeMinutes(downtimeMinutes);
        rec.setMaintenanceMinutes(maintenance);
        rec.setRunMinutes(run);
        rec.setFaultCount(fault);
        rec.setAvailability(availability);
        rec.setPerformanceRate(DEFAULT_PERF);
        rec.setQualityRate(DEFAULT_PERF);
        rec.setOee(oee);
        baseMapper.insert(rec);
        return 1;
    }

    // ==================== 私有辅助 ====================

    private List<EqOeeRecordVo> queryRecords(EqOeeQueryBo bo) {
        Date[] range = resolveRange(bo);
        return baseMapper.selectVoList(buildListWrapper(bo, range));
    }

    private LambdaQueryWrapper<EqOeeRecord> buildListWrapper(EqOeeQueryBo bo, Date[] range) {
        return Wrappers.<EqOeeRecord>lambdaQuery()
            .ge(EqOeeRecord::getStatDate, range[0])
            .le(EqOeeRecord::getStatDate, range[1])
            .eq(bo.getEquipmentId() != null, EqOeeRecord::getEquipmentId, bo.getEquipmentId())
            .eq(bo.getWorkcenterId() != null, EqOeeRecord::getWorkcenterId, bo.getWorkcenterId())
            .eq(StringUtils.isNotBlank(bo.getEquipmentType()), EqOeeRecord::getEquipmentType, bo.getEquipmentType())
            .orderByDesc(EqOeeRecord::getStatDate);
    }

    /** 解析时间范围; 为空默认最近 7 天 */
    private Date[] resolveRange(EqOeeQueryBo bo) {
        LocalDate today = LocalDate.now();
        LocalDate end = bo.getEndDate() != null ? toLocalDate(bo.getEndDate()) : today;
        LocalDate start = bo.getStartDate() != null ? toLocalDate(bo.getStartDate()) : today.minusDays(RECENT_DAYS - 1L);
        if (start.isAfter(end)) {
            start = end;
        }
        return new Date[]{ toDate(start), toDate(end) };
    }

    /** 当日起止 [00:00:00, 次日00:00:00) 避免丢失23:59:59.xxx 的记录 */
    private Date[] dayRange(Date statDate) {
        LocalDate ld = toLocalDate(statDate);
        Date start = toDate(ld);
        Date end = toDate(ld.plusDays(1));
        return new Date[]{ start, end };
    }

    private List<String> queryTenantIds() {
        try {
            DynamicDataSourceContextHolder.push("master");
            List<String> ids = mesSysTenantMapper.selectAllTenantIds();
            return (ids == null || ids.isEmpty()) ? List.of(DEFAULT_TENANT_ID) : ids;
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    private BigDecimal sum(List<EqOeeRecordVo> list, Function<EqOeeRecordVo, BigDecimal> getter) {
        return list.stream().map(getter).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private LocalDate toLocalDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private Date toDate(LocalDate ld) {
        return Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
