package com.nexus.common.ai.service.image;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文生图厂商策略工厂
 * <p>启动时自动发现所有 {@link IImageGenerationService} Bean，按 {@link IImageGenerationService#getProviderName()}
 * 注册；运行时按 providerName 路由</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Slf4j
@Component
public class ImageServiceFactory implements ApplicationContextAware {

    private final Map<String, IImageGenerationService> providers = new ConcurrentHashMap<>();

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        ctx.getBeansOfType(IImageGenerationService.class).values().forEach(p -> {
            providers.put(p.getProviderName(), p);
            log.info("[ImageServiceFactory] 注册文生图策略: {} -> {}", p.getProviderName(), p.getClass().getSimpleName());
        });
    }

    /**
     * 获取文生图策略
     *
     * @param providerName 厂商名称
     * @return 策略实例，未注册返回 null
     */
    public IImageGenerationService get(String providerName) {
        return providers.get(providerName);
    }
}
