package com.nexus.common.cv.config;

import com.nexus.common.cv.client.VisionClient;
import com.nexus.common.cv.client.VisionClientImpl;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 视觉服务自动配置
 * <p>始终注册 {@link VisionClient} Bean;nexus.vision.enabled=false 时由
 * {@link VisionClientImpl} 在调用时抛出 {@code VisionException},业务方捕获降级,
 * 避免 ERP 侧硬构造器注入在禁用场景下启动失败</p>
 *
 * @author nexus
 * @since 1.0.0
 */
@AutoConfiguration
public class CvAutoConfiguration {

    /**
     * 统一视觉客户端
     */
    @Bean
    public VisionClient visionClient(CvProperties properties) {
        return new VisionClientImpl(properties);
    }
}
