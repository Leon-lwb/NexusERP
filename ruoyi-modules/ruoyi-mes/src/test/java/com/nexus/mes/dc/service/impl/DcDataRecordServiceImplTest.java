package com.nexus.mes.dc.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.mes.dc.domain.DcCollectionPoint;
import com.nexus.mes.dc.domain.DcDataRecord;
import com.nexus.mes.dc.domain.bo.DcDataRecordBo;
import com.nexus.mes.dc.domain.vo.DcDataRecordVo;
import com.nexus.mes.dc.mapper.DcCollectionPointMapper;
import com.nexus.mes.dc.mapper.DcDataRecordMapper;
import com.nexus.mes.dc.service.IDcDataRecordService;
import jakarta.validation.constraints.NotNull;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MES 采集数据记录 Service 单元测试。
 *
 * <p>覆盖（修复前必失败）：
 * <ol>
 *   <li>MES-DC-03: NUMERIC 采集点缺 numericValue / 负值被拒（原先静默入库并被 SPC 的
 *       {@code isNotNull(numeric_value)} 丢弃）；TEXT/ENUM 采集点缺 textValue 被拒；
 *       超出 decimal(14,4) 的小数位按 HALF_UP 收敛；{@code collectTime} 为必填；</li>
 *   <li>MES-DC-04: {@code listByPointId(pointId, start, end, limit)} 带 limit/时间窗查询，
 *       取最近 N 条后反转为升序返回。</li>
 * </ol>
 *
 * @author Nexus
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class DcDataRecordServiceImplTest {

    @Mock
    private DcDataRecordMapper baseMapper;
    @Mock
    private DcCollectionPointMapper pointMapper;

    @InjectMocks
    private DcDataRecordServiceImpl dcDataRecordService;

    /** 真实列缓存: LambdaQueryWrapper.getSqlSegment() 需要实体 TableInfo。 */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), DcDataRecord.class);
    }

    // ==================== MES-DC-03 录入校验 ====================

    @Test
    void insertThrowsWhenPointMissing() {
        when(pointMapper.selectById(1L)).thenReturn(null);
        DcDataRecordBo bo = bo(1L, null, null, new Date());
        ServiceException ex = assertThrows(ServiceException.class, () -> dcDataRecordService.insertByBo(bo));
        assertTrue(ex.getMessage().contains("采集点不存在"));
    }

    /**
     * NUMERIC 采集点只填 textValue 时必须报错，而不是静默入库后被 SPC 丢弃。
     */
    @Test
    void insertNumericPointWithoutNumericValueRejected() {
        when(pointMapper.selectById(1L)).thenReturn(point(1L, "P1", "NUMERIC"));
        DcDataRecordBo bo = bo(1L, null, "12.5", new Date());
        ServiceException ex = assertThrows(ServiceException.class, () -> dcDataRecordService.insertByBo(bo));
        assertTrue(ex.getMessage().contains("必须填写数值"), "实际: " + ex.getMessage());
        verify(baseMapper, never()).insert(any(DcDataRecord.class));
    }

    @Test
    void insertNumericPointWithNegativeValueRejected() {
        when(pointMapper.selectById(1L)).thenReturn(point(1L, "P1", "NUMERIC"));
        DcDataRecordBo bo = bo(1L, new BigDecimal("-1.5"), null, new Date());
        ServiceException ex = assertThrows(ServiceException.class, () -> dcDataRecordService.insertByBo(bo));
        assertTrue(ex.getMessage().contains("不能为负数"), "实际: " + ex.getMessage());
        verify(baseMapper, never()).insert(any(DcDataRecord.class));
    }

    @Test
    void insertTextPointWithoutTextValueRejected() {
        when(pointMapper.selectById(1L)).thenReturn(point(1L, "P2", "TEXT"));
        DcDataRecordBo bo = bo(1L, new BigDecimal("1"), null, new Date());
        ServiceException ex = assertThrows(ServiceException.class, () -> dcDataRecordService.insertByBo(bo));
        assertTrue(ex.getMessage().contains("必须填写文本值"), "实际: " + ex.getMessage());
    }

    @Test
    void insertEnumPointWithBlankTextValueRejected() {
        when(pointMapper.selectById(1L)).thenReturn(point(1L, "P3", "ENUM"));
        DcDataRecordBo bo = bo(1L, null, "   ", new Date());
        ServiceException ex = assertThrows(ServiceException.class, () -> dcDataRecordService.insertByBo(bo));
        assertTrue(ex.getMessage().contains("必须填写文本值"), "实际: " + ex.getMessage());
    }

    /**
     * 合法 NUMERIC 录入: 小数位超过 decimal(14,4) 时按 HALF_UP 收敛到 4 位。
     */
    @Test
    void insertNumericPointRoundsScaleToFour() {
        when(pointMapper.selectById(1L)).thenReturn(point(1L, "P1", "NUMERIC"));
        when(baseMapper.selectVoById(any())).thenReturn(new DcDataRecordVo());
        DcDataRecordBo bo = bo(1L, new BigDecimal("1.234567"), null, new Date());
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);

            dcDataRecordService.insertByBo(bo);
        }
        ArgumentCaptor<DcDataRecord> inserted = ArgumentCaptor.forClass(DcDataRecord.class);
        verify(baseMapper).insert(inserted.capture());
        assertEquals(new BigDecimal("1.2346"), inserted.getValue().getNumericValue());
        assertEquals("P1", inserted.getValue().getPointCode());
        assertEquals(IDcDataRecordService.SOURCE_MANUAL, inserted.getValue().getSource());
        assertNotNull(inserted.getValue().getCollectTime());
    }

    /**
     * MES-DC-03: DcDataRecordBo.collectTime 为必填(@NotNull)。
     */
    @Test
    void collectTimeIsMandatoryOnBo() throws Exception {
        Field field = DcDataRecordBo.class.getDeclaredField("collectTime");
        assertNotNull(field.getAnnotation(NotNull.class), "collectTime 应标注 @NotNull");
    }

    // ==================== MES-DC-04 查询上限 ====================

    @Test
    void listByPointIdWithLimitQueriesRecentAndReturnsAscending() {
        List<DcDataRecordVo> desc = new ArrayList<>();
        desc.add(record(new Date(3_000L)));
        desc.add(record(new Date(2_000L)));
        desc.add(record(new Date(1_000L)));
        when(baseMapper.selectVoList(any())).thenReturn(desc);

        List<DcDataRecordVo> result = dcDataRecordService.listByPointId(10L, null, null, 5);

        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).selectVoList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("LIMIT 5"), "应带 LIMIT 上限: " + sql);
        assertTrue(sql.toUpperCase().contains("ORDER BY"), sql);
        assertTrue(sql.toUpperCase().contains("COLLECT_TIME"), sql);
        // DESC 取最近 N 条后内存反转为升序
        assertEquals(3, result.size());
        assertEquals(1_000L, result.get(0).getCollectTime().getTime());
        assertEquals(3_000L, result.get(2).getCollectTime().getTime());
    }

    @Test
    void listByPointIdWithoutLimitQueriesAscending() {
        when(baseMapper.selectVoList(any())).thenReturn(new ArrayList<>());

        // 旧签名(已 @Deprecated)委托到新签名, 不带 limit
        dcDataRecordService.listByPointId(10L);

        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).selectVoList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertFalse(sql.contains("LIMIT"), "不限条数时不应拼 LIMIT: " + sql);
        assertTrue(sql.toUpperCase().contains("ORDER BY"), sql);
    }

    @Test
    void listByPointIdAppliesTimeWindow() {
        when(baseMapper.selectVoList(any())).thenReturn(new ArrayList<>());

        dcDataRecordService.listByPointId(10L, new Date(1_000L), new Date(2_000L), 20);

        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).selectVoList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains(">="), "应带起始时间下界: " + sql);
        assertTrue(sql.contains("<="), "应带结束时间上界: " + sql);
        assertTrue(sql.toUpperCase().contains("NUMERIC_VALUE"), "SPC 只取有数值的记录: " + sql);
    }

    // ==================== fixture 辅助 ====================

    private DcCollectionPoint point(Long pointId, String pointCode, String dataType) {
        DcCollectionPoint p = new DcCollectionPoint();
        p.setPointId(pointId);
        p.setPointCode(pointCode);
        p.setDataType(dataType);
        return p;
    }

    private DcDataRecordBo bo(Long pointId, BigDecimal numericValue, String textValue, Date collectTime) {
        DcDataRecordBo bo = new DcDataRecordBo();
        bo.setPointId(pointId);
        bo.setNumericValue(numericValue);
        bo.setTextValue(textValue);
        bo.setCollectTime(collectTime);
        return bo;
    }

    private DcDataRecordVo record(Date collectTime) {
        DcDataRecordVo vo = new DcDataRecordVo();
        vo.setNumericValue(BigDecimal.ONE);
        vo.setCollectTime(collectTime);
        return vo;
    }
}
