package com.nexus.mes.eq.task;

import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.nexus.common.iot.service.IotDeviceDataQueryService;
import com.nexus.mes.common.mapper.MesSysTenantMapper;
import com.nexus.mes.eq.domain.EqEquipment;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.eq.service.IEqEquipmentService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * EquipmentStatusSyncTask 单元测试 (F8-5)。
 * <p>直接测包级 syncOne: ONLINE->NORMAL / OFFLINE->OFFLINE / UNKNOWN|null 跳过 / 同态 no-op / CAS 命中0行 false。
 * mockStatic(LambdaUtils,CALLS_REAL_METHODS) 解决 lambdaUpdate 缓存(对齐 NcrOrderServiceImplTest)。
 * jobExecute 的多租户遍历(TenantHelper.dynamic + master 数据源)由 E2E 覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
class EquipmentStatusSyncTaskTest {

    @Mock private EqEquipmentMapper equipmentMapper;
    @Mock private IotDeviceDataQueryService iotDeviceDataQueryService;
    @Mock private MesSysTenantMapper mesSysTenantMapper;
    @InjectMocks private EquipmentStatusSyncTask task;

    private MockedStatic<LambdaUtils> lambdaUtilsMock;

    @BeforeEach
    void setUp() {
        lambdaUtilsMock = mockStatic(LambdaUtils.class, CALLS_REAL_METHODS);
        Map<String, ColumnCache> cols = new HashMap<>();
        cols.put("STATUS", col("status"));
        cols.put("EQUIPMENTID", col("equipment_id"));
        cols.put("IOTDEVICEID", col("iot_device_id"));
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(EqEquipment.class)).thenReturn(cols);
    }

    private ColumnCache col(String c) {
        return new ColumnCache(c, c);
    }

    @AfterEach
    void tearDown() {
        if (lambdaUtilsMock != null) {
            lambdaUtilsMock.close();
        }
    }

    private EqEquipment eq(Long id, Long iotId, String status) {
        EqEquipment e = new EqEquipment();
        e.setEquipmentId(id);
        e.setIotDeviceId(iotId);
        e.setStatus(status);
        return e;
    }

    @Test
    void syncOne_online_offlineToNormal() {
        EqEquipment e = eq(1L, 10L, IEqEquipmentService.STATUS_OFFLINE);
        when(iotDeviceDataQueryService.getOnlineStatus(10L)).thenReturn("ONLINE");
        when(equipmentMapper.update(isNull(), any())).thenReturn(1);

        assertTrue(task.syncOne(e), "OFFLINE + ONLINE -> NORMAL, 应变更");
        verify(equipmentMapper).update(isNull(), any());
    }

    @Test
    void syncOne_offline_normalToOffline() {
        EqEquipment e = eq(1L, 10L, IEqEquipmentService.STATUS_NORMAL);
        when(iotDeviceDataQueryService.getOnlineStatus(10L)).thenReturn("OFFLINE");
        when(equipmentMapper.update(isNull(), any())).thenReturn(1);

        assertTrue(task.syncOne(e), "NORMAL + OFFLINE -> OFFLINE, 应变更");
    }

    @Test
    void syncOne_unknown_skip() {
        EqEquipment e = eq(1L, 10L, IEqEquipmentService.STATUS_NORMAL);
        when(iotDeviceDataQueryService.getOnlineStatus(10L)).thenReturn("UNKNOWN");

        assertFalse(task.syncOne(e), "UNKNOWN 不臆断, 跳过");
        verify(equipmentMapper, never()).update(any(), any());
    }

    @Test
    void syncOne_iotNull_skip() {
        EqEquipment e = eq(1L, 10L, IEqEquipmentService.STATUS_NORMAL);
        when(iotDeviceDataQueryService.getOnlineStatus(10L)).thenReturn(null);

        assertFalse(task.syncOne(e), "IoT 状态 null 跳过");
        verify(equipmentMapper, never()).update(any(), any());
    }

    @Test
    void syncOne_sameStatus_noop() {
        EqEquipment e = eq(1L, 10L, IEqEquipmentService.STATUS_NORMAL);
        when(iotDeviceDataQueryService.getOnlineStatus(10L)).thenReturn("ONLINE");

        assertFalse(task.syncOne(e), "NORMAL + ONLINE 同态, no-op");
        verify(equipmentMapper, never()).update(any(), any());
    }

    @Test
    void syncOne_casMiss_returnsFalse() {
        // 模拟 CAS WHERE status IN(NORMAL,OFFLINE) 命中 0 行(并发被改 FAULT/MAINTENANCE/SCRAPPED)
        EqEquipment e = eq(1L, 10L, IEqEquipmentService.STATUS_NORMAL);
        when(iotDeviceDataQueryService.getOnlineStatus(10L)).thenReturn("OFFLINE");
        when(equipmentMapper.update(isNull(), any())).thenReturn(0);

        assertFalse(task.syncOne(e), "CAS 命中 0 行(受管状态不覆盖) -> false");
    }
}
