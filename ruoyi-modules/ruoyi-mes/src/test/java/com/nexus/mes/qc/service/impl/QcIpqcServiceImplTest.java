package com.nexus.mes.qc.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.common.mq.MesMqSendService;
import com.nexus.mes.ncr.service.INcrOrderService;
import com.nexus.mes.pro.domain.ProWorkOrder;
import com.nexus.mes.pro.mapper.ProWorkOrderMapper;
import com.nexus.mes.qc.domain.QcIpqc;
import com.nexus.mes.qc.domain.bo.QcIpqcBo;
import com.nexus.mes.qc.domain.vo.QcIpqcVo;
import com.nexus.mes.qc.mapper.QcIpqcMapper;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QcIpqcServiceImplTest {

    @Mock
    private QcIpqcMapper baseMapper;
    @Mock
    private MesMqSendService mesMqSendService;
    @Mock
    private ProWorkOrderMapper proWorkOrderMapper;
    @Mock
    private INcrOrderService ncrOrderService;
    @Mock
    private com.nexus.mes.qc.service.IQcInspectDetailService inspectDetailService;
    @InjectMocks
    private QcIpqcServiceImpl service;

    @Captor
    private ArgumentCaptor<QcIpqc> insertCaptor;

    private MockedStatic<LoginHelper> loginHelperMock;
    private MockedStatic<TenantHelper> tenantHelperMock;

    @BeforeAll
    static void initTableInfo() {
        // 初始化真实 TableInfo/列缓存, 让 MyBatis-Plus lambda 在纯单测中正常解析
        // (旧做法 mockStatic(LambdaUtils) 未 stub extract() 会返回 null meta, 服务新增 lambda 调用即 NPE)
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), QcIpqc.class);
    }

    @AfterAll
    static void clearTableInfo() {
        TableInfoHelper.remove(QcIpqc.class);
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
    void autoCreateForKeyProcessSkipsWhenExists() {
        QcIpqcBo bo = new QcIpqcBo();
        bo.setMesWoId(10L);
        bo.setProcessId(20L);
        when(baseMapper.selectCount(any())).thenReturn(1L);

        service.autoCreateForKeyProcess(bo);

        verify(baseMapper, never()).insert(any(QcIpqc.class));
    }

    @Test
    void autoCreateForKeyProcessCreatesWhenNotExists() {
        QcIpqcBo bo = new QcIpqcBo();
        bo.setMesWoId(10L);
        bo.setProcessId(20L);
        bo.setProcessCode("PC-001");
        bo.setProcessName("焊接");
        bo.setMaterialId(30L);
        bo.setMaterialCode("MAT-001");
        bo.setMaterialName("测试物料");
        bo.setSampleQty(BigDecimal.valueOf(5));
        when(baseMapper.selectCount(any())).thenReturn(0L);
        QcIpqcVo insertedVo = new QcIpqcVo();
        insertedVo.setIpqcId(1L);
        when(baseMapper.selectVoById(any())).thenReturn(insertedVo);

        service.autoCreateForKeyProcess(bo);

        verify(baseMapper).insert(insertCaptor.capture());
        QcIpqc captured = insertCaptor.getValue();
        assertEquals("PENDING", captured.getInspectResult());
        assertEquals("PENDING", captured.getStatus());
        assertEquals(BigDecimal.ZERO, captured.getDefectQty());
        assertEquals(10L, captured.getMesWoId());
        assertEquals(20L, captured.getProcessId());
        assertEquals("PC-001", captured.getProcessCode());
        assertEquals("焊接", captured.getProcessName());
        // MES-QC-02: IPQC 建单必须按 物料/工序 维度匹配模板并生成检验明细
        verify(inspectDetailService).generateDetails(eq("IPQC"), any(), eq(30L), eq(BigDecimal.valueOf(5)),
            eq(20L), isNull());
    }

    @Test
    void existsByWoAndProcessReturnsTrueWhenCountGreaterThanZero() {
        when(baseMapper.selectCount(any())).thenReturn(2L);

        boolean exists = service.existsByWoAndProcess(10L, 20L);

        assertTrue(exists);
    }

    @Test
    void existsByWoAndProcessReturnsFalseWhenCountIsZero() {
        when(baseMapper.selectCount(any())).thenReturn(0L);

        boolean exists = service.existsByWoAndProcess(10L, 20L);

        assertFalse(exists);
    }

    @Test
    void inspectRejectsInvalidResult() {
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> service.inspect(1L, "INVALID", null, null, null, null));
        assertTrue(exception.getMessage().contains("QUALIFIED"));
    }

    @Test
    void inspectRejectsNonexistentIpqc() {
        when(baseMapper.selectById(1L)).thenReturn(null);

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> service.inspect(1L, "QUALIFIED", null, null, null, null));
        assertTrue(exception.getMessage().contains("不存在"));
    }

    @Test
    void inspectRejectsAlreadyCompleted() {
        QcIpqc ipqc = new QcIpqc();
        ipqc.setIpqcId(1L);
        ipqc.setStatus("COMPLETED");
        when(baseMapper.selectById(1L)).thenReturn(ipqc);

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> service.inspect(1L, "QUALIFIED", null, null, null, null));
        assertTrue(exception.getMessage().contains("已检验完成"));
    }

    @Test
    void inspectQualifiedCompletesSuccessfully() {
        QcIpqc ipqc = new QcIpqc();
        ipqc.setIpqcId(1L);
        ipqc.setIpqcNo("IPQC-001");
        ipqc.setStatus("PENDING");
        ipqc.setMesWoId(10L);
        when(baseMapper.selectById(1L)).thenReturn(ipqc);
        when(baseMapper.update(any(), any())).thenReturn(1);

        service.inspect(1L, "QUALIFIED", "{}", BigDecimal.valueOf(10), BigDecimal.valueOf(1), "OK");

        verify(baseMapper).update(any(), any());
    }

    @Test
    void inspectThrowsWhenCasRowZero() {
        QcIpqc ipqc = new QcIpqc();
        ipqc.setIpqcId(1L);
        ipqc.setIpqcNo("IPQC-001");
        ipqc.setStatus("PENDING");
        when(baseMapper.selectById(1L)).thenReturn(ipqc);
        when(baseMapper.update(any(), any())).thenReturn(0);

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> service.inspect(1L, "QUALIFIED", null, null, null, null));
        assertTrue(exception.getMessage().contains("状态已变更"));
    }

    @Test
    void inspectCalculatesDefectRateCorrectly() {
        QcIpqc ipqc = new QcIpqc();
        ipqc.setIpqcId(1L);
        ipqc.setIpqcNo("IPQC-001");
        ipqc.setStatus("PENDING");
        ipqc.setMesWoId(10L);
        when(baseMapper.selectById(1L)).thenReturn(ipqc);
        when(baseMapper.update(any(), any())).thenReturn(1);

        service.inspect(1L, "UNQUALIFIED", null, BigDecimal.valueOf(100), BigDecimal.valueOf(5), null);

        verify(baseMapper).update(any(), any());
    }

    @Test
    void inspectWithErpWoIdSendsMqAfterCommit() {
        QcIpqc ipqc = new QcIpqc();
        ipqc.setIpqcId(1L);
        ipqc.setIpqcNo("IPQC-001");
        ipqc.setStatus("PENDING");
        ipqc.setMesWoId(10L);
        when(baseMapper.selectById(1L)).thenReturn(ipqc);
        when(baseMapper.update(any(), any())).thenReturn(1);

        ProWorkOrder wo = new ProWorkOrder();
        wo.setErpWoId(999L);
        wo.setWoNo("WO-001");
        // 原工单映射必须在事务内读取，不能延迟到 afterCommit。
        when(proWorkOrderMapper.selectById(10L)).thenReturn(wo);

        service.inspect(1L, "QUALIFIED", null, null, null, null);

        verify(mesMqSendService, never()).sendIpqcCompleted(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(proWorkOrderMapper).selectById(10L);
        clearInvocations(proWorkOrderMapper, baseMapper);
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
        verify(mesMqSendService).sendIpqcCompleted(1L, "IPQC-001", 10L, 999L, "WO-001",
            "QUALIFIED", null, null, 1L, "tester", "000000");
        verifyNoInteractions(proWorkOrderMapper, baseMapper);
    }

    @Test
    void inspectWithoutErpWoIdSkipsMq() {
        QcIpqc ipqc = new QcIpqc();
        ipqc.setIpqcId(1L);
        ipqc.setIpqcNo("IPQC-001");
        ipqc.setStatus("PENDING");
        ipqc.setMesWoId(10L);
        when(baseMapper.selectById(1L)).thenReturn(ipqc);
        when(baseMapper.update(any(), any())).thenReturn(1);

        ProWorkOrder wo = new ProWorkOrder();
        wo.setErpWoId(null);
        // 原工单映射必须在事务内读取，不能延迟到 afterCommit。
        when(proWorkOrderMapper.selectById(10L)).thenReturn(wo);

        service.inspect(1L, "QUALIFIED", null, null, null, null);
        verify(proWorkOrderMapper).selectById(10L);
        clearInvocations(proWorkOrderMapper, baseMapper);
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
        verifyNoInteractions(mesMqSendService, proWorkOrderMapper, baseMapper);
    }
}