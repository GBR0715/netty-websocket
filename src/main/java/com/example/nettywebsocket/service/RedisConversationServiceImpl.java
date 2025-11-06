package com.example.nettywebsocket.service;

import com.example.nettywebsocket.model.Conversation;
import com.example.nettywebsocket.model.MessageRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 基于Redis的会话历史服务实现
 * 提供基本的会话和消息记录存储功能
 */
@Service
public class RedisConversationServiceImpl implements ConversationService {

    private static final Logger logger = LoggerFactory.getLogger(RedisConversationServiceImpl.class);

    // Redis key前缀
    private static final String CONVERSATION_KEY = "conversation:";
    private static final String MESSAGE_KEY = "messages:";
    private static final String USER_CONVERSATIONS_KEY = "user_conversations:";
    private static final String AGENT_CONVERSATIONS_KEY = "agent_conversations:";
    private static final String ACTIVE_CONVERSATION_KEY = "active_conversation:";
    private static final String ACTIVE_CONVERSATIONS_KEY = "active_conversations";
    private static final long CONVERSATION_TTL = 7 * 24 * 60 * 60; // 7天

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public Conversation createConversation(Conversation conversation) {
        try {
            // 生成会话ID
            if (conversation.getConversationId() == null) {
                conversation.setConversationId(UUID.randomUUID().toString());
            }

            // 保存会话信息
            String conversationKey = CONVERSATION_KEY + conversation.getConversationId();
            redisTemplate.opsForValue().set(conversationKey, objectMapper.writeValueAsString(conversation));
            // 设置过期时间为7天
            redisTemplate.expire(conversationKey, 7, TimeUnit.DAYS);

            // 记录用户的会话列表
            redisTemplate.opsForZSet().add(
                    USER_CONVERSATIONS_KEY + conversation.getCreatorId(),
                    conversation.getConversationId(),
                    System.currentTimeMillis()
            );
            redisTemplate.opsForZSet().add(
                    USER_CONVERSATIONS_KEY + conversation.getReceiverId(),
                    conversation.getConversationId(),
                    System.currentTimeMillis()
            );

            // 记录客服的会话列表（如果接收者是客服）
            if ("AGENT".equals(conversation.getCreatorRole())) {
                redisTemplate.opsForZSet().add(
                        AGENT_CONVERSATIONS_KEY + conversation.getCreatorId(),
                        conversation.getConversationId(),
                        System.currentTimeMillis()
                );
            } else {
                redisTemplate.opsForZSet().add(
                        AGENT_CONVERSATIONS_KEY + conversation.getReceiverId(),
                        conversation.getConversationId(),
                        System.currentTimeMillis()
                );
            }

            // 记录活跃会话
            String activeKey = ACTIVE_CONVERSATION_KEY + conversation.getCreatorId() + ":" + conversation.getReceiverId();
            redisTemplate.opsForValue().set(activeKey, conversation.getConversationId());

            logger.info("创建新会话: {}", conversation.getConversationId());
            return conversation;
        } catch (Exception e) {
            logger.error("创建会话失败", e);
            throw new RuntimeException("创建会话失败", e);
        }
    }

    @Override
    public Conversation getConversationById(String conversationId) {
        try {
            String conversationKey = CONVERSATION_KEY + conversationId;
            String json = (String) redisTemplate.opsForValue().get(conversationKey);
            return json != null ? objectMapper.readValue(json, Conversation.class) : null;
        } catch (Exception e) {
            logger.error("获取会话失败: {}", conversationId, e);
            return null;
        }
    }

    @Override
    public Conversation endConversation(String conversationId, String endType) {
        try {
            Conversation conversation = getConversationById(conversationId);
            if (conversation != null) {
                conversation.setEndTime(new Date());
                conversation.setEndType(endType);
                conversation.setStatus("closed");

                // 更新会话信息
                String conversationKey = CONVERSATION_KEY + conversationId;
                redisTemplate.opsForValue().set(conversationKey, objectMapper.writeValueAsString(conversation));

                // 移除活跃会话标记
                String activeKey = ACTIVE_CONVERSATION_KEY + conversation.getCreatorId() + ":" + conversation.getReceiverId();
                redisTemplate.delete(activeKey);

                logger.info("结束会话: {}, 结束类型: {}", conversationId, endType);
            }
            return conversation;
        } catch (Exception e) {
            logger.error("结束会话失败: {}", conversationId, e);
            throw new RuntimeException("结束会话失败", e);
        }
    }

    @Override
    public List<Conversation> getUserConversations(String userId, int page, int size) {
        try {
            String key = USER_CONVERSATIONS_KEY + userId;
            int start = (page - 1) * size;
            int end = page * size - 1;

            // 获取会话ID列表（按时间倒序）
            Set<Object> objectIds = redisTemplate.opsForZSet().reverseRange(key, start, end);
            Set<String> conversationIds = objectIds != null ? 
                    objectIds.stream().map(Object::toString).collect(Collectors.toSet()) : 
                    new HashSet<>();
            if (conversationIds.isEmpty()) {
                return Collections.emptyList();
            }

            // 获取会话详情
            return conversationIds.stream()
                    .map(this::getConversationById)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("获取用户会话列表失败: {}", userId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Conversation> getAgentConversations(String agentId, int page, int size) {
        try {
            String key = AGENT_CONVERSATIONS_KEY + agentId;
            int start = (page - 1) * size;
            int end = page * size - 1;

            // 获取会话ID列表（按时间倒序）
            Set<Object> objectIds = redisTemplate.opsForZSet().reverseRange(key, start, end);
            Set<String> conversationIds = objectIds != null ? 
                    objectIds.stream().map(Object::toString).collect(Collectors.toSet()) : 
                    new HashSet<>();
            if (conversationIds.isEmpty()) {
                return Collections.emptyList();
            }

            // 获取会话详情
            return conversationIds.stream()
                    .map(this::getConversationById)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("获取客服会话列表失败: {}", agentId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public Conversation getActiveConversation(String userId, String agentId) {
        try {
            String activeKey = ACTIVE_CONVERSATION_KEY + userId + ":" + agentId;
            String conversationId = (String) redisTemplate.opsForValue().get(activeKey);
            if (conversationId == null) {
                // 尝试反向键名
                activeKey = ACTIVE_CONVERSATION_KEY + agentId + ":" + userId;
                conversationId = (String) redisTemplate.opsForValue().get(activeKey);
            }
            return conversationId != null ? getConversationById(conversationId) : null;
        } catch (Exception e) {
            logger.error("获取活跃会话失败: user={}, agent={}", userId, agentId, e);
            return null;
        }
    }

    @Override
    public MessageRecord saveMessageRecord(MessageRecord messageRecord) {
        try {
            // 生成记录ID
            if (messageRecord.getRecordId() == null) {
                messageRecord.setRecordId(UUID.randomUUID().toString());
            }

            // 保存消息记录
            String messageKey = MESSAGE_KEY + messageRecord.getConversationId();
            redisTemplate.opsForList().leftPush(messageKey, objectMapper.writeValueAsString(messageRecord));
            // 设置过期时间为7天
            redisTemplate.expire(messageKey, 7, TimeUnit.DAYS);

            // 更新会话的最后消息时间
            Conversation conversation = getConversationById(messageRecord.getConversationId());
            if (conversation != null) {
                conversation.setLastMessageTime(new Date());
                String conversationKey = CONVERSATION_KEY + conversation.getConversationId();
                redisTemplate.opsForValue().set(conversationKey, objectMapper.writeValueAsString(conversation));
            }

            logger.debug("保存消息记录: {}, 会话ID: {}", messageRecord.getRecordId(), messageRecord.getConversationId());
            return messageRecord;
        } catch (Exception e) {
            logger.error("保存消息记录失败", e);
            throw new RuntimeException("保存消息记录失败", e);
        }
    }

    @Override
    public List<MessageRecord> getConversationMessages(String conversationId, int page, int size) {
        try {
            String messageKey = MESSAGE_KEY + conversationId;
            int start = (page - 1) * size;
            int end = page * size - 1;

            // 获取消息记录列表
            List<Object> messages = redisTemplate.opsForList().range(messageKey, start, end);
            if (messages == null || messages.isEmpty()) {
                return Collections.emptyList();
            }

            // 转换为MessageRecord对象
            return messages.stream()
                    .map(msg -> {
                        try {
                            return objectMapper.readValue((String) msg, MessageRecord.class);
                        } catch (Exception e) {
                            logger.error("解析消息记录失败", e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("获取会话消息失败: {}", conversationId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public int batchSaveMessageRecords(List<MessageRecord> messageRecords) {
        if (messageRecords == null || messageRecords.isEmpty()) {
            return 0;
        }

        int savedCount = 0;
        for (MessageRecord record : messageRecords) {
            try {
                saveMessageRecord(record);
                savedCount++;
            } catch (Exception e) {
                logger.error("批量保存消息记录失败: {}", record.getMessageId(), e);
            }
        }
        return savedCount;
    }

    @Override
    public boolean updateMessageStatus(String messageId, String status) {
        // 注意：在Redis实现中，由于消息存储在列表中，更新单条消息需要重新保存整个列表
        // 这里为简化实现，仅记录日志
        logger.info("更新消息状态: {}, 状态: {}", messageId, status);
        return true;
    }

    @Override
    public Map<String, Object> getConversationStatistics(String userId, String agentId) {
        Map<String, Object> stats = new HashMap<>();
        try {
            // 获取用户的会话总数
            String userKey = USER_CONVERSATIONS_KEY + userId;
            Long userConversationCount = redisTemplate.opsForZSet().size(userKey);
            stats.put("userConversationCount", userConversationCount != null ? userConversationCount : 0);

            // 获取客服的会话总数
            if (agentId != null) {
                String agentKey = AGENT_CONVERSATIONS_KEY + agentId;
                Long agentConversationCount = redisTemplate.opsForZSet().size(agentKey);
                stats.put("agentConversationCount", agentConversationCount != null ? agentConversationCount : 0);
            }

            // 更多统计信息可以根据需要添加
        } catch (Exception e) {
            logger.error("获取会话统计信息失败", e);
        }
        return stats;
    }

    @Override
    public boolean deleteConversation(String conversationId) {
        try {
            Conversation conversation = getConversationById(conversationId);
            if (conversation != null) {
                // 删除会话信息
                String conversationKey = CONVERSATION_KEY + conversationId;
                redisTemplate.delete(conversationKey);

                // 从用户会话列表中移除
                redisTemplate.opsForZSet().remove(USER_CONVERSATIONS_KEY + conversation.getCreatorId(), conversationId);
                redisTemplate.opsForZSet().remove(USER_CONVERSATIONS_KEY + conversation.getReceiverId(), conversationId);

                // 从客服会话列表中移除
                if ("AGENT".equals(conversation.getCreatorRole())) {
                    redisTemplate.opsForZSet().remove(AGENT_CONVERSATIONS_KEY + conversation.getCreatorId(), conversationId);
                } else {
                    redisTemplate.opsForZSet().remove(AGENT_CONVERSATIONS_KEY + conversation.getReceiverId(), conversationId);
                }

                // 删除活跃会话标记
                String activeKey = ACTIVE_CONVERSATION_KEY + conversation.getCreatorId() + ":" + conversation.getReceiverId();
                redisTemplate.delete(activeKey);

                // 删除消息记录
                String messageKey = MESSAGE_KEY + conversationId;
                redisTemplate.delete(messageKey);

                logger.info("删除会话: {}", conversationId);
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("删除会话失败: {}", conversationId, e);
            return false;
        }
    }
    
    @Override
    public void updateConversation(Conversation conversation) {
        try {
            redisTemplate.opsForValue().set(CONVERSATION_KEY + conversation.getConversationId(), conversation);
        } catch (Exception e) {
            logger.error("更新会话失败", e);
        }
    }
    
    @Override
    public int getActiveSessionsCount() {
        try {
            Set<Object> activeConversations = redisTemplate.opsForSet().members(ACTIVE_CONVERSATIONS_KEY);
            return activeConversations != null ? activeConversations.size() : 0;
        } catch (Exception e) {
            logger.error("获取活跃会话数失败", e);
            return 0;
        }
    }
}