package com.example.nettywebsocket.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DefaultTokenService测试类
 * 测试Redis集成的token验证功能
 */
@ExtendWith(MockitoExtension.class)
class DefaultTokenServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private DefaultTokenService tokenService;

    @BeforeEach
    void setUp() {
        // 只在需要valueOperations的测试中设置mock
    }

    @Test
    void testValidateTokenWithRedis() {
        // 模拟Redis中有token
        when(redisTemplate.hasKey("websocket:token:user:validToken123")).thenReturn(true);
        
        boolean result = tokenService.validateToken("validToken123");
        
        assertTrue(result);
        verify(redisTemplate).hasKey("websocket:token:user:validToken123");
    }

    @Test
    void testValidateTokenWithRedisNotFound() {
        // 模拟Redis中没有token
        when(redisTemplate.hasKey("websocket:token:user:invalidToken456")).thenReturn(false);
        
        boolean result = tokenService.validateToken("invalidToken456");
        
        assertFalse(result);
        verify(redisTemplate).hasKey("websocket:token:user:invalidToken456");
    }

    @Test
    void testGetUserIdByTokenWithRedis() {
        // 模拟Redis中有token对应的userId
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("websocket:token:user:validToken123")).thenReturn("user123");
        
        String userId = tokenService.getUserIdByToken("validToken123");
        
        assertEquals("user123", userId);
        verify(valueOperations).get("websocket:token:user:validToken123");
    }

    @Test
    void testGetUserIdByTokenWithRedisNotFound() {
        // 模拟Redis中没有token对应的userId
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("websocket:token:user:invalidToken456")).thenReturn(null);
        
        String userId = tokenService.getUserIdByToken("invalidToken456");
        
        assertNull(userId);
        verify(valueOperations).get("websocket:token:user:invalidToken456");
    }

    @Test
    void testAddTokenToRedis() {
        // 测试添加token到Redis
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        tokenService.addToken("newToken789", "user789");
        
        // 验证Redis操作被调用
        verify(valueOperations).set(eq("websocket:token:user:newToken789"), eq("user789"), anyLong(), any());
        verify(valueOperations).set(eq("websocket:token:token:user789"), eq("newToken789"), anyLong(), any());
    }

    @Test
    void testRemoveTokenFromRedis() {
        // 模拟Redis中有token对应的userId
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("websocket:token:user:tokenToRemove")).thenReturn("userToRemove");
        
        tokenService.removeToken("tokenToRemove");
        
        // 验证Redis删除操作被调用
        verify(redisTemplate).delete("websocket:token:user:tokenToRemove");
        verify(redisTemplate).delete("websocket:token:token:userToRemove");
    }

    @Test
    void testRemoveTokenByUserIdFromRedis() {
        // 模拟Redis中有userId对应的token
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("websocket:token:token:userToRemove")).thenReturn("tokenToRemove");
        
        tokenService.removeTokenByUserId("userToRemove");
        
        // 验证Redis删除操作被调用
        verify(redisTemplate).delete("websocket:token:user:tokenToRemove");
        verify(redisTemplate).delete("websocket:token:token:userToRemove");
    }

    @Test
    void testRefreshTokenInRedis() {
        // 模拟Redis中有token对应的userId
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("websocket:token:user:tokenToRefresh")).thenReturn("userToRefresh");
        
        tokenService.refreshToken("tokenToRefresh");
        
        // 验证Redis过期时间刷新操作被调用
        verify(redisTemplate).expire(eq("websocket:token:user:tokenToRefresh"), anyLong(), any());
        verify(redisTemplate).expire(eq("websocket:token:token:userToRefresh"), anyLong(), any());
    }
}