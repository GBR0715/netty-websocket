package com.example.nettywebsocket.manager;

import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;

import java.util.Map;

/**
 * WebSocket连接管理器接口
 */
public interface WebSocketConnectionManager {
    
    /**
     * 添加连接
     * @param userId 用户ID
     * @param channel WebSocket通道
     */
    void addConnection(String userId, Channel channel);
    
    /**
     * 移除连接
     * @param channel WebSocket通道
     */
    void removeConnection(Channel channel);
    
    /**
     * 根据用户ID获取通道
     * @param userId 用户ID
     * @return WebSocket通道
     */
    Channel getChannel(String userId);
    
    /**
     * 获取所有连接
     * @return 连接映射
     */
    Map<String, Channel> getAllConnections();
    
    /**
     * 判断用户是否在线
     * @param userId 用户ID
     * @return 是否在线
     */
    boolean isOnline(String userId);
    
    /**
     * 向指定用户发送消息
     * @param userId 用户ID
     * @param message 消息内容
     * @return 是否发送成功
     */
    boolean sendMessage(String userId, String message);
    
    /**
     * 广播消息给所有在线用户
     * @param message 消息内容
     */
    void broadcast(String message);
    
    /**
     * 获取在线用户数量
     * @return 在线用户数量
     */
    int getOnlineCount();
    
    /**
     * 获取用户组通道组
     * @param groupId 群组ID
     * @return 通道组
     */
    ChannelGroup getGroup(String groupId);
    
    /**
     * 将用户添加到指定组
     * @param userId 用户ID
     * @param groupId 群组ID
     */
    void addToGroup(String userId, String groupId);
    
    /**
     * 将用户从指定组移除
     * @param userId 用户ID
     * @param groupId 群组ID
     */
    void removeFromGroup(String userId, String groupId);
    
    /**
     * 向指定组发送消息
     * @param groupId 群组ID
     * @param message 消息内容
     */
    void sendToGroup(String groupId, String message);
}