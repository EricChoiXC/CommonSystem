package com.common.system.sys.i18n.service;

import com.common.system.sys.i18n.dao.SysI18nTranslationDao;
import com.common.system.sys.i18n.model.SysI18nTranslation;
import com.common.system.sys.i18n.properties.SysI18nCacheConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 多语言翻译缓存服务测试
 *
 * @author Eric
 * @date 2026/11/5
 * @description 覆盖 Redis 不可用/为空时的进程内兜底逻辑（照 {@code ModelCacheServiceTest}）：
 *              cacheTranslations 无论 Redis 是否可用都更新本地兜底（回归清单「无 Redis
 *              环境翻译可读」）；getMessages 在 Redis 读取失败或缓存为空时回退本地；
 *              按语言失效与全量失效联动两侧缓存。
 */
class SysI18nCacheServiceTest {

    private static final String KEY_ZH = SysI18nCacheConstants.CACHE_KEY_I18N_MAP + "zh_CN";
    private static final String KEY_EN = SysI18nCacheConstants.CACHE_KEY_I18N_MAP + "en_US";

    private RedisTemplate<String, Object> redisTemplate;
    private HashOperations<String, Object, Object> hashOps;
    private SysI18nTranslationDao translationDao;
    private SysI18nCacheService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        hashOps = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        translationDao = mock(SysI18nTranslationDao.class);
        service = new SysI18nCacheService(redisTemplate, translationDao);
    }

    /** 构造一条翻译 */
    private SysI18nTranslation translation(String key, String message) {
        SysI18nTranslation model = new SysI18nTranslation();
        model.setFieldKey(key);
        model.setFieldLanguage("zh_CN");
        model.setFieldMessage(message);
        return model;
    }

    @Test
    void cacheTranslationsUpdatesLocalFallbackWhenRedisFails() {
        // Redis 写入失败（如未部署 Redis）：本地兜底缓存已更新，读取侧有值
        when(translationDao.selectByLanguage("zh_CN"))
                .thenReturn(List.of(translation("org.user.save", "保存用户")));
        doThrow(new RuntimeException("redis down")).when(hashOps).putAll(anyString(), anyMap());

        int cached = service.cacheTranslations(List.of("zh_CN"));

        assertEquals(1, cached);
        Map<String, String> messages = service.getMessages("zh_CN");
        assertEquals("保存用户", messages.get("org.user.save"));
    }

    @Test
    void getMessagesPrefersRedisWhenAvailable() {
        // 先经 cacheTranslations 建立本地兜底，再让 Redis 返回不同值：优先取 Redis
        when(translationDao.selectByLanguage("zh_CN"))
                .thenReturn(List.of(translation("org.user.save", "保存用户")));
        service.cacheTranslations(List.of("zh_CN"));
        when(hashOps.entries(KEY_ZH)).thenReturn(Map.of("org.user.save", "来自Redis"));

        Map<String, String> messages = service.getMessages("zh_CN");

        assertEquals("来自Redis", messages.get("org.user.save"));
    }

    @Test
    void getMessagesFallsBackToLocalWhenRedisReadFails() {
        when(translationDao.selectByLanguage("zh_CN"))
                .thenReturn(List.of(translation("org.user.save", "保存用户")));
        service.cacheTranslations(List.of("zh_CN"));
        when(hashOps.entries(anyString())).thenThrow(new RuntimeException("redis down"));

        Map<String, String> messages = service.getMessages("zh_CN");

        assertEquals("保存用户", messages.get("org.user.save"));
    }

    @Test
    void getMessagesFallsBackToLocalWhenRedisCacheEmpty() {
        // Redis 可达但该语言缓存为空（key 被清理 / 语言未缓存）
        when(translationDao.selectByLanguage("zh_CN"))
                .thenReturn(List.of(translation("org.user.save", "保存用户")));
        service.cacheTranslations(List.of("zh_CN"));
        when(hashOps.entries(KEY_ZH)).thenReturn(Map.of());

        Map<String, String> messages = service.getMessages("zh_CN");

        assertEquals("保存用户", messages.get("org.user.save"));
    }

    @Test
    void cacheTranslationsRefreshesToNewValues() {
        // 缓存刷新后读取到新值（同步/部署后的可见性）
        when(translationDao.selectByLanguage("zh_CN"))
                .thenReturn(List.of(translation("org.user.save", "旧文案")))
                .thenReturn(List.of(translation("org.user.save", "新文案")));
        service.cacheTranslations(List.of("zh_CN"));
        when(hashOps.entries(KEY_ZH)).thenReturn(Map.of());

        service.cacheTranslations(List.of("zh_CN"));

        assertEquals("新文案", service.getMessages("zh_CN").get("org.user.save"));
    }

    @Test
    void cacheTranslationsSkipsLanguageWithoutTranslations() {
        // 语言无翻译：不进入本地兜底，并清理该语言缓存
        when(translationDao.selectByLanguage("zh_CN")).thenReturn(List.of());
        when(translationDao.selectByLanguage("en_US")).thenReturn(List.of());

        int cached = service.cacheTranslations(List.of("zh_CN", "en_US"));

        assertEquals(0, cached);
        assertTrue(service.getMessages("zh_CN").isEmpty());
        // cacheTranslations 写入前会先删旧 key，此处至少一次即可（含清理动作）
        verify(redisTemplate, atLeastOnce()).delete(KEY_ZH);
    }

    @Test
    void evictLanguageRemovesLocalAndRedis() {
        when(translationDao.selectByLanguage("zh_CN"))
                .thenReturn(List.of(translation("org.user.save", "保存用户")));
        when(translationDao.selectByLanguage("en_US"))
                .thenReturn(List.of(translation("org.user.save", "Save User")));
        service.cacheTranslations(List.of("zh_CN", "en_US"));
        when(hashOps.entries(anyString())).thenReturn(Map.of());

        service.evictLanguage("zh_CN");

        // zh_CN 两侧均失效，en_US 不受影响
        assertTrue(service.getMessages("zh_CN").isEmpty());
        assertEquals("Save User", service.getMessages("en_US").get("org.user.save"));
        verify(redisTemplate, atLeastOnce()).delete(KEY_ZH);
    }

    @Test
    void evictAllClearsEveryLanguage() {
        when(translationDao.selectByLanguage("zh_CN"))
                .thenReturn(List.of(translation("org.user.save", "保存用户")));
        when(translationDao.selectByLanguage("en_US"))
                .thenReturn(List.of(translation("org.user.save", "Save User")));
        service.cacheTranslations(List.of("zh_CN", "en_US"));
        when(hashOps.entries(anyString())).thenReturn(Map.of());

        service.evictAll();

        assertTrue(service.getMessages("zh_CN").isEmpty());
        assertTrue(service.getMessages("en_US").isEmpty());
        verify(redisTemplate, atLeastOnce()).delete(KEY_ZH);
        verify(redisTemplate, atLeastOnce()).delete(KEY_EN);
    }

    @Test
    void getMessagesReturnsEmptyForBlankLanguage() {
        assertFalse(service.getMessages("").containsKey("org.user.save"));
    }
}
