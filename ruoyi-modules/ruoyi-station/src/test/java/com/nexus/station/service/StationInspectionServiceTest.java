package com.nexus.station.service;

import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.mes.eq.domain.EqEquipment;
import com.nexus.mes.eq.domain.EqInspectionRecord;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.eq.mapper.EqInspectionRecordMapper;
import com.nexus.mes.eq.service.IEqEquipmentService;
import com.nexus.station.domain.MesStationDevice;
import com.nexus.station.domain.dto.StationInspectionSubmitDto;
import com.nexus.station.mapper.StationDeviceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

/**
 * StationInspectionService 单元测试 — 工位点检任务/提交(C14/C15)。
 *
 * @author Nexus
 */
@ExtendWith(MockitoExtension.class)
class StationInspectionServiceTest {

    @Mock
    private StationDeviceMapper deviceMapper;
    @Mock
    private StationDeviceLinkageService linkageService;
    @Mock
    private EqEquipmentMapper equipmentMapper;
    @Mock
    private EqInspectionRecordMapper inspectionRecordMapper;
    @Mock
    private IEqEquipmentService equipmentService;
    @Mock
    private StationAndonService andonService;
    @InjectMocks
    private StationInspectionService service;

    private MockedStatic<LambdaUtils> lambdaUtilsMock;

    private static final StationPassService.OperatorInfo OPERATOR =
        new StationPassService.OperatorInfo(105L, "SCZX-1", "张明");

    @BeforeEach
    void setUp() {
        lambdaUtilsMock = mockStatic(LambdaUtils.class, CALLS_REAL_METHODS);
        Map<String, ColumnCache> deviceColumns = new HashMap<>();
        deviceColumns.put("DEVICEID", new ColumnCache("device_id", "device_id"));
        deviceColumns.put("STATIONCODE", new ColumnCache("station_code", "station_code"));
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(MesStationDevice.class)).thenReturn(deviceColumns);
        Map<String, ColumnCache> equipmentColumns = new HashMap<>();
        equipmentColumns.put("EQUIPMENTID", new ColumnCache("equipment_id", "equipment_id"));
        equipmentColumns.put("LASTINSPECTION", new ColumnCache("last_inspection", "last_inspection"));
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(EqEquipment.class)).thenReturn(equipmentColumns);
        Map<String, ColumnCache> recordColumns = new HashMap<>();
        recordColumns.put("EQUIPMENTID", new ColumnCache("equipment_id", "equipment_id"));
        recordColumns.put("INSPECTTIME", new ColumnCache("inspect_time", "inspect_time"));
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(EqInspectionRecord.class)).thenReturn(recordColumns);
    }

    @AfterEach
    void tearDown() {
        if (lambdaUtilsMock != null) lambdaUtilsMock.close();
    }

    // ==================== C14 当日点检任务 ====================

    @Test
    void tasks_fullChain_returnsItemsAndNotInspected() {
        when(deviceMapper.selectOne(any())).thenReturn(device(7L, "STN|JZ|L1|ASM|GENERAL"));
        when(linkageService.equipmentsOfDevice(7L)).thenReturn(linkage(
            List.of(linkageEquipment(1L)), null));
        when(equipmentMapper.selectById(1L)).thenReturn(equipment(
            "[{\"item\":\"油位\",\"standard\":\"正常\"},{\"item\":\"气压\",\"standard\":\"0.6MPa\"}]"));
        when(inspectionRecordMapper.selectOne(any())).thenReturn(null);

        Map<String, Object> data = service.tasks("STN|JZ|L1|ASM|GENERAL");

        assertEquals("ASM", data.get("processCode"));
        List<?> equipments = (List<?>) data.get("equipments");
        assertEquals(1, equipments.size());
        Map<?, ?> task = (Map<?, ?>) equipments.get(0);
        assertEquals("ASM-001", task.get("equipmentCode"));
        assertEquals(2, ((List<?>) task.get("items")).size());
        assertFalse((Boolean) task.get("inspectedToday"));
    }

    @Test
    void tasks_oldFormatInspectionItems_compat() {
        // SQL 种子数据旧格式 name/unit/threshold → 兼容映射为 item/standard
        when(deviceMapper.selectOne(any())).thenReturn(device(7L, "STN|JZ|L1|ASM|GENERAL"));
        when(linkageService.equipmentsOfDevice(7L)).thenReturn(linkage(
            List.of(linkageEquipment(1L)), null));
        when(equipmentMapper.selectById(1L)).thenReturn(equipment(
            "[{\"name\":\"主轴温度\",\"unit\":\"℃\",\"threshold\":80}]"));
        when(inspectionRecordMapper.selectOne(any())).thenReturn(null);

        Map<String, Object> data = service.tasks("STN|JZ|L1|ASM|GENERAL");

        List<?> items = (List<?>) ((Map<?, ?>) ((List<?>) data.get("equipments")).get(0)).get("items");
        assertEquals(1, items.size());
        Map<?, ?> item = (Map<?, ?>) items.get(0);
        assertEquals("主轴温度", item.get("item"));
        assertEquals("80℃", item.get("standard"));
    }

    @Test
    void tasks_inspectedToday_flagsLatestRecord() {
        when(deviceMapper.selectOne(any())).thenReturn(device(7L, "STN|JZ|L1|ASM|GENERAL"));
        when(linkageService.equipmentsOfDevice(7L)).thenReturn(linkage(
            List.of(linkageEquipment(1L)), null));
        when(equipmentMapper.selectById(1L)).thenReturn(equipment("[]"));
        EqInspectionRecord today = new EqInspectionRecord();
        today.setOverallPass("Y");
        today.setInspectTime(new Date());
        when(inspectionRecordMapper.selectOne(any())).thenReturn(today);

        Map<String, Object> data = service.tasks("STN|JZ|L1|ASM|GENERAL");

        Map<?, ?> task = (Map<?, ?>) ((List<?>) data.get("equipments")).get(0);
        assertTrue((Boolean) task.get("inspectedToday"));
        assertEquals("Y", task.get("lastOverallPass"));
    }

    @Test
    void tasks_deviceNotRegistered_throws() {
        when(deviceMapper.selectOne(any())).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.tasks("STN|JZ|L1|ASM|GENERAL"));
    }

    @Test
    void tasks_noEquipment_passesLinkageMessage() {
        when(deviceMapper.selectOne(any())).thenReturn(device(7L, "STN|JZ|L1|ASM|GENERAL"));
        when(linkageService.equipmentsOfDevice(7L)).thenReturn(linkage(List.of(), "工序 ASM 未配置默认工作中心"));

        Map<String, Object> data = service.tasks("STN|JZ|L1|ASM|GENERAL");

        assertEquals("工序 ASM 未配置默认工作中心", data.get("message"));
        assertTrue(((List<?>) data.get("equipments")).isEmpty());
    }

    // ==================== C15 提交点检 ====================

    @Test
    void submit_pass_writesRecordAndLastInspection() {
        when(equipmentMapper.selectById(1L)).thenReturn(equipment("[]"));

        Map<String, Object> data = service.submit(dto(true), OPERATOR);

        assertTrue((Boolean) data.get("overallPass"));
        ArgumentCaptor<EqInspectionRecord> recordCaptor = ArgumentCaptor.forClass(EqInspectionRecord.class);
        verify(inspectionRecordMapper).insert(recordCaptor.capture());
        EqInspectionRecord record = recordCaptor.getValue();
        assertEquals(1L, record.getEquipmentId());
        assertEquals("Y", record.getOverallPass());
        assertEquals("张明", record.getInspectorName());
        assertEquals("SCZX-1", record.getOperatorCode());
        assertTrue(record.getStationCode().startsWith("STN|JZ|L1|ASM"));
        // 同步 last_inspection
        verify(equipmentMapper).update(isNull(), any());
        // 合格不联动 FAULT/安灯
        verify(equipmentService, never()).updateStatus(any(), any(), any());
        verify(andonService, never()).call(any(), any(), any(), any(), any(), any());
    }

    @Test
    void submit_fail_triggersFaultAndRepairAndon() {
        when(equipmentMapper.selectById(1L)).thenReturn(equipment("[]"));

        Map<String, Object> data = service.submit(dto(false), OPERATOR);

        assertEquals(false, data.get("overallPass"));
        verify(equipmentService).updateStatus(1L, "FAULT", "点检不合格");
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(andonService).call(eq("REPAIR"), reasonCaptor.capture(), anyString(), eq("SCZX-1"), isNull(), isNull());
        assertTrue(reasonCaptor.getValue().contains("ASM-001"));
    }

    @Test
    void submit_faultFailure_degradesAndStillSubmits() {
        // 置 FAULT 抛异常不应阻断提交(降级语义, 对齐 PC 端)
        when(equipmentMapper.selectById(1L)).thenReturn(equipment("[]"));
        when(equipmentService.updateStatus(1L, "FAULT", "点检不合格"))
            .thenThrow(new RuntimeException("MQ 不可用"));

        Map<String, Object> data = service.submit(dto(false), OPERATOR);

        assertEquals(false, data.get("overallPass"));
        verify(inspectionRecordMapper).insert(any(EqInspectionRecord.class));
        verify(andonService).call(eq("REPAIR"), anyString(), anyString(), eq("SCZX-1"), isNull(), isNull());
    }

    @Test
    void submit_overallPassDefaultsToAllItems() {
        when(equipmentMapper.selectById(1L)).thenReturn(equipment("[]"));
        StationInspectionSubmitDto dto = dto(null);
        dto.getResults().get(1).setPass(false);

        Map<String, Object> data = service.submit(dto, OPERATOR);

        assertEquals(false, data.get("overallPass"), "缺省总判定应取全部项与");
    }

    @Test
    void submit_invalidArgs_throw() {
        StationInspectionSubmitDto noStation = dto(true);
        noStation.setStationCode(" ");
        assertThrows(ServiceException.class, () -> service.submit(noStation, OPERATOR));

        StationInspectionSubmitDto noResults = dto(true);
        noResults.setResults(List.of());
        assertThrows(ServiceException.class, () -> service.submit(noResults, OPERATOR));

        StationInspectionSubmitDto noEquipment = dto(true);
        noEquipment.setEquipmentId(null);
        assertThrows(ServiceException.class, () -> service.submit(noEquipment, OPERATOR));
    }

    @Test
    void submit_equipmentNotFound_throws() {
        when(equipmentMapper.selectById(99L)).thenReturn(null);
        StationInspectionSubmitDto dto = dto(true);
        dto.setEquipmentId(99L);
        assertThrows(ServiceException.class, () -> service.submit(dto, OPERATOR));
    }

    // ==================== 辅助 ====================

    private MesStationDevice device(Long id, String stationCode) {
        MesStationDevice d = new MesStationDevice();
        d.setDeviceId(id);
        d.setDeviceCode("IPC-0007");
        d.setStationCode(stationCode);
        d.setProcessCode("ASM");
        return d;
    }

    /** 联动链正向返回结构(对齐 StationDeviceLinkageService.equipmentsOfDevice) */
    private Map<String, Object> linkage(List<Map<String, Object>> equipments, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deviceCode", "IPC-0007");
        m.put("stationCode", "STN|JZ|L1|ASM|GENERAL");
        m.put("processCode", "ASM");
        m.put("processName", "装配");
        m.put("equipments", equipments);
        if (message != null) {
            m.put("message", message);
        }
        return m;
    }

    private Map<String, Object> linkageEquipment(Long equipmentId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("equipmentId", equipmentId);
        m.put("equipmentCode", "ASM-001");
        m.put("equipmentName", "装配流水线");
        return m;
    }

    private EqEquipment equipment(String inspectionItems) {
        EqEquipment e = new EqEquipment();
        e.setEquipmentId(1L);
        e.setEquipmentCode("ASM-001");
        e.setEquipmentName("装配流水线");
        e.setStatus("NORMAL");
        e.setInspectionItems(inspectionItems);
        return e;
    }

    private StationInspectionSubmitDto dto(Boolean overallPass) {
        StationInspectionSubmitDto dto = new StationInspectionSubmitDto();
        dto.setStationCode("STN|JZ|L1|ASM|GENERAL");
        dto.setOperatorCode("SCZX-1");
        dto.setEquipmentId(1L);
        StationInspectionSubmitDto.ItemResult r1 = new StationInspectionSubmitDto.ItemResult();
        r1.setItem("油位");
        r1.setStandard("正常");
        r1.setActual("正常");
        r1.setPass(true);
        StationInspectionSubmitDto.ItemResult r2 = new StationInspectionSubmitDto.ItemResult();
        r2.setItem("气压");
        r2.setStandard("0.6MPa");
        r2.setActual("0.6MPa");
        r2.setPass(true);
        dto.setResults(List.of(r1, r2));
        dto.setOverallPass(overallPass);
        return dto;
    }
}
