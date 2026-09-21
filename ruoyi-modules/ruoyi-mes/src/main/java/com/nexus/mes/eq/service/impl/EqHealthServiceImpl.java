package com.nexus.mes.eq.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.eq.domain.EqEquipment;
import com.nexus.mes.eq.domain.EqHealthRecord;
import com.nexus.mes.eq.domain.EqRepair;
import com.nexus.mes.eq.domain.vo.EqHealthRecordVo;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.eq.mapper.EqHealthRecordMapper;
import com.nexus.mes.eq.mapper.EqRepairMapper;
import com.nexus.mes.eq.service.IEqHealthService;
import com.nexus.mes.eq.service.IEqRepairService;
import com.nexus.mes.pro.service.IProAndonService;
import com.nexus.common.iot.service.IotDeviceDataQueryService;
import com.nexus.common.iot.util.EwmaUtil;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备健康评分 Service 实现（预测性维护）
 * <p>
 * 评分模型（可解释规则，纯 Java 统计）：
 * <pre>
 *   score = 100 − 异常点扣分 − 维修扣分
 *   异常点扣分 = 24h 内 z-score>3 的数据点数 × 4
 *   维修扣分  = min(90天内维修单数×5, 30) + 未闭环维修单数×10
 *   等级: HIGH>=80 / MEDIUM>=60 / LOW<60
 * </pre>
 * 异常点统计与 IoT 管道在线检测共用 common-iot 的 {@link EwmaUtil}（唯一实现），
 * 对每属性的 24h 时序离线重放：均值/方差 EWMA 收敛后逐点计算 z-score。
 * IoT 数据经 common-iot 契约 {@link IotDeviceDataQueryService#getHistoryData} 读取
 * （返回 Map，避免 MES 依赖 ruoyi-iot 实体；实现方 ruoyi-iot 自带 @DS("iot") 切库）。
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service
public class EqHealthServiceImpl implements IEqHealthService {

    private final EqEquipmentMapper equipmentMapper;
    private final EqHealthRecordMapper healthRecordMapper;
    private final EqRepairMapper repairMapper;
    private final IotDeviceDataQueryService iotDeviceDataQueryService;
    private final IProAndonService andonService;
    private final Environment environment;

    /**
     * 每个异常点扣分
     */
    private static final int DEDUCT_PER_ANOMALY = 4;

    /**
     * 每笔 90 天内维修扣分(封顶)
     */
    private static final int DEDUCT_PER_REPAIR = 5;

    /**
     * 90 天维修扣分封顶
     */
    private static final int REPAIR_DEDUCT_CAP = 30;

    /**
     * 每笔未闭环维修扣分
     */
    private static final int DEDUCT_PER_OPEN_REPAIR = 10;

    /**
     * 统计窗口: 最近 24 小时
     */
    private static final Duration WINDOW = Duration.ofHours(24);

    /**
     * 健康度安灯限频键前缀(MES-EQ-16): {@code mes:eq:health:andon:{tenantId}:{equipmentId}}
     */
    private static final String ANDON_THROTTLE_PREFIX = "mes:eq:health:andon:";

    /**
     * 健康度安灯限频窗口: 同一设备 24h 内只推一次(MES-EQ-16)
     */
    private static final Duration ANDON_THROTTLE_TTL = Duration.ofHours(24);

    @Override
    public TableDataInfo<EqHealthRecordVo> selectLatestPage(String equipmentName, String grade, PageQuery pageQuery) {
        // 每台设备最新一条: 按 recordDate 分组取最新 → 内存分页(设备数量级小, 可接受)
        List<EqHealthRecord> all = healthRecordMapper.selectList(Wrappers.lambdaQuery(EqHealthRecord.class)
            .isNotNull(EqHealthRecord::getEquipmentId)
            .orderByDesc(EqHealthRecord::getRecordDate));
        Map<Long, EqHealthRecord> latest = new LinkedHashMap<>();
        for (EqHealthRecord record : all) {
            latest.putIfAbsent(record.getEquipmentId(), record);
        }
        List<EqHealthRecordVo> filtered = latest.values().stream()
            .filter(r -> equipmentName == null || equipmentName.isBlank()
                || (r.getEquipmentName() != null && r.getEquipmentName().contains(equipmentName)))
            .filter(r -> grade == null || grade.isBlank() || grade.equals(r.getGrade()))
            .map(r -> toVo(r))
            .sorted(Comparator.comparing(EqHealthRecordVo::getScore))
            .toList();
        return buildPage(filtered, pageQuery);
    }

    @Override
    public List<EqHealthRecordVo> selectTrend(Long equipmentId, int limitDays) {
        Date since = Date.from(LocalDate.now().minusDays(Math.max(1, limitDays))
            .atStartOfDay(ZoneId.systemDefault()).toInstant());
        List<EqHealthRecord> records = healthRecordMapper.selectList(
            Wrappers.lambdaQuery(EqHealthRecord.class)
                .eq(EqHealthRecord::getEquipmentId, equipmentId)
                .ge(EqHealthRecord::getRecordDate, since)
                .orderByAsc(EqHealthRecord::getRecordDate));
        return records.stream().map(this::toVo).toList();
    }

    @Override
    public int evaluateAll() {
        List<EqEquipment> equipments = equipmentMapper.selectList(
            Wrappers.lambdaQuery(EqEquipment.class)
                .isNotNull(EqEquipment::getIotDeviceId));
        int processed = 0;
        for (EqEquipment equipment : equipments) {
            try {
                evaluateOne(equipment);
                processed++;
            } catch (Exception e) {
                log.warn("[设备健康] 设备 {} 评估失败: {}", equipment.getEquipmentId(), e.getMessage());
            }
        }
        return processed;
    }

    @Override
    public EqHealthRecord evaluateOne(EqEquipment equipment) {
        Date now = new Date();
        Date windowStart = new Date(now.getTime() - WINDOW.toMillis());

        // 1. IoT 24h 数据(common-iot 契约, Map 形式)
        List<Map<String, Object>> dataList = iotDeviceDataQueryService.getHistoryData(
            equipment.getIotDeviceId(), windowStart, now);

        // 2. 按属性分组 → EWMA 离线重放统计异常点
        Map<String, List<Map<String, Object>>> byProperty = new HashMap<>();
        for (Map<String, Object> data : dataList) {
            Object value = data.get("numericValue");
            Object propertyCode = data.get("propertyCode");
            if (value instanceof Number && propertyCode != null) {
                byProperty.computeIfAbsent(propertyCode.toString(), k -> new ArrayList<>()).add(data);
            }
        }
        int anomalyPoints = 0;
        List<Map<String, Object>> detail = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : byProperty.entrySet()) {
            List<Map<String, Object>> series = entry.getValue();
            series.sort(Comparator.comparing(d -> d.get("collectTime") == null ? d.get("receiveTime") : d.get("collectTime"),
                Comparator.nullsFirst(Comparator.comparing(o -> o == null ? 0L : ((Date) o).getTime()))));
            EwmaStats stats = replayEwma(series);
            anomalyPoints += stats.anomalyCount;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("propertyCode", entry.getKey());
            item.put("propertyName", series.get(0).get("propertyName"));
            item.put("mean", stats.mean.setScale(2, RoundingMode.HALF_UP));
            item.put("std", stats.std.setScale(4, RoundingMode.HALF_UP));
            item.put("anomalyPoints", stats.anomalyCount);
            detail.add(item);
        }

        // 3. 维修历史(90 天内 + 未闭环)
        Date since90d = new Date(now.getTime() - Duration.ofDays(90).toMillis());
        Long repairCount90d = repairMapper.selectCount(Wrappers.lambdaQuery(EqRepair.class)
            .eq(EqRepair::getEquipmentId, equipment.getEquipmentId())
            .ge(EqRepair::getRepairStart, since90d));
        Long openRepairs = repairMapper.selectCount(Wrappers.lambdaQuery(EqRepair.class)
            .eq(EqRepair::getEquipmentId, equipment.getEquipmentId())
            .notIn(EqRepair::getStatus, IEqRepairService.STATUS_COMPLETED, IEqRepairService.STATUS_CANCELLED));

        // 4. 评分
        int repairDeduct = Math.min((int) (repairCount90d * DEDUCT_PER_REPAIR), REPAIR_DEDUCT_CAP)
            + (int) (openRepairs * DEDUCT_PER_OPEN_REPAIR);
        int score = Math.max(0, Math.min(100, 100 - anomalyPoints * DEDUCT_PER_ANOMALY - repairDeduct));
        String grade = score >= 80 ? "HIGH" : score >= 60 ? "MEDIUM" : "LOW";

        // 5. 前次评分对比
        EqHealthRecord prev = healthRecordMapper.selectOne(Wrappers.lambdaQuery(EqHealthRecord.class)
            .eq(EqHealthRecord::getEquipmentId, equipment.getEquipmentId())
            .orderByDesc(EqHealthRecord::getRecordDate)
            .last("LIMIT 1"));
        Integer prevScore = prev == null ? null : prev.getScore();
        Integer scoreChange = prevScore == null ? null : score - prevScore;

        // 6. 落库: 同设备同日先条件更新(CAS), 影响 0 行才插入(MES-EQ-15)。
        //    原 select-then-insert/update 在并发重评(定时任务与手工触发重叠)下会插入同设备同日多条;
        //    eq_health_record 的唯一键 uk_tenant_eq_date(tenant_id, equipment_id, record_date) 兜底(DDL 见汇报)。
        EqHealthRecord record = new EqHealthRecord();
        record.setEquipmentId(equipment.getEquipmentId());
        record.setEquipmentCode(equipment.getEquipmentCode());
        record.setEquipmentName(equipment.getEquipmentName());
        record.setIotDeviceId(equipment.getIotDeviceId());
        record.setScore(score);
        record.setGrade(grade);
        record.setAnomalyPoints(anomalyPoints);
        record.setRepairCount90d((int) (long) repairCount90d);
        record.setOpenRepairCount((int) (long) openRepairs);
        record.setDetailJson(JsonUtils.toJsonString(detail));
        record.setPrevScore(prevScore);
        record.setScoreChange(scoreChange);
        record.setRecordDate(now);

        LambdaUpdateWrapper<EqHealthRecord> sameDay = Wrappers.<EqHealthRecord>lambdaUpdate()
            .eq(EqHealthRecord::getEquipmentId, equipment.getEquipmentId())
            .apply("DATE(record_date) = DATE({0})", now);
        if (healthRecordMapper.update(record, sameDay) == 0) {
            try {
                healthRecordMapper.insert(record);
            } catch (DuplicateKeyException e) {
                // 并发下另一线程已插入同日记录: 退化为更新, 保证"同设备同日一条"
                log.debug("[设备健康] 同日记录并发插入冲突, 转为更新: equipmentId={}", equipment.getEquipmentId());
                healthRecordMapper.update(record, Wrappers.<EqHealthRecord>lambdaUpdate()
                    .eq(EqHealthRecord::getEquipmentId, equipment.getEquipmentId())
                    .apply("DATE(record_date) = DATE({0})", now));
            }
        }

        // 7. LOW 且评分下滑 → 安灯(同一设备 24h 内只推一次, MES-EQ-16)
        if ("LOW".equals(grade) && (scoreChange == null || scoreChange < 0)) {
            if (!tryAcquireAndonThrottle(TenantHelper.getTenantId(), equipment.getEquipmentId())) {
                log.debug("[设备健康] 设备 {} 安灯限频中(24h 内已推送), 跳过", equipment.getEquipmentId());
            } else {
                String reason = String.format("设备健康预警: 设备[%s(%s)] 健康评分 %d(%s), 较前次%s, 24h异常点 %d, 未闭环维修 %d",
                    equipment.getEquipmentName(), equipment.getEquipmentCode(), score, grade,
                    scoreChange == null ? "首次评估" : ("下降 " + Math.abs(scoreChange)), anomalyPoints, openRepairs);
                try {
                    andonService.callAndon(null, null, null, IProAndonService.TYPE_EQUIPMENT, reason);
                } catch (Exception e) {
                    log.warn("[设备健康] 设备 {} 触发安灯失败: {}", equipment.getEquipmentId(), e.getMessage());
                }
            }
        }
        return record;
    }

    /**
     * 健康度安灯限频键(MES-EQ-16): {@code mes:eq:health:andon:{tenantId}:{equipmentId}}。
     * <p>equipment_id 为雪花全局唯一, 带上 tenantId 仅为便于按租户排查与批量清理。</p>
     *
     * @param tenantId    租户ID(定时任务线程由 TenantHelper.dynamic 注入, 缺失时为空串)
     * @param equipmentId 设备ID
     * @return Redis 限频键
     */
    static String andonThrottleKey(String tenantId, Long equipmentId) {
        return ANDON_THROTTLE_PREFIX + (tenantId == null ? "" : tenantId) + ":" + equipmentId;
    }

    /**
     * 获取安灯推送许可: 同一设备 24h 内只允许推送一次(MES-EQ-16)。
     * <p>抽为可覆盖(protected)方法, 便于单测在不静态打桩 {@code RedisUtils} 的前提下验证限频行为。
     * Redis 不可用时放行(告警优先, 不因缓存故障静默丢弃设备健康预警)。</p>
     *
     * @param tenantId    租户ID(可空)
     * @param equipmentId 设备ID
     * @return true 表示本次获得推送许可
     */
    protected boolean tryAcquireAndonThrottle(String tenantId, Long equipmentId) {
        try {
            return RedisUtils.setObjectIfAbsent(andonThrottleKey(tenantId, equipmentId), "1", ANDON_THROTTLE_TTL);
        } catch (Exception e) {
            log.warn("[设备健康] 安灯限频键获取失败, 本次仍推送: {}", e.getMessage());
            return true;
        }
    }

    /**
     * EWMA 离线重放: 按时间序逐点更新均值/方差, 预热后统计超 z 阈值的点数。
     * <p>
     * 算法与参数均与 IoT 管道在线检测共用一处实现：数学部分为 common-iot 的
     * {@link EwmaUtil}；λ/预热/z 阈值读取在线检测同一组配置键
     * {@code nexus.iot.pipeline.anomaly.*}（未配置时取 EwmaUtil 默认值），
     * 保证本页"异常点数"与实时 ANOMALY 告警口径一致。
     */
    private EwmaStats replayEwma(List<Map<String, Object>> series) {
        double lambda = environment.getProperty("nexus.iot.pipeline.anomaly.lambda", Double.class, EwmaUtil.DEFAULT_LAMBDA);
        int warmup = environment.getProperty("nexus.iot.pipeline.anomaly.warmup", Integer.class, EwmaUtil.DEFAULT_WARMUP);
        double zThreshold = environment.getProperty("nexus.iot.pipeline.anomaly.z-warn", Double.class, EwmaUtil.DEFAULT_Z_WARN);
        EwmaUtil.EwmaState state = null;
        int anomalyCount = 0;
        for (Map<String, Object> data : series) {
            BigDecimal x = toBigDecimal(data.get("numericValue"));
            if (x == null) {
                continue;
            }
            // MES-EQ-17: 显式取绝对值, 向下突变(骤降)与向上突变同等计为异常点。
            // EwmaUtil.zScore 内部已返回 |x-mean|/std, 此处 .abs() 为防御性双保险。
            if (EwmaUtil.warmedUp(state, warmup)
                && EwmaUtil.zScore(state, x).abs().compareTo(BigDecimal.valueOf(zThreshold)) > 0) {
                anomalyCount++;
            }
            state = EwmaUtil.update(state, x, lambda);
        }
        BigDecimal std = state == null || state.variance().compareTo(BigDecimal.ZERO) <= 0
            ? BigDecimal.ZERO : state.variance().sqrt(MathContext.DECIMAL64);
        return new EwmaStats(state == null ? BigDecimal.ZERO : state.mean(), std, anomalyCount);
    }

    /**
     * IoT 契约返回的数值统一转 BigDecimal
     */
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number num) {
            return new BigDecimal(num.toString());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private EqHealthRecordVo toVo(EqHealthRecord record) {
        EqHealthRecordVo vo = new EqHealthRecordVo();
        vo.setRecordId(record.getRecordId());
        vo.setEquipmentId(record.getEquipmentId());
        vo.setEquipmentCode(record.getEquipmentCode());
        vo.setEquipmentName(record.getEquipmentName());
        vo.setIotDeviceId(record.getIotDeviceId());
        vo.setScore(record.getScore());
        vo.setGrade(record.getGrade());
        vo.setAnomalyPoints(record.getAnomalyPoints());
        vo.setRepairCount90d(record.getRepairCount90d());
        vo.setOpenRepairCount(record.getOpenRepairCount());
        vo.setDetailJson(record.getDetailJson());
        vo.setPrevScore(record.getPrevScore());
        vo.setScoreChange(record.getScoreChange());
        vo.setRecordDate(record.getRecordDate());
        vo.setCreateTime(record.getCreateTime());
        return vo;
    }

    private TableDataInfo<EqHealthRecordVo> buildPage(List<EqHealthRecordVo> data, PageQuery pageQuery) {
        TableDataInfo<EqHealthRecordVo> result = new TableDataInfo<>();
        int pageNum = Math.max(1, pageQuery.getPageNum());
        int pageSize = Math.max(1, pageQuery.getPageSize());
        int from = Math.min((pageNum - 1) * pageSize, data.size());
        int to = Math.min(from + pageSize, data.size());
        result.setRows(new ArrayList<>(data.subList(from, to)));
        result.setTotal(data.size());
        return result;
    }

    /**
     * EWMA 重放统计结果
     */
    private record EwmaStats(BigDecimal mean, BigDecimal std, int anomalyCount) {
    }
}
