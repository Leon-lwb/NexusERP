package com.nexus.common.sign.config;

import org.springframework.context.annotation.Configuration;

/**
 * 电子签章模块自动装配。
 * <p>本模块的 Service 通过 {@code @Service} 声明，由各业务模块扫描加载；
 * 此类作为 {@code AutoConfiguration.imports} 的注册入口，保持与项目其它 common 模块一致。</p>
 *
 * @author nexus
 */
@Configuration
public class SignAutoConfiguration {
}
