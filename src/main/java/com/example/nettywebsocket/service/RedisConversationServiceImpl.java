package com.example.nettywebsocket.service;

import com.example.nettywebsocket.model.Conversation;
import com.example.nettywebsocket.model.MessageRecord;
import com.example.nettywebsocket.util.RedisUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
    private RedisUtil redisUtil;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    // 本地内存存储（当Redis不可用时使用）
    private final Map<String, Conversation> localConversations = new ConcurrentHashMap<>();
    private final Map<String, List<MessageRecord>> localMessages = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> userConversations = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> agentConversations = new ConcurrentHashMap<>();
    private final Map<String, String> activeConversations = new ConcurrentHashMap<>();

    @Override
    public Conversation createConversation(Conversation conversation) {
        try {
            // 生成会话ID
            if (conversation.getConversationId() == null) {
                conversation.setConversationId(UUID.randomUUID().toString());
            }

            if (redisUtil.isRedisAvailable()) {
                // 保存会话信息到Redis
                String conversationKey = CONVERSATION_KEY + conversation.getConversationId();
                redisUtil.set(conversationKey, objectMapper.writeValueAsString(conversation), 7, TimeUnit.DAYS);

                // 记录用户的会话列表
                redisUtil.addToSortedSet(
                        USER_CONVERSATIONS_KEY + conversation.getCreatorId(),
                        conversation.getConversationId(),
                        System.currentTimeMillis()
                );
                redisUtil.addToSortedSet(
                        USER_CONVERSATIONS_KEY + conversation.getReceiverId(),
                        conversation.getConversationId(),
                        System.currentTimeMillis()
                );

                // 记录客服的会话列表（如果接收者是客服）
                if ("AGENT".equals(conversation.getCreatorRole())) {
                    redisUtil.addToSortedSet(
                            AGENT_CONVERSATIONS_KEY + conversation.getCreatorId(),
                            conversation.getConversationId(),
                            System.currentTimeMillis()
                    );
                } else {
                    redisUtil.addToSortedSet(
                            AGENT_CONVERSATIONS_KEY + conversation.getReceiverId(),
                            conversation.getConversationId(),
                            System.currentTimeMillis()
                    );
                }

                // 记录活跃会话
                String activeKey = ACTIVE_CONVERSATION_KEY + conversation.getCreatorId() + ":" + conversation.getReceiverId();
                redisUtil.set(activeKey, conversation.getConversationId());

                logger.info("创建新会话: {}", conversation.getConversationId());
            } else {
                // 使用本地内存存储
                localConversations.put(conversation.getConversationId(), conversation);
                
                // 记录用户的会话列表
                userConversations.computeIfAbsent(conversation.getCreatorId(), k -> ConcurrentHashMap.newKeySet()).add(conversation.getConversationId());
                userConversations.computeIfAbsent(conversation.getReceiverId(), k -> ConcurrentHashMap.newKeySet()).add(conversation.getConversationId());
                
                // 记录客服的会话列表
                if ("AGENT".equals(conversation.getCreatorRole())) {
                    agentConversations.computeIfAbsent(conversation.getCreatorId(), k -> ConcurrentHashMap.newKeySet()).add(conversation.getConversationId());
                } else {
                    agentConversations.computeIfAbsent(conversation.getReceiverId(), k -> ConcurrentHashMap.newKeySet()).add(conversation.getConversationId());
                }
                
                // 记录活跃会话
                String activeKey = conversation.getCreatorId() + ":" + conversation.getReceiverId();
                activeConversations.put(activeKey, conversation.getConversationId());
                
                logger.info("创建新会话（本地存储）: {}", conversation.getConversationId());
            }
            
            return conversation;
        } catch (Exception e) {
            logger.error("创建会话失败", e);
            throw new RuntimeException("创建会话失败", e);
        }
    }

    @Override
    public Conversation getConversationById(String conversationId) {
        try {
            if (redisUtil.isRedisAvailable()) {
                String conversationKey = CONVERSATION_KEY + conversationId;
                String json = redisUtil.get(conversationKey,String.class);
                return json != null ? objectMapper.readValue(json, Conversation.class) : null;
            } else {
                // 使用本地内存存储
                return localConversations.get(conversationId);
            }
        } catch (Exception e) {
            logger.error("获取会话失败: {}", conversationId, e);
            throw new RuntimeException("获取会话失败", e);
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
                redisUtil.set(conversationKey, objectMapper.writeValueAsString(conversation));

                // 移除活跃会话标记
                String activeKey = ACTIVE_CONVERSATION_KEY + conversation.getCreatorId() + ":" + conversation.getReceiverId();
                redisUtil.delete(activeKey);

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
            if (redisUtil.isRedisAvailable()) {
                String key = USER_CONVERSATIONS_KEY + userId;
                int start = (page - 1) * size;
                int end = page * size - 1;

                // 获取会话ID列表（按时间倒序）
                Set<Object> objectIds = redisUtil.getSortedSetReverseRange(key, start, end);
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
            } else {
                // 使用本地内存存储
                Set<String> conversationIds = userConversations.get(userId);
                if (conversationIds == null || conversationIds.isEmpty()) {
                    return Collections.emptyList();
                }
                
                // 分页逻辑
                List<String> conversationIdList = new ArrayList<>(conversationIds);
                int startIndex = (page - 1) * size;
                int endIndex = Math.min(page * size, conversationIdList.size());
                
                if (startIndex >= conversationIdList.size()) {
                    return Collections.emptyList();
                }
                
                List<Conversation> conversations = new ArrayList<>();
                for (int i = startIndex; i < endIndex; i++) {
                    String conversationId = conversationIdList.get(i);
                    Conversation conversation = localConversations.get(conversationId);
                    if (conversation != null) {
                        conversations.add(conversation);
                    }
                }
                
                return conversations;
            }
        } catch (Exception e) {
            logger.error("获取用户会话列表失败: {}", userId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Conversation> getAgentConversations(String agentId, int page, int size) {
        try {
            if (redisUtil.isRedisAvailable()) {
                String key = AGENT_CONVERSATIONS_KEY + agentId;
                int start = (page - 1) * size;
                int end = page * size - 1;

                // 获取会话ID列表（按时间倒序）
                Set<Object> objectIds = redisUtil.getSortedSetReverseRange(key, start, end);
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
            } else {
                // 使用本地内存存储
                Set<String> conversationIds = agentConversations.get(agentId);
                if (conversationIds == null || conversationIds.isEmpty()) {
                    return Collections.emptyList();
                }
                
                // 分页逻辑
                List<String> conversationIdList = new ArrayList<>(conversationIds);
                int startIndex = (page - 1) * size;
                int endIndex = Math.min(page * size, conversationIdList.size());
                
                if (startIndex >= conversationIdList.size()) {
                    return Collections.emptyList();
                }
                
                List<Conversation> conversations = new ArrayList<>();
                for (int i = startIndex; i < endIndex; i++) {
                    String conversationId = conversationIdList.get(i);
                    Conversation conversation = localConversations.get(conversationId);
                    if (conversation != null) {
                        conversations.add(conversation);
                    }
                }
                
                return conversations;
            }
        } catch (Exception e) {
            logger.error("获取客服会话列表失败: {}", agentId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public Conversation getActiveConversation(String userId, String agentId) {
        try {
            if (redisUtil.isRedisAvailable()) {
                String activeKey = ACTIVE_CONVERSATION_KEY + userId + ":" + agentId;
                String conversationId = redisUtil.get(activeKey,String.class);
                if (conversationId == null) {
                    // 尝试反向键名
                    activeKey = ACTIVE_CONVERSATION_KEY + agentId + ":" + userId;
                    conversationId = redisUtil.get(activeKey,String.class);
                }
                return conversationId != null ? getConversationById(conversationId) : null;
            } else {
                // 使用本地内存存储
                String activeKey1 = userId + ":" + agentId;
                String activeKey2 = agentId + ":" + userId;
                
                String conversationId = activeConversations.get(activeKey1);
                if (conversationId == null) {
                    conversationId = activeConversations.get(activeKey2);
                }
                
                return conversationId != null ? localConversations.get(conversationId) : null;
            }
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

            if (redisUtil.isRedisAvailable()) {
                // 保存消息记录到Redis
                String messageKey = MESSAGE_KEY + messageRecord.getConversationId();
                redisUtil.leftPushToList(messageKey, objectMapper.writeValueAsString(messageRecord));
                redisUtil.expire(messageKey, 7, TimeUnit.DAYS);

                // 更新会话的最后消息时间
                Conversation conversation = getConversationById(messageRecord.getConversationId());
                if (conversation != null) {
                    conversation.setLastMessageTime(new Date());
                    String conversationKey = CONVERSATION_KEY + conversation.getConversationId();
                    redisUtil.set(conversationKey, objectMapper.writeValueAsString(conversation));
                }

                logger.debug("保存消息记录: {}, 会话ID: {}", messageRecord.getRecordId(), messageRecord.getConversationId());
            } else {
                // 使用本地内存存储
                localMessages.computeIfAbsent(messageRecord.getConversationId(), k -> new ArrayList<>()).add(0, messageRecord);
                
                // 更新会话的最后消息时间
                Conversation conversation = localConversations.get(messageRecord.getConversationId());
                if (conversation != null) {
                    conversation.setLastMessageTime(new Date());
                }
                
                logger.debug("保存消息记录（本地存储）: {}, 会话ID: {}", messageRecord.getRecordId(), messageRecord.getConversationId());
            }
            
            return messageRecord;
        } catch (Exception e) {
            logger.error("保存消息记录失败", e);
            throw new RuntimeException("保存消息记录失败", e);
        }
    }

    @Override
    public List<MessageRecord> getConversationMessages(String conversationId, int page, int size) {
        try {
            if (redisUtil.isRedisAvailable()) {
                String messageKey = MESSAGE_KEY + conversationId;
                int start = (page - 1) * size;
                int end = page * size - 1;

                // 获取消息记录列表
                List<Object> messages = redisUtil.getListRange(messageKey, start, end);
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
            } else {
                // 使用本地内存存储
                List<MessageRecord> messages = localMessages.get(conversationId);
                if (messages == null || messages.isEmpty()) {
                    return Collections.emptyList();
                }
                
                // 分页逻辑
                int start = (page - 1) * size;
                int end = Math.min(page * size, messages.size());
                
                if (start >= messages.size()) {
                    return Collections.emptyList();
                }
                
                return messages.subList(start, end);
            }
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
            if (redisUtil.isRedisAvailable()) {
                // 获取用户的会话总数
                String userKey = USER_CONVERSATIONS_KEY + userId;
                Long userConversationCount = redisUtil.getSortedSetSize(userKey);
                stats.put("userConversationCount", userConversationCount != null ? userConversationCount : 0);

                // 获取客服的会话总数
                if (agentId != null) {
                    String agentKey = AGENT_CONVERSATIONS_KEY + agentId;
                    Long agentConversationCount = redisUtil.getSortedSetSize(agentKey);
                    stats.put("agentConversationCount", agentConversationCount != null ? agentConversationCount : 0);
                }
            } else {
                // 使用本地内存存储
                Set<String> userConversationIds = userConversations.get(userId);
                stats.put("userConversationCount", userConversationIds != null ? userConversationIds.size() : 0);

                // 获取客服的会话总数
                if (agentId != null) {
                    Set<String> agentConversationIds = agentConversations.get(agentId);
                    stats.put("agentConversationCount", agentConversationIds != null ? agentConversationIds.size() : 0);
                }
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
                if (redisUtil.isRedisAvailable()) {
                    // 删除会话信息
                    String conversationKey = CONVERSATION_KEY + conversationId;
                    redisUtil.delete(conversationKey);

                    // 从用户会话列表中移除
                    redisUtil.remove(USER_CONVERSATIONS_KEY + conversation.getCreatorId(), conversationId);
                    redisUtil.remove(USER_CONVERSATIONS_KEY + conversation.getReceiverId(), conversationId);

                    // 从客服会话列表中移除
                    if ("AGENT".equals(conversation.getCreatorRole())) {
                        redisUtil.remove(AGENT_CONVERSATIONS_KEY + conversation.getCreatorId(), conversationId);
                    } else {
                        redisUtil.remove(AGENT_CONVERSATIONS_KEY + conversation.getReceiverId(), conversationId);
                    }

                    // 删除活跃会话标记
                    String activeKey = ACTIVE_CONVERSATION_KEY + conversation.getCreatorId() + ":" + conversation.getReceiverId();
                    redisUtil.delete(activeKey);

                    // 删除消息记录
                    String messageKey = MESSAGE_KEY + conversationId;
                    redisUtil.delete(messageKey);

                    logger.info("删除会话: {}", conversationId);
                } else {
                    // 使用本地内存存储
                    localConversations.remove(conversationId);
                    
                    // 从用户会话列表中移除
                    Set<String> creatorConversations = userConversations.get(conversation.getCreatorId());
                    if (creatorConversations != null) {
                        creatorConversations.remove(conversationId);
                    }
                    
                    Set<String> receiverConversations = userConversations.get(conversation.getReceiverId());
                    if (receiverConversations != null) {
                        receiverConversations.remove(conversationId);
                    }
                    
                    // 从客服会话列表中移除
                    if ("AGENT".equals(conversation.getCreatorRole())) {
                        Set<String> agentConvs = agentConversations.get(conversation.getCreatorId());
                        if (agentConvs != null) {
                            agentConvs.remove(conversationId);
                        }
                    } else {
                        Set<String> agentConvs = agentConversations.get(conversation.getReceiverId());
                        if (agentConvs != null) {
                            agentConvs.remove(conversationId);
                        }
                    }
                    
                    // 删除活跃会话标记
                    String activeKey1 = conversation.getCreatorId() + ":" + conversation.getReceiverId();
                    String activeKey2 = conversation.getReceiverId() + ":" + conversation.getCreatorId();
                    activeConversations.remove(activeKey1);
                    activeConversations.remove(activeKey2);
                    
                    // 删除消息记录
                    localMessages.remove(conversationId);
                    
                    logger.info("删除会话（本地存储）: {}", conversationId);
                }
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
            if (redisUtil.isRedisAvailable()) {
                redisUtil.set(CONVERSATION_KEY + conversation.getConversationId(), conversation);
            } else {
                // 使用本地内存存储
                localConversations.put(conversation.getConversationId(), conversation);
            }
        } catch (Exception e) {
            logger.error("更新会话失败", e);
        }
    }
    
    @Override
    public int getActiveSessionsCount() {
        try {
            if (redisUtil.isRedisAvailable()) {
                Set<Object> activeConversations = redisUtil.getSortedSetMembers(ACTIVE_CONVERSATIONS_KEY);
                return activeConversations != null ? activeConversations.size() : 0;
            } else {
                // 使用本地内存存储
                return activeConversations.size();
            }
        } catch (Exception e) {
            logger.error("获取活跃会话数失败", e);
            return 0;
        }
    }
}