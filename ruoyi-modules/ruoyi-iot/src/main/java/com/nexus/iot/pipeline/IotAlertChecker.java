package com.nexus.iot.pipeline;

import com.nexus.common.iot.dto.IotAlert;
import com.nexus.iot.device.domain.IotDeviceData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * IoT 硬编码告警阈值检查器（第一版）。
 * <p>
 * 阈值来自 {@code nexus.iot.pipeline.alert-thresholds}（propertyCode → 阈值），
 * numericValue 超过阈值即构造 {@link IotAlert} 交 {@link IotAlertNotifier} 推送。
 * 告警规则表 / 多条件组合留待阶段二规则引擎。
 * <p>
 * {@link #check} 返回是否触发告警，供调用方 {@link IotStreamConsumer#afterPersist}
 * 对同一数据点的 EWMA ANOMALY 告警做互斥抑制（阈值已告警则不再发 ANOMALY）。
 *
 * @author nexus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IotAlertChecker {

    /** 管道配置属性，提供告警阈值映射。 */
    private final IotPipelineProperties properties;
    /** 告警通知器，负责限频推送与 MES 安灯联动。 */
    private final IotAlertNotifier alertNotifier;

    /**
     * 检查单条设备数据是否触发告警。
     *
     * @param data 已落库的设备数据
     * @return true 表示该数据点已触发阈值告警
     */
    public boolean check(IotDeviceData data) {
        if (data == null || data.getNumericValue() == null || data.getPropertyCode() == null) {
            return false;
        }
        Map<String, BigDecimal> thresholds = properties.getAlertThresholds();
        if (thresholds == null || thresholds.isEmpty()) {
            return false;
        }
        BigDecimal threshold = thresholds.get(data.getPropertyCode());
        if (threshold == null) {
            return false;
        }
        if (data.getNumericValue().compareTo(threshold) > 0) {
            IotAlert alert = new IotAlert();
            alert.setDeviceId(data.getDeviceId());
            alert.setDeviceCode(data.getDeviceCode());
            alert.setAlertType(resolveAlertType(data.getPropertyCode()));
            alert.setAlertLevel("HIGH");
            alert.setAlertMessage(String.format("设备 %s 属性 %s 超阈值：当前 %s > 阈值 %s",
                data.getDeviceCode(), data.getPropertyCode(), data.getNumericValue(), threshold));
            alert.setValue(data.getNumericValue());
            alert.setThreshold(threshold);
            alert.setTenantId(data.getTenantId());
            alertNotifier.notify(alert);
            return true;
        }
        return false;
    }

    /**
     * 按属性编码推断告警类型：含 temp→温度、vib→振动、press→压力，否则归为 OTHER。
     *
     * @param propertyCode 属性编码
     * @return 告警类型枚举字符串
     */
    private String resolveAlertType(String propertyCode) {
        String lower = propertyCode == null ? "" : propertyCode.toLowerCase();
        if (lower.contains("temp")) {
            return "TEMPERATURE";
        }
        if (lower.contains("vib")) {
            return "VIBRATION";
        }
        if (lower.contains("press")) {
            return "PRESSURE";
        }
        return "OTHER";
    }

}
