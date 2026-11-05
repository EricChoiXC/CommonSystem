package com.common.system.sys.i18n.service;

import com.common.system.common.utils.StringUtil;
import com.common.system.sys.i18n.dao.SysI18nTranslationDao;
import com.common.system.sys.i18n.model.SysI18nTranslation;
import com.common.system.sys.i18n.properties.SysI18nCacheConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多语言翻译缓存服务
 *
 * <p>照 {@code ModelCacheService} 先例实现「Redis 优先 + 进程内兜底」的翻译缓存：</p>
 * <ul>
 * <li>结构：内存 {@code Map<language, Map<key, message>>} + Redis Hash
 *     （key = {@code i18n:message:map:{language}}，field = 资源 key，value = 翻译文本），
 *     每语言一份 Hash 便于按语言整体失效；</li>
 * <li>写入（{@link #cacheTranslations}）：无论 Redis 是否可用都先更新进程内兜底缓存，
 *     保证读取侧始终有值；Redis 写入失败仅记录日志不抛出（缓存属增强能力）；</li>
 * <li>读取（{@link #getMessages}）：优先读 Redis，失败或为空时回退进程内兜底
 *     （部署环境默认无 Redis，本地兜底是可用性底线）；</li>
 * <li>失效（{@link #evictLanguage} / {@link #evictAll}）：按语言删除 Hash + 从兜底缓存移除，
 *     供语言增删改（I18N3）、翻译维护（I18N4）与部署（I18N5）联动调用；</li>
 * <li>并发安全：兜底缓存与语言 Map 均为并发容器，重建时整体替换（volatile 引用），读不加锁。</li>
 * </ul>
 *
 * @author Eric
 * @date 2026/11/5
 */
@Service
public class SysI18nCacheService {

    private static final Logger log = LoggerFactory.getLogger(SysI18nCacheService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final SysI18nTranslationDao translationDao;

    /**
     * 进程内兜底缓存：language → (key → message)。
     * <p>volatile 引用 + 不可变 Map，整体替换实现并发安全（读不加锁）；
     * Redis 不可用、缓存为空或读取失败时回退本缓存。</p>
     */
    private volatile Map<String, Map<String, String>> localMessages = Map.of();

    public SysI18nCacheService(RedisTemplate<String, Object> redisTemplate,
                               SysI18nTranslationDao translationDao) {
        this.redisTemplate = redisTemplate;
        this.translationDao = translationDao;
    }

    /* ==================== 写入 ==================== */

    /**
     * 全量刷新翻译缓存：从 DB 读取全部启用语言的翻译，写入 Redis Hash 与进程内兜底缓存。
     * <p>启动同步、部署动作与翻译维护完成后调用（任务清单 I18N2-BE-001/002、I18N4、I18N5）。
     * 单语言失败仅记录日志继续（不阻断其他语言）；Redis 失败不影响本地兜底更新。</p>
     *
     * @param languages 语言码列表（如 zh_CN、en_US；需为启用语言，调用方过滤）
     * @return 实际写入缓存的翻译总条数（Redis 失败时为本地兜底条数）
     */
    public int cacheTranslations(List<String> languages) {
        if (null == languages || languages.isEmpty()) {
            return 0;
        }
        int total = 0;
        Map<String, Map<String, String>> local = new HashMap<>();
        for (String language : languages) {
            Map<String, String> messages = loadMessages(language);
            if (messages.isEmpty()) {
                // 语言无翻译：清空该语言缓存（含 Redis 侧），保持「有语言无翻译」与 DB 一致
                evictLanguage(language);
                continue;
            }
            local.put(language, Map.copyOf(messages));
            total += messages.size();
            try {
                redisTemplate.delete(SysI18nCacheConstants.CACHE_KEY_I18N_MAP + language);
                redisTemplate.opsForHash().putAll(SysI18nCacheConstants.CACHE_KEY_I18N_MAP + language, messages);
            } catch (Exception e) {
                log.warn("failed to cache i18n messages to redis, language={}, fallback to local cache", language, e);
            }
        }
        // 兜底缓存整体替换（volatile 引用），保证并发读安全
        localMessages = Map.copyOf(local);
        return total;
    }

    /**
     * 按语言失效缓存：删除该语言的 Redis Hash 与进程内兜底条目。
     * <p>供删除语言（D4 级联）、单条翻译重置等「只影响部分语言」的场景精确失效。</p>
     *
     * @param language 语言码
     */
    public void evictLanguage(String language) {
        if (StringUtil.isEmpty(language)) {
            return;
        }
        try {
            redisTemplate.delete(SysI18nCacheConstants.CACHE_KEY_I18N_MAP + language);
        } catch (Exception e) {
            log.warn("failed to evict i18n messages from redis, language={}", language, e);
        }
        Map<String, Map<String, String>> local = new HashMap<>(localMessages);
        local.remove(language);
        localMessages = Map.copyOf(local);
    }

    /**
     * 全量失效缓存：清空全部语言的 Redis Hash 与进程内兜底缓存。
     */
    public void evictAll() {
        for (String language : localMessages.keySet()) {
            try {
                redisTemplate.delete(SysI18nCacheConstants.CACHE_KEY_I18N_MAP + language);
            } catch (Exception e) {
                log.warn("failed to evict i18n messages from redis, language={}", language, e);
            }
        }
        localMessages = Map.of();
    }

    /* ==================== 读取 ==================== */

    /**
     * 读取指定语言的翻译 Map（key → message）。
     * <p>优先读 Redis；Redis 读取失败或缓存为空（未部署 Redis / key 被清理 / 语言未缓存）时
     * 回退进程内兜底缓存，保证翻译读取不依赖 Redis 可用性。</p>
     *
     * @param language 语言码
     * @return 翻译 Map；无翻译时返回空 Map
     */
    public Map<String, String> getMessages(String language) {
        if (StringUtil.isEmpty(language)) {
            return Map.of();
        }
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(SysI18nCacheConstants.CACHE_KEY_I18N_MAP + language);
            if (!entries.isEmpty()) {
                Map<String, String> result = new LinkedHashMap<>();
                entries.forEach((key, value) -> result.put(String.valueOf(key), String.valueOf(value)));
                return result;
            }
            return localMessages.getOrDefault(language, Map.of());
        } catch (Exception e) {
            log.warn("failed to read i18n messages from redis, language={}, fallback to local cache", language, e);
            return localMessages.getOrDefault(language, Map.of());
        }
    }

    /* ==================== 内部 ==================== */

    /**
     * 从 DB 读取指定语言的全部翻译（key 升序）并展平为 Map。
     */
    private Map<String, String> loadMessages(String language) {
        List<SysI18nTranslation> translations = translationDao.selectByLanguage(language);
        Map<String, String> messages = new HashMap<>(Math.max(16, translations.size() * 2));
        for (SysI18nTranslation translation : translations) {
            if (StringUtil.isNotEmpty(translation.getFieldKey())
                    && null != translation.getFieldMessage()) {
                messages.put(translation.getFieldKey(), translation.getFieldMessage());
            }
        }
        return messages;
    }
}
