package com.common.system.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 基础配置
 *
 * <p>自定义 {@link RedisTemplate}（key 使用字符串序列化，value 使用 JSON 序列化并携带类型信息），
 * 供项目内缓存读写统一使用；连接参数（host/port/password/database）由 application.properties
 * 中 spring.data.redis.* 配置提供（默认连接本地 redis 服务 localhost:6379）。
 *
 * @author Eric
 * @date 2026/8/7
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        // key 统一字符串序列化，便于运维查看与按前缀清理
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        // value 统一 JSON 序列化（携带 @class 类型信息，反序列化可还原对象）
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }
}
