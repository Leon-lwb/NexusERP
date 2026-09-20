package com.nexus.mes.spc.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.mes.dc.domain.vo.DcDataRecordVo;
import com.nexus.mes.dc.service.IDcDataRecordService;
import com.nexus.mes.pro.service.IProAndonService;
import com.nexus.mes.spc.domain.SpcControlChart;
import com.nexus.mes.spc.domain.SpcDataPoint;
import com.nexus.mes.spc.domain.vo.SpcControlChartVo;
import com.nexus.mes.spc.domain.vo.SpcDataPointVo;
import com.nexus.mes.spc.mapper.SpcControlChartMapper;
import com.nexus.mes.spc.mapper.SpcDataPointMapper;
import com.nexus.mes.spc.service.ISpcControlChartService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MES SPC控制图 Service 单元测试。
 *
 * <p>覆盖（修复前必失败的断言已标注）：
 * <ol>
 *   <li>MES-SPC-01: R 图 D3/D4、S 图 B3/B4、X-MR(E2/D4) 控制限数值锚点 + sub_value 序列独立 OOC；</li>
 *   <li>MES-SPC-02: 过程能力样本量不足时 capabilityInsufficient 且不回写 cp/cpk；样本充足时
 *       Cp/Cpk(组内σ) 与 Pp/Ppk(整体s) 数值锚点；</li>
 *   <li>MES-SPC-03: 重复 analyze 复用既有 spc_data_point 行(data_id 稳定)，不再全量删除重建；</li>
 *   <li>MES-DC-04: analyze 只取"最近 N 组"样本(查询带 limit)。</li>
 * </ol>
 *
 * @author Nexus
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class SpcControlChartServiceImplTest {

    @Mock
    private SpcControlChartMapper baseMapper;
    @Mock
    private SpcDataPointMapper dataPointMapper;
    @Mock
    private IDcDataRecordService dcDataRecordService;
    @Mock
    private IProAndonService proAndonService;

    @InjectMocks
    private SpcControlChartServiceImpl spcControlChartService;

    /** 真实列缓存: LambdaQueryWrapper/LambdaUpdateWrapper 需要实体 TableInfo。 */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SpcDataPoint.class);
        TableInfoHelper.initTableInfo(assistant, SpcControlChart.class);
    }

    // ==================== 异常边界 ====================

    @Test
    void analyzeThrowsWhenChartNotFound() {
        when(baseMapper.selectById(999L)).thenReturn(null);
        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> spcControlChartService.analyze(999L));
        assertTrue(ex.getMessage().contains("控制图不存在"));
    }

    @Test
    void analyzeThrowsWhenNoPointId() {
        SpcControlChart chart = buildChart(1L, ISpcControlChartService.TYPE_X_BAR_R, 2, null);
        when(baseMapper.selectById(1L)).thenReturn(chart);
        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> spcControlChartService.analyze(1L));
        assertTrue(ex.getMessage().contains("未关联采集点"));
    }

    @Test
    void analyzeThrowsWhenNoData() {
        SpcControlChart chart = buildChart(1L, ISpcControlChartService.TYPE_X_BAR_R, 2, 10L);
        when(baseMapper.selectById(1L)).thenReturn(chart);
        when(dcDataRecordService.listByPointId(eq(10L), isNull(), isNull(), anyInt())).thenReturn(new ArrayList<>());
        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> spcControlChartService.analyze(1L));
        assertTrue(ex.getMessage().contains("无可用数据"));
    }

    @Test
    void analyzeThrowsWhenDataInsufficientForGrouping() {
        // X_BAR_R sampleSize=2 但只有 1 条记录, 分组后 0 组(尾部丢弃)
        SpcControlChart chart = buildChart(1L, ISpcControlChartService.TYPE_X_BAR_R, 2, 10L);
        when(baseMapper.selectById(1L)).thenReturn(chart);
        when(dcDataRecordService.listByPointId(eq(10L), isNull(), isNull(), anyInt())).thenReturn(List.of(record(10)));
        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> spcControlChartService.analyze(1L));
        assertTrue(ex.getMessage().contains("数据不足"));
    }

    @Test
    void analyzeThrowsForUnsupportedChartType() {
        SpcControlChart chart = buildChart(1L, "UNKNOWN_TYPE", 2, 10L);
        when(baseMapper.selectById(1L)).thenReturn(chart);
        when(dcDataRecordService.listByPointId(eq(10L), isNull(), isNull(), anyInt()))
            .thenReturn(List.of(record(10), record(12)));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> spcControlChartService.analyze(1L));
        assertTrue(ex.getMessage().contains("不支持的图表类型"));
    }

    // ==================== X_BAR_R 控制限(含 R 图 D3/D4) ====================

    @Test
    void analyzeCalculatesXBarRControlLimits() {
        // 4 条记录 [10,12,14,16], sampleSize=2 -> 2 组
        // 组1: 均值=11, 极差=2; 组2: 均值=15, 极差=2
        // x̄̄=13, R̄=2, A2(n=2)=1.880, delta=3.76 -> UCL=16.76, CL=13, LCL=9.24
        // R 图: D4(2)=3.267 -> RUCL=6.534, RCL=2, RLCL=0
        SpcControlChart chart = buildChart(1L, ISpcControlChartService.TYPE_X_BAR_R, 2, 10L);
        when(baseMapper.selectById(1L)).thenReturn(chart);
        when(dcDataRecordService.listByPointId(eq(10L), isNull(), isNull(), anyInt()))
            .thenReturn(List.of(record(10), record(12), record(14), record(16)));
        when(dataPointMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(dataPointMapper.selectVoList(any())).thenReturn(points(
            point(1L, 1, "11", "2"), point(2L, 2, "15", "2")));
        when(baseMapper.selectVoById(1L)).thenReturn(new SpcControlChartVo());

        Map<String, Object> result = spcControlChartService.analyze(1L);

        assertEquals(new BigDecimal("16.7600"), result.get("ucl"));
        assertEquals(new BigDecimal("13.0000"), result.get("cl"));
        assertEquals(new BigDecimal("9.2400"), result.get("lcl"));
        assertEquals(new BigDecimal("6.5340"), result.get("rUcl"));
        assertEquals(new BigDecimal("2.0000"), result.get("rCl"));
        assertEquals(new BigDecimal("0.0000"), result.get("rLcl"));
        assertEquals(0, result.get("oocCount"));
        // 新数据点被插入(2 组), 且写入 sub_value=组极差
        ArgumentCaptor<SpcDataPoint> inserted = ArgumentCaptor.forClass(SpcDataPoint.class);
        verify(dataPointMapper, times(2)).insert(inserted.capture());
        assertDecimal("2", inserted.getAllValues().get(0).getSubValue());
        // 不再全量删除重建
        verify(dataPointMapper, never()).delete(any());
    }

    /**
     * MES-SPC-01 数值锚点: n=5, R̄=2.0 → UCL_R = D4(5)·R̄ = 2.114×2 = 4.228
     */
    @Test
    void analyzeCalculatesRChartLimitsAnchorN5() {
        // 5 组 × 5 样本, 每组 [10,11,12,10,11] -> 组均值 10.8, 组极差 2.0
        // x̄̄=10.8, R̄=2.0, A2(5)=0.577 -> UCL=11.954, LCL=9.646
        // R 图: D4(5)=2.114 -> RUCL=4.228, RCL=2.0, D3(5)=0 -> RLCL=0
        SpcControlChart chart = buildChart(1L, ISpcControlChartService.TYPE_X_BAR_R, 5, 10L);
        when(baseMapper.selectById(1L)).thenReturn(chart);
        List<DcDataRecordVo> records = new ArrayList<>();
        for (int g = 0; g < 5; g++) {
            records.add(record(10));
            records.add(record(11));
            records.add(record(12));
            records.add(record(10));
            records.add(record(11));
        }
        when(dcDataRecordService.listByPointId(eq(10L), isNull(), isNull(), eq(125))).thenReturn(records);
        when(dataPointMapper.selectList(any())).thenReturn(new ArrayList<>());
        List<SpcDataPointVo> fixture = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            fixture.add(point((long) (i + 1), i + 1, "10.8", "2"));
        }
        when(dataPointMapper.selectVoList(any())).thenReturn(fixture);
        when(baseMapper.selectVoById(1L)).thenReturn(new SpcControlChartVo());

        Map<String, Object> result = spcControlChartService.analyze(1L);

        assertEquals(new BigDecimal("11.9540"), result.get("ucl"));
        assertEquals(new BigDecimal("10.8000"), result.get("cl"));
        assertEquals(new BigDecimal("9.6460"), result.get("lcl"));
        assertEquals(new BigDecimal("4.2280"), result.get("rUcl"), "UCL_R 应 = D4(5)×R̄ = 2.114×2");
        assertEquals(new BigDecimal("2.0000"), result.get("rCl"));
        assertEquals(new BigDecimal("0.0000"), result.get("rLcl"), "n=5 时 D3=0");
        assertEquals(0, result.get("oocCount"));
        // 样本量不足(5 组 < 25): 不回写过程能力
        assertEquals(Boolean.TRUE, result.get("capabilityInsufficient"));
    }

    // ==================== X_BAR_S 控制限(含 S 图 B3/B4) ====================

    @Test
    void analyzeCalculatesXBarSControlLimits() {
        // 4 条记录 [10,12,14,16], sampleSize=2 -> 2 组
        // 组1: 均值=11, S=√2=1.4142; 组2: 均值=15, S=1.4142
        // x̄̄=13, S̄=1.4142, A3(2)=2.659 -> delta=3.7604 -> UCL=16.7604, LCL=9.2396
        // S 图: B4(2)=3.267 -> SUCL=4.6202, SCL=1.4142, B3(2)=0 -> SLCL=0
        SpcControlChart chart = buildChart(1L, ISpcControlChartService.TYPE_X_BAR_S, 2, 10L);
        when(baseMapper.selectById(1L)).thenReturn(chart);
        when(dcDataRecordService.listByPointId(eq(10L), isNull(), isNull(), anyInt()))
            .thenReturn(List.of(record(10), record(12), record(14), record(16)));
        when(dataPointMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(dataPointMapper.selectVoList(any())).thenReturn(points(
            point(1L, 1, "11", "1.4142"), point(2L, 2, "15", "1.4142")));
        when(baseMapper.selectVoById(1L)).thenReturn(new SpcControlChartVo());

        Map<String, Object> result = spcControlChartService.analyze(1L);

        assertEquals(new BigDecimal("13.0000"), result.get("cl"));
        assertEquals(new BigDecimal("16.7604"), result.get("ucl"));
        assertEquals(new BigDecimal("9.2396"), result.get("lcl"));
        assertEquals(new BigDecimal("4.6202"), result.get("rUcl"), "UCL_S 应 = B4(2)×S̄ = 3.267×1.4142");
        assertEquals(new BigDecimal("1.4142"), result.get("rCl"));
        assertEquals(new BigDecimal("0.0000"), result.get("rLcl"));
    }

    // ==================== X-MR 单值-移动极差 ====================

    @Test
    void analyzeCalculatesXMrControlLimits() {
        // 单值 [10,12,15,11] -> 移动极差 [-,2,3,4] -> MR̄=3.0, x̄=12.0
        // X 图: E2=2.660 -> delta=7.98 -> UCL=19.98, LCL=4.02
        // MR 图: D4(2)=3.267 -> UCL=9.801, RCL=3.0, D3(2)=0 -> LCL=0
        SpcControlChart chart = buildChart(1L, ISpcControlChartService.TYPE_X_MR, 5, 10L);
        when(baseMapper.selectById(1L)).thenReturn(chart);
        when(dcDataRecordService.listByPointId(eq(10L), isNull(), isNull(), eq(50)))
            .thenReturn(List.of(record(10), record(12), record(15), record(11)));
        when(dataPointMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(dataPointMapper.selectVoList(any())).thenReturn(points(
            point(1L, 1, "10", null), point(2L, 2, "12", "2"),
            point(3L, 3, "15", "3"), point(4L, 4, "11", "4")));
        when(baseMapper.selectVoById(1L)).thenReturn(new SpcControlChartVo());

        Map<String, Object> result = spcControlChartService.analyze(1L);

        assertEquals(new BigDecimal("19.9800"), result.get("ucl"));
        assertEquals(new BigDecimal("12.0000"), result.get("cl"));
        assertEquals(new BigDecimal("4.0200"), result.get("lcl"));
        assertEquals(new BigDecimal("9.8010"), result.get("rUcl"));
        assertEquals(new BigDecimal("3.0000"), result.get("rCl"));
        assertEquals(new BigDecimal("0.0000"), result.get("rLcl"));
        // 首点无移动极差
        ArgumentCaptor<SpcDataPoint> inserted = ArgumentCaptor.forClass(SpcDataPoint.class);
        verify(dataPointMapper, times(4)).insert(inserted.capture());
        assertNull(inserted.getAllValues().get(0).getSubValue());
        assertDecimal("2", inserted.getAllValues().get(1).getSubValue());
    }

    /**
     * MES-SPC-01: sub_value(组内离散度)序列独立跑 OOC —— 组极差连续 6 点递增应命中 RULE3-SUB。
     */
    @Test
    void analyzeMarksSubValueOocOnDispersionTrend() {
        // 6 组 × 2 样本: (10,11)(10,12)(10,13)(10,14)(10,15)(10,16)
        // 组极差 = 1,2,3,4,5,6 (连续递增) -> RULE3-SUB
        SpcControlChart chart = buildChart(1L, ISpcControlChartService.TYPE_X_BAR_R, 2, 10L);
        when(baseMapper.selectById(1L)).thenReturn(chart);
        List<DcDataRecordVo> records = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            records.add(record(10));
            records.add(record(10 + i));
        }
        when(dcDataRecordService.listByPointId(eq(10L), isNull(), isNull(), eq(50))).thenReturn(records);
        when(dataPointMapper.selectList(any())).thenReturn(new ArrayList<>());
        List<SpcDataPointVo> fixture = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            fixture.add(point((long) i, i, String.valueOf(10 + i / 2.0), String.valueOf(i)));
        }
        when(dataPointMapper.selectVoList(any())).thenReturn(fixture);
        when(baseMapper.selectVoById(1L)).thenReturn(new SpcControlChartVo());

        Map<String, Object> result = spcControlChartService.analyze(1L);

        assertEquals(new BigDecimal("11.4345"), result.get("rUcl"), "UCL_R = D4(2)×R̄ = 3.267×3.5");
        assertEquals(new BigDecimal("3.5000"), result.get("rCl"));
        @SuppressWarnings("unchecked")
        List<SpcDataPointVo> out = (List<SpcDataPointVo>) result.get("points");
        assertEquals("1", out.get(5).getIsOoc(), "组内离散度失控点应被标记 OOC");
        assertTrue(out.get(5).getOocRule().contains(ISpcControlChartService.OOC_RULE_3 + "-SUB"),
            "离散度序列应独立命中 RULE3-SUB, 实际: " + out.get(5).getOocRule());
        assertTrue((Integer) result.get("oocCount") >= 1);
    }

    // ==================== C 图控制限 + DC-04 limit ====================

    @Test
    void analyzeCalculatesCChartLimitsAndLimitsFetch() {
        // 3 条记录 [5,10,15], C图每条一组, statValue=缺陷数原值
        // c̄=10, √10=3.1623, UCL=19.4868, LCL=0.5132
        SpcControlChart chart = buildChart(1L, ISpcControlChartService.TYPE_C, 5, 10L);
        when(baseMapper.selectById(1L)).thenReturn(chart);
        when(dcDataRecordService.listByPointId(eq(10L), isNull(), isNull(), eq(25)))
            .thenReturn(List.of(record(5), record(10), record(15)));
        when(dataPointMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(dataPointMapper.selectVoList(any())).thenReturn(points(
            point(1L, 1, "5", null), point(2L, 2, "10", null), point(3L, 3, "15", null)));
        when(baseMapper.selectVoById(1L)).thenReturn(new SpcControlChartVo());

        Map<String, Object> result = spcControlChartService.analyze(1L);

        BigDecimal ucl = (BigDecimal) result.get("ucl");
        BigDecimal lcl = (BigDecimal) result.get("lcl");
        assertEquals(new BigDecimal("10.0000"), result.get("cl"));
        assertTrue(ucl.compareTo(new BigDecimal("19.48")) > 0 && ucl.compareTo(new BigDecimal("19.49")) < 0,
            "C 图 UCL 应≈19.4868, 实际: " + ucl);
        assertTrue(lcl.compareTo(new BigDecimal("0.51")) > 0 && lcl.compareTo(new BigDecimal("0.52")) < 0,
            "C 图 LCL 应≈0.5132, 实际: " + lcl);
        assertNull(result.get("rUcl"), "计数型图无次级图控制限");
        assertEquals(0, result.get("oocCount"));
        // MES-DC-04: 计数型图每组一条记录 -> limit = maxGroups(25)
        verify(dcDataRecordService).listByPointId(10L, null, null, 25);
    }

    /**
     * MES-DC-04: X_BAR_R(sampleSize=2) 只取最近 25 组 = 50 条样本。
     */
    @Test
    void analyzeFetchesOnlyRecentGroupsWithLimit() {
        SpcControlChart chart = buildChart(1L, ISpcControlChartService.TYPE_X_BAR_R, 2, 10L);
        when(baseMapper.selectById(1L)).thenReturn(chart);
        when(dcDataRecordService.listByPointId(eq(10L), isNull(), isNull(), eq(50)))
            .thenReturn(List.of(record(10), record(12)));
        when(dataPointMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(dataPointMapper.selectVoList(any())).thenReturn(points(point(1L, 1, "11", "2")));
        when(baseMapper.selectVoById(1L)).thenReturn(new SpcControlChartVo());

        spcControlChartService.analyze(1L);

        verify(dcDataRecordService).listByPointId(10L, null, null, 50);
    }

    // ==================== SPC-02 过程能力 ====================

    /**
     * SPC-02: 子组数不足(<25)时不回写 cp/cpk 并在返回体标记 capabilityInsufficient。
     */
    @Test
    void analyzeSkipsCapabilityWhenSampleInsufficient() {
        SpcControlChart chart = buildChart(1L, ISpcControlChartService.TYPE_X_BAR_R, 2, 10L);
        chart.setUsl(new BigDecimal("11.5"));
        chart.setLsl(new BigDecimal("10.5"));
        when(baseMapper.selectById(1L)).thenReturn(chart);
        when(dcDataRecordService.listByPointId(eq(10L), isNull(), isNull(), anyInt()))
            .thenReturn(List.of(record(10), record(12), record(14), record(16)));
        when(dataPointMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(dataPointMapper.selectVoList(any())).thenReturn(points(
            point(1L, 1, "11", "2"), point(2L, 2, "15", "2")));
        SpcControlChartVo vo = new SpcControlChartVo();
        when(baseMapper.selectVoById(1L)).thenReturn(vo);

        Map<String, Object> result = spcControlChartService.analyze(1L);

        assertEquals(Boolean.TRUE, result.get("capabilityInsufficient"));
        assertTrue(String.valueOf(result.get("capabilityInsufficientReason")).contains("样本量不足"),
            "原因文案应说明样本量不足, 实际: " + result.get("capabilityInsufficientReason"));
        assertNull(result.get("cp"));
        assertNull(result.get("cpk"));
        assertNull(chart.getCpk(), "样本不足时不得回写 cpk(避免小样本算出 A+ 等级)");
        assertEquals(Boolean.TRUE, vo.getCapabilityInsufficient());
    }

    /**
     * SPC-02 数值锚点: 25 组 × 2 样本(每组 10.0/10.1), USL=10.55, LSL=9.55
     * <pre>
     *   x̄̄=10.05, R̄=0.10, d2(2)=1.128 -> σ组内=0.0886525
     *   Cp  = 1.00/(6σ) = 1.8800 ; Cpk = 0.50/(3σ) = 1.8800 (A+)
     *   s   = √(50×0.05²/49) = 0.050508 -> Pp = Ppk = 1.00/(6s) = 3.2998
     * </pre>
     */
    @Test
    void analyzeWritesCapabilityWhenSampleSufficient() {
        SpcControlChart chart = buildChart(1L, ISpcControlChartService.TYPE_X_BAR_R, 2, 10L);
        chart.setUsl(new BigDecimal("10.55"));
        chart.setLsl(new BigDecimal("9.55"));
        when(baseMapper.selectById(1L)).thenReturn(chart);
        List<DcDataRecordVo> records = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            records.add(record(10.0));
            records.add(record(10.1));
        }
        when(dcDataRecordService.listByPointId(eq(10L), isNull(), isNull(), eq(50))).thenReturn(records);
        when(dataPointMapper.selectList(any())).thenReturn(new ArrayList<>());
        List<SpcDataPointVo> fixture = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            fixture.add(point((long) (i + 1), i + 1, "10.05", "0.1"));
        }
        when(dataPointMapper.selectVoList(any())).thenReturn(fixture);
        when(baseMapper.selectVoById(1L)).thenReturn(new SpcControlChartVo());

        Map<String, Object> result = spcControlChartService.analyze(1L);

        assertEquals(Boolean.FALSE, result.get("capabilityInsufficient"));
        assertEquals(new BigDecimal("1.8800"), result.get("cp"));
        assertEquals(new BigDecimal("1.8800"), result.get("cpk"));
        assertEquals("A+", result.get("cpkGrade"));
        assertEquals(new BigDecimal("3.2998"), result.get("pp"));
        assertEquals(new BigDecimal("3.2998"), result.get("ppk"));
        assertEquals(new BigDecimal("0.3267"), result.get("rUcl"), "UCL_R = 3.267×0.1");
    }

    // ==================== SPC-03 数据点 upsert ====================

    /**
     * SPC-03: 重复 analyze 复用既有数据点行(updateById), 不新增行、data_id 稳定。
     */
    @Test
    void analyzeReusesExistingDataPointRowsKeepingDataId() {
        SpcControlChart chart = buildChart(1L, ISpcControlChartService.TYPE_X_BAR_R, 2, 10L);
        when(baseMapper.selectById(1L)).thenReturn(chart);
        when(dcDataRecordService.listByPointId(eq(10L), isNull(), isNull(), anyInt()))
            .thenReturn(List.of(record(10), record(12), record(14), record(16)));
        // 库中已有第 1、2 组(上次 analyze 留下的行)
        when(dataPointMapper.selectList(any())).thenReturn(List.of(
            existingPoint(101L, 1), existingPoint(102L, 2)));
        when(dataPointMapper.selectVoList(any())).thenReturn(points(
            point(101L, 1, "11", "2"), point(102L, 2, "15", "2")));
        when(baseMapper.selectVoById(1L)).thenReturn(new SpcControlChartVo());

        Map<String, Object> result = spcControlChartService.analyze(1L);

        ArgumentCaptor<SpcDataPoint> updated = ArgumentCaptor.forClass(SpcDataPoint.class);
        verify(dataPointMapper, times(2)).updateById(updated.capture());
        assertEquals(101L, updated.getAllValues().get(0).getDataId(), "data_id 必须保持稳定");
        assertEquals(102L, updated.getAllValues().get(1).getDataId());
        assertDecimal("2", updated.getAllValues().get(1).getSubValue());
        verify(dataPointMapper, never()).insert(any(SpcDataPoint.class));
        // 组数未变: 范围外删除命中 0 行, 不产生僵尸行
        verify(dataPointMapper).physicalDeleteOutOfRange(1L, 2);
        assertEquals(2, ((List<?>) result.get("points")).size());
    }

    /**
     * SPC-03: 分组数变少时只清理本次范围外的旧组(sample_group &gt; N)，范围内的行保持 data_id。
     */
    @Test
    void analyzePhysicallyDeletesOutOfRangeGroups() {
        SpcControlChart chart = buildChart(1L, ISpcControlChartService.TYPE_X_BAR_R, 2, 10L);
        when(baseMapper.selectById(1L)).thenReturn(chart);
        when(dcDataRecordService.listByPointId(eq(10L), isNull(), isNull(), anyInt()))
            .thenReturn(List.of(record(10), record(12), record(14), record(16)));
        when(dataPointMapper.selectList(any())).thenReturn(List.of(
            existingPoint(101L, 1), existingPoint(102L, 2), existingPoint(103L, 3)));
        when(dataPointMapper.selectVoList(any())).thenReturn(points(
            point(101L, 1, "11", "2"), point(102L, 2, "15", "2")));
        when(baseMapper.selectVoById(1L)).thenReturn(new SpcControlChartVo());

        spcControlChartService.analyze(1L);

        // 本次只有 2 组 -> 物理删除 sample_group > 2 的第 3 组(del_flag='2' 僵尸行一并清除)
        verify(dataPointMapper).physicalDeleteOutOfRange(1L, 2);
        verify(dataPointMapper, times(2)).updateById(any(SpcDataPoint.class));
        verify(dataPointMapper, never()).insert(any(SpcDataPoint.class));
    }

    // ==================== fixture 辅助 ====================

    private SpcControlChart buildChart(Long chartId, String chartType, int sampleSize, Long pointId) {
        SpcControlChart chart = new SpcControlChart();
        chart.setChartId(chartId);
        chart.setChartType(chartType);
        chart.setSampleSize(sampleSize);
        chart.setPointId(pointId);
        return chart;
    }

    private DcDataRecordVo record(double numericValue) {
        DcDataRecordVo vo = new DcDataRecordVo();
        vo.setNumericValue(BigDecimal.valueOf(numericValue));
        vo.setCollectTime(new Date());
        return vo;
    }

    private SpcDataPoint existingPoint(Long dataId, int sampleGroup) {
        SpcDataPoint p = new SpcDataPoint();
        p.setDataId(dataId);
        p.setChartId(1L);
        p.setSampleGroup(sampleGroup);
        return p;
    }

    private SpcDataPointVo point(Long dataId, int sampleGroup, String value, String subValue) {
        SpcDataPointVo pv = new SpcDataPointVo();
        pv.setDataId(dataId);
        pv.setChartId(1L);
        pv.setSampleGroup(sampleGroup);
        pv.setValue(value == null ? null : new BigDecimal(value));
        pv.setSubValue(subValue == null ? null : new BigDecimal(subValue));
        pv.setCollectTime(new Date());
        return pv;
    }

    private List<SpcDataPointVo> points(SpcDataPointVo... values) {
        return new ArrayList<>(List.of(values));
    }

    /** BigDecimal 按数值比较(忽略 scale: 组极差为 subtract 结果, scale 与字面量不一致) */
    private void assertDecimal(String expected, BigDecimal actual) {
        assertNotNull(actual, "期望 " + expected + " 但实际为 null");
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "期望 " + expected + " 实际 " + actual);
    }
}
