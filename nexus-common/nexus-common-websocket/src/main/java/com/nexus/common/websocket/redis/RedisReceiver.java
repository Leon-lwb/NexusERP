package com.nexus.common.websocket.redis;

import com.nexus.common.base.BaseMap;
import com.nexus.common.constant.GlobalConstants;
import com.nexus.common.utils.spring.SpringUtils;
import com.nexus.common.websocket.listener.NbcioRedisListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RedisReceiver {

    public void onMessage(BaseMap params) {
        Object handlerName = params.get(GlobalConstants.HANDLER_NAME);
        if (handlerName == null) {
            return;
        }
        try {
            Object handler = SpringUtils.getBean(handlerName.toString());
            if (handler instanceof NbcioRedisListener listener) {
                listener.onMessage(params);
            }
        } catch (Exception e) {
            log.error("处理Redis消息失败: {}", e.getMessage(), e);
        }
    }
}
