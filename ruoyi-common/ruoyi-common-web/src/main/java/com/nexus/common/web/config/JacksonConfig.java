package com.nexus.common.web.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.nexus.common.web.core.BigNumberSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 全局配置
 * <p>解决前端 JS 对超长 Long（雪花ID，超过 Number.MAX_SAFE_INTEGER = 2^53-1 = 9007199254740991）
 * 精度丢失问题：把超出 JS 安全整数范围的 Long 序列化为 String，普通 Long 仍保持数字。</p>
 * <p>对应前端现象：后端返回 {@code "id": 2071644711126446082}（数字），
 * 前端 {@code JSON.parse} 解析后变成 {@code 2071644711126446000}（末尾被截断），导致按 id 查询时找不到记录。</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer bigNumberCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule();
            // 仅对超出 JS 安全整数范围的 Long 转 String，普通 Long 保持数字（兼容前端 typeof === 'number'）
            module.addSerializer(Long.class, BigNumberSerializer.instance);
            module.addSerializer(Long.TYPE, BigNumberSerializer.instance);
            builder.modules(module);
        };
    }
}
