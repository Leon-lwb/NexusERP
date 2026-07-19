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
     * P5-4 MEDIUM: 内容净化钩子，默认透传。
     * <p>文本类消息（TextMessageHandler）可重写此方法执行 XSS 标签清理；
     * JSON 类消息（image/file）默认不清理，避免破坏结构。
     * <p>调用时机：在 trim 与空校验之后、长度校验之前，保证清理后的内容仍受长度限制。
     */
    default String sanitize(String value) {
        return value;
    }

    /**
     * 通用文本内容规范化逻辑：trim + 空校验 + sanitize + 长度校验
     * <p>P5-4: 在 trim 后插入 sanitize 钩子，便于子类按需做 XSS 清理。
     */
    default String doNormalize(String content) {
        String value = StrUtil.trim(content);
        if (StrUtil.isBlank(value)) {
            throw new ServiceException("消息内容不能为空");
        }
        value = sanitize(value);
        // P5-4: sanitize 后可能产生空字符串（如原内容全是 HTML 标签被清理）
        if (StrUtil.isBlank(value)) {
            throw new ServiceException("消息内容不能为空");
        }
        if (value.length() > maxLength()) {
            throw new ServiceException("消息内容不能超过" + maxLength() + "个字符");
        }
        return value;
    }
}
