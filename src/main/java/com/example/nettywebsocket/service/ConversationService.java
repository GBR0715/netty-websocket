package com.example.nettywebsocket.service;

import com.example.nettywebsocket.model.Conversation;
import com.example.nettywebsocket.model.MessageRecord;

import java.util.List;
import java.util.Map;

/**
 * 会话历史服务接口
 * 业务系统可以实现此接口来自定义会话历史和消息记录的存储方式
 */
public interface ConversationService {
    
    /**
     * 创建新会话
     * @param conversation 会话信息
     * @return 创建的会话
     */
    Conversation createConversation(Conversation conversation);
    
    /**
     * 根据会话ID获取会话信息
     * @param conversationId 会话ID
     * @return 会话信息
     */
    Conversation getConversationById(String conversationId);
    
    /**
     * 结束会话
     * @param conversationId 会话ID
     * @param endType 结束类型：manual（手动结束）、auto（自动结束）、timeout（超时结束）
     * @return 更新后的会话
     */
    Conversation endConversation(String conversationId, String endType);
    
    /**
     * 获取用户的所有会话列表
     * @param userId 用户ID
     * @param page 页码
     * @param size 每页大小
     * @return 会话列表
     */
    List<Conversation> getUserConversations(String userId, int page, int size);
    
    /**
     * 获取客服的所有会话列表
     * @param agentId 客服ID
     * @param page 页码
     * @param size 每页大小
     * @return 会话列表
     */
    List<Conversation> getAgentConversations(String agentId, int page, int size);
    
    /**
     * 获取用户和客服之间的当前活跃会话
     * @param userId 用户ID
     * @param agentId 客服ID
     * @return 会话信息，如果不存在则返回null
     */
    Conversation getActiveConversation(String userId, String agentId);
    
    /**
     * 保存消息记录
     * @param messageRecord 消息记录
     * @return 保存的消息记录
     */
    MessageRecord saveMessageRecord(MessageRecord messageRecord);
    
    /**
     * 获取会话中的消息记录列表
     * @param conversationId 会话ID
     * @param page 页码
     * @param size 每页大小
     * @return 消息记录列表
     */
    List<MessageRecord> getConversationMessages(String conversationId, int page, int size);
    
    /**
     * 批量保存消息记录
     * @param messageRecords 消息记录列表
     * @return 保存的消息记录数
     */
    int batchSaveMessageRecords(List<MessageRecord> messageRecords);
    
    /**
     * 更新消息状态
     * @param messageId 消息ID
     * @param status 新状态：delivered（已送达）、read（已读）
     * @return 更新是否成功
     */
    boolean updateMessageStatus(String messageId, String status);
    
    /**
     * 获取统计信息
     * @param userId 用户ID（可选）
     * @param agentId 客服ID（可选）
     * @return 统计信息Map
     */
    Map<String, Object> getConversationStatistics(String userId, String agentId);
    
    /**
     * 删除会话及其相关消息记录
     * @param conversationId 会话ID
     * @return 删除是否成功
     */
    boolean deleteConversation(String conversationId);
    
    /**
     * 更新会话信息
     * @param conversation 会话对象
     */
    void updateConversation(Conversation conversation);
    
    /**
     * 获取活跃会话数量
     * @return 活跃会话数
     */
    int getActiveSessionsCount();
}