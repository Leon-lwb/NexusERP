package com.nexus.common.websocket.listener;


import com.nexus.common.base.BaseMap;

/**
 *  自定义消息监听
 *  @author nbacheng
 *  @date 2023-09-20
 */

public interface NbcioRedisListener {
	void onMessage(BaseMap message);
}
