package com.example.nettywebsocket.controller;

import com.example.nettywebsocket.manager.WebSocketConnectionManager;
import com.example.nettywebsocket.model.WebSocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * WebSocket控制器，提供REST API接口
 */
@RestController
@RequestMapping("/api/websocket")
public class WebSocketController {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketController.class);
    
    @Autowired
    private WebSocketConnectionManager connectionManager;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * 获取在线用户数量
     */
    @GetMapping("/online-count")
    public Map<String, Object> getOnlineCount() {
        Map<String, Object> result = new HashMap<>();
        result.put("onlineCount", connectionManager.getOnlineCount());
        result.put("status", "success");
        return result;
    }
    
    /**
     * 检查用户是否在线
     */
    @GetMapping("/is-online/{userId}")
    public Map<String, Object> isOnline(@PathVariable String userId) {
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("isOnline", connectionManager.isOnline(userId));
        result.put("status", "success");
        return result;
    }
    
    /**
     * 向指定用户发送消息
     */
    @PostMapping("/send-to-user")
    public Map<String, Object> sendToUser(@RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String userId = request.get("userId");
            String message = request.get("message");
            String type = request.getOrDefault("type", "CHAT");
            
            if (userId == null || message == null) {
                result.put("status", "error");
                result.put("message", "userId和message不能为空");
                return result;
            }
            
            // 创建消息对象
            WebSocketMessage wsMessage = new WebSocketMessage();
            wsMessage.setType(type);
            wsMessage.setContent(message);
            wsMessage.setSenderId("server");
            wsMessage.setReceiverId(userId);
            wsMessage.setMessageId(UUID.randomUUID().toString());
            
            // 发送消息
            boolean sent = connectionManager.sendMessage(userId, objectMapper.writeValueAsString(wsMessage));
            
            result.put("status", sent ? "success" : "error");
            result.put("message", sent ? "消息发送成功" : "用户不在线");
            result.put("userId", userId);
        } catch (Exception e) {
            logger.error("发送消息失败", e);
            result.put("status", "error");
            result.put("message", "消息发送失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 向指定群组发送消息
     */
    @PostMapping("/send-to-group")
    public Map<String, Object> sendToGroup(@RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String groupId = request.get("groupId");
            String message = request.get("message");
            
            if (groupId == null || message == null) {
                result.put("status", "error");
                result.put("message", "groupId和message不能为空");
                return result;
            }
            
            // 创建消息对象
            WebSocketMessage wsMessage = new WebSocketMessage();
            wsMessage.setType("GROUP");
            wsMessage.setContent(message);
            wsMessage.setSenderId("server");
            wsMessage.setReceiverId(groupId);
            wsMessage.setMessageId(UUID.randomUUID().toString());
            
            // 发送消息
            connectionManager.sendToGroup(groupId, objectMapper.writeValueAsString(wsMessage));
            
            result.put("status", "success");
            result.put("message", "群组消息发送成功");
            result.put("groupId", groupId);
        } catch (Exception e) {
            logger.error("发送群组消息失败", e);
            result.put("status", "error");
            result.put("message", "群组消息发送失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 广播消息给所有在线用户
     */
    @PostMapping("/broadcast")
    public Map<String, Object> broadcast(@RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String message = request.get("message");
            
            if (message == null) {
                result.put("status", "error");
                result.put("message", "message不能为空");
                return result;
            }
            
            // 创建消息对象
            WebSocketMessage wsMessage = new WebSocketMessage();
            wsMessage.setType("BROADCAST");
            wsMessage.setContent(message);
            wsMessage.setSenderId("server");
            wsMessage.setMessageId(UUID.randomUUID().toString());
            
            // 广播消息
            connectionManager.broadcast(objectMapper.writeValueAsString(wsMessage));
            
            result.put("status", "success");
            result.put("message", "广播消息发送成功");
            result.put("onlineCount", connectionManager.getOnlineCount());
        } catch (Exception e) {
            logger.error("广播消息失败", e);
            result.put("status", "error");
            result.put("message", "广播消息发送失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 将用户添加到群组
     */
    @PostMapping("/add-to-group")
    public Map<String, Object> addToGroup(@RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String userId = request.get("userId");
            String groupId = request.get("groupId");
            
            if (userId == null || groupId == null) {
                result.put("status", "error");
                result.put("message", "userId和groupId不能为空");
                return result;
            }
            
            // 添加用户到群组
            connectionManager.addToGroup(userId, groupId);
            
            result.put("status", "success");
            result.put("message", "用户已添加到群组");
            result.put("userId", userId);
            result.put("groupId", groupId);
        } catch (Exception e) {
            logger.error("添加用户到群组失败", e);
            result.put("status", "error");
            result.put("message", "添加用户到群组失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 将用户从群组移除
     */
    @PostMapping("/remove-from-group")
    public Map<String, Object> removeFromGroup(@RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String userId = request.get("userId");
            String groupId = request.get("groupId");
            
            if (userId == null || groupId == null) {
                result.put("status", "error");
                result.put("message", "userId和groupId不能为空");
                return result;
            }
            
            // 从群组移除用户
            connectionManager.removeFromGroup(userId, groupId);
            
            result.put("status", "success");
            result.put("message", "用户已从群组移除");
            result.put("userId", userId);
            result.put("groupId", groupId);
        } catch (Exception e) {
            logger.error("从群组移除用户失败", e);
            result.put("status", "error");
            result.put("message", "从群组移除用户失败: " + e.getMessage());
        }
        
        return result;
    }
}