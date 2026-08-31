package com.nexus.common.web.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.nexus.common.web.core.BigNumberSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 全局配置
 * <p>解决前端 JS 对超长 Long（雪花ID，超过 Number.MAX_SAFE_INTEGER = 2^53-1 = 9007199254740991）
 * 精度丢失问题：把超出 JS 安全整数范围的 Long 序列化为 String，普通 Long 仍保持数字。</p>
 * <p>对应前端现象：后端返回 {@code "id": 2071644711126446082}（数字），
 * 前端 {@code JSON.parse} 解析后变成 {@code 2071644711126446000}（末尾被截断），导致按 id 查询时找不到记录。</p>
 *
 * <p><b>注册方式</b>：以 {@link Module} Bean 形式注册，由 Spring Boot 自动发现，并与 {@code ruoyi-common-json}
 * 注册的 {@code JavaTimeModule} 一并装配。<b>切勿</b>改用 {@code Jackson2ObjectMapperBuilderCustomizer}
 * 配合 {@code builder.modules(...)}——后者会整体替换模块列表并关闭 {@code Module} Bean 的自动发现，
 * 从而把 {@code JavaTimeModule} 屏蔽掉，导致 {@code LocalDateTime} 无法序列化
 * （报错 {@code Type definition error: [simple type, class java.time.LocalDateTime]}）。</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Module bigNumberModule() {
        SimpleModule module = new SimpleModule();
        // 仅对超出 JS 安全整数范围的 Long 转 String，普通 Long 保持数字（兼容前端 typeof === 'number'）
        module.addSerializer(Long.class, BigNumberSerializer.instance);
        module.addSerializer(Long.TYPE, BigNumberSerializer.instance);
        return module;
    }
}
