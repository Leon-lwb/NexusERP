package com.nexus.mes.qc.service.impl;

import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.common.mq.MesMqSendService;
import com.nexus.mes.qc.domain.QcOqc;
import com.nexus.mes.qc.domain.bo.QcOqcBo;
import com.nexus.mes.qc.domain.vo.QcOqcVo;
import com.nexus.mes.qc.mapper.QcOqcMapper;
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
class QcOqcServiceImplTest {

    @Mock
    private QcOqcMapper baseMapper;
    @Mock
    private MesMqSendService mesMqSendService;
    @InjectMocks
    private QcOqcServiceImpl service;

    @Captor
    private ArgumentCaptor<QcOqc> insertCaptor;

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
        // Pre-populate column cache for QcOqc
        Map<String, ColumnCache> columnMap = new HashMap<>();
        columnMap.put("OQCID", new ColumnCache("oqc_id", "oqc_id"));
        columnMap.put("OQCNO", new ColumnCache("oqc_no", "oqc_no"));
        columnMap.put("SOID", new ColumnCache("so_id", "so_id"));
        columnMap.put("MATERIALID", new ColumnCache("material_id", "material_id"));
        columnMap.put("MATERIALCODE", new ColumnCache("material_code", "material_code"));
        columnMap.put("MATERIALNAME", new ColumnCache("material_name", "material_name"));
        columnMap.put("QUANTITY", new ColumnCache("quantity", "quantity"));
        columnMap.put("SAMPLEQTY", new ColumnCache("sample_qty", "sample_qty"));
        columnMap.put("INSPECTRESULT", new ColumnCache("inspect_result", "inspect_result"));
        columnMap.put("DEFECTINFO", new ColumnCache("defect_info", "defect_info"));
        columnMap.put("INSPECTORID", new ColumnCache("inspector_id", "inspector_id"));
        columnMap.put("INSPECTORNAME", new ColumnCache("inspector_name", "inspector_name"));
        columnMap.put("INSPECTTIME", new ColumnCache("inspect_time", "inspect_time"));
        columnMap.put("STATUS", new ColumnCache("status", "status"));
        columnMap.put("REMARK", new ColumnCache("remark", "remark"));
        columnMap.put("CREATETIME", new ColumnCache("create_time", "create_time"));
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
        when(baseMapper.selectCount(any())).thenReturn(0L);

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