package com.example.nettywebsocket.security;

 import com.example.nettywebsocket.util.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import javax.annotation.PostConstruct;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Token验证服务的默认实现
 * 支持通过配置调用外部系统的token验证接口
 */
@Service
public class DefaultTokenService implements TokenService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultTokenService.class);

    // 是否启用外部token验证接口
    @Value("${websocket.security.token.external.enable:false}")
    private boolean enableExternalTokenValidation;

    // 外部token验证接口URL
    @Value("${websocket.security.token.external.validate-url:}")
    private String externalValidateUrl;

    // 外部获取用户信息接口URL
    @Value("${websocket.security.token.external.user-info-url:}")
    private String externalUserInfoUrl;

    // 外部接口请求超时时间（毫秒）
    @Value("${websocket.security.token.external.timeout:3000}")
    private int externalRequestTimeout;
    
    // 是否在外部接口失败时回退到本地验证
    @Value("${websocket.security.token.fallback-to-local:false}")
    private boolean fallbackToLocal;

    // Redis token存储配置
    @Value("${websocket.security.token.redis.expire-time:3600}")
    private int tokenExpireTime; // token过期时间，单位秒，默认1小时

    // Redis键前缀
    private static final String REDIS_PREFIX = "websocket:token:";
    private static final String TOKEN_USER_KEY = REDIS_PREFIX + "user:";
    private static final String USER_TOKEN_KEY = REDIS_PREFIX + "token:";

    // Redis工具类
    @Autowired
    private RedisUtil redisUtil;
    
    // 本地内存存储（当Redis不可用时作为回退方案）
    private final Map<String, String> tokenMap = new ConcurrentHashMap<>();
    private final Map<String, String> userTokenMap = new ConcurrentHashMap<>();

    // REST客户端，用于调用外部接口
    private final RestTemplate restTemplate;

    public DefaultTokenService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Spring初始化完成后调用
     */
    @PostConstruct
    public void init() {
        // 设置超时时间
        restTemplate.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {
            {
                setConnectTimeout(externalRequestTimeout);
                setReadTimeout(externalRequestTimeout);
            }
        });
    }

    @Override
    public boolean validateToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }

        // 如果启用了外部验证接口
        if (enableExternalTokenValidation && externalValidateUrl != null && !externalValidateUrl.isEmpty()) {
            try {
                logger.debug("使用外部接口验证token: {}", externalValidateUrl);
                
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + token);
                HttpEntity<String> entity = new HttpEntity<>("", headers);
                
                ResponseEntity<Map> response = restTemplate.exchange(
                        externalValidateUrl,
                        HttpMethod.POST,
                        entity,
                        Map.class
                );
                
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    return Boolean.TRUE.equals(response.getBody().get("valid"));
                }
            } catch (Exception e) {
                logger.error("调用外部token验证接口失败", e);
                // 检查是否配置了回退到本地验证
                if (fallbackToLocal) {
                    logger.warn("外部验证失败，回退到本地验证");
                    return hasKeyInStorage(TOKEN_USER_KEY + token);
                }
            }
            return false;
        } else {
            // 使用存储验证（优先Redis，回退到本地内存）
            return hasKeyInStorage(TOKEN_USER_KEY + token);
        }
    }

    @Override
    public String getUserIdByToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }

        // 如果启用了外部用户信息接口
        if (enableExternalTokenValidation && externalUserInfoUrl != null && !externalUserInfoUrl.isEmpty()) {
            try {
                logger.debug("使用外部接口获取用户信息: {}", externalUserInfoUrl);
                
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + token);
                HttpEntity<String> entity = new HttpEntity<>("", headers);
                
                ResponseEntity<Map> response = restTemplate.exchange(
                        externalUserInfoUrl,
                        HttpMethod.GET,
                        entity,
                        Map.class
                );
                
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    return (String) response.getBody().get("userId");
                }
            } catch (Exception e) {
                logger.error("调用外部用户信息接口失败", e);
                // 检查是否配置了回退到Redis验证
                if (fallbackToLocal) {
                    logger.warn("外部用户信息获取失败，回退到Redis验证");
                    return redisUtil.get(TOKEN_USER_KEY + token,String.class);
                }
            }
            return null;
        } else {
            // 使用Redis验证（分布式环境）
            return redisUtil.get(TOKEN_USER_KEY + token,String.class);
        }
    }

    @Override
    public void releaseToken(String token) {

    }

    /**
     * 添加token到Redis存储（分布式环境）
     * @param token token值
     * @param userId 用户ID
     */
    public void addToken(String token, String userId) {
        try {
            // 存储token->userId映射，并设置过期时间
            redisUtil.set(TOKEN_USER_KEY + token, userId, tokenExpireTime, TimeUnit.SECONDS);
            
            // 存储userId->token映射，用于防止同一用户多个token
            redisUtil.set(USER_TOKEN_KEY + userId, token, tokenExpireTime, TimeUnit.SECONDS);
            
            logger.info("添加token到Redis: token={}, userId={}, 过期时间={}秒", token, userId, tokenExpireTime);
        } catch (Exception e) {
            logger.error("添加token到Redis失败", e);
        }
    }

    /**
     * 从Redis存储移除token（分布式环境）
     * @param token token值
     */
    public void removeToken(String token) {
        try {
            // 先获取对应的userId
            String userId = redisUtil.get(TOKEN_USER_KEY + token,String.class);
            
            // 删除token->userId映射
            redisUtil.delete(TOKEN_USER_KEY + token);
            
            // 如果userId存在，删除userId->token映射
            if (userId != null) {
                redisUtil.delete(USER_TOKEN_KEY + userId);
            }
            
            logger.info("从Redis移除token: {}", token);
        } catch (Exception e) {
            logger.error("从Redis移除token失败", e);
        }
    }

    /**
     * 根据用户ID移除token
     * @param userId 用户ID
     */
    public void removeTokenByUserId(String userId) {
        try {
            // 获取用户对应的token
            String token = redisUtil.get(USER_TOKEN_KEY + userId,String.class);
            
            if (token != null) {
                // 删除token->userId映射
                redisUtil.delete(TOKEN_USER_KEY + token);
                // 删除userId->token映射
                redisUtil.delete(USER_TOKEN_KEY + userId);
                
                logger.info("根据用户ID移除token: userId={}, token={}", userId, token);
            }
        } catch (Exception e) {
            logger.error("根据用户ID移除token失败", e);
        }
    }

    /**
     * 刷新token过期时间
     * @param token token值
     */
    public void refreshToken(String token) {
        try {
            // 获取当前token对应的userId
            String userId = redisUtil.get(TOKEN_USER_KEY + token,String.class);
            
            if (userId != null) {
                // 刷新token->userId映射的过期时间
                redisUtil.expire(TOKEN_USER_KEY + token, tokenExpireTime, TimeUnit.SECONDS);
                // 刷新userId->token映射的过期时间
                redisUtil.expire(USER_TOKEN_KEY + userId, tokenExpireTime, TimeUnit.SECONDS);
                
                logger.debug("刷新token过期时间: token={}, userId={}", token, userId);
            }
        } catch (Exception e) {
            logger.error("刷新token过期时间失败", e);
        }
    }
    
    /**
     * 检查存储中是否存在指定键
     * @param key 键名
     * @return 是否存在
     */
    private boolean hasKeyInStorage(String key) {
        if (redisUtil.isRedisAvailable()) {
            return redisUtil.hasKey(key);
        } else {
            // 回退到本地内存存储
            return tokenMap.containsKey(key);
        }
    }
}