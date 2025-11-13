package com.example.nettywebsocket.config;

import com.example.nettywebsocket.listener.RedisMessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

/**
 * Redis消息监听配置类
 */
@Configuration
public class RedisMessageListenerConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(RedisMessageListenerConfig.class);
    private static final String REDIS_PREFIX = "websocket:";
    
    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;
    
    @Autowired(required = false)
    private RedisMessageListener redisMessageListener;
    
    /**
     * 创建Redis消息监听容器（仅在Redis可用时创建）
     */
    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisMessageListenerContainer redisMessageListenerContainer() {
        logger.info("尝试创建Redis消息监听容器，RedisConnectionFactory: {}", 
            redisConnectionFactory != null ? redisConnectionFactory.getClass().getName() : "null");
            
        if (redisConnectionFactory == null) {
            logger.warn("RedisConnectionFactory不可用，跳过Redis消息监听容器创建");
            return null;
        }
        
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        
        // 注册广播消息监听
        MessageListenerAdapter adapter = messageListenerAdapter();
        ChannelTopic topic = broadcastTopic();
        
        if (adapter != null && topic != null) {
            container.addMessageListener(adapter, topic);
            logger.info("Redis消息监听容器创建成功");
        } else {
            logger.warn("消息监听器适配器或主题为空，跳过监听器注册");
        }
        
        // 注意：用户和群组消息监听不能在这里静态注册，需要动态注册
        // 实际使用时，可以根据需要动态添加和移除监听
        
        return container;
    }
    
    /**
     * 创建消息监听器适配器（仅在Redis可用时创建）
     */
    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public MessageListenerAdapter messageListenerAdapter() {
        logger.info("尝试创建消息监听器适配器，RedisMessageListener: {}", 
            redisMessageListener != null ? redisMessageListener.getClass().getName() : "null");
            
        if (redisMessageListener == null) {
            logger.warn("RedisMessageListener不可用，跳过消息监听器适配器创建");
            return null;
        }
        return new MessageListenerAdapter(redisMessageListener);
    }
    
    /**
     * 创建广播通道主题（仅在Redis可用时创建）
     */
    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public ChannelTopic broadcastTopic() {
        logger.info("创建广播通道主题");
        return new ChannelTopic(REDIS_PREFIX + "broadcast");
    }
}