package com.nexus.common.iot.service;

import com.nexus.common.iot.dto.IotAlert;

/**
 * MES 安灯服务接口（IoT 调用 MES）。
 * <p>
 * 跨模块契约：IoT 发现设备异常时，通过此接口触发 MES 安灯呼叫。
 * <ul>
 *   <li>实现方：未来 {@code ruoyi-mes} 模块以 {@code @Primary @Service} 提供真实实现；</li>
 *   <li>默认方：{@code ruoyi-iot} 提供 {@code @ConditionalOnMissingBean} 的 no-op 实现，MES 未接入时避免启动报错。</li>
 * </ul>
 * 两个业务模块仅依赖本公共契约模块，互不依赖。
 *
 * @author nexus
 */
public interface IMesAndonService {

    /**
     * 从 IoT 告警创建 MES 安灯呼叫。
     *
     * @param alert IoT 告警信息
     * @return 安灯ID；MES 未实现时返回 null
     */
    Long createAndonFromAlert(IotAlert alert);

}
