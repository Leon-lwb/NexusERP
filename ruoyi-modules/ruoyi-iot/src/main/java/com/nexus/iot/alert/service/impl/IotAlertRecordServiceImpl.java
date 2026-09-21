package com.nexus.iot.alert.service.impl;

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
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.iot.alert.domain.IotAlertCondition;
import com.nexus.iot.alert.domain.IotAlertConstants;
import com.nexus.iot.alert.domain.IotAlertNotifyLog;
import com.nexus.iot.alert.domain.IotAlertRecord;
import com.nexus.iot.alert.domain.IotAlertRule;
import com.nexus.iot.alert.domain.IotAlertStatus;
import com.nexus.iot.alert.domain.bo.IotAlertRecordBo;
import com.nexus.iot.alert.domain.vo.IotAlertNotifyLogVo;
import com.nexus.iot.alert.domain.vo.IotAlertRecordVo;
import com.nexus.iot.alert.mapper.IotAlertNotifyLogMapper;
import com.nexus.iot.alert.mapper.IotAlertRecordMapper;
import com.nexus.iot.alert.service.IIotAlertRecordService;
import com.nexus.iot.device.domain.IotDeviceData;
import com.nexus.iot.pipeline.IotAlertNotifier;
import com.nexus.iot.pipeline.IotAlertStateStore;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Date;
import java.util.List;

/**
 * IoT告警记录Service业务层处理（IOT-ALERT-01）。
 * <p>
 * 落库 → 去抖 → 通知 → 投递留痕的完整链路；消费线程无登录上下文，
 * 所有写库都在 {@link TenantHelper#dynamic} 中执行，避免 tenant_id 落 0 / 全租户串读。
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("iot")
@Service
public class IotAlertRecordServiceImpl implements IIotAlertRecordService {

    /** 未恢复标记值（唯一键组成） */
    private static final int ACTIVE_FLAG = 1;
    /** 默认去抖窗口（秒），与旧限频窗口一致 */
    private static final int DEFAULT_DEBOUNCE_SEC = 60;

    private final IotAlertRecordMapper baseMapper;
    private final IotAlertNotifyLogMapper notifyLogMapper;
    private final IotAlertNotifier alertNotifier;
    private final IotAlertStateStore alertStateStore;

    /**
     * 分页查询告警记录。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 告警分页结果
     */
    @Override
    public TableDataInfo<IotAlertRecordVo> selectPageList(IotAlertRecordBo bo, PageQuery pageQuery) {
        Page<IotAlertRecordVo> page = baseMapper.selectVoPage(pageQuery.build(), buildQueryWrapper(bo));
        return TableDataInfo.build(page);
    }

    /**
     * 查询告警记录列表（不分页）。
     *
     * @param bo 查询条件
     * @return 告警列表
     */
    @Override
    public List<IotAlertRecordVo> selectList(IotAlertRecordBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 查询告警详情。
     *
     * @param alertId 告警ID
     * @return 告警VO，不存在返回 null
     */
    @Override
    public IotAlertRecordVo selectById(Long alertId) {
        return baseMapper.selectVoById(alertId);
    }

    /**
     * 查询某条告警的通知投递留痕。
     *
     * @param alertId 告警ID
     * @return 投递留痕列表
     */
    @Override
    public List<IotAlertNotifyLogVo> selectNotifyLogs(Long alertId) {
        LambdaQueryWrapper<IotAlertNotifyLog> lqw = Wrappers.lambdaQuery();
        lqw.eq(IotAlertNotifyLog::getAlertId, alertId);
        lqw.orderByAsc(IotAlertNotifyLog::getLogId);
        return notifyLogMapper.selectVoList(lqw);
    }

    /**
     * 确认告警：ACTIVE → ACKNOWLEDGED（CAS）。
     *
     * @param alertId 告警ID
     * @param remark  确认备注
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean acknowledge(Long alertId, String remark) {
        IotAlertRecord record = requireRecord(alertId);
        if (!IotAlertStatus.canAcknowledge(record.getStatus())) {
            throw new ServiceException("告警状态非法流转：当前 " + record.getStatus() + " 不允许确认（仅 ACTIVE 可确认）");
        }
        Long userId = LoginHelper.getUserId();
        Date now = new Date();
        int rows = baseMapper.update(null, Wrappers.<IotAlertRecord>lambdaUpdate()
            .eq(IotAlertRecord::getAlertId, alertId)
            .eq(IotAlertRecord::getStatus, IotAlertStatus.ACTIVE)
            .set(IotAlertRecord::getStatus, IotAlertStatus.ACKNOWLEDGED)
            .set(IotAlertRecord::getAckTime, now)
            .set(IotAlertRecord::getAckBy, userId)
            .set(IotAlertRecord::getAckRemark, remark)
            .set(IotAlertRecord::getUpdateBy, userId)
            .set(IotAlertRecord::getUpdateTime, now));
        if (rows == 0) {
            throw new ServiceException("告警确认失败：状态已被其他操作变更");
        }
        return true;
    }

    /**
     * 恢复告警：ACTIVE|ACKNOWLEDGED → RESOLVED（CAS，清 active_flag 以允许再次触发新记录）。
     *
     * @param alertId      告警ID
     * @param resolveValue 恢复值
     * @param remark       恢复备注
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean resolve(Long alertId, BigDecimal resolveValue, String remark) {
        IotAlertRecord record = requireRecord(alertId);
        if (!IotAlertStatus.canResolve(record.getStatus())) {
            throw new ServiceException("告警状态非法流转：当前 " + record.getStatus() + " 不允许恢复（仅 ACTIVE/ACKNOWLEDGED 可恢复）");
        }
        Long userId = LoginHelper.getUserId();
        Date now = new Date();
        int rows = baseMapper.update(null, Wrappers.<IotAlertRecord>lambdaUpdate()
            .eq(IotAlertRecord::getAlertId, alertId)
            .in(IotAlertRecord::getStatus, List.of(IotAlertStatus.ACTIVE, IotAlertStatus.ACKNOWLEDGED))
            .set(IotAlertRecord::getStatus, IotAlertStatus.RESOLVED)
            .set(IotAlertRecord::getResolveTime, now)
            .set(IotAlertRecord::getResolveBy, userId)
            .set(IotAlertRecord::getResolveValue, resolveValue != null ? resolveValue : record.getTriggerValue())
            .set(IotAlertRecord::getResolveRemark, remark)
            .set(IotAlertRecord::getUpdateBy, userId)
            .set(IotAlertRecord::getUpdateTime, now)
            .setSql("active_flag = NULL"));
        if (rows == 0) {
            throw new ServiceException("告警恢复失败：状态已被其他操作变更");
        }
        clearDurationKey(record);
        return true;
    }

    /**
     * 消费端入口：触发/合并告警 + 落库 + 去抖通知 + 投递留痕。
     *
     * @param rule   命中规则（可为 null）
     * @param data   触发数据
     * @param alert  告警内容
     * @param source 来源
     * @return 落库后的告警ID（失败返回 null）
     */
    @Override
    public Long trigger(IotAlertRule rule, IotDeviceData data, IotAlert alert, String source) {
        if (data == null || data.getDeviceId() == null || StringUtils.isBlank(data.getPropertyCode())) {
            return null;
        }
        String tenantId = StringUtils.isNotBlank(alert.getTenantId()) ? alert.getTenantId() : data.getTenantId();
        if (StringUtils.isBlank(tenantId)) {
            log.warn("告警缺少租户ID，跳过落库：device={}, property={}", data.getDeviceCode(), data.getPropertyCode());
            return null;
        }
        return TenantHelper.dynamic(tenantId, () -> doTrigger(tenantId, rule, data, alert, source));
    }

    /**
     * 消费端入口：属性恢复正常时自动 RESOLVED。
     *
     * @param data 当前设备数据
     */
    @Override
    public void autoResolve(IotDeviceData data) {
        if (data == null || data.getDeviceId() == null || data.getNumericValue() == null
            || StringUtils.isBlank(data.getPropertyCode())) {
            return;
        }
        String tenantId = data.getTenantId();
        if (StringUtils.isBlank(tenantId)) {
            return;
        }
        TenantHelper.dynamic(tenantId, () -> {
            IotAlertRecord active = selectUnresolved(tenantId, data.getDeviceId(), data.getPropertyCode());
            if (active == null || active.getThreshold() == null) {
                return;
            }
            if (IotAlertCondition.breached(active.getOperator(), data.getNumericValue(), active.getThreshold())) {
                // 仍处于超限状态，保持未恢复
                return;
            }
            Date now = new Date();
            int rows = baseMapper.update(null, Wrappers.<IotAlertRecord>lambdaUpdate()
                .eq(IotAlertRecord::getAlertId, active.getAlertId())
                .eq(IotAlertRecord::getActiveFlag, ACTIVE_FLAG)
                .set(IotAlertRecord::getStatus, IotAlertStatus.RESOLVED)
                .set(IotAlertRecord::getResolveTime, now)
                .set(IotAlertRecord::getResolveValue, data.getNumericValue())
                .set(IotAlertRecord::getResolveRemark, "属性值恢复正常，系统自动恢复")
                .set(IotAlertRecord::getUpdateTime, now)
                .setSql("active_flag = NULL"));
            if (rows > 0) {
                clearDurationKey(active);
                log.info("IoT 告警已自动恢复：alertId={}, device={}, property={}, value={}, threshold={}",
                    active.getAlertId(), active.getDeviceCode(), active.getPropertyCode(),
                    data.getNumericValue(), active.getThreshold());
            }
        });
    }

    // ------------------------------------------------------------------ 内部实现

    /**
     * 落库主体：先查未恢复告警（合并），否则插入（唯一键兜底并发）。
     */
    private Long doTrigger(String tenantId, IotAlertRule rule, IotDeviceData data, IotAlert alert, String source) {
        Date now = new Date();
        IotAlertRecord active = selectUnresolved(tenantId, data.getDeviceId(), data.getPropertyCode());
        if (active != null) {
            merge(active.getAlertId(), alert, now);
            active.setTriggerValue(alert.getValue());
            active.setAlertLevel(alert.getAlertLevel());
            active.setAlertMessage(alert.getAlertMessage());
            log.debug("未恢复告警已存在，合并更新：alertId={}, device={}, property={}",
                active.getAlertId(), data.getDeviceCode(), data.getPropertyCode());
            notifyWithDebounce(tenantId, active, rule, alert);
            return active.getAlertId();
        }
        IotAlertRecord record = buildRecord(tenantId, rule, data, alert, source, now);
        try {
            baseMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 并发下另一线程已插入同一 (设备, 属性) 的未恢复告警 → 退化为合并，保证幂等
            IotAlertRecord concurrent = selectUnresolved(tenantId, data.getDeviceId(), data.getPropertyCode());
            if (concurrent == null) {
                throw e;
            }
            merge(concurrent.getAlertId(), alert, now);
            log.info("并发触发同一未恢复告警，退化为合并：alertId={}, device={}, property={}",
                concurrent.getAlertId(), data.getDeviceCode(), data.getPropertyCode());
            notifyWithDebounce(tenantId, concurrent, rule, alert);
            return concurrent.getAlertId();
        }
        log.warn("IoT 告警已落库：alertId={}, source={}, device={}, property={}, level={}, value={}, threshold={}",
            record.getAlertId(), source, record.getDeviceCode(), record.getPropertyCode(),
            record.getAlertLevel(), record.getTriggerValue(), record.getThreshold());
        notifyWithDebounce(tenantId, record, rule, alert);
        return record.getAlertId();
    }

    /**
     * 合并更新：保留首次触发时间与确认状态，仅刷新最近触发信息。
     */
    private void merge(Long alertId, IotAlert alert, Date now) {
        baseMapper.update(null, Wrappers.<IotAlertRecord>lambdaUpdate()
            .eq(IotAlertRecord::getAlertId, alertId)
            .eq(IotAlertRecord::getActiveFlag, ACTIVE_FLAG)
            .set(IotAlertRecord::getTriggerValue, alert.getValue())
            .set(IotAlertRecord::getLastTriggerTime, now)
            .set(IotAlertRecord::getAlertLevel, alert.getAlertLevel())
            .set(IotAlertRecord::getAlertMessage, alert.getAlertMessage())
            .set(IotAlertRecord::getUpdateTime, now));
    }

    /**
     * 去抖后推送，并逐渠道写入投递留痕（无在线用户/去抖抑制也会留痕，不再"仅落日志"）。
     */
    private void notifyWithDebounce(String tenantId, IotAlertRecord record, IotAlertRule rule, IotAlert alert) {
        int debounceSec = rule == null || rule.getDebounceSec() == null ? DEFAULT_DEBOUNCE_SEC : rule.getDebounceSec();
        if (debounceSec > 0) {
            String ruleKey = record.getRuleId() == null ? "default" : String.valueOf(record.getRuleId());
            String key = IotAlertStateStore.debounceKey(
                tenantId, record.getDeviceId(), record.getPropertyCode(), ruleKey);
            if (!alertStateStore.acquireDebounce(key, Duration.ofSeconds(debounceSec))) {
                writeNotifyLog(record, IotAlertConstants.CHANNEL_SSE, null,
                    IotAlertConstants.NOTIFY_SKIPPED, "去抖窗口内(" + debounceSec + "s)，仅合并落库不重复推送");
                return;
            }
        }
        List<IotAlertNotifier.ChannelOutcome> outcomes;
        try {
            outcomes = alertNotifier.notify(alert);
        } catch (Exception e) {
            writeNotifyLog(record, IotAlertConstants.CHANNEL_SSE, null,
                IotAlertConstants.NOTIFY_FAILED, "推送异常：" + e.getMessage());
            log.warn("告警推送异常：alertId={}, err={}", record.getAlertId(), e.getMessage());
            return;
        }
        if (outcomes == null) {
            return;
        }
        for (IotAlertNotifier.ChannelOutcome outcome : outcomes) {
            writeNotifyLog(record, outcome.channel(), outcome.target(), outcome.status(), outcome.message());
        }
    }

    /**
     * 写投递留痕，失败仅记日志不阻断告警主流程。
     */
    private void writeNotifyLog(IotAlertRecord record, String channel, String target, String status, String message) {
        try {
            IotAlertNotifyLog entity = new IotAlertNotifyLog();
            entity.setTenantId(record.getTenantId());
            entity.setAlertId(record.getAlertId());
            entity.setChannel(channel);
            entity.setTarget(target);
            entity.setStatus(status);
            entity.setMessage(StringUtils.substring(message, 0, 500));
            notifyLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("告警投递留痕写入失败：alertId={}, channel={}, err={}", record.getAlertId(), channel, e.getMessage());
        }
    }

    /**
     * 查询 (租户, 设备, 属性) 的未恢复告警（唯一键保证最多一条）。
     */
    private IotAlertRecord selectUnresolved(String tenantId, Long deviceId, String propertyCode) {
        LambdaQueryWrapper<IotAlertRecord> lqw = Wrappers.lambdaQuery();
        lqw.eq(IotAlertRecord::getTenantId, tenantId);
        lqw.eq(IotAlertRecord::getDeviceId, deviceId);
        lqw.eq(IotAlertRecord::getPropertyCode, propertyCode);
        lqw.eq(IotAlertRecord::getActiveFlag, ACTIVE_FLAG);
        lqw.orderByDesc(IotAlertRecord::getTriggerTime);
        lqw.last("limit 1");
        return baseMapper.selectOne(lqw);
    }

    /**
     * 组装告警记录（阈值/比较符快照，供规则被改删后仍可判定恢复）。
     */
    private IotAlertRecord buildRecord(String tenantId, IotAlertRule rule, IotDeviceData data,
                                       IotAlert alert, String source, Date now) {
        IotAlertRecord record = new IotAlertRecord();
        record.setTenantId(tenantId);
        record.setRuleId(rule == null ? null : rule.getRuleId());
        record.setSource(StringUtils.isNotBlank(source) ? source : IotAlertConstants.SOURCE_RULE);
        record.setDeviceId(data.getDeviceId());
        record.setDeviceCode(data.getDeviceCode());
        record.setProductId(rule != null && rule.getProductId() != null ? rule.getProductId() : 0L);
        record.setPropertyCode(data.getPropertyCode());
        record.setAlertType(StringUtils.isNotBlank(alert.getAlertType()) ? alert.getAlertType() : "OTHER");
        record.setAlertLevel(StringUtils.isNotBlank(alert.getAlertLevel()) ? alert.getAlertLevel() : "MEDIUM");
        record.setOperator(rule == null ? IotAlertCondition.GT : IotAlertCondition.normalizeOperator(rule.getOperator()));
        record.setThreshold(alert.getThreshold());
        record.setTriggerValue(alert.getValue());
        record.setAlertMessage(StringUtils.substring(alert.getAlertMessage(), 0, 500));
        record.setStatus(IotAlertStatus.ACTIVE);
        record.setActiveFlag(ACTIVE_FLAG);
        record.setTriggerTime(now);
        record.setLastTriggerTime(now);
        return record;
    }

    /**
     * 清理持续时长窗口键，使同一异常再次出现时重新计时。
     */
    private void clearDurationKey(IotAlertRecord record) {
        String ruleKey = record.getRuleId() == null ? "default" : String.valueOf(record.getRuleId());
        alertStateStore.clearDuration(IotAlertStateStore.durationKey(
            record.getTenantId(), record.getDeviceId(), record.getPropertyCode(), ruleKey));
    }

    private IotAlertRecord requireRecord(Long alertId) {
        if (alertId == null) {
            throw new ServiceException("告警ID不能为空");
        }
        IotAlertRecord record = baseMapper.selectById(alertId);
        if (record == null) {
            throw new ServiceException("告警不存在");
        }
        return record;
    }

    private LambdaQueryWrapper<IotAlertRecord> buildQueryWrapper(IotAlertRecordBo bo) {
        LambdaQueryWrapper<IotAlertRecord> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getAlertId() != null, IotAlertRecord::getAlertId, bo.getAlertId());
        lqw.eq(bo.getDeviceId() != null, IotAlertRecord::getDeviceId, bo.getDeviceId());
        lqw.like(StringUtils.isNotBlank(bo.getDeviceCode()), IotAlertRecord::getDeviceCode, bo.getDeviceCode());
        lqw.eq(bo.getProductId() != null, IotAlertRecord::getProductId, bo.getProductId());
        lqw.like(StringUtils.isNotBlank(bo.getPropertyCode()), IotAlertRecord::getPropertyCode, bo.getPropertyCode());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), IotAlertRecord::getStatus, bo.getStatus());
        lqw.eq(StringUtils.isNotBlank(bo.getAlertLevel()), IotAlertRecord::getAlertLevel, bo.getAlertLevel());
        lqw.eq(StringUtils.isNotBlank(bo.getAlertType()), IotAlertRecord::getAlertType, bo.getAlertType());
        lqw.eq(StringUtils.isNotBlank(bo.getSource()), IotAlertRecord::getSource, bo.getSource());
        lqw.ge(StringUtils.isNotBlank(bo.getBeginTime()), IotAlertRecord::getTriggerTime, bo.getBeginTime());
        lqw.le(StringUtils.isNotBlank(bo.getEndTime()), IotAlertRecord::getTriggerTime, bo.getEndTime());
        lqw.orderByDesc(IotAlertRecord::getTriggerTime);
        return lqw;
    }

}
