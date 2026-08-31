package com.nexus.im.msgtype;

import org.springframework.stereotype.Component;

/**
 * 图片消息处理器
 * <p>
 * content 通常是 JSON: {"url":"...", "width":..., "height":...}
 * 当前仅做通用文本校验，后续可扩展图片元数据解析。
 *
 * @author nexus
 */
@Component
public class ImageMessageHandler implements ImMessageTypeHandler {

    @Override
    public String type() {
        return "image";
    }

    @Override
    public boolean requiresOss() {
        return true;
    }

    @Override
    public String normalize(String content) {
        return doNormalize(content);
    }
}
