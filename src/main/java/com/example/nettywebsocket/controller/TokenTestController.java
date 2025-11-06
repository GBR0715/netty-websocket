package com.example.nettywebsocket.controller;

import com.example.nettywebsocket.security.DefaultTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Token测试控制器（仅用于开发环境）
 * 用于生成和管理测试用的token
 */
@RestController
@RequestMapping("/api/test")
public class TokenTestController {

    private static final Logger logger = LoggerFactory.getLogger(TokenTestController.class);

    @Autowired
    private DefaultTokenService tokenService;

    /**
     * 生成测试token
     * @param userId 用户ID
     * @return 生成的token信息
     */
    @PostMapping("/generate-token")
    public Map<String, String> generateToken(@RequestParam String userId) {
        Map<String, String> result = new HashMap<>();
        
        if (userId == null || userId.isEmpty()) {
            result.put("status", "error");
            result.put("message", "userId不能为空");
            return result;
        }
        
        // 生成随机token
        String token = "test_" + UUID.randomUUID().toString().replace("-", "");
        
        // 添加到本地token存储
        tokenService.addToken(token, userId);
        
        result.put("status", "success");
        result.put("token", token);
        result.put("userId", userId);
        result.put("message", "测试token生成成功");
        result.put("websocketUrl", "ws://localhost:8081/websocket?token=" + token);
        
        logger.info("生成测试token: userId={}, token={}", userId, token);
        
        return result;
    }

    /**
     * 移除测试token
     * @param token token值
     * @return 操作结果
     */
    @DeleteMapping("/remove-token")
    public Map<String, String> removeToken(@RequestParam String token) {
        Map<String, String> result = new HashMap<>();
        
        if (token == null || token.isEmpty()) {
            result.put("status", "error");
            result.put("message", "token不能为空");
            return result;
        }
        
        tokenService.removeToken(token);
        
        result.put("status", "success");
        result.put("message", "测试token已移除");
        
        logger.info("移除测试token: {}", token);
        
        return result;
    }

    /**
     * 验证token
     * @param token token值
     * @return 验证结果
     */
    @GetMapping("/validate-token")
    public Map<String, Object> validateToken(@RequestParam String token) {
        Map<String, Object> result = new HashMap<>();
        
        if (token == null || token.isEmpty()) {
            result.put("valid", false);
            result.put("message", "token不能为空");
            return result;
        }
        
        boolean isValid = tokenService.validateToken(token);
        String userId = tokenService.getUserIdByToken(token);
        
        result.put("valid", isValid);
        result.put("userId", userId);
        result.put("message", isValid ? "token有效" : "token无效");
        
        logger.info("验证测试token: {}, 结果: {}, userId: {}", token, isValid, userId);
        
        return result;
    }
}