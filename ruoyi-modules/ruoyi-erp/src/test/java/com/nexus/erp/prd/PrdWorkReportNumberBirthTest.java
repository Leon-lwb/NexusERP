package com.nexus.erp.prd;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.prd.domain.PrdWorkReport;
import com.nexus.erp.prd.domain.bo.PrdWorkReportBo;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.prd.mapper.PrdWorkReportMapper;
import com.nexus.erp.prd.service.impl.PrdWorkReportServiceImpl;
import com.nexus.system.strategy.AutoCodeUtil;
import io.github.linpeilie.Converter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 正常报工BO新增的编号出生测试：捕获真实insert参数，不以请求回填或前端提示代替持久化输入。
 * 编号器及Mapper均为mock，不调用真实编码规则、数据库或MQ；跨库注解核查不代表事务运行验收。
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PrdWorkReportNumberBirthTest {

    private static final String GENERATED_REPORT_NO = "BG261007001";
    private static final String EXISTING_REPORT_NO = "BG261006001";

    @Mock
    private PrdWorkReportMapper baseMapper;
    @Mock
    private PrdWorkOrderMapper workOrderMapper;
    @Mock
    private AutoCodeUtil autoCodeUtil;
    @InjectMocks
    private PrdWorkReportServiceImpl service;

    private static MockedStatic<SpringUtil> springUtilMock;

    @BeforeAll
    static void initConverter() {
        springUtilMock = mockStatic(SpringUtil.class);
        Converter converter = new Converter() {
            @Override
            public <S, T> T convert(S source, Class<T> targetType) {
                return BeanUtil.copyProperties(source, targetType);
            }
        };
        springUtilMock.when(() -> SpringUtil.getBean(Converter.class)).thenReturn(converter);
    }

    @AfterAll
    static void closeConverter() {
        if (springUtilMock != null) {
            springUtilMock.close();
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    @DisplayName("空白报工号必须在insert前通过既有规则出生，状态仍强制SUBMITTED")
    void blankReportNumberIsGeneratedBeforeInsert(String reportNo) {
        PrdWorkReportBo bo = newReport(reportNo);
        // RED阶段旧服务尚未使用编号器，lenient只允许这一个预期未调用的stub；断言仍核真实insert参数。
        lenient().when(autoCodeUtil.genSerialCode("PRD_REPORT_CODE", null)).thenReturn(GENERATED_REPORT_NO);
        when(baseMapper.insert(any(PrdWorkReport.class))).thenReturn(1);

        assertEquals(1, service.insertByBo(bo));

        ArgumentCaptor<PrdWorkReport> captor = ArgumentCaptor.forClass(PrdWorkReport.class);
        verify(baseMapper).insert(captor.capture());
        PrdWorkReport inserted = captor.getValue();
        assertEquals(GENERATED_REPORT_NO, inserted.getReportNo(), "真实insert参数必须已有服务端生成的报工号");
        assertEquals("SUBMITTED", inserted.getReportStatus(), "编号出生不能放开客户端直接审核");
        assertEquals(bo.getWoId(), inserted.getWoId());
        assertEquals(bo.getMaterialId(), inserted.getMaterialId());
        assertEquals(bo.getUnitId(), inserted.getUnitId());
        assertEquals(bo.getReportedQty(), inserted.getReportedQty());
        verify(autoCodeUtil).genSerialCode("PRD_REPORT_CODE", null);
        verifyNoMoreInteractions(autoCodeUtil, baseMapper);
        verifyNoInteractions(workOrderMapper);
    }

    @Test
    @DisplayName("合法已有自填号保持原样，不生成新号也不触碰历史报工")
    void existingReportNumberIsPreservedWithoutGenerating() {
        PrdWorkReportBo bo = newReport(EXISTING_REPORT_NO);
        when(baseMapper.insert(any(PrdWorkReport.class))).thenReturn(1);

        assertEquals(1, service.insertByBo(bo));

        ArgumentCaptor<PrdWorkReport> captor = ArgumentCaptor.forClass(PrdWorkReport.class);
        verify(baseMapper).insert(captor.capture());
        assertEquals(EXISTING_REPORT_NO, captor.getValue().getReportNo());
        assertEquals("SUBMITTED", captor.getValue().getReportStatus());
        verifyNoMoreInteractions(baseMapper);
        verifyNoInteractions(autoCodeUtil, workOrderMapper);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " \t")
    @DisplayName("编号器返回空白必须明确拒绝，不能把无号报工送入insert")
    void blankGeneratedReportNumberIsRejected(String generatedNumber) {
        PrdWorkReportBo bo = newReport(null);
        lenient().when(autoCodeUtil.genSerialCode("PRD_REPORT_CODE", null)).thenReturn(generatedNumber);

        ServiceException error = assertThrows(ServiceException.class, () -> service.insertByBo(bo));

        assertTrue(error.getMessage().contains("编号"), error.getMessage());
        verify(autoCodeUtil).genSerialCode("PRD_REPORT_CODE", null);
        verify(baseMapper, never()).insert(any(PrdWorkReport.class));
        verifyNoInteractions(workOrderMapper);
    }

    @Test
    @DisplayName("现有编号器失败原样传播，不构造替代编号或继续insert")
    void generatorFailureDoesNotInsertReport() {
        ServiceException expected = new ServiceException("报工编号规则不可用");
        lenient().when(autoCodeUtil.genSerialCode("PRD_REPORT_CODE", null)).thenThrow(expected);

        ServiceException actual = assertThrows(ServiceException.class, () -> service.insertByBo(newReport(null)));

        assertSame(expected, actual);
        verify(autoCodeUtil).genSerialCode("PRD_REPORT_CODE", null);
        verify(baseMapper, never()).insert(any(PrdWorkReport.class));
        verifyNoInteractions(workOrderMapper);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, -1})
    @DisplayName("insert返回0、2、-1均必须异常拒绝，不能返回零效果或多行成功")
    void unexpectedInsertCountIsRejected(int insertedRows) {
        when(baseMapper.insert(any(PrdWorkReport.class))).thenReturn(insertedRows);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.insertByBo(newReport(EXISTING_REPORT_NO)), "insert必须恰好影响1行");

        assertTrue(error.getMessage().contains("写入失败"), error.getMessage());
        ArgumentCaptor<PrdWorkReport> captor = ArgumentCaptor.forClass(PrdWorkReport.class);
        verify(baseMapper).insert(captor.capture());
        assertEquals(EXISTING_REPORT_NO, captor.getValue().getReportNo());
        assertEquals("SUBMITTED", captor.getValue().getReportStatus());
        verifyNoMoreInteractions(baseMapper);
        verifyNoInteractions(autoCodeUtil, workOrderMapper);
    }

    @Test
    @DisplayName("复用编号器现有master独立新事务，不自建跨库事务平台")
    void generatorRetainsExistingMasterRequiresNewBoundary() throws NoSuchMethodException {
        Method method = AutoCodeUtil.class.getMethod("genSerialCode", String.class, String.class);
        assertEquals("master", method.getAnnotation(DS.class).value());
        assertEquals(Propagation.REQUIRES_NEW, method.getAnnotation(Transactional.class).propagation());
    }

    private static PrdWorkReportBo newReport(String reportNo) {
        PrdWorkReportBo bo = new PrdWorkReportBo();
        bo.setReportNo(reportNo);
        bo.setReportStatus("APPROVED");
        bo.setWoId(9L);
        bo.setMaterialId(10L);
        bo.setUnitId(26L);
        bo.setReportedQty(new BigDecimal("2"));
        return bo;
    }
}