package com.nexus.im.msgtype;

import org.springframework.stereotype.Component;

/**
 * 文本消息处理器
 *
 * @author nexus
 */
@Component
public class TextMessageHandler implements ImMessageTypeHandler {

    @Override
    public String type() {
        return "text";
    }

    @Override
    public String normalize(String content) {
        return doNormalize(content);
    }
}
