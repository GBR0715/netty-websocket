package com.example.nettywebsocket.controller;

import com.example.nettywebsocket.model.Conversation;
import com.example.nettywebsocket.model.MessageRecord;
import com.example.nettywebsocket.service.ConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话历史控制器
 * 提供会话和消息记录的REST API接口
 */
@RestController
@RequestMapping("/api/conversation")
public class ConversationController {

    private static final Logger logger = LoggerFactory.getLogger(ConversationController.class);

    @Autowired
    private ConversationService conversationService;

    /**
     * 创建新会话
     */
    @PostMapping("/create")
    public Map<String, Object> createConversation(@RequestBody Conversation conversation) {
        Map<String, Object> result = new HashMap<>();
        try {
            Conversation createdConversation = conversationService.createConversation(conversation);
            result.put("success", true);
            result.put("data", createdConversation);
        } catch (Exception e) {
            logger.error("创建会话失败", e);
            result.put("success", false);
            result.put("message", "创建会话失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 获取会话详情
     */
    @GetMapping("/{conversationId}")
    public Map<String, Object> getConversation(@PathVariable String conversationId) {
        Map<String, Object> result = new HashMap<>();
        try {
            Conversation conversation = conversationService.getConversationById(conversationId);
            if (conversation != null) {
                result.put("success", true);
                result.put("data", conversation);
            } else {
                result.put("success", false);
                result.put("message", "会话不存在");
            }
        } catch (Exception e) {
            logger.error("获取会话详情失败: {}", conversationId, e);
            result.put("success", false);
            result.put("message", "获取会话详情失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 结束会话
     */
    @PostMapping("/{conversationId}/end")
    public Map<String, Object> endConversation(@PathVariable String conversationId, 
                                              @RequestParam String endType) {
        Map<String, Object> result = new HashMap<>();
        try {
            Conversation conversation = conversationService.endConversation(conversationId, endType);
            if (conversation != null) {
                result.put("success", true);
                result.put("data", conversation);
            } else {
                result.put("success", false);
                result.put("message", "会话不存在");
            }
        } catch (Exception e) {
            logger.error("结束会话失败: {}", conversationId, e);
            result.put("success", false);
            result.put("message", "结束会话失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 获取用户的会话列表
     */
    @GetMapping("/user/{userId}")
    public Map<String, Object> getUserConversations(@PathVariable String userId,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Conversation> conversations = conversationService.getUserConversations(userId, page, size);
            result.put("success", true);
            result.put("data", conversations);
            result.put("page", page);
            result.put("size", size);
        } catch (Exception e) {
            logger.error("获取用户会话列表失败: {}", userId, e);
            result.put("success", false);
            result.put("message", "获取用户会话列表失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 获取客服的会话列表
     */
    @GetMapping("/agent/{agentId}")
    public Map<String, Object> getAgentConversations(@PathVariable String agentId,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Conversation> conversations = conversationService.getAgentConversations(agentId, page, size);
            result.put("success", true);
            result.put("data", conversations);
            result.put("page", page);
            result.put("size", size);
        } catch (Exception e) {
            logger.error("获取客服会话列表失败: {}", agentId, e);
            result.put("success", false);
            result.put("message", "获取客服会话列表失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 获取用户和客服之间的活跃会话
     */
    @GetMapping("/active")
    public Map<String, Object> getActiveConversation(@RequestParam String userId, 
                                                   @RequestParam String agentId) {
        Map<String, Object> result = new HashMap<>();
        try {
            Conversation conversation = conversationService.getActiveConversation(userId, agentId);
            result.put("success", true);
            result.put("data", conversation);
        } catch (Exception e) {
            logger.error("获取活跃会话失败", e);
            result.put("success", false);
            result.put("message", "获取活跃会话失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 获取会话消息记录
     */
    @GetMapping("/{conversationId}/messages")
    public Map<String, Object> getConversationMessages(@PathVariable String conversationId,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "50") int size) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<MessageRecord> messages = conversationService.getConversationMessages(conversationId, page, size);
            result.put("success", true);
            result.put("data", messages);
            result.put("page", page);
            result.put("size", size);
        } catch (Exception e) {
            logger.error("获取会话消息失败: {}", conversationId, e);
            result.put("success", false);
            result.put("message", "获取会话消息失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 更新消息状态
     */
    @PutMapping("/message/{messageId}/status")
    public Map<String, Object> updateMessageStatus(@PathVariable String messageId, 
                                                 @RequestParam String status) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = conversationService.updateMessageStatus(messageId, status);
            result.put("success", success);
            if (!success) {
                result.put("message", "更新消息状态失败");
            }
        } catch (Exception e) {
            logger.error("更新消息状态失败: {}", messageId, e);
            result.put("success", false);
            result.put("message", "更新消息状态失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 获取统计信息
     */
    @GetMapping("/statistics")
    public Map<String, Object> getStatistics(@RequestParam(required = false) String userId,
                                           @RequestParam(required = false) String agentId) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> stats = conversationService.getConversationStatistics(userId, agentId);
            result.put("success", true);
            result.put("data", stats);
        } catch (Exception e) {
            logger.error("获取统计信息失败", e);
            result.put("success", false);
            result.put("message", "获取统计信息失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/{conversationId}")
    public Map<String, Object> deleteConversation(@PathVariable String conversationId) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = conversationService.deleteConversation(conversationId);
            result.put("success", success);
            if (!success) {
                result.put("message", "会话不存在或删除失败");
            }
        } catch (Exception e) {
            logger.error("删除会话失败: {}", conversationId, e);
            result.put("success", false);
            result.put("message", "删除会话失败: " + e.getMessage());
        }
        return result;
    }
}