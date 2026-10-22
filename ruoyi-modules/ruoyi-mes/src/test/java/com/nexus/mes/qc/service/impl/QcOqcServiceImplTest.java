package com.nexus.mes.qc.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.common.mq.MesMqSendService;
import com.nexus.mes.ncr.service.INcrOrderService;
import com.nexus.mes.qc.domain.QcOqc;
import com.nexus.mes.qc.domain.bo.QcOqcBo;
import com.nexus.mes.qc.domain.vo.QcOqcVo;
import com.nexus.mes.qc.mapper.QcOqcMapper;
import com.nexus.mes.qc.service.IQcInspectDetailService;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;

@ExtendWith(MockitoExtension.class)
class QcOqcServiceImplTest {

    @Mock
    private QcOqcMapper baseMapper;
    @Mock
    private MesMqSendService mesMqSendService;
    @Mock
    private INcrOrderService ncrOrderService;
    @Mock
    private IQcInspectDetailService inspectDetailService;
    @InjectMocks
    private QcOqcServiceImpl service;

    @Captor
    private ArgumentCaptor<QcOqc> insertCaptor;

    private MockedStatic<LoginHelper> loginHelperMock;
    private MockedStatic<TenantHelper> tenantHelperMock;

    @BeforeAll
    static void initTableInfo() {
        // 初始化真实 TableInfo/列缓存, 让 MyBatis-Plus lambda 在纯单测中正常解析
        // (旧做法 mockStatic(LambdaUtils) 未 stub extract() 会返回 null meta, 服务新增 lambda 调用即 NPE)
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), QcOqc.class);
    }

    @AfterAll
    static void clearTableInfo() {
        TableInfoHelper.remove(QcOqc.class);
    }

    @BeforeEach
    void setUp() {
        loginHelperMock = mockStatic(LoginHelper.class);
        tenantHelperMock = mockStatic(TenantHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(1L);
        loginHelperMock.when(LoginHelper::getUsername).thenReturn("tester");
        tenantHelperMock.when(TenantHelper::getTenantId).thenReturn("000000");
        // 激活事务同步, 使 service 内 registerSynchronization 可注册 afterCommit 回调(单测无真实事务提交)
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (loginHelperMock != null) loginHelperMock.close();
        if (tenantHelperMock != null) tenantHelperMock.close();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void insertByBoSetsInitialStatusAndResult() {
        QcOqcBo bo = new QcOqcBo();
        bo.setSoId(100L);
        bo.setMaterialId(200L);
        bo.setMaterialCode("MAT-001");
        bo.setMaterialName("测试物料");
        bo.setQuantity(BigDecimal.TEN);
        bo.setSampleQty(BigDecimal.valueOf(5));
        bo.setRemark("测试");

        QcOqcVo insertedVo = new QcOqcVo();
        insertedVo.setOqcId(1L);
        when(baseMapper.selectVoById(any())).thenReturn(insertedVo);

        QcOqcVo result = service.insertByBo(bo);

        verify(baseMapper).insert(insertCaptor.capture());
        QcOqc captured = insertCaptor.getValue();
        assertEquals("PENDING", captured.getInspectResult());
        assertEquals("PENDING", captured.getStatus());
        assertEquals(100L, captured.getSoId());
        assertEquals(200L, captured.getMaterialId());
        assertEquals("MAT-001", captured.getMaterialCode());
        assertEquals("测试物料", captured.getMaterialName());
        assertEquals(BigDecimal.TEN, captured.getQuantity());
        assertEquals(BigDecimal.valueOf(5), captured.getSampleQty());
        assertEquals("测试", captured.getRemark());
        assertNotNull(captured.getOqcNo());
        assertTrue(captured.getOqcNo().startsWith("OQC-"));
        assertEquals(insertedVo, result);
        // MES-QC-02: OQC 建单必须按 物料 维度匹配模板并生成检验明细
        verify(inspectDetailService).generateDetails(eq("OQC"), any(), eq(200L), eq(BigDecimal.TEN), isNull(), isNull());
    }

    @Test
    void existsBySoAndMaterialReturnsTrueWhenCountGreaterThanZero() {
        when(baseMapper.selectCount(any())).thenReturn(2L);

        boolean exists = service.existsBySoAndMaterial(100L, 200L);

        assertTrue(exists);
    }

    @Test
    void existsBySoAndMaterialReturnsFalseWhenCountIsZero() {
        when(baseMapper.selectCount(any())).thenReturn(0L);

        boolean exists = service.existsBySoAndMaterial(100L, 200L);

        assertFalse(exists);
    }

    @Test
    void inspectRejectsInvalidResult() {
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> service.inspect(1L, "INVALID", null, null, null));
        assertTrue(exception.getMessage().contains("QUALIFIED"));
    }

    @Test
    void inspectRejectsNonexistentOqc() {
        when(baseMapper.selectById(1L)).thenReturn(null);

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> service.inspect(1L, "QUALIFIED", null, null, null));
        assertTrue(exception.getMessage().contains("不存在"));
    }

    @Test
    void inspectRejectsAlreadyCompleted() {
        QcOqc oqc = new QcOqc();
        oqc.setOqcId(1L);
        oqc.setStatus("COMPLETED");
        when(baseMapper.selectById(1L)).thenReturn(oqc);

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> service.inspect(1L, "QUALIFIED", null, null, null));
        assertTrue(exception.getMessage().contains("已检验完成"));
    }

    @Test
    void inspectQualifiedCompletesSuccessfully() {
        QcOqc oqc = new QcOqc();
        oqc.setOqcId(1L);
        oqc.setOqcNo("OQC-001");
        oqc.setStatus("PENDING");
        oqc.setSoId(100L);
        oqc.setMaterialId(200L);
        oqc.setMaterialCode("MAT-001");
        oqc.setQuantity(BigDecimal.TEN);
        when(baseMapper.selectById(1L)).thenReturn(oqc);
        when(baseMapper.update(any(), any())).thenReturn(1);

        service.inspect(1L, "QUALIFIED", "{}", BigDecimal.valueOf(5), "OK");

        verify(baseMapper).update(any(), any());
    }

    @Test
    void inspectThrowsWhenCasRowZero() {
        QcOqc oqc = new QcOqc();
        oqc.setOqcId(1L);
        oqc.setOqcNo("OQC-001");
        oqc.setStatus("PENDING");
        when(baseMapper.selectById(1L)).thenReturn(oqc);
        when(baseMapper.update(any(), any())).thenReturn(0);

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> service.inspect(1L, "QUALIFIED", null, null, null));
        assertTrue(exception.getMessage().contains("状态已变更"));
    }

    @Test
    void inspectUnqualifiedCompletesSuccessfully() {
        QcOqc oqc = new QcOqc();
        oqc.setOqcId(1L);
        oqc.setOqcNo("OQC-001");
        oqc.setStatus("PENDING");
        oqc.setSoId(100L);
        oqc.setMaterialId(200L);
        oqc.setMaterialCode("MAT-001");
        oqc.setQuantity(BigDecimal.TEN);
        when(baseMapper.selectById(1L)).thenReturn(oqc);
        when(baseMapper.update(any(), any())).thenReturn(1);

        service.inspect(1L, "UNQUALIFIED", "不良信息", BigDecimal.valueOf(5), "不合格");

        verify(baseMapper).update(any(), any());
    }

    @Test
    void inspectWithoutSoIdSkipsMqSend() {
        QcOqc oqc = new QcOqc();
        oqc.setOqcId(1L);
        oqc.setOqcNo("OQC-001");
        oqc.setStatus("PENDING");
        oqc.setSoId(null);
        when(baseMapper.selectById(1L)).thenReturn(oqc);
        when(baseMapper.update(any(), any())).thenReturn(1);

        service.inspect(1L, "QUALIFIED", null, null, null);

        // afterCommit won't execute, no direct MQ call
        verify(mesMqSendService, never()).sendOqcCompleted(any(), any(), any(), any(), any(), any(), any(), any());
    }
}