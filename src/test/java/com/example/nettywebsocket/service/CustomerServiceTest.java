package com.example.nettywebsocket.service;

import com.example.nettywebsocket.manager.WebSocketConnectionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * CustomerService测试类
 */
@ExtendWith(MockitoExtension.class)
public class CustomerServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private WebSocketConnectionManager connectionManager;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ConversationService conversationService;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private SetOperations<String, Object> setOperations;

    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService();
        
        // 使用反射设置私有字段
        try {
            java.lang.reflect.Field redisTemplateField = CustomerService.class.getDeclaredField("redisTemplate");
            redisTemplateField.setAccessible(true);
            redisTemplateField.set(customerService, redisTemplate);

            java.lang.reflect.Field connectionManagerField = CustomerService.class.getDeclaredField("connectionManager");
            connectionManagerField.setAccessible(true);
            connectionManagerField.set(customerService, connectionManager);

            java.lang.reflect.Field objectMapperField = CustomerService.class.getDeclaredField("objectMapper");
            objectMapperField.setAccessible(true);
            objectMapperField.set(customerService, objectMapper);

            java.lang.reflect.Field conversationServiceField = CustomerService.class.getDeclaredField("conversationService");
            conversationServiceField.setAccessible(true);
            conversationServiceField.set(customerService, conversationService);

            java.lang.reflect.Field maxUsersPerAgentField = CustomerService.class.getDeclaredField("maxUsersPerAgent");
            maxUsersPerAgentField.setAccessible(true);
            maxUsersPerAgentField.set(customerService, 20);

        } catch (Exception e) {
            throw new RuntimeException("设置测试环境失败", e);
        }

        // 设置mock行为
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Test
    void testRegisterAgentWithWaitingUsers() {
        // 模拟有等待的用户
        Map<String, Channel> mockConnections = new HashMap<>();
        mockConnections.put("user1", mock(Channel.class));
        mockConnections.put("user2", mock(Channel.class));
        
        when(connectionManager.getAllConnections()).thenReturn(mockConnections);
        
        // 模拟用户未分配客服
        when(valueOperations.get(anyString())).thenReturn(null);
        
        // 模拟客服负载
        when(valueOperations.get("websocket:customer:service:load:agent1")).thenReturn(0);
        
        // 模拟客服注册
        when(setOperations.add("websocket:customer:service", "agent1")).thenReturn(1L);
        
        // 执行测试
        customerService.registerAgent("agent1");
        
        // 验证客服被注册
        verify(setOperations).add("websocket:customer:service", "agent1");
        verify(valueOperations).set("websocket:customer:service:load:agent1", 0);
        
        // 验证尝试分配等待用户（由于是私有方法，我们主要验证没有异常）
        // 实际业务逻辑会在registerAgent中调用assignWaitingUsersToNewAgent
    }

    @Test
    void testAssignCustomerService() {
        // 模拟用户未分配客服
        when(valueOperations.get("websocket:user:customer:service:user1")).thenReturn(null);
        
        // 模拟在线客服
        Set<Object> agents = new HashSet<>(Arrays.asList("agent1", "agent2"));
        when(setOperations.members("websocket:customer:service")).thenReturn(agents);
        
        // 模拟客服负载
        when(valueOperations.get("websocket:customer:service:load:agent1")).thenReturn(5);
        when(valueOperations.get("websocket:customer:service:load:agent2")).thenReturn(3);
        
        // 执行分配
        String assignedAgent = customerService.assignCustomerService("user1");
        
        // 验证分配了负载较小的客服agent2
        assertEquals("agent2", assignedAgent);
        
        // 验证映射关系被设置
        verify(valueOperations).set("websocket:user:customer:service:user1", "agent2");
        verify(setOperations).add("websocket:customer:service:users:agent2", "user1");
        verify(valueOperations).increment("websocket:customer:service:load:agent2");
    }
}