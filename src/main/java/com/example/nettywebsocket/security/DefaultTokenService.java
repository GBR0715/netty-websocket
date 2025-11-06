package com.example.nettywebsocket.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    // 本地模拟的token存储（仅用于开发环境或测试）
    private final Map<String, String> tokenMap = new ConcurrentHashMap<>();

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
                    return tokenMap.containsKey(token);
                }
            }
            return false;
        } else {
            // 使用本地验证（仅用于开发或测试）
            return tokenMap.containsKey(token);
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
                // 检查是否配置了回退到本地验证
                if (fallbackToLocal) {
                    logger.warn("外部用户信息获取失败，回退到本地验证");
                    return tokenMap.get(token);
                }
            }
            return null;
        } else {
            // 使用本地验证（仅用于开发或测试）
            return tokenMap.get(token);
        }
    }

    /**
     * 添加token到本地存储（仅用于开发或测试）
     * @param token token值
     * @param userId 用户ID
     */
    public void addToken(String token, String userId) {
        tokenMap.put(token, userId);
        logger.info("添加测试token: {}, userId: {}", token, userId);
    }

    /**
     * 从本地存储移除token（仅用于开发或测试）
     * @param token token值
     */
    public void removeToken(String token) {
        tokenMap.remove(token);
        logger.info("移除测试token: {}", token);
    }
}