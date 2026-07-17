package com.nexus.im.msgtype;

import com.nexus.common.core.exception.ServiceException;
import cn.hutool.core.util.StrUtil;

/**
 * IM 消息类型处理器接口
 * <p>
 * 策略模式替代 ImChatServiceImpl 原硬编码的 SUPPORTED_MSG_TYPES Set。
 * 新增消息类型只需实现此接口并注册为 Spring Bean，由 {@link ImMessageTypeRegistry}
 * 自动收集，无需修改 ImChatServiceImpl。
 *
 * @author nexus
 */
public interface ImMessageTypeHandler {

    /**
     * 消息类型 key（小写），如 "text" / "image" / "file"
     */
    String type();

    /**
     * 内容规范化（trim / 空校验 / 长度校验等），返回处理后的 content
     */
    String normalize(String content);

    /**
     * 内容最大长度，默认 2000
     */
    default int maxLength() {
        return 2000;
    }

    /**
     * 是否需要 OSS 预处理（图片/文件类默认 false，子类可覆盖）
     */
    default boolean requiresOss() {
        return false;
    }

    /**
     * 通用文本内容规范化逻辑：trim + 空校验 + 长度校验
     */
    default String doNormalize(String content) {
        String value = StrUtil.trim(content);
        if (StrUtil.isBlank(value)) {
            throw new ServiceException("消息内容不能为空");
        }
        if (value.length() > maxLength()) {
            throw new ServiceException("消息内容不能超过" + maxLength() + "个字符");
        }
        return value;
    }
}
