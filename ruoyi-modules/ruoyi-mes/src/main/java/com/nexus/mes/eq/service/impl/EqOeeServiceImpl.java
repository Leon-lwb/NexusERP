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
import com.nexus.mes.eq.domain.EqMaintenanceRecord;
import com.nexus.mes.eq.domain.EqOeeRecord;
import com.nexus.mes.eq.domain.EqRepair;
import com.nexus.mes.eq.domain.bo.EqOeeQueryBo;
import com.nexus.mes.eq.domain.vo.EqOeeDistributionVo;
import com.nexus.mes.eq.domain.vo.EqOeeOverviewVo;
import com.nexus.mes.eq.domain.vo.EqOeeRecordVo;
import com.nexus.mes.eq.domain.vo.EqOeeTopVo;
import com.nexus.mes.eq.domain.vo.EqOeeTrendVo;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.eq.mapper.EqMaintenanceRecordMapper;
import com.nexus.mes.eq.mapper.EqOeeRecordMapper;
import com.nexus.mes.eq.mapper.EqRepairMapper;
import com.nexus.mes.eq.service.IEqOeeService;
import com.nexus.mes.pro.domain.ProFlowCardStep;
import com.nexus.mes.pro.mapper.ProFlowCardStepMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * MES设备 OEE & 稼动率看板 Service 业务层实现
 * <p>
 * 数据源: ry-mes 库(eq_oee_record / eq_equipment / eq_repair / eq_maintenance_record / pro_flow_card_step)。
 * 类级 @DS("mes"): 所有查询走 mes 库; aggregate 内查租户列表时显式 push("master")。
 * <p>
 * 口径(MES-EQ-03):
 * <ul>
 *   <li>planned_minutes: 优先取 eq_equipment.planned_minutes_per_day(设备级可配置, 两班/三班制),
 *       为空时取配置项 nexus.mes.eq.oee.planned-minutes-per-day(默认 480 = 8h 单班);</li>
 *   <li>downtime_minutes: eq_repair.downtime_hours 当日求和 × 60(人工填报口径);</li>
 *   <li>maintenance_minutes: eq_maintenance_record.duration_minutes 当日真实汇总(未采集的记录按 0 计并在 remark 标注);</li>
 *   <li>quality_rate: 同工作中心当日流转卡工序的 goodQty /(goodQty+defectQty+scrapQty), 无数据时为 null(占位, 不再伪装 100%);</li>
 *   <li>performance_rate: 当前无可用的"理论节拍"数据源, 恒为 null(占位);</li>
 *   <li>oee = availability × (performance_rate ?? 1) × (quality_rate ?? 1), 占位因子在 remark 中显式标注。</li>
 * </ul>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("eqOeeService")
public class EqOeeServiceImpl implements IEqOeeService {

    private static final BigDecimal SIXTY = new BigDecimal("60");
    private static final int RECENT_DAYS = 7;
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String DEFAULT_TENANT_ID = "000000";

    /** 默认计划生产时长(分钟/天): 设备未配置 planned_minutes_per_day 时使用(MES-EQ-03) */
    @Value("${nexus.mes.eq.oee.planned-minutes-per-day:480}")
    private BigDecimal defaultPlannedMinutes;

    private final EqOeeRecordMapper baseMapper;
    private final EqEquipmentMapper equipmentMapper;
    private final EqRepairMapper repairMapper;
    private final EqMaintenanceRecordMapper maintenanceRecordMapper;
    private final ProFlowCardStepMapper flowCardStepMapper;
    private final MesSysTenantMapper mesSysTenantMapper;

    // ==================== 看板查询 ====================

    /**
     * 获取概览概览
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 返回结果
     */
    @Override
    public EqOeeOverviewVo overview(EqOeeQueryBo bo) {
        List<EqOeeRecordVo> list = queryRecords(bo);
        EqOeeOverviewVo vo = new EqOeeOverviewVo();
        BigDecimal totalPlanned = sum(list, EqOeeRecordVo::getPlannedMinutes);
        BigDecimal totalRun = sum(list, EqOeeRecordVo::getRunMinutes);
        BigDecimal totalDown = sum(list, EqOeeRecordVo::getDowntimeMinutes);
        int totalFault = list.stream().mapToInt(r -> r.getFaultCount() == null ? 0 : r.getFaultCount()).sum();
        long eqCount =
                list.stream()
                        .map(EqOeeRecordVo::getEquipmentId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .count();
        BigDecimal avgAvail = totalPlanned.compareTo(BigDecimal.ZERO) > 0
            ? totalRun.divide(totalPlanned, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        vo.setAvgAvailability(avgAvail);
        // 平均 OEE 按计划时长加权(占位因子按 1 参与, 与明细口径一致), 避免简单平均掩盖长计划设备
        BigDecimal weightedOee = BigDecimal.ZERO;
        for (EqOeeRecordVo r : list) {
            if (r.getOee() == null || r.getPlannedMinutes() == null) {
                continue;
            }
            weightedOee = weightedOee.add(r.getOee().multiply(r.getPlannedMinutes()));
        }
        vo.setAvgOee(totalPlanned.compareTo(BigDecimal.ZERO) > 0
            ? weightedOee.divide(totalPlanned, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        // 占位标注: 表现率/质量率是否已有真实数据源(无则前端应展示"占位"而不是 100%)
        vo.setPerformanceRateAvailable(false);
        vo.setQualityRateAvailable(list.stream().anyMatch(r -> r.getQualityRate() != null));
        vo.setTotalDowntimeMinutes(totalDown);
        vo.setTotalFaultCount(totalFault);
        vo.setEquipmentCount((int) eqCount);
        vo.setTotalPlannedMinutes(totalPlanned);
        vo.setTotalRunMinutes(totalRun);
        return vo;
    }

    /**
     * 统计趋势趋势
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 结果列表
     */
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

    /**
     * 统计分布分布
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 返回结果
     */
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

    /**
     * 统计排行排行
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 结果列表
     */
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

    /**
     * 分页查询OEE列表
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果
     */
    @Override
    public TableDataInfo<EqOeeRecordVo> selectPageList(EqOeeQueryBo bo, PageQuery pageQuery) {
        Date[] range = resolveRange(bo);
        Page<EqOeeRecordVo> page = baseMapper.selectVoPage(pageQuery.build(), buildListWrapper(bo, range));
        return TableDataInfo.build(page);
    }

    /**
     * 查询OEE列表
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 结果列表
     */
    @Override
    public List<EqOeeRecordVo> selectList(EqOeeQueryBo bo) {
        Date[] range = resolveRange(bo);
        return baseMapper.selectVoList(buildListWrapper(bo, range));
    }

    // ==================== 聚合任务 ====================

    /**
     * 聚合聚合
     *
     * @param date 统计日期（null 表示聚合昨天）
     * @return 数量/结果
     */
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

        // 当日故障次数(报修数, 按 create_time 归集, 不含已作废的误报修)
        Long faultCount = repairMapper.selectCount(Wrappers.<EqRepair>lambdaQuery()
            .eq(EqRepair::getEquipmentId, eq.getEquipmentId())
            .ne(EqRepair::getStatus, "CANCELLED")
            .between(EqRepair::getCreateTime, dayRange[0], dayRange[1]));

        // 计划生产时长: 设备级配置优先, 否则取配置默认值(默认 480 = 8h 单班)
        BigDecimal planned = resolvePlannedMinutes(eq);

        // 保养占用时长: 当日保养记录实际耗时汇总(MES-EQ-03), 未采集时长的记录按 0 计并标注
        List<EqMaintenanceRecord> maintRecords = maintenanceRecordMapper.selectList(
            Wrappers.<EqMaintenanceRecord>lambdaQuery()
                .eq(EqMaintenanceRecord::getEquipmentId, eq.getEquipmentId())
                .between(EqMaintenanceRecord::getExecuteTime, dayRange[0], dayRange[1]));
        BigDecimal maintenance = BigDecimal.ZERO;
        int maintMissingDuration = 0;
        for (EqMaintenanceRecord mr : maintRecords) {
            if (mr.getDurationMinutes() == null) {
                maintMissingDuration++;
            } else if (mr.getDurationMinutes().signum() > 0) {
                maintenance = maintenance.add(mr.getDurationMinutes());
            }
        }
        maintenance = maintenance.setScale(2, RoundingMode.HALF_UP);

        // 质量率: 同工作中心当日工序报工的良品率; 无报工数据时为 null(占位, 不伪装 100%)
        BigDecimal qualityRate = clamp01(resolveQualityRate(eq, dayRange));
        // 表现率: 缺少"理论节拍/标准工时"数据源, 恒为占位 null
        BigDecimal performanceRate = null;

        BigDecimal run = planned.subtract(downtimeMinutes).subtract(maintenance).max(BigDecimal.ZERO);
        // MES-EQ-13: 四个比率统一钳制到 [0,1]。原来只有 max(ZERO) 下限, 负停机时长/负保养时长
        // 或历史脏数据会让 run > planned, 落库 availability/oee > 1, 看板出现"超产"假象。
        BigDecimal availability = clamp01(planned.compareTo(BigDecimal.ZERO) > 0
            ? run.divide(planned, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        BigDecimal oee = clamp01(availability
            .multiply(performanceRate == null ? BigDecimal.ONE : performanceRate)
            .multiply(qualityRate == null ? BigDecimal.ONE : qualityRate))
            .setScale(4, RoundingMode.HALF_UP);
        int fault = faultCount == null ? 0 : faultCount.intValue();
        String remark = buildOeeRemark(performanceRate, qualityRate, maintRecords.size(), maintMissingDuration);

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
                .set(EqOeeRecord::getPerformanceRate, performanceRate)
                .set(EqOeeRecord::getQualityRate, qualityRate)
                .set(EqOeeRecord::getOee, oee)
                .set(EqOeeRecord::getRemark, remark)
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
        rec.setPerformanceRate(performanceRate);
        rec.setQualityRate(qualityRate);
        rec.setOee(oee);
        rec.setRemark(remark);
        baseMapper.insert(rec);
        return 1;
    }

    /**
     * 比率钳制到 [0,1](MES-EQ-13)。
     * <p>
     * planned(分母)与 run(分子)都依赖人工填报数据, 负停机/负成本或历史脏数据会让
     * availability/oee 突破 1 的物理上界; 此处统一做上限钳制。
     * null 原样返回, 保留"表现率/质量率无数据源 = 占位"的语义。
     *
     * @param rate 原始比率(可空)
     * @return 落在 [0,1] 的比率; 入参为 null 时返回 null
     */
    static BigDecimal clamp01(BigDecimal rate) {
        if (rate == null) {
            return null;
        }
        return rate.max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }

    /**
     * 计划生产时长: 设备级 planned_minutes_per_day 优先, 否则取配置默认值(默认 480 = 8h 单班)。
     */
    private BigDecimal resolvePlannedMinutes(EqEquipment eq) {
        if (eq.getPlannedMinutesPerDay() != null && eq.getPlannedMinutesPerDay().signum() > 0) {
            return eq.getPlannedMinutesPerDay().setScale(2, RoundingMode.HALF_UP);
        }
        if (defaultPlannedMinutes != null && defaultPlannedMinutes.signum() > 0) {
            return defaultPlannedMinutes.setScale(2, RoundingMode.HALF_UP);
        }
        return DEFAULT_PLANNED_MINUTES;
    }

    /**
     * 质量率 = 当日同工作中心工序报工良品数 /(良品+不良+报废)。无工作中心或无报工数据时返回 null(占位)。
     */
    private BigDecimal resolveQualityRate(EqEquipment eq, Date[] dayRange) {
        if (eq.getWorkcenterId() == null) {
            return null;
        }
        List<ProFlowCardStep> steps = flowCardStepMapper.selectList(Wrappers.<ProFlowCardStep>lambdaQuery()
            .eq(ProFlowCardStep::getWorkcenterId, eq.getWorkcenterId())
            .between(ProFlowCardStep::getActualEndTime, dayRange[0], dayRange[1]));
        BigDecimal good = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (ProFlowCardStep s : steps) {
            if (s.getGoodQty() != null) {
                good = good.add(s.getGoodQty());
            }
            if (s.getGoodQty() != null) {
                total = total.add(s.getGoodQty());
            }
            if (s.getDefectQty() != null) {
                total = total.add(s.getDefectQty());
            }
            if (s.getScrapQty() != null) {
                total = total.add(s.getScrapQty());
            }
        }
        if (total.signum() <= 0) {
            return null;
        }
        return good.divide(total, 4, RoundingMode.HALF_UP);
    }

    /**
     * 生成 OEE 口径备注: 显式标注哪些因子是占位(而非真实的 100%)。
     */
    private String buildOeeRemark(BigDecimal performanceRate, BigDecimal qualityRate,
                                  int maintRecordCount, int maintMissingDuration) {
        List<String> notes = new ArrayList<>(3);
        if (performanceRate == null) {
            notes.add("表现率无数据源(占位, 按1参与计算)");
        }
        if (qualityRate == null) {
            notes.add("质量率无报工数据(占位, 按1参与计算)");
        }
        if (maintMissingDuration > 0) {
            notes.add("保养记录 " + maintMissingDuration + "/" + maintRecordCount + " 条未填时长(按0计)");
        }
        return notes.isEmpty() ? null : String.join("; ", notes);
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
        return list.stream()
                .map(getter)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private LocalDate toLocalDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private Date toDate(LocalDate ld) {
        return Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
