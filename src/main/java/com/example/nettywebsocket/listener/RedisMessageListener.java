package com.example.nettywebsocket.listener;

import com.example.nettywebsocket.manager.RedisWebSocketConnectionManager;
import com.example.nettywebsocket.model.WebSocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Redis消息监听器，用于处理分布式环境下的WebSocket消息
 */
@Component
public class RedisMessageListener implements MessageListener {
    
    private static final Logger logger = LoggerFactory.getLogger(RedisMessageListener.class);
    
    private static final String REDIS_PREFIX = "websocket:";
    private static final String BROADCAST_TOPIC = REDIS_PREFIX + "broadcast";
    private static final String USER_TOPIC_PREFIX = REDIS_PREFIX + "user:";
    private static final String GROUP_TOPIC_PREFIX = REDIS_PREFIX + "group:";
    
    @Autowired
    private RedisWebSocketConnectionManager connectionManager;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel());
            String messageBody = new String(message.getBody());
            
            logger.debug("收到Redis消息，通道: {}, 内容: {}", channel, messageBody);
            
            // 解析消息
            WebSocketMessage wsMessage = objectMapper.readValue(messageBody, WebSocketMessage.class);
            
            // 根据通道类型处理消息
            if (channel.equals(BROADCAST_TOPIC)) {
                // 处理广播消息
                connectionManager.handleRedisBroadcast(wsMessage.getContent());
            } else if (channel.startsWith(USER_TOPIC_PREFIX)) {
                // 处理用户定向消息
                String userId = channel.substring(USER_TOPIC_PREFIX.length());
                connectionManager.handleRedisUserMessage(userId, wsMessage.getContent());
            } else if (channel.startsWith(GROUP_TOPIC_PREFIX)) {
                // 处理群组消息
                String groupId = channel.substring(GROUP_TOPIC_PREFIX.length());
                connectionManager.handleRedisGroupMessage(groupId, wsMessage.getContent());
            }
        } catch (Exception e) {
            logger.error("处理Redis消息失败", e);
        }
    }
}