package com.nexus.mes.spc.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.mes.dc.domain.vo.DcDataRecordVo;
import com.nexus.mes.dc.service.IDcDataRecordService;
import com.nexus.mes.pro.service.IProAndonService;
import com.nexus.mes.spc.domain.SpcControlChart;
import com.nexus.mes.spc.domain.SpcDataPoint;
import com.nexus.mes.spc.domain.bo.SpcControlChartBo;
import com.nexus.mes.spc.domain.vo.SpcControlChartVo;
import com.nexus.mes.spc.domain.vo.SpcDataPointVo;
import com.nexus.mes.spc.mapper.SpcControlChartMapper;
import com.nexus.mes.spc.mapper.SpcDataPointMapper;
import com.nexus.mes.spc.service.ISpcControlChartService;
import com.nexus.mes.spc.service.SpcCapabilityCalculator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * MES SPC控制图 Service 业务层实现
 * <p>
 * analyze: 拉采集点dc_data_record(仅最近 N 组)→按样本大小分组→按类型计算UCL/CL/LCL(含次级图
 * R/S/MR 控制限)→回写图表→按 sample_group upsert spc_data_point(value=组统计值, sub_value=组内离散度)
 * →主图与离散度序列各跑一遍 OOC 规则。
 * <p>
 * 图表类型与value:
 *   X_BAR_R/X_BAR_S: 计量型, 每组sampleSize个样本, value=组均值 x̄, sub_value=组极差R/组标准差S
 *   X_MR: 单值-移动极差, 每条记录一组, value=单值 x, sub_value=移动极差 MR
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
    /** R 图系数 D3 (index 0 = n=2 .. index 8 = n=10; n<=6 时 D3=0) */
    private static final double[] D3 = {0.000, 0.000, 0.000, 0.000, 0.000, 0.076, 0.136, 0.184, 0.223};
    /** R 图系数 D4 (index 0 = n=2 .. index 8 = n=10) */
    private static final double[] D4 = {3.267, 2.574, 2.282, 2.114, 2.004, 1.924, 1.864, 1.816, 1.777};
    /** S 图系数 B3 (index 0 = n=2 .. index 8 = n=10; n<=5 时 B3=0) */
    private static final double[] B3 = {0.000, 0.000, 0.000, 0.000, 0.030, 0.118, 0.185, 0.239, 0.284};
    /** S 图系数 B4 (index 0 = n=2 .. index 8 = n=10) */
    private static final double[] B4 = {3.267, 2.568, 2.266, 2.089, 1.970, 1.882, 1.815, 1.761, 1.716};
    /** X-MR 系数 E2: n=2 时 E2 = 3/d2(2) = 3/1.128 ≈ 2.660 (index 0 = n=2) */
    private static final double[] E2 = {2.660};

    /** 控制图数据访问 Mapper。 */
    private final SpcControlChartMapper baseMapper;
    /** 数据点数据访问 Mapper。 */
    private final SpcDataPointMapper dataPointMapper;
    /** 采集点数据记录服务，用于分析时拉取采集数据。 */
    private final IDcDataRecordService dcDataRecordService;
    /** 安灯服务，用于 OOC 命中 RULE1 时联动告警。 */
    private final IProAndonService proAndonService;

    /** 单次分析最多取的样本组数(默认 25 组), 避免全量拉取采集点历史。 */
    @Value("${mes.spc.analysis-max-groups:25}")
    private int analysisMaxGroups = 25;

    /**
     * 分页查询 SPC 控制图列表。
     *
     * @param bo        控制图查询条件（名称/类型/状态/采集点/物料）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果，包含控制图视图列表及分页元信息
     */
    @Override
    public TableDataInfo<SpcControlChartVo> selectPageList(SpcControlChartBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SpcControlChart> lqw = buildQueryWrapper(bo);
        Page<SpcControlChartVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 根据主键 ID 获取 SPC 控制图详情。
     *
     * @param chartId 控制图 ID
     * @return 控制图视图对象
     */
    @Override
    public SpcControlChartVo selectById(Long chartId) {
        return baseMapper.selectVoById(chartId);
    }

    /**
     * 新增 SPC 控制图，默认样本大小为 5、状态为启用（ACTIVE）。
     *
     * @param bo 控制图业务对象
     * @return 新增后的控制图视图对象
     */
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

    /**
     * 编辑 SPC 控制图基础信息（控制限由 analyze 接口计算回写，不在此更新）。
     *
     * @param bo 控制图业务对象（需包含 chartId）
     * @return 编辑后的控制图视图对象
     */
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
        // 规格限为用户维护项: 原先漏写导致 USL/LSL 永远为空, 过程能力(Cp/Cpk/Pp/Ppk)无从计算
        chart.setUsl(bo.getUsl());
        chart.setLsl(bo.getLsl());
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

    /**
     * 批量删除 SPC 控制图，并级联删除其下数据点。
     *
     * @param ids 待删除的控制图 ID 数组
     * @return 是否删除成功
     */
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
     * 分析 SPC 控制图（核心）：按"最近 N 组"拉取采集点数据→按样本分组→按类型计算 UCL/CL/LCL
     * 与次级图(R/S/MR)控制限并回写→按 sample_group upsert 数据点→主图与离散度序列各跑一遍 OOC
     * 规则判定，命中 RULE1 时联动质量安灯。
     *
     * @param chartId 控制图 ID
     * @return 分析结果，包含 chart / points / oocCount / ucl / cl / lcl / rUcl / rCl / rLcl /
     *     pp / ppk / capabilityInsufficient / capabilityInsufficientReason
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

        // 1. 拉采集点数据: 只取"最近 N 组"所需样本(collect_time DESC LIMIT k 后内存反转为升序),
        //    不再全量加载采集点历史(MES-DC-04)
        int maxGroups = analysisMaxGroups > 0 ? analysisMaxGroups : 25;
        int fetchLimit;
        if (TYPE_X_BAR_R.equals(chartType) || TYPE_X_BAR_S.equals(chartType)) {
            // 每组 sampleSize 条样本
            fetchLimit = maxGroups * sampleSize;
        } else if (TYPE_X_MR.equals(chartType)) {
            // 单值图每个单值即一组: 至少取到过程能力所需的最小样本数, 否则能力分析永远因样本不足被跳过
            fetchLimit = Math.max(maxGroups, MIN_CAPABILITY_SAMPLES);
        } else {
            // 计数型 P/C/U: 每条记录即一组
            fetchLimit = maxGroups;
        }
        List<DcDataRecordVo> records = dcDataRecordService.listByPointId(chart.getPointId(), null, null, fetchLimit);
        if (records.isEmpty()) {
            throw new RuntimeException("采集点无可用数据, 无法分析");
        }

        // 2. 按样本大小分组, 计算每组统计值
        List<GroupStat> groups = buildGroups(records, sampleSize, chartType);
        if (groups.isEmpty()) {
            throw new RuntimeException("分组后无有效样本组(数据不足或尾部丢弃)");
        }

        // 3. 计算控制限 UCL/CL/LCL(含次级图 R/S/MR 控制限)
        ControlLimit limit = calcControlLimit(groups, chartType, sampleSize);

        // 3.5 过程能力分析(计量型图且已维护规格限): Cp/Cpk(组内σ) + Pp/Ppk(整体s) 回写
        SpcCapabilityCalculator.Capability capability = calcCapability(chart, groups, chartType, sampleSize);
        SpcCapabilityCalculator.LongTermCapability longTerm = calcLongTermCapability(chart, records, chartType, groups);
        String insufficientReason = capabilityInsufficientReason(chart, groups, chartType);
        boolean insufficient = insufficientReason != null;
        if (insufficient) {
            // 子组数/总样本数不足(或规格限缺失): 不回写 cp/cpk, 避免小样本算出 A+ 等级误导
            chart.setCp(null);
            chart.setCpk(null);
            chart.setCpkGrade(null);
            chart.setPp(null);
            chart.setPpk(null);
            log.warn("过程能力未回写: chartId={}, reason={}", chartId, insufficientReason);
        } else {
            chart.setCp(capability == null ? null : capability.cp());
            chart.setCpk(capability == null ? null : capability.cpk());
            chart.setCpkGrade(capability == null ? null : capability.grade());
            chart.setPp(longTerm == null ? null : longTerm.pp());
            chart.setPpk(longTerm == null ? null : longTerm.ppk());
            log.info("过程能力分析: chartId={}, Cp={}, Cpk={}, Pp={}, Ppk={}, 等级={}, σ组内={}",
                chartId, chart.getCp(), chart.getCpk(), chart.getPp(), chart.getPpk(), chart.getCpkGrade(),
                capability == null ? null : capability.sigmaWithin());
        }

        // 4. 回写图表控制限(主图 + 次级图)
        chart.setUcl(limit.ucl);
        chart.setCl(limit.cl);
        chart.setLcl(limit.lcl);
        chart.setRUcl(limit.rUcl);
        chart.setRCl(limit.rCl);
        chart.setRLcl(limit.rLcl);
        baseMapper.updateById(chart);

        // 5. 数据点按 sample_group upsert(保持 data_id 稳定), 不再全量删除重建
        upsertDataPoints(chartId, groups);

        // 6. OOC判定: 需在所有点生成后基于完整序列, 主图与组内离散度序列各判一遍
        List<SpcDataPointVo> allPoints = listPointsForOoc(dataPointMapper, chartId);
        Set<Long> oocPointIds = new HashSet<>();
        for (int i = 0; i < allPoints.size(); i++) {
            SpcDataPointVo pv = allPoints.get(i);
            String rules = judgeOoc(pv.getValue(), limit, allPoints, i);
            if (StringUtils.isNotBlank(rules)) {
                markOoc(pv, rules);
                oocPointIds.add(pv.getDataId());
                // OOC 命中 RULE1 → 触发 QUALITY 安灯(10 分钟限频)
                if (rules.contains(OOC_RULE_1)) {
                    triggerAndonForOoc(chart, pv, rules);
                }
            }
        }
        // 6.1 组内离散度(R/S/MR)序列独立跑一遍 OOC: 组内波动失控同样要能触发
        if (limit.rCl != null) {
            ControlLimit subLimit = new ControlLimit();
            subLimit.ucl = limit.rUcl;
            subLimit.cl = limit.rCl;
            subLimit.lcl = limit.rLcl;
            for (int i = 0; i < allPoints.size(); i++) {
                SpcDataPointVo pv = allPoints.get(i);
                if (pv.getSubValue() == null) {
                    continue;
                }
                String subRules = judgeOoc(pv.getSubValue(), subLimit, allPoints, i,
                    SpcDataPointVo::getSubValue, OOC_RULE_SUB_SUFFIX);
                if (StringUtils.isBlank(subRules)) {
                    continue;
                }
                String merged = StringUtils.isBlank(pv.getOocRule()) ? subRules : pv.getOocRule() + "," + subRules;
                markOoc(pv, merged);
                oocPointIds.add(pv.getDataId());
                if (subRules.contains(OOC_RULE_1)) {
                    triggerAndonForOoc(chart, pv, merged);
                }
            }
        }
        int oocCount = oocPointIds.size();

        log.info("控制图分析完成: chartId={}, type={}, groups={}, oocCount={}, UCL={}, CL={}, LCL={}, RUCL={}, RCL={}, RLCL={}",
            chartId, chartType, groups.size(), oocCount, limit.ucl, limit.cl, limit.lcl,
            limit.rUcl, limit.rCl, limit.rLcl);

        Map<String, Object> result = new HashMap<>();
        SpcControlChartVo chartVo = baseMapper.selectVoById(chartId);
        if (chartVo != null) {
            chartVo.setCapabilityInsufficient(insufficient);
            chartVo.setCapabilityInsufficientReason(insufficientReason);
        }
        result.put("chart", chartVo);
        result.put("points", allPoints);
        result.put("oocCount", oocCount);
        result.put("ucl", limit.ucl);
        result.put("cl", limit.cl);
        result.put("lcl", limit.lcl);
        result.put("rUcl", limit.rUcl);
        result.put("rCl", limit.rCl);
        result.put("rLcl", limit.rLcl);
        result.put("cp", chart.getCp());
        result.put("cpk", chart.getCpk());
        result.put("cpkGrade", chart.getCpkGrade());
        result.put("pp", chart.getPp());
        result.put("ppk", chart.getPpk());
        result.put("capabilityInsufficient", insufficient);
        result.put("capabilityInsufficientReason", insufficientReason);
        return result;
    }

    /**
     * 数据点按 sample_group upsert：已存在的组 {@code updateById}（data_id 保持稳定），
     * 新组 insert，最后物理删除组号超出本次范围的旧点（{@code sample_group > N}），
     * 既清理多余组也清掉历史 del_flag='2' 僵尸行。
     */
    private void upsertDataPoints(Long chartId, List<GroupStat> groups) {
        List<SpcDataPoint> existing = dataPointMapper.selectList(Wrappers.<SpcDataPoint>lambdaQuery()
            .eq(SpcDataPoint::getChartId, chartId)
            .orderByAsc(SpcDataPoint::getSampleGroup));
        Map<Integer, SpcDataPoint> existingByGroup = new HashMap<>();
        if (existing != null) {
            for (SpcDataPoint p : existing) {
                if (p.getSampleGroup() != null) {
                    existingByGroup.putIfAbsent(p.getSampleGroup(), p);
                }
            }
        }
        for (int i = 0; i < groups.size(); i++) {
            GroupStat g = groups.get(i);
            int groupNo = i + 1;
            SpcDataPoint point = existingByGroup.get(groupNo);
            if (point != null) {
                point.setSampleIndex(null);
                point.setValue(g.statValue);
                point.setSubValue(g.rangeOrStd);
                point.setCollectTime(g.collectTime);
                // 复位 OOC 标记后再判定: oocRule 用空串而非 null(MyBatis-Plus 默认 NOT_NULL 策略会跳过 null 字段)
                point.setIsOoc("0");
                point.setOocRule("");
                dataPointMapper.updateById(point);
            } else {
                point = new SpcDataPoint();
                point.setChartId(chartId);
                point.setSampleGroup(groupNo);
                point.setSampleIndex(null);
                point.setValue(g.statValue);
                point.setSubValue(g.rangeOrStd);
                point.setCollectTime(g.collectTime);
                point.setIsOoc("0");
                point.setOocRule("");
                dataPointMapper.insert(point);
            }
        }
        // 组号连续(1..N), 范围外即 sample_group > N; 物理删除避免 del_flag='2' 僵尸行累积
        int removed = dataPointMapper.physicalDeleteOutOfRange(chartId, groups.size());
        if (removed > 0) {
            log.info("控制图 {} 清理范围外旧数据点 {} 个", chartId, removed);
        }
    }

    /** 标记数据点为 OOC 并回写库(内存 VO 同步) */
    private void markOoc(SpcDataPointVo pv, String rules) {
        dataPointMapper.update(null, Wrappers.<SpcDataPoint>lambdaUpdate()
            .set(SpcDataPoint::getIsOoc, "1")
            .set(SpcDataPoint::getOocRule, rules)
            .eq(SpcDataPoint::getDataId, pv.getDataId()));
        pv.setIsOoc("1");
        pv.setOocRule(rules);
    }

    /**
     * 过程能力计算: 仅计量型(X_BAR_R/X_BAR_S/X_MR)且 USL/LSL 至少维护一侧时进行;
     * X_BAR_R 用 R̄/d2 估计组内σ, X_BAR_S 用 S̄/c4, X_MR 用 MR̄/d2(2); 计数型图返回 null。
     */
    private SpcCapabilityCalculator.Capability calcCapability(SpcControlChart chart, List<GroupStat> groups,
                                                              String chartType, int sampleSize) {
        if (!isMeasurableChart(chartType) || (chart.getUsl() == null && chart.getLsl() == null)) {
            return null;
        }
        BigDecimal grandMean = mean(groups, g -> g.statValue);
        if (TYPE_X_BAR_R.equals(chartType)) {
            BigDecimal rBar = mean(groups, g -> g.rangeOrStd);
            return SpcCapabilityCalculator.calcByRange(grandMean, rBar, sampleSize, chart.getUsl(), chart.getLsl());
        }
        if (TYPE_X_BAR_S.equals(chartType)) {
            BigDecimal sBar = mean(groups, g -> g.rangeOrStd);
            return SpcCapabilityCalculator.calcByStd(grandMean, sBar, sampleSize, chart.getUsl(), chart.getLsl());
        }
        // X_MR: 单值图, 组内σ 用移动极差 MR̄/d2(2) 估计
        return SpcCapabilityCalculator.calcByRange(grandMean, meanSub(groups), 2, chart.getUsl(), chart.getLsl());
    }

    /**
     * 长期过程性能 Pp/Ppk: 用参与本次分析的全部单体测量值的整体样本标准差 s(n−1)。
     */
    private SpcCapabilityCalculator.LongTermCapability calcLongTermCapability(SpcControlChart chart,
                                                                             List<DcDataRecordVo> records,
                                                                             String chartType, List<GroupStat> groups) {
        if (!isMeasurableChart(chartType) || (chart.getUsl() == null && chart.getLsl() == null)) {
            return null;
        }
        List<BigDecimal> values = new ArrayList<>();
        for (DcDataRecordVo r : records) {
            if (r.getNumericValue() != null) {
                values.add(r.getNumericValue());
            }
        }
        BigDecimal sigmaLongTerm = SpcCapabilityCalculator.sampleStdDev(values);
        if (sigmaLongTerm == null) {
            return null;
        }
        return SpcCapabilityCalculator.calcByStdLongTerm(mean(groups, g -> g.statValue), sigmaLongTerm,
            chart.getUsl(), chart.getLsl());
    }

    /**
     * 过程能力样本量校验: 子组数 &lt; 25 或总样本数 &lt; 50 或规格限完全缺失时返回原因文案
     * (非计量型图返回 null, 不做能力分析)。
     */
    private String capabilityInsufficientReason(SpcControlChart chart, List<GroupStat> groups, String chartType) {
        if (!isMeasurableChart(chartType)) {
            return null;
        }
        int subgroupCount = groups.size();
        int totalSamples = 0;
        for (GroupStat g : groups) {
            totalSamples += Math.max(g.n, 0);
        }
        if (subgroupCount < MIN_CAPABILITY_SUBGROUPS || totalSamples < MIN_CAPABILITY_SAMPLES) {
            return String.format("样本量不足: 子组数 %d/%d, 总样本数 %d/%d",
                subgroupCount, MIN_CAPABILITY_SUBGROUPS, totalSamples, MIN_CAPABILITY_SAMPLES);
        }
        if (chart.getUsl() == null && chart.getLsl() == null) {
            return "未维护规格限(USL/LSL), 无法计算过程能力";
        }
        return null;
    }

    /** 是否为可做过程能力的计量型图 */
    private boolean isMeasurableChart(String chartType) {
        return TYPE_X_BAR_R.equals(chartType) || TYPE_X_BAR_S.equals(chartType) || TYPE_X_MR.equals(chartType);
    }

    // ==================== 分组与统计 ====================

    /** 组统计: 统计值 + 极差/标准差/样本量 + 采集时间(取组内首条) */
    private static class GroupStat {
        BigDecimal statValue;
        BigDecimal rangeOrStd;
        int n;
        Date collectTime;
    }

    /**
     * 按样本大小分组并计算每组统计值
     * X_BAR_R/X_BAR_S: 每组sampleSize个样本, 尾部不足丢弃, value=组均值, rangeOrStd=极差/标准差
     * X_MR: 每条记录为一个单值点, value=单值, rangeOrStd=移动极差|xi−x(i−1)|(首点为 null)
     * P/C/U: 每条记录为一个检验批(组), value=不良率/缺陷数/单位缺陷数
     */
    private List<GroupStat> buildGroups(List<DcDataRecordVo> records, int sampleSize, String chartType) {
        List<GroupStat> groups = new ArrayList<>();
        if (TYPE_X_MR.equals(chartType)) {
            // 单值-移动极差: 每个单值一组, MR 由相邻单值差得到(首点无移动极差)
            BigDecimal prev = null;
            for (DcDataRecordVo r : records) {
                BigDecimal v = r.getNumericValue();
                if (v == null) continue;
                GroupStat gs = new GroupStat();
                gs.n = 1;
                gs.collectTime = r.getCollectTime();
                gs.statValue = v;
                gs.rangeOrStd = prev == null ? null : v.subtract(prev).abs();
                prev = v;
                groups.add(gs);
            }
        } else if (TYPE_X_BAR_R.equals(chartType) || TYPE_X_BAR_S.equals(chartType)) {
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

    /** 控制限(主图 ucl/cl/lcl + 次级图 rUcl/rCl/rLcl, 计数型图无次级图) */
    private static class ControlLimit {
        BigDecimal ucl;
        BigDecimal cl;
        BigDecimal lcl;
        BigDecimal rUcl;
        BigDecimal rCl;
        BigDecimal rLcl;
    }

    /**
     * 按图表类型计算 UCL/CL/LCL(主图) 与次级图控制限
     * <pre>
     *   X_BAR_R: x̄̄ ± A2·R̄        ; R 图 UCL=D4·R̄, CL=R̄, LCL=D3·R̄ (n&lt;=6 时 D3=0)
     *   X_BAR_S: x̄̄ ± A3·S̄        ; S 图 UCL=B4·S̄, CL=S̄, LCL=B3·S̄ (n&lt;=5 时 B3=0)
     *   X_MR   : x̄ ± E2·MR̄ (E2=2.660); MR 图 UCL=D4(2)·MR̄=3.267·MR̄, CL=MR̄, LCL=D3(2)·MR̄=0
     * </pre>
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
                // R 图(组内离散度): UCL = D4·R̄, CL = R̄, LCL = D3·R̄
                limit.rCl = rBar;
                limit.rUcl = BigDecimal.valueOf(coefficient(D4, sampleSize)).multiply(rBar);
                limit.rLcl = BigDecimal.valueOf(coefficient(D3, sampleSize)).multiply(rBar);
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
                // S 图(组内离散度): UCL = B4·S̄, CL = S̄, LCL = B3·S̄
                limit.rCl = sBar;
                limit.rUcl = BigDecimal.valueOf(coefficient(B4, sampleSize)).multiply(sBar);
                limit.rLcl = BigDecimal.valueOf(coefficient(B3, sampleSize)).multiply(sBar);
                break;
            }
            case TYPE_X_MR: {
                // 单值图: x̄ ± E2·MR̄ (E2 = 3/d2(2) = 2.660)
                BigDecimal xBar = mean(groups, g -> g.statValue);
                BigDecimal mrBar = meanSub(groups);
                BigDecimal delta = BigDecimal.valueOf(E2[0]).multiply(mrBar).setScale(SCALE, RoundingMode.HALF_UP);
                limit.cl = xBar;
                limit.ucl = xBar.add(delta);
                limit.lcl = xBar.subtract(delta);
                // MR 图: n=2 系数 D4=3.267, D3=0
                limit.rCl = mrBar;
                limit.rUcl = BigDecimal.valueOf(coefficient(D4, 2)).multiply(mrBar);
                limit.rLcl = BigDecimal.valueOf(coefficient(D3, 2)).multiply(mrBar);
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
        if (limit.rUcl != null) limit.rUcl = limit.rUcl.setScale(SCALE, RoundingMode.HALF_UP);
        if (limit.rCl != null) limit.rCl = limit.rCl.setScale(SCALE, RoundingMode.HALF_UP);
        if (limit.rLcl != null) limit.rLcl = limit.rLcl.setScale(SCALE, RoundingMode.HALF_UP);
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
    private String judgeOoc(BigDecimal value, ControlLimit limit, List<SpcDataPointVo> allPoints, int currentIndex) {
        return judgeOoc(value, limit, allPoints, currentIndex, SpcDataPointVo::getValue, "");
    }

    /**
     * 判定单个数据点是否OOC(可指定取值字段与规则后缀, 供次级图离散度序列复用同一套规则)
     *
     * @param extractor  取值函数(主图取 value, 次级图取 subValue)
     * @param ruleSuffix 规则后缀(次级图用 {@link #OOC_RULE_SUB_SUFFIX}, 主图传空串)
     */
    private String judgeOoc(BigDecimal value, ControlLimit limit, List<SpcDataPointVo> allPoints, int currentIndex,
                            Function<SpcDataPointVo, BigDecimal> extractor, String ruleSuffix) {
        if (value == null || limit == null || limit.cl == null || limit.ucl == null || limit.lcl == null) {
            return null;
        }
        List<String> rules = new ArrayList<>();
        // RULE1: 超限
        if (value.compareTo(limit.ucl) > 0 || value.compareTo(limit.lcl) < 0) {
            rules.add(OOC_RULE_1 + ruleSuffix);
        }
        // RULE2/3/4 基于当前点在序列中的真实下标（由调用方遍历传入），
        // 不能用值相等匹配定位——重复测量值时 getValue() 会命中首个同值点导致规则窗口错位
        int idx = currentIndex;
        if (idx >= 0) {
            // RULE2: 连续9点(含当前)在CL同侧
            if (idx >= 8 && checkSameSide(allPoints, idx, 9, limit.cl, extractor)) {
                rules.add(OOC_RULE_2 + ruleSuffix);
            }
            // RULE3: 连续6点(含当前)严格递增或递减
            if (idx >= 5 && checkTrend(allPoints, idx, 6, extractor)) {
                rules.add(OOC_RULE_3 + ruleSuffix);
            }
            // RULE4: 连续14点(含当前)交替上下
            if (idx >= 13 && checkAlternating(allPoints, idx, 14, extractor)) {
                rules.add(OOC_RULE_4 + ruleSuffix);
            }
        }
        return rules.isEmpty() ? null : String.join(",", rules);
    }

    /** 检查从 idx 起向前(含idx)共 count 个点是否全在 CL 同侧(序列中存在空值则不判定) */
    private boolean checkSameSide(List<SpcDataPointVo> points, int idx, int count, BigDecimal cl,
                                  Function<SpcDataPointVo, BigDecimal> extractor) {
        int positive = 0, negative = 0;
        for (int i = idx - count + 1; i <= idx; i++) {
            BigDecimal v = extractor.apply(points.get(i));
            if (v == null) return false;
            int cmp = v.compareTo(cl);
            if (cmp > 0) positive++;
            else if (cmp < 0) negative++;
        }
        return positive == count || negative == count;
    }

    /** 检查从 idx 起向前(含idx)共 count 个点是否严格递增或递减(序列中存在空值则不判定) */
    private boolean checkTrend(List<SpcDataPointVo> points, int idx, int count,
                               Function<SpcDataPointVo, BigDecimal> extractor) {
        boolean increasing = true, decreasing = true;
        for (int i = idx - count + 1; i < idx; i++) {
            BigDecimal prev = extractor.apply(points.get(i));
            BigDecimal curr = extractor.apply(points.get(i + 1));
            if (prev == null || curr == null) return false;
            if (curr.compareTo(prev) <= 0) increasing = false;
            if (curr.compareTo(prev) >= 0) decreasing = false;
        }
        return increasing || decreasing;
    }

    /** 检查从 idx 起向前(含idx)共 count 个点是否交替上下(zig-zag, 相邻点差值符号交替) */
    private boolean checkAlternating(List<SpcDataPointVo> points, int idx, int count,
                                     Function<SpcDataPointVo, BigDecimal> extractor) {
        Integer prevSign = null;
        for (int i = idx - count + 1; i < idx; i++) {
            BigDecimal prev = extractor.apply(points.get(i));
            BigDecimal curr = extractor.apply(points.get(i + 1));
            if (prev == null || curr == null) return false;
            int cmp = curr.compareTo(prev);
            if (cmp == 0) return false;
            int sign = cmp > 0 ? 1 : -1;
            if (prevSign != null && sign == prevSign) return false;
            prevSign = sign;
        }
        return true;
    }

    // ==================== 私有辅助方法 ====================

    private List<SpcDataPointVo> listPointsForOoc(SpcDataPointMapper mapper, Long chartId) {
        return mapper.selectVoList(Wrappers.<SpcDataPoint>lambdaQuery()
            .eq(SpcDataPoint::getChartId, chartId)
            .orderByAsc(SpcDataPoint::getSampleGroup));
    }

    /** 列表均值 */
    private BigDecimal mean(List<GroupStat> groups, Function<GroupStat, BigDecimal> picker) {
        BigDecimal sum = BigDecimal.ZERO;
        for (GroupStat g : groups) {
            sum = sum.add(picker.apply(g));
        }
        return sum.divide(BigDecimal.valueOf(groups.size()), SCALE, RoundingMode.HALF_UP);
    }

    /** 次级图序列均值(R̄/S̄/MR̄): 只统计非空的离散度值(X_MR 首点无移动极差) */
    private BigDecimal meanSub(List<GroupStat> groups) {
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (GroupStat g : groups) {
            if (g.rangeOrStd != null) {
                sum = sum.add(g.rangeOrStd);
                count++;
            }
        }
        if (count == 0) {
            return BigDecimal.ZERO;
        }
        return sum.divide(BigDecimal.valueOf(count), SCALE, RoundingMode.HALF_UP);
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

    // ==================== OOC 安灯联动 ====================

    private static final String SPC_ANDON_THROTTLE_PREFIX = "spc:andon:throttle:";
    private static final Duration SPC_ANDON_THROTTLE_TTL = Duration.ofMinutes(10);

    /**
     * OOC 命中 RULE1 时触发 QUALITY 安灯，10 分钟限频防风暴。
     * 安灯失败仅告警日志，不影响 SPC 分析主流程。
     */
    private void triggerAndonForOoc(SpcControlChart chart, SpcDataPointVo dataPoint, String oocRules) {
        try {
            String throttleKey = SPC_ANDON_THROTTLE_PREFIX + chart.getChartId();
            if (!RedisUtils.setObjectIfAbsent(throttleKey, "1", SPC_ANDON_THROTTLE_TTL)) {
                log.info("[SPC] 控制图 {} OOC 安灯限频中，跳过", chart.getChartId());
                return;
            }

            String reason = String.format("SPC OOC 告警: 控制图[%s] 规则[%s] 数值[%s] CL[%s] UCL[%s] LCL[%s]",
                chart.getChartName(),
                oocRules,
                dataPoint.getValue(),
                chart.getCl() == null ? "N/A" : chart.getCl(),
                chart.getUcl() == null ? "N/A" : chart.getUcl(),
                chart.getLcl() == null ? "N/A" : chart.getLcl());

            // SPC 触发的安灯无具体工单/流转卡上下文，mesWoId/cardId/stepRecordId 传 null
            proAndonService.callAndon(null, null, null, IProAndonService.TYPE_QUALITY, reason);
            log.info("[SPC] 控制图 {} OOC 已触发 QUALITY 安灯", chart.getChartId());
        } catch (Exception e) {
            log.warn("[SPC] 控制图 {} OOC 触发安灯失败: {}", chart.getChartId(), e.getMessage(), e);
            // 安灯失败不影响 SPC 分析主流程
        }
    }
}
