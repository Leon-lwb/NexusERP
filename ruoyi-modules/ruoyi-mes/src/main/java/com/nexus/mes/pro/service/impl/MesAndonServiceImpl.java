package com.nexus.mes.pro.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.iot.dto.IotAlert;
import com.nexus.common.iot.service.IMesAndonService;
import com.nexus.common.tenant.helper.TenantHelper;
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
 * 覆盖 IoT 侧 {@code IotMesAndonServiceNoop}（{@code @ConditionalOnMissingBean} 在本 bean 存在时自动退场）。
 * <p>
 * IoT 告警线程无 Sa-Token ThreadLocal，故用 {@link TenantHelper#dynamic(String, java.util.function.Supplier)}
 * 注入 {@code alert.tenantId}，确保多租户插件写入正确 tenant_id。
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
    public Long createAndonFromAlert(IotAlert alert) {
        // 设备告警 -> EQUIPMENT 类型安灯, 无工单/流转卡上下文
        String reason = String.format("IoT设备告警[%s]: %s (值=%s, 阈值=%s)",
            alert.getAlertType(), alert.getAlertMessage(),
            alert.getValue(), alert.getThreshold());
        try {
            // IoT 异步线程无 Sa-Token 上下文, 用 TenantHelper.dynamic 注入 alert.tenantId
            ProAndonVo vo = TenantHelper.dynamic(alert.getTenantId(),
                () -> proAndonService.callAndon(null, null, null,
                    IProAndonService.TYPE_EQUIPMENT, reason));
            log.info("IoT告警触发安灯: alertDevice={}, andonId={}", alert.getDeviceCode(), vo.getAndonId());
            return vo.getAndonId();
        } catch (Exception e) {
            log.error("IoT告警触发安灯失败: alertDevice={}, type={}", alert.getDeviceCode(), alert.getAlertType(), e);
            return null;
        }
    }
}
