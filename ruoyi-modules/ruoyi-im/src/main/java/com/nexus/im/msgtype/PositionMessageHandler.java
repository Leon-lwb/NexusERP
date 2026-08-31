package com.nexus.im.msgtype;

import org.springframework.stereotype.Component;

/**
 * 位置消息处理器
 * <p>
 * content 为 JSON: {"latitude":31.23, "longitude":121.47, "name":"...", "address":"..."}
 *
 * @author nexus
 */
@Component
public class PositionMessageHandler extends AbstractJsonMessageHandler {

    @Override
    public String type() {
        return "position";
    }

    @Override
    protected String[] requiredFields() {
        return new String[] { "latitude", "longitude" };
    }
}
