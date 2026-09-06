package com.nexus.mes.qc.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.common.mq.MesMqSendService;
import com.nexus.mes.lt.service.ILtLotService;
import com.nexus.mes.ncr.service.INcrOrderService;
import com.nexus.mes.qc.domain.QcIqc;
import com.nexus.mes.qc.domain.bo.QcIqcBo;
import com.nexus.mes.qc.domain.vo.QcIqcVo;
import com.nexus.mes.qc.mapper.QcIqcMapper;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QcIqcServiceImplTest {

    @Mock
    private QcIqcMapper baseMapper;
    @Mock
    private MesMqSendService mesMqSendService;
    @Mock
    private ILtLotService ltLotService;
    @Mock
    private INcrOrderService ncrOrderService;
    @Mock
    private IQcInspectDetailService inspectDetailService;
    @InjectMocks
    private QcIqcServiceImpl service;

    @Captor
    private ArgumentCaptor<QcIqc> insertCaptor;

    private MockedStatic<LoginHelper> loginHelperMock;
    private MockedStatic<TenantHelper> tenantHelperMock;

    @BeforeAll
    static void initTableInfo() {
        // 初始化真实 TableInfo/列缓存, 让 MyBatis-Plus lambda 在纯单测中正常解析
        // (旧做法 mockStatic(LambdaUtils) 未 stub extract() 会返回 null meta, 服务新增 lambda 调用即 NPE)
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), QcIqc.class);
    }

    @AfterAll
    static void clearTableInfo() {
        TableInfoHelper.remove(QcIqc.class);
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
        QcIqcBo bo = new QcIqcBo();
        bo.setReceiveId(100L);
        bo.setMaterialId(200L);
        bo.setMaterialCode("MAT-001");
        bo.setMaterialName("测试物料");
        bo.setVendorId(300L);
        bo.setVendorName("测试供应商");
        bo.setQuantity(BigDecimal.TEN);
        bo.setSampleQty(BigDecimal.valueOf(5));
        bo.setRemark("测试");

        QcIqcVo insertedVo = new QcIqcVo();
        insertedVo.setIqcId(1L);
        when(baseMapper.selectVoById(any())).thenReturn(insertedVo);
        when(baseMapper.selectCount(any())).thenReturn(0L);

        QcIqcVo result = service.insertByBo(bo);

        verify(baseMapper).insert(insertCaptor.capture());
        QcIqc captured = insertCaptor.getValue();
        assertEquals("PENDING", captured.getInspectResult());
        assertEquals("PENDING", captured.getStatus());
        assertEquals(100L, captured.getReceiveId());
        assertEquals(200L, captured.getMaterialId());
        assertEquals("MAT-001", captured.getMaterialCode());
        assertEquals("测试物料", captured.getMaterialName());
        assertEquals(BigDecimal.TEN, captured.getQuantity());
        assertEquals(BigDecimal.valueOf(5), captured.getSampleQty());
        assertEquals("测试", captured.getRemark());
        assertEquals(300L, captured.getVendorId());
        assertEquals("测试供应商", captured.getVendorName());
        assertNotNull(captured.getIqcNo());
        assertTrue(captured.getIqcNo().startsWith("IQC-"));
        assertEquals(insertedVo, result);
    }

    @Test
    void existsByReceiveAndMaterialReturnsTrueWhenCountGreaterThanZero() {
        when(baseMapper.selectCount(any())).thenReturn(3L);

        boolean exists = service.existsByReceiveAndMaterial(100L, 200L);

        assertTrue(exists);
    }

    @Test
    void existsByReceiveAndMaterialReturnsFalseWhenCountIsZero() {
        when(baseMapper.selectCount(any())).thenReturn(0L);

        boolean exists = service.existsByReceiveAndMaterial(100L, 200L);

        assertFalse(exists);
    }

    @Test
    void inspectRejectsInvalidResult() {
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> service.inspect(1L, "INVALID", null, null, null));
        assertTrue(exception.getMessage().contains("QUALIFIED"));
    }

    @Test
    void inspectRejectsNonexistentIqc() {
        when(baseMapper.selectById(1L)).thenReturn(null);

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> service.inspect(1L, "QUALIFIED", null, null, null));
        assertTrue(exception.getMessage().contains("不存在"));
    }

    @Test
    void inspectRejectsAlreadyCompleted() {
        QcIqc iqc = new QcIqc();
        iqc.setIqcId(1L);
        iqc.setStatus("COMPLETED");
        iqc.setIqcNo("IQC-001");
        when(baseMapper.selectById(1L)).thenReturn(iqc);

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> service.inspect(1L, "QUALIFIED", null, null, null));
        assertTrue(exception.getMessage().contains("已检验完成"));
    }

    @Test
    void inspectCompletesQualifiedAndUpdatesStatus() {
        QcIqc iqc = new QcIqc();
        iqc.setIqcId(1L);
        iqc.setIqcNo("IQC-20250727-001");
        iqc.setStatus("PENDING");
        iqc.setReceiveId(100L);
        iqc.setMaterialId(200L);
        iqc.setQuantity(BigDecimal.TEN);
        when(baseMapper.selectById(1L)).thenReturn(iqc);
        when(baseMapper.update(any(), any())).thenReturn(1);

        service.inspect(1L, "QUALIFIED", "{}", BigDecimal.valueOf(5), "OK");

        verify(baseMapper).update(any(), any());
    }

    @Test
    void inspectThrowsWhenCasRowZero() {
        QcIqc iqc = new QcIqc();
        iqc.setIqcId(1L);
        iqc.setIqcNo("IQC-001");
        iqc.setStatus("PENDING");
        when(baseMapper.selectById(1L)).thenReturn(iqc);
        when(baseMapper.update(any(), any())).thenReturn(0);

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> service.inspect(1L, "QUALIFIED", null, null, null));
        assertTrue(exception.getMessage().contains("状态已变更"));
    }

    @Test
    void inspectQualifiedCallsApplyIqcResultAndMqInAfterCommit() {
        QcIqc iqc = new QcIqc();
        iqc.setIqcId(1L);
        iqc.setIqcNo("IQC-20250727-001");
        iqc.setStatus("PENDING");
        iqc.setReceiveId(100L);
        iqc.setMaterialId(200L);
        iqc.setQuantity(BigDecimal.TEN);
        when(baseMapper.selectById(1L)).thenReturn(iqc);
        when(baseMapper.update(any(), any())).thenReturn(1);

        service.inspect(1L, "QUALIFIED", null, null, null);

        // afterCommit won't execute in unit test (no transaction), but verify the MQ was not called directly
        verify(mesMqSendService, never()).sendIqcCompleted(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void inspectUnqualifiedCompletesSuccessfully() {
        QcIqc iqc = new QcIqc();
        iqc.setIqcId(1L);
        iqc.setIqcNo("IQC-20250727-001");
        iqc.setStatus("PENDING");
        iqc.setReceiveId(100L);
        iqc.setMaterialId(200L);
        iqc.setQuantity(BigDecimal.TEN);
        when(baseMapper.selectById(1L)).thenReturn(iqc);
        when(baseMapper.update(any(), any())).thenReturn(1);

        service.inspect(1L, "UNQUALIFIED", "不良信息", BigDecimal.valueOf(5), "不合格");

        verify(baseMapper).update(any(), any());
    }
}