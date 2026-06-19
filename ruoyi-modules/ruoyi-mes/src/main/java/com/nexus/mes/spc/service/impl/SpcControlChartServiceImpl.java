package com.nexus.mes.spc.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.dc.domain.vo.DcDataRecordVo;
import com.nexus.mes.dc.service.IDcDataRecordService;
import com.nexus.mes.spc.domain.SpcControlChart;
import com.nexus.mes.spc.domain.SpcDataPoint;
import com.nexus.mes.spc.domain.bo.SpcControlChartBo;
import com.nexus.mes.spc.domain.vo.SpcDataPointVo;
import com.nexus.mes.spc.domain.vo.SpcControlChartVo;
import com.nexus.mes.spc.mapper.SpcControlChartMapper;
import com.nexus.mes.spc.mapper.SpcDataPointMapper;
import com.nexus.mes.spc.service.ISpcControlChartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MES SPC控制图 Service 业务层实现
 * <p>
 * analyze: 拉采集点dc_data_record→按样本大小分组→按类型计算UCL/CL/LCL→回写图表→
 * 重建spc_data_point(value=组统计值)→4条OOC规则判定。
 * <p>
 * 图表类型与value:
 *   X_BAR_R/X_BAR_S: 计量型, 每组sampleSize个样本, value=组均值 x̄
 *   P: 计数型不良率, 每组一检验批, value=组不良率 p
 *   C: 计数型缺陷数, value=组缺陷数 c
 *   U: 计数型单位缺陷数, value=组单位缺陷数 u
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("spcControlChartService")
public class SpcControlChartServiceImpl implements ISpcControlChartService {

    private static final int SCALE = 4;
    private static final BigDecimal THREE = BigDecimal.valueOf(3);

    /** X_BAR_R 系数 A2 (index 0 = n=2 .. index 8 = n=10) */
    private static final double[] A2 = {1.880, 1.023, 0.729, 0.577, 0.483, 0.419, 0.373, 0.337, 0.308};
    /** X_BAR_S 系数 A3 (index 0 = n=2 .. index 8 = n=10) */
    private static final double[] A3 = {2.659, 1.954, 1.628, 1.427, 1.287, 1.182, 1.099, 1.032, 0.975};

    private final SpcControlChartMapper baseMapper;
    private final SpcDataPointMapper dataPointMapper;
    private final IDcDataRecordService dcDataRecordService;

    @Override
    public TableDataInfo<SpcControlChartVo> selectPageList(SpcControlChartBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SpcControlChart> lqw = buildQueryWrapper(bo);
        Page<SpcControlChartVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public SpcControlChartVo selectById(Long chartId) {
        return baseMapper.selectVoById(chartId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpcControlChartVo insertByBo(SpcControlChartBo bo) {
        SpcControlChart chart = new SpcControlChart();
        chart.setChartName(bo.getChartName());
        chart.setChartType(bo.getChartType());
        chart.setPointId(bo.getPointId());
        chart.setMaterialId(bo.getMaterialId());
        chart.setProcessId(bo.getProcessId());
        chart.setSampleSize(bo.getSampleSize() != null ? bo.getSampleSize() : 5);
        chart.setStatus(StringUtils.isNotBlank(bo.getStatus()) ? bo.getStatus() : STATUS_ACTIVE);
        chart.setRemark(bo.getRemark());
        baseMapper.insert(chart);
        log.info("新增控制图: chartId={}, type={}", chart.getChartId(), chart.getChartType());
        return baseMapper.selectVoById(chart.getChartId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpcControlChartVo updateByBo(SpcControlChartBo bo) {
        if (bo.getChartId() == null) {
            throw new RuntimeException("控制图ID不能为空");
        }
        SpcControlChart chart = baseMapper.selectById(bo.getChartId());
        if (chart == null) {
            throw new RuntimeException("控制图不存在: " + bo.getChartId());
        }
        chart.setChartName(bo.getChartName());
        if (StringUtils.isNotBlank(bo.getChartType())) {
            chart.setChartType(bo.getChartType());
        }
        chart.setPointId(bo.getPointId());
        chart.setMaterialId(bo.getMaterialId());
        chart.setProcessId(bo.getProcessId());
        if (bo.getSampleSize() != null) {
            chart.setSampleSize(bo.getSampleSize());
        }
        if (StringUtils.isNotBlank(bo.getStatus())) {
            chart.setStatus(bo.getStatus());
        }
        chart.setRemark(bo.getRemark());
        baseMapper.updateById(chart);
        log.info("编辑控制图: chartId={}", chart.getChartId());
        return baseMapper.selectVoById(chart.getChartId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return false;
        }
        // 级联删除数据点
        for (Long chartId : ids) {
            dataPointMapper.delete(Wrappers.<SpcDataPoint>lambdaQuery().eq(SpcDataPoint::getChartId, chartId));
        }
        return baseMapper.deleteByIds(Arrays.asList(ids)) > 0;
    }

    /**
     * 分析控制图(核心): 拉数据→分组→计算控制限→回写→重建数据点+OOC判定
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> analyze(Long chartId) {
        SpcControlChart chart = baseMapper.selectById(chartId);
        if (chart == null) {
            throw new RuntimeException("控制图不存在: " + chartId);
        }
        if (chart.getPointId() == null) {
            throw new RuntimeException("控制图未关联采集点, 无法分析");
        }
        int sampleSize = chart.getSampleSize() != null ? chart.getSampleSize() : 5;
        String chartType = chart.getChartType();

        // 1. 拉采集点数据(按collect_time升序, 仅numeric_value非空)
        List<DcDataRecordVo> records = dcDataRecordService.listByPointId(chart.getPointId());
        if (records.isEmpty()) {
            throw new RuntimeException("采集点无可用数据, 无法分析");
        }

        // 2. 按样本大小分组, 计算每组统计值
        List<GroupStat> groups = buildGroups(records, sampleSize, chartType);
        if (groups.isEmpty()) {
            throw new RuntimeException("分组后无有效样本组(数据不足或尾部丢弃)");
        }

        // 3. 计算控制限 UCL/CL/LCL
        ControlLimit limit = calcControlLimit(groups, chartType, sampleSize);

        // 4. 回写图表控制限
        chart.setUcl(limit.ucl);
        chart.setCl(limit.cl);
        chart.setLcl(limit.lcl);
        baseMapper.updateById(chart);

        // 5. 删除旧数据点, 生成新数据点 + OOC判定
        dataPointMapper.delete(Wrappers.<SpcDataPoint>lambdaQuery().eq(SpcDataPoint::getChartId, chartId));
        List<SpcDataPointVo> pointVos = new ArrayList<>();
        int oocCount = 0;
        for (int i = 0; i < groups.size(); i++) {
            GroupStat g = groups.get(i);
            SpcDataPoint point = new SpcDataPoint();
            point.setChartId(chartId);
            point.setSampleGroup(i + 1);
            point.setSampleIndex(null);
            point.setValue(g.statValue);
            point.setCollectTime(g.collectTime);
            baseMapperInsert(dataPointMapper, point);
        }
        // OOC判定需在所有点生成后基于完整序列
        List<SpcDataPointVo> allPoints = listPointsForOoc(dataPointMapper, chartId);
        for (SpcDataPointVo pv : allPoints) {
            String rules = judgeOoc(pv.getValue(), limit, allPoints);
            if (StringUtils.isNotBlank(rules)) {
                dataPointMapper.update(null, Wrappers.<SpcDataPoint>lambdaUpdate()
                    .set(SpcDataPoint::getIsOoc, "1")
                    .set(SpcDataPoint::getOocRule, rules)
                    .eq(SpcDataPoint::getDataId, pv.getDataId()));
                pv.setIsOoc("1");
                pv.setOocRule(rules);
                oocCount++;
            }
            pointVos.add(pv);
        }

        log.info("控制图分析完成: chartId={}, type={}, groups={}, oocCount={}, UCL={}, CL={}, LCL={}",
            chartId, chartType, groups.size(), oocCount, limit.ucl, limit.cl, limit.lcl);

        Map<String, Object> result = new HashMap<>();
        result.put("chart", baseMapper.selectVoById(chartId));
        result.put("points", pointVos);
        result.put("oocCount", oocCount);
        result.put("ucl", limit.ucl);
        result.put("cl", limit.cl);
        result.put("lcl", limit.lcl);
        return result;
    }

    // ==================== 分组与统计 ====================

    /** 组统计: 统计值 + 极差/标准差/样本量 + 采集时间(取组内首条) */
    private static class GroupStat {
        BigDecimal statValue;
        BigDecimal rangeOrStd;
        int n;
        java.util.Date collectTime;
    }

    /**
     * 按样本大小分组并计算每组统计值
     * X_BAR_R/X_BAR_S: 每组sampleSize个样本, 尾部不足丢弃, value=组均值, rangeOrStd=极差/标准差
     * P/C/U: 每条记录为一个检验批(组), value=不良率/缺陷数/单位缺陷数
     */
    private List<GroupStat> buildGroups(List<DcDataRecordVo> records, int sampleSize, String chartType) {
        List<GroupStat> groups = new ArrayList<>();
        if (TYPE_X_BAR_R.equals(chartType) || TYPE_X_BAR_S.equals(chartType)) {
            // 计量型: 每 sampleSize 个样本一组, 尾部不足丢弃
            int total = records.size();
            int groupCount = total / sampleSize;
            for (int g = 0; g < groupCount; g++) {
                GroupStat gs = new GroupStat();
                gs.n = sampleSize;
                gs.collectTime = records.get(g * sampleSize).getCollectTime();
                BigDecimal sum = BigDecimal.ZERO;
                BigDecimal min = null, max = null;
                List<BigDecimal> vals = new ArrayList<>();
                for (int i = 0; i < sampleSize; i++) {
                    BigDecimal v = records.get(g * sampleSize + i).getNumericValue();
                    if (v == null) v = BigDecimal.ZERO;
                    sum = sum.add(v);
                    vals.add(v);
                    if (min == null || v.compareTo(min) < 0) min = v;
                    if (max == null || v.compareTo(max) > 0) max = v;
                }
                BigDecimal mean = sum.divide(BigDecimal.valueOf(sampleSize), SCALE, RoundingMode.HALF_UP);
                gs.statValue = mean;
                if (TYPE_X_BAR_R.equals(chartType)) {
                    gs.rangeOrStd = max.subtract(min);
                } else {
                    // 标准差 S = sqrt(Σ(xi-x̄)²/(n-1))
                    BigDecimal sqSum = BigDecimal.ZERO;
                    for (BigDecimal v : vals) {
                        sqSum = sqSum.add(v.subtract(mean).pow(2));
                    }
                    double variance = sqSum.divide(BigDecimal.valueOf(sampleSize - 1), SCALE, RoundingMode.HALF_UP).doubleValue();
                    gs.rangeOrStd = BigDecimal.valueOf(Math.sqrt(variance)).setScale(SCALE, RoundingMode.HALF_UP);
                }
                groups.add(gs);
            }
        } else {
            // 计数型 P/C/U: 每条记录为一组(检验批)
            // 约定: numeric_value 直接作为缺陷数/不良数; P图不良率 = 不良数/样本量(用sampleSize作检验数n)
            //       U图单位缺陷数 = 缺陷数/单位数(用sampleSize作单位数)
            for (DcDataRecordVo r : records) {
                BigDecimal v = r.getNumericValue();
                if (v == null) continue;
                GroupStat gs = new GroupStat();
                gs.n = sampleSize;
                gs.collectTime = r.getCollectTime();
                if (TYPE_P.equals(chartType)) {
                    // 不良率 = 不良数 / 检验数(n)
                    gs.statValue = v.divide(BigDecimal.valueOf(sampleSize), SCALE, RoundingMode.HALF_UP);
                } else if (TYPE_C.equals(chartType)) {
                    gs.statValue = v; // 缺陷数原值
                } else if (TYPE_U.equals(chartType)) {
                    // 单位缺陷数 = 缺陷数 / 单位数(n)
                    gs.statValue = v.divide(BigDecimal.valueOf(sampleSize), SCALE, RoundingMode.HALF_UP);
                }
                groups.add(gs);
            }
        }
        return groups;
    }

    /** 控制限 */
    private static class ControlLimit {
        BigDecimal ucl;
        BigDecimal cl;
        BigDecimal lcl;
    }

    /**
     * 按图表类型计算 UCL/CL/LCL
     */
    private ControlLimit calcControlLimit(List<GroupStat> groups, String chartType, int sampleSize) {
        ControlLimit limit = new ControlLimit();
        switch (chartType) {
            case TYPE_X_BAR_R: {
                // CL = x̄̄ (组均值均值), UCL/LCL = x̄̄ ± A2(n) × R̄
                BigDecimal grandMean = mean(groups, g -> g.statValue);
                BigDecimal rBar = mean(groups, g -> g.rangeOrStd);
                double a2 = coefficient(A2, sampleSize);
                BigDecimal delta = BigDecimal.valueOf(a2).multiply(rBar).setScale(SCALE, RoundingMode.HALF_UP);
                limit.cl = grandMean;
                limit.ucl = grandMean.add(delta);
                limit.lcl = grandMean.subtract(delta);
                break;
            }
            case TYPE_X_BAR_S: {
                BigDecimal grandMean = mean(groups, g -> g.statValue);
                BigDecimal sBar = mean(groups, g -> g.rangeOrStd);
                double a3 = coefficient(A3, sampleSize);
                BigDecimal delta = BigDecimal.valueOf(a3).multiply(sBar).setScale(SCALE, RoundingMode.HALF_UP);
                limit.cl = grandMean;
                limit.ucl = grandMean.add(delta);
                limit.lcl = grandMean.subtract(delta);
                break;
            }
            case TYPE_P: {
                // p̄ = 总不良数/总检验数; UCL/LCL = p̄ ± 3√(p̄(1-p̄)/n̄)
                // 约定: 每组 statValue=不良率, 不良数=statValue×n; 总不良数=Σ(statValue×n), 总检验数=组数×n
                int k = groups.size();
                BigDecimal sumDefect = BigDecimal.ZERO;
                for (GroupStat g : groups) {
                    sumDefect = sumDefect.add(g.statValue.multiply(BigDecimal.valueOf(g.n)));
                }
                BigDecimal totalInspect = BigDecimal.valueOf((long) k * sampleSize);
                BigDecimal pBar = sumDefect.divide(totalInspect, SCALE, RoundingMode.HALF_UP);
                limit.cl = pBar;
                BigDecimal sqrtTerm = sqrtP(pBar, sampleSize);
                limit.ucl = pBar.add(THREE.multiply(sqrtTerm)).setScale(SCALE, RoundingMode.HALF_UP);
                limit.lcl = pBar.subtract(THREE.multiply(sqrtTerm)).setScale(SCALE, RoundingMode.HALF_UP);
                if (limit.lcl.compareTo(BigDecimal.ZERO) < 0) limit.lcl = BigDecimal.ZERO;
                break;
            }
            case TYPE_C: {
                // c̄ = 组缺陷数均值; UCL/LCL = c̄ ± 3√c̄
                BigDecimal cBar = mean(groups, g -> g.statValue);
                limit.cl = cBar;
                BigDecimal sqrtC = BigDecimal.valueOf(Math.sqrt(cBar.doubleValue())).setScale(SCALE, RoundingMode.HALF_UP);
                limit.ucl = cBar.add(THREE.multiply(sqrtC)).setScale(SCALE, RoundingMode.HALF_UP);
                limit.lcl = cBar.subtract(THREE.multiply(sqrtC)).setScale(SCALE, RoundingMode.HALF_UP);
                if (limit.lcl.compareTo(BigDecimal.ZERO) < 0) limit.lcl = BigDecimal.ZERO;
                break;
            }
            case TYPE_U: {
                // ū = 组单位缺陷数均值; UCL/LCL = ū ± 3√(ū/n̄)
                BigDecimal uBar = mean(groups, g -> g.statValue);
                limit.cl = uBar;
                BigDecimal sqrtTerm = BigDecimal.valueOf(Math.sqrt(uBar.doubleValue() / sampleSize))
                    .setScale(SCALE, RoundingMode.HALF_UP);
                limit.ucl = uBar.add(THREE.multiply(sqrtTerm)).setScale(SCALE, RoundingMode.HALF_UP);
                limit.lcl = uBar.subtract(THREE.multiply(sqrtTerm)).setScale(SCALE, RoundingMode.HALF_UP);
                if (limit.lcl.compareTo(BigDecimal.ZERO) < 0) limit.lcl = BigDecimal.ZERO;
                break;
            }
            default:
                throw new RuntimeException("不支持的图表类型: " + chartType);
        }
        limit.ucl = limit.ucl.setScale(SCALE, RoundingMode.HALF_UP);
        limit.cl = limit.cl.setScale(SCALE, RoundingMode.HALF_UP);
        limit.lcl = limit.lcl.setScale(SCALE, RoundingMode.HALF_UP);
        return limit;
    }

    // ==================== OOC 判定(4条规则) ====================

    /**
     * 判定单个数据点是否OOC, 返回命中的规则(逗号拼接), 无命中返回 null
     * RULE1: 1点超出控制限
     * RULE2: 连续9点在CL同侧
     * RULE3: 连续6点严格递增或递减
     * RULE4: 连续14点交替上下(zig-zag)
     */
    private String judgeOoc(BigDecimal value, ControlLimit limit, List<SpcDataPointVo> allPoints) {
        List<String> rules = new ArrayList<>();
        // RULE1: 超限
        if (value.compareTo(limit.ucl) > 0 || value.compareTo(limit.lcl) < 0) {
            rules.add("RULE1");
        }
        // 找当前点在序列中的索引
        int idx = -1;
        for (int i = 0; i < allPoints.size(); i++) {
            if (allPoints.get(i).getValue().compareTo(value) == 0) {
                idx = i;
                break;
            }
        }
        if (idx >= 0) {
            // RULE2: 连续9点(含当前)在CL同侧
            if (idx >= 8 && checkSameSide(allPoints, idx, 9, limit.cl)) {
                rules.add("RULE2");
            }
            // RULE3: 连续6点(含当前)严格递增或递减
            if (idx >= 5 && checkTrend(allPoints, idx, 6)) {
                rules.add("RULE3");
            }
            // RULE4: 连续14点(含当前)交替上下
            if (idx >= 13 && checkAlternating(allPoints, idx, 14)) {
                rules.add("RULE4");
            }
        }
        return rules.isEmpty() ? null : String.join(",", rules);
    }

    /** 检查从 idx 起向前(含idx)共 count 个点是否全在 CL 同侧 */
    private boolean checkSameSide(List<SpcDataPointVo> points, int idx, int count, BigDecimal cl) {
        int positive = 0, negative = 0;
        for (int i = idx - count + 1; i <= idx; i++) {
            BigDecimal v = points.get(i).getValue();
            int cmp = v.compareTo(cl);
            if (cmp > 0) positive++;
            else if (cmp < 0) negative++;
        }
        return positive == count || negative == count;
    }

    /** 检查从 idx 起向前(含idx)共 count 个点是否严格递增或递减 */
    private boolean checkTrend(List<SpcDataPointVo> points, int idx, int count) {
        boolean increasing = true, decreasing = true;
        for (int i = idx - count + 1; i < idx; i++) {
            BigDecimal prev = points.get(i).getValue();
            BigDecimal curr = points.get(i + 1).getValue();
            if (curr.compareTo(prev) <= 0) increasing = false;
            if (curr.compareTo(prev) >= 0) decreasing = false;
        }
        return increasing || decreasing;
    }

    /** 检查从 idx 起向前(含idx)共 count 个点是否交替上下(zig-zag, 相邻点差值符号交替) */
    private boolean checkAlternating(List<SpcDataPointVo> points, int idx, int count) {
        Integer prevSign = null;
        for (int i = idx - count + 1; i < idx; i++) {
            BigDecimal prev = points.get(i).getValue();
            BigDecimal curr = points.get(i + 1).getValue();
            int cmp = curr.compareTo(prev);
            if (cmp == 0) return false;
            int sign = cmp > 0 ? 1 : -1;
            if (prevSign != null && sign == prevSign) return false;
            prevSign = sign;
        }
        return true;
    }

    // ==================== 私有辅助方法 ====================

    private void baseMapperInsert(SpcDataPointMapper mapper, SpcDataPoint point) {
        mapper.insert(point);
    }

    private List<SpcDataPointVo> listPointsForOoc(SpcDataPointMapper mapper, Long chartId) {
        return mapper.selectVoList(Wrappers.<SpcDataPoint>lambdaQuery()
            .eq(SpcDataPoint::getChartId, chartId)
            .orderByAsc(SpcDataPoint::getSampleGroup));
    }

    /** 列表均值 */
    private BigDecimal mean(List<GroupStat> groups, java.util.function.Function<GroupStat, BigDecimal> picker) {
        BigDecimal sum = BigDecimal.ZERO;
        for (GroupStat g : groups) {
            sum = sum.add(picker.apply(g));
        }
        return sum.divide(BigDecimal.valueOf(groups.size()), SCALE, RoundingMode.HALF_UP);
    }

    /** P图 3σ 项: 3√(p̄(1-p̄)/n̄) 的内层平方根 */
    private BigDecimal sqrtP(BigDecimal pBar, int n) {
        double p = pBar.doubleValue();
        double inner = p * (1 - p) / n;
        if (inner <= 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(Math.sqrt(inner)).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** 取系数表(n=2..10, index 0=n=2) */
    private double coefficient(double[] table, int n) {
        if (n < 2 || n > 10) {
            throw new RuntimeException("样本大小仅支持 2~10, 当前: " + n);
        }
        return table[n - 2];
    }

    private LambdaQueryWrapper<SpcControlChart> buildQueryWrapper(SpcControlChartBo bo) {
        return Wrappers.<SpcControlChart>lambdaQuery()
            .like(StringUtils.isNotBlank(bo.getChartName()), SpcControlChart::getChartName, bo.getChartName())
            .eq(StringUtils.isNotBlank(bo.getChartType()), SpcControlChart::getChartType, bo.getChartType())
            .eq(StringUtils.isNotBlank(bo.getStatus()), SpcControlChart::getStatus, bo.getStatus())
            .eq(bo.getPointId() != null, SpcControlChart::getPointId, bo.getPointId())
            .eq(bo.getMaterialId() != null, SpcControlChart::getMaterialId, bo.getMaterialId())
            .orderByDesc(SpcControlChart::getCreateTime);
    }
}
