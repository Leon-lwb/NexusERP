package com.nexus.iot.event.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.iot.dto.IotAlert;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.iot.alert.domain.IotAlertConstants;
import com.nexus.iot.alert.service.IIotAlertRecordService;
import com.nexus.iot.device.domain.IotDevice;
import com.nexus.iot.device.domain.IotDeviceData;
import com.nexus.iot.event.domain.IotDeviceEvent;
import com.nexus.iot.event.domain.bo.IotDeviceEventBo;
import com.nexus.iot.event.mapper.IotDeviceEventMapper;
import com.nexus.iot.event.service.IIotDeviceEventService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * IoT设备事件Service业务层处理（IOT-PIPE-02）。
 * <p>
 * 处理状态机：新事件落库为 PENDING，人工在事件页标记 PROCESSED/IGNORED（CAS）；
 * 告警类事件（{@link IotDeviceEvent#isAlarmEvent}）在落库后联动 {@code iot_alert_record}
 * （来源 SOURCE_EVENT），使"设备主动上报的故障"与"属性越限"统一进入告警中心——
 * 这是轻量联动，不是完整规则引擎。
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("iot")
@Service
public class IotDeviceEventServiceImpl implements IIotDeviceEventService {

    /** 处理状态：待处理 */
    public static final String STATUS_PENDING = "PENDING";
    /** 处理状态：已处理 */
    public static final String STATUS_PROCESSED = "PROCESSED";
    /** 处理状态：已忽略 */
    public static final String STATUS_IGNORED = "IGNORED";
    /** 处理状态：处理失败 */
    public static final String STATUS_FAILED = "FAILED";

    private final IotDeviceEventMapper baseMapper;
    private final IIotAlertRecordService alertRecordService;

    /**
     * 分页查询设备事件。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 事件分页结果
     */
    @Override
    public TableDataInfo<IotDeviceEvent> selectPageList(IotDeviceEventBo bo, PageQuery pageQuery) {
        Page<IotDeviceEvent> page = baseMapper.selectPage(pageQuery.build(), buildQueryWrapper(bo));
        return TableDataInfo.build(page);
    }

    /**
     * 查询设备事件列表（不分页）。
     *
     * @param bo 查询条件
     * @return 事件列表
     */
    @Override
    public List<IotDeviceEvent> selectList(IotDeviceEventBo bo) {
        return baseMapper.selectList(buildQueryWrapper(bo));
    }

    /**
     * 查询事件详情。
     *
     * @param eventId 事件ID
     * @return 事件实体，不存在返回 null
     */
    @Override
    public IotDeviceEvent selectById(Long eventId) {
        return eventId == null ? null : baseMapper.selectById(eventId);
    }

    /**
     * 标记事件处理状态（CAS）。
     *
     * @param eventId       事件ID
     * @param processStatus 目标状态
     * @param remark        处理说明
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markProcessed(Long eventId, String processStatus, String remark) {
        if (eventId == null) {
            throw new ServiceException("事件ID不能为空");
        }
        if (!STATUS_PROCESSED.equals(processStatus) && !STATUS_IGNORED.equals(processStatus)
            && !STATUS_FAILED.equals(processStatus)) {
            throw new ServiceException("非法的处理状态：" + processStatus + "（仅支持 PROCESSED/IGNORED/FAILED）");
        }
        IotDeviceEvent event = baseMapper.selectById(eventId);
        if (event == null) {
            throw new ServiceException("事件不存在：" + eventId);
        }
        int rows = baseMapper.update(null, Wrappers.<IotDeviceEvent>lambdaUpdate()
            .eq(IotDeviceEvent::getEventId, eventId)
            .eq(IotDeviceEvent::getProcessStatus, event.getProcessStatus())
            .set(IotDeviceEvent::getProcessStatus, processStatus)
            .set(IotDeviceEvent::getProcessRemark, StringUtils.substring(remark, 0, 500)));
        if (rows == 0) {
            throw new ServiceException("事件状态变更失败：状态已被其他操作变更，请刷新后重试");
        }
        return true;
    }

    /**
     * 消费端入口：落库事件 + 告警联动（幂等）。
     *
     * @param device   设备记录
     * @param entry    事件流 entry
     * @param streamId Stream entry id
     * @return 事件ID；重复消息返回 null
     */
    @Override
    public Long persistEvent(IotDevice device, Map<Object, Object> entry, String streamId) {
        if (device == null || entry == null || StringUtils.isBlank(device.getTenantId())) {
            return null;
        }
        return TenantHelper.dynamic(device.getTenantId(), () -> doPersist(device, entry, streamId));
    }

    // ------------------------------------------------------------------ 内部实现

    /**
     * 落库主体：组装实体 → insert（唯一键幂等）→ 告警联动。
     */
    private Long doPersist(IotDevice device, Map<Object, Object> entry, String streamId) {
        String eventCode = str(entry.get("event_code"));
        if (StringUtils.isBlank(eventCode)) {
            log.warn("事件缺少事件编码，跳过：deviceCode={}", device.getDeviceCode());
            return null;
        }
        String eventLevel = str(entry.get("event_level"));
        IotDeviceEvent entity = new IotDeviceEvent();
        entity.setTenantId(device.getTenantId());
        entity.setDeviceId(device.getDeviceId());
        entity.setDeviceCode(device.getDeviceCode());
        entity.setProductId(device.getProductId());
        entity.setEventCode(eventCode);
        entity.setEventName(StringUtils.substring(str(entry.get("event_name")), 0, 128));
        entity.setEventLevel(StringUtils.substring(eventLevel, 0, 16));
        entity.setEventTime(toDate(entry.get("event_time")));
        entity.setReceiveTime(toDate(entry.get("receive_time")));
        entity.setPayload(str(entry.get("payload")));
        entity.setQuality(StringUtils.isBlank(str(entry.get("quality"))) ? "GOOD" : str(entry.get("quality")));
        entity.setProcessStatus(STATUS_PENDING);
        entity.setSource(StringUtils.isBlank(str(entry.get("source"))) ? "MQTT" : str(entry.get("source")));
        entity.setSourceMsgId(streamId);
        entity.setCreateTime(new Date());
        try {
            baseMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            // Stream 重投（XACK 丢失等）→ 视为已处理，避免重复事件与重复告警
            log.info("设备事件重复（唯一键冲突），按已处理跳过：streamId={}, device={}, eventCode={}",
                streamId, device.getDeviceCode(), eventCode);
            return null;
        }
        log.info("设备事件已落库：eventId={}, device={}, eventCode={}, level={}",
            entity.getEventId(), device.getDeviceCode(), eventCode, eventLevel);
        // 告警联动（轻量）：仅"告警类"事件进告警中心，其余事件仅留档
        if (IotDeviceEvent.isAlarmEvent(eventCode, eventLevel)) {
            linkAlert(entity);
        }
        return entity.getEventId();
    }

    /**
     * 把告警类事件联动写入 {@code iot_alert_record}（来源 EVENT），并回写 alert_id。
     * <p>
     * 复用告警记录服务的"同设备+属性唯一未恢复告警"幂等语义：以事件编码作为 propertyCode，
     * 同一事件反复上报只会合并更新而不会刷屏；阈值/比较符为空，因此不会被"属性恢复正常"自动恢复。
     */
    private void linkAlert(IotDeviceEvent event) {
        try {
            IotAlert alert = new IotAlert();
            alert.setTenantId(event.getTenantId());
            alert.setDeviceId(event.getDeviceId());
            alert.setDeviceCode(event.getDeviceCode());
            alert.setAlertType("EVENT");
            alert.setAlertLevel(normalizeLevel(event.getEventLevel()));
            alert.setAlertMessage(StringUtils.substring(
                "设备事件[" + event.getEventCode() + "]"
                    + (StringUtils.isNotBlank(event.getEventName()) ? " " + event.getEventName() : ""), 0, 500));
            IotDeviceData shim = new IotDeviceData();
            shim.setTenantId(event.getTenantId());
            shim.setDeviceId(event.getDeviceId());
            shim.setDeviceCode(event.getDeviceCode());
            shim.setPropertyCode(event.getEventCode());
            Long alertId = alertRecordService.trigger(null, shim, alert, IotAlertConstants.SOURCE_EVENT);
            if (alertId != null) {
                baseMapper.update(null, Wrappers.<IotDeviceEvent>lambdaUpdate()
                    .eq(IotDeviceEvent::getEventId, event.getEventId())
                    .set(IotDeviceEvent::getAlertId, alertId));
                event.setAlertId(alertId);
            }
        } catch (Exception e) {
            // 告警联动失败不得影响事件落库结果（事件本身已持久化）
            log.warn("设备事件联动告警失败：eventId={}, device={}, err={}",
                event.getEventId(), event.getDeviceCode(), e.getMessage());
        }
    }

    /**
     * 事件级别 → 告警级别映射（告警表级别为 HIGH/MEDIUM/LOW 三档）。
     */
    private String normalizeLevel(String eventLevel) {
        if (StringUtils.isBlank(eventLevel)) {
            return "MEDIUM";
        }
        String lv = eventLevel.toUpperCase();
        if (lv.contains("CRITICAL") || lv.contains("FATAL") || lv.contains("ERROR") || lv.contains("HIGH")) {
            return "HIGH";
        }
        if (lv.contains("WARN") || lv.contains("MEDIUM")) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private LambdaQueryWrapper<IotDeviceEvent> buildQueryWrapper(IotDeviceEventBo bo) {
        LambdaQueryWrapper<IotDeviceEvent> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getEventId() != null, IotDeviceEvent::getEventId, bo.getEventId());
        lqw.eq(bo.getDeviceId() != null, IotDeviceEvent::getDeviceId, bo.getDeviceId());
        lqw.like(StringUtils.isNotBlank(bo.getDeviceCode()), IotDeviceEvent::getDeviceCode, bo.getDeviceCode());
        lqw.eq(bo.getProductId() != null, IotDeviceEvent::getProductId, bo.getProductId());
        lqw.like(StringUtils.isNotBlank(bo.getEventCode()), IotDeviceEvent::getEventCode, bo.getEventCode());
        lqw.eq(StringUtils.isNotBlank(bo.getEventLevel()), IotDeviceEvent::getEventLevel, bo.getEventLevel());
        lqw.eq(StringUtils.isNotBlank(bo.getProcessStatus()), IotDeviceEvent::getProcessStatus, bo.getProcessStatus());
        lqw.isNotNull(Boolean.TRUE.equals(bo.getHasAlert()), IotDeviceEvent::getAlertId);
        lqw.ge(StringUtils.isNotBlank(bo.getBeginTime()), IotDeviceEvent::getEventTime, bo.getBeginTime());
        lqw.le(StringUtils.isNotBlank(bo.getEndTime()), IotDeviceEvent::getEventTime, bo.getEndTime());
        lqw.orderByDesc(IotDeviceEvent::getEventTime);
        return lqw;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private Date toDate(Object o) {
        if (o == null) {
            return new Date();
        }
        try {
            return new Date(Long.parseLong(String.valueOf(o)));
        } catch (NumberFormatException e) {
            return new Date();
        }
    }
}
