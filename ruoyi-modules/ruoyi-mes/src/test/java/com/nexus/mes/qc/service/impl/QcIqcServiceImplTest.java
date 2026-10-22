package com.nexus.mes.qc.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.common.mq.MesMqSendService;
import com.nexus.mes.lt.service.ILtLotService;
import com.nexus.mes.ncr.service.INcrOrderService;
import com.nexus.mes.qc.domain.QcInspectDetail;
import com.nexus.mes.qc.domain.QcIqc;
import com.nexus.mes.qc.domain.bo.QcInspectEntryBo;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    void legacyReceiptCreationMustNotBypassNativeBirthDispatcher() {
        QcIqcBo bo = new QcIqcBo();
        bo.setReceiveId(100L); bo.setMaterialId(200L); bo.setQuantity(new BigDecimal("999"));
        ServiceException failure = assertThrows(ServiceException.class, () -> service.insertByBo(bo));
        assertEquals("IQC_RECEIPT_NATIVE_BIRTH_REQUIRED", failure.getMessage());
        verifyNoInteractions(baseMapper, inspectDetailService);
    }

    @Test
    void unlinkedManualInsertSetsInitialStatusAndResult() {
        QcIqcBo bo = new QcIqcBo();
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

        QcIqcVo result = service.insertByBo(bo);

        verify(baseMapper).insert(insertCaptor.capture());
        QcIqc captured = insertCaptor.getValue();
        assertEquals("PENDING", captured.getInspectResult());
        assertEquals("PENDING", captured.getStatus());
        assertNull(captured.getReceiveId());
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

        // 单元测试没有真实事务，不会执行提交后回调；仍须确认没有直接发送MQ。
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

    // 原始唯一键冲突不能作为出生绑定存在或人工单创建成功的证据。
    @Test
    void unlinkedManualDuplicateKeyIsNotBlindSuccess() {
        QcIqcBo bo = new QcIqcBo(); bo.setMaterialId(200L);
        when(baseMapper.insert(any(QcIqc.class))).thenThrow(new DuplicateKeyException("uk_iqc_no"));
        assertThrows(DuplicateKeyException.class, () -> service.insertByBo(bo));
        verify(baseMapper, never()).selectVoOne(any());
        verifyNoInteractions(inspectDetailService);
    }

    // ==================== MES-QC-05: 缺项提交必须被拒 ====================

    @Test
    void inspectWithDetailsRejectsWhenAnyDetailMissingMeasuredValue() {
        when(inspectDetailService.selectByCheck("IQC", 1L))
            .thenReturn(List.of(detail(1L, "尺寸"), detail(2L, "硬度")));

        QcInspectEntryBo entry = new QcInspectEntryBo();
        entry.setDetailId(1L);
        entry.setMeasuredValue("10.0");

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.inspectWithDetails(1L, List.of(entry), null, null));

        assertTrue(ex.getMessage().contains("仍有 1 项未录入实测值"), ex.getMessage());
        assertTrue(ex.getMessage().contains("硬度"), "错误信息必须列出未录入项目名: " + ex.getMessage());
        verify(inspectDetailService, never()).updateDetails(any());
    }

    @Test
    void inspectWithDetailsAcceptsWhenAllDetailsCovered() {
        QcInspectDetail d1 = detail(1L, "尺寸");
        d1.setDataType("QUALITATIVE");
        when(inspectDetailService.selectByCheck("IQC", 1L)).thenReturn(List.of(d1));

        QcIqc iqc = new QcIqc();
        iqc.setIqcId(1L);
        iqc.setIqcNo("IQC-001");
        iqc.setStatus("PENDING");
        iqc.setQuantity(BigDecimal.TEN);
        when(baseMapper.selectById(1L)).thenReturn(iqc);
        when(baseMapper.update(any(), any())).thenReturn(1);

        QcInspectEntryBo entry = new QcInspectEntryBo();
        entry.setDetailId(1L);
        entry.setMeasuredValue("OK");

        assertDoesNotThrow(() -> service.inspectWithDetails(1L, List.of(entry), null, null));
        verify(inspectDetailService).updateDetails(any());
    }

    // ==================== MES-QC-08: 并发生成单号必须唯一 ====================

    @Test
    void generateNoIsUniqueAcrossRepeatedInserts() {
        when(baseMapper.selectVoById(any())).thenReturn(new QcIqcVo());
        Set<String> nos = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            QcIqcBo bo = new QcIqcBo();
            bo.setMaterialId(200L);
            service.insertByBo(bo);
        }

        verify(baseMapper, times(10)).insert(insertCaptor.capture());
        for (QcIqc inserted : insertCaptor.getAllValues()) {
            assertTrue(inserted.getIqcNo().startsWith("IQC-"), inserted.getIqcNo());
            assertTrue(nos.add(inserted.getIqcNo()), "MES-QC-08: 单号重复 " + inserted.getIqcNo());
        }
        assertEquals(10, nos.size());
    }

    // ==================== MES-QC-10①: 批次回写失败必须回滚(不静默) ====================

    @Test
    void inspectFailsWhenApplyIqcResultThrows() {
        QcIqc iqc = new QcIqc();
        iqc.setIqcId(1L);
        iqc.setIqcNo("IQC-001");
        iqc.setStatus("PENDING");
        iqc.setReceiveId(100L);
        iqc.setMaterialId(200L);
        iqc.setQuantity(BigDecimal.TEN);
        when(baseMapper.selectById(1L)).thenReturn(iqc);
        when(baseMapper.update(any(), any())).thenReturn(1);
        when(ltLotService.applyIqcResult(1L, "QUALIFIED")).thenThrow(new RuntimeException("批次状态已变更"));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.inspect(1L, "QUALIFIED", null, null, null));

        assertTrue(ex.getMessage().contains("IQC回写批次QC状态失败"), ex.getMessage());
        // MES-QC-10①: 批次回写必须在检验事务内执行(而不是 afterCommit 里 warn 后静默吞掉)
        verify(ltLotService).applyIqcResult(1L, "QUALIFIED");
        verify(mesMqSendService, never()).sendIqcCompleted(any(), any(), any(), any(), any(), any(), any());
    }

    private static QcInspectDetail detail(Long detailId, String itemName) {
        QcInspectDetail d = new QcInspectDetail();
        d.setDetailId(detailId);
        d.setItemName(itemName);
        d.setDataType("QUANTITATIVE");
        d.setStandardType("LSL_USL");
        d.setLslValue(BigDecimal.ZERO);
        d.setUslValue(BigDecimal.valueOf(100));
        d.setCriticality("MAJOR");
        d.setJudgeResult("PENDING");
        return d;
    }
}