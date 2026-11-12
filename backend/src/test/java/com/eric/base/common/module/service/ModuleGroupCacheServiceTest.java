package com.eric.base.common.module.service;

import com.eric.base.common.module.dao.CommonModuleDao;
import com.eric.base.common.module.model.CommonModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.RedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 模块组开关状态缓存服务测试
 *
 * @author Eric
 * @date 2026/11/12
 * @description 覆盖修订稿第 9 节缓存行为表与 fail-open 分支（mock 依赖，不真连 DB / Redis）：
 *              缓存命中不回源、TTL 过期回源、DB 异常时 fail-open / 沿用旧值、未知 key 放行、
 *              广播失败不影响主流程、收到广播失效后下次判定回源。
 */
class ModuleGroupCacheServiceTest {

    private CommonModuleDao moduleDao;
    private RedisTemplate<String, Object> redisTemplate;
    private ModuleGroupCacheService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        moduleDao = mock(CommonModuleDao.class);
        redisTemplate = mock(RedisTemplate.class);
        service = new ModuleGroupCacheService(moduleDao, redisTemplate);
    }

    private static CommonModule module(String key, boolean enable) {
        CommonModule module = new CommonModule();
        module.setFieldKey(key);
        module.setFieldEnable(enable);
        return module;
    }

    @Test
    void isEnabledUsesCacheWithoutExtraDbHit() {
        when(moduleDao.selectAll()).thenReturn(Arrays.asList(
                module("calendar", true), module("org-group", false)));

        service.refresh();

        assertTrue(service.isEnabled("calendar"));
        assertFalse(service.isEnabled("org-group"));
        // 判定全部命中进程内缓存，除预热外无额外回源
        verify(moduleDao, times(1)).selectAll();
    }

    @Test
    void expiredCacheTriggersDbReloadWithNewValue() {
        when(moduleDao.selectAll()).thenReturn(Collections.singletonList(module("calendar", true)));
        service.refresh();
        assertTrue(service.isEnabled("calendar"));

        // invalidate 置空加载时间戳，模拟 TTL 到期（与广播失效同一路径）；DB 状态已变更
        when(moduleDao.selectAll()).thenReturn(Collections.singletonList(module("calendar", false)));
        service.invalidate();

        assertFalse(service.isEnabled("calendar"));
        verify(moduleDao, times(2)).selectAll();
    }

    @Test
    void dbFailureWithoutCacheFailsOpen() {
        when(moduleDao.selectAll()).thenThrow(new RuntimeException("db down"));

        // DB 异常且无可用缓存：放行并告警（fail-open），不放大为全站拒绝
        assertTrue(service.isEnabled("calendar"));
        verify(moduleDao, times(1)).selectAll();
    }

    @Test
    void dbFailureWithCacheKeepsLastValue() {
        when(moduleDao.selectAll()).thenReturn(Collections.singletonList(module("calendar", false)));
        service.refresh();
        assertFalse(service.isEnabled("calendar"));

        when(moduleDao.selectAll()).thenThrow(new RuntimeException("db down"));
        service.invalidate();

        // DB 异常但有缓存：沿用上次缓存值（false），不放行
        assertFalse(service.isEnabled("calendar"));
        verify(moduleDao, times(2)).selectAll();
    }

    @Test
    void unknownKeyFailsOpen() {
        when(moduleDao.selectAll()).thenReturn(Collections.singletonList(module("calendar", true)));
        service.refresh();

        // key 不在 common_module_group 中（拼写不一致等）：按「开启」放行，不触发额外回源
        assertTrue(service.isEnabled("not-exist"));
        verify(moduleDao, times(1)).selectAll();
    }

    @Test
    void broadcastFailureDoesNotAffectRefresh() {
        when(moduleDao.selectAll()).thenReturn(Collections.singletonList(module("calendar", true)));
        doThrow(new RuntimeException("redis down")).when(redisTemplate).convertAndSend(anyString(), any());

        // 广播 best-effort：Redis 抛异常不影响方法返回，本实例刷新照常生效
        assertDoesNotThrow(service::refreshAndBroadcast);

        assertTrue(service.isEnabled("calendar"));
        verify(redisTemplate).convertAndSend(eq(ModuleGroupCacheService.CHANNEL_MODULE_GROUP_EVICT), any());
    }

    @Test
    void broadcastMessageInvalidatesCacheForNextCheck() {
        when(moduleDao.selectAll()).thenReturn(Collections.singletonList(module("calendar", true)));
        service.refresh();
        assertTrue(service.isEnabled("calendar"));

        // 收到其他实例的广播消息：本实例缓存失效，下次判定回源读取新状态
        when(moduleDao.selectAll()).thenReturn(Collections.singletonList(module("calendar", false)));
        DefaultMessage message = new DefaultMessage(
                ModuleGroupCacheService.CHANNEL_MODULE_GROUP_EVICT.getBytes(StandardCharsets.UTF_8),
                "calendar".getBytes(StandardCharsets.UTF_8));
        service.onMessage(message, null);

        assertFalse(service.isEnabled("calendar"));
        verify(moduleDao, times(2)).selectAll();
    }

    @Test
    void refreshForcesReloadEvenWhenCacheFresh() {
        when(moduleDao.selectAll()).thenReturn(Collections.singletonList(module("calendar", true)));

        // 强制刷新语义：切换成功路径 / 预热不受 TTL 保护，每次都回源
        service.refresh();
        service.refresh();

        verify(moduleDao, times(2)).selectAll();
    }

    @Test
    void dirtyRowsSkippedAndEmptyResultFailsOpen() {
        CommonModule noKey = new CommonModule();
        CommonModule noEnable = new CommonModule();
        noEnable.setFieldKey("broken");
        when(moduleDao.selectAll()).thenReturn(Arrays.asList(module("calendar", true), noKey, noEnable));

        service.refresh();

        assertTrue(service.isEnabled("calendar"));
        // 脏数据不进入缓存，其余 key 按未知 key fail-open
        assertTrue(service.isEnabled("any-unknown"));
        verify(moduleDao, times(1)).selectAll();
    }

    @Test
    void emptyKeyFailsOpenWithoutAnyIo() {
        assertTrue(service.isEnabled(null));
        assertTrue(service.isEnabled(" "));
        verifyNoInteractions(moduleDao, redisTemplate);
    }
}
