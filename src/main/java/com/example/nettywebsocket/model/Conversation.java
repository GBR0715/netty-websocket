package com.example.nettywebsocket.model;

import java.io.Serializable;
import java.util.Date;

/**
 * 会话模型类，记录会话的基本信息
 */
public class Conversation implements Serializable {

    private static final long serialVersionUID = 1L;

    // 会话ID
    private String conversationId;
    
    // 会话创建者ID
    private String creatorId;
    
    // 会话接收者ID（可能是客服ID或用户ID）
    private String receiverId;
    
    // 会话开始时间
    private Date startTime;
    
    // 会话结束时间
    private Date endTime;
    
    // 结束类型：manual（手动结束）、auto（自动结束）、timeout（超时结束）
    private String endType;
    
    // 会话状态：active（活跃）、closed（已关闭）
    private String status;
    
    // 创建会话的用户角色：USER（普通用户）或AGENT（客服）
    private String creatorRole;
    
    // 最后一条消息时间
    private Date lastMessageTime;
    
    // 构造函数
    public Conversation() {
        this.startTime = new Date();
        this.status = "active";
    }
    
    // getter和setter方法
    public String getConversationId() {
        return conversationId;
    }
    
    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
    
    public String getCreatorId() {
        return creatorId;
    }
    
    public void setCreatorId(String creatorId) {
        this.creatorId = creatorId;
    }
    
    public String getReceiverId() {
        return receiverId;
    }
    
    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }
    
    public Date getStartTime() {
        return startTime;
    }
    
    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }
    
    public Date getEndTime() {
        return endTime;
    }
    
    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }
    
    public String getEndType() {
        return endType;
    }
    
    public void setEndType(String endType) {
        this.endType = endType;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getCreatorRole() {
        return creatorRole;
    }
    
    public void setCreatorRole(String creatorRole) {
        this.creatorRole = creatorRole;
    }
    
    public Date getLastMessageTime() {
        return lastMessageTime;
    }
    
    public void setLastMessageTime(Date lastMessageTime) {
        this.lastMessageTime = lastMessageTime;
    }
    
    @Override
    public String toString() {
        return "Conversation{" +
                "conversationId='" + conversationId + '\'' +
                ", creatorId='" + creatorId + '\'' +
                ", receiverId='" + receiverId + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", endType='" + endType + '\'' +
                ", status='" + status + '\'' +
                ", creatorRole='" + creatorRole + '\'' +
                ", lastMessageTime=" + lastMessageTime +
                '}';
    }
}