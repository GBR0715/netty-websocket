package com.example.nettywebsocket.service;

import com.example.nettywebsocket.manager.WebSocketConnectionManager;
import com.example.nettywebsocket.model.Conversation;
import com.example.nettywebsocket.model.MessageRecord;
import com.example.nettywebsocket.model.WebSocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import com.example.nettywebsocket.util.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 客服服务类，负责客服分配和会话管理
 */
@Service
public class CustomerService {

    private static final Logger logger = LoggerFactory.getLogger(CustomerService.class);

    // Redis键前缀
    private static final String REDIS_PREFIX = "websocket:";
    private static final String CUSTOMER_SERVICE_KEY = REDIS_PREFIX + "customer:service";
    private static final String CUSTOMER_SERVICE_LOAD_KEY = REDIS_PREFIX + "customer:service:load:";
    private static final String USER_CUSTOMER_SERVICE_KEY = REDIS_PREFIX + "user:customer:service:";
    private static final String CUSTOMER_SERVICE_USERS_KEY = REDIS_PREFIX + "customer:service:users:";

    @Autowired(required = false)
    private RedisUtil redisUtil;
    
    @Autowired
    private WebSocketConnectionManager connectionManager;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private ConversationService conversationService;

    // 客服同时服务的最大用户数
    @Value("${netty.websocket.maxUsersPerAgent:20}")
    private int maxUsersPerAgent;

    // 本地缓存 - 客服在线状态
    private final Map<String, Boolean> onlineAgents = new ConcurrentHashMap<>();
    
    // 本地存储 - 客服服务相关数据（Redis降级时使用）
    private final Map<String, Set<String>> localCustomerServiceUsers = new ConcurrentHashMap<>(); // 客服-用户映射
    private final Map<String, String> localUserCustomerService = new ConcurrentHashMap<>(); // 用户-客服映射
    private final Map<String, Integer> localCustomerServiceLoad = new ConcurrentHashMap<>(); // 客服负载

    /**
     * 注册客服
     * @param agentId 客服ID
     */
    public void registerAgent(String agentId) {
        try {
            if (redisUtil != null) {
                // Redis可用时使用Redis存储
                // 添加到客服列表
                redisUtil.addToSet(CUSTOMER_SERVICE_KEY, agentId);
                // 初始化客服负载为0
                redisUtil.set(CUSTOMER_SERVICE_LOAD_KEY + agentId, 0);
                logger.info("客服 {} 已注册（Redis存储）", agentId);
            } else {
                // Redis不可用时使用本地存储
                // 添加到本地客服列表
                localCustomerServiceUsers.put(agentId, new HashSet<>());
                // 初始化客服负载为0
                localCustomerServiceLoad.put(agentId, 0);
                logger.info("客服 {} 已注册（本地存储）", agentId);
            }
            
            // 更新本地缓存
            onlineAgents.put(agentId, true);
            
            // 客服上线后，自动分配等待的用户
            assignWaitingUsersToNewAgent(agentId);
        } catch (Exception e) {
            logger.error("注册客服失败", e);
        }
    }

    /**
     * 注销客服
     * @param agentId 客服ID
     */
    public void unregisterAgent(String agentId) {
        try {
            if (redisUtil != null) {
                // Redis可用时使用Redis存储
                // 从客服列表移除
                redisUtil.removeFromSet(CUSTOMER_SERVICE_KEY, agentId);
                // 移除负载记录
                redisUtil.delete(CUSTOMER_SERVICE_LOAD_KEY + agentId);
                
                // 获取该客服的所有用户并重新分配
                Set<String> userIds = redisUtil.getSetMembers(CUSTOMER_SERVICE_USERS_KEY + agentId, String.class);
                if (userIds != null) {
                    for (String userId : userIds) {
                        // 移除用户-客服映射
                        redisUtil.delete(USER_CUSTOMER_SERVICE_KEY + userId);
                        
                        // 发送客服下线通知给用户
                        try {
                            WebSocketMessage userMsg = new WebSocketMessage();
                            userMsg.setType(WebSocketMessage.TYPE_SYSTEM);
                            userMsg.setContent("您的客服已下线，正在为您重新分配...");
                            userMsg.setSenderId("system");
                            userMsg.setReceiverId(userId);
                            connectionManager.sendMessage(userId, objectMapper.writeValueAsString(userMsg));
                        } catch (Exception e) {
                            logger.error("发送客服下线通知失败", e);
                        }
                        
                        // 重新分配用户
                        assignCustomerService(userId);
                    }
                    // 清除客服用户列表
                    redisUtil.delete(CUSTOMER_SERVICE_USERS_KEY + agentId);
                }
                logger.info("客服 {} 已注销（Redis存储）", agentId);
            } else {
                // Redis不可用时使用本地存储
                // 获取该客服的所有用户并重新分配
                Set<String> userIds = localCustomerServiceUsers.get(agentId);
                if (userIds != null) {
                    for (String userId : userIds) {
                        // 移除用户-客服映射
                        localUserCustomerService.remove(userId);
                        
                        // 发送客服下线通知给用户
                        try {
                            WebSocketMessage userMsg = new WebSocketMessage();
                            userMsg.setType(WebSocketMessage.TYPE_SYSTEM);
                            userMsg.setContent("您的客服已下线，正在为您重新分配...");
                            userMsg.setSenderId("system");
                            userMsg.setReceiverId(userId);
                            connectionManager.sendMessage(userId, objectMapper.writeValueAsString(userMsg));
                        } catch (Exception e) {
                            logger.error("发送客服下线通知失败", e);
                        }
                        
                        // 重新分配用户
                        assignCustomerService(userId);
                    }
                }
                // 清除本地存储
                localCustomerServiceUsers.remove(agentId);
                localCustomerServiceLoad.remove(agentId);
                logger.info("客服 {} 已注销（本地存储）", agentId);
            }
            
            // 更新本地缓存
            onlineAgents.remove(agentId);
        } catch (Exception e) {
            logger.error("注销客服失败", e);
        }
    }

    /**
     * 分配客服给用户
     * @param userId 用户ID
     * @return 分配的客服ID
     */
    public String assignCustomerService(String userId) {
        try {
            // 检查用户是否已分配客服
            String existingAgentId = null;
            if (redisUtil != null) {
                existingAgentId = redisUtil.get(USER_CUSTOMER_SERVICE_KEY + userId,String.class);
            } else {
                existingAgentId = localUserCustomerService.get(userId);
            }
            
            if (existingAgentId != null) {
                return existingAgentId;
            }

            // 获取所有在线客服
            List<String> allAgents = null;
            if (redisUtil != null) {
                Set<String> redisAgents = redisUtil.getSetMembers(CUSTOMER_SERVICE_KEY, String.class);
                if (redisAgents != null) {
                    allAgents = new ArrayList<>(redisAgents);
                }
            } else {
                allAgents = new ArrayList<>(localCustomerServiceUsers.keySet());
            }
            
            if (allAgents == null || allAgents.isEmpty()) {
                logger.warn("暂无可用客服");
                return null;
            }

            // 按负载从小到大排序客服
            List<Map.Entry<String, Integer>> agentsWithLoad = new ArrayList<>();
            for (String agentId : allAgents) {
                Integer load = null;
                if (redisUtil != null) {
                    load = redisUtil.get(CUSTOMER_SERVICE_LOAD_KEY + agentId, Integer.class);
                } else {
                    load = localCustomerServiceLoad.get(agentId);
                }
                if (load == null) {
                    load = 0;
                }
                // 只考虑负载未达到上限的客服
                if (load < maxUsersPerAgent) {
                    agentsWithLoad.add(new AbstractMap.SimpleEntry<>(agentId, load));
                }
            }

            // 按负载排序
            agentsWithLoad.sort(Map.Entry.comparingByValue());

            if (agentsWithLoad.isEmpty()) {
                logger.warn("所有客服负载已满");
                return null;
            }

            // 选择负载最小的客服
            String selectedAgentId = agentsWithLoad.get(0).getKey();

            // 更新映射关系
            if (redisUtil != null) {
                redisUtil.set(USER_CUSTOMER_SERVICE_KEY + userId, selectedAgentId);
                redisUtil.addToSet(CUSTOMER_SERVICE_USERS_KEY + selectedAgentId, userId);
                // 增加客服负载
                redisUtil.increment(CUSTOMER_SERVICE_LOAD_KEY + selectedAgentId);
            } else {
                localUserCustomerService.put(userId, selectedAgentId);
                Set<String> users = localCustomerServiceUsers.get(selectedAgentId);
                if (users != null) {
                    users.add(userId);
                }
                // 增加客服负载
                Integer currentLoad = localCustomerServiceLoad.get(selectedAgentId);
                localCustomerServiceLoad.put(selectedAgentId, currentLoad != null ? currentLoad + 1 : 1);
            }

            // 发送通知消息
            sendAssignmentNotifications(userId, selectedAgentId);
            
            // 创建会话
            createNewConversation(userId, selectedAgentId);

            logger.info("用户 {} 已分配给客服 {}", userId, selectedAgentId);
            return selectedAgentId;
        } catch (Exception e) {
            logger.error("分配客服失败", e);
            return null;
        }
    }

    /**
     * 获取用户对应的客服
     * @param userId 用户ID
     * @return 客服ID
     */
    public String getAgentForUser(String userId) {
        try {
            if (redisUtil != null) {
                return redisUtil.get(USER_CUSTOMER_SERVICE_KEY + userId, String.class);
            } else {
                return localUserCustomerService.get(userId);
            }
        } catch (Exception e) {
            logger.error("获取用户客服失败", e);
            return null;
        }
    }

    /**
     * 获取客服的所有用户
     * @param agentId 客服ID
     * @return 用户ID列表
     */
    public List<String> getUsersForAgent(String agentId) {
        try {
            if (redisUtil != null) {
                Set<String> users = redisUtil.getSetMembers(CUSTOMER_SERVICE_USERS_KEY + agentId, String.class);
                if (users == null) {
                    return new ArrayList<>();
                }
                return new ArrayList<>(users);
            } else {
                Set<String> users = localCustomerServiceUsers.get(agentId);
                if (users == null) {
                    return new ArrayList<>();
                }
                return new ArrayList<>(users);
            }
        } catch (Exception e) {
            logger.error("获取客服用户列表失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取所有在线客服
     * @return 客服ID列表
     */
    public List<String> getAllOnlineAgents() {
        try {
            if (redisUtil != null) {
                Set<String> agents = redisUtil.getSetMembers(CUSTOMER_SERVICE_KEY, String.class);
                if (agents == null) {
                    return new ArrayList<>();
                }
                return new ArrayList<>(agents);
            } else {
                return new ArrayList<>(localCustomerServiceUsers.keySet());
            }
        } catch (Exception e) {
            logger.error("获取在线客服列表失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 创建新的会话
     * @param userId 用户ID
     * @param agentId 客服ID
     */
    private void createNewConversation(String userId, String agentId) {
        try {
            // 检查是否已有活跃会话
            Conversation activeConversation = conversationService.getActiveConversation(userId, agentId);
            if (activeConversation == null) {
                // 创建新会话
                Conversation conversation = new Conversation();
                conversation.setConversationId(UUID.randomUUID().toString());
                conversation.setCreatorId(userId);
                conversation.setReceiverId(agentId);
                conversation.setStartTime(new Date(System.currentTimeMillis()));
                conversation.setStatus("active");
                conversation.setCreatorRole("user");
                conversation.setLastMessageTime(new Date(System.currentTimeMillis()));
                conversationService.createConversation(conversation);
            }
        } catch (Exception e) {
            logger.error("创建会话失败", e);
        }
    }
    
    /**
     * 发送客服分配通知
     * @param userId 用户ID
     * @param agentId 客服ID
     */
    private void sendAssignmentNotifications(String userId, String agentId) {
        try {
            // 发送给用户的通知
            WebSocketMessage userMsg = new WebSocketMessage();
            userMsg.setType(WebSocketMessage.TYPE_CS_ASSIGN);
            userMsg.setContent("您已分配给客服: " + agentId);
            userMsg.setSenderId("system");
            userMsg.setReceiverId(userId);
            connectionManager.sendMessage(userId, objectMapper.writeValueAsString(userMsg));

            // 发送给客服的通知
            WebSocketMessage agentMsg = new WebSocketMessage();
            agentMsg.setType(WebSocketMessage.TYPE_USER_JOIN);
            agentMsg.setContent("用户 " + userId + " 已连接，等待您的服务");
            agentMsg.setSenderId("system");
            agentMsg.setReceiverId(agentId);
            connectionManager.sendMessage(agentId, objectMapper.writeValueAsString(agentMsg));
        } catch (Exception e) {
            logger.error("发送分配通知失败", e);
        }
    }

    /**
     * 发送用户离开通知
     * @param userId 用户ID
     * @param agentId 客服ID
     */
    private void sendUserLeaveNotification(String userId, String agentId) {
        try {
            WebSocketMessage agentMsg = new WebSocketMessage();
            agentMsg.setType(WebSocketMessage.TYPE_USER_LEAVE);
            agentMsg.setContent("用户 " + userId + " 已离开");
            agentMsg.setSenderId("system");
            agentMsg.setReceiverId(agentId);
            connectionManager.sendMessage(agentId, objectMapper.writeValueAsString(agentMsg));
        } catch (Exception e) {
            logger.error("发送用户离开通知失败", e);
        }
    }

    /**
     * 处理用户消息，保存到消息记录
     * @param message WebSocket消息
     */
    public void handleUserMessage(WebSocketMessage message) {
        try {
            String userId = message.getSenderId();
            String agentId = getAgentForUser(userId);
            
            if (agentId != null) {
                // 确保会话存在
                Conversation conversation = conversationService.getActiveConversation(userId, agentId);
                if (conversation != null) {
                    // 保存消息记录
                    saveMessageRecord(message, conversation, "user");
                    
                    // 更新会话最后活动时间
                    conversation.setLastMessageTime(new Date(System.currentTimeMillis()));
                    conversationService.updateConversation(conversation);
                }
            }
        } catch (Exception e) {
            logger.error("处理用户消息记录失败", e);
        }
    }
    
    /**
     * 处理客服消息，保存到消息记录
     * @param message WebSocket消息
     */
    public void handleAgentMessage(WebSocketMessage message) {
        try {
            String agentId = message.getSenderId();
            String userId = message.getReceiverId();
            
            // 确保会话存在
            Conversation conversation = conversationService.getActiveConversation(userId, agentId);
            if (conversation != null) {
                // 保存消息记录
                saveMessageRecord(message, conversation, "agent");
                
                // 更新会话最后活动时间
                conversation.setLastMessageTime(new Date(System.currentTimeMillis()));
                conversationService.updateConversation(conversation);
            }
        } catch (Exception e) {
            logger.error("处理客服消息记录失败", e);
        }
    }
    
    /**
     * 保存消息记录
     */
    private void saveMessageRecord(WebSocketMessage message, Conversation conversation, String senderRole) {
        try {
            MessageRecord record = new MessageRecord();
            record.setRecordId(UUID.randomUUID().toString());
            record.setConversationId(conversation.getConversationId());
            // 处理messageId可能为null的情况
            String messageId = message.getMessageId();
            record.setMessageId(messageId != null ? messageId : UUID.randomUUID().toString());
            record.setSenderId(message.getSenderId());
            record.setReceiverId(message.getReceiverId());
            record.setContent(message.getContent());
            record.setMessageType(message.getType());
            record.setSenderRole(senderRole);
            // 处理timestamp可能为null的情况
            Long timestamp = message.getTimestamp();
            record.setSendTime(timestamp != null ? new Date(timestamp) : new Date(System.currentTimeMillis()));
            record.setStatus("sent");
            
            conversationService.saveMessageRecord(record);
        } catch (Exception e) {
            logger.error("保存消息记录失败", e);
        }
    }
    
    /**
     * 用户断开连接时，解除与客服的关联
     * @param userId 用户ID
     */
    public void removeUserFromAgent(String userId) {
        try {
            String agentId = null;
            if (redisUtil != null) {
                agentId = redisUtil.get(USER_CUSTOMER_SERVICE_KEY + userId, String.class);
            } else {
                agentId = localUserCustomerService.get(userId);
            }
            
            if (agentId != null) {
                if (redisUtil != null) {
                    // 移除用户-客服映射
                    redisUtil.delete(USER_CUSTOMER_SERVICE_KEY + userId);
                    // 从客服用户列表中移除
                    redisUtil.removeFromSet(CUSTOMER_SERVICE_USERS_KEY + agentId, userId);
                    // 减少客服负载
                    redisUtil.decrement(CUSTOMER_SERVICE_LOAD_KEY + agentId);
                } else {
                    // 移除用户-客服映射
                    localUserCustomerService.remove(userId);
                    // 从客服用户列表中移除
                    Set<String> users = localCustomerServiceUsers.get(agentId);
                    if (users != null) {
                        users.remove(userId);
                    }
                    // 减少客服负载
                    Integer currentLoad = localCustomerServiceLoad.get(agentId);
                    if (currentLoad != null && currentLoad > 0) {
                        localCustomerServiceLoad.put(agentId, currentLoad - 1);
                    }
                }
                
                // 发送用户离开通知给客服
                sendUserLeaveNotification(userId, agentId);
                
                // 更新会话最后活动时间
                try {
                    Conversation activeConversation = conversationService.getActiveConversation(userId, agentId);
                    if (activeConversation != null) {
                        activeConversation.setLastMessageTime(new Date(System.currentTimeMillis()));
                        conversationService.updateConversation(activeConversation);
                    }
                } catch (Exception e) {
                    logger.error("更新会话最后活动时间失败", e);
                }
                
                logger.info("用户 {} 已从客服 {} 服务列表中移除", userId, agentId);
            }
        } catch (Exception e) {
            logger.error("移除用户客服关联失败", e);
        }
    }

    /**
     * 检查是否为客服
     * @param userId 用户ID
     * @return 是否为客服
     */
    public boolean isAgent(String userId) {
        try {
            if (redisUtil != null) {
                return redisUtil.isMemberOfSet(CUSTOMER_SERVICE_KEY, userId);
            } else {
                return localCustomerServiceUsers.containsKey(userId);
            }
        } catch (Exception e) {
            logger.error("检查用户角色失败", e);
            return false;
        }
    }

    /**
     * 获取客服负载
     * @param agentId 客服ID
     * @return 当前服务的用户数
     */
    public int getAgentLoad(String agentId) {
        try {
            if (redisUtil != null) {
                Integer load = redisUtil.get(CUSTOMER_SERVICE_LOAD_KEY + agentId, Integer.class);
                return load != null ? load : 0;
            } else {
                Integer load = localCustomerServiceLoad.get(agentId);
                return load != null ? load : 0;
            }
        } catch (Exception e) {
            logger.error("获取客服负载失败", e);
            return 0;
        }
    }

    /**
     * 设置客服最大服务人数
     * @param maxUsersPerAgent 最大服务人数
     */
    public void setMaxUsersPerAgent(int maxUsersPerAgent) {
        this.maxUsersPerAgent = maxUsersPerAgent;
    }
    
    /**
     * 获取当前活跃会话数
     */
    public int getActiveSessionsCount() {
        try {
            return conversationService.getActiveSessionsCount();
        } catch (Exception e) {
            logger.error("获取活跃会话数失败", e);
            return 0;
        }
    }
    
    /**
     * 结束会话
     */
    public Conversation endConversation(String conversationId, String endType) {
        try {
            return conversationService.endConversation(conversationId, endType);
        } catch (Exception e) {
            logger.error("结束会话失败", e);
            return null;
        }
    }
    
    /**
     * 客服上线后，自动分配等待的用户给新客服
     * @param newAgentId 新上线的客服ID
     */
    private void assignWaitingUsersToNewAgent(String newAgentId) {
        try {
            // 获取所有在线用户
            Map<String, Channel> allConnections = connectionManager.getAllConnections();
            if (allConnections == null || allConnections.isEmpty()) {
                return;
            }
            
            // 查找没有分配客服的在线用户（等待中的用户）
            List<String> waitingUsers = new ArrayList<>();
            for (Map.Entry<String, Channel> entry : allConnections.entrySet()) {
                String userId = entry.getKey();
                
                // 检查用户是否已分配客服
                String assignedAgentId = getAgentForUser(userId);
                if (assignedAgentId == null) {
                    // 检查用户是否为普通用户（不是客服）
                    if (!isAgent(userId)) {
                        waitingUsers.add(userId);
                    }
                }
            }
            
            if (waitingUsers.isEmpty()) {
                logger.info("客服 {} 上线，当前没有等待的用户", newAgentId);
                return;
            }
            
            logger.info("客服 {} 上线，发现 {} 个等待的用户，开始分配", newAgentId, waitingUsers.size());
            
            // 按顺序分配用户给新客服，但不超过客服的最大负载
            int assignedCount = 0;
            int currentLoad = getAgentLoad(newAgentId);
            
            for (String userId : waitingUsers) {
                // 检查客服是否还有容量
                if (currentLoad >= maxUsersPerAgent) {
                    logger.info("客服 {} 负载已满（{}/{}），停止分配", newAgentId, currentLoad, maxUsersPerAgent);
                    break;
                }
                
                // 分配用户给新客服
                assignUserToAgent(userId, newAgentId);
                assignedCount++;
                currentLoad++;
                
                logger.info("将等待用户 {} 分配给新上线客服 {}", userId, newAgentId);
            }
            
            logger.info("客服 {} 上线后，成功分配了 {} 个等待用户", newAgentId, assignedCount);
            
        } catch (Exception e) {
            logger.error("分配等待用户给新客服失败", e);
        }
    }
    
    /**
     * 直接将用户分配给指定客服（不进行负载均衡）
     * @param userId 用户ID
     * @param agentId 客服ID
     */
    private void assignUserToAgent(String userId, String agentId) {
        try {
            // 检查用户是否已分配客服
            String existingAgentId = null;
            if (redisUtil != null) {
                existingAgentId = redisUtil.get(USER_CUSTOMER_SERVICE_KEY + userId,String.class);
            } else {
                existingAgentId = localUserCustomerService.get(userId);
            }
            
            if (existingAgentId != null) {
                logger.warn("用户 {} 已分配给客服 {}，无需重新分配", userId, existingAgentId);
                return;
            }
            
            // 更新映射关系
            if (redisUtil != null) {
                redisUtil.set(USER_CUSTOMER_SERVICE_KEY + userId, agentId);
                redisUtil.addToSet(CUSTOMER_SERVICE_USERS_KEY + agentId, userId);
                
                // 增加客服负载
                redisUtil.increment(CUSTOMER_SERVICE_LOAD_KEY + agentId);
            } else {
                // 使用本地存储
                localUserCustomerService.put(userId, agentId);
                Set<String> users = localCustomerServiceUsers.get(agentId);
                if (users != null) {
                    users.add(userId);
                }
                // 增加客服负载
                Integer currentLoad = localCustomerServiceLoad.get(agentId);
                localCustomerServiceLoad.put(agentId, currentLoad != null ? currentLoad + 1 : 1);
            }
            
            // 发送通知消息
            sendAssignmentNotifications(userId, agentId);
            
            // 创建会话
            createNewConversation(userId, agentId);
            
            logger.info("用户 {} 已直接分配给客服 {}", userId, agentId);
            
        } catch (Exception e) {
            logger.error("直接分配用户给客服失败", e);
        }
    }
}