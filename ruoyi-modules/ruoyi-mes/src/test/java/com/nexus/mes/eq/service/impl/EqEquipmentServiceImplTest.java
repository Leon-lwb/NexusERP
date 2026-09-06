package com.nexus.mes.eq.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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

    @BeforeAll
    static void initTableInfo() {
        // 初始化真实 TableInfo/列缓存, 让 MyBatis-Plus lambda 在纯单测中正常解析
        // (旧做法 mockStatic(LambdaUtils) 未 stub extract() 会返回 null meta, 服务新增 lambda 调用即 NPE)
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EqEquipment.class);
    }

    @AfterAll
    static void clearTableInfo() {
        TableInfoHelper.remove(EqEquipment.class);
    }

    @BeforeEach
    void setUp() {
        loginHelperMock = mockStatic(LoginHelper.class);
        tenantHelperMock = mockStatic(TenantHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(1L);
        loginHelperMock.when(LoginHelper::getUsername).thenReturn("tester");
        tenantHelperMock.when(TenantHelper::getTenantId).thenReturn("000000");
    }

    @AfterEach
    void tearDown() {
        if (loginHelperMock != null) loginHelperMock.close();
        if (tenantHelperMock != null) tenantHelperMock.close();
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