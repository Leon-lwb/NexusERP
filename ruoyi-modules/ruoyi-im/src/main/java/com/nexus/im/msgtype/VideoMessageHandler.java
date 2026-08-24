package com.nexus.im.msgtype;

import org.springframework.stereotype.Component;

/**
 * 视频消息处理器
 * <p>
 * content 为 JSON: {"url":"...", "duration":15}（duration 单位秒，可缺省）
 *
 * @author nexus
 */
@Component
public class VideoMessageHandler extends AbstractJsonMessageHandler {

    @Override
    public String type() {
        return "video";
    }

    @Override
    public boolean requiresOss() {
        return true;
    }

    @Override
    protected String[] requiredFields() {
        return new String[] { "url" };
    }
}
