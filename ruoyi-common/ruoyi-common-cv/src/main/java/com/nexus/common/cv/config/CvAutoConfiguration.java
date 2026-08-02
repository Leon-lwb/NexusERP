package com.nexus.common.cv.config;

import com.nexus.common.cv.client.VisionClient;
import com.nexus.common.cv.client.VisionClientImpl;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 视觉服务自动配置
 * <p>注册 {@link VisionClient} Bean;当 nexus.vision.enabled=false 时不注册(业务方注入可空处理)</p>
 *
 * @author nexus
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "nexus.vision", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CvAutoConfiguration {

    /**
     * 统一视觉客户端
     */
    @Bean
    public VisionClient visionClient(CvProperties properties) {
        return new VisionClientImpl(properties);
    }
}
