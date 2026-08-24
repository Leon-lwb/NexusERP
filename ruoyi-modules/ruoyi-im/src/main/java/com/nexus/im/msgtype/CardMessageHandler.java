package com.nexus.im.msgtype;

import org.springframework.stereotype.Component;

/**
 * 名片消息处理器
 * <p>
 * content 为 JSON: {"userId":1, "name":"张三", "avatar":"https://..."}（avatar 可缺省）
 *
 * @author nexus
 */
@Component
public class CardMessageHandler extends AbstractJsonMessageHandler {

    @Override
    public String type() {
        return "card";
    }

    @Override
    protected String[] requiredFields() {
        return new String[] { "userId", "name" };
    }
}
