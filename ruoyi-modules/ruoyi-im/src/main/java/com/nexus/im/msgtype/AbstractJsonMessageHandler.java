package com.nexus.im.msgtype;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.exception.ServiceException;

import java.util.Map;

/**
 * JSON 结构化消息处理器基类
 * <p>
 * voice / video / card / position 等富媒体消息的 content 为 JSON 对象，
 * 在通用 trim / 空校验 / 长度校验之上增加 JSON 结构与必备字段校验。
 * <p>
 * 与 {@link ImageMessageHandler} / {@link FileMessageHandler} 的宽松校验不同：
 * 新类型自注册之日起即为新契约，直接按 JSON 结构强校验，便于及早暴露客户端拼装错误。
 * <p>
 * 注：此处校验用 ObjectMapper 为本类持有的独立静态实例（仅做 readTree/Map 转换，
 * 不依赖 JsonUtils 的 Spring 容器初始化），保证 Handler 可在纯单元测试环境下运行。
 *
 * @author nexus
 */
public abstract class AbstractJsonMessageHandler implements ImMessageTypeHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    /**
     * content JSON 必备字段列表（子类声明），字段值需非空白
     */
    protected abstract String[] requiredFields();

    @Override
    public String normalize(String content) {
        String value = doNormalize(content);
        Map<String, Object> map = parseObject(value);
        for (String field : requiredFields()) {
            Object v = map.get(field);
            if (v == null || StrUtil.isBlank(String.valueOf(v))) {
                throw new ServiceException("消息内容缺少必要字段: " + field);
            }
        }
        return value;
    }

    /**
     * 解析为 Map：非 JSON、JSON 数组、JSON 标量均无法映射为 Map，统一抛业务异常
     */
    private Map<String, Object> parseObject(String value) {
        try {
            return MAPPER.readValue(value, MAP_TYPE);
        } catch (Exception e) {
            throw new ServiceException("消息内容必须是合法的 JSON 对象");
        }
    }
}
