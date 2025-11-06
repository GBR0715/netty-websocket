package com.example.nettywebsocket.controller;

import com.example.nettywebsocket.service.CustomerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客服相关的控制器
 */
@RestController
@RequestMapping("/api/customer-service")
public class CustomerServiceController {

    private static final Logger logger = LoggerFactory.getLogger(CustomerServiceController.class);

    @Autowired
    private CustomerService customerService;

    /**
     * 客服注册
     */
    @PostMapping("/register")
    public Map<String, Object> registerAgent(@RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();
        try {
            String agentId = request.get("agentId");
            if (agentId == null || agentId.trim().isEmpty()) {
                result.put("status", "error");
                result.put("message", "客服ID不能为空");
                return result;
            }

            customerService.registerAgent(agentId);
            result.put("status", "success");
            result.put("message", "客服注册成功");
            result.put("agentId", agentId);
        } catch (Exception e) {
            logger.error("客服注册失败", e);
            result.put("status", "error");
            result.put("message", "客服注册失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 客服注销
     */
    @PostMapping("/unregister")
    public Map<String, Object> unregisterAgent(@RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();
        try {
            String agentId = request.get("agentId");
            if (agentId == null || agentId.trim().isEmpty()) {
                result.put("status", "error");
                result.put("message", "客服ID不能为空");
                return result;
            }

            customerService.unregisterAgent(agentId);
            result.put("status", "success");
            result.put("message", "客服注销成功");
            result.put("agentId", agentId);
        } catch (Exception e) {
            logger.error("客服注销失败", e);
            result.put("status", "error");
            result.put("message", "客服注销失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 获取客服的服务列表
     */
    @GetMapping("/agent/{agentId}/users")
    public Map<String, Object> getAgentUsers(@PathVariable String agentId) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (!customerService.isAgent(agentId)) {
                result.put("status", "error");
                result.put("message", "无效的客服ID");
                return result;
            }

            List<String> users = customerService.getUsersForAgent(agentId);
            int currentLoad = customerService.getAgentLoad(agentId);
            
            result.put("status", "success");
            result.put("agentId", agentId);
            result.put("users", users);
            result.put("currentLoad", currentLoad);
        } catch (Exception e) {
            logger.error("获取客服服务列表失败", e);
            result.put("status", "error");
            result.put("message", "获取服务列表失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 获取用户对应的客服
     */
    @GetMapping("/user/{userId}/agent")
    public Map<String, Object> getUserAgent(@PathVariable String userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            String agentId = customerService.getAgentForUser(userId);
            
            result.put("status", "success");
            result.put("userId", userId);
            result.put("agentId", agentId);
            result.put("hasAgent", agentId != null);
        } catch (Exception e) {
            logger.error("获取用户客服信息失败", e);
            result.put("status", "error");
            result.put("message", "获取客服信息失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 获取所有在线客服
     */
    @GetMapping("/online-agents")
    public Map<String, Object> getOnlineAgents() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<String> agents = customerService.getAllOnlineAgents();
            
            // 获取每个客服的负载信息
            Map<String, Integer> agentLoads = new HashMap<>();
            for (String agentId : agents) {
                agentLoads.put(agentId, customerService.getAgentLoad(agentId));
            }
            
            result.put("status", "success");
            result.put("agents", agents);
            result.put("agentLoads", agentLoads);
            result.put("totalAgents", agents.size());
        } catch (Exception e) {
            logger.error("获取在线客服列表失败", e);
            result.put("status", "error");
            result.put("message", "获取在线客服失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 设置客服最大服务人数
     */
    @PostMapping("/set-max-users")
    public Map<String, Object> setMaxUsersPerAgent(@RequestBody Map<String, Integer> request) {
        Map<String, Object> result = new HashMap<>();
        try {
            Integer maxUsers = request.get("maxUsers");
            if (maxUsers == null || maxUsers <= 0) {
                result.put("status", "error");
                result.put("message", "最大服务人数必须大于0");
                return result;
            }

            customerService.setMaxUsersPerAgent(maxUsers);
            result.put("status", "success");
            result.put("message", "客服最大服务人数设置成功");
            result.put("maxUsersPerAgent", maxUsers);
        } catch (Exception e) {
            logger.error("设置最大服务人数失败", e);
            result.put("status", "error");
            result.put("message", "设置失败: " + e.getMessage());
        }
        return result;
    }
}