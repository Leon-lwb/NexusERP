package com.nexus.station.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.mes.eq.domain.EqEquipment;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.station.domain.MesStationDevice;
import com.nexus.station.mapper.StationDeviceMapper;
import com.nexus.station.mapper.StationErpProcessMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 工位终端 ↔ 工作中心设备联动视图 (M3, 纯运维便利性增强)
 * <p>
 * 评审结论: 不加列、不建新模型, 复用既有数据链:
 * 正向(工位终端→设备清单): mes_station_device.process_code → bd_process.workstation_id(工序默认工作中心)
 * → eq_equipment.workcenter_id 反查; 反向(工作中心→工位终端): bd_process(默认工作中心=该站)取工序集
 * → mes_station_device.process_code 命中即该工作中心的工位机, 在线状态按心跳判定。
 * </p>
 * <p>数据源: 类级 @DS("mes")(台账/设备在 ry-mes 库); bd_process/bd_workstation 在 ry-erp 库,
 * 经 StationErpProcessMapper(@DS("erp")) 编程式 push/poll 切库查询(对齐 resolveOperator 模式)。
 * 租户由多租户插件按当前登录上下文注入(管理端调用, 不需 TenantHelper.dynamic)。</p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service
public class StationDeviceLinkageService {

    /** 在线判定: 心跳在 3 拍(60s/拍)内视为在线(对齐 StationDeviceAdminController) */
    private static final long ONLINE_THRESHOLD_MS = 3 * 60 * 1000L;

    private final StationDeviceMapper deviceMapper;
    private final EqEquipmentMapper equipmentMapper;
    private final StationErpProcessMapper erpProcessMapper;

    /**
     * 正向: 工位终端 → 工作中心设备清单。
     *
     * @param deviceId 工位终端设备ID(mes_station_device.device_id)
     * @return {deviceCode, stationCode, processCode, processName, workstation, equipments, message?}
     */
    public Map<String, Object> equipmentsOfDevice(Long deviceId) {
        if (deviceId == null) {
            throw new ServiceException("deviceId 不能为空");
        }
        MesStationDevice device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new ServiceException("工位终端不存在: deviceId=" + deviceId);
        }
        String processCode = resolveDeviceProcessCode(device);
        Map<String, Object> data = new LinkedHashMap<>(8);
        data.put("deviceCode", device.getDeviceCode());
        data.put("stationCode", device.getStationCode());
        data.put("processCode", processCode);
        data.put("processName", null);
        data.put("workstation", null);
        data.put("equipments", new ArrayList<>());
        if (StringUtils.isBlank(processCode)) {
            data.put("message", "工位终端未绑定工序, 无法定位工作中心");
            return data;
        }
        Map<String, Object> process = queryErp(() -> erpProcessMapper.selectByProcessCode(processCode));
        if (process == null) {
            data.put("message", "工序 " + processCode + " 未在 ERP 基础数据中定义");
            return data;
        }
        data.put("processName", process.get("processName"));
        Long workstationId = toLong(process.get("workstationId"));
        if (workstationId == null) {
            data.put("message", "工序 " + processCode + " 未配置默认工作中心");
            return data;
        }
        data.put("workstation", workstationOf(workstationId, process));
        List<Map<String, Object>> equipments = equipmentMapper.selectList(Wrappers.<EqEquipment>lambdaQuery()
                .eq(EqEquipment::getWorkcenterId, workstationId)
                .orderByAsc(EqEquipment::getEquipmentCode))
            .stream().map(this::equipmentRow).toList();
        data.put("equipments", equipments);
        if (equipments.isEmpty()) {
            data.put("message", "工作中心未关联设备");
        }
        return data;
    }

    /**
     * 反向: 工作中心 → 工位终端清单(设备详情页展示"由哪台工位机操控 + 在线状态")。
     *
     * @param workcenterId 工作中心ID(bd_workstation.workstation_id, eq_equipment.workcenter_id)
     * @return {workstation, processes, devices, message?}
     */
    public Map<String, Object> devicesOfWorkcenter(Long workcenterId) {
        if (workcenterId == null) {
            throw new ServiceException("workcenterId 不能为空");
        }
        Map<String, Object> workstation = queryErp(() -> erpProcessMapper.selectWorkstationById(workcenterId));
        List<Map<String, Object>> processes = queryErp(() -> erpProcessMapper.selectByWorkstationId(workcenterId));
        Map<String, Object> data = new LinkedHashMap<>(4);
        data.put("workstation", workstation);
        data.put("processes", processes);
        List<String> processCodes = processes.stream()
            .map(p -> (String) p.get("processCode"))
            .filter(StringUtils::isNotBlank)
            .toList();
        if (workstation == null) {
            data.put("message", "工作中心主数据不存在(可能已删除): workstationId=" + workcenterId);
        } else if (processCodes.isEmpty()) {
            data.put("message", "该工作中心未配置默认工序, 无法关联工位终端");
        }
        if (processCodes.isEmpty()) {
            data.put("devices", new ArrayList<>());
            return data;
        }
        Date now = new Date();
        List<Map<String, Object>> devices = deviceMapper.selectList(Wrappers.<MesStationDevice>lambdaQuery()
                .in(MesStationDevice::getProcessCode, processCodes)
                .orderByDesc(MesStationDevice::getLastHeartbeat))
            .stream().map(d -> deviceRow(d, now)).toList();
        data.put("devices", devices);
        if (devices.isEmpty() && workstation != null) {
            data.put("message", "暂无工位终端绑定到该工作中心的工序");
        }
        return data;
    }

    // ==================== 辅助 ====================

    /**
     * 工位终端的工序码: 心跳绑定值(process_code)优先, 工位码第 4 段解析兜底
     * (与 StationPassService.resolveProcessCode 的解析规则一致, 但直用本行数据免一次回查)。
     */
    private String resolveDeviceProcessCode(MesStationDevice device) {
        if (StringUtils.isNotBlank(device.getProcessCode())) {
            return device.getProcessCode().trim();
        }
        if (StringUtils.isNotBlank(device.getStationCode())) {
            String[] parts = device.getStationCode().split("\\|");
            if (parts.length >= 5 && "STN".equalsIgnoreCase(parts[0]) && StringUtils.isNotBlank(parts[3])) {
                return parts[3].trim();
            }
        }
        return null;
    }

    /** 工作中心信息: bd_workstation 主数据优先, 已删时用 bd_process 冗余字段兜底 */
    private Map<String, Object> workstationOf(Long workstationId, Map<String, Object> process) {
        Map<String, Object> workstation = queryErp(() -> erpProcessMapper.selectWorkstationById(workstationId));
        if (workstation != null) {
            return workstation;
        }
        Map<String, Object> fallback = new HashMap<>(4);
        fallback.put("workstationId", workstationId);
        fallback.put("workstationCode", process.get("workstationCode"));
        fallback.put("workstationName", process.get("workstationName"));
        return fallback;
    }

    /** ry-erp 库查询: 编程式切数据源(类级 @DS("mes") 下访问 ERP 表, 对齐 resolveOperator 模式) */
    private <T> T queryErp(Supplier<T> supplier) {
        DynamicDataSourceContextHolder.push("erp");
        try {
            return supplier.get();
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    private Map<String, Object> equipmentRow(EqEquipment e) {
        Map<String, Object> m = new HashMap<>(10);
        m.put("equipmentId", e.getEquipmentId());
        m.put("equipmentCode", e.getEquipmentCode());
        m.put("equipmentName", e.getEquipmentName());
        m.put("equipmentType", e.getEquipmentType());
        m.put("model", e.getModel());
        m.put("manufacturer", e.getManufacturer());
        m.put("location", e.getLocation());
        m.put("status", e.getStatus());
        return m;
    }

    /** 工位终端行(与 StationDeviceAdminController.list 列结构一致, 便于前端复用展示) */
    private Map<String, Object> deviceRow(MesStationDevice d, Date now) {
        Map<String, Object> m = new HashMap<>(12);
        m.put("deviceId", d.getDeviceId());
        m.put("deviceCode", d.getDeviceCode());
        m.put("stationCode", d.getStationCode());
        m.put("processCode", d.getProcessCode());
        m.put("appVersion", d.getAppVersion());
        m.put("operatorCode", d.getOperatorCode());
        m.put("operatorName", d.getOperatorName());
        m.put("shutdown", "1".equals(d.getShutdownFlag()));
        m.put("lastHeartbeat", d.getLastHeartbeat());
        boolean online = !"1".equals(d.getShutdownFlag())
            && d.getLastHeartbeat() != null
            && now.getTime() - d.getLastHeartbeat().getTime() < ONLINE_THRESHOLD_MS;
        m.put("online", online);
        return m;
    }

    private Long toLong(Object v) {
        return v == null ? null : Long.valueOf(String.valueOf(v));
    }
}
