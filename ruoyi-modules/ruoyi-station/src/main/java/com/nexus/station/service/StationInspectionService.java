package com.nexus.station.service;

import cn.hutool.core.date.DateUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.mes.eq.domain.EqEquipment;
import com.nexus.mes.eq.domain.EqInspectionRecord;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.eq.mapper.EqInspectionRecordMapper;
import com.nexus.mes.eq.service.IEqEquipmentService;
import com.nexus.station.domain.MesStationDevice;
import com.nexus.station.domain.dto.StationInspectionSubmitDto;
import com.nexus.station.mapper.StationDeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工位终端设备点检服务 (M3 契约 C14/C15)
 * <p>当日点检任务定位链复用 {@link StationDeviceLinkageService}:
 * 工位工序 → bd_process 默认工作中心 → eq_equipment 反查;
 * 点检项数据源为 eq_equipment.inspection_items(兼容 item/standard 新格式与
 * name/unit/threshold 旧格式), 历史留痕落 eq_inspection_record(新表)并同步
 * eq_equipment.last_inspection(对齐 PC 端 submitInspection 的 JSON 格式)。</p>
 * <p>提交不合格联动(均降级不阻断): 设备置 FAULT(复用 IEqEquipmentService.updateStatus,
 * 含 MQ 事件) + REPAIR 安灯(复用 StationAndonService.call)。
 * 操作员解析必须在事务外(事务连接钉在 ry-mes 无法查 ry-erp 的 sch_worker),
 * 由 Controller 解析后传入本类事务方法(对齐 StationPassService 模式)。</p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service
public class StationInspectionService {

    /** 设备状态: 故障(PC 端点检不合格同款置位) */
    private static final String STATUS_FAULT = "FAULT";

    /** JSON 解析用独立 mapper(纯函数可单测, 不涉 API 序列化) */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final StationDeviceMapper deviceMapper;
    private final StationDeviceLinkageService linkageService;
    private final EqEquipmentMapper equipmentMapper;
    private final EqInspectionRecordMapper inspectionRecordMapper;
    private final IEqEquipmentService equipmentService;
    private final StationAndonService andonService;

    /**
     * 工位当日点检任务(C14): 联动链定位设备清单 + 逐台点检项/当日已检状态。
     *
     * @param stationCode 工位编码(支持 6 段码, 自动归一化)
     * @return {stationCode, processCode, workstation, message?, equipments: [{equipmentId, equipmentCode,
     *         equipmentName, status, items: [{item, standard}], inspectedToday, lastOverallPass, lastInspectTime}]}
     */
    public Map<String, Object> tasks(String stationCode) {
        String station = StationPassService.normalizeStationCode(stationCode);
        if (StringUtils.isBlank(station)) {
            throw new ServiceException("stationCode 不能为空");
        }
        MesStationDevice device = deviceMapper.selectOne(Wrappers.<MesStationDevice>lambdaQuery()
            .eq(MesStationDevice::getStationCode, station)
            .last("LIMIT 1"));
        if (device == null) {
            throw new ServiceException("工位终端未注册(心跳未上报): " + station);
        }
        // 复用 M3 联动视图正向链: 工位工序 → 默认工作中心 → 设备清单
        Map<String, Object> linkage = linkageService.equipmentsOfDevice(device.getDeviceId());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> linkageEquipments = (List<Map<String, Object>>) linkage.getOrDefault("equipments", List.of());
        List<Map<String, Object>> equipments = new ArrayList<>(linkageEquipments.size());
        for (Map<String, Object> linkageEq : linkageEquipments) {
            Long equipmentId = toLong(linkageEq.get("equipmentId"));
            if (equipmentId == null) {
                continue;
            }
            EqEquipment equipment = equipmentMapper.selectById(equipmentId);
            if (equipment == null) {
                continue;
            }
            EqInspectionRecord today = latestTodayRecord(equipmentId);
            Map<String, Object> task = new LinkedHashMap<>(10);
            task.put("equipmentId", equipmentId);
            task.put("equipmentCode", equipment.getEquipmentCode());
            task.put("equipmentName", equipment.getEquipmentName());
            task.put("status", equipment.getStatus());
            task.put("items", parseInspectionItems(equipment.getInspectionItems()));
            task.put("inspectedToday", today != null);
            task.put("lastOverallPass", today != null ? today.getOverallPass() : null);
            task.put("lastInspectTime", today != null ? today.getInspectTime() : null);
            equipments.add(task);
        }
        Map<String, Object> data = new LinkedHashMap<>(6);
        data.put("stationCode", station);
        data.put("processCode", linkage.get("processCode"));
        data.put("workstation", linkage.get("workstation"));
        data.put("equipments", equipments);
        if (linkage.get("message") != null) {
            data.put("message", linkage.get("message"));
        } else if (equipments.isEmpty()) {
            data.put("message", "本工位未关联设备, 无点检任务");
        }
        return data;
    }

    /**
     * 提交点检(C15): 写 eq_inspection_record 留痕 + 同步 last_inspection;
     * 不合格置 FAULT + REPAIR 安灯(均降级)。
     * <p>操作员由 Controller 在事务外解析传入(事务内无法跨库查 sch_worker)。</p>
     *
     * @param dto      点检提交请求
     * @param operator 事务外解析好的操作员(可空)
     * @return {recordId, overallPass}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submit(StationInspectionSubmitDto dto, StationPassService.OperatorInfo operator) {
        String station = StationPassService.normalizeStationCode(dto == null ? null : dto.getStationCode());
        if (StringUtils.isBlank(station)) {
            throw new ServiceException("stationCode 不能为空");
        }
        if (dto.getEquipmentId() == null) {
            throw new ServiceException("equipmentId 不能为空");
        }
        if (dto.getResults() == null || dto.getResults().isEmpty()) {
            throw new ServiceException("点检结果不能为空");
        }
        for (StationInspectionSubmitDto.ItemResult r : dto.getResults()) {
            if (r == null || StringUtils.isBlank(r.getItem())) {
                throw new ServiceException("点检项存在空项名");
            }
        }
        EqEquipment equipment = equipmentMapper.selectById(dto.getEquipmentId());
        if (equipment == null) {
            throw new ServiceException("设备不存在: " + dto.getEquipmentId());
        }
        boolean overallPass = dto.getOverallPass() != null
            ? dto.getOverallPass()
            : dto.getResults().stream().allMatch(r -> Boolean.TRUE.equals(r.getPass()));
        Date now = new Date();

        // 1. 历史留痕
        EqInspectionRecord record = new EqInspectionRecord();
        record.setEquipmentId(dto.getEquipmentId());
        record.setStationCode(station);
        record.setInspectorId(operator.userId());
        record.setInspectorName(operator.name());
        record.setOperatorCode(operator.code());
        record.setInspectTime(now);
        record.setResults(toJson(dto.getResults()));
        record.setOverallPass(overallPass ? "Y" : "N");
        record.setRemark(dto.getRemark());
        inspectionRecordMapper.insert(record);

        // 2. 同步 eq_equipment.last_inspection(对齐 PC 端 submitInspection 的 JSON 格式, PC 详情页可见)
        Map<String, Object> last = new LinkedHashMap<>(6);
        last.put("inspectTime", now);
        last.put("inspectorId", operator.userId());
        last.put("inspectorName", StringUtils.isNotBlank(operator.name()) ? operator.name() : operator.code());
        last.put("results", dto.getResults());
        last.put("overallPass", overallPass);
        last.put("remark", dto.getRemark());
        equipmentMapper.update(null, Wrappers.<EqEquipment>lambdaUpdate()
            .set(EqEquipment::getLastInspection, toJson(last))
            .eq(EqEquipment::getEquipmentId, dto.getEquipmentId()));

        // 3. 不合格联动(降级, 不阻断提交): 置 FAULT + REPAIR 安灯
        if (!overallPass) {
            long failCount = dto.getResults().stream().filter(r -> !Boolean.TRUE.equals(r.getPass())).count();
            String reason = String.format("点检不合格: 设备[%s(%s)] 不合格项[%d]项",
                equipment.getEquipmentName(), equipment.getEquipmentCode(), failCount);
            try {
                equipmentService.updateStatus(dto.getEquipmentId(), STATUS_FAULT, "点检不合格");
            } catch (Exception e) {
                log.warn("点检不合格置 FAULT 失败(降级): equipmentId={}, error={}", dto.getEquipmentId(), e.getMessage());
            }
            try {
                andonService.call("REPAIR", reason, station, operator.code(), null, null);
            } catch (Exception e) {
                log.warn("点检不合格触发安灯失败(降级): equipmentId={}, error={}", dto.getEquipmentId(), e.getMessage());
            }
        }
        log.info("工位点检提交: station={}, equipment={}, overallPass={}, recordId={}",
            station, equipment.getEquipmentCode(), overallPass, record.getRecordId());

        Map<String, Object> data = new LinkedHashMap<>(2);
        data.put("recordId", record.getRecordId());
        data.put("overallPass", overallPass);
        return data;
    }

    // ==================== 辅助 ====================

    /** 当日最新点检记录(inspect_time >= 今日 0 点) */
    private EqInspectionRecord latestTodayRecord(Long equipmentId) {
        Date dayStart = DateUtil.beginOfDay(new Date());
        return inspectionRecordMapper.selectOne(Wrappers.<EqInspectionRecord>lambdaQuery()
            .eq(EqInspectionRecord::getEquipmentId, equipmentId)
            .ge(EqInspectionRecord::getInspectTime, dayStart)
            .orderByDesc(EqInspectionRecord::getInspectTime)
            .last("LIMIT 1"));
    }

    /**
     * 解析点检项配置: 兼容新旧两种格式 ——
     * 新 [{"item":"油位","standard":"正常"}](plus-ui 录入) 与
     * 旧 [{"name":"主轴温度","unit":"℃","threshold":80}](SQL 种子数据, name/threshold 兜底映射)。
     */
    private List<Map<String, Object>> parseInspectionItems(String json) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (StringUtils.isBlank(json)) {
            return items;
        }
        try {
            List<Map<String, Object>> raw = JSON_MAPPER.readValue(json, new TypeReference<>() {
            });
            for (Map<String, Object> m : raw) {
                String item = str(m.get("item"));
                if (StringUtils.isBlank(item)) {
                    item = str(m.get("name"));
                }
                if (StringUtils.isBlank(item)) {
                    continue;
                }
                String standard = str(m.get("standard"));
                if (StringUtils.isBlank(standard)) {
                    Object threshold = m.get("threshold");
                    if (threshold != null) {
                        String unit = str(m.get("unit"));
                        standard = threshold + (StringUtils.isNotBlank(unit) ? unit : "");
                    }
                }
                Map<String, Object> row = new LinkedHashMap<>(2);
                row.put("item", item);
                row.put("standard", standard);
                items.add(row);
            }
        } catch (Exception e) {
            log.warn("点检项配置解析失败(忽略): {}", e.getMessage());
        }
        return items;
    }

    private String toJson(Object value) {
        try {
            return JSON_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new ServiceException("JSON 序列化失败: " + e.getMessage());
        }
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private Long toLong(Object v) {
        return v == null ? null : Long.valueOf(String.valueOf(v));
    }
}
