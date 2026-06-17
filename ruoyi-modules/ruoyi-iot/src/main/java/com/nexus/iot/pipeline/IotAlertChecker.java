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
 *
 * @author nexus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IotAlertChecker {

    private final IotPipelineProperties properties;
    private final IotAlertNotifier alertNotifier;

    /**
     * 检查单条设备数据是否触发告警。
     *
     * @param data 已落库的设备数据
     */
    public void check(IotDeviceData data) {
        if (data == null || data.getNumericValue() == null || data.getPropertyCode() == null) {
            return;
        }
        Map<String, BigDecimal> thresholds = properties.getAlertThresholds();
        if (thresholds == null || thresholds.isEmpty()) {
            return;
        }
        BigDecimal threshold = thresholds.get(data.getPropertyCode());
        if (threshold == null) {
            return;
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
        }
    }

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
