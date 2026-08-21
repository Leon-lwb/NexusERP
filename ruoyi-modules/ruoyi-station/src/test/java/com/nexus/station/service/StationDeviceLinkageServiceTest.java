package com.nexus.station.service;

import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.mes.eq.domain.EqEquipment;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.station.domain.MesStationDevice;
import com.nexus.station.mapper.StationDeviceMapper;
import com.nexus.station.mapper.StationErpProcessMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

/**
 * StationDeviceLinkageService 单元测试 — M3 联动视图正向/反向查询。
 *
 * @author Nexus
 */
@ExtendWith(MockitoExtension.class)
class StationDeviceLinkageServiceTest {

    @Mock
    private StationDeviceMapper deviceMapper;
    @Mock
    private EqEquipmentMapper equipmentMapper;
    @Mock
    private StationErpProcessMapper erpProcessMapper;
    @InjectMocks
    private StationDeviceLinkageService service;

    private MockedStatic<LambdaUtils> lambdaUtilsMock;

    @BeforeEach
    void setUp() {
        // CALLS_REAL_METHODS: extract() 走真实方法(反序列化 lambda 拿属性名), 仅 getColumnMap 打桩
        lambdaUtilsMock = mockStatic(LambdaUtils.class, CALLS_REAL_METHODS);
        Map<String, ColumnCache> deviceColumns = new HashMap<>();
        deviceColumns.put("DEVICEID", new ColumnCache("device_id", "device_id"));
        deviceColumns.put("DEVICECODE", new ColumnCache("device_code", "device_code"));
        deviceColumns.put("STATIONCODE", new ColumnCache("station_code", "station_code"));
        deviceColumns.put("PROCESSCODE", new ColumnCache("process_code", "process_code"));
        deviceColumns.put("LASTHEARTBEAT", new ColumnCache("last_heartbeat", "last_heartbeat"));
        deviceColumns.put("SHUTDOWNFLAG", new ColumnCache("shutdown_flag", "shutdown_flag"));
        Map<String, ColumnCache> equipmentColumns = new HashMap<>();
        equipmentColumns.put("EQUIPMENTID", new ColumnCache("equipment_id", "equipment_id"));
        equipmentColumns.put("EQUIPMENTCODE", new ColumnCache("equipment_code", "equipment_code"));
        equipmentColumns.put("WORKCENTERID", new ColumnCache("workcenter_id", "workcenter_id"));
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(MesStationDevice.class)).thenReturn(deviceColumns);
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(EqEquipment.class)).thenReturn(equipmentColumns);
    }

    @AfterEach
    void tearDown() {
        if (lambdaUtilsMock != null) lambdaUtilsMock.close();
    }

    // ==================== 正向: 工位终端 → 工作中心设备清单 ====================

    @Test
    void forward_fullChain_returnsWorkstationAndEquipments() {
        MesStationDevice device = device(1L, "IPC-0007", "STN|注塑车间|产线1|ASM|GENERAL", "ASM");
        when(deviceMapper.selectById(1L)).thenReturn(device);
        when(erpProcessMapper.selectByProcessCode("ASM")).thenReturn(process("ASM", "装配", 6L));
        when(erpProcessMapper.selectWorkstationById(6L)).thenReturn(workstation(6L, "GZZ0007", "装配线A"));
        when(equipmentMapper.selectList(any())).thenReturn(List.of(
            equipment("EQ-1", "装配流水线#1", 6L), equipment("EQ-2", "压合机A", 6L)));

        Map<String, Object> data = service.equipmentsOfDevice(1L);

        assertEquals("ASM", data.get("processCode"));
        assertEquals("装配", data.get("processName"));
        Map<?, ?> workstation = (Map<?, ?>) data.get("workstation");
        assertEquals("GZZ0007", workstation.get("workstationCode"));
        assertEquals("装配车间#1", workstation.get("workshopName"));
        List<?> equipments = (List<?>) data.get("equipments");
        assertEquals(2, equipments.size());
        assertNull(data.get("message"), "正常链路不应有提示语");
    }

    @Test
    void forward_processWithoutWorkstation_returnsMessageAndEmptyList() {
        MesStationDevice device = device(1L, "IPC-0007", null, "PKG");
        when(deviceMapper.selectById(1L)).thenReturn(device);
        Map<String, Object> process = process("PKG", "包装", null);
        when(erpProcessMapper.selectByProcessCode("PKG")).thenReturn(process);

        Map<String, Object> data = service.equipmentsOfDevice(1L);

        assertEquals("工序 PKG 未配置默认工作中心", data.get("message"));
        assertNull(data.get("workstation"));
        assertTrue(((List<?>) data.get("equipments")).isEmpty());
    }

    @Test
    void forward_processCodeNull_fallsBackToStationCodeParse() {
        // 心跳绑定工序缺失时按工位码第 4 段解析兜底(与 resolveProcessCode 口径一致)
        MesStationDevice device = device(1L, "IPC-0007", "STN|注塑车间|产线1|PKG|GENERAL", null);
        when(deviceMapper.selectById(1L)).thenReturn(device);
        when(erpProcessMapper.selectByProcessCode("PKG")).thenReturn(process("PKG", "包装", 8L));
        when(erpProcessMapper.selectWorkstationById(8L)).thenReturn(workstation(8L, "GZZ0014", "包装线"));
        when(equipmentMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> data = service.equipmentsOfDevice(1L);

        assertEquals("PKG", data.get("processCode"));
        assertEquals("GZZ0014", ((Map<?, ?>) data.get("workstation")).get("workstationCode"));
        assertEquals("工作中心未关联设备", data.get("message"));
    }

    @Test
    void forward_deviceNotFound_throws() {
        when(deviceMapper.selectById(99L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.equipmentsOfDevice(99L));
    }

    // ==================== 反向: 工作中心 → 工位终端清单 ====================

    @Test
    void reverse_fullChain_aggregatesDevicesWithOnlineStatus() {
        when(erpProcessMapper.selectWorkstationById(6L)).thenReturn(workstation(6L, "GZZ0007", "装配线A"));
        when(erpProcessMapper.selectByWorkstationId(6L)).thenReturn(List.of(
            processRow("ASM", "装配"), processRow("PKG", "包装")));
        when(deviceMapper.selectList(any())).thenReturn(List.of(
            onlineDevice("IPC-0007", "ASM"),
            shutdownDevice("IPC-0008", "ASM"),
            staleHeartbeatDevice("IPC-0009", "PKG")));

        Map<String, Object> data = service.devicesOfWorkcenter(6L);

        assertEquals("GZZ0007", ((Map<?, ?>) data.get("workstation")).get("workstationCode"));
        assertEquals(2, ((List<?>) data.get("processes")).size());
        List<?> devices = (List<?>) data.get("devices");
        assertEquals(3, devices.size());
        assertTrue((Boolean) ((Map<?, ?>) devices.get(0)).get("online"), "心跳 1 分钟内应在线");
        assertFalse((Boolean) ((Map<?, ?>) devices.get(1)).get("online"), "shutdown_flag=1 应即时离线");
        assertFalse((Boolean) ((Map<?, ?>) devices.get(2)).get("online"), "心跳超 3 分钟应离线");
        assertNull(data.get("message"));
        verify(deviceMapper).selectList(any());
    }

    @Test
    void reverse_noProcesses_returnsEmptyDevicesWithMessage() {
        when(erpProcessMapper.selectWorkstationById(6L)).thenReturn(workstation(6L, "GZZ0007", "装配线A"));
        when(erpProcessMapper.selectByWorkstationId(6L)).thenReturn(List.of());

        Map<String, Object> data = service.devicesOfWorkcenter(6L);

        assertEquals("该工作中心未配置默认工序, 无法关联工位终端", data.get("message"));
        assertTrue(((List<?>) data.get("devices")).isEmpty());
    }

    @Test
    void reverse_workstationDeleted_stillResolvesProcessesWithMessage() {
        // bd_workstation 主数据已删但 bd_process 仍指向该站: 照常反查工序与工位机
        when(erpProcessMapper.selectWorkstationById(6L)).thenReturn(null);
        when(erpProcessMapper.selectByWorkstationId(6L)).thenReturn(List.of(processRow("ASM", "装配")));
        when(deviceMapper.selectList(any())).thenReturn(List.of(onlineDevice("IPC-0007", "ASM")));

        Map<String, Object> data = service.devicesOfWorkcenter(6L);

        assertEquals("工作中心主数据不存在(可能已删除): workstationId=6", data.get("message"));
        assertEquals(1, ((List<?>) data.get("devices")).size());
    }

    // ==================== 辅助 ====================

    private MesStationDevice device(Long id, String code, String stationCode, String processCode) {
        MesStationDevice d = new MesStationDevice();
        d.setDeviceId(id);
        d.setDeviceCode(code);
        d.setStationCode(stationCode);
        d.setProcessCode(processCode);
        return d;
    }

    /** bd_process 行(camelCase 键, 对齐 StationErpProcessMapper SQL 别名) */
    private Map<String, Object> process(String code, String name, Long workstationId) {
        Map<String, Object> m = new HashMap<>();
        m.put("processId", 1L);
        m.put("processCode", code);
        m.put("processName", name);
        m.put("workstationId", workstationId);
        m.put("workstationCode", workstationId != null ? "GZZ0007" : null);
        m.put("workstationName", workstationId != null ? "装配线A" : null);
        return m;
    }

    /** bd_process 行(反向查询, 无工作中心冗余字段) */
    private Map<String, Object> processRow(String code, String name) {
        Map<String, Object> m = new HashMap<>();
        m.put("processId", 1L);
        m.put("processCode", code);
        m.put("processName", name);
        return m;
    }

    /** bd_workstation 行 */
    private Map<String, Object> workstation(Long id, String code, String name) {
        Map<String, Object> m = new HashMap<>();
        m.put("workstationId", id);
        m.put("workstationCode", code);
        m.put("workstationName", name);
        m.put("workshopCode", "CJ009");
        m.put("workshopName", "装配车间#1");
        m.put("status", 1);
        return m;
    }

    private EqEquipment equipment(String code, String name, Long workcenterId) {
        EqEquipment e = new EqEquipment();
        e.setEquipmentId(1L);
        e.setEquipmentCode(code);
        e.setEquipmentName(name);
        e.setWorkcenterId(workcenterId);
        e.setStatus("NORMAL");
        return e;
    }

    private MesStationDevice onlineDevice(String code, String processCode) {
        MesStationDevice d = device(1L, code, "STN|注塑车间|产线1|" + processCode + "|GENERAL", processCode);
        d.setShutdownFlag("0");
        d.setLastHeartbeat(new Date(System.currentTimeMillis() - 60_000L));
        return d;
    }

    private MesStationDevice shutdownDevice(String code, String processCode) {
        MesStationDevice d = device(2L, code, "STN|注塑车间|产线1|" + processCode + "|GENERAL", processCode);
        d.setShutdownFlag("1");
        d.setLastHeartbeat(new Date());
        return d;
    }

    private MesStationDevice staleHeartbeatDevice(String code, String processCode) {
        MesStationDevice d = device(3L, code, "STN|注塑车间|产线1|" + processCode + "|GENERAL", processCode);
        d.setShutdownFlag("0");
        d.setLastHeartbeat(new Date(System.currentTimeMillis() - 10 * 60_000L));
        return d;
    }
}
