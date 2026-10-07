package com.nexus.erp.prd;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.domain.PrdWorkReport;
import com.nexus.erp.prd.domain.bo.PrdWorkReportBo;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.prd.mapper.PrdWorkReportMapper;
import com.nexus.erp.prd.service.impl.PrdWorkReportServiceImpl;
import com.nexus.system.strategy.AutoCodeUtil;
import io.github.linpeilie.Converter;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 生产报工单元测试（ERP-PRD-02：不得直落 APPROVED、回写要 CAS、补超报工上限）。
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PrdWorkReportServiceImplTest {

    @Mock
    private PrdWorkReportMapper baseMapper;
    @Mock
    private PrdWorkOrderMapper workOrderMapper;
    @Mock
    private AutoCodeUtil autoCodeUtil;

    @InjectMocks
    private PrdWorkReportServiceImpl workReportService;

    private static MockedStatic<SpringUtil> springUtilMock;

    @BeforeAll
    static void initStatics() {
        springUtilMock = mockStatic(SpringUtil.class);
        Converter converter = new Converter() {
            @Override
            public <S, T> T convert(S source, Class<T> targetType) {
                return BeanUtil.copyProperties(source, targetType);
            }
        };
        lenient().when(SpringUtil.getBean(Converter.class)).thenReturn(converter);

        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, PrdWorkReport.class);
        TableInfoHelper.initTableInfo(assistant, PrdWorkOrder.class);
    }

    @AfterAll
    static void closeStatics() {
        if (springUtilMock != null) {
            springUtilMock.close();
        }
    }

    /** ERP-PRD-02：新增报工带 reportStatus=APPROVED 也必须落 SUBMITTED */
    @Test
    void insertByBo_clientApproved_forcedToSubmitted() {
        PrdWorkReportBo bo = new PrdWorkReportBo();
        bo.setReportStatus("APPROVED");
        bo.setWoId(9L);
        // 编号出生依赖用 mock 隔离，既有强制 SUBMITTED 断言保持不变。
        when(autoCodeUtil.genSerialCode("PRD_REPORT_CODE", null)).thenReturn("BG261007001");
        when(baseMapper.insert(any(PrdWorkReport.class))).thenReturn(1);

        assertEquals(1, workReportService.insertByBo(bo));

        ArgumentCaptor<PrdWorkReport> captor = ArgumentCaptor.forClass(PrdWorkReport.class);
        verify(baseMapper).insert(captor.capture());
        assertEquals("SUBMITTED", captor.getValue().getReportStatus());
    }

    /** ERP-PRD-02：超报工上限（已完工 50 + 本次 60 > 计划 100）→ 拒绝且不回写 */
    @Test
    void updateByBo_overReport_rejected() {
        PrdWorkReport old = new PrdWorkReport();
        old.setReportId(1L);
        old.setWoId(9L);
        old.setReportStatus("SUBMITTED");
        old.setReportedQty(new BigDecimal("60"));
        when(baseMapper.selectById(1L)).thenReturn(old);
        when(baseMapper.selectOne(any())).thenReturn(old);

        PrdWorkOrder wo = new PrdWorkOrder();
        wo.setWoId(9L);
        wo.setPlannedQty(new BigDecimal("100"));
        wo.setCompletedQty(new BigDecimal("50"));
        when(workOrderMapper.selectOne(any())).thenReturn(wo);

        PrdWorkReportBo bo = new PrdWorkReportBo();
        bo.setReportId(1L);
        bo.setReportStatus("APPROVED");

        ServiceException ex = assertThrows(ServiceException.class, () -> workReportService.updateByBo(bo));
        assertTrue(ex.getMessage().contains("超过"), ex.getMessage());
        verify(workOrderMapper, never()).update(any(), any());
        verify(baseMapper, never()).update(any(), any());
    }

    /** ERP-PRD-02：CAS 未抢到（已被审核）→ 返回 0 且不重复累加工单完工数量 */
    @Test
    void updateByBo_alreadyApproved_noDoubleAccumulate() {
        PrdWorkReport old = new PrdWorkReport();
        old.setReportId(1L);
        old.setWoId(9L);
        old.setReportStatus("APPROVED");
        old.setReportedQty(new BigDecimal("10"));
        when(baseMapper.selectById(1L)).thenReturn(old);
        when(baseMapper.selectOne(any())).thenReturn(old);

        PrdWorkOrder wo = new PrdWorkOrder();
        wo.setWoId(9L);
        wo.setPlannedQty(new BigDecimal("1000"));
        wo.setCompletedQty(BigDecimal.ZERO);
        when(workOrderMapper.selectOne(any())).thenReturn(wo);
        when(baseMapper.update(any(), any())).thenReturn(0);

        PrdWorkReportBo bo = new PrdWorkReportBo();
        bo.setReportId(1L);
        bo.setReportStatus("APPROVED");

        assertEquals(0, workReportService.updateByBo(bo));
        verify(workOrderMapper, never()).update(any(), any());
    }

    /** ERP-PRD-02：CAS 抢到后原子累加工单完工数量 */
    @Test
    void updateByBo_casWon_accumulatesWorkOrderQty() {
        PrdWorkReport old = new PrdWorkReport();
        old.setReportId(1L);
        old.setWoId(9L);
        old.setReportStatus("SUBMITTED");
        old.setReportedQty(new BigDecimal("10"));
        when(baseMapper.selectById(1L)).thenReturn(old);
        when(baseMapper.selectOne(any())).thenReturn(old);

        PrdWorkOrder wo = new PrdWorkOrder();
        wo.setWoId(9L);
        wo.setPlannedQty(new BigDecimal("100"));
        wo.setCompletedQty(BigDecimal.ZERO);
        when(workOrderMapper.selectOne(any())).thenReturn(wo);
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(workOrderMapper.update(any(), any())).thenReturn(1);

        PrdWorkReportBo bo = new PrdWorkReportBo();
        bo.setReportId(1L);
        bo.setReportStatus("APPROVED");

        assertEquals(1, workReportService.updateByBo(bo));
        verify(workOrderMapper).update(any(), any());
    }

    /** ERP-PRD-02：updatePrdWorkReport 也不能绕过 CAS/回写 */
    @Test
    void updatePrdWorkReport_approved_goesThroughCas() {
        PrdWorkReport old = new PrdWorkReport();
        old.setReportId(1L);
        old.setWoId(9L);
        old.setReportStatus("SUBMITTED");
        old.setReportedQty(new BigDecimal("10"));
        when(baseMapper.selectById(1L)).thenReturn(old);
        when(baseMapper.selectOne(any())).thenReturn(old);

        PrdWorkOrder wo = new PrdWorkOrder();
        wo.setWoId(9L);
        wo.setPlannedQty(new BigDecimal("100"));
        wo.setCompletedQty(BigDecimal.ZERO);
        when(workOrderMapper.selectOne(any())).thenReturn(wo);
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(workOrderMapper.update(any(), any())).thenReturn(1);

        PrdWorkReport entity = new PrdWorkReport();
        entity.setReportId(1L);
        entity.setReportStatus("APPROVED");

        assertEquals(1, workReportService.updatePrdWorkReport(entity));
        verify(workOrderMapper).update(any(), any());
    }
}
