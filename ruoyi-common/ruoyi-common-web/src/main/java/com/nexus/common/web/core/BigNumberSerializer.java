package com.nexus.common.web.core;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JacksonStdImpl;
import com.fasterxml.jackson.databind.ser.std.NumberSerializer;

import java.io.IOException;

/**
 * 超长数字序列化器
 * <p>仅当 Long 值超出 JS 安全整数范围（Number.MAX_SAFE_INTEGER = 2^53-1 = 9007199254740991）时
 * 序列化为 String，否则保持为数字。避免前端 {@code JSON.parse} 对雪花ID精度丢失。</p>
 *
 * <p><b>必须继承 {@link NumberSerializer}</b>：其 {@code serialize} 对范围内的数字保持数字输出
 * （{@code gen.writeNumber}），超范围时由本类改为 {@code gen.writeString}。
 * <b>切勿</b>继承 {@code ToStringSerializer}——后者的 {@code serialize} 会将值 {@code toString()}
 * 后以字符串输出，导致本类在“范围内调用 {@code super.serialize}”时仍输出字符串，
 * 使所有 Long（含 total、id 等小数字）被错误转成字符串，
 * 前端 el-pagination 因 {@code typeof total !== 'number'} 无法计算页数（不显示分页按钮）。</p>
 *
 * <p>移植自 RuoYi-Vue-Plus 官方 BigNumberSerializer（原 {@code com.nexus.common.json.handler.BigNumberSerializer}）。</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@JacksonStdImpl
public class BigNumberSerializer extends NumberSerializer {

    public static final BigNumberSerializer instance = new BigNumberSerializer(Number.class);

    /** JS 安全整数上限（Number.MAX_SAFE_INTEGER = 2^53 - 1） */
    private static final long JS_MAX_SAFE_INTEGER = 9007199254740991L;

    public BigNumberSerializer(Class<? extends Number> rawType) {
        super(rawType);
    }

    @Override
    public void serialize(Number value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        long longValue = value.longValue();
        // 超出 JS 安全整数范围 → 输出为字符串，避免前端精度丢失
        if (longValue > JS_MAX_SAFE_INTEGER || longValue < -JS_MAX_SAFE_INTEGER) {
            gen.writeString(value.toString());
            return;
        }
        // 范围内 → 保持数字（NumberSerializer.serialize 走 gen.writeNumber，不会转字符串）
        super.serialize(value, gen, provider);
    }
}
