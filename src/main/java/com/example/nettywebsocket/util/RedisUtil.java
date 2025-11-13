package com.example.nettywebsocket.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis工具类
 */
@Component
@ConditionalOnBean(RedisTemplate.class)
public class RedisUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(RedisUtil.class);
    

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    static {
        
    }

    /**
     * 判断Redis是否可用
     * @return 是否可用
     */
    public boolean isRedisAvailable() {
        try {
            if (redisTemplate == null) {
                logger.warn("RedisTemplate为null，Redis不可用");
                return false;
            }
            
            RedisConnectionFactory connectionFactory = redisTemplate.getConnectionFactory();
            if (connectionFactory == null) {
                logger.warn("Redis连接工厂为null，Redis不可用");
                return false;
            }
            
            connectionFactory.getConnection().ping();
            logger.debug("Redis连接测试成功");
            return true;
        } catch (Exception e) {
            logger.warn("Redis不可用: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取值
     * @param key 键
     * @param clazz 类型
     * @param <T> 泛型
     * @return 值
     */
    public <T> T get(String key, Class<T> clazz) {
        try {
            if (!isRedisAvailable()) {
                return null;
            }
            ValueOperations<String, Object> operations = redisTemplate.opsForValue();
            Object value = operations.get(key);
            if (value != null && clazz.isInstance(value)) {
                return clazz.cast(value);
            }
            return null;
        } catch (Exception e) {
            logger.error("获取Redis值失败，key: {}", key, e);
            return null;
        }
    }
    
    /**
     * 设置值
     * @param key 键
     * @param value 值
     */
    public void set(String key, Object value) {
        try {
            if (!isRedisAvailable()) {
                return;
            }
            ValueOperations<String, Object> operations = redisTemplate.opsForValue();
            operations.set(key, value);
        } catch (Exception e) {
            logger.error("设置Redis值失败，key: {}", key, e);
        }
    }
    
    /**
     * 设置值并指定过期时间
     * @param key 键
     * @param value 值
     * @param timeout 过期时间
     * @param timeUnit 时间单位
     */
    public void set(String key, Object value, long timeout, TimeUnit timeUnit) {
        try {
            if (!isRedisAvailable()) {
                return;
            }
            ValueOperations<String, Object> operations = redisTemplate.opsForValue();
            operations.set(key, value, timeout, timeUnit);
        } catch (Exception e) {
            logger.error("设置Redis值失败，key: {}", key, e);
        }
    }
    
    /**
     * 删除键
     * @param key 键
     */
    public void delete(String key) {
        try {
            if (!isRedisAvailable()) {
                return;
            }
            redisTemplate.delete(key);
        } catch (Exception e) {
            logger.error("删除Redis键失败，key: {}", key, e);
        }
    }
    
    /**
     * 判断键是否存在
     * @param key 键
     * @return 是否存在
     */
    public boolean hasKey(String key) {
        try {
            if (!isRedisAvailable()) {
                return false;
            }
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            logger.error("判断Redis键是否存在失败，key: {}", key, e);
            return false;
        }
    }
    
    /**
     * 设置键的过期时间
     * @param key 键
     * @param timeout 过期时间
     * @param timeUnit 时间单位
     * @return 是否设置成功
     */
    public boolean expire(String key, long timeout, TimeUnit timeUnit) {
        try {
            if (!isRedisAvailable()) {
                return false;
            }
            return Boolean.TRUE.equals(redisTemplate.expire(key, timeout, timeUnit));
        } catch (Exception e) {
            logger.error("设置Redis键过期时间失败，key: {}", key, e);
            return false;
        }
    }
    
    /**
     * 添加元素到集合
     * @param key 键
     * @param value 值
     */
    public void addToSet(String key, Object value) {
        try {
            if (!isRedisAvailable()) {
                return;
            }
            SetOperations<String, Object> operations = redisTemplate.opsForSet();
            operations.add(key, value);
        } catch (Exception e) {
            logger.error("添加元素到Redis集合失败，key: {}", key, e);
        }
    }
    
    /**
     * 从集合中移除元素
     * @param key 键
     * @param value 值
     */
    public void removeFromSet(String key, Object value) {
        try {
            if (!isRedisAvailable()) {
                return;
            }
            SetOperations<String, Object> operations = redisTemplate.opsForSet();
            operations.remove(key, value);
        } catch (Exception e) {
            logger.error("从Redis集合中移除元素失败，key: {}", key, e);
        }
    }
    
    /**
     * 获取集合中的所有元素
     * @param key 键
     * @param clazz 类型
     * @param <T> 泛型
     * @return 集合
     */
    public <T> Set<T> getSetMembers(String key, Class<T> clazz) {
        try {
            if (!isRedisAvailable()) {
                return null;
            }
            SetOperations<String, Object> operations = redisTemplate.opsForSet();
            Set<Object> members = operations.members(key);
            if (members == null) {
                return null;
            }
            
            Set<T> result = new java.util.HashSet<>();
            for (Object member : members) {
                if (member != null && clazz.isInstance(member)) {
                    result.add(clazz.cast(member));
                }
            }
            return result;
        } catch (Exception e) {
            logger.error("获取Redis集合元素失败，key: {}", key, e);
            return null;
        }
    }
    
    /**
     * 判断元素是否在集合中
     * @param key 键
     * @param value 值
     * @return 是否在集合中
     */
    public boolean isMemberOfSet(String key, Object value) {
        try {
            if (!isRedisAvailable()) {
                return false;
            }
            SetOperations<String, Object> operations = redisTemplate.opsForSet();
            return Boolean.TRUE.equals(operations.isMember(key, value));
        } catch (Exception e) {
            logger.error("判断元素是否在Redis集合中失败，key: {}", key, e);
            return false;
        }
    }
    
    /**
     * 判断元素是否在集合中（isMemberOfSet的别名）
     * @param key 键
     * @param value 值
     * @return 是否在集合中
     */
    public boolean isMember(String key, Object value) {
        return isMemberOfSet(key, value);
    }
    
    /**
     * 获取集合的大小
     * @param key 键
     * @return 集合大小
     */
    public Long getSetSize(String key) {
        try {
            if (!isRedisAvailable()) {
                return 0L;
            }
            SetOperations<String, Object> operations = redisTemplate.opsForSet();
            return operations.size(key);
        } catch (Exception e) {
            logger.error("获取Redis集合大小失败，key: {}", key, e);
            return 0L;
        }
    }
    
    /**
     * 添加元素到有序集合
     * @param key 键
     * @param value 值
     * @param score 分数
     */
    public void addToSortedSet(String key, Object value, double score) {
        try {
            if (!isRedisAvailable()) {
                return;
            }
            ZSetOperations<String, Object> operations = redisTemplate.opsForZSet();
            operations.add(key, value, score);
        } catch (Exception e) {
            logger.error("添加元素到Redis有序集合失败，key: {}", key, e);
        }
    }
    
    /**
     * 获取有序集合指定范围的元素（按分数从低到高）
     * @param key 键
     * @param start 开始位置
     * @param end 结束位置
     * @return 元素集合
     */
    public Set<Object> getSortedSetRange(String key, long start, long end) {
        try {
            if (!isRedisAvailable()) {
                return null;
            }
            ZSetOperations<String, Object> operations = redisTemplate.opsForZSet();
            return operations.range(key, start, end);
        } catch (Exception e) {
            logger.error("获取Redis有序集合范围失败，key: {}", key, e);
            return null;
        }
    }
    
    /**
     * 获取有序集合指定范围的元素（按分数从高到低）
     * @param key 键
     * @param start 开始位置
     * @param end 结束位置
     * @return 元素集合
     */
    public Set<Object> getSortedSetReverseRange(String key, long start, long end) {
        try {
            if (!isRedisAvailable()) {
                return null;
            }
            ZSetOperations<String, Object> operations = redisTemplate.opsForZSet();
            return operations.reverseRange(key, start, end);
        } catch (Exception e) {
            logger.error("获取Redis有序集合反向范围失败，key: {}", key, e);
            return null;
        }
    }
    
    /**
     * 获取有序集合的大小
     * @param key 键
     * @return 集合大小
     */
    public Long getSortedSetSize(String key) {
        try {
            if (!isRedisAvailable()) {
                return 0L;
            }
            ZSetOperations<String, Object> operations = redisTemplate.opsForZSet();
            return operations.size(key);
        } catch (Exception e) {
            logger.error("获取Redis有序集合大小失败，key: {}", key, e);
            return 0L;
        }
    }
    
    /**
     * 获取有序集合中的所有元素
     * @param key 键
     * @return 元素集合
     */
    public Set<Object> getSortedSetMembers(String key) {
        try {
            if (!isRedisAvailable()) {
                return null;
            }
            ZSetOperations<String, Object> operations = redisTemplate.opsForZSet();
            return operations.range(key, 0, -1);
        } catch (Exception e) {
            logger.error("获取Redis有序集合所有元素失败，key: {}", key, e);
            return null;
        }
    }
    
    /**
     * 从有序集合中移除元素
     * @param key 键
     * @param value 值
     */
    public void remove(String key, Object value) {
        try {
            if (!isRedisAvailable()) {
                return;
            }
            ZSetOperations<String, Object> operations = redisTemplate.opsForZSet();
            operations.remove(key, value);
        } catch (Exception e) {
            logger.error("从Redis有序集合中移除元素失败，key: {}", key, e);
        }
    }
    
    /**
     * 从列表左侧添加元素
     * @param key 键
     * @param value 值
     * @return 列表长度
     */
    public Long leftPushToList(String key, Object value) {
        try {
            if (!isRedisAvailable()) {
                return 0L;
            }
            ListOperations<String, Object> operations = redisTemplate.opsForList();
            return operations.leftPush(key, value);
        } catch (Exception e) {
            logger.error("从Redis列表左侧添加元素失败，key: {}", key, e);
            return 0L;
        }
    }
    
    /**
     * 获取列表指定范围的元素
     * @param key 键
     * @param start 开始位置
     * @param end 结束位置
     * @return 元素列表
     */
    public List<Object> getListRange(String key, long start, long end) {
        try {
            if (!isRedisAvailable()) {
                return null;
            }
            ListOperations<String, Object> operations = redisTemplate.opsForList();
            return operations.range(key, start, end);
        } catch (Exception e) {
            logger.error("获取Redis列表范围失败，key: {}", key, e);
            return null;
        }
    }
    
    /**
     * 递增键的值
     * @param key 键
     * @return 递增后的值
     */
    public Long increment(String key) {
        try {
            if (!isRedisAvailable()) {
                return null;
            }
            ValueOperations<String, Object> operations = redisTemplate.opsForValue();
            return operations.increment(key);
        } catch (Exception e) {
            logger.error("递增Redis键值失败，key: {}", key, e);
            return null;
        }
    }
    
    /**
     * 递减键的值
     * @param key 键
     * @return 递减后的值
     */
    public Long decrement(String key) {
        try {
            if (!isRedisAvailable()) {
                return null;
            }
            ValueOperations<String, Object> operations = redisTemplate.opsForValue();
            return operations.increment(key, -1);
        } catch (Exception e) {
            logger.error("递减Redis键值失败，key: {}", key, e);
            return null;
        }
    }
}