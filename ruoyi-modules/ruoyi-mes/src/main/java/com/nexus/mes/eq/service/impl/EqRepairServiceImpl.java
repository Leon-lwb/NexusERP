package com.nexus.mes.eq.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.redis.utils.SequenceUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.mes.eq.domain.EqEquipment;
import com.nexus.mes.eq.domain.EqRepair;
import com.nexus.mes.eq.domain.bo.EqRepairBo;
import com.nexus.mes.eq.domain.bo.EqRepairCompleteBo;
import com.nexus.mes.eq.domain.bo.EqRepairSpareBo;
import com.nexus.mes.eq.domain.vo.EqEquipmentVo;
import com.nexus.mes.eq.domain.vo.EqRepairStatisticsVo;
import com.nexus.mes.eq.domain.vo.EqRepairVo;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.eq.mapper.EqRepairMapper;
import com.nexus.mes.eq.service.IEqEquipmentService;
import com.nexus.mes.eq.service.IEqRepairService;
import com.nexus.mes.eq.service.IEqRepairSpareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MES设备维修记录 Service 业务层实现
 * <p>
 * 状态机: REPORTED -> REPAIRING -> COMPLETED
 * 设备状态联动:
 *   报修(INSERT) -> 设备 status=FAULT(SCRAPPED 终态不联动)
 *   完成(COMPLETE) -> 该设备无其他活跃报修(REPORTED/REPAIRING)则恢复 NORMAL
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("eqRepairService")
public class EqRepairServiceImpl implements IEqRepairService {

    private final EqRepairMapper baseMapper;
    private final EqEquipmentMapper equipmentMapper;
    private final IEqEquipmentService equipmentService;
    private final IEqRepairSpareService repairSpareService;

    @Override
    public TableDataInfo<EqRepairVo> selectPageList(EqRepairBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<EqRepair> lqw = buildQueryWrapper(bo);
        Page<EqRepairVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        fillEquipmentName(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public EqRepairVo selectById(Long repairId) {
        EqRepairVo vo = baseMapper.selectVoById(repairId);
        if (vo != null) {
            fillEquipmentName(List.of(vo));
        }
        return vo;
    }

    @Override
    public List<EqRepairVo> selectList(EqRepairBo bo) {
        List<EqRepairVo> list = baseMapper.selectVoList(buildQueryWrapper(bo));
        fillEquipmentName(list);
        return list;
    }

    /**
     * 报修(新增维修单 REPORTED), 联动置设备 FAULT
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public EqRepairVo insertByBo(EqRepairBo bo) {
        EqRepair repair = new EqRepair();
        repair.setRepairNo(generateNo());
        repair.setEquipmentId(bo.getEquipmentId());
        repair.setFaultType(bo.getFaultType());
        repair.setFaultDesc(bo.getFaultDesc());
        repair.setReporterId(LoginHelper.getUserId());
        repair.setReporterName(LoginHelper.getUsername());
        repair.setStatus(STATUS_REPORTED);
        repair.setRemark(bo.getRemark());
        baseMapper.insert(repair);
        log.info("新增维修单: repairId={}, repairNo={}, equipmentId={}", repair.getRepairId(), repair.getRepairNo(), repair.getEquipmentId());

        // 联动: 设备状态置 FAULT(SCRAPPED 终态由 updateStatus 内部跳过)
        equipmentService.updateStatus(bo.getEquipmentId(), IEqEquipmentService.STATUS_FAULT, "故障报修");
        EqRepairVo vo = baseMapper.selectVoById(repair.getRepairId());
        fillEquipmentName(List.of(vo));
        return vo;
    }

    /**
     * 开工(REPORTED -> REPAIRING), 置维修人/维修开始时间
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startRepair(Long repairId) {
        int rows = baseMapper.update(null, Wrappers.<EqRepair>lambdaUpdate()
            .set(EqRepair::getRepairerId, LoginHelper.getUserId())
            .set(EqRepair::getRepairerName, LoginHelper.getUsername())
            .set(EqRepair::getRepairStart, new Date())
            .set(EqRepair::getStatus, STATUS_REPAIRING)
            .eq(EqRepair::getRepairId, repairId)
            .eq(EqRepair::getStatus, STATUS_REPORTED));
        if (rows == 0) {
            throw new ServiceException("维修单状态已变更, 仅已报修可开工");
        }
        log.info("维修开工: repairId={}", repairId);
    }

    /**
     * 完成(REPAIRING -> COMPLETED), 联动: 该设备无其他活跃报修则恢复 NORMAL;
     * 若携带更换备件明细, 事务内写 eq_repair_spare + 扣减库存(P1-2 R1)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeRepair(Long repairId, EqRepairCompleteBo bo) {
        EqRepair repair = baseMapper.selectById(repairId);
        if (repair == null) {
            throw new ServiceException("维修单不存在: " + repairId);
        }
        if (!STATUS_REPAIRING.equals(repair.getStatus())) {
            throw new ServiceException("维修单状态已变更, 仅维修中可完成");
        }
        BigDecimal downtimeHours = bo.getDowntimeHours();
        BigDecimal repairCost = bo.getRepairCost();
        String remark = bo.getRemark();
        Date repairEnd = new Date();
        int rows = baseMapper.update(null, Wrappers.<EqRepair>lambdaUpdate()
            .set(EqRepair::getRepairEnd, repairEnd)
            .set(downtimeHours != null, EqRepair::getDowntimeHours, downtimeHours)
            .set(repairCost != null, EqRepair::getRepairCost, repairCost)
            .set(StringUtils.isNotBlank(remark), EqRepair::getRemark, remark)
            .set(EqRepair::getStatus, STATUS_COMPLETED)
            .eq(EqRepair::getRepairId, repairId)
            .eq(EqRepair::getStatus, STATUS_REPAIRING));
        if (rows == 0) {
            throw new ServiceException("维修单状态已变更, 完成失败");
        }
        log.info("维修完成: repairId={}, downtimeHours={}, repairCost={}", repairId, downtimeHours, repairCost);

        // 联动: 原子恢复设备 NORMAL(仅当该设备无其他活跃报修且非 SCRAPPED 时), 避免
        // "查询活跃报修数 -> 更新设备状态"两步之间的 TOCTOU(完成瞬间插入新报修导致误恢复)。M3。
        boolean restored = equipmentService.resetToNormalIfNoActiveRepair(repair.getEquipmentId());
        if (restored) {
            log.info("设备无其他活跃报修, 恢复正常: equipmentId={}", repair.getEquipmentId());
        } else {
            log.info("设备仍有活跃报修或已终态, 维持当前状态: equipmentId={}", repair.getEquipmentId());
        }

        // 更换备件明细: 事务内写 eq_repair_spare + 扣减库存(REPAIR_USE), 与本次完成原子
        List<EqRepairSpareBo> spares = bo.getSpares();
        if (spares != null && !spares.isEmpty()) {
            for (EqRepairSpareBo s : spares) {
                if (s.getSpareId() == null || s.getQuantity() == null || s.getQuantity().signum() <= 0) {
                    throw new ServiceException("备件明细非法: 备件ID/数量必填且数量需大于0");
                }
                s.setRepairId(repairId);
            }
            repairSpareService.batchAdd(spares);
            log.info("维修领用备件 {} 项: repairId={}", spares.size(), repairId);
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 生成维修单号: RP-yyyyMMdd-3位序号
     * <p>
     * 序号走 Redisson 原子发号器(按日 key, 次日重置), 消除 selectCount+1 在并发报修下的
     * 序号碰撞(此前仅靠 uk 唯一索引兜底重试)。M6。
     */
    private String generateNo() {
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        long seq = SequenceUtils.getNextId("mes:eq:repair:no:" + today, Duration.ofDays(1));
        return "RP-" + today + "-" + String.format("%03d", seq);
    }

    private LambdaQueryWrapper<EqRepair> buildQueryWrapper(EqRepairBo bo) {
        return Wrappers.<EqRepair>lambdaQuery()
            .like(StringUtils.isNotBlank(bo.getRepairNo()), EqRepair::getRepairNo, bo.getRepairNo())
            .eq(bo.getEquipmentId() != null, EqRepair::getEquipmentId, bo.getEquipmentId())
            .eq(StringUtils.isNotBlank(bo.getStatus()), EqRepair::getStatus, bo.getStatus())
            .eq(StringUtils.isNotBlank(bo.getFaultType()), EqRepair::getFaultType, bo.getFaultType())
            .orderByDesc(EqRepair::getCreateTime);
    }

    /** 批量补填设备编码/名称 */
    private void fillEquipmentName(List<EqRepairVo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> ids = records.stream()
            .map(EqRepairVo::getEquipmentId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        List<EqEquipmentVo> eqs = equipmentMapper.selectVoList(
            Wrappers.<EqEquipment>lambdaQuery().in(EqEquipment::getEquipmentId, ids));
        Map<Long, EqEquipmentVo> map = eqs.stream()
            .collect(Collectors.toMap(EqEquipmentVo::getEquipmentId, e -> e));
        records.forEach(r -> {
            EqEquipmentVo e = map.get(r.getEquipmentId());
            if (e != null) {
                r.setEquipmentCode(e.getEquipmentCode());
                r.setEquipmentName(e.getEquipmentName());
            }
        });
    }

    // ==================== 故障统计分析 ====================

    @Override
    public EqRepairStatisticsVo statistics(Date startDate, Date endDate) {
        // 默认最近 30 天
        if (endDate == null) {
            endDate = new Date();
        }
        if (startDate == null) {
            startDate = Date.from(endDate.toInstant().atZone(java.time.ZoneId.systemDefault())
                .toLocalDate().minusDays(29).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant());
        }
        Date finalStart = startDate;
        Date finalEnd = endDate;

        // 用 Wrapper 按 create_time 过滤(避免全表查询后 Java 过滤)
        List<EqRepairVo> filtered = baseMapper.selectVoList(Wrappers.<EqRepair>lambdaQuery()
            .ge(EqRepair::getCreateTime, finalStart)
            .le(EqRepair::getCreateTime, finalEnd)
            .orderByDesc(EqRepair::getCreateTime));
        fillEquipmentName(filtered);

        EqRepairStatisticsVo vo = new EqRepairStatisticsVo();

        // ===== KPI 汇总 =====
        vo.setTotalFault(filtered.size());
        vo.setCompletedCount((int) filtered.stream().filter(r -> STATUS_COMPLETED.equals(r.getStatus())).count());
        vo.setRepairingCount((int) filtered.stream().filter(r -> STATUS_REPORTED.equals(r.getStatus()) || STATUS_REPAIRING.equals(r.getStatus())).count());
        vo.setTotalDowntimeHours(filtered.stream().map(EqRepairVo::getDowntimeHours)
            .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
        vo.setTotalRepairCost(filtered.stream().map(EqRepairVo::getRepairCost)
            .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));

        // MTTR: avg(repair_end - repair_start) 小时
        BigDecimal totalHours = BigDecimal.ZERO;
        int mttrCount = 0;
        for (EqRepairVo r : filtered) {
            if (r.getRepairStart() != null && r.getRepairEnd() != null && STATUS_COMPLETED.equals(r.getStatus())) {
                long millis = r.getRepairEnd().getTime() - r.getRepairStart().getTime();
                if (millis > 0) {
                    totalHours = totalHours.add(BigDecimal.valueOf(millis).divide(BigDecimal.valueOf(3600000), 2, java.math.RoundingMode.HALF_UP));
                    mttrCount++;
                }
            }
        }
        vo.setMttrHours(mttrCount > 0 ? totalHours.divide(new BigDecimal(mttrCount), 2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO);

        // MTBF 近似: 区间总时长(小时) / 故障次数
        long spanHours = (endDate.getTime() - startDate.getTime()) / 3600000L;
        vo.setMtbfHours(!filtered.isEmpty() && spanHours > 0
            ? new BigDecimal(spanHours).divide(new BigDecimal(filtered.size()), 2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO);

        // ===== 趋势(按日) =====
        Map<LocalDate, List<EqRepairVo>> byDate = filtered.stream()
            .collect(Collectors.groupingBy(r -> r.getCreateTime().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()));
        vo.setTrend(byDate.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e -> {
            EqRepairStatisticsVo.TrendItem t = new EqRepairStatisticsVo.TrendItem();
            t.setStatDate(Date.from(e.getKey().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()));
            t.setFaultCount(e.getValue().size());
            t.setDowntimeHours(e.getValue().stream().map(EqRepairVo::getDowntimeHours).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
            return t;
        }).collect(Collectors.toList()));

        // ===== Pareto(按 fault_type) =====
        Map<String, Long> byType = filtered.stream()
            .filter(r -> r.getFaultType() != null && !r.getFaultType().isEmpty())
            .collect(Collectors.groupingBy(EqRepairVo::getFaultType, Collectors.counting()));
        int typedTotal = byType.values().stream().mapToInt(Long::intValue).sum();
        vo.setPareto(byType.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).map(e -> {
            EqRepairStatisticsVo.ParetoItem p = new EqRepairStatisticsVo.ParetoItem();
            p.setFaultType(e.getKey());
            p.setCount(e.getValue().intValue());
            p.setRatio(typedTotal > 0 ? new BigDecimal(e.getValue()).divide(new BigDecimal(typedTotal), 4, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO);
            return p;
        }).collect(Collectors.toList()));

        // ===== 设备 TOP10 =====
        Map<Long, List<EqRepairVo>> byEquip = filtered.stream()
            .filter(r -> r.getEquipmentId() != null)
            .collect(Collectors.groupingBy(EqRepairVo::getEquipmentId));
        vo.setTopEquipment(byEquip.entrySet().stream().map(e -> {
            EqRepairStatisticsVo.TopItem t = new EqRepairStatisticsVo.TopItem();
            t.setEquipmentId(e.getKey());
            EqRepairVo first = e.getValue().get(0);
            t.setEquipmentCode(first.getEquipmentCode());
            t.setEquipmentName(first.getEquipmentName());
            t.setFaultCount(e.getValue().size());
            t.setDowntimeHours(e.getValue().stream().map(EqRepairVo::getDowntimeHours).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
            return t;
        }).sorted(Comparator.comparingInt(EqRepairStatisticsVo.TopItem::getFaultCount).reversed())
            .limit(10).collect(Collectors.toList()));

        return vo;
    }

    /**
     * 维修状态计数汇总(全量 COUNT, 不受分页限制, 供监控大屏)
     */
    @Override
    public Map<String, Object> statusSummary() {
        long reported = countByStatus(STATUS_REPORTED);
        long repairing = countByStatus(STATUS_REPAIRING);
        long completed = countByStatus(STATUS_COMPLETED);
        Map<String, Object> map = new LinkedHashMap<>(8);
        map.put("reported", reported);
        map.put("repairing", repairing);
        map.put("completed", completed);
        map.put("active", reported + repairing);
        map.put("total", reported + repairing + completed);
        return map;
    }

    private long countByStatus(String status) {
        Long cnt = baseMapper.selectCount(Wrappers.<EqRepair>lambdaQuery()
            .eq(StringUtils.isNotBlank(status), EqRepair::getStatus, status));
        return cnt == null ? 0L : cnt;
    }
}
