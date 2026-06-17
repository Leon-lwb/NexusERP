package com.nexus.iot.config;

import com.nexus.common.iot.service.IMesAndonService;
import com.nexus.iot.common.IotMesAndonServiceNoop;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * IoT 模块配置。
 * <p>
 * 注册 MES 联动接口的默认 no-op 实现：当 {@code ruoyi-mes} 未提供 {@link IMesAndonService}
 * 真实 Bean 时生效；MES Phase 2 提供 {@code @Primary @Service} 实现后自动让位。
 *
 * @author nexus
 */
@Configuration
public class IotConfiguration {

    @Bean
    @ConditionalOnMissingBean(IMesAndonService.class)
    public IMesAndonService iotMesAndonService() {
        return new IotMesAndonServiceNoop();
    }

}
