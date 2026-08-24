package com.nexus.im.msgtype;

import org.springframework.stereotype.Component;

/**
 * 语音消息处理器
 * <p>
 * content 为 JSON: {"url":"...", "duration":12}（duration 单位秒）
 *
 * @author nexus
 */
@Component
public class VoiceMessageHandler extends AbstractJsonMessageHandler {

    @Override
    public String type() {
        return "voice";
    }

    @Override
    public boolean requiresOss() {
        return true;
    }

    @Override
    protected String[] requiredFields() {
        return new String[] { "url", "duration" };
    }
}
