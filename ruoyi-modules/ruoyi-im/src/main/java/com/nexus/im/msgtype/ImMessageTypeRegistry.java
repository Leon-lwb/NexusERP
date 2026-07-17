package com.nexus.im.msgtype;

import cn.hutool.core.util.StrUtil;
import com.nexus.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * IM 消息类型注册中心
 * <p>
 * Spring 启动时自动收集所有 {@link ImMessageTypeHandler} Bean，按 type() 建 Map 查表。
 * ImChatServiceImpl 通过此注册中心获取 Handler，替代原硬编码 SUPPORTED_MSG_TYPES Set。
 *
 * @author nexus
 */
@Component
public class ImMessageTypeRegistry {

    private final Map<String, ImMessageTypeHandler> handlers;

    public ImMessageTypeRegistry(List<ImMessageTypeHandler> handlerList) {
        this.handlers = handlerList.stream()
            .collect(Collectors.toUnmodifiableMap(
                ImMessageTypeHandler::type,
                h -> h));
    }

    /**
     * 按类型获取 Handler，类型不存在或空白时回退到 "text"
     * @throws ServiceException 类型不支持
     */
    public ImMessageTypeHandler require(String type) {
        String normalized = StrUtil.blankToDefault(StrUtil.trim(type), "text").toLowerCase();
        ImMessageTypeHandler h = handlers.get(normalized);
        if (h == null) {
            throw new ServiceException("不支持的消息类型");
        }
        return h;
    }

    /**
     * 当前已注册的所有消息类型
     */
    public Set<String> supportedTypes() {
        return handlers.keySet();
    }
}
