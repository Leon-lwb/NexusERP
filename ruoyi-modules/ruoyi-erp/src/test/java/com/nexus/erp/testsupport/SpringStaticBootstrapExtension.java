package com.nexus.erp.testsupport;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * 为 ERP 模块的每个测试类在 {@code @BeforeAll} 之前完成 Spring 依赖静态工具类的预热。
 *
 * <p>通过 {@code src/test/resources/META-INF/services/org.junit.jupiter.api.extension.Extension}
 * + {@code junit-platform.properties} 中的
 * {@code junit.jupiter.extensions.autodetection.enabled=true} 自动注册，
 * 使预热与测试类执行顺序无关，从根本上消除「首个踩雷的测试类毒化整个 surefire fork」的问题。</p>
 *
 * @author Nexus
 */
public class SpringStaticBootstrapExtension implements BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        SpringStaticBootstrap.ensureActive();
    }
}
