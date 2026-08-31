package com.nexus.common.ai.service.image;

import com.nexus.common.ai.domain.dto.image.ImageContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 文生图工厂键规范化单元测试（纯 Java + GenericApplicationContext 装配，无 Spring 测试上下文/无网络）
 * <p>验证注册键与查询键的大小写/空白宽容、厂商类型码别名（DASHSCOPE→通义万相）
 * 与未知键返回 null 的行为。</p>
 *
 * @author nexus-school
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class ImageServiceFactoryTest {

    private static IImageGenerationService fake(String providerName) {
        return new IImageGenerationService() {
            @Override
            public String generateImage(ImageContext ctx) {
                return "fake-url";
            }

            @Override
            public String getProviderName() {
                return providerName;
            }
        };
    }

    /**
     * 用 GenericApplicationContext 手工注册单例再触发 setApplicationContext，
     * 复现生产装配路径（getBeansOfType 自动发现）且不引入 Spring 测试装置
     */
    private static ImageServiceFactory factoryWith(IImageGenerationService... services) {
        GenericApplicationContext ctx = new GenericApplicationContext();
        for (int i = 0; i < services.length; i++) {
            ctx.getBeanFactory().registerSingleton("imageService" + i, services[i]);
        }
        ctx.refresh();
        ImageServiceFactory factory = new ImageServiceFactory();
        factory.setApplicationContext(ctx);
        return factory;
    }

    @Test
    void providerTypeAliasDashScopeResolvesTongyiwanx() {
        IImageGenerationService tongyiwanx = fake("Tongyiwanx");
        ImageServiceFactory factory = factoryWith(tongyiwanx);
        IImageGenerationService byType = factory.get("DASHSCOPE");
        assertNotNull(byType, "厂商类型码 DASHSCOPE 应经别名解析到通义万相实现");
        assertSame(tongyiwanx, byType);
        // 别名与厂商实现名解析到同一实例（模型 providerType 与旧配置键两条路都通）
        assertSame(factory.get("Tongyiwanx"), byType);
    }

    @Test
    void lookupIsCaseAndWhitespaceTolerant() {
        ImageServiceFactory factory = factoryWith(fake("Tongyiwanx"));
        assertNotNull(factory.get("tongyiwanx"));
        assertNotNull(factory.get(" Tongyiwanx "));
        assertNotNull(factory.get(" dashscope "));
    }

    @Test
    void unknownNullOrBlankKeyReturnsNull() {
        ImageServiceFactory factory = factoryWith(fake("Tongyiwanx"));
        assertNull(factory.get("OPENAI"), "未注册厂商类型码应返回 null（调用方转为配置错误提示）");
        assertNull(factory.get("Tongyiwanx-V2"));
        assertNull(factory.get(""));
        assertNull(factory.get("  "));
        assertNull(factory.get(null));
    }
}
