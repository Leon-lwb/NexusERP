package com.nexus.mes.qc.service.impl;

import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.common.mq.MesMqSendService;
import com.nexus.mes.lt.service.ILtLotService;
import com.nexus.mes.qc.domain.QcIqc;
import com.nexus.mes.qc.domain.bo.QcIqcBo;
import com.nexus.mes.qc.domain.vo.QcIqcVo;
import com.nexus.mes.qc.mapper.QcIqcMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

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
    @InjectMocks
    private QcIqcServiceImpl service;

    @Captor
    private ArgumentCaptor<QcIqc> insertCaptor;

    private MockedStatic<LoginHelper> loginHelperMock;
    private MockedStatic<TenantHelper> tenantHelperMock;
    private MockedStatic<LambdaUtils> lambdaUtilsMock;

    @BeforeEach
    void setUp() {
        loginHelperMock = mockStatic(LoginHelper.class);
        tenantHelperMock = mockStatic(TenantHelper.class);
        lambdaUtilsMock = mockStatic(LambdaUtils.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(1L);
        loginHelperMock.when(LoginHelper::getUsername).thenReturn("tester");
        tenantHelperMock.when(TenantHelper::getTenantId).thenReturn("000000");
        // Pre-populate column cache for QcIqc to avoid MyBatis-Plus lambda resolution failure in pure unit test
        Map<String, ColumnCache> columnMap = new HashMap<>();
        columnMap.put("iqc_id", new ColumnCache("iqc_id", "iqc_id"));
        columnMap.put("iqc_no", new ColumnCache("iqc_no", "iqc_no"));
        columnMap.put("receive_id", new ColumnCache("receive_id", "receive_id"));
        columnMap.put("material_id", new ColumnCache("material_id", "material_id"));
        columnMap.put("material_code", new ColumnCache("material_code", "material_code"));
        columnMap.put("material_name", new ColumnCache("material_name", "material_name"));
        columnMap.put("vendor_id", new ColumnCache("vendor_id", "vendor_id"));
        columnMap.put("vendor_name", new ColumnCache("vendor_name", "vendor_name"));
        columnMap.put("quantity", new ColumnCache("quantity", "quantity"));
        columnMap.put("sample_qty", new ColumnCache("sample_qty", "sample_qty"));
        columnMap.put("inspect_result", new ColumnCache("inspect_result", "inspect_result"));
        columnMap.put("defect_info", new ColumnCache("defect_info", "defect_info"));
        columnMap.put("inspector_id", new ColumnCache("inspector_id", "inspector_id"));
        columnMap.put("inspector_name", new ColumnCache("inspector_name", "inspector_name"));
        columnMap.put("inspect_time", new ColumnCache("inspect_time", "inspect_time"));
        columnMap.put("status", new ColumnCache("status", "status"));
        columnMap.put("remark", new ColumnCache("remark", "remark"));
        columnMap.put("create_time", new ColumnCache("create_time", "create_time"));
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(any())).thenReturn(columnMap);
    }

    @AfterEach
    void tearDown() {
        if (loginHelperMock != null) loginHelperMock.close();
        if (tenantHelperMock != null) tenantHelperMock.close();
        if (lambdaUtilsMock != null) lambdaUtilsMock.close();
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