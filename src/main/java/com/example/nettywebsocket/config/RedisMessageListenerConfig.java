package com.example.nettywebsocket.config;

import com.example.nettywebsocket.listener.RedisMessageListener;
import org.springframework.beans.factory.annotation.Autowired;
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
    
    private static final String REDIS_PREFIX = "websocket:";
    
    @Autowired
    private RedisConnectionFactory redisConnectionFactory;
    
    @Autowired
    private RedisMessageListener redisMessageListener;
    
    /**
     * 创建Redis消息监听容器
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer() {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        
        // 注册广播消息监听
        container.addMessageListener(messageListenerAdapter(), broadcastTopic());
        
        // 注意：用户和群组消息监听不能在这里静态注册，需要动态注册
        // 实际使用时，可以根据需要动态添加和移除监听
        
        return container;
    }
    
    /**
     * 创建消息监听器适配器
     */
    @Bean
    public MessageListenerAdapter messageListenerAdapter() {
        return new MessageListenerAdapter(redisMessageListener);
    }
    
    /**
     * 创建广播通道主题
     */
    @Bean
    public ChannelTopic broadcastTopic() {
        return new ChannelTopic(REDIS_PREFIX + "broadcast");
    }
}