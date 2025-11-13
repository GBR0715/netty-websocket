package com.example.nettywebsocket.manager;

import com.example.nettywebsocket.model.WebSocketMessage;
import com.example.nettywebsocket.util.RedisUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
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
    
    // Redis工具类
    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private ObjectMapper objectMapper;

    // Redis发布订阅通道
    private final ChannelTopic broadcastTopic;
    private final ChannelTopic userTopicPrefix;
    
    // 本地内存存储（当Redis不可用时使用）
    private final Map<String, String> localUserServerMap = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> localServerUsersMap = new ConcurrentHashMap<>();
    private final Set<String> localOnlineUsers = ConcurrentHashMap.newKeySet();
    
    public RedisWebSocketConnectionManager() {
        // 生成服务器实例ID，可以使用UUID或其他方式
        this.serverId = "server:" + System.currentTimeMillis() + ":" + Math.random();
        this.broadcastTopic = new ChannelTopic(REDIS_PREFIX + "broadcast");
        this.userTopicPrefix = new ChannelTopic(REDIS_PREFIX + "user:");
        
        logger.info("RedisWebSocketConnectionManager初始化，服务器ID: {}", serverId);
    }
    
    @Override
    public void addConnection(String userId, Channel channel) {
        logger.debug("尝试添加用户连接，用户ID: {}", userId);
        
        // 存储本地连接
        localConnections.put(userId, channel);
        
        try {
            // 检查Redis是否可用
            if (redisUtil.isRedisAvailable()) {
                // 在Redis中记录用户与服务器的映射关系
                redisUtil.set(USER_SERVER_KEY + userId, serverId, 24, TimeUnit.HOURS);
                
                // 在Redis中记录当前服务器的在线用户
                redisUtil.addToSet(SERVER_USER_KEY + serverId, userId);
                
                // 添加到在线用户集合
                redisUtil.addToSet(ONLINE_USER_KEY, userId);
                
                logger.info("用户 {} 已连接，当前服务器：{}", userId, serverId);
            } else {
                logger.warn("Redis不可用，使用本地存储记录用户连接");
                // Redis失败时，使用本地内存存储
                localUserServerMap.put(userId, serverId);
                localServerUsersMap.computeIfAbsent(serverId, k -> ConcurrentHashMap.newKeySet()).add(userId);
                localOnlineUsers.add(userId);
                logger.info("用户 {} 已连接（本地存储），当前服务器：{}", userId, serverId);
            }
        } catch (Exception e) {
            logger.error("Redis记录用户连接失败", e);
            // Redis失败时，使用本地内存存储
            localUserServerMap.put(userId, serverId);
            localServerUsersMap.computeIfAbsent(serverId, k -> ConcurrentHashMap.newKeySet()).add(userId);
            localOnlineUsers.add(userId);
            logger.info("用户 {} 已连接（本地存储），当前服务器：{}", userId, serverId);
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
                redisUtil.delete(USER_SERVER_KEY + userId);
                
                // 从当前服务器的在线用户集合中移除
                redisUtil.removeFromSet(SERVER_USER_KEY + serverId, userId);
                
                // 从在线用户集合中移除
                redisUtil.removeFromSet(ONLINE_USER_KEY, userId);
                
                logger.info("用户 {} 已断开连接", userId);
            } catch (Exception e) {
                logger.error("Redis移除用户连接失败", e);
                // Redis失败时，使用本地内存存储
                localUserServerMap.remove(userId);
                localServerUsersMap.computeIfAbsent(serverId, k -> ConcurrentHashMap.newKeySet()).remove(userId);
                localOnlineUsers.remove(userId);
                logger.info("用户 {} 已断开连接（本地存储）", userId);
            }
            
            // 从所有群组中移除用户
            for (ChannelGroup group : localChannelGroups.values()) {
                group.remove(channel);
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
        // 首先检查本地连接
        if (localConnections.containsKey(userId)) {
            return true;
        }
        
        try {
            // 检查Redis中是否在线
            return redisUtil.isMember(ONLINE_USER_KEY, userId);
        } catch (Exception e) {
            logger.error("Redis检查用户在线状态失败", e);
            // Redis失败时，使用本地内存存储
            return localOnlineUsers.contains(userId);
        }
    }
    
    @Override
    public boolean sendMessage(String userId, String message) {
        // 检查用户是否连接在当前服务器
        Channel channel = localConnections.get(userId);
        if (channel != null && channel.isActive()) {
            // 直接发送消息
            channel.writeAndFlush(new TextWebSocketFrame(message));
            return true;
        } else {
            // 检查用户是否连接在其他服务器
            try {
                String targetServerId = redisUtil.get(USER_SERVER_KEY + userId,String.class);
                if (targetServerId != null) {
                    // 通过Redis发布订阅发送到其他服务器
                    WebSocketMessage wsMessage = new WebSocketMessage("DIRECT", message, null, userId);
                    // 注意：这里需要额外的Redis发布订阅实现，暂时保留原逻辑
                    logger.info("用户 {} 在其他服务器 {} 上，需要实现跨服务器消息发送", userId, targetServerId);
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
                channel.writeAndFlush(new TextWebSocketFrame(message));
            }
        }
        
        // 通过Redis向其他服务器实例广播
        try {
            WebSocketMessage wsMessage = new WebSocketMessage("BROADCAST", message, null, null);
            // 注意：这里需要额外的Redis发布订阅实现，暂时保留原逻辑
            logger.info("需要实现跨服务器广播消息");
        } catch (Exception e) {
            logger.error("Redis广播消息失败", e);
        }
    }
    
    @Override
    public long getOnlineCount() {
        try {
            return redisUtil.getSetSize(ONLINE_USER_KEY);
        } catch (Exception e) {
            logger.error("获取在线用户数量失败", e);
            // Redis失败时，使用本地内存存储
            return localOnlineUsers.size();
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
            group.writeAndFlush(new TextWebSocketFrame(message));
        }
        
        // 通过Redis向其他服务器实例的同一群组发送消息
        try {
            WebSocketMessage wsMessage = new WebSocketMessage("GROUP", message, null, groupId);
            // 注意：这里需要额外的Redis发布订阅实现，暂时保留原逻辑
            logger.info("需要实现跨服务器群组消息发送");
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
                channel.writeAndFlush(new TextWebSocketFrame(message));
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
            channel.writeAndFlush(new TextWebSocketFrame(message));
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
            group.writeAndFlush(new TextWebSocketFrame(message));
        }
    }
}