package com.nexus.mes.pro.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.iot.dto.IotAlert;
import com.nexus.common.iot.service.IMesAndonService;
import com.nexus.mes.pro.domain.vo.ProAndonVo;
import com.nexus.mes.pro.service.IProAndonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MES 安灯服务实现（IoT 调用 MES 的跨模块契约）。
 * <p>
 * 实现 {@link IMesAndonService}，由 {@code ruoyi-mes} 以 {@code @Primary @Service} 提供真实实现，
 * 覆盖 IoT 侧 {@code IotMesAndonServiceNoop}（{@code @ConditionalOnMissingBean} 在本组件存在时自动退场）。
 * <p>
 * IoT 告警线程无 Sa-Token ThreadLocal，由 {@link IProAndonService#callIotAndon(IotAlert)}
 * 验证并注入 {@code alert.tenantId}，确保首次插入的来源及通知租户正确。
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Primary
@Service
public class MesAndonServiceImpl implements IMesAndonService {

    private final IProAndonService proAndonService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 创建
     * @param alert IoT 告警
     * @return 受影响行数
     */
    public Long createAndonFromAlert(IotAlert alert) {
        if (alert == null) {
            log.warn("IoT告警为空, 跳过安灯联动");
            return null;
        }
        try {
            ProAndonVo vo = proAndonService.callIotAndon(alert);
            log.info("IoT告警触发安灯: alertDevice={}, andonId={}", alert.getDeviceCode(), vo.getAndonId());
            return vo.getAndonId();
        } catch (Exception e) {
            log.error("IoT告警触发安灯失败: alertDevice={}, type={}", alert.getDeviceCode(), alert.getAlertType(), e);
            return null;
        }
    }
}
