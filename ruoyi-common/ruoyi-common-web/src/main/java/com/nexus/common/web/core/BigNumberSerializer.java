package com.nexus.common.web.core;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JacksonStdImpl;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.io.IOException;

/**
 * 超长数字序列化器
 * <p>仅当 Long 值超出 JS 安全整数范围（Number.MAX_SAFE_INTEGER = 2^53-1 = 9007199254740991）时
 * 序列化为 String，否则保持为数字。避免前端 {@code JSON.parse} 对雪花ID精度丢失。</p>
 * <p>移植自 RuoYi-Vue-Plus 官方 BigNumberSerializer。</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@JacksonStdImpl
public class BigNumberSerializer extends ToStringSerializer {

    public static final BigNumberSerializer instance = new BigNumberSerializer();

    /** JS 安全整数上限（Number.MAX_SAFE_INTEGER = 2^53 - 1） */
    private static final long JS_MAX_SAFE_INTEGER = 9007199254740991L;

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value instanceof Number) {
            long longValue = ((Number) value).longValue();
            // 超出 JS 安全整数范围 → 输出为字符串，避免前端精度丢失
            if (longValue > JS_MAX_SAFE_INTEGER || longValue < -JS_MAX_SAFE_INTEGER) {
                gen.writeString(value.toString());
                return;
            }
        }
        super.serialize(value, gen, provider);
    }
}
