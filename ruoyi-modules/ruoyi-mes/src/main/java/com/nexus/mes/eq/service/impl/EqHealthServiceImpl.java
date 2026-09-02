package com.nexus.mes.eq.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.nexus.mes.pro.service.IProAndonService;
import com.nexus.common.iot.service.IotDeviceDataQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 异常点统计复用 EWMA 在线估计（与 IoT 管道 {@code IotAnomalyDetector} 同一算法），
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

    /**
     * EWMA 平滑系数(与 IoT 管道默认一致)
     */
    private static final double EWMA_LAMBDA = 0.3;

    /**
     * 异常 z-score 阈值
     */
    private static final double Z_THRESHOLD = 3.0;

    /**
     * EWMA 预热样本数
     */
    private static final int EWMA_WARMUP = 20;

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
            .notIn(EqRepair::getStatus, "COMPLETED", "CANCELLED"));

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

        // 6. 落库(同日重评则覆盖)
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

        EqHealthRecord existing = healthRecordMapper.selectOne(Wrappers.lambdaQuery(EqHealthRecord.class)
            .eq(EqHealthRecord::getEquipmentId, equipment.getEquipmentId())
            .apply("DATE(record_date) = DATE({0})", now)
            .last("LIMIT 1"));
        if (existing != null) {
            record.setRecordId(existing.getRecordId());
            healthRecordMapper.updateById(record);
        } else {
            healthRecordMapper.insert(record);
        }

        // 7. LOW 且评分下滑 → 安灯
        if ("LOW".equals(grade) && (scoreChange == null || scoreChange < 0)) {
            String reason = String.format("设备健康预警: 设备[%s(%s)] 健康评分 %d(%s), 较前次%s, 24h异常点 %d, 未闭环维修 %d",
                equipment.getEquipmentName(), equipment.getEquipmentCode(), score, grade,
                scoreChange == null ? "首次评估" : ("下降 " + Math.abs(scoreChange)), anomalyPoints, openRepairs);
            try {
                andonService.callAndon(null, null, null, IProAndonService.TYPE_EQUIPMENT, reason);
            } catch (Exception e) {
                log.warn("[设备健康] 设备 {} 触发安灯失败: {}", equipment.getEquipmentId(), e.getMessage());
            }
        }
        return record;
    }

    /**
     * EWMA 离线重放: 按时间序逐点更新均值/方差, 预热后统计 z>3 的点数
     */
    private EwmaStats replayEwma(List<Map<String, Object>> series) {
        BigDecimal mean = null;
        BigDecimal variance = BigDecimal.ZERO;
        int count = 0;
        int anomalyCount = 0;
        BigDecimal lambda = BigDecimal.valueOf(EWMA_LAMBDA);
        for (Map<String, Object> data : series) {
            BigDecimal x = toBigDecimal(data.get("numericValue"));
            if (x == null) {
                continue;
            }
            if (mean == null) {
                mean = x;
                variance = BigDecimal.ZERO;
                count = 1;
                continue;
            }
            BigDecimal std = variance.compareTo(BigDecimal.ZERO) <= 0
                ? new BigDecimal("1e-9") : variance.sqrt(MathContext.DECIMAL64);
            if (count >= EWMA_WARMUP) {
                BigDecimal z = x.subtract(mean).abs().divide(std, 4, RoundingMode.HALF_UP);
                if (z.compareTo(BigDecimal.valueOf(Z_THRESHOLD)) > 0) {
                    anomalyCount++;
                }
            }
            BigDecimal diff = x.subtract(mean);
            mean = lambda.multiply(x, MathContext.DECIMAL64)
                .add(BigDecimal.ONE.subtract(lambda).multiply(mean, MathContext.DECIMAL64));
            variance = BigDecimal.ONE.subtract(lambda).multiply(
                variance.add(lambda.multiply(diff.multiply(diff, MathContext.DECIMAL64), MathContext.DECIMAL64), MathContext.DECIMAL64),
                MathContext.DECIMAL64);
            count++;
        }
        BigDecimal std = variance.compareTo(BigDecimal.ZERO) <= 0
            ? BigDecimal.ZERO : variance.sqrt(MathContext.DECIMAL64);
        return new EwmaStats(mean == null ? BigDecimal.ZERO : mean, std, anomalyCount);
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
