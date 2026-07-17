package com.nexus.im.msgtype;

import org.springframework.stereotype.Component;

/**
 * 文件消息处理器
 * <p>
 * content 通常是 JSON: {"url":"...", "name":"...", "size":...}
 * 当前仅做通用文本校验，后续可扩展文件元数据解析。
 *
 * @author nexus
 */
@Component
public class FileMessageHandler implements ImMessageTypeHandler {

    @Override
    public String type() {
        return "file";
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
