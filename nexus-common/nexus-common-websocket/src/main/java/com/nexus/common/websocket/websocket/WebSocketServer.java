package com.nexus.common.websocket.websocket;

import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.nexus.common.constant.WebsocketConst;
import com.nexus.common.base.BaseMap;
import com.nexus.common.core.redis.NbcioRedisClient;
import jakarta.annotation.Resource;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

// @ServerEndpoint 声明并创建了webSocket端点, 并且指明了请求路径
// id 为客户端请求时携带的参数, 用于服务端区分客户端使用

/**
 * @ServerEndpoint 声明并创建了websocket端点, 并且指明了请求路径
 * userId 为客户端请求时携带的用户userId, 用于区分发给哪个用户的消息
 * @author nbacheng
 * @date 2023-09-20
*/

@Component
@Slf4j
@ServerEndpoint("/websocket/{userName}") //此注解相当于设置访问URL
public class WebSocketServer {

    private Session session;

    private String userName;

    private static final String REDIS_TOPIC_NAME = "socketHandler";

    @Resource
    private NbcioRedisClient nbcioRedisClient;

    /**
     * 缓存 webSocket连接到单机服务class中（整体方案支持集群）
     */
    private static CopyOnWriteArraySet<WebSocketServer> webSockets = new CopyOnWriteArraySet<>();
    private static Map<String, Session> sessionPool = new HashMap<String, Session>();


    @OnOpen
    public void onOpen(Session session, @PathParam(value = "userName") String userName) {
        try {
            this.session = session;
            this.userName = userName;
            webSockets.add(this);
            sessionPool.put(userName, session);
            log.info("【websocket消息】有新的连接，总数为:" + webSockets.size());
        } catch (Exception e) {
        }
    }

    @OnClose
    public void onClose() {
        try {
            webSockets.remove(this);
            sessionPool.remove(this.userName);
            log.info("【websocket消息】连接断开，总数为:" + webSockets.size());
        } catch (Exception e) {
        }
    }


    /**
     * 服务端推送消息
     *
     * @param userId
     * @param message
     */
    public void pushMessage(String userId, String message) {
        Session session = sessionPool.get(userId);
        if (session != null && session.isOpen()) {
            try {
                synchronized (session){
                    log.info("【websocket消息】 单点消息:" + message);
                    session.getBasicRemote().sendText(message);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 服务器端推送消息
     */
    public void pushMessage(String message) {
        try {
            webSockets.forEach(ws -> ws.session.getAsyncRemote().sendText(message));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @OnMessage
    public void onMessage(String message) {
        //todo 现在有个定时任务刷，应该去掉
        log.debug("【websocket消息】收到客户端消息:" + message);
        JSONObject obj = new JSONObject();
        //业务类型
        obj.put(WebsocketConst.MSG_CMD, WebsocketConst.CMD_CHECK);
        //消息内容
        obj.put(WebsocketConst.MSG_TXT, "心跳响应");
        for (WebSocketServer webSocket : webSockets) {
            webSocket.pushMessage(message);
        }
    }

    /**
     * 后台发送消息到redis
     *
     * @param message
     */
    public void sendMessage(String message) {
        log.info("【websocket消息】广播消息:" + message);
        BaseMap baseMap = new BaseMap();
        baseMap.put("userName", "");
        baseMap.put("message", message);
        try {
			nbcioRedisClient.sendMessage(REDIS_TOPIC_NAME, baseMap);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }

    /**
     * 此为单点消息
     *
     * @param userId
     * @param message
     */
    public void sendMessage(String userName, String message) {
        BaseMap baseMap = new BaseMap();
        baseMap.put("userName", userName);
        baseMap.put("message", message);
        try {
			nbcioRedisClient.sendMessage(REDIS_TOPIC_NAME, baseMap);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }

    /**
     * 此为单点消息(多人)
     *
     * @param userIds
     * @param message
     */
    public void sendMessage(String[] userNames, String message) {
        for (String userName : userNames) {
            sendMessage(userName, message);
        }
    }

}