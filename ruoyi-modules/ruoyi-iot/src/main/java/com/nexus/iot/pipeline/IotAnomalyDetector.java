package com.nexus.iot.pipeline;

import com.nexus.common.iot.dto.IotAlert;
import com.nexus.common.iot.util.EwmaUtil;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.iot.alert.domain.IotAlertConstants;
import com.nexus.iot.alert.service.IIotAlertRecordService;
import com.nexus.iot.device.domain.IotDeviceData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * IoT 数据 EWMA 统计异常检测器（预测性维护-实时侧）
 * <p>
 * 与 {@link IotAlertChecker}(固定阈值) 并列：无需预设阈值，为每个 设备×属性 维护
 * EWMA 均值/方差在线估计（Redis 滚动状态，TTL 7 天），
 * 新点偏离 mean 超过 z 倍标准差即触发 ANOMALY 告警，经 {@link IIotAlertRecordService}
 * 落库 + 去抖 + 安灯/SSE 推送（IOT-ALERT-01：异常告警同样不再"仅落日志"）。
 * <p>
 * 与阈值告警的互斥抑制：同一数据点已触发阈值告警时，由调用方
 * {@link IotStreamConsumer#afterPersist} 传入 {@code suppressNotify=true}，
 * 此时 EWMA 状态照常更新（保持基线连续），但不再重复推送 ANOMALY，
 * 避免同一尖峰同时产生 阈值告警 + ANOMALY 双告警/双安灯。
 * <p>
 * 适用于缓慢漂移类退化（温度爬升、振动增大等固定阈值难以覆盖的场景）。
 *
 * @author nexus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IotAnomalyDetector {

    private static final String STATE_KEY_PREFIX = "iot:ewma:";
    private static final Duration STATE_TTL = Duration.ofDays(7);

    /** 管道配置属性。 */
    private final IotPipelineProperties properties;
    /** 告警记录服务(落库 + 去抖通知 + 投递留痕)。 */
    private final IIotAlertRecordService alertRecordService;

    /**
     * 检测单条设备数据：更新 EWMA 状态，并在 z-score 超阈值且未被抑制时推送异常告警。
     *
     * @param data            已落库的设备数据(仅数值型参与)
     * @param suppressNotify  true 时仅更新 EWMA 状态、不推送告警(该数据点已由阈值检查器告警)
     */
    public void detect(IotDeviceData data, boolean suppressNotify) {
        if (data == null || data.getNumericValue() == null || data.getDeviceId() == null || data.getPropertyCode() == null) {
            return;
        }
        IotPipelineProperties.Anomaly anomaly = properties.getAnomaly();
        if (anomaly == null || !anomaly.isEnabled()) {
            return;
        }
        String key = STATE_KEY_PREFIX + data.getDeviceId() + ":" + data.getPropertyCode();
        try {
            EwmaUtil.EwmaState state = RedisUtils.getCacheObject(key);
            BigDecimal z = EwmaUtil.zScore(state, data.getNumericValue());
            EwmaUtil.EwmaState updated = EwmaUtil.update(state, data.getNumericValue(), anomaly.getLambda());
            RedisUtils.setCacheObject(key, updated, STATE_TTL);

            if (!suppressNotify
                && EwmaUtil.warmedUp(state, anomaly.getWarmup()) && z.compareTo(BigDecimal.valueOf(anomaly.getZWarn())) > 0) {
                String level = z.compareTo(BigDecimal.valueOf(anomaly.getZHigh())) > 0 ? "HIGH" : "MEDIUM";
                IotAlert alert = new IotAlert();
                alert.setDeviceId(data.getDeviceId());
                alert.setDeviceCode(data.getDeviceCode());
                alert.setAlertType("ANOMALY");
                alert.setAlertLevel(level);
                alert.setAlertMessage(String.format("设备 %s 属性 %s 统计异常：当前 %s 偏离基线 %s 个标准差(z=%.2f)",
                    data.getDeviceCode(), data.getPropertyCode(), data.getNumericValue(),
                    state.mean().setScale(2, java.math.RoundingMode.HALF_UP), z));
                alert.setValue(data.getNumericValue());
                alert.setThreshold(state.mean());
                alert.setTenantId(data.getTenantId());
                alertRecordService.trigger(null, data, alert, IotAlertConstants.SOURCE_ANOMALY);
            }
        } catch (Exception e) {
            // 异常检测失败不阻断数据管道
            log.warn("EWMA异常检测失败 deviceId={} property={}: {}", data.getDeviceId(), data.getPropertyCode(), e.getMessage());
        }
    }
}
