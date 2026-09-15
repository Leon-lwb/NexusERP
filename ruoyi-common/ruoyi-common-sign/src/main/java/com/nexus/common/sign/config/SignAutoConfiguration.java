package com.nexus.common.sign.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * 电子签章模块自动配置。
 * <p>本模块的 Service 通过 {@code @Service} 声明，由依赖本模块的业务方组件扫描加载；
 * 此类作为 {@code AutoConfiguration.imports} 的注册入口，与项目其它 common 模块保持一致。</p>
 *
 * @author nexus
 * @since 1.0.0
 */
@AutoConfiguration
public class SignAutoConfiguration {
}
