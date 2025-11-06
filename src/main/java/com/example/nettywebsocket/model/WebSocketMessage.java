package com.example.nettywebsocket.model;

import java.io.Serializable;

/**
 * WebSocket消息模型类
 */
public class WebSocketMessage implements Serializable {
    
    // 消息类型常量
    public static final String TYPE_SYSTEM = "SYSTEM";      // 系统消息
    public static final String TYPE_CHAT = "CHAT";          // 聊天消息
    public static final String TYPE_GROUP = "GROUP";        // 群组消息
    public static final String TYPE_BROADCAST = "BROADCAST"; // 广播消息
    
    // 客服相关消息类型
    public static final String TYPE_CS_ASSIGN = "CS_ASSIGN"; // 客服分配消息
    public static final String TYPE_CS_STATUS = "CS_STATUS"; // 客服状态消息
    public static final String TYPE_USER_JOIN = "USER_JOIN"; // 用户加入消息
    public static final String TYPE_USER_LEAVE = "USER_LEAVE"; // 用户离开消息
    private static final long serialVersionUID = 1L;
    
    // 消息类型：聊天消息、系统消息等
    private String type;
    
    // 消息内容
    private String content;
    
    // 发送者ID
    private String senderId;
    
    // 接收者ID，可以是用户ID或群组ID
    private String receiverId;
    
    // 消息时间戳
    private long timestamp;
    
    // 分布式环境下的消息ID
    private String messageId;
    
    // 构造函数
    public WebSocketMessage() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public WebSocketMessage(String type, String content, String senderId, String receiverId) {
        this.type = type;
        this.content = content;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.timestamp = System.currentTimeMillis();
    }
    
    // getter和setter方法
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getSenderId() {
        return senderId;
    }
    
    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }
    
    public String getReceiverId() {
        return receiverId;
    }
    
    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getMessageId() {
        return messageId;
    }
    
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
    
    @Override
    public String toString() {
        return "WebSocketMessage{" +
                "type='" + type + '\'' +
                ", content='" + content + '\'' +
                ", senderId='" + senderId + '\'' +
                ", receiverId='" + receiverId + '\'' +
                ", timestamp=" + timestamp +
                ", messageId='" + messageId + '\'' +
                '}';
    }
}