package com.example.nettywebsocket.model;

import java.io.Serializable;
import java.util.Date;

/**
 * 消息记录模型类，记录每次会话中的消息详细信息
 */
public class MessageRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    // 消息记录ID
    private String recordId;
    
    // 关联的会话ID
    private String conversationId;
    
    // 消息ID（与WebSocketMessage中的messageId关联）
    private String messageId;
    
    // 消息发送者ID
    private String senderId;
    
    // 消息接收者ID
    private String receiverId;
    
    // 消息内容
    private String content;
    
    // 消息类型：聊天消息、系统消息等
    private String messageType;
    
    // 发送者角色：USER（普通用户）或AGENT（客服）
    private String senderRole;
    
    // 消息发送时间
    private Date sendTime;
    
    // 消息状态：sent（已发送）、delivered（已送达）、read（已读）
    private String status;
    
    // 构造函数
    public MessageRecord() {
        this.sendTime = new Date();
        this.status = "sent";
    }
    
    public MessageRecord(String conversationId, String messageId, String senderId, 
                        String receiverId, String content, String messageType, String senderRole) {
        this.conversationId = conversationId;
        this.messageId = messageId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.content = content;
        this.messageType = messageType;
        this.senderRole = senderRole;
        this.sendTime = new Date();
        this.status = "sent";
    }
    
    // getter和setter方法
    public String getRecordId() {
        return recordId;
    }
    
    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }
    
    public String getConversationId() {
        return conversationId;
    }
    
    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
    
    public String getMessageId() {
        return messageId;
    }
    
    public void setMessageId(String messageId) {
        this.messageId = messageId;
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
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getMessageType() {
        return messageType;
    }
    
    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }
    
    public String getSenderRole() {
        return senderRole;
    }
    
    public void setSenderRole(String senderRole) {
        this.senderRole = senderRole;
    }
    
    public Date getSendTime() {
        return sendTime;
    }
    
    public void setSendTime(Date sendTime) {
        this.sendTime = sendTime;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    @Override
    public String toString() {
        return "MessageRecord{" +
                "recordId='" + recordId + '\'' +
                ", conversationId='" + conversationId + '\'' +
                ", messageId='" + messageId + '\'' +
                ", senderId='" + senderId + '\'' +
                ", receiverId='" + receiverId + '\'' +
                ", content='" + content + '\'' +
                ", messageType='" + messageType + '\'' +
                ", senderRole='" + senderRole + '\'' +
                ", sendTime=" + sendTime +
                ", status='" + status + '\'' +
                '}';
    }
}