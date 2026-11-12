package com.eric.base.common.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 模块信息缓存服务测试
 *
 * @author Eric
 * @date 2026/9/2
 * @description 覆盖 Redis 不可用/为空时的进程内兜底逻辑：
 *              cacheModelNames 无论 Redis 是否可用都更新本地缓存；
 *              getModelNames / resolveModelName 在 Redis 读取失败或缓存为空时回退本地，
 *              保证来源模块下拉等功能不依赖 Redis（部署环境默认无 Redis，见 infra/docker-compose.yml）。
 */
class ModelCacheServiceTest {

    private RedisTemplate<String, Object> redisTemplate;
    private HashOperations<String, Object, Object> hashOps;
    private ModelCacheService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        hashOps = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        service = new ModelCacheService(redisTemplate);
    }

    @Test
    void cacheModelNamesUpdatesLocalFallbackWhenRedisFails() {
        // Redis 写入失败（如未部署 Redis）：返回 0，但本地兜底缓存已更新
        doThrow(new RuntimeException("redis down")).when(hashOps).putAll(anyString(), anyMap());

        int cached = service.cacheModelNames();

        assertEquals(0, cached);
        // 本地兜底包含 classpath 下各模块（至少含 SYS-ORG / SYS-NOTIFY / SYS-DICT）
        Map<String, String> local = service.getModelNames();
        assertTrue(local.containsKey("SYS-ORG"));
        assertTrue(local.containsKey("SYS-NOTIFY"));
        assertTrue(local.containsKey("SYS-DICT"));
    }

    @Test
    void getModelNamesPrefersRedisWhenAvailable() {
        when(hashOps.entries(ModelCacheService.CACHE_KEY_MODEL_MAP))
                .thenReturn(Map.of("SYS-ORG", "组织架构和权限模块", "SYS-NOTIFY", "通知待办模块"));

        Map<String, String> names = service.getModelNames();

        assertEquals(2, names.size());
        assertEquals("组织架构和权限模块", names.get("SYS-ORG"));
    }

    @Test
    void getModelNamesFallsBackToLocalWhenRedisReadFails() {
        // 先写入本地兜底（模拟启动任务）
        service.cacheModelNames();
        when(hashOps.entries(anyString())).thenThrow(new RuntimeException("redis down"));

        Map<String, String> names = service.getModelNames();

        assertFalse(names.isEmpty());
        assertTrue(names.containsKey("SYS-ORG"));
    }

    @Test
    void getModelNamesFallsBackToLocalWhenRedisCacheEmpty() {
        // Redis 可达但缓存为空（key 被清理 / 未写入成功）
        service.cacheModelNames();
        when(hashOps.entries(anyString())).thenReturn(Map.of());

        Map<String, String> names = service.getModelNames();

        assertFalse(names.isEmpty());
        assertTrue(names.containsKey("SYS-ORG"));
        assertEquals("组织架构和权限模块", names.get("SYS-ORG"));
    }

    @Test
    void resolveModelNameUsesLocalFallback() {
        service.cacheModelNames();

        assertEquals("组织架构和权限模块", service.resolveModelName("SYS-ORG"));
        // 未知 key 回退原值
        assertEquals("UNKNOWN", service.resolveModelName("UNKNOWN"));
    }
}
