package com.example.nettywebsocket.manager;

import com.example.nettywebsocket.model.WebSocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Redis实现的WebSocket连接管理器，支持分布式部署
 */
@Component
public class RedisWebSocketConnectionManager implements WebSocketConnectionManager {
    
    private static final Logger logger = LoggerFactory.getLogger(RedisWebSocketConnectionManager.class);
    
    // 本地连接缓存，存储当前服务器实例的连接
    private final Map<String, Channel> localConnections = new ConcurrentHashMap<>();
    
    // 本地通道组缓存
    private final Map<String, ChannelGroup> localChannelGroups = new ConcurrentHashMap<>();
    
    // Redis键前缀
    private static final String REDIS_PREFIX = "websocket:";
    private static final String ONLINE_USER_KEY = REDIS_PREFIX + "online:users";
    private static final String USER_SERVER_KEY = REDIS_PREFIX + "user:server:";
    private static final String SERVER_USER_KEY = REDIS_PREFIX + "server:users:";
    
    // 服务器实例ID，用于标识当前服务器
    private final String serverId;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    // Redis发布订阅通道
    private final ChannelTopic broadcastTopic;
    private final ChannelTopic userTopicPrefix;
    
    public RedisWebSocketConnectionManager() {
        // 生成服务器实例ID，可以使用UUID或其他方式
        this.serverId = "server:" + System.currentTimeMillis() + ":" + Math.random();
        this.broadcastTopic = new ChannelTopic(REDIS_PREFIX + "broadcast");
        this.userTopicPrefix = new ChannelTopic(REDIS_PREFIX + "user:");
    }
    
    @Override
    public void addConnection(String userId, Channel channel) {
        // 存储本地连接
        localConnections.put(userId, channel);
        
        try {
            // 在Redis中记录用户与服务器的映射关系
            redisTemplate.opsForValue().set(USER_SERVER_KEY + userId, serverId, 24, TimeUnit.HOURS);
            
            // 在Redis中记录当前服务器的在线用户
            redisTemplate.opsForSet().add(SERVER_USER_KEY + serverId, userId);
            
            // 添加到在线用户集合
            redisTemplate.opsForSet().add(ONLINE_USER_KEY, userId);
            
            logger.info("用户 {} 已连接，当前服务器：{}", userId, serverId);
        } catch (Exception e) {
            logger.error("Redis记录用户连接失败", e);
        }
    }
    
    @Override
    public void removeConnection(Channel channel) {
        // 查找对应的用户ID
        String userId = null;
        for (Map.Entry<String, Channel> entry : localConnections.entrySet()) {
            if (entry.getValue().equals(channel)) {
                userId = entry.getKey();
                break;
            }
        }
        
        if (userId != null) {
            // 移除本地连接
            localConnections.remove(userId);
            
            try {
                // 从Redis中移除用户与服务器的映射关系
                redisTemplate.delete(USER_SERVER_KEY + userId);
                
                // 从当前服务器的在线用户集合中移除
                redisTemplate.opsForSet().remove(SERVER_USER_KEY + serverId, userId);
                
                // 从在线用户集合中移除
                redisTemplate.opsForSet().remove(ONLINE_USER_KEY, userId);
                
                // 从所有群组中移除用户
                for (ChannelGroup group : localChannelGroups.values()) {
                    group.remove(channel);
                }
                
                logger.info("用户 {} 已断开连接", userId);
            } catch (Exception e) {
                logger.error("Redis移除用户连接失败", e);
            }
        }
    }
    
    @Override
    public Channel getChannel(String userId) {
        return localConnections.get(userId);
    }
    
    @Override
    public Map<String, Channel> getAllConnections() {
        return new ConcurrentHashMap<>(localConnections);
    }
    
    @Override
    public boolean isOnline(String userId) {
        try {
            return redisTemplate.opsForSet().isMember(ONLINE_USER_KEY, userId);
        } catch (Exception e) {
            logger.error("检查用户在线状态失败", e);
            return false;
        }
    }
    
    @Override
    public boolean sendMessage(String userId, String message) {
        // 检查用户是否连接在当前服务器
        Channel channel = localConnections.get(userId);
        if (channel != null && channel.isActive()) {
            // 直接发送消息
            channel.writeAndFlush(message);
            return true;
        } else {
            // 检查用户是否连接在其他服务器
            try {
                String targetServerId = (String) redisTemplate.opsForValue().get(USER_SERVER_KEY + userId);
                if (targetServerId != null) {
                    // 通过Redis发布订阅发送到其他服务器
                    WebSocketMessage wsMessage = new WebSocketMessage("DIRECT", message, null, userId);
                    redisTemplate.convertAndSend(REDIS_PREFIX + "user:" + userId, objectMapper.writeValueAsString(wsMessage));
                    return true;
                }
            } catch (Exception e) {
                logger.error("发送消息到用户 {} 失败", userId, e);
            }
            return false;
        }
    }
    
    @Override
    public void broadcast(String message) {
        // 先向本地所有连接广播
        for (Channel channel : localConnections.values()) {
            if (channel.isActive()) {
                channel.writeAndFlush(message);
            }
        }
        
        // 通过Redis向其他服务器实例广播
        try {
            WebSocketMessage wsMessage = new WebSocketMessage("BROADCAST", message, null, null);
            redisTemplate.convertAndSend(broadcastTopic.getTopic(), objectMapper.writeValueAsString(wsMessage));
        } catch (Exception e) {
            logger.error("Redis广播消息失败", e);
        }
    }
    
    @Override
    public int getOnlineCount() {
        try {
            return redisTemplate.opsForSet().size(ONLINE_USER_KEY).intValue();
        } catch (Exception e) {
            logger.error("获取在线用户数量失败", e);
            return localConnections.size();
        }
    }
    
    @Override
    public ChannelGroup getGroup(String groupId) {
        return localChannelGroups.computeIfAbsent(groupId, k -> new DefaultChannelGroup(GlobalEventExecutor.INSTANCE));
    }
    
    @Override
    public void addToGroup(String userId, String groupId) {
        Channel channel = localConnections.get(userId);
        if (channel != null) {
            ChannelGroup group = getGroup(groupId);
            group.add(channel);
        }
    }
    
    @Override
    public void removeFromGroup(String userId, String groupId) {
        ChannelGroup group = localChannelGroups.get(groupId);
        if (group != null) {
            Channel channel = localConnections.get(userId);
            if (channel != null) {
                group.remove(channel);
            }
        }
    }
    
    @Override
    public void sendToGroup(String groupId, String message) {
        ChannelGroup group = localChannelGroups.get(groupId);
        if (group != null) {
            group.writeAndFlush(message);
        }
        
        // 通过Redis向其他服务器实例的同一群组发送消息
        try {
            WebSocketMessage wsMessage = new WebSocketMessage("GROUP", message, null, groupId);
            redisTemplate.convertAndSend(REDIS_PREFIX + "group:" + groupId, objectMapper.writeValueAsString(wsMessage));
        } catch (Exception e) {
            logger.error("发送群组消息失败", e);
        }
    }
    
    /**
     * 处理来自Redis的广播消息
     * @param message 消息内容
     */
    public void handleRedisBroadcast(String message) {
        for (Channel channel : localConnections.values()) {
            if (channel.isActive()) {
                channel.writeAndFlush(message);
            }
        }
    }
    
    /**
     * 处理来自Redis的用户定向消息
     * @param userId 用户ID
     * @param message 消息内容
     */
    public void handleRedisUserMessage(String userId, String message) {
        Channel channel = localConnections.get(userId);
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(message);
        }
    }
    
    /**
     * 处理来自Redis的群组消息
     * @param groupId 群组ID
     * @param message 消息内容
     */
    public void handleRedisGroupMessage(String groupId, String message) {
        ChannelGroup group = localChannelGroups.get(groupId);
        if (group != null) {
            group.writeAndFlush(message);
        }
    }
}