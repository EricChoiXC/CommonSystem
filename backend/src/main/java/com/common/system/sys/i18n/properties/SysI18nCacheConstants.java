package com.common.system.sys.i18n.properties;

/**
 * 多语言模块缓存常量
 *
 * @author Eric
 * @date 2026/11/5
 * @description 多语言模块 Redis 缓存 key 常量：翻译缓存以「每语言一份 Hash」存储
 *              （key 形如 {@code i18n:message:map:{language}}，field = 资源 key，value = 翻译文本），
 *              便于按语言整体失效；与 {@code ModelCacheService.CACHE_KEY_MODEL_MAP} 一样
 *              属部署增强能力，Redis 不可用时由进程内兜底保证可读。
 */
public final class SysI18nCacheConstants {

    /** 翻译缓存 key 前缀（完整 key = 前缀 + 语言码，如 i18n:message:map:zh_CN） */
    public static final String CACHE_KEY_I18N_MAP = "i18n:message:map:";

    private SysI18nCacheConstants() {
    }
}
