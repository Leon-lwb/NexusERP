package com.nexus.iot.common;

import com.nexus.common.iot.dto.IotAlert;
import com.nexus.common.iot.service.IMesAndonService;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link IMesAndonService} 默认 no-op 实现。
 * <p>
 * MES 未接入时由 IoT 侧提供，避免 {@code IotAlertNotifier} 注入失败。
 * 通过 {@code IotConfiguration} 以 {@code @Bean @ConditionalOnMissingBean} 注册，
 * 确保 MES Phase 2 在 {@code ruoyi-mes} 提供 {@code @Primary @Service} 真实实现后能可靠让位
 * （@Configuration @Bean 的条件求值晚于组件扫描，可稳定感知 MES 的 Bean）。
 *
 * @author nexus
 */
@Slf4j
public class IotMesAndonServiceNoop implements IMesAndonService {

    /**
     * 默认实现：不联动安灯，记录告警日志并返回 null（MES 未接入时的兜底）。
     *
     * @param alert 告警信息
     * @return 始终返回 null，表示未创建安灯
     */
    @Override
    public Long createAndonFromAlert(IotAlert alert) {
        log.warn("MES 安灯接口未实现，IoT 告警已忽略：device={}, type={}",
            alert.getDeviceCode(), alert.getAlertType());
        return null;
    }

}
