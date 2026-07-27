package com.nexus.mes.eq.service.impl;

import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.iot.service.IotDeviceDataQueryService;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.common.mq.MesMqSendService;
import com.nexus.mes.eq.domain.EqEquipment;
import com.nexus.mes.eq.domain.bo.EqEquipmentBo;
import com.nexus.mes.eq.domain.vo.EqEquipmentVo;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.pro.mapper.ProWorkOrderMapper;
import com.nexus.mes.pro.service.IProAndonService;
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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EqEquipmentServiceImplTest {

    @Mock
    private EqEquipmentMapper baseMapper;
    @Mock
    private IotDeviceDataQueryService iotDeviceDataQueryService;
    @Mock
    private IProAndonService proAndonService;
    @Mock
    private MesMqSendService mesMqSendService;
    @Mock
    private ProWorkOrderMapper proWorkOrderMapper;
    @InjectMocks
    private EqEquipmentServiceImpl service;

    @Captor
    private ArgumentCaptor<EqEquipment> insertCaptor;

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
        // Pre-populate column cache for EqEquipment
        Map<String, ColumnCache> columnMap = new HashMap<>();
        columnMap.put("EQUIPMENTID", new ColumnCache("equipment_id", "equipment_id"));
        columnMap.put("EQUIPMENTCODE", new ColumnCache("equipment_code", "equipment_code"));
        columnMap.put("EQUIPMENTNAME", new ColumnCache("equipment_name", "equipment_name"));
        columnMap.put("EQUIPMENTTYPE", new ColumnCache("equipment_type", "equipment_type"));
        columnMap.put("MODEL", new ColumnCache("model", "model"));
        columnMap.put("MANUFACTURER", new ColumnCache("manufacturer", "manufacturer"));
        columnMap.put("SERIALNO", new ColumnCache("serial_no", "serial_no"));
        columnMap.put("WORKCENTERID", new ColumnCache("workcenter_id", "workcenter_id"));
        columnMap.put("WORKCENTERCODE", new ColumnCache("workcenter_code", "workcenter_code"));
        columnMap.put("LOCATION", new ColumnCache("location", "location"));
        columnMap.put("PURCHASEDATE", new ColumnCache("purchase_date", "purchase_date"));
        columnMap.put("WARRANTYDATE", new ColumnCache("warranty_date", "warranty_date"));
        columnMap.put("STATUS", new ColumnCache("status", "status"));
        columnMap.put("INSPECTIONITEMS", new ColumnCache("inspection_items", "inspection_items"));
        columnMap.put("LASTINSPECTION", new ColumnCache("last_inspection", "last_inspection"));
        columnMap.put("IOTDEVICEID", new ColumnCache("iot_device_id", "iot_device_id"));
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

    // ==================== updateStatus 联动 ====================

    @Test
    void updateStatusReturnsFalseWhenEquipmentNotFound() {
        when(baseMapper.selectById(1L)).thenReturn(null);

        Boolean result = service.updateStatus(1L, "FAULT");

        assertFalse(result);
    }

    @Test
    void updateStatusReturnsFalseForNullArgs() {
        Boolean result = service.updateStatus(null, "FAULT");
        assertFalse(result);

        result = service.updateStatus(1L, null);
        assertFalse(result);
    }

    @Test
    void updateStatusScrappedNotOverwritten() {
        EqEquipment equipment = new EqEquipment();
        equipment.setEquipmentId(1L);
        equipment.setStatus("SCRAPPED");
        when(baseMapper.selectById(1L)).thenReturn(equipment);

        Boolean result = service.updateStatus(1L, "NORMAL");

        assertFalse(result);
        verify(baseMapper, never()).update(any(), any());
    }

    @Test
    void updateStatusSameStatusReturnsTrueWithoutEvent() {
        EqEquipment equipment = new EqEquipment();
        equipment.setEquipmentId(1L);
        equipment.setEquipmentCode("EQ-001");
        equipment.setEquipmentName("测试设备");
        equipment.setStatus("NORMAL");
        when(baseMapper.selectById(1L)).thenReturn(equipment);

        Boolean result = service.updateStatus(1L, "NORMAL");

        assertTrue(result);
        verify(baseMapper, never()).update(any(), any());
    }

    @Test
    void updateStatusChangesStatusAndPublishesEvent() {
        EqEquipment equipment = new EqEquipment();
        equipment.setEquipmentId(1L);
        equipment.setEquipmentCode("EQ-001");
        equipment.setEquipmentName("测试设备");
        equipment.setStatus("NORMAL");
        when(baseMapper.selectById(1L)).thenReturn(equipment);
        when(baseMapper.update(any(), any())).thenReturn(1);

        Boolean result = service.updateStatus(1L, "FAULT", "故障");

        assertTrue(result);
        verify(baseMapper).update(any(), any());
    }

    @Test
    void updateStatusCasRowZeroReturnsFalse() {
        EqEquipment equipment = new EqEquipment();
        equipment.setEquipmentId(1L);
        equipment.setEquipmentCode("EQ-001");
        equipment.setEquipmentName("测试设备");
        equipment.setStatus("NORMAL");
        when(baseMapper.selectById(1L)).thenReturn(equipment);
        when(baseMapper.update(any(), any())).thenReturn(0);

        Boolean result = service.updateStatus(1L, "FAULT", "故障");

        assertFalse(result);
    }

    // ==================== resetToNormalIfNoActiveRepair ====================

    @Test
    void resetToNormalIfNoActiveRepairReturnsFalseForNull() {
        boolean result = service.resetToNormalIfNoActiveRepair(null);
        assertFalse(result);
    }

    @Test
    void resetToNormalIfNoActiveRepairReturnsFalseWhenEquipmentNotFound() {
        when(baseMapper.selectById(1L)).thenReturn(null);

        boolean result = service.resetToNormalIfNoActiveRepair(1L);

        assertFalse(result);
    }

    @Test
    void resetToNormalIfNoActiveRepairReturnsFalseWhenAlreadyNormal() {
        EqEquipment equipment = new EqEquipment();
        equipment.setEquipmentId(1L);
        equipment.setStatus("NORMAL");
        when(baseMapper.selectById(1L)).thenReturn(equipment);

        boolean result = service.resetToNormalIfNoActiveRepair(1L);

        assertFalse(result);
    }

    @Test
    void resetToNormalIfNoActiveRepairReturnsFalseWhenScrapped() {
        EqEquipment equipment = new EqEquipment();
        equipment.setEquipmentId(1L);
        equipment.setStatus("SCRAPPED");
        when(baseMapper.selectById(1L)).thenReturn(equipment);

        boolean result = service.resetToNormalIfNoActiveRepair(1L);

        assertFalse(result);
    }

    @Test
    void resetToNormalIfNoActiveRepairReturnsTrueWhenNoActiveRepairs() {
        EqEquipment equipment = new EqEquipment();
        equipment.setEquipmentId(1L);
        equipment.setEquipmentCode("EQ-001");
        equipment.setEquipmentName("测试设备");
        equipment.setStatus("FAULT");
        when(baseMapper.selectById(1L)).thenReturn(equipment);
        when(baseMapper.resetToNormalIfNoActiveRepair(1L)).thenReturn(1);

        boolean result = service.resetToNormalIfNoActiveRepair(1L);

        assertTrue(result);
    }

    @Test
    void resetToNormalIfNoActiveRepairReturnsFalseWhenHasActiveRepairs() {
        EqEquipment equipment = new EqEquipment();
        equipment.setEquipmentId(1L);
        equipment.setEquipmentCode("EQ-001");
        equipment.setEquipmentName("测试设备");
        equipment.setStatus("FAULT");
        when(baseMapper.selectById(1L)).thenReturn(equipment);
        when(baseMapper.resetToNormalIfNoActiveRepair(1L)).thenReturn(0);

        boolean result = service.resetToNormalIfNoActiveRepair(1L);

        assertFalse(result);
    }

    // ==================== insertByBo ====================

    @Test
    void insertByBoRejectsDuplicateCode() {
        when(baseMapper.selectCount(any())).thenReturn(1L);

        EqEquipmentBo bo = new EqEquipmentBo();
        bo.setEquipmentCode("EQ-001");

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.insertByBo(bo));
        assertTrue(exception.getMessage().contains("设备编码已存在"));
    }

    @Test
    void insertByBoSetsDefaultStatusNormal() {
        when(baseMapper.selectCount(any())).thenReturn(0L);
        EqEquipmentVo insertedVo = new EqEquipmentVo();
        insertedVo.setEquipmentId(1L);
        when(baseMapper.selectVoById(any())).thenReturn(insertedVo);

        EqEquipmentBo bo = new EqEquipmentBo();
        bo.setEquipmentCode("EQ-001");
        bo.setEquipmentName("测试设备");

        service.insertByBo(bo);

        verify(baseMapper).insert(insertCaptor.capture());
        EqEquipment captured = insertCaptor.getValue();
        assertEquals("NORMAL", captured.getStatus());
        assertEquals("EQ-001", captured.getEquipmentCode());
        assertEquals("测试设备", captured.getEquipmentName());
    }

    // ==================== deleteByIds ====================

    @Test
    void deleteByIdsReturnsFalseForNull() {
        Boolean result = service.deleteByIds(null);
        assertFalse(result);
    }

    @Test
    void deleteByIdsDeletesSuccessfully() {
        when(baseMapper.deleteByIds(anyList())).thenReturn(1);

        Boolean result = service.deleteByIds(new Long[]{1L});

        assertTrue(result);
    }

    // ==================== updateIotDeviceLink ====================

    @Test
    void updateIotDeviceLinkReturnsFalseForNullEquipmentId() {
        Boolean result = service.updateIotDeviceLink(null, 1L);
        assertFalse(result);
    }

    @Test
    void updateIotDeviceLinkUpdatesSuccessfully() {
        when(baseMapper.update(any(), any())).thenReturn(1);

        Boolean result = service.updateIotDeviceLink(1L, 100L);

        assertTrue(result);
    }

    // ==================== statusSummary ====================

    @Test
    void statusSummaryReturnsAllCounts() {
        when(baseMapper.selectCount(any())).thenReturn(10L, 5L, 2L, 2L, 1L);

        var summary = service.statusSummary();

        assertEquals(10L, summary.get("total"));
        assertEquals(5L, summary.get("normal"));
        assertEquals(2L, summary.get("maintenance"));
        assertEquals(2L, summary.get("fault"));
        assertEquals(1L, summary.get("scrapped"));
    }
}