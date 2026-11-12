package com.eric.base.common.config;

import com.eric.base.common.module.service.ModuleGroupCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
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

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

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

    /**
     * 模块组开关状态广播监听容器：订阅 {@link ModuleGroupCacheService#CHANNEL_MODULE_GROUP_EVICT} 频道，
     * 收到其他实例的开关切换通知后失效本实例状态缓存（下次判定回源 DB）。
     * <p>Redis pub/sub 属可选增强：仅当 Redis 可达时才注册监听器（无监听器时容器 start 为空操作，
     * 不会发起订阅连接），Redis 不可达（部署环境默认无 Redis）时不注册、仅记日志，
     * 保证上下文启动不因订阅连接失败而失败，广播失效退化为依赖 TTL 收敛（决策 D1）；
     * Redis 启动后恢复可达需重启应用以重新建立订阅。开关判定不依赖 Redis
     * （进程内 TTL 缓存 + fail-open 兜底，见 ModuleGroupCacheService）。</p>
     */
    @Bean
    public RedisMessageListenerContainer moduleGroupListenerContainer(
            RedisConnectionFactory connectionFactory, ModuleGroupCacheService moduleGroupCacheService) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        if (isRedisReachable(connectionFactory)) {
            container.addMessageListener(moduleGroupCacheService,
                    new ChannelTopic(ModuleGroupCacheService.CHANNEL_MODULE_GROUP_EVICT));
        }
        return container;
    }

    /**
     * 探测 Redis 是否可达：可达时才注册广播监听器（pub/sub 订阅需要真实连接），
     * 不可达时容器保持无监听状态，避免 start 阶段订阅连接失败导致上下文启动失败。
     */
    private boolean isRedisReachable(RedisConnectionFactory connectionFactory) {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            return true;
        } catch (Exception e) {
            log.warn("redis unreachable at startup, module group evict subscription disabled (rely on ttl convergence)", e);
            return false;
        }
    }
}
